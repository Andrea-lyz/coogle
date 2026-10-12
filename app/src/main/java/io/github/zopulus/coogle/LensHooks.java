package io.github.zopulus.coogle;

import java.lang.reflect.Method;
import java.util.List;

final class LensHooks {
    static void install(HookInstaller hooks, List<Method> targets) {
        if (targets.size() < 2) { hooks.info("LENS skip: eligibility targets not uniquely found"); return; }
        int mark = hooks.mark();
        try {
            for (int i = 0; i < 2; i++) {
                hooks.install(targets.get(i), chain -> FeatureConfig.enabled(FeaturePolicy.LENS) ? true : chain.proceed());
            }
            for (int i = 2; i < targets.size(); i++) hooks.deoptimize(targets.get(i));
            hooks.info("LENS Talk + Create dynamic eligibility hooks installed; callers=" + (targets.size() - 2));
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LENS incompatible targets; eligibility hooks removed", failure);
        }
    }
    private LensHooks() {}
}
