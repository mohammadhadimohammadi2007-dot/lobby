// Settings shared by every module.
subprojects {
    apply(plugin = "java")

    group = "io.github.mohammadhadimohammadi2007-dot"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") // Velocity API
        maven("https://repo.viaversion.com/") // ViaVersion API
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Database tests (classes ending in IT) skip themselves without LOBBY_TEST_DB_PORT, which is fine
        // on a laptop. CI sets LOBBY_REQUIRE_DB_TESTS=1, and then a skipped one fails the build.
        val requireDbTests = System.getenv("LOBBY_REQUIRE_DB_TESTS") == "1"
        val itSources = project.fileTree("src/test/java") { include("**/*IT.java") }
        val results = reports.junitXml.outputLocation
        if (requireDbTests) {
            // Always run them, even when Gradle thinks nothing changed.
            outputs.upToDateWhen { false }
        }
        doLast {
            if (!requireDbTests || itSources.isEmpty) {
                return@doLast
            }
            val suites = results.get().asFile.listFiles { file -> file.name.matches(Regex("""TEST-.*IT\.xml""")) }
                .orEmpty()
            val counts = Regex("""tests="(\d+)" skipped="(\d+)"""")
            val problems = mutableListOf<String>()
            for (suite in suites) {
                val match = counts.find(suite.readText().take(2000)) ?: continue
                val (tests, skipped) = match.destructured
                if (skipped.toInt() > 0 || tests.toInt() == 0) {
                    problems += suite.name.removePrefix("TEST-").removeSuffix(".xml") + " (" + skipped + " skipped)"
                }
            }
            if (suites.size < itSources.files.size) {
                problems += "only " + suites.size + " of " + itSources.files.size + " database test classes ran"
            }
            if (problems.isNotEmpty()) {
                throw GradleException("Database tests must run here (LOBBY_REQUIRE_DB_TESTS=1): "
                        + problems.joinToString(", ") + ". Is LOBBY_TEST_DB_PORT set?")
            }
        }
    }
}
