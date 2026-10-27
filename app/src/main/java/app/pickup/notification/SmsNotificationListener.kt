package app.pickup.notification

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import app.pickup.data.sms.PickupSmsParser
import app.pickup.permission.hasNotificationAccess
import java.time.Instant
import javax.inject.Inject

/**
 * 撤掉短信应用为取件短信发的通知，改由本应用发新包裹通知，同一条短信只提醒一次（spec message_source.md §6）。
 * 只看默认短信应用的通知，按通知里最新一条短信的正文判断是不是取件短信。
 */
@AndroidEntryPoint
class SmsNotificationListener : NotificationListenerService() {
    @Inject lateinit var notifier: PickupNotifier

    override fun onListenerConnected() {
        connected = true
        // 断开期间短信应用发的取件通知还挂着，连上后补撤
        activeNotifications?.forEach(::cancelIfPickup)
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = cancelIfPickup(sbn)

    private fun cancelIfPickup(sbn: StatusBarNotification) {
        if (sbn.packageName != Telephony.Sms.getDefaultSmsPackage(this)) return
        // 本应用收不到新短信或发不出通知时保留短信应用的，免得一条提醒都没有
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        if (!notifier.canNotify()) return
        val text = sbn.notification.latestMessageText() ?: return
        // 识别不出的短信、取出与签收通知都解析不出包裹，照常由短信应用提醒
        if (PickupSmsParser.parse(text, Instant.ofEpochMilli(sbn.postTime)).isNotEmpty()) {
            cancelNotification(sbn.key)
        }
    }

    companion object {
        @Volatile private var connected = false

        /**
         * 通知使用权开着、系统却没绑定本服务时让系统重新绑定（§6，2026-10-26）。
         * 三星上实测会出现这种状态，且系统不会自己恢复。requestRebind 只对 requestUnbind 过的服务有效，
         * 所以把组件禁用再启用：系统收到组件变化后重新绑定。
         */
        fun ensureBound(context: Context) {
            if (connected || !context.hasNotificationAccess()) return
            val component = ComponentName(context, SmsNotificationListener::class.java)
            context.packageManager.apply {
                setComponentEnabledSetting(
                    component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
                )
                setComponentEnabledSetting(
                    component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/** 会话式通知取最后一条消息，否则取正文；展开后的长文本更完整，优先用 */
private fun Notification.latestMessageText(): String? {
    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(this)
        ?.messages?.lastOrNull()?.text
        ?.let { return it.toString() }
    return (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
        ?.toString()
}
