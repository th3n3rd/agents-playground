import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
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

    implementation(platform(libs.http4k.bom))
    implementation(libs.http4k.ai.a2a.sdk)
    implementation(libs.http4k.ai.a2a.client)
    implementation(libs.http4k.ai.llm.core)
    implementation(libs.http4k.ai.llm.openai)
    implementation(libs.http4k.ai.llm.openai.fake)
    implementation(libs.http4k.ai.mcp.sdk)
    implementation(libs.http4k.ai.mcp.client)
    implementation(libs.http4k.ai.mcp.testing)
    implementation(libs.http4k.config)
    implementation(libs.http4k.core)
    implementation(libs.http4k.format.moshi)
    implementation(libs.http4k.ops.opentelemetry)
    implementation(libs.http4k.server.jetty)
    testImplementation(libs.http4k.server.jetty)
    testImplementation(libs.http4k.testing.hamkrest)
    testImplementation(libs.http4k.testing.kotest)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.opentelemetry.sdk.testing)
}

