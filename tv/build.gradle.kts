plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.alex.aceshare"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.alex.aceshare"
        minSdk = 25
        targetSdk = 37
        val versionCodeBase = (findProperty("VERSION_CODE_BASE") as String?)?.toIntOrNull() ?: 10100
        versionCode = versionCodeBase * 10 + 1
        versionName = (findProperty("VERSION_NAME") as String?) ?: "1.1.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyPassword = System.getenv("KEY_PASSWORD")
    val hasReleaseKeystore =
        !keystoreFile.isNullOrBlank() &&
            !keystorePassword.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() &&
            !keyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Local / CI without secrets: keep APK installable.
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    "githubImplementation"(project(":update"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.lottie.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
