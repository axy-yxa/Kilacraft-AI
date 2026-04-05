# Kilacraft-AI - Knowledge Base Enhancement Guide

> **Version**: v1.4.0  
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

```markdown
# Level 1 Heading (Document Topic)

## Level 2 Heading (Main Category)

### Level 3 Heading (Subcategory)

#### Level 4 Heading (Detailed Content)
```

**Example**:
```markdown
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
```markdown
## How to Get Diamonds?

You can obtain diamonds through the following methods:

1. **Mining**: Dig at Y=-58 to Y=-53 layers
2. **Exploration**: Find exposed veins in caves and ravines
3. **Trading**: Exchange with other players
4. **Rewards**: Complete server tasks

**Tip**: Using Fortune enchantment can increase drops!
```

❌ **Avoid**:
```markdown
## How to get diamonds

You can mine or go to caves to find or trade with others or do quests, oh and using fortune enchantment is better.
```

---

#### 3. Include Keywords and Synonyms

```markdown
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

**Benefit**: Whether players say "claim", "territory" or "protect", relevant content can be retrieved.

---

#### 4. Provide Specific Examples

```markdown
## Economy System Details

### Currency Unit
The server's currency unit is "Gold Coin".

### Common Item Price Reference

| Item | Price Range | Description |
|------|------------|-------------|
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

```markdown
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
- Filename and path (for溯源)

---

### Three-Level Scoring Mechanism

When user asks a question, the system scores all segments:

#### Level 1: Complete Question Match (Highest Priority)

If segment content directly contains user's question:

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

#### Level 2: Keyword Match (Medium Priority)

Extract keywords from user question, calculate match degree:

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

Score: 75 points (keyword match)
```

**Chinese Word Segmentation Optimization**:
- Uses n-gram algorithm for Chinese
- Intelligently filters stop words ("的", "了", "吗", etc.)
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

### Cache Optimization

**First Retrieval**:
1. Traverse all knowledge files
2. Parse and segment
3. Calculate similarity scores
4. Return Top-K results

**Second Retrieval (Same Question)**:
- Read results directly from cache
- Speed improved by approximately **70%**

**Cache Invalidation Conditions**:
- Execute `/kilacraft knowledge reload`
- Knowledge files modified
- Server restart

---

## ⚙️ Configuration Options

### Configuration File Location

```
plugins/Kilacraft-AI/config.yml
```

### Related Configuration Items

```yaml
knowledge_base:
  enabled: true                  # Whether to enable knowledge base
  directory: "knowledge"         # Knowledge base directory path
  max_chunks_per_query: 3        # Maximum segments returned per query
  similarity_threshold: 0.3      # Similarity threshold (0.0-1.0)
  cache_enabled: true            # Whether to enable cache
  cache_ttl_seconds: 3600        # Cache expiration time (seconds)
  debug_mode: false              # Debug mode (output detailed logs)
```

### Configuration Explanation

#### `max_chunks_per_query`

Controls the number of knowledge segments returned per retrieval:

| Value | Effect | Applicable Scenario |
|-------|--------|-------------------|
| 1-2 | Precise answers | Simple questions |
| 3-5 | Balanced | Most scenarios (recommended) |
| 6+ | Comprehensive but verbose | Complex questions |

**Note**: Too many segments will increase token consumption and response time.

---

#### `similarity_threshold`

Set minimum similarity threshold to filter irrelevant results:

| Value | Effect |
|-------|--------|
| 0.1-0.3 | Lenient, returns more results |
| 0.3-0.5 | Balanced (recommended) |
| 0.5-0.8 | Strict, only returns highly relevant results |
| 0.8+ | Very strict, may return no results |

---

#### `cache_ttl_seconds`

Cache expiration time:

```yaml
cache_ttl_seconds: 3600   # 1 hour
# or
cache_ttl_seconds: 86400  # 24 hours
# or
cache_ttl_seconds: 0      # Never expires (not recommended)
```

---

#### `debug_mode`

After enabling debug mode, console outputs detailed retrieval information:

```yaml
debug_mode: true
```

**Output Example**:
```
[KnowledgeRetriever] Starting knowledge retrieval...
[KnowledgeRetriever] Retrieval time: 45ms
[KnowledgeRetriever] Total files: 5, Total segments: 128
[KnowledgeRetriever] Matched segments: 12, Returning top 3

[KnowledgeRetriever] === Matched Segment Details ===
[KnowledgeRetriever] [1] File: server_rules.md, Score: 95.2, Length: 256 characters
  Preview: "## How to Claim Land? You can use /claim command..."
[KnowledgeRetriever] [2] File: commands.md, Score: 78.5, Length: 180 characters
  Preview: "### /claim - Claim territory..."
[KnowledgeRetriever] [3] File: faq.md, Score: 65.3, Length: 120 characters
  Preview: "Q: How to protect my build? A: Use claim system..."
```

**When to Enable**:
- Debugging retrieval效果
- Optimizing knowledge base content
- Troubleshooting related issues

**Production Environment Recommendation**: Disable to save log space.

---

## 🎯 Advanced Usage

### 1. Multilingual Knowledge Base

Provide exclusive knowledge bases for players of different languages:

```
knowledge/
├── zh_CN/              # Simplified Chinese
│   ├── rules.md
│   └── guide.md
├── en_US/              # English
│   ├── rules.md
│   └── guide.md
└── ja_JP/              # Japanese
    ├── rules.md
    └── guide.md
