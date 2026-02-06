import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Pulse project directory is not a properly cloned Git repository.
         
         In order to build Pulse from source you must clone
         the Pulse repository using Git, not download a code
         zip from GitHub.
         
         Built Pulse jars are available for download at
         https://pulsemc.dev/
         
         See https://github.com/Pulse-MC/Pulse-Paper/blob/HEAD/CONTRIBUTING.md
         for further information on building and modifying Pulse.
        ===================================================
    """.trimIndent()
    error(errorText)
}

rootProject.name = "pulse"
for (name in listOf("pulse-api", "pulse-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}

optionalInclude("test-plugin")

fun optionalInclude(name: String, op: (ProjectDescriptor.() -> Unit)? = null) {
    val settingsFile = file("$name.settings.gradle.kts")
    if (settingsFile.exists()) {
        apply(from = settingsFile)
        findProject(":$name")?.let { op?.invoke(it) }
    } else {
        settingsFile.writeText(
            """
            // Uncomment to enable the '$name' project
            // include(":$name")

            """.trimIndent()
        )
    }
}
