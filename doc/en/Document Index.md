# Kilacraft-AI Document Index

> **Last Updated**: 2026-08-01  
> **Description**: Quick index of all Kilacraft-AI documentation to help you find what you need

---

## Server Owner / Admin Documents

| Document | Description | Audience |
|----------|-------------|----------|
| [Server Owner Guide](./Server%20Owner%20Guide) | Installation, core features (incl. web search / web fetch / chat suggestions), advanced features (incl. Guardian / player watch / cross-player watch), commands & permissions, FAQ | Server Administrators |
| [Admin Features Guide](./Admin%20Features%20Guide) | Health monitoring, player analysis, audit logs — full configuration & usage, incl. `/kila doctor` and `/kila cache` auxiliary diagnostic commands | Server Administrators |
| [Changelog](./Changelog) | Version history and change descriptions | Everyone |
| [Database and Persistence Configuration Guide](./Database%20and%20Persistence%20Configuration%20Guide) | Database architecture, persisted data, configuration reference | Server Administrators |
| [Player Profile and Social Relations System Guide](./Player%20Profile%20and%20Social%20Relations%20System%20Guide) | Player profiles, social graph, event collection system | Server Owners, Developers |

## Developer Documents

| Document | Description | Audience |
|----------|-------------|----------|
| [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide) | Custom skill development guide (architecture, interfaces, example code, incl. SkillEntityHelper utility) | Plugin Developers |
| [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide) | Third-party plugin integration (callback mechanism, async optimization, MythicMobs) | Plugin Developers |

## Technical Reference Documents

| Document | Description | Audience |
|----------|-------------|----------|
| [Built-in Skills and Events Capability List](./Built-in%20Skills%20and%20Events%20Capability%20List) | All 17 built-in Skills and WatchSkill's 11 event-listening types | Server Owners, Developers |
| [Bukkit API Reference](./Bukkit%20API%20Reference) | 71 read-only APIs — configuration examples, permission management | Server Owners, Developers |
| [Personality System Guide](./Personality%20System%20Guide) | Multi-personality management, prompt writing, style configuration | Server Owners, Advanced Users |
| [Knowledge Base Guide](./Knowledge%20Base%20Guide) | RAG principles, document writing standards, retrieval optimization (incl. BM25+Embedding fusion) | Server Owners, Content Creators |
| [Knowledge Base File Template](./Knowledge%20Base%20File%20Template) | General knowledge-base template, 7 writing rules, a copy-ready working sample | Server Owners, Content Creators |
| [CommandSkill Knowledge Base Example](./CommandSkill%20Knowledge%20Base%20Example) | Writing knowledge-base entries so the AI executes custom commands, with a full example and checklist | Server Owners, Content Creators |
| [Intent Recognition Prompt Configuration Guide](./Intent%20Recognition%20Prompt%20Configuration%20Guide) | Intent recognition system config, decision rules, constraint rules | Server Owners, Developers |
| [System Architecture Details](./System%20Architecture%20Details) | Call chains of three interaction modes, design philosophy, per-version subsystem architecture | Developers, Technical Personnel |
| [Known Bugs](./Known%20Bugs) | Known issues, impact scope, temporary solutions | Everyone |

## Archived Documents

The features described below were removed or fully replaced in v2.2.0. These docs are kept for historical reference only. See the [archive directory README](../归档/README.md) (Chinese).

| Document | Reason for Archiving |
|----------|----------------------|
| [AFK Task System Guide](../归档/AFK%20Task%20System%20Guide.md) | The AFK task system was removed in v2.2.0; its capabilities are replaced by Guardian / WatchSkill / PlayerWatchSkill |
| [Bukkit Event Listener Reference](../归档/Bukkit%20Event%20Listener%20Reference.md) | The old 19 AFK listeners were removed; event-listening is now provided by WatchSkill's 11 event types |

---

> Found an error or want to add content? Submit an [Issue](https://github.com/axy-yxa/Kilacraft-AI/issues) or Pull Request
