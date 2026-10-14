# Example Vibe extension

This optional JAR provides **Test** in Movement. Yaw and Pitch feed MoveFix without moving the first-person camera. Disable Test to release its rotation.

From the repository root, run `gradlew exampleExtensionJar`. Install `build/libs/Vibe-Example-0.0.6.jar` alongside core Vibe in `mods/`, restart, then open Movement → Test. The core client does not include Test.

This folder also builds independently: build core's development JAR with `gradlew jar`, then run `gradlew -p extensions/example build`. To copy it into a new project, change the package, entrypoint, descriptor ID and module name. Supply `-PvibeJar=<absolute path to Vibe's development JAR>` outside this repository. See [the extension guide](../../docs/EXTENSIONS.md) for lifecycle hooks, resources, compatibility and remapping.
