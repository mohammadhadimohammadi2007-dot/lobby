// Settings shared by every module.
subprojects {
    apply(plugin = "java")

    group = "io.github.mohammadhadimohammadi2007-dot"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") // Velocity API
        maven("https://repo.viaversion.com/") // ViaVersion API
        // LuckPerms Minestom port, only used with -PwithLuckPerms (docs/integrations/luckperms.md).
        // First your local Maven cache (filled by `publishToMavenLocal` in the port's folder), then the
        // port's own repository. Only dev.lu15 artifacts are looked up here.
        mavenLocal {
            content { includeGroup("dev.lu15") }
        }
        maven("https://repo.hypera.dev/snapshots/") {
            content { includeGroup("dev.lu15") }
        }
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
    }
}
