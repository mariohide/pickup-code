package app.pickup.model

import java.time.Instant

/** 一件包裹，由消息解析产出（spec pickup_list.md §1）。 */
data class Parcel(
    val id: Long,
    /** 快递公司；识别不出时为空 */
    val carrier: String?,
    val arrivedAt: Instant,
    /** 归一化后的取件地点；识别不出时为空 */
    val location: String?,
    /** 收件手机号后 4 位；短信里没有时为空 */
    val phoneTail: String?,
    val pickupMethod: PickupMethod,
    val status: ParcelStatus,
)

sealed interface ParcelStatus {
    data object Pending : ParcelStatus
    data class PickedUp(val at: Instant) : ParcelStatus
}

sealed interface PickupMethod {
    /** 驿站货架码，如 `3-2-1234` */
    data class ShelfCode(val code: String) : PickupMethod

    /** 快递柜取件码；柜号、格口识别不出时为空 */
    data class LockerCode(
        val code: String,
        val lockerNumber: String?,
        val compartment: String?,
    ) : PickupMethod

    /** 出示淘宝、拼多多身份码等，没有取件码；[instruction] 可直接展示 */
    data class IdentityCode(val instruction: String) : PickupMethod

    /** 其他没有取件码的方式，[instruction] 是原文中的取件说明 */
    data class Other(val instruction: String) : PickupMethod
}
