rootProject.name = "lobby"

include("lobby-common")
include("lobby-server")
include("lobby-bridge-velocity")

include("lobby-luckperms")

// The LuckPerms Minestom port is built from source: a git submodule in third_party/luckperms
// (run `git submodule update --init` after cloning). No Maven repository is needed for it.
includeBuild("third_party/luckperms") {
    dependencySubstitution {
        substitute(module("dev.lu15:luckperms-minestom")).using(project(":minestom"))
    }
}
