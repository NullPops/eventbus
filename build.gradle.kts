import org.jreleaser.model.Active
import org.jreleaser.model.Signing

plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    `maven-publish`
    id("org.jreleaser") version "1.19.0"
}

group = "io.github.nullpops"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

val kotlin_coroutines = "1.10.2"
val logger = "1.0.1"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlin_coroutines")
    implementation("io.github.nullpops:logger:$logger")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri("${layout.buildDirectory.asFile.get().path }/staging-deploy")
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("eventbus")
                description.set("Simple, fast, in-process pub/sub with priority ordering and cancellation.")
                url.set("https://github.com/nullpops/eventbus")
                licenses {
                    license {
                        name.set("AGPL-3.0-only")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                        distribution.set("repo")
                    }
                    license {
                        name.set("NullPops Commercial License")
                        url.set("https://github.com/NullPops/eventbus/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("zeruth")
                        name.set("Tyler Bochard")
                        email.set("tylerbochard@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/nullpops/eventbus.git")
                    developerConnection.set("scm:git:ssh://github.com/nullpops/eventbus.git")
                    url.set("https://github.com/nullpops/eventbus")
                }
            }
        }
    }
}

jreleaser {
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        mode = Signing.Mode.MEMORY
        providers.environmentVariable("JRELEASER_GPG_PUBLIC_KEY_PATH").orNull?.let {
            publicKey.set(file(it).readText())
        }
        providers.environmentVariable("JRELEASER_GPG_PRIVATE_KEY_PATH").orNull?.let {
            secretKey.set(file(it).readText())
        }

        passphrase.set(providers.environmentVariable("JRELEASER_GPG_PASSPHRASE"))
    }
    deploy {
        maven {
            mavenCentral {
                create("logger") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}