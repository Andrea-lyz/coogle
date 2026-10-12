import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
BASH = 'C:/Program Files/Git/bin/bash.exe'


def unix(path):
    value = path.as_posix()
    return '/' + value[0].lower() + value[2:]


class AudioPatchTest(unittest.TestCase):
    def run_patch(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'source.xml'
            path.write_bytes(source)
            return subprocess.run([BASH, '-c',
                f'awk -v BINMODE=3 -f "{unix(ROOT / "integration/patch-audio.awk")}" "{unix(path)}"'], capture_output=True)

    def test_only_hotword_attribute_changes_preserving_other_bytes(self):
        for quote, value in ((b'"', b'false'), (b"'", b'false'), (b'"', b'true')):
            with self.subTest(quote=quote, value=value):
                block = (b'<stream_config>\r\n<name> HOTWORD_VOICE_UI </name>\r\n'
                         b'<param get_module_version = ' + quote + value + quote + b'/>\r\n</stream_config>')
                comment = b'<!-- <stream_config><name>HOTWORD_VOICE_UI</name></stream_config> -->\r\n'
                other = b'<stream_config><name>OTHER</name><param get_module_version="false"/></stream_config>\r\n'
                source = b'<root>\r\n' + comment + other + block + b'\r\n</root>\r\n'
                result = self.run_patch(source)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, source.replace(block, block.replace(quote+value+quote, quote+b'true'+quote)))

    def test_missing_duplicate_and_unrecognized_targets_produce_no_overlay(self):
        valid = b'<stream_config><name>HOTWORD_VOICE_UI</name><param get_module_version="false"/></stream_config>'
        for source in (b'<root/>', valid+valid, valid.replace(b'false', b'unknown'),
                       valid.replace(b'</stream_config>', b''),
                       valid.replace(b'get_module_version="false"', b'get_module_version="false" get_module_version="true"')):
            with self.subTest(source=source):
                result = self.run_patch(source)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b'')


if __name__ == '__main__':
    unittest.main()
