
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}


/// Module

include("metaplus-core")
include("metaplus-backend-lib")
include("metaplus-backend-server")
