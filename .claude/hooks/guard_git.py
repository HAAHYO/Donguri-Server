"""PreToolUse(Bash) hook: git/gh 작업 규칙을 기계적으로 강제한다.

혼자 하는 프로젝트라 리뷰어가 없으므로, 사람이 지켜줄 규칙을 hook이 대신 지킨다.
- main 브랜치에서 커밋/푸시 금지 (모든 변경은 PR을 거친다)
- git add -A / git add . / --no-verify 금지
- 커밋 메시지 컨벤션 검사 (`<type>: <한국어 요약>`)
- gh pr create: 현재 HEAD에 대한 자가 QA 리포트(PASS)가 있어야 한다
- gh pr merge: 현재 HEAD에 대한 셀프 리뷰 리포트(필수 0건)도 있어야 한다

차단 시 exit 2 + stderr 메시지 → Claude에게 그대로 전달된다.
"""

import json
import os
import re
import subprocess
import sys

COMMIT_TYPES = "feat|fix|refactor|test|docs|chore|build|ci|cd|perf|style|init"
SUBJECT_RE = re.compile(rf"^({COMMIT_TYPES})(\([\w./-]+\))?!?: \S.*$")
MAX_SUBJECT = 72
QA_MARKER_RE = re.compile(r"<!--\s*qa-report\s+head=([0-9a-f]{7,40})\s+verdict=(PASS|FAIL)\s*-->")
REVIEW_MARKER_RE = re.compile(r"<!--\s*self-review\s+pr=(\d+)\s+head=([0-9a-f]{7,40})\s+required_open=(\d+)\s*-->")
PROTECTED_BRANCHES = {"main"}


def git(*args, cwd):
    try:
        out = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", timeout=10)
        return out.stdout.strip() if out.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def block(msg):
    # Windows 콘솔 코드페이지(cp949)로 인코딩되면 Claude에게 깨진 문자열이 전달된다
    sys.stderr.buffer.write(f"[guard-git] {msg}\n".encode("utf-8"))
    sys.stderr.flush()
    sys.exit(2)


HEREDOC_RE = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?[^\n]*\n(.*?)\n\s*\1\b", re.S)


def commit_subject(command, cwd):
    """커밋 명령에서 제목 줄을 뽑는다. 메시지를 지정하지 않았으면 None."""
    heredoc = HEREDOC_RE.search(command)
    if heredoc:
        lines = [ln.strip() for ln in heredoc.group(2).splitlines() if ln.strip()]
        return lines[0] if lines else ""
    m = re.search(r"(?:-m|--message)(?:\s+|=)(\"(?:[^\"\\]|\\.)*\"|'[^']*'|\S+)", command)
    if m:
        value = m.group(1)
        if value[:1] in "\"'":
            value = value[1:-1]
        return value.splitlines()[0].strip() if value.strip() else ""
    f = re.search(r"(?:-F|--file)(?:\s+|=)(\S+)", command)
    if f:
        try:
            with open(os.path.join(cwd, f.group(1).strip("\"'")), encoding="utf-8") as fh:
                for ln in fh:
                    if ln.strip():
                        return ln.strip()
        except OSError:
            return None
        return ""
    return None


def read_marker(path, pattern):
    try:
        with open(path, encoding="utf-8") as fh:
            return pattern.search(fh.read())
    except OSError:
        return None


def same_sha(a, b):
    return bool(a and b) and (a.startswith(b) or b.startswith(a))


