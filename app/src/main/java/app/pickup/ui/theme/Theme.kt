package app.pickup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 主题跟随设备：深浅色跟随系统设置；Android 12 起取壁纸动态配色，更早的系统用 M3 基线配色。
 */
@Composable
fun PickupTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
}

/**
 * 内容滚到顶栏、状态栏下方时，其底色 surfaceContainer 的不透明度：
 * 足以保护状态栏图标，又能隐约透出下面滚过的内容。取件清单与首次引导共用。
 */
internal const val SCROLLED_BAR_ALPHA = 0.85f
