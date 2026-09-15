plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-neoforge" /* [SC] DO NOT EDIT */

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    // Node names are "<minecraft>-<loader>", so the trailing half is the loader. This is what
    // makes `//? if fabric {` and `//? if neoforge {` usable in shared source: exactly one of
    // these constants is true per node.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")

    // Lets source read the mod and Minecraft version without hardcoding either
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
}
