# VexBot GUI Addon 1.17.0

Client-side Fabric addon (1.21.11) for the **VexBot 3.2.0** server mod. **B** opens the panel, **N** quick-spawns a bot with your last settings.

Tabs: DASHBOARD, QUICK, SPAWN, LOADOUT (+ kits), COMBAT, ORDERS, TEAMS, SETTINGS, PRESETS, CONSOLE.

- All commands were checked against the real VexBot 3.2.0 command tree (see CHANGELOG below).
- Server replies appear in the CONSOLE tab and in the footer.
- Command syntax lives in `config/vexbot_gui_commands.json` (`{bot}`, `{arg}`, `{arg2}` are replaced). Edit it, then press RELOAD CMDS. An older file is backed up as `.old` and reset automatically.
- Presets are saved in `config/vexbot_gui_presets.json` (old presets are migrated).
- Equipment uses vanilla `/item replace` (needs OP). Kits are VexBot's own (`/vexbot givekit`, `spawn ... <kit>` needs `settings advancedspawn on`).

## Changes in 1.17.0 (stability review of 1.16.0, see REVIEW-REPORT-v17.md)
- **Honest status**: QUEUED (scheduled) / SENT (left this client) / SERVER MESSAGE / SERVER ERROR / NOT SENT / NOT CONFIRMED.
  A bot counts as *confirmed* only when it is really in the player list; the chat report says "ONLINE (confirmed)" and "setup sent",
  never "done". The last-bot target is only set for a confirmed bot, and is cleared when you kill it.
- **Confirmations**: the SPAWN and BUILD TEAM buttons show CONFIRM only while the confirmation for exactly those settings is waiting;
  KILL / DELETE confirmations now expire visibly after 5 s ("CONFIRMATION EXPIRED").
- **Teams**: the list is read once per session automatically (no retry loop on failure); a failed or incomplete refresh marks the data
  OLD / "not confirmed"; roster answers can no longer land on the wrong team; DELETE TEAM and BUILD TEAM re-read the server list and
  report whether the result is really there.
- **Console**: every line has a time and a direction (`>` sent, `<` reply, `!` note), errors are red, config warnings stay on top.
  New dashboard row with the config state and COPY DIAGNOSTICS (versions, connection, warnings, last console lines).
- **Config safety**: a reset commands file is never silent; the original presets file is kept before a migration or when entries are
  skipped; a rolling `vexbot_gui_presets.json.bak` is written before each save; backup problems are warnings.
- **Robustness**: a bug in one scheduled task, reply collector or button can no longer crash the game (it is shown in the console);
  buttons that previously showed success without a connection (STOP EVERYTHING, kit, equipment) now check it; shorter text fitting.
- Tests: 47 (was 22): classifier, task queue, log buffer, confirmations, backups, team list completeness.
- CI also uploads the test report and generates the Gradle wrapper files as an artifact.

## Changes in 1.16.0 (real team list)
- TEAMS now has two clearly separated pages: **CREATE NEW TEAM** (name, size, level, BUILD) and **MANAGE A TEAM**.
- MANAGE has a **team picker** filled from the server (`/vexbot team list`, read-only) plus teams built here.
  It shows bots/players counts and how old the data is. REFRESH asks again; SHOW MEMBERS reads one team's roster.
- Every MANAGE action acts on the SELECTED team (never on a typed or remembered name); the status names the team.
- After BUILD TEAM the addon switches to MANAGE and refreshes the counts once the bots had time to join.
- The list is read from the server's chat reply; if the reply cannot be recognised the panel says so instead of guessing.
- New `vexbot_gui_commands.json` key `teamslist` (missing keys use the built-in default, so no reset is needed).
- Unit tests for the list/roster parser.

## Changes in 1.15.0 (safety + honest status)
- **Session reset**: joining or leaving a server forgets the last bot, last team, reserved random names and any pending confirmation.
- **Teams**: names are validated (1-24 letters, digits, `_`, `-`). BUILD TEAM and DELETE TEAM always need the name typed;
  other team actions may use the last team, and the field hint and status say so.
- **Confirmations** (mass spawn, BUILD TEAM over 3 bots) are bound to everything that matters (name, size, level, server session)
  and expire after 5 s; changing anything starts the confirmation over.
- **Honest status**: a command shows "sent, waiting for server", then "SERVER MESSAGE" with the first reply, or
  "no reply (not confirmed)". Server messages are not matched to a specific command (the console only records chat after a send).
