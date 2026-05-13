# Kilacraft-AI - Database and Persistence Configuration Guide

> **Last Updated**: 2026-05-11  
> **Description**: This document details Kilacraft-AI's database architecture, persistent content, configuration methods, and data management

---

## 📖 Overview

Starting from v2.0.0, Kilacraft-AI introduces a database persistence layer. Player conversations, profiles, social relations, and other important data survive restarts. Supports both H2 embedded database and MySQL external database — choose flexibly based on server scale and needs.

### Core Features

- ✅ **Dual Database Support**: Supports both H2 embedded database (zero-config, single-server friendly) and MySQL (multi-server data sharing)
- ✅ **Hot-Swap Database**: Switch between H2 and MySQL via `/kilacraft reload`, auto-fallback on failure
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
database:
  type: mysql
  host: localhost
  port: 3306
  database: kilacraft
  username: root
  password: your-password
  table_prefix: "kca_"            # Table name prefix
  data_retention_days: 30         # Data retention days
```

After configuration, run `/kilacraft reload` for hot-switching. Auto-fallback to old connection pool on failure.

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
| `source` | VARCHAR(16) | Source identifier (chat/command/plugin/greeting/afk_callback) |
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

**Event Types**: `PLAYER_DEATH`, `PLAYER_ADVANCEMENT`, `PLAYER_LEVEL_UP`, `MARKET_ITEM_SOLD`, `MARKET_ITEM_LISTED`, `MARKET_MONEY_RECEIVED`, `PLAYER_LOGIN`, `PLAYER_LOGOUT`, `PLAYER_FIRST_JOIN`, `PLAYER_PVP_KILL`, `PLAYER_PVP_DEATH`, `PLAYER_DEFEAT_BOSS`, `PLAYER_TAME_ANIMAL`, etc.

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
3. Run `/kilacraft reload` to apply changes

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
  analysis_timeout_seconds: 60

  # Profile analysis system prompt (supports multiline, /kilacraft reload to apply)
  analysis_system_prompt: |
    你是一个玩家行为分析助手。根据玩家的对话历史，分析该玩家的游戏风格、偏好和行为特征。
    请输出一个 JSON 对象，包含以下字段（如果没有足够信息则留空字符串）：
    {
      "playstyle": "玩家的游戏风格描述（如：探索型、建造型、战斗型、社交型等）",
      "personality": "从对话中观察到的性格特征（如：友好、幽默、直接、内向等）",
      "preferences": "玩家的偏好（如：喜欢的活动、感兴趣的话题等）",
      "communication_style": "沟通风格（如：简短直接、详细描述、使用表情符号等）",
      "notes": "其他值得注意的观察"
    }
    注意：
    1. 只分析对话中明确体现的信息，不要推测
    2. 每个字段尽量简洁，控制在50字以内
    3. 如果某个维度信息不足，对应字段填空字符串
    4. 只输出 JSON，不要包含其他内容
  # Profile analysis system prompt in English (effective when language=en)
  analysis_system_prompt_en: |
    You are a player behavior analysis assistant. Analyze the player's game style, preferences, and behavioral traits based on their conversation history.
    Output a JSON object with the following fields (use empty string if insufficient information):
    {
      "playstyle": "Player's game style description (e.g., explorer, builder, fighter, socializer)",
      "personality": "Personality traits observed from conversations (e.g., friendly, humorous, direct, introverted)",
      "preferences": "Player preferences (e.g., favorite activities, topics of interest)",
      "communication_style": "Communication style (e.g., brief and direct, detailed, uses emojis)",
      "notes": "Other notable observations"
    }
    Notes:
    1. Only analyze information explicitly shown in conversations, do not speculate
    2. Keep each field concise, within 50 characters
    3. If insufficient information for a dimension, use empty string
    4. Output only JSON, no other content
  # Incremental analysis system prompt (used when existing profile is present, leave empty for default) Added in v2.0.2
  incremental_system_prompt: |
    你是一个玩家行为分析助手。以下是玩家的历史画像数据（JSON）和新的对话记录。
    请对比历史画像和新对话，**保留仍然准确的内容，修正已经变化的内容**，融合输出更新后的完整画像 JSON。
    不要被最近几句话过度左右 —— 关注长期稳定的特征。

    【输出要求】
    1. 只输出 JSON，不要包含其他内容
    2. 字段与历史画像保持一致（playstyle、personality、preferences、communication_style、notes）
    3. 如果某个维度信息不足，对应字段填空字符串
  # Incremental analysis system prompt in English (effective when language=en and existing profile is present) Added in v2.0.2
  incremental_system_prompt_en: |
    You are a player behavior analysis assistant. Below is the player's existing profile data (JSON) and new conversation records.
    Compare the existing profile with new conversations, **retain what still holds true, revise what has changed**, and produce a complete updated profile JSON.
    Do not over-weight the most recent few messages — focus on consistent long-term traits.

    Requirements:
    1. Output only JSON, no other content
    2. Keep the same fields as the existing profile (playstyle, personality, preferences, communication_style, notes)
    3. If insufficient information for a dimension, use empty string
```

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
4. Run `/kilacraft reload` to apply new configuration

### Switch Back to H2

1. Change `type` back to `h2` in `database.yml`
2. Run `/kilacraft reload`
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
Modify `cleanup_interval_hours` and `data_retention_days` in `database.yml`, then run `/kilacraft reload`.

---

## 🔗 Related Documentation

- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration guide
- [Player Profile and Social Relations System Guide](./Player%20Profile%20and%20Social%20Relations%20System%20Guide) - Profile and social system details
- [System Architecture Details](./System%20Architecture%20Details.md) - Overall system architecture
