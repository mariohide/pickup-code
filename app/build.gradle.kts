import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.pickup"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.pickup"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.1"
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        val storeFilePath = System.getenv("RELEASE_STORE_FILE")
            ?: keystoreProps.getProperty("storeFile")
        val storePasswordVal = System.getenv("RELEASE_STORE_PASSWORD")
            ?: keystoreProps.getProperty("storePassword")
        val keyAliasVal = System.getenv("RELEASE_KEY_ALIAS")
            ?: keystoreProps.getProperty("keyAlias")
        val keyPasswordVal = System.getenv("RELEASE_KEY_PASSWORD")
            ?: keystoreProps.getProperty("keyPassword")

        if (storeFilePath != null && storePasswordVal != null && keyAliasVal != null && keyPasswordVal != null) {
            val storeFileObj = file(storeFilePath)
            if (storeFileObj.exists()) {
                create("release") {
                    storeFile = storeFileObj
                    storePassword = storePasswordVal
                    keyAlias = keyAliasVal
                    keyPassword = keyPasswordVal
                    enableV1Signing = true
                    enableV2Signing = true
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // 本地没配发版密钥时用 debug 签名，方便自测；CI 发版缺密钥时在工作流里就失败
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // 本地单测会构造 SQLiteException 等 framework 类，让 android.jar 桩返回默认值而不是抛错
        unitTests.isReturnDefaultValues = true
    }
}

// 生成基线配置文件时 baselineprofile 插件建的构建类型：装成独立应用，
// 测试跑完卸载时不会连带卸掉测试机上真正在用的 app.pickup 和它的数据。
// 在 buildTypes 里设后缀会被插件按 release 覆盖，所以在变体上设
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "nonMinifiedRelease" || variant.buildType == "benchmarkRelease") {
            variant.applicationId.set("app.pickup.benchmark")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 安装时按基线配置文件预编译；配置文件由 :baselineprofile 生成，提交在 src/release/generated 下
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
