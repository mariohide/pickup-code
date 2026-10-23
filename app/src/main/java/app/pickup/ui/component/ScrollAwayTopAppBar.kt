package app.pickup.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 随内容滚走的顶栏，仿 Google 相册（2026-10-23 定，pickup_list.md §2）：
 * - 内容的上内边距固定为 [scrollAwayTopAppBarHeight]，顶栏按内容实际滚过的距离 1:1 平移，穿过状态栏移出屏幕；
 *   往上滚时立即从屏幕顶端滑回。
 * - 在内容顶部附近，顶栏贴着内容一起滚，是内容的一部分，保持 surface；
 *   在中段滑回、浮在内容上时，连同状态栏一起换成不透明的 surfaceContainer。
 *
 * 用法：页面铺满整屏，滚动容器挂上 [ScrollAwayTopAppBarState.nestedScrollConnection]，
 * 本组件叠在内容上层（画在内容之后）。状态栏遮罩已含在内，不要再叠 [StatusBarScrim]。
 *
 * @param contentShown 滚动内容是否在屏上；不在（如加载失败换成了别的内容）时顶栏完整显示、不浮起，
 *   免得按残留的滚动位置摆放
 */
@Composable
fun ScrollAwayTopAppBar(
    title: @Composable () -> Unit,
    state: ScrollAwayTopAppBarState,
    modifier: Modifier = Modifier,
    contentShown: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val floatingFraction by animateFloatAsState(
        if (contentShown && state.floating) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val surface = MaterialTheme.colorScheme.surface
    val floatingColor = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier.fillMaxWidth()) {
        TopAppBar(
            title = title,
            modifier = Modifier
                .padding(WindowInsets.statusBars.only(WindowInsetsSides.Top).asPaddingValues())
                .graphicsLayer { translationY = if (contentShown) state.offset else 0f }
                // 底色自己画：TopAppBar 会对传入的容器色再做一次动画，与状态栏遮罩先后变色
                .drawBehind { drawRect(lerp(surface, floatingColor, floatingFraction)) },
            actions = actions,
            // 状态栏由遮罩负责，顶栏只留左右安全区
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        // 画在顶栏之上，顶栏移出屏幕时从它下面穿过
        StatusBarScrim(Modifier.windowInsetsTopHeight(WindowInsets.statusBars), floatingFraction = { floatingFraction })
    }
}

/** 顶栏完整显示时占的高度，含状态栏；滚动内容的上内边距从这里算起 */
@Composable
fun scrollAwayTopAppBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopAppBarDefaults.TopAppBarExpandedHeight

/**
 * [ScrollAwayTopAppBar] 的状态。
 *
 * @param scrolledDistance 内容离顶部滚过的距离；超过 [hideDistance] 后可以只返回一个更大的值
 */
@Stable
class ScrollAwayTopAppBarState internal constructor(
    private val hideDistance: Float,
    private val scrolledDistance: () -> Float,
) {
    /** 顶栏的平移量，0 为完整显示，-[hideDistance] 为整个移出屏幕 */
    internal var offset by mutableFloatStateOf(0f)

    /** 挂在滚动容器上，挂在下拉刷新等里层，顶栏只跟随内容自身的滚动 */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        // 只跟内容实际滚过的距离，不抢滚动
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            offset = (offset + consumed.y).coerceIn(-hideDistance, 0f)
            return Offset.Zero
        }
    }

    /** 顶栏比贴着内容时应在的位置更靠下，即浮在内容上 */
    internal val floating by derivedStateOf {
        offset > -scrolledDistance().coerceAtMost(hideDistance) + 1f
    }

    internal fun expand() {
        offset = 0f
    }
}

/** 用于 LazyColumn 等列表 */
@Composable
fun rememberScrollAwayTopAppBarState(listState: LazyListState): ScrollAwayTopAppBarState =
    rememberScrollAwayTopAppBarState(listState) {
        // 首项的偏移以列表上内边距下沿为 0；首项已滚出屏幕时，滚过的距离早已超过顶栏移出所需
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        when {
            first == null -> 0f
            first.index == 0 -> -first.offset.toFloat()
            else -> Float.POSITIVE_INFINITY
        }
    }

/** 用于 verticalScroll 的整页滚动 */
@Composable
fun rememberScrollAwayTopAppBarState(scrollState: ScrollState): ScrollAwayTopAppBarState =
    rememberScrollAwayTopAppBarState(scrollState) { scrollState.value.toFloat() }

@Composable
private fun rememberScrollAwayTopAppBarState(
    scrollableState: ScrollableState,
    scrolledDistance: () -> Float,
): ScrollAwayTopAppBarState {
    // 顶栏要连同状态栏高度一起移出屏幕，否则会停在状态栏遮罩下面
    val density = LocalDensity.current
    val hideDistance = with(density) { scrollAwayTopAppBarHeight().toPx() }
    val state = remember(scrollableState, hideDistance) { ScrollAwayTopAppBarState(hideDistance, scrolledDistance) }
    // 回到顶部（含内容变短、滚不动了）时顶栏必须完整显示
    LaunchedEffect(state) {
        snapshotFlow { scrollableState.canScrollBackward }.collect { if (!it) state.expand() }
    }
    return state
}
