# Kilacraft-AI - Built-in Skills and Events Capability List

> **Last Updated**: 2026-04-23  
> **Description**: This document summarizes all built-in Skill actions and supported Bukkit Event listeners of Kilacraft-AI, helping server administrators and plugin developers quickly understand the plugin's capabilities, integrated third-party plugins, and security risks.

---

## 📋 Table of Contents

1. [Skill Capability List](#skill-capability-list)
2. [Bukkit Event Listener List](#bukkit-event-listener-list)
3. [Third-Party Plugin Dependencies](#third-party-plugin-dependencies)
4. [Capability Boundaries](#capability-boundaries)

---

## Skill Capability List

### 1. AFKTaskSkill - AFK Task System

**Capability Type**: Event Listener + Delayed Callback Task Chain
**Dependency Plugin**: Pure Bukkit Native API
**File Location**: `skills/afktask/AFKTaskSkill.yml`
**Implementation Class**: `AFKTaskSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `create_task` | Create a new AFK task | `task_type`, `target_player` | `callback` |
| `cancel_task` | Cancel the player's current AFK task | None | None |
| `query_task` | Query the player's current AFK task status | None | None |

#### Supported 11 Event Listener Types

| Task Type | Monitoring Target | Level | Dependent Event |
|----------|-----------------|-------|-----------------|
| `PLAYER_ONLINE_WATCH` | Player online | S-level | PlayerJoinEvent |
| `PLAYER_OFFLINE_WATCH` | Player offline | S-level | PlayerQuitEvent |
| `PLAYER_DEATH_WATCH` | Player death | S-level | PlayerDeathEvent |
| `PLAYER_TELEPORT_WATCH` | Player teleport | S-level | PlayerTeleportEvent |
| `PLAYER_LEVEL_CHANGE_WATCH` | Player level change | S-level | PlayerLevelChangeEvent |
| `PLAYER_CHANGED_WORLD_WATCH` | Player world change | S-level | PlayerChangedWorldEvent |
| `WEATHER_CHANGE_WATCH` | Weather change | S-level | WeatherChangeEvent |
| `PLAYER_BED_ENTER_WATCH` | Player enters bed | A-level | PlayerBedEnterEvent |
| `PLAYER_BED_LEAVE_WATCH` | Player leaves bed | A-level | PlayerBedLeaveEvent |
| `PLAYER_RESPAWN_WATCH` | Player respawn | A-level | PlayerRespawnEvent |
| `PLAYER_ITEM_BREAK_WATCH` | Player item break | A-level | PlayerItemBreakEvent |
| `CUSTOM` | Custom condition polling | A-level | Any Skill |

#### Core Features

- ✅ **Dual Mode Support**: Notification-only mode (fast response) vs. Callback mode (multi-step task chain)
- ✅ **Placeholder System**: Rich context placeholders provided when events trigger (`{triggered_player}`, `{from_world}`, `{to_x}`, etc.)
- ✅ **Delayed Feedback Optimization**: Inject empty conversation history during callback execution to avoid stale context noise
- ✅ **One Task Per Player**: Each player can only have one AFK task at a time, automatic conflict detection
- ✅ **Automatic Resource Cleanup**: Automatic cleanup of listeners and task indexes when tasks complete, are manually canceled, or players go offline

#### Typical Usage Scenario

```
Player: Watch Steve come online, after he comes online help me query his location
→ AFKTaskSkill (create PLAYER_ONLINE_WATCH task)
    → PlayerJoinEvent triggers
      → Execute callback task (includes get_player_location)
        → LLM secondary analysis
          → Notify player location information
```

---

### 2. GenericBukkitAPI - Generic Bukkit API Executor

**Capability Type**: Native API Data Query
**Dependency Plugin**: Pure Bukkit Native API
**File Location**: `skills/bukkit/apis.yml`
**Implementation Class**: `GenericBukkitAPISkill.java`

#### Supported API Actions (60+)

**Player Related** (27)

| API Action | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| Inventory | | |
| `get_player_hand_item` | Get player main hand item | `item_name`, `item_amount` |
| `get_player_offhand_item` | Get player off hand item | `item_name`, `item_amount` |
| Health & Status | | |
| `get_player_health` | Get player health | `health`, `max_health` |
| `get_player_food` | Get player hunger | `food_level`, `saturation` |
| `get_player_oxygen` | Get player oxygen | `remaining_air`, `maximum_air` |
| Location & Movement | | |
| `get_player_location` | Get player location | `x`, `y`, `z`, `yaw`, `pitch`, `world` |
| `get_player_eye_location` | Get player eye location | `x`, `y`, `z` |
| `get_player_velocity` | Get player velocity vector | - |
| Game Mode & Flight | | |
| `get_player_gamemode` | Get player game mode | - |
| `get_player_fly_status` | Get player flight status | `allow_flight`, `is_flying` |
| `get_player_fly_speed` | Get player flight speed | - |
| `get_player_walk_speed` | Get player walking speed | - |
| Experience & Level | | |
| `get_player_exp` | Get player experience | `level`, `exp_progress` |
| `get_player_exp_to_level` | Get exp required for next level | - |
| Other Status | | |
| `get_player_main_hand` | Get player main hand preference | - |
| `get_player_ping` | Get player network latency | - |
| `get_player_sleep_status` | Get player sleep status | `is_sleeping`, `sleep_ticks` |
| `get_player_last_death` | Get player last death location | - |
| `get_player_attack_cooldown` | Get player attack cooldown | - |
| `get_player_vehicle` | Get player ride status | `in_vehicle` |
| `get_player_fire_status` | Get player on fire status | `fire_ticks`, `max_fire_ticks` |
| `get_player_freeze_status` | Get player freeze status | `is_frozen`, `freeze_ticks`, `max_freeze_ticks` |
| `get_player_pose` | Get player pose | - |
| Equipment & Effects | | |
| `get_player_armor` | Get player armor equipment | `helmet`, `chestplate`, `leggings`, `boots` |
| `get_player_potion_effects` | Get player potion effects | `effects` |
| `get_player_target_block` | Get player target block | `block_type`, `x`, `y`, `z` |
| Action Status | | |
| `get_player_sneak_status` | Get player sneak status | - |
| `get_player_sprint_status` | Get player sprint status | - |
| Client Info | | |
| `get_player_locale` | Get player client language | - |
| `get_player_display_name` | Get player display name | - |
| Respawn Point | | |
| `get_player_bed_spawn` | Get player bed spawn point | `x`, `y`, `z`, `world` |
| Experience Details | | |
| `get_player_total_exp` | Get player total experience | `total_exp` |

**World Related** (20)

| API Action | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| Time & Weather | | |
| `get_world_time` | Get world time | `time_ticks` |
| `get_weather` | Get weather condition | `weather_desc` |
| World Info | | |
| `get_world_info` | Get world basic information | `name`, `environment`, `difficulty` |
| `get_world_seed` | Get world seed | - |
| `get_world_spawn` | Get world spawn point | - |
| `get_world_height_limit` | Get world height limit | `min_height`, `max_height` |
| Mob Spawn Rules | | |
| `get_world_spawn_rules` | Get world mob spawn rules | `allow_monsters`, `allow_animals` |
| `get_world_pvp` | Get world PVP setting | - |
| Biome & Environment | | |
| `get_world_biome` | Get world biome | `biome` |
| `get_world_temperature` | Get world temperature | `temperature` |
| `get_world_humidity` | Get world humidity | `humidity` |
| Entity Statistics | | |
| `get_world_player_count` | Get world player count | `player_count` |
| `get_world_living_entities` | Get world living entities count | `living_entities` |
| `get_world_entity_count` | Get world total entity count | `entity_count` |
| World Attributes | | |
| `get_world_sea_level` | Get world sea level | - |
| Weather Duration | | |
| `get_world_clear_weather_duration` | Get clear weather remaining time | - |
| `get_world_thunder_duration` | Get thunder remaining time | - |
| Time Details | | |
| `get_world_full_time` | Get world total time | `full_time` |
| `get_world_game_time` | Get world game time | `game_time` |
| Raid Events | | |
| `get_world_raids` | Get world raid events | `raids` |

**Server Related** (7)

| API Action | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| Player Info | | |
| `get_online_players` | Get online player count and list | - |
| `get_max_players` | Get max player count | - |
| Version Info | | |
| `get_server_version` | Get server version | `version`, `bukkit_version` |
| `get_server_motd` | Get server MOTD | - |
| World List | | |
| `get_server_worlds` | Get server world list | - |
| Server Settings | | |
| `get_server_settings` | Get server settings | `allow_flight`, `allow_nether`, `allow_end` |

#### Core Features

- ✅ **Read-Only Operations**: All APIs are data queries, no game state changes
- ✅ **Chain Call Support**: Supports up to 2-layer chain calls (e.g., `getLocation.getX`)
- ✅ **Parallel Calls**: `additional_methods` supports getting multiple independent attributes simultaneously
- ✅ **Result Templating**: `result_template` supports placeholder replacement

---

### 3. CMISkill - CMI Plugin Integration

**Capability Type**: Teleportation + Player Info Query
**Dependency Plugin**: CMI (v9.8.6.4+)
**File Location**: `skills/cmi/CMISkill.yml`
**Implementation Class**: `CMISkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `query_homes` | Query player's own home list | None | None |
| `query_warps` | Query server public warp list | None | None |
| `query_player_info` | Query specified player's CMI enhanced info | None | `target_player` |
| `query_kits` | Query server available kit list | None | None |
| `query_online_players` | Query online player list (enhanced) | None | None |
| `teleport_home` | Teleport to player's own home | `home_name` | None |
| `teleport_to_warp` | Teleport to public warp | `warp_name` | None |
| `send_tp_request` | Send teleport request to target player (TPA) | `target_player` | None |

#### Core Features

- ✅ **AFK Status Recognition**: Automatically identify AFK status when querying player info
- ✅ **Vanish Status Recognition**: Automatically identify vanish status when querying online players
- ✅ **Pre-Query Constraint**: `teleport_home` must first call `query_homes`, `teleport_to_warp` must first call `query_warps`
- ✅ **Array Index Placeholder**: Use `{step_X.homes[0].home_name}` in multi-step tasks to reference query results
- ✅ **Enhanced Player Info**: Detailed data including playtime, flight status, game mode, etc.

#### Typical Usage Scenario

```
Player: Help me go home
→ CMISkill (create multi-step task)
    1. query_homes (query home list)
    2. teleport_home (teleport home, use {step_1.homes[0].home_name})
```

---

### 4. CommandSkill - Command Execution

**Capability Type**: Server Command Execution (Player Identity)  
**Dependency Plugin**: Pure Bukkit Native API  
**File Location**: `skills/command/CommandSkill.yml`  
**Implementation Class**: `CommandSkill.java`

#### Supported Actions

| Action | Description | Required Parameters |
|--------|-------------|---------------------|
| `execute_command` | Execute one server command as player | `command` |

#### Core Features

- ✅ **Permission Boundary**: AI executes commands as player, constrained by server permission system
- ✅ **Fallback Mechanism**: When dedicated Skills cannot cover user needs, try executing commands
- ✅ **Security Mechanism**: Does not bypass any server security mechanisms (permissions, cooldowns, safe areas)

---

### 5. BukkitFXSkill - Sound & Particle Effects

**Capability Type**: Client-side Effect Playback (Only Caller Visible/Audible)  
**Dependency Plugin**: Pure Bukkit Native API  
**File Location**: `skills/bukkit/BukkitFXSkill.yml`  
**Implementation Class**: `BukkitFXSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `play_sound` | Play sound (only caller hears) | `sound` | `volume`, `pitch` |
| `spawn_particle` | Show particle effect (only caller sees) | `particle` | `count`, `offset_x`, `offset_y`, `offset_z` |

#### Sound Categories Examples

| Category | Example | Applicable Scenario |
|----------|---------|---------------------|
| Ambient Sound | `AMBIENT_CAVE` | Cave exploration atmosphere |
| Block Sound | `BLOCK_ANVIL_BREAK` | Building/destruction prompt |
| Entity Sound | `ENTITY_PLAYER_LEVELUP` | Level up celebration, task completion |
| Item Sound | `ITEM_ARMOR_EQUIP_DIAMOND` | Equipment prompt |

#### Particle Categories Examples

| Category | Example | Applicable Scenario |
|----------|---------|---------------------|
| Celebration | `HEART`, `VILLAGER_HAPPY` | Task completion, celebration |
| Warning | `VILLAGER_ANGRY`, `DAMAGE_INDICATOR` | Danger reminder |
| Combat | `CRIT`, `SWEEP_ATTACK` | Combat feedback |
| Magic | `ENCHANTMENT_TABLE`, `SPELL` | Enchantment/potion effect |
| Nature | `FLAME`, `SMOKE_NORMAL` | Environment atmosphere |
| Explosion | `EXPLOSION_NORMAL`, `EXPLOSION_LARGE` | Explosion effect |
| Portal | `PORTAL`, `END_ROD` | Teleport prompt |

#### Core Features

- ✅ **Security Isolation**: All effects only apply to `context.getPlayer()`, no impact on other players
- ✅ **Parameter Range Limits**: Volume 0.0-1.0, pitch 0.5-2.0, particle count 1-100
- ✅ **YML Configuration Driven**: Descriptions and hints defined via config files, supports hot reload
- ✅ **Knowledge Base Enhanced**: Supports extending supported sound/particle lists through knowledge base files
- ✅ **Main Thread Execution**: Automatically detects thread environment, ensures effects play on main thread

#### Typical Usage Scenario

```
Player: Play a level up sound to celebrate
→ BukkitFXSkill (play_sound)
    sound: ENTITY_PLAYER_LEVELUP
    volume: 1.0, pitch: 1.0

Player: Show some heart particles
→ BukkitFXSkill (spawn_particle)
    particle: HEART
    count: 10, offset: 0.5/0.5/0.5
```

---

### 6. BukkitStatsSkill - Vanilla Statistics Query

**Capability Type**: Player Vanilla Cumulative Statistics Query (Career Records)  
**Dependency Plugin**: Pure Bukkit Native API  
**File Location**: `skills/bukkit/BukkitStatsSkill.yml`  
**Implementation Class**: `BukkitStatsSkill.java`  
**Knowledge Base**: `knowledge/statistics.md` (BM25 semantic retrieval, 80+ statistic enums)

#### Supported Actions

| Action | Description | Required Params | Optional Params | Returned Data Fields |
|--------|-------------|-----------------|-----------------|---------------------|
| `query_statistic` | Query specified statistic value | `statistic` | `entity_type`, `material` | `statistic`, `value`, `statistic_type` |

#### Four Statistic Types

| Type | Description | Example Statistics | Extra Param |
|------|-------------|-------------------|-------------|
| UNTYPED | No parameter, direct query | DEATHS, PLAYER_KILLS, JUMP | None |
| ITEM | Requires item parameter | CRAFT_ITEM, USE_ITEM, BREAK_ITEM | `material` |
| BLOCK | Requires block parameter | MINE_BLOCK | `material` |
| ENTITY | Requires entity parameter | KILL_ENTITY, ENTITY_KILLED_BY | `entity_type` |

#### Typical Statistics

| Statistic | Type | Description |
|-----------|------|-------------|
| DEATHS | UNTYPED | Total death count |
| PLAYER_KILLS | UNTYPED | Total player kills |
| MOB_KILLS | UNTYPED | Total mob kills |
| PLAY_ONE_MINUTE | UNTYPED | Total game time (ticks) |
| TIME_SINCE_DEATH | UNTYPED | Time since last death (ticks) |
| WALK_ONE_CM | UNTYPED | Total walking distance (cm) |
| JUMP | UNTYPED | Total jump count |
| KILL_ENTITY | ENTITY | Kills of specified entity |
| ENTITY_KILLED_BY | ENTITY | Times killed by specified entity |
| MINE_BLOCK | BLOCK | Times mined specified block |
| CRAFT_ITEM | ITEM | Times crafted specified item |

#### Smart Formatting

- **Distance Stats**: Auto-converts cm → meters/km (e.g., 1234567 cm → 12.3 km)
- **Time Stats**: Auto-converts ticks → readable time (e.g., 72000 ticks → 1 hour)
- **EntityType Translation**: 30+ common entity Chinese names
- **Material Translation**: Reuses ItemTranslator

#### Core Features

- ✅ **Knowledge Base Driven**: Statistic enums matched via BM25 retrieval, LLM auto-gets correct enum name
- ✅ **Multi-step Data Passing**: Returns value field, supports AFK CUSTOM polling condition monitoring
- ✅ **Parameter Validation**: Auto-validates Material/EntityType legality
- ✅ **Cumulative Stats Boundary**: Clear distinction from current state (HP/hunger/level) queries

#### Typical Use Cases

```
Player: How many times have I died in total
→ BukkitStatsSkill (query_statistic)
    statistic: DEATHS
    Returns: Total death count: 42

Player: How many zombies have I killed
→ BukkitStatsSkill (query_statistic)
    statistic: KILL_ENTITY
    entity_type: ZOMBIE
    Returns: Entity kills (Zombie): 15

Player: How far have I walked
→ BukkitStatsSkill (query_statistic)
    statistic: WALK_ONE_CM
    Returns: Walking distance: 12.5 km

Player: Watch my elytra flight distance, celebrate with fireworks when it exceeds 100,000 blocks
→ Multi-step task:
    Step 1: BukkitStatsSkill (query_statistic)
            statistic: AVIATE_ONE_CM
    Step 2: CUSTOM AFK task
            condition: "{step_1.value} > 10000000"
            callback: BukkitFXSkill (spawn_particle)
                    particle: FIREWORKS_SPARK, count: 50
```

---

### 7. MarketQuerySkill - GlobalMarketPlus Plugin Integration

**Capability Type**: Market Information Query
**Dependency Plugin**: GlobalMarketPlus (v1.3.8.0+)
**File Location**: `skills/globalmarketplus/MarketQuerySkill.yml`
**Implementation Class**: `MarketQuerySkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `query_balance` | Query player account balance | None | None |
| `query_price` | Query market price of specified item | `item` | None |
| `query_items` | Query items listed on market | None | None |
| `query_availability` | Query if specified item is for sale | `item` | None |
| `query_my_items` | Query player's own listed items | None | None |
| `query_mailbox` | Query player mailbox unclaimed mail | None | None |
| `query_market_stats` | Query market statistics | None | None |

#### Core Features

- ✅ **English Comma Separation**: `entities.item` format is `ItemName:Quantity`
- ✅ **Pre-Query Constraint**: `query_price` needs to first call `get_player_hand_item` to get item name
- ✅ **Formatted Price**: Amount automatically formatted for display

---

## Security Interceptor

Kilacraft-AI v1.4.5 introduces a **non-cooperative security filtering mechanism** (SkillSecurityFilter) that automatically scans player names in parameters before every Skill execution, protecting player data from being accessed or tampered with by malicious Skills.

### Core Mechanism

- **Value Scanning + Sanitization**: Directly scans all values in Skill parameters, validates permissions when online player names are detected
- **Non-Cooperative**: Does not rely on Skill declarations, directly detects actual transmitted data values
- **Automatic Sanitization**: Replaces with current player name if validation fails, Skill continues execution instead of being blocked

### Built-in Skill Whitelist

| Skill/Action | Whitelist Type | Description |
|-------------|---------------|-------------|
| `cmi.send_tp_request` | Action-level | CMI teleport request (TPA), allows sending teleport requests to other players |
| `AFKTask.create_task` | Action-level | AFK tasks can monitor other players' events |
| `command.execute_command` | Action-level | Commands execute as player identity, permission boundary = player's own permissions |

### Third-Party Skill Protection

- Even if third-party Skills attempt to operate on other players, the security filter will automatically sanitize (replace with current player name)
- Server administrators are advised to review code before installing third-party Skills to ensure behavior meets expectations

---

## Bukkit Event Listener List

### S-Level Listeners (7)

| Task Type | Listening Event | Monitoring Target | Trigger Timing |
|----------|-----------------|-----------------|-----------------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | Player online | Triggers after player passes authentication |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | Player offline | Triggers when player leaves server |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | Player death | Triggers when player dies |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | Player teleport | Triggers when player teleports |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | Player level change | Triggers when player levels up or down |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | Player world change | Triggers when player teleports between worlds |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | Weather change | Triggers when world weather changes |

### A-Level Listeners (4)

| Task Type | Listening Event | Monitoring Target | Trigger Timing |
|----------|-----------------|-----------------|-----------------|
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | Player enters bed | Triggers when player enters bed to start sleeping |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | Player leaves bed | Triggers when player gets up from bed |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | Player respawn | Triggers when player respawns |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | Player item break | Triggers when player item breaks |

### Custom Task Type (1)

| Task Type | Monitoring Method | Monitoring Target | Supported Conditions |
|----------|-----------------|-----------------|---------------------|
| `CUSTOM` | Periodic polling | Any Skill return value | Supports single condition comparison (less_than, greater_than, equal, etc.) |

### Special Placeholders

| Listener Type | Available Placeholders |
|-------------|----------------------|
| PLAYER_ONLINE/OFFLINE/DEATH | `{triggered_player}`, `{creator}` |
| PLAYER_TELEPORT | `{from_world}`, `{to_world}`, `{from_x}`, `{from_y}`, `{from_z}`, `{to_x}`, `{to_y}`, `{to_z}` |
| PLAYER_LEVEL_CHANGE | `{old_level}`, `{new_level}`, `{direction}` |
| PLAYER_CHANGED_WORLD | `{from_world}`, `{to_world}` |
| WEATHER_CHANGE | `{world_name}`, `{weather_state}`, `{weather_type}` |
| PLAYER_BED/RESPAWN | `{x}`, `{y}`, `{z}`, `{world}` |
| PLAYER_ITEM_BREAK | `{item_name}`, `{item_type}` |

---

## Third-Party Plugin Dependencies

### Required Dependencies

| Plugin Name | Version Requirement | Function | Provided Capabilities |
|-------------|-------------------|-----------|----------------------|
| **None** | - | - | Core features require no third-party plugins |

### Optional Dependencies (Recommended for Complete Functionality)

| Plugin Name | Version Requirement | Function | Provided Capabilities | Dependent Skill |
|-------------|-------------------|-----------|----------------------|-------------------|
| **CMI** | v9.8.6.4+ | Teleportation, homes, warps, kits, player enhancements, TPA | Teleportation, Player Info Query | CMISkill |
| **GlobalMarketPlus** | v1.3.8.0+ | Global market, item trading, mailbox, kits | Market Query | MarketQuerySkill |

### Compatibility Notes

- ✅ **Folia Support**: Plugin fully compatible with Folia server architecture via reflection
- ✅ **No Soft Dependencies**: When optional plugins are not installed, corresponding Skills become automatically unavailable without affecting core features
- ✅ **SPI Extension**: Third-party plugins can register their own Skills via Bukkit ServicesManager

---

## Capability Boundaries

### What AI Plugin Can Do

✅ **Can**:
- Query Minecraft native API data (player, world, server status)
- Listen to 11 Bukkit Event types (S-level 7 + A-level 4)
- Create AFK tasks (automatically execute multi-step callbacks after event triggers)
- Execute server commands (as player identity, constrained by permissions)
- Query third-party plugin data (CMI teleportation/info, GlobalMarketPlus market)
- Multi-step task chains (support data references and placeholder replacement between steps)
- LLM secondary analysis (convert API execution results to natural language)

### What AI Plugin Cannot Do

❌ **Cannot**:
- Modify Minecraft core data (modify world configs, server settings, etc.)
- Bypass server permission system (all operations are constrained by permissions)
- Execute commands requiring OP permission (unless player has that permission)
- Directly manipulate game physics (cannot modify blocks/entities, etc.)
- Access player private data (only query public game status)
- Automate repetitive tasks (AFKTask only supports one-time trigger, no loop/scheduled tasks)
- Recursive nested AFK tasks (cannot create AFKTask again in callbacks)

### Data Access Boundaries

| Data Type | Read/Write | Boundary Description |
|------------|-------------|----------------------|
| Player Status | Read-Only | Can query health, location, items, etc., cannot modify |
| World Status | Read-Only | Can query time, weather, biome, etc., cannot modify |
| Server Config | Read-Only | Can query version, MOTD, world list, cannot modify |
| Command Execution | Write (Indirect) | Execute via dispatchCommand, constrained by permissions |
| CMI Data | Read-Only | Query homes, warps, player info, cannot directly modify |
| Market Data | Read-Only | Query prices, items, cannot directly modify |

---

> **Last Updated**: 2026-04-19  
> **Plugin Version**: 1.4.6+  
> **Total Skills**: 6 (AFKTaskSkill, GenericBukkitAPI, CMISkill, CommandSkill, BukkitFXSkill, MarketQuerySkill)  
> **Total API Actions**: 60+ (GenericBukkitAPI) + 8 (CMISkill) + 2 (BukkitFXSkill) + 7 (MarketQuerySkill)  
> **Total Event Listeners**: 11 (S-level 7 + A-level 4)
