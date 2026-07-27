plugins {
    id("fold.jvm.library")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:platform"))
    implementation(libs.coroutines.core)
}
