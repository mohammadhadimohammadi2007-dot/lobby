// An example addon for the lobby: a click counter that shows most of the addon API (see README.md).
// It is part of the lobby's build, so it always compiles against the current API, and a test starts it in a
// real lobby. To start your own addon, copy this folder and follow "Using it as a template" in README.md.
plugins {
    java
}

group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // compileOnly: the lobby provides the API and Minestom at runtime, so they must not be inside the addon's jar.
    compileOnly(project(":lobby-api"))
    compileOnly(libs.minestom)
}

tasks.jar {
    // A fixed name, so the lobby's test (and you) can find it: build/libs/example-addon.jar
    archiveFileName = "example-addon.jar"
}

tasks.processResources {
    // addon.json gets the version from here.
    val addonVersion = project.version.toString()
    inputs.property("version", addonVersion)
    filesMatching("addon.json") {
        expand("version" to addonVersion)
    }
}
