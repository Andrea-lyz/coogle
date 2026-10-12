package io.github.zopulus.coogle;

public final class LiveTranslateBindingTest {
    static class Capability { public boolean a() { return false; } }
    static class Action {
        Action(Object first, Capability second, Object third) {}
        public boolean i() { return false; }
    }
    static class Ambiguous extends Action {
        Ambiguous(Object a, Capability b, Object c) { super(a,b,c); }
        Ambiguous(String a, Capability b, String c) { super(a,b,c); }
        public boolean i() { return false; }
    }
    static class StaticCapability { public static boolean a() { return false; } }
    static class WrongAction {
        WrongAction(Object a, StaticCapability b, Object c) {}
        public boolean i() { return false; }
    }
    static class RenamedCapability { public boolean renamed() { return false; } }
    static class RenamedAction {
        RenamedAction(Object first, RenamedCapability second, Object third) {}
        public boolean renamed() { return false; }
    }
    public static void main(String[] args) throws Exception {
        if (LiveTranslateBinding.bind(Action.class) == null) throw new AssertionError("Valid action rejected");
        if (LiveTranslateBinding.bind(RenamedAction.class) == null) throw new AssertionError("Renamed action rejected");
        if (LiveTranslateBinding.bind(Ambiguous.class) != null) throw new AssertionError("Ambiguous action accepted");
        if (LiveTranslateBinding.bind(WrongAction.class) != null) throw new AssertionError("Static capability accepted");
        if (LiveTranslateBinding.bind(Object.class) != null) throw new AssertionError("Unknown action accepted");
        if (LiveTranslateBinding.isFeature("unrelated") || LiveTranslateBinding.isFeature(null))
            throw new AssertionError("Unrelated feature overridden");
        if (!LiveTranslateBinding.isFeature("com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE"))
            throw new AssertionError("Live translation feature missing");
        System.out.println("PASS: live translate binding rejects incompatible/ambiguous targets and filters features");
    }
}
