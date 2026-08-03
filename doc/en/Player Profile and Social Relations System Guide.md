# Kilacraft-AI - Player Profile and Social Relations System Guide

> **Last Updated**: 2026-08-01  
> **Description**: This document details the Player Profile System, Social Relationship Graph, and Server Event Collection System introduced in Kilacraft-AI v2.0.0

---

## 📖 Overview

v2.0.0 introduces three data-driven subsystems that let AI remember each player's behavioral preferences, track social relationships between players, and automatically record server milestone events. These systems share the database persistence layer and work together to make AI increasingly understand your players.

---

## 🧠 Player Profile System

### Core Design

Player profiles are AI's "long-term memory" of players. The system automatically triggers LLM analysis on player login/logout, generates a structured eight-dimension profile, and dynamically injects profile summaries into system prompts during subsequent conversations, making AI's service more personalized.

### Profile Dimensions (8 Dimensions)

| Dimension | JSON Key | Description | Example |
|-----------|----------|-------------|----------|
| Playstyle | playstyle | Player's gameplay preferences | PVP enthusiast / Master builder / Redstone engineer |
| Personality | personality | Behavioral patterns | Adventurous / Social / Cautious |
| Interests | interests | Liked areas and activities | Economy & trading / Redstone / Building |
| Boundaries | boundaries | Disliked content or behaviors | Don't use my name / Don't rush me |
| Communication | communication | Preferred AI response style | Brief and direct / No emojis |
| Spatial Memory | spatial | Mentioned locations, base positions | Main base at desert (1200,64,-800) |
| Known Facts | facts | Explicitly stated facts by the player | Steve is a friend / Home near desert temple |
| Special Observations | notes | LLM freeform observations | "This player has recently shown strong interest in enchanting" |

### Workflow

```
Player Login → Load memory cache → Check if analysis needed (triple gate)
                                        ↓ Yes
                            Check for existing profile (extendedData)
                              ↙                    ↘
                         First Analysis          Incremental Analysis
                        (no existing profile)    (has existing profile)
                              ↓                        ↓
                          LLM Analysis             LLM Fusion
                        (first prompt)         (old profile + new conversations)
                              ↓                        ↓
                              └──────────┬─────────────┘
                                         ↓
                              Increment version (+1)
                              Inject analyzed_at timestamp
                                         ↓
                              Update memory cache
                              Async write to DB (player_profile)
                              Async write snapshot (profile_snapshot) Added in v2.0.2
                                         ↓
                              Profile summary injected into system prompt on next conversation
```

**Added in v2.0.2 — Incremental Analysis Mechanism**:

Before v2.0.2, LLM only saw recent conversations each time, outputting a brand-new profile that fully overwrote old data — all previous knowledge was lost. v2.0.2 introduces two improvements:

1. **Input side — Old profile injection**: For non-first analyses, the old profile JSON is injected into LLM input, allowing LLM to build upon existing knowledge rather than starting from scratch
2. **Output side — Version increment**: The `version` field changed from hardcoded `1` to auto-incrementing (`oldVersion + 1`), producing a new version with each analysis

**Dual Prompt System**:

| Scenario | Prompt Used | Description |
|----------|-------------|-------------|
| First analysis (no existing profile) | `DEFAULT_SYSTEM_PROMPT` | Original prompt, requests eight-dimension JSON |
| Incremental analysis (has existing profile) | `DEFAULT_INCREMENTAL_SYSTEM_PROMPT` | Requests comparison of old profile + new conversations, producing a fused updated profile |

Both prompts can be customized in `database.yml` (Chinese/English each with their own config). Custom values take priority when set; built-in defaults are used otherwise.

