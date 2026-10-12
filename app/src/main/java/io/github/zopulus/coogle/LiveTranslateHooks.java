package io.github.zopulus.coogle;

import java.lang.reflect.Method;

/** Mirrors the feature declared by CPH2653's feature_contextualsearch.xml.
 * Google App duxd reads this feature; its native eligibility and execution remain intact.
 */
final class LiveTranslateHooks {
    static final String SYSTEM_FEATURE = "com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE)) return;
        int mark = hooks.mark();
        try {
            Class<?> pm = Class.forName("android.app.ApplicationPackageManager", false, loader);
            Method simple = pm.getDeclaredMethod("hasSystemFeature", String.class);
            Method versioned = pm.getDeclaredMethod("hasSystemFeature", String.class, int.class);
            for (Method method : new Method[]{simple, versioned}) {
                hooks.install(method, chain -> {
                    if (!SYSTEM_FEATURE.equals(chain.getArg(0)) || !FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE))
                        return chain.proceed();
                    // The original XML declares version 0. Keep newer version requests native.
                    return method.getParameterCount() == 1 || ((Integer)chain.getArg(1)) <= 0
                            ? Boolean.TRUE : chain.proceed();
                });
            }
            hooks.info("LIVE_TRANSLATE native system feature exposed; Google eligibility retained");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LIVE_TRANSLATE system feature unavailable; original behavior retained", failure);
        }
    }
    private LiveTranslateHooks() {}
}
