#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
load_features "$MODDIR/features.conf"
if ! apply_features; then
    touch "$MODDIR/component-error" "$MODDIR/skip_mount"
    google=0; hey=0; translate=0
    save_features "$MODDIR/applied-features.conf"
    exit 1
fi
if [ -f "$MODDIR/component-error" ]; then
    rm -f "$MODDIR/component-error" "$MODDIR/skip_mount"
fi
save_features "$MODDIR/applied-features.conf"
