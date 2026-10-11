# 构建

APK：JDK 17，Android SDK Platform 37.0 / Build Tools 36.0.0。配置 JAVA_HOME 和 ANDROID_HOME（或 local.properties），运行 ./build.ps1。仅构建 arm64-v8a，release 开启 R8 与资源压缩。

附属模块：Python 3，运行 python companion/build_combined.py，产物在 build/Coogle-Companion-1.2.zip。更新后复制到 app/src/main/assets/coogle-companion.zip 再构建 APK。

APK 当前使用本机 Android debug keystore 签名，不同计算机的默认签名不相同。
