"""Static checks that the release workflow keeps the signing key out of logs, artifacts and the repository.

These read the workflow text; they cannot prove what GitHub does at run time, but they catch the usual
mistakes (echoing a secret, uploading the keystore, publishing during a dry run).
Run with: python3 -m unittest discover -s tests -v
"""
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
        uploads = re.findall(r'uses: actions/upload-artifact@v4.*?(?=\n      - |\Z)', WORKFLOW, re.S)
        self.assertEqual(len(uploads), 1)
        text = uploads[0]
        self.assertIn('path: ${{ env.APK_PATH }}', text)
        for bad in ('.jks', 'signing', 'onay/', 'RUNNER_TEMP', 'work/input'):
            self.assertNotIn(bad, text)
        days = int(re.search(r'retention-days: (\d+)', text).group(1))
        self.assertLessEqual(days, 7)

    def test_checkout_does_not_persist_credentials_in_publish_job(self):
        self.assertRegex(publish_job_text(), r'uses: actions/checkout@v4\s+with:\s+persist-credentials: false')

    def test_signing_key_is_removed_even_when_a_step_fails(self):
        text = step('Remove the signing key from the runner')
        self.assertIn('if: always()', text)
        self.assertIn('signing.jks', text)

    def test_dry_run_never_publishes(self):
        text = step('Create release and upload APK')
        self.assertIn('if: ${{ !inputs.dry_run }}', text)
        self.assertIn('if: ${{ !inputs.dry_run }}', step('Prepare release notes'))
        self.assertIn('if: ${{ inputs.dry_run }}', step('Keep the APK as a short-lived artifact'))

    def test_certificate_of_the_source_release_must_match(self):
        text = step('Verify packaging and that the signing certificate matches the original release')
        self.assertIn('verify_universal_apk.sh "$apk" "$src"', text)
        self.assertIn('[ -n "$src" ]', text)

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


if __name__ == '__main__':
    unittest.main()
