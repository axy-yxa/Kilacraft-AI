# Let the AI Execute Custom Commands (Command Skill) — Knowledge Base Writing Example

> **Last Updated**: 2026-08-01  
> **Description**: Server owners write their server's custom commands into the knowledge base so the AI can execute them on the player's behalf via the command skill (CommandSkill). Works with two-phase intent recognition + the corpus-seeding dictionary.

---

## When to Use This

Your server runs **custom-command plugins** (Essentials, CMI, HuskTowns, MyHome, etc.) providing commands like `/back`, `/home`, `/spawn`, `/tpa`, `/menu`. You want players to trigger them with natural language ("take me home", "teleport me to Steve") — just write those commands into the knowledge base.

Prerequisite: enable `command_skill.enabled: true` in `config.yml`.

---

## How It Works

```
Player speaks ("I died, take me back to grab my stuff")
   ↓
Phase 1 coarse intent selection (no knowledge-base lookup)
   ↓
Phase 2 precise intent selection [retrieves the knowledge base] ← hits the "/back return to death point" chunk
   ↓
LLM decides: skill=command, action=execute_command, command="back"
   ↓
The command skill (CommandSkill) runs /back as the player
```

Three key facts:

1. **The knowledge base is retrieved during Phase 2 intent recognition, not when the command skill runs.** Its job is to "teach the Phase 2 LLM what your commands are and how to produce the correct `command` parameter."
2. **The command skill is a fallback skill:** if a dedicated skill (economy transfer, market query, etc.) can handle the request, that skill wins. Only server-specific custom commands not covered by a dedicated skill rely on the command skill + the knowledge base.
3. **The `command` parameter has no leading `/`:** the LLM must fill `back`, not `/back`.

---

## The Command Skill (CommandSkill) Contract

| Item | Value |
|---|---|
| Skill name | `command` (implementation class `CommandSkill`) |
| Action | `execute_command` |
| Parameter | `command`: the full command, **without the leading `/`**, extracted from the player's message |
| Permission | `kilacraft.command.execute` (default OP); runs as the player, bounded by the player's own permissions (if the player lacks a permission, so does the AI) |
| Toggle | Only registered when `command_skill.enabled: true` is set in `config.yml` |
| Role | Fallback skill (dedicated skills take priority) |

---

## Full Example (copy to `knowledge/custom_commands.md`, replace with your real commands)

````markdown
# Server Custom Commands

When a player asks for something in natural language, the AI can execute the commands below on their behalf. Commands run as the player, so anything the player can't do, neither can the AI.

## Return to death point / go back to where you were (/back)
`/back` takes you back to your last position — usually where you died and dropped items, or where you were before a teleport. 30-second cooldown; cannot be used in combat.
Q: I died, how do I go back for my stuff / can I get my dropped items back / take me back to where I was / how long is back cooldown
AI executes: back

## Go to your home (/home)
`/home` teleports you to your set home. If you have multiple homes, append the home name, e.g. `/home main-base` to go to the home named "main-base".
Q: go home / take me to my base / send me back to my hut / go to my home called main-base
AI executes: home (or `home main-base` when a name is given)

## Teleport to another player (/tpa)
`/tpa <player>` sends a teleport request to that player; you only go after they accept — you cannot force it. For example `/tpa Steve` requests a teleport to Steve.
Q: teleport to Steve / I want to find my friend / take me to Alex / teleport me to Tom
AI executes: tpa <player> (e.g. `tpa Steve`)

## Return to spawn / main city (/spawn)
`/spawn` teleports you to the server's main spawn point; no arguments needed.
Q: go to spawn / take me to the spawn point / how do I get back to the city / teleport to town
AI executes: spawn

## Open the server menu (/menu)
`/menu` opens the main menu, which contains the shop, teleport, quests, kits, and all other features.
Q: open the menu / where is the shop / I want to buy something / how do I claim my kit / where are the quests
AI executes: menu
````

---

## Why It's Written This Way (each choice serves a specific link in the chain)

| Choice | Which link it serves | Why |
|---|---|---|
| Always write the command in full as `/back` (in both heading and body) | Corpus seeding + keyword weighting | `/back` triggers **corpus seeding** (`back` auto-enters the HanLP dictionary, so it isn't split when a player types `/back` directly); it's also a ≥4-char long word, so BM25 weights it ×3 |
| Headings stack Chinese synonyms (go home / return / death point / spawn) | **The main driver of Chinese recall** | A player saying "go home" in Chinese matches the heading "go home" → heading-term bonus `+15×term weight`. Corpus seeding can't help here; recall depends on this |
| **No blank line** between heading and body | Keeps the heading block intact | A blank line splits the heading block; the body becomes an untitled orphan chunk, losing the heading bonus (and may be dropped if <20 chars) |
| Each `Q:` lists 2–4 ways players might phrase it | Keyword-coverage padding | Adds words players use but your body doesn't ("my stuff", "town"); short phrasings may even match the whole keyword string for +50 |
| The `AI executes: back` line | Teaches the LLM the command value | Explicitly tells the Phase 2 LLM what `command` to fill and that it has **no `/`**, reducing mistakes like `/back` or wrong argument formats |
| One command per `##` block | Recall completeness | BM25 segments by heading; a search for "go home" returns one complete chunk (command + purpose + phrasings), giving the LLM everything it needs to produce the command |
| Each segment 20–500 chars | Not dropped / no lost bonus | <20-char chunks are filtered; >500-char chunks get re-split and the second part loses the heading bonus |
| Only server custom commands — not "pay money / check market" | The command skill is a fallback | Requests covered by dedicated skills (economy / market) are handled by those skills; writing them here only muddies the match |

---

## Two Important Reminders

1. **Chinese recall is the bigger lever:** the corpus-seeding dictionary only helps when a player's input contains an English command word (`/back`, `ender-dragon`); most players say it in Chinese ("go home / back to spawn"), which relies on **Chinese-synonym coverage in the headings and body**. The more synonyms you stack, the more stable the recall — this is where to spend your effort.

2. **Always give an example for parameterized commands:** for things like `/home main-base` or `/tpa Steve`, just writing "teleport to a player" lets the LLM fill it wrong; a concrete example (`AI executes: tpa Steve`) makes the LLM follow the argument format.

---

## Checklist

Go through this when optimizing any command-skill knowledge file:

- ☐ One command per `##` heading, the heading contains [player phrasing + command name] (e.g. `Go home / Return (/home)`)?
- ☐ Command written in `/full` form (triggers corpus seeding + long-word weighting)?
- ☐ No blank line between heading and body?
- ☐ Each segment 20–500 chars?
- ☐ Each command has a `Q:` listing several ways players might ask?
- ☐ Each command has an `AI executes: xxx` stating the command value (no `/`)?
- ☐ Parameterized commands give a concrete example?
- ☐ Nothing that a dedicated skill already covers (economy / market)?
- ☐ UTF-8 encoded, `.md` extension, in the `knowledge/` directory?

---

## Related Documentation

- [Knowledge Base Guide](./Knowledge%20Base%20Guide.md) - retrieval mechanism, segmentation strategy, scoring algorithm
- [Knowledge Base File Template](./Knowledge%20Base%20File%20Template.md) - general knowledge-base template and the 7 rules
