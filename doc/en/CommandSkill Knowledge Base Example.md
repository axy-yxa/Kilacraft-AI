# Let the AI Execute Custom Commands (Command Skill) — Command Document Writing Example

> **Last Updated**: 2026-08-04  
> **Description**: Server owners register their server's commands in `commands/commands.md` so the AI can execute them on the player's behalf via the command skill (CommandSkill). This mechanism is independent of the knowledge base (`knowledge/` directory).

---

## When to Use This

Your server runs **custom-command plugins** (Essentials, CMI, HuskTowns, MyHome, etc.) providing commands like `/back`, `/home`, `/spawn`, `/tpa`, `/menu`. You want players to trigger them with natural language ("take me home", "teleport me to Steve") — just register those commands in `commands/commands.md`.

> ⚠️ **Important change**: Early versions wrote commands into the knowledge base (`knowledge/` directory) and retrieved them via BM25. That mechanism is deprecated. Commands now go through a **standalone command document** (`commands/commands.md`), are **not retrieved via the knowledge base**, and no longer "seed" the HanLP dictionary. The command skill is always registered — there is no toggle in `config.yml`.

---

## How It Works

```
Player speaks ("I died, take me back to grab my stuff")
   ↓
CommandSkill, as a DynamicContextProvider, filters the visible commands by the player's
   permissions and injects them into the Phase 1 prompt as an "AI-executable commands" summary
   ↓
The LLM sees entries like "back: return to death point" and decides skill=command, command="back"
   ↓
CommandSkill runs /back as the player (bounded by the player's own permissions)
```

Three key facts:

1. **The command document is independent of the knowledge base**: command entries are parsed from `commands/commands.md` and do NOT go through the knowledge base's segmentation/retrieval/scoring pipeline, nor do they participate in corpus seeding (the HanLP dictionary only scans `knowledge/`, not `commands/`).
2. **The command skill is a fallback skill:** if a dedicated skill (economy transfer, market query, etc.) can handle the request, that skill wins. Only server-specific custom commands not covered by a dedicated skill rely on the command skill + the command document.
3. **The `command` parameter has no leading `/`:** the LLM must fill `back`, not `/back`. The `example:` field guides it to the correct value.

---

## The Command Skill (CommandSkill) Contract

| Item | Value |
|---|---|
| Skill name | `command` (implementation class `CommandSkill`, also implements `DynamicContextProvider` and `CallerDescriptionProvider`) |
| Action | Runs a command as the player |
| Parameter | `command`: the full command, **without the leading `/`**, extracted from the player's message |
| Permission | `kilacraft.command.execute` (default OP); runs as the player, bounded by the player's own permissions (if the player lacks a permission, so does the AI) |
| Command source | `commands/commands.md` (Chinese/default), `commands/<lang>/commands.md` (matched by client language, e.g. `commands/en/commands.md`) |
| Toggle | **No toggle needed** — the command skill is always registered (the old `command_skill.enabled` config key has been removed) |
| Role | Fallback skill (dedicated skills take priority) |

---

## Command Document Format

File location (under the plugin data folder):

```
plugins/Kilacraft-AI/
├── commands/
│   ├── commands.md          # Default/Chinese command document
│   └── en/
│       └── commands.md      # Command document used by English clients
└── knowledge/               # Knowledge base (unrelated to this mechanism)
```

> On first startup the plugin auto-generates `commands/commands.md` with Kilacraft-AI's own built-in commands already filled in, plus format notes and commented examples. Just append your server's commands at the end of the file.

Each command is declared with a `##` heading (**no leading `/`**), followed by four fields:

```
## command_name
description: What the command does
example: Full invocation (with arguments)
permission: Server permission node required to run this command
keywords: Words players might use to express this intent (optional, comma-separated)
```

**Field meanings**:

| Field | Required | Purpose |
|---|---|---|
| `description:` | Yes | One sentence on what the command does; injected into the prompt so the LLM can judge whether it matches the player's intent |
| `example:` | Yes | Full invocation (with arguments); guides the LLM to fill the correct `command` value (**no `/`**) |
| `permission:` | Yes | Server permission node. CommandSkill **filters by the player's permissions in real time**: commands the player lacks permission for are NOT injected into that player's prompt (the AI neither sees nor attempts to execute them) |
| `keywords:` | No | Words players might use to express this intent, comma-separated. Supplements synonyms / colloquial phrasings to improve recognition accuracy |

> The command name may also carry argument placeholders, e.g. `## kila clear <player>`, `## home [home_name]`, to describe parameterized commands.

---

## Full Example (edit `commands/commands.md` directly, append at the end)

Below shows how to append third-party plugin commands after the built-in ones. The first part is the plugin's own `kila` series (built-in, **no need to write by hand** — shown here only as a format reference); the second part is the third-party commands (Essentials, etc.) you should add.

````markdown
# AI Executable Commands Document
#
# This file is auto-generated by Kilacraft-AI with built-in plugin commands.
# Add commands from other plugins and custom commands below.
# Run /kila reload after editing to apply changes.
#
# This file is independent from the knowledge base (knowledge/ directory),
# used only for AI command execution skill intent recognition.

