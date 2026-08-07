# Kilacraft-AI - Knowledge Base Enhancement Guide

> **Last Updated**: 2026-08-04  
> **Description**: This document details how to use RAG (Retrieval Augmented Generation) technology to enable AI to provide accurate answers based on your server documentation.

---

## 💡 Core Philosophy: Knowledge Base Quality is Everything

> **"Garbage In, Garbage Out" (GIGO)**  
> —— George Fuechsel, Computer Scientist

In RAG (Retrieval Augmented Generation) technology, **the quality of knowledge base documents directly determines retrieval quality and recall rate**:

- ✅ **High-Quality Knowledge Base** → Precise Retrieval → Accurate Answers → **Save Tokens**
- ❌ **Low-Quality Knowledge Base** → Chaotic Retrieval → Wrong Answers → **Waste Tokens**

### Why Does Quality Affect Token Consumption?

| Knowledge Base Quality | Retrieval Result | Token Consumption | Answer Quality |
|----------------------|------------------|-------------------|----------------|
| **High Quality** (clear structure, accurate content) | 1-2 precise chunks | ~200-300 tokens | Accurate, concise |
| **Medium Quality** (structured but not precise enough) | 3-5 relevant chunks | ~500-800 tokens | Basically accurate |
| **Low Quality** (chaotic, repetitive, verbose) | 5+ noise chunks | ~1000+ tokens | Possibly wrong |

**Conclusion**: Spending time optimizing knowledge base documents not only improves AI answer accuracy, but also **significantly reduces API call costs**!

### Key Characteristics of a High-Quality Knowledge Base

1. ✅ **Clear Structure**: Use Markdown heading hierarchy for intelligent segmentation
2. ✅ **Precise Content**: Each section focuses on one topic, avoid mixing content
3. ✅ **Concise Language**: Express in concise language, avoid verbose writing
4. ✅ **Rich Keywords**: Include keywords and synonyms that users might use
5. ✅ **Standard Format**: Use structured content like lists and tables
6. ✅ **Regular Maintenance**: Update outdated content timely, remove redundant information

---

## 📖 Overview

### What is Knowledge Base Enhancement?

Knowledge Base Enhancement (RAG - Retrieval Augmented Generation) is a technology that allows AI to access **your server's exclusive knowledge**.

**Working Principle**:
```
User Question → Retrieve Relevant Knowledge Chunks → Inject Knowledge into Prompt → LLM Generates Answer
```

**Traditional AI vs Knowledge Base Enhanced AI**:

| Feature | Traditional AI | Knowledge Base Enhanced AI |
|---------|---------------|---------------------------|
| Knowledge Source | Training data (general) | Your server docs + training data |
| Accuracy | May be outdated or inaccurate | Based on latest server rules |
| Customization | Cannot understand your server | Fully understands your server |
| Maintenance Cost | Requires model retraining | Just update documents |

---

## 🚀 Quick Start

### 1. Create the Knowledge Base Directory

```bash
plugins/Kilacraft-AI/knowledge/
```

### 2. Add Knowledge Documents

Create Markdown or plain text files:

```
knowledge/
├── server_rules.md          # Server rules
├── beginner_guide.md        # Beginner guide
├── faq.md                   # Frequently asked questions
├── economy_system.md        # Economy system description
└── events.md                # Event introductions
```

### 3. Load the Knowledge Base

```
/kila knowledge reload
```

### 4. Test the Effect

```
Player: How do I claim land?
AI: You can use the /claim command to define your territory. Requires at least 10 gold coins.
    (Based on the "Server Rules" document in the knowledge base)
```

---

## 📝 Document Writing Guidelines

### Supported Formats

- ✅ **Markdown (.md)** - Recommended, supports heading-based segmentation
- ✅ **Plain Text (.txt)** - For simple scenarios

### Markdown Best Practices

#### 1. Use a Clear Heading Structure

```
# Level 1 Heading (Document Topic)

## Level 2 Heading (Main Category)

### Level 3 Heading (Subcategory)

#### Level 4 Heading (Detailed Content)
```

**Example**:
```
# Server Rules

## Basic Rules

### Prohibited Actions
1. No cheating
2. No insulting

### Allowed Actions
1. Friendly communication
2. Cooperative building

## Economy System

### Ways to Earn Money
- Mining
- Fishing
- Trading

### Prohibited Behaviors
- Money duplication exploits
- Fraud
```

**Why is this important?**
- AI segments intelligently based on headings
- Relevant sections can be precisely located during retrieval
- Improves answer accuracy and relevance

---

#### 2. Use Lists and Structured Content

✅ **Recommended**:
```
## How to Get Diamonds?

You can obtain diamonds through the following methods:

1. **Mining**: Dig at Y=-58 to Y=-53 layers
2. **Exploration**: Find exposed veins in caves and ravines
3. **Trading**: Exchange with other players
4. **Rewards**: Complete server tasks

**Tip**: Using the Fortune enchantment can increase drops!
```

❌ **Avoid**:
```
## How to get diamonds

You can mine or go to caves to find them or trade with others or do quests, oh and using the Fortune enchantment is better.
```

---

#### 3. Include Keywords and Synonyms

```
## Claim Land / Territory / Protected Area

**Claiming land** (also known as territory, protected area) is a method to protect your builds from being destroyed by other players.

### How to Claim Land?
1. Prepare 10 gold coins
2. Stand at the location you want to claim
3. Execute the `/claim` command

### Related Commands
- `/claim` - Claim territory
- `/unclaim` - Unclaim territory
- `/trust <player>` - Add a trusted player
- `/trustlist` - View the trust list
```

