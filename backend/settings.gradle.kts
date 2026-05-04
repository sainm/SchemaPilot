pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "schemapilot-backend"

include(
    "app",
    "common",
    "project",
    "input",
    "parser",
    "model",
    "dependency",
    "rule",
    "convert",
    "risk",
    "ai",
    "report",
    "review",
    "export",
)
