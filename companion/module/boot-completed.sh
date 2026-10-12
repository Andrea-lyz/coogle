#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
load_features "$MODDIR/applied-features.conf"
if [ "$hey" = 1 ] && [ ! -f "$MODDIR/component-error" ]; then
    sleep 30
    # Binder shell commands require pipe FDs rather than module-directory FDs.
    /system/bin/sh "$MODDIR/setup.sh" 2>&1 | cat > /dev/null
fi
/system/bin/sh "$MODDIR/status.sh" > /dev/null 2>&1
