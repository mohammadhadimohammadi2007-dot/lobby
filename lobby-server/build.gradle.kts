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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyMain")
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
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
