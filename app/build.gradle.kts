import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("kapt") version "1.8.20"
    id("org.jetbrains.compose") version "1.4.0"
}

group = "me.tanstan"
version = "2.1.0"

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    implementation(compose.desktop.currentOs)

    // RxJava3
    implementation ("io.reactivex.rxjava3:rxjava:3.1.6")
    implementation ("io.reactivex.rxjava3:rxkotlin:3.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.1")

    implementation("com.squareup.moshi:moshi:1.14.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("io.netty:netty-all:4.1.86.Final")

    // QRCode Gen
    implementation("com.google.zxing:javase:3.3.0")

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