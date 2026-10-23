package app.pickup.data

import app.pickup.data.db.ParcelDao
import app.pickup.data.db.toDomain
import app.pickup.model.Parcel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

interface ParcelRepository {
    /**
     * 全部待取的包裹，加上 [pickedSince] 之后记为已取的。
     * 读取失败时流以 [android.database.SQLException] 结束。
     */
    fun observeVisibleParcels(pickedSince: Instant): Flow<List<Parcel>>

    /** @throws android.database.SQLException 写入失败 */
    suspend fun markPickedUp(id: Long, at: Instant)

    /** @throws android.database.SQLException 写入失败 */
    suspend fun markPending(id: Long)

    /**
     * 全部待取的包裹（pickedAt 为空）。
     * 读取失败时抛出 [android.database.SQLException]。
     */
    suspend fun getPendingParcels(): List<Parcel>

    /**
     * 记为已取的件数，含清单里已不显示的 30 天以前的。
     * 读取失败时流以 [android.database.SQLException] 结束。
     */
    fun observePickedCount(): Flow<Int>

    /**
     * 删除全部已取包裹（spec settings §2），返回删除的件数。
     * @throws android.database.SQLException 写入失败
     */
    suspend fun deletePicked(): Int
}

class RoomParcelRepository @Inject constructor(
    private val dao: ParcelDao,
) : ParcelRepository {
    override fun observeVisibleParcels(pickedSince: Instant): Flow<List<Parcel>> =
        dao.observeVisible(pickedSince.toEpochMilli()).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPendingParcels(): List<Parcel> =
        dao.pendingParcels().map { it.toDomain() }

    override suspend fun markPickedUp(id: Long, at: Instant) = dao.setPickedAt(id, at.toEpochMilli())

    override suspend fun markPending(id: Long) = dao.setPickedAt(id, null)

    override fun observePickedCount(): Flow<Int> = dao.observePickedCount()

    override suspend fun deletePicked(): Int = dao.deletePicked()
}
