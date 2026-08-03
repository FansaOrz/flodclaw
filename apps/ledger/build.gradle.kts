plugins {
    id("fold.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.foldledger.app"

    defaultConfig {
        applicationId = "com.foldledger"
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"] = "FoldLedger"
    }

    buildTypes {
        create("next") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".next"
            versionNameSuffix = "-next"
            matchingFallbacks += "debug"
            manifestPlaceholders["appLabel"] = "FoldLedger Next"
        }
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":products:ledger:domain"))
    implementation(project(":products:ledger:data"))
    implementation(project(":products:ledger:capture"))
    implementation(project(":products:ledger:presentation"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
