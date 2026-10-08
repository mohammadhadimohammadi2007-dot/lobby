// LuckPerms support for the lobby server, built on the LuckPerms Minestom port. The port is compiled
// from the git submodule in third_party/luckperms (see settings.gradle.kts). LuckPerms is always part of
// lobby-server.jar but stays off until it is enabled in integrations.yml.
dependencies {
    compileOnly(project(":lobby-server"))
    compileOnly(libs.minestom)
    // Gradle resolves LuckPerms' older Gson and Guava to the newer versions the lobby ships.
    implementation(libs.luckperms.minestom)

    testImplementation(project(":lobby-server"))
    testImplementation(libs.minestom)
    testImplementation(libs.minestom.testing)
    testImplementation(libs.mariadb)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("minestom.inside-test", "true")
    // LuckPermsLiveIT starts a second JVM; pass the database settings through.
    environment(System.getenv().filterKeys { it.startsWith("LOBBY_TEST_DB_") })
}
