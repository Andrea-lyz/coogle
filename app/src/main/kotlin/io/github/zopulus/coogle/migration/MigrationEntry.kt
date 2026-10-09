package io.github.zopulus.coogle.migration

import io.github.zopulus.coogle.migration.compat.ModernHookRuntime
import io.github.libxposed.api.XposedModule

/** Entry point for the Google-related features migrated from ONextBox. */
object MigrationEntry {
    @JvmStatic fun attach(module: XposedModule) = ModernHookRuntime.attach(module)
    @JvmStatic fun hookPackage(packageName: String, loader: ClassLoader) {
        if (packageName == "com.google.android.googlequicksearchbox") {
            OkGoogleHotwordCompatibilityHooker.hookGoogleApp(loader)
        } else {
            AssistantHooker.hook(packageName, loader)
        }
    }
    @JvmStatic fun hookSystem(loader: ClassLoader) {
        GmsRegionRestrictionHooker.hook(loader)
        AssistantHooker.hook("system", loader)
        OkGoogleHotwordCompatibilityHooker.hookSystemServer(loader)
    }
}
