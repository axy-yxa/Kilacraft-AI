# Kilacraft-AI - Bukkit API Reference Manual

> **Last Updated**: 2026-08-04  
> **Description**: This document provides detailed explanations, configuration examples, and usage scenarios for all built-in Bukkit APIs

---

## 📊 Bukkit API Quick Reference

### 👤 Player-related APIs (44)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_player_hand_item` | Get Player Main Hand Item | Get main hand item info | method_chain | `kilacraft.api.player.inventory` |
| `get_player_offhand_item` | Get Player Offhand Item | Get off hand (shield slot) item | method_chain | `kilacraft.api.player.inventory` |
| `get_player_health` | Get Player Health | Get current/max health | additional_methods | `kilacraft.api.player.status` |
| `get_player_food` | Get Player Hunger | Get food level and saturation | additional_methods | `kilacraft.api.player.status` |
| `get_player_oxygen` | Get Player Oxygen | Get underwater breathing time | additional_methods | `kilacraft.api.player.status` |
| `get_player_location` | Get Player Location | Get coordinates and world | additional_methods | `kilacraft.api.player.info` |
| `get_player_eye_location` | Get Player Eye Location | Get precise eye coordinates | method_chain | `kilacraft.api.player.info` |
| `get_player_velocity` | Get Player Velocity | Get movement velocity vector | method_chain | `kilacraft.api.player.info` |
| `get_player_gamemode` | Get Player Gamemode | Get Survival/Creative/Adventure/Spectator | method_chain | `kilacraft.api.player.info` |
| `get_player_fly_status` | Get Player Fly Status | Get allow flying/is flying | additional_methods | `kilacraft.api.player.info` |
| `get_player_fly_speed` | Get Player Fly Speed | Get fly speed setting | method_chain | `kilacraft.api.player.info` |
| `get_player_walk_speed` | Get Player Walk Speed | Get walk speed setting | method_chain | `kilacraft.api.player.info` |
| `get_player_exp` | Get Player Experience | Get level and exp progress | additional_methods | `kilacraft.api.player.status` |
| `get_player_exp_to_level` | Get Exp to Next Level | Get exp needed to level up | method_chain | `kilacraft.api.player.status` |
| `get_player_main_hand` | Get Player Main Hand Preference | Get left-handed/right-handed setting | method_chain | `kilacraft.api.player.info` |
| `get_player_ping` | Get Player Ping | Get network latency (ms) | method_chain | `kilacraft.api.player.info` |
| `get_player_sleep_status` | Get Player Sleep Status | Get whether sleeping and duration | additional_methods | `kilacraft.api.player.status` |
| `get_player_last_death` | Get Player Last Death Location | Get death location coordinates | method_chain | `kilacraft.api.player.info` |
| `get_player_attack_cooldown` | Get Player Attack Cooldown | Get attack cooldown progress (0-1) | method_chain | `kilacraft.api.player.status` |
| `get_player_vehicle` | Get Player Vehicle Status | Get whether in a vehicle | additional_methods | `kilacraft.api.player.info` |
| `get_player_fire_status` | Get Player Fire Status | Get whether on fire and burn time | additional_methods | `kilacraft.api.player.status` |
| `get_player_freeze_status` | Get Player Freeze Status | Get whether frozen and freeze level | additional_methods | `kilacraft.api.player.status` |
| `get_player_pose` | Get Player Pose | Get standing/crouching/swimming, etc. | method_chain | `kilacraft.api.player.info` |
| `get_player_armor` | Get Player Armor | Get full armor set info | method_chain | `kilacraft.api.player.inventory` |
| `get_player_potion_effects` | Get Player Potion Effects | Get all active potion effects | method_chain | `kilacraft.api.player.status` |
| `get_player_target_block` | Get Player Target Block | Get block the crosshair is aiming at | method_chain | `kilacraft.api.player.info` |
| `get_player_sneak_status` | Get Player Sneak Status | Get whether sneaking (Shift) | method_chain | `kilacraft.api.player.status` |
| `get_player_sprint_status` | Get Player Sprint Status | Get whether sprinting (double-tap W) | method_chain | `kilacraft.api.player.status` |
| `get_player_locale` | Get Player Client Language | Get language setting (e.g. zh_CN) | method_chain | `kilacraft.api.player.info` |
| `get_player_display_name` | Get Player Display Name | Get display name (incl. prefix) | method_chain | `kilacraft.api.player.info` |
| `get_player_bed_spawn` | Get Player Bed Spawn | Get bed spawn location | method_chain | `kilacraft.api.player.info` |
| `get_player_total_exp` | Get Player Total Experience | Get accumulated total experience | method_chain | `kilacraft.api.player.status` |
| `get_player_inventory_usage` | Get Inventory Usage | Get occupied slots/empty slots | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_inventory` | Get Inventory Summary | Get inventory item name+amount list | method_chain | `kilacraft.api.player.inventory` |
| `get_player_ender_chest` | Get Ender Chest Summary | Get ender chest item name+amount list | method_chain | `kilacraft.api.player.inventory` |
| `get_player_open_container` | Get Open Container Content | Get current open container item summary | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_open_inventory` | Get Open Inventory | Get currently viewing container/type | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_absorption` | Get Absorption Hearts | Get extra absorption health | method_chain | `kilacraft.api.player.status` |
| `get_player_arrows_in_body` | Get Arrows in Body | Get embedded arrow count | method_chain | `kilacraft.api.player.status` |
| `get_player_no_damage_ticks` | Get No Damage Ticks | Get invincibility time after hit (tick) | method_chain | `kilacraft.api.player.status` |
| `get_player_fall_distance` | Get Fall Distance | Get accumulated fall distance | method_chain | `kilacraft.api.player.status` |
| `get_player_compass_target` | Get Compass Target | Get compass pointing coordinates | additional_methods | `kilacraft.api.player.info` |
| `get_player_feet_block` | Get Feet Block | Get block standing on | method_chain | `kilacraft.api.player.info` |
| `get_player_last_damage` | Get Last Damage | Get last damage source/cause/amount | method_chain | `kilacraft.api.player.status` |

### 🌍 World-related APIs (21)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_world_time` | Get World Time | Get game time (HH:MM) | method_chain | `kilacraft.api.world.info` |
| `get_weather` | Get Weather | Get clear/rain/thunderstorm | additional_methods | `kilacraft.api.world.info` |
| `get_world_info` | Get World Basic Info | Get name/environment/difficulty | additional_methods | `kilacraft.api.world.info` |
| `get_world_seed` | Get World Seed | Get world seed value | method_chain | `kilacraft.api.world.info` |
| `get_world_spawn` | Get World Spawn | Get world spawn location | method_chain | `kilacraft.api.world.info` |
| `get_world_height_limit` | Get World Height Limit | Get min/max build height | additional_methods | `kilacraft.api.world.info` |
| `get_world_spawn_rules` | Get World Spawn Rules | Get whether mobs/animals can spawn | additional_methods | `kilacraft.api.world.info` |
| `get_world_pvp` | Get World PVP Setting | Get whether PVP is allowed | method_chain | `kilacraft.api.world.info` |
| `get_world_biome` | Get World Biome | Get biome type (plains/desert, etc.) | method_chain | `kilacraft.api.world.info` |
| `get_world_temperature` | Get World Temperature | Get temperature (affects snowfall) | method_chain | `kilacraft.api.world.info` |
| `get_world_humidity` | Get World Humidity | Get humidity (affects rainfall) | method_chain | `kilacraft.api.world.info` |
| `get_world_player_count` | Get World Player Count | Get player count in world | additional_methods | `kilacraft.api.world.info` |
| `get_world_living_entities` | Get World Living Entities Count | Get living entity count | additional_methods | `kilacraft.api.world.info` |
| `get_world_entity_count` | Get World Entity Count | Get total entity count | additional_methods | `kilacraft.api.world.info` |
| `get_world_sea_level` | Get World Sea Level | Get sea level Y coordinate | method_chain | `kilacraft.api.world.info` |
| `get_world_clear_weather_duration` | Get Clear Weather Duration | Get remaining clear weather ticks | method_chain | `kilacraft.api.world.info` |
| `get_world_thunder_duration` | Get Thunder Duration | Get remaining thunder ticks | method_chain | `kilacraft.api.world.info` |
| `get_world_full_time` | Get World Full Time | Get total running time (unaffected by sleep) | method_chain | `kilacraft.api.world.info` |
| `get_world_game_time` | Get World Game Time | Get total time since creation | method_chain | `kilacraft.api.world.info` |
| `get_world_raids` | Get World Raids | Get list of ongoing raids | method_chain | `kilacraft.api.world.info` |
| `get_world_border` | Get World Border | Get border center/size/damage | method_chain | `kilacraft.api.world.info` |

