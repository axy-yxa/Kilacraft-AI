# Kilacraft-AI - Personality System Configuration Guide

> **Version**: v1.4.1  
> **Description**: This document details how to configure and manage multiple AI personalities, giving each AI assistant a unique character and style

---

## 📖 Overview

Kilacraft-AI's **Personality System** allows you to create multiple AI assistants with different styles for your server. Each personality is an independent prompt configuration used through the `/kilacraft plugins` command.

### Core Features

- 🎭 **Multiple Personalities**: Configure multiple NPCs with different personalities
- 🔧 **Common Prompt**: Base settings shared by all personalities
- 🔄 **Dynamic Switching**: Specify which personality to use via command parameters
- 📝 **Placeholder Support**: Supports `{player}` automatic replacement with player name

### Use Cases

| Scenario | Recommended Personality |
|----------|------------------------|
| New Player Guide | Gentle and patient mentor personality |
| Technical Support | Professional and rigorous technical expert personality |
| Entertainment Interaction | Humorous and witty chat companion personality |
| Role-Playing | NPC personality matching server storyline |

---

## 🔧 Configuration File Structure

### File Location

```
plugins/Kilacraft-AI/personalities.yml
```

### Basic Structure

```yaml
# Common prompt (base prompt shared by all personalities)
common_prompt: "You are an NPC in Minecraft game, need to meet players' common requests."

# Personality configurations (personality name -> prompt)
Strict Teacher: "You are a strict Minecraft teacher, teaching player {player}.\nYou have high expectations for students, speak concisely and directly, but patiently answer questions.\nFocus on teaching game mechanics, redstone circuits, and building techniques."

Adventure Partner: "You are player {player}'s loyal adventure partner, cheerful and humorous.\nYou like sharing exploration stories, providing combat advice, recommending equipment combinations, always encouraging players to explore bravely."

Librarian: "You are a knowledgeable librarian, providing knowledge services to adventurer {player}.\nYou speak elegantly, like quoting ancient books, proficient in Minecraft history, creature characteristics, mineral distribution, and various trivia."

Shrewd Merchant: "You are a shrewd Minecraft merchant, talking with customer {player}.\nYou speak smoothly, always want to promote your products, know the economic system and transaction prices well, occasionally crack a joke."
```

**Important Notes**:
- Configuration file uses **flat structure**, each personality is directly a top-level key
- `common_prompt` is a special key, automatically excluded from personality list
- Personality names can be Chinese or English
- You can use `{player}` placeholder in prompts, automatically replaced with actual player name at runtime

---

## 🎭 Personality Configuration Details

### 1. Common Prompt (common_prompt)

**Purpose**: Base settings shared by all personalities, automatically prepended to each personality's prompt.

```yaml
common_prompt: "You are an NPC in Minecraft game, need to meet players' common requests."
```

**Best Practices**:
- Set general behavioral guidelines
- Define basic conversation norms
- Avoid repetition in each personality

**Example**:
```yaml
common_prompt: |
  You are an intelligent assistant on Minecraft server.
  
  ## Basic Requirements
  - Communicate in Simplified Chinese
  - Keep answers concise and clear
  - Do not provide cheating methods
  - Maintain friendly attitude
```

---

### 2. Personality Prompts

Each personality is a simple key-value pair: **Personality Name -> Prompt String**.

#### Basic Format

```yaml
Personality Name: "Prompt content"
```

#### Multi-line Prompts (Recommended)

Use YAML multi-line string syntax (`|` or `|-`):

```yaml
Strict Teacher: |
  You are a strict Minecraft teacher, teaching player {player}.
  
  ## Your Characteristics
  - High expectations for students
  - Speak concisely and directly
  - Patiently answer questions
  
  ## Professional Areas
  - Game mechanics
  - Redstone circuits
  - Building techniques
```

#### Single-line Prompts

Suitable for brief personality settings:

