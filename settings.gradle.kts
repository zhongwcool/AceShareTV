import java.io.File

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Fail fast if the JVM running Gradle is a stripped JRE (e.g. Cursor/VS Code embed)
// without jlink — AGP needs a full JDK such as Android Studio's jbr.
val javaHome = File(System.getProperty("java.home"))
val jlink = listOf("jlink.exe", "jlink")
    .map { File(javaHome, "bin/$it") }
    .firstOrNull { it.isFile }
if (jlink == null) {
    throw GradleException(
        "Gradle JVM has no jlink: ${javaHome.absolutePath}\n" +
            "Use Android Studio JBR, for example:\n" +
            "  org.gradle.java.home=<Android Studio>/jbr\n" +
            "Do not use Cursor/VS Code embedded JRE."
    )
}

rootProject.name = "AceShare"
include(":app")
