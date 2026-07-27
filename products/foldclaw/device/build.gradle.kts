plugins {
    id("fold.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldclaw.device"
}

dependencies {
    implementation(project(":products:foldclaw:domain"))
    implementation(project(":shared:core"))
    implementation(project(":products:foldclaw:agent"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
}
