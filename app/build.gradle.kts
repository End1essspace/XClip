

import java.io.File
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

fun toolchainJavaHome(): File {
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }.get()
    return launcher.metadata.installationPath.asFile
}

group = "io.xseries"
version = "1.4.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.graphics")
}

application {
    mainClass.set("io.xseries.xclip.XClipApp")
}

fun Test.configureDeterministicTestRuntime() {
    useJUnitPlatform()

    // Native desktop and SQLite integration tests intentionally share process state.
    maxParallelForks = 1
    systemProperty("file.encoding", "UTF-8")

    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }

    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = false
    }
}

tasks.withType<Test>().configureEach {
    configureDeterministicTestRuntime()
}

val repeatRegressionTest = tasks.register<Test>("repeatRegressionTest") {
    group = "verification"
    description = "Repeats the complete test suite in a deterministic alternate order."

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    mustRunAfter(tasks.named("test"))

    systemProperty(
        "junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer\$Random"
    )
    systemProperty(
        "junit.jupiter.testmethod.order.default",
        "org.junit.jupiter.api.MethodOrderer\$Random"
    )
    systemProperty("junit.jupiter.execution.order.random.seed", "1302026")
}

tasks.named<JavaExec>("run") {
    val argsLine = (project.findProperty("appJvmArgs") as String?)
        ?: "-Xms64m -Xmx512m -Xss512k -Dfile.encoding=UTF-8"
    jvmArgs(argsLine.split(" ").filter { it.isNotBlank() })
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "XClip",
            "Implementation-Version" to project.version.toString()
        )
    }
}

val requiredPackagedUiResources = listOf(
    "ui/theme.css",
    "ui/controls.css",
    "ui/popup.css",
    "ui/dialogs.css",
    "ui/ui-contract-v1.4.0.properties",
    "META-INF/THIRD-PARTY-NOTICES.txt",
    "META-INF/licenses/LUCIDE-ISC.txt"
)

fun verifyUiResourcesInJar(jarFile: File) {
    if (!jarFile.isFile) {
        throw GradleException("Application JAR not found: ${jarFile.absolutePath}")
    }

    val iconSourceDir = file("src/main/resources/icons/ui")
    val iconFiles = iconSourceDir.listFiles { candidate ->
        candidate.isFile && candidate.extension.equals("svg", ignoreCase = true)
    }?.sortedBy { it.name } ?: emptyList()

    val contractFile = file("src/main/resources/ui/ui-contract-v1.4.0.properties")
    val currentContract = Properties().apply {
        contractFile.inputStream().use { stream -> load(stream) }
    }
    val expectedIconCount = currentContract.getProperty("popup.iconCount")?.toIntOrNull()
        ?: throw GradleException(
            "Missing or invalid popup.iconCount in ${contractFile.path}"
        )
    val contractProductVersion = currentContract.getProperty("product.version")
    if (contractProductVersion != project.version.toString()) {
        throw GradleException(
            "Current UI contract version $contractProductVersion does not match project version ${project.version}"
        )
    }

    if (iconFiles.size != expectedIconCount) {
        throw GradleException(
            "UI contract expects $expectedIconCount Lucide SVG resources, " +
                "found ${iconFiles.size} in ${iconSourceDir.path}"
        )
    }

    ZipFile(jarFile).use { zip ->
        val expectedEntries = requiredPackagedUiResources +
            iconFiles.map { "icons/ui/${it.name}" }

        for (entryName in expectedEntries) {
            val entry = zip.getEntry(entryName)
                ?: throw GradleException("Missing packaged resource: $entryName")
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw GradleException("Empty packaged resource: $entryName")
            }
        }

        for (iconFile in iconFiles) {
            val entryName = "icons/ui/${iconFile.name}"
            val entry = zip.getEntry(entryName)
                ?: throw GradleException("Missing packaged SVG: $entryName")
            val source = zip.getInputStream(entry)
                .use { it.readBytes().toString(Charsets.UTF_8) }
                .lowercase(Locale.ROOT)

            if (!source.contains("<svg")) {
                throw GradleException("Invalid packaged SVG root: $entryName")
            }
            if (source.contains("<image") || source.contains("<script")
                || source.contains("<foreignobject") || source.contains("<!doctype")) {
                throw GradleException("Unsafe or raster SVG content: $entryName")
            }

            val renderable = listOf(
                "<path", "<line", "<circle", "<ellipse",
                "<rect", "<polyline", "<polygon"
            ).any(source::contains)
            if (!renderable) {
                throw GradleException("SVG has no supported vector shapes: $entryName")
            }
        }
    }
}

