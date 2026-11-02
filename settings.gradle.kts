rootProject.name = "lobby"

include("lobby-common")
include("lobby-api")
include("lobby-server")
include("lobby-bridge-velocity")

include("lobby-luckperms")

// The example addon (examples/addon-example): built with the lobby, so it never falls behind the API.
include("example-addon")
project(":example-addon").projectDir = file("examples/addon-example")

// The LuckPerms Minestom port is built from source: a git submodule in third_party/luckperms
// (run `git submodule update --init` after cloning). No Maven repository is needed for it.
includeBuild("third_party/luckperms") {
    dependencySubstitution {
        substitute(module("dev.lu15:luckperms-minestom")).using(project(":minestom"))
    }
}
