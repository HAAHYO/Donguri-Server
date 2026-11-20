"""guard_git.py 회귀 테스트. 실행: python .claude/hooks/test_guard_git.py

임시 git 저장소를 만들어 실제 hook 프로세스에 JSON을 넘기고 exit code를 확인한다.
"""

import json
import os
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

    def tearDown(self):
        self.tmp.cleanup()

    def run_hook(self, command):
        env = {**os.environ, "CLAUDE_PROJECT_DIR": self.repo}
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
        self.write("qa-report.md", f"<!-- qa-report head={self.head} verdict=PASS -->\n")
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
        self.assertEqual(self.run_hook("GH_TOKEN=x gh pr create --fill"), 2)
        self.assertEqual(self.run_hook("gh -R o/r pr create --fill"), 2)

    def test_review_marker_must_match_pr_number(self):
        self.feature_branch()
        self.write("qa-report.md", f"<!-- qa-report head={self.head} verdict=PASS -->\n")
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

    def test_switch_in_same_command_is_tracked(self):
        self.assertEqual(self.run_hook('git switch -c feat/y && git add a.kt && git commit -m "feat: 추가"'), 0)
        self.feature_branch()
        self.assertEqual(self.run_hook('git switch main && git commit -m "feat: 추가"'), 2)

    def test_pr_create_requires_fresh_passing_qa(self):
        self.feature_branch()
        self.assertEqual(self.run_hook("gh pr create --fill"), 2)
        self.write("qa-report.md", "<!-- qa-report head=deadbeef verdict=PASS -->\n")
        self.assertEqual(self.run_hook("gh pr create --fill"), 2)
        self.write("qa-report.md", f"<!-- qa-report head={self.head[:7]} verdict=FAIL -->\n")
        self.assertEqual(self.run_hook("gh pr create --fill"), 2)
        self.write("qa-report.md", f"<!-- qa-report head={self.head[:7]} verdict=PASS -->\n")
        self.assertEqual(self.run_hook("gh pr create --fill"), 0)

    def test_pr_merge_requires_clean_self_review(self):
        self.feature_branch()
        self.write("qa-report.md", f"<!-- qa-report head={self.head} verdict=PASS -->\n")
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 2)
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=2 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 2)
        self.write("review-report.md", f"<!-- self-review pr=1 head={self.head} required_open=0 -->\n")
        self.assertEqual(self.run_hook("gh pr merge 1 --squash"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
