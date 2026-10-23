package app.pickup.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import app.pickup.ui.theme.SCROLLED_BAR_ALPHA
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pickup.R
import app.pickup.ui.permission.PermissionActionLabel
import app.pickup.ui.permission.PermissionStatus
import app.pickup.ui.permission.rememberAppPermissions
import app.pickup.ui.theme.PickupTheme

/**
 * 首次引导（spec onboarding.md）。[fromSettings] 时是设置页「使用说明与权限」打开的同一页面：
 * 底部按钮改为「完成」，始终可点，点了回到设置。
 */
@Composable
fun OnboardingRoute(
    fromSettings: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissions = rememberAppPermissions()
    // 去过通知使用权设置、回来仍未开启：可能被受限设置拦下，在这一行加说明（spec §3）。
    // 按回来时算，免得点「去设置」的一瞬间提示先冒出来
    var notificationAccessSettingsOpened by rememberSaveable { mutableStateOf(false) }
    var returnedFromNotificationAccessSettings by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (notificationAccessSettingsOpened) returnedFromNotificationAccessSettings = true
        onPauseOrDispose {}
    }
    OnboardingScreen(
        fromSettings = fromSettings,
        sms = permissions.sms,
        notifications = permissions.notifications,
        notificationAccessGranted = permissions.notificationAccess,
        showRestrictedSettingsHint = returnedFromNotificationAccessSettings && !permissions.notificationAccess,
        onEnableSms = permissions::enableSms,
        onEnableNotifications = permissions::enableNotifications,
        onOpenNotificationAccessSettings = {
            notificationAccessSettingsOpened = true
            permissions.openNotificationAccessSettings()
        },
        onOpenAppDetails = permissions::openAppDetailsSettings,
        onDone = {
            if (!fromSettings) viewModel.completeOnboarding()
            onDone()
        },
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    fromSettings: Boolean,
    sms: PermissionStatus,
    notifications: PermissionStatus,
    notificationAccessGranted: Boolean,
    showRestrictedSettingsHint: Boolean,
    onEnableSms: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只有短信权限必需：没有它清单永远是空的（spec §1）
    val canFinish = fromSettings || sms == PermissionStatus.Granted
    val scrollState = rememberScrollState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            // 下面还有内容（正从按钮区后面经过）时抬升
            DoneBar(
                fromSettings = fromSettings,
                enabled = canFinish,
                elevated = scrollState.canScrollForward,
                onDone = onDone,
            )
        },
    ) { padding ->
        // 内容超出一屏时整页滚动，底部按钮固定（spec §2）。
        // 页面铺满整屏、在状态栏与底部按钮区后面滚动，Scaffold 的内边距放在滚动区里面作为内容边距；
        // 状态栏底色叠在内容上层
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(horizontal = PAGE_HORIZONTAL_PADDING)
                    .padding(top = 32.dp, bottom = 16.dp),
            ) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                CoreDesign(Modifier.padding(top = 24.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    PermissionRow(
                        title = stringResource(R.string.permission_sms),
                        optional = false,
                        description = stringResource(R.string.permission_sms_description),
                        status = sms,
                        onClick = onEnableSms,
                        index = 0,
                    )
                    PermissionRow(
                        title = stringResource(R.string.permission_notifications),
                        optional = true,
                        description = stringResource(R.string.permission_notifications_description),
                        status = notifications,
                        onClick = onEnableNotifications,
                        index = 1,
                    )
                    PermissionRow(
                        title = stringResource(R.string.permission_notification_access),
                        optional = true,
                        description = stringResource(R.string.permission_notification_access_description),
                        // 没有系统权限窗，按钮始终是「去设置」（spec §3）
                        status = if (notificationAccessGranted) PermissionStatus.Granted else PermissionStatus.SettingsOnly,
                        onClick = onOpenNotificationAccessSettings,
                        index = 2,
                        hint = if (showRestrictedSettingsHint) {
                            { RestrictedSettingsHint(onOpenAppDetails, Modifier.padding(top = 4.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            StatusBarScrim(scrolled = scrollState.value > 0)
        }
    }
}

/**
 * 核心设计只讲一条：看码 = 已取。一种字号、一段话，不配图标，不写强提醒（spec §2）。
 * 全页唯一要用户改习惯的地方，用 primaryContainer 与下面的权限行区分开
 */
@Composable
private fun CoreDesign(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            // 全页唯一的重点，用强调字体（AGENTS.md 界面约定）
            Text(text = stringResource(R.string.onboarding_core_title), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                text = stringResource(R.string.onboarding_core_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 权限一行，样式同设置页的分段列表（spec §2）：名称与用途在左，操作在右，各行的操作排成一列，
 * 与取件清单卡片上的按钮同侧。已开启时按钮置灰、写「已开启」，开启前后这一行不跳。
 * [hint]（受限设置提示）较长，放在这一行下面，占满行宽。
 */
@Composable
private fun PermissionRow(
    title: String,
    optional: Boolean,
    description: String,
    status: PermissionStatus,
    onClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
    hint: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ListItemDefaults.segmentedShapes(index = index, count = PERMISSION_COUNT).shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.alignByBaseline())
                        if (optional) {
                            Text(
                                text = stringResource(R.string.permission_optional),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // 同取件卡片上的按钮：重复出现的卡片内控件用 tonal（AGENTS.md 界面约定）。
                // 已开启时保留按钮外形、置灰写「已开启」，各行右侧都是按钮，排得整齐
                val granted = status == PermissionStatus.Granted
                FilledTonalButton(
                    onClick = onClick,
                    enabled = !granted,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    if (granted) Text(stringResource(R.string.permission_granted)) else PermissionActionLabel(status)
                }
            }
            hint?.invoke()
        }
    }
}

/**
 * 受限设置提示（spec §3）：「应用信息」是链接样式，后面嵌同色的外跳图标，表明会离开本应用。
 * 整段只有一个操作，所以整段作为一个可点区域：四周外扩 8dp，按下时整段出涟漪，可点高度不小于 48dp。
 */
@Composable
private fun RestrictedSettingsHint(onOpenAppDetails: () -> Unit, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(stringResource(R.string.restricted_settings_hint_before))
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(stringResource(R.string.restricted_settings_hint_link))
        }
        // 图标不加下划线；读屏读整段文字，这里的替代文字只占位
        appendInlineContent(OUTWARD_ICON_ID, alternateText = " ")
        append(stringResource(R.string.restricted_settings_hint_after))
    }
    val inlineContent = mapOf(
        OUTWARD_ICON_ID to InlineTextContent(
            Placeholder(width = 1.em, height = 1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            Icon(Icons.Rounded.ArrowOutward, contentDescription = null, tint = linkColor)
        },
    )
    Box(
        modifier = modifier
            .bleedHorizontally(HINT_BLEED)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onOpenAppDetails)
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .padding(horizontal = HINT_BLEED, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 左右各伸出 [bleed]，伸进父布局的内边距：可点区域比文字宽，文字仍与上面对齐 */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = (bleed * 2).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

/**
 * 状态栏底色同取件清单的顶栏：内容滚到状态栏下方时转为半透明 surfaceContainer，
 * 既保护状态栏图标，又能隐约看到下面的内容；在顶部时透明
 */
@Composable
private fun StatusBarScrim(scrolled: Boolean, modifier: Modifier = Modifier) {
    val scrolledColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SCROLLED_BAR_ALPHA)
    val color by animateColorAsState(
        targetValue = if (scrolled) scrolledColor else scrolledColor.copy(alpha = 0f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color),
    )
}

/**
 * 底部固定的「开始使用」；从设置进入时是「完成」，始终可点。
 * [elevated]：内容正从按钮区后面经过，底色换成半透明 surfaceContainer 抬升，同状态栏；底色铺到导航栏
 */
@Composable
private fun DoneBar(
    fromSettings: Boolean,
    enabled: Boolean,
    elevated: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (elevated) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SCROLLED_BAR_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = motion.defaultEffectsSpec(),
    )
    Surface(modifier = modifier.fillMaxWidth(), color = containerColor) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = PAGE_HORIZONTAL_PADDING, vertical = 16.dp),
        ) {
            // 按钮点不动时说明原因（spec §1）；开启短信权限后说明消失
            AnimatedVisibility(
                visible = !enabled,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
            ) {
                Text(
                    text = stringResource(R.string.start_using_requires_sms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onDone,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                Text(
                    text = stringResource(if (fromSettings) R.string.done else R.string.start_using),
                    style = ButtonDefaults.textStyleFor(height),
                )
            }
        }
    }
}

private const val PERMISSION_COUNT = 3

private const val OUTWARD_ICON_ID = "outward"

private val PAGE_HORIZONTAL_PADDING = 16.dp

private val HINT_BLEED = 8.dp

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun OnboardingScreenPreview() {
    PickupTheme {
        OnboardingScreen(
            fromSettings = false,
            sms = PermissionStatus.Granted,
            notifications = PermissionStatus.Requestable,
            notificationAccessGranted = false,
            showRestrictedSettingsHint = true,
            onEnableSms = {},
            onEnableNotifications = {},
            onOpenNotificationAccessSettings = {},
            onOpenAppDetails = {},
            onDone = {},
        )
    }
}
