"""PreToolUse(Bash) hook: git/gh 작업 규칙을 기계적으로 강제한다.

혼자 하는 프로젝트라 리뷰어가 없으므로, 사람이 지켜줄 규칙을 hook이 대신 지킨다.
- main/develop 에서 커밋/푸시 금지 (모든 변경은 PR을 거친다)
- gh pr create 의 base: 기능 브랜치 → develop, develop → main(릴리스)만 허용
- 일괄 스테이징(git add -A / . / -u) 과 hook 우회(--no-verify) 금지
- 커밋 메시지 컨벤션 검사 (`<type>: <한국어 요약>`)
- gh pr create: 현재 HEAD에 대한 자가 QA 리포트(PASS)가 있어야 한다
- gh pr merge: 현재 HEAD에 대한 셀프 리뷰 리포트(필수 0건)도 있어야 한다
- gh api 로 머지하는 우회 경로 금지

명령 문자열을 정규식으로 훑지 않고 셸 토큰 단위로 파싱한다. `git -C <path>`, `VAR=x git`,
`(git ...)`, `a & git ...`, 따옴표 안의 연산자 같은 변형에서 규칙이 빠지지 않게 하기 위해서다.
차단 시 exit 2 + stderr 메시지 → Claude에게 그대로 전달된다. 내부 오류도 차단(fail closed)한다.
"""

import json
import os
import re
import shlex
import subprocess
import sys

COMMIT_TYPES = "feat|fix|refactor|test|docs|chore|build|ci|cd|perf|style|init"
SUBJECT_RE = re.compile(rf"^({COMMIT_TYPES})(\([\w./-]+\))?!?: \S.*$")
MAX_SUBJECT = 72
QA_MARKER_RE = re.compile(r"<!--\s*qa-report\s+head=([0-9a-f]{7,40})\s+verdict=(PASS|FAIL)\s*-->")
REVIEW_MARKER_RE = re.compile(r"<!--\s*self-review\s+pr=(\d+)\s+head=([0-9a-f]{7,40})\s+required_open=(\d+)\s*-->")
# 브랜치 전략: 기능 브랜치 → PR → develop, 릴리스는 develop → PR → main (main 푸시가 CD를 트리거)
INTEGRATION_BRANCH = "develop"
RELEASE_BRANCH = "main"
PROTECTED_BRANCHES = {RELEASE_BRANCH, INTEGRATION_BRANCH}
HEREDOC_RE = re.compile(r"<<-?\s*(['\"]?)([^\s'\"<>|&;()]+)\1[^\n]*\n(.*?)\n[ \t]*\2(?=\s|$|\))", re.S)
OPERATORS = {"&&", "||", ";", "|", "&", "(", ")", "\n", ";;", "|&"}
# 값을 별도 토큰으로 받는 전역 옵션
GIT_GLOBAL_WITH_ARG = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env"}
GH_GLOBAL_WITH_ARG = {"-R", "--repo", "--hostname"}
# 명령 앞에 붙어도 실제 명령은 그 뒤에 오는 토큰들
WRAPPERS = {"command", "env", "exec", "time", "nohup", "builtin", "sudo", "{", "}", "!", "if", "then", "else", "elif",
            "do", "while", "until", "fi", "done"}
SHELLS = {"bash", "sh", "zsh", "dash"}
# 뒤에 오는 명령을 실행하는 래퍼 (옵션은 건너뛴다). timeout 은 기간 인자를 하나 더 받는다
RUNNERS = {"nice", "ionice", "stdbuf", "xargs", "timeout", "nohup", "chronic", "unbuffer"}
RUNNER_OPTS_WITH_ARG = {"-n", "-c", "-p", "-I", "-i", "-o", "-e", "-d", "-P", "-L", "-s", "-a", "-E", "-k", "--signal",
                        "--kill-after", "--adjustment", "--max-args", "--max-procs", "--delimiter", "--arg-file"}
# gh api graphql 에서 허용하는 mutation (리뷰 스레드 답글/해결만)
ALLOWED_MUTATIONS = {"resolveReviewThread", "unresolveReviewThread", "addPullRequestReviewThreadReply"}
UNKNOWN = object()  # 이 명령 안에서 브랜치가 바뀌었지만 어디로 바뀌었는지 알 수 없음


class Blocked(Exception):
    pass


def block(msg):
    raise Blocked(msg)


