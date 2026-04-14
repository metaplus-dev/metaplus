
plugins {
    id("org.springframework.boot") version "3.4.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {

    repositories {
        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        mavenCentral()
    }

}