**Benefits**: Whether players say "claim", "territory", or "protect", relevant content can be retrieved.

---

#### 4. Provide Specific Examples

```
## Economy System Details

### Currency Unit
The server's currency unit is the "Gold Coin".

### Common Item Price Reference

| Item | Price Range | Description |
|-------|--------------|-------------|
| Diamond | $80-120 | Fluctuates with the market |
| Netherite Ingot | $500-800 | Rare material |
| Enchanted Book (Mending) | $2000-3000 | Top-tier enchantment |

### Money-Making Suggestions

**Beginner Stage (0-1000 Gold Coins)**
- Mine and sell minerals
- Fish and sell fish
- Collect and sell wood

**Intermediate Stage (1000-10000 Gold Coins)**
- Operate automated farms for selling
- Explore ruins to obtain treasures
- Participate in server events

**Advanced Stage (10000+ Gold Coins)**
- Open shops to resell goods
- Provide building services
- Organize events and competitions
```

---

### Document Organization Strategies

#### Strategy 1: Split by Topic (Recommended)

```
knowledge/
├── rules.md              # Server rules
├── gameplay.md           # Gameplay guide
├── economy.md            # Economy system
├── commands.md           # Command reference
├── faq.md                # FAQ
└── events.md             # Event information
```

> Subdirectories are also supported — the plugin recursively scans all `.md` / `.txt` files under `knowledge/` (at any depth). For example, `knowledge/tutorials/redstone.md` and `knowledge/advanced/mob-farm.md` will both be loaded.

**Advantages**:
- Easy to maintain and update
- High retrieval precision
- Facilitates multi-person collaborative editing

---

#### Strategy 2: Single Comprehensive Document

```
knowledge/
└── server_wiki.md        # Complete server wiki
```

**Advantages**:
- Simple management
- Suitable for small servers

**Disadvantages**:
- Large files affect retrieval speed
- Prone to errors when updating

---

## 🔍 Retrieval Mechanism Details

### Intelligent Segmentation Algorithm

Kilacraft-AI uses a **three-tier fallback segmentation strategy**, automatically selecting the most suitable segmentation method:

```
Strategy 1: Split by Markdown headings (Priority)
  ↓ If no headings
Strategy 2: Split by paragraphs (empty-line separator)
  ↓ If no empty lines
Strategy 3: Split by fixed size (Fallback)
```

---

#### Strategy 1: Split by Markdown Headings ⭐ **Recommended**

**Trigger Condition**: The document contains `#` to `######` headings

**Segmentation Rules**:
- Start from the heading, read the subsequent content
- Stop when encountering an **empty line** or **the next heading**
- Includes the heading + the content immediately following it (content after an empty line is excluded)
- If a single chunk exceeds `max_size (500 chars)` → automatically falls back to Strategy 2

**Example 1: Standard Command Documentation**

```markdown
# Server Commands

## /back - Return to Death Point
After you die, you can use this command to quickly return to your death location.
This is very useful when exploring caves or fighting bosses.
Cooldown: 30 seconds
Permission: None

## /spawn - Teleport to Spawn
Teleport to the server's spawn area.
All players can use this.
```

**Segmentation Result**:
```
Chunk 1: "# Server Commands" (17 chars)
Chunk 2: "## /back - Return to Death Point\nAfter you die...Cooldown: 30 seconds\nPermission: None" (~180 chars) ✅
Chunk 3: "## /spawn - Teleport to Spawn\nTeleport to the server's spawn area...All players can use this." (~140 chars) ✅
```

**✅ Advantages**:
- Each command maintains complete context
- Heading information is preserved (BM25 heading bonus +15 × keyword weight)
- Best semantic integrity

---

**Example 2: Ultra-long Command Description (>500 chars)**

```markdown
## /back - Return to Death Point
After you die, you can use this command to quickly return to your death location.
This is very useful when exploring caves or fighting bosses.
(400 chars of detailed explanation omitted...)
Notes:
1. Cooldown: 30 seconds
2. Cannot be used in combat
3. Need to stand still for 3 seconds
```

**Segmentation Result**:
```
Strategy 1 attempt: Single chunk 600 chars > max_size (500)
  ↓ Triggers Strategy 2
Strategy 2 splits by paragraphs:
  Chunk 1: "## /back - Return to Death Point\nAfter you die..." (~300 chars) ✅
  Chunk 2: "Notes:\n1. Cooldown: 30 seconds..." (~200 chars) ✅
```

**⚠️ Note**:
- The heading is only in chunk 1; chunk 2 loses the heading bonus
- A user searching "back cooldown" may match chunk 2 without the heading bonus

---

#### Strategy 2: Split by Paragraphs 📄 **Alternative**

**Trigger Condition**: Markdown segmentation fails, or the document has no headings

**Segmentation Rules**:
- Use **empty lines** (`\n\n`) as separators
- Each paragraph must be `≥ min_size (20 chars)`
- Paragraphs shorter than 20 chars will be **filtered out**

**Example 3: TXT Command Documentation (with empty lines)**

```txt
/back - Return to death point
After you die, you can use this command.
Cooldown: 30 seconds

/spawn - Teleport to spawn
Teleport to the server's spawn area.
All players can use this.
```

**Segmentation Result**:
```
Chunk 1: "/back - Return to death point\nAfter you die...Cooldown: 30 seconds" (~70 chars) ✅
Chunk 2: "/spawn - Teleport to spawn\nTeleport to the server's spawn area...All players can use this." (~80 chars) ✅
```

**✅ Advantages**:
- Natural segmentation by empty lines
- Maintains command integrity

**⚠️ Note**:
- No heading markers → loses the BM25 heading bonus (-15 × weight)
- Command names in plain text carry lower weight

