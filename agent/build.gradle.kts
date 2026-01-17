plugins {
    id("java-library")
}

group = rootProject.group
version = rootProject.version

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.janrupf.gradle.hytale.dev.agent.HytaleDevAgent",
        )
    }
}