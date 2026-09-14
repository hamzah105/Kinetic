import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localBuildProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "dev.kinetic.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "dev.kinetic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.4-astra-stellar"
    }

    signingConfigs.getByName("debug") {
        // Existing Android debug credentials remain defaults; only a local key path is selected.
        localBuildProperties.getProperty("kinetic.debug.keystore")?.let { storeFile = file(it) }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:kernel"))
    implementation(project(":data:persistence"))
    implementation(project(":data:model"))
    implementation(project(":data:android-capabilities"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
}
