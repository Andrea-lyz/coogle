package io.github.zopulus.coogle;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import android.content.Context;
import android.content.pm.ApplicationInfo;

/** Mirrors the feature declared by CPH2653's feature_contextualsearch.xml.
 * Google App duxd reads this feature; its native eligibility and execution remain intact.
 */
final class LiveTranslateHooks {
    static final String SYSTEM_FEATURE = "com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE";

    static void install(HookInstaller hooks, ClassLoader loader) {
        int mark = hooks.mark();
        try {
            Class<?> pm = Class.forName("android.app.ApplicationPackageManager", false, loader);
            Field contextField = pm.getDeclaredField("mContext");
            contextField.setAccessible(true);
            NativeSupport support = new NativeSupport(contextField);
            Method simple = pm.getDeclaredMethod("hasSystemFeature", String.class);
            Method versioned = pm.getDeclaredMethod("hasSystemFeature", String.class, int.class);
            for (Method method : new Method[]{simple, versioned}) {
                boolean versionedQuery = method.getParameterCount() == 2;
                hooks.install(method, chain -> {
                    if (!SYSTEM_FEATURE.equals(chain.getArg(0)))
                        return chain.proceed();
                    // The original XML declares version 0. Keep newer version requests native.
                    if (versionedQuery && ((Integer)chain.getArg(1)) > 0) return chain.proceed();
                    return support.ready(chain.getThisObject()) ? Boolean.TRUE : chain.proceed();
                });
            }
            hooks.info("LIVE_TRANSLATE automatic native-support detection installed; Google eligibility retained");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LIVE_TRANSLATE system feature unavailable; original behavior retained", failure);
        }
    }
    private static final class NativeSupport {
        private final Field contextField;
        private volatile Boolean ready;
        NativeSupport(Field contextField) { this.contextField = contextField; }
        boolean ready(Object manager) throws IllegalAccessException {
            if (ready != null) return ready;
            Context context = manager == null ? null : (Context)contextField.get(manager);
            if (context == null) return false;
            try {
                ApplicationInfo overlay = context.getPackageManager().getApplicationInfo(
                        "io.github.zopulus.coogle.overlay.contextualsearch", 0);
                int id = context.getResources().getIdentifier("config_defaultContextualSearchPackageName", "string", "android");
                ready = (overlay.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && id != 0
                        && "com.google.android.googlequicksearchbox".equals(context.getResources().getString(id));
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
                ready = false;
            }
            return ready;
        }
    }
    private LiveTranslateHooks() {}
}
