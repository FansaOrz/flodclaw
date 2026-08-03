plugins {
    id("fold.android.library.compose")
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldpods.presentation"
}

dependencies {
    implementation(project(":products:airpods:domain"))
    implementation(project(":products:airpods:bluetooth"))
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    implementation(libs.coroutines.android)
}
