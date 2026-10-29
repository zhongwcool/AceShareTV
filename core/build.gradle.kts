plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.alex.aceshare.core"
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
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.lifecycle.viewmodel)
    implementation(libs.okhttp.sse)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
}
