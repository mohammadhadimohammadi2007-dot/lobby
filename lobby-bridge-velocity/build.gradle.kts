// Velocity proxy plugin that feeds the lobby with network information.
// Produces build/libs/lobby-bridge.jar
plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":lobby-common"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.viaversion.api)
}

tasks.shadowJar {
    archiveFileName.set("lobby-bridge.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
