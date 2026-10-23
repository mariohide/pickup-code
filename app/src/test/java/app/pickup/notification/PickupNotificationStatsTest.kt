package app.pickup.notification

import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PickupNotificationStatsTest {

    @Test
    fun `待取概览统计件数与地点数`() {
        val summary = summarizePending(
            listOf(
                parcel(1, location = "阳光小区3号楼"),
                parcel(2, location = "阳光小区3号楼"),
                parcel(3, location = "丰巢快递柜"),
            ),
        )

        assertEquals(PendingSummary(count = 3, locationCount = 2), summary)
    }

    @Test
    fun `未识别地点的包裹合起来算一个地点`() {
        val summary = summarizePending(
            listOf(
                parcel(1, location = "阳光小区3号楼"),
                parcel(2, location = null),
                parcel(3, location = null),
            ),
        )

        assertEquals(PendingSummary(count = 3, locationCount = 2), summary)
    }

    @Test
    fun `一批都在同一地点时标题带地点`() {
        assertEquals("阳光小区3号楼", batchLocation(listOf(parcel(1, "阳光小区3号楼"), parcel(2, "阳光小区3号楼"))))
    }

    @Test
    fun `地点未识别或不止一个时标题不带地点`() {
        assertNull(batchLocation(listOf(parcel(1, location = null))))
        assertNull(batchLocation(listOf(parcel(1, location = ""))))
        assertNull(batchLocation(listOf(parcel(1, "阳光小区3号楼"), parcel(2, "丰巢快递柜"))))
        assertNull(batchLocation(listOf(parcel(1, "阳光小区3号楼"), parcel(2, location = null))))
    }

    private fun parcel(id: Long, location: String?) = Parcel(
        id = id,
        carrier = "中通快递",
        arrivedAt = Instant.EPOCH,
        location = location,
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode("1-1-1"),
        status = ParcelStatus.Pending,
    )
}
