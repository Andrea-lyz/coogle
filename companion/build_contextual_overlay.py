"""Build the single-resource native contextual-search configuration overlay."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent

def build():
    sdk = Path(os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools/36.0.0'
    java = Path(os.environ['JAVA_HOME']) / 'bin/java.exe'
    scratch = ROOT.parent / 'build/contextual-overlay'
    scratch.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'contextual-overlay'
    compiled = scratch / 'resources.zip'
    unsigned = scratch / 'unsigned.apk'
    output = ROOT / 'module/system/product/overlay/CoogleContextualSearch/CoogleContextualSearch.apk'
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(tools/'aapt2.exe'), 'compile', '--dir', str(source/'res'),
                    '-o', str(compiled)], check=True)
    subprocess.run([str(tools/'aapt2.exe'), 'link', '-o', str(unsigned),
                    '-I', str(sdk/'platforms/android-37.0/android.jar'),
                    '--manifest', str(source/'AndroidManifest.xml'),
                    '--auto-add-overlay', str(compiled)], check=True)
    # Use the same local debug certificate as the app; the static product overlay
    # needs no runtime enable command and does not contain executable code.
    subprocess.run([str(java), '-jar', str(tools/'lib/apksigner.jar'), 'sign',
                    '--ks', str(Path.home()/'.android/debug.keystore'),
                    '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
                    '--key-pass', 'pass:android', '--v4-signing-enabled', 'false',
                    '--out', str(output), str(unsigned)], check=True)
    print(output)

if __name__ == '__main__':
    build()
