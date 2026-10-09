"""Combine hotword payloads with the user-supplied Google systemizer."""
from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / 'build/Coogle-Companion-1.0.zip'

def payload():
    files = {}
    for directory in (ROOT/'module/system',):
        for path in directory.rglob('*'):
            if path.is_file():
                name = 'system/' + path.relative_to(directory).as_posix()
                assert name not in files, name
                files[name] = path.read_bytes()
    with zipfile.ZipFile(ROOT/'googlequicksearchbox-systemizer.zip') as systemizer:
        for info in systemizer.infolist():
            if not info.is_dir() and info.filename.startswith('system/'):
                assert '..' not in Path(info.filename).parts
                assert info.filename not in files
                files[info.filename] = systemizer.read(info)
        systemizer_install = systemizer.read('customize.sh').decode('utf-8').replace('\r\n','\n')
    for name in ('patch-audio.awk', 'prepare-audio.sh', 'status.sh', 'hotword-status.awk'):
        path = ROOT/'integration'/name
        files[name] = path.read_bytes().replace(b'\r\n', b'\n')
    for name in ('setup.sh', 'uninstall.sh'):
        files[name] = (ROOT/'module'/name).read_bytes().replace(b'\r\n', b'\n')
    files['boot-completed.sh'] = b'''#!/system/bin/sh
MODDIR=${0%/*}
sleep 30
# Binder shell commands require pipe FDs rather than module-directory FDs.
/system/bin/sh "$MODDIR/setup.sh" 2>&1 | cat > /dev/null
/system/bin/sh "$MODDIR/status.sh" > /dev/null 2>&1
'''
    installer = '#!/system/bin/sh\n' + systemizer_install + '''
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
for script in boot-completed.sh setup.sh uninstall.sh prepare-audio.sh status.sh; do
    set_perm "$MODPATH/$script" 0 0 0755
done
/system/bin/sh "$MODPATH/prepare-audio.sh"
ui_print "Google system app, enrollment and DSP support combined. Reboot required."
'''
    files['customize.sh'] = installer.replace('\r\n', '\n').encode()
    files['module.prop'] = 'id=coogle_companion\nname=Coogle 附属模块\nversion=1.0\nversionCode=100\nauthor=zopulus\ndescription=[⏳待重启检测]\n'.encode()
    return files

def build():
    files = payload()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(files.items()):
            info = zipfile.ZipInfo(name)
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | (0o755 if name.endswith('.sh') else 0o644)) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    print(f'{OUT} ({OUT.stat().st_size} bytes)')

if __name__ == '__main__':
    build()
