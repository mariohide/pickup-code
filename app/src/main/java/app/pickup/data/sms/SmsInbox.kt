package app.pickup.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

data class SmsMessage(val body: String, val receivedAt: Instant)

interface SmsInbox {
    /**
     * 收件箱里在 [after] 之后到达的短信，按到达时间从早到晚。
     * @throws SecurityException 没有短信读取权限
     */
    suspend fun readAfter(after: Instant): List<SmsMessage>
}

class ContentResolverSmsInbox @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsInbox {
    override suspend fun readAfter(after: Instant): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("没有短信读取权限")
        }
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(after.toEpochMilli().toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyColumn) ?: continue
                    add(SmsMessage(body, Instant.ofEpochMilli(cursor.getLong(dateColumn))))
                }
            }
        } ?: emptyList()
    }
}
