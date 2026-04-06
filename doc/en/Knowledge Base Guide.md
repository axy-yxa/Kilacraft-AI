# Kilacraft-AI - Knowledge Base Enhancement Guide

> **Version**: v1.4.1  
> **Description**: This document details how to use RAG (Retrieval Augmented Generation) technology to enable AI to provide accurate answers based on your server documentation

---

## 📖 Overview

### What is Knowledge Base Enhancement?

Knowledge Base Enhancement (RAG - Retrieval Augmented Generation) is a technology that allows AI to access **your server's exclusive knowledge**.

**Working Principle**:
```
User Question → Retrieve Relevant Knowledge Segments → Inject Knowledge into Prompt → LLM Generates Answer
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

### 1. Create Knowledge Base Directory

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

### 3. Load Knowledge Base

```
/kilacraft knowledge reload
```

### 4. Test Effect

```
Player: How do I claim land?
AI: You can use the /claim command to define your territory. Requires at least 10 gold coins.
    (Based on "Server Rules" document in knowledge base)
```

---

## 📝 Document Writing Guidelines

### Supported Formats

- ✅ **Markdown (.md)** - Recommended, supports heading segmentation
- ✅ **Plain Text (.txt)** - Simple scenarios

### Markdown Best Practices

#### 1. Use Clear Heading Structure

````
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

**Why Important?**
- AI intelligently segments based on headings
- Can precisely locate relevant chapters during retrieval
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

**Tip**: Using Fortune enchantment can increase drops!
```

❌ **Avoid**:
```
## How to get diamonds

You can mine or go to caves to find or trade with others or do quests, oh and using fortune enchantment is better.
```

---

#### 3. Include Keywords and Synonyms

```
## Claim Land/Territory/Protected Area

**Claiming land** (also known as territory, protected area) is a method to protect your builds from being destroyed by other players.

### How to Claim Land?
1. Prepare 10 gold coins
2. Stand at the location you want to claim
3. Execute `/claim` command

### Related Commands
- `/claim` - Claim territory
- `/unclaim` - Unclaim territory
- `/trust <player>` - Add trusted player
- `/trustlist` - View trust list
```

**Benefits**: Whether players say "claim", "territory" or "protect", relevant content can be retrieved.

---

#### 4. Provide Specific Examples

```
## Economy System Details

### Currency Unit
The server's currency unit is "Gold Coin".

### Common Item Price Reference

| Item | Price Range | Description |
|-------|--------------|-------------|
| Diamond | $80-120 | Fluctuates with market |
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

#### Strategy 3: Hybrid Approach

```
knowledge/
├── basics/               # Basic information
│   ├── rules.md
│   └── getting_started.md
├── advanced/             # Advanced content
│   ├── redstone_guide.md
│   └── building_tips.md
└── reference/            # Reference materials
    ├── commands.md
    └── item_prices.md
```

**Advantages**:
- Clear structure
- Suitable for large servers
- Can load on demand

---

## 🔍 Retrieval Mechanism Details

### Intelligent Segmentation Algorithm

Kilacraft-AI uses **Markdown heading-based intelligent segmentation**:

```
# Server Rules                    ← Segmentation point 1

## Basic Rules                      ← Segmentation point 2

### Prohibited Actions                     ← Segmentation point 3
1. No cheating
2. No insulting

### Allowed Actions                     ← Segmentation point 4
1. Friendly communication
2. Cooperative building

## Economy System                      ← Segmentation point 5
...
```

**Each segment contains**:
- Complete content text
- Heading hierarchy information (for weight calculation)
- Filename and path (for traceability)

---

### Comprehensive Scoring Algorithm

When a user asks a question, the system scores all segments:

#### Level 1: Complete Question Match (Highest Priority)

If segment content directly contains the user's question:

```
User Question: "How do I claim land?"

Matched Segment:
"""
## How to Claim Land?

You can use the /claim command to define your territory...
"""

Score: 100 points (exact match)
```

---

#### Level 2: BM25 Keyword Scoring

Extract keywords from user's question using HanLP TF-IDF algorithm, then calculate match degree using BM25 formula:

```
User Question: "I want to build a farm, what do I need?"

Keywords Extracted: ["farm", "need"]

Matched Segment:
"""
## Farm Building Guide

Building a farm requires preparation:
1. Seeds
2. Water
3. Tools
"""
```

**Chinese Word Segmentation Optimization**:
- Uses **HanLP TF-IDF algorithm** for Chinese
- Intelligently filters stop words ("的", "了", "吗", etc.)
- TF-IDF automatically extracts most important keywords, filters meaningless words
- Supports synonym expansion

---

#### Level 3: Title Match (Base Priority)

If content and keywords don't match, then match titles:

```
User Question: "What are the rules?"

