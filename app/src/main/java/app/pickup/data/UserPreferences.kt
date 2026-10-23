package app.pickup.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记在本机的界面状态：首次引导是否完成（spec onboarding §1）、哪些权限横幅点过「不再提示」（spec message_source §5）。
 * 清空已取数据不影响这些，卸载重装后从头开始。
 */
interface UserPreferences {
    /** 首次引导已完成：启动时据此决定首屏，要同步读到 */
    val isOnboardingCompleted: Boolean

    fun completeOnboarding()

    /** 点过「不再提示」的横幅 */
    val dismissedBanners: StateFlow<Set<DismissibleBanner>>

    fun dismissBanner(banner: DismissibleBanner)

    /** 权限开启过：之后再被关掉是新情况，横幅重新出现 */
    fun restoreBanner(banner: DismissibleBanner)
}

/** 可以点「不再提示」关掉的权限横幅；短信权限横幅常驻，不在其中 */
enum class DismissibleBanner {
    Notifications,
    NotificationAccess,
}

@Singleton
class SharedPreferencesUserPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : UserPreferences {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dismissed = MutableStateFlow(
        prefs.getStringSet(KEY_DISMISSED_BANNERS, emptySet()).orEmpty()
            .mapNotNull { name -> DismissibleBanner.entries.find { it.name == name } }
            .toSet(),
    )

    override val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    override val dismissedBanners: StateFlow<Set<DismissibleBanner>> = dismissed.asStateFlow()

    override fun dismissBanner(banner: DismissibleBanner) = saveDismissed { it + banner }

    override fun restoreBanner(banner: DismissibleBanner) = saveDismissed { it - banner }

    private fun saveDismissed(change: (Set<DismissibleBanner>) -> Set<DismissibleBanner>) {
        val before = dismissed.value
        dismissed.update(change)
        if (dismissed.value != before) {
            prefs.edit().putStringSet(KEY_DISMISSED_BANNERS, dismissed.value.map { it.name }.toSet()).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "user_preferences"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_DISMISSED_BANNERS = "dismissed_banners"
    }
}