val verifyPackagedUiResources = tasks.register("verifyPackagedUiResources") {
    group = "verification"
    description = "Verifies CSS, Lucide SVG, and license resources inside the application JAR."
    dependsOn(tasks.named("jar"))

    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        verifyUiResourcesInJar(jarFile)
        println("PACKAGED_UI_RESOURCES_OK: ${jarFile.absolutePath}")
    }
}

val verifyR11RegressionAssets = tasks.register("verifyR11RegressionAssets") {
    group = "verification"
    description = "Verifies the frozen R11 UI contract, regression matrix, screenshots, and evidence scripts."

    doLast {
        val requiredFiles = listOf(
            rootProject.file("docs/UI_CONTRACT_v1.3.0.md"),
            rootProject.file("docs/R11_REGRESSION_UI_FREEZE.md"),
            rootProject.file("docs/R11_REGRESSION_MATRIX.csv"),
            rootProject.file("docs/R11_SCREENSHOT_SET.csv"),
            rootProject.file("scripts/start_r11_manual_validation.ps1"),
            rootProject.file("scripts/run_r11_automated_gate.ps1"),
            rootProject.file("scripts/validate_r11_evidence.ps1"),
            file("src/main/resources/ui/ui-contract-v1.3.0.properties")
        )

        for (required in requiredFiles) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException("Missing or empty R11 asset: ${required.absolutePath}")
            }
        }

        val contractProperties = Properties().apply {
            file("src/main/resources/ui/ui-contract-v1.3.0.properties")
                .inputStream()
                .use { stream -> load(stream) }
        }
        val contractVersion = contractProperties.getProperty("product.version")
        if (contractVersion != "1.3.0") {
            throw GradleException(
                "Historical R11 UI contract must remain at product version 1.3.0, found $contractVersion"
            )
        }

        val matrix = rootProject.file("docs/R11_REGRESSION_MATRIX.csv")
            .readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (matrix.size != 39) {
            throw GradleException(
                "R11 regression matrix must contain one header plus 38 cases, found ${matrix.size} lines"
            )
        }

        val ids = matrix.drop(1).map { line ->
            line.substringBefore(',').trim().removeSurrounding("\"")
        }
        val expectedIds = (1..38).map { index -> "R11-%03d".format(index) }
        if (ids != expectedIds) {
            throw GradleException("R11 regression IDs are missing, duplicated, or out of order: $ids")
        }

        val screenshots = rootProject.file("docs/R11_SCREENSHOT_SET.csv")
            .readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (screenshots.size != 10) {
            throw GradleException(
                "R11 screenshot set must contain one header plus 9 screenshots, found ${screenshots.size} lines"
            )
        }

        println("R11_REGRESSION_ASSETS_OK: cases=38 screenshots=9")
    }
}

val r11AutomatedGate = tasks.register("r11AutomatedGate") {
    group = "verification"
    description = "Runs the frozen R11 automated UI and regression gate."
    dependsOn(tasks.named("check"), verifyR11RegressionAssets)

    doLast {
        println("R11_AUTOMATED_GRADLE_GATE_OK")
    }
}

tasks.named("check") {
    dependsOn(verifyPackagedUiResources, verifyR11RegressionAssets)
}

