plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
}

android {
    namespace = "com.pedro.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pedro.sample"
        minSdk = 16
        targetSdk = 34
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":rtspserver"))
    implementation(libs.rootencoder.library)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
}
