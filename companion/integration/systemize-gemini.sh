#!/system/bin/sh
# Installer-only: preserve the installed Gemini base APK and all its splits.
gemini_paths=$(pm path com.google.android.apps.bard 2>/dev/null | sed -n 's/^package://p')
if [ -n "$gemini_paths" ]; then
    gemini_dir="$MODPATH/system/app/Gemini"
    mkdir -p "$gemini_dir" || abort 'Cannot create Gemini system directory'
    printf '%s\n' "$gemini_paths" | while IFS= read -r apk; do
        [ -f "$apk" ] || exit 1
        cp -f "$apk" "$gemini_dir/${apk##*/}" || exit 1
    done || abort 'Cannot copy installed Gemini APKs'
    [ -f "$gemini_dir/base.apk" ] || abort 'Gemini base APK is missing'
    ui_print 'Gemini and its installed splits will be systemized after reboot.'
else
    ui_print 'Gemini is not installed; Gemini systemization skipped.'
fi