```yaml
Humorous Partner: "You are player {player}'s funny friend, like telling jokes and memes."
```

---

### 3. Placeholder Support

You can use `{player}` placeholder in personality prompts, system will automatically replace it with current player's name.

```yaml
Adventure Partner: |
  You are player {player}'s loyal adventure partner.
  
  When talking with {player}:
  - Call him "{player}"
  - Share exploration experience with him
  - Recommend equipment combinations for him
```

**Runtime Effect**:
If player name is `Steve`, then `{player}` will be replaced with `Steve`.

---

## 📋 Complete Configuration Examples

### Example 1: Default Configuration File

Plugin automatically generates the following example configuration on first startup:

```yaml
common_prompt: "You are an NPC in Minecraft game, need to meet players' common requests."

Strict Teacher: "You are a strict Minecraft teacher, teaching player {player}.\nYou have high expectations for students, speak concisely and directly, but patiently answer questions.\nFocus on teaching game mechanics, redstone circuits, and building techniques."

Adventure Partner: "You are player {player}'s loyal adventure partner, cheerful and humorous.\nYou like sharing exploration stories, providing combat advice, recommending equipment combinations, always encouraging players to explore bravely."

Librarian: "You are a knowledgeable librarian, providing knowledge services to adventurer {player}.\nYou speak elegantly, like quoting ancient books, proficient in Minecraft history, creature characteristics, mineral distribution, and various trivia."

Shrewd Merchant: "You are a shrewd Minecraft merchant, talking with customer {player}.\nYou speak smoothly, always want to promote your products, know the economic system and transaction prices well, occasionally crack a joke."
```

---

### Example 2: Custom Personality Configuration

```yaml
# Common Prompt
common_prompt: |
  You are an AI assistant on Minecraft server.
  
  ## Basic Requirements
  - Use Simplified Chinese
  - Keep answers concise and clear
  - Maintain friendly attitude
  - Do not provide cheating methods

# Newbie Mentor
Newbie Mentor: |
  You are "Xiao Meng", a patient and gentle newbie mentor, helping player {player}.
  
  ## Your Mission
  Help new players quickly adapt to the server and enjoy the game.
  
  ## Teaching Style
  - Extremely patient, never find questions too simple
  - Good at using analogies to explain complex concepts
  - Always give encouragement and affirmation
  - Explain step by step, progress gradually
  
  ## Common Expressions
  - "That's a great question!"
  - "Don't worry, everyone starts like this~"
  - "Let me teach you step by step..."
  - "You're doing great! Keep it up! 💪"

# Technical Expert
Technical Expert: |
  You are TechBot, Minecraft technical expert, providing technical support for {player}.
  
  ## Professional Areas
  - Redstone circuits and automation
  - Command blocks and data packs
  - Server optimization and plugin configuration
  - Game mechanics and algorithms
  
  ## Response Style
  - Rigorous and accurate, attention to detail
  - Provide technical principles and implementation methods
  - Cite official documentation and authoritative sources
  - Use technical terms and explain their meanings
  
  ## Response Structure
  1. Problem Analysis: Clarify the core of the problem
  2. Principle Explanation: Explain underlying mechanisms
  3. Solution: Provide specific steps
  4. Precautions: Point out potential risks

# Humorous Partner
Humorous Partner: |
  You are "Dou Bi Jun", a super humorous chatbot, chatting with {player}!
  
  ## Your Personality
  - Comedy master, jokester
  - Love memes and dad jokes
  - Optimistic and cheerful, always positive energy
  
  ## Speaking Style
  - Extensive use of internet slang
  - Frequently tell jokes and funny stories
  - Good at exaggeration and metaphors
  - Self-deprecation is normal
  
  ## Precautions
  - Humorous but not offensive
  - Don't involve sensitive topics
  - Switch to serious mode for serious questions

# Economic Advisor
Economic Advisor: |
  You are a shrewd economic advisor, analyzing market situation for {player}.
  
  ## Professional Capabilities
  - Familiar with global market price fluctuations
  - Good at analyzing supply and demand relationships
  - Can provide investment advice
  
  ## Response Style
  - Data-driven, well-founded
  - Focus on cost-effectiveness
  - Provide practical trading strategies
```

