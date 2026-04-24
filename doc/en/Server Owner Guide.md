# Kilacraft-AI

> A Minecraft AI Agent plugin based on LLM intent recognition and skill execution framework, enabling natural language interaction with servers

[![GitHub Tag](https://img.shields.io/github/v/tag/axy-yxa/Kilacraft-AI?label=Release&color=blue)](https://github.com/axy-yxa/Kilacraft-AI/tags)
[![MC](https://img.shields.io/badge/MC-1.16.5--1.21+-green.svg)](https://github.com/axy-yxa/Kilacraft-AI)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/axy-yxa/Kilacraft-AI/blob/main/LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2.svg)](https://discord.gg/nNmhcZHDxr)

---

## Version Compatibility

**Minimum Requirements: Minecraft 1.16.5 + Java 17**

| Minecraft Version | Java Requirement | Supported Servers | Notes |
|:-:|:-:|:-:|:-:|
| **1.16.5** | Java 17+ | Paper / Purpur / Leaf / Folia | CraftBukkit/Spigot require `-DPaper.IgnoreJavaVersion=true` |
| **1.17 - 1.19** | Java 17+ | All | Spigot / Paper / Purpur / Leaf / Folia / CraftBukkit |
| **1.20 - 1.21+** | Java 21+ | All | Server requires Java 21 |

One JAR package compatible with all versions, developed on Spigot 1.16.5 API, fully supporting Folia regional thread scheduling.

---

## Core Advantages

- **Zero Middleware** — One JAR file to run, no database, Redis or external services required
- **Extremely Low Memory Usage** — 8-12 MB on small servers, 30-50 MB on large servers, async non-blocking design
- **Out of the Box** — 5-minute setup, supports all OpenAI-standard LLM providers
- **Highly Customizable** — Knowledge base, personality system, intent prompts, output channels all configurable
- **Open Ecosystem** — SPI interface allows third-party plugins to register custom Skills in 5 minutes
- **Bilingual Support** — Complete internationalization architecture, switch with `language: zh/en`

---

## Quick Start

### 1. Installation

Download the latest `Kilacraft-AI.jar`, place it in the server `plugins/` directory, and start the server.

### 2. Configure API

Edit `plugins/Kilacraft-AI/config.yml` and enter your LLM API information:

```yaml
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-chat"
```

Supports all OpenAI-standard providers (DeepSeek, Zhipu AI, Moonshot, OpenAI, Groq, SiliconFlow, Gemini, OpenRouter, etc.). Simply change `api_url` and `model` to switch.

### 3. Test

```
/kila Hello
```

If you see an AI reply, configuration is successful.

> Use `/kilacraft reload` to reload config, `/kilacraft knowledge reload` for knowledge base, and `/kilacraft personalities reload` for personalities.

---

## Intelligent Dialogue

### Three Interaction Modes

```
# Command Mode
/kila How do I get diamonds?

# Continuous Chat Mode
/kilacraft chat
> I want to build a farm
> AI: Great idea! What crops would you like to grow?
> Wheat please
> AI: To plant wheat you'll need a hoe, water bucket, and seeds...

# Keyword Trigger (Public Channel)
@ai How to do this?
```

Command aliases: `/kilacraft`, `/kila`, `/ai`, `/zm`

### Stream Output

When enabled, AI replies display character by character in real-time:

```yaml
output:
  stream:
    enabled: true
```

Supports 5 output channels, configurable per scenario:

| Channel | Effect | Suitable For |
|:-:|:-:|:-:|
| **SIDEBAR** | Right sidebar, non-intrusive | Long text replies (recommended) |
| **BOSS_BAR** | Top bar | Medium-length text |
| **ACTION_BAR** | Above hotbar | Short text prompts |
| **CHAT** | Chat channel | Default |
| **TITLE** | Center screen | Short highlighted text |

### AI Response Sound Effect

Automatic sound effect when AI starts responding, only audible to the triggering player:

```yaml
output:
  sound:
    enabled: true
    sound_name: "ENTITY_PLAYER_LEVELUP"
    volume: 0.5
    pitch: 1.2
```

---

## Knowledge Base Enhancement

Teach AI about your server rules, gameplay, and FAQ. Simply place Markdown or TXT files in `plugins/Kilacraft-AI/knowledge/`:

```markdown
# server_rules.md

## How to claim land?
Use the /claim command to define your territory. Requires at least 10 gold coins.

## How to earn money?
You can earn money by mining, fishing, or selling items in player shops.
```

Execute `/kilacraft knowledge reload` to load, and AI will automatically retrieve and reference when players ask questions:

```
Player: How can I claim land?
AI: You can use the /claim command to define your territory. Requires at least 10 gold coins.
```

Supports custom dictionary to add server-specific terms for significantly improved retrieval accuracy:

```yaml
knowledge:
  custom_dictionary:
    enabled: true
    words:
      - "claim land"
      - "territory"
      - "redstone"
      - "mob farm"
```

---

## Intent Recognition & Task Orchestration

Kilacraft-AI doesn't just answer questions — it understands players' true intent and automatically calls corresponding functions:

```
User Input → LLM Intent Recognition → Execute Skill/Task → Analyze Results → Generate Natural Language Response
```

### Single Intent

Simple queries execute directly:

```
Player: How much are diamonds?
AI: Diamond current price: $100.00 each
```

### Multi-Step Task

Complex requests automatically decompose into ordered steps:

```
Player: Check if someone is selling the item I'm holding and how much it costs
  ↓
Step 1: Query player held item → Diamond Sword
Step 2: Query Diamond Sword market availability → Available, stock 2
Step 3: Query Diamond Sword price → $500.00 each
  ↓
AI: You're holding a Diamond Sword. There are 2 available on the market at $500.00 each.
```

Steps support automatic data passing (`{step_1.item_name}`) and array indexing (`{step_1.homes[0].home_name}`).

### Error Tolerance

- Partial step failures don't interrupt the entire flow
- Intent recognition failure automatically falls back to normal AI dialogue

---

## Vanilla Data Query

### Bukkit API (72 Built-in Interfaces)

AI directly queries player status, world info, and server info without coding:

```
Player: What am I holding?     → You're holding: Diamond Sword x1
Player: How much health do I have? → Health: 18.5/20.0
Player: What time is it?       → World time: 06:00 (Morning)
Player: How's the weather?     → Current weather: Clear
Player: How many people are online? → Online players: 15/100
```

Coverage categories:

| Category | Count | Examples |
|:-:|:-:|:-:|
| Player Inventory | 2 | Main hand/offhand, armor, backpack, ender chest |
| Player Status | 10+ | Health, hunger, oxygen, experience, on fire, frozen, potion effects, sneaking/running... |
| Player Info | 15+ | Location, game mode, flying, ping, death point, target block, locale... |
| World Info | 20+ | Time, weather, biome, temperature, humidity, entity stats, raids... |
| Server Info | 7+ | Online players, version, MOTD, world list, TPS... |
| Environmental Awareness | 14+ | Block below, last damage cause, world border, invulnerability frames, fall distance... |

All APIs are read-only operations, each category has independent permission nodes for fine-grained control via LuckPerms.

### Vanilla Statistics

Query players' Minecraft vanilla career statistics:

```
Player: How many times have I died?   → Total deaths: 42
Player: How many zombies have I killed? → Killed entities (Zombie): 15
Player: How far have I walked?       → Walking distance: 12.5 kilometers
Player: How many diamond ores have I mined? → Mined blocks (Diamond Ore): 128
```

Supports 80+ statistic enums, with automatic unit conversion (centimeters→kilometers, ticks→readable time).

---

## AFK Task System

Let AI "keep an eye out" for you. Create background monitoring tasks through natural language, with automatic notifications or actions when conditions are met.

### Event Listeners (12 Types)

```
Player: Help me watch for Steve to come online
AI: OK! I'll notify you as soon as Steve comes online.

[30 minutes later...]
🔔 Steve has joined the server!
```

```
Player: Watch for Steve to come online, then check what he's holding
AI: OK! When Steve comes online, I'll automatically check his held item.

[Steve comes online...]
🔔 Steve is online! Detected him holding Diamond Sword x1 in main hand.
```

| Monitoring Type | Description |
|:-:|:-:|
| Player Join/Leave | Monitor specified player login/logout |
| Player Death/Respawn | Monitor death events and respawn |
| Player Teleport/World Switch | Monitor position changes |
| Level Change | Monitor player level up/down |
| Weather Change | Monitor world weather |
| Sleep/Item Break | Enter bed, leave bed, item durability break |

### Custom Condition Polling

Monitor numerical conditions from any Skill:

```
Player: Tell me when my health drops below 10
Player: Remind me when my balance drops below 1000
Player: Check diamond price when I reach level 30
```

Management commands: `/kilacraft afk` to query, `/kilacraft afk cancel` to cancel. One task per player at a time.

---

## Personality System & NPC Dialogue

### Personality Configuration

Define different AI personalities in `personalities.yml`:

```yaml
common_prompt: "You are a Minecraft server NPC talking to player {player}."

Fox:
  You are a clever fox NPC who speaks playfully and cutely.
  Like to end sentences with "~", often use emojis.

Strict Teacher:
  You are a strict Minecraft teacher with high standards.
  Speak concisely and directly, but patiently answer questions.
```

### NPC Intelligent Dialogue

Two ways to give MythicMobs NPCs independent personalities:

**Method 1: Callback Commands** (Recommended)

```yaml
# MythicMobs skill configuration
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

AI generates a reply and automatically executes the callback command. Your plugin receives and displays the response.

**Method 2: MythicMobs Placeholder**

Use the built-in placeholder `<caster.ai.answer{type=personality_name}>` to directly get AI replies:

```yaml
# MythicMobs skill configuration (first trigger AI generation, then read reply with placeholder)
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid>"} @self
  - message{msg="<caster.ai.answer{type=Fox}>"} @trigger
```

The placeholder reads and consumes the cached AI reply, returning `UNDEFINED` if not ready. Supports dynamic parameters (e.g., `{type=<skill.puppet>}`).

> Plugin commands are console-only, each `UUID_personality` combination has independent history, and callback commands support `{response}` placeholder.

---

## Plugin Integration

### GlobalMarketPlus (Economy System)

```
Player: How much are diamonds?       → Diamond current price: $100.00 each
Player: How much balance do I have?  → Your balance: $12,580
Player: What's on the market?       → Current products: Diamond x15, Iron Ingot x32...
Player: Is diamond for sale?         → Diamond is available, stock 2
```

7 read-only query actions, no item or money consumption.

### CMI (Teleport & Player Info)

```
Player: Take me home           → AI queries home list and teleports to specified home
Player: Teleport to spawn      → AI queries warp list and teleports to specified warp
Player: Teleport to Steve      → AI sends TPA request
Player: Is Steve online?       → Steve is currently online, AFK: No
Player: What warps are there?  → Current warps: Spawn, Resource Area, PVP Arena...
```

8 actions (5 queries + 3 teleports), teleport uses TPA request mode, AFK/vanish status automatically detected.

### Sound & Particle Effects

```
Player: Play a level up sound   → Plays ENTITY_PLAYER_LEVELUP (only audible to you)
Player: Show heart particles   → Shows HEART particles (only visible to you)
```

Effects are only visible/audible to the triggering player, triggered through natural language.

### Command Execution

AI executes server commands as the player, fully inheriting the server permission system:

```
Player: Help me return to my death point  → Executes /back
```

Disabled by default, requires manual enablement. AI cannot exceed player permissions.

### Optional Dependencies

| Plugin | Version | Features |
|:-:|:-:|:-:|
| **CMI** | 9.8.6.4+ | Teleport, homes, warps, enhanced player info, TPA |
| **GlobalMarketPlus** | 1.3.8.0+ | Market queries, balance, prices, product lists |
| **MythicMobs** | 5.12.0+ | NPC placeholders (let NPCs display AI replies) |
| **Vault** | Latest | Multi-currency system support |

Corresponding features automatically disable when not installed, core dialogue remains unaffected.

---

## Open SPI Interface

Third-party plugin developers can expose their features to AI through the Skill SPI interface:

- **API JAR** only 5 KB (compileOnly dependency)
- Implement `SkillProvider` interface to register custom Skills
- Automatic discovery and registration through Bukkit `ServicesManager`
- Built-in error isolation, third-party Skill exceptions don't affect core processes

Suitable for integrating territory systems, leaderboards, RPG systems, guild systems, achievement systems, world management, and more.

See [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide).

---

## Commands & Permissions

### Command List

| Command | Permission | Description |
|:-:|:-:|:-:|
| `/kilacraft <message>` | None | Chat with AI |
| `/kila` `/ai` `/zm` | None | Shorthand commands |
| `/kilacraft chat` | None | Enter/exit continuous chat mode |
| `/kilacraft clear` | `kilacraft.clear.self` | Clear your own chat history |
| `/kilacraft clear <player>` | `kilacraft.clear.other` | Clear specified player's history |
| `/kilacraft reload` | `kilacraft.reload` | Reload configuration |
| `/kilacraft knowledge reload` | `kilacraft.knowledge` | Reload knowledge base |
| `/kilacraft personalities reload` | `kilacraft.personalities` | Reload personality configuration |
| `/kilacraft afk` | `kilacraft.afk` | Query AFK task |
| `/kilacraft afk cancel` | `kilacraft.afk` | Cancel AFK task |
| `/kilacraft plugins ...` | Console-only | Third-party plugin calls |

### Skill Permissions

| Permission Node | Default | Description |
|:-:|:-:|:-:|
| `kilacraft.api.player.inventory` | true | Query player inventory |
| `kilacraft.api.player.status` | true | Query player status |
| `kilacraft.api.player.info` | true | Query player info |
| `kilacraft.api.world.info` | true | Query world info |
| `kilacraft.api.server.info` | true | Query server info |
| `kilacraft.cmi.query` | true | CMI info query |
| `kilacraft.cmi.teleport` | true | CMI teleport functionality |
| `kilacraft.bukkit_fx` | true | Sound and particle effects |
| `kilacraft.bukkit_stats` | true | Vanilla statistics query |
| `kilacraft.command.execute` | op | Command execution (OP only by default) |

Wildcards `kilacraft.api.*` and `kilacraft.cmi.*` include all corresponding sub-permissions.

---

## Security Mechanisms

### Player Data Isolation

Built-in non-cooperative security filtering mechanism that automatically runs before every Skill execution:

- Scans all values in Skill parameters to detect if they contain other online player names
- Non-self and not in whitelist → automatically replaced with current player name (sanitization), Skill continues execution
- Whitelist mechanism: Skills requiring other player operations (CMI teleport requests, AFK tasks, command execution) are pre-whitelisted

### Built-in Skill Security

| Skill | Security |
|:-:|:-:|
| Bukkit API Query | Read-only, only calls getter methods |
| Vanilla Statistics | Read-only |
| Market Query | Read-only, no item or money consumption |
| CMI Teleport | TPA request mode, CMI handles permissions |
| Command Execution | Executes as player, fully inherits server permissions |
| Sound/Particles | Only visible/audible to caller |
| AFK Tasks | Whitelist allowed, concurrency re-entry protection |

### Third-Party Skill Protection

Even if third-party Skills attempt to operate on other players, the security filter automatically sanitizes. Review code sources before installation.

---

## Frequently Asked Questions

**Q: Are API costs high?**
With DeepSeek, a single conversation costs about ¥0.001-0.002. Setting cooldown (default 5 seconds) effectively controls costs.

**Q: Which LLMs are supported?**
All OpenAI-standard providers: DeepSeek, Zhipu AI, Moonshot, OpenAI, Groq, SiliconFlow, Gemini, OpenRouter, etc. Reasoning models (like deepseek-reasoner, o1) are not supported.

**Q: Will it lag the server?**
No. All API requests are asynchronous, memory usage 8-50 MB, HTTP connection pool reused, streaming response reduces latency.

**Q: How to update?**
Backup `plugins/Kilacraft-AI/` directory to preserve config, replace JAR file, restart server.

---

## Community & Resources

<table>
<tr>
<td width="50%">

**Source Code**

[![GitHub](https://img.shields.io/badge/GitHub-axy--yxa/Kilacraft--AI-181717?logo=github)](https://github.com/axy-yxa/Kilacraft-AI)
[![Gitee](https://img.shields.io/badge/Gitee-zm__mmm/kilacraft--ai-C71D23?logo=gitee)](https://gitee.com/zm_mmm/kilacraft-ai)

</td>
<td width="50%">

**Community**

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?logo=discord)](https://discord.gg/nNmhcZHDxr)
[![QQ Group](https://img.shields.io/badge/QQ%20Group-1094391147-12B7F5?logo=tencentqq)](https://qm.qq.com/q/1094391147)

</td>
</tr>
<tr>
<td>

**Documentation**

[![Wiki](https://img.shields.io/badge/Chinese_Wiki-View_Docs-0B8FDC)](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3%E7%B4%A2%E5%BC%95)
[![English Wiki](https://img.shields.io/badge/English_Wiki-View_Docs-0B8FDC)](https://github.com/axy-yxa/Kilacraft-AI/wiki)
[![Skill Registry](https://img.shields.io/badge/Skill_Registry-View_Stats-4CAF50)](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html)

</td>
<td>

**Feedback & Contribution**

[![Issues](https://img.shields.io/badge/Submit_Issue-GitHub-orange)](https://github.com/axy-yxa/Kilacraft-AI/issues)
[![PR](https://img.shields.io/badge/Submit_PR-Contributions_Welcome-brightgreen)](https://github.com/axy-yxa/Kilacraft-AI/pulls)

</td>
</tr>
</table>

MIT License — If you find it useful, give it a ⭐ Star!
