package io.github.zopulus.coogle;
import java.lang.reflect.Method;
import java.util.List;
import com.google.apps.tiktok.account.AccountId;

public final class LensBindingTest {
    static class Gate {}
    static class RenamedCreate {
        RenamedCreate(AccountId account, Gate gate, boolean enabled, long mode) {}
        boolean renamed() { return false; }
    }
    static class RenamedTalk {
        RenamedTalk(Object provider, Object caller, Object scope, boolean one, boolean two, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Wrong { boolean renamed() { return false; } }
    static class DuplicateCreate {
        DuplicateCreate(AccountId account, Gate gate, boolean enabled, long mode) {}
        boolean renamed() { return false; }
    }
    static class CombinedController {
        CombinedController(AccountId account, Gate gate, boolean enabled, long mode) {}
        CombinedController(Object provider, Object caller, Object scope, boolean one, boolean two, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    public static void main(String[] args) throws Exception {
        Method create = RenamedCreate.class.getDeclaredMethod("renamed");
        Method talk = RenamedTalk.class.getDeclaredMethod("renamed");
        Method wrong = Wrong.class.getDeclaredMethod("renamed");
        Method[] result = LensBinding.select(List.of(wrong, talk, create), Gate.class);
        check(result != null && result[0].equals(create) && result[1].equals(talk), "Renamed targets not selected");
        check(LensBinding.isPair(create, talk) && !LensBinding.isPair(talk, create), "Cached target order not validated");
        check(LensBinding.select(List.of(create, talk, DuplicateCreate.class.getDeclaredMethod("renamed")), Gate.class) == null,
                "Ambiguous create accepted");
        check(LensBinding.select(List.of(create, wrong), Gate.class) == null, "Missing talk accepted");
        check(LensBinding.select(List.of(create, talk), Wrong.class) == null, "Wrong gate accepted");
        Method combined = CombinedController.class.getDeclaredMethod("renamed");
        check(LensBinding.select(List.of(combined), Gate.class) == null,
                "One method accepted as both Lens targets");
        System.out.println("PASS: renamed Lens targets selected; ambiguous/missing/mismatched targets rejected");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
