// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.zopulus.coogle.migration

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import io.github.zopulus.coogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect as XposedHelpers
import java.util.ArrayDeque
import io.github.zopulus.coogle.migration.AssistantHooker.hook
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookNativeAssistantFeatureRoute
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookGoogleAssistantAppFeature
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.forceStaticBoolean
import io.github.zopulus.coogle.migration.AssistantPowerHooks.isPowerAssistantEnabled
import io.github.zopulus.coogle.migration.ContextualSearchHooks.triggerCircleToSearchC17
import io.github.zopulus.coogle.migration.AssistantRuntime.SYSTEM_UI_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT
import io.github.zopulus.coogle.migration.AssistantRuntime.SECURE_ASSISTANT_TUTORIAL_STARTED
import io.github.zopulus.coogle.migration.AssistantRuntime.INVOCATION_TYPE_GESTURE_CORNER_ASSISTANT
import io.github.zopulus.coogle.migration.AssistantRuntime.INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS
import io.github.zopulus.coogle.migration.AssistantRuntime.INVOCATION_TYPE_CIRCLE_TO_SEARCH
import io.github.zopulus.coogle.migration.AssistantRuntime.INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE
import io.github.zopulus.coogle.migration.AssistantRuntime.findContext
import io.github.zopulus.coogle.migration.AssistantRuntime.findSystemContext
import io.github.zopulus.coogle.migration.AssistantRuntime.resolveDefaultAssistant
import io.github.zopulus.coogle.migration.AssistantRuntime.isGestureCircleToSearchEnabled
import io.github.zopulus.coogle.migration.AssistantRuntime.isGestureCornerAssistantEnabled
import io.github.zopulus.coogle.migration.AssistantRuntime.log
import io.github.zopulus.coogle.migration.AssistantRuntime.installedHooks
import io.github.zopulus.coogle.migration.AssistantRuntime.systemContext

