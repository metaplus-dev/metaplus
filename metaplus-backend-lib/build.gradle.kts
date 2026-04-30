plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(annotationProcessor.get())
    }
}

// withEsTest
val withEsTestSourceSet = sourceSets.create("withEsTest") {
    java.srcDir("src/withEsTest/java")
    resources.srcDir("src/withEsTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}
configurations[withEsTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[withEsTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())


dependencies {
    implementation(project(":metaplus-core"))

    implementation("org.sjf4j:sjf4j:${Versions.sjf4j}") { isChanging = true }
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.apache.httpcomponents.client5:httpclient5:${Versions.apacheHttpClient5}")
    implementation("net.openhft:zero-allocation-hashing:${Versions.zeroAllocationHashing}")
    implementation("org.apache.calcite:calcite-core:${Versions.calcite}")

    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")
    compileOnly("org.projectlombok:lombok:${Versions.lombok}")
    annotationProcessor("org.projectlombok:lombok:${Versions.lombok}")
    testCompileOnly("org.projectlombok:lombok:${Versions.lombok}")
    testAnnotationProcessor("org.projectlombok:lombok:${Versions.lombok}")

    testImplementation(platform("org.junit:junit-bom:${Versions.junit}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(enforcedPlatform("org.testcontainers:testcontainers-bom:${Versions.testcontainers}"))
    testImplementation("org.testcontainers:testcontainers:${Versions.testcontainers}")
    testImplementation("org.testcontainers:junit-jupiter:${Versions.testcontainers}")
    testImplementation("org.testcontainers:elasticsearch:${Versions.testcontainers}")

    add(withEsTestSourceSet.implementationConfigurationName,
            platform("org.junit:junit-bom:${Versions.junit}"))
    add(withEsTestSourceSet.implementationConfigurationName,
            enforcedPlatform("org.testcontainers:testcontainers-bom:${Versions.testcontainers}"))
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:testcontainers:${Versions.testcontainers}")
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.junit.jupiter:junit-jupiter")
    add(withEsTestSourceSet.runtimeOnlyConfigurationName,
            "org.junit.platform:junit-platform-launcher")
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:junit-jupiter:${Versions.testcontainers}")
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:elasticsearch:${Versions.testcontainers}")

}

tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}


tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("withEsTest") {
    description = "Runs Elasticsearch integration tests."
    group = "verification"
    testClassesDirs = withEsTestSourceSet.output.classesDirs
    classpath = withEsTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
    listOf("metaplus.backend.es.baseUrl", "metaplus.test.es.baseUrl").forEach { key ->
        System.getProperty(key)?.takeIf { it.isNotBlank() }?.let { value ->
            systemProperty(key, value)
        }
    }
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    useJUnitPlatform()
}