**Progressive effect of incremental analysis**:
- V1 analysis: Conversations A → Profile V1 (initial creation)
- V2 analysis: Profile V1 + Conversations B → Profile V2 (V1's knowledge carried into LLM, preserved in V2)
- V3 analysis: Profile V2 + Conversations C → Profile V3 (V1+V2 knowledge preserved)

Profile knowledge is cumulative and never lost. A player who built for six months and recently played some PvP will see their profile updated by fusing the new observations onto the existing base, rather than having "master builder" suddenly replaced by "PvP fanatic".

### Triple Gate Mechanism

To prevent unnecessary LLM overhead, profile analysis requires **all three conditions simultaneously**:

1. **Time Interval**: Time since last analysis ≥ configured interval (e.g., 30 minutes)
2. **Message Count**: Player's cumulative message increase ≥ threshold (e.g., 20 messages)
3. **Sliding Window**: Sufficient new content within the recent window (e.g., last 100 messages)

### Version Stamp Anti-Race-Condition

Profile cache cleanup uses an in-memory version stamp mechanism — when a player logs out, the cache is not immediately removed. Instead, after a 5-minute delay, the version stamp is checked. If the player reconnected during the delay, the version stamp has changed and removal is skipped. This prevents cache from being erroneously cleared during quick reconnects. Database updates use `profileDao.update()` full-field writes without optimistic locking.

### Profile Version Tracking `Added in v2.0.2`

Before v2.0.2, the `version` field was hardcoded to `1` and never actually incremented. v2.0.2 changes this to read the old version from the in-memory `extendedData` and auto-increment by +1 on each analysis. For the first analysis, `oldVersion = 0` → `newVersion = 1`.

### Historical Snapshots `Added in v2.0.2`

After each profile analysis, the system automatically inserts a snapshot record into `kca_profile_snapshot` at the same time as writing to `player_profile`, recording the complete profile JSON, version number, analysis window time range, and message count.

**Snapshot vs Profile Update relationship**:

| Operation | Target Table | Description |
|-----------|-------------|-------------|
| `profileDao.updateProfileData()` | `kca_player_profile` | Updates `profile_data` + `profile_analyzed_at` (overwrites) |
| `snapshotDao.insert()` | `kca_profile_snapshot` | Inserts a historical snapshot (appends, permanently retained) |

Both operations execute serially in the same IO task within the same `Connection`, ensuring data consistency. The snapshot table is permanently retained and not subject to `DataCleanupService` scheduled cleanup.

### Memory Cache

Profile data is cached in memory for sub-second access. Writes are asynchronous, never impacting player experience.

---

## 🕸️ Social Relationship Graph

### Core Design

The social relationship graph tracks interactions between players, automatically calculates relationship strength, and forms a dynamic social network. Daily auto-decay weakens inactive relationships.

### Interaction Weights

| Interaction Source | Weight | Trigger Method |
|-------------------|:------:|----------------|
| Private message (/tell, /msg) | +0.01 | Auto-listen to chat events |
| TPA teleport | +0.02 | Auto-listen to CMI TPA commands |
| Skill interaction (whitelist) | +0.005 | SocialRelationExtractor periodically scans kca_skill_log |

### Daily Decay

```
New Strength = Current Strength × 0.95 (5% daily decay)
```

Relations with strength < threshold (e.g., 0.01) after decay are auto-cleaned, maintaining data freshness.

### Distributed Safety Mechanism

#### Problem
In multi-server environments, multiple servers may execute daily decay simultaneously, causing duplicate decay.

#### Solution
- **Watermark Table** (kca_watermark): Decay watermark name is fixed as `decay_date` (globally unique)
- **DB Row Lock** (`SELECT FOR UPDATE`): Acquires lock before decay, checks if watermark date is today
- **CAS Mutual Exclusion** (TaskScheduler layer): Only one thread per task executes at a time within a single server

Two-layer mutual exclusion ensures no conflicts in multi-server environments. Social relations are inherently cross-server shared (no server_id field), decay operates on the entire table, executed globally only once.

### Social Relation Extractor

`SocialRelationExtractor` periodically scans the `kca_skill_log` table for whitelisted Skill records involving "player-to-player" interactions (e.g., teleport, trade), automatically strengthening social relations between the involved players.

**Whitelisted Skill Examples**:
- `cmi.send_tp_request` (TPA teleport request)
- `/tpa`-type commands in `command.execute_command`

### Social Awareness in Conversations

AI greetings can reference social relationships:
> "Welcome back! Your friend Steve is also online. You two have been interacting a lot lately."

---

## 📡 Server Event Collection System

### Core Design

Automatically records milestone events occurring on the server, allowing players to review "what they missed" while offline.

The event collection system (`EventCollector` + `MarketEventCollector`) listens to Bukkit events and writes to the `kca_server_event` table. Grouped by source:

**Lifecycle** (written by the login listener)

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `PLAYER_LOGIN` | Player logs in | Login time |
| `PLAYER_LOGOUT` | Player logs out | Logout time, world/coordinates |
| `PLAYER_FIRST_JOIN` | First time joining the server | First login time |

**Player achievements & behaviors** (`EventCollector` listens to corresponding Bukkit events)

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `PLAYER_DEATH` | Player dies | Death location, cause |
| `PLAYER_PVP_KILL` | PvP kill on another player | Target, weapon |
| `PLAYER_PVP_DEATH` | Killed in PvP | Killer |
| `PLAYER_ADVANCEMENT` | Unlocks an achievement (recipe-type filtered) | Achievement name, description |
| `PLAYER_LEVEL_UP` | Levels up | Old level, new level |
| `PLAYER_USE_TOTEM` | Uses a Totem of Undying | — |
| `PLAYER_DEFEAT_BOSS` | Defeats a boss (Ender Dragon / Wither / Warden / Elder Guardian) | Boss type |
| `PLAYER_PET_DEATH` | Tamed pet dies | Pet type |
| `PLAYER_COMPLETE_RAID` | Completes a raid | Raid location |
| `PLAYER_TOOL_BREAK` | Tool durability exhausted and breaks | Item type |
| `PLAYER_CATCH_TREASURE` | Catches a treasure item while fishing | Item type |
| `PLAYER_LIGHTNING_STRIKE` | Struck by lightning | — |
| `PLAYER_CURE_VILLAGER` | Cures a zombie villager | — |
| `PLAYER_MINE_ANCIENT_DEBRIS` | Mines ancient debris | — |
| `PLAYER_TAME_ANIMAL` | Tames an animal | Animal type |
| `PLAYER_CRAFT_ENCH_GOLDEN_APPLE` | Crafts an enchanted golden apple | — |
| `PLAYER_BUILD_WITHER` | Summons a Wither | — |

**Market** (`MarketEventCollector`, requires GlobalMarketPlus integration)

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `MARKET_ITEM_LISTED` | Lists an item | Item name, price, quantity |
| `MARKET_ITEM_SOLD` | Item sells | Item name, price, buyer |
| `MARKET_MONEY_RECEIVED` | Receives sale proceeds | Amount, item |

**System/Admin** (written by the admin management subsystem, not EventCollector)

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `HEALTH_ALERT` / `HEALTH_ALERT_NOTIFIED` | Server health alert / alert notified | Alert summary |
| `UPDATE_AVAILABLE` / `UPDATE_NOTIFIED` | New version detected / admin notified | Version number |

**Player custom watch** (written by the watch system on trigger)

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `PLAYER_WATCH_TRIGGERED` | Player's watch condition is met | Watch details |

> Naming detail: `PLAYER_LEVEL_UP` (with underscore), `MARKET_ITEM_SOLD` (not `MARKET_SELL`). See "Database and Persistence Configuration Guide" for the full field structure.

### Offline Event Aggregator

When a player logs in, the system uses a three-category architecture to query events that occurred during their offline period and generates a summary for AI greetings:

```
Player: [Login]
System: Three-category data aggregation
  → Category 1 (Own): Your diamonds were sold (+$500)
  → Category 2 (Friends): Steve completed 2 achievements, Alex leveled up 3 times
  → Category 3 (Summary): 100 login milestone! Last session you defeated the Ender Dragon
  → Use this information to generate login greeting
```

Event count and types are configurable via the `greeting` section of `behavior.yml`. Friend dynamics use LEFT JOIN with `kca_player_profile` to obtain player names.

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────┐
│               Bukkit Event Layer             │
│  PlayerJoin/Quit, Chat, PlayerDeath, CMI... │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│         Event Collection & Social Listeners  │
│  EventCollector  │  SocialGraph  │  CMI Events│
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│          Memory Cache Layer (sub-sec access)  │
│  ProfileCache  │  SocialGraph(memory)        │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│        Database Async Write (Write-Behind)    │
│  kca_player_profile │ kca_social_relation    │
│  kca_server_event   │ kca_skill_log          │
│  kca_profile_snapshot (Added in v2.0.2)       │
└─────────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│       LLM Profile Analysis + AI Context      │
│  Profile summary → system prompt injection   │
│  Social relations → greeting injection       │
│  Offline events → return greeting injection  │
└─────────────────────────────────────────────┘
```

---

## 🔧 Configuration Reference

### Profile Analysis Configuration (database.yml)

All profile analysis settings are centralized in `database.yml`'s `profile` section (in early versions they lived in `llm.yml` under `agent.profile`; they have since been migrated):

```yaml
profile:
  analysis_interval_days: 1          # Minimum days between analyses
  min_messages_to_trigger: 10        # Minimum new messages to trigger analysis
  analysis_timeout_seconds: 120      # LLM analysis timeout (seconds)

  # First-time analysis prompt (used when no existing profile)
  analysis_system_prompt: |
    ...（8-dimension output + data quality red lines; see database.yml or the Database Guide for full content）
  analysis_system_prompt_en: |
    ...
  # Incremental analysis prompt (used when existing profile is present, since v2.0.2)
  incremental_system_prompt: |
    ...
  incremental_system_prompt_en: |
    ...
```

> The three-gate parameters: `analysis_interval_days` (time interval) + `min_messages_to_trigger` (message count) together determine whether analysis triggers. Run `/kila reload` to apply changes.

### Social Relations Configuration (Internal Parameters)

Social relation weights and decay rates are currently controlled internally by code. Future versions may expose YAML configuration. Current defaults:
- Private message weight: 0.01
- TPA weight: 0.02
- Skill interaction weight: 0.005
- Daily decay rate: 5% (daily decay factor 0.95)
- Weak relation cleanup threshold: 0.01

> Since v2.2.0, the Skill whitelist used for social extraction is configured in `config.yml`'s `social.skill_whitelist` (default contains `market_action`, `cmi`, `player_watch`).

### Greeting & Offline Events (behavior.yml)

Greeting configuration is consolidated under the `greeting` section of `behavior.yml` (in early versions it was the standalone `greeting.yml`, now merged into `behavior.yml` alongside suggestion/watch/utility.prompts):

```yaml
greeting:
  enabled: true
  delay_ticks: 100                   # Delay in ticks before greeting after login
  greeting_cooldown_minutes: 30      # Greeting cooldown (minutes)
  max_own_offline_events: 20         # Max own offline events (Category 1)
  max_friend_offline_events: 10      # Max friend dynamics (Category 2)
  max_summary_events: 5              # Max last session highlights (Category 3)
  profile_injection_enabled: true    # Whether to inject player profile into greeting prompt
  server_info: ""                    # Custom server info (injected into greeting)
  first_login_prompt: |              # First-time login greeting prompt
    ...
  returning_login_prompt: |          # Returning login greeting prompt
    ...
```

---

## 🔗 Related Documentation

- [Database and Persistence Configuration Guide](./Database%20and%20Persistence%20Configuration%20Guide) - Database table structures and configuration
- [System Architecture Details](./System%20Architecture%20Details.md) - Overall system architecture
- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration guide
