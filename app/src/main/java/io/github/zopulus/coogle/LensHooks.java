package io.github.zopulus.coogle;

import java.lang.reflect.Method;
import java.util.List;

final class LensHooks {
    static void installForApp(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.LENS)) return;
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            install(hooks, targets.resolve(FeaturePolicy.LENS));
        } catch (Throwable failure) { hooks.error("LENS lookup failed; original behavior retained", failure); }
    }
    static void install(HookInstaller hooks, List<Method> targets) {
        if (targets.size() < 2) { hooks.info("LENS skip: eligibility targets not uniquely found"); return; }
        int mark = hooks.mark();
        try {
            for (int i = 0; i < 2; i++) {
                hooks.install(targets.get(i), chain -> FeatureConfig.enabled(FeaturePolicy.LENS) ? true : chain.proceed());
            }
            for (int i = 2; i < targets.size(); i++) hooks.deoptimize(targets.get(i));
            hooks.info("LENS Talk + Create dynamic eligibility hooks installed; callers=" + (targets.size() - 2));
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LENS incompatible targets; eligibility hooks removed", failure);
        }
    }
    private LensHooks() {}
}
