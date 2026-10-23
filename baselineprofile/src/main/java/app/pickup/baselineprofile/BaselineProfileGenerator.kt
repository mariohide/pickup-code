package app.pickup.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录下常用操作路径，生成基线配置文件：首次引导 → 清单滚动 → 设置 → 使用说明与权限 → 逐层返回。
 * 被测的是带 .benchmark 后缀的独立应用（见 app/build.gradle.kts），不碰测试机上真正在用的应用
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "缺少插桩参数 targetAppId"
        }
        rule.collect(packageName = packageName, includeInStartupProfile = true) {
            // 只授给 .benchmark 副本：让清单读到短信、有卡片可画；副本随测试结束卸载
            device.executeShellCommand("pm grant $packageName android.permission.READ_SMS")
            pressHome()
            startActivityAndWait()

            // 首次运行是引导页，之后直接进清单
            device.findObject(By.text("开始使用"))?.click()
            device.wait(Until.hasObject(By.desc("设置")), TIMEOUT_MILLIS)

            device.findObject(By.scrollable(true))?.let { list ->
                list.fling(Direction.DOWN)
                list.fling(Direction.UP)
            }

            device.findObject(By.desc("设置")).click()
            device.wait(Until.hasObject(By.text("使用说明与权限")), TIMEOUT_MILLIS)
            device.findObject(By.text("使用说明与权限")).click()
            device.wait(Until.hasObject(By.text("完成")), TIMEOUT_MILLIS)

            device.pressBack()
            device.wait(Until.hasObject(By.text("使用说明与权限")), TIMEOUT_MILLIS)
            device.pressBack()
            device.wait(Until.hasObject(By.desc("设置")), TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
