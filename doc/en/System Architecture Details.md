# Kilacraft-AI System Architecture Details

> **This document provides detailed explanation of Kilacraft-AI's core architecture design, working principles of three interaction modes, call chains, and design philosophy.**  
> Suitable for developers and technical personnel who need in-depth understanding of the system's internal mechanisms.

---

## 📋 Table of Contents

- [Overview of Three Interaction Modes](#-overview-of-three-interaction-modes)
- [Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)](#-mode-1--2-chatlistener--kilacraftcommand-agent-enabled)
- [Mode 3: Plugin Command Mode (Agent Disabled)](#-mode-3-plugin-command-mode-agent-disabled)
- [Core Differences Comparison Table](#-core-differences-comparison-table)
- [Design Philosophy Summary](#-design-philosophy-summary)
- [How to Choose Which Mode to Use?](#-how-to-choose-which-mode-to-use)

---

## 🔄 Overview of Three Interaction Modes

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenarios |
|------|---------------|------------------|-------------------------|-------------------|------------------|
| **ChatListener** | `@ai` keyword / continuous chat | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Player active interaction |
| **KilacraftCommand** | `/kilacraft <message>` | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Server owner/admin queries |
| **Plugin Command** | `/kilacraft plugins ...` | ❌ Disabled | ✅ Normal retrieval | ✅ Required | Third-party plugin integration |

---

## 📊 Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)

These two modes share the same Agent processing flow, differing only in trigger method.

### Complete Call Chain

```
User Input: "@ai What am I holding? How much can this sell for?"
  ↓
【1. Entry Layer】ChatListener / KilacraftCommand
  ├─ Check permissions and cooldown
  ├─ Send "Thinking..." message
  └─ Get conversation history
  ↓
【2. Intent Recognition Layer】SkillIntentRecognizer
  ├─ Build system prompt (including all available Skill descriptions)
  ├─ Call LLM for intent analysis
  ├─ Parse JSON response
  └─ Determine task type
       ├─ Single intent → SkillIntent
       ├─ Multi-step → TaskPlan
       └─ Invalid intent → Fallback to normal AI
  ↓
【3a. Skill Execution Layer】(If valid intent exists)
  ├─ Single Intent Path:
  │   ├─ SkillManager.executeSkillByIntent()
  │   ├─ Execute specific Skill (e.g., get_player_hand_item)
  │   └─ Return SkillResult { message, data }
  │
  └─ Multi-Step Path:
      ├─ TaskExecutor.executeTask()
      ├─ Execute multiple Skills in dependency order
      ├─ Data auto-flow ({step_1.xxx} → step_2)
      └─ Return comprehensive result
  ↓
【3b. Secondary Analysis Layer】LLMAnalysisService ✨ Knowledge Enhancement
  ├─ Build analysis prompt:
  │   ├─ [History] (Last N rounds of conversation)
  │   ├─ [Execution Results] (Data returned by Skills)
  │   └─ [Knowledge Context] ← New!
  │        └─ Retrieve relevant knowledge snippets
  │           (server rules, item descriptions, etc.)
  ├─ Call LLM for comprehensive analysis
  ├─ Generate natural language response
  └─ Return SkillResult
  ↓
【4. Response Layer】
  ├─ Save conversation to history
  ├─ Display to player
  └─ Complete
```

### Key Features

1. **Intelligent Intent Recognition**: LLM understands user's true intent, automatically selects Skills
2. **Multi-Step Orchestration**: Complex tasks decomposed into ordered steps, data auto-flows
3. **Knowledge Enhancement**: Inject relevant knowledge during secondary analysis, improve accuracy
4. **Failure Fallback**: Auto-convert to normal AI dialogue when intent recognition fails or Skill execution errors

### Example Flow

```
User: "What am I holding? How much can this sell for?"
  ↓
【Intent Recognition】→ Multi-step task
  ├─ step_1: bukkit_api.get_player_hand_item
  └─ step_2: market_query.query_price (depends on step_1)
  ↓
【Execute Skills】
  ├─ step_1 → "Diamond Sword"
  └─ step_2 → Price $500
  ↓
【Secondary Analysis + Knowledge Enhancement】
  Retrieved knowledge:
  - Server rule: Rare weapon price cap $1000
  - Diamond sword is medium-value item
  
  LLM comprehensive analysis:
  "You're holding a diamond sword, market price $500.
   According to server economic policy, this price is reasonable,
   recommend selling it in the market."
  ↓
【Return Result】
```

---

## 📊 Mode 3: Plugin Command Mode (Agent Disabled)

Plugin command mode is an interface designed specifically for **third-party plugin integration**, with a completely different design philosophy from normal modes.

### Why doesn't plugin command mode enable Agent capabilities?

1. **Callback mechanism requires pure text output**
   ```java
   executeCallback(callbackCommand, fullResponse);
   //                          ^^^^^^^^^^^^
   //                          Must be String type!
   ```
   - Agent path may return structured data (Map, List, etc.)
   - Cannot guarantee final result is serializable pure text
   - Callback commands need clear text content as parameters

2. **Different responsibility positioning**
   - ChatListener/KilacraftCommand: "Help me do something" (task execution)
   - Plugin command: "Give me a piece of AI-generated text" (content creation)

3. **Performance considerations**
   - Plugin commands may be called frequently (e.g., MythicMobs placeholders)
   - Avoid unnecessary intent recognition overhead (~2-5 seconds)
   - Direct normal AI call is faster (~1-3 seconds)

4. **Semantic clarity**
   - `/kilacraft plugins default Hello UUID` = "Answer 'Hello' with default personality"
   - Not "Help me execute some task"

### Complete Call Chain

```
Console: "/kilacraft plugins default Hello UUID callback_cmd"
  ↓
【1. Entry Layer】KilacraftCommand.handlePluginsCommand()
  ├─ Validate parameters (personality, UUID, callback command)
  ├─ Check world restrictions and cooldown
  ├─ Get isolated history records (UUID_personality)
  └─ Create PluginCommandResponseHandler
  ↓
【2. Personality Configuration】
  ├─ Load personality prompt from personalities.yml
  ├─ Replace {player} placeholder
  └─ Build complete system prompt
  ↓
【3. Normal AI Dialogue】LLMProvider.processRequestWithCustomSystemPrompt()
  ├─ 【Knowledge Retrieval】(Inside GenericLLMProvider)
  │   ├─ Retrieve relevant knowledge snippets
  │   └─ Inject into user message
  ├─ Build request:
  │   ├─ system: Personality prompt
  │   ├─ history: Isolated history records
  │   └─ user: User message + knowledge context
  ├─ Call LLM API (streaming response)
  └─ Return pure text response
  ↓
【4. Callback Layer】
  ├─ Save conversation to isolated history
  ├─ Cache latest response (for polling retrieval)
  ├─ Execute callback command:
  │   ├─ Replace {response} placeholder
  │   ├─ Execute command on main thread
  │   └─ Timeout protection (default 3 seconds)
  └─ Clean up cache
  ↓
【5. Complete】
```

### Key Features

1. **Pure Text Output**: Ensure callback commands can correctly receive and process
2. **Personality**: Use specified personality prompts, controllable style
3. **Knowledge Support**: Still can retrieve knowledge base, enhance answer quality
4. **Isolated History**: Independent history for each `UUID_personality` combination
5. **Callback Mechanism**: Automatically execute specified commands after AI completes

### Typical Application Scenarios

**Scenario 1: MythicMobs Placeholder Integration**
```yaml
# MythicMobs configuration
Skills:
  BossSkill:
    Skills:
      - message{msg=%kilacraft_ai_answer(How to defeat me?)%}
```

Expected output:
```
AI: "Attack my weak point on the head, use bows for ranged attacks,
     watch out for my fire skills!"
```

**Scenario 2: Other Plugin Code Integration**
```java
// Some plugin code
String aiAdvice = plugin.callAI(playerUUID, "How to defeat this boss?");
player.sendMessage(aiAdvice); // Display text advice
```

Expected output:
```
AI: "This boss has 3 phases, first phase...
     Recommend using fire resistance potions..."
```

**Scenario 3: Automated Scripts**
```bash
# Scheduled task
/kilacraft plugins daily_tips What events today? UUID save_to_file
```

Expected output:
```
AI: "Today's events:
     1. 14:00 PVP Tournament
     2. 20:00 Building Competition
     Welcome to participate!"
```

---

## 🔍 Core Differences Comparison Table

| Dimension | ChatListener / Command | Plugin Command Mode |
|-----------|----------------------|--------------------|
| **Trigger Method** | Player chat / console command | Console command (for plugin calls only) |
| **Agent Capability** | ✅ Enabled (intent recognition + skill execution) | ❌ Disabled (direct normal AI) |
| **Intent Recognition** | ✅ LLM automatic recognition | ❌ Skipped |
| **Skill Execution** | ✅ Supports single/multi-step | ❌ Not supported |
| **Knowledge Retrieval** | ✅ Injected during secondary analysis | ✅ Injected during normal dialogue |
| **Personality System** | ❌ Not supported (only default system prompt) | ✅ Required (can specify any personality) |
| **Output Form** | Diverse (skill results + AI summary) | Pure text (must be serializable) |
| **Callback Mechanism** | ❌ Not needed | ✅ Required (pass to caller) |
| **History Records** | Isolated by player UUID | Isolated by `UUID_personality` combination |
| **Cooldown** | General cooldown_seconds | Dedicated plugins_cooldown_seconds |
| **Response Speed** | ~3-8 seconds (includes intent + skills) | ~1-3 seconds (direct AI) |
| **Applicable Scenarios** | Player interaction, task execution | Content generation, plugin integration |

---

## 💡 Design Philosophy Summary

### ChatListener / KilacraftCommand Mode

> "Let players interact with the server in the most natural way, AI understands intent and executes tasks"

- Target end users (players)
- Emphasize intelligence and automation
- Support complex task orchestration
- Flexible and diverse output

### Plugin Command Mode

> "Provide stable and reliable AI text generation interface for third-party plugins"

- Target developers
- Emphasize stability and predictability
- Output must be pure text
- Support callback mechanism

### Why design this way?

1. **Responsibility Separation**: Two modes serve different target groups
2. **Performance Optimization**: Plugin integration doesn't need intent recognition overhead
3. **Reliability**: Pure text output ensures callback mechanism stability
4. **Flexibility**: Keep both modes to meet different needs

---

## 🎯 How to Choose Which Mode to Use?

### Use ChatListener / KilacraftCommand if:

- ✅ Players need intelligent interaction with AI
- ✅ Need to execute server operations (query, purchase, management, etc.)
- ✅ Want AI to understand complex multi-step tasks
- ✅ Output can be diverse (data + suggestions)

### Use Plugin Command Mode if:

- ✅ You are a plugin developer needing to call AI in code
- ✅ Only need AI-generated text content
- ✅ Need to pass AI replies to other systems
- ✅ Need personality-based reply styles
- ✅ Need callback mechanism for automated workflows

---

> **Last Updated**: 2026-04-07  
> **Applicable Plugin Version**: Kilacraft-AI 1.4.2+  
> **Related Documents**: 
> - [Server Owner Guide](./Server%20Owner%20Guide)
> - [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide)
