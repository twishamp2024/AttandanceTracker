plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    useJUnit()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "io.github.twishamp2024"
            artifactId = "attendance-core"
            version = "0.1.0"

            pom {
                name.set("Attendance Core")
                description.set("Pure Kotlin domain engine and mathematics for attendance tracking, calculations, and simulations.")
                url.set("https://github.com/twishamp2024/AttandanceTracker")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("twishamp2024")
                        name.set("Twishamp Mandal")
                        email.set("twishamp2024@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/twishamp2024/AttandanceTracker.git")
                    developerConnection.set("scm:git:ssh://github.com:twishamp2024/AttandanceTracker.git")
                    url.set("https://github.com/twishamp2024/AttandanceTracker")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/twishamp2024/AttandanceTracker")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (findProperty("gpr.user") as? String)
                    ?: System.getenv("GITHUB_USER")
                    ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: (findProperty("gpr.key") as? String)
                    ?: ""
            }
        }
    }
}
