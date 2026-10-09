package io.github.zopulus.coogle;

import io.github.zopulus.coogle.migration.MigrationEntry;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class CoogleModule extends XposedModule {
    private final HookInstaller hooks = new HookInstaller(this);
    private final NavigationHooks navigation = new NavigationHooks(hooks);

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        MigrationEntry.attach(this);
        try { FeatureConfig.attach(getRemotePreferences(FeatureConfig.GROUP)); }
        catch (Throwable failure) { hooks.error("Configuration unavailable; verified defaults retained",failure); }
        hooks.info("Coogle " + BuildConfig.VERSION_NAME + " loaded in " + param.getProcessName() + "; framework=" + getFrameworkName()
                + " API=" + getApiVersion());
    }

    @Override public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        if (!SystemCompatibility.isColorOs17()) return;
        MigrationEntry.attach(this);
        MigrationEntry.hookSystem(param.getClassLoader());
        SignalModelHooks.install(hooks, param.getClassLoader());
        hooks.info("SYSTEM native assistant + Google hotword hooks installed");
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SystemCompatibility.isColorOs17()) return;
        if ("com.android.launcher".equals(packageName)) SearchBarHooks.install(hooks, param.getClassLoader());
        if ("com.android.settings".equals(packageName)) SettingsHooks.installGoogleSettingsEntry(hooks, param.getClassLoader());
        if ("com.android.systemui".equals(packageName) || "com.android.settings".equals(packageName)
                || "com.android.launcher".equals(packageName)
                || "com.google.android.googlequicksearchbox".equals(packageName)) {
            MigrationEntry.attach(this);
            MigrationEntry.hookPackage(packageName, param.getClassLoader());
            if ("com.google.android.googlequicksearchbox".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.LENS)) LensHooks.install(hooks, param.getClassLoader());
            if ("com.android.settings".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) SettingsHooks.installSettingsHiddenSupport(hooks, param.getClassLoader());
        }
        if ("com.android.launcher".equals(packageName)) navigation.install(param.getClassLoader());
    }

}
