package app.pickup.ui.pickup

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.pickup.data.MessageScanRepository
import app.pickup.data.DismissibleBanner
import app.pickup.data.ParcelRepository
import app.pickup.data.UserPreferences
import app.pickup.model.Parcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * 取件清单（spec pickup_list.md）。
 *
 * ViewModel 的生命周期覆盖切后台、锁屏，期间卡片保持原位；退出应用后再进入是新的实例，
 * 去其他页面再返回由 [leave] 重新归类，两者都按最新状态重新分组（spec §7）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PickupListViewModel @Inject constructor(
    private val parcelRepository: ParcelRepository,
    private val messageScanRepository: MessageScanRepository,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
) : ViewModel() {

    /** 已取区只列最近 30 天（spec §6），起点在进入页面时定下 */
    private val pickedSince = clock.instant().minus(PICKED_RETENTION)

    private val loadRequests = MutableStateFlow(0)

    /** 每件包裹第一次出现时所在的区；之后状态再变也不移动 */
    private val areas = MutableStateFlow<Map<Long, ListArea>>(emptyMap())

    private val failures = MutableStateFlow<Map<Long, CardFailure>>(emptyMap())

    private val refreshState = MutableStateFlow(RefreshState())

    private val parcels: Flow<LoadResult> = loadRequests.flatMapLatest {
        parcelRepository.observeVisibleParcels(pickedSince)
            .onEach(::assignAreas)
            .map<List<Parcel>, LoadResult> { LoadResult.Success(it) }
            .catch { e -> if (e is SQLException) emit(LoadResult.Failed) else throw e }
    }

    val uiState: StateFlow<PickupListUiState> =
        combine(
            parcels,
            areas,
            failures,
            refreshState,
            userPreferences.dismissedBanners,
        ) { result, areas, failures, refresh, dismissedBanners ->
            when {
                result is LoadResult.Failed -> PickupListUiState.LoadFailed
                result !is LoadResult.Success -> PickupListUiState.Loading
                else -> buildPickupListContent(result.parcels, areas, failures, clock.zone).copy(
                    isRefreshing = refresh.isRefreshing,
                    scanFailed = refresh.scanFailed,
                    dismissedBanners = dismissedBanners,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickupListUiState.Loading)

    /** 「查看取件码」或「标记已取」 */
    fun pickUp(id: Long) = markPickedUp(listOf(id))

    /** 「查看全部取件码」：组内所有待取包裹（spec §5） */
    fun pickUpAll(ids: List<Long>) = markPickedUp(ids)

    /** 「撤销已取」或「改回待取」 */
    fun undoPickUp(id: Long) {
        viewModelScope.launch {
            try {
                parcelRepository.markPending(id)
                failures.update { it - id }
            } catch (e: SQLException) {
                failures.update { it + (id to CardFailure.UndoFailed) }
            }
        }
    }

    fun retryLoad() = loadRequests.update { it + 1 }

    /**
     * 进入本页、回到前台、刚获得短信权限时静默扫描（spec §8）。新包裹按 §7 插入，不移动已有卡片；
     * 失败不提示，下次再扫（spec §9）。
     */
    fun scan() {
        viewModelScope.launch {
            try {
                messageScanRepository.scanSms()
            } catch (e: SecurityException) {
                // 没有权限由横幅提示（spec message_source §5）
            } catch (e: SQLException) {
                // 静默是有意为之
            }
        }
    }

    /**
     * 下拉刷新：扫描短信，等同于重新进入本页（spec §7）。刷新态至少保持 [MIN_REFRESH_DURATION]，
     * 让用户看清刷新已触发；结束时才重新归类，「转一下 → 列表整理好」有因果感（spec §8）。
     * 扫描失败时提示，列表保持不变（spec §9）。
     */
    fun refresh() {
        if (refreshState.value.isRefreshing) return
        refreshState.value = RefreshState(isRefreshing = true)
        viewModelScope.launch {
            var scanFailed = false
            try {
                scanFailed = !coroutineScope {
                    launch { delay(MIN_REFRESH_DURATION) }
                    scanForRefresh()
                }
                if (!scanFailed) regroup()
            } finally {
                refreshState.value = RefreshState(scanFailed = scanFailed)
            }
        }
    }

    /** 「扫描短信失败」提示已显示 */
    fun onScanFailedShown() = refreshState.update { it.copy(scanFailed = false) }

    /** 权限横幅点了「不再提示」：记在本机，之后不再出现（spec message_source §5） */
    fun dismissBanner(banner: DismissibleBanner) = userPreferences.dismissBanner(banner)

    /**
     * 每次检查权限后调用：开启着的权限，之前的「不再提示」不再算数，
     * 之后再被关掉是新情况，横幅重新出现（spec message_source §5）
     */
    fun onPermissionsChecked(notificationsGranted: Boolean, notificationAccessGranted: Boolean) {
        if (notificationsGranted) userPreferences.restoreBanner(DismissibleBanner.Notifications)
        if (notificationAccessGranted) userPreferences.restoreBanner(DismissibleBanner.NotificationAccess)
    }

    /**
     * 离开本页去其他页面（spec §7）：回来时应按最新状态重新归类。导航时本 ViewModel 不销毁，
     * 所以在离开时就重新归类；此时页面正在退场，用户看不到卡片挪动。
     */
    fun leave() = regroup()

    /** 清掉落区记录与卡片上的失败提示，重新读一次数据，所有包裹按最新状态落区；之后的状态变化照旧不移动卡片 */
    private fun regroup() {
        areas.value = emptyMap()
        failures.value = emptyMap()
        loadRequests.update { it + 1 }
    }

    /** 扫描成功或没有权限时返回 true：没有权限只重新归类，横幅已说明原因（spec message_source §5） */
    private suspend fun scanForRefresh(): Boolean = try {
        messageScanRepository.scanSms()
        true
    } catch (e: SecurityException) {
        true
    } catch (e: SQLException) {
        false
    }

    private fun markPickedUp(ids: List<Long>) {
        val at = clock.instant()
        viewModelScope.launch {
            // 逐件写入，失败只落在那一张卡片上（spec §9）
            for (id in ids) {
                try {
                    parcelRepository.markPickedUp(id, at)
                    failures.update { it - id }
                } catch (e: SQLException) {
                    failures.update { it + (id to CardFailure.MarkFailed) }
                }
            }
        }
    }

    private fun assignAreas(parcels: List<Parcel>) {
        areas.update { assigned ->
            val newcomers = parcels.filter { it.id !in assigned }
            if (newcomers.isEmpty()) assigned else assigned + newcomers.associate { it.id to it.naturalArea() }
        }
    }

    private data class RefreshState(val isRefreshing: Boolean = false, val scanFailed: Boolean = false)

    private sealed interface LoadResult {
        data class Success(val parcels: List<Parcel>) : LoadResult
        data object Failed : LoadResult
    }

    private companion object {
        val PICKED_RETENTION: Duration = Duration.ofDays(30)

        /** 刷新态最短保持时间；扫描更久就按实际时间 */
        val MIN_REFRESH_DURATION = 700.milliseconds
    }
}
