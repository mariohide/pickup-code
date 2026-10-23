package app.pickup.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush

/**
 * 状态栏遮罩，叠在铺满整屏的内容上层，高度由调用方用 [modifier] 定。取件清单与首次引导共用。
 * 平时是 surface 到透明的渐变：与页面同色的东西滑过时看不出来，内容滚到下面时又能保护状态栏图标。
 * [floatingFraction] 为 1 时换成不透明的 surfaceContainer，与浮在内容上的顶栏连成一体。
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier, floatingFraction: () -> Float = { 0f }) {
    val surface = MaterialTheme.colorScheme.surface
    val floatingColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0f))))
            // 在绘制阶段读进度，滚动、变色时不重组
            .drawBehind { drawRect(floatingColor.copy(alpha = floatingFraction())) },
    )
}