### 🖥️ Server APIs (6)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_online_players` | Get Online Player Count | Get online player count and list | method_chain | `kilacraft.api.server.info` |
| `get_max_players` | Get Max Players | Get server max capacity | method_chain | `kilacraft.api.server.info` |
| `get_server_version` | Get Server Version | Get Bukkit and MC version | additional_methods | `kilacraft.api.server.info` |
| `get_server_motd` | Get Server MOTD | Get server intro message | method_chain | `kilacraft.api.server.info` |
| `get_server_worlds` | Get Server Worlds List | Get all loaded worlds | method_chain | `kilacraft.api.server.info` |
| `get_server_settings` | Get Server Settings | Get flight/nether/end settings | additional_methods | `kilacraft.api.server.info` |

**Statistics**: Total **71 APIs** (Player 44 + World 21 + Server 6)

---

## 📖 Overview

Kilacraft-AI includes **71 built-in read-only Bukkit APIs**, allowing AI to access various data from the Minecraft server. These APIs are provided by 5 independent config-driven skills, all extending `AbstractBukkitQuerySkill`.

### Core Features

- ✅ **Standard config-driven**: 5 independent skills (`BukkitPlayerInfo`/`BukkitPlayerStatus`/`BukkitPlayerInventory`/`BukkitWorld`/`BukkitServer`), each defined via `description`+`action_descriptions`+`hints`, fully consistent with other built-in skills in the project
- ✅ **Per-skill permission control**: Each skill has a single permission node covering all APIs in that skill (see "Permission Management")
- ✅ **Smart formatting**: Complex types (Location, ItemStack, GameMode, ItemStack[], Set<PotionEffect>, etc.) are handled automatically by each skill's Java class (`AbstractBukkitQuerySkill` and subclasses)
- ✅ **Error isolation**: API execution failures don't affect other features

### Configuration File Location

The 5 split skill config files, all under the `skills/bukkit/` directory:

```
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerInfoSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerStatusSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerInventorySkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitWorldSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitServerSkill.yml
```

> Note: The old single `apis.yml` / `apis_en.yml` were removed in commit b64cb0e and are no longer used.

---

## 🔧 Skill Configuration Structure

> As of commit b64cb0e, the old data-driven model based on a single `apis.yml` + `method_chain`/`additional_methods`/`result_template` has been removed. The 5 split skills now use the same standard skill configuration structure as every other built-in skill in the project.

### Standard Skill Configuration Schema

Each `Bukkit*Skill.yml` file follows the unified skill config schema, with core fields `description` + `action_descriptions` (text block) + `hints`:

```yaml
# BukkitPlayerInventorySkill.yml (excerpt)
skill_id: "player_inventoryentory"
display_name: "Bukkit Player Inventory Query"
description: |
  Query player inventory-related info: main/off-hand items, inventory and ender chest
  summaries, inventory usage, armor equipment, currently open container/inventory type, etc.
permission: "kilacraft.api.player.inventory"

action_descriptions: |
  get_player_hand_item — Get player main hand item (type/amount/enchantments/durability)
  get_player_offhand_item — Get player offhand item (shield slot)
  get_player_armor — Get full armor set (helmet/chestplate/leggings/boots, incl. name/type/amount/enchantments/durability)
  get_player_inventory_usage — Get inventory occupied slots/empty slots
  get_player_inventory — Get inventory item name+amount list
  ...

hints: |
  - Inventory queries are read-only and safe to run async.
  - Methods requiring the main thread (armor/inventory/container) are scheduled internally by the skill.
```

