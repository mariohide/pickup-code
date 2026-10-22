package app.pickup.ui.pickup

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pickup.R
import app.pickup.model.PickupMethod
import app.pickup.notification.SmsNotificationListener
import app.pickup.ui.theme.PickupTheme
import java.time.LocalDate

@Composable
fun PickupListRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickupListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsPermissionGranted by remember { mutableStateOf(context.hasSmsPermission()) }
    var notificationAccessGranted by remember { mutableStateOf(context.hasNotificationAccess()) }
    // 每次启动应用只请求一次；拒绝两次后系统不再弹窗，只能从横幅去系统设置（spec message_source §5）
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsPermissionGranted = context.hasSmsPermission()
        if (smsPermissionGranted) viewModel.scan()
    }
    // 进入本页、回到前台（含从系统设置开启权限回来）时重新检查权限并静默扫描（spec §8）
    LifecycleStartEffect(viewModel) {
        smsPermissionGranted = context.hasSmsPermission()
        notificationAccessGranted = context.hasNotificationAccess()
        // 取件码通知里的包裹清单上都有（spec message_source §6）
        NotificationManagerCompat.from(context).cancelAll()
        if (smsPermissionGranted) viewModel.scan()
        val missing = context.missingRuntimePermissions()
        if (missing.isNotEmpty() && !permissionsRequested) {
            permissionsRequested = true
            permissionLauncher.launch(missing.toTypedArray())
        }
        onStopOrDispose {}
    }
    PickupListScreen(
        uiState = uiState,
        smsPermissionGranted = smsPermissionGranted,
        onEnableSmsPermission = { context.openAppSettings() },
        notificationAccessGranted = notificationAccessGranted,
        onEnableNotificationAccess = { context.openNotificationAccessSettings() },
        onScanFailedShown = viewModel::onScanFailedShown,
        onPickUp = viewModel::pickUp,
        onPickUpAll = viewModel::pickUpAll,
        onUndoPickUp = viewModel::undoPickUp,
        onRetryLoad = viewModel::retryLoad,
        onRefresh = viewModel::refresh,
        onOpenSettings = {
            viewModel.leave()
            onOpenSettings()
        },
        modifier = modifier,
    )
}

private fun Context.hasSmsPermission(): Boolean =
    checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

/** 读短信、收新短信、发通知；收短信与读短信同组，读短信已授权时系统直接给，不弹窗 */
private fun Context.missingRuntimePermissions(): List<String> =
    buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

private fun Context.hasNotificationAccess(): Boolean =
    packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

/** Android 11 起可直达本应用的开关，否则到通知使用权列表 */
private fun Context.openNotificationAccessSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(this, SmsNotificationListener::class.java).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    startActivity(intent)
}