val c8BaselineGate = tasks.register("c8BaselineGate") {
    group = "verification"
    description = "Runs the complete baseline gate plus a second randomized-order test pass."
    dependsOn(tasks.named("check"), repeatRegressionTest)

    doLast {
        println("C8_BASELINE_GATE_OK: standard and alternate-order suites passed")
    }
}

val verifyM6SettingsRegressionAssets = tasks.register(
    "verifyM6SettingsRegressionAssets"
) {
    group = "verification"
    description = "Verifies the frozen M6 Settings contract and 24-case regression matrix."

    doLast {
        val validation = rootProject.file("docs/M6_SETTINGS_VALIDATION.md")
        val matrix = rootProject.file("docs/M6_SETTINGS_REGRESSION_MATRIX.csv")
        val contractFile = file(
            "src/main/resources/ui/ui-contract-v1.4.0.properties"
        )

        for (required in listOf(validation, matrix, contractFile)) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException(
                    "Missing or empty M6 Settings asset: ${required.absolutePath}"
                )
            }
        }

        val matrixLines = matrix.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (matrixLines.size != 25) {
            throw GradleException(
                "M6 Settings matrix must contain one header plus 24 cases, " +
                    "found ${matrixLines.size} lines"
            )
        }

        val ids = matrixLines.drop(1).map { line ->
            line.substringBefore(',').trim().removeSurrounding("\"")
        }
        val expectedIds = (1..24).map { index ->
            "M6-%03d".format(index)
        }
        if (ids != expectedIds) {
            throw GradleException(
                "M6 Settings regression IDs are missing, duplicated, or out of order: $ids"
            )
        }

        val contract = Properties().apply {
            contractFile.inputStream().use { stream -> load(stream) }
        }
        val contractRevision = contract.getProperty("contract.version")
            ?.toIntOrNull()
            ?: throw GradleException("Invalid UI contract revision")
        if (contractRevision < 15) {
            throw GradleException(
                "M6.5 requires UI contract revision 15 or newer"
            )
        }
        if (contract.getProperty("settings.regressionGate")
                != "M6_SETTINGS_GATE") {
            throw GradleException(
                "Missing M6 Settings regression gate contract"
            )
        }

        println("M6_SETTINGS_ASSETS_OK: cases=24 contract=$contractRevision")
    }
}

tasks.named("check") {
    dependsOn(verifyM6SettingsRegressionAssets)
}

val m6SettingsGate = tasks.register("m6SettingsGate") {
    group = "verification"
    description = "Runs the complete C8 baseline and frozen M6 Settings regression gate."
    dependsOn(c8BaselineGate, verifyM6SettingsRegressionAssets)

    doLast {
        println(
            "M6_SETTINGS_GATE_OK: responsive, accessibility, " +
                "contract, and full regression checks passed"
        )
    }
}