### Key Changes

| Old model (removed) | New model (current) |
|---------------------|---------------------|
| Single `apis.yml` with 71 API entries | 5 independent `Bukkit*Skill.yml` skill files |
| Per-API `method_chain` / `additional_methods` / `result_template` | Invocation and formatting logic moved into Java classes (`AbstractBukkitQuerySkill` + subclasses) |
| One `required_permission` node per API | One permission node per skill, covering all APIs in that skill |
| `description` as a single-API description | `action_descriptions` text block describing all actions in the skill |

> **Note**: The action IDs, return fields (e.g. `helmet_name`, `item_count`, `raw_result`), and output format are all preserved from the original `apis.yml` — only the organization changed from "one config block per API in a single file" to "one yml + Java implementation per skill". The "Return Fields" and "Multi-step Data Passing" notes for each API below remain valid.

---

## 👤 Player-related APIs

### Inventory-related

#### get_player_hand_item

**Function**: Get player main hand item

```yaml
get_player_hand_item:
  id: "get_player_hand_item"
  display_name: "Get Player Main Hand Item"
  description: "Get information about the item held in player's main hand, including item type, quantity, etc."
  usage_scenarios:
    - "When user asks 'what am I holding'"
    - "Check my items"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getItemInMainHand"
```

**Usage Example**:
```
Player: What am I holding?
AI: You are holding in main hand: Diamond Sword x1
```

---

#### get_player_offhand_item

**Function**: Get player offhand item

```yaml
get_player_offhand_item:
  id: "get_player_offhand_item"
  display_name: "Get Player Offhand Item"
  description: "Get the item held in player's offhand (shield slot)"
  usage_scenarios:
    - "What's in offhand"
    - "Check my offhand"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getItemInOffHand"
```

---

### Health & Status

#### get_player_health

**Function**: Get player health

```yaml
get_player_health:
  id: "get_player_health"
  display_name: "Get Player Health"
  description: "Get player's current health and maximum health"
  usage_scenarios:
    - "How much health do I have"
    - "My health"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    health: "getHealth"
    max_health: "getMaxHealth"
  result_template: "Health: {health}/{max_health}"
```

**Usage Example**:
```
Player: How much health do I have?
AI: Health: 18.5/20.0
```

---

#### get_player_food

**Function**: Get player hunger level

```yaml
get_player_food:
  id: "get_player_food"
  display_name: "Get Player Hunger"
  description: "Get player's current hunger level (food level) and saturation"
  usage_scenarios:
    - "I'm hungry"
    - "My hunger level"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    food_level: "getFoodLevel"
    saturation: "getSaturation"
  result_template: "Food: {food_level}/20, Saturation: {saturation}"
```

---

#### get_player_oxygen

**Function**: Get player oxygen level

```yaml
get_player_oxygen:
  id: "get_player_oxygen"
  display_name: "Get Player Oxygen"
  description: "Get player's current oxygen level (underwater breathing time), in ticks"
  usage_scenarios:
    - "How long can I hold my breath"
    - "How much oxygen left"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    remaining_air: "getRemainingAir"
    maximum_air: "getMaximumAir"
  result_template: "Oxygen: {remaining_air}/{maximum_air} tick"
```

---

### Location & Movement

#### get_player_location

**Function**: Get player location coordinates

```yaml
get_player_location:
  id: "get_player_location"
  display_name: "Get Player Location"
  description: "Get player's coordinates (X, Y, Z) and current world in the game"
  usage_scenarios:
    - "Where am I"
    - "What are my coordinates"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    x: "getLocation.getX"
    y: "getLocation.getY"
    z: "getLocation.getZ"
    world: "getLocation.getWorld.getName"
  result_template: "Location: X={x}, Y={y}, Z={z}, World={world}"
```

**Usage Example**:
```
Player: Where am I?
AI: Location: X=128, Y=64, Z=-256, World=world
```

---

#### get_player_eye_location

**Function**: Get player eye location

```yaml
get_player_eye_location:
  id: "get_player_eye_location"
  display_name: "Get Player Eye Location"
  description: "Get precise coordinates of player's eyes (viewpoint starting position)"
  usage_scenarios:
    - "Where are my eyes"
    - "Where is my viewpoint"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getEyeLocation"
```

---

#### get_player_velocity

**Function**: Get player velocity vector

```yaml
get_player_velocity:
  id: "get_player_velocity"
  display_name: "Get Player Velocity"
  description: "Get player's current movement velocity vector (speed in X, Y, Z directions)"
  usage_scenarios:
    - "What is my speed"
    - "Which direction am I moving"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getVelocity"
```

---

### Game Mode & Flight

#### get_player_gamemode

**Function**: Get player game mode

```yaml
get_player_gamemode:
  id: "get_player_gamemode"
  display_name: "Get Player Game Mode"
  description: "Get player's current game mode (Survival/Creative/Adventure/Spectator)"
  usage_scenarios:
    - "What mode am I in"
    - "My game mode"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getGameMode"
```

**Usage Example**:
```
Player: What game mode am I in?
AI: Survival
```

---

#### get_player_fly_status

**Function**: Get player fly status

```yaml
get_player_fly_status:
  id: "get_player_fly_status"
  display_name: "Get Player Fly Status"
  description: "Get whether player is allowed to fly and currently flying"
  usage_scenarios:
    - "Can I fly"
    - "Am I flying now"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    allow_flight: "getAllowFlight"
    is_flying: "isFlying"
  result_template: "Allow flight: {allow_flight}, Flying: {is_flying}"
```

---

#### get_player_fly_speed

**Function**: Get player fly speed

```yaml
get_player_fly_speed:
  id: "get_player_fly_speed"
  display_name: "Get Player Fly Speed"
  description: "Get player's fly speed setting"
  usage_scenarios:
    - "What is my fly speed"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getFlySpeed"
```

---

#### get_player_walk_speed

**Function**: Get player walk speed