Matched Segment Titles:
- "# Server Rules" → Score 50 points
- "## Basic Rules" → Score 45 points
```

---

**Scoring Formula**:

#### Level 1: Complete Question Match
- If segment content contains complete user question: +50.0 points

#### Level 2: BM25 Keyword Scoring
```
Base Score = TF × (k1 + 1) / (TF + k1 × lengthNorm)
```

**BM25 Formula Components**:
- **TF (Term Frequency)**: Number of times keyword appears in document
- **IDF (Inverse Document Frequency)**: Automatically calculated by HanLP TF-IDF
- **lengthNorm (Document Length Normalization)**: `1 - b + b × (docLength / avgDocLength)`
- **k1 (Term Frequency Saturation Parameter)**: Default 1.5 (recommended 1.2-2.0)
- **b (Document Length Normalization Parameter)**: Default 0.75 (recommended 0.5-0.8)

**Calculation Formula**:
```
Keyword Score = TF × (k1 + 1) / (TF + k1 × lengthNorm)
```

**Keyword Weight**:
- Length ≥ 4: weight 3
- Length = 3: weight 2
- Length < 3: weight 1

#### Level 3: Position Weighting
If keyword appears in heading (line starting with `#`):
```
Keyword Score += 15.0 × Keyword Weight
```

#### Level 4: Exact Match Bonus
If any keyword appears in document:
```
Total Score += 10.0
```

#### Final Score
```
Total Score = 50.0 (complete match, if present) 
    + Sum of all keywords' BM25 scores
    + Sum of all heading-weighted BM25 scores
    + 10.0 (exact match bonus, if present)
```

---

**Example Calculation**:

User Question: "Claim land method"
Extracted Keywords: ["claim", "land", "method"]
Document Segment:
```
## How to Claim Land?

1. Prepare 10 gold coins
2. Stand at the location you want to claim
3. Execute `/claim`
```

