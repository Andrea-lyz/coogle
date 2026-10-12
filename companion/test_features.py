import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
BASH = r'C:/Program Files/Git/bin/bash.exe'

def unix(path):
    text = path.as_posix()
    return '/' + text[0].lower() + text[2:]

class FeaturesTest(unittest.TestCase):
    def test_independent_choices_restore_payload_without_deleting_google(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            paths = ['system/priv-app/GoogleQuickSearchBox/base.apk',
                     'system/product/priv-app/CoogleEnrollmentXGoogle/enroll.apk',
                     'system/product/priv-app/CoogleEnrollmentYGoogle/enroll.apk',
                     'system/framework/com.android.hotwordenrollment.common.util.jar',
                     'system/etc/sysconfig/coogle-hotword-library.xml',
                     'system/odm/etc/resourcemanager.xml',
                     'system/product/overlay/CoogleContextualSearch/overlay.apk']
            for name in paths:
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode())
            run = lambda code: subprocess.run([BASH, '-c',
                f'MODDIR="{unix(work)}"; . "{unix(ROOT / "module/features.sh")}"; {code}'],
                check=True, capture_output=True, text=True, encoding='utf-8')
            run('google=1; hey=0; translate=1; apply_features')
            self.assertTrue((work/paths[0]).is_file())
            self.assertTrue((work/paths[-1]).is_file())
            for name in paths[1:-1]:
                self.assertFalse((work/name).exists())
                self.assertEqual((work/'optional'/name).read_bytes(), name.encode())
            run('google=0; hey=0; translate=0; apply_features; apply_features')
            self.assertFalse((work/paths[0]).exists())
            self.assertFalse((work/paths[-1]).exists())
            run('google=1; hey=1; translate=1; apply_features; apply_features')
            for name in paths:
                self.assertEqual((work/name).read_bytes(), name.encode())

    def test_settings_are_data_and_round_trip(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config = work/'features.conf'
            config.write_bytes(b'google=0\nhey=0\ntranslate=1\n$(touch injected)\n')
            result = subprocess.run([BASH, '-c',
                f'cd "{unix(work)}"; . "{unix(ROOT / "module/features.sh")}"; '
                'load_features features.conf; save_features saved.conf; cat saved.conf'],
                check=True, capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(result.stdout, 'google=0\nhey=0\ntranslate=1\n')
            self.assertFalse((work/'injected').exists())

    def test_disabled_hotword_never_initializes(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            for name in ('setup.sh', 'features.sh'):
                (work/name).write_bytes((ROOT/'module'/name).read_bytes().replace(b'\r\n', b'\n'))
            (work/'applied-features.conf').write_bytes(b'google=1\nhey=0\ntranslate=1\n')
            # Any binder initialization would fail the test rather than touch Android.
            script = (work/'setup.sh').read_text(encoding='utf-8').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
            (work/'run.sh').write_text('am() { exit 9; }; dumpsys() { exit 9; };\n'+script)
            result = subprocess.run([BASH, str(work/'run.sh')], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_action_saves_or_cancels_and_cleans_up_listener(self):
        for save in (True, False):
            with self.subTest(save=save), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
                original = b'google=1\nhey=1\ntranslate=1\n'
                (work/'features.conf').write_bytes(original)
                script = (ROOT/'module/action.sh').read_text(encoding='utf-8').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
                # Feed volume presses to the production FIFO reader, then keep the
                # producer alive so exiting must actually terminate the listener.
                choices = ['KEY_VOLUMEUP', 'KEY_VOLUMEDOWN', 'KEY_VOLUMEUP',
                           'KEY_VOLUMEUP' if save else 'KEY_VOLUMEDOWN']
                events = '\n'.join(f'EV_KEY {key} DOWN' for key in choices)
                producer = "getevent() { printf '%s\\n' '" + events + "'; exec sleep 20; };\n"
                (work/'action.sh').write_bytes((producer+script).encode())
                result = subprocess.run([BASH, str(work/'action.sh'), '--menu'],
                                        capture_output=True, text=True, encoding='utf-8', timeout=5)
                self.assertEqual(result.returncode, 0, result.stderr)
                expected = b'google=1\nhey=0\ntranslate=1\n' if save else original
                self.assertEqual((work/'features.conf').read_bytes(), expected)
                self.assertFalse(list(work.glob('.action.*')))

    def test_disabled_status_skips_android_queries(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
            (work/'applied-features.conf').write_bytes(b'google=0\nhey=0\ntranslate=0\n')
            (work/'module.prop').write_bytes(b'id=coogle_companion\n')
            script = (ROOT/'integration/status.sh').read_text(encoding='utf-8').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
            mocks = f'query() {{ touch "{unix(work)}/queried"; }}; dumpsys() {{ query; }}; pm() {{ query; }}; cmd() {{ query; }};\n'
            (work/'status.sh').write_bytes((mocks+script).encode())
            result = subprocess.run([BASH, str(work/'status.sh')], check=True, capture_output=True, text=True, encoding='utf-8')
            self.assertIn('Google固化关闭', result.stdout)
            self.assertIn('Hey Google支持关闭', result.stdout)
            self.assertFalse((work/'queried').exists())

    def test_payload_conflict_pauses_mount_and_can_recover(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
            script = (ROOT/'module/post-fs-data.sh').read_text(encoding='utf-8').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
            (work/'post-fs-data.sh').write_bytes(script.encode())
            (work/'features.conf').write_bytes(b'google=0\nhey=0\ntranslate=0\n')
            active = work/'system/priv-app/GoogleQuickSearchBox/base.apk'
            parked = work/'optional/system/priv-app/GoogleQuickSearchBox/base.apk'
            for path in (active, parked):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'preserved')
            run = lambda: subprocess.run([BASH, str(work/'post-fs-data.sh')], capture_output=True)
            self.assertNotEqual(run().returncode, 0)
            self.assertTrue((work/'skip_mount').exists())
            self.assertEqual(active.read_bytes(), b'preserved')
            parked.unlink(); parked.parent.rmdir()
            self.assertEqual(run().returncode, 0)
            self.assertFalse((work/'skip_mount').exists())
            self.assertEqual(parked.read_bytes(), b'preserved')

if __name__ == '__main__':
    unittest.main()
