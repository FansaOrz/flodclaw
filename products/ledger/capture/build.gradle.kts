plugins {
    id("fold.android.library")
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldledger.capture"
}

dependencies {
    implementation(project(":products:ledger:domain"))
    implementation(project(":products:ledger:data"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