def git(*args, cwd):
    try:
        out = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", timeout=10)
        return out.stdout.strip() if out.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def tokenize(command):
    """heredoc 본문을 자리표시자로 바꾸고, 셸 연산자 단위로 명령을 나눈다."""
    bodies = []

    def stash(m):
        bodies.append(m.group(3))
        return f"<<__HEREDOC{len(bodies) - 1}__"

    code = HEREDOC_RE.sub(stash, command)
    lex = shlex.shlex(code, posix=True, punctuation_chars="();<>|&\n")
    lex.whitespace = " \t\r"
    lex.whitespace_split = True
    lex.commenters = ""
    try:
        tokens = list(lex)
    except ValueError:
        # 따옴표가 닫히지 않은 명령 — 셸도 실행하지 못하지만, 보수적으로 공백 단위로 나눈다
        tokens = re.findall(r"&&|\|\||[;|&()\n]|[^\s;|&()]+", code)

    segments, current = [], []
    for tok in tokens:
        if tok in OPERATORS:
            if current:
                segments.append(current)
            current = []
        else:
            current.append(tok)
    if current:
        segments.append(current)
    return segments, bodies


def strip_prefix(seg):
    """VAR=x, env/command 래퍼를 걷어낸다."""
    i = 0
    while i < len(seg):
        tok = seg[i]
        prev = seg[i - 1] if i else ""
        if re.match(r"^[A-Za-z_]\w*=", tok) or tok in WRAPPERS or (prev in ("env", "time", "sudo") and tok.startswith("-")):
            i += 1
            continue
        if program(tok) in RUNNERS:
            runner = program(tok)
            i += 1
            while i < len(seg) and seg[i].startswith("-"):
                i += 2 if seg[i] in RUNNER_OPTS_WITH_ARG else 1
            if runner == "timeout" and i < len(seg):
                i += 1  # 기간 (예: 30s)
            continue
        break
    return seg[i:]


def program(tok):
    name = re.split(r"[\\/]", tok)[-1].lower()
    return name[:-4] if name.endswith(".exe") else name


def split_globals(args, with_arg):
    """전역 옵션을 건너뛰고 (옵션 목록, 서브커맨드, 나머지 인자)를 돌려준다."""
    opts, i = [], 0
    while i < len(args) and args[i].startswith("-"):
        opt = args[i]
        if opt in with_arg and i + 1 < len(args):
            opts.append((opt, args[i + 1]))
            i += 2
        else:
            opts.append((opt.split("=", 1)[0], opt.split("=", 1)[1] if "=" in opt else None))
            i += 1
    if i >= len(args):
        return opts, None, []
    return opts, args[i], args[i + 1:]


def short_flags(arg):
    """`-am` 같은 짧은 옵션 묶음이면 문자 집합, 아니면 빈 문자열."""
    return arg[1:] if re.match(r"^-[A-Za-z]+$", arg) else ""


def resolve_message(value, bodies):
    m = re.search(r"<<__HEREDOC(\d+)__", value)
    if m:
        value = bodies[int(m.group(1))]
    for ln in value.splitlines():
        if ln.strip():
            return ln.strip()
    return ""


def commit_subject(args, bodies, cwd):
    """커밋 인자에서 제목 줄을 뽑는다. 메시지를 지정하지 않았으면 None."""
    i = 0
    while i < len(args):
        a = args[i]
        nxt = args[i + 1] if i + 1 < len(args) else ""
        if a in ("-m", "--message"):
            return resolve_message(nxt, bodies)
        if a.startswith("--message="):
            return resolve_message(a.split("=", 1)[1], bodies)
        if a in ("-F", "--file") or a.startswith("--file="):
            path = a.split("=", 1)[1] if "=" in a else nxt
            if path == "-":
                # stdin 메시지: 같은 명령의 heredoc 이면 그 본문, 파이프면 내용을 알 수 없으므로 차단
                doc = next((t for t in args if re.match(r"^__HEREDOC\d+__$", t)), None)
                if doc:
                    return resolve_message(f"<<{doc}", bodies)
                block("git commit -F - 로 파이프된 메시지는 검사할 수 없습니다. -m \"$(cat <<'EOF' ... EOF)\" 형식을 쓰세요.")
            try:
                with open(os.path.join(cwd, path), encoding="utf-8") as fh:
                    return resolve_message(fh.read(), bodies)
            except OSError:
                return None
        if re.match(r"^-[A-Za-z]+", a) and not a.startswith("--") and "m" in a:
            # -am "msg", -mmsg, -amfix: ...
            idx = a.index("m")
            rest = a[idx + 1:]
            return resolve_message(rest if rest else nxt, bodies)
        i += 1
    return None


