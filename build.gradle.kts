plugins {
    // Applies the correct loom variant for the active Minecraft version
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group here: Stonecutter manages the version nodes' coordinates
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

// Minecraft's own Java requirement per era. 1.20.5 is where Mojang moved to 21.
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// Read at project scope. Inside a task configuration block, property() resolves against the
// Task rather than the Project, which is what made `gradlew tasks` fail with
// "Could not get unknown property 'mod.id' for task ':1.20.1:processResources'".
val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modMcCompat = property("mod.mc_compat") as String

repositories {
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")

    // Yarn, pinned per version in stonecutter.properties.toml. Mojang mappings were tried first
    // but left the compile classpath without a Minecraft jar under loom-back-compat; Yarn is
    // fully supported on Fabric for every target here and needs no source changes.
    mappings("net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // The core package has no Minecraft in it, so its tests need no Minecraft either
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()

    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.processResources {
    val values = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to project.version.toString(),
        "minecraft" to modMcCompat,
    )

    inputs.properties(values)

    filesMatching("fabric.mod.json") {
        expand(values)
    }
}

loom {
    // Shared between version nodes, so they do not each need their own world
    runConfigs.all {
        runDirectory = rootProject.file("run")
    }
}
