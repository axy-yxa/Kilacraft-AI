<div align="center">

# Kilacraft-AI <sub>[中文](README.zh.md)</sub>

**The first plugin that gives every server an AI agent that understands you better the more you use it**

[![Version](https://img.shields.io/badge/Version-2.0.2-orange)](https://github.com/axy-yxa/Kilacraft-AI/wiki/Changelog)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/nNmhcZHDxr)
[![GitHub](https://img.shields.io/badge/GitHub-Kilacraft--AI-blue?logo=github)](https://github.com/axy-yxa/Kilacraft-AI)

</div>

---

## Features

**Core**

| | Feature | Description |
|:---:|:---|:---|
| 🤖 | AI Engine | LLM intent recognition · Multi-step task planning & execution · 5 output carriers · Streaming output · Public broadcast |
| 🔍 | Bukkit API | 72 read-only APIs · YAML data-driven · Multi-step data passing · Fine-grained permissions |
| 💰 | Economy | GlobalMarketPlus integration · Natural language balance/price queries · AI-powered trading (sell/buy/transfer/auction) |
| 🎮 | CMI Integration | Teleport (home/warp/TPA) · Enhanced player info (playtime/AFK/vanish/online list) |
| 📊 | Vanilla Stats | 80+ statistics query · Knowledge base driven retrieval · Distance/time auto-conversion |
| 🔧 | Command Execution | Execute commands as player · Inherits permissions · Config toggle + permission node |
| 🎨 | Sound & Particles | AI-triggered sounds/particles · Only caller perceives · YAML-driven config |

**Intelligence**

| | Feature | Description |
|:---:|:---|:---|
| 🧠 | Player Profile | 8-dimension behavioral analysis · Incremental updates · Historical snapshots · Dynamic prompt injection |
| 🕸️ | Social Relations | Private message/TPA/skill interaction tracking · Diminishing incremental strength · Friend milestone cross-promotion |
| 👋 | Smart Greetings | Personalized login greetings · Three-category aggregation (own events/friend dynamics/session highlights) |
| 🎭 | Personalization | Multi-personality system · RAG knowledge enhancement (HanLP + BM25 + Embedding) · Custom dictionary |
| 🔔 | AFK Tasks | 20 types (19 event listeners + custom polling) · Natural language creation · Notification/callback dual mode |
| 🏥 | Server Admin | Spark + AI diagnostics · Player behavior analysis · Audit logs · Discord/DingTalk alerts |

**Infrastructure**

| | Feature | Description |
|:---:|:---|:---|
| 💾 | Data Persistence | Conversations/profiles/social/events/audit persisted · H2 zero-config / MySQL hot-swap |
| 🔌 | SPI Extension | Third-party Skill registration · Plugin command mode · Global Skill registry |
| 🛡️ | Security | Non-cooperative value scanning · Cross-player ops blocked · Skill permission pre-filter |

## Quick Start

**1.** Download [Kilacraft-AI-2.0.2.jar](https://github.com/axy-yxa/Kilacraft-AI/releases) and place it in `plugins/`

**2.** Edit `plugins/Kilacraft-AI/llm.yml` with your API key:

```yaml
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-chat"
```

**3.** Restart the server, then test with `/ai Hello`

> Supports all OpenAI-compatible APIs — DeepSeek / Zhipu AI / Moonshot / SiliconFlow (China) · OpenAI / Groq / Gemini / OpenRouter (International). Just change `api_url` and `model`.

## Examples

```
Player: How much is diamond?
  AI: Diamond current price: $100.00/item (15 in stock)

Player: Check diamond price, see if I can afford 10
  AI: Diamond: $80.00/item, Balance: $12,580 → 10 costs $800, sufficient funds!

Player: Watch for Steve to come online
  AI: Monitoring task created.
  🔔 Steve is online!

Player: Alert me when my health drops below 5
  AI: Monitoring task created.
  🔔 Your health has dropped to 4.0!
```

## Compatibility

| Minecraft | Java | Server | Status |
|---|---|---|---|
| 1.16.5 | 17+ | Paper/Purpur/Leaf/Folia | ⚠️ Requires `-DPaper.IgnoreJavaVersion=true` |
| 1.17 - 1.19 | 17+ | All major cores | ✅ |
| 1.20 - 1.21+ | 21+ | All major cores | ✅ |

> Optional dependencies: MythicMobs 5.12+ (NPC placeholders) · GlobalMarketPlus 1.3.8+ (economy) · CMI (teleport/query) · Spark (performance analysis, Paper 1.21+ etc. have it built-in) · Vault (multi-currency)
> Features auto-disable when plugins are missing — core chat remains unaffected.

## Skills

| Category | Capabilities | Dependency |
|:---:|:---|:---:|
| **Bukkit API** | 72 read-only interfaces: player inventory/status/info, world info, server info, environment awareness | None |
| **Vanilla Stats** | 80+ vanilla cumulative stat queries, knowledge base driven retrieval, auto unit conversion | None |
| **Global Market** | Search/list/collect/buy-order/delist/transfer/auction/bulk-sell/bulk-buy (9 operations) | GlobalMarketPlus |
| **CMI Integration** | 5 queries (home/warp/player info/online/AFK) + 3 teleports | CMI |
| **AFK Tasks** | 19 event listeners + custom polling, natural language creation | None |
| **Utility** | Timed delay, proactive notification, server-wide broadcast | None |
| **Command Execution** | Execute commands as player, inherits permissions (disabled by default) | None |
| **Sound & Particles** | AI-triggered sounds/particles, only caller perceives | None |
| **Server Admin** | Health monitoring, player analysis, audit logs | Spark (optional) |

## Commands

| Command | Permission | Description |
|---|---|---|
| `/kila <message>` | None | Chat with AI (`/kilacraft`, `/ai`, `/zm` all work) |
| `/kilacraft chat` | None | Toggle continuous chat mode |
| `/kilacraft clear [player]` | `clear.self` / `clear.other` | Clear chat history |
| `/kilacraft reload` | OP | Reload config and language files |
| `/kilacraft knowledge reload` | OP | Reload knowledge base |
| `/kilacraft personalities reload` | OP | Reload personality config |
| `/kilacraft afk [cancel]` | None | View/cancel AFK tasks |
| `/kilacraft tasks` | `kilacraft.tasks` | View scheduled task status (default OP) |
| `/kilacraft profile start [seconds]` | `admin.health` | Start manual profiling (30-120s) |
| `/kilacraft profile status` | `admin.health` | View profiling status |
| `/kilacraft profile stop` | `admin.health` | Abort profiling and discard data |
| `/kilacraft notify test` | `admin.health` | Test external notification channels |
| `/kilacraft plugins <personality> <message> <UUID> [callback]` | Console | Third-party plugin integration |

## Documentation

| Document | Description |
|---|---|
| [Server Owner Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/Server-Owner-Guide) | Complete setup & troubleshooting |
| [Admin Features Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/Server-Owner-Guide#server-health-monitoring) | Health monitoring / Player analysis / Audit logs |
| [Changelog](https://github.com/axy-yxa/Kilacraft-AI/wiki/Changelog) | Version history |
| [Bukkit API Reference](https://github.com/axy-yxa/Kilacraft-AI/wiki/Bukkit-API-Reference) | 72 API details |
| [AFK Task Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/AFK-Task-System-Guide) | AFK task system architecture |
| [Personality Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/Personality-System-Guide) | AI persona management |
| [Knowledge Base Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/Knowledge-Base-Guide) | RAG knowledge base config |
| [Skill SPI Doc](https://github.com/axy-yxa/Kilacraft-AI/wiki/Skill-SPI-Integration-Guide) | Third-party Skill development |
| [Skill Global Registry](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html) | Real-time Skill usage stats & security review |

## Developers

- **Building** requires JDK 21+ (MythicMobs 5.12+ dependency), output JAR runs on Java 17+
- Third-party plugins can register custom Skills via **Skill SPI** → [Integration Guide](https://github.com/axy-yxa/Kilacraft-AI/wiki/Skill-SPI-Integration-Guide)
- Contributions welcome via [Issues](https://github.com/axy-yxa/Kilacraft-AI/issues) / Pull Requests

## Submit Skill for Review

Want your Skill to be whitelisted in the global registry? After review, users will see 🟢 Verified badge on the [Skill Global Registry](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html).

**How to submit**: Create a new [Issue](https://github.com/axy-yxa/Kilacraft-AI/issues) with title `[Skill Review] Your Skill Name`

**Required information:**
1. **Skill name** (must match registration)
2. **Source code or JAR** (required for security review)
3. **Description** (brief explanation of what the Skill does)
4. **Permissions** (Bukkit permissions needed or plugin dependencies)
5. **Documentation** (optional, if you have usage docs or Wiki)

**Review criteria:**
- ✅ Does not manipulate other players' data (unless explicitly stated and justified)
- ✅ Does not execute dangerous commands (op-level commands, file I/O, etc.)
- ✅ No malicious network requests or data exfiltration
- ✅ Proper resource cleanup (no memory leaks)

Once approved, I'll add your Skill to the whitelist and mark it as 🟢 Verified on the registry page.

---

<div align="center">

This project is fully open-source and free. Sponsorship is purely voluntary. Your support keeps me motivated ❤️

**🎁 Sponsor perks:** 💬 Exclusive Discord channel · 🎫 Priority support · 🚀 Early access to new versions

<a href="https://ko-fi.com/zmmmm"><img src="Ko-fi.jpg" width="350" alt="Support on Ko-fi" /></a>

<a href="https://ko-fi.com/zmmmm"><img src="ko-fi-button.png" alt="Support on Ko-fi" height="40" /></a>

*Please keep your payment screenshot after sponsoring, then contact me on Discord to activate your perks*

</div>

---

<div align="center">

MIT License · Made by [Zm_Mmm](https://github.com/axy-yxa) · <a href="https://discord.gg/nNmhcZHDxr"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white" height="20" valign="middle" alt="Discord" /></a>

Custom development / Server-specific optimization · [Discord](https://discord.gg/nNmhcZHDxr)

</div>
