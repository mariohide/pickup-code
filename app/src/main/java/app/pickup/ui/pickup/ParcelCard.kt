package app.pickup.ui.pickup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pickup.R
import app.pickup.model.PickupMethod
import app.pickup.ui.theme.PickupTheme
import java.time.LocalDate

/** 取件码遮挡：圆点个数固定，不暴露取件码长度（spec §4） */
private const val MASKED_DOT_COUNT = 6

@Composable
fun ParcelCardItem(
    card: ParcelCard,
    onPickUp: (Long) -> Unit,
    onUndoPickUp: (Long) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.largeIncreased,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 页面背景用 surface，卡片容器统一用 surfaceContainerLow；已取区通过透明度整体降低对比度（spec §6）
            .then(if (card.inPickedArea) Modifier.graphicsLayer { alpha = 0.72f } else Modifier),
        shape = shape,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (card.inPickedArea) {
                Text(
                    text = card.location ?: stringResource(R.string.unknown_location),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickupInfo(card, Modifier.weight(1f))
                CardAction(card, onPickUp, onUndoPickUp)
            }
            when {
                card.state == CardState.MarkFailed -> CardError(
                    message = stringResource(R.string.mark_picked_up_failed),
                    onRetry = { onPickUp(card.id) },
                )
                card.undoFailed -> CardError(message = stringResource(R.string.undo_failed), onRetry = null)
            }
            CardMetaRow(card, Modifier.padding(top = 8.dp))
        }
    }
}

/** 卡片底部的次要信息：左侧「尾号 · 快递公司」，为空的项连同分隔点一起省略；到件日期居右（spec §3） */
@Composable
private fun CardMetaRow(card: ParcelCard, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.outline
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = listOfNotNull(card.phoneTail?.let { stringResource(R.string.phone_tail, it) }, card.carrier)
                .joinToString(" · "),
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.arrived_on, card.arrivedOn.monthValue, card.arrivedOn.dayOfMonth),
            style = style,
            color = color,
        )
    }
}

@Composable
private fun PickupInfo(card: ParcelCard, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val method = card.pickupMethod) {
            is PickupMethod.ShelfCode, is PickupMethod.LockerCode -> {
                val masked = card.state == CardState.Pending
                val motion = MaterialTheme.motionScheme
                val codeAlpha by animateFloatAsState(if (masked) 0f else 1f, motion.fastEffectsSpec(), label = "code-alpha")
                // 码与圆点叠放：码始终参与测量，揭码前后高度不变、不推动卡片；揭码只是圆点与码之间的短交叉淡化
                Box(contentAlignment = Alignment.CenterStart) {
                    Column(
                        modifier = Modifier
                            .graphicsLayer { alpha = codeAlpha }
                            // 遮住时码不可见，也不能让读屏读出来
                            .then(if (masked) Modifier.clearAndSetSemantics {} else Modifier),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = method.code(),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = if (card.state == CardState.Picked) colors.onSurfaceVariant else colors.primary,
                        )
                        if (method is PickupMethod.LockerCode) {
                            lockerPosition(method)?.let {
                                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    MaskedCode(Modifier.graphicsLayer { alpha = 1f - codeAlpha })
                }
            }
            is PickupMethod.IdentityCode, is PickupMethod.Other -> {
                Text(
                    text = method.instruction(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (card.state == CardState.Picked) colors.onSurfaceVariant else colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MaskedCode(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(MASKED_DOT_COUNT) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
private fun CardAction(card: ParcelCard, onPickUp: (Long) -> Unit, onUndoPickUp: (Long) -> Unit) {
    when (card.state) {
        CardState.Pending -> FilledTonalButton(
            onClick = { onPickUp(card.id) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(if (card.pickupMethod.hasCode()) R.string.reveal_code else R.string.mark_picked_up))
        }
        CardState.Revealed -> TextButton(onClick = { onUndoPickUp(card.id) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.undo_picked_up))
        }
        CardState.Picked -> TextButton(onClick = { onUndoPickUp(card.id) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.restore_pending))
        }
        // 失败时操作在提示条里（重试）
        CardState.MarkFailed -> Unit
    }
}

@Composable
private fun CardError(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

/** 「7号柜 · 23格口」；柜号、格口都识别不出时为空 */
@Composable
private fun lockerPosition(method: PickupMethod.LockerCode): String? = listOfNotNull(
    method.lockerNumber?.let { stringResource(R.string.locker_number, it) },
    method.compartment?.let { stringResource(R.string.compartment, it) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun PickupMethod.hasCode(): Boolean = this is PickupMethod.ShelfCode || this is PickupMethod.LockerCode

private fun PickupMethod.code(): String = when (this) {
    is PickupMethod.ShelfCode -> code
    is PickupMethod.LockerCode -> code
    is PickupMethod.IdentityCode, is PickupMethod.Other -> error("$this 没有取件码")
}

private fun PickupMethod.instruction(): String = when (this) {
    is PickupMethod.IdentityCode -> instruction
    is PickupMethod.Other -> instruction
    is PickupMethod.ShelfCode, is PickupMethod.LockerCode -> error("$this 有取件码，应显示码")
}

@Preview(showBackground = true)
@Composable
private fun ParcelCardPreview() {
    PickupTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardState.entries.forEach { state ->
                ParcelCardItem(
                    card = ParcelCard(
                        id = state.ordinal.toLong(),
                        inPickedArea = state == CardState.Picked,
                        location = "阳光小区3号楼",
                        phoneTail = "1234",
                        arrivedOn = LocalDate.of(2026, 10, 20),
                        carrier = "申通快递",
                        pickupMethod = PickupMethod.LockerCode("80123456", lockerNumber = "7", compartment = "23"),
                        state = state,
                        undoFailed = false,
                    ),
                    onPickUp = {},
                    onUndoPickUp = {},
                )
            }
        }
    }
}
