// Top-level build file where you can add configuration options common to all sub-projects/modules.
val libraryGroup by rootProject.extra { "com.github.pedroSG94" }
val vCode by rootProject.extra { 130 }
val vName by rootProject.extra { "1.3.0" }
val coroutinesVersion by rootProject.extra { "1.9.0" }
val rootEncoderVersion by rootProject.extra { "6eaaa288a0" }

plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply true
}

tasks.register("clean") {
    delete(layout.buildDirectory)
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(File("docs"))
}