def read_marker(path, pattern):
    try:
        with open(path, encoding="utf-8") as fh:
            return pattern.search(fh.read())
    except OSError:
        return None


def same_sha(a, b):
    return bool(a and b) and (a.startswith(b) or b.startswith(a))


def expected_base(head_branch):
    return RELEASE_BRANCH if head_branch == INTEGRATION_BRANCH else INTEGRATION_BRANCH


def strategy_msg():
    return f"(기능 → {INTEGRATION_BRANCH}, 릴리스 {INTEGRATION_BRANCH} → {RELEASE_BRANCH})"


def pr_info(selector, cwd):
    """gh pr view 로 PR 의 번호/base/head/head SHA 를 조회한다. 조회 실패 시 None.

    테스트에서는 GUARD_TEST_PR_JSON (선택자 → PR 정보 JSON 파일)로 대체한다.
    """
    fake = os.environ.get("GUARD_TEST_PR_JSON")
    if fake:
        with open(fake, encoding="utf-8") as fh:
            return json.load(fh).get(selector or "current")
    cmd = ["gh", "pr", "view", *([selector] if selector else []), "--json", "number,baseRefName,headRefName,headRefOid"]
    try:
        out = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8", timeout=20)
        return json.loads(out.stdout) if out.returncode == 0 else None
    except (OSError, subprocess.SubprocessError, ValueError):
        return None


def require_qa(cwd, head=None):
    head = head or git("rev-parse", "HEAD", cwd=cwd)
    qa = read_marker(os.path.join(cwd, "_workspace", "qa-report.md"), QA_MARKER_RE)
    if not qa:
        block("자가 QA 리포트(_workspace/qa-report.md)가 없습니다. qa-inspector 에이전트로 QA 를 먼저 실행하세요.")
    if not same_sha(qa.group(1), head):
        block(f"QA 리포트가 현재 HEAD({head[:7]})가 아니라 {qa.group(1)[:7]} 기준입니다. 바뀐 코드로 QA 를 다시 실행하세요.")
    if qa.group(2) != "PASS":
        block("QA 리포트 판정이 FAIL 입니다. BLOCKER 를 고치고 QA 를 다시 실행하세요.")
    return head


def require_review(cwd, head, pr_number):
    rv = read_marker(os.path.join(cwd, "_workspace", "review-report.md"), REVIEW_MARKER_RE)
    if not rv:
        block("셀프 리뷰 리포트(_workspace/review-report.md)가 없습니다. pr-reviewer 에이전트로 리뷰를 먼저 받으세요.")
    if pr_number and rv.group(1) != pr_number:
        block(f"셀프 리뷰 리포트는 PR #{rv.group(1)} 기준입니다. PR #{pr_number} 에 대한 리뷰가 필요합니다.")
    if not same_sha(rv.group(2), head):
        block(f"셀프 리뷰가 현재 HEAD({head[:7]})가 아니라 {rv.group(2)[:7]} 기준입니다. 반영 커밋 이후 재리뷰가 필요합니다.")
    if int(rv.group(3)) > 0:
        block(f"셀프 리뷰의 (필수) 코멘트가 {rv.group(3)}건 남아 있습니다. 반영하거나 사유를 남긴 뒤 재리뷰하세요.")