```

Dynamically load based on player language in code:

```java
String lang = player.getLocale(); // "zh_CN", "en_US", etc.
String knowledgeDir = "knowledge/" + lang;
```

---

### 2. Permission-Controlled Knowledge Base

Provide different knowledge content for different player groups:

```
knowledge/
├── public/             # Visible to everyone
│   ├── rules.md
│   └── faq.md
├── vip/                # VIP exclusive
│   ├── vip_benefits.md
│   └── exclusive_items.md
└── staff/              # Administrator exclusive
    ├── admin_commands.md
    └── moderation_guide.md
```

Implementation:
```java
if (player.hasPermission("group.vip")) {
    loadKnowledge("knowledge/vip/");
}
```

---

### 3. Time-Sensitive Knowledge

Create temporary knowledge bases for limited-time events:

```
knowledge/
├── permanent/          # Permanent knowledge
│   └── rules.md
└── seasonal/           # Seasonal knowledge
    ├── christmas_2026.md
    └── halloween_2026.md
```

Delete or archive after event ends:
```bash
mv knowledge/seasonal/christmas_2026.md knowledge/archive/
/kilacraft knowledge reload
```

---

### 4. Versioned Knowledge Base

Track knowledge base change history:

```
knowledge/
├── current/            # Current version
│   └── rules.md
├── v1.0/               # Historical versions
│   └── rules.md
└── v2.0/
    └── rules.md
```

Rollback to old version:
```bash
cp knowledge/v1.0/rules.md knowledge/current/rules.md
/kilacraft knowledge reload
```

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
```markdown
# file1.md
## How to Claim Land
Use /claim command to claim land.

# file2.md
## Claiming Method
Execute /claim to claim land.
```

✅ **Merge Content**:
```markdown
# rules.md
## How to Claim Land
Use /claim command to claim land. Requires 10 gold coins.

### Detailed Explanation
Claiming land can protect your builds...
```

---

### 3. Use Concise Language

❌ **Verbose**:
```markdown
Regarding the question of how to claim a piece of land belonging to yourself on our server through specific commands and protect it from being destroyed by other players, you need to first ensure that you have enough gold coins (specifically you need 10 gold coins), then stand at the center position of the territory you want to claim, and finally input the /claim command in the chat box.
```

✅ **Concise**:
```markdown
## How to Claim Land

**Requirements**: Have 10 gold coins

**Steps**:
1. Stand at territory center
2. Execute `/claim`

**Effect**: Protect builds within territory from destruction
```

---

### 4. Index Hot Content

For frequently queried content, extract as independent files:

```
knowledge/
├── hot_topics/         # Hot content (priority retrieval)
│   ├── how_to_claim.md
│   ├── how_to_make_money.md
│   └── server_rules.md
└── others/             # Other content
    └── ...
```

Prioritize searching `hot_topics/` directory during retrieval.

---

## 🐛 Troubleshooting

### Q1: AI Not Using Knowledge Base Content?

**Checklist**:

1. **Confirm Knowledge Base Enabled**
   ```yaml
   knowledge_base:
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

4. **Adjust Similarity Threshold**
   ```yaml
   similarity_threshold: 0.2  # Lower threshold
   ```

5. **Enable Debug Mode**
   ```yaml
   debug_mode: true
   ```
   View retrieval logs to confirm if there are matching results.

---

### Q2: Inaccurate Retrieval Results?

**Optimization Methods**:

1. **Improve Document Structure**
   - Use clear headings
   - Add keywords and synonyms
   - Provide specific examples

2. **Increase Related Content Density**
   ```markdown
   ## Claim Land/Territory/Protection
   
   Claiming land (territory, protected area) is...
   
   Related commands:
   - /claim (claim land)
   - /unclaim (unclaim land)
   ```

3. **Adjust Returned Segment Count**
   ```yaml
   max_chunks_per_query: 5  # Increase return count
   ```

4. **Check Segmentation Quality**
   Enable debug mode to view actual segment content合理性.

---

### Q3: Slow Retrieval Speed?

**Optimization Methods**:

1. **Enable Cache**
   ```yaml
   cache_enabled: true
   cache_ttl_seconds: 3600
   ```

2. **Reduce Knowledge Base Scale**
   - Delete outdated content
   - Merge similar documents
   - Control single file size

3. **Use SSD Storage**
   Storing knowledge base files on SSD can significantly improve read speed.

4. **Preload Common Knowledge**
   Preload hot content to memory when plugin starts.

---

### Q4: Poor Chinese Retrieval Effect?

**Known Issues**:
- Chinese word segmentation is more complex than English
- More synonyms and near-synonyms

**Solutions**:

1. **Add Synonyms in Documents**
   ```markdown
   ## Claim Land/Territory/Protected Area/Claim
   
   Claiming land (also known as territory, protected area, Claim) is...
   ```

2. **Use Lower Similarity Threshold**
   ```yaml
   similarity_threshold: 0.2
   ```

3. **Increase Keyword Density**
   Mention different expressions of core concepts multiple times in documents.

4. **Provide FAQ List**
   ```markdown
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

### Statistics

View knowledge base statistics:
```
/kilacraft knowledge stats
```

**Output Example**:
```
=== Knowledge Base Statistics ===
Total Files: 5
Total Segments: 128
Cache Hits: 342
Cache Misses: 58
Average Retrieval Time: 45ms
```

---

## 📚 Related Documentation

- [Server Owner Guide](./服主指南) - Complete configuration and usage instructions
- [Personality System Configuration Guide](./人格系统配置指南) - How to make AI better utilize knowledge base
- [Bukkit API Reference Manual](./Bukkit-API参考手册) - Advanced usage combining API and knowledge base

---

> **Last Updated**: 2026-04-05  
> **Plugin Version**: 1.4.0+  
> **Tip**: Regularly update knowledge base to keep content synchronized with server
