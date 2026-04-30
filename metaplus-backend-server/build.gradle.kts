plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

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
    implementation(project(":metaplus-backend-lib"))

    implementation("org.sjf4j:sjf4j:${Versions.sjf4j}") { isChanging = true }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok:${Versions.lombok}")
    annotationProcessor("org.projectlombok:lombok:${Versions.lombok}")
    testCompileOnly("org.projectlombok:lombok:${Versions.lombok}")
    testAnnotationProcessor("org.projectlombok:lombok:${Versions.lombok}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(enforcedPlatform("org.testcontainers:testcontainers-bom:${Versions.testcontainers}"))
    testImplementation("org.testcontainers:testcontainers:${Versions.testcontainers}")
    testImplementation("org.testcontainers:junit-jupiter:${Versions.testcontainers}")
    testImplementation("org.testcontainers:elasticsearch:${Versions.testcontainers}")

    add(withEsTestSourceSet.implementationConfigurationName,
            enforcedPlatform("org.testcontainers:testcontainers-bom:${Versions.testcontainers}"))
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:testcontainers:${Versions.testcontainers}")
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:junit-jupiter:${Versions.testcontainers}")
    add(withEsTestSourceSet.implementationConfigurationName,
            "org.testcontainers:elasticsearch:${Versions.testcontainers}")
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
    useJUnitPlatform()
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
