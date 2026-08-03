plugins {
    id("fold.android.library")
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldpods.bluetooth"
}

dependencies {
    implementation(project(":products:airpods:domain"))
    implementation(project(":shared:core"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.hiddenapibypass)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
