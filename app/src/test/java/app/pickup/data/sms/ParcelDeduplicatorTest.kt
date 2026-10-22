package app.pickup.data.sms

import app.pickup.model.PickupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ParcelDeduplicatorTest {

    private val base = Instant.parse("2026-10-20T08:00:00Z")

    @Test
    fun `催取短信与库里的同一件包裹去重`() {
        val existing = listOf(shelf("3-2-1234", days = 0))
        val reminder = shelf("3-2-1234", days = 1)

        assertTrue(ParcelDeduplicator.newParcels(existing, listOf(reminder)).isEmpty())
    }

    @Test
    fun `本批内去重时保留先到的`() {
        val first = shelf("3-2-1234", days = 0)
        val reminder = shelf("3-2-1234", days = 1)

        assertEquals(listOf(first), ParcelDeduplicator.newParcels(emptyList(), listOf(reminder, first)))
    }

    @Test
    fun `取件码相同但相隔超过 14 天算两件`() {
        val existing = listOf(shelf("3-2-1234", days = 0))
        val later = shelf("3-2-1234", days = 15)

        assertEquals(listOf(later), ParcelDeduplicator.newParcels(existing, listOf(later)))
    }

    @Test
    fun `取件码相同但地点不同算两件`() {
        val existing = listOf(shelf("1234", days = 0, location = "阳光小区3号楼"))
        val other = shelf("1234", days = 0, location = "华强北路12号")

        assertEquals(listOf(other), ParcelDeduplicator.newParcels(existing, listOf(other)))
    }

    @Test
    fun `一方地点识别不出时不比较地点`() {
        val existing = listOf(shelf("3-2-1234", days = 0))
        val noLocation = shelf("3-2-1234", days = 1, location = null)

        assertTrue(ParcelDeduplicator.newParcels(existing, listOf(noLocation)).isEmpty())
    }

    @Test
    fun `没有取件码的包裹 3 天内同方式同地点同公司算同一件`() {
        val first = identity(days = 0)

        assertTrue(ParcelDeduplicator.newParcels(listOf(first), listOf(identity(days = 2))).isEmpty())
        assertEquals(1, ParcelDeduplicator.newParcels(listOf(first), listOf(identity(days = 4))).size)
        assertEquals(1, ParcelDeduplicator.newParcels(listOf(first), listOf(identity(days = 1, carrier = "中通快递"))).size)
    }

    private fun shelf(code: String, days: Long, location: String? = "阳光小区3号楼") = ParsedParcel(
        carrier = "申通快递",
        arrivedAt = base.plus(Duration.ofDays(days)),
        location = location,
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode(code),
    )

    private fun identity(days: Long, carrier: String? = null) = ParsedParcel(
        carrier = carrier,
        arrivedAt = base.plus(Duration.ofDays(days)),
        location = "阳光小区3号楼",
        phoneTail = null,
        pickupMethod = PickupMethod.IdentityCode("出示淘宝身份码"),
    )
}
