rootProject.name = "lobby"

include("lobby-common")
include("lobby-server")
include("lobby-bridge-velocity")

// Optional LuckPerms support. Only built with: ./gradlew build -PwithLuckPerms
// It needs the LuckPerms Minestom port, which is not on Maven Central (see docs/integrations/luckperms.md).
if (providers.gradleProperty("withLuckPerms").isPresent) {
    include("lobby-luckperms")
}
