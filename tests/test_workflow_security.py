"""Static checks that the release workflow keeps the signing key out of logs, artifacts and the repository.

These read the workflow text; they cannot prove what GitHub does at run time, but they catch the usual
mistakes (echoing a secret, uploading the keystore, publishing during a dry run).
Run with: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / '.github' / 'workflows' / 'publish-single-apk.yml').read_text(encoding='utf-8')
ALLOWED_SECRETS = {'ONAY_KEYSTORE_B64', 'ONAY_KS_PASS', 'ONAY_KS_ALIAS'}
SECRET_VARS = ('ONAY_KS_PASS', 'KEYSTORE_PASSWORD', 'KEYSTORE_B64')


def publish_job_text():
    return WORKFLOW[WORKFLOW.index('\n  publish:'):]


def step(name_start):
    """Text of the step whose name starts with name_start (up to the next step)."""
    start = WORKFLOW.index('- name: ' + name_start)
    nxt = WORKFLOW.find('\n      - ', start + 1)
    return WORKFLOW[start:nxt if nxt != -1 else len(WORKFLOW)]


class WorkflowSecurityTest(unittest.TestCase):
    def test_secrets_are_only_read_into_env_variables(self):
        for line in WORKFLOW.splitlines():
            if 'secrets.' in line and 'github.token' not in line:
                self.assertRegex(line, r'^\s+[A-Z_0-9]+: \$\{\{ secrets\.[A-Z_0-9]+ \}\}$', line)

    def test_only_expected_secrets_are_used(self):
        used = set(re.findall(r'secrets\.([A-Z_0-9]+)', WORKFLOW))
        self.assertTrue(used <= ALLOWED_SECRETS, used - ALLOWED_SECRETS)

    def test_no_shell_tracing_or_debug_output(self):
        for pattern in (r'set\s+-[a-z]*x', r'bash\s+-[a-z]*x', r'sh\s+-[a-z]*x', r'ACTIONS_STEP_DEBUG',
                        r'ACTIONS_RUNNER_DEBUG', r'\bprintenv\b', r'^\s*env\s*$', r'\bset\s*$'):
            self.assertIsNone(re.search(pattern, WORKFLOW, re.M), pattern)

    def test_secret_values_are_never_printed(self):
        for line in WORKFLOW.splitlines():
            if re.search(r'\b(echo|cat|tee)\b', line):
                for var in SECRET_VARS:
                    self.assertNotRegex(line, r'\$\{?' + var + r'\b', line)

    def test_keystore_is_decoded_only_into_runner_temp(self):
        decodes = [ln for ln in WORKFLOW.splitlines() if 'base64 --decode' in ln]
        self.assertEqual(len(decodes), 1)
        self.assertIn('> "$RUNNER_TEMP/onay/signing.jks"', decodes[0])
        self.assertIn('chmod 600 "$RUNNER_TEMP/onay/signing.jks"', WORKFLOW)

    def test_keystore_variable_is_unset_after_decoding(self):
        self.assertIn('unset KEYSTORE_B64', WORKFLOW)

    def test_only_the_apk_is_uploaded_as_an_artifact(self):
        uploads = re.findall(r'uses: actions/upload-artifact@[0-9a-f]{40}.*?(?=\n      - |\Z)', WORKFLOW, re.S)
        self.assertEqual(len(uploads), 1)
        text = uploads[0]
        self.assertIn('path: ${{ env.APK_PATH }}', text)
        for bad in ('.jks', 'signing', 'onay/', 'RUNNER_TEMP', 'work/input'):
            self.assertNotIn(bad, text)
        days = int(re.search(r'retention-days: (\d+)', text).group(1))
        self.assertLessEqual(days, 7)

    def test_checkout_does_not_persist_credentials_in_publish_job(self):
        self.assertRegex(publish_job_text(), r'uses: actions/checkout@[0-9a-f]{40}[^\n]*\s+with:\s+persist-credentials: false')

    def test_signing_key_is_removed_even_when_a_step_fails(self):
        text = step('Remove signing artifacts from runner')
        self.assertIn('if: always()', text)
        self.assertIn('signing.jks', text)

    def test_dry_run_never_publishes(self):
        text = step('Create release and upload APK')
        self.assertIn('if: ${{ !inputs.dry_run }}', text)
        self.assertIn('if: ${{ !inputs.dry_run }}', step('Prepare release notes'))
        self.assertIn('if: ${{ inputs.dry_run }}', step('Keep the APK as a short-lived artifact'))

    def test_new_certificate_only_with_explicit_standalone_package_mode(self):
        text = step('Verify packaging and enforce explicit certificate policy')
        self.assertIn('[ -n "$src" ]', text)
        self.assertIn('if [ "$NEW_KEY" = "true" ]', text)
        self.assertIn('EXPECTED_PACKAGE=kz.onay.free bash scripts/verify_universal_apk.sh "$apk" |', text)
        self.assertIn('EXPECTED_PACKAGE=kz.onay.free bash scripts/verify_universal_apk.sh "$apk" "$src"', text)
        self.assertIn('ONAY_PACKAGE_NAME: kz.onay.free', WORKFLOW)
        self.assertIn('new_signing_key:', WORKFLOW)
        self.assertIn('default: false', WORKFLOW)

    def test_release_and_source_tags_are_validated(self):
        text = step('Check release tag and target')
        self.assertIn('Invalid release tag', text)
        self.assertIn('Invalid source tag', text)
        self.assertIn('Target and source tags must differ', text)
        self.assertIn('refusing to overwrite', text)

    def test_workflow_asks_for_minimal_permissions(self):
        block = re.search(r'^permissions:\n((?:  .*\n)+)', WORKFLOW, re.M).group(1)
        self.assertEqual(block.strip(), 'contents: write')

    def test_packaging_tests_run_before_publishing(self):
        self.assertIn('needs: checks', WORKFLOW)
        self.assertIn('python3 -m unittest discover -s tests -v', WORKFLOW)

    def test_gitignore_excludes_keys_and_build_output(self):
        ignore = (ROOT / '.gitignore').read_text(encoding='utf-8').split()
        for pattern in ('*.jks', '*.apk', '*.apks', 'work/'):
            self.assertIn(pattern, ignore)

    @unittest.skipUnless(shutil.which('git') and (ROOT / '.git').exists(), 'needs a git checkout')
    def test_no_key_material_is_tracked(self):
        tracked = subprocess.run(['git', 'ls-files'], cwd=ROOT, text=True, capture_output=True).stdout.split()
        for name in tracked:
            self.assertFalse(re.search(r'\.(jks|keystore|p12|pfx|pem)$', name), name)
            self.assertFalse(name.startswith('work/'), name)


class AllWorkflowsPinned(unittest.TestCase):
    """Third-party actions are pinned to a full commit SHA, so a moved tag cannot change what runs."""

    def workflows(self):
        import glob
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        return sorted(glob.glob(os.path.join(root, ".github", "workflows", "*.yml")))

    def test_signing_jobs_do_not_install_pip_packages_or_add_venvs_to_path(self):
        for path in self.workflows():
            workflow = Path(path).read_text(encoding="utf-8")
            jobs = re.split(r"(?m)^  [A-Za-z_][A-Za-z0-9_-]*:\s*$", workflow)
            for job in jobs:
                if re.search(r"secrets\.ONAY_", job):
                    self.assertNotRegex(job, r"(?i)\bpip(?:3)?[\"']?\s+install\b", path)
                    for line in job.splitlines():
                        if "GITHUB_PATH" in line:
                            self.assertNotRegex(line, r"(?i)venv|virtualenv", path)

    def test_corridor_build_uses_pinned_upstream_enjarify(self):
        workflow = (ROOT / '.github/workflows/build-corridor-qa.yml').read_text(encoding='utf-8')
        self.assertIn('repository: google/enjarify', workflow)
        self.assertIn('ref: c4fb7e047a457ab580a3c8577aa76fd53ce967b0', workflow)
        self.assertNotIn('enjarify-adapter', workflow)
        self.assertIn('ENJARIFY_MODULE_DIR="$GITHUB_WORKSPACE/work/enjarify"', workflow)

    def test_every_action_is_pinned_to_a_commit_sha(self):
        for path in self.workflows():
            with open(path, encoding="utf-8") as f:
                for n, line in enumerate(f, 1):
                    m = re.search(r"uses:\s*(\S+)", line)
                    if m and not m.group(1).startswith("./"):
                        self.assertRegex(m.group(1), r"@[0-9a-f]{40}$", "%s:%d is not pinned: %s" % (path, n, line.strip()))

    def test_ci_workflow_is_read_only_and_checks_on_pull_requests(self):
        for path in self.workflows():
            if path.endswith("ci.yml"):
                text = open(path, encoding="utf-8").read()
                self.assertIn("pull_request:", text)
                self.assertRegex(text, r"permissions:\s+contents: read")
                self.assertNotIn("secrets.", text)
                self.assertNotIn("contents: write", text)


if __name__ == '__main__':
    unittest.main()
