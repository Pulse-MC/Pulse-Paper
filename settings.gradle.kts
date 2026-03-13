pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Pulse project directory is not a properly cloned Git repository.
         
         In order to build Pulse from source you must clone
         the Pulse repository using Git, not download a code
         zip from GitHub.
         
         Built Pulse jars are available for download at
         https://pulsemc.dev/releases or https://pulsemc.dev/devbuilds
         
         See https://github.com/Pulse-MC/Pulse/blob/main/CONTRIBUTING.md
         for further information on building and modifying Pulse.
        ===================================================
    """.trimIndent()
    error(errorText)
}

rootProject.name = "pulse"

include("pulse-api")
include("pulse-server")
