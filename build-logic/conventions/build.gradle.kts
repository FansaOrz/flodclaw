plugins {
    `kotlin-dsl`
}

group = "com.foldsuite.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "fold.android.application"
            implementationClass = "com.foldsuite.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "fold.android.library"
            implementationClass = "com.foldsuite.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "fold.android.library.compose"
            implementationClass = "com.foldsuite.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "fold.jvm.library"
            implementationClass = "com.foldsuite.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
