package app.pickup.data

import androidx.room.withTransaction
import app.pickup.data.db.ParcelEntity
import app.pickup.data.db.PickupDatabase
import app.pickup.data.db.ScanCursorEntity
import app.pickup.data.db.toDomain
import app.pickup.data.db.toEntity
import app.pickup.data.db.toParsed
import app.pickup.data.sms.ParcelDeduplicator
import app.pickup.data.sms.ParsedParcel
import app.pickup.data.sms.PickupSmsParser
import app.pickup.data.sms.SmsInbox
import app.pickup.data.sms.SmsMessage
import app.pickup.model.Parcel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

interface MessageScanRepository {
    /**
     * 扫描上次之后新到的短信，把识别出的新包裹写入清单，返回新增件数（spec message_source.md）。
     * 失败时什么都不写，下次从同一位置重扫。
     * @throws SecurityException 没有短信读取权限
     * @throws android.database.SQLException 读写失败
     */
    suspend fun scanSms(): Int

    /**
     * 新短信广播带来的一条短信，直接解析入库，返回新增的包裹（spec message_source.md §1）。
     * 不动扫描位置：之后扫描收件箱读到同一条短信时，靠去重不再重复入库。
     * @throws android.database.SQLException 读写失败
     */
    suspend fun importReceivedSms(message: SmsMessage): List<Parcel>
}

class SmsScanRepository @Inject constructor(
    private val database: PickupDatabase,
    private val inbox: SmsInbox,
    private val clock: Clock,
) : MessageScanRepository {

    /** 进入页面、下拉刷新、新短信广播可能同时触发，排队执行，后一次读到的就是空的 */
    private val mutex = Mutex()

    override suspend fun scanSms(): Int = mutex.withLock {
        val cursorDao = database.scanCursorDao()
        val after = cursorDao.lastReceivedAt(SOURCE_SMS)?.let(Instant::ofEpochMilli)
            ?: clock.instant().minus(FIRST_SCAN_LOOKBACK)
        val messages = inbox.readAfter(after)
        if (messages.isEmpty()) return 0
        val parsed = messages.flatMap { PickupSmsParser.parse(it.body, it.receivedAt) }
        database.withTransaction {
            val fresh = insertNew(parsed)
            cursorDao.upsert(ScanCursorEntity(SOURCE_SMS, messages.maxOf { it.receivedAt }.toEpochMilli()))
            fresh.size
        }
    }

    override suspend fun importReceivedSms(message: SmsMessage): List<Parcel> = mutex.withLock {
        val parsed = PickupSmsParser.parse(message.body, message.receivedAt)
        if (parsed.isEmpty()) return emptyList()
        database.withTransaction { insertNew(parsed) }.map { it.toDomain() }
    }

    /** 去重后入库，返回带上 id 的新包裹；在事务里调用 */
    private suspend fun insertNew(parsed: List<ParsedParcel>): List<ParcelEntity> {
        val earliest = parsed.minOfOrNull { it.arrivedAt } ?: return emptyList()
        val parcelDao = database.parcelDao()
        val existing = parcelDao.arrivedSince(earliest.minus(ParcelDeduplicator.LOOKBACK).toEpochMilli())
        val fresh = ParcelDeduplicator.newParcels(existing.map { it.toParsed() }, parsed).map { it.toEntity() }
        return fresh.zip(parcelDao.insertAll(fresh)) { parcel, id -> parcel.copy(id = id) }
    }

    private companion object {
        /** 带上解析规则版本：规则更新后没有扫描位置，按首次扫描重新回溯，去重保证已有包裹不重复 */
        const val SOURCE_SMS = "sms:v${PickupSmsParser.VERSION}"
        val FIRST_SCAN_LOOKBACK: Duration = Duration.ofDays(7)
    }
}
