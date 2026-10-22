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
import app.pickup.ui.pickup.PickupListRoute
import app.pickup.ui.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object PickupListNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/** 应用的导航：取件清单为首页，设置页在其上 */
@Composable
fun PickupApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(PickupListNavKey)
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
                SettingsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
