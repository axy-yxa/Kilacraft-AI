# Kilacraft-AI - Built-in Skills and Events Capability List

> **Last Updated**: 2026-08-01  
> **Description**: This document summarizes all built-in Skills of Kilacraft-AI, helping server administrators and plugin developers quickly understand the plugin's capabilities, integrated third-party plugins, and security risks. Currently **17 built-in Skills**.

---

## 📋 Table of Contents

1. [Skill Capability List](#skill-capability-list)
2. [Bukkit Event Listening (WatchSkill Event Watches)](#bukkit-event-listening-watchskill-event-watches)
3. [Third-Party Plugin Dependencies](#third-party-plugin-dependencies)
4. [Capability Boundaries](#capability-boundaries)

---

## Skill Capability List

> v2.2.0 removed the AFK task system (AFKTaskSkill). Its capabilities are replaced by the two new Skills below: WatchSkill, PlayerWatchSkill.

### 1. WatchSkill - Player Custom Watch

**Capability Type**: Condition watch (polling) + event watch  
**Dependency**: Pure Bukkit (depends on built-in WatchService)  
**File Location**: `skills/watch/WatchSkill.yml`  
**Implementation Class**: `WatchSkill.java` (implements `DynamicContextProvider`)

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `create_watch` | Create a watch (mode=polling/event) | `mode`, `description` | `single_shot`, etc. |
| `cancel_watch` | Cancel a watch (by watch_id exact or description fuzzy) | `watch_id` or `description` | None |
| `list_watches` | List all active watches for the current player | None | None |

#### Supported 11 Event Watch Types

| Event Type | Monitoring Target | Available Filter |
|------------|------------------|-----------------|
| `furnace_smelt` | Furnace smelt complete | `result_type` (product material) |
| `crop_mature` | Crop mature | `crop_type` (crop type) |
| `entity_death` | Entity death | `entity_type` (entity type) |
| `entity_spawn` | Entity spawn | `entity_type` (entity type) |
| `player_death` | Player death | None |
| `player_teleport` | Player teleport | `cause` (teleport cause) |
| `player_level_change` | XP level change | None |
| `player_changed_world` | World change | None |
| `block_break` | Block break | `block_type` (block type) |
| `player_fish` | Fishing success | None |
| `player_chat` | Chat message | `keyword` (keyword match) |

#### Core Features

- ✅ **Two modes**: polling (periodically run a skill action and compare returned field values) + event (Bukkit event triggers on hit)
- ✅ **Dynamic context injection**: implements `DynamicContextProvider` to inject watch lists into Phase 2 prompts
- ✅ **Notify-only on trigger** (safer than the old AFK task system — no automatic callback execution)
- ✅ **Limits**: per-player polling ≤ 3 / event ≤ 5 / global 200
- ✅ **Offline grace window** (default 5 min, for reconnect recovery)
- ✅ Global singleton listener + reverse index; zero-cost when no one is watching

---

### 2. PlayerWatchSkill - Cross-Player Online/Offline Subscription

**Capability Type**: Player social light interaction (online/offline notification subscription)  
**Dependency**: Pure Bukkit (depends on built-in PlayerWatchService)  
**File Location**: `skills/playerwatch/PlayerWatchSkill.yml`  
**Implementation Class**: `PlayerWatchSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `subscribe` | Subscribe to a player's online/offline notifications | `target_player` | `trigger_event` (JOIN/QUIT/BOTH, default BOTH) |
| `unsubscribe` | Unsubscribe from a player | `target_player` | `trigger_event` (omitting cancels all) |
| `list` | List all active subscriptions for the current player | None | None |
| `unsubscribe_all` | Cancel all subscriptions for the current player | None | None |

#### Core Features

- ✅ **One-way subscription**: the target is unaware; only active while subscriber is online
- ✅ **Multi-target support**: subscribe to multiple players at once (old system only allowed one)
- ✅ **Anti-disorder**: offline notification cancels any pending online notification
- ✅ **Online notification delayed 2 sec**: wait for player to fully enter
- ✅ **Non-persistent**: cleared on restart; subscriber offline auto-clears; per-player limit of 5

---

### 3. GenericBukkitAPI - Generic Bukkit API Executor

**Capability Type**: Native API Data Query
**Dependency Plugin**: Pure Bukkit Native API
**File Location**: `skills/bukkit/apis.yml`
**Implementation Class**: `GenericBukkitAPISkill.java`

#### Supported API Actions (71 total — 44 player + 21 world + 6 server)

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
| `get_player_location` | Get player location | `x`, `y`, `z`, `world` |
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

### 4. CMISkill - CMI Plugin Integration

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

### 5. CommandSkill - Command Execution

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

### 6. BukkitFXSkill - Sound & Particle Effects

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

### 7. BukkitStatsSkill - Vanilla Statistics Query

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

### 8. MarketQuerySkill - GlobalMarketPlus Plugin Integration

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
| `query_seller_items` | Query a specific seller's listed items | `seller_name` | None |
| `query_mailbox` | Query player mailbox unclaimed mail | None | None |
| `query_market_stats` | Query market statistics | None | None |

#### Core Features

- ✅ **English Comma Separation**: `entities.item` format is `ItemName:Quantity`
- ✅ **Pre-Query Constraint**: `query_price` needs to first call `get_player_hand_item` to get item name
- ✅ **Formatted Price**: Amount automatically formatted for display

---

### 9. MarketActionSkill - GlobalMarket Write Operations

**Capability Type**: Market Write Operations (Trade Delegation)  
**Dependency Plugin**: GlobalMarketPlus (v1.3.8.0+)  
**File Location**: `skills/globalmarketplus/MarketActionSkill.yml`  
**Implementation Class**: `MarketActionSkill.java`

#### Supported Actions (9)

| Action | Description | Confirmation |
|--------|-------------|-------------|
| `search_item` | Search market for items | None |
| `list_item` | List item on market | Confirm price & quantity |
| `claim_all` | Claim all mailbox items | Confirm mailbox content |
| `create_buy_order` | Create buy order | Confirm item & price |
| `delist_item` | Delist market item | None |
| `transfer_balance` | Transfer balance to player | Double confirm recipient & amount |
| `create_auction` | Create auction listing | Confirm item & starting price |
| `batch_sell` | Batch sell all items | Confirm total items |
| `batch_buy` | Batch buy items | Confirm purchase list |

#### Core Features
- ✅ All write operations executed via Bukkit command delegation, atomicity guaranteed by GMP
- ✅ Requires GlobalMarketPlus independent permission nodes

---

### 10. UtilitySkill - Generic Utility Actions

**Capability Type**: Basic Utility Actions (Delay, Notification, Broadcast)  
**Dependency**: Pure Bukkit Native API  
**File Location**: `skills/utility/UtilitySkill.yml`  
**Implementation Class**: `UtilitySkill.java`

#### Supported Actions

| Action | Description | Required Parameters |
|--------|-------------|---------------------|
| `delay_wait` | Non-blocking delayed wait (1-60 sec) | `seconds` |
| `notify_player` | Summarize interim results via LLM and notify player | `message` |
| `broadcast_message` | OP-only: beautify and broadcast a message server-wide | `message` |

#### Core Features

- ✅ **delay_wait**: dedicated scheduler, no IO thread pool usage
- ✅ **notify_player**: respects server's streaming output config
- ✅ **broadcast_message**: CHAT carrier, supports single-intent and multi-step

---

### 11. WebSearchSkill - Web Search

**Capability Type**: Real-time web search  
**Dependency**: Pure Bukkit (self-managed HTTP calls; requires API Key in `web.yml`)  
**File Location**: `skills/websearch/WebSearchSkill.yml`  
**Implementation Class**: `WebSearchSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `search` | Search with keywords, returns title/URL/snippet | `query` | `count`, `time_range`, etc. |

#### Core Features

- ✅ **9 search engine providers**: 5 domestic (Zhipu/Baidu Qianfan/Volcengine Doubao/Qiniu Baidu/Alibaba IQS) + 4 international (Tavily/Brave/Exa/You.com); `provider: auto` routes by server language
- ✅ **Time range filtering**: today / last week / last month
- ✅ **Auto multi-step search**: complex queries split into up to 5 sub-searches
- ✅ Requires `kilacraft.websearch` permission + API Key configured by server owner

---

### 12. WebFetchSkill - Web Fetch

**Capability Type**: Fetch URL body and answer questions about it  
**Dependency**: Pure Bukkit (OkHttp + Jsoup local-only, zero-config, no API Key)  
**File Location**: `skills/webfetch/WebFetchSkill.yml`  
**Implementation Class**: `WebFetchSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `fetch` | Fetch a URL and extract body text | `url` | `question`, etc. |

#### SSRF Protection (when `ssrf_protection: true`)

- **Private-network address interception**: blocks localhost/LAN addresses (127.x/10.x/192.168.x/172.16-31.x)
- **Anti-DNS rebinding**: IP check embedded in OkHttp DNS resolution, eliminating the TOCTOU window between "check" and "connect"
- **Forced HTTPS + per-hop redirect re-check**: `http://` auto-upgraded to `https://`; up to 3 hops, each re-checked
- **Byte-level hard limit on response body**: `readBodyWithLimit` strictly bounded by `max_body_size_mb` (default 2 MB), prevents OOM

#### Core Features

- ✅ **Zero config pure local**, no API Key needed
- ✅ Auto-strips script/style/nav noise; truncated at `max_text_chars`
- ✅ Async fetch (IO thread pool), skill self-manages timeout
- ✅ Requires `kilacraft.webfetch` permission (default: all players)

---

### 13. VersionInfoSkill - Version Info Query

**Capability Type**: Plugin version & update info query (read-only)  
**Dependency**: Pure Bukkit (data source: Gitee/GitHub Release API, routed by i18n language)  
**File Location**: `skills/admin/VersionInfoSkill.yml`  
**Implementation Class**: `VersionInfoSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `check_update` | Self-check current version + query latest (compare + download URL + full changelog) | None | None |
| `read_changelog` | Read full changelog for a specific version | `version` | None |
| `list_versions` | List recent versions | None | `limit` (default 10) |

#### Core Features

- ✅ Read-only query only; download URLs returned in data
- ✅ Default action `check_update` covers all three needs in one call
- ✅ Requires `kilacraft.admin.info` permission (default OP)

---

### 14-16. Server Admin Skills

Three server admin Skills; detailed usage in the "Admin Features Guide":

| # | Skill | Skill Name | Actions | Permission |
|---|-------|-----------|---------|------------|
| 15 | ServerHealthSkill | `server_health` | `health_report` / `list_reports` / `read_report` | `kilacraft.admin.health` |
| 16 | PlayerAnalysisSkill | `player_analysis` | `online_trend` / `top_active` / `new_players` / `profile_coverage` / `social_insights` / `player_relations` | `kilacraft.admin.player` |
| 17 | AuditLogSkill | `audit_log` | `query_logs` / `skill_stats` / `error_logs` | `kilacraft.admin.audit` |

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
| `player_watch.subscribe` | Action-level | Cross-player watch subscription, allows subscribing to other players' online/offline events |
| `command.execute_command` | Action-level | Commands execute as player identity, permission boundary = player's own permissions |

> v2.2.0 removed `AFKTask.create_task` (AFK task system deleted) and added `player_watch.subscribe`. Whitelisted actions are only audited, not replaced (replacing would break cross-player operation commands). This interceptor always runs and cannot be skipped.

### Third-Party Skill Protection

- Even if third-party Skills attempt to operate on other players, the security filter will automatically sanitize (replace with current player name)
- Server administrators are advised to review code before installing third-party Skills to ensure behavior meets expectations

---

## Bukkit Event Listening (WatchSkill Event Watches)

> Starting from v2.2.0, event listening is provided by WatchSkill (replacing the 19 old AFK system listeners). Players create event watches via natural language; WatchSkill uses a **global singleton Listener** to monitor the following 11 high-value Bukkit events, triggering notifications when filters match.

### Event Watch Types (11)

| Event Type | Bukkit Event | Trigger Timing | Available Filter |
|------------|-------------|----------------|-----------------|
| `furnace_smelt` | FurnaceExtractEvent | Player extracts smelted item from furnace | `result_type` (product material) |
| `crop_mature` | BlockGrowEvent | Crop reaches max maturity | `crop_type` (crop type) |
| `entity_death` | EntityDeathEvent | Entity dies | `entity_type` (entity type) |
| `entity_spawn` | CreatureSpawnEvent | Entity spawns near player | `entity_type` (entity type) |
| `player_death` | PlayerDeathEvent | Player dies | None |
| `player_teleport` | PlayerTeleportEvent | Player teleports | `cause` (teleport cause) |
| `player_level_change` | PlayerLevelChangeEvent | XP level changes | None |
| `player_changed_world` | PlayerChangedWorldEvent | Player changes world | None |
| `block_break` | BlockBreakEvent | Player breaks block | `block_type` (block type) |
| `player_fish` | PlayerFishEvent | Fishing success (filters non-catch states) | None |
| `player_chat` | AsyncPlayerChatEvent | Chat message | `keyword` (substring match) |

### Performance Optimizations

- **Global singleton Listener**: only one `PlayerWatchListener` registered server-wide (not one per player), preventing event amplification overhead on high-frequency events like BlockGrowEvent
- **Reverse index short-circuit**: `eventType → Set<WatchRef>` reverse index; direct return (zero-cost) when no one is watching that event type
- **Three event ownership modes**: self (O(1)) / killer attribution / coordinate distance (based on **snapshot position at watch creation time**, not real-time player position — eliminates Folia cross-region getLocation risk)
- **CAS anti-reentry**: event cooldown using `AtomicLong` CAS (`Watch.casFireTime`), only one passes under concurrent events
- **Single timer per player**: all polling watches for a given player merged into one timer

### Polling Watch (supplementary note)

In addition to event watches, WatchSkill supports **polling watches** (`polling` mode): periodically execute a built-in skill's read-only action (implementing the `ProbeSource` interface) and compare returned field values against thresholds. Value types (number/boolean/string) are auto-detected at runtime.

> For detailed watch creation, cancellation, and management, see the "Player Custom Watch" section of the Server Owner Guide. For full design constraints, see the v2.2.0 subsystem architecture section of System Architecture Details.

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
| **GlobalMarketPlus** | v1.3.8.0+ | Global market, item trading, mailbox, kits | Market Query, Market Actions | MarketQuerySkill, MarketActionSkill |

### Compatibility Notes

- ✅ **Folia Support**: Plugin fully compatible with Folia server architecture via reflection
- ✅ **No Soft Dependencies**: When optional plugins are not installed, corresponding Skills become automatically unavailable without affecting core features
- ✅ **SPI Extension**: Third-party plugins can register their own Skills via Bukkit ServicesManager

---

## Capability Boundaries

### What AI Plugin Can Do

✅ **Can**:
- Query Minecraft native API data (player, world, server status)
- Listen to 19 Bukkit Event types (S-level 7 + A-level 12)
- Player custom watches (11 event types + polling, WatchSkill)
- Cross-player online/offline subscriptions (PlayerWatchSkill)
- Web search & fetch (WebSearch / WebFetch)
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
- Auto-execute write operations on watch triggers (WatchSkill trigger is notify-only, not auto-callback — this is by design, safer than the old AFK task system)

### Data Access Boundaries

| Data Type | Read/Write | Boundary Description |
|------------|-------------|----------------------|
| Player Status | Read-Only | Can query health, location, items, etc., cannot modify |
| World Status | Read-Only | Can query time, weather, biome, etc., cannot modify |
| Server Config | Read-Only | Can query version, MOTD, world list, cannot modify |
| Command Execution | Write (Indirect) | Execute via dispatchCommand, constrained by permissions |
| CMI Data | Read-Only | Query homes, warps, player info, cannot directly modify |
| Market Data | Read/Write | Query prices and items, AI-delegated trading via command delegation |

---

> **Last Updated**: 2026-08-01  
> **Plugin Version**: 2.2.0+  
> **Total Built-in Skills**: 17  
> **Total API Actions**: 71 (GenericBukkitAPI) + 8 (CMISkill) + 2 (BukkitFXSkill) + 8 (MarketQuerySkill, incl. `query_seller_items`) + 9 (MarketActionSkill) + 3 (UtilitySkill)  
> **Event Watch Types**: 11 (WatchSkill, global singleton Listener + reverse index)
