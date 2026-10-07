// Optional LuckPerms support for the lobby server, built on the LuckPerms Minestom port
// (github.com/LooFifteen/LuckPerms, branch feat/minestom). Included in lobby-server.jar when building
// with -PwithLuckPerms. See docs/integrations/luckperms.md for how to get the port.
dependencies {
    compileOnly(project(":lobby-server"))
    compileOnly(libs.minestom)
    // Gradle resolves LuckPerms' older Adventure and Gson to the newer versions the lobby ships.
    implementation(libs.luckperms.minestom)
}
