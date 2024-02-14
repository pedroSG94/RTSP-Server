val vCode: Int by rootProject.extra
val vName: String by rootProject.extra
val rootEncoderVersion: String by rootProject.extra

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pedro.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pedro.sample"
        minSdk = 16
        targetSdk = 34
        versionCode = vCode
        versionName = vName
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
    implementation("com.github.pedroSG94.RootEncoder:library:$rootEncoderVersion")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
