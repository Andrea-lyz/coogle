import subprocess
import unittest
from pathlib import Path

AWK = r"C:/Program Files/Git/usr/bin/awk.exe"
PARSER = Path(__file__).parent / "integration/hotword-status.awk"
HEADER = "VOICE INTERACTION MANAGER\nmComponent=com.google.android.googlequicksearchbox/service\n"
def session(kind, initialized=True, destroyed=False):
    return f"mCallback=session\nmInitialized={str(initialized).lower()}\nmDestroyed={str(destroyed).lower()}\nDetectorType=trusted_hotword_{kind}\n"
class HotwordStatusTest(unittest.TestCase):
    def check(self, dump, expected):
        result = subprocess.run([AWK, "-f", str(PARSER)], input=dump, text=True, capture_output=True, check=True)
        self.assertEqual(expected, result.stdout.strip())
    def test_ready_dsp(self): self.check(HEADER + session("dsp"), "dsp")
    def test_mixed_sessions_do_not_report_dsp(self):
        self.check(HEADER + session("dsp", False, True) + session("software"), "software")
    def test_destroyed_dsp(self): self.check(HEADER + session("dsp", True, True), "inactive")
    def test_uninitialized_dsp(self): self.check(HEADER + session("dsp", False), "inactive")
    def test_other_assistant(self): self.check(HEADER.replace("com.google.android.googlequicksearchbox", "other.assistant") + session("dsp"), "inactive")
    def test_unavailable(self): self.check("", "unknown")
if __name__ == "__main__": unittest.main()
