import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("kapt") version "1.8.20"
    id("org.jetbrains.compose") version "1.4.0"
}

group = "me.tanstan"
version = "2.0.0"

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
}

dependencies {
    implementation(compose.desktop.currentOs)

    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.6.4")

    implementation("com.squareup.moshi:moshi:1.14.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("io.netty:netty-all:4.1.86.Final")

    implementation(project(":net"))
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks.register("prepareKotlinBuildScriptModel"){}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Exe)
            packageName = "tFileTransfer"
        }
        nativeDistributions {
            macOS {
                iconFile.set(project.file("launcher-icons/launcher_macos.icns"))
            }
            windows {
                iconFile.set(project.file("launcher-icons/launcher_windows.ico"))
            }
            linux {
                iconFile.set(project.file("launcher-icons/launcher_linux.png"))
            }
        }
    }
}