internal object AssistantSystemUiHooks {
    fun hookSystemUi(classLoader: ClassLoader) {
        hookSystemUiAttachRetry()
        hookSystemUiExperimentalRoutes(classLoader)
    }
    /** Contextual-search permission and feature gates. */
    private fun hookSystemUiExperimentalRoutes(classLoader: ClassLoader) {
        val nativeCircleEnabled = LspConfig.isAssistantNativeCircleEnabledXposed()
        hookNativeAssistantFeatureRoute(SYSTEM_UI_PACKAGE, classLoader)
        hookSystemUiBreenoAssistantFeature(classLoader)
        hookGoogleAssistantAppFeature(classLoader)
        hookSystemUiAssistantTutorialState(classLoader)
        if (nativeCircleEnabled) {
            hookContextualSearchSystemFeature(SYSTEM_UI_PACKAGE, classLoader)
            hookNativeCircleSystemUi(classLoader)
            hookSystemUiCircleToSearchInterceptor(classLoader)
        }
        hookNativeAssistantSystemUiGate(classLoader)
    }
    /** Keep the international one-time tutorial complete instead of resetting it on every radio change. */
    private fun hookSystemUiAssistantTutorialState(classLoader: ClassLoader) {
        val observerClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.shutdown.observer.AssistSettingObserver",
            classLoader,
        )
        if (observerClass != null) {
            val key = "systemui:assistant-tutorial-observer"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        observerClass,
                        "onChange",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to suppress the domestic tutorial reset", it)
                }
            }
        }

        val managerClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.shutdown.AssistManagerImpl",
            classLoader,
        ) ?: return
        val key = "systemui:assistant-tutorial-migration"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllConstructors(
                managerClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context
                            ?: findContext(param.thisObject)
                            ?: return
                        val resolver = context.contentResolver
                        val stored = Settings.Secure.getString(
                            resolver,
                            SECURE_ASSISTANT_TUTORIAL_STARTED,
                        )
                        if (stored == "0" &&
                            Settings.System.getInt(
                                resolver,
                                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                                1,
                            ) != 0 &&
                            resolveDefaultAssistant(context) != null
                        ) {
                            Settings.Secure.putInt(
                                resolver,
                                SECURE_ASSISTANT_TUTORIAL_STARTED,
                                1,
                            )
                            log("Migrated the already-seen C17 assistant tutorial state")
                        }
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to migrate the assistant tutorial state", it)
        }
    }
    private fun hookSystemUiBreenoAssistantFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.ShutdownFeatureOption",
            classLoader,
        ) ?: return
        val key = "systemui:disable-breeno-power-assistant"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isBreenoAsVoiceAssistant",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                },
            )
            log("Disabled the cached Breeno power-button assistant route in SystemUI")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to disable the Breeno power-button assistant route in SystemUI", it)
        }
    }
    private fun hookNativeAssistantSystemUiGate(classLoader: ClassLoader) {
        val assistManagerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.assist.AssistManager",
            classLoader,
        )
        if (assistManagerClass != null) {
            val key = "systemui:native-assistant-route-scope"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        assistManagerClass,
                        "startAssist\$1",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val enabled = isEnabledNativeInvocation(param.args.firstOrNull())
                                val frames = nativeAssistantRouteFrames.get()
                                    ?: ArrayDeque<Boolean>().also(nativeAssistantRouteFrames::set)
                                frames.addLast(enabled)
                                if (!enabled) return
                                nativeAssistantRouteDepth.set(
                                    (nativeAssistantRouteDepth.get() ?: 0) + 1,
                                )
                            }

                            override fun afterHookedMethod(param: MethodHookParam) {
                                val frames = nativeAssistantRouteFrames.get() ?: return
                                val enabled = frames.pollLast() ?: false
                                if (frames.isEmpty()) nativeAssistantRouteFrames.remove()
                                if (!enabled) return
                                val nextDepth = (nativeAssistantRouteDepth.get() ?: 1) - 1
                                if (nextDepth > 0) {
                                    nativeAssistantRouteDepth.set(nextDepth)
                                } else {
                                    nativeAssistantRouteDepth.remove()
                                }
                            }
                        },
                    )
                    log("Hooked the ColorOS 17 SystemUI native assistant route")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to scope the ColorOS 17 native assistant route", it)
                }
            }
        }

        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.FeatureOption",
            classLoader,
        ) ?: return
        val key = "systemui:native-assistant-exp-gate"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isExpRegion",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if ((nativeAssistantRouteDepth.get() ?: 0) > 0) {
                            param.result = true
                        }
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to pass the ColorOS 17 assistant region gate", it)
        }
    }
    private fun isEnabledNativeInvocation(argument: Any?): Boolean {
        val invocationType = (argument as? Bundle)?.getInt("invocation_type", 0) ?: return false
        val context = systemContext ?: findSystemContext()
        return when (invocationType) {
            INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS ->
                    isPowerAssistantEnabled(context)

            INVOCATION_TYPE_GESTURE_CORNER_ASSISTANT ->
                LspConfig.isAssistantNativeCircleEnabledXposed() &&
                    isGestureCornerAssistantEnabled(context)

            INVOCATION_TYPE_CIRCLE_TO_SEARCH,
            INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE,
            -> LspConfig.isAssistantNativeCircleEnabledXposed() &&
                isGestureCircleToSearchEnabled(context)

            else -> false
        }
    }
    private fun hookSystemUiCircleToSearchInterceptor(classLoader: ClassLoader) {
        val managerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.navigationbar.otherbusiness.circlesearch." +
                "OplusCircleToSearchManagerEx",
            classLoader,
        ) ?: return
        val key = "systemui:native-circle-interceptor"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                managerClass,
                "interceptStartAssistInternal",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                        val context = findContext(param.thisObject) ?: systemContext
                        if (!isGestureCircleToSearchEnabled(context)) return
                        val bundle = param.args.firstOrNull() as? Bundle ?: return
                        val invocationType = bundle.getInt("invocation_type", 0)
                        if (invocationType != INVOCATION_TYPE_CIRCLE_TO_SEARCH &&
                            invocationType != INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE
                        ) return

                        val triggered = triggerCircleToSearchC17()
                        log(
                            "C17 contextual-search interception for invocation type " +
                                "$invocationType: $triggered",
                        )
                        if (triggered) {
                            param.result = true
                        }
                    }
                },
            )
            log("Hooked the C17 SystemUI contextual-search interceptor")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the C17 SystemUI contextual-search interceptor", it)
        }
    }
    private fun hookNativeCircleSystemUi(classLoader: ClassLoader) {
        val customizeClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.CustomizeFeatureOption",
            classLoader,
        )
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.FeatureOption",
            classLoader,
        )
        if (customizeClass != null) {
            forceStaticBoolean(customizeClass, "sIsSupportCircleToSearch", true)
        }
        if (featureClass != null && customizeClass != null) {
            val key = "systemui:native-circle-feature-option"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        featureClass,
                        "init",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                                    forceStaticBoolean(
                                        customizeClass,
                                        "sIsSupportCircleToSearch",
                                        true,
                                    )
                                }
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to keep native Circle to Search enabled", it)
                }
            }
        }

        // On a phone with the normal visible gesture handle, C17's launcher CUI consumer only
        // owns touch interception/animation. SystemUI's HomeHandleTouchEvent still owns the
        // actual long-press recognition. Keep that stock recognizer alive and replace only its
        // domestic Breeno destination with the same invocation_type=8 AssistManager route used
        // by the international launcher branch. Either business can be selected by C17 at
        // runtime depending on the OCR compatibility metadata, so cover both implementations.
        listOf(
            "com.oplus.systemui.navigationbar.gesture.otherbusiness.SpeedChassistMainBusiness",
            "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness",
        ).forEach { className ->
            val businessClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            hookNativeCircleSystemUiBusiness(businessClass, classLoader)
        }
    }
    private fun hookNativeCircleSystemUiBusiness(
        businessClass: Class<*>,
        classLoader: ClassLoader,
    ) {
        val key = "systemui:native-circle-business:${businessClass.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                businessClass,
                "onLongPressed",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                        val context = findContext(param.thisObject) ?: systemContext
                        if (!isGestureCircleToSearchEnabled(context)) return
                        if (dispatchSystemUiNativeAssistant(
                                classLoader = classLoader,
                                invocationType = INVOCATION_TYPE_CIRCLE_TO_SEARCH,
                            )
                        ) {
                            // Recognition, pointer pilfering and the launcher's press animation
                            // have already run in C17. Suppress only the final Breeno/OCR action.
                            param.result = null
                        }
                    }
                },
            )
            log("Hooked C17 gesture-bar recognition: ${businessClass.name}")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook C17 gesture-bar recognition: ${businessClass.name}", it)
        }
    }
    private fun dispatchSystemUiNativeAssistant(
        classLoader: ClassLoader,
        invocationType: Int,
    ): Boolean {
        return runCatching {
            val dependencyClass = XposedHelpers.findClass(
                "com.android.systemui.Dependency",
                classLoader,
            )
            val assistManagerClass = XposedHelpers.findClass(
                "com.android.systemui.assist.AssistManager",
                classLoader,
            )
            val dependency = readStaticField(dependencyClass, "sDependency")
                ?: error("SystemUI Dependency is unavailable")
            val assistManager = XposedHelpers.callMethod(
                dependency,
                "getDependencyInner",
                assistManagerClass,
            ) ?: error("SystemUI AssistManager is unavailable")
            val bundle = Bundle().apply {
                putInt("invocation_type", invocationType)
            }
            XposedHelpers.callMethod(assistManager, "startAssist\$1", bundle)
            log("Forwarded gesture-bar invocation type $invocationType inside SystemUI")
            true
        }.onFailure {
            log("Failed to forward the gesture-bar invocation inside SystemUI", it)
        }.getOrDefault(false)
    }
    /** SystemUI dependency singleton access. */
    private fun readStaticField(targetClass: Class<*>, fieldName: String): Any? {
        val field = targetClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(null)
    }
    private fun hookSystemUiAttachRetry() {
        val key = "systemui:application-attach"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        if (systemContext == null) {
                            systemContext = context.applicationContext ?: context
                        }
                        sequenceOf(
                            context.classLoader,
                            param.thisObject?.javaClass?.classLoader,
                            Thread.currentThread().contextClassLoader
                        ).filterNotNull().distinct().forEach { classLoader ->
                            hookSystemUiExperimentalRoutes(classLoader)
                        }
                    }
                }
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to install SystemUI assistant retry", it)
        }
    }
    private val nativeAssistantRouteDepth = ThreadLocal<Int>()
    private val nativeAssistantRouteFrames = ThreadLocal<ArrayDeque<Boolean>>()
}