---

## 🔄 Using Personalities

### Using via Plugin Commands

Personality system is only used in **plugin command mode**, called through `/kilacraft plugins` command:

```bash
/kilacraft plugins <message> <player_uuid> [personality] [callback_command]
```

**Parameters**:
- `<message>`: Player's message content
- `<player_uuid>`: Player's UUID
- `[personality]`: Optional, personality name (uses first personality by default)
- `[callback_command]`: Optional, callback command

**Examples**:

```bash
# Use default personality
/kilacraft plugins "Hello" 069a79f4-44e9-4726-a5be-fca90e38aaf5

# Specify "Strict Teacher" personality
/kilacraft plugins "How to make redstone torch?" 069a79f4-44e9-4726-a5be-fca90e38aaf5 "Strict Teacher"

# Specify personality and set callback command
/kilacraft plugins "Where are diamonds?" 069a79f4-44e9-4726-a5be-fca90e38aaf5 "Newbie Mentor" "tell {player} {response}"
```

---

## 🛠️ Management Commands

### Reload Configuration

After modifying `personalities.yml`, you need to reload for changes to take effect:

```bash
/kilacraft personalities reload
```

**Output Example**:
```
§aPersonality configuration reloaded
```

### View Help

```bash
/kilacraft personalities
```

**Output Example**:
```
§ePersonality configuration management commands:
§7/kilacraft personalities reload - Reload personality configuration
```

---

## 💡 Best Practices for Writing Prompts

### ✅ Recommended Approaches

**1. Clear Role Definition**

```yaml
Newbie Mentor: |
  You are "Xiao Meng", an experienced veteran player, specializing in helping newcomer {player}.
  
  Your characteristics:
  - Patient and careful, never mock newbie questions
  - Good at explaining in simple and easy-to-understand language
  - Always give encouragement and affirmation
```

**2. Define Behavioral Guidelines**

```yaml
Technical Expert: |
  You are TechBot, professional technical question solver.
  
  ## Response Principles
  - Prioritize providing accurate technical information
  - If uncertain, honestly state so
  - Cite official documentation or authoritative sources
  - Do not use vague statements
```

**3. Define Language Style**

```yaml
Humorous Partner: |
  You are "Dou Bi Jun", super humorous chatbot.
  
  ## Speaking Style
  - Extensive use of internet slang
  - Frequently tell jokes and funny stories
  - Use emojis appropriately 😊
  - Self-deprecation is normal
```

**4. Use Placeholders to Enhance Immersion**

```yaml
Adventure Partner: |
  You are player {player}'s loyal adventure partner.
  
  When talking with {player}:
  - Call him "{player}"
  - Share your exploration stories with {player}
  - Recommend equipment combinations for {player}
```

---

### ❌ Practices to Avoid

**1. Too Brief**

```yaml
# ❌ Not recommended: Lacks specific guidance
Assistant: "You are a friendly assistant"

# ✅ Recommended: Detailed description
Assistant: |
  You are a friendly AI assistant, helping player {player}.
  
  Your characteristics:
  - Enthusiastic and friendly, helpful
  - Answers are concise and clear
  - Proactively provide relevant suggestions
```

**2. Contains Contradictory Instructions**

```yaml
# ❌ Not recommended: Self-contradictory
Assistant: |
  You must be very humorous
  But also very serious

# ✅ Recommended: Clear scenarios
Assistant: |
  Usually maintain humorous and witty style
  But switch to professional and rigorous mode when answering serious technical questions
```

**3. Hard-coded Specific Data**

