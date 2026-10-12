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
        LiveTranslateHooks.install(hooks, DynamicGoogleHookTest.class.getClassLoader());
        check(!hooks.callbacks.containsKey(capable) && hooks.constructorsDeoptimized == 0 && hooks.installed == 4,
                "Native live-translate eligibility or constructor was altered");
        Method feature = ApplicationPackageManager.class.getMethod("hasSystemFeature",String.class,int.class);
        Method simple = ApplicationPackageManager.class.getMethod("hasSystemFeature",String.class);
        check(invoke(hooks.callbacks.get(simple), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE"), false).equals(true),
                "Unversioned native feature not exposed");
        reads.clear();
        check(invoke(hooks.callbacks.get(feature), List.of("other.feature",0), false).equals(false) && reads.isEmpty(),
                "Unrelated query altered or read configuration");
        check(invoke(hooks.callbacks.get(feature), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE",0), false).equals(true),
                "Live feature not exposed");
        check(invoke(hooks.callbacks.get(feature), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE",1), false).equals(false),
                "Version-zero XML feature accepted a newer requested version");
        enabled[0] = false;
        Object original = new Object();
        check(invoke(hooks.callbacks.get(create), List.of(), original) == original, "Disabled Lens changed original result");
        check(invoke(hooks.callbacks.get(feature), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE",0), original) == original,
                "Disabled translation changed original feature result");
        HookInstaller failing = new HookInstaller();
        LensHooks.install(failing, List.of(create, visible));
        enabled[0] = true;
        failing.failAt = 4;
        LiveTranslateHooks.install(failing, DynamicGoogleHookTest.class.getClassLoader());
        check(failing.installed == 2 && failing.callbacks.containsKey(create) && !failing.callbacks.containsKey(capable),
                "Failed native feature exposure retained partial hooks or removed Lens hooks");
        System.out.println("PASS: native live-translate feature, preserved eligibility, version semantics, disabled bypass and rollback");
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
