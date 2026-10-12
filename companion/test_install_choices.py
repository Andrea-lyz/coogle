import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from build_combined import payload
from test_features import BASH, ROOT, unix

class InstallChoicesTest(unittest.TestCase):
    def test_installer_reads_selected_choices_or_skips_hidden_prompt(self):
        for mode in ('save', 'headless', 'cancel', 'timeout'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                module = work/'module'
                module.mkdir()
                old = work/'previous.conf'
                old.write_bytes(b'google=0\nhey=1\ntranslate=0\n')
                (module/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
                (module/'prepare-audio.sh').write_bytes(b'#!/bin/sh\nexit 0\n')
                menu = b'#!/bin/sh\ntouch "${0%/*}/menu-called"\n'
                if mode == 'save':
                    menu += b'printf "google=1\\nhey=0\\ntranslate=1\\n" > "${0%/*}/features.conf"\n'
                menu += b'exit 1\n' if mode == 'timeout' else b'exit 0\n'
                (module/'action.sh').write_bytes(menu)
                enrollment = module/'system/product/priv-app/CoogleEnrollmentXGoogle/base.apk'
                enrollment.parent.mkdir(parents=True)
                enrollment.write_bytes(b'fixture')
                installer = payload()['customize.sh'].decode('utf-8')
                installer = installer.replace('/data/adb/modules/coogle_companion/features.conf', unix(old))
                installer = installer.replace('/system/bin/sh', f'"{unix(Path(BASH))}"')
                # Run the real generated installer, with Android-only helpers mocked.
                helpers = 'set_perm_recursive() { :; }; set_perm() { :; }; ui_print() { :; }; abort() { exit 9; }; pm() { :; };\n'
                script = work/'installer.sh'
                script.write_bytes((helpers+installer).encode('utf-8'))
                env = os.environ.copy()
                env['MODPATH'] = unix(module)
                env['COOGLE_NONINTERACTIVE'] = '1' if mode == 'headless' else '0'
                subprocess.run([BASH, str(script)], env=env, check=True, capture_output=True)
                expected = b'google=1\nhey=0\ntranslate=1\n' if mode == 'save' else old.read_bytes()
                self.assertEqual((module/'features.conf').read_bytes(), expected)
                self.assertEqual((module/'menu-called').exists(), mode != 'headless')
                self.assertEqual(enrollment.exists(), mode != 'save')
                self.assertEqual((module/'optional/system/product/priv-app/CoogleEnrollmentXGoogle/base.apk').exists(), mode == 'save')

if __name__ == '__main__':
    unittest.main()
