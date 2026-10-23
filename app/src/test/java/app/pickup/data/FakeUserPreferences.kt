package app.pickup.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeUserPreferences : UserPreferences {
    override var isOnboardingCompleted = false
        private set

    override fun completeOnboarding() {
        isOnboardingCompleted = true
    }

    private val dismissed = MutableStateFlow<Set<DismissibleBanner>>(emptySet())

    override val dismissedBanners: StateFlow<Set<DismissibleBanner>> = dismissed

    override fun dismissBanner(banner: DismissibleBanner) = dismissed.update { it + banner }

    override fun restoreBanner(banner: DismissibleBanner) = dismissed.update { it - banner }
}
