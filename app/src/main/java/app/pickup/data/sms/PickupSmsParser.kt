package app.pickup.data.sms

import app.pickup.model.PickupMethod
import java.time.Instant

/** 从一条消息解析出的包裹，尚未入库 */
data class ParsedParcel(
    val carrier: String?,
    val arrivedAt: Instant,
    val location: String?,
    val phoneTail: String?,
    val pickupMethod: PickupMethod,
)

/** 取件短信的解析规则（spec message_source.md §2、§3） */
object PickupSmsParser {

    /** 规则有变化时加一：扫描位置按版本记录，新规则会重新回溯扫描（spec message_source.md §1） */
    const val VERSION = 3

    /** 不是取件短信时返回空列表；一条短信有多个取件码时每个码一件 */
    fun parse(body: String, receivedAt: Instant): List<ParsedParcel> {
        if (PICKED_UP_NOTICE.containsMatchIn(body)) return emptyList()
        // 签名参与识别快递公司，但不能被当成地点
        val text = body.replace(SIGNATURE, "")
        val methods = codeMethods(text, isLocker = LOCKER.containsMatchIn(body))
            ?: identityMethod(text)?.let(::listOf)
            ?: tailMethod(text)?.let(::listOf)
            ?: return emptyList()
        val carrier = carrier(body)
        val location = location(text)
        val phoneTail = PHONE_TAIL.find(text)?.groupValues?.get(1)
        return methods.map { ParsedParcel(carrier, receivedAt, location, phoneTail, it) }
    }

    /** [isLocker]：正文或签名提到柜子，如【丰巢】 */
    private fun codeMethods(text: String, isLocker: Boolean): List<PickupMethod>? {
        val codes = CODE_LIST.findAll(text)
            .flatMap { match -> CODE.findAll(match.groupValues[1]).map { it.value } }
            .filter { code -> code.any(Char::isDigit) }
            .distinct()
            .toList()
        if (codes.isEmpty()) return null
        if (!isLocker) return codes.map(PickupMethod::ShelfCode)
        // 「7号柜23格口」或「1号柜A16」
        val locker = LOCKER_NUMBER.find(text)
        val lockerNumber = locker?.groupValues?.get(1)
        val compartment = COMPARTMENT.find(text)?.groupValues?.get(1)
            ?: locker?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
        return codes.map { PickupMethod.LockerCode(it, lockerNumber, compartment) }
    }

    private fun identityMethod(text: String): PickupMethod? {
        val match = IDENTITY_CODE.find(text) ?: return null
        return PickupMethod.IdentityCode("出示${match.groupValues[1]}身份码")
    }

    private fun tailMethod(text: String): PickupMethod? =
        if (TAIL_PICKUP.containsMatchIn(text)) PickupMethod.Other(TAIL_PICKUP_INSTRUCTION) else null

    private fun carrier(body: String): String? = CARRIERS
        .mapNotNull { (keyword, name) -> body.indexOf(keyword).takeIf { it >= 0 }?.let { it to name } }
        .minByOrNull { it.first }
        ?.second

    private fun location(text: String): String? {
        val raw = LOCATION_AFTER_ARRIVAL.find(text)?.groupValues?.get(1)
            ?: LOCATION_BEFORE_PICKUP.find(text)?.groupValues?.get(1)
            ?: return null
        return normalizeLocation(raw)
    }

    /** 同一个地点的不同写法归到同一组（spec §3） */
    internal fun normalizeLocation(raw: String): String? {
        var location = raw.replace(WHITESPACE, "")
        val bracketed = BRACKETED.find(location)
        location = if (bracketed != null && stripSuffixes(location.substring(0, bracketed.range.first)).isEmpty()) {
            // 「菜鸟驿站（阳光小区3号楼店）」：括号前只有品牌，地点在括号里
            bracketed.groupValues[1]
        } else {
            location.replace(BRACKETED, "")
        }
        location = stripSuffixes(location)
        return location.takeIf { it.length in 2..20 }
    }

    private fun stripSuffixes(location: String): String {
        var current = location
        while (true) {
            val stripped = current.replace(LOCATION_SUFFIX, "")
            if (stripped == current) return current
            current = stripped
        }
    }

    private const val TAIL_PICKUP_INSTRUCTION = "凭手机尾号取件"

    private val PICKED_UP_NOTICE = Regex("已取出|已签收|已被取走")
    private val SIGNATURE = Regex("^\\s*【[^】]*】|【[^】]*】\\s*$")
    private val WHITESPACE = Regex("\\s+")

    /**
     * 关键词后的一个或多个码，多个码以「、」「，」「/」分隔；「凭1-5-1596到…」的「凭」后直接跟码。
     * 码可以带括号或引号，如「凭"72778"到…」
     */
    private val CODE_LIST = Regex(
        "(?:取件码|取货码|提货码|取件密码|取件号|凭)[是为：:\\s]*[【\\[\"“「]?" +
            "([A-Za-z0-9-]+(?:\\s*[、,，/]\\s*[A-Za-z0-9-]+)*)",
    )
    private val CODE = Regex("(?<![A-Za-z0-9-])[A-Za-z0-9][A-Za-z0-9-]{2,14}[A-Za-z0-9](?![A-Za-z0-9-])")

    private val LOCKER = Regex("丰巢|快递柜|智能柜|自提柜|\\d+号柜|格口")
    private val LOCKER_NUMBER = Regex("(\\d+)号柜([A-Za-z]?\\d+)?")
    private val COMPARTMENT = Regex("(\\d+)号?格口")

    private val IDENTITY_CODE = Regex("(淘宝|拼多多|菜鸟|京东|抖音)?身份码")
    private val TAIL_PICKUP = Regex("[凭报](?:收件人)?(?:手机)?尾号(?:\\d{4})?(?:取件|取货|领取|取)")
    private val PHONE_TAIL = Regex("尾号[是为：:\\s]*(\\d{4})(?!\\d)")

    private val CARRIERS = listOf(
        "申通" to "申通快递",
        "中通" to "中通快递",
        "圆通" to "圆通速递",
        "韵达" to "韵达快递",
        "顺丰" to "顺丰速运",
        "京东" to "京东快递",
        "极兔" to "极兔速递",
        "邮政" to "中国邮政",
        "EMS" to "EMS",
        "德邦" to "德邦快递",
        "百世" to "百世快递",
        "丹鸟" to "丹鸟",
    )

    private const val LOCATION_END = "(?=[，,。；;！!：:]|请|凭|取件|$)"
    private val LOCATION_AFTER_ARRIVAL = Regex(
        "(?:已到达|已到|已放至|已放入|已放在|已存放至|已存放在|已存放于|已存入|已存至|已投递至|已投递到|已送达|已送至)" +
            "(.+?)$LOCATION_END",
    )
    /** 「到阳光小区3号楼取尾号1234包裹」「至华强北路12号丰巢柜取件」 */
    private val LOCATION_BEFORE_PICKUP = Regex("(?:请到|请至|请前往|前往|到|至)([^，,。；;！!：:]+?)(?:领取|取)")

    private val BRACKETED = Regex("[（(]([^）)]*)[）)]")
    private val LOCATION_SUFFIX = Regex(
        "(?:的|\\d+号柜(?:[A-Za-z]?\\d+)?|\\d+号?格口|菜鸟驿站|妈妈驿站|多多驿站|兔喜生活|驿站|代收点|服务站|快递超市|" +
            "丰巢智能柜|丰巢柜|丰巢|快递柜|智能柜|自提柜)$",
    )
}
