package app.pickup.ui.settings

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.pickup.data.ParcelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页（spec settings.md） */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val parcelRepository: ParcelRepository,
) : ViewModel() {

    private val isClearing = MutableStateFlow(false)

    private val message = MutableStateFlow<SettingsMessage?>(null)

    private val pickedCount = parcelRepository.observePickedCount()
        .map<Int, PickedCount> { PickedCount.Known(it) }
        .catch { e -> if (e is SQLException) emit(PickedCount.Unknown) else throw e }

    val uiState: StateFlow<SettingsUiState> =
        combine(pickedCount, isClearing, message, ::SettingsUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SettingsUiState(PickedCount.Loading, isClearing = false, message = null),
            )

    /** 清空已取数据，用户已在对话框里确认过 */
    fun clearPicked() {
        if (isClearing.value) return
        isClearing.value = true
        viewModelScope.launch {
            message.value = try {
                SettingsMessage.Cleared(parcelRepository.deletePicked())
            } catch (e: SQLException) {
                SettingsMessage.ClearFailed
            } finally {
                isClearing.value = false
            }
        }
    }

    fun onMessageShown() {
        message.value = null
    }
}
