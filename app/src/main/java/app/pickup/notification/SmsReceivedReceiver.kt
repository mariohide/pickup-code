package app.pickup.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import app.pickup.data.MessageScanRepository
import app.pickup.data.sms.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * 新短信一到就解析，是取件短信就入库并发新包裹通知（spec message_source.md §1、§6）。
 * 应用没在运行时，系统也会拉起进程送达。
 */
@AndroidEntryPoint
class SmsReceivedReceiver : BroadcastReceiver() {
    @Inject lateinit var scanRepository: MessageScanRepository
    @Inject lateinit var notifier: PickupNotifier
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // 监听器没绑定时短信应用的通知撤不掉；现在重新绑定，连上后会补撤这条（§6）
        SmsNotificationListener.ensureBound(context)
        // 长短信分段送达，拼回一条
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        // 收件箱里记的也是短信到达手机的时间，与扫描读到的同一条短信去重时对得上
        val message = SmsMessage(body, clock.instant())
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                notifier.notifyNewParcels(scanRepository.importReceivedSms(message))
                // 顺带扫描收件箱，扫描位置越过这条短信：否则它入库后被标记已取、在设置里清空，
                // 下次扫描会把它当作新包裹再导入一次（§1「不重扫旧短信」）
                scanRepository.scanSms()
            } catch (_: SQLException) {
                // 静默：下次扫描收件箱时补进清单
            } catch (_: SecurityException) {
                // 短信读取权限被关闭，清单页有横幅
            } finally {
                pending.finish()
            }
        }
    }
}
