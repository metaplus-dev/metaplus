
rootProject.name = "metaplus"

/// Proxy: +aliyun

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}


/// Module

include("metaplus-core")
include("metaplus-backend-lib")

