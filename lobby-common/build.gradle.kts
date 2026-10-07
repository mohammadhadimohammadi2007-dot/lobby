// Code shared by the lobby server and the proxy bridge plugin.
// Keep this module free of Minestom and Velocity dependencies.
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
