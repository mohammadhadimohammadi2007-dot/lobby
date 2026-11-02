// The public API for addons: jars in the lobby's addons/ folder that extend it without forking it.
// Interfaces and small value types only, so the lobby's own classes can change freely. Semantically
// versioned on its own (see ApiVersion); published as io.github.mohammadhadimohammadi2007-dot:lobby-api.
plugins {
    `java-library`
    `maven-publish`
}

// The API's own version, independent of the lobby's. Keep ApiVersion.CURRENT equal to it (a test checks).
// A new class or method needs the next minor version, a removed or changed one the next major version:
// ApiCompatibilityTest compares the API with api/lobby-api.api and says what to do (docs/addon-development.md).
version = "1.0.0"

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
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        // Missing comments are not errors; broken references and HTML still are.
        addStringOption("Xdoclint:all,-missing", "-quiet")
        links("https://docs.oracle.com/en/java/javase/25/docs/api/")
    }
}

val apiFile = layout.projectDirectory.file("api/lobby-api.api")

tasks.test {
    systemProperty("lobby.api.version", project.version.toString())
    systemProperty("lobby.api.file", apiFile.asFile.absolutePath)
    inputs.file(apiFile).optional()
    // The dump is written by apiDump, never by the normal tests.
    filter {
        excludeTestsMatching("*ApiCompatibilityTest.writeTheApiFile")
    }
}

// Records the current API in api/lobby-api.api, after checking the version was raised as the changes need.
tasks.register<Test>("apiDump") {
    description = "Writes the current public API to api/lobby-api.api (checks the version first)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    systemProperty("lobby.api.version", project.version.toString())
    systemProperty("lobby.api.file", apiFile.asFile.absolutePath)
    filter {
        includeTestsMatching("*ApiCompatibilityTest.writeTheApiFile")
    }
    outputs.upToDateWhen { false }
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
