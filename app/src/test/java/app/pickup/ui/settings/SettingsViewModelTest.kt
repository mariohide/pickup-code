package app.pickup.ui.settings

import app.pickup.data.FakeParcelRepository
import app.pickup.model.Parcel
import app.pickup.model.ParcelStatus
import app.pickup.model.PickupMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val now = Instant.parse("2026-10-22T08:00:00Z")
    private val parcels = FakeParcelRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `显示已取件数`() = runTest {
        parcels.items.value = listOf(pending(1), picked(2), picked(3))
        val viewModel = SettingsViewModel(parcels)
        collect(viewModel)

        assertEquals(PickedCount.Known(2), viewModel.uiState.value.pickedCount)
    }

    @Test
    fun `清空后只留下待取包裹，并提示清空了几件`() = runTest {
        parcels.items.value = listOf(pending(1), picked(2), picked(3))
        val viewModel = SettingsViewModel(parcels)
        collect(viewModel)

        viewModel.clearPicked()

        assertEquals(listOf(1L), parcels.items.value.map { it.id })
        assertEquals(PickedCount.Known(0), viewModel.uiState.value.pickedCount)
        assertEquals(SettingsMessage.Cleared(2), viewModel.uiState.value.message)
    }

    @Test
    fun `清空失败时数据不变，提示清空失败`() = runTest {
        parcels.items.value = listOf(pending(1), picked(2))
        val viewModel = SettingsViewModel(parcels)
        collect(viewModel)

        parcels.failWrites = true
        viewModel.clearPicked()

        assertEquals(listOf(1L, 2L), parcels.items.value.map { it.id })
        assertEquals(SettingsMessage.ClearFailed, viewModel.uiState.value.message)
        assertEquals(false, viewModel.uiState.value.isClearing)
    }

    @Test
    fun `读不到已取件数时为未知`() = runTest {
        parcels.failReads = true
        val viewModel = SettingsViewModel(parcels)
        collect(viewModel)

        assertEquals(PickedCount.Unknown, viewModel.uiState.value.pickedCount)
    }

    @Test
    fun `提示显示后清掉`() = runTest {
        parcels.items.value = listOf(picked(1))
        val viewModel = SettingsViewModel(parcels)
        collect(viewModel)

        viewModel.clearPicked()
        viewModel.onMessageShown()

        assertNull(viewModel.uiState.value.message)
    }

    private fun TestScope.collect(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun pending(id: Long) = parcel(id, ParcelStatus.Pending)

    private fun picked(id: Long) = parcel(id, ParcelStatus.PickedUp(now))

    private fun parcel(id: Long, status: ParcelStatus) = Parcel(
        id = id,
        carrier = "申通快递",
        arrivedAt = now.minusSeconds(3600),
        location = "阳光小区3号楼",
        phoneTail = "1234",
        pickupMethod = PickupMethod.ShelfCode("3-2-$id"),
        status = status,
    )
}
