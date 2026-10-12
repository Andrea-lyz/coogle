package io.github.zopulus.coogle;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.List;

public final class LensHookLifecycleTest {
    public static class Talk { public boolean enabled() { return false; } }
    public static class Create { public boolean enabled() { return false; } }
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {false};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p,m,a) -> enabled[0]));
        HookInstaller off = new HookInstaller();
        LensHooks.installForApp(off, LensHookLifecycleTest.class.getClassLoader(), "missing.apk", null);
        check(off.installed == 0, "Disabled Lens installed hooks or scanned APK");
        Method talk = Talk.class.getMethod("enabled"), create = Create.class.getMethod("enabled");
        HookInstaller hooks = new HookInstaller();
        enabled[0] = true;
        LensHooks.install(hooks, List.of(talk, create));
        check(hooks.installed == 2, "Lens targets not installed");
        check(Boolean.TRUE.equals(invoke(hooks.callbacks.get(talk), false)), "Enabled Lens not exposed");
        enabled[0] = false;
        Object original = new Object();
        check(invoke(hooks.callbacks.get(create), original) == original, "Disabled Lens changed result");
        HookInstaller partial = new HookInstaller();
        partial.failAt = 2;
        LensHooks.install(partial, List.of(talk, create));
        check(partial.installed == 0 && partial.callbacks.isEmpty(), "Partial Lens installation retained hooks");
        System.out.println("PASS: Lens lazy lookup, switches and complete installation rollback");
    }
    private static Object invoke(XposedInterface.Hooker hook, Object result) throws Throwable {
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p,m,a) -> {
                    if (m.getName().equals("proceed")) return result;
                    throw new AssertionError("Unexpected chain access: " + m.getName());
                });
        return hook.intercept(chain);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
