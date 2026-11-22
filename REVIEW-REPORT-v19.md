# VexBot GUI Addon 1.19.0 — VexBot 3.2.5 Team Integration Fix

## Evidence used
The supplied `vexbot-3.2.5+mc1.21.11.zip` contains the compiled VexBot 3.2.5 classes. Bytecode inspection confirmed:
- `team create <name>` invokes `openTeamBuilderGui` for a player source; it is not a silent CLI create operation.
- `team <name> members add <bot>` calls `TeamManager.addBotsToTeam`, which calls `TeamManager.getOrCreate`; this is the supported non-interactive team creation path.
- `team <name> members addall` exists, but was deliberately NOT used because it would add every spawned bot on the server, including unrelated bots.
- `team <name> follow <player> <formation>` resolves the team, iterates every name in `BotTeam.getBotNames()`, and calls `BotManager.setFollowTarget` for each member.
- `SettingsCli` supports `autotarget`, `targetplayers`, `targetbots`, `targetmobs`, and `revengemode`, with boolean values `on`/`off`.
- `TeamManager.addBotsToTeam` creates a missing team and stores member names in a set, so re-adding the same bot is idempotent.

## Changes
1. Removed the premature `team create` command from GUI team build. The first successful `members add` now creates the team without opening a server-side menu.
2. After each bot joins the team, apply team-scoped safety settings: `autotarget off`, `targetplayers off`, `targetbots off`, `targetmobs off`, `revengemode off`.
3. Team presets now default to `safe` playstyle rather than switching Hard/Expert/Perfect bots to aggressive/adaptive automatically. Users can still explicitly change playstyle from Manage Team.
4. Added expected-member tracking for teams created by this GUI. On FOLLOW ME, the addon checks which expected bots are currently visible, idempotently re-adds those bots to that specific team, then sends one team-scoped follow command. It does not use `addall`, so unrelated bots are not pulled into the team.
5. Follow replies are displayed as server replies; no success is claimed merely because the command was sent. Missing expected bots are named.
6. Added `autotarget` and `revengemode` to the global Settings picker.
7. Existing arrow, firework rocket, and wind charge supply logic is retained. It grants inventory items; actual AI use still depends on VexBot's server-side controllers.

## Files changed
- `build.gradle` — version 1.19.0
- `src/main/resources/fabric.mod.json` — version 1.19.0
- `src/main/java/com/fares/vexbotgui/Config.java` — correct `off` values and new team safety command templates
- `src/main/java/com/fares/vexbotgui/Actions.java` — safe team build, team safety policy, expected roster tracking, follow resync, extra settings
- `src/main/java/com/fares/vexbotgui/VexBotScreen.java` — FOLLOW ME now uses roster-aware team flow
- `src/main/java/com/fares/vexbotgui/TeamSafetyPolicy.java` — new pure command policy helper
- `src/main/java/com/fares/vexbotgui/TeamFollowPlan.java` — new expected-member online/missing partition helper
- tests added for safety command templates and follow-member partitioning

## Tests
The pure helper tests were compiled and exercised independently with `javac`/`java`; see `LOCAL-TEST-RESULTS.txt`. ZIP integrity and JSON parsing were checked. A full Fabric Gradle build and in-game test were not possible in this environment.

## Known limitations / in-game checks
- The server must be running VexBot 3.2.5 for the inspected command behavior to apply.
- `members add` and team settings require the server permission level VexBot expects (the addon already requires operator permissions for these controls).
- Team follow only targets members recorded in that team. The addon re-adds expected bots that are online; bots not in the player list are reported as missing.
- Setting `revengemode off` and target filters off prevents autonomous/retaliatory target acquisition for the team; explicit attack commands remain available.
- Ammo/rocket/wind-charge grants require permission for vanilla `/give`. This verifies grant requests only; controller behavior must be tested in-game.
- No VexBot server JAR was modified. This addon cannot patch bugs inside the server mod's combat or movement controllers.
