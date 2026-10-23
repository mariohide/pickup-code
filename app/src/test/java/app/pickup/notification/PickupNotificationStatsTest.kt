package app.pickup.notification

import app.pickup.data.FakeParcelRepository
import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PickupNotificationStatsTest {

    private val repository = FakeParcelRepository()

    @Test
    fun `统计未取包裹数与地点数 - 多个包裹在不同地点`() = runTest {
        repository.items.value = listOf(
            parcel(1, location = "阳光小区3号楼", pending = true),
            parcel(2, location = "阳光小区3号楼", pending = true),
            parcel(3, location = "丰巢快递柜", pending = true),
            parcel(4, location = "丰巢快递柜", pending = false),
        )

        val pending = repository.getPendingParcels()
        val pendingCount = pending.size
        val locationCount = pending.map { it.location }.distinct().size

        assertEquals(3, pendingCount)
        assertEquals(2, locationCount)
    }

    @Test
    fun `统计未取包裹数与地点数 - 包含未识别地点`() = runTest {
        repository.items.value = listOf(
            parcel(1, location = "阳光小区3号楼", pending = true),
            parcel(2, location = null, pending = true),
        )

        val pending = repository.getPendingParcels()
        val pendingCount = pending.size
        val locationCount = pending.map { it.location }.distinct().size

        assertEquals(2, pendingCount)
        assertEquals(2, locationCount)
    }

    @Test
    fun `文案格式化符合要求`() {
        assertEquals("新包裹已到达阳光小区3号楼", PickupNotifier.formatTitleText("阳光小区3号楼"))
        assertEquals("新包裹已到达", PickupNotifier.formatTitleText(null))
        assertEquals("新包裹已到达", PickupNotifier.formatTitleText(""))
        assertEquals("剩3件未取包裹，在2个地点。", PickupNotifier.formatContentText(3, 2))
        assertEquals("剩1件未取包裹，在1个地点。", PickupNotifier.formatContentText(1, 1))
    }

    private fun parcel(id: Long, location: String?, pending: Boolean) = Parcel(
        id = id,
        carrier = "中通快递",
        arrivedAt = Instant.EPOCH,
        location = location,
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode("1-1-1"),
        status = if (pending) ParcelStatus.Pending else ParcelStatus.PickedUp(Instant.EPOCH),
    )
}
