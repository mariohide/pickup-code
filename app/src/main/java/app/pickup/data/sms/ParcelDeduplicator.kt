package app.pickup.data.sms

import app.pickup.model.PickupMethod
import java.time.Duration

/** 同一件包裹的多条消息只留先到的那件（spec message_source.md §4） */
object ParcelDeduplicator {

    /** 库里有取件码的包裹往前查这么远就够了：再早的不会与新消息算同一件 */
    val LOOKBACK: Duration = Duration.ofDays(14)

    private val NO_CODE_WINDOW: Duration = Duration.ofDays(3)

    /**
     * [incoming] 中真正新的包裹：与 [existing] 或本批中更早的包裹算同一件的都丢弃。
     * 已有包裹不论待取、已取都不改动。
     */
    fun newParcels(existing: List<ParsedParcel>, incoming: List<ParsedParcel>): List<ParsedParcel> {
        val kept = mutableListOf<ParsedParcel>()
        for (parcel in incoming.sortedBy { it.arrivedAt }) {
            if (existing.none { isSameParcel(it, parcel) } && kept.none { isSameParcel(it, parcel) }) {
                kept += parcel
            }
        }
        return kept
    }

    internal fun isSameParcel(a: ParsedParcel, b: ParsedParcel): Boolean {
        val gap = Duration.between(a.arrivedAt, b.arrivedAt).abs()
        val codeA = a.pickupMethod.code
        val codeB = b.pickupMethod.code
        return if (codeA != null || codeB != null) {
            codeA == codeB &&
                gap <= LOOKBACK &&
                (a.location == null || b.location == null || a.location == b.location)
        } else {
            a.pickupMethod == b.pickupMethod &&
                a.location == b.location &&
                a.carrier == b.carrier &&
                gap <= NO_CODE_WINDOW
        }
    }

    private val PickupMethod.code: String?
        get() = when (this) {
            is PickupMethod.ShelfCode -> code
            is PickupMethod.LockerCode -> code
            is PickupMethod.IdentityCode, is PickupMethod.Other -> null
        }
}
