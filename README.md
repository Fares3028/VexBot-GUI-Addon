# VexBot GUI Addon 1.13.0

Client-side Fabric addon (1.21.11) for the **VexBot 3.2.0** server mod. **B** opens the panel, **N** quick-spawns a bot with your last settings.

Tabs: QUICK, SPAWN, LOADOUT (+ kits), COMBAT, ORDERS, TEAMS, SETTINGS, PRESETS, CONSOLE.

- All commands were checked against the real VexBot 3.2.0 command tree (see CHANGELOG below).
- Server replies appear in the CONSOLE tab and in the footer.
- Command syntax lives in `config/vexbot_gui_commands.json` (`{bot}`, `{arg}`, `{arg2}` are replaced). Edit it, then press RELOAD CMDS. An older file is backed up as `.old` and reset automatically.
- Presets are saved in `config/vexbot_gui_presets.json` (old presets are migrated).
- Equipment uses vanilla `/item replace` (needs OP). Kits are VexBot's own (`/vexbot givekit`, `spawn ... <kit>` needs `settings advancedspawn on`).

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
