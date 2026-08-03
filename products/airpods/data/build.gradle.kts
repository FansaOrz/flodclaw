plugins {
    id("fold.android.library")
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldpods.data"
}

dependencies {
    implementation(project(":products:airpods:domain"))
    implementation(project(":shared:core"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
}
