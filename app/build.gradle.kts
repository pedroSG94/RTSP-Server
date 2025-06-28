plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
}

android {
    namespace = "com.pedro.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pedro.sample"
        minSdk = 16
        targetSdk = 36
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":rtspserver"))
    implementation(libs.rootEncoder.library)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
}
