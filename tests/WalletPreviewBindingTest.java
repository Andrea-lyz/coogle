package io.github.zopulus.coogle;
import android.app.Activity;
import android.app.KeyguardManager;

public final class WalletPreviewBindingTest {
    static class Unit { static final Unit renamed = new Unit(); public String toString() { return "kotlin.Unit"; } }
    static class AmbiguousUnit { static final AmbiguousUnit a = new AmbiguousUnit(), b = new AmbiguousUnit(); }
    static class RenamedRequest {
        Activity renamedActivity; KeyguardManager renamedKeyguard;
        RenamedRequest(KeyguardManager k, Activity a, Runnable first, Runnable second, Object continuation) {}
        Object renamed(Object value) { return value; }
    }
    static class WrongRequest {
        Activity activity; KeyguardManager keyguard;
        Object renamed(Object value) { return value; }
    }
    public static void main(String[] args) throws Exception {
        var method = RenamedRequest.class.getDeclaredMethod("renamed", Object.class);
        var binding = WalletPreviewBinding.bind(method, Unit.class);
        check(binding != null && binding.unit == Unit.renamed, "Renamed wallet request rejected");
        check(WalletPreviewBinding.bind(method, AmbiguousUnit.class) == null, "Ambiguous Unit accepted");
        check(WalletPreviewBinding.bind(WrongRequest.class.getDeclaredMethod("renamed",Object.class), Unit.class) == null,
                "Wrong coroutine accepted");
        System.out.println("PASS: renamed wallet coroutine/fields/Unit resolved; incompatible and ambiguous layouts rejected");
    }
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
