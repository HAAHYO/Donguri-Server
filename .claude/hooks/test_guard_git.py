"""guard_git.py 회귀 테스트. 실행: python .claude/hooks/test_guard_git.py

임시 git 저장소를 만들어 실제 hook 프로세스에 JSON을 넘기고 exit code를 확인한다.
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "guard_git.py")


def sh(*args, cwd):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True)


class GuardGitTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = self.tmp.name
        sh("git", "init", "-q", "-b", "main", cwd=self.repo)
        sh("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "init", cwd=self.repo)
        self.head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.repo, capture_output=True, text=True, check=True
        ).stdout.strip()
        os.makedirs(os.path.join(self.repo, "_workspace"))
        # gh pr view 대체: 선택자(번호/브랜치/current) → PR 정보
        pr = lambda n, head, base: {"number": n, "headRefName": head, "baseRefName": base, "headRefOid": self.head}  # noqa: E731
        self.prs = {"1": pr(1, "feat/x", "develop"), "current": pr(1, "feat/x", "develop"),
                    "2": pr(2, "feat/y", "develop"), "3": pr(3, "feat/z", "main"), "4": pr(4, "develop", "main")}
        self.pr_json = os.path.join(self.tmp.name, "prs.json")
        with open(self.pr_json, "w", encoding="utf-8") as fh:
            json.dump(self.prs, fh)

    def tearDown(self):
        self.tmp.cleanup()

    def run_hook(self, command):
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo, "GUARD_TEST_PR_JSON": self.pr_json}
        payload = json.dumps({"tool_name": "Bash", "tool_input": {"command": command}}).encode("utf-8")
        return subprocess.run([sys.executable, HOOK], input=payload, env=env, capture_output=True).returncode

    def feature_branch(self):
        sh("git", "switch", "-q", "-c", "feat/x", cwd=self.repo)

    def write(self, name, text):
        with open(os.path.join(self.repo, "_workspace", name), "w", encoding="utf-8") as fh:
            fh.write(text)

    def test_unrelated_command_passes(self):
        self.assertEqual(self.run_hook("ls -la"), 0)

    def test_commit_on_main_blocked(self):
        self.assertEqual(self.run_hook('git commit -m "feat: 기능 추가"'), 2)

    def test_valid_commit_on_feature_branch(self):
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit -m "feat: 헬스체크 API 추가"'), 0)

    def test_heredoc_commit(self):
        self.feature_branch()
        ok = "git commit -m \"$(cat <<'EOF'\nfix: 응답 상태 코드 수정\n\nCo-Authored-By: x <x@x>\nEOF\n)\""
        bad = "git commit -m \"$(cat <<'EOF'\n응답 상태 코드 수정\nEOF\n)\""
        self.assertEqual(self.run_hook(ok), 0)
        self.assertEqual(self.run_hook(bad), 2)

    def test_bad_commit_type_blocked(self):
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit -m "update stuff"'), 2)
        self.assertEqual(self.run_hook('git commit -m "feature: 추가"'), 2)

    def test_too_long_subject_blocked(self):
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit -m "feat: ' + "가" * 80 + '"'), 2)

    def test_bulk_add_and_no_verify_blocked(self):
        self.assertEqual(self.run_hook("git add ."), 2)
        self.assertEqual(self.run_hook("git add -A"), 2)
        self.assertEqual(self.run_hook("git add src/main/kotlin/A.kt"), 0)
        self.assertEqual(self.run_hook("git add ./src"), 0)
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit --no-verify -m "feat: 추가"'), 2)

    def test_push_to_main_blocked(self):
        self.assertEqual(self.run_hook("git push"), 2)
        self.feature_branch()
        self.assertEqual(self.run_hook("git push origin main"), 2)
        self.assertEqual(self.run_hook("git push origin HEAD:main"), 2)
        self.assertEqual(self.run_hook("git push -u origin feat/x"), 0)
        self.assertEqual(self.run_hook("git push -u origin feat/main-page"), 0)

    def test_heredoc_body_is_not_a_command(self):
        cmd = "python - <<'EOF'\ndoc = '`git add -A`, `--no-verify`, git push origin main 차단'\nEOF\ngit status"
        self.assertEqual(self.run_hook(cmd), 0)

    def test_block_message_is_utf8(self):
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo}
        payload = json.dumps({"tool_input": {"command": "git add ."}}).encode("utf-8")
        err = subprocess.run([sys.executable, HOOK], input=payload, env=env, capture_output=True).stderr
        self.assertIn("금지", err.decode("utf-8"))

    def test_compound_command_checked(self):
        self.assertEqual(self.run_hook('cd /tmp && git commit -m "feat: 추가"'), 2)

    def test_every_commit_in_command_is_checked(self):
        self.feature_branch()
        ok = "git commit -m \"$(cat <<'EOF'\nfeat: 첫 커밋\nEOF\n)\" && git commit -m \"$(cat <<'EOF'\ndocs: 둘째 커밋\nEOF\n)\""
        bad = "git commit -m \"$(cat <<'EOF'\nfeat: 첫 커밋\nEOF\n)\" && git commit -m \"$(cat <<'EOF'\n둘째 커밋\nEOF\n)\""
        self.assertEqual(self.run_hook(ok), 0)
        self.assertEqual(self.run_hook(bad), 2)

    # --- QA 라운드 1에서 발견된 우회/오탐 사례 ---

    def test_git_global_options_and_prefixes_do_not_bypass(self):
        for cmd in [
            f'git -C {self.repo.replace(os.sep, "/")} commit -m "feat: 추가"',
            'git -C C:\\no\\such\\dir commit -m "feat: 추가"',
            'git -C . push origin main',
            'git -c core.editor=true commit -m "feat: 추가"',
            'git --no-pager commit -m "feat: 추가"',
            'GIT_TRACE=1 git commit -m "feat: 추가"',
            'env FOO=1 git commit -m "feat: 추가"',
            '(git commit -m "feat: 추가")',
            'ls & git commit -m "feat: 추가"',
            'echo x | git commit -F -',
        ]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)

    def test_gh_api_merge_blocked(self):
        self.feature_branch()
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=0 -->\n")
        for cmd in [
            "gh api repos/o/r/pulls/1/merge -X PUT",
            "gh api -X POST repos/o/r/merges -f base=main -f head=feat/x",
            "gh api graphql -f query='mutation{mergePullRequest(input:{pullRequestId:\"x\"}){clientMutationId}}'",
        ]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)
        self.assertEqual(self.run_hook("gh api repos/o/r/pulls/1/reviews --input p.json"), 0)

    def test_gh_prefixes_do_not_bypass_gates(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("GH_TOKEN=x gh pr create --base main --fill"), 2)
        self.assertEqual(self.run_hook("gh -R o/r pr create --fill"), 2)

    def test_review_marker_must_match_pr_number(self):
        self.feature_branch()
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 2 --squash"), 2)
        self.assertEqual(self.run_hook("gh pr merge --squash"), 0)

    def test_commit_message_forms(self):
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit -am "bad subject"'), 2)
        self.assertEqual(self.run_hook('git commit -m"bad subject"'), 2)
        self.assertEqual(self.run_hook('git commit --message="bad subject"'), 2)
        self.assertEqual(self.run_hook('git commit -am "fix: 수정"'), 0)
        self.assertEqual(self.run_hook('git commit --message="fix: 수정"'), 0)
        self.assertEqual(self.run_hook('git commit -n -m "fix: 수정"'), 2)

    def test_push_refspec_forms(self):
        self.feature_branch()
        for cmd in ["git push origin HEAD:refs/heads/main", 'git push origin "main"', "git push origin +feat/x:main",
                    "git push origin --delete main", "git push --all origin"]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)
        sh("git", "switch", "-q", "main", cwd=self.repo)
        self.assertEqual(self.run_hook("git push origin HEAD --tags"), 2)
        self.assertEqual(self.run_hook("git push origin --tags"), 0)
        self.assertEqual(self.run_hook("git push origin v1.0.0"), 0)

    def test_quoted_operators_and_text_are_not_commands(self):
        self.feature_branch()
        self.assertEqual(self.run_hook('git commit -m "docs: --no-verify 와 git add . 금지 규칙 문서화"'), 0)
        self.assertEqual(self.run_hook('git commit -m "fix: a && b 처리"'), 0)
        self.assertEqual(self.run_hook('echo "git push origin main"'), 0)

    def test_commit_subject_not_taken_from_later_heredoc(self):
        self.feature_branch()
        cmd = "git commit -m \"bad subject\" && python - <<'EOF'\nfeat: 이건 커밋 메시지가 아님\nEOF"
        self.assertEqual(self.run_hook(cmd), 2)

    def test_switch_variants(self):
        self.assertEqual(self.run_hook('git switch -q -c feat/y && git commit -m "feat: 추가"'), 0)
        self.assertEqual(self.run_hook('git switch -c feat/y origin/main && git commit -m "feat: 추가"'), 0)
        self.feature_branch()
        self.assertEqual(self.run_hook('git switch - && git commit -m "feat: 추가"'), 2)
        self.assertEqual(self.run_hook('git checkout -- a.kt && git commit -m "feat: 추가"'), 0)

    def test_bulk_add_variants(self):
        for cmd in ["git add -Av", "git add :/", "git add -u", "git add --all src"]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)

    def test_internal_error_fails_closed(self):
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo}
        out = subprocess.run([sys.executable, HOOK], input=b"{not json", env=env, capture_output=True)
        self.assertEqual(out.returncode, 2)

    # --- develop 브랜치 전략 ---

    def test_develop_is_protected(self):
        sh("git", "switch", "-q", "-c", "develop", cwd=self.repo)
        self.assertEqual(self.run_hook('git commit -m "feat: 추가"'), 2)
        self.assertEqual(self.run_hook("git push"), 2)
        self.feature_branch()
        self.assertEqual(self.run_hook("git push origin HEAD:develop"), 2)

    def test_pr_base_must_follow_branch_strategy(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("gh pr create --fill"), 2)
        self.assertEqual(self.run_hook("gh pr create --base main --fill"), 2)
        self.assertEqual(self.run_hook("gh pr create -B develop --fill"), 0)
        self.assertEqual(self.run_hook("gh pr create --base=develop --fill"), 0)
        sh("git", "switch", "-q", "-c", "develop", cwd=self.repo)
        self.assertEqual(self.run_hook("gh pr create --base develop --fill"), 2)
        self.assertEqual(self.run_hook("gh pr create --base main --fill"), 0)

    # --- QA 라운드 2에서 발견된 우회/오탐 사례 ---

    def test_graphql_only_allows_review_thread_mutations(self):
        ok = [
            "gh api graphql -f query='query($pr:Int!){repository(owner:\"o\",name:\"r\"){pullRequest(number:$pr){id}}}' -F pr=1",
            "gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -f id=X",
        ]
        bad = [
            "gh api graphql -F query=@_workspace/q.graphql",
            "gh api graphql --input q.json",
            'gh api graphql -f query="$(cat q)"',
            "gh api graphql -f query=$(cat q)",
            "gh api graphql -f query='mutation{updateRef(input:{refId:\"x\",oid:\"y\"}){clientMutationId}}'",
            "gh api graphql -f query='mutation{createCommitOnBranch(input:{}){commit{oid}}}'",
            "gh api graphql -f query='mutation{a:resolveReviewThread(input:{}){x} b:mergePullRequest(input:{}){x}}'",
        ]
        for cmd in ok:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 0)
        for cmd in bad:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)

    def test_gh_api_rest_writes_to_refs_blocked(self):
        self.assertEqual(self.run_hook("gh api -X PATCH repos/o/r/git/refs/heads/main -f sha=x"), 2)
        self.assertEqual(self.run_hook("gh api repos/o/r/git/refs -f ref=refs/heads/main -f sha=x"), 2)
        self.assertEqual(self.run_hook("gh api repos/o/r/git/refs/heads/main"), 0)

    def test_reply_body_mentioning_merge_is_allowed(self):
        cmd = "gh api repos/o/r/pulls/1/comments/123/replies -f body='/merges 와 mergeBranch, pulls/1/merge 경로를 막았어요'"
        self.assertEqual(self.run_hook(cmd), 0)

    def test_shell_wrappers_and_compound_syntax_checked(self):
        for cmd in [
            "bash -c 'git commit -m \"feat: 추가\"'",
            "sh -c \"git push origin main\"",
            "eval git commit -m 'feat: 추가'",
            "{ git commit -m 'feat: 추가'; }",
            "if true; then git commit -m 'feat: 추가'; fi",
            "! git commit -m 'feat: 추가'",
            "time -p git commit -m 'feat: 추가'",
        ]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)

    def test_more_git_bypass_forms(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("git push --repo origin main"), 2)
        self.assertEqual(self.run_hook('git commit --no-veri -m "feat: 추가"'), 2)
        self.assertEqual(self.run_hook("printf 'bad\\n' | git commit -F -"), 2)
        self.assertEqual(self.run_hook("git commit -F - <<'EOF'\nbad subject\nEOF"), 2)
        self.assertEqual(self.run_hook("git commit -F - <<'EOF'\nfix: 수정\nEOF"), 0)

    def test_pr_merge_by_url_checks_number(self):
        self.feature_branch()
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge https://github.com/o/r/pull/2"), 2)
        self.assertEqual(self.run_hook("gh pr merge https://github.com/o/r/pull/1"), 0)

    # --- QA 라운드 3에서 발견된 우회/오탐 사례 ---

    def test_merge_checks_pr_base_against_strategy(self):
        self.write("review-report.md", f"<!-- self-review pr=3 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 3"), 2)  # feat/z → main
        self.write("review-report.md", f"<!-- self-review pr=4 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 4"), 0)  # 릴리스 develop → main

    def test_pr_edit_base_follows_strategy(self):
        self.assertEqual(self.run_hook("gh pr edit 1 --base main"), 2)
        self.assertEqual(self.run_hook("gh pr edit 1 --base develop"), 0)
        self.assertEqual(self.run_hook("gh pr edit 1 --title 'chore: 제목'"), 0)
        self.assertEqual(self.run_hook("gh api -X PATCH repos/o/r/pulls/1 -f base=main"), 2)

    def test_repo_settings_changes_blocked(self):
        self.assertEqual(self.run_hook("gh api -X DELETE repos/o/r/branches/main/protection"), 2)
        self.assertEqual(self.run_hook("gh api -X PATCH repos/o/r -f default_branch=main"), 2)
        self.assertEqual(self.run_hook("gh api repos/o/r/branches/main/protection"), 0)

    def test_graphql_commas_do_not_hide_mutations(self):
        q = "mutation{resolveReviewThread(input:{threadId:\"x\"}){thread{id}},updateRef,(input:{refId:\"r\",oid:\"o\"}){clientMutationId}}"
        self.assertEqual(self.run_hook(f"gh api graphql -f query='{q}'"), 2)

    def test_pr_create_respects_head_option(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("gh pr create --base develop --head feat/x --fill"), 0)
        self.assertEqual(self.run_hook("gh pr create --base develop --head develop --fill"), 2)  # develop 의 base 는 main

    def test_runner_wrappers_checked(self):
        for cmd in ["nice -n 5 git commit -m 'feat: 추가'", "timeout 30s git push origin main",
                    "echo x | xargs -0 git commit -m 'feat: 추가'", "bash -lc 'git push origin main'",
                    "bash <<'EOF'\ngit push origin main\nEOF"]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)

    def test_local_history_changes_on_protected_branch_blocked(self):
        for cmd in ["git merge feat/x", "git cherry-pick abc123", "git revert HEAD", "git rebase feat/x"]:
            with self.subTest(cmd=cmd):
                self.assertEqual(self.run_hook(cmd), 2)
        self.assertEqual(self.run_hook("git merge --ff-only origin/main"), 0)
        self.assertEqual(self.run_hook("git pull"), 0)

    def test_wrapper_without_python_blocks_only_git(self):
        wrapper = os.path.join(os.path.dirname(HOOK), "guard-git.sh")
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo, "GUARD_PYTHON": "no-such-python"}

        def run(cmd):
            payload = json.dumps({"tool_input": {"command": cmd}}).encode("utf-8")
            # Windows 의 CreateProcess 는 PATH 보다 System32(WSL bash)를 먼저 찾으므로 PATH 순서로 직접 찾는다
            bash = shutil.which("bash") or "bash"
            return subprocess.run([bash, wrapper], input=payload, env=env, capture_output=True).returncode

        self.assertEqual(run("ls -la"), 0)
        self.assertEqual(run("git status"), 2)
        env["GUARD_PYTHON"] = sys.executable
        self.assertEqual(run("git add ."), 2)
        self.assertEqual(run("git status"), 0)
        # 인터프리터 경로에 공백이 있어도 하나의 후보로 취급한다 (C:\Program Files\... 등)
        spaced = os.path.join(self.repo, "py dir", "python")
        os.makedirs(os.path.dirname(spaced))
        with open(spaced, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(f'#!/bin/sh\nexec "{sys.executable.replace(os.sep, "/")}" "$@"\n')
        os.chmod(spaced, 0o755)
        env["GUARD_PYTHON"] = spaced.replace(os.sep, "/")
        self.assertEqual(run("git status"), 0)

    def test_verify_hook_skips_hook_tests_without_python(self):
        verify = os.path.join(os.path.dirname(HOOK), "verify.sh")
        os.makedirs(os.path.join(self.repo, ".claude", "hooks"))
        with open(os.path.join(self.repo, ".claude", "hooks", "x.py"), "w", encoding="utf-8") as fh:
            fh.write("")
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo, "GUARD_PYTHON": "no-such-python"}

        def run():
            bash = shutil.which("bash") or "bash"
            return subprocess.run([bash, verify], input=b"{}", env=env, capture_output=True)

        # 인터프리터가 없으면 테스트 실패로 오인해 차단하지 않고 경고만 남긴다
        r = run()
        self.assertEqual(r.returncode, 0)
        self.assertIn("python", r.stdout.decode("utf-8"))
        # 실행 가능한 python 이 있으면 테스트를 실제로 돌린다 (임시 저장소엔 테스트 파일이 없어 실패 → 차단)
        env["GUARD_PYTHON"] = sys.executable
        self.assertEqual(run().returncode, 2)

    def test_heredoc_delimiter_with_dash(self):
        self.feature_branch()
        cmd = "python - <<'END-OF'\nprint('git push origin main')\nEND-OF\ngit status"
        self.assertEqual(self.run_hook(cmd), 0)

    def test_switch_in_same_command_is_tracked(self):
        self.assertEqual(self.run_hook('git switch -c feat/y && git add a.kt && git commit -m "feat: 추가"'), 0)
        self.feature_branch()
        self.assertEqual(self.run_hook('git switch main && git commit -m "feat: 추가"'), 2)

    def test_pr_create_does_not_require_qa_report(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("gh pr create --base develop --fill"), 0)

    def test_pr_merge_requires_clean_self_review(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 2)
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=2 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 2)
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
