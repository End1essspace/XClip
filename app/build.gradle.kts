import java.io.File
import org.gradle.jvm.tasks.Jar
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
version = "1.0.0"

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

tasks.test {
    useJUnitPlatform()
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

// -------------------------
// Packaging config
// -------------------------
val appName = "XClip"
val vendorName = "XCON X-SERIES"
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

/**
 * Creates minimal runtime image (bundled JRE) with JavaFX modules included.
 * This runtime will be embedded into MSI, so target machines do NOT need Java installed.
 */
tasks.register("createRuntimeImage") {
    group = "distribution"
    description = "Creates bundled runtime image using jlink (includes JavaFX)."

    dependsOn("prepareJpackageInput")

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

/**
 * Builds MSI using jpackage + bundled runtime image.
 */
tasks.register("packageMsi") {
    group = "distribution"
    description = "Builds MSI installer with bundled runtime (works without Java installed)."

    dependsOn("createRuntimeImage", "prepareJpackageInput")

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
    }
}
