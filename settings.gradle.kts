pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Picks the right Loom variant per version: fabric-loom-remap below 26, fabric-loom above.
    // Also provides loomx.applyMojangMappings().
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Provisions a JDK when the required toolchain is not installed. 1.21.1 needs Java 21, which
    // is not on this machine, so this is what stops the build simply failing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 1.20.1 is the proven-green baseline; 1.21.1 is the port target. Keeping both means any
        // breakage can be attributed to the port rather than to the harness.
        versions("1.20.1", "1.21.1")
        vcsVersion = "1.20.1"
    }
}

rootProject.name = "gradient-wand"