- **Config**: command and preset problems are separate lists (several warnings are kept); a failed save no longer hides a load warning.
  A broken commands/presets JSON is moved to `<file>.bad-yyyyMMdd-HHmmss` instead of being overwritten.
- New unit tests: confirmations, file backup, preset migration, team-name validation.

## Changes in 1.14.0
- Added a dashboard with connection, last bot/team, preset count, and shortcuts.
- Console log now supports mouse-wheel scrolling.
- Preset save validation, 50-preset cap, and delete confirmation; preset writes use a safer temporary-file replacement.
- Random bot names avoid names already reserved in this session and online names more reliably.
- Layout spacing and sidebar sizing tuned for smaller GUI resolutions.
- Loadout now includes Elytra as a chest-slot option; preview and equip logic treat it separately from armor sets.

## Changes in 1.13.0
- Animations: panel fades in on open, the sidebar highlight glides between tabs, buttons fade smoothly on hover (frame-rate independent).

## Changes in 1.12.0
- Pixel-art icons for every sidebar tab (grey, mint when active).

## Changes in 1.11.0 (reliability)
- Pending commands, waiters and old replies are dropped when you join or leave a server/world.
- No server connection now shows "NO SERVER CONNECTION" instead of a fake success.
- The N key uses the same mass-spawn confirmation as the SPAWN button (press again within 5 s).
- Bot names are validated (1-16 letters, digits, _; empty = random). Console commands are cleaned.
- Config JSON is validated (empty/too long/invalid entries ignored) and file problems are shown in the panel.
- Added the missing key-binding names (lang file) and unit tests for validation.

## Changes in 1.10.0
- The preview now shows a **real player model** (same renderer as the inventory screen) that actually wears the
  chosen armor, holds the main-hand item and the off-hand item, and turns to follow your mouse.
  The glow behind it still scales with the difficulty. If the real model cannot be created, the old drawn bot is used.

## Changes in 1.9.0
- **Live preview card** on the left of the panel (shown when the window is at least 600 GUI-pixels wide):
  a bot that wears the selected armor (colour = material), holds the selected main-hand and off-hand items,
  with a glow behind it that grows, brightens and gets more sparks as the difficulty rises (easy -> perfect).
  In QUICK it previews the level you hover; in TEAMS it shows the team level; elsewhere your current setup.

## Changes in 1.8.0
- **Spawning is now reliable**: the addon spawns the bot, waits until it is really in the world (up to 5 s),
  then sets level, play style and gear one command per tick. At the end it tells you in chat whether it worked
  or what the server refused (usually: you need OP / cheats for the `item replace` gear commands).
- **Random names**: an empty name box gives a random free name (e.g. `Blaze47`) everywhere (QUICK, SPAWN, PRESETS, N key).
  Orders (follow, attack, kit ...) use the last spawned bot when the box is empty.
- **TEAMS rebuilt** into 3 clear steps: name the team, BUILD TEAM (spawns N bots of a level and adds them;
  the team is created with the first bot), then team orders. `teamcreate <name>` alone opens a server GUI,
  so the addon always uses `teamcreate <name> addbot <bot>` instead.

## Changes in 1.7.0
- Completely new layout: sidebar navigation on the left, one calm page on the right with a title, soft rounded cards,
  flat buttons with hover, wide level cards in QUICK, and borderless input fields with a mint focus line.
- Buttons are drawn by the addon; the vanilla buttons on top are invisible and only handle clicks.

## Changes in 1.6.0
- New **QUICK** tab (opens first): one click spawns a ready bot next to you.
  EASY = leather + wooden sword, MEDIUM = chainmail + stone sword, HARD = iron + iron sword + shield,
  EXPERT = diamond + diamond sword + shield, PERFECT = netherite + netherite sword + totem, plus RANDOM LEVEL.
  Name is automatic unless you type one. KILL LAST QUICK BOT removes the last one.
- Calmer mint/slate look with gradient bar, soft card and colour-coded level buttons.
- Added wooden / stone / iron swords to the LOADOUT weapon list.

## Changes in 1.5.0
- Fixed: `stop`, `wander`, `tp`, `follow`, `attack`, `randomgear`, `playstyle` used wrong syntax.
- Difficulty is now per-bot (`difficulty player <bot> preset <level>`); a separate button sets it for all bots.
- Removed `pro` difficulty and `passive` style (VexBot has neither); play style is now also applied on spawn.
- New ORDERS tab: follow / attack / hide / wander / go to / look / stop everything.
- New TEAMS tab, SETTINGS tab (common on/off settings), kit buttons, CLEAR LOG.
