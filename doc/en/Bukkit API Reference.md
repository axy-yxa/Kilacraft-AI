# Kilacraft-AI - Bukkit API Reference Manual

> **Version**: v1.4.3  
> **Description**: This document provides detailed explanations, configuration examples, and usage scenarios for all built-in Bukkit APIs

---

## 📊 Bukkit API Quick Reference

### 👤 Player APIs (31)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_player_hand_item` | Get Player Main Hand Item | Get main hand item info | method_chain | `kilacraft.api.player.inventory` |
| `get_player_offhand_item` | Get Player Off Hand Item | Get off hand item info | method_chain | `kilacraft.api.player.inventory` |
| `get_player_armor_contents` | Get Player Armor | Get 4 armor slot items | method_chain | `kilacraft.api.player.inventory` |
| `get_player_inventory` | Get Player Inventory | Get all inventory items | method_chain | `kilacraft.api.player.inventory` |
| `get_player_health` | Get Player Health | Get current/max health | method_chain | `kilacraft.api.player.status` |
| `get_player_hunger` | Get Player Hunger | Get current/max hunger | method_chain | `kilacraft.api.player.status` |
| `get_player_level` | Get Player Level | Get current level/exp | method_chain | `kilacraft.api.player.status` |
| `get_player_gamemode` | Get Player Gamemode | Get current gamemode | method_chain | `kilacraft.api.player.status` |
| `get_player_location` | Get Player Location | Get coordinates/world/direction | method_chain | `kilacraft.api.player.location` |
| `get_player_velocity` | Get Player Velocity | Get movement speed/direction | method_chain | `kilacraft.api.player.location` |
| `get_player_fly_status` | Get Player Fly Status | Get fly mode/allow fly/speed | method_chain | `kilacraft.api.player.status` |
| `get_player_game_time` | Get Player Game Time | Get player-specific game time | method_chain | `kilacraft.api.player.info` |
| `get_player_statistics` | Get Player Statistics | Get custom statistics | method_chain | `kilacraft.api.player.stats` |
| `get_player_max_health` | Get Player Max Health | Get maximum health value | method_chain | `kilacraft.api.player.status` |
| `get_player_absorption` | Get Player Absorption | Get absorption hearts | method_chain | `kilacraft.api.player.status` |
| `get_player_saturation` | Get Player Saturation | Get saturation value | method_chain | `kilacraft.api.player.status` |
| `get_player_exhaustion` | Get Player Exhaustion | Get exhaustion value | method_chain | `kilacraft.api.player.status` |
| `get_player_food_level` | Get Player Food Level | Get food level | method_chain | `kilacraft.api.player.status` |
| `get_player_total_experience` | Get Player Total Exp | Get total experience | method_chain | `kilacraft.api.player.status` |
| `get_player_exp` | Get Player Exp Progress | Get exp bar progress | method_chain | `kilacraft.api.player.status` |
| `get_player_exp_to_level` | Get Exp to Next Level | Get exp needed for next level | method_chain | `kilacraft.api.player.status` |
| `get_player_display_name` | Get Player Display Name | Get display name | method_chain | `kilacraft.api.player.info` |
| `get_player_ip` | Get Player IP | Get IP address | method_chain | `kilacraft.api.player.info` |
| `get_player_port` | Get Player Port | Get port number | method_chain | `kilacraft.api.player.info` |
| `get_player_address` | Get Player Address | Get full address | method_chain | `kilacraft.api.player.info` |
| `get_player_unique_id` | Get Player UUID | Get unique identifier | method_chain | `kilacraft.api.player.info` |
| `get_player_first_played` | Get Player First Join | Get first join time | method_chain | `kilacraft.api.player.info` |
| `get_player_last_played` | Get Player Last Seen | Get last seen time | method_chain | `kilacraft.api.player.info` |
| `get_player_is_banned` | Get Player Ban Status | Check if banned | method_chain | `kilacraft.api.player.info` |
| `get_player_is_op` | Get Player OP Status | Check if OP | method_chain | `kilacraft.api.player.info` |
| `get_player_is_online` | Get Player Online Status | Check if online | method_chain | `kilacraft.api.player.info` |