```yaml
get_player_walk_speed:
  id: "get_player_walk_speed"
  display_name: "Get Player Walk Speed"
  description: "Get player's walk speed setting"
  usage_scenarios:
    - "What is my movement speed"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getWalkSpeed"
```

---

### Experience & Level

#### get_player_exp

**Function**: Get player experience

```yaml
get_player_exp:
  id: "get_player_exp"
  display_name: "Get Player Experience"
  description: "Get player's current experience and level"
  usage_scenarios:
    - "How much experience do I have"
    - "What is my level"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    exp_progress: "getExp"
    level: "getLevel"
  result_template: "Level: {level}, Experience progress: {exp_progress}"
```

---

#### get_player_exp_to_level

**Function**: Get experience needed for next level

```yaml
get_player_exp_to_level:
  id: "get_player_exp_to_level"
  display_name: "Get Experience to Next Level"
  description: "Get experience points needed to reach next level from current progress"
  usage_scenarios:
    - "How much experience to level up"
    - "How much more experience do I need"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getExpToLevel"
```

---

### Other Status

#### get_player_main_hand

**Function**: Get player main hand preference

```yaml
get_player_main_hand:
  id: "get_player_main_hand"
  display_name: "Get Player Main Hand"
  description: "Get player's left/right hand preference (left-handed/right-handed)"
  usage_scenarios:
    - "Am I left-handed or right-handed"
    - "My main hand setting"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getMainHand"
```

---

#### get_player_ping

**Function**: Get player ping

```yaml
get_player_ping:
  id: "get_player_ping"
  display_name: "Get Player Ping"
  description: "Get player's network latency (ping), in milliseconds"
  usage_scenarios:
    - "What is my ping"
    - "Am I lagging"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getPing"
```

---

#### get_player_sleep_status

**Function**: Get player sleep status

```yaml
get_player_sleep_status:
  id: "get_player_sleep_status"
  display_name: "Get Player Sleep Status"
  description: "Get whether player is sleeping and sleep duration"
  usage_scenarios:
    - "Am I sleeping"
    - "My sleep status"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    is_sleeping: "isSleeping"
    sleep_ticks: "getSleepTicks"
  result_template: "Sleeping: {is_sleeping}, Sleep time: {sleep_ticks} tick"
```

---

#### get_player_last_death

**Function**: Get player last death location

```yaml
get_player_last_death:
  id: "get_player_last_death"
  display_name: "Get Player Last Death Location"
  description: "Get player's last death location coordinates"
  usage_scenarios:
    - "Where did I die last time"
    - "My death location"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getLastDeathLocation"
```

---

#### get_player_attack_cooldown

**Function**: Get player attack cooldown

```yaml
get_player_attack_cooldown:
  id: "get_player_attack_cooldown"
  display_name: "Get Player Attack Cooldown"
  description: "Get player's current attack cooldown progress (0-1, 1 means cooldown complete)"
  usage_scenarios:
    - "My attack cooldown"
    - "Can I attack now"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getAttackCooldown"
```

---

#### get_player_vehicle

**Function**: Get player vehicle status

```yaml
get_player_vehicle:
  id: "get_player_vehicle"
  display_name: "Get Player Vehicle Status"
  description: "Get whether player is in a vehicle and vehicle type"
  usage_scenarios:
    - "What am I riding"
    - "Am I in a boat"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    in_vehicle: "isInsideVehicle"
  result_template: "In vehicle: {in_vehicle}"
```

---

#### get_player_fire_status

**Function**: Get player fire status

```yaml
get_player_fire_status:
  id: "get_player_fire_status"
  display_name: "Get Player Fire Status"
  description: "Get whether player is on fire. Shows 'Not on fire' if not burning, or remaining burn time (seconds) if burning"
  usage_scenarios:
    - "Am I on fire"
    - "How long until I stop burning"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    fire_ticks: "getFireTicks"
    max_fire_ticks: "getMaxFireTicks"
  result_template: "Fire time: {fire_ticks}/{max_fire_ticks} tick"
```

---

#### get_player_freeze_status

**Function**: Get player freeze status

```yaml
get_player_freeze_status:
  id: "get_player_freeze_status"
  display_name: "Get Player Freeze Status"
  description: "Get whether player is frozen (in powder snow) and freeze level"
  usage_scenarios:
    - "Am I frozen"
    - "My freeze status"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    is_frozen: "isFrozen"
    freeze_ticks: "getFreezeTicks"
    max_freeze_ticks: "getMaxFreezeTicks"
  result_template: "Frozen: {is_frozen}, Freeze level: {freeze_ticks}/{max_freeze_ticks} tick"
```

---

#### get_player_pose

**Function**: Get player pose

```yaml
get_player_pose:
  id: "get_player_pose"
  display_name: "Get Player Pose"
  description: "Get player's current pose state (standing/crouching/swimming/sleeping, etc.)"
  usage_scenarios:
    - "What is my current pose"
    - "Am I crouching"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getPose"
```

---

#### get_player_armor

**Function**: Get player armor equipment

```yaml
get_player_armor:
  id: "get_player_armor"
  display_name: "Get Player Armor"
  description: "Get all armor currently worn by the player (helmet, chestplate, leggings, boots). The returned data is expanded per-slot into a series of fields: helmet_name/helmet_type/helmet_amount/helmet_enchantments/helmet_max_durability/helmet_remaining_durability (and identically-structured chestplate_*/leggings_*/boots_*), for subsequent steps to reference."
  usage_scenarios:
    - "What armor am I wearing"
    - "Check my armor"
    - "My armor"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getArmorContents"
```

**Usage Example**:
```
Player: What armor am I wearing?
AI: Your armor equipment:
• Helmet: Diamond Helmet
• Chestplate: Diamond Chestplate
• Leggings: Diamond Leggings
• Boots: Iron Boots
```

**Multi-step Data Passing**:
The returned data expands each slot into a group of fields: `<slot>_name`, `<slot>_type`, `<slot>_amount`, and if applicable `<slot>_enchantments`, `<slot>_max_durability`, `<slot>_remaining_durability`, where `<slot>` ∈ `helmet`/`chestplate`/`leggings`/`boots`. Subsequent steps can reference them via `{step_xxx.helmet_name}`, `{step_xxx.boots_remaining_durability}`, etc. Empty slots do not write their corresponding fields.

