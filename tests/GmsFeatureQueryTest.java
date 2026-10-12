package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.server.pm.PackageManagerService;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public final class GmsFeatureQueryTest {
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {false};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> enabled[0]));
        Class<?> type = Class.forName("io.github.zopulus.coogle.migration.GmsRegionRestrictionHooker");
        Object instance = type.getField("INSTANCE").get(null);
        Method install = type.getMethod("hook", ClassLoader.class);
        Method query = PackageManagerService.class.getMethod("hasSystemFeature", String.class, int.class);
        check(!(boolean) install.invoke(instance, GmsFeatureQueryTest.class.getClassLoader()), "Disabled feature installed");
        check(!ModernHookRuntime.INSTANCE.module.callbacks.containsKey(query), "Disabled feature left callback");
        enabled[0] = true;
        check((boolean) install.invoke(instance, GmsFeatureQueryTest.class.getClassLoader()), "Enabled feature not installed");
        var hook = ModernHookRuntime.INSTANCE.module.callbacks.get(query);
        for (String name : new String[]{"cn.google.services", "com.google.android.feature.services_updater"}) {
            int[] calls = {0};
            check(Boolean.FALSE.equals(invoke(hook, query, name, true, null, calls)), "Blocked feature exposed");
            check(calls[0] == 0, "Blocked feature dispatched unnecessary native query");
        }
        for (String name : new String[]{"android.hardware.nfc", null}) {
            for (boolean value : new boolean[]{false, true}) {
                int[] calls = {0};
                check(Boolean.valueOf(value).equals(invoke(hook, query, name, value, null, calls)), "Native result changed");
                check(calls[0] == 1, "Unrelated feature not dispatched exactly once");
            }
        }
        Exception failure = new Exception("native query failed");
        try {
            invoke(hook, query, "other", false, failure, new int[]{0});
            throw new AssertionError("Native failure swallowed");
        } catch (Exception caught) { check(caught == failure, "Native failure replaced"); }
        Method filter = type.getDeclaredMethod("filterFeatureArray", Object.class);
        filter.setAccessible(true);
        var ordinary = new android.content.pm.FeatureInfo("android.hardware.nfc");
        var unfiltered = new android.content.pm.FeatureInfo[]{ordinary, null};
        check(filter.invoke(instance, (Object) unfiltered) == unfiltered, "Unchanged array copied");
        var original = new android.content.pm.FeatureInfo[]{ordinary,
                new android.content.pm.FeatureInfo("cn.google.services"), null,
                new android.content.pm.FeatureInfo("com.google.android.feature.services_updater")};
        var filtered = (android.content.pm.FeatureInfo[]) filter.invoke(instance, (Object) original);
        check(filtered.length == 2 && filtered[0] == ordinary && filtered[1] == null,
                "Typed array filter lost unrelated entries or order");
        check(original.length == 4 && original[1] != null, "Original array modified");
        check(filter.invoke(instance, new Object[]{null}) == null, "Null array changed");
        System.out.println("PASS: GMS query disabled gate, exact feature masking, native results and exceptions");
    }
    private static Object invoke(XposedInterface.Hooker hook, Method query, String name, boolean nativeValue,
                                 Exception failure, int[] calls) throws Throwable {
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p, m, a) -> switch (m.getName()) {
                    case "getExecutable" -> query;
                    case "getArgs" -> Arrays.asList(name, 0);
                    case "proceed" -> { calls[0]++; if (failure != null) throw failure; yield nativeValue; }
                    default -> throw new AssertionError("Unexpected query access: " + m.getName());
                });
        return hook.intercept(chain);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
