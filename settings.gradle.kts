pluginManagement {
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

dependencyResolutionManagement {   // ← ye fix hai
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ImgPro"
include(":app")