---

#### get_player_potion_effects

**Function**: Get player potion effects

```yaml
get_player_potion_effects:
  id: "get_player_potion_effects"
  display_name: "Get Player Potion Effects"
  description: "Get all active potion effects (including effect name, level, remaining time). The returned data contains effects field (effect list) for subsequent steps to reference."
  usage_scenarios:
    - "What potion effects do I have"
    - "My buffs"
    - "Am I poisoned"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getActivePotionEffects"
```

**Usage Example**:
```
Player: What potion effects do I have?
AI: Current potion effects:
• Speed II (remaining 2:30)
• Regeneration I (remaining 0:45)
```

**Multi-step Data Passing**:
The returned data contains `effects` field (potion effect list, each effect contains type, duration, amplifier fields). Subsequent steps can reference specific elements in the list via `{step_xxx.effects[0].type}`, etc.

---

#### get_player_target_block

**Function**: Get player target block

```yaml
get_player_target_block:
  id: "get_player_target_block"
  display_name: "Get Player Target Block"
  description: "Get the block the player is currently aiming at (max distance 100 blocks). The returned data contains block_type, x, y, z fields for subsequent steps to reference."
  usage_scenarios:
    - "What block am I looking at"
    - "The block I'm staring at"
    - "My aim position"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getTargetBlock"
```

**Usage Example**:
```
Player: What block am I looking at?
AI: You're aiming at: Diamond Ore (X=128, Y=16, Z=-256)
```

**Multi-step Data Passing**:
The returned data contains `block_type`, `x`, `y`, `z` fields. Subsequent steps can reference them via `{step_xxx.block_type}`, etc.

---

#### get_player_sneak_status

**Function**: Get player sneak status

```yaml
get_player_sneak_status:
  id: "get_player_sneak_status"
  display_name: "Get Player Sneak Status"
  description: "Check if the player is currently sneaking (Shift key)"
  usage_scenarios:
    - "Am I sneaking"
    - "Am I crouching"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "isSneaking"
```

**Usage Example**:
```
Player: Am I crouching?
AI: Yes, you are sneaking
```

---

#### get_player_sprint_status

**Function**: Get player sprint status

```yaml
get_player_sprint_status:
  id: "get_player_sprint_status"
  display_name: "Get Player Sprint Status"
  description: "Check if the player is currently sprinting (double-tap W)"
  usage_scenarios:
    - "Am I running"
    - "Am I sprinting"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "isSprinting"
```

**Usage Example**:
```
Player: Am I running?
AI: No, you are not currently sprinting
```

---

#### get_player_locale

**Function**: Get player client language

```yaml
get_player_locale:
  id: "get_player_locale"
  display_name: "Get Player Client Language"
  description: "Get the player's client language setting (e.g., zh_CN, en_US)"
  usage_scenarios:
    - "What is my game language"
    - "My client language"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getLocale"
```

**Usage Example**:
```
Player: What is my game language?
AI: Your client language: zh_CN (Simplified Chinese)
```

---

#### get_player_display_name

**Function**: Get player display name

```yaml
get_player_display_name:
  id: "get_player_display_name"
  display_name: "Get Player Display Name"
  description: "Get the player's display name (may include prefix/suffix, different from getName)"
  usage_scenarios:
    - "What is my display name"
    - "What do others see me as"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getDisplayName"
```

**Usage Example**:
```
Player: What do others see me as?
AI: Your display name: [VIP] Player123
```

---

#### get_player_bed_spawn

**Function**: Get player bed spawn location

```yaml
get_player_bed_spawn:
  id: "get_player_bed_spawn"
  display_name: "Get Player Bed Spawn"
  description: "Get the player's bed spawn location (returns null if not set). The returned data contains x, y, z, world fields for subsequent steps to reference."
  usage_scenarios:
    - "Where is my bed"
    - "My respawn point"
    - "My bed location"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getBedSpawnLocation"
```

**Usage Example**:
```
Player: Where is my bed?
AI: Your bed spawn location: X=256, Y=64, Z=128, World=world
```

**Multi-step Data Passing**:
The returned data contains `x`, `y`, `z`, `world` fields. Subsequent steps can reference them via `{step_xxx.x}`, etc.

---

#### get_player_total_exp

**Function**: Get player total experience

```yaml
get_player_total_exp:
  id: "get_player_total_exp"
  display_name: "Get Player Total Experience"
  description: "Get the total accumulated experience from the start of the game (different from current level progress). The returned data contains total_exp field for subsequent steps to reference."
  usage_scenarios:
    - "How much total experience do I have"
    - "My total experience"
    - "How much experience have I accumulated since server start"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getTotalExperience"
```

**Usage Example**:
```
Player: How much total experience do I have?
AI: Your total experience: 125,680
```

**Multi-step Data Passing**:
The returned data contains `total_exp` field. Subsequent steps can reference it via `{step_xxx.total_exp}`.

---

## 🌍 World-related APIs

### Time & Weather

#### get_world_time

**Function**: Get world time

```yaml
get_world_time:
  id: "get_world_time"
  display_name: "Get World Time"
  description: "Get current game time (in ticks) of the world, automatically formatted as HH:MM. The returned data contains time_ticks field for subsequent steps to reference or AFK task condition comparison"
  usage_scenarios:
    - "What time is it"
    - "World time"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getTime"
```

**Usage Example**:
```
Player: What time is it?
AI: World time: 06:00
```

**Multi-step Data Passing**:
The returned data contains `time_ticks` field (Long type, world ticks). Subsequent steps can reference it via `{step_xxx.time_ticks}`. AFK task condition evaluation can also use this field for numeric comparison.

---

#### get_weather

**Function**: Get weather conditions

```yaml
get_weather:
  id: "get_weather"
  display_name: "Get Weather"
  description: "Get current weather of the world (clear/rain/thunderstorm)"
  usage_scenarios:
    - "How's the weather"
    - "Will it rain"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    has_storm: "hasStorm"
    is_thundering: "isThundering"
  result_template: "Weather: {weather_desc}"
```

