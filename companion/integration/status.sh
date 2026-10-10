#!/system/bin/sh
MODDIR=${0%/*}
google='⚠️Google未固化'
enrollment='⚠️注册组件未就绪'
dsp='⚠️DSP未运行'
package=$(dumpsys -t 10 package com.google.android.googlequicksearchbox 2>/dev/null | grep -m 1 'flags=\[.*SYSTEM')
if [ -n "$package" ]; then
    google='✅Google系统应用'
fi
library=$(pm list libraries 2>/dev/null)
if pm path com.android.hotwordenrollment.xgoogle 2>/dev/null | grep -q '^package:' &&
   pm path com.android.hotwordenrollment.okgoogle 2>/dev/null | grep -q '^package:' &&
   printf '%s\n' "$library" | grep -q 'com.android.hotwordenrollment.common.util'; then
    enrollment='✅注册组件已加载'
fi
state=$(dumpsys -t 10 voiceinteraction 2>/dev/null | /data/adb/ksu/bin/busybox awk -f "$MODDIR/hotword-status.awk")
case "$state" in
    dsp) dsp='✅DSP检测已初始化' ;;
    software) dsp='⚠️Google使用软件检测' ;;
    unknown) dsp='⏳DSP状态未知' ;;
esac
audio=$(cat "$MODDIR/audio-state" 2>/dev/null)
case "$audio" in
    unsupported|missing) dsp="$dsp；音频配置不支持" ;;
    inherited-overlay) dsp="$dsp；原配置被挂载，未生成覆盖" ;;
    io-failed) dsp="$dsp；音频配置写入失败" ;;
    label-failed) dsp="$dsp；音频标签设置失败" ;;
esac
description="[$google | $enrollment | $dsp]"
sed '/^description=/d' "$MODDIR/module.prop" > "$MODDIR/module.prop.tmp" || exit 1
printf 'description=%s\n' "$description" >> "$MODDIR/module.prop.tmp"
chmod 0644 "$MODDIR/module.prop.tmp"
mv "$MODDIR/module.prop.tmp" "$MODDIR/module.prop"
printf '%s\n' "$description"
