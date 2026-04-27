
plugins {
    id("org.springframework.boot") version "3.4.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "dev.metaplus"
    version = Versions.metaplus
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