---

### World Information

#### get_world_info

**Function**: Get world basic information

```yaml
get_world_info:
  id: "get_world_info"
  display_name: "Get World Info"
  description: "Get current world's name, environment type (Overworld/Nether/End) and difficulty"
  usage_scenarios:
    - "What type is this world"
    - "This world's difficulty"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    name: "getName"
    environment: "getEnvironment"
    difficulty: "getDifficulty"
  result_template: "World: {name}, Type: {environment}, Difficulty: {difficulty}"
```

---

#### get_world_seed

**Function**: Get world seed

```yaml
get_world_seed:
  id: "get_world_seed"
  display_name: "Get World Seed"
  description: "Get the seed value of current world"
  usage_scenarios:
    - "What is this world's seed"
    - "World seed"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSeed"
```

---

#### get_world_spawn

**Function**: Get world spawn point

```yaml
get_world_spawn:
  id: "get_world_spawn"
  display_name: "Get World Spawn"
  description: "Get the spawn point location of current world"
  usage_scenarios:
    - "Where is the spawn"
    - "World respawn point"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSpawnLocation"
```

---

#### get_world_height_limit

**Function**: Get world height limit

```yaml
get_world_height_limit:
  id: "get_world_height_limit"
  display_name: "Get World Height Limit"
  description: "Get minimum and maximum build height of current world"
  usage_scenarios:
    - "How high can I build in this world"
    - "How low can I dig"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    min_height: "getMinHeight"
    max_height: "getMaxHeight"
  result_template: "Height range: {min_height} ~ {max_height}"
```

---

#### get_world_spawn_rules

**Function**: Get world spawn rules

```yaml
get_world_spawn_rules:
  id: "get_world_spawn_rules"
  display_name: "Get World Spawn Rules"
  description: "Get whether current world allows spawning monsters and animals"
  usage_scenarios:
    - "Do mobs spawn here"
    - "Do animals spawn here"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    allow_monsters: "getAllowMonsters"
    allow_animals: "getAllowAnimals"
  result_template: "Allow monsters: {allow_monsters}, Allow animals: {allow_animals}"
```

---

#### get_world_pvp

**Function**: Get world PVP setting

```yaml
get_world_pvp:
  id: "get_world_pvp"
  display_name: "Get World PVP"
  description: "Get whether PVP is allowed in current world"
  usage_scenarios:
    - "Can we fight here"
    - "Is PVP enabled in this world"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getPVP"
```

---

#### get_world_biome

**Function**: Get world biome

```yaml
get_world_biome:
  id: "get_world_biome"
  display_name: "Get World Biome"
  description: "Get biome type at specified coordinates (e.g., plains, desert, forest). Defaults to player's current location. The returned data contains biome field for subsequent steps to reference."
  usage_scenarios:
    - "What biome am I in"
    - "Is this a desert"
    - "Current biome"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getBiome"
```

**Usage Example**:
```
Player: What biome am I in?
AI: Current biome: Plains
```

**Multi-step Data Passing**:
The returned data contains `biome` field. Subsequent steps can reference it via `{step_xxx.biome}`.

---

#### get_world_temperature

**Function**: Get world temperature

```yaml
get_world_temperature:
  id: "get_world_temperature"
  display_name: "Get World Temperature"
  description: "Get temperature value at specified coordinates (affects mob spawning, snowfall, etc.). Defaults to player's current location. The returned data contains temperature field for subsequent steps to reference."
  usage_scenarios:
    - "What's the temperature here"
    - "Will it snow here"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getTemperature"
```

**Usage Example**:
```
Player: What's the temperature here?
AI: Current temperature: 0.8 (no snow)
```

**Multi-step Data Passing**:
The returned data contains `temperature` field. Subsequent steps can reference it via `{step_xxx.temperature}`.

---

#### get_world_humidity

**Function**: Get world humidity

```yaml
get_world_humidity:
  id: "get_world_humidity"
  display_name: "Get World Humidity"
  description: "Get humidity value at specified coordinates (affects rainfall probability). Defaults to player's current location. The returned data contains humidity field for subsequent steps to reference."
  usage_scenarios:
    - "What's the humidity here"
    - "Does it rain often here"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getHumidity"
```

**Usage Example**:
```
Player: Does it rain often here?
AI: Current humidity: 0.3 (low rainfall probability)
```

**Multi-step Data Passing**:
The returned data contains `humidity` field. Subsequent steps can reference it via `{step_xxx.humidity}`.

---

#### get_world_sea_level

**Function**: Get world sea level

```yaml
get_world_sea_level:
  id: "get_world_sea_level"
  display_name: "Get World Sea Level"
  description: "Get sea level height (Y coordinate) of current world"
  usage_scenarios:
    - "How high is the sea level"
    - "Sea level of this world"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSeaLevel"
```

**Usage Example**:
```
Player: How high is the sea level?
AI: Current world sea level: Y=63
```

---

#### get_world_player_count

**Function**: Get world player count

```yaml
get_world_player_count:
  id: "get_world_player_count"
  display_name: "Get World Player Count"
  description: "Get number of players in current world"
  usage_scenarios:
    - "How many people in this world"
    - "How many players in overworld"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    player_count: "getPlayers.size"
  result_template: "Current world player count: {player_count}"
```

**Usage Example**:
```
Player: How many players in overworld?
AI: Current world player count: 5
```

---

#### get_world_living_entities

**Function**: Get world living entities count

```yaml
get_world_living_entities:
  id: "get_world_living_entities"
  display_name: "Get World Living Entities"
  description: "Get number of all living entities (including players, monsters, animals, etc.) in current world"
  usage_scenarios:
    - "How many creatures in this world"
    - "How many living things here"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    living_entities: "getLivingEntities.size"
  result_template: "Current world living entities count: {living_entities}"
```

**Usage Example**:
```
Player: How many creatures in this world?
AI: Current world living entities count: 128
```

---

#### get_world_entity_count

**Function**: Get world entity count

