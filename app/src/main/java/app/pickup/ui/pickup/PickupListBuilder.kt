package app.pickup.ui.pickup

import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import java.time.Instant
import java.time.ZoneId

/** 卡片落在哪个区。本次进入页面时定下，之后状态再变也不移动（spec §7）。 */
sealed interface ListArea {
    data object Pending : ListArea

    /** [pickedAt] 是进入页面时的取件时间，作已取区的排序依据，改回待取后也不变 */
    data class Picked(val pickedAt: Instant) : ListArea
}

enum class CardFailure { MarkFailed, UndoFailed }

fun Parcel.naturalArea(): ListArea = when (val status = status) {
    ParcelStatus.Pending -> ListArea.Pending
    is ParcelStatus.PickedUp -> ListArea.Picked(status.at)
}

/**
 * 把包裹排成清单页内容。[areas] 里没有的包裹（本次停留期间新到的）按当前状态落区。
 */
fun buildPickupListContent(
    parcels: List<Parcel>,
    areas: Map<Long, ListArea>,
    failures: Map<Long, CardFailure>,
    zone: ZoneId,
): PickupListUiState.Content {
    val inPendingArea = mutableListOf<Parcel>()
    val inPickedArea = mutableListOf<Pair<Parcel, Instant>>()
    for (parcel in parcels) {
        when (val area = areas[parcel.id] ?: parcel.naturalArea()) {
            ListArea.Pending -> inPendingArea += parcel
            is ListArea.Picked -> inPickedArea += parcel to area.pickedAt
        }
    }

    val groups = inPendingArea
        .groupBy { it.location }
        .map { (location, members) ->
            val sorted = members.sortedWith(compareByDescending<Parcel> { it.arrivedAt }.thenByDescending { it.id })
            val cards = sorted.map { it.toCard(inPickedArea = false, failures[it.id], zone) }
            sorted.first().arrivedAt to LocationGroup(
                location = location,
                cards = cards,
                pendingIds = sorted.filter { it.status == ParcelStatus.Pending }.map { it.id },
            )
        }
        // 未识别地点排最后；其余按组内最近的到件时间，最近的排最上
        .sortedWith(compareBy<Pair<Instant, LocationGroup>> { it.second.location == null }.thenByDescending { it.first })
        .map { it.second }

    val picked = inPickedArea
        .sortedWith(compareByDescending<Pair<Parcel, Instant>> { it.second }.thenBy { it.first.id })
        .map { (parcel, _) -> parcel.toCard(inPickedArea = true, failures[parcel.id], zone) }

    return PickupListUiState.Content(
        pendingCount = parcels.count { it.status == ParcelStatus.Pending },
        groups = groups,
        picked = picked,
    )
}

private fun Parcel.toCard(inPickedArea: Boolean, failure: CardFailure?, zone: ZoneId): ParcelCard {
    val state = when (status) {
        ParcelStatus.Pending -> if (failure == CardFailure.MarkFailed) CardState.MarkFailed else CardState.Pending
        is ParcelStatus.PickedUp -> if (inPickedArea) CardState.Picked else CardState.Revealed
    }
    return ParcelCard(
        id = id,
        inPickedArea = inPickedArea,
        location = location,
        phoneTail = phoneTail,
        arrivedOn = arrivedAt.atZone(zone).toLocalDate(),
        carrier = carrier,
        pickupMethod = pickupMethod,
        state = state,
        undoFailed = failure == CardFailure.UndoFailed && status is ParcelStatus.PickedUp,
    )
}
