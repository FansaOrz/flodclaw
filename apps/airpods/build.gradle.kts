plugins {
    id("fold.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldpods"

    defaultConfig {
        applicationId = "com.foldpods"
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":products:airpods:domain"))
    implementation(project(":products:airpods:presentation"))
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