```yaml
get_world_entity_count:
  id: "get_world_entity_count"
  display_name: "Get World Entity Count"
  description: "Get total number of all entities (including creatures, items, minecarts, paintings, etc.) in current world"
  usage_scenarios:
    - "How many entities in this world"
    - "Total entities here"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    entity_count: "getEntities.size"
  result_template: "Current world entity count: {entity_count}"
```

**Usage Example**:
```
Player: Total entities here?
AI: Current world entity count: 456
```

---

#### get_world_clear_weather_duration

**Function**: Get clear weather duration

```yaml
get_world_clear_weather_duration:
  id: "get_world_clear_weather_duration"
  display_name: "Get Clear Weather Duration"
  description: "Get remaining duration of current clear weather (ticks)"
  usage_scenarios:
    - "How long will clear weather last"
    - "When will it rain"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getClearWeatherDuration"
```

**Usage Example**:
```
Player: How long will clear weather last?
AI: Clear weather remaining: 12000 ticks (about 10 minutes)
```

---

#### get_world_thunder_duration

**Function**: Get thunder duration

```yaml
get_world_thunder_duration:
  id: "get_world_thunder_duration"
  display_name: "Get Thunder Duration"
  description: "Get remaining duration of current thunder weather (ticks)"
  usage_scenarios:
    - "How long will thunder last"
    - "When will thunder end"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getThunderDuration"
```

**Usage Example**:
```
Player: How long will thunder last?
AI: Thunder remaining: 6000 ticks (about 5 minutes)
```

---

#### get_world_full_time

**Function**: Get world full time

```yaml
get_world_full_time:
  id: "get_world_full_time"
  display_name: "Get World Full Time"
  description: "Get total running time of the world (ticks), unaffected by sleeping, continuously accumulates. The returned data contains full_time field for subsequent steps to reference."
  usage_scenarios:
    - "How long has this world been running"
    - "World full time"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getFullTime"
```

**Usage Example**:
```
Player: How long has this world been running?
AI: World total running time: 2,400,000 ticks (about 33 hours 20 minutes)
```

**Multi-step Data Passing**:
The returned data contains `full_time` field. Subsequent steps can reference it via `{step_xxx.full_time}`.

---

#### get_world_game_time

**Function**: Get world game time

```yaml
get_world_game_time:
  id: "get_world_game_time"
  display_name: "Get World Game Time"
  description: "Get total game time since world creation (ticks), unaffected by /time set. The returned data contains game_time field for subsequent steps to reference."
  usage_scenarios:
    - "How long has this world existed"
    - "World game time"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getGameTime"
```

**Usage Example**:
```
Player: How long has this world existed?
AI: World game time: 5,000,000 ticks (about 69 hours 26 minutes)
```

**Multi-step Data Passing**:
The returned data contains `game_time` field. Subsequent steps can reference it via `{step_xxx.game_time}`.

---

#### get_world_raids

**Function**: Get world raids

```yaml
get_world_raids:
  id: "get_world_raids"
  display_name: "Get World Raids"
  description: "Get all ongoing raids in current world. The returned data contains raids field (raid count) for subsequent steps to reference."
  usage_scenarios:
    - "Are there any raids now"
    - "Is the village under attack"
    - "Current raid events"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getRaids"
```

**Usage Example**:
```
Player: Are there any raids now?
AI: Currently 2 raid events in progress
```

**Multi-step Data Passing**:
The returned data contains `raids` field (raid event list). Subsequent steps can reference specific raids in the list via `{step_xxx.raids[0]}`, etc.

---

## 🖥️ Server-related APIs

### Player Information

#### get_online_players

**Function**: Get online player count

```yaml
get_online_players:
  id: "get_online_players"
  display_name: "Get Online Players"
  description: "Get number and list of online players on current server"
  usage_scenarios:
    - "How many people are online"
    - "How many players on server"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getOnlinePlayers"
```

**Usage Example**:
```
Player: How many people are online?
AI: Current online players: 5
```

---

#### get_max_players

**Function**: Get max players

```yaml
get_max_players:
  id: "get_max_players"
  display_name: "Get Max Players"
  description: "Get maximum number of players allowed on server"
  usage_scenarios:
    - "How many players can server hold"
    - "Max player count"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getMaxPlayers"
```

---

### Version Information

#### get_server_version

**Function**: Get server version

```yaml
get_server_version:
  id: "get_server_version"
  display_name: "Get Server Version"
  description: "Get server version information (Bukkit version and Minecraft version)"
  usage_scenarios:
    - "What is server version"
    - "Server version"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  additional_methods:
    version: "getVersion"
    bukkit_version: "getBukkitVersion"
  result_template: "Server version: {version}, Bukkit version: {bukkit_version}"
```

---

#### get_server_motd

**Function**: Get server MOTD

```yaml
get_server_motd:
  id: "get_server_motd"
  display_name: "Get Server MOTD"
  description: "Get server's MOTD (Message of the Day / server introduction)"
  usage_scenarios:
    - "What is server introduction"
    - "Server MOTD"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getMotd"
```

---

### World List

#### get_server_worlds

**Function**: Get server worlds list

```yaml
get_server_worlds:
  id: "get_server_worlds"
  display_name: "Get Server Worlds"
  description: "Get list of all loaded worlds on server"
  usage_scenarios:
    - "What worlds does server have"
    - "World list"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getWorlds"
```

---

### Server Settings

#### get_server_settings

**Function**: Get server settings

```yaml
get_server_settings:
  id: "get_server_settings"
  display_name: "Get Server Settings"
  description: "Get basic server settings (allow flight, nether, end)"
  usage_scenarios:
    - "Does server have nether"
    - "Does server have end"
    - "Does server allow flight"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  additional_methods:
    allow_flight: "getAllowFlight"
    allow_nether: "getAllowNether"
    allow_end: "getAllowEnd"
  result_template: "Allow flight: {allow_flight}, Allow nether: {allow_nether}, Allow end: {allow_end}"
```

---

## 🎯 Advanced Usage

### About "Custom Read-Only Query APIs"

> ⚠️ Important change: The old single `apis.yml` allowed users to add query APIs themselves via `method_chain`/`additional_methods`; that model has been removed.

