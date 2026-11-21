"""Offline identity-clone tests; no SDK, APK files or signing secrets needed."""
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from patch_clone_manifest import A, change_manifest

SOURCE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  package="kz.onay">
  <permission android:name="kz.onay.permission.PUSH" android:protectionLevel="signature"/>
  <uses-permission android:name="kz.onay.permission.PUSH"/>
  <uses-permission android:name="android.permission.INTERNET"/>
  <queries><package android:name="kz.onay"/></queries>
  <application android:name=".OnayApp" android:label="@string/app_name"
    android:appComponentFactory="androidx.core.app.CoreComponentFactory"
    android:taskAffinity="kz.onay">
    <activity android:name=".ui.splash.SplashActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
    <activity android:name="OtherActivity"/>
    <activity android:name="com.thirdparty.OtherActivity"/>
    <activity-alias android:name=".Alt" android:targetActivity=".ui.splash.SplashActivity"/>
    <provider android:name="androidx.core.content.FileProvider"
      android:authorities="kz.onay.files;kz.onay.sync" android:exported="false"
      android:readPermission="kz.onay.permission.PUSH"/>
    <provider android:name=".LocalProvider"
      android:authorities="kz.onay.firebaseinitprovider"/>
    <provider android:name="com.ext.Provider" android:authorities="com.someone.shared"/>
    <receiver android:name=".PushReceiver" android:permission="kz.onay.permission.PUSH"/>
    <service android:name="MyService" android:process=":remote"/>
  </application>
</manifest>
"""


class CloneManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "AndroidManifest.xml"
        self.path.write_text(SOURCE, encoding="utf-8")

    def root(self):
        return ET.parse(self.path).getroot()

    def test_standalone_identity_and_original_java_classes(self):
        stats = change_manifest(self.path)
        root = self.root()
        self.assertEqual(root.get("package"), "kz.onay.free")
        app = root.find("application")
        self.assertEqual(app.get(A("name")), "kz.onay.OnayApp")
        self.assertEqual(app.get(A("label")), "ONAY Free")
        self.assertEqual(app.get(A("taskAffinity")), "kz.onay.free")
        activities = app.findall("activity")
        self.assertEqual([v.get(A("name")) for v in activities],
                         ["kz.onay.ui.splash.SplashActivity", "kz.onay.OtherActivity",
                          "com.thirdparty.OtherActivity"])
        self.assertEqual(activities[0].get(A("label")), "ONAY Free")
        alias = app.find("activity-alias")
        self.assertEqual(alias.get(A("name")), "kz.onay.Alt")
        self.assertEqual(alias.get(A("targetActivity")), "kz.onay.ui.splash.SplashActivity")
        self.assertEqual(app.find("service").get(A("process")), ":remote")
        self.assertGreater(stats["classes_preserved"], 0)

    def test_provider_authorities_and_own_permissions_are_unique(self):
        change_manifest(self.path)
        root = self.root()
        app = root.find("application")
        providers = app.findall("provider")
        self.assertEqual(providers[0].get(A("authorities")), "kz.onay.free.files;kz.onay.free.sync")
        self.assertEqual(providers[1].get(A("authorities")), "kz.onay.free.firebaseinitprovider")
        self.assertEqual(providers[2].get(A("authorities")), "com.someone.shared")
        own = "kz.onay.free.permission.PUSH"
        self.assertEqual(root.find("permission").get(A("name")), own)
        self.assertEqual(root.find("uses-permission").get(A("name")), own)
        self.assertEqual(app.find("receiver").get(A("permission")), own)
        self.assertEqual(providers[0].get(A("readPermission")), own)
        self.assertEqual(root.findall("uses-permission")[1].get(A("name")),
                         "android.permission.INTERNET")
        # Querying an installed original ONAY is a separate cross-package intent; leave it unchanged.
        self.assertEqual(root.find("queries").find("package").get(A("name")), "kz.onay")

    def test_rejects_invalid_source(self):
        self.path.write_text(SOURCE.replace('package="kz.onay"', 'package="com.other"'))
        with self.assertRaisesRegex(ValueError, "expected source package"):
            change_manifest(self.path)

    def test_rejects_shared_user_id(self):
        self.path.write_text(SOURCE.replace('package="kz.onay"',
                                             'package="kz.onay" android:sharedUserId="kz.onay"'))
        with self.assertRaisesRegex(ValueError, "sharedUserId"):
            change_manifest(self.path)

    def test_preserves_preexisting_duplicate_authorities(self):
        self.path.write_text(SOURCE.replace('android:authorities="com.someone.shared"',
                                             'android:authorities="kz.onay.sync"'))
        change_manifest(self.path)
        providers = self.root().find("application").findall("provider")
        self.assertIn("kz.onay.free.sync", providers[0].get(A("authorities")))
        self.assertEqual(providers[2].get(A("authorities")), "kz.onay.free.sync")

    def test_rejects_unsafe_destination(self):
        with self.assertRaisesRegex(ValueError, "unsafe"):
            change_manifest(self.path, new="kz.onay")

    def test_no_mutation_on_rejected_manifest(self):
        bad = SOURCE.replace('package="kz.onay"', 'package="com.other"')
        self.path.write_text(bad)
        with self.assertRaises(ValueError):
            change_manifest(self.path)
        self.assertEqual(self.path.read_text(), bad)


if __name__ == "__main__":
    unittest.main()
