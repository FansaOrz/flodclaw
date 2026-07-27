plugins {
    id("fold.jvm.library")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(libs.coroutines.core)
}
