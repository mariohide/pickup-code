package app.pickup.data.sms

import app.pickup.model.PickupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PickupSmsParserTest {

    private val at = Instant.parse("2026-10-22T08:00:00Z")

    private fun parse(body: String) = PickupSmsParser.parse(body, at)

    @Test
    fun `驿站货架码`() {
        val parcel = parse("【菜鸟驿站】您的申通包裹已到阳光小区3号楼菜鸟驿站，请凭取件码3-2-1234及时取件。尾号1234").single()

        assertEquals(PickupMethod.ShelfCode("3-2-1234"), parcel.pickupMethod)
        assertEquals("申通快递", parcel.carrier)
        assertEquals("阳光小区3号楼", parcel.location)
        assertEquals("1234", parcel.phoneTail)
        assertEquals(at, parcel.arrivedAt)
    }

    @Test
    fun `快递柜码连同柜号格口`() {
        val parcel = parse("【丰巢】您的中通快递包裹已放入华强北路12号丰巢柜7号柜23格口，取件码80123456，请及时取件").single()

        assertEquals(PickupMethod.LockerCode("80123456", "7", "23"), parcel.pickupMethod)
        assertEquals("中通快递", parcel.carrier)
        assertEquals("华强北路12号", parcel.location)
    }

    @Test
    fun `签名是丰巢时算快递柜码`() {
        val parcel = parse("【丰巢】您的包裹已到华强北路12号，取件码：80123456").single()

        assertEquals(PickupMethod.LockerCode("80123456", null, null), parcel.pickupMethod)
        assertNull(parcel.carrier)
    }

    @Test
    fun `地点在取件之前`() {
        val parcel = parse("【韵达快递】请凭取件码80123456至华强北路12号丰巢柜取件").single()

        assertEquals("华强北路12号", parcel.location)
        assertEquals("韵达快递", parcel.carrier)
    }

    @Test
    fun `括号前只有品牌时地点取括号里的内容`() {
        val parcel = parse("您的圆通包裹已到菜鸟驿站（阳光小区3号楼店），取件码为5-1-3021").single()

        assertEquals("阳光小区3号楼店", parcel.location)
    }

    @Test
    fun `凭后直接跟码`() {
        val parcel = parse("【申通快递】凭1-5-1001到阳光小区3号楼取尾号1234包裹").single()

        assertEquals(PickupMethod.ShelfCode("1-5-1001"), parcel.pickupMethod)
        assertEquals("申通快递", parcel.carrier)
        assertEquals("阳光小区3号楼", parcel.location)
        assertEquals("1234", parcel.phoneTail)
    }

    @Test
    fun `驿站签名不算快递公司`() {
        val parcel = parse("【多多代收点】凭2-4-1002到阳光小区3号楼取尾号1234包裹").single()

        assertNull(parcel.carrier)
        assertEquals("阳光小区3号楼", parcel.location)
    }

    @Test
    fun `请凭码到某处取件`() {
        val parcel = parse("【极兔速递】请凭1-4-1003到阳光小区1号极兔快递取件，地址：阳光小区1号极兔快递。").single()

        assertEquals(PickupMethod.ShelfCode("1-4-1003"), parcel.pickupMethod)
        assertEquals("极兔速递", parcel.carrier)
        assertEquals("阳光小区1号极兔快递", parcel.location)
    }

    @Test
    fun `柜号后直接跟格口`() {
        val parcel = parse("【欢猫驿站】快递员提醒您，请凭取件码100004到朝阳阳光小区3号楼多多驿站1号柜A16智能柜取您的快递").single()

        assertEquals(PickupMethod.LockerCode("100004", "1", "A16"), parcel.pickupMethod)
        assertEquals("朝阳阳光小区3号楼", parcel.location)
    }

    @Test
    fun `上门取件与银行卡尾号短信不算`() {
        assertTrue(parse("【京东】您的洗护订单已变更上门取件时间，如有疑问可联系快递员确认取件时间。").isEmpty())
        assertTrue(parse("您尾号1234卡于2026年09月到期，请及时换卡。【中国银行】").isEmpty())
    }

    @Test
    fun `一条短信多个取件码各算一件`() {
        val parcels = parse("【兔喜生活】您有2个包裹已到阳光小区3号楼驿站，取件码3-2-1234、3-2-0567，请及时领取")

        assertEquals(
            listOf(PickupMethod.ShelfCode("3-2-1234"), PickupMethod.ShelfCode("3-2-0567")),
            parcels.map { it.pickupMethod },
        )
        assertTrue(parcels.all { it.location == "阳光小区3号楼" })
    }

    @Test
    fun `身份码`() {
        val parcel = parse("【多多驿站】您的包裹已到阳光小区3号楼，请出示拼多多身份码取件").single()

        assertEquals(PickupMethod.IdentityCode("出示拼多多身份码"), parcel.pickupMethod)
        assertEquals("阳光小区3号楼", parcel.location)
    }

    @Test
    fun `没有平台名的身份码`() {
        assertEquals(PickupMethod.IdentityCode("出示身份码"), parse("包裹已到驿站，请出示身份码取件").single().pickupMethod)
    }

    @Test
    fun `凭手机尾号取件`() {
        val parcel = parse("【极兔速递】您的包裹已放在阳光小区3号楼门口快递架，凭手机尾号取件").single()

        assertEquals(PickupMethod.Other("凭手机尾号取件"), parcel.pickupMethod)
        assertEquals("极兔速递", parcel.carrier)
        assertEquals("阳光小区3号楼门口快递架", parcel.location)
    }

    @Test
    fun `识别不出地点时为空`() {
        val parcel = parse("您的快递取件码是 3-2-1234").single()

        assertNull(parcel.location)
        assertNull(parcel.phoneTail)
    }

    @Test
    fun `取出与签收通知不算`() {
        assertTrue(parse("【丰巢】您的包裹已取出，取件码80123456已失效").isEmpty())
        assertTrue(parse("【申通快递】您的快件已签收，感谢使用").isEmpty())
    }

    @Test
    fun `与取件无关的短信不算`() {
        assertTrue(parse("【某银行】您的验证码是 123456，5分钟内有效").isEmpty())
        assertTrue(parse("您的快递正在派送中，派件员电话13800000000").isEmpty())
    }

    @Test
    fun `取件码后面紧跟柜号时不把柜号当成码`() {
        val parcel = parse("【丰巢】取件码80123456,7号柜23格口").single()

        assertEquals(PickupMethod.LockerCode("80123456", "7", "23"), parcel.pickupMethod)
    }

    @Test
    fun `地点归一化`() {
        assertEquals("阳光小区3号楼", PickupSmsParser.normalizeLocation(" 阳光小区3号楼 菜鸟驿站 "))
        assertEquals("华强北路12号", PickupSmsParser.normalizeLocation("华强北路12号的丰巢智能柜"))
        assertEquals("阳光小区3号楼", PickupSmsParser.normalizeLocation("阳光小区3号楼驿站（营业至21点）"))
        assertNull(PickupSmsParser.normalizeLocation("菜鸟驿站"))
    }
}
