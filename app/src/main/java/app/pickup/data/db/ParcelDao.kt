package app.pickup.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    /** 全部待取的包裹，加上 [pickedSince] 之后记为已取的 */
    @Query("SELECT * FROM parcel WHERE picked_at IS NULL OR picked_at >= :pickedSince")
    fun observeVisible(pickedSince: Long): Flow<List<ParcelEntity>>

    /** [pickedAt] 为空即改回待取 */
    @Query("UPDATE parcel SET picked_at = :pickedAt WHERE id = :id")
    suspend fun setPickedAt(id: Long, pickedAt: Long?)

    /** 记为已取的件数，含清单里已不显示的 30 天以前的 */
    @Query("SELECT COUNT(*) FROM parcel WHERE picked_at IS NOT NULL")
    fun observePickedCount(): Flow<Int>

    /** 删除全部已取包裹，返回删除的件数 */
    @Query("DELETE FROM parcel WHERE picked_at IS NOT NULL")
    suspend fun deletePicked(): Int

    /** 全部待取的包裹（用于通知统计未取件数与地点等） */
    @Query("SELECT * FROM parcel WHERE picked_at IS NULL")
    suspend fun pendingParcels(): List<ParcelEntity>

    /** [since] 之后到件的包裹，不论状态；去重时与新消息比对 */
    @Query("SELECT * FROM parcel WHERE arrived_at >= :since")
    suspend fun arrivedSince(since: Long): List<ParcelEntity>

    /** 返回新行的 id，顺序与 [parcels] 一致 */
    @Insert
    suspend fun insertAll(parcels: List<ParcelEntity>): List<Long>
}