def check(command, cwd):
    # heredoc 본문(커밋 메시지, 파일 내용)은 명령이 아니므로 규칙 검사에서 제외한다.
    # 여러 명령이 이어진 경우(&&, ;, 줄바꿈)에도 각 부분을 검사한다
    code = HEREDOC_RE.sub("<<HEREDOC", command)
    segments = re.split(r"&&|\|\||;|\n", code)
    branch = git("rev-parse", "--abbrev-ref", "HEAD", cwd=cwd)
    head = git("rev-parse", "HEAD", cwd=cwd)
    workspace = os.path.join(cwd, "_workspace")

    for seg in segments:
        s = seg.strip()

        # 같은 명령 안에서 먼저 브랜치를 바꾼 경우 이후 부분은 그 브랜치 기준으로 검사한다
        switched = re.match(r"git\s+(?:switch\s+(?:-c|-C|--create)|checkout\s+-[bB]|switch|checkout)\s+([\w./-]+)\s*$", s)
        if switched:
            branch = switched.group(1)
            continue

        if re.match(r"git\s", s) and re.search(r"\s--no-verify\b", s):
            block("--no-verify 는 금지입니다. hook이 실패하면 원인을 고치세요.")

        if re.match(r"git\s+add\b", s) and re.search(r"\s(-A|--all|\.)(\s|$)", s):
            block("git add -A / git add . 는 금지입니다. 커밋 단위별로 경로를 지정해 스테이징하세요.")

        if re.match(r"git\s+commit\b", s):
            if branch in PROTECTED_BRANCHES:
                block(f"'{branch}' 브랜치에 직접 커밋할 수 없습니다. `git switch -c feat/<주제>` 로 브랜치를 먼저 만드세요.")
            if "--amend" in s and "--no-edit" in s:
                continue
            # 원본 명령에서 이 커밋에 해당하는 위치부터 메시지를 찾는다 (한 명령에 커밋이 여러 개일 수 있음)
            starts = [m.start() for m in re.finditer(r"git\s+commit\b", command)]
            nth = sum(1 for prev in segments[: segments.index(seg)] if re.match(r"\s*git\s+commit\b", prev))
            start = starts[nth] if nth < len(starts) else 0
            end = starts[nth + 1] if nth + 1 < len(starts) else len(command)
            subject = commit_subject(command[start:end], cwd)
            if subject is None:
                continue
            if not SUBJECT_RE.match(subject):
                block(
                    f"커밋 제목 '{subject}' 이(가) 컨벤션에 맞지 않습니다. "
                    f"형식: `<type>: <한국어 요약>` (type: {COMMIT_TYPES.replace('|', ', ')})"
                )
            if len(subject) > MAX_SUBJECT:
                block(f"커밋 제목이 {len(subject)}자입니다({MAX_SUBJECT}자 이하). 커밋을 더 작게 나누세요.")

        if re.match(r"git\s+push\b", s):
            targets = re.findall(r"(?:^|\s|:)(main)(?:\s|$)", s.split("push", 1)[1])
            if targets or (branch in PROTECTED_BRANCHES and not re.search(r"\s(--tags|v\d)", s)):
                block("main 에 직접 푸시할 수 없습니다. 기능 브랜치를 푸시하고 PR 을 여세요.")

        if re.match(r"gh\s+pr\s+(create|merge)\b", s):
            qa = read_marker(os.path.join(workspace, "qa-report.md"), QA_MARKER_RE)
            if not qa:
                block("자가 QA 리포트(_workspace/qa-report.md)가 없습니다. qa-inspector 에이전트로 QA 를 먼저 실행하세요.")
            if not same_sha(qa.group(1), head):
                block(f"QA 리포트가 현재 HEAD({head[:7]})가 아니라 {qa.group(1)[:7]} 기준입니다. 바뀐 코드로 QA 를 다시 실행하세요.")
            if qa.group(2) != "PASS":
                block("QA 리포트 판정이 FAIL 입니다. BLOCKER 를 고치고 QA 를 다시 실행하세요.")

        if re.match(r"gh\s+pr\s+merge\b", s):
            rv = read_marker(os.path.join(workspace, "review-report.md"), REVIEW_MARKER_RE)
            if not rv:
                block("셀프 리뷰 리포트(_workspace/review-report.md)가 없습니다. pr-reviewer 에이전트로 리뷰를 먼저 받으세요.")
            if not same_sha(rv.group(2), head):
                block(f"셀프 리뷰가 현재 HEAD({head[:7]})가 아니라 {rv.group(2)[:7]} 기준입니다. 반영 커밋 이후 재리뷰가 필요합니다.")
            if int(rv.group(3)) > 0:
                block(f"셀프 리뷰의 (필수) 코멘트가 {rv.group(3)}건 남아 있습니다. 반영하거나 사유를 남긴 뒤 재리뷰하세요.")


def main():
    try:
        payload = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return
    command = (payload.get("tool_input") or {}).get("command") or ""
    if not re.search(r"\b(git|gh)\b", command):
        return
    cwd = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
    check(command, cwd)


if __name__ == "__main__":
    main()