@Composable
fun PickupListScreen(
    uiState: PickupListUiState,
    smsPermissionGranted: Boolean,
    onEnableSmsPermission: () -> Unit,
    notificationAccessGranted: Boolean,
    onEnableNotificationAccess: () -> Unit,
    onScanFailedShown: () -> Unit,
    onPickUp: (Long) -> Unit,
    onPickUpAll: (List<Long>) -> Unit,
    onUndoPickUp: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 往下滚时顶栏收起、往上滚立刻出现，长列表时把顶部空间让给内容。
    // 列表在顶栏与状态栏后面滚动；内容滚到其下方时底色转为半透明 surfaceContainer，
    // 既保护状态栏图标，又能隐约看到下面的内容
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanFailed = (uiState as? PickupListUiState.Content)?.scanFailed == true
    val scanFailedText = stringResource(R.string.scan_sms_failed)
    LaunchedEffect(scanFailed) {
        if (scanFailed) {
            snackbarHostState.showSnackbar(scanFailedText)
            onScanFailedShown()
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pickup_list_title)) },
                actions = {
                    // 设置是目前唯一的操作，直接放图标按钮，不放只有一项的溢出菜单（spec settings §1）
                    IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SCROLLED_BAR_ALPHA),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
        when (uiState) {
            PickupListUiState.Loading -> PickupListSkeleton(contentModifier)
            PickupListUiState.LoadFailed -> LoadFailedContent(onRetryLoad, contentModifier)
            is PickupListUiState.Content -> {
                // 列表铺满整屏、在顶栏后面滚动，Scaffold 的内边距作为列表的 contentPadding
                val topInset = padding.calculateTopPadding()
                val pullState = rememberPullToRefreshState()
                // 列表不动，指示器从顶栏下沿浮出（下拉只发生在列表顶部，此时顶栏总是展开的）；刷新中停在拉到位的位置转圈
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullRefreshIndicator(
                            state = pullState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topInset),
                        )
                    },
                ) {
                    PickupList(
                        content = uiState,
                        smsPermissionGranted = smsPermissionGranted,
                        onEnableSmsPermission = onEnableSmsPermission,
                        notificationAccessGranted = notificationAccessGranted,
                        onEnableNotificationAccess = onEnableNotificationAccess,
                        topInset = topInset,
                        bottomInset = padding.calculateBottomPadding(),
                        onPickUp = onPickUp,
                        onPickUpAll = onPickUpAll,
                        onUndoPickUp = onUndoPickUp,
                        // 顶栏只跟随列表自身的滚动：挂在下拉刷新里层，下拉手势消耗的距离不会让顶栏变色
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickupList(
    content: PickupListUiState.Content,
    smsPermissionGranted: Boolean,
    onEnableSmsPermission: () -> Unit,
    notificationAccessGranted: Boolean,
    onEnableNotificationAccess: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    onPickUp: (Long) -> Unit,
    onPickUpAll: (List<Long>) -> Unit,
    onUndoPickUp: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = LIST_HORIZONTAL_PADDING,
            end = LIST_HORIZONTAL_PADDING,
            top = 8.dp + topInset,
            bottom = 16.dp + bottomInset,
        ),
        // 同组卡片之间是 Expressive 分段列表的窄缝，组与组之间靠组头的上边距拉开
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        // 同时只显示一条，短信读取权限更要紧
        if (!smsPermissionGranted) {
            item(key = "sms-permission") {
                PermissionBanner(
                    text = stringResource(R.string.sms_permission_off),
                    onEnable = onEnableSmsPermission,
                    modifier = animateListItem().padding(bottom = 8.dp),
                )
            }
        } else if (!notificationAccessGranted) {
            item(key = "notification-access") {
                PermissionBanner(
                    text = stringResource(R.string.notification_access_off),
                    onEnable = onEnableNotificationAccess,
                    modifier = animateListItem().padding(bottom = 8.dp),
                )
            }
        }
        item(key = "summary") {
            PendingSummary(content.pendingCount)
        }
        if (content.groups.isEmpty()) {
            item(key = "no-pending") {
                Text(
                    text = stringResource(R.string.no_pending_parcels),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        content.groups.forEach { group ->
            item(key = "group:${group.location}", contentType = "group-header") {
                LocationGroupHeader(group, onPickUpAll, animateListItem().padding(top = 16.dp, bottom = 6.dp))
            }
            itemsIndexed(group.cards, key = { _, card -> card.id }, contentType = { _, _ -> "card" }) { index, card ->
                SegmentedParcelCard(card, index, group.cards.size, onPickUp, onUndoPickUp)
            }
        }
        if (content.picked.isNotEmpty()) {
            item(key = "picked-header") {
                PickedHeader(content.picked.size, animateListItem().padding(top = 24.dp, bottom = 6.dp))
            }
            itemsIndexed(content.picked, key = { _, card -> card.id }, contentType = { _, _ -> "card" }) { index, card ->
                SegmentedParcelCard(card, index, content.picked.size, onPickUp, onUndoPickUp)
            }
        }
    }
}

/**
 * 下拉指示器：全程待在内容区内，不滑进顶栏后面（顶栏画在内容上层，会把它切掉）。
 * 随下拉进度淡入、由小放大，并跟手下移一小段；松手后随进度归零原路收回。
 */
@Composable
private fun PullRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val indicatorModifier = modifier
        .graphicsLayer {
            val fraction = state.distanceFraction
            // 进度 0 时贴着内容顶边，进度 1 时下移到顶边下 16dp；超拉时继续跟手
            translationY = fraction * 16.dp.toPx()
            val shown = fraction.coerceIn(0f, 1f)
            alpha = shown
            scaleX = 0.5f + 0.5f * shown
            scaleY = 0.5f + 0.5f * shown
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        .shadow(PullToRefreshDefaults.LoadingIndicatorElevation, CircleShape)
    val containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor
    val indicatorColor = PullToRefreshDefaults.loadingIndicatorColor
    if (isRefreshing) {
        // 刷新中：不确定进度，持续变形
        ContainedLoadingIndicator(
            modifier = indicatorModifier,
            containerColor = containerColor,
            indicatorColor = indicatorColor,
            containerShape = CircleShape,
        )
    } else {
        // 下拉中：图形随下拉进度变形
        ContainedLoadingIndicator(
            progress = { state.distanceFraction.coerceIn(0f, 1f) },
            modifier = indicatorModifier,
            containerColor = containerColor,
            indicatorColor = indicatorColor,
            containerShape = CircleShape,
        )
    }
}

/** 顶栏滚动后的底色不透明度：足以保护状态栏图标，又能隐约透出下面滚过的内容 */
private const val SCROLLED_BAR_ALPHA = 0.85f

/** 列表左右留白，卡片边缘落在这里 */
private val LIST_HORIZONTAL_PADDING = 16.dp

/** 列表项的出现、消失、换位都用主题的 Expressive 弹簧 */
@Composable
private fun LazyItemScope.animateListItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

@Composable
private fun LazyItemScope.SegmentedParcelCard(
    card: ParcelCard,
    index: Int,
    count: Int,
    onPickUp: (Long) -> Unit,
    onUndoPickUp: (Long) -> Unit,
) {
    ParcelCardItem(
        card = card,
        onPickUp = onPickUp,
        onUndoPickUp = onUndoPickUp,
        modifier = animateListItem(),
        // 同组首尾大圆角、中间小圆角，连成一组
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
    )
}

/** 短信读取权限或通知使用权关闭时的常驻横幅（spec §9）：随列表滚动，不加图标 */
@Composable
private fun PermissionBanner(text: String, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.enable_permission))
            }
        }
    }
}

