/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    includeBuild("gradle-plugin")
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.0.22"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = !System.getenv("CI").toBoolean()
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
include(
    "alchemist-incarnation-collektive",
    "compiler-embeddable",
    "compiler-plugin-test",
    "dsl",
    "stdlib",
    "test-tooling"
)
