package app.pickup.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import app.pickup.data.UserPreferences
import javax.inject.Inject

/** 首次引导（spec onboarding.md）。权限状态来自系统，在界面层检查；这里只记引导是否完成 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** 点了「开始使用」：之后启动直接进入取件清单（spec §1） */
    fun completeOnboarding() = userPreferences.completeOnboarding()
}
