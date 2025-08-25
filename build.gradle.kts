import org.jreleaser.gradle.plugin.tasks.JReleaserDeployTask
import org.jreleaser.model.Active
import org.jreleaser.model.Signing

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("org.jreleaser")
}

group = "io.github.nullpops"
version = "1.0.1"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    with (libs) {
        implementation(kotlinx.coroutines.core.jvm)
        implementation(logger)
        testImplementation(platform(junit.bom))
        testImplementation(junit.jupiter)
    }

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JReleaserDeployTask> {
    dependsOn("publish")
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
        dryrun = false
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
                create("eventbus") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}