# Server Rules and Common Commands

<!-- ══════════════ Usage Notes (you may delete this comment block before deploying to knowledge/) ══════════════
  This is a **ready-to-use working sample**, not an empty placeholder.
  ▶ Server owner: copy it into a new file, then replace the top # title and the content below with your server's real information.
  ▶ Maintainer: when given any messy file from a server owner, **rewrite it into the structure below** and the optimization is complete.
  ▶ Delete all comments before deploying, so that comment text is not treated as knowledge content and included in retrieval.
  For the full rationale, see "Knowledge Base Guide.md" in the same directory.

  ════ Writing Rules (7 rules, ordered by impact on recall, high to low) ════
  1. Use ## / ### headings to split concepts, and put **core words + synonyms** in headings.
     The system splits by Markdown headings; words in headings get extra weighting (each hit: +15 × keyword weight, stackable).
     Synonyms let a player's different phrasing still match.
  2. Write a heading and its content **right next to each other, with no empty line in between**.
     An empty line cuts off the heading chunk, turning the following content into headerless orphan chunks (losing the heading bonus, and possibly dropped for being < 20 chars).
     To split long content, use ### sub-headings, not empty lines.
  3. Keep a single heading section within 500 characters.
     Anything longer gets secondarily split, and from the second chunk on the heading bonus is lost.
  4. Each chunk must be at least 20 characters.
     Chunks below 20 characters are dropped outright (a one-command-per-line short list will be filtered entirely).
  5. Core words must also appear in the body; write command names and proper nouns in full.
     Body keywords score an exact-hit bonus (+10) and participate in the BM25 score; the longer the word, the higher its weight (≥4 chars ×3, 3 chars ×2, otherwise ×1).
  6. After each rule, add a "Q:" line listing 2~3 phrasings a player might use.
     The purpose is to add keyword coverage: covering words players use but that you did not write in the body; short questions may also match as a whole for a bonus.
  7. Do not repeat: write each rule in only one place, and do not write it twice across files.
     Duplicates are recalled as two chunks, taking up slots and possibly contradicting each other.

  Also: the system uses **both** keyword matching and semantic matching, so you must both write the core words clearly (for the keyword path)
  and explain things in plain language (for the semantic path). Covering both gives the most stable recall.
═══════════════════════════════════════════════════════════════════ -->

<!-- Below is the working sample. Replace the content with your server's real information. Note that there is NO empty line between any heading and its content. -->

## Claim Land / Territory / Protected Area
<!-- Rule 1: heading contains core words + synonyms. -->
Claiming land (also known as territory, protected area) protects your builds from being destroyed or stolen by other players.
Q: How do I claim land? / How do I protect my home? / How do I apply for territory?
<!-- Rule 6: adds common player words like "protect", "home", "apply" that may not appear in the body. -->

### Claim Steps
1. Prepare 10 gold coins
2. Stand at the center of the area to protect
3. Type `/claim`

### Related Commands
- `/claim` —— Define territory
- `/unclaim` —— Remove territory
- `/trust <player>` —— Add a trusted member
<!-- Rule 5: write command names in full (/claim etc.); longer words carry more weight. -->

---

## Death Protection / Return to Death Point
When you die you drop items, but you can use `/back` within 30 seconds to return to your death point and recover them.
`/back` cannot be used in combat and requires standing still for 3 seconds.
Q: How do I go back after dying? / Can I recover dropped items? / What is the /back cooldown?

### Related Commands
- `/back` —— Return to your last death point (30 second cooldown)

---

## Economy / Item Prices
The server's currency unit is Gold. Players can trade freely with each other.
Q: How much is a diamond? / How do I make money? / Where is the price list?

### Common Item Price Reference
| Item | Unit Price (Gold) | Note |
|------|------|------|
| Diamond | 80-120 | Fluctuates with the market |
| Netherite Ingot | 500-800 | Rare material |
| Mending Enchanted Book | 2000-3000 | Top-tier enchantment |

### Ways to Earn Money
- Mine and sell minerals
- Fish and sell fish
- Participate in server events
<!-- Rules 5&1: words like "mining", "fishing", "events", "diamond" participate in matching; prices in a table. -->

---

## Frequently Asked Questions (FAQ)
<!-- Rule 6 zone: gather high-frequency questions together to maximize keyword coverage. -->
Q: How do I get started as a beginner?
A: First claim the beginner kit at spawn (`/kit starter`), then use `/spawn` to return to spawn and check the shops.
Q: How do I privately message another player?
A: Use `/msg <player> <content>` to send a private message.
Q: What version is the server running?
A: Currently running 1.20.x, supporting cross-version entry for both Java and Bedrock editions.

<!--
  ════ Checklist (go through this when optimizing any file) ════
  □ Split with ## / ### headings, each heading containing core words + synonyms?
  □ No empty line between heading and content (content not orphaned into a headerless chunk)?
  □ Each section < 500 chars (split with ### sub-headings if longer)?
  □ Each chunk ≥ 20 chars (not dropped)?
  □ Core words appear in the body (not only in the heading)?
  □ Each rule comes with 2~3 likely player phrasings (Q:)?
  □ Commands/prices/steps in lists or tables, right under the heading?
  □ No rule duplicated anywhere in the file?
  □ File is UTF-8 encoded, with a .md or .txt extension, placed in the knowledge/ directory?
  ════════════════════════════════════════════
-->
