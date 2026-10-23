package app.pickup.ui.pickup

import app.pickup.data.DismissibleBanner
import app.pickup.model.PickupMethod
import java.time.LocalDate

sealed interface PickupListUiState {
    data object Loading : PickupListUiState

    /** 进入页面时列表读取失败（spec §9） */
    data object LoadFailed : PickupListUiState

    data class Content(
        /** 状态为待取的包裹数，与卡片落在哪个区无关 */
        val pendingCount: Int,
        /** 待取区，按地点分组（spec §3） */
        val groups: List<LocationGroup>,
        /** 已取区，不分组（spec §6） */
        val picked: List<ParcelCard>,
        /** 下拉刷新进行中（spec §8） */
        val isRefreshing: Boolean = false,
        /** 下拉刷新扫描短信失败，待显示底部提示，显示后清空（spec §9） */
        val scanFailed: Boolean = false,
        /** 点过「不再提示」的权限横幅（spec message_source §5） */
        val dismissedBanners: Set<DismissibleBanner> = emptySet(),
    ) : PickupListUiState
}

data class LocationGroup(
    /** 为空即「未识别地点」 */
    val location: String?,
    val cards: List<ParcelCard>,
    /** 组内仍是待取状态的包裹，「查看全部取件码」作用于它们 */
    val pendingIds: List<Long>,
) {
    val canRevealAll: Boolean get() = pendingIds.size >= 2
}

data class ParcelCard(
    val id: Long,
    /** 已取区不分组，以地点作卡片标题；待取区由组头承担地点 */
    val inPickedArea: Boolean,
    /** 为空即「未识别地点」 */
    val location: String?,
    val phoneTail: String?,
    val arrivedOn: LocalDate,
    val carrier: String?,
    val pickupMethod: PickupMethod,
    val state: CardState,
    /** 撤销已取 / 改回待取失败，卡片保持原状态并提示 */
    val undoFailed: Boolean,
)

enum class CardState {
    /** 待取：取件码遮住 */
    Pending,

    /** 本页内刚记为已取、留在待取区原位：显示取件码与「撤销已取」 */
    Revealed,

    /** 记为已取失败：取件码照常显示，提示并给「重试」 */
    MarkFailed,

    /** 已取区：取件码直接显示，给「改回待取」 */
    Picked,
}