# ...(built-in kila clear / kila usage / kila reload etc. omitted here)...

# ============ Add your commands below ============

## home
description: Teleport to your set home
example: home [home_name]
permission: essentials.home
keywords: go home, back to base, my hut, take me to my home

## tpa
description: Request a teleport to a player (you only go after they accept)
example: tpa player_name
permission: essentials.tpa
keywords: teleport, find player, go to a friend, take me to someone

## back
description: Return to your last position (usually where you died and dropped items)
example: back
permission: essentials.back
keywords: death point, recover items, take me back to where I was

## spawn
description: Teleport to the server's main spawn point
example: spawn
permission: essentials.spawn
keywords: go to spawn, spawn point, back to town, teleport to city

## menu
description: Open the main menu (shop, teleport, quests, kits, etc.)
example: menu
permission: essentials.menu
keywords: open menu, where is the shop, claim kit, where are the quests
````

> English clients read `commands/en/commands.md`; the format is identical, only the field names change to `description:` / `example:` / `permission:` / `keywords:` (see the English template generated by the plugin).

---

## Why It's Written This Way (each choice serves a specific link in the chain)

| Choice | Which link it serves | Why |
|---|---|---|
| Command name as the `##` heading, **no `/`** | Parsing + prompt injection | The parser splits command entries by `##`; the heading is the command name itself, so the LLM directly sees the fillable `command` value |
| `example:` gives the full invocation (with arguments) | Teaches the LLM the command value | Explicitly tells the LLM what to fill and that it has **no `/`**; parameterized commands (e.g. `tpa player_name`) rely on the example to show the argument format, reducing mistakes |
| `permission:` filled with the accurate server permission node | **Dynamic per-player filtering** | CommandSkill implements `DynamicContextProvider` and filters each entry against the player's current permissions: if the player lacks `essentials.home`, `home` is never injected for them — the AI neither sees nor attempts it (no unauthorized commands get executed) |
| `keywords:` stacks Chinese/colloquial synonyms | Improves recognition accuracy | When a player says "go home / back to spawn" in their language, these words help the LLM match the right command entry |
| `description:` clarifies the command's boundaries and limits | Reduces LLM misuse | One sentence stating "what it can do / when it can't" (e.g. "you only go after they accept") prevents the AI from forcing a command in an unfit scenario |
| Only server custom commands — not "pay money / check market" | The command skill is a fallback | Requests covered by dedicated skills (economy / market) are handled by those skills; writing them here only muddies the match |
| One command per `##` block | Recall completeness | Each command is injected as an independent entry, giving the LLM everything it needs to produce the command (description + example + keywords) |

---

## Two Important Reminders

1. **The command document does NOT enter the HanLP dictionary**: corpus seeding (auto-populating the dictionary) only scans the `knowledge/` directory, **not `commands/`**. So whether you write `/back` in full in the command document has no effect on tokenization. Command recognition relies on the structured fields above + the prompt — NOT on keyword tokenization recall. This differs from the old mechanism.

2. **Always give an example for parameterized commands:** for things like `home main-base` or `tpa Steve`, just writing "teleport to a player" lets the LLM fill it wrong; a concrete example (`example: tpa player_name`) makes the LLM follow the argument format.

---

## Ops

| Action | Command | Purpose |
|---|---|---|
| Reload the command document | `/kila reload` | Run after editing `commands/commands.md` to apply immediately (no server restart needed) |
| Command document health check | `/kila doctor` | The self-check inspects the command skill's entries (`hasCommandEntries`); if the document is empty or malformed it will warn |

> Edits to the command document are **hot-reloadable**: run `/kila reload` after editing, no restart required. Language versions (Chinese/English) are selected by client language at file-read time, with no extra configuration.

---

## Checklist

Go through this when optimizing any command document:

- ☐ One command per `##` heading, and the heading has **no leading `/`**?
- ☐ Each command has the three required fields `description:` / `example:` / `permission:`?
- ☐ `permission:` is the **accurate server permission node** (e.g. `essentials.home`)?
- ☐ `example:` gives the full invocation and has **no `/`** (e.g. `home [home_name]`, not `/home`)?
- ☐ Parameterized commands provide a concrete argument placeholder in `example:` (e.g. `tpa player_name`)?
- ☐ `keywords:` lists several colloquial / native-language phrasings players might use?
- ☐ Nothing that a dedicated skill already covers (economy / market)?
- ☐ The file is at `commands/commands.md` (English: `commands/en/commands.md`), UTF-8 encoded?
- ☐ After editing you ran `/kila reload`, and `/kila doctor` passed the self-check?

---

## Related Documentation

- [Knowledge Base Guide](./Knowledge%20Base%20Guide.md) - knowledge-base retrieval mechanism, segmentation strategy, scoring algorithm (the command document does NOT use this)
- [Knowledge Base File Template](./Knowledge%20Base%20File%20Template.md) - general knowledge-base template and rules
