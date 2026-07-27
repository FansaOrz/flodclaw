plugins {
    id("fold.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    // 复制本目录为 apps/<id> 后修改：
    namespace = "com.example.template"

    defaultConfig {
        applicationId = "com.example.template"
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))
    // implementation(project(":products:<id>:domain"))
    // implementation(project(":products:<id>:presentation"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
