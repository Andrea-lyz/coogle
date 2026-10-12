#!/system/bin/sh
# Pre-mount check only; no Binder calls, runtime hooks or background polling.
prepare_native_translation() {
    overlay="$MODDIR/system/product/overlay/CoogleContextualSearch/CoogleContextualSearch.apk"
    feature=system/etc/permissions/coogle-live-translate.xml
    [ -r "$overlay" ] && { [ -r "$MODDIR/$feature" ] || [ -r "$MODDIR/optional/$feature" ]; } || return 1
    idmap="$MODDIR/native-translate.idmap.tmp"
    native_ready=0
    if /system/bin/idmap2 create --target-apk-path /system/framework/framework-res.apk \
        --overlay-apk-path "$overlay" --idmap-path "$idmap" \
        --policy public --policy system --policy product >/dev/null 2>&1; then
        if value=$(/system/bin/idmap2 lookup --idmap-path "$idmap" --config en \
            --resid android:string/config_defaultContextualSearchPackageName 2>/dev/null); then
            value=${value#*value=\"}
            value=${value%\"}
            [ "$value" != com.google.android.googlequicksearchbox ] || native_ready=1
        fi
    fi
    rm -f "$idmap"
    # Unsupported firmware keeps the rest of the companion active.
    select_payload "$native_ready" "$feature" || return 1
    if [ "$native_ready" = 1 ]; then
        rm -f "$MODDIR/translation-error"
    else
        printf 'overlay-incompatible\n' > "$MODDIR/translation-error" || return 1
    fi
}
