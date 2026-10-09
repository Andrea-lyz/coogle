import subprocess, tempfile, unittest
from pathlib import Path
ROOT = Path(__file__).parent
BASH = r"C:/Program Files/Git/bin/bash.exe"
class AudioFailureTest(unittest.TestCase):
    def test_file_operation_failures_remove_overlay(self):
        for operation in ("cp", "mv", "chmod", "sha256sum"):
            with self.subTest(operation=operation), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                script = (ROOT/"integration/prepare-audio.sh").read_text(encoding="utf-8")
                # Run the production script against isolated input and output paths.
                unix = "/" + work.as_posix()[0].lower() + work.as_posix()[2:]
                script = script.replace("MODDIR=${0%/*}", f'MODDIR="{unix}"')
                script = script.replace("SOURCE=/odm/etc/resourcemanager.xml", f'SOURCE="{unix}/source.xml"')
                script = script.replace("OLD=/data/adb/modules/coogle_companion", f'OLD="{unix}/old"')
                script = script.replace("BB=/data/adb/ksu/bin/busybox", f'BB="{unix}/busybox"')
                script = f"{operation}() {{ return 1; }}\n" + script
                (work/"prepare.sh").write_text(script,encoding="utf-8")
                (work/"busybox").write_text('#!/bin/sh\nif [ "$1" = awk ]; then cat "$4"; else exit 0; fi\n')
                subprocess.run([BASH,"-c", f'chmod +x "{unix}/busybox"'],check=True)
                (work/"source.xml").write_text("valid fixture")
                subprocess.run([BASH,str(work/"prepare.sh")],check=True,capture_output=True)
                self.assertEqual("io-failed",(work/"audio-state").read_text().strip())
                self.assertFalse((work/"system/odm/etc/resourcemanager.xml").exists())
if __name__ == "__main__": unittest.main()
