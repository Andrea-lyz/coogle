from pathlib import Path
import hashlib
import unittest
import zipfile
from build_combined import ROOT, OUT

class CombinedTest(unittest.TestCase):
    def test_supplied_systemizer_used_without_bundled_google_apks(self):
        with zipfile.ZipFile(OUT) as z, zipfile.ZipFile(ROOT/'googlequicksearchbox-systemizer.zip') as source:
            marker='system/priv-app/GoogleQuickSearchBox/.replace'
            self.assertEqual(source.read(marker),z.read(marker))
            self.assertFalse(any(n.startswith('system/priv-app/GoogleQuickSearchBox/') and n.endswith('.apk') for n in z.namelist()))
            self.assertIn(source.read('customize.sh').replace(b'\r\n',b'\n'),z.read('customize.sh'))

    def test_complete_payload_integrity_and_no_unsafe_overlays(self):
        with zipfile.ZipFile(OUT) as z:
            self.assertIsNone(z.testzip())
            from build_combined import payload
            expected = payload()
            self.assertEqual(set(expected),set(z.namelist()))
            for name,data in expected.items():
                self.assertEqual(hashlib.sha256(data).digest(),hashlib.sha256(z.read(name)).digest())
            listed = {n for n in z.namelist() if n.startswith('system/')}
            self.assertFalse(any(n.startswith('system/product/etc/') or n.startswith('system/vendor/') for n in listed))
            self.assertNotIn('system/odm/etc/resourcemanager.xml',z.namelist())

    def test_only_initialization_cleanup_and_permissions(self):
        with zipfile.ZipFile(OUT) as z:
            self.assertIn(b'author=zopulus\n',z.read('module.prop'))
            self.assertIn(b'id=coogle_companion\n',z.read('module.prop'))
            self.assertIn('name=Coogle 附属模块\n'.encode(),z.read('module.prop'))
            self.assertNotIn('Google系统应用 + Hotword + DSP'.encode(), z.read('module.prop'))
            self.assertNotIn('Google系统应用 + Hotword + DSP'.encode(), z.read('status.sh'))
            installer = z.read('customize.sh')
            self.assertNotIn(b'abort',installer)
            self.assertNotIn(b'getprop',installer)
            self.assertNotIn(b'dumpsys',installer)
            self.assertIn(b'chcon --reference=',z.read('prepare-audio.sh'))
            self.assertIn(b'prepare-audio.sh',installer)
            for old in (b'coogle_hotword_enrollment', b'coogle_hotword_hal_probe', b'google_system_app'):
                self.assertNotIn(old,installer)
            self.assertNotIn(b'rm -rf',installer)
            for removed in ('post-fs-data.sh','rescue.sh','diagnose.sh','payload.sha256','action.sh'):
                self.assertNotIn(removed,z.namelist())
            self.assertIn(b'cat > /dev/null',z.read('boot-completed.sh'))
            self.assertIn(b'Existing model 101 retained',z.read('setup.sh'))
            self.assertIn(b'am compat reset',z.read('uninstall.sh'))

if __name__ == '__main__': unittest.main()
