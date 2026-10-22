package app.pickup.ui.pickup

import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PickupListBuilderTest {

    @Test
    fun `按地点分组，组与组内都按到件时间从新到旧，未识别地点排最后`() {
        val content = build(
            parcel(1, location = null, arrivedAt = 1),
            parcel(2, location = "B", arrivedAt = 5),
            parcel(3, location = "A", arrivedAt = 9),
            parcel(4, location = "B", arrivedAt = 3),
        )

        assertEquals(listOf("A", "B", null), content.groups.map { it.location })
        assertEquals(listOf(2L, 4L), content.groups[1].cards.map { it.id })
    }

    @Test
    fun `本页内刚记为已取的包裹留在待取区原位，显示为 Revealed`() {
        val revealed = parcel(1, location = "A", arrivedAt = 1, pickedAt = 100)
        val content = build(revealed, parcel(2, location = "A", arrivedAt = 2), areas = mapOf(1L to ListArea.Pending))

        val cards = content.groups.single().cards
        assertEquals(listOf(2L, 1L), cards.map { it.id })
        assertEquals(CardState.Revealed, cards.single { it.id == 1L }.state)
        assertTrue(content.picked.isEmpty())
        assertEquals(1, content.pendingCount)
    }

    @Test
    fun `组内待取不足 2 件时不能查看全部，组头件数仍数列出的卡片`() {
        val content = build(
            parcel(1, location = "A", arrivedAt = 1, pickedAt = 100),
            parcel(2, location = "A", arrivedAt = 2),
            areas = mapOf(1L to ListArea.Pending),
        )

        val group = content.groups.single()
        assertEquals(2, group.cards.size)
        assertEquals(listOf(2L), group.pendingIds)
        assertFalse(group.canRevealAll)
    }

    @Test
    fun `记为已取失败的包裹仍是待取，显示为 MarkFailed`() {
        val content = build(
            parcel(1, location = "A", arrivedAt = 1),
            parcel(2, location = "A", arrivedAt = 2),
            failures = mapOf(1L to CardFailure.MarkFailed),
        )

        val group = content.groups.single()
        assertEquals(CardState.MarkFailed, group.cards.single { it.id == 1L }.state)
        assertEquals(2, content.pendingCount)
        assertTrue(group.canRevealAll)
    }

    @Test
    fun `已取区改回待取的包裹留在已取区原位`() {
        val content = build(
            parcel(1, location = "A", arrivedAt = 1, pickedAt = 50),
            parcel(2, location = "A", arrivedAt = 2),
            parcel(3, location = "A", arrivedAt = 3, pickedAt = 40),
            areas = mapOf(1L to ListArea.Picked(Instant.ofEpochMilli(50)), 2L to ListArea.Picked(Instant.ofEpochMilli(60))),
        )

        // 按进入页面时的取件时间从新到旧
        assertEquals(listOf(2L, 1L, 3L), content.picked.map { it.id })
        assertEquals(CardState.Pending, content.picked[0].state)
        assertTrue(content.picked.all { it.inPickedArea })
        assertTrue(content.groups.isEmpty())
    }

    @Test
    fun `撤销失败只标在仍为已取的卡片上`() {
        val content = build(
            parcel(1, location = "A", arrivedAt = 1, pickedAt = 50),
            failures = mapOf(1L to CardFailure.UndoFailed),
        )

        assertTrue(content.picked.single().undoFailed)
    }

    private fun build(
        vararg parcels: Parcel,
        areas: Map<Long, ListArea> = emptyMap(),
        failures: Map<Long, CardFailure> = emptyMap(),
    ) = buildPickupListContent(parcels.toList(), areas, failures, zone = ZoneOffset.UTC)

    private fun parcel(id: Long, location: String?, arrivedAt: Long, pickedAt: Long? = null) = Parcel(
        id = id,
        carrier = "申通快递",
        arrivedAt = Instant.ofEpochMilli(arrivedAt),
        location = location,
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode("3-2-$id"),
        status = pickedAt?.let { ParcelStatus.PickedUp(Instant.ofEpochMilli(it)) } ?: ParcelStatus.Pending,
    )
}
