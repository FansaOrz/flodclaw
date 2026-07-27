plugins {
    id("fold.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":products:foldclaw:domain"))
    implementation(project(":shared:core"))
    implementation(project(":products:foldclaw:policy"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
