# Kilacraft-AI - Built-in Skills and Events Capability List

> **Last Updated**: 2026-08-04
> **Description**: This document summarizes all built-in Skills of Kilacraft-AI, helping server administrators and plugin developers quickly understand the plugin's capabilities, integrated third-party plugins, and security risks. Currently **20 built-in Skills**.

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

### 3-7. Bukkit Query Skills (5)

> v2.2.0 refactor: The old single `GenericBukkitAPISkill` (data-driven `apis.yml`) was split by responsibility into 5 independent standard config-driven skills, totaling 71 read-only query actions (action IDs, return fields, and output format are all preserved unchanged). Each skill's prompt now includes reverse-boundary and disambiguation hints for more precise AI routing. The old `skills/bukkit/apis.yml` / `apis_en.yml` have been removed.

**Capability Type**: Native API Data Query (read-only)
**Dependency Plugin**: Pure Bukkit Native API
**File Location**: `skills/bukkit/Bukkit*.yml`
**Implementation Class**: all extend `AbstractBukkitQuerySkill.java` (unified formatting, Folia data extraction, three-way field-name sync)

#### 5 Split Skills and Permissions

| # | Skill Name | Skill Class | Responsibility | API Count | Permission Node (default: true) |
|---|-----------|-------------|----------------|-----------|---------------------------------|
| 3 | `bukkit_player_info` | `BukkitPlayerInfoSkill` | Player base info (location/gamemode/XP/client/respawn, etc.) | 18 | `kilacraft.api.player.info` |
| 4 | `bukkit_player_status` | `BukkitPlayerStatusSkill` | Player live status (HP/hunger/oxygen/fire/freeze/pose, etc.) | 18 | `kilacraft.api.player.status` |
| 5 | `bukkit_player_inventory` | `BukkitPlayerInventorySkill` | Player inventory (main/off hand/armor/open container/usage) | 8 | `kilacraft.api.player.inventory` |
| 6 | `bukkit_world` | `BukkitWorldSkill` | World info (time/weather/biome/entity stats/raids, etc.) | 21 | `kilacraft.api.world.info` |
| 7 | `bukkit_server` | `BukkitServerSkill` | Server info (online/version/MOTD/world list/settings) | 6 | `kilacraft.api.server.info` |

#### Supported API Actions (71 total, grouped by skill)