---

**Example 4: TXT Command Documentation (no empty lines)** ❌ **Critical Issue**

```txt
/back - Return to death point
After you die, you can use this command.
Cooldown: 30 seconds
/spawn - Teleport to spawn
Teleport to the server's spawn area.
/home - Go home
Teleport to your home.
```

**Segmentation Result**:
```
Strategy 1: No # headings → Failed
Strategy 2: No empty lines → Entire document as 1 paragraph (~170 chars)
  ↓ 170 < max_size (500)
  Result: 1 large chunk containing all commands ❌
```

**❌ Problem**:
- All commands are mixed together
- BM25 retrieval cannot distinguish them
- A user searching "back" gets a large chunk containing all commands

**✅ Correct Approach**: You must add empty lines between commands!

---

**Example 5: Short Command List (< 20 chars)** ⚠️ **Will Be Filtered**

```txt
/back go back

/spawn go spawn

/home go home

/money balance
```

**Segmentation Result**:
```
Chunk 1: "/back go back" (13 chars) ❌ Less than 20 chars, filtered
Chunk 2: "/spawn go spawn" (15 chars) ❌ Less than 20 chars, filtered
Chunk 3: "/home go home" (13 chars) ❌ Less than 20 chars, filtered
Chunk 4: "/money balance" (14 chars) ❌ Less than 20 chars, filtered

Result: 0 chunks ❌ All commands filtered!
```

**⚠️ Important Note**:
- The `min_size: 20` configuration filters out too-short chunks
- **A one-command-per-line concise format will be filtered!**
- You must add detailed descriptions so that each command chunk is ≥ 20 chars

**✅ Correct Approach**:
```txt
/back - Return to death point
After you die, use this command to quickly return to your death location.
Cooldown: 30 seconds.

/spawn - Teleport to spawn
Teleport to the server's spawn area, all players can use this.
Spawn is a safe zone where PVP is prohibited.
```

---

#### Strategy 3: Split by Fixed Size 🔧 **Fallback**

**Trigger Condition**: Paragraph segmentation also fails (continuous text with no empty lines)

**Segmentation Rules**:
- Maximum `max_size (500 chars)` per chunk
- Prefer cutting at **sentence boundaries** (`.` `!` `?` `\n`)
- Adjacent chunks have `overlap (30 chars)` of overlap

**Example 6: Continuous Text (No Headings, No Empty Lines)**

```txt
The server has various commands, /back to return to your death point, /spawn to return to spawn, /home to go home, /money to check balance, /tpa to request teleport. These commands are very commonly used; beginners are recommended to familiarize themselves with these basic commands first.
```

**Segmentation Result**:
```
Strategy 1: No headings → Failed
Strategy 2: No empty lines → Entire as 1 paragraph (~230 chars)
Strategy 3: 230 < max_size (500) → Not triggered
Result: 1 chunk (~230 chars)
```

---

**Example 7: Ultra-long Continuous Text (>500 chars)**

```txt
The server has many commands, including /back to return to your death point, /spawn to return to spawn, /home to go home, /money to check balance, /tpa to request teleport, /warp to teleport to landmarks, /kit to claim kits, /sell to sell items, /buy to buy items, /pay to transfer to other players, /balance to check balance, /afk to set away status, /nick to change nickname, /hat to wear an item on your head, /ender to open the ender chest, /workbench to open a workbench, /anvil to open an anvil, /enchant to open an enchanting table. These commands cover all aspects of the server, from teleportation to the economy system, from social to practical tools, everything is available. Beginners are recommended to learn the basic commands first, then gradually master the advanced commands. Veteran players can use these commands to improve efficiency and optimize their experience. The server will also regularly update new commands, please follow announcements.
```

**Segmentation Result**:
```
Strategy 1: No headings → Failed
Strategy 2: No empty lines → Entire as 1 paragraph (~750 chars)
Strategy 3: 750 > max_size (500) → Triggered
```

**If it exceeds 500 chars**:
```
Chunk 1: "The server has many commands...enchanting table." (~400 chars, cut at a period) ✅
Chunk 2: "These commands cover all...everything is available." (~200 chars, 30 chars overlap) ✅
Chunk 3: "Beginners are recommended...follow announcements." (~150 chars) ✅
```

---

### Segmentation Configuration Parameters

```yaml
knowledge:
  segment:
    max_size: 500    # Maximum characters per chunk
    min_size: 20     # Minimum characters per chunk (filtered if below this)
    overlap: 30      # Chunk overlap characters (Strategy 3 only)
```

**Parameter Description**:

| Parameter | Purpose | Default | Impact |
|-----------|---------|---------|--------|
| `max_size` | Limit the maximum length of a chunk | 500 | Prevents chunks being too long, which hurts retrieval precision |
| `min_size` | Filter out too-short, meaningless chunks | 20 | **⚠️ Short command lists will be filtered!** |
| `overlap` | Maintain context between adjacent chunks | 30 | Strategy 3 only, prevents information loss |

**⚠️ `min_size` Important Reminder**:

If you write command documentation in the following format:

```txt
❌ Wrong: One command per line (will be filtered)
/back go back
/spawn go spawn
/home go home
```

You must change it to:

```txt
✅ Correct: Add detailed descriptions (≥ 20 chars)
/back - Return to death point
After you die, use this command to quickly return to your death location.
Cooldown: 30 seconds.

/spawn - Teleport to spawn
Teleport to the server's spawn area, all players can use this.
```

Or use Markdown format:

```markdown
✅ Recommended: Use heading segmentation
## /back - Return to death point
After you die, use this command to quickly return to your death location.
Cooldown: 30 seconds.

## /spawn - Teleport to spawn
Teleport to the server's spawn area, all players can use this.
```

