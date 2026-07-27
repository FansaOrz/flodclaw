plugins {
    id("fold.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldclaw"

    defaultConfig {
        applicationId = "com.foldclaw"
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":products:foldclaw:domain"))
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))
    implementation(project(":products:foldclaw:data"))
    implementation(project(":products:foldclaw:agent"))
    implementation(project(":products:foldclaw:policy"))
    implementation(project(":products:foldclaw:device"))
    implementation(project(":products:foldclaw:presentation"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    testImplementation(libs.junit)
}
