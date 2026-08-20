import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("java-library")
    id("com.github.ben-manes.versions") version "0.61.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    kotlin("kapt") version "2.4.10"
}

private val kotlinVersion = "2.4.10"
extra["kotlin.version"] = kotlinVersion
extra["jackson-bom.version"] = "2.21.5"
extra["log4j2.version"] = "2.25.5"
extra["postgresql.version"] = "42.7.12"

group = "no.novari"
version = findProperty("version")?.toString() ?: "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.fintlabs.no/releases")
    }
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.26.0") {
            because("Fixes CVE-2024-25710 and CVE-2024-26308 in the Testcontainers transitive dependency")
        }
    }

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.hibernate.orm:hibernate-envers")
    api("io.hypersistence:hypersistence-utils-hibernate-63:3.15.4")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.github.ben-manes.caffeine:caffeine")

    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-client")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

publishing {
    repositories {
        maven {
            url = uri("https://repo.fintlabs.no/releases")
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return !isStable
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
