# Kilacraft-AI - Player Profile and Social Relations System Guide

> **Last Updated**: 2026-05-06  
> **Description**: This document details the Player Profile System, Social Relationship Graph, and Server Event Collection System introduced in Kilacraft-AI v2.0.0

---

## 📖 Overview

v2.0.0 introduces three data-driven subsystems that let AI remember each player's behavioral preferences, track social relationships between players, and automatically record server milestone events. These systems share the database persistence layer and work together to make AI increasingly understand your players.

---

## 🧠 Player Profile System

### Core Design

Player profiles are AI's "long-term memory" of players. The system automatically triggers LLM analysis on player login/logout, generates a structured five-dimension profile, and dynamically injects profile summaries into system prompts during subsequent conversations, making AI's service more personalized.

### Profile Dimensions (Five-Point Method)

| Dimension | Description | Example |
|-----------|-------------|---------|
| Playstyle | Player's gameplay preferences | PVP enthusiast / Master builder / Redstone engineer |
| Personality | Behavioral patterns | Adventurous / Social / Cautious |
| Content Preference | Topics of interest | Economy & trading / Dungeon raids / Equipment crafting |
| Communication Style | Communication approach | Prefers concise / Prefers detailed / Prefers humorous |
| Special Observations | LLM freeform observations | "This player has recently shown strong interest in enchanting" |

### Workflow

```
Player Login → Load memory cache → Check if analysis needed (triple gate)
                                        ↓ Yes
                                   LLM Profile Analysis
                                        ↓
                                   Update memory cache + async write to DB
                                        ↓
                                   Profile summary injected into system prompt on next conversation
```

### Triple Gate Mechanism

To prevent unnecessary LLM overhead, profile analysis requires **all three conditions simultaneously**:

1. **Time Interval**: Time since last analysis ≥ configured interval (e.g., 30 minutes)
2. **Message Count**: Player's cumulative message increase ≥ threshold (e.g., 20 messages)
3. **Sliding Window**: Sufficient new content within the recent window (e.g., last 100 messages)

### Version Stamp Anti-Race-Condition

Profile updates use a version stamp mechanism — checks `version_stamp` when writing to DB, only allows overwrite when version numbers match, preventing data loss from concurrent updates.

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
- **Watermark Table** (kca_watermark): One record per periodic task
- **DB Row Lock** (`SELECT FOR UPDATE`): Acquires lock before decay, checks if `last_run_date` is today
- **CAS Mutual Exclusion** (TaskScheduler layer): Only one thread per task executes at a time within a single server

Two-layer mutual exclusion ensures no conflicts in multi-server environments.

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

### Event Types

| Event Type | Trigger Condition | Recorded Content |
|-----------|-------------------|------------------|
| `PLAYER_DEATH` | Player dies | Death location, cause of death |
| `PLAYER_ADVANCEMENT` | Player unlocks achievement | Achievement name, description |
| `PLAYER_LEVELUP` | Player levels up | New level |
| `MARKET_LIST` | Player lists item | Item name, price, quantity |
| `MARKET_SELL` | Item sold | Item name, price, buyer |
| `MARKET_BUY_RECEIVE` | Purchase payment received | Item name, amount, seller |

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

Event count and types are configurable via `greeting.yml`. Friend dynamics use LEFT JOIN with `kca_player_profile` to obtain player names.

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

### Profile Analysis Control (llm.yml)

```yaml
agent:
  profile:
    enabled: true
    min_interval_minutes: 30          # Minimum analysis interval (minutes)
    min_message_delta: 20             # Minimum new message count
    sliding_window_size: 100          # Sliding window size
```

### Social Relations Configuration (Internal Parameters)

Social relation weights and decay rates are currently controlled internally by code. Future versions may expose YAML configuration. Current defaults:
- Private message weight: 0.01
- TPA weight: 0.02
- Skill interaction weight: 0.005
- Daily decay rate: 5%
- Weak relation cleanup threshold: 0.01

### Greeting & Offline Events (greeting.yml)

```yaml
greeting:
  enabled: true
  max_own_offline_events: 10     # Max own offline events (Category 1)
  max_friend_offline_events: 5   # Max friend dynamics (Category 2)
  max_summary_events: 3          # Max last session highlights (Category 3)
  greeting_cooldown_minutes: 30
```

---

## 🔗 Related Documentation

- [Database and Persistence Configuration Guide](./Database%20and%20Persistence%20Configuration%20Guide) - Database table structures and configuration
- [System Architecture Details](./System%20Architecture%20Details.md) - Overall system architecture
- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration guide
