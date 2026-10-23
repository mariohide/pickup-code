package app.pickup.ui.pickup

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import app.pickup.data.DismissibleBanner
import app.pickup.model.PickupMethod
import app.pickup.permission.hasSmsPermission
import app.pickup.ui.component.ScrollAwayTopAppBar
import app.pickup.ui.component.rememberScrollAwayTopAppBarState
import app.pickup.ui.component.scrollAwayTopAppBarHeight
import app.pickup.ui.permission.OpenSettingsLabel
import app.pickup.ui.permission.PermissionActionLabel
import app.pickup.ui.permission.PermissionStatus
import app.pickup.ui.permission.rememberAppPermissions
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
    // 启动时不再自动弹系统权限窗：申请改在首次引导里由用户点按钮发起，之后缺的权限只靠横幅提醒（spec onboarding §4）
    val permissions = rememberAppPermissions()
    val notificationsGranted = permissions.notifications == PermissionStatus.Granted
    LaunchedEffect(notificationsGranted, permissions.notificationAccess) {
        viewModel.onPermissionsChecked(notificationsGranted, permissions.notificationAccess)
    }
    // 进入本页、回到前台（含从系统设置开启权限回来）时静默扫描（spec §8）
    LifecycleStartEffect(viewModel) {
        // 新包裹通知里的包裹清单上都有（spec message_source §6）
        NotificationManagerCompat.from(context).cancelAll()
        if (context.hasSmsPermission()) viewModel.scan()
        onStopOrDispose {}
    }
    PickupListScreen(
        uiState = uiState,
        permissionBanner = permissionBanner(
            smsGranted = permissions.sms == PermissionStatus.Granted,
            notifications = permissions.notifications,
            notificationAccessGranted = permissions.notificationAccess,
            dismissed = (uiState as? PickupListUiState.Content)?.dismissedBanners.orEmpty(),
        ),
        onEnableSmsPermission = permissions::openAppDetailsSettings,
        onEnableNotifications = permissions::enableNotifications,
        onEnableNotificationAccess = permissions::openNotificationAccessSettings,
        onDismissBanner = viewModel::dismissBanner,
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

/** 清单顶部的权限横幅，同时只显示一条 */
sealed interface PermissionBannerState {
    /** 短信权限：常驻、不能关，error 色系 */
    data object Sms : PermissionBannerState

    /** 发通知：可关；按钮按系统是否还弹窗写「开启」或「去设置」 */
    data class Notifications(val status: PermissionStatus) : PermissionBannerState

    /** 通知使用权：可关，按钮始终「去设置」 */
    data object NotificationAccess : PermissionBannerState
}

/**
 * 按轻重只取一条：短信 > 发通知 > 通知使用权。发通知关着时不显示通知使用权横幅：
 * 本应用不发通知，就谈不上与短信应用重复提醒（spec message_source §5）
 */
private fun permissionBanner(
    smsGranted: Boolean,
    notifications: PermissionStatus,
    notificationAccessGranted: Boolean,
    dismissed: Set<DismissibleBanner>,
): PermissionBannerState? = when {
    !smsGranted -> PermissionBannerState.Sms
    notifications != PermissionStatus.Granted ->
        PermissionBannerState.Notifications(notifications).takeIf { DismissibleBanner.Notifications !in dismissed }
    !notificationAccessGranted ->
        PermissionBannerState.NotificationAccess.takeIf { DismissibleBanner.NotificationAccess !in dismissed }
    else -> null
}

@Composable
fun PickupListScreen(
    uiState: PickupListUiState,
    permissionBanner: PermissionBannerState?,
    onEnableSmsPermission: () -> Unit,
    onEnableNotifications: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onDismissBanner: (DismissibleBanner) -> Unit,
    onScanFailedShown: () -> Unit,
    onPickUp: (Long) -> Unit,
    onPickUpAll: (List<Long>) -> Unit,
    onUndoPickUp: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 往下滚时顶栏收起、往上滚立刻出现，长列表时把顶部空间让给内容（spec §2）
    val listState = rememberLazyListState()
    val topBarState = rememberScrollAwayTopAppBarState(listState)
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
    ) { padding ->
        // 顶栏不放进 Scaffold，自己叠在内容上层；内容的上边距固定为顶栏完整显示时的高度
        val topInset = scrollAwayTopAppBarHeight()
        Box(Modifier.fillMaxSize()) {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
            when (uiState) {
                PickupListUiState.Loading -> PickupListSkeleton(contentModifier)
                PickupListUiState.LoadFailed -> LoadFailedContent(onRetryLoad, contentModifier)
                is PickupListUiState.Content -> {
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
                        // 列表铺满整屏、在顶栏与状态栏后面滚动
                        PickupList(
                            content = uiState,
                            listState = listState,
                            permissionBanner = permissionBanner,
                            onEnableSmsPermission = onEnableSmsPermission,
                            onEnableNotifications = onEnableNotifications,
                            onEnableNotificationAccess = onEnableNotificationAccess,
                            onDismissBanner = onDismissBanner,
                            topInset = topInset,
                            bottomInset = padding.calculateBottomPadding(),
                            onPickUp = onPickUp,
                            onPickUpAll = onPickUpAll,
                            onUndoPickUp = onUndoPickUp,
                            // 挂在下拉刷新里层，顶栏只跟随列表自身的滚动
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(topBarState.nestedScrollConnection),
                        )
                    }
                }
            }
            ScrollAwayTopAppBar(
                title = { Text(stringResource(R.string.pickup_list_title)) },
                state = topBarState,
                contentShown = uiState is PickupListUiState.Content,
                actions = {
                    // 设置是目前唯一的操作，直接放图标按钮，不放只有一项的溢出菜单（spec settings §1）
                    IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        }
    }
}

@Composable
private fun PickupList(
    content: PickupListUiState.Content,
    listState: LazyListState,
    permissionBanner: PermissionBannerState?,
    onEnableSmsPermission: () -> Unit,
    onEnableNotifications: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onDismissBanner: (DismissibleBanner) -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    onPickUp: (Long) -> Unit,
    onPickUpAll: (List<Long>) -> Unit,
    onUndoPickUp: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = LIST_HORIZONTAL_PADDING,
            end = LIST_HORIZONTAL_PADDING,
            top = 8.dp + topInset,
            bottom = 16.dp + bottomInset,
        ),
        // 同组卡片之间是 Expressive 分段列表的窄缝，组与组之间靠组头的上边距拉开
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        // 同时只显示一条（spec message_source §5）
        when (permissionBanner) {
            PermissionBannerState.Sms -> item(key = "sms-permission") {
                SmsPermissionBanner(onEnableSmsPermission, animateListItem().padding(bottom = 8.dp))
            }
            is PermissionBannerState.Notifications -> item(key = "notifications") {
                DismissiblePermissionBanner(
                    text = stringResource(R.string.notifications_off),
                    status = permissionBanner.status,
                    onEnable = onEnableNotifications,
                    onDismiss = { onDismissBanner(DismissibleBanner.Notifications) },
                    modifier = animateListItem().padding(bottom = 8.dp),
                )
            }
            PermissionBannerState.NotificationAccess -> item(key = "notification-access") {
                DismissiblePermissionBanner(
                    text = stringResource(R.string.notification_access_off),
                    status = PermissionStatus.SettingsOnly,
                    onEnable = onEnableNotificationAccess,
                    onDismiss = { onDismissBanner(DismissibleBanner.NotificationAccess) },
                    modifier = animateListItem().padding(bottom = 8.dp),
                )
            }
            null -> Unit
        }
        item(key = "summary") {
            PendingSummary(content.pendingCount)
        }
        if (content.groups.isEmpty()) {
            item(key = "no-pending") {
                // 新用户首次扫描常常一件都没有，第二行说明应用在工作（spec §8）
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.no_pending_parcels),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.no_pending_parcels_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                PickedHeader(content.picked.size, animateListItem().padding(top = 64.dp, bottom = 8.dp))
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

/**
 * 短信权限关闭时的常驻横幅（spec §9）：随列表滚动，不加图标，不能关。
 * 关了新包裹一件都进不来，用 error 色系，比可选权限的横幅提醒得更重。
 */
@Composable
private fun SmsPermissionBanner(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sms_permission_off),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // 跳到系统设置，按全应用口径写「去设置」并带外跳图标（AGENTS.md 按钮口径）
            TextButton(
                onClick = onEnable,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onErrorContainer),
            ) {
                OpenSettingsLabel()
            }
        }
    }
}

/**
 * 发通知、通知使用权关闭时的横幅：中性色，可点「不再提示」关掉；文字里已写明之后去哪里重新开启（spec message_source §5）。
 * 两个按钮放在文字下方靠右，不与文字并排：横幅文字长，并排会把文字挤成很多行。
 */
@Composable
private fun DismissiblePermissionBanner(
    text: String,
    status: PermissionStatus,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Text(stringResource(R.string.dont_show_again))
                }
                TextButton(onClick = onEnable, shapes = ButtonDefaults.shapes()) {
                    PermissionActionLabel(status)
                }
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

/**
 * 「2 件已取」：与汇总「N 件待取」同句式，表明是与待取区并列的另一个区，而不是又一个地点组（spec §2）。
 * 比汇总小、用低对比色，与已取卡片整体弱化一致；上方留 64dp 把两个区分开，不加分隔线。
 */
@Composable
private fun PickedHeader(count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(R.string.picked_suffix),
            style = MaterialTheme.typography.titleMedium,
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
            permissionBanner = PermissionBannerState.Notifications(PermissionStatus.Requestable),
            onEnableSmsPermission = {},
            onEnableNotifications = {},
            onEnableNotificationAccess = {},
            onDismissBanner = {},
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
