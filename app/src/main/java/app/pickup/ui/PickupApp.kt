package app.pickup.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pickup.ui.onboarding.OnboardingRoute
import app.pickup.ui.pickup.PickupListRoute
import app.pickup.ui.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object PickupListNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/** 首次引导；[fromSettings] 时是设置页「使用说明与权限」打开的同一页面（spec onboarding §1） */
@Serializable
data class OnboardingNavKey(val fromSettings: Boolean) : NavKey

/** 应用的导航：取件清单为首页，设置页在其上；引导没完成时先进引导，完成后由清单代替 */
@Composable
fun PickupApp(onboardingCompleted: Boolean, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(if (onboardingCompleted) PickupListNavKey else OnboardingNavKey(fromSettings = false))
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        },
        predictivePopTransitionSpec = { _ ->
            slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() togetherWith
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        },
        // 每个页面有自己的 ViewModel 与可保存状态，出栈时一并清理
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<PickupListNavKey> {
                PickupListRoute(onOpenSettings = { backStack.add(SettingsNavKey) })
            }
            entry<SettingsNavKey> {
                SettingsRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenUsageGuide = { backStack.add(OnboardingNavKey(fromSettings = true)) },
                )
            }
            entry<OnboardingNavKey> { key ->
                OnboardingRoute(
                    fromSettings = key.fromSettings,
                    onDone = {
                        if (key.fromSettings) {
                            backStack.removeLastOrNull()
                        } else {
                            // 首次引导完成：清单代替引导成为首页，返回不再回到引导
                            backStack.add(PickupListNavKey)
                            backStack.remove(key)
                        }
                    },
                )
            }
        },
    )
}
