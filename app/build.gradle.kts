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
        versionCode = 36
        versionName = "0.19.5"
    }