val verifyM7DatabaseRegressionAssets = tasks.register(
    "verifyM7DatabaseRegressionAssets"
) {
    group = "verification"
    description = "Verifies the M7.2 database-maintenance contract and 20-case matrix."

    doLast {
        val validation = rootProject.file("docs/M7_DATABASE_MAINTENANCE.md")
        val matrix = rootProject.file("docs/M7_DATABASE_REGRESSION_MATRIX.csv")
        val contractFile = file(
            "src/main/resources/ui/ui-contract-v1.4.0.properties"
        )

        for (required in listOf(validation, matrix, contractFile)) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException(
                    "Missing or empty M7.2 database asset: ${required.absolutePath}"
                )
            }
        }

        val matrixLines = matrix.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (matrixLines.size != 21) {
            throw GradleException(
                "M7.2 database matrix must contain one header plus 20 cases, " +
                    "found ${matrixLines.size} lines"
            )
        }

        val ids = matrixLines.drop(1).map { line ->
            line.substringBefore(',').trim().removeSurrounding("\"")
        }
        val expectedIds = (1..20).map { index ->
            "M7-%03d".format(index)
        }
        if (ids != expectedIds) {
            throw GradleException(
                "M7.2 database regression IDs are missing, duplicated, or out of order: $ids"
            )
        }

        val contract = Properties().apply {
            contractFile.inputStream().use { stream -> load(stream) }
        }
        val contractRevision = contract.getProperty("contract.version")
            ?.toIntOrNull()
            ?: throw GradleException("Invalid UI contract revision")
        if (contractRevision < 16) {
            throw GradleException("M7.2 requires UI contract revision 16 or newer")
        }
        if (contract.getProperty("database.regressionGate")
                != "M7_DATABASE_GATE") {
            throw GradleException(
                "Missing M7.2 database regression gate contract"
            )
        }
        if (contract.getProperty("settings.backupRestore")
                != "VERSIONED_ARCHIVE_VALIDATED_ATOMIC_REPLACE") {
            throw GradleException(
                "Missing M7.2 backup/restore contract"
            )
        }

        println("M7_DATABASE_ASSETS_OK: cases=20 contract=$contractRevision")
    }
}

tasks.named("check") {
    dependsOn(verifyM7DatabaseRegressionAssets)
}

val m7DatabaseGate = tasks.register("m7DatabaseGate") {
    group = "verification"
    description = "Runs the M6 baseline plus M7.2 database maintenance regression gate."
    dependsOn(m6SettingsGate, verifyM7DatabaseRegressionAssets)

    doLast {
        println(
            "M7_DATABASE_GATE_OK: migrations, integrity, checkpoint, " +
                "vacuum, backup, restore, and full regression checks passed"
        )
    }
}


val verifyM7LargeDataAssets = tasks.register(
    "verifyM7LargeDataAssets"
) {
    group = "verification"
    description = "Verifies the frozen M7.3 large-data contract and 18-case matrix."

    doLast {
        val validation = rootProject.file("docs/M7_LARGE_DATA_VALIDATION.md")
        val matrix = rootProject.file("docs/M7_LARGE_DATA_MATRIX.csv")
        val runner = rootProject.file("scripts/run_m7_large_data_validation.ps1")
        val contractFile = file(
            "src/main/resources/ui/ui-contract-v1.4.0.properties"
        )

        for (required in listOf(validation, matrix, runner, contractFile)) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException(
                    "Missing or empty M7.3 large-data asset: ${required.absolutePath}"
                )
            }
        }

        val matrixLines = matrix.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (matrixLines.size != 19) {
            throw GradleException(
                "M7.3 large-data matrix must contain one header plus 18 cases, " +
                    "found ${matrixLines.size} lines"
            )
        }

        val ids = matrixLines.drop(1).map { line ->
            line.substringBefore(',').trim().removeSurrounding("\"")
        }
        val expectedIds = (1..18).map { index ->
            "M7L-%03d".format(index)
        }
        if (ids != expectedIds) {
            throw GradleException(
                "M7.3 large-data IDs are missing, duplicated, or out of order: $ids"
            )
        }

        val contract = Properties().apply {
            contractFile.inputStream().use { stream -> load(stream) }
        }
        val contractRevision = contract.getProperty("contract.version")
            ?.toIntOrNull()
            ?: throw GradleException("Invalid UI contract revision")
        if (contractRevision < 17) {
            throw GradleException("M7.3 requires UI contract revision 17 or newer")
        }
        if (contract.getProperty("performance.datasets")
                != "1000|10000|50000") {
            throw GradleException("Missing M7.3 dataset contract")
        }
        if (contract.getProperty("performance.regressionGate")
                != "M7_LARGE_DATA_GATE") {
            throw GradleException("Missing M7.3 large-data gate contract")
        }
        if (contract.getProperty("performance.evidence")
                != "SUMMARY_JSON|METRICS_CSV|ENVIRONMENT_PROPERTIES") {
            throw GradleException("Missing M7.3 evidence contract")
        }

        println("M7_LARGE_DATA_ASSETS_OK: cases=18 contract=$contractRevision")
    }
}

