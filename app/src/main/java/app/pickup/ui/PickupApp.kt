package app.pickup.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
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
import kotlinx.coroutines.delay
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
    val entryProvider = remember(backStack) {
        entryProvider {
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
        }
    }
    NavDisplay(
        backStack = backStack,
        // 淡出淡入交替时两页都半透明，垫上页面底色，不露出窗口背景
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = { sharedAxisZ() },
        popTransitionSpec = { sharedAxisZ() },
        predictivePopTransitionSpec = { _ -> sharedAxisZ() },
        // 每个页面有自己的 ViewModel 与可保存状态，出栈时一并清理
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberPoppedEntryReleaseDecorator(backStack, entryProvider),
        ),
        entryProvider = entryProvider,
    )
}

/*
 * 页面切换用 MD3 的共享轴 Z（父子页面之间），参数取自 MDC-Android 的 MaterialSharedAxis：
 * 前进时离开的页面放大到 1.1、进入的页面从 0.8 放大；返回时反过来。
 * 透明度是淡出淡入：离开的页面在进度前 35% 淡出，进入的页面在其后淡入。
 * 时长 450ms（motionDurationLong1），强调缓动；淡出淡入的 35% 按缓动后的进度算，同 MDC
 */

private const val SHARED_AXIS_DURATION_MILLIS = 450

/** 淡出淡入的分界，按缓动后的进度 */
private const val FADE_THROUGH_THRESHOLD = 0.35f

/** MD3 强调缓动（m3_sys_motion_easing_emphasized） */
private val EmphasizedEasing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private val FadeOutEasing = Easing { (EmphasizedEasing.transform(it) / FADE_THROUGH_THRESHOLD).coerceIn(0f, 1f) }
private val FadeInEasing = Easing {
    ((EmphasizedEasing.transform(it) - FADE_THROUGH_THRESHOLD) / (1f - FADE_THROUGH_THRESHOLD)).coerceIn(0f, 1f)
}

private fun <T> sharedAxisTween(easing: Easing) = tween<T>(SHARED_AXIS_DURATION_MILLIS, easing = easing)

/**
 * 按返回栈深度定方向，不用 NavDisplay 的判断：返回手势打断进行中的前进时，NavDisplay 会把返回当成前进。
 * 目标比原先浅是返回，否则是前进（含引导完成后清单代替引导）
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.sharedAxisZ(): ContentTransform {
    val forward = targetState.previousEntries.size >= initialState.previousEntries.size
    val enter = fadeIn(sharedAxisTween(FadeInEasing)) +
        scaleIn(sharedAxisTween(EmphasizedEasing), initialScale = if (forward) 0.8f else 1.1f)
    val exit = fadeOut(sharedAxisTween(FadeOutEasing)) +
        scaleOut(sharedAxisTween(EmphasizedEasing), targetScale = if (forward) 1.1f else 0.8f)
    return enter togetherWith exit
}

/** 离开的页面淡到全透明所需的时长：淡出缓动到达 1 的时刻 */
private val FADE_OUT_MILLIS: Long = run {
    var t = 0f
    while (FadeOutEasing.transform(t) < 1f) t += 0.005f
    (t * SHARED_AXIS_DURATION_MILLIS).toLong()
}

/**
 * 被返回移出返回栈的页面，淡到全透明后不再组合内容，只留不处理触摸的占位。
 * Navigation 3 自 1.3 起返回时让离开的页面留在上层，转场结束前它仍会接住点击，
 * 刚回到的页面收不到；淡完后撤掉内容，点击就落到下面的页面上。
 * 预测性返回拖动时页面还在返回栈里，内容不动，取消返回也不受影响；
 * 退出途中又被打开（同一页面沿用同一份组合）时立即恢复内容
 */
@Composable
private fun rememberPoppedEntryReleaseDecorator(
    backStack: List<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): NavEntryDecorator<NavKey> = remember(backStack, entryProvider) {
    NavEntryDecorator(decorate = { entry ->
        val inBackStack = backStack.any { entryProvider(it).contentKey == entry.contentKey }
        var fadedOut by remember { mutableStateOf(false) }
        LaunchedEffect(inBackStack) {
            fadedOut = false
            if (!inBackStack) {
                delay(FADE_OUT_MILLIS)
                fadedOut = true
            }
        }
        if (!inBackStack && fadedOut) Spacer(Modifier.fillMaxSize()) else entry.Content()
    })
}