---

**Each chunk contains**:
- The complete content text
- Heading hierarchy information (used for weight calculation)
- The filename and path (for traceability)

---

### Comprehensive Scoring Algorithm

When a user asks a question, the system scores all chunks:

#### Level 1: Full Question Match (+50 bonus)

The system takes the player's **raw question** directly (only lowercased — no tokenization, no stop-word removal, no keyword extraction) and compares it against each chunk. If a chunk's content **contains the entire raw question**, it gets a +50 bonus:

```
User question: "claim land"  →  lowercased: "claim land"

Matching chunk (content contains the whole "claim land"):
"""
## How to Claim Land?

You can use the /claim command to define your territory...
"""
```

Score: +50

**Note**: This +50 is a substring match against the **entire raw question**, NOT a per-keyword match after tokenization. Therefore:
- **Short / single-keyword questions** (e.g. a player simply saying "claim land") match easily, and the +50 bonus is significant.
- **Long questions** (full sentences with "?", "how", etc., like "How do I claim land?") almost never appear verbatim in any knowledge chunk's content, so the +50 usually does NOT trigger — here Level 2/3 per-keyword matching carries the weight.
- So "writing the core concept words into the document" is more reliable than "copying the player's exact sentence" (see the heading bonus and keyword scoring below).

---

#### Level 2: BM25 Keyword Scoring

Keywords are extracted from the user's question using the HanLP TF-IDF algorithm (Chinese) / tokenizer + stop-word filtering (English), then the match degree is calculated using the BM25 formula:

```
User question: "I want to build a farm, what do I need?"

Extracted keywords: ["farm", "need"]

Matching chunk:
"""
## Farm Building Guide

Building a farm requires preparation:
1. Seeds
2. Water
3. Tools
"""
```

**Chinese Word Segmentation Optimization (v1.4.3 update)**:
- **Three-layer keyword extraction strategy**: original query + segmentation result + TF-IDF keywords, compatible with both short text and long documents
- **Single-character query optimization**: supports single-character queries like "bow", "sword" via the custom dictionary and stop-word checks
- **Intelligent stop-word filtering**: filters meaningless words like "的", "了", "吗"
- **TF-IDF automatic important-keyword extraction**: calculates keyword weights based on the HanLP TF-IDF algorithm
- **Built-in vocabulary support**: loads built-in vocabulary from the `internal/vocabulary/` directory inside the JAR, merges it with the custom vocabulary, and deduplicates automatically

---

#### Level 3: Heading Match (Base Priority)

If neither the content nor the keywords match, it matches headings:

```
User question: "What are the rules?"

Matching chunk headings:
- "# Server Rules" → +15 × keyword weight
- "## Basic Rules" → +15 × keyword weight
```

**Scoring Formula**:

#### Level 1: Full Question Match
- If chunk content contains the complete user question: +50.0

#### Level 2: BM25 Keyword Scoring
```
Base Score = TF × (k1 + 1) / (TF + k1 × lengthNorm)
```

**BM25 Formula Components**:
- **TF (Term Frequency)**: number of times the keyword appears in the document
- **IDF (Inverse Document Frequency)**: automatically calculated by HanLP TF-IDF
- **lengthNorm (Document Length Normalization)**: `1 - b + b × (docLength / avgDocLength)`
- **k1 (Term Frequency Saturation Parameter)**: default 1.5 (recommended 1.2-2.0)
- **b (Document Length Normalization Parameter)**: default 0.75 (recommended 0.5-0.8)

**Calculation Formula**:
```
Keyword Score = TF × (k1 + 1) / (TF + k1 × lengthNorm)
```

**Keyword Weight**:
- Length ≥ 4: weight 3
- Length = 3: weight 2
- Length < 3: weight 1

#### Level 3: Position Weighting
If a keyword appears in a heading (a line starting with `#`):
```
Keyword Score += 15.0 × Keyword Weight
```

#### Level 4: Exact Match Bonus
If any keyword appears in the document:
```
Total Score += 10.0
```

#### Final Score
```
Total Score = 50.0 (full question match, if present)
    + Sum of all keywords' BM25 scores
    + Sum of all heading-weighted BM25 scores
    + 10.0 (exact match bonus, if present)
```

---

**Example Calculation**:

User question: "claim-land method"
Extracted keywords: ["claim-land", "method"]
Document chunk:
```
## How to Claim Land?

1. Prepare 10 gold coins
2. Stand at the location you want to claim
3. Execute `/claim`
```

Scoring Process:
1. Full question match: × (does not contain the whole question)
2. Keyword "claim-land" (length=2, weight=1):
   - TF=3, Score = 3 × 1.5 / (3 + 1.5 × 0.6) = 2.25 × 1 = 2.25
3. Keyword "method" (length=2, weight=1):
   - TF=2, Score = 2 × 1.5 / (2 + 1.5 × 0.6) = 1.5 × 1 = 1.5
4. Position Weighting: check "## How to Claim Land?" for "claim-land" and "method" → only "claim-land"
   - Extra bonus for the keyword in the heading: +15.0 × 1 = +15.0
5. Exact Match: √ (some keyword present in the document)

Final Score ≈ 2.25 + 1.5 + 15.0 + 0 = 18.75

---

### Cache Optimization

**First Retrieval**:
1. Traverse all knowledge files
2. Parse and segment (intelligent segmentation strategy)
3. Calculate similarity scores
4. Cache the segmentation results
5. Return the Top-K results

**Second Retrieval (same file)**:
- Read chunks directly from the cache
- Speed improved by roughly **70%**

