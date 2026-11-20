# VexBot GUI Addon 1.7.0

Client-side Fabric addon (1.21.11) for the **VexBot 3.2.0** server mod. **B** opens the panel, **N** quick-spawns a bot with your last settings.

Tabs: QUICK, SPAWN, LOADOUT (+ kits), COMBAT, ORDERS, TEAMS, SETTINGS, PRESETS, CONSOLE.

- All commands were checked against the real VexBot 3.2.0 command tree (see CHANGELOG below).
- Server replies appear in the CONSOLE tab and in the footer.
- Command syntax lives in `config/vexbot_gui_commands.json` (`{bot}`, `{arg}`, `{arg2}` are replaced). Edit it, then press RELOAD CMDS. An older file is backed up as `.old` and reset automatically.
- Presets are saved in `config/vexbot_gui_presets.json` (old presets are migrated).
- Equipment uses vanilla `/item replace` (needs OP). Kits are VexBot's own (`/vexbot givekit`, `spawn ... <kit>` needs `settings advancedspawn on`).

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
