import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val protectedProductionApplicationId = "dev.kinetic.app"
val destructiveProductionPackagePatterns = listOf(
    Regex(
        """(?i)\badb(?:\.exe)?\b[^\r\n]*(?:\buninstall\b|\bpm\s+(?:uninstall|clear)\b)[^\r\n]*\bdev\.kinetic\.app\b""",
    ),
    Regex("""(?i)\bpm\s+(?:uninstall|clear)\b[^\r\n]*\bdev\.kinetic\.app\b"""),
)
val containsDestructiveProductionPackageCommand: (String) -> Boolean = { text ->
    destructiveProductionPackagePatterns.any { pattern -> pattern.containsMatchIn(text) }
}

val testDeviceTestSafetyGuard by tasks.registering {
    group = "verification"
    description = "Self-tests the production-package device-test safety matcher."

    doLast {
        val destructiveExamples = listOf(
            "adb " + "uninstall " + protectedProductionApplicationId,
            "adb shell pm " + "uninstall --user 0 " + protectedProductionApplicationId,
            "pm " + "clear " + protectedProductionApplicationId,
        )
        val allowedExamples = listOf(
            "adb shell pm path $protectedProductionApplicationId",
            "adb install -r app-debug.apk",
            "adb shell am force-stop $protectedProductionApplicationId",
        )

        check(destructiveExamples.all(containsDestructiveProductionPackageCommand)) {
            "The device-test safety matcher failed to reject a destructive production-package command."
        }
        check(allowedExamples.none(containsDestructiveProductionPackageCommand)) {
            "The device-test safety matcher rejected a permitted non-destructive command."
        }
    }
}

val verifyDeviceTestSafety by tasks.registering {
    group = "verification"
    description = "Prevents automated device tests from deleting the owner's installed Kinetic package."
    dependsOn(testDeviceTestSafetyGuard)

    doLast {
        val productionAppAndroidTestRoot = file("app/src/androidTest")
        val productionAppInstrumentationSources =
            if (productionAppAndroidTestRoot.exists()) {
                productionAppAndroidTestRoot.walkTopDown()
                    .filter { file -> file.isFile && file.extension.lowercase() in setOf("kt", "java") }
                    .toList()
            } else {
                emptyList()
            }
        if (productionAppInstrumentationSources.isNotEmpty()) {
            throw GradleException(
                "Production-app instrumentation is disabled because AGP/UTP uninstalls its target APK after the " +
                    "test. Move device fixtures to a distinct test-only application package. Found: " +
                    productionAppInstrumentationSources.joinToString { it.relativeTo(projectDir).path },
            )
        }

        val scannedExtensions = setOf("gradle", "kts", "kt", "java", "ps1", "bat", "cmd", "sh")
        val excludedDirectories = setOf("build", ".gradle", ".git", "docs")
        val destructiveCommandFiles = projectDir.walkTopDown()
            .onEnter { directory -> directory == projectDir || directory.name !in excludedDirectories }
            .filter { file -> file.isFile && file.extension.lowercase() in scannedExtensions }
            .filter { file -> containsDestructiveProductionPackageCommand(file.readText()) }
            .map { file -> file.relativeTo(projectDir).path }
            .toList()
        if (destructiveCommandFiles.isNotEmpty()) {
            throw GradleException(
                "Destructive commands targeting $protectedProductionApplicationId are prohibited: " +
                    destructiveCommandFiles.joinToString(),
            )
        }
    }
}

gradle.projectsEvaluated {
    subprojects.forEach { subproject ->
        subproject.tasks.matching { task ->
            task.name.matches(Regex("(?i)(connected|device).+AndroidTest"))
        }.configureEach {
            dependsOn(rootProject.tasks.named("verifyDeviceTestSafety"))
            if (subproject.path == ":app") {
                doFirst {
                    throw GradleException(
                        "App-target device tests are blocked before UTP execution because UTP teardown would " +
                            "uninstall $protectedProductionApplicationId. Use a distinct test-only app fixture.",
                    )
                }
            }
        }
    }
}
