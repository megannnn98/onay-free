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
elif name == 'apktool':
    if args[1] == 'd':
        dest = pathlib.Path(args[args.index('-o') + 1])
        dest.mkdir()
        shutil.copyfile(args[-1], dest / 'merged.apk')
        (dest / 'AndroidManifest.xml').write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android"'
            ' package="kz.onay"><application android:name=".OnayApp" />'
            '</manifest>', encoding='utf-8')
    elif args[1] == 'b':
        out = pathlib.Path(args[args.index('-o') + 1])
        origin = pathlib.Path(args[-1])
        with zipfile.ZipFile(origin / 'merged.apk') as old, zipfile.ZipFile(out, 'w') as z:
            for entry in old.namelist():
                data = old.read(entry)
                if entry == 'AndroidManifest.xml':
                    # Offline placeholder for a binary AXML produced by real apktool.
                    data = bytes([3, 0, 8, 0]) + (origin / entry).read_bytes()
                if entry == 'classes.dex' and os.environ.get('MOCK_CLONE_CORRUPT'):
                    data = b'broken dex'
                z.writestr(entry, data)
elif name == 'aapt2':
    if args[:2] == ['dump', 'badging']:
        print("package: name='%s' versionCode='23310'" %
              ('kz.onay' if os.environ.get('MOCK_WRONG_PACKAGE') else 'kz.onay.free'))
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
        for tool in ('java', 'zipalign', 'apksigner', 'apktool', 'aapt2'):
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
                    'MOCK_LOG': str(self.log),
                    'ONAY_PACKAGE_NAME': 'kz.onay'}

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

    def test_independent_package_keeps_existing_dex_and_native_payload(self):
        result = self.run_build(ONAY_PACKAGE_NAME='kz.onay.free')
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            manifest = apk.read('AndroidManifest.xml').decode()
            self.assertIn('package="kz.onay.free"', manifest)
            self.assertIn('kz.onay.OnayApp', manifest)
        calls = self.log.read_text()
        self.assertIn('apktool -JXmx6g d ', calls)
        self.assertIn('apktool -JXmx6g b ', calls)
        self.assertIn('aapt2 dump badging ', calls)

    def test_apktool_dex_mutation_is_not_shipped(self):
        # Manifest-only transplant intentionally discards *everything* except
        # apktool's compiled manifest, even if apktool rewrote classes.dex.
        result = self.run_build(ONAY_PACKAGE_NAME='kz.onay.free', MOCK_CLONE_CORRUPT='1')
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            self.assertEqual(apk.read('classes.dex'), b'fake')

    def test_clone_rejects_unexpected_installed_package(self):
        result = self.run_build(ONAY_PACKAGE_NAME='kz.onay.free', MOCK_WRONG_PACKAGE='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('clone built as kz.onay', result.stderr)
        self.assertFalse(self.output.exists())

    def test_repackages_existing_latest_single_apk(self):
        source = self.path / 'onay-free-0.0.4.apk'
        with zipfile.ZipFile(source, 'w') as z:
            for name in ('AndroidManifest.xml', 'classes.dex', 'resources.arsc',
                         'lib/arm64-v8a/libfake.so'):
                z.writestr(name, b'original bytes')
        result = self.run_build(SPLITS_INPUT=str(source), ONAY_PACKAGE_NAME='kz.onay.free')
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as apk:
            self.assertIn('package="kz.onay.free"', apk.read('AndroidManifest.xml').decode())
            self.assertEqual(apk.read('classes.dex'), b'original bytes')
        self.assertNotIn('java -Xmx6g', self.log.read_text())

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
