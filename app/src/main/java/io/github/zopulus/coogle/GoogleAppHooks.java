package io.github.zopulus.coogle;

/** Native feature exposure does not depend on Lens dex scanning. */
final class GoogleAppHooks {
    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        LiveTranslateHooks.install(hooks, loader);
        if (!FeatureConfig.enabled(FeaturePolicy.LENS)) return;
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            LensHooks.install(hooks, targets.resolve(FeaturePolicy.LENS));
        } catch (Throwable failure) { hooks.error("LENS lookup failed; original behavior retained", failure); }
    }
    private GoogleAppHooks() {}
}
