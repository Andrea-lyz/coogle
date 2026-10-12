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
        check(invoke(hooks.callbacks.get(feature), List.of("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE",0), original).equals(true),
                "Automatic translation depended on a removed preference switch");
        for (int invalid = 0; invalid < 3; invalid++) {
            ApplicationPackageManager manager = new ApplicationPackageManager();
            if (invalid == 0) manager.mContext.manager.installed = false;
            if (invalid == 1) manager.mContext.manager.flags = 0;
            if (invalid == 2) manager.mContext.resources.provider = "other.provider";
            HookInstaller inactive = new HookInstaller();
            LiveTranslateHooks.install(inactive, DynamicGoogleHookTest.class.getClassLoader());
            check(invoke(inactive.callbacks.get(feature), List.of(LiveTranslateHooks.SYSTEM_FEATURE,0), original, manager) == original,
                    "Missing, non-system or inactive native support enabled translation");
        }
        HookInstaller delayed = new HookInstaller();
        LiveTranslateHooks.install(delayed, DynamicGoogleHookTest.class.getClassLoader());
        ApplicationPackageManager manager = new ApplicationPackageManager();
        manager.mContext = null;
        check(invoke(delayed.callbacks.get(feature), List.of(LiveTranslateHooks.SYSTEM_FEATURE,0), original, manager) == original,
                "Missing context changed native behavior");
        manager.mContext = new android.content.Context();
        check(invoke(delayed.callbacks.get(feature), List.of(LiveTranslateHooks.SYSTEM_FEATURE,0), original, manager).equals(true),
                "Missing context was cached as permanently unsupported");
        int lookups = manager.mContext.manager.reads;
        invoke(delayed.callbacks.get(feature), List.of(LiveTranslateHooks.SYSTEM_FEATURE,0), original, manager);
        check(manager.mContext.manager.reads == lookups, "Native support was repeatedly queried");
        HookInstaller failing = new HookInstaller();
        LensHooks.install(failing, List.of(create, visible));
        enabled[0] = true;
        failing.failAt = 4;
        LiveTranslateHooks.install(failing, DynamicGoogleHookTest.class.getClassLoader());
        check(failing.installed == 2 && failing.callbacks.containsKey(create) && !failing.callbacks.containsKey(capable),
                "Failed native feature exposure retained partial hooks or removed Lens hooks");
        System.out.println("PASS: automatic native translation support, preserved eligibility, version semantics and rollback");
    }
    static Object invoke(XposedInterface.Hooker hook, List<Object> args, Object original) throws Throwable {
        return invoke(hook, args, original, new ApplicationPackageManager());
    }
    static Object invoke(XposedInterface.Hooker hook, List<Object> args, Object original, ApplicationPackageManager manager) throws Throwable {
        XposedInterface.Chain chain = (XposedInterface.Chain)Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p,m,a) -> switch(m.getName()) {
                    case "getArg" -> args.get((Integer)a[0]); case "getArgs" -> args; case "getThisObject" -> manager; case "proceed" -> original;
                    default -> throw new AssertionError(m.getName());
                });
        return hook.intercept(chain);
    }
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
