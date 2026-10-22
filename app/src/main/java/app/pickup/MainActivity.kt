package app.pickup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import app.pickup.ui.PickupApp
import app.pickup.ui.theme.PickupTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统栏图标深浅随系统深色模式切换
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PickupTheme {
                PickupApp()
            }
        }
    }
}
