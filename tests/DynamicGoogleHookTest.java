package io.github.zopulus.coogle;
import android.content.SharedPreferences;
import android.app.ApplicationPackageManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

public final class DynamicGoogleHookTest {
    public static class Capability { public boolean renamed() { return false; } }
    public static class Action {
        public Action(Object a, Capability b, Object c) {}
        public boolean renamed() { return false; }
    }
    public static class Create { public boolean renamed() { return false; } }
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {true};
        List<String> reads = new ArrayList<>();
        FeatureConfig.attach((SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p,m,a) -> { reads.add((String)a[0]); return enabled[0]; }));
        Method capable = Capability.class.getMethod("renamed");
        Method visible = Action.class.getMethod("renamed");
        Method create = Create.class.getMethod("renamed");
        HookInstaller hooks = new HookInstaller();
        LensHooks.install(hooks, List.of(create, visible));
        check(hooks.installed == 2, "Lens hooks not installed");
        LiveTranslateHooks.install(hooks, DynamicGoogleHookTest.class.getClassLoader(), List.of(capable, visible));
        check(hooks.installed == 4 && hooks.constructorsDeoptimized == 1, "Live translate hooks or constructor deoptimization missing");
        Method feature = ApplicationPackageManager.class.getMethod("hasSystemFeature",String.class,int.class);
        check(invoke(hooks.callbacks.get(capable), List.of(), false).equals(true), "Capability not exposed");
        reads.clear();
        check(invoke(hooks.callbacks.get(feature), List.of("other.feature",0), false).equals(false) && reads.isEmpty(),
                "Unrelated query altered or read configuration");
        check(invoke(hooks.callbacks.get(feature), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE",0), false).equals(true),
                "Live feature not exposed");
        enabled[0] = false;
        Object original = new Object();
        check(invoke(hooks.callbacks.get(create), List.of(), original) == original, "Disabled Lens changed original result");
        check(invoke(hooks.callbacks.get(capable), List.of(), original) == original, "Disabled translation changed original result");
        HookInstaller failing = new HookInstaller();
        LensHooks.install(failing, List.of(create, visible));
        failing.failAt = 4;
        LiveTranslateHooks.install(failing, DynamicGoogleHookTest.class.getClassLoader(), List.of(capable, visible));
        check(failing.installed == 2 && failing.callbacks.containsKey(create) && !failing.callbacks.containsKey(capable),
                "Failed translation retained partial hooks or removed Lens hooks");
        System.out.println("PASS: dynamic Google hooks, disabled bypass, feature isolation, constructor deoptimization and rollback");
    }
    static Object invoke(XposedInterface.Hooker hook, List<Object> args, Object original) throws Throwable {
        XposedInterface.Chain chain = (XposedInterface.Chain)Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p,m,a) -> switch(m.getName()) {
                    case "getArg" -> args.get((Integer)a[0]); case "getArgs" -> args; case "proceed" -> original;
                    default -> throw new AssertionError(m.getName());
                });
        return hook.intercept(chain);
    }
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