After being split into 5 standard config-driven skills in commit b64cb0e, **these 71 read-only query APIs are no longer user-extensible via configuration**. Reasons:

- The query logic (method invocation, parameter handling, result formatting) has moved into each skill's Java class (`AbstractBukkitQuerySkill` + subclasses) and no longer reads user-written `method_chain` configs;
- Adding a new query API now requires editing the relevant Java skill class + updating that skill's yml `action_descriptions`. This is code-level development, no longer a no-code configuration task.

If you want to cover more query scenarios, the recommended approaches are:

1. **Extend the knowledge base**: Add documents under the `knowledge/` directory so the AI can leverage the existing 71 APIs + the knowledge base to answer broader questions;
2. **Develop a custom Skill**: Follow the "Skill SPI Integration Guide" to develop an independent Skill (entirely outside the bukkit read-only query skill system) implementing any custom logic.

---

### Combined Queries

AI can automatically combine multiple API calls to answer complex questions:

```
Player: How is my current status?
→ AI identifies as multi-step task:
   1. get_player_health (health)
   2. get_player_food (hunger)
   3. get_player_gamemode (game mode)
   4. get_player_location (location)
→ Comprehensive response:
   Your current status:
   • Health: 18.5/20.0
   • Food: 16/20, Saturation: 5.2
   • Game mode: Survival
   • Location: X=128, Y=64, Z=-256, World=world
```

---

## 🔒 Permission Management

### Permission Nodes

Permissions are granted at the **skill** granularity — **a single permission node covers all APIs in that skill** (no longer one node per API). There are 5 nodes, one per split skill:

| Permission Node | Skill | API Coverage |
|-----------------|-------|--------------|
| `kilacraft.api.player.info` | `player_info` | Player basic info: location, eye location, velocity, gamemode, flight, main-hand preference, ping, last death, vehicle, pose, target block, client language, display name, bed spawn, compass target, feet block, etc. |
| `kilacraft.api.player.status` | `player_status` | Player status: health, hunger, oxygen, exp/level, sleep, attack cooldown, fire, freeze, potion effects, sneak, sprint, absorption, arrows in body, no-damage ticks, fall distance, last damage, etc. |
| `kilacraft.api.player.inventory` | `player_inventoryentory` | Player inventory: main/off-hand items, inventory summary, ender chest summary, inventory usage, armor, open container, open inventory type |
| `kilacraft.api.world.info` | `world_info` | World info: time, weather, seed, spawn, height limit, spawn rules, PVP, biome, temperature, humidity, sea level, entity counts, raids, border, etc. |
| `kilacraft.api.server.info` | `server_info` | Server info: online players, max players, version, MOTD, world list, server settings |

> ⚠️ Note: Nodes like `kilacraft.api.player.health` or `kilacraft.api.player.stats` no longer exist. Health/hunger and other status APIs are all covered by `kilacraft.api.player.status`.

**Wildcard Permissions**:
```yaml
kilacraft.api.*              # All API permissions
kilacraft.api.player.*       # All player-related APIs (info + status + inventory)
kilacraft.api.world.*        # All world-related APIs
kilacraft.api.server.*       # All server-related APIs
```

### Granting Permissions

Use LuckPerms plugin to grant permissions:

```bash
# Grant a skill's permission (e.g. player status skill, covers health/hunger and all status APIs)
/lp user <player> permission set kilacraft.api.player.status true

# Grant all player-related API permissions
/lp user <player> permission set kilacraft.api.player.* true

# Grant all API permissions
/lp user <player> permission set kilacraft.api.* true
```

---

## ⚙️ Performance Optimization Tips

### 1. Read-Only Operations

All Bukkit APIs are **read-only operations** that don't modify game state:
- ✅ Safe: Won't accidentally change player data
- ✅ Isolated: API execution failures don't affect other features
- ✅ Concurrent: Can be safely executed in async threads

### 2. Permission Checks

Permissions are checked at the skill granularity: before executing an API, the permission node of the skill that API belongs to is re-checked, ensuring only authorized players can access sensitive information.

### 3. Direct Invocation

Each skill's Java class (`AbstractBukkitQuerySkill` + subclasses) calls Bukkit APIs directly. The old reflection-based executor (`BukkitAPIExecutor`) was removed together with `apis.yml`, resulting in a shorter call path that is easier for the JVM to inline and optimize.

---

## 🐛 Troubleshooting

### API Returns null

**Problem**: Some API calls return `null` values

**Causes**:
- Player offline
- World doesn't exist
- Target object doesn't exist (e.g. querying `get_player_open_container` when no container is open)

**Solutions**:
- Confirm the target player is online and the world is loaded
- Check console for error logs
- If the issue persists, it may be an internal scheduling failure in the skill's Java class — please report it

---

### Insufficient Permissions

**Problem**: Player receives "You don't have permission to perform this operation" error

**Cause**: Player doesn't have the corresponding skill's permission node (permissions are granted per skill)

**Solution**:
```bash
# e.g. a player querying health/hunger and other status APIs needs the player.status skill permission
/lp user <player> permission set kilacraft.api.player.status true
```

---

### API Not Registered

**Problem**: AI cannot recognize an API

**Causes**:
- The corresponding `Bukkit*Skill.yml` skill config file has a format error or is missing (`BukkitPlayerInfoSkill.yml` / `BukkitPlayerStatusSkill.yml` / `BukkitPlayerInventorySkill.yml` / `BukkitWorldSkill.yml` / `BukkitServerSkill.yml`)
- The skill wasn't loaded correctly
- Configuration not reloaded

**Solutions**:
1. Check the YAML format of the relevant `Bukkit*.yml` skill file
2. Confirm all 5 skill files exist under the `skills/bukkit/` directory
3. Execute `/kila reload` to reload configuration
4. Check console for error logs

> Note: The old `apis.yml` has been removed and is no longer read. Do not create or edit that file.

---

## 📚 Related Documentation

- [Server Owner Guide](./Server%20Owner%20Guide) - Complete configuration and usage instructions
- [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide) - How to extend custom skills
- [Changelog](./Changelog) - Version history and changes


