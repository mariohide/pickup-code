package app.pickup.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** 每个消息来源扫描到哪儿了：下次只读之后到达的消息（spec message_source.md §1） */
@Entity(tableName = "scan_cursor")
data class ScanCursorEntity(
    @PrimaryKey val source: String,
    /** 已扫描的最后一条消息的到达时间 */
    @ColumnInfo(name = "last_received_at") val lastReceivedAt: Long,
)

@Dao
interface ScanCursorDao {
    @Query("SELECT last_received_at FROM scan_cursor WHERE source = :source")
    suspend fun lastReceivedAt(source: String): Long?

    @Upsert
    suspend fun upsert(cursor: ScanCursorEntity)
}
