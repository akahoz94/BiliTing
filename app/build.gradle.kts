plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tingbili.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tingbili.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 56
        versionName = "0.23.4"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // ===== 发行版归档：统一文件名 + 打完自动落到发行版目录 =====
    //
    // 命名规则与「G:\biliting开发\发行版」历史产物保持一致：
    //   BiliTing-v<versionName>-<release|debug>.apk
    // 例：BiliTing-v0.23.4-release.apk
    val archiveDir = file("G:/biliting开发/发行版")

    afterEvaluate {
        // assemble<Type> 任务在 afterEvaluate 之后才注册，钩子必须延迟到这里
        listOf("Release", "Debug").forEach { type ->
            val lower = type.lowercase()
            val apkName = "BiliTing-v${android.defaultConfig.versionName}-$lower.apk"
            val archive = tasks.register<Copy>("archiveApk$type") {
                dependsOn("assemble$type")
                // 构建产物原名 app-<type>.apk，归档到发行版目录时统一改名
                from(layout.buildDirectory.file("outputs/apk/$lower/app-$lower.apk"))
                into(archiveDir)
                rename { apkName }
            }
            tasks.named("assemble$type") { finalizedBy(archive) }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