**Cache Invalidation Conditions**:
- Execute `/kila knowledge reload`
- Server restart

---

## ⚙️ Configuration Options

### Configuration File Location

```
plugins/Kilacraft-AI/knowledge.yml
```

> Note: All knowledge-base configuration is centralized in the standalone `knowledge.yml` (in early versions it lived in `config.yml`; it has since been migrated). After editing, run `/kila knowledge reload` or `/kila reload` to apply; Embedding-related items require a restart.

### Related Configuration Items

```yaml
knowledge:
  enabled: true                    # Whether to enable the knowledge base
  max_relevant_chunks: 3           # Maximum number of chunks returned per retrieval

  segment:
    max_size: 500                  # Maximum characters per chunk
    min_size: 20                   # Minimum characters per chunk (chunks below this are filtered)
    overlap: 30                    # Chunk overlap characters (only used by the fallback strategy)

  keywords:
    top_k: 10                      # Number of keywords extracted per query

  bm25:
    k1: 1.5                        # Term frequency saturation parameter (1.2-2.0)
    b: 0.75                        # Document length normalization parameter (0.5-0.8)
    avg_doc_length: 0              # Average document length (0 = auto-statistic at startup; a positive number uses a fixed value)

  retrieval:
    noise_floor: 25.0              # Noise floor: chunks below this score are discarded outright (hard threshold)
    relative_threshold: 0.2        # Relative threshold: chunks below "max score × this ratio" are discarded (soft threshold)
    rrf_k: 60                      # RRF fusion parameter (only effective when Embedding is enabled)

  embedding:
    enabled: false                 # Enable Embedding semantic retrieval (requires api_url/api_key/model)
    api_url: ""                    # Embedding API URL
    api_key: ""                    # API Key
    model: ""                      # Model name (different models have incompatible vector dimensions; requires restart on change)
    dimensions: 1024               # Vector dimensions (must match the chosen model)
    min_similarity: 0.5            # Minimum cosine similarity threshold (recommended 0.45~0.65)
    timeout_seconds: 10            # API timeout (seconds)
    cache_enabled: true            # Vector cache persistence (avoids recomputation on every startup)

  custom_dictionary:
    enabled: true                  # Whether to enable the custom dictionary
    words:                         # Chinese custom words (merged with the built-in vocabulary and corpus-seeded words, deduplicated)
      - "claim"
      - "territory"
      - "redstone"
    words_en: []                   # English custom words
```

**Final filter gate**: `max(noise_floor, max score × relative_threshold)`. This two-stage filter mathematically guarantees that as long as the top-scoring chunk exceeds the noise floor, **at least 1** relevant result is returned — never the "relevant content exists but nothing is returned" case.

### Configuration Explanation

#### `max_relevant_chunks`

Controls the number of knowledge chunks returned per retrieval:

| Value | Effect | Applicable Scenario |
|-------|--------|-------------------|
| 1-2 | Precise answers | Simple questions |
| 3-5 | Balanced | Most scenarios (recommended) |
| 6+ | Comprehensive but verbose | Complex questions |

**Note**: Too many chunks increase token consumption and response time.

---

#### `segment.max_size` / `min_size` / `overlap`

**For a detailed explanation, see the "Segmentation Configuration Parameters" section above**.

Brief description:
- `max_size`: Maximum characters per chunk (default 500)
- `min_size`: Minimum characters per chunk (default 20, **chunks below this will be filtered**)
- `overlap`: Chunk overlap characters (default 30, Strategy 3 only)

---

#### `keywords.top_k`

Controls the number of keywords extracted per query:

| Value | Effect |
|-------|--------|
| 5-10 | Balanced (recommended) |
| 10-15 | Higher recall, but may introduce noise |
| 15+ | High recall, but lower precision |

---

#### `bm25.k1` / `b` / `avg_doc_length`

BM25 algorithm parameters:

| Parameter | Effect | Recommended Value |
|-----------|--------|------------------|
| k1 | Term frequency saturation point (higher values weight frequency more) | 1.2-2.0 |
| b | Document length normalization (higher values penalize long documents more) | 0.5-0.8 |
| avg_doc_length | Average document length, used as the denominator in length normalization | 0 (auto-statistic) |