@Composable
private fun PendingSummary(pendingCount: Int, modifier: Modifier = Modifier) {
    // 「4 件待取」：数字是全页唯一的大字，后缀与之基线对齐
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = pendingCount.toString(),
            style = MaterialTheme.typography.displayLargeEmphasized,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(R.string.pending_suffix),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun LocationGroupHeader(
    group: LocationGroup,
    onPickUpAll: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 与按钮的最小触控高度一致：「查看全部取件码」出现或隐藏时组头高度不变，下方卡片不跳动
            .heightIn(min = LocalMinimumInteractiveComponentSize.current),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 「阳光小区3号楼（2 件）」：件数与地点基线对齐；不加地点图标，文字本身贴左侧对齐线
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = group.location ?: stringResource(R.string.unknown_location),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false),
            )
            Text(
                text = stringResource(R.string.piece_count, group.cards.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = group.canRevealAll,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
            // 每组一个，用低强调的标准图标按钮。图标右缘贴右侧对齐线（与卡片右缘、顶栏齿轮同一条竖线）：
            // 右移图标与触控区之间的空白，触控区伸进列表留白，同 M3 顶栏的做法；只移绘制位置，不占布局
            val iconInset = (LocalMinimumInteractiveComponentSize.current - IconButtonDefaults.smallIconSize) / 2
            IconButton(
                onClick = { confirming = true },
                modifier = Modifier.offset(x = iconInset),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = stringResource(R.string.reveal_all_codes))
            }
        }
    }
    if (confirming) {
        RevealAllDialog(
            pendingCount = group.pendingIds.size,
            onConfirm = {
                confirming = false
                // 取确认时的待取包裹：对话框打开期间可能有卡片被单独查看
                onPickUpAll(group.pendingIds)
            },
            onDismiss = { confirming = false },
        )
    }
}

/** 一次把整组记为已取，先确认（spec §5）；之后可以逐件撤销，所以不用错误色 */
@Composable
private fun RevealAllDialog(pendingCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reveal_all_confirm_title)) },
        text = { Text(stringResource(R.string.reveal_all_confirm_body, pendingCount)) },
        confirmButton = { TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reveal)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PickedHeader(count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.picked_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun LoadFailedContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.load_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun PickupListScreenPreview() {
    fun card(id: Long, state: CardState, tail: String, code: String, inPickedArea: Boolean = false) = ParcelCard(
        id = id,
        inPickedArea = inPickedArea,
        location = "阳光小区3号楼",
        phoneTail = tail,
        arrivedOn = LocalDate.of(2026, 10, 20),
        carrier = "申通快递",
        pickupMethod = PickupMethod.ShelfCode(code),
        state = state,
        undoFailed = false,
    )
    PickupTheme {
        PickupListScreen(
            uiState = PickupListUiState.Content(
                pendingCount = 2,
                groups = listOf(
                    LocationGroup(
                        location = "阳光小区3号楼",
                        cards = listOf(card(1, CardState.Revealed, "1234", "3-2-1234"), card(2, CardState.Pending, "5678", "3-2-0567")),
                        pendingIds = listOf(2),
                    ),
                    LocationGroup(
                        location = null,
                        cards = listOf(
                            card(3, CardState.Pending, "1234", "").copy(
                                carrier = null,
                                pickupMethod = PickupMethod.IdentityCode("出示淘宝身份码"),
                            ),
                        ),
                        pendingIds = listOf(3),
                    ),
                ),
                picked = listOf(card(4, CardState.Picked, "9999", "5-1-3021", inPickedArea = true)),
            ),
            smsPermissionGranted = false,
            onEnableSmsPermission = {},
            notificationAccessGranted = true,
            onEnableNotificationAccess = {},
            onScanFailedShown = {},
            onPickUp = {},
            onPickUpAll = {},
            onUndoPickUp = {},
            onRetryLoad = {},
            onRefresh = {},
            onOpenSettings = {},
        )
    }
}
