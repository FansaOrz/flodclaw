plugins {
    id("fold.android.library.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldclaw.presentation"
}

dependencies {
    implementation(project(":products:foldclaw:domain"))
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))
    implementation(project(":products:foldclaw:agent"))
    implementation(project(":products:foldclaw:policy"))
    implementation(project(":products:foldclaw:device"))
    implementation(project(":products:foldclaw:data"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(libs.adaptive)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)
    implementation(libs.window)

    implementation(libs.hilt.navigation.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
