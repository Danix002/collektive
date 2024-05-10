pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    includeBuild("gradle-plugin")
}

plugins {
    id("com.gradle.enterprise") version "3.17.3"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.0.5"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishOnFailure()
    }
}

gitHooks {
    commitMsg { conventionalCommits() }
    preCommit {
        tasks("detektAll", "ktlintCheck")
    }
    createHooks(overwriteExisting = true)
}

rootProject.name = "collektive"

includeBuild("compiler-plugin")
include("alchemist-incarnation-collektive", "compiler-embeddable", "dsl")
