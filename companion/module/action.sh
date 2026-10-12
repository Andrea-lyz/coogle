#!/system/bin/sh
MODDIR=${0%/*}
BB=/data/adb/ksu/bin/busybox
# Bound the interactive action; no listener remains after closing or timeout.
if [ "$1" != --menu ]; then
    "$BB" timeout 90 /system/bin/sh "$0" --menu
    result=$?
    [ "$result" = 0 ] || echo '操作结束，未保存的选择不生效。'
    exit "$result"
fi
. "$MODDIR/features.sh"
CONFIGDIR=$MODDIR
if [ -f /data/adb/modules_update/coogle_companion/module.prop ]; then
    CONFIGDIR=/data/adb/modules_update/coogle_companion
fi
load_features "$CONFIGDIR/features.conf"
input_dir=$(mktemp -d "$MODDIR/.action.XXXXXX") || exit 1
pipe="$input_dir/events"
mkfifo "$pipe" || { rmdir "$input_dir"; exit 1; }
getevent -ql > "$pipe" 2>/dev/null &
events=$!
cleanup() {
    exit_status=$?
    trap - EXIT
    kill "$events" 2>/dev/null
    wait "$events" 2>/dev/null
    rm -f "$pipe"
    rmdir "$input_dir"
    exit "$exit_status"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
exec 3< "$pipe"
key() {
    while IFS= read -r event <&3; do
        case "$event" in
            *EV_KEY*KEY_VOLUMEUP*DOWN*) return 0 ;;
            *EV_KEY*KEY_VOLUMEDOWN*DOWN*) return 1 ;;
        esac
    done
    echo '无法读取音量键，未保存。' >&2
    exit 1
}
choose() {
    label=$1; value=$2
    [ "$value" = 1 ] && state=开启 || state=关闭
    echo "$label（当前：$state）"
    echo '音量＋：开启    音量－：关闭'
    if key; then selected=1; else selected=0; fi
}
echo 'Coogle 附属模块：支持组件选择'
echo '配置保存后重启生效；Coogle 中的功能开关仍需开启。'
choose 'Google 固化为系统应用' "$google"; google=$selected
choose 'Hey Google 支持' "$hey"; hey=$selected
choose '实时翻译支持' "$translate"; translate=$selected
echo 'Hey Google 和实时翻译需要 Google 已具备相应系统支持。'
echo '音量＋：保存    音量－：取消'
if key; then
    save_features "$CONFIGDIR/features.conf" || exit 1
    echo '已保存，请重启。'
else
    echo '已取消，原配置保留。'
fi
