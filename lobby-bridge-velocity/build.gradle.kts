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

tasks.build {
    dependsOn(tasks.shadowJar)
}