tasks.named("check") {
    dependsOn(verifyM7LargeDataAssets)
}

val m7LargeDataReportDirectory = layout.buildDirectory.dir(
    "reports/m7-large-data"
)

val m7LargeDataValidation = tasks.register<JavaExec>(
    "m7LargeDataValidation"
) {
    group = "verification"
    description = "Runs the isolated 1k/10k/50k M7.3 latency, memory, and JavaFX-stall matrix."

    dependsOn(tasks.named("testClasses"), verifyM7LargeDataAssets)
    mustRunAfter(m7DatabaseGate)

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set(
        "io.xseries.xclip.validation.LargeDataValidationMain"
    )
    args(m7LargeDataReportDirectory.get().asFile.absolutePath)

    jvmArgs(
        "-Xms128m",
        "-Xmx768m",
        "-Xss512k",
        "-Dfile.encoding=UTF-8"
    )

    doFirst {
        if (!OperatingSystem.current().isWindows) {
            throw GradleException(
                "M7.3 JavaFX responsiveness validation must run on Windows."
            )
        }
    }

    doLast {
        val report = m7LargeDataReportDirectory.get().asFile
        val summary = File(report, "summary.json")
        val metrics = File(report, "metrics.csv")
        val environment = File(report, "environment.properties")
        val pass = File(report, "PASS.txt")

        for (required in listOf(summary, metrics, environment, pass)) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException(
                    "Missing M7.3 runtime evidence: ${required.absolutePath}"
                )
            }
        }
        println("M7_LARGE_DATA_EVIDENCE_OK: ${report.absolutePath}")
    }
}

val m7LargeDataGate = tasks.register("m7LargeDataGate") {
    group = "verification"
    description = "Runs the complete M7.2 baseline and the explicit M7.3 large-data release gate."

    dependsOn(
        m7DatabaseGate,
        verifyM7LargeDataAssets,
        m7LargeDataValidation
    )

    doLast {
        println(
            "M7_LARGE_DATA_GATE_OK: 1k/10k/50k datasets, large clip, " +
                "pinned/tags/duplicates, cleanup, churn, heap, DB size, " +
                "and JavaFX responsiveness passed"
        )
    }
}

