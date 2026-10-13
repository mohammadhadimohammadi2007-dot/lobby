// The public API for addons: jars in the lobby's addons/ folder that extend it without forking it.
// Interfaces and small value types only, so the lobby's own classes can change freely. Semantically
// versioned on its own (see ApiVersion); published as io.github.mohammadhadimohammadi2007-dot:lobby-api.
plugins {
    `java-library`
    `maven-publish`
}

// The API's own version, independent of the lobby's. Keep ApiVersion.CURRENT equal to it (a test checks).
version = "0.2.0"

dependencies {
    // Addons compile against Minestom and get it from the server at runtime.
    compileOnlyApi(libs.minestom)
    compileOnlyApi(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.minestom)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
}

tasks.test {
    systemProperty("lobby.api.version", project.version.toString())
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "lobby-api"
            from(components["java"])
            pom {
                name.set("Lobby API")
                description.set("API for addons of the Minestom lobby server")
                licenses {
                    license {
                        name.set("MIT")
                    }
                }
            }
        }
    }
}
