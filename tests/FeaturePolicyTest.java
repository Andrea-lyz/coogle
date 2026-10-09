package io.github.zopulus.coogle;
public final class FeaturePolicyTest {
    public static void main(String[] args) {
        if (FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,true,false)) throw new AssertionError("Missing companion accepted");
        if (!FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,true,true)) throw new AssertionError("Ready companion rejected");
        if (FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,false,true)) throw new AssertionError("Disabled feature active");
        if (FeaturePolicy.defaultValue(FeaturePolicy.GMS) || FeaturePolicy.defaultValue(FeaturePolicy.SEARCH_BAR)) throw new AssertionError("New feature implicitly enabled");
        if (!FeaturePolicy.defaultValue(FeaturePolicy.LENS)) throw new AssertionError("Existing Lens default lost");
        System.out.println("PASS: feature defaults and companion requirement");
    }
}
