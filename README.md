# VexBot GUI Addon (client-side)

A separate Fabric client addon for Minecraft 1.21.11. Press **B** to open the control panel. It does not modify or bundle the original VexBot JAR.

## Current prototype
- Modern dark control panel.
- Bot name field.
- EASY / MEDIUM / HARD selector.
- Spawn Bot action.
- Random Gear action.
- Armor selector (none/leather/chainmail/iron/diamond/netherite) via vanilla `/item replace`.
- Kill Bot button.

## Important compatibility note
VexBot 3.2.0 declares itself as a server-side mod. The addon sends commands through the normal client command connection, so the player must have permission to run VexBot commands and VexBot must be installed on the server/world. The original mod's command tree confirms `/vexbot spawn <name>`, `/vexbot randomgear`, and `/vexbot difficulty preset <level>` exist; the exact accepted difficulty preset values and whether the preset is global or per-bot must be tested in-game before treating this prototype as release-ready.

## Build
Requires Java 21 and Gradle 8.14+ with internet access for Fabric Loom/Minecraft dependencies:

```bash
gradle build
```

The compiled JAR will be in `build/libs/`.

## Install
1. Build the project.
2. Put `vexbot-gui-addon-1.0.0.jar` in the client's `mods` folder.
3. Keep the original VexBot mod installed on the server/world.
4. Launch Minecraft 1.21.11 with Fabric and press **B**.
