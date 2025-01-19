import it.unibo.collektive.collektivize.CollektivizeTask

plugins {
    alias(libs.plugins.collektivize)
}

apply(plugin = libs.plugins.kotlin.multiplatform.id)

configureKotlinMultiplatform()

collektive {
    collektiveEnabled = true
}

collektivize {
    outputDirectory =
        layout.buildDirectory
            .dir("generated/kotlin/collektive")
            .get()
            .asFile
}

val collektivizeKotlinStdlibTask = tasks.named<CollektivizeTask>("collektivizeKotlinStdlib")

// Avoid verification tasks to complain about being not dependent on the code generation tasks
tasks.withType<SourceTask>().configureEach {
    if (this is VerificationTask) {
        dependsOn(collektivizeKotlinStdlibTask)
    }
}

kotlinMultiplatform {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":dsl"))
            }
            kotlin.srcDirs(collektivizeKotlinStdlibTask)
        }
        commonTest.dependencies {
            implementation(project(":test-tooling"))
            implementation(rootProject.libs.bundles.kotlin.testing.common)
        }
        jvmTest.dependencies {
            implementation(rootProject.libs.kotest.runner.junit5.jvm)
        }
    }
}
