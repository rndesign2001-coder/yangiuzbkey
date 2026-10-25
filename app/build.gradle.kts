import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: read from keystore.properties (local, not committed) or from
// environment variables (GitHub Actions secrets). Without either, release builds
// stay unsigned and the debug build still works.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "uz.yangiuzbkey.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.yangiuzbkey.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "YUK_KEYSTORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "YUK_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "YUK_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "YUK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
