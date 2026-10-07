// Code shared by the lobby server and the proxy bridge plugin.
// Keep this module free of Minestom and Velocity dependencies.
// Compiled for Java 21 because it is bundled into the Velocity bridge, which must also run on Java 21 proxies.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
