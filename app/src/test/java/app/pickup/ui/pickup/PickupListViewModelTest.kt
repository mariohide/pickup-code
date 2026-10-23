package app.pickup.ui.pickup

import android.database.sqlite.SQLiteException
import app.pickup.data.DismissibleBanner
import app.pickup.data.FakeMessageScanRepository
import app.pickup.data.FakeParcelRepository
import app.pickup.data.FakeUserPreferences
import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PickupListViewModelTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneOffset.UTC)
    private val parcels = FakeParcelRepository()
    private val scanner = FakeMessageScanRepository(parcels)
    private val preferences = FakeUserPreferences()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `查看取件码后卡片留在待取区原位，待取件数减少`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(listOf(2L, 1L), content.groups.single().cards.map { it.id })
        assertEquals(CardState.Revealed, content.groups.single().cards.single { it.id == 1L }.state)
        assertTrue(content.picked.isEmpty())
        assertEquals(1, content.pendingCount)
    }

    @Test
    fun `下拉刷新后按最新状态重新归类`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        viewModel.refresh()
        advanceUntilIdle()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(listOf(2L), content.groups.single().cards.map { it.id })
        assertEquals(listOf(1L), content.picked.map { it.id })
        assertFalse(content.isRefreshing)
    }

    @Test
    fun `下拉刷新态至少保持 700 毫秒，结束时才重新归类`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        viewModel.refresh()
        advanceTimeBy(699)

        val during = viewModel.uiState.value as PickupListUiState.Content
        assertTrue(during.isRefreshing)
        assertEquals(listOf(2L, 1L), during.groups.single().cards.map { it.id })

        advanceTimeBy(2)

        val after = viewModel.uiState.value as PickupListUiState.Content
        assertFalse(after.isRefreshing)
        assertEquals(listOf(2L), after.groups.single().cards.map { it.id })
    }

    @Test
    fun `下拉刷新后再查看取件码，卡片仍留在原位`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.pickUp(2)

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(listOf(2L, 1L), content.groups.single().cards.map { it.id })
        assertEquals(CardState.Revealed, content.groups.single().cards.single { it.id == 2L }.state)
        assertTrue(content.picked.isEmpty())
    }

    @Test
    fun `去设置页再回来时按最新状态重新归类`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        viewModel.leave()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(listOf(2L), content.groups.single().cards.map { it.id })
        assertEquals(listOf(1L), content.picked.map { it.id })
    }

    @Test
    fun `撤销已取后卡片仍在原位，恢复待取`() = runTest {
        parcels.items.value = listOf(pending(1))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        viewModel.undoPickUp(1)

        val card = (viewModel.uiState.value as PickupListUiState.Content).groups.single().cards.single()
        assertEquals(CardState.Pending, card.state)
    }

    @Test
    fun `记为已取失败时卡片显示为 MarkFailed，重试成功后恢复`() = runTest {
        parcels.items.value = listOf(pending(1))
        val viewModel = viewModel()
        collect(viewModel)

        parcels.failWrites = true
        viewModel.pickUp(1)
        val failed = (viewModel.uiState.value as PickupListUiState.Content).groups.single().cards.single()
        assertEquals(CardState.MarkFailed, failed.state)

        parcels.failWrites = false
        viewModel.pickUp(1)
        val retried = (viewModel.uiState.value as PickupListUiState.Content).groups.single().cards.single()
        assertEquals(CardState.Revealed, retried.state)
    }

    @Test
    fun `列表读取失败后可重试`() = runTest {
        parcels.failReads = true
        val viewModel = viewModel()
        collect(viewModel)
        assertEquals(PickupListUiState.LoadFailed, viewModel.uiState.value)

        parcels.failReads = false
        viewModel.retryLoad()
        assertTrue(viewModel.uiState.value is PickupListUiState.Content)
    }

    @Test
    fun `静默扫描到的新包裹插入对应的组，不移动已有卡片`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        scanner.nextScan = listOf(pending(3))
        viewModel.scan()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(listOf(3L, 2L, 1L), content.groups.single().cards.map { it.id })
        assertEquals(CardState.Revealed, content.groups.single().cards.single { it.id == 1L }.state)
    }

    @Test
    fun `静默扫描失败不提示`() = runTest {
        parcels.items.value = listOf(pending(1))
        val viewModel = viewModel()
        collect(viewModel)

        scanner.failure = FakeMessageScanRepository.Failure.ReadFailed
        viewModel.scan()

        assertFalse((viewModel.uiState.value as PickupListUiState.Content).scanFailed)
    }

    @Test
    fun `下拉刷新会扫描短信，新包裹随重新归类一起出现`() = runTest {
        parcels.items.value = listOf(pending(1))
        val viewModel = viewModel()
        collect(viewModel)

        scanner.nextScan = listOf(pending(2))
        viewModel.refresh()
        advanceUntilIdle()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertEquals(1, scanner.scanCount)
        assertEquals(listOf(2L, 1L), content.groups.single().cards.map { it.id })
    }

    @Test
    fun `下拉刷新扫描失败时提示，列表保持不变`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        scanner.failure = FakeMessageScanRepository.Failure.ReadFailed
        viewModel.refresh()
        advanceUntilIdle()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertTrue(content.scanFailed)
        assertFalse(content.isRefreshing)
        assertEquals(listOf(2L, 1L), content.groups.single().cards.map { it.id })

        viewModel.onScanFailedShown()
        assertFalse((viewModel.uiState.value as PickupListUiState.Content).scanFailed)
    }

    @Test
    fun `没有短信权限时下拉刷新只重新归类，不提示失败`() = runTest {
        parcels.items.value = listOf(pending(1), pending(2))
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.pickUp(1)
        scanner.failure = FakeMessageScanRepository.Failure.NoPermission
        viewModel.refresh()
        advanceUntilIdle()

        val content = viewModel.uiState.value as PickupListUiState.Content
        assertFalse(content.scanFailed)
        assertEquals(listOf(2L), content.groups.single().cards.map { it.id })
        assertEquals(listOf(1L), content.picked.map { it.id })
    }

    @Test
    fun `点了不再提示的横幅记下，权限开启过后重新算数`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.dismissBanner(DismissibleBanner.NotificationAccess)
        viewModel.onPermissionsChecked(notificationsGranted = false, notificationAccessGranted = false)
        assertEquals(setOf(DismissibleBanner.NotificationAccess), (viewModel.uiState.value as PickupListUiState.Content).dismissedBanners)

        // 开启过通知使用权，之后再被关掉时横幅应重新出现
        viewModel.onPermissionsChecked(notificationsGranted = false, notificationAccessGranted = true)
        assertTrue((viewModel.uiState.value as PickupListUiState.Content).dismissedBanners.isEmpty())
    }

    @Test
    fun `两条可关的横幅各记各的`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.dismissBanner(DismissibleBanner.Notifications)
        viewModel.dismissBanner(DismissibleBanner.NotificationAccess)
        viewModel.onPermissionsChecked(notificationsGranted = true, notificationAccessGranted = false)

        assertEquals(setOf(DismissibleBanner.NotificationAccess), (viewModel.uiState.value as PickupListUiState.Content).dismissedBanners)
    }

    private fun viewModel() = PickupListViewModel(parcels, scanner, preferences, clock)

    private fun TestScope.collect(viewModel: PickupListViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun pending(id: Long) = Parcel(
        id = id,
        carrier = "申通快递",
        arrivedAt = clock.instant().minusSeconds(3600 - id),
        location = "阳光小区3号楼",
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode("3-2-$id"),
        status = ParcelStatus.Pending,
    )
}
