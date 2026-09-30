# Bundled build dependencies

The official BedWars1058 25.2 release bundles the sidebar library (`com.andrei1058.spigot.sidebar`)
in its shaded form. Those versions are not published in any public maven repository anymore
(`repo.codemc.io` only has `23.12.1-SNAPSHOT`, `sidebar-base:24.2` is nowhere to be found), so they
are extracted from the official release jar and provided here.

## How they were generated

1. all classes under `com/andrei1058/bedwars/libs/sidebar/**` were extracted from the official
   `bedwars1058-plugin-25.2.jar`;
2. the shade relocation was reverted (`com/andrei1058/bedwars/libs/sidebar` ->
   `com/andrei1058/spigot/sidebar`) with `libs/tools/DeRelocator.java` (ASM based, the string
   constants are remapped as well), build it with `asm` + `asm-commons`;
3. the classes were packaged into the original artifacts, using the versions recorded in the
   official jar (`sidebar-base:24.2`, all `sidebar-vX_Y_RZ:24.2` except `sidebar-v1_20_R4:23.12`).

`com.flowpowered:flow-nbt:2.0.0` was extracted the same way (it is not relocated, so the classes
could be copied as is), because the public repositories only have `2.0.2` while the official
release bundles `2.0.0`.

`libs/repo` is a local maven repository, registered as the `bundled-libs` repository in the root
`pom.xml`, so `mvn clean package` resolves them like any other dependency.

## Contents

| Artifact | Version |
| --- | --- |
| `com.andrei1058.spigot.sidebar:sidebar-base` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_8_R3` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_12_R1` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_16_R3` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_17_R1` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_18_R2` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_19_R2` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_19_R3` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_20_R1` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_20_R2` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_20_R3` | 24.2 |
| `com.andrei1058.spigot.sidebar:sidebar-v1_20_R4` | 23.12 |

The jars are shaded into the plugin jar by the build, exactly like the original artifacts were.
