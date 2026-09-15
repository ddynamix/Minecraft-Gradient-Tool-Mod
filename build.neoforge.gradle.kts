plugins {
    // Version pinned in settings.gradle.kts pluginManagement
    id("net.neoforged.moddev")
}

// DO NOT set group here: Stonecutter manages the version nodes' coordinates
version = "${property("mod.version")}+${sc.current.version}-neoforge"
base.archivesName = property("mod.id") as String

// Read at project scope. Inside a task configuration block, property() resolves against the
// Task rather than the Project, which is what made `gradlew tasks` fail before.
val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modMcCompat = property("mod.mc_compat") as String

neoForge {
    version = property("deps.neoforge") as String

    runs {
        create("client") { client() }
        create("server") { server() }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // The core package has no Minecraft in it, so its tests need no Minecraft either
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()

    // 1.21.1 is Java 21; NeoForge has no targets below that
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.processResources {
    // Fabric metadata belongs only in the Fabric jars
    exclude("fabric.mod.json")

    val values = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to project.version.toString(),
        "minecraft" to modMcCompat,
    )

    inputs.properties(values)

    // NeoForge reads its metadata from META-INF, not from fabric.mod.json
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(values)
    }
}