def check_git(opts, sub, args, state, bodies):
    cwd = state["cwd"]
    for opt, val in opts:
        if opt == "-C" and val:
            cwd = os.path.normpath(os.path.join(cwd, val))
    branch = state["branch"].get(cwd)
    if branch is None:
        branch = git("rev-parse", "--abbrev-ref", "HEAD", cwd=cwd) if os.path.isdir(cwd) else ""
        if not branch:
            # -C 경로를 해석할 수 없으면 프로젝트 저장소 기준으로 판단한다 (규칙이 빠지는 쪽보다 낫다)
            cwd = state["cwd"]
            branch = state["branch"].get(cwd) or git("rev-parse", "--abbrev-ref", "HEAD", cwd=cwd)
        state["branch"][cwd] = branch

    # git 은 모호하지 않은 옵션 약어(--no-veri)도 받아들인다
    if any(len(a) >= 6 and "--no-verify".startswith(a) for a in args):
        block("--no-verify 는 금지입니다. hook 이 실패하면 원인을 고치세요.")

    if sub in ("switch", "checkout"):
        create = None
        positional = []
        i = 0
        while i < len(args):
            a = args[i]
            if a in ("-c", "-C", "--create", "--force-create", "-b", "-B", "--orphan") and i + 1 < len(args):
                create = args[i + 1]
                i += 2
                continue
            if a == "--":
                break
            if not a.startswith("-") or a == "-":
                positional.append(a)
            i += 1
        if create:
            state["branch"][cwd] = create
        elif positional:
            target = positional[0]
            if target == "-" or "--detach" in args:
                state["branch"][cwd] = UNKNOWN
            elif sub == "switch" or git("rev-parse", "--verify", "--quiet", f"refs/heads/{target}", cwd=cwd):
                state["branch"][cwd] = target
        return

    if sub in ("merge", "cherry-pick", "revert", "am", "rebase") and branch in PROTECTED_BRANCHES:
        if not (sub == "merge" and "--ff-only" in args):
            block(f"'{branch}' 브랜치에서 로컬 {sub} 는 금지입니다. 기능 브랜치에서 작업하고 PR 로 반영하세요.")

    if sub == "add":
        for a in args:
            if a in ("-A", "--all", "-u", "--update", ".", ":/", ":", "*", "./") or re.match(r"^-[A-Za-z]*[Au][A-Za-z]*$", a):
                block("일괄 스테이징(git add -A / . / -u)은 금지입니다. 커밋 단위별로 경로를 지정해 스테이징하세요.")
        return

    if sub == "commit":
        if any("n" in short_flags(a) for a in args):
            block("git commit -n(--no-verify)은 금지입니다.")
        if branch is UNKNOWN:
            block("같은 명령 안에서 브랜치를 바꾼 뒤 커밋하고 있어 대상 브랜치를 알 수 없습니다. 명령을 나눠 실행하세요.")
        if branch in PROTECTED_BRANCHES:
            block(f"'{branch}' 브랜치에 직접 커밋할 수 없습니다. `git switch -c feat/<주제> {INTEGRATION_BRANCH}` 로 브랜치를 먼저 만드세요.")
        if "--amend" in args and "--no-edit" in args:
            return
        subject = commit_subject(args, bodies, cwd)
        if subject is None:
            return
        if not SUBJECT_RE.match(subject):
            block(f"커밋 제목 '{subject}' 이(가) 컨벤션에 맞지 않습니다. 형식: `<type>: <한국어 요약>` (type: {COMMIT_TYPES.replace('|', ', ')})")
        if len(subject) > MAX_SUBJECT:
            block(f"커밋 제목이 {len(subject)}자입니다({MAX_SUBJECT}자 이하). 커밋을 더 작게 나누세요.")
        return

    if sub == "push":
        protected_msg = f"{RELEASE_BRANCH}/{INTEGRATION_BRANCH} 에 직접 푸시할 수 없습니다. 기능 브랜치를 푸시하고 PR 을 여세요."
        if any(a in ("--all", "--mirror", "--branches") for a in args):
            block("git push --all / --mirror 는 보호 브랜치를 포함하므로 금지입니다. 브랜치를 지정해 푸시하세요.")
        positional, i = [], 0
        repo_given = any(a == "--repo" or a.startswith("--repo=") for a in args)
        while i < len(args):
            a = args[i]
            if a in ("-o", "--push-option", "--repo", "--receive-pack", "--exec") and i + 1 < len(args):
                i += 2
                continue
            if not a.startswith("-"):
                positional.append(a)
            i += 1
        # --repo 로 원격을 지정하면 위치 인자는 모두 refspec 이다
        refspecs = positional if repo_given else positional[1:]
        tags_only = "--tags" in args or "--follow-tags" in args
        if not refspecs:
            if tags_only and "--follow-tags" not in args:
                return
            if branch is UNKNOWN or branch in PROTECTED_BRANCHES:
                block(protected_msg)
            return
        for spec in refspecs:
            spec = spec.lstrip("+")
            dst = spec.split(":", 1)[1] if ":" in spec else spec
            if dst in ("HEAD", "@"):
                dst = branch
            if dst is UNKNOWN:
                block("푸시 대상 브랜치를 알 수 없습니다. 명령을 나눠 실행하세요.")
            dst = re.sub(r"^refs/heads/", "", dst or "")
            if dst in PROTECTED_BRANCHES:
                block(protected_msg)


