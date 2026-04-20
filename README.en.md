<div align="center">

# Kilacraft-AI

**Lightweight AI Agent Plugin for Minecraft**

Plan-and-Execute Architecture · Natural Language · Zero Dependencies · Fully Open Source

[![Version](https://img.shields.io/badge/Version-1.4.6-orange)](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Changelog)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![GitHub](https://img.shields.io/badge/GitHub-Kilacraft--AI-blue?logo=github)](https://github.com/axy-yxa/Kilacraft-AI)
[![Gitee](https://img.shields.io/badge/Gitee-Kilacraft--AI-red?logo=gitee)](https://gitee.com/zm_mmm/kilacraft-ai)

</div>

---

Players interact with AI through natural language — query game data, execute commands, and orchestrate multi-step tasks.  
Built on Plan-and-Execute + Function Calling architecture. Single JAR, no database, no Redis, no middleware.

## Features

| | Feature | Description |
|:---:|:---|:---|
| 🤖 | **AI Engine** | LLM intent recognition · Multi-step task planning & execution · RAG knowledge base · 5 output carriers · Streaming output · Secondary analysis coordinator · Public broadcast · Response sound effects |
| 💰 | **Economy** | GlobalMarketPlus integration · Natural language balance/price/listing queries · Multi-item search |
| 🔍 | **Bukkit API** | 72 built-in read-only APIs · YAML data-driven · Multi-step data passing (with array indexing) · Fine-grained permissions |
| 🎮 | **CMI Integration** | Teleport (home/warp/TPA) · Enhanced player info (playtime/AFK/vanish/armor/online list) |
| 🔧 | **Command Execution** | Execute commands as player · Full server permission inheritance · Config toggle + permission node |
| 🎨 | **Sound & Particles** | AI-triggered sounds/particles · Only caller perceives · YAML-driven config |
| 📊 | **Vanilla Stats** | 80+ vanilla statistics query · Knowledge base BM25 retrieval · Distance/time auto-conversion · Multi-step condition monitoring |
| 🔔 | **AFK Tasks** | 12 types (11 event listeners + CUSTOM polling) · Natural language creation · Notification/callback dual mode |
| 🎭 | **Personalization** | Multi-personality system · RAG knowledge enhancement (HanLP + BM25) · Custom dictionary · Full language config |
| 🔌 | **SPI Extension** | Third-party Skill registration · Plugin command mode · Complete dev docs |
| 🛡️ | **Security** | Non-cooperative value scanning filter · Cross-player ops blocked unless whitelisted · Malicious Skills cannot bypass |

## Quick Start

**1.** Download [Kilacraft-AI-1.4.6.jar](https://github.com/Zm-Mmm/Kilacraft-AI/releases) and place it in `plugins/`

**2.** Edit `plugins/Kilacraft-AI/config.yml` with your API key:

```yaml
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-chat"
```

**3.** Restart the server, then test with `/kila Hello`

> Supports all OpenAI-compatible APIs (DeepSeek / Zhipu AI / Moonshot etc.). Just change `api_url` and `model`.

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

> Optional dependencies: MythicMobs 5.12+ (NPC placeholders) · GlobalMarketPlus 1.3.8+ (economy) · CMI (teleport/query) · Vault (multi-currency)  
> Features auto-disable when plugins are missing — core chat remains unaffected.

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
| `/kilacraft plugins <personality> <message> <UUID> [callback]` | Console | Third-party plugin integration |

## Documentation

| Document | Description |
|---|---|
| [Server Owner Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Server-Owner-Guide) | Complete setup & troubleshooting |
| [Changelog](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Changelog) | Version history |
| [Bukkit API Reference](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Bukkit-API-Reference) | 58 API details |
| [AFK Task Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/AFK-Task-System-Guide) | AFK task system architecture |
| [Personality Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Personality-System-Guide) | AI persona management |
| [Knowledge Base Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Knowledge-Base-Guide) | RAG knowledge base config |
| [Skill SPI Doc](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Skill-SPI-Integration-Guide) | Third-party Skill development |

## Developers

- **Building** requires JDK 21+ (MythicMobs 5.12+ dependency), output JAR runs on Java 17+
- Third-party plugins can register custom Skills via **Skill SPI** → [Integration Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Skill-SPI-Integration-Guide)
- Contributions welcome via [Issues](https://github.com/axy-yxa/Kilacraft-AI/issues) / Pull Requests

---

<div align="center">

MIT License · Made by [Zm_Mmm](https://github.com/axy-yxa) · <a href="http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=FlgSknDUfFcXVYsU7tqPZ8xMMBLgPJjg&authKey=pAJFrXh%2BPpr4V%2BbXhhVUWzoQN4j%2BOWitvi%2BcB59jx7S1JM21yL8kikQBhqm3Cgff&noverify=0&group_code=1094391147"><img src="https://img.shields.io/badge/QQ%20Group-1094391147-12B7F5?logo=tencent-qq" height="20" valign="middle" alt="QQ Group" /></a>

This project is fully open-source and free. If you find it useful, consider buying me a coffee ☕

<img src="sponsor.jpg" width="300" alt="Sponsor QR Code" />

Feel free to include your in-game name in the note!

**Custom development / Server-specific optimization**: QQ 1456133139 · WeChat lyh1456133139

</div>
