package app.pickup.ui.permission

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pickup.notification.SmsNotificationListener
import app.pickup.permission.hasNotificationAccess
import app.pickup.permission.hasSmsPermission

/** 一项运行时权限此刻的状态，决定按钮写「开启」还是「去设置」（spec onboarding §3） */
enum class PermissionStatus {
    Granted,

    /** 系统还会弹权限窗：按钮「开启」 */
    Requestable,

    /** 系统不再弹窗，或在系统设置里关了：按钮「去设置」 */
    SettingsOnly,
}

/**
 * 首次引导与清单横幅共用的权限状态与操作。回到页面（含从系统权限窗、系统设置回来）时重新检查。
 *
 * 系统不提供「还会不会弹窗」的查询：申请过、且系统不再要求说明理由（拒绝过两次）时，视为不再弹窗。
 * 申请过哪些权限记在本机；没有记录的（如引导上线前就申请过的）先当作还会弹窗，点了没弹窗就转为「去设置」。
 */
@Stable
class AppPermissions internal constructor(private val activity: Activity) {

    var sms by mutableStateOf(PermissionStatus.Requestable)
        private set

    var notifications by mutableStateOf(PermissionStatus.Requestable)
        private set

    var notificationAccess by mutableStateOf(false)
        private set

    internal var smsLauncher: ActivityResultLauncher<Array<String>>? = null
    internal var notificationLauncher: ActivityResultLauncher<String>? = null

    private val requestLog = activity.getSharedPreferences(REQUEST_LOG, Context.MODE_PRIVATE)

    init {
        refresh()
    }

    fun refresh() {
        sms = if (activity.hasSmsPermission()) PermissionStatus.Granted else runtimeStatus(Manifest.permission.READ_SMS)
        notifications = notificationStatus()
        notificationAccess = activity.hasNotificationAccess()
    }

    /** 「开启」弹系统权限窗，一次申请读短信与收新短信；「去设置」跳到本应用的系统设置页 */
    fun enableSms() {
        when (sms) {
            PermissionStatus.Granted -> Unit
            PermissionStatus.Requestable -> {
                markRequested(Manifest.permission.READ_SMS)
                smsLauncher?.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            }
            PermissionStatus.SettingsOnly -> openAppDetailsSettings()
        }
    }

    /** 「开启」弹系统权限窗；「去设置」跳到本应用的通知设置页 */
    fun enableNotifications() {
        when (notifications) {
            PermissionStatus.Granted -> Unit
            PermissionStatus.Requestable -> {
                markRequested(Manifest.permission.POST_NOTIFICATIONS)
                notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionStatus.SettingsOnly -> activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
            )
        }
    }

    /** 没有系统权限窗；Android 11 起可直达本应用的开关，否则到通知使用权列表 */
    fun openNotificationAccessSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(activity, SmsNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        activity.startActivity(intent)
    }

    /** 本应用的应用信息页：短信权限、受限设置都在这里 */
    fun openAppDetailsSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    /** Android 13 以下没有权限窗：开着就是已开启，关了只能去设置。权限给了、却在系统设置里关了通知的，也只能去设置 */
    private fun notificationStatus(): PermissionStatus {
        val enabled = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (enabled) PermissionStatus.Granted else PermissionStatus.SettingsOnly
        }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        return when {
            activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED ->
                runtimeStatus(permission)
            enabled -> PermissionStatus.Granted
            else -> PermissionStatus.SettingsOnly
        }
    }

    /** 未授权的权限：没申请过，或拒绝过一次（系统要求说明理由）时还会弹窗 */
    private fun runtimeStatus(permission: String): PermissionStatus =
        if (!requestLog.getBoolean(permission, false) || activity.shouldShowRequestPermissionRationale(permission)) {
            PermissionStatus.Requestable
        } else {
            PermissionStatus.SettingsOnly
        }

    private fun markRequested(permission: String) = requestLog.edit { putBoolean(permission, true) }

    private companion object {
        const val REQUEST_LOG = "permission_requests"
    }
}

/** [onSmsGranted]：在本页通过系统权限窗刚开启短信权限时调用 */
@Composable
fun rememberAppPermissions(onSmsGranted: () -> Unit = {}): AppPermissions {
    val activity = checkNotNull(LocalActivity.current)
    val permissions = remember(activity) { AppPermissions(activity) }
    val currentOnSmsGranted by rememberUpdatedState(onSmsGranted)
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions.refresh()
        if (permissions.sms == PermissionStatus.Granted) currentOnSmsGranted()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions.refresh()
    }
    DisposableEffect(permissions, smsLauncher, notificationLauncher) {
        permissions.smsLauncher = smsLauncher
        permissions.notificationLauncher = notificationLauncher
        onDispose {
            permissions.smsLauncher = null
            permissions.notificationLauncher = null
        }
    }
    // 系统权限窗盖在上面时页面只暂停、不停止，所以按恢复来检查
    LifecycleResumeEffect(permissions) {
        permissions.refresh()
        onPauseOrDispose {}
    }
    return permissions
}