### 🌍 World APIs (21)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_world_time` | Get World Time | Get game time (HH:MM) | method_chain | `kilacraft.api.world.info` |
| `get_world_full_time` | Get World Full Time | Get total ticks | method_chain | `kilacraft.api.world.info` |
| `get_world_day` | Get World Day | Get day count | method_chain | `kilacraft.api.world.info` |
| `get_world_difficulty` | Get World Difficulty | Get difficulty level | method_chain | `kilacraft.api.world.info` |
| `get_world_environment` | Get World Environment | Get environment type | method_chain | `kilacraft.api.world.info` |
| `get_world_weather` | Get World Weather | Get weather status | method_chain | `kilacraft.api.world.info` |
| `get_world_storm_duration` | Get Storm Duration | Get rain remaining time | method_chain | `kilacraft.api.world.info` |
| `get_world_thunder_duration` | Get Thunder Duration | Get thunder remaining time | method_chain | `kilacraft.api.world.info` |
| `get_world_spawn_location` | Get World Spawn | Get spawn point coordinates | method_chain | `kilacraft.api.world.info` |
| `get_world_max_height` | Get World Max Height | Get maximum height | method_chain | `kilacraft.api.world.info` |
| `get_world_min_height` | Get World Min Height | Get minimum height | method_chain | `kilacraft.api.world.info` |
| `get_world_chunk_count` | Get Loaded Chunks | Get loaded chunk count | method_chain | `kilacraft.api.world.info` |
| `get_world_entity_count` | Get World Entities | Get entity count | method_chain | `kilacraft.api.world.info` |
| `get_world_living_entity_count` | Get Living Entities | Get living entity count | method_chain | `kilacraft.api.world.info` |
| `get_world_player_count` | Get World Players | Get player count in world | method_chain | `kilacraft.api.world.info` |
| `get_world_name` | Get World Name | Get world name | method_chain | `kilacraft.api.world.info` |
| `get_world_seed` | Get World Seed | Get world seed | method_chain | `kilacraft.api.world.info` |
| `get_world_is_pvp_allowed` | Get PvP Status | Check if PvP allowed | method_chain | `kilacraft.api.world.info` |
| `get_world_is_hardcore` | Get Hardcore Status | Check if hardcore mode | method_chain | `kilacraft.api.world.info` |
| `get_world_is_natural_regeneration` | Get Natural Regen | Check natural regeneration | method_chain | `kilacraft.api.world.info` |
| `get_world_ultra_warm` | Get Ultra Warm | Check if ultra warm | method_chain | `kilacraft.api.world.info` |

### 🖥️ Server APIs (6)

| API ID | Display Name | Function | Call Mode | Permission |
|--------|-------------|----------|-----------|------------|
| `get_online_players` | Get Online Players | Get online player count and list | method_chain | `kilacraft.api.server.info` |
| `get_max_players` | Get Max Players | Get max player limit | method_chain | `kilacraft.api.server.info` |
| `get_server_version` | Get Server Version | Get server version | method_chain | `kilacraft.api.server.info` |
| `get_server_motd` | Get Server MOTD | Get server description | method_chain | `kilacraft.api.server.info` |
| `get_server_worlds` | Get All Worlds | Get all world names | method_chain | `kilacraft.api.server.info` |
| `get_server_name` | Get Server Name | Get server name | method_chain | `kilacraft.api.server.info` |

**Statistics**: Total **58 APIs** (Player 31 + World 21 + Server 6)

---

## 📖 Overview

Kilacraft-AI includes **58 built-in Bukkit APIs**, allowing AI to access various data from the Minecraft server. These APIs are defined through YAML configuration and can be used without writing code.

### Core Features

- ✅ **Data-driven configuration**: Define APIs in `apis.yml`, supports hot reload
- ✅ **Permission control**: Each API can have independent access permissions
- ✅ **Dual-mode execution**: Supports method_chain (chained calls) and additional_methods (parallel calls)
- ✅ **Smart formatting**: Automatically handles complex types (Location, ItemStack, GameMode, ItemStack[], Set<PotionEffect>, etc.)
- ✅ **Error isolation**: API execution failures don't affect other features

### Configuration File Location

```
plugins/Kilacraft-AI/skills/bukkit/apis.yml
```

---

## 🔧 API Configuration Structure

### Basic Structure

```yaml
player:  # Category (player/world/server/paper_player/paper_world/paper_server)
  api_id:  # API unique identifier
    id: "api_id"  # API ID (consistent with key name)
    display_name: "API Display Name"
    description: "API function description, sent to LLM"
    usage_scenarios:  # Usage scenario examples (optional)
      - "When user asks 'what am I holding'"
      - "Check my items"
    target_type: "Player"  # Target type: Player/World/Server
    required_permission: "kilacraft.api.player.inventory"  # Required permission (optional)
    
    # Choose one of the following two configurations:
    
    # Mode 1: method_chain (chained calls, returns complex objects)
    method_chain:
      - "getInventory"
      - "getItemInMainHand"
    
    # Mode 2: additional_methods (parallel calls to multiple methods)
    additional_methods:
      health: "getHealth"
      max_health: "getMaxHealth"
    result_template: "Health: {health}/{max_health}"  # Result template (only for additional_methods)
```

### Important Rules

#### 1. method_chain vs additional_methods

| Feature | method_chain | additional_methods |
|---------|--------------|-------------------|
| **Purpose** | Chained calls (relay), returns complex objects | Parallel calls to multiple independent methods, returns simple values |
| **Return Value** | ItemStack, Location, GameMode, etc. | Map<String, Object> |
| **Formatting** | Special handling by code (formatItemStack, etc.) | Uses result_template for replacement |
| **Typical Use** | Get items, locations, game modes | Get health, coordinates, experience, etc. |

**Example Comparison**:

```yaml
# ✅ method_chain: Get main hand item (returns ItemStack)
get_player_hand_item:
  target_type: "Player"
  method_chain:
    - "getInventory"
    - "getItemInMainHand"
  # No need for result_template, ItemStack is automatically formatted by code

# ✅ additional_methods: Get health (returns multiple values)
get_player_health:
  target_type: "Player"
  additional_methods:
    health: "getHealth"
    max_health: "getMaxHealth"
  result_template: "Health: {health}/{max_health}"
```

