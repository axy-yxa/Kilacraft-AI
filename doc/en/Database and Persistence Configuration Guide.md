# Kilacraft-AI - Database and Persistence Configuration Guide

> **Last Updated**: 2026-05-06  
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
| `player_name` | VARCHAR(64) | Player name |
| `role` | VARCHAR(16) | Role (user/assistant) |
| `message` | TEXT | Message content |
| `source` | VARCHAR(32) | Source identifier (chat/command/console_plugin/console/greeting/afk_callback) |
| `created_at` | TIMESTAMP | Creation time |

**Write Strategy**: Write-Behind async flush  
**Flush Frequency**: Every 30 seconds, or immediately when message queue ≥ 20  
**Read Strategy**: Lazy-load last N messages on player's first message

### kca_player_profile - Player Profile

| Field | Type | Description |
|------|------|-------------|
| `player_uuid` | VARCHAR(36) | Player UUID (unique) |
| `player_name` | VARCHAR(64) | Player name |
| `first_login` | TIMESTAMP | First login time |
| `total_playtime_seconds` | BIGINT | Total playtime (seconds) |
| `login_count` | INT | Login count |
| `profile_data` | TEXT | Profile JSON (5 dimensions) |
| `last_analysis_time` | TIMESTAMP | Last profile analysis time |
| `version_stamp` | BIGINT | Version stamp (prevents race conditions) |

**Profile Dimensions**:
- Playstyle: PVP/PVE/Building/Exploration/Redstone/Survival, etc.
- Personality: Extroverted/Introverted/Adventurous/Cautious, etc.
- Content Preference: Economy/Combat/Social/Building, etc.
- Communication Style: Concise/Detailed/Humorous/Formal, etc.
- Special Observations: Freeform personalized observations by LLM

**Analysis Trigger Conditions** (triple gate, all three must be met):
1. Time since last analysis ≥ configurable interval
2. Cumulative message count ≥ threshold
3. Sufficient new messages within sliding window

### kca_server_event - Server Events

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Related player UUID |
| `target_uuid` | VARCHAR(36) | Target player UUID (nullable) |
| `event_type` | VARCHAR(64) | Event type |
| `data` | TEXT | Event data (structured text, event-specific) |
| `created_at` | TIMESTAMP | Event time |

**Event Types**: `PLAYER_DEATH`, `PLAYER_ADVANCEMENT`, `PLAYER_LEVEL_UP`, `MARKET_ITEM_SOLD`, `MARKET_ITEM_LISTED`, `MARKET_MONEY_RECEIVED`, `PLAYER_LOGIN`, `PLAYER_LOGOUT`, `PLAYER_FIRST_JOIN`, etc.

> **Note**: This table does NOT store a `player_name` column. When a player name is needed (e.g., friend dynamics in AI greetings), it's obtained via LEFT JOIN with `kca_player_profile`. This design prevents data inconsistency from name changes.

### kca_social_relation - Social Relations

| Field | Type | Description |
|------|------|-------------|
| `player_uuid` | VARCHAR(36) | Player UUID |
| `target_uuid` | VARCHAR(36) | Target player UUID |
| `strength` | DOUBLE | Relation strength |
| `last_interaction` | TIMESTAMP | Last interaction time |

**Interaction Weights**:
- Private message: +0.01
- TPA teleport: +0.02
- Skill interaction (whitelisted Skills): +0.005

**Decay Mechanism**: Daily decay of 5%, auto-clean when strength drops below threshold.

### kca_skill_log - Skill Audit Log

| Field | Type | Description |
|------|------|-------------|
| `id` | BIGINT | Primary key, auto-increment |
| `player_uuid` | VARCHAR(36) | Player UUID |
| `player_name` | VARCHAR(64) | Player name |
| `skill_name` | VARCHAR(128) | Skill name |
| `action` | VARCHAR(128) | Action name |
| `parameters` | TEXT | Parameters JSON |
| `result` | VARCHAR(32) | Execution result (SUCCESS/FAILURE/TIMEOUT) |
| `duration_ms` | BIGINT | Execution duration (milliseconds) |
| `trigger_source` | VARCHAR(64) | Trigger source |
| `created_at` | TIMESTAMP | Creation time |

### kca_watermark - Watermark (Distributed Mutual Exclusion)

| Field | Type | Description |
|------|------|-------------|
| `task_name` | VARCHAR(128) | Task name (unique) |
| `last_run_date` | VARCHAR(16) | Last run date |
| `server_id` | VARCHAR(64) | Executing server identifier |
| `updated_at` | TIMESTAMP | Update time |

Used for distributed mutual exclusion of daily tasks like social relation decay (`SELECT FOR UPDATE` row lock).

---

## ⚙️ Configuration Reference

### database.yml Full Configuration

```yaml
database:
  # Database type: h2 (embedded, default) or mysql (external)
  type: h2

  # H2 configuration (used when type=h2)
  h2:
    file_path: "data/kilacraft"   # Database file path (relative to plugin directory)

  # MySQL configuration (used when type=mysql)
  mysql:
    host: localhost
    port: 3306
    database: kilacraft
    username: root
    password: ""
    properties:
      useSSL: false
      allowPublicKeyRetrieval: true
      serverTimezone: Asia/Shanghai

  # HikariCP connection pool configuration
  pool:
    maximum_pool_size: 10
    minimum_idle: 2
    connection_timeout: 30000
    idle_timeout: 600000
    max_lifetime: 1800000

  # Table name prefix
  table_prefix: "kca_"

  # Data retention days (conversation records older than this will be cleaned)
  data_retention_days: 30

  # Cleanup task execution interval (hours)
  cleanup_interval_hours: 24
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
