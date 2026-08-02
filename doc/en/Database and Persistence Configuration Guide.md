# Kilacraft-AI - Database and Persistence Configuration Guide

> **Last Updated**: 2026-08-01  
> **Description**: This document details Kilacraft-AI's database architecture, persistent content, configuration methods, and data management

---

## 📖 Overview

Starting from v2.0.0, Kilacraft-AI introduces a database persistence layer. Player conversations, profiles, social relations, and other important data survive restarts. Supports both H2 embedded database and MySQL external database — choose flexibly based on server scale and needs.

### Core Features

- ✅ **Dual Database Support**: Supports both H2 embedded database (zero-config, single-server friendly) and MySQL (multi-server data sharing)
- ✅ **Hot-Swap Database**: Switch between H2 and MySQL via `/kila reload`, auto-fallback on failure
- ✅ **Auto DDL**: Auto-creates all table structures on first startup (H2/MySQL dual-dialect compatible)
- ✅ **HikariCP Connection Pool**: High-performance database connection pool
- ✅ **Async Writes**: All write operations execute asynchronously, never blocks the main thread
- ✅ **Scheduled Cleanup**: Expired data auto-cleaned based on configurable retention days

---

## 🚀 Quick Start

### Choose Database Type

Kilacraft-AI supports two databases. Choose based on your server scale:

**H2 Embedded Database** (for single server / development):  
Zero-config, works out of the box. Database files are automatically created in `plugins/Kilacraft-AI/data/` on first startup. No external services needed.

**MySQL External Database** (for multi-server / production):  
Share player data across multiple sub-servers. Edit `plugins/Kilacraft-AI/database.yml`:

```yaml
type: MYSQL
mysql:
  host: localhost
  port: 3306
  database: kilacraft_ai
  username: root
  password: your-password
  table_prefix: "kca_"            # Table name prefix (only letters/digits/underscores allowed)

# Data retention days (see "Configuration Reference" below for full description)
retention:
  conversation_retention_days: 60
  event_retention_days: 90
  skill_log_retention_days: 60
```

After configuration, run `/kila reload` for hot-switching. Auto-fallback to old connection pool on failure. Full configuration options under "Configuration Reference" below.

---

## 🗄️ Table Structures

### kca_conversation - Chat History

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Player UUID |
| `role` | VARCHAR(16) | Role (user/assistant) |
| `content` | TEXT | Message content |
| `personality` | VARCHAR(32) | Personality ID (empty for default AI) |
| `source` | VARCHAR(16) | Source identifier (chat/command/plugin/greeting/guardian) |
| `created_at` | BIGINT | Message creation timestamp (ms) |
| `server_id` | VARCHAR(64) | Server identifier (for group servers, empty for single server) |

**Write Strategy**: Write-Behind async flush  
**Flush Frequency**: Every 30 seconds, or immediately when message queue ≥ 20  
**Read Strategy**: Lazy-load last N messages on player's first message

### kca_player_profile - Player Profile

| Field | Type | Description |
|------|------|-------------|
| `uuid` | VARCHAR(36) | Player UUID (primary key) |
| `name` | VARCHAR(16) | Player name |
| `first_login` | BIGINT | First login timestamp (ms) |
| `last_login` | BIGINT | Last login timestamp (ms) |
| `last_logout` | BIGINT | Last logout timestamp (ms) |
| `login_count` | INT | Total login count |
| `total_playtime_ms` | BIGINT | Total playtime (ms) |
| `last_world` | VARCHAR(64) | Last world on logout |
| `last_x` | DOUBLE | Last X coordinate on logout |
| `last_y` | DOUBLE | Last Y coordinate on logout |
| `last_z` | DOUBLE | Last Z coordinate on logout |
| `last_greeting_time` | BIGINT | Last AI greeting timestamp (ms) |
| `profile_data` | TEXT | Profile JSON (8 dimensions) |
| `profile_analyzed_at` | BIGINT | Last profile analysis completion timestamp (ms) |
| `updated_at` | BIGINT | Last update timestamp (ms) |

