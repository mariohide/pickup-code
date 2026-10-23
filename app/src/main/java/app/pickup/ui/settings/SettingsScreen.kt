package app.pickup.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pickup.R
import app.pickup.ui.theme.PickupTheme

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenUsageGuide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenUsageGuide = onOpenUsageGuide,
        onClearPicked = viewModel::clearPicked,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOpenUsageGuide: () -> Unit,
    onClearPicked: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = when (val message = uiState.message) {
        is SettingsMessage.Cleared -> stringResource(R.string.picked_cleared, message.count)
        SettingsMessage.ClearFailed -> stringResource(R.string.clear_picked_failed)
        null -> null
    }
    LaunchedEffect(uiState.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    var confirming by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            UsageGuideItem(onClick = onOpenUsageGuide)
            // 不可恢复的操作单独成区，固定在页面最底部；以后的普通设置项排在它上面（spec settings §2）
            SectionHeader(stringResource(R.string.danger_zone), color = MaterialTheme.colorScheme.error)
            ClearPickedItem(uiState, onClick = { confirming = true })
        }
    }

    if (confirming) {
        ClearPickedDialog(
            pickedCount = uiState.pickedCount,
            onConfirm = {
                confirming = false
                onClearPicked()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** 打开首次引导页随时回看，也是被关掉的权限提示重新开启的入口（spec settings §3） */
@Composable
private fun UsageGuideItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
        supportingContent = { Text(stringResource(R.string.usage_guide_description)) },
    ) {
        Text(stringResource(R.string.usage_guide))
    }
}

@Composable
private fun ClearPickedItem(uiState: SettingsUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val noPicked = (uiState.pickedCount as? PickedCount.Known)?.count == 0
    // 件数还没读到、没有已取数据、正在清空时不可点（spec settings §2）
    val enabled = uiState.pickedCount != PickedCount.Loading && !noPicked && !uiState.isClearing
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = modifier,
        enabled = enabled,
        // 页面是 surface，卡片容器用 surfaceContainer；分段列表项默认容器是 surface，需显式指定，置灰时也保留容器
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        leadingContent = {
            // 可点时用错误色标明破坏性；置灰时退回禁用色，免得红色看起来仍可点
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        },
        supportingContent = {
            Text(stringResource(if (noPicked) R.string.no_picked_data else R.string.clear_picked_description))
        },
    ) {
        Text(
            text = stringResource(R.string.clear_picked),
            color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

@Composable
private fun ClearPickedDialog(pickedCount: PickedCount, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_picked_confirm_title)) },
        text = {
            // 读不到件数时省略件数（spec settings §3）
            val body = when (pickedCount) {
                is PickedCount.Known -> stringResource(R.string.clear_picked_confirm_body_count, pickedCount.count)
                PickedCount.Loading, PickedCount.Unknown -> stringResource(R.string.clear_picked_confirm_body)
            }
            Text(body)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.clear))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PickupTheme {
        SettingsScreen(
            uiState = SettingsUiState(PickedCount.Known(13), isClearing = false, message = null),
            onBack = {},
            onOpenUsageGuide = {},
            onClearPicked = {},
            onMessageShown = {},
        )
    }
}
