import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleKsp)
}

group = "me.tanstan"
version = "2.3.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.reouseces)

    // RxJava3
    implementation(libs.rxjava3)
    implementation(libs.rxkotlin3)

    // Kotlin Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.core.jvm)
    implementation(libs.coroutines.rx3)

    // Moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.netty)

    implementation(libs.zxing)

    implementation(project(":net"))
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks.register("prepareKotlinBuildScriptModel"){}

//tasks.withType<KotlinCompile> {
//    kotlinOptions.jvmTarget = "11"
//}

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

compose.resources {

    publicResClass = true
    packageOfResClass = "com.tans.tfiletranserdesktop.resources"
    generateResClass = auto
}