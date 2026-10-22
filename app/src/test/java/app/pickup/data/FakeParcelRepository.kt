package app.pickup.data

import android.database.sqlite.SQLiteException
import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** 内存里的包裹仓库；[failReads] / [failWrites] 模拟数据库读写失败 */
class FakeParcelRepository : ParcelRepository {
    val items = MutableStateFlow<List<Parcel>>(emptyList())
    var failReads = false
    var failWrites = false

    override fun observeVisibleParcels(pickedSince: Instant): Flow<List<Parcel>> =
        items.map { if (failReads) throw SQLiteException() else it }

    override suspend fun markPickedUp(id: Long, at: Instant) = setStatus(id, ParcelStatus.PickedUp(at))

    override suspend fun markPending(id: Long) = setStatus(id, ParcelStatus.Pending)

    override fun observePickedCount(): Flow<Int> =
        items.map { list -> if (failReads) throw SQLiteException() else list.count { it.status is ParcelStatus.PickedUp } }

    override suspend fun deletePicked(): Int {
        if (failWrites) throw SQLiteException()
        val picked = items.value.count { it.status is ParcelStatus.PickedUp }
        items.update { list -> list.filter { it.status !is ParcelStatus.PickedUp } }
        return picked
    }

    private fun setStatus(id: Long, status: ParcelStatus) {
        if (failWrites) throw SQLiteException()
        items.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}
