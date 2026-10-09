package io.github.zopulus.coogle

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zopulus.coogle.ui.CoogleScreen
import io.github.zopulus.coogle.ui.CoogleState
import io.github.zopulus.coogle.ui.CoogleTheme
import io.github.zopulus.coogle.ui.CompanionState
import io.github.zopulus.coogle.ui.DialogKind
import io.github.libxposed.service.XposedService
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var preferences: SharedPreferences? = null
    private var state by mutableStateOf(CoogleState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = state.copy(assistant = AssistantSelection.summary(this), iconHidden = iconHidden())
        setContent {
            CoogleTheme {
                CoogleScreen(state, ::changeSetting, ::openAssistant, ::changeIconVisibility,
                    { showDialog(DialogKind.STATUS) }, ::openRepository,
                    ::confirmDialog, ::dismissDialog)
            }
        }
        SettingsConnection.connect(this)
    }

    override fun onResume() {
        super.onResume()
        state = state.copy(assistant = AssistantSelection.summary(this), iconHidden = iconHidden())
        if (preferences != null && state.features[FeaturePolicy.HEY_GOOGLE] == true) checkCompanion(false)
    }

    override fun onDestroy() {
        SettingsConnection.detach(this)
        worker.shutdown()
        super.onDestroy()
    }

    fun onFrameworkChanged(service: XposedService?) {
        if (isDestroyed) return
        preferences = runCatching { service?.getRemotePreferences(FeatureConfig.GROUP) }.getOrNull()
        syncPreferences()
        if (state.features[FeaturePolicy.HEY_GOOGLE] == true) checkCompanion(false)
    }

    private fun syncPreferences() {
        val values = CoogleState.featureKeys.associateWith { key ->
            runCatching { preferences?.getBoolean(key, FeaturePolicy.defaultValue(key)) }
                .getOrNull() ?: FeaturePolicy.defaultValue(key)
        }
        state = state.copy(connected = preferences != null, features = values)
    }

    private fun changeSetting(key: String, enabled: Boolean) {
        if (state.busy || preferences == null) return
        if (key == FeaturePolicy.HEY_GOOGLE && enabled) checkCompanion(true)
        else saveSetting(key, enabled)
    }

    private fun saveSetting(key: String, enabled: Boolean, companionInstalled: Boolean? = null) {
        val settings = preferences ?: return
        state = state.copy(busy = true)
        worker.execute {
            val result = runCatching {
                val editor = settings.edit().putBoolean(key, enabled)
                companionInstalled?.let { editor.putBoolean("companion_ready", it) }
                if (!editor.commit()) throw IOException("配置未保存，请检查 LSPosed 连接。")
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(busy = false)
                syncPreferences()
                result.onFailure { error(it.message ?: "配置保存失败") }
            }
        }
    }

    private fun checkCompanion(enableAfterCheck: Boolean) {
        if (state.busy) return
        state = state.copy(busy = true, companion = CompanionState.CHECKING)
        worker.execute {
            val result = runCatching { CompanionState.valueOf(CompanionInstaller.check()) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(busy = false)
                result.onSuccess { installed ->
                    state = state.copy(companion = installed)
                    if (enableAfterCheck) when (installed) {
                        CompanionState.READY, CompanionState.PENDING -> saveSetting(FeaturePolicy.HEY_GOOGLE, true, true)
                        CompanionState.DISABLED -> showDialog(DialogKind.DISABLED)
                        else -> showDialog(DialogKind.INSTALL)
                    }
                    else if (!installed.installed && state.features[FeaturePolicy.HEY_GOOGLE] == true)
                        saveSetting(FeaturePolicy.HEY_GOOGLE, false, false)
                }.onFailure {
                    state = state.copy(companion = CompanionState.UNKNOWN)
                    if (enableAfterCheck) error("无法检查安装状态。请在 KernelSU 中为 Coogle 授予 Root 权限，然后重新开启 Hey Google。")
                }
            }
        }
    }

    private fun installCompanion() {
        val settings = preferences ?: return
        state = state.copy(busy = true, companion = CompanionState.INSTALLING, dialog = DialogKind.NONE)
        worker.execute {
            val result = runCatching {
                CompanionInstaller.install(this)
                if (!settings.edit().putBoolean("companion_ready", true).putBoolean(FeaturePolicy.HEY_GOOGLE, true).commit())
                    throw IOException("附属模块已安装，但配置保存失败，请重新开启 Hey Google。")
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(busy = false)
                syncPreferences()
                result.onSuccess {
                    state = state.copy(companion = CompanionState.PENDING)
                    showDialog(DialogKind.INSTALLED)
                }.onFailure {
                    state = state.copy(companion = CompanionState.UNKNOWN)
                    error(it.message ?: "附属模块安装失败")
                }
            }
        }
    }

    private fun openAssistant() {
        runCatching { AssistantSelection.open(this) }.onFailure { error("无法打开系统语音助理选择页面。") }
    }

    private fun iconHidden() = packageManager.getComponentEnabledSetting(ComponentName(this, "$packageName.Launcher")) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun changeIconVisibility(hidden: Boolean) {
        if (hidden) showDialog(DialogKind.HIDE_ICON) else setIconHidden(false)
    }

    private fun setIconHidden(hidden: Boolean) {
        runCatching {
            packageManager.setComponentEnabledSetting(ComponentName(this, "$packageName.Launcher"),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
            state = state.copy(iconHidden = hidden)
        }.onFailure { error("无法修改桌面图标状态。") }
    }

    private fun confirmDialog() {
        val dialog = state.dialog
        dismissDialog()
        when (dialog) {
            DialogKind.INSTALL -> installCompanion()
            DialogKind.HIDE_ICON -> setIconHidden(true)
            else -> Unit
        }
    }

    private fun showDialog(kind: DialogKind) { state = state.copy(dialog = kind) }
    private fun dismissDialog() { state = state.copy(dialog = DialogKind.NONE) }
    private fun error(message: String) { state = state.copy(dialog = DialogKind.ERROR, dialogText = message) }
    private fun openRepository() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zopulus/coogle"))) }
            .onFailure { error("请使用浏览器打开 github.com/zopulus/coogle。") }
    }

}
