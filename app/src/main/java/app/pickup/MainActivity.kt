package app.pickup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import app.pickup.data.UserPreferences
import app.pickup.ui.PickupApp
import app.pickup.ui.theme.PickupTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统栏图标深浅随系统深色模式切换
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 首屏在第一帧前定下，引导与清单之间不闪
        val onboardingCompleted = userPreferences.isOnboardingCompleted
        setContent {
            PickupTheme {
                PickupApp(onboardingCompleted = onboardingCompleted)
            }
        }
    }
}