#### 2. additional_methods Supports Simple Chained Calls

In `additional_methods`, method names can use dots for two-level chained calls:

```yaml
get_player_location:
  target_type: "Player"
  additional_methods:
    x: "getLocation.getX"        # player.getLocation().getX()
    y: "getLocation.getY"        # player.getLocation().getY()
    z: "getLocation.getZ"        # player.getLocation().getZ()
    world: "getLocation.getWorld.getName"  # player.getLocation().getWorld().getName()
  result_template: "Location: X={x}, Y={y}, Z={z}, World={world}"
```

**Limitations**:
- ✅ Supports up to 2 levels of chaining (e.g., `"a.b"`)
- ❌ Does not support 3+ levels of chaining (e.g., `"a.b.c"`)
- ❌ Does not support methods with parameters

#### 3. result_template Placeholder Rules

Placeholders `{key}` in `result_template` must exactly match keys in `additional_methods`:

```yaml
# ✅ Correct
additional_methods:
  health: "getHealth"
result_template: "Health: {health}"

# ❌ Incorrect: Case mismatch
result_template: "Health: {Health}"
```

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
  description: "Get all armor currently worn by the player (helmet, chestplate, leggings, boots). The returned data contains helmet, chestplate, leggings, boots fields for subsequent steps to reference."
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
The returned data contains `helmet`, `chestplate`, `leggings`, `boots` fields. Subsequent steps can reference them via `{step_xxx.helmet}`, etc.

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
The returned data contains `effects` field (effect list). Subsequent steps can reference it via `{step_xxx.effects}`.

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
  description: "Get current game time (in ticks) of the world, automatically formatted as HH:MM"
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
The returned data contains `raids` field (raid list). Subsequent steps can reference it via `{step_xxx.raids}`.

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

### Custom APIs

You can add your own Bukkit API calls. For example, add an API to get player kill count:

```yaml
player:
  get_player_kills:
    id: "get_player_kills"
    display_name: "Get Player Kills"
    description: "Query player's total kill count"
    usage_scenarios:
      - "How many players have I killed"
      - "My kill count"
    target_type: "Player"
    required_permission: "kilacraft.api.player.stats"
    method_chain:
      - "getStatistic"  # Note: This method requires parameters, not supported in current version
```

**Note**: Current version only supports parameterless method calls. Methods with parameters (like `getStatistic(Statistic.PLAYER_KILLS)`) cannot be used yet.

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

All Bukkit APIs have independent permission nodes in format: `kilacraft.api.<category>.<type>`

**Categories**:
- `player.info` - Player basic info (location, game mode, ping, etc.)
- `player.status` - Player status info (health, hunger, experience, etc.)
- `player.inventory` - Player inventory info
- `world.info` - World information
- `server.info` - Server information

**Wildcard Permissions**:
```yaml
kilacraft.api.*              # All API permissions
kilacraft.api.player.*       # All player-related APIs
kilacraft.api.world.*        # All world-related APIs
kilacraft.api.server.*       # All server-related APIs
```

### Granting Permissions

Use LuckPerms plugin to grant permissions:

```bash
# Grant single API permission
/lp user <player> permission set kilacraft.api.player.health true

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

Each API has independent permission checks to ensure only authorized players can access sensitive information.

### 3. Reflection Caching

BukkitAPIExecutor uses reflection to call methods, and JVM automatically optimizes frequently called methods.

---

## 🐛 Troubleshooting

### API Returns null

**Problem**: Some API calls return `null` values

**Causes**:
- Player offline
- World doesn't exist
- Method call failed

**Solutions**:
- Check if `target_type` in API configuration is correct
- Confirm methods in `method_chain` or `additional_methods` exist
- Check console for error logs

---

### Insufficient Permissions

**Problem**: Player receives "You don't have permission to perform this operation" error

**Cause**: Player doesn't have corresponding permission node

**Solution**:
```bash
/lp user <player> permission set kilacraft.api.player.health true
```

---

### API Not Registered

**Problem**: AI cannot recognize an API

**Causes**:
- `apis.yml` file format error
- Configuration not reloaded

**Solutions**:
1. Check if YAML format is correct
2. Execute `/kilacraft reload` to reload configuration
3. Check console for error logs

---

### Both method_chain and additional_methods Configured

**Problem**: API execution fails with message "API must configure method_chain or additional_methods"

**Cause**: Only one of the two configurations can be used, not both simultaneously

**Solution**:
- If returning complex objects (ItemStack, Location, etc.) → Use `method_chain`
- If returning multiple simple values → Use `additional_methods` + `result_template`

---

## 📚 Related Documentation

- [Server Owner Guide](./Server%20Owner%20Guide) - Complete configuration and usage instructions
- [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide) - How to extend custom skills
- [Changelog](./Changelog) - Version history and changes

---

> **Last Updated**: 2026-04-08  
> **Plugin Version**: 1.4.3+  
> **Total APIs**: 58 (Player 31 + World 21 + Server 6)
