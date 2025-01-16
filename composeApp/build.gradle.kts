import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
    kotlin("kapt")
    idea
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
    }
}

springBoot {
    buildInfo()
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation("org.jetbrains.kotlin:kotlin-reflect")
            implementation("org.springframework.boot:spring-boot-starter-actuator")
            implementation("org.springframework.boot:spring-boot-starter-web")
            implementation("org.springframework.retry:spring-retry")
            implementation(libs.oshai.logging)
            implementation(libs.logback.encoder)
            implementation("com.squareup.okhttp3:okhttp")
            implementation(libs.mayakapps.kache)
            implementation(libs.azure.msal)
            implementation(libs.micrometer.tracing)
            implementation(libs.micrometer.bridge.otel)
            implementation(libs.irgaly.kfswatcher)
            implementation(libs.kotlin.coroutines.core)
            implementation(libs.kotlin.coroutines.reactor) // to enable @Scheduled on Kotlin suspending functions

            implementation(libs.spring.boot.starter)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "com.swisscom.health.des.cdr.client.CdrClientApplicationKt"
        javaHome = System.getenv("JDK17")
    //    from(kotlin.targets["desktop"])

        //https://blog.jetbrains.com/kotlin/2022/10/compose-multiplatform-1-2-is-out/#proguard
        // https://conveyor.hydraulic.dev/13.0/configs/jvm/#proguard-obfuscation
        // https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution#minification--obfuscation
        buildTypes.release.proguard {
            obfuscate.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "cdr-client"
            packageVersion = "3.2.3"
            description = "Client to exchange Forum Datenaustausch format xmls with Swisscom"
            copyright = "© 2025 Swisscom (Schweiz) AG. All rights reserved."
            vendor = "Swisscom (Schweiz) AG"
        //    modules = arrayListOf("java.compiler", "java.instrument", "java.naming", "java.net.http", "java.prefs", "java.rmi", "java.scripting", "java.security.jgss", "jdk.httpserver", "jdk.jfr", "jdk.management", "jdk.unsupported")
            includeAllModules = true
  /*          License {
                file = project.file("LICENSE")
            } */
            linux {
                iconFile = project.file("resources/swisscom-logo-lifeform-180x180.png")
            }
        }

    }
}
