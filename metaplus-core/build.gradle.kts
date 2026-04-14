plugins {
    id("java")
    id("jacoco")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

configurations {
    compileOnly {
        extendsFrom(annotationProcessor.get())
    }
}


dependencies {
    implementation("org.sjf4j:sjf4j:${Versions.sjf4j}")
    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")

    compileOnly("com.alibaba.fastjson2:fastjson2:${Versions.fastjson2}")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}")
    compileOnly("org.springframework.boot:spring-boot:${Versions.springBootJdk8}")
    compileOnly("org.springframework:spring-web:${Versions.springFrameworkJdk8}")
    compileOnly("org.springframework:spring-webflux:${Versions.springFrameworkJdk8}")

    compileOnly("org.projectlombok:lombok:${Versions.lombok}")
    annotationProcessor("org.projectlombok:lombok:${Versions.lombok}")

    testImplementation(platform("org.junit:junit-bom:${Versions.junit}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework:spring-core:${Versions.springFrameworkJdk8}")
    testImplementation("org.springframework:spring-context:${Versions.springFrameworkJdk8}")
    testImplementation("org.springframework:spring-web:${Versions.springFrameworkJdk8}")
    testImplementation("org.springframework:spring-webflux:${Versions.springFrameworkJdk8}")
    testImplementation("org.springframework:spring-test:${Versions.springFrameworkJdk8}")

    testCompileOnly("org.projectlombok:lombok:${Versions.lombok}")
    testAnnotationProcessor("org.projectlombok:lombok:${Versions.lombok}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}")

}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
