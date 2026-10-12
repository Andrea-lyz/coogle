package io.github.zopulus.coogle;

/** Shares one lazy scan between the enabled Google integrations; failures remain isolated. */
final class GoogleAppHooks {
    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        boolean lens = FeatureConfig.enabled(FeaturePolicy.LENS);
        boolean translate = FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE);
        if (!lens && !translate) return;
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            if (lens) {
                try { LensHooks.install(hooks, targets.resolve(FeaturePolicy.LENS)); }
                catch (Throwable failure) { hooks.error("LENS lookup failed; original behavior retained", failure); }
            }
            if (translate) {
                try { LiveTranslateHooks.install(hooks, loader, targets.resolve(FeaturePolicy.LIVE_TRANSLATE)); }
                catch (Throwable failure) { hooks.error("LIVE_TRANSLATE lookup failed; original behavior retained", failure); }
            }
        } catch (Throwable failure) { hooks.error("Google dynamic lookup unavailable; original behavior retained", failure); }
    }
    private GoogleAppHooks() {}
}
