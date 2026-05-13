# Kilacraft-AI

> The first plugin that gives every server an AI agent that understands you better the more you use it — powered by LLM intent recognition and skill execution framework

[![GitHub Tag](https://img.shields.io/github/v/tag/axy-yxa/Kilacraft-AI?label=Release&color=blue)](https://github.com/axy-yxa/Kilacraft-AI/tags)
[![MC](https://img.shields.io/badge/MC-1.16.5--1.21+-green.svg)](https://github.com/axy-yxa/Kilacraft-AI)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/axy-yxa/Kilacraft-AI/blob/main/LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2.svg)](https://discord.gg/nNmhcZHDxr)

---

## Version Compatibility

**Minimum Requirements: Minecraft 1.16.5 + Java 17**

| Minecraft Version | Java Requirement | Supported Servers |
|:-:|:-:|:-:|
| **1.16.5** | Java 17+ | Paper / Purpur / Leaf / Folia |
| **1.17 - 1.19** | Java 17+ | All |
| **1.20 - 1.21+** | Java 21+ | All |

> One JAR compatible with all versions. Developed on Spigot 1.16.5 API, fully supports Folia regional thread scheduling.

### Optional Dependencies

| Plugin | Version | Features |
|:-:|:-:|:-:|
| **CMI** | 9.8.6.4+ | Teleport, homes, warps, enhanced player info, TPA |
| **GlobalMarketPlus** | 1.3.8.0+ | Market queries, balance, prices, listings |
| **MythicMobs** | 5.12.0+ | NPC placeholders (display AI responses) |
| **Vault** | Latest | Multi-currency support |

> Features auto-disable when plugins are missing. Core chat remains unaffected.

---

## Quick Start

### 1. Install

Download the latest `Kilacraft-AI.jar`, place it in your server's `plugins/` directory, and start the server.

### 2. Configure API

Edit `plugins/Kilacraft-AI/llm.yml` with your LLM API credentials:

```yaml
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-chat"
```

Supports all OpenAI-compatible providers (DeepSeek, Zhipu AI, Moonshot, OpenAI, Groq, SiliconFlow, Gemini, OpenRouter, etc.). Just change `api_url` and `model` to switch.

### 3. Test

```
/kila hello
```

If you see an AI response, you're all set.

> Reload config with `/kilacraft reload`, knowledge base with `/kilacraft knowledge reload`, personalities with `/kilacraft personalities reload`.

---

## Smart Chat

### Three Interaction Modes

```
# Command mode
/kila How do I get diamonds?

# Continuous chat mode
/kilacraft chat
> I want to build a farm
> AI: Great idea! What crop do you want to plant?

# Keyword trigger (public chat)
@ai How do I craft this?
```

Command aliases: `/kilacraft`, `/kila`, `/ai`, `/zm`

### Streaming Output

AI responses display character by character in real-time:

```yaml
output:
  stream:
    enabled: true
```

Supports 5 output carriers, configurable per scenario:

| Carrier | Effect | Best For |
|:-:|:-:|:-:|
| **SIDEBAR** | Right sidebar, no FOV obstruction | Long responses (recommended) |
| **BOSS\_BAR** | Top bar | Medium-length text |
| **ACTION\_BAR** | Above hotbar | Short notifications |
| **CHAT** | Chat box | Default |
| **TITLE** | Screen center | Short highlights |

### AI Response Sound

Plays a sound when AI starts responding, only the triggering player hears it:

```yaml
output:
  sound:
    enabled: true
    sound_name: "ENTITY_PLAYER_LEVELUP"
    volume: 0.5
    pitch: 1.2
```

---

## Knowledge Base

Let AI understand your server rules, gameplay, and FAQ. Just place Markdown or TXT files in `plugins/Kilacraft-AI/knowledge/`:

```markdown
# server_rules.md

## How to claim land?
Use the /claim command to define your territory. Costs at least 10 coins.
```

Run `/kilacraft knowledge reload` to load. AI automatically retrieves and cites relevant content when players ask questions.

Supports custom dictionaries for server-specific terminology to improve search accuracy.

---

## Player Profile System

AI automatically analyzes player conversation history and builds an eight-dimension behavioral profile for each player, dynamically injecting profile summaries as reference context in subsequent conversations. **AI gets smarter about your players over time.**

### Eight Profile Dimensions

| Dimension | Description | Example |
|:-:|:-:|:-:|
| Playstyle | Gameplay preferences | Combat-oriented, Explorer, Builder |
| Personality | Behavioral patterns | Friendly, Humorous, Direct |
| Interests | Liked areas and activities | Economy & trading, Redstone, Building |
| Boundaries | Disliked content or behaviors | Don't use my name, Don't rush me |
| Communication | Preferred AI response style | Brief & direct, No emojis |
| Spatial Memory | Mentioned locations, base positions | Main base at desert (1200,64,-800) |
| Known Facts | Explicitly stated facts by the player | Steve is a friend, Home near desert temple |
| Special Observations | AI freeform observations | "This player has recently shown interest in enchanting" |

### How It Works

- Automatically triggers analysis on player login/logout (triple gate mechanism to prevent wasting API calls)
- Analysis data sourced from player's real conversations with AI (excludes NPC dialogues and greeting messages)
- Results are automatically injected into system prompts as reference context for subsequent conversations
- Configuration in `llm.yml`:

```yaml
agent:
  profile:
    enabled: true
    min_interval_minutes: 30    # Minimum analysis interval
    min_message_delta: 20       # Minimum new message count
```

---

## Social Relationship System

Automatically tracks interactions between players, builds a social relationship graph, and lets AI perceive social connections.

### Tracked Interaction Types

| Interaction Type | Trigger | Social Weight |
|:-:|:-:|:-:|
| Private Message | `/msg` private chat | High |
| TPA Teleport | `/tpa` teleport request | Medium-High |
| Skill Interaction | Collaborating via AI tasks | Medium |

### Strength Calculation

Relationship strength uses diminishing incremental algorithm — new interactions contribute less as existing strength grows, preventing a few players from gaming the system. Inactive relationships naturally decay over time.

### Effects

- Friends' milestone events (boss kills, raid completions, pet deaths, etc.) appear in each other's login greetings
- AI can sense "who's friends with whom" and naturally mention friend dynamics in conversations
- Social skill whitelist configurable in `config.yml`:

```yaml
social:
  skill_whitelist:
    - "market_action"
    - "cmi"
    - "AFKTask"
```

---

## AI Login Greeting

AI automatically sends personalized greetings when players log in. Based on player profiles, offline events, and friend dynamics, every greeting is unique.

### First Login

Welcomes new players with an introduction to AI assistant features. Supports custom server info (configured via `server_info` in `greeting.yml`).

### Returning Login (Three-Category Data Aggregation)

| Category | Data Source | Example |
|:-:|:-:|:-:|
| Own Events | Events that happened while player was offline | Items sold, payments received |
| Friend Dynamics | Friends' milestones during offline period | Friend killed the Ender Dragon |
| Session Highlights | Important events since last greeting | Pet killed, totem triggered, Warden defeated |

### Configuration Example

```yaml
# greeting.yml
greeting:
  enabled: true
  delay_ticks: 100               # Delay after login (ticks)
  max_own_offline_events: 10     # Max own offline events
  max_friend_offline_events: 5   # Max friend events
  max_summary_events: 3          # Max session highlights
  greeting_cooldown_minutes: 30  # Greeting cooldown (minutes)
  server_info: "Your server intro" # Optional, mentioned to new players
```

### Example Output

```
# First login
Hey Hub, welcome to the server! I'm your AI assistant, just use /ai to reach me.
I can help you check items, browse the market, run background tasks, and more.

# Returning (offline 3 days)
Welcome back! Your diamonds sold while you were away. Your friend Steve killed the Ender Dragon. Hub is also online now.

# Quick reconnect (offline 10 seconds)
Hey, back already?
```

---

## Data Persistence

Supports both H2 embedded database and MySQL. Data persists across restarts.

### H2 Embedded (Default)

Zero-config out of the box. Database files stored in `plugins/Kilacraft-AI/data/`.

### MySQL

Recommended for multi-server data sharing:

```yaml
# database.yml
database:
  type: mysql
  host: localhost
  port: 3306
  database: kilacraft
  username: root
  password: your-password
```

Run `/kilacraft reload` for hot-switching. Auto-fallback on failure.

### Persisted Data

| Data | Description |
|------|------|
| Conversation History | All player-AI conversations, batch flushed every 30 seconds |
| Player Profiles | Eight-dimension behavioral analysis results |
| Social Relations | Interaction strength and type between players |
| Server Events | Milestone events like deaths, achievements, trades |
| Skill Audit | All Skill execution logs |

> Data retention days configurable in `database.yml`. Expired data is automatically cleaned up.

---

## AFK Task System

Create background monitoring tasks via natural language. Automatically notifies or executes actions when conditions are met.

### Event Listeners (19 types)

```
Player: Watch for Steve to come online
AI: Got it! I'll notify you as soon as Steve logs in.

[30 minutes later...]
🔔 Steve has joined the server!
```

```
Player: Watch for Steve to come online, then check what he's holding
AI: Will do! I'll automatically check Steve's item when he logs in.

[After Steve joins...]
🔔 Steve is online! He's holding a Diamond Sword x1 in main hand.
```

| Monitor Type | Description |
|:-:|:-:|
| Player Join/Quit | Monitor specific player online status |
| Player Death/Respawn | Monitor death events and respawns |
| Player Teleport/World Change | Monitor position changes |
| Level Change | Monitor player level ups/downs |
| Weather Change | Monitor world weather |
| Sleep/Item Break | Enter/leave bed, item breakage |
| Fishing | Notify or trigger actions on catch |
| Chat | Trigger automation via keywords |
| Block Break | Trigger actions when specific blocks mined |
| Entity Death | Boss kill detection |
| Entity Spawn | Mob farm efficiency monitoring |
| Entity Explosion | Anti-grief warning |
| Furnace Smelt | Notify when smelting completes |
| Crop Growth | Notify when crops mature |

### Custom Condition Polling

Monitor any numeric condition returned by Skills:

```
Player: Tell me when my health drops below 10
Player: Remind me when my balance goes below 1000
Player: Check diamond price when I reach level 30
```

Management: `/kilacraft afk` to view, `/kilacraft afk cancel` to cancel. One task per player at a time.

---

## Vanilla Data Query

### Bukkit API (72 Built-in Interfaces)

AI directly queries player status, world info, and server info:

```
Player: What am I holding?       → Main hand: Diamond Sword x1
Player: How much health do I have? → Health: 18.5/20.0
Player: How many players online?  → Online players: 15/100
```

Categories: Player Inventory, Player Status, Player Info, World Info, Server Info, Environment.

All APIs are read-only, each category has independent permission nodes.

### Vanilla Statistics

Query Minecraft vanilla cumulative stats (lifetime records). Supports 80+ stat enums with automatic unit conversion.

---

## Global Market Operations

AI can execute trading operations on behalf of players (requires GlobalMarketPlus):

```
Player: List my diamonds for 100 each     → AI guides confirmation then lists
Player: Collect all my mailbox items       → AI collects everything
Player: Place a buy order for 50 diamonds  → AI creates the order
Player: Transfer 500 to Steve              → AI confirms then transfers
```

Supports 9 operations (search/list/collect/buy-order/delist/transfer/auction/bulk-sell/bulk-buy). All write operations executed via Bukkit commands.

---

## CMI Integration

```
Player: Take me home            → AI queries home list then teleports
Player: Teleport to spawn       → AI queries warp list then teleports
Player: Teleport to Steve       → AI sends TPA request
Player: Is Steve online?        → Steve is online, AFK: No
```

8 actions (5 queries + 3 teleports). Teleport uses TPA request mode.

---

## Personality System & NPC Dialogue

### Personality Config

Define different AI personality styles in `personalities.yml`:

```yaml
common_prompt: "You are an NPC on a Minecraft server, talking to player {player}."

Fox: |
  You are a clever fox NPC who speaks playfully and cutely.
  Likes to end sentences with "~", often uses emojis.
```

### NPC Smart Dialogue

Two ways to give MythicMobs NPCs independent personalities:

**Method 1: Callback Command** (Recommended)

```yaml
# MythicMobs skill config
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

**Method 2: MythicMobs Placeholder**

```yaml
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid>"} @self
  - message{msg="<caster.ai.answer{type=Fox}>"} @trigger
```

> Plugin commands are console-only. Each `UUID_personality` combination has independent history.

---

## Utility Skills

Provides three basic actions: timed delay, proactive notification, and server-wide broadcast:

```
Player: Check my inventory first, wait 10 seconds then list my diamonds    → Non-blocking delay
Admin: Write a server announcement for double XP this weekend              → AI-polished broadcast
```

---

## Command Execution

AI executes server commands as the player, fully inheriting the server's permission system:

```
Player: Take me back to my death point  → Executes /back
```

Disabled by default, requires manual enable. AI cannot exceed player's own permissions.

---

## Sound & Particle Effects

```
Player: Play a level-up sound   → Plays ENTITY_PLAYER_LEVELUP (only you hear it)
Player: Show some heart particles → Displays HEART particles (only you see them)
```

Effects visible/audible only to the triggering player. Triggered via natural language.

---

## Commands & Permissions

### Command List

| Command | Permission | Description |
|:-:|:-:|:-:|
| `/kilacraft <message>` | None | Chat with AI |
| `/kila` `/ai` `/zm` | None | Aliases |
| `/kilacraft chat` | None | Toggle continuous chat mode |
| `/kilacraft clear` | `kilacraft.clear.self` | Clear own chat history |
| `/kilacraft clear <player>` | `kilacraft.clear.other` | Clear specified player's history |
| `/kilacraft reload` | `kilacraft.reload` | Reload config |
| `/kilacraft knowledge reload` | `kilacraft.knowledge` | Reload knowledge base |
| `/kilacraft personalities reload` | `kilacraft.personalities` | Reload personality config |
| `/kilacraft afk` | `kilacraft.afk` | View AFK tasks |
| `/kilacraft afk cancel` | `kilacraft.afk` | Cancel AFK task |
| `/kilacraft tasks` | `kilacraft.tasks` | View scheduled task status (default OP) |
| `/kilacraft plugins ...` | Console only | Third-party plugin integration |

### Skill Permissions

| Permission Node | Default | Description |
|:-:|:-:|:-:|
| `kilacraft.api.player.inventory` | true | Query player inventory |
| `kilacraft.api.player.status` | true | Query player status |
| `kilacraft.api.player.info` | true | Query player info |
| `kilacraft.api.world.info` | true | Query world info |
| `kilacraft.api.server.info` | true | Query server info |
| `kilacraft.cmi.query` | true | CMI info queries |
| `kilacraft.cmi.teleport` | true | CMI teleportation |
| `kilacraft.bukkit_fx` | true | Sound & particle effects |
| `kilacraft.bukkit_stats` | true | Vanilla stats queries |
| `kilacraft.command.execute` | op | Command execution (OP only by default) |
| `kilacraft.tasks` | op | View scheduled task status (OP by default) |

> Wildcards `kilacraft.api.*` and `kilacraft.cmi.*` include all sub-permissions.

---

## Security

### Player Data Isolation

Built-in non-cooperative security filter runs before every Skill execution:

- Scans all Skill parameter values for other online player names
- If not the player themselves and not whitelisted → automatically replaced with current player name (sanitization), Skill continues execution
- Whitelist mechanism: Skills that legitimately need to reference other players (CMI teleport, AFK tasks, etc.) are pre-whitelisted

### Third-Party Skill Protection

Even if a third-party Skill attempts to operate on other players, the security filter automatically sanitizes inputs.

### Skill Global Registry

Usage statistics and security review status of all registered third-party Skills are available in real-time at the [Skill Global Registry](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html). Reviewed Skills are marked with a 🟢 badge, helping server owners decide whether to install them.

---

## Open SPI Interface

Third-party plugin developers can expose their features to AI via the Skill SPI interface:

- **API JAR** only 5 KB (compileOnly dependency)
- Implement `SkillProvider` interface to register custom Skills
- Built-in error isolation — third-party Skill exceptions don't affect core functionality

See [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide.md).

---

## FAQ

**Q: Is the API expensive?**

With DeepSeek as an example, a single conversation costs approximately ¥0.001-0.002. Setting cooldown time (default 5 seconds) effectively controls costs.

**Q: Which LLMs are supported?**

All OpenAI-compatible providers. Thinking/reasoning models (like deepseek-reasoner, o1) are not supported.

**Q: Will it lag the server?**

No. All API requests are async. Memory usage 8-50 MB, HTTP connection pool reuse, supports streaming to reduce latency.

**Q: How to update?**

Back up `plugins/Kilacraft-AI/` to preserve configs, replace the JAR, restart the server.

---

## Community

<table>
<tr>
<td width="50%">

**Source Code**

[![GitHub](https://img.shields.io/badge/GitHub-axy--yxa/Kilacraft--AI-181717?logo=github)](https://github.com/axy-yxa/Kilacraft-AI)

</td>
<td width="50%">

**Community**

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord)](https://discord.gg/nNmhcZHDxr)

</td>
</tr>
<tr>
<td>

**Documentation**

[![English Wiki](https://img.shields.io/badge/English_Wiki-View_Docs-0B8FDC)](https://github.com/axy-yxa/Kilacraft-AI/wiki)
[![Skill Registry](https://img.shields.io/badge/Skill_Registry-View-4CAF50)](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html)

</td>
<td>

**Contributing**

[![Issues](https://img.shields.io/badge/Submit_Issue-GitHub-orange)](https://github.com/axy-yxa/Kilacraft-AI/issues)
[![PR](https://img.shields.io/badge/Submit_PR-Welcome-brightgreen)](https://github.com/axy-yxa/Kilacraft-AI/pulls)

</td>
</tr>
</table>

MIT License — If you find this useful, a ⭐ Star would be appreciated!