val verifyM8WindowsLifecycleAssets = tasks.register(
    "verifyM8WindowsLifecycleAssets"
) {
    group = "verification"
    description = "Verifies the frozen M8 Windows lifecycle contract and 18-case packaged matrix."

    doLast {
        val validation = rootProject.file("docs/M8_WINDOWS_LIFECYCLE.md")
        val matrix = rootProject.file("docs/M8_WINDOWS_LIFECYCLE_MATRIX.csv")
        val starter = rootProject.file("scripts/start_m8_windows_lifecycle_validation.ps1")
        val validator = rootProject.file("scripts/validate_m8_windows_lifecycle_evidence.ps1")
        val contractFile = file(
            "src/main/resources/ui/ui-contract-v1.4.0.properties"
        )

        for (required in listOf(validation, matrix, starter, validator, contractFile)) {
            if (!required.isFile || required.length() <= 0L) {
                throw GradleException(
                    "Missing or empty M8 lifecycle asset: ${required.absolutePath}"
                )
            }
        }

        val matrixLines = matrix.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        if (matrixLines.size != 19) {
            throw GradleException(
                "M8 lifecycle matrix must contain one header plus 18 cases, " +
                    "found ${matrixLines.size} lines"
            )
        }
        val ids = matrixLines.drop(1).map { line ->
            line.substringBefore(',').trim().removeSurrounding("\"")
        }
        val expectedIds = (1..18).map { index -> "M8-%03d".format(index) }
        if (ids != expectedIds) {
            throw GradleException(
                "M8 lifecycle IDs are missing, duplicated, or out of order: $ids"
            )
        }

        val contract = Properties().apply {
            contractFile.inputStream().use { stream -> load(stream) }
        }
        val revision = contract.getProperty("contract.version")
            ?.toIntOrNull()
            ?: throw GradleException("Invalid UI contract revision")
        if (revision < 18) {
            throw GradleException("M8 requires UI contract revision 18 or newer")
        }
        if (contract.getProperty("lifecycle.regressionGate")
                != "M8_WINDOWS_LIFECYCLE_GATE") {
            throw GradleException("Missing M8 lifecycle regression gate contract")
        }
        if (contract.getProperty("lifecycle.singleInstance")
                != "LOOPBACK_ACKNOWLEDGED") {
            throw GradleException("Missing acknowledged single-instance contract")
        }
        if (contract.getProperty("lifecycle.exitCleanupTimeoutMillis")
                != "3000") {
            throw GradleException("Missing bounded exit-cleanup contract")
        }

        val buildScript = file("build.gradle.kts").readText(Charsets.UTF_8)
        val upgradeAssignment = Regex(
            """val\s+upgradeUuid\s*=\s*"1322455b-12c4-4363-b896-12cd27ac3e3d"""
        )
        if (!upgradeAssignment.containsMatchIn(buildScript)
                || !buildScript.contains(
                    "\"--win-upgrade-uuid\", upgradeUuid"
                )
                || !buildScript.contains("\"--win-per-user-install\"")) {
            throw GradleException(
                "MSI upgrade UUID or per-user install contract changed"
            )
        }

        println("M8_WINDOWS_LIFECYCLE_ASSETS_OK: cases=18 contract=$revision")
    }
}

tasks.named("check") {
    dependsOn(verifyM8WindowsLifecycleAssets)
}

val m8WindowsLifecycleGate = tasks.register("m8WindowsLifecycleGate") {
    group = "verification"
    description = "Runs all prior gates plus M8 Windows runtime lifecycle hardening checks."
    dependsOn(m7LargeDataGate, verifyM8WindowsLifecycleAssets)

    doLast {
        println(
            "M8_WINDOWS_LIFECYCLE_GATE_OK: single instance, watcher recovery, " +
                "tray/hotkey self-healing, bounded shutdown, autostart repair, " +
                "display/session policy, packaging contract, and prior gates passed"
        )
    }
}

val validateM8WindowsLifecycleEvidence = tasks.register(
    "validateM8WindowsLifecycleEvidence"
) {
    group = "verification"
    description = "Validates a completed 18-case packaged M8 evidence directory (-Pm8EvidenceDir=...)."

    doLast {
        if (!OperatingSystem.current().isWindows) {
            throw GradleException(
                "M8 packaged lifecycle evidence validation is Windows-only."
            )
        }

        val rawDirectory = providers.gradleProperty("m8EvidenceDir").orNull
            ?: throw GradleException(
                "Provide -Pm8EvidenceDir=<completed M8 evidence directory>"
            )
        val directory = file(rawDirectory).absoluteFile
        val validator = rootProject.file(
            "scripts/validate_m8_windows_lifecycle_evidence.ps1"
        )

        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            validator.absolutePath,
            "-EvidenceDirectory",
            directory.absolutePath
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (output.isNotBlank()) println(output.trim())
        if (exitCode != 0) {
            throw GradleException(
                "M8 evidence validator failed with exit code $exitCode"
            )
        }

        val pass = File(directory, "PASS.txt")
        if (!pass.isFile || pass.length() <= 0L) {
            throw GradleException(
                "M8 validator completed without PASS.txt: ${pass.absolutePath}"
            )
        }

        println("M8_WINDOWS_LIFECYCLE_EVIDENCE_OK: ${directory.absolutePath}")
    }
}

// -------------------------
// Packaging config
// -------------------------
val appName = "XClip"
val vendorName = "End1essspace X-SERIES"
val mainClassName = "io.xseries.xclip.XClipApp"
val iconIco = file("src/main/resources/icons/app.ico")