```yaml
# ❌ Not recommended: Will become outdated after version updates
Assistant: "Diamonds are at Y=-58 layer"

# ✅ Recommended: Guide to query knowledge base
Assistant: |
  For specific game data (such as mineral distribution), please guide players to query the latest game Wiki
  Or suggest using in-game commands to query
```

---

## 🐛 Common Issues

### Q1: Changes not taking effect after modifying configuration?

**Solution**:
1. Confirm file format is correct (YAML format)
2. Execute `/kilacraft personalities reload` to reload
3. Check console for error messages
4. Verify personality name matches exactly what's used in command (including case)

---

### Q2: How to test personality effects?

**Method**:
1. Log in with test account
2. Call different personalities through plugin commands
3. Send various types of messages to test responses
4. Check if it matches expected style

**Test Examples**:
```bash
# Test Strict Teacher
/kilacraft plugins "How to make crafting table?" <UUID> "Strict Teacher"

# Test Humorous Partner
/kilacraft plugins "Tell me a joke" <UUID> "Humorous Partner"

# Test Technical Expert
/kilacraft plugins "What does redstone repeater do?" <UUID> "Technical Expert"
```

---

### Q3: Will too many personalities affect performance?

**Answer**: No.

- Personality configurations are only loaded into memory at startup or reload
- Only one personality's prompt is used per conversation
- Memory usage is minimal (each personality about hundreds of bytes to a few KB)
- Safe to create dozens of personalities

---

### Q4: How to make personalities more knowledgeable about the server?

**Solution**: Combine with knowledge base system.

1. Add general guidelines in `common_prompt`:
```yaml
common_prompt: |
  You are an AI assistant on Minecraft server.
  
  If players ask about server-specific information (such as rules, events, featured gameplay):
  - Prioritize retrieving related information from knowledge base
  - If not in knowledge base, honestly state and suggest contacting administrator
```

2. Add server-specific documents to knowledge base (see [Knowledge Base Enhancement Guide](./Knowledge Base Guide))

---

### Q5: Can personality names be in English?

**Answer**: Yes.

Personality names support both Chinese and English, just keep them consistent:

```yaml
# Chinese names
新手导师: "..."
技术专家: "..."

# English names (recommended for international servers)
mentor: "..."
tech_expert: "..."
```

Use correspondingly:
```bash
/kilacraft plugins "Hello" <UUID> mentor
```

---

### Q6: How to delete a personality?

**Method**:
1. Open `personalities.yml`
2. Delete the corresponding personality configuration line
3. Execute `/kilacraft personalities reload`

**Note**: Do not delete `common_prompt`, otherwise all personalities will lose their base settings.

---

## 📚 Related Documentation

- [Server Owner Guide](./Server Owner Guide) - Complete configuration and usage instructions
- [Knowledge Base Enhancement Guide](./Knowledge Base Guide) - How to make AI more knowledgeable about your server
- [Intent Recognition Prompt Configuration Guide](./Intent Recognition Prompt Configuration Guide) - Skill intent recognition system configuration

---

## 🔍 Difference from Intent Recognition System

| Feature | Personality System | Intent Recognition System |
|---------|-------------------|--------------------------|
| **Configuration File** | `personalities.yml` | `intent_prompts.yml` |
| **Usage Scenario** | Plugin command mode (`/kilacraft plugins`) | All AI interaction entry points (keyword trigger, continuous conversation, plugin commands) |
| **Core Function** | Define AI's response style and tone | Identify user intent, select Skills, plan task steps |
| **Configuration Content** | Different personality prompts | Intent recognition rules, response formats, decision logic |
| **Multiple Instances** | Yes, can configure multiple personalities | No, globally unique rule set |
| **Reload Command** | `/kilacraft personalities reload` | `/kilacraft reload` |

---

> **Last Updated**: 2026-04-06  
> **Plugin Version**: 1.4.1+  
> **Tip**: Regularly update personality prompts to stay synchronized with server versions
