package app.pickup.data.sms

import app.pickup.model.PickupMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * 真实短信语料回归：只核对提取出的取件码，空列表表示不该识别为取件短信。
 *
 * 语料取自 PickupCodeGrabber 的冒烟测试集（MIT License，Copyright (c) 2026 O-kai）：
 * https://github.com/O-kai/Xiaomi-HyperOs-pickup-code-grabber
 */
class PickupSmsCorpusTest {

    @Test
    fun `取件短信提取出全部取件码`() {
        val failures = POSITIVE.mapNotNull { (body, expected) ->
            val actual = codes(body)
            if (actual == expected) null else "$body\n  期望 $expected，实际 $actual"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `非取件短信不识别`() {
        val failures = NEGATIVE.filter { PickupSmsParser.parse(it, AT).isNotEmpty() }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    private fun codes(body: String) = PickupSmsParser.parse(body, AT).mapNotNull {
        when (val method = it.pickupMethod) {
            is PickupMethod.ShelfCode -> method.code
            is PickupMethod.LockerCode -> method.code
            else -> null
        }
    }

    private companion object {
        val AT: Instant = Instant.parse("2026-10-22T08:00:00Z")

        val POSITIVE = listOf(
            "【菜鸟驿站】您有3个包裹到站，取件码为16-4-9626, 15-3-2194, 16-3-0906，凭码取件" to
                listOf("16-4-9626", "15-3-2194", "16-3-0906"),
            "【丰巢】凭22-2-3579到华润万家旁丰巢柜取件" to listOf("22-2-3579"),
            "【妈妈驿站】取货码2-2-7508，您有极兔快递包裹，已到新创村沙咀里132号" to listOf("2-2-7508"),
            "【兔喜生活】您有包裹已到达兴龙湾30栋兔喜店，取件码为2-3-05025，地址:兴龙湾30号楼102" to listOf("2-3-05025"),
            "【菜鸟驿站】请23:59前到站凭\"72778\"到佛山桂城南约六区185号店站点领取您的顺丰*72778包裹" to listOf("72778"),
            "【中邮驿站】您的邮政包裹已到，取件码A88123，请及时领取" to listOf("A88123"),
            "【妈妈驿站】取货码5-5-9-13，您有包裹YT764306505052已到苍山下坡圆通快递" to listOf("5-5-9-13"),
            "【申通快递】请凭9-7-0993到苍山老菜场斜对面申通快递取您的快递，详询代收驿站" to listOf("9-7-0993"),
            "【圆通快递】凭1-2-21003到森泰首府快递驿站取尾号0236包裹" to listOf("1-2-21003"),
            "【中通快递】凭9-1-21006到森泰首府快递驿站取尾号0107包裹" to listOf("9-1-21006"),
            "【妈妈驿站】3个包裹，取货码2-5-7077，2-5-7075，2-3-4346，中峰乡信用社斜对面圆通速递妈妈驿站" to
                listOf("2-5-7077", "2-5-7075", "2-3-4346"),
            "[兔喜生活]请凭552577至同兴冠寓3号楼1号兔喜快递柜取件，免费存24小时，超时收0.50元3.00元封顶" to listOf("552577"),
            "【南京百米需】您的中通包裹已到x镇x村xx号菜鸟驿站3号柜284号格口，取件码4869-9777" to listOf("4869-9777"),
            "【邮侠】凭取件码1681-5191到x镇x村xx号菜鸟驿站2号柜365号格口,询。" to listOf("1681-5191"),
        )

        val NEGATIVE = listOf(
            "【银行】您的验证码 858822，请勿泄露",
            "【某App】动态验证码 33-3-0444 请在5分钟内输入",
            "【拼多多】您的商品在代收点已超6小时未取，为避免鲜活商品损坏，请尽快取件。",
            "尊敬的客户，您在积分商城兑换的订单1064己发货，请注意查收。物流小可邮政快递包裹，单号123123123也可登录积分商城在“我的订单”处查看物流信息：http://if.189.cn/AFXQLl【中国电信积分商城】",
            "【中国移动卡券提醒】（卡券到账提醒）尊敬的159****6739客户，您的一张“话费券”已到账，请前往中国移动APP-我的-卡券查看使用，或戳我直达：https://dx.10086.cn/A/3Oa0Hw（拒收请回复R）",
            "【蜂鸟配送】您的订单已经送达已送达，宿舍楼下外卖架位置，淘宝闪购搜索719987，领学生专属免单卡，再搜94666还可领叠加红包，如有问题请去订单详情页联系骑手.谢谢～",
            "[e测试]双节特惠:生物SEM、激光共聚焦、Tunel荧光等低至6折券已到您账户，节前提前锁优惠，拒收请回复R",
        )
    }
}
