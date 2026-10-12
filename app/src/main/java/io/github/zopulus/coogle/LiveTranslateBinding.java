package io.github.zopulus.coogle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Validates the action shape used by XGoogle before any hook is installed. */
final class LiveTranslateBinding {
    final Method visible;
    final Method capable;

    private LiveTranslateBinding(Method visible, Method capable) {
        this.visible = visible;
        this.capable = capable;
    }

    static boolean isFeature(Object name) {
        return "com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE".equals(name);
    }

    static LiveTranslateBinding bind(Class<?> action) {
        Constructor<?> only = null;
        for (Constructor<?> constructor : action.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 3) continue;
            if (only != null) return null;
            only = constructor;
        }
        if (only == null) return null;
        Method visible = booleanGetter(action);
        Method capable = booleanGetter(only.getParameterTypes()[1]);
        return visible == null || capable == null ? null : new LiveTranslateBinding(visible, capable);
    }

    private static Method booleanGetter(Class<?> type) {
        Method found = null;
        for (Method method : type.getDeclaredMethods()) {
            if (!LensBinding.isPredicate(method)) continue;
            if (found != null) return null;
            found = method;
        }
        if (found != null) found.setAccessible(true);
        return found;
    }
}
