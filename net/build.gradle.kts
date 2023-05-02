plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("kapt") version "1.8.20"
}

//tasks.withType<KotlinCompile> {
//    kotlinOptions.jvmTarget = "11"
//}

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
}

dependencies {

    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.6.4")

    implementation("com.squareup.moshi:moshi:1.14.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("io.netty:netty-all:4.1.86.Final")

    implementation("androidx.annotation:annotation:1.6.0")
    implementation("com.squareup.okio:okio:3.3.0")
}