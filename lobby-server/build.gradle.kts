// The Minestom lobby server. Produces build/libs/lobby-server.jar (a runnable fat jar).
plugins {
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":lobby-common"))

    implementation(libs.minestom)
    implementation(libs.polar)
    implementation(libs.adventure.minimessage)
    implementation(libs.configurate.yaml)
    implementation(libs.logback.classic)
    implementation(libs.hikaricp)
    implementation(libs.mariadb)
    implementation(libs.gson)
    // LuckPerms support, found at runtime through ServiceLoader (see LuckPermsIntegration).
    runtimeOnly(project(":lobby-luckperms"))
    // Polar's class files reference fastutil types; Minestom only ships it at runtime.
    compileOnly(libs.fastutil)
    testCompileOnly(libs.fastutil)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.minestom.testing)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyMain")
    // zstd (used by Polar) loads a native library.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Lets Minestom's test harness create fake players on normal threads.
    systemProperty("minestom.inside-test", "true")
}

// `./gradlew :lobby-server:run` starts a local server inside lobby-server/run/
tasks.named<JavaExec>("run") {
    val runDir = layout.projectDirectory.dir("run").asFile
    doFirst { runDir.mkdirs() }
    workingDir = runDir
    standardInput = System.`in`
}

tasks.shadowJar {
    archiveFileName.set("lobby-server.jar")
    mergeServiceFiles()
    manifest {
        attributes(
            "Implementation-Version" to project.version,
            // Same as --enable-native-access=ALL-UNNAMED for `java -jar` (zstd loads a native library).
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
