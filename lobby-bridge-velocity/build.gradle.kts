import java.util.zip.ZipFile

// Velocity proxy plugin that feeds the lobby with network information.
// Produces build/libs/lobby-bridge.jar
plugins {
    alias(libs.plugins.shadow)
}

// Java 21 bytecode so the plugin loads on Velocity 3.5 (Java 21) as well as Velocity 4 (Java 25).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation(project(":lobby-common"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.viaversion.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.shadowJar {
    archiveFileName.set("lobby-bridge.jar")
}

// ViaVersion and Velocity are GPL-3.0 programs the proxy provides at runtime; their classes must never be
// bundled. The jar may only hold this project's own classes (lobby-common included) and the plugin files.
val checkBundledClasses by tasks.registering {
    description = "Fails if lobby-bridge.jar bundles any class that is not this project's own."
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val own = "io/github/mohammadhadimohammadi2007_dot/"
        val allowedFiles = setOf("velocity-plugin.json", "config.toml", "io/", "io/github/")
        val bundled = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        val foreign = bundled.filter { name ->
            !name.startsWith(own) && !name.startsWith("META-INF/") && name !in allowedFiles
        }
        val gpl = bundled.filter { it.startsWith("com/viaversion/") || it.startsWith("com/velocitypowered/") }
        if (gpl.isNotEmpty()) {
            throw GradleException("lobby-bridge.jar bundles ViaVersion or Velocity classes, which must only come from " +
                    "the proxy: ${gpl.take(5)}")
        }
        if (foreign.isNotEmpty()) {
            throw GradleException("lobby-bridge.jar bundles files that are not this project's: ${foreign.take(10)}")
        }
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(checkBundledClasses)
}
