package io.github.zopulus.coogle;

import android.content.Context;
import android.content.pm.PackageManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Exercises the compiled production hook, including its early-boot context transition. */
public final class HotwordUidTest {
    public static void main(String[] args) throws Exception {
        Class<?> hook = Class.forName("io.github.zopulus.coogle.migration.OkGoogleHotwordCompatibilityHooker");
        Object instance = hook.getField("INSTANCE").get(null);
        Field context = hook.getDeclaredField("systemContext");
        Field cache = hook.getDeclaredField("googleUidCache");
        Method lookup = hook.getDeclaredMethod("isGoogleUid", int.class);
        context.setAccessible(true);
        cache.setAccessible(true);
        lookup.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();
        context.set(null, null);
        if ((boolean) lookup.invoke(instance, 10001)) throw new AssertionError("Unknown UID granted before context");
        final int[] calls = {0};
        PackageManager packages = new PackageManager() {
            @Override public String[] getPackagesForUid(int uid) {
                calls[0]++;
                return new String[]{uid == 10001 ? "com.google.android.googlequicksearchbox" : "com.example.other"};
            }
        };
        context.set(null, new Context() {
            @Override public PackageManager getPackageManager() { return packages; }
        });
        if (!(boolean) lookup.invoke(instance, 10001)) throw new AssertionError("Early-boot failure permanently cached");
        if (!(boolean) lookup.invoke(instance, 10001)) throw new AssertionError("Verified Google UID rejected");
        if ((boolean) lookup.invoke(instance, 10002)) throw new AssertionError("Other app granted hotword access");
        if ((boolean) lookup.invoke(instance, 10002)) throw new AssertionError("Other app cached incorrectly");
        if ((boolean) lookup.invoke(instance, -1)) throw new AssertionError("Invalid UID accepted");
        if (calls[0] != 2) throw new AssertionError("Resolved UID cache not used: " + calls[0]);
        System.out.println("PASS: hotword UID lookup recovers after context becomes available; other apps remain rejected");
    }
}