**Profile Dimensions**:
- Playstyle (playstyle): PVP/PVE/Building/Exploration/Redstone/Survival, etc.
- Personality (personality): Extroverted/Introverted/Adventurous/Cautious, etc.
- Interests (interests): Liked areas and activities
- Boundaries (boundaries): Disliked content or behaviors
- Communication (communication): Preferred AI response style
- Spatial Memory (spatial): Mentioned locations, base positions
- Known Facts (facts): Explicitly stated facts by the player
- Special Observations (notes): Freeform personalized observations by LLM

**Analysis Trigger Conditions** (triple gate, all three must be met):
1. Time since last analysis ≥ configurable interval
2. Cumulative message count ≥ threshold
3. Sufficient new messages within sliding window

### kca_server_event - Server Events

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `event_type` | VARCHAR(32) | Event type |
| `player_uuid` | VARCHAR(36) | Triggering player UUID |
| `target_uuid` | VARCHAR(36) | Target player UUID (optional) |
| `data` | TEXT | Event metadata JSON |
| `created_at` | BIGINT | Event timestamp (ms) |
| `server_id` | VARCHAR(64) | Server identifier (for group servers) |

**Event Types** (grouped by source):

- **Lifecycle**: `PLAYER_LOGIN`, `PLAYER_LOGOUT`, `PLAYER_FIRST_JOIN`
- **Market** (collected when GlobalMarketPlus is integrated): `MARKET_ITEM_SOLD`, `MARKET_ITEM_LISTED`, `MARKET_MONEY_RECEIVED`
- **System/Admin**: `HEALTH_ALERT`, `HEALTH_ALERT_NOTIFIED`, `UPDATE_AVAILABLE`, `UPDATE_NOTIFIED`
- **Player achievements & behaviors**: `PLAYER_DEATH`, `PLAYER_ADVANCEMENT`, `PLAYER_LEVEL_UP`, `PLAYER_USE_TOTEM`, `PLAYER_DEFEAT_BOSS`, `PLAYER_COMPLETE_RAID`, `PLAYER_PET_DEATH`, `PLAYER_PVP_KILL`, `PLAYER_PVP_DEATH`, `PLAYER_TOOL_BREAK`, `PLAYER_CATCH_TREASURE`, `PLAYER_LIGHTNING_STRIKE`, `PLAYER_CURE_VILLAGER`, `PLAYER_MINE_ANCIENT_DEBRIS`, `PLAYER_TAME_ANIMAL`, `PLAYER_CRAFT_ENCH_GOLDEN_APPLE`, `PLAYER_BUILD_WITHER`
- **Player custom watch**: `PLAYER_WATCH_TRIGGERED` (written by the watch system when a player's watch condition is met)

> Naming detail: `PLAYER_LEVEL_UP` (with underscore, not `PLAYER_LEVELUP`), `MARKET_ITEM_SOLD` (not `MARKET_SELL`). Also note that `ENTITY_PLAYER_LEVELUP` is a Minecraft sound name, unrelated to this event — do not confuse them.

> **Note**: This table does NOT store a `player_name` column. When a player name is needed (e.g., friend dynamics in AI greetings), it's obtained via LEFT JOIN with `kca_player_profile`. This design prevents data inconsistency from name changes.

### kca_social_relation - Social Relations

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Player UUID (relationship owner) |
| `target_uuid` | VARCHAR(36) | Target UUID (relationship target) |
| `relation_type` | VARCHAR(32) | Relation type (e.g., PRIVATE_CHAT / TPA_INTERACTION / SKILL_INTERACTION) |
| `interaction_count` | INT | Interaction count |
| `last_interaction` | BIGINT | Last interaction timestamp (ms) |
| `strength` | DOUBLE | Relation strength (daily decay) |
| `updated_at` | BIGINT | Last update timestamp (ms) |

**Interaction Weights**:
- Private message: +0.01
- TPA teleport: +0.02
- Skill interaction (whitelisted Skills): +0.005

**Decay Mechanism**: Daily decay of 5%, auto-clean when strength drops below threshold.

### kca_skill_log - Skill Audit Log

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Triggering player UUID |
| `skill_name` | VARCHAR(32) | Skill name |
| `action` | VARCHAR(64) | Executed action |
| `entities` | TEXT | Involved entities JSON |
| `success` | BOOLEAN | Success flag |
| `result_message` | TEXT | Result message |
| `trigger_message` | TEXT | Trigger original message |
| `execution_ms` | BIGINT | Execution time (ms) |
| `source` | VARCHAR(16) | Trigger source (agent/manual) |
| `created_at` | BIGINT | Creation timestamp (ms) |
| `server_id` | VARCHAR(64) | Server identifier (for group servers) |

### kca_watermark - Watermark (Distributed Mutual Exclusion)

| Field | Type | Description |
|------|------|-------------|
| `name` | VARCHAR(64) | Watermark name (primary key, e.g., `decay_date`, `extract_time:survival`) |
| `value` | VARCHAR(128) | Watermark value (date string or timestamp) |

Used for distributed mutual exclusion of scheduled tasks (`SELECT FOR UPDATE` row lock), ensuring only one sub-server in a group executes cleanup/decay tasks. Watermark names are based on task type (e.g., `decay_date`, `cleanup_conversation`, `cleanup_events`). Extractor watermark names include `:server_id` suffix for per-server processing (e.g., `extract_time:survival`).

### kca_profile_snapshot - Profile Snapshot `Added in v2.0.2`

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Player UUID |
| `snapshot_data` | TEXT | Profile snapshot JSON (complete profile data) |
| `message_count` | INT | Number of messages analyzed in this analysis |
| `window_start` | BIGINT | Analysis window start time (ms, = previous `profile_analyzed_at`, 0 for first) |
| `window_end` | BIGINT | Analysis window end time (ms, ≈ current time) |
| `version` | INT | Profile version number, matches version field in `profile_data` |
| `analyzed_at` | BIGINT | Analysis completion timestamp (ms) |

**Design Notes**:
- **No server_id**: Same sharing strategy as `player_profile`, naturally cross-server shared
- **No independent retention**: Data volume is minimal (at most 1 per player per day, controlled by `analysis_interval_days`), kept permanently, not subject to scheduled cleanup
- **Traceability granularity**: `window_start + window_end + message_count` precisely traces "which time period and how many messages were analyzed for version N"
- **Trigger timing**: Written in the same IO task as `player_profile` update after each profile analysis (same Connection, same transaction)

---

## Group Server Data Isolation

### Table Isolation Strategy

| Table | Strategy | Reason | Has server_id |
|---|:---:|------|:---:|
| `conversation` | Isolated | Conversation context belongs to sub-server | Yes |
| `server_event` | Isolated | Events occur on specific sub-server | Yes |
| `skill_log` | Isolated | Skill execution on specific sub-server | Yes |
| `player_profile` | Shared | Player profiles accumulate cross-server (no server_id field) | No |
| `social_relation` | Shared | Social relations are inherently cross-server (no server_id field) | No |
| `profile_snapshot` | Shared | Same sharing strategy as player_profile `Added in v2.0.2` | No |
| `watermark` | Global | Distributed mutex locks, distinguished by task name | No |

> **Note**: player_profile, social_relation and profile_snapshot are inherently shared — these tables have no server_id field, so all sub-servers operate on the same data. The watermark table is global, with watermark names based on task type (e.g., `decay_date`, `cleanup_conversation`, `cleanup_events`, `extract_time:server_id`). Watermark names without server_id suffix indicate global single-execution.

### Group Server Setup

1. Configure the same MySQL connection parameters in each sub-server's `database.yml`
2. Set a unique `group.server_id` for each sub-server (e.g., `survival`, `minigame`, `rpg`)
3. Run `/kila reload` to apply changes

> **Note**: `server_id` changes only affect newly written data. Existing data is not automatically migrated.

---

## ⚙️ Configuration Reference

### database.yml Full Configuration

```yaml
# Database type: H2, MYSQL
type: H2

# H2 Configuration (effective when type=H2)
h2:
  file: "data/kilacraft"

# MySQL Configuration (effective when type=MYSQL)
mysql:
  host: "localhost"
  port: 3306
  database: "kilacraft_ai"
  username: "root"
  password: "password"
  table_prefix: "kca_"

# Group server configuration (leave empty for single server)
group:
  server_id: ""              # Unique identifier for this sub-server

# Connection pool (HikariCP)
pool:
  maximum_pool_size: 0       # 0=adaptive
  minimum_idle: 0            # 0=adaptive
  connection_timeout: 10000
  idle_timeout: 300000
  max_lifetime: 1800000

# Data retention policy
retention:
  conversation_retention_days: 60
  event_retention_days: 90
  skill_log_retention_days: 60

# Conversation history loading strategy
conversation:
  load_history_on_login: true

# Player profile analysis configuration
profile:
  analysis_interval_days: 1
  min_messages_to_trigger: 10
  analysis_timeout_seconds: 120

  # Profile analysis system prompt (supports multiline, /kila reload to apply)
  analysis_system_prompt: |
    你是一个玩家行为分析助手。根据玩家的对话历史，分析该玩家的游戏风格、偏好和行为特征。

    【画像用途】画像会作为长期记忆注入未来的对话，但刷新间隔较长（按天计）。一旦写入易过期的内容，会长期误导后续回复。务必只提炼长期稳定、不会随单次操作而改变的特征。

    请输出一个 JSON 对象，包含以下字段（信息不足则留空字符串）：
    {
      "playstyle": "长期游戏倾向（如：偏探索、偏建造、偏战斗、偏社交等）",
      "personality": "观察到的性格特征（如：友好、幽默、直接、内敛等）",
      "interests": "长期稳定的兴趣领域（如：建筑、经济、自动化、探险等）",
      "boundaries": "交互禁忌或反感（如：不愿被催促、介意特定称呼等，无则留空）",
      "communication": "期望的 AI 回复方式（如：简短直接、不用表情、语气克制等）",
      "spatial": "长期归属地的定性描述（如：在某区域有长期基地），严禁写坐标和数量",
      "facts": "玩家声明的稳定关系或身份（如：某玩家是好友或对手、自称某身份），严禁写数值",
      "notes": "其他值得长期注意的观察"
    }

    【数据性质红线 - 最重要】
    1. 严禁记录任何会随时间或他人行为变化的具体数值快照：余额/金币、库存数量与物品清单、当前坐标、血量/饱食、在线状态、在售商品与价格、附近实体数量等。这类数据随时变化，固化进画像只会留下过期错误答案——即使玩家本人在对话里明确说出具体数字，也不要记录。
    2. 只记"定性关系与稳定倾向"，不记"瞬时数值"。
    3. 只分析对话中明确体现的信息，不要推测。

    【输出规范】
    1. 每个字段用简短的短语或句子，整个 JSON 总字符数控制在 500 以内。
    2. 如果某个维度信息不足，对应字段填空字符串。
    3. 只输出 JSON，不要包含其他内容。
  # Profile analysis system prompt in English (effective when language=en)
  analysis_system_prompt_en: |
    You are a player behavior analysis assistant. Analyze the player's game style, preferences, and behavioral traits based on their conversation history.

    [Profile purpose] The profile is injected into future conversations as long-term memory, but refreshes infrequently (on the order of days). Anything volatile that gets written in will mislead subsequent replies for a long time. Extract only traits that are long-term stable and do not change with a single action.

    Output a JSON object with the following fields (use empty string if insufficient information):
    {
      "playstyle": "Long-term game tendency (e.g., explorer-leaning, builder-leaning, combat-leaning, socializer-leaning)",
      "personality": "Observed personality traits (e.g., friendly, humorous, direct, reserved)",
      "interests": "Long-term stable interests (e.g., building, economy, automation, exploration)",
      "boundaries": "Interaction taboos or dislikes (e.g., dislikes being rushed, sensitive to certain forms of address; leave empty if none)",
      "communication": "Preferred AI response style (e.g., brief and direct, no emojis, restrained tone)",
      "spatial": "Qualitative description of long-term turf (e.g., has a long-term base in some region); NEVER write coordinates or counts",
      "facts": "Stated stable relations or identity (e.g., a player is a friend or rival, self-claimed role); NEVER write numeric values",
      "notes": "Other observations worth remembering long-term"
    }

    [Data-nature red lines - most important]
    1. NEVER record numeric snapshots of anything that changes over time or with others' actions: balance/coins, inventory counts and item lists, current coordinates, health/hunger, online status, shop listings and prices, nearby entity counts, etc.
    2. Record only "qualitative relations and stable tendencies", not "instantaneous values".
    3. Analyze only information explicitly shown in conversations; do not speculate.

    [Output rules]
    1. Use short phrases or sentences for each field; keep total JSON under 500 characters.
    2. If insufficient information for a dimension, use empty string.
    3. Output only JSON, no other content.
  # Incremental analysis system prompt (used when existing profile is present) Added in v2.0.2
  incremental_system_prompt: |
    你是一个玩家行为分析助手。以下是玩家的历史画像数据（JSON）和新的对话记录。
    请对比历史画像和新对话，**保留仍然准确的内容，修正已经变化的内容**，融合输出更新后的完整画像 JSON。
    不要被最近几句话过度左右 —— 关注长期稳定的特征。

    【输出要求】
    1. 只输出 JSON，不要包含其他内容
    2. 字段与历史画像保持一致（playstyle、personality、interests、boundaries、communication、spatial、facts、notes）
    3. 如果某个维度信息不足，对应字段填空字符串
  # Incremental analysis system prompt in English (effective when language=en and existing profile is present) Added in v2.0.2
  incremental_system_prompt_en: |
    You are a player behavior analysis assistant. Below is the player's existing profile data (JSON) and new conversation records.
    Compare the existing profile with new conversations, **retain what still holds true, revise what has changed**, and produce a complete updated profile JSON.
    Do not over-weight the most recent few messages — focus on consistent long-term traits.

    Requirements:
    1. Output only JSON, no other content
    2. Keep the same fields as the existing profile (playstyle, personality, interests, boundaries, communication, spatial, facts, notes)
    3. If insufficient information for a dimension, use empty string
```

> **Eight profile dimensions**: the current version uses **8 fields** (compared to the early version's 5: playstyle/personality/preferences/communication_style/notes). The `analysis_system_prompt` and `incremental_system_prompt` field lists **must match** (all 8 dimensions listed). When modifying any profile prompt, check the other one simultaneously to avoid fields being dropped during incremental analysis.

---

## 🔧 Operations Guide

### Backup Database

**H2 Mode**:
Directly backup the `.mv.db` file in the `plugins/Kilacraft-AI/data/` directory.

**MySQL Mode**:
Use mysqldump or your preferred database backup solution.

### Migrate from H2 to MySQL

1. Backup `plugins/Kilacraft-AI/` directory
2. Manually export H2 data
3. Change `type` to `mysql` in `database.yml`
4. Run `/kila reload` to apply new configuration

### Switch Back to H2

1. Change `type` back to `h2` in `database.yml`
2. Run `/kila reload`
3. If the switch fails, the plugin auto-falls back to the old connection pool

---

## 🐛 FAQ

**Q: Where are the H2 database files?**  
`plugins/Kilacraft-AI/data/kilacraft.mv.db`

**Q: How to reset all data?**  
Stop the server, delete the `plugins/Kilacraft-AI/data/` directory, then restart.

**Q: What if MySQL connection fails?**  
Check the connection parameters in `database.yml` and whether the MySQL service is running. On failure, the plugin auto-falls back to the old connection pool without data loss.

**Q: How to configure cleanup intervals?**  
Data retention days are configured in `database.yml` under the `retention` section per table: `conversation_retention_days` (conversations, default 60), `event_retention_days` (events, default 90), `skill_log_retention_days` (skill logs, default 60). Set to `0` for permanent retention. Run `/kila reload` to apply changes. The cleanup task runs on a fixed periodic schedule managed by the unified task scheduler (no separate cleanup interval to configure).

---

## 🔗 Related Documentation

- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration guide
- [Player Profile and Social Relations System Guide](./Player%20Profile%20and%20Social%20Relations%20System%20Guide) - Profile and social system details
- [System Architecture Details](./System%20Architecture%20Details.md) - Overall system architecture
