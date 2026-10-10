import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
BASH = r'C:/Program Files/Git/bin/bash.exe'


class GeminiSystemizerTest(unittest.TestCase):
    def run_installer(self, directory, paths):
        unix = '/' + directory.as_posix()[0].lower() + directory.as_posix()[2:]
        (directory / 'paths').write_text('\n'.join(paths) + '\n', encoding='utf-8')
        script = f'''MODPATH='{unix}/module'
pm() {{ cat '{unix}/paths'; }}
ui_print() {{ :; }}
abort() {{ echo "$*" >&2; exit 1; }}
'''
        script += (ROOT / 'integration/systemize-gemini.sh').read_text(encoding='utf-8')
        (directory / 'install.sh').write_text(script, encoding='utf-8')
        return subprocess.run([BASH, str(directory / 'install.sh')], capture_output=True, text=True)

    def test_installed_base_and_splits_are_preserved(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            app = directory / 'installed app'
            app.mkdir()
            names = ['base.apk', 'split_config.arm64_v8a.apk', 'split_config.zh.apk']
            for name in names:
                (app / name).write_bytes(name.encode())
            unix = '/' + app.as_posix()[0].lower() + app.as_posix()[2:]
            result = self.run_installer(directory, ['package:' + unix + '/' + name for name in names])
            self.assertEqual(0, result.returncode, result.stderr)
            for name in names:
                self.assertEqual((app / name).read_bytes(), (directory / 'module/system/app/Gemini' / name).read_bytes())

    def test_missing_app_is_optional(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            self.assertEqual(0, self.run_installer(directory, []).returncode)
            self.assertFalse((directory / 'module/system/app/Gemini').exists())

    def test_incomplete_copy_aborts_installation(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertNotEqual(0, self.run_installer(Path(temp), ['package:/nonexistent/base.apk']).returncode)


if __name__ == '__main__':
    unittest.main()
