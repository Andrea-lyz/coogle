# Coogle

让国行版ColorOS更加Google，仅适配ColorOS17。

## 功能

- **Hey Google**：补全国行系统的 Google 语音唤醒支持。
- **实时翻译**：附属模块配置生效后自动启用，在圈定即搜中点击翻译，滚动时持续翻译；没有独立开关。安装或更新附属模块后需重启，Google App 版本不兼容时保留原行为。
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

实时翻译依据国际版一加 13 实机链路，检测系统中附属模块的配置覆盖包和已生效的 Google 提供方配置，然后自动补齐 `CONTEXTUAL_SEARCH_LIVE_TRANSLATE` 能力查询；不依赖打开 Coogle 或读取旧翻译开关，也不需要 Root/DexKit 扫描。保留 Google 原生资格、来源应用、场景、网络和悬浮窗权限判断。原生排除译文采集的修复已由国行一加 15 用户确认消除闪烁；本次自动启用改动尚待安装复测。

Hey Google 的系统方法、模型校验保持原实现，附属模块的语音组件挂载与初始化跟随 Coogle 内的 Hey Google 开关。

## Coogle 附属模块

整合 Google 系统应用固化、Hotword 注册组件、DSP 唤醒支持和原生圈选搜索提供方配置。

开启 Hey Google 时自动检查附属模块。

不提供 Action 或安装选择菜单。Google 固化与原生翻译配置作为基础支持保留，语音组件由 APK 开关同步控制；关闭 Hey Google 后，重启即停止挂载语音注册/DSP 配置，并跳过模型初始化。首次安装或从旧菜单版升级时，语音支持默认关闭，避免未与 APK 开关同步就执行初始化。更新新版 APK 和附属模块后，打开 Coogle 设置 Hey Google，再重启完成挂载；后续更新保留已经同步的语音状态。

## 致谢

部分代码参考 [ONextBox](https://github.com/MiToverG422/ONextBox) 和 [LuckyTool](https://github.com/luckyzyx/LuckyTool)。

UI 参考 [KernelSU](https://github.com/tiann/KernelSU) 和 [LSPosed](https://github.com/LSPosed/LSPosed)。

感谢以上项目的开发者与贡献者。

作者：**zopulus**\
开源协议：**GPL-3.0**
