import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("com.gradleup.shadow") version "9.3.0"
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass = "com.example.AgentsPlaygroundKt"
}

tasks {
    shadowJar {
        archiveBaseName.set(project.name)
        archiveClassifier = null
        archiveVersion = null
        mergeServiceFiles()
        dependsOn(distTar, distZip)
        isZip64 = true
    }
}

repositories {
    mavenCentral()
}

tasks {
    withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(false)
            jvmTarget.set(JVM_21)
            freeCompilerArgs.add("-jvm-default=enable")
        }
    }

    withType<Test> {
        useJUnitPlatform()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {

    implementation(platform("org.http4k:http4k-bom:6.46.0.0"))
    implementation("org.http4k.pro:http4k-ai-a2a-sdk")
    implementation("org.http4k:http4k-config")
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-format-moshi")
    implementation("org.http4k:http4k-server-jetty")
    testImplementation("org.http4k.pro:http4k-ai-a2a-client")
    testImplementation("org.http4k:http4k-server-jetty")
    testImplementation("org.http4k:http4k-testing-hamkrest")
    testImplementation("org.http4k:http4k-testing-kotest")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.0.3")
}

