pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FoldSuite"

// Shared
include(":shared:core")
include(":shared:platform")

// FoldClaw product
include(":apps:foldclaw")
include(":products:foldclaw:domain")
include(":products:foldclaw:data")
include(":products:foldclaw:agent")
include(":products:foldclaw:policy")
include(":products:foldclaw:device")
include(":products:foldclaw:presentation")

// AirPods companion product
include(":apps:airpods")
include(":products:airpods:domain")
include(":products:airpods:bluetooth")
include(":products:airpods:data")
include(":products:airpods:presentation")
