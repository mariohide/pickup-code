package app.pickup.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
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
        transitionSpec = { slideTransform() },
        popTransitionSpec = { slideTransform() },
        predictivePopTransitionSpec = { _ -> slideTransform() },
        // 每个页面有自己的 ViewModel 与可保存状态，出栈时一并清理
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberStackedPageNavEntryDecorator(),
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

/*
 * 页面切换（2026-10-23 定）：上层页面是盖在上面的一张纸，进出都整张滑动，不变透明；
 * 下层页面被推向左侧三分之一，不改透明度，按推开的距离叠 dim。
 * 转场里只有滑动，遮挡与 dim 由 rememberStackedPageNavEntryDecorator 按页面位置画。
 */

/** 下层页面被推向左侧的距离：页面宽度的三分之一 */
private fun pushedAsideOffset(width: Int): Int = -width / 3

/** 下层页面推到最远时 dim 的不透明度，取 M3 scrim 的 32% */
private const val PUSHED_ASIDE_DIM_ALPHA = 0.32f

/** 页面切换的弹簧：不回弹，免得整页滑过头露出边缘；刚度同 Expressive 的默认空间弹簧 */
private fun <T> navSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

/**
 * 按返回栈深度定方向，不用 NavDisplay 的判断：返回手势打断进行中的前进时，NavDisplay 会把返回当成前进。
 * 目标比原先浅是返回，否则是前进（含引导完成后清单代替引导）。
 * 层级也按深度设，越深越在上；但 AnimatedContent 在打断后可能留下错误的层级，所以遮挡另由页面自己保证
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.slideTransform(): ContentTransform {
    val targetDepth = targetState.previousEntries.size
    val transform = if (targetDepth < initialState.previousEntries.size) {
        slideInHorizontally(navSpring(), initialOffsetX = ::pushedAsideOffset) togetherWith
            slideOutHorizontally(navSpring(), targetOffsetX = { it })
    } else {
        slideInHorizontally(navSpring(), initialOffsetX = { it }) togetherWith
            slideOutHorizontally(navSpring(), targetOffsetX = ::pushedAsideOffset)
    }
    return transform.apply { targetContentZIndex = targetDepth.toFloat() }
}

/**
 * 按页面在窗口里的位置画遮挡与 dim，不依赖 AnimatedContent 的层级：
 * - 右边的页面总在上层：每页只画到右边最近一页的左缘为止，画错层级也盖不住上层页面；
 * - 被推向左侧的页面按推开的距离叠 scrim，与裁剪在同一层，不会压暗上层页面。
 * 只看位置，前进、返回、返回手势与打断都适用；手势拖到哪里，dim 跟到哪里。
 */
@Composable
private fun rememberStackedPageNavEntryDecorator(): NavEntryDecorator<NavKey> {
    val scrim = MaterialTheme.colorScheme.scrim
    // 在屏页面左缘在窗口中的横坐标，按 contentKey 记
    val pageLefts = remember { mutableStateMapOf<Any, Float>() }
    return remember(scrim) {
        NavEntryDecorator(decorate = { entry ->
            val key = entry.contentKey
            DisposableEffect(key) { onDispose { pageLefts.remove(key) } }
            Box(
                Modifier
                    .fillMaxSize()
                    // 滑动是外层改摆放位置，本页内容不会重画；位置记进状态，读它的绘制随之刷新
                    .onGloballyPositioned { pageLefts[key] = it.positionInWindow().x }
                    .drawWithContent {
                        val left = pageLefts[key] ?: 0f
                        val maxOffset = pushedAsideOffset(size.width.toInt()).toFloat()
                        val dim = if (maxOffset < 0f) (left / maxOffset).coerceIn(0f, 1f) else 0f
                        val rightPageLeft = pageLefts.values.filter { it > left }.minOrNull()
                        clipRect(right = if (rightPageLeft != null) rightPageLeft - left else size.width) {
                            this@drawWithContent.drawContent()
                            if (dim > 0f) drawRect(scrim, alpha = PUSHED_ASIDE_DIM_ALPHA * dim)
                        }
                    },
            ) { entry.Content() }
        })
    }
}