def option_values(args, names):
    """`-X v`, `-Xv`(짧은 옵션), `--name=v` 형태의 값을 모두 모은다."""
    values, i = [], 0
    while i < len(args):
        a = args[i]
        for n in names:
            if a == n and i + 1 < len(args):
                values.append(args[i + 1])
                i += 1
                break
            if n.startswith("--") and a.startswith(n + "="):
                values.append(a.split("=", 1)[1])
                break
            if not n.startswith("--") and a.startswith(n) and len(a) > len(n):
                values.append(a[len(n):])
                break
        i += 1
    return values


def check_gh_api(args, bodies):
    """gh api 는 리뷰/코멘트 읽기·쓰기만 허용한다. 본문(-f body=...) 텍스트는 검사하지 않는다."""
    gate_msg = "gh api 로 머지하거나 브랜치를 직접 바꾸는 것은 금지입니다. 셀프 리뷰 게이트를 거치도록 `gh pr merge` 를 사용하세요."
    value_opts = {"-X", "--method", "-f", "--raw-field", "-F", "--field", "-H", "--header", "--input", "-q", "--jq",
                  "-t", "--template", "--hostname", "--cache", "-p", "--preview"}
    positional, i = [], 0
    while i < len(args):
        a = args[i]
        if a in value_opts:
            i += 2
            continue
        if not a.startswith("-"):
            positional.append(a)
        i += 1
    endpoint = positional[0] if positional else ""
    fields = option_values(args, ["-f", "--raw-field", "-F", "--field"])
    methods = [m.upper() for m in option_values(args, ["-X", "--method"])]
    method = methods[-1] if methods else ("POST" if fields or "--input" in args else "GET")

    if endpoint == "graphql":
        if "--input" in args:
            block("gh api graphql --input 은 검사할 수 없어 금지입니다. -f query='...' 로 쿼리를 명령에 직접 쓰세요.")
        queries = [f.split("=", 1)[1] for f in fields if f.startswith("query=")]
        if not queries:
            block("gh api graphql 은 -f query='...' 로 쿼리를 직접 지정해야 합니다.")
        for q in queries:
            if q.startswith("@") or "__HEREDOC" in q or "$(" in q or "`" in q or q.endswith("$") or not q.strip():
                block("gh api graphql 쿼리는 파일/치환이 아니라 명령에 직접 써야 검사할 수 있습니다.")
            if re.search(r"\bmutation\b", q):
                # 인자를 받는 모든 필드 이름을 허용 목록과 대조한다 (작업 이름 `mutation Foo(...)` 는 제외)
                # GraphQL 은 쉼표를 공백으로 취급한다 (`updateRef,(...)` 도 유효)
                names = set(re.findall(r"(\w+)\s*\(", re.sub(r"\bmutation\b\s*\w*", "", q.replace(",", " "))))
                bad = names - ALLOWED_MUTATIONS
                if not names or bad:
                    block(f"허용되지 않은 GraphQL mutation 입니다({', '.join(sorted(bad)) or '알 수 없음'}). "
                          f"허용: {', '.join(sorted(ALLOWED_MUTATIONS))}")
        return

    path = endpoint.split("?", 1)[0]
    if method == "GET":
        return
    if re.search(r"/pulls/\d+/merge\b|/merges\b|/git/refs\b|/git/commits\b|/contents/|/pulls/\d+/update-branch", path):
        block(gate_msg)
    if re.search(r"/pulls/\d+$", path) and any(f.startswith("base=") for f in fields):
        block(f"gh api 로 PR base 를 바꾸는 것은 금지입니다. `gh pr edit --base` 를 사용하세요 {strategy_msg()}.")
    if re.search(r"/branches/[^/]+/protection|/rulesets", path) or re.fullmatch(r"repos/[^/]+/[^/]+", path.strip("/")):
        block("브랜치 보호·저장소 설정 변경은 사용자가 결정할 일입니다. 필요하면 사용자에게 `! gh api ...` 로 직접 실행을 요청하세요.")