// Generate ONCE in PowerShell and never change:
// [guid]::NewGuid().ToString()
val upgradeUuid = "1322455b-12c4-4363-b896-12cd27ac3e3d"

// Where we stage jars for jpackage
val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val runtimeImageDir = layout.buildDirectory.dir("runtime/${project.version}")

/**
 * Copies app jar + runtimeClasspath deps into build/jpackage/input
 */
tasks.register<Copy>("prepareJpackageInput") {
    dependsOn(tasks.named("jar"))
    into(jpackageInputDir)

    // app jar
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })

    // deps
    from(configurations.runtimeClasspath)
}

val verifyJpackageInput = tasks.register("verifyJpackageInput") {
    group = "verification"
    description = "Verifies the staged application JAR used by jpackage."
    dependsOn("prepareJpackageInput", "verifyPackagedUiResources")

    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val stagedJar = File(jpackageInputDir.get().asFile, jarFile.name)
        verifyUiResourcesInJar(stagedJar)
        println("JPACKAGE_INPUT_UI_RESOURCES_OK: ${stagedJar.absolutePath}")
    }
}

/**
 * Creates minimal runtime image (bundled JRE) with JavaFX modules included.
 * This runtime will be embedded into MSI, so target machines do NOT need Java installed.
 */
tasks.register("createRuntimeImage") {
    group = "distribution"
    description = "Creates bundled runtime image using jlink (includes JavaFX)."

    dependsOn("prepareJpackageInput", "verifyPackagedUiResources")

    doLast {
        if (!OperatingSystem.current().isWindows) {
            throw GradleException("createRuntimeImage is Windows-only in this setup.")
        }

        val javaHome = toolchainJavaHome()
        val jlinkExe = File(javaHome, "bin/jlink.exe")
        if (!jlinkExe.exists()) throw GradleException("jlink.exe not found: ${jlinkExe.absolutePath}")

        val jmodsDir = File(javaHome, "jmods")
        if (!jmodsDir.exists()) throw GradleException("JDK jmods folder not found: ${jmodsDir.absolutePath}")

        val rtCp = configurations.runtimeClasspath.get().files

        val javafxJars = rtCp.filter { f ->
            val n = f.name.lowercase()
            n.startsWith("javafx-") && n.endsWith(".jar")
        }
        if (javafxJars.isEmpty()) {
            throw GradleException("No JavaFX jars found on runtimeClasspath. Check javafx { modules = ... }")
        }

        val outDir = runtimeImageDir.get().asFile

        // jlink требует, чтобы output dir НЕ существовал
        if (outDir.exists()) {
            println("Runtime image already exists, skipping: ${outDir.absolutePath}")
            return@doLast
        }
        outDir.parentFile.mkdirs()

        println("Runtime dir: ${outDir.absolutePath} exists=${outDir.exists()}")
        println("JavaFX jars: ${javafxJars.map { it.name }}")

        // Module path for jlink:
        // - JDK jmods
        // - JavaFX module jars
        val modulePath = (listOf(jmodsDir.absolutePath) + javafxJars.map { it.absolutePath })
            .joinToString(File.pathSeparator)

        // Modules we need in runtime (safe superset)
        val modules = listOf(
            "java.base",
            "java.desktop",
            "java.logging",
            "java.sql",
            "java.naming",
            "java.xml",
            "javafx.base",
            "javafx.graphics",
            "javafx.controls"
        ).joinToString(",")

        val cmd = listOf(
            jlinkExe.absolutePath,
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=2",
            "--module-path", modulePath,
            "--add-modules", modules,
            "--output", outDir.absolutePath
        )

        println(cmd.joinToString(" "))

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()

        p.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { println(it) }
        }

        val code = p.waitFor()
        if (code != 0) throw GradleException("jlink failed with exit code $code")
    }
}

