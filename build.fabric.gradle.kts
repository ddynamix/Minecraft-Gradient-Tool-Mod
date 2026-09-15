plugins {
    // Applies the correct loom variant for the active Minecraft version
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group here: Stonecutter manages the version nodes' coordinates
version = "${property("mod.version")}+${sc.current.version}-fabric"
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

    // Mojang official names, via the helper loom-back-compat provides. NeoForge builds against
    // these, and Yarn is unusable there: architectury-loom issue 230 reports unfixable mapping
    // collisions at exactly yarn 1.21.1+build.3 against NeoForge 21.1.x. Sharing one source tree
    // across both loaders therefore means Mojang names on the Fabric side too.
    loomx.applyMojangMappings()

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

    // javac stops listing after 100 and Gradle caps at 200, which hides the true error count
    // during the Yarn to Mojang migration. Harmless once the tree is green again.
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.processResources {
    // NeoForge metadata belongs only in the NeoForge jar
    exclude("META-INF/neoforge.mods.toml")

    val values = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to project.version.toString(),
        "minecraft" to modMcCompat,
        // Was hardcoded to 17 in fabric.mod.json, which under-declared the 1.21.1 jar: it is
        // compiled at 21, so a player on 17 got a class-version crash instead of a clean refusal.
        "java" to requiredJava.majorVersion,
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
