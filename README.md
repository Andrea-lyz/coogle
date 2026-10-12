# Coogle

让国行版ColorOS更加Google，仅适配ColorOS17。

## 功能

- **Hey Google**：补全国行系统的 Google 语音唤醒支持。
- **实时翻译**：在圈定即搜中点击翻译，滚动时持续翻译；默认关闭，开启后需重载 Google App。Google App 版本不兼容时保留原行为，尚待 ColorOS 真机验证。
- **Google 智能镜头**：补全 Google 智能镜头功能入口。
- **系统导航方式**：补全设置中的导航选项，支持圈定即搜和左/右下角向内滑动呼出数字助理。
- **Google 搜索框**：启用桌面底部 Google 搜索框。
- **Google 钱包**：支持双击电源键呼出 Google 钱包卡片，需将 Google 钱包设置为默认付款应用。
- **锁屏快捷入口**：补全 Google 数字助理、电子钱包、扫描二维码和家居控制。
- **控制中心**：补全原生钱包、二维码扫描入口。
- **移除 GMS 对国行的使用限制**。
- **设置底部显示 Google 服务**。

## 定位与兼容性

Google 智能镜头和钱包预览中的混淆代码使用 DexKit 特征与调用关系定位；明确名称的系统入口保留反射方式。定位结果按作用域应用 APK 缓存，更新后重新查找；目标缺失或歧义时保留原行为。新动态定位尚待真机验证，不保证所有 Google App / 钱包版本适配。

实时翻译依据国际版一加 13 实机链路，仅在 Google App 内补齐 `CONTEXTUAL_SEARCH_LIVE_TRANSLATE` 能力查询，保留原生资格、来源应用、场景、网络和悬浮窗权限判断；无需 DexKit 扫描该功能，开关更改需重载 Google App。

Hey Google 的系统方法、模型校验和附属模块配置保持原实现；此次无需更新附属模块。

## Coogle 附属模块

整合 Google 系统应用固化、Hotword 注册组件和 DSP 唤醒支持。

开启 Hey Google 时自动检查附属模块。

## 致谢

部分代码参考 [ONextBox](https://github.com/MiToverG422/ONextBox) 和 [LuckyTool](https://github.com/luckyzyx/LuckyTool)。

UI 参考 [KernelSU](https://github.com/tiann/KernelSU) 和 [LSPosed](https://github.com/LSPosed/LSPosed)。

感谢以上项目的开发者与贡献者。

作者：**zopulus**\
开源协议：**GPL-3.0**
