// Top-level build file where you can add configuration options common to all sub-projects/modules.
val libraryGroup by rootProject.extra { "com.github.pedroSG94" }
val vCode by rootProject.extra { 122 }
val vName by rootProject.extra { "1.2.2" }
val coroutinesVersion by rootProject.extra { "1.7.3" }
val rootEncoderVersion by rootProject.extra { "2.3.6" }

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply true
}

tasks.register("clean") {
    delete(rootProject.buildDir)
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(File("docs"))
}
