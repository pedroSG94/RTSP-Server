// Top-level build file where you can add configuration options common to all sub-projects/modules.
allprojects {
    group = "com.github.pedroSG94"
    version = "1.4.0"

    plugins.withType<PublishingPlugin> {
        configure<PublishingExtension> {
            publications.withType<MavenPublication>().all {
                pom {
                    name = "RTSP-Server"
                    description = "Plugin of RootEncoder to stream directly to RTSP player"
                    url = "https://github.com/pedroSG94/RTSP-Server"
                    licenses {
                        license {
                            name = "Apache-2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                            distribution = "manual"
                        }
                    }
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin) apply false
    alias(libs.plugins.jetbrains.dokka) apply true
}

dependencies {
    dokka(project(":rtspserver"))
}

tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml") {
    outputDirectory.set(layout.projectDirectory.dir("docs"))
}
