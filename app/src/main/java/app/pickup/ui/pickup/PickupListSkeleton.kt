package app.pickup.ui.pickup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pickup.R
import app.pickup.ui.theme.PickupTheme

/** 两组地点的卡片数，大致填满一屏 */
private val SKELETON_GROUPS = listOf(3, 2)

/**
 * 首次读取清单时的骨架屏（spec §2「首次加载」）：按真实布局排出汇总、组头与分段卡片，
 * 位置与高度都和内容一致，加载完替换时不跳动。占位块缓慢明暗呼吸。
 */
@Composable
fun PickupListSkeleton(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier
            // 与清单 LazyColumn 的 contentPadding 一致
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SummarySkeleton()
        SKELETON_GROUPS.forEach { count ->
            GroupHeaderSkeleton(Modifier.padding(top = 16.dp, bottom = 6.dp))
            repeat(count) { index ->
                CardSkeleton(index, count)
            }
        }
    }
}

/** 「N 件待取」：与大号数字一行等高 */
@Composable
private fun SummarySkeleton() {
    val lineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.displayLargeEmphasized.lineHeight.toDp()
    }
    Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
        Bone(width = 132.dp, height = 40.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

/** 组头：地点名，与按钮的最小触控高度等高 */
@Composable
private fun GroupHeaderSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.height(LocalMinimumInteractiveComponentSize.current), contentAlignment = Alignment.CenterStart) {
        Bone(width = 112.dp, height = 16.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

/** 卡片：取件码与按钮一行，底部一行次要信息；内边距与行距同 [ParcelCardItem] */
@Composable
private fun CardSkeleton(index: Int, count: Int) {
    val boneColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 按钮连同最小触控区一起占高
            Row(
                modifier = Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Bone(width = 144.dp, height = 24.dp, color = boneColor)
                Spacer(Modifier.weight(1f))
                Bone(width = 104.dp, height = ButtonDefaults.MinHeight, color = boneColor, pill = true)
            }
            val metaHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
            Row(
                modifier = Modifier.padding(top = 8.dp).height(metaHeight).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bone(width = 96.dp, height = 12.dp, color = boneColor)
                Spacer(Modifier.weight(1f))
                Bone(width = 56.dp, height = 12.dp, color = boneColor)
            }
        }
    }
}

/** 一块占位；文字占位用小圆角，按钮占位用胶囊形 */
@Composable
private fun Bone(width: Dp, height: Dp, color: Color, pill: Boolean = false) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(color, if (pill) CircleShape else RoundedCornerShape(6.dp)),
    )
}

@Preview
@Composable
private fun PickupListSkeletonPreview() {
    PickupTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PickupListSkeleton()
        }
    }
}
