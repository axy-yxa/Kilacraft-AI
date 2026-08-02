# Kilacraft-AI

> The first plugin that gives every server an AI agent that understands you better the more you use it — powered by LLM intent recognition and skill execution framework

[![GitHub Tag](https://img.shields.io/github/v/tag/axy-yxa/Kilacraft-AI?label=Release&color=blue)](https://github.com/axy-yxa/Kilacraft-AI/tags)
[![MC](https://img.shields.io/badge/MC-1.16.5--1.21+-green.svg)](https://github.com/axy-yxa/Kilacraft-AI)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/axy-yxa/Kilacraft-AI/blob/main/LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2.svg)](https://discord.gg/nNmhcZHDxr)

***

## Version Compatibility

**Minimum Requirements: Minecraft 1.16.5 + Java 17**

| Minecraft Version | Java Requirement | Supported Servers |
| :---: | :---: | :---: |
| **1.16.5** | Java 17+ | Paper / Purpur / Leaf / Folia |
| **1.17 - 1.19** | Java 17+ | All |
| **1.20 - 1.21+** | Java 21+ | All |

> One JAR compatible with all versions. Developed on Spigot 1.16.5 API, fully supports Folia regional thread scheduling.

### Optional Dependencies

| Plugin | Version | Features |
| :---: | :---: | :---: |
| **CMI** | 9.8.6.4+ | Teleport, homes, warps, enhanced player info, TPA |
| **GlobalMarketPlus** | 1.3.8.0+ | Market queries, balance, prices, listings |
| **MythicMobs** | 5.12.0+ | NPC placeholders (display AI responses) |
| **Vault** | Latest | Multi-currency support |
| **Spark** | Latest | Server performance sampling & analysis (Paper 1.21+ etc. have it built-in) |

> Features auto-disable when plugins are missing. Core chat remains unaffected.

***

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

> Reload config with `/kila reload`, knowledge base with `/kila knowledge reload`, personalities with `/kila personalities reload`.

***

## Core Features

### AI Smart Chat

**Three Interaction Modes:**

```
# Command mode
/kila How do I get diamonds?

# Continuous chat mode
/kila chat
> I want to build a farm
> AI: Great idea! What crop do you want to plant?

# Keyword trigger (public chat)
@ai How do I craft this?
```

Command aliases: `/kilacraft`, `/kila`, `/ai`, `/zm`

**Streaming Output & Carriers:**

AI responses display character by character in real-time, eliminating wait. Supports 5 output carriers, configurable per scenario:

| Carrier | Effect | Best For |
| :---: | :---: | :---: |
| **SIDEBAR** | Right sidebar, no FOV obstruction | Long responses (recommended) |
| **BOSS\_BAR** | Top bar | Medium-length text |
| **ACTION\_BAR** | Above hotbar | Short notifications |
| **CHAT** | Chat box | Default |
| **TITLE** | Screen center | Short highlights |

**AI Response Sound:**

Plays a sound when AI starts responding, only the triggering player hears it. Configured in `output.yml`:

```yaml
output:
  sound:
    enabled: true
    sound_name: "ENTITY_PLAYER_LEVELUP"
    volume: 0.5
    pitch: 1.2
```

### Knowledge Base Enhancement

Let AI understand your server rules, gameplay, and FAQ. Just place Markdown or TXT files in `plugins/Kilacraft-AI/knowledge/`:

```markdown
# server_rules.md

## How to claim land?
Use the /claim command to define your territory. Costs at least 10 coins.
```

Run `/kila knowledge reload` to load. AI automatically retrieves and cites relevant content when players ask questions. Supports custom dictionaries for server-specific terminology to improve search accuracy.

### Web Search

Let AI break through training-data cutoff limits and query Minecraft version updates, mod recommendations, today's gold price/exchange rates, sports scores, wiki articles, and more in real time. Built-in **9 search-engine providers**, auto-routed by server language (`provider: auto`), or manually specified:

- 5 domestic (China): Zhipu AI, Baidu Qianfan, Volcengine Doubao, Qiniu Baidu, Aliyun Bailian IQS
- 4 international: Tavily, Brave, Exa, You.com

All providers offer free tiers, so you can start at zero cost; you can configure multiple providers as mutual backups. Server owners just fill in one API Key in `web.yml`. Supports time-range filtering (today / last week / last month) and automatic multi-step search (complex questions are split into up to 5 sub-searches). Requires `kilacraft.websearch` permission (default: all players, but only takes effect once the server owner configures an API Key).

### Web Fetch

Give AI a specific URL and it will fetch the page body, read it, then answer your questions about that page — e.g. "help me read what this tutorial page is about" or "what's the crafting recipe on this mod wiki page". Complements web search: search finds a list of pages by keyword, while fetch reads the body of a specific URL.

**Zero config — no API Key needed**, works out of the box. Built-in enterprise-grade SSRF protection (private-network address interception, anti-DNS-rebinding, forced HTTPS + per-hop redirect re-checking, byte-level hard limit on response body), so server owners can enable it with confidence. Requires `kilacraft.webfetch` permission (default: all players). Configured under the `web.fetch` section of `web.yml`.

### Conversation Suggestions

After AI finishes replying, 1-5 clickable "you might also want to ask" suggested questions are appended below the chat. Clicking sends them as a command — no typing needed. Generated intelligently from multi-turn conversation history + the summary of currently available skills, with a strict "quality over quantity" rule (no output when uncertain).

**Only triggers in command mode, not in continuous chat mode** — to preserve the immersive "chat naturally" feel. Players can turn it off with `/kila suggestion off` (on by default, no permission required); server owners can configure suggestion count, timeout, excluded scenarios/skills, and display wording under the `suggestion:` section of `behavior.yml`.

***

## Advanced Features

### Player Profile & Social Relations

**Player Profile:** AI automatically analyzes player conversation history and builds an eight-dimension behavioral profile for each player, dynamically injecting profile summaries as reference context in subsequent conversations. **AI gets smarter about your players over time.**

| Dimension | Description | Example |
| :---: | :---: | :---: |
| Playstyle | Gameplay preferences | Combat-oriented, Explorer, Builder |
| Personality | Behavioral patterns | Friendly, Humorous, Direct |
| Interests | Liked areas and activities | Economy & trading, Redstone, Building |
| Boundaries | Disliked content or behaviors | Don't use my name, Don't rush me |
| Communication | Preferred AI response style | Brief & direct, No emojis |
| Spatial Memory | Mentioned locations, base positions | Main base at desert (1200,64,-800) |
| Known Facts | Explicitly stated facts by the player | Steve is a friend, Home near desert temple |
| Special Observations | AI freeform observations | "This player has recently shown interest in enchanting" |

Automatically triggers analysis on player login/logout (triple gate mechanism to prevent wasting API calls). Configured in `llm.yml`:

```yaml
agent:
  profile:
    enabled: true
    min_interval_minutes: 30    # Minimum analysis interval
    min_message_delta: 20       # Minimum new message count
```

**Social Relations:** Automatically tracks interactions between players (private messages, TPA, skill interactions), building a social relationship graph. Relationship strength uses diminishing incremental algorithm — inactive relationships naturally decay over time.

- Friends' milestone events (boss kills, raid completions, pet deaths, etc.) appear in each other's login greetings
- AI can sense "who's friends with whom" and naturally mention friend dynamics in conversations

### Login Greeting

AI automatically sends personalized greetings when players log in. Based on player profiles, offline events, and friend dynamics, every greeting is unique.

**First Login:** Welcomes new players with an introduction to AI assistant features. Supports custom server info (configured via `server_info` under the `greeting` section of `behavior.yml`).

**Returning Login (Three-Category Data Aggregation):**

| Category | Data Source | Example |
| :---: | :---: | :---: |
| Own Events | Events that happened while player was offline | Items sold, payments received |
| Friend Dynamics | Friends' milestones during offline period | Friend killed the Ender Dragon |
| Session Highlights | Important events since last greeting | Pet killed, totem triggered, Warden defeated |

```
# First login
Hey Hub, welcome to the server! I'm your AI assistant, just use /ai to reach me.
I can help you check items, browse the market, run background tasks, and more.

# Returning (offline 3 days)
Welcome back! Your diamonds sold while you were away. Your friend Steve killed the Ender Dragon. Hub is also online now.

# Quick reconnect (offline 10 seconds)
Hey, back already?
```

### Personality System & NPC Dialogue

Define different AI personality styles in `personalities.yml`, and with MythicMobs, NPCs can have independent personalities and dialogue capabilities:

```yaml
common_prompt: "You are an NPC on a Minecraft server, talking to player {player}."

Fox: |
  You are a clever fox NPC who speaks playfully and cutely.
  Likes to end sentences with "~", often uses emojis.
```

**NPC Dialogue Methods:**

Method 1 (Callback Command, recommended):
```yaml
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

Method 2 (MythicMobs Placeholder):
```yaml
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid>"} @self
  - message{msg="<caster.ai.answer{type=Fox}>"} @trigger
```

> Plugin commands are console-only. Each `UUID_personality` combination has independent history.

### Guardian System (Proactive AI Watch)

AI upgrades from "only answers when asked" to "proactive watching". Once enabled, AI acts like a personal butler, speaking up proactively to remind you at moments you're **not currently focused on — but would care about afterward**.

```
Player: Turn on guardian
AI: OK, guardian is on. I'll keep an eye on what you can't see.

[While fighting mobs...]
⚠️ Your diamond sword durability is almost gone — don't let it break at a critical moment!

[While mining...]
⚠️ Your inventory is nearly full, 3 slots left — better head back and clean it out.

[Behind you...]
⚠️ Watch out! A creeper 5 blocks behind you has locked onto you!
```

**Proactive alerts cover three scenarios players easily overlook:**
- **Inventory nearly full**: proactively warns when free slots run low
- **Gear durability critical**: warns when held or worn gear is about to break
- **Hostile mob locked onto you from behind/side**: alerts when a hostile mob outside your field of view (within 8 blocks) targets you

Alert wording is generated on the fly by AI (not fixed templates), 1-2 natural sentences. It automatically stays silent when you open your inventory/item UI (you're already looking at item states); it auto-pauses after 5 minutes of no input (no wasted resources) and resumes the moment you act.

**Deliberately not done** for scenarios players can perceive themselves: hunger, health, oxygen, fire, negative effects, day/night weather, etc. — things you know by looking at the HUD or your body's reactions, which AI won't repeat. Creeper ignition (1.5s), projectile flight, and other windows shorter than AI response latency are also skipped.

Enable with `/kila guardian on`, or just tell the AI "turn on guardian". `off`/`status` need no permission (so players can turn off their own guardian even if permission was revoked). Requires `kilacraft.guardian` permission (default: all players, opt-in — players must enable it themselves). Configured under the `guardian:` section of `behavior.yml`.

### Player Custom Watch (WatchSkill)

Set an "auto-watch" with a single natural-language sentence; AI proactively reminds you when a condition is met or an event occurs. More capable and safer than the old AFK task system:

```
Player: Watch my iron ingots until they hit 64
AI: OK, I'll remind you when your iron ingots reach 64.

[When iron ingots reach 64...]
🔔 Your iron ingots have reached 64.
```

**Two types of watches:**

- **Condition watch**: watch a numeric value or status — "watch my iron ingots until 64", "remind me when health drops below 30%", "notify me when balance hits 10000". Supports watching the read-only queries of built-in skills (Bukkit stats, CMI, market queries, vanilla APIs), auto-detecting the value type (number/boolean/string) at runtime.
- **Event watch**: watch 11 high-value game events — furnace smelt complete, crop mature, boss kill, nearby entity spawn, player death, teleport, experience level up, world switch, block break, fishing, chat keyword.

When triggered it **only notifies AI, never auto-executes operations** (safer than the old AFK tasks — won't do anything while the player is offline). If a player briefly disconnects (within 5 minutes) and reconnects, watches auto-restore.

Performance is optimized via a global singleton event listener + reverse index — events cost nothing when no one subscribes; all condition watches for the same player are merged into a single timer. Requires `kilacraft.watch` permission (default: all players). Per-player limits: 3 condition watches / 5 event watches / 200 total across the server. Configured under the `watch:` section of `behavior.yml`.

### Cross-Player Online/Offline Subscription (PlayerWatchSkill)

Subscribe to a friend's online/offline notifications via natural language — "tell me when Steve comes online", "notify me for both Alex online and offline". **More capable than the old system**: supports subscribing to multiple players at once (the old system could only watch one at a time), has built-in anti-reordering (an offline notification cancels any not-yet-sent online notification, avoiding "offline before online" inversion), and online notifications are delayed 2 seconds to wait until the player has fully joined the server.

This is positioned as a lightweight social interaction — subscriptions live in memory only and aren't persisted, and are auto-cleared when the subscriber goes offline. Requires `kilacraft.player_watch` permission (default: all players), with a per-player limit of 5 subscriptions.

> The three new systems above (Guardian / Player Watch / Cross-Player Subscription) **replace the old AFK task system**. The old `/kila afk` command, `kilacraft.afk` permission, and `afk_task` config section have been removed. If you previously relied on the old AFK tasks, switch to these three new systems.

### Server Health Monitoring

Real-time server performance monitoring and AI-powered diagnostics based on Spark. A daemon thread runs 24/7 in the background with automatic anomaly detection and alerting.

**Automatic Monitoring:** The daemon thread polls TPS/MSPT/CPU metrics every 10 seconds. When thresholds are exceeded, it automatically launches Spark Profiler sampling, then calls the reasoning model to generate a diagnostic report upon completion.

**Manual Profiling:** Use for proactive troubleshooting:

```bash
/kila profile start [30-120]    # Start profiling (seconds)
/kila profile status            # View status
/kila profile stop              # Abort and discard
```

**Diagnostic Reports:** Includes server status overview, plugin timing and hot method trigger paths (self time), AI diagnostic conclusions and optimization recommendations. Reports are saved in `plugins/Kilacraft-AI/reports/` permanently.

**Alert Notifications:** Supports in-game notifications + Discord Webhook / DingTalk bot external push (automatic mode only). Use `/kila notify test` to test notification channels.

**Historical Queries:** Query historical alerts and reports via natural language:
- "What alerts have there been in the past day?"
- "List recent diagnostic reports"
- "I upgraded MythicMobs yesterday, check if performance has improved"

> Full configuration guide in `admin.yml`. Requires Spark plugin + reasoning model API key. Paper 1.21+/Folia/Purpur/Leaf/Pufferfish have Spark built-in.

### Player Behavior Analysis

Query your server's player ecosystem via natural language. Permission: `kilacraft.admin.player`.

- **Online Trends:** "What's the player online trend this week?" — Login/logout time distribution
- **Activity Rankings:** "Show the most active players leaderboard" — Login count, playtime, last login
- **New Player Influx:** "How many new players joined this week?" — Count and time distribution
- **Profile Coverage:** "How many players have AI profile analysis?" — Analyzed/pending count
- **Social Insights:** "Show player social network analysis" — Total relations, average strength, isolated players
- **Specific Player Social Relations:** "Check Steve's social relations" — Detailed social relations, strength, interaction frequency for a specific player

### Audit Log Query

Query AI skill usage via natural language. Permission: `kilacraft.admin.audit`.

- **Execution Records:** "Query what skills player Steve has used" — Skill name, parameters, results
- **Usage Statistics:** "Show skill usage statistics leaderboard" — Usage count, success/failure, avg duration
- **Error Tracking:** "Show failed skill execution records" — Failure records, error messages, time distribution

### AI Data Query

A set of read-only commands for players to check their own AI usage; admins can query any player or the whole server. Self-view requires `kilacraft.query.self` (default: everyone); viewing others / server-wide requires `kilacraft.usage.other` / `kilacraft.history.other` / `kilacraft.memory.other` respectively (default: OP).

- **Usage Stats** `/kila usage [player|all] [1d/3d/7d/30d]`: conversation turns, skill-call count & success rate, top skills, top active players. Defaults to your own last 7 days; `all` for server-wide
- **Conversation History** `/kila history [player] [page] [-f]`: paginated conversation records, chronological within a page (old→new); `-f` shows full content (long entries are truncated by default)
- **Player Profile** `/kila memory [player]`: login stats (first/last login, login count, total playtime) + AI-analyzed 8-dimension profile (hidden until analyzed)

> Offline players can be queried too: just enter the player name; the UUID is resolved in the background.

***

## AI Skill System

AI interacts with the server through Skills, each corresponding to a category of operations. All Skills are read-only queries (except explicitly marked write operations), with fine-grained permission control.

**Listing & manually running skills:**

- `/kila skills [page]`: lists the skills you currently have permission to trigger (same source as the permission pre-filter used during AI chat — you only see skills you can use)
- `/kila run <skill> <prompt>`: skips intent recognition and directly executes the named skill. Useful for precise triggering or debugging, e.g. `/kila run market_query check diamond price`. Falls back to normal chat if the intent can't be parsed

### Capabilities Overview

| Category | Capabilities | Dependency | Permission Node |
| :---: | :--- | :---: | :---: |
| **Bukkit API** | 71 built-in read-only interfaces: player inventory/status/info, world info, server info, environment awareness | None | `kilacraft.api.*` |
| **Vanilla Stats** | 80+ vanilla cumulative stat queries, knowledge base BM25 retrieval, auto unit conversion | None | `kilacraft.bukkit_stats` |
| **Global Market** | Search/list/collect/buy-order/delist/transfer/auction/bulk-sell/bulk-buy (9 operations) | GlobalMarketPlus | `kilacraft.market.*` |
| **CMI Integration** | 5 queries (home/warp/player info/online/AFK) + 3 teleports | CMI | `kilacraft.cmi.*` |
| **Guardian** | Proactive AI watch: inventory near-full, low durability, threat behind (see Advanced Features above) | None | `kilacraft.guardian` |
| **Player Watch** | Custom condition/event watches, 11 event types (see Advanced Features above) | None | `kilacraft.watch` |
| **Cross-Player Watch** | Subscribe to friends' online/offline notifications (see Advanced Features above) | None | `kilacraft.player_watch` |
| **Web Search** | Real-time web search, 9 search engine providers, time-range filtering | None (requires API Key) | `kilacraft.websearch` |
| **Web Fetch** | Fetch page body from a given URL, zero-config, built-in SSRF protection | None | `kilacraft.webfetch` |
| **Version Info** | Query plugin version, changelog, new version detection | None | `kilacraft.admin.info` |
| **Utility** | Timed delay, proactive notification, server-wide broadcast | None | `kilacraft.utility` |
| **Command Execution** | Execute commands as player, inherits permission system (disabled by default) | None | `kilacraft.command.execute` |
| **Sound & Particles** | AI-triggered sounds/particles, only caller perceives, YAML-driven config | None | `kilacraft.bukkit_fx` |
| **Server Admin** | Health monitoring, player analysis, audit logs (see Advanced Features above) | Spark (optional) | `kilacraft.admin.*` |

> Wildcards `kilacraft.api.*` and `kilacraft.cmi.*` include all sub-permissions respectively. New feature permissions (Guardian/Watch/WebSearch etc.) are all available to all players by default, but require opt-in (players must actively enable Guardian) or API Key configuration by the server owner for web search to function.

***

## Data & Security

### Data Persistence

Supports both H2 embedded database (default, zero-config) and MySQL. Data persists across restarts. MySQL recommended for multi-server data sharing. Hot-switch with `/kila reload`, auto-fallback on failure.

| Data | Description |
|------|------|
| Conversation History | All player-AI conversations, batch flushed every 30 seconds |
| Player Profiles | Eight-dimension behavioral analysis results |
| Social Relations | Interaction strength and type between players |
| Server Events | Milestone events like deaths, achievements, trades |
| Skill Audit | All Skill execution logs |
| Profile Snapshots | Profile historical versions |

> Data retention days configurable in `database.yml`. Expired data is automatically cleaned up. Group server data isolation.

### Security

- **Player Data Isolation**: Built-in non-cooperative security filter scans all Skill parameter values — non-self, non-whitelisted player names are automatically replaced (sanitization)
- **Third-Party Skill Protection**: Even if a third-party Skill attempts to operate on other players, the security filter automatically sanitizes inputs
- **Skill Global Registry**: Usage statistics and security review status of all registered third-party Skills at [Skill Global Registry](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html). Reviewed Skills marked as Verified

### Open SPI Interface

Third-party plugin developers can expose their features to AI via the Skill SPI interface:

- **API JAR** only 5 KB (compileOnly dependency)
- Implement `SkillProvider` interface to register custom Skills
- Built-in error isolation — third-party Skill exceptions don't affect core functionality

See [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide.md).

***

## Commands & Permissions

### Command List

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/kila <message>` | None | Chat with AI (`/kilacraft`, `/ai`, `/zm` all work) |
| `/kila chat` | None | Toggle continuous chat mode |
| `/kila clear` | `kilacraft.clear.self` | Clear own chat history |
| `/kila clear <player>` | `kilacraft.clear.other` | Clear specified player's history |
| `/kila reload` | `kilacraft.reload` | Reload config and language files |
| `/kila knowledge reload` | `kilacraft.knowledge` | Reload knowledge base |
| `/kila personalities reload` | `kilacraft.personalities` | Reload personality config |
| `/kila guardian on\|off\|status` | `kilacraft.guardian` (on) / none (off, status) | Enable/disable/check Guardian system (see "Guardian System" above) |
| `/kila suggestion on\|off\|status` | None (open to all) | Enable/disable/check chat suggestions |
| `/kila tasks` | `kilacraft.tasks` | View scheduled task status (default OP) |
| `/kila usage [player\|all] [1d/3d/7d/30d]` | `kilacraft.query.self` / `kilacraft.usage.other` | AI usage stats (see "AI Data Query" section) |
| `/kila history [player] [page] [-f]` | `kilacraft.query.self` / `kilacraft.history.other` | Conversation history (-f: full) |
| `/kila memory [player]` | `kilacraft.query.self` / `kilacraft.memory.other` | Player profile & 8 dimensions |
| `/kila skills [page]` | None | List available skills |
| `/kila run <skill> <prompt>` | Per-skill | Force-execute a skill, skipping intent recognition (player only) |
| `/kila doctor` | `kilacraft.admin.info` | Config self-diagnostic (collapsible grouped output) |
| `/kila cache [reset]` | `kilacraft.admin.cache` | View/reset LLM cache hit-rate statistics |
| `/kila about` | `kilacraft.admin.info` | Version & update check |
| `/kila profile start [seconds]` | `kilacraft.admin.health` | Start manual profiling |
| `/kila profile status` | `kilacraft.admin.health` | View profiling status |
| `/kila profile stop` | `kilacraft.admin.health` | Abort profiling and discard |
| `/kila notify test` | `kilacraft.admin.health` | Test external notification channels |
| `/kila plugins ...` | Console only | Third-party plugin integration |

### Permission Nodes

> Command and skill permissions are listed together; default `true` = all players, `op` = OP only.

| Permission Node | Default | Description |
| :--- | :---: | :--- |
| `kilacraft.api.player.inventory` | true | Query player inventory |
| `kilacraft.api.player.status` | true | Query player status |
| `kilacraft.api.player.info` | true | Query player info |
| `kilacraft.api.world.info` | true | Query world info |
| `kilacraft.api.server.info` | true | Query server info |
| `kilacraft.cmi.query` | true | CMI info queries |
| `kilacraft.cmi.teleport` | true | CMI teleportation |
| `kilacraft.bukkit_fx` | true | Sound & particle effects |
| `kilacraft.bukkit_stats` | true | Vanilla stats queries |
| `kilacraft.utility` | true | Utility tools (delay/notify) |
| `kilacraft.utility.broadcast` | op | Server-wide broadcast (OP only by default) |
| `kilacraft.command.execute` | op | Command execution (OP only by default) |
| `kilacraft.guardian` | true | Guardian system toggle (opt-in, must actively enable) |
| `kilacraft.watch` | true | Player custom watch |
| `kilacraft.player_watch` | true | Cross-player watch subscription |
| `kilacraft.websearch` | true | Web search (requires API Key configured) |
| `kilacraft.webfetch` | true | Web fetch (zero-config) |
| `kilacraft.tasks` | op | View scheduled task status (OP by default) |
| `kilacraft.query.self` | true | View own AI data (usage / history / profile) |
| `kilacraft.usage.other` | op | View others' usage & server-wide overview |
| `kilacraft.history.other` | op | View others' conversation history |
| `kilacraft.memory.other` | op | View others' player profile |
| `kilacraft.admin.health` | op | Server health monitoring, profiling |
| `kilacraft.admin.player` | op | Player behavior analysis |
| `kilacraft.admin.audit` | op | Audit log query |
| `kilacraft.admin.info` | op | Config self-diagnostic (doctor) / version check (about) |
| `kilacraft.admin.cache` | op | View LLM cache hit-rate statistics |
| `kilacraft.admin.*` | op | All admin features |

> Wildcards `kilacraft.api.*` and `kilacraft.cmi.*` include all sub-permissions respectively; `kilacraft.admin.*` includes all admin permissions (health / player / audit / info / cache). New feature permissions (Guardian/Watch/WebSearch etc.) default to available for all players, but are opt-in — players must actively enable Guardian or the server owner must configure API Keys for web search to take effect.

***

## FAQ

**Q: Is the API expensive?**

With DeepSeek as an example, a single conversation costs approximately ¥0.001-0.002. Setting cooldown time (default 5 seconds) effectively controls costs.

**Q: Which LLMs are supported?**

All OpenAI-compatible providers. Regular conversations do not support thinking/reasoning models (like deepseek-reasoner, o1), but the admin features' AI diagnostics support configuring a reasoning model (in `admin.yml`).

**Q: Will it lag the server?**

No. All API requests are async. Memory usage 8-50 MB, HTTP connection pool reuse, supports streaming to reduce latency.

**Q: How to update?**

Back up `plugins/Kilacraft-AI/` to preserve configs, replace the JAR, restart the server.

***

## Community & Resources

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
