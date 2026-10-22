package app.pickup.ui.settings

/** 设置页（spec settings.md） */
data class SettingsUiState(
    val pickedCount: PickedCount,
    /** 正在清空，期间不能再次触发 */
    val isClearing: Boolean,
    /** 待显示的底部提示，显示后清空 */
    val message: SettingsMessage?,
)

sealed interface PickedCount {
    data object Loading : PickedCount

    data class Known(val count: Int) : PickedCount

    /** 读取失败：列表项照常可点，对话框不写件数（spec settings §3） */
    data object Unknown : PickedCount
}

sealed interface SettingsMessage {
    data class Cleared(val count: Int) : SettingsMessage

    data object ClearFailed : SettingsMessage
}
