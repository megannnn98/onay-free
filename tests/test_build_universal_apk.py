"""Offline tests of the packaging wrapper, without Android SDK or an ONAY binary.

These validate the script's checks and signing workflow, not runtime behavior.
Run with: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts' / 'build_universal_apk.sh'

# Test doubles only: the real jar/signing tools must be used for releases.
TOOL = '''#!/usr/bin/env python3
import os, pathlib, shutil, sys, zipfile
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ['MOCK_LOG'], 'a') as f:
    f.write(name + ' ' + ' '.join(args) + '\\n')
if name == 'java':
    out = pathlib.Path(args[args.index('-o') + 1])
    with zipfile.ZipFile(out, 'w') as z:
        for f in ('AndroidManifest.xml', 'resources.arsc', 'classes.dex'):
            z.writestr(f, b'fake')
        if not os.environ.get('MOCK_NO_LIB'):
            z.writestr('lib/arm64-v8a/libfake.so', b'fake')
elif name == 'zipalign':
    if '-c' not in args:
        shutil.copyfile(args[-2], args[-1])
elif name == 'apksigner':
    if args[0] == 'sign':
        shutil.copyfile(args[-1], args[args.index('--out') + 1])
    elif args[0] != 'verify':
        sys.exit(2)
'''


class UniversalApkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        self.split_dir = self.path / 'splits'
        self.split_dir.mkdir()
        (self.split_dir / 'base.apk').write_bytes(b'base')
        (self.split_dir / 'split_config.arm64_v8a.apk').write_bytes(b'arm64')
        (self.split_dir / 'split_config.xxhdpi.apk').write_bytes(b'density')
        self.tools = self.path / 'bin'
        self.tools.mkdir()
        for tool in ('java', 'zipalign', 'apksigner'):
            file = self.tools / tool
            file.write_text(TOOL)
            file.chmod(0o755)
        self.jar = self.path / 'APKEditor.jar'
        self.jar.write_bytes(b'not actually executed')
        self.key = self.path / 'test.jks'
        self.key.write_bytes(b'test key placeholder')
        self.output = self.path / 'release' / 'onay-free.apk'
        self.log = self.path / 'tool-calls.log'
        self.env = {**os.environ,
                    'PATH': str(self.tools) + os.pathsep + os.environ['PATH'],
                    'SPLITS_INPUT': str(self.split_dir),
                    'OUTPUT_APK': str(self.output),
                    'APKEDITOR_JAR': str(self.jar),
                    'ONAY_KS': str(self.key),
                    'ONAY_KS_PASS': 'fake_password',
                    'MOCK_LOG': str(self.log)}

    def run_build(self, **environment):
        return subprocess.run(['bash', str(SCRIPT)],
                              env={**self.env, **environment},
                              text=True, capture_output=True)

    def test_merges_aligns_signs_and_verifies(self):
        result = self.run_build()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.output.is_file())
        with zipfile.ZipFile(self.output) as apk:
            self.assertIn('lib/arm64-v8a/libfake.so', apk.namelist())
        calls = self.log.read_text()
        self.assertIn('m -i ' + str(self.split_dir), calls)
        self.assertIn('-validate-modules -clean-meta', calls)
        self.assertIn('apksigner sign ', calls)
        self.assertIn('apksigner verify ', calls)

    def test_can_merge_release_apks_archive(self):
        archive = self.path / 'release.apks'
        with zipfile.ZipFile(archive, 'w') as z:
            for source in self.split_dir.iterdir():
                z.write(source, source.name)
        result = self.run_build(SPLITS_INPUT=str(archive))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.output.is_file())

    def test_missing_base_fails_without_output(self):
        (self.split_dir / 'base.apk').unlink()
        result = self.run_build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing base.apk', result.stderr)
        self.assertFalse(self.output.exists())

    def test_multiple_abis_are_rejected(self):
        (self.split_dir / 'split_config.x86_64.apk').write_bytes(b'x86')
        result = self.run_build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('expected only the arm64 split', result.stderr)

    def test_missing_native_libraries_are_rejected(self):
        result = self.run_build(MOCK_NO_LIB='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing arm64 libraries', result.stderr)
        self.assertFalse(self.output.exists())

    def test_missing_keystore_is_rejected(self):
        result = self.run_build(ONAY_KS=str(self.path / 'missing.jks'))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing signing keystore', result.stderr)


if __name__ == '__main__':
    unittest.main()
