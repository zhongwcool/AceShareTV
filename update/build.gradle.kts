plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.alex.aceshare.update"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 25
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
