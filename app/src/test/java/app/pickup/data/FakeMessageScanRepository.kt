package app.pickup.data

import android.database.sqlite.SQLiteException
import app.pickup.data.sms.SmsMessage
import app.pickup.model.Parcel

/** 扫描时把 [nextScan] 里的包裹加入 [parcels]；[failure] 模拟没有权限或读写失败 */
class FakeMessageScanRepository(private val parcels: FakeParcelRepository) : MessageScanRepository {
    var nextScan: List<Parcel> = emptyList()
    var failure: Failure? = null
    var scanCount = 0
        private set

    enum class Failure { NoPermission, ReadFailed }

    override suspend fun scanSms(): Int {
        scanCount++
        when (failure) {
            Failure.NoPermission -> throw SecurityException()
            Failure.ReadFailed -> throw SQLiteException()
            null -> Unit
        }
        val added = nextScan
        nextScan = emptyList()
        parcels.items.value += added
        return added.size
    }

    override suspend fun importReceivedSms(message: SmsMessage): List<Parcel> = emptyList()
}
