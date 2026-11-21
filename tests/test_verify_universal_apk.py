"""Offline tests of scripts/verify_universal_apk.sh.

The Android tools (aapt2, apksigner, zipalign) are test doubles; unzip and the zip files are real, so the
"stored, not compressed" rule for native libraries is checked against the actual archive.
Run with: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = Path(os.environ.get('VERIFY_SCRIPT', ROOT / 'scripts' / 'verify_universal_apk.sh'))
CERT = 'aa' * 32

AAPT2 = '''#!/usr/bin/env python3
import os, sys
args = sys.argv[1:]
if args[:2] == ['dump', 'badging']:
    print("package: name='%s' versionCode='23310' versionName='3.6.5'" % os.environ.get('MOCK_PACKAGE', 'kz.onay.free'))
    print("native-code: '%s'" % os.environ.get('MOCK_ABI', 'arm64-v8a'))
elif args[:2] == ['dump', 'xmltree']:
    print('A: http://schemas.android.com/apk/res/android:extractNativeLibs(0x010104ea)=false')
    if os.environ.get('MOCK_SPLIT'):
        print('A: http://schemas.android.com/apk/res/android:isSplitRequired(0x01010591)=true')
'''
APKSIGNER = '''#!/usr/bin/env python3
import os, sys
args = sys.argv[1:]
if os.environ.get('MOCK_NO_SIG'):
    sys.exit(1)
if '--print-certs' in args:
    print('Signer #1 certificate SHA-256 digest: ' + os.environ.get('MOCK_CERT', 'aa' * 32))
elif '--verbose' in args:
    print('Verified using v2 scheme (APK Signature Scheme v2): true')
'''
ZIPALIGN = '''#!/usr/bin/env python3
import os, sys
sys.exit(1 if os.environ.get('MOCK_UNALIGNED') else 0)
'''


class VerifyUniversalApkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.bin = self.dir / 'bin'
        self.bin.mkdir()
        for name, body in (('aapt2', AAPT2), ('apksigner', APKSIGNER), ('zipalign', ZIPALIGN)):
            path = self.bin / name
            path.write_text(body)
            path.chmod(0o755)

    def make_apk(self, lib_method=zipfile.ZIP_STORED, arsc_method=zipfile.ZIP_STORED,
                 libs=('lib/arm64-v8a/libfake.so',), omit=()):
        apk = self.dir / 'test.apk'
        with zipfile.ZipFile(apk, 'w') as z:
            for name in ('AndroidManifest.xml', 'classes.dex'):
                if name not in omit:
                    z.writestr(name, b'x' * 200, compress_type=zipfile.ZIP_DEFLATED)
            if 'resources.arsc' not in omit:
                z.writestr('resources.arsc', b'y' * 4096, compress_type=arsc_method)
            for lib in libs:
                z.writestr(lib, b'z' * 4096, compress_type=lib_method)
        return apk

    def run_script(self, apk, expected=None, **env):
        full = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'])
        full.update(env)
        cmd = ['bash', str(SCRIPT), str(apk)] + ([expected] if expected else [])
        return subprocess.run(cmd, env=full, text=True, capture_output=True)

    def test_good_apk_passes_and_prints_sha(self):
        r = self.run_script(self.make_apk(), expected=CERT)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn('sha256:', r.stdout)
        self.assertIn('OK:', r.stdout)

    def test_compressed_native_library_is_rejected(self):
        r = self.run_script(self.make_apk(lib_method=zipfile.ZIP_DEFLATED))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('stored uncompressed', r.stderr)

    def test_compressed_resources_arsc_is_rejected(self):
        r = self.run_script(self.make_apk(arsc_method=zipfile.ZIP_DEFLATED))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('resources.arsc', r.stderr)

    def test_split_reference_in_manifest_is_rejected(self):
        r = self.run_script(self.make_apk(), MOCK_SPLIT='1')
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('split', r.stderr)

    def test_wrong_package_is_rejected(self):
        r = self.run_script(self.make_apk(), MOCK_PACKAGE='com.other.app')
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('package', r.stderr)

    def test_extra_abi_is_rejected(self):
        r = self.run_script(self.make_apk(), MOCK_ABI="arm64-v8a' 'x86_64")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('native-code', r.stderr)

    def test_library_of_another_abi_is_rejected(self):
        apk = self.make_apk(libs=('lib/arm64-v8a/liba.so', 'lib/x86_64/libb.so'))
        r = self.run_script(apk)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('other ABIs', r.stderr)

    def test_missing_native_libraries_are_rejected(self):
        r = self.run_script(self.make_apk(libs=()))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('no arm64 native libraries', r.stderr)

    def test_missing_required_entry_is_rejected(self):
        r = self.run_script(self.make_apk(omit=('classes.dex',)))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('classes.dex', r.stderr)

    def test_unaligned_apk_is_rejected(self):
        r = self.run_script(self.make_apk(), MOCK_UNALIGNED='1')
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('aligned', r.stderr)

    def test_unsigned_apk_is_rejected(self):
        r = self.run_script(self.make_apk(), MOCK_NO_SIG='1')
        self.assertNotEqual(r.returncode, 0)

    def test_certificate_mismatch_is_rejected(self):
        r = self.run_script(self.make_apk(), expected='bb' * 32)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('certificate differs', r.stderr)

    def test_certificate_comparison_ignores_case(self):
        r = self.run_script(self.make_apk(), expected=CERT.upper())
        self.assertEqual(r.returncode, 0, r.stderr)


if __name__ == '__main__':
    unittest.main()