> For the full API list (with each API's config, permission, and return fields), see the Bukkit API Reference. Below are representative actions grouped by skill.

**bukkit_player_info (Player base info, 18)** — location & movement, gamemode & flight, XP & level, client info, respawn

| API Action (selected) | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| `get_player_location` | Get player foot location | `x`, `y`, `z`, `world` |
| `get_player_eye_location` | Get player eye location | `x`, `y`, `z` |
| `get_player_velocity` | Get player velocity vector | - |
| `get_player_gamemode` | Get player game mode | - |
| `get_player_exp` | Get player experience | `level`, `exp_progress` |
| `get_player_locale` | Get player client language | - |
| `get_player_bed_spawn` | Get player bed spawn point | `x`, `y`, `z`, `world` |
| `get_player_total_exp` | Get player total experience | `total_exp` |

**bukkit_player_status (Player live status, 18)** — health & status, other status, equipment & effects, action status

| API Action (selected) | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| `get_player_health` | Get player health | `health`, `max_health` |
| `get_player_food` | Get player hunger | `food_level`, `saturation` |
| `get_player_oxygen` | Get player oxygen | `remaining_air`, `maximum_air` |
| `get_player_armor` | Get player full armor | `helmet_name`/`helmet_type`/`helmet_amount`... `boots_remaining_durability` (each piece: name/type/count/enchants/durability) |
| `get_player_potion_effects` | Get player potion effects | `effects` |
| `get_player_target_block` | Get player target block | `block_type`, `x`, `y`, `z` |
| `get_player_sneak_status` | Get player sneak status | - |

**bukkit_player_inventory (Player inventory, 8)** — three-tier inventory design

| API Action (selected) | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| `get_player_hand_item` | Get player main hand item | `item_name`, `item_amount` |
| `get_player_offhand_item` | Get player off hand item | `item_name`, `item_amount` |
| `get_player_open_inventory` | Get player currently open container contents | `raw_result` |
| `get_player_inventory_usage` | Get inventory usage | `item_count` (occupied slots), `empty_slots` |

**bukkit_world (World info, 21)** — time & weather, world info, biome & environment, entity stats, raid events

| API Action (selected) | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| `get_world_time` | Get world time | `time_ticks` |
| `get_weather` | Get weather condition | `weather_desc` |
| `get_world_biome` | Get world biome | `biome` |
| `get_world_raids` | Get world raid events | `raids` |
| `get_world_temperature` | Get world temperature | `temperature` |

**bukkit_server (Server info, 6)**

| API Action | Description | Additional Data Fields |
|-----------|-------------|-----------------------|
| `get_online_players` | Get online player count and list | - |
| `get_max_players` | Get max player count | - |
| `get_server_version` | Get server version | `version`, `bukkit_version` |
| `get_server_motd` | Get server MOTD | - |
| `get_server_worlds` | Get server world list | - |
| `get_server_settings` | Get server settings | `allow_flight`, `allow_nether`, `allow_end` |

#### Core Features

- ✅ **Read-Only Operations**: All APIs are data queries, no game state changes
- ✅ **Standard config-driven**: Each skill's prompt (capability summary + trigger scenarios + reverse boundaries) is defined by `description` + `action_descriptions` + `hints`, consistent with other built-in Skills
- ✅ **Disambiguation hints**: each skill prompt includes reverse boundaries (e.g., "location" = foot coords vs "eye location" = eye coords), for more precise AI routing
- ✅ **Unified field naming**: returned field names stay in sync across "yml description / Java formatting / TaskExecutor placeholder resolution" (renaming a field requires updating all three places)

---

### 8. CMISkill - CMI Plugin Integration

**Capability Type**: Teleportation + Player Info Query
**Dependency Plugin**: CMI (v9.8.6.4+)
**File Location**: `skills/cmi/CMISkill.yml`
**Implementation Class**: `CMISkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `query_homes` | Query player's own home list | None | None |
| `query_warps` | Query server public warp list | None | None |
| `query_player_info` | Query current player's own CMI enhanced info (playtime/AFK/vanish/fly/mode) | None | None |
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

### 9. CommandSkill - Command Execution

**Capability Type**: Server Command Execution (Player Identity)
**Dependency Plugin**: Pure Bukkit Native API
**File Location**: `skills/command/CommandSkill.yml`
**Implementation Class**: `CommandSkill.java`

#### Supported Actions

| Action | Description | Required Parameters |
|--------|-------------|---------------------|
| `execute_command` | Execute one server command as player | `command` |

#### Core Features

- ✅ **Always registered** (v2.2.0 removed the `command_skill.enabled` config switch), controlled solely by the `kilacraft.command.execute` permission node (default: all players)
- ✅ **Command knowledge base**: identifies command intent based on the `commands/commands.md` doc (English: `commands/commands_en.md`); server owners can append third-party plugin commands via the template
- ✅ **Permission Boundary**: AI executes commands as player, constrained by server permission system; the command list the AI sees is dynamically filtered by the current player's actual permissions
- ✅ **Fallback Mechanism**: When dedicated Skills cannot cover user needs, try executing commands
- ✅ **Security Mechanism**: Does not bypass any server security mechanisms (permissions, cooldowns, safe areas)

---

### 10. BukkitFXSkill - Sound & Particle Effects

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

### 11. BukkitStatsSkill - Vanilla Statistics Query

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

### 12. MarketQuerySkill - GlobalMarketPlus Plugin Integration

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

### 13. MarketActionSkill - GlobalMarket Write Operations

**Capability Type**: Market Write Operations (Trade Delegation)
**Dependency Plugin**: GlobalMarketPlus (v1.3.8.0+)
**File Location**: `skills/globalmarketplus/MarketActionSkill.yml`
**Implementation Class**: `MarketActionSkill.java`

#### Supported Actions

| Action | Description | Required | Optional |
|--------|-------------|----------|----------|
| `search_item` | Search items and open buy GUI | `item` | None |
| `sell_item` | List the main-hand item (must have item in main hand) | None | `price` (missing → returns ref price + [NEED_INFO]), `quantity` |
| `pickup_mail` | Claim mailbox items | None | `target` (default all; may specify mail UID) |
| `buy_item` | Create a buy order (must have item in main hand) | None | `price`, `quantity` |
| `cancel_listing` | Show listings, then delist | None | `uid` (omitting returns the listings to pick from) |
| `transfer_money` | Transfer money to another player | `target_player` | `amount` (missing → [NEED_INFO]; supports arithmetic placeholder like `{step_0.balance}/3`) |
| `auction_item` | Auction the main-hand item (must have item in main hand) | None | `price`, `quantity` |
| `sell_inventory` | Batch sell same-type inventory items (opens GUI) | `price` | None |
| `buy_inventory` | Batch buy items (opens GUI) | `price` | None |

#### Core Features
- ✅ **Only auto-registered when GlobalMarketPlus is present**
- ✅ All write operations executed via Bukkit command delegation, atomicity guaranteed by GMP internally
- ✅ **Guided price confirmation**: when listing, AI guides the player to confirm the price
- ✅ **Large-transfer double confirmation**: prevents mistakes

---

### 14. UtilitySkill - Generic Utility Actions

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

### 15. WebSearchSkill - Web Search

**Capability Type**: Real-time web search  
**Dependency**: Pure Bukkit (self-managed HTTP calls; requires API Key in `web.yml`)  
**File Location**: `skills/websearch/WebSearchSkill.yml`  
**Implementation Class**: `WebSearchSkill.java`

#### Supported Actions

| Action | Description | Required Parameters | Optional Parameters |
|--------|-------------|---------------------|---------------------|
| `search` | Search with keywords, returns title/URL/snippet | `query` | `count`, `recency`, etc. |

#### Core Features

- ✅ **9 search engine providers**: 5 domestic (Zhipu/Baidu Qianfan/Volcengine Doubao/Qiniu Baidu/Alibaba IQS) + 4 international (Tavily/Brave/Exa/You.com); `provider: auto` routes by server language
- ✅ **Time range filtering**: `recency` accepts day/week/month/year
- ✅ **Auto multi-step search**: complex queries split into up to 5 sub-searches
- ✅ Requires `kilacraft.websearch` permission + API Key configured by server owner

---

### 16. WebFetchSkill - Web Fetch

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

### 17. VersionInfoSkill - Version Info Query

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

### 18-20. Server Admin Skills

Three server admin Skills; detailed usage in the "Admin Features Guide":

| # | Skill | Skill Name | Actions | Permission |
|---|-------|-----------|---------|------------|
| 18 | ServerHealthSkill | `server_health` | `health_report` / `list_reports` / `read_report` | `kilacraft.admin.health` |
| 19 | PlayerAnalysisSkill | `player_analysis` | `online_trend` / `top_active` / `new_players` / `profile_coverage` / `social_insights` / `player_relations` | `kilacraft.admin.player` |
| 20 | AuditLogSkill | `audit_log` | `query_logs` / `skill_stats` / `error_logs` | `kilacraft.admin.audit` |

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

> **Last Updated**: 2026-08-04
> **Plugin Version**: 2.2.0+
> **Total Built-in Skills**: 20
> **Total API Actions**: 71 read-only queries (5 Bukkit query skills combined) + 8 (CMISkill) + 2 (BukkitFXSkill) + 8 (MarketQuerySkill, incl. `query_seller_items`) + 9 (MarketActionSkill) + 3 (UtilitySkill), etc.
> **Event Watch Types**: 11 (WatchSkill, global singleton Listener + reverse index)