def check_gh(sub, args, state, bodies):
    cwd = state["cwd"]
    if sub == "api":
        check_gh_api(args, bodies)
        return
    if sub != "pr" or not args:
        return
    action, rest = args[0], args[1:]
    # 위치 인자: PR 번호, URL, 브랜치 이름
    selector = next((a for a in rest if not a.startswith("-")), None)
    if selector:
        m = re.match(r"^#?(\d+)$", selector) or re.search(r"/pull/(\d+)", selector)
        selector = m.group(1) if m else selector

    if action in ("create", "new"):
        heads = option_values(rest, ["-H", "--head"])
        head_branch = heads[-1].split(":")[-1] if heads else git("rev-parse", "--abbrev-ref", "HEAD", cwd=cwd)
        bases = option_values(rest, ["-B", "--base"])
        expected = expected_base(head_branch)
        if not bases:
            block(f"gh pr create 에 --base {expected} 를 명시하세요 {strategy_msg()}.")
        if bases[-1] != expected:
            block(f"'{head_branch}' 의 PR base 는 {expected} 여야 합니다 {strategy_msg()}.")
        if heads and head_branch != git("rev-parse", "--abbrev-ref", "HEAD", cwd=cwd):
            block(f"--head {head_branch} 는 현재 브랜치가 아닙니다. QA 리포트는 현재 HEAD 기준이므로 해당 브랜치로 전환해 PR 을 여세요.")
        require_qa(cwd)

    elif action == "edit":
        bases = option_values(rest, ["-B", "--base"])
        if bases:
            pr = pr_info(selector, cwd)
            if not pr:
                block("PR 정보를 조회할 수 없어 base 변경을 검사할 수 없습니다.")
            if bases[-1] != expected_base(pr["headRefName"]):
                block(f"PR #{pr['number']}({pr['headRefName']}) 의 base 는 {expected_base(pr['headRefName'])} 여야 합니다 {strategy_msg()}.")

    elif action == "merge":
        pr = pr_info(selector, cwd)
        if not pr:
            block("머지할 PR 정보를 조회할 수 없어 게이트를 검사할 수 없습니다. PR 번호를 지정하세요.")
        if pr["baseRefName"] != expected_base(pr["headRefName"]):
            block(f"PR #{pr['number']} 은 {pr['headRefName']} → {pr['baseRefName']} 입니다. 브랜치 전략 {strategy_msg()}에 맞지 않아 머지할 수 없습니다.")
        # QA/리뷰 리포트는 로컬 HEAD 가 아니라 실제로 머지될 PR head 기준이어야 한다
        head = require_qa(cwd, pr["headRefOid"])
        require_review(cwd, head, str(pr["number"]))


def check(command, cwd, depth=0):
    segments, bodies = tokenize(command)
    state = {"cwd": cwd, "branch": {}}
    for seg in segments:
        seg = strip_prefix(seg)
        if not seg:
            continue
        prog = program(seg[0])
        # bash -c '...', bash -lc, bash <<EOF, eval '...' 안의 명령도 같은 규칙으로 검사한다
        if depth < 3 and prog in SHELLS:
            idx = next((k for k, a in enumerate(seg[1:], 1) if re.match(r"^-[A-Za-z]*c[A-Za-z]*$", a)), None)
            if idx is not None and idx + 1 < len(seg):
                check(seg[idx + 1], cwd, depth + 1)
                continue
            docs = [int(m.group(1)) for t in seg for m in [re.match(r"^__HEREDOC(\d+)__$", t)] if m]
            for d in docs:
                check(bodies[d], cwd, depth + 1)
            if docs:
                continue
        if depth < 3 and prog == "eval":
            check(" ".join(seg[1:]), cwd, depth + 1)
            continue
        if prog == "git":
            opts, sub, args = split_globals(seg[1:], GIT_GLOBAL_WITH_ARG)
            if sub:
                check_git(opts, sub, args, state, bodies)
        elif prog == "gh":
            _, sub, args = split_globals(seg[1:], GH_GLOBAL_WITH_ARG)
            if sub:
                check_gh(sub, args, state, bodies)


def main():
    try:
        payload = json.loads(sys.stdin.buffer.read().decode("utf-8"))
        command = (payload.get("tool_input") or {}).get("command") or ""
        if not re.search(r"\b(git|gh)\b", command):
            return 0
        cwd = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
        check(command, cwd)
        return 0
    except Blocked as e:
        msg = str(e)
    except Exception as e:  # noqa: BLE001 — 가드가 조용히 꺼지는 것보다 막는 편이 낫다
        msg = f"hook 내부 오류로 명령을 차단했습니다({type(e).__name__}: {e}). .claude/hooks/guard_git.py 를 확인하세요."
    # Windows 콘솔 코드페이지(cp949)로 인코딩되면 Claude에게 깨진 문자열이 전달된다
    sys.stderr.buffer.write(f"[guard-git] {msg}\n".encode("utf-8"))
    sys.stderr.flush()
    return 2


if __name__ == "__main__":
    sys.exit(main())
