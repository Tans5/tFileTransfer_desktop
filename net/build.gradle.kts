plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.googleKsp)
}

//tasks.withType<KotlinCompile> {
//    kotlinOptions.jvmTarget = "11"
//}

dependencies {

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.core.jvm)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.netty)
    implementation(libs.okio)

    implementation(libs.androidx.annotaion)
}