// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.zopulus.coogle.migration

import android.content.Context
import io.github.zopulus.coogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect as XposedHelpers
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.zopulus.coogle.migration.AssistantRuntime.LAUNCHER_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.findContext
import io.github.zopulus.coogle.migration.AssistantRuntime.resolveDefaultAssistant
import io.github.zopulus.coogle.migration.AssistantRuntime.isGestureCornerAssistantEnabled
import io.github.zopulus.coogle.migration.AssistantRuntime.log
import io.github.zopulus.coogle.migration.AssistantRuntime.installedHooks

internal object AssistantLauncherHooks {
    /** Launcher gesture-bar assistant routing. */
    fun hookLauncher(classLoader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        hookContextualSearchSystemFeature(LAUNCHER_PACKAGE, classLoader)
        hookLauncherAssistantAvailability(classLoader)

        val featureClass = XposedHelpers.findClassIfExists(
            "com.android.common.config.FeatureOption",
            classLoader,
        ) ?: return
        val key = "launcher:native-circle-feature-option"
        if (!installedHooks.add(key)) return
        runCatching {
            // ProbeModule owns the Circle to Search gates and their caller deoptimization.
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportGemini",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                            param.result = true
                        }
                    }
                },
            )
            log("Enabled the ColorOS 17 launcher Gemini feature gate")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to enable the ColorOS 17 launcher Circle to Search route", it)
        }
        // Standalone CN17 build retains native e(93, Context) and its recents-animation wait.
        // The upstream override uses firmware-specific proxy names and is unnecessary here.
    }
    /** Assistant availability reporting. */
    private fun hookLauncherAssistantAvailability(classLoader: ClassLoader) {
        val serviceClass = XposedHelpers.findClassIfExists(
            "com.android.quickstep.TouchInteractionService",
            classLoader,
        ) ?: return
        sequenceOf(serviceClass, *serviceClass.declaredClasses)
            .flatMap { it.declaredMethods.asSequence() }
            .filter { method ->
                method.name == "onAssistantAvailable" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == java.lang.Boolean.TYPE
            }
            .forEach { method ->
                val key = "launcher:assistant-available:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                val context = findContext(param.thisObject) ?: return
                                if (!isGestureCornerAssistantEnabled(context)) return
                                if (resolveDefaultAssistant(context) == null) return
                                param.args[0] = true
                            }
                        },
                    )
                    log("Restored the C17 launcher corner-assistant availability signal")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to restore launcher assistant availability", it)
                }
            }
    }
}