val verifyRuntimeImage = tasks.register("verifyRuntimeImage") {
    group = "verification"
    description = "Verifies the jlink runtime required by the packaged XClip application."
    dependsOn("createRuntimeImage")

    doLast {
        if (!OperatingSystem.current().isWindows) {
            throw GradleException("verifyRuntimeImage is Windows-only.")
        }

        val runtimeDir = runtimeImageDir.get().asFile
        val javaExe = File(runtimeDir, "bin/java.exe")
        val javawExe = File(runtimeDir, "bin/javaw.exe")
        if (!javaExe.isFile || !javawExe.isFile) {
            throw GradleException("Incomplete runtime image: ${runtimeDir.absolutePath}")
        }

        val process = ProcessBuilder(javaExe.absolutePath, "--list-modules")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0) {
            throw GradleException("Runtime module verification failed with exit code $code\n$output")
        }

        val modules = output.lineSequence()
            .map { it.substringBefore('@').trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val requiredModules = setOf(
            "java.base",
            "java.desktop",
            "java.sql",
            "java.xml",
            "javafx.base",
            "javafx.graphics",
            "javafx.controls"
        )
        val missing = requiredModules - modules
        if (missing.isNotEmpty()) {
            throw GradleException("Runtime image is missing modules: ${missing.sorted()}")
        }

        println("JLINK_RUNTIME_OK: ${runtimeDir.absolutePath}")
    }
}

/**
 * Builds MSI using jpackage + bundled runtime image.
 */
tasks.register("packageMsi") {
    group = "distribution"
    description = "Builds MSI installer with bundled runtime (works without Java installed)."

    dependsOn(
        "createRuntimeImage",
        "prepareJpackageInput",
        "verifyJpackageInput",
        "verifyRuntimeImage"
    )

    doLast {
        if (!OperatingSystem.current().isWindows) {
            throw GradleException("packageMsi is Windows-only.")
        }

        if (!iconIco.exists()) {
            throw GradleException("Icon not found: ${iconIco.path}")
        }

        val javaHome = toolchainJavaHome()
        val jpackageExe = File(javaHome, "bin/jpackage.exe")
        if (!jpackageExe.exists()) throw GradleException("jpackage.exe not found: ${jpackageExe.absolutePath}")

        val outDir = layout.buildDirectory.dir("installer").get().asFile
        outDir.mkdirs()

        val inputDir = jpackageInputDir.get().asFile
        val runtimeDir = runtimeImageDir.get().asFile

        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile

        val cmd = listOf(
            jpackageExe.absolutePath,
            "--type", "msi",
            "--name", appName,
            "--vendor", vendorName,
            "--app-version", project.version.toString(),
            "--input", inputDir.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", mainClassName,
            "--runtime-image", runtimeDir.absolutePath,
            "--icon", iconIco.absolutePath,

            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-per-user-install",
            "--win-upgrade-uuid", upgradeUuid,

            "--java-options", "-Xms64m",
            "--java-options", "-Xmx512m",
            "--java-options", "-Xss512k",
            "--java-options", "-Dfile.encoding=UTF-8",

            "--dest", outDir.absolutePath
        )

        println(cmd.joinToString(" "))

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env["PATH"] = "D:\\PROG_INS\\wix314-binaries;" + (env["PATH"] ?: "")
        val p = pb.start()

        p.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { println(it) }
        }

        val code = p.waitFor()
        if (code != 0) throw GradleException("jpackage failed with exit code $code")

        val installers = outDir.listFiles { candidate ->
            candidate.isFile && candidate.extension.equals("msi", ignoreCase = true)
        }?.toList() ?: emptyList()
        if (installers.isEmpty()) {
            throw GradleException("jpackage completed without producing an MSI in ${outDir.absolutePath}")
        }

        println("MSI_PACKAGE_OK: ${installers.maxBy { it.lastModified() }.absolutePath}")
    }
}