- `avg_doc_length: 0` (default) — auto-statistics the actual average length of all knowledge-base chunks at startup, so BM25 scores long and short documents more fairly.
- A positive number uses a fixed value (suitable when the knowledge-base content is stable and you don't want it to drift with the corpus).

> **IDF Weight Note**: BM25's IDF (Inverse Document Frequency) uses the `ln(1 + x)` form (the BM25+ variant), not the classic `ln(x)`. The former guarantees IDF is always positive — rare, specialized keywords that appear in only a few documents rank higher, while common words like "player" and "command" that appear everywhere don't dilute the results. The classic `ln(x)` turns negative when a term appears in more than half of all documents, conflicting with the plugin's "keep only chunks with score > 0" filter, so it is not used here.

---

#### `retrieval` - Retrieval Filter Thresholds (refactored in v2.1.2)

The old single hard threshold `min_relevance_score` (default 30) has been replaced by **two-stage filtering**, avoiding the dilemma of "set it high and relevant content gets killed; set it low and noise floods in":

| Parameter | Default | Purpose |
|-----------|---------|---------|
| `noise_floor` | 25.0 | **Noise floor (hard gate)**: chunks with an absolute score below this are discarded outright, no matter how high the top score is |
| `relative_threshold` | 0.2 | **Relative threshold (soft gate)**: chunks scoring below "current top score × this ratio" are discarded (tuned from 0.3 in v2.2.0 for better recall of tangentially related questions) |
| `rrf_k` | 60 | RRF fusion parameter (only effective when Embedding is enabled; see the Embedding section below) |

**Final gate** = `max(noise_floor, top score × relative_threshold)`.

Tuning directions:
- **Want more results** (also answer tangentially related questions): lower `noise_floor`, lower `relative_threshold`.
- **Want stricter behavior** (stay silent rather than mislead when unsure): raise both.
- Most server owners can **just keep the defaults** — `relative_threshold` was tuned from 0.3 to 0.2 in v2.2.0 (better recall, empty-recall behavior unchanged).

**Behavior when no content matches (v2.2.0)**: when the knowledge base truly has no relevant content (or the server has no knowledge files at all), retrieval returns empty and nothing is injected — the AI answers normally and **never mentions the knowledge base**, exactly as if none were configured. When a daily question accidentally retrieves irrelevant chunks, the AI simply ignores them and answers normally, **never fabricating specifics (times, numbers, drops, etc.) from weakly related chunks**. If Embedding is enabled and no chunk passes `min_similarity`, the system automatically falls back to keyword retrieval with the noise floor — weak chunks are not surfaced either.

---

#### `custom_dictionary.enabled` / `words`

---

#### `custom_dictionary.enabled` / `words`

Custom dictionary for adding professional terminology and server-specific vocabulary:

```
custom_dictionary:
  enabled: true
  words:
    - "claim"
    - "territory"
    - "redstone"
    - "mob farm"
```

**Benefits**: Improves Chinese word segmentation accuracy and enhances retrieval effectiveness.

---

#### `embedding` - Embedding Semantic Retrieval (Fused with BM25)

> ⚠️ **Important Change (since v2.1.2)**: Enabling Embedding **no longer replaces BM25**. Instead, **BM25 + Embedding run in parallel**, and the results are fused by rank via **RRF (Reciprocal Rank Fusion)**. The older description "configuring Embedding replaces BM25 with semantic retrieval" is deprecated.

**Why fuse instead of replace**: BM25 excels at exact keyword matching (command names, proper nouns), while Embedding excels at semantic understanding (different phrasings of the same concept). Running both in parallel and fusing them captures the strengths of each — chunks that both paths contribute to rank higher, and chunks that appear in only one path still participate in the ranking.

**Working Principle (fusion mode)**:
```
                ┌── BM25 path: keyword matching → BM25 ranking of each chunk
User query ─────┤
                └── Embedding path: query vectorization → cosine similarity → semantic ranking of each chunk

RRF fusion: fused score = Σ 1 / (rrf_k + rank)    (each path's rank contributes; rrf_k default 60)
          → sort by fused score → take Top-K
```

- A larger `rrf_k` makes the head-rank advantage smoother (favors chunks that rank lower but hit on both paths); a smaller value makes the head-rank advantage more pronounced.
- The Embedding path is only enabled when `embedding.enabled: true` AND valid `api_url`/`api_key`/`model` are configured; otherwise it automatically degrades to pure BM25 (the default path used by the vast majority of server owners).

**Configuration Example** (`knowledge.yml`):

```yaml
knowledge:
  embedding:
    enabled: true                                 # Enable Embedding (must also configure api_url/api_key/model, otherwise auto-degrades to pure BM25)
    # This plugin specifies the provider via api_url; there is no provider field
    api_url: "https://open.bigmodel.cn/api/paas/v4/embeddings"   # Requires restart after change
    api_key: "your-embedding-api-key"             # Requires restart after change
    model: "embedding-3"                          # Requires restart after change (vectors from different models are incompatible)
    dimensions: 1024                              # Vector dimensions (embedding-3: 2048/1024/512/256; bge-large-zh/m3: 1024; text-embedding-3-small: 1536). Requires restart after change.
    min_similarity: 0.5                           # Minimum cosine similarity threshold (0~1). Recommended range: 0.45~0.65
    timeout_seconds: 10                           # API timeout (seconds). Requires restart after change.
    cache_enabled: true                           # Vector cache persistence (avoids recomputation on every startup)
```

**Ops Notes**:
- Vector cache persistence (`cache_enabled: true`): the vectors for knowledge-base chunks are pre-computed and persisted to disk, avoiding re-calling the Embedding API on every startup.
- Supports an Embedding provider different from the conversational LLM (ZhipuAI, SiliconFlow, OpenAI, etc.), as long as it is compatible with the OpenAI Embeddings interface.
- The vector dimensions must match the chosen model; changing the dimensions or model requires deleting the old cache and restarting.
- **Performance optimization**: the norm of each chunk's vector is pre-computed and cached, reducing the cosine-similarity workload by roughly 2/3; concurrent retrieval for multiple players asking questions at once no longer serializes them.

**Applicable Scenarios**:
- Knowledge content is semantically rich and keyword matching is not accurate enough
- User queries are expressed in varied ways (different phrasings of the same concept)
- You expect smarter semantic understanding rather than keyword matching

---

## 📊 Performance Optimization

### 1. File Size Control

**Recommendations**:
- A single file should not exceed **50 KB**
- Keep the total number of chunks within **500**
- Regularly clean up outdated content

**How to Check**:
```bash
# Linux/Mac
ls -lh plugins/Kilacraft-AI/knowledge/

# Windows PowerShell
Get-ChildItem plugins\Kilacraft-AI\knowledge\ | Select-Object Name, Length
```

---

### 2. Reduce Redundant Content

❌ **Avoid Duplication**:
```
# file1.md
## How to Claim Land
Use the /claim command to claim land.

# file2.md
## Claiming Method
Execute /claim to claim land.
```

✅ **Merge Content**:
```
# rules.md
## How to Claim Land
Use the /claim command to claim land. Requires 10 gold coins.

### Detailed Explanation
Claiming land can protect your builds...
```

---

### 3. Use Concise Language

❌ **Verbose**:
```
Regarding the question of how to claim a piece of land belonging to yourself on our server through a specific command and protect it from being destroyed by other players, you need to first ensure that you have enough gold coins (specifically you need 10 gold coins), then stand at the center position of the territory you want to claim, and finally input the /claim command in the chat box.
```

✅ **Concise**:
```
## How to Claim Land

**Requirement**: Have 10 gold coins

**Steps**:
1. Stand at the territory center
2. Execute `/claim`

**Effect**: Protects builds within the territory from destruction
```

---

## 🐛 Troubleshooting

### Q1: AI Not Using Knowledge Base Content?

**Checklist**:

1. **Confirm the knowledge base is enabled**
   ```yaml
   knowledge:
     enabled: true
   ```

2. **Confirm files are loaded**
   ```
   /kila knowledge reload
   ```
   Check the console output:
   ```
   [Kilacraft-AI] Loaded 5 knowledge files, 128 chunks total
   ```

3. **Check the file format**
   - The file extension must be `.md` or `.txt`
   - The file encoding must be UTF-8

4. **Adjust the returned chunk count**
   ```yaml
   max_relevant_chunks: 5  # Increase the return count
   ```

5. **Enable debug mode**
   Set `settings.debug_mode: true` in config.yml
   View the retrieval logs to confirm whether there are matching results.

---

### Q2: Inaccurate Retrieval Results?

**Optimization Methods**:

1. **Improve document structure**
   - Use clear headings
   - Add keywords and synonyms
   - Provide specific examples

2. **Increase related-content density**
   ````
   ## Claim Land / Territory / Protection

   Claiming land (territory, protected area) is...

   Related commands:
   - /claim (claim land)
   - /unclaim (unclaim land)
   ````

3. **Adjust the returned chunk count**
   ```yaml
   max_relevant_chunks: 5  # Increase the return count
   ```

4. **Check segmentation quality**
   Enable debug mode and review the actual chunk content for reasonableness.

---

### Q3: Slow Retrieval Speed?

**Optimization Methods**:

1. **Reduce the knowledge base scale**
   - Delete outdated content
   - Merge similar documents
   - Control single-file size

2. **Use SSD storage**
   Storing knowledge base files on an SSD can significantly improve read speed.

---

### Q4: Poor Chinese Retrieval Effect?

**Known Issues**:
- Chinese word segmentation is more complex than English
- There are many synonyms and near-synonyms

**Solutions**:

1. **Add synonyms in documents**
   ````
   ## Claim Land / Territory / Protected Area / Claim

   Claiming land (also known as territory, protected area, Claim) is...
   ````

2. **Adjust the returned chunk count**
   ```yaml
   max_relevant_chunks: 5  # Increase the return count
   ```

3. **Increase keyword density**
   Mention different expressions of the core concept multiple times in the document.

4. **Provide an FAQ list**
   ````
   ## Frequently Asked Questions

   Q: How do I claim land?
   Q: How do I protect my build?
   Q: How do I get territory?

   A: Use the /claim command...
   ````

---

## 🧱 Standard File Template (Copy and Use Directly)

> The full template is in the same directory: **Knowledge Base File Template.md** (Chinese version: `doc/文档/知识库文件模板.md`). Below is a ready-to-copy working sample — replace the content with your server's real information.
> The `<!-- -->` comments in the file are the writing rules; understanding them lets you optimize any knowledge file.

```markdown
# Server Rules and Common Commands

<!-- Writing rules (ordered by impact on recall, high to low):
   1. Use ## / ### headings to split concepts, and put core words + synonyms in headings.
   2. Do NOT leave an empty line between a heading and its content (an empty line orphans the content into a headerless chunk).
   3. Keep each heading section ≤ 500 chars; if longer, split with ### sub-headings.
   4. Each chunk must be ≥ 20 chars (shorter chunks are dropped).
   5. Also put core words in the body; write command/proper names in full (longer words weigh more).
   6. After each rule, add a "Q:" line listing 2~3 phrasings players might use (adds keyword coverage).
   7. Do not repeat.
   The system uses BOTH keyword matching and semantic matching, so write clear core words AND explain in plain language.-->

## Claim Land / Territory / Protected Area
<!-- Heading contains core words + synonyms. -->
Claiming land (also known as territory, protected area) protects your builds from being destroyed or stolen by other players.
Q: How do I claim land? / How do I protect my home? / How do I apply for territory?

### Claim Steps
1. Prepare 10 gold coins
2. Stand at the center of the area to protect
3. Type `/claim`

### Related Commands
- `/claim` —— Define territory
- `/unclaim` —— Remove territory
- `/trust <player>` —— Add a trusted member

---

## Death Protection / Return to Death Point
When you die you drop items, but you can use `/back` within 30 seconds to return to your death point and recover them.
`/back` cannot be used in combat and requires standing still for 3 seconds.
Q: How do I go back after dying? / Can I recover dropped items? / What's the /back cooldown?

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

---

## Frequently Asked Questions (FAQ)
Q: How do I get started as a beginner?
A: First claim the beginner kit at spawn (`/kit starter`), then use `/spawn` to return to spawn and check the shops.
Q: How do I privately message another player?
A: Use `/msg <player> <content>` to send a private message.
```

**Checklist** (go through this when optimizing any file):
- ☐ Does every `##` heading contain a core word and at least one synonym?
- ☐ Is there **no empty line between each heading and its content** (content not orphaned into a headerless chunk)?
- ☐ Is each section < 500 chars (split with `###` sub-headings if longer)?
- ☐ Is each chunk ≥ 20 chars (not dropped)?
- ☐ Do core words appear in the body (not only in the heading)?
- ☐ Does each rule come with 2~3 likely player phrasings (`Q:`)?
- ☐ Are commands/prices/steps in lists or tables, right under the heading (no empty lines splitting them)?
- ☐ Is no rule duplicated anywhere in the file?
- ☐ Is the file UTF-8 encoded, with a `.md` or `.txt` extension, placed in the `knowledge/` directory?

---

## 🎯 How to Write High-Recall Knowledge Files (Quick Reference)

This translates the retrieval system's concrete behavior into "what to do when writing", ordered by impact on recall (high to low):

| Rank | Writing Rule | Underlying System Behavior | Code Location |
|---|---|---|---|
| 1 | Use `##` / `###` headings to split concepts, and **put core words + synonyms in headings** | Splits by Markdown headings; keywords in headings get extra weighting `+15 × keyword weight`, stackable across keywords | `splitByMarkdownHeaders`, `calculateRelevance` |
| 2 | **Do NOT leave an empty line between a heading and its content** | An empty line cuts off the heading chunk, orphaning the following content into a headerless chunk (loses heading bonus, and may be dropped if < 20 chars) | `splitByMarkdownHeaders` |
| 3 | Also put core words in the body; write command/proper names in **full** | BM25 term-frequency score (× keyword weight × 5) + exact-hit `+10`; longer keywords weigh more (≥4 → ×3, =3 → ×2) | `calculateRelevance`, `BM25Scorer` |
| 4 | Write out synonyms/aliases for the same concept | A different phrasing by the player can still hit (the query is tokenized before matching) | `Chinese/EnglishTextProcessor.toSearchQuery` |
| 5 | Keep each heading section under **500 chars**; split with `###` if longer | Sections exceeding `max_size` are secondarily split, and sub-chunks lose the heading | `splitIntoChunks` |
| 6 | Each chunk must be **≥ 20 chars** | Chunks below `min_size` are dropped | `splitByParagraphs` (`min_size:20`) |
| 7 | Use lists or tables for commands/prices/steps, **right under the heading, with no empty lines between items** | List items with no empty lines stay in one heading chunk and are recalled together; empty lines cut them apart | `splitByMarkdownHeaders` (empty line = cut boundary) |
| 8 | Add 2~3 likely player phrasings (`Q:`) per rule | Adds keyword coverage; for short questions the raw question may match as a whole for `+50` | `calculateRelevance` |
| 9 | Write each rule only once; do not repeat | Duplicates are recalled as two chunks, taking up `max_relevant_chunks` slots and possibly contradicting | `max_relevant_chunks` limit |

> **The two paths to better recall**:
> - ① **Writing optimization** (the table above) — where the value of a paid maintenance service lies;
> - ② **Retrieval-engine tuning** (see `wiki/已实施/知识库检索引擎增强设计文档.md`: BM25+Embedding RRF fusion, optional rerank, soft thresholds).
>
> Important: engine tuning **does not change the segmentation/parsing logic** (the three segmentation strategies, `min_size`/`max_size`, and heading weighting all stay the same), so **the writing rules in this table apply both before and after engine upgrades** — the file template needs no changes due to an engine upgrade.

---

## 🎬 Scenario Example: Let the AI Execute Custom Commands (command skill)

When your server has custom commands (provided by Essentials/CMI etc., such as `/back`, `/home`, `/spawn`, `/tpa`, `/menu`) and you want players to trigger them with natural language, write those commands into the knowledge base and use the command skill (skill name `command`, class `CommandSkill`) (enable `command_skill.enabled: true` in `config.yml`).

**Flow**: player speaks → Phase 2 intent recognition [retrieves the knowledge base] → hits the command skill + extracts the `command` parameter → runs as the player. The knowledge base's job is to teach the Phase 2 LLM the commands and produce the correct `command` (no `/`). The command skill is a **fallback skill** — don't write in requests a dedicated skill (economy/market) already covers.

The full example, design notes, and checklist are in the standalone document **"CommandSkill Knowledge Base Example.md"**. Core writing:

````markdown
# Server Custom Commands

## Return to death point / go back to where you were (/back)
`/back` takes you back to your last position — usually where you died and dropped items. 30-second cooldown; cannot be used in combat.
Q: I died, how do I go back for my stuff / can I get my dropped items back / take me back to where I was
AI executes: back

## Go to your home (/home)
`/home` teleports you to your set home. If you have multiple homes, append the home name, e.g. `/home main-base`.
Q: go home / take me to my base / go to my home called main-base
AI executes: home (or `home main-base` when a name is given)
````

**Key points**:
- Write commands in full as `/back` → triggers **corpus seeding** (`back` enters the HanLP dictionary) + long-word weighting (×3)
- Headings stack **Chinese synonyms** (回家/返回/死亡点) → the main driver of Chinese recall; it's what players hit when speaking Chinese, and corpus seeding can't help here
- Each entry has a `Q:` with player phrasings + an `AI executes: xxx` stating the command value (no `/`)
- Always give a concrete example for parameterized commands (e.g. `AI executes: tpa Steve`)
- Only server custom commands — not requests a dedicated skill already covers

---

## 📚 Related Documentation

- [Server Owner Guide](./Server%20Owner%20Guide) - Complete configuration, troubleshooting
- [Personality System Configuration Guide](./Personality%20System%20Guide) - How to make AI better utilize the knowledge base
- [Bukkit API Reference Manual](./Bukkit%20API%20Reference) - Advanced usage combining the API and the knowledge base