Scoring Process:
1. Complete Question Match: × (doesn't contain complete question)
2. Keyword "claim" (length=5, weight=3):
   - TF=2, Score = 2 × 2.5 / (2 + 1.5 × 0.8) = 2.08 × 3 = 6.24
3. Keyword "land" (length=4, weight=3):
   - TF=4, Score = 4 × 2.5 / (4 + 1.5 × 0.8) = 1.85 × 3 = 5.55
4. Keyword "method" (length=6, weight=3):
   - TF=0, Score = 0
5. Position Weighting: Check "## How to Claim Land?" for keywords
   - Contains "claim" and "land": +15.0 × 3 + 15.0 × 3 = +90.0
6. Exact Match: √ (any keyword present in document)
   
Final Score ≈ 6.24 + 5.55 + 0 + 90.0 + 10.0 = 111.79
```

---

### Cache Optimization

**First Retrieval**:
1. Traverse all knowledge files
2. Parse and segment (intelligent segmentation strategy)
3. Calculate similarity scores
4. Cache segment results
5. Return Top-K results

**Second Retrieval (Same File)**:
- Read segments directly from cache
- Speed improved by approximately **70%**

**Cache Invalidation Conditions**:
- Execute `/kilacraft knowledge reload`
- Server restart

---

## ⚙️ Configuration Options

### Configuration File Location

```
plugins/Kilacraft-AI/config.yml
```

### Related Configuration Items

```yaml
knowledge:
  enabled: true                    # Whether to enable knowledge base
  max_relevant_chunks: 3           # Maximum segments returned per query
  
  segment:
    max_size: 500                  # Maximum characters per chunk
    min_size: 25                   # Minimum characters per chunk
    overlap: 30                    # Chunk overlap characters (context retention)
  
  keywords:
    top_k: 10                      # Number of keywords extracted per query
  
  bm25:
    k1: 1.5                        # Term frequency saturation parameter (1.2-2.0)
    b: 0.75                        # Document length normalization parameter (0.5-0.8)
  
  custom_dictionary:
    enabled: true                    # Whether to enable custom dictionary
    words:
      - "圈地"
      - "领地"
      - "红石"
```

### Configuration Explanation

#### `max_relevant_chunks`

Controls the number of knowledge segments returned per retrieval:

| Value | Effect | Applicable Scenario |
|-------|--------|-------------------|
| 1-2 | Precise answers | Simple questions |
| 3-5 | Balanced | Most scenarios (recommended) |
| 6+ | Comprehensive but verbose | Complex questions |

**Note**: Too many segments will increase token consumption and response time.

---

#### `segment.max_size` / `min_size` / `overlap`

Controls knowledge base segmentation strategy:

| Config Item | Effect | Default Value |
|-------------|--------|--------------|
| max_size | Maximum characters per chunk | 500 |
| min_size | Minimum characters per chunk | 25 |
| overlap | Chunk overlap characters (context retention) | 30 |

**Segmentation Strategy**:
1. Priority: Markdown heading segmentation
2. Secondary: Paragraph (empty line) segmentation
3. Fallback: Fixed-size segmentation

---

#### `keywords.top_k`

Controls the number of keywords extracted per query:

| Value | Effect |
|-------|--------|
| 5-10 | Balanced (recommended) |
| 10-15 | More recall, but may introduce noise |
| 15+ | High recall, but lower precision |

---

#### `bm25.k1` / `b`

BM25 algorithm parameters:

| Parameter | Effect | Recommended Value |
|-----------|--------|------------------|
| k1 | Term frequency saturation point (higher values value frequency more) | 1.2-2.0 |
| b | Document length normalization (higher values penalize long documents more) | 0.5-0.8 |

---

#### `custom_dictionary.enabled` / `words`

Custom dictionary for adding professional terminology and server-specific vocabulary:

```yaml
custom_dictionary:
  enabled: true
  words:
    - "圈地"
    - "领地"
    - "红石"
    - "刷怪塔"
```

**Benefits**: Improves Chinese word segmentation accuracy, enhances retrieval effectiveness.

---

## 📊 Performance Optimization

### 1. File Size Control

**Recommendations**:
- Single file not exceeding **50 KB**
- Total segments controlled within **500**
- Regularly clean up outdated content

**Check Method**:
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
Use /claim command to claim land.

# file2.md
## Claiming Method
Execute /claim to claim land.
```

✅ **Merge Content**:
```
# rules.md
## How to Claim Land
Use /claim command to claim land. Requires 10 gold coins.

### Detailed Explanation
Claiming land can protect your builds...
```

---

### 3. Use Concise Language

❌ **Verbose**:
```
Regarding the question of how to claim a piece of land belonging to yourself on our server through specific commands and protect it from being destroyed by other players, you need to first ensure that you have enough gold coins (specifically you need 10 gold coins), then stand at the center position of the territory you want to claim, and finally input the /claim command in the chat box.
```

✅ **Concise**:
```
## How to Claim Land

**Requirements**: Have 10 gold coins

**Steps**:
1. Stand at territory center
2. Execute `/claim`

**Effect**: Protect builds within territory from destruction
```

---

## 🐛 Troubleshooting

### Q1: AI Not Using Knowledge Base Content?

**Checklist**:

1. **Confirm Knowledge Base Enabled**
   ```yaml
   knowledge:
     enabled: true
   ```

2. **Confirm Files Loaded**
   ```
   /kilacraft knowledge reload
   ```
   Check console output:
   ```
   [Kilacraft-AI] Loaded 5 knowledge files, total 128 segments
   ```

3. **Check File Format**
   - File extension must be `.md` or `.txt`
   - File encoding must be UTF-8

4. **Adjust Returned Segment Count**
   ```yaml
   max_relevant_chunks: 5  # Increase return count
   ```

5. **Enable Debug Mode**
   Set `settings.debug_mode: true` in config.yml
   View retrieval logs to confirm if there are matching results.

---

### Q2: Inaccurate Retrieval Results?

**Optimization Methods**:

1. **Improve Document Structure**
   - Use clear headings
   - Add keywords and synonyms
   - Provide specific examples

2. **Increase Related Content Density**
   ````
   ## Claim Land/Territory/Protection
   
   Claiming land (territory, protected area) is...
   
   Related commands:
   - /claim (claim land)
   - /unclaim (unclaim land)
   ```

3. **Adjust Returned Segment Count**
   ```yaml
   max_relevant_chunks: 5  # Increase return count
   ```

4. **Check Segmentation Quality**
   Enable debug mode to view actual segment content for reasonableness.

---

### Q3: Slow Retrieval Speed?

**Optimization Methods**:

1. **Reduce Knowledge Base Scale**
   - Delete outdated content
   - Merge similar documents
   - Control single file size

2. **Use SSD Storage**
   Storing knowledge base files on SSD can significantly improve read speed.

---

### Q4: Poor Chinese Retrieval Effect?

**Known Issues**:
- Chinese word segmentation is more complex than English
- More synonyms and near-synonyms

**Solutions**:

1. **Add Synonyms in Documents**
   ````
   ## Claim Land/Territory/Protected Area/Claim
   
   Claiming land (also known as territory, protected area, Claim) is...
   ```

2. **Adjust Returned Segment Count**
   ```yaml
   max_relevant_chunks: 5  # Increase return count
   ```

3. **Increase Keyword Density**
   Mention different expressions of core concepts multiple times in documents.

4. **Provide FAQ List**
   ````
   ## Frequently Asked Questions
   
   Q: How do I claim land?
   Q: How to protect my build?
   Q: How to get territory?
   
   A: Use /claim command...
   ```

---

## 📈 Monitoring and Maintenance

### Regular Inspection Checklist

- [ ] Whether knowledge base content is synchronized with server version
- [ ] Whether there are outdated rules or commands
- [ ] Whether file sizes are within reasonable range
- [ ] Whether retrieval accuracy meets requirements
- [ ] Whether new knowledge documents need to be added

---

## 📚 Related Documentation

- [Server Owner Guide](./Server Owner Guide) - Complete configuration, troubleshooting
- [Personality System Configuration Guide](./Personality System Configuration Guide) - How to make AI better utilize knowledge base
- [Bukkit API Reference Manual](./Bukkit API Reference Manual) - Advanced usage combining API and knowledge base

---

> **Last Updated**: 2026-04-05  
> **Plugin Version**: 1.4.1+  
> **Tip**: Regularly update knowledge base to keep content synchronized with server
