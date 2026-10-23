package app.pickup.ui.permission

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pickup.R

/**
 * 权限按钮的文字（AGENTS.md 按钮口径）：弹系统权限窗、不离开本应用的写「开启」；
 * 跳到系统设置的写「去设置」，后面带外跳图标。
 */
@Composable
fun RowScope.PermissionActionLabel(status: PermissionStatus) {
    if (status == PermissionStatus.Requestable) {
        Text(stringResource(R.string.enable))
    } else {
        OpenSettingsLabel()
    }
}

/** 「去设置」加外跳图标 */
@Composable
fun RowScope.OpenSettingsLabel() {
    Text(stringResource(R.string.open_settings))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Icon(Icons.Rounded.ArrowOutward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
}
