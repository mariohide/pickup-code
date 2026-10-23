package app.pickup.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/** 读短信；收新短信与它同组，一起授予、一起收回 */
fun Context.hasSmsPermission(): Boolean =
    checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

/** 通知权限（Android 13 起）与系统里本应用的通知开关都开着 */
fun Context.canPostNotifications(): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(this).areNotificationsEnabled()

/** 通知使用权（spec message_source §6） */
fun Context.hasNotificationAccess(): Boolean =
    packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
