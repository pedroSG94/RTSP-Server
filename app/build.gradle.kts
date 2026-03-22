plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
}

android {
    namespace = "com.pedro.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pedro.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = project.version.toString().replace(".", "").toInt()
        versionName = project.version.toString()
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.rootEncoder.extra.sources)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
}
