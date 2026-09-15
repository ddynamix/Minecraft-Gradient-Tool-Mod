pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }

    // Pinned here so the per-loader buildscripts can ask for the plugin without repeating a version
    plugins {
        id("net.neoforged.moddev") version "2.0.147"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Picks the right Loom variant per version: fabric-loom-remap below 26, fabric-loom above.
    // Also provides loomx.applyMojangMappings(), which is what lets the Fabric side use the same
    // Mojang names NeoForge needs, so one source tree can serve both loaders.
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Provisions a JDK when the required toolchain is not installed. 1.21.1 needs Java 21, which
    // is not on this machine, so this is what stops the build simply failing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Nodes are named "<minecraft>-<loader>" and each loader gets its own buildscript:
        // Loom for Fabric, ModDevGradle for NeoForge. The loader half of the name is also what
        // stonecutter.gradle.kts turns into the `fabric` / `neoforge` constants.
        fun match(minecraft: String, vararg loaders: String) =
            loaders.forEach { loader ->
                version("$minecraft-$loader", minecraft).buildscript = "build.$loader.gradle.kts"
            }

        match("1.20.1", "fabric")
        match("1.21.1", "fabric", "neoforge")

        vcsVersion = "1.21.1-fabric"
    }
}

rootProject.name = "gradient-wand"
