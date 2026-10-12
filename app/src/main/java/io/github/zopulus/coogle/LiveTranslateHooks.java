// Adapted from Leekin2/XGoogle, commit c7f0c6f369a2076d2957ca7a1f63b95f7d784e82 (GPL-3.0).
package io.github.zopulus.coogle;

import java.lang.reflect.Method;
import java.util.List;

/** Native Circle to Search scroll translation; no touch listener or translation service. */
final class LiveTranslateHooks {
    static void install(HookInstaller hooks, ClassLoader loader, List<Method> targets) {
        if (targets.size() != 2) { hooks.info("LIVE_TRANSLATE skip: action not uniquely found"); return; }
        int mark = hooks.mark();
        try {
            hooks.install(targets.get(0), chain ->
                    FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE) ? Boolean.TRUE : chain.proceed());
            // The action constructor caches the capability; invalidate inlined copies there too.
            for (var constructor : targets.get(1).getDeclaringClass().getDeclaredConstructors())
                hooks.deoptimize(constructor);
            hooks.deoptimize(targets.get(1));
            Class<?> pm = Class.forName("android.app.ApplicationPackageManager", false, loader);
            hooks.install(pm.getDeclaredMethod("hasSystemFeature", String.class, int.class), chain ->
                    LiveTranslateBinding.isFeature(chain.getArg(0)) && FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE)
                            ? Boolean.TRUE : chain.proceed());
            hooks.info("LIVE_TRANSLATE dynamic capability hook installed");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LIVE_TRANSLATE incompatible targets; hooks removed", failure);
        }
    }
    private LiveTranslateHooks() {}
}
