# Kilacraft-AI System Architecture Details

> **Last Updated**: 2026-05-06  
> **Description**: This document provides detailed explanation of Kilacraft-AI's core architecture design, working principles of three interaction modes, call chains, and design philosophy

---

## 📋 Table of Contents

- [Overview of Three Interaction Modes](#overview-of-three-interaction-modes)
- [Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)](#mode-1--2-chatlistener--kilacraftcommand-agent-enabled)
- [Mode 3: Plugin Command Mode (Agent Disabled)](#mode-3-plugin-command-mode-agent-disabled)
- [Core Differences Comparison Table](#core-differences-comparison-table)
- [Design Philosophy Summary](#design-philosophy-summary)
- [How to Choose Which Mode to Use?](#how-to-choose-which-mode-to-use)

---

## 🔄 Overview of Three Interaction Modes

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenarios |
|------|---------------|------------------|------------------------|------------------|-------------------|
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
  │   ├─ Execute specific Skill (e.g., get_player_hand_item, command.execute_command)
  │   └─ Return SkillResult { message, data }
  │
  └─ Multi-step path:
      ├─ TaskExecutor.executeTask()
      ├─ Execute multiple Skills in dependency order
      ├─ Data auto-flow ({step_1.xxx} → step_2)
      │  └─ Supports array index access: {step_1.warps[0].warp_name}
      └─ Return comprehensive result
  ↓
【3b. Secondary Analysis Layer】LLMOutputCoordinator ✨ Intermediate Coordination Layer
  ├─ LLMAnalysisService.analyzeResultWithHandler() analyzes execution results
  │   ├─ Build analysis prompt:
  │   │   ├─ [History] (Last N rounds of conversation)
  │   │   ├─ [Execution Results] (Structured data returned by Skills)
  │   │   └─ [Knowledge Context]
  │   │        └─ Retrieve relevant knowledge snippets
  │   │           (server rules, item descriptions, etc.)
  │   ├─ Call LLM for comprehensive analysis
  │   └─ Generate natural language response
  │
  └─ LLMOutputCoordinator unified output scheduling ✨ New!
      ├─ Automatically determine stream/non-stream output based on configuration
      ├─ Stream mode:
      │   ├─ Start AIResponsePipeline.startStream()
      │   ├─ Receive LLM chunks in real-time → updateStream()
      │   └─ Complete output → completeStream()
      └─ Non-stream mode:
          └─ Directly call AIResponsePipeline.send()
  ↓
【4. Response Layer】AIResponsePipeline ✨ Unified Output Pipeline
  ├─ Select output carrier based on scenario (CHAT/ACTION_BAR/BOSS_BAR/TITLE/SIDEBAR)
  ├─ Scenario configuration: NORMAL_CHAT / SKILL_RESULT / TASK_RESULT / AFK_CALLBACK / ERROR
  ├─ Public broadcast: Unified CHAT carrier + AI prefix
  ├─ Thinking message: Uses dynamically configured thinking_channel
  ├─ Stream output (optional, all scenarios supported):
  │   ├─ StreamOutputManager manages window period state (state machine: IDLE → GENERATING → COMPLETED)
  │   ├─ Request initiates → Display "✍️ AI is generating..." placeholder
  │   ├─ Receive SSE chunks → Real-time update ACTION_BAR/BOSS_BAR/SIDEBAR
  │   └─ Stream complete → Decide whether to keep final result based on configuration
  └─ MessageDispatcher intelligently routes to different carriers (encapsulated method, external calls prohibited)
```

### Key Features

1. **Intelligent Intent Recognition**: LLM understands user's true intent, automatically selects Skills
2. **Multi-Step Orchestration**: Complex tasks decomposed into ordered steps, data auto-flows
3. **Knowledge Enhancement**: Inject relevant knowledge during secondary analysis, improve accuracy
4. **Failure Fallback**: Auto-convert to normal AI dialogue when intent recognition fails or Skill execution errors
5. **Command Execution Fallback**: CommandSkill supports execution-type commands (e.g., /back, /spawn), covering Skill gaps
6. **LLM Secondary Analysis Coordination Layer**: LLMOutputCoordinator unified scheduling of analysis + output, supports streaming
7. **AI Response Unified Output Pipeline**: Supports 5 carriers, 5 scenario configurations, MessageDispatcher encapsulates output logic
8. **Stream Output All-Scenario Coverage**: Normal chat/skill results/task results/AFK callbacks all support streaming, state machine prevents race conditions
9. **Thinking Message Dynamic Configuration**: `output.thinking_channel` independently configured, not linked with scenario configuration

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
  Retrieved knowledge from base:
  - Server rule: Rare weapon price limit $1000
  - Diamond sword is mid-tier value
  
  LLM comprehensive analysis:
  "You are holding a diamond sword, market price $500.

---

## 📦 Mode 3: Plugin Command Mode (Agent Disabled)

This mode is designed for third-party plugin integration, where AI is called via callback command.

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
【3. Knowledge Base Retrieval】
  ├─ Extract keywords from user input
  ├─ Query relevant knowledge chunks
  └─ Build knowledge context
  ↓
【4. LLM Call】
  ├─ Knowledge enhancement prompt injection
  ├─ Call LLM provider
  ├─ Receive complete response
  └─ Parse into callback command string
  ↓
【5. Callback Command Execution】
  ├─ Check if callback_cmd parameter exists
  ├─ Parse callback arguments
  ├─ Execute callback command (e.g., /myplugin callback result_data)
  └─ No LLM analysis layer (single call mode)
```

### Key Differences

| Feature | ChatListener/KilacraftCommand | Plugin Command |
|---------|-------------------------------|-----------------|
| **Agent Capability** | ✅ Enabled | ❌ Disabled |
| **Knowledge Retrieval** | Smart injection (context-aware) | Normal retrieval (keyword-based) |
| **Callback Mechanism** | ❌ Not needed | ✅ Required |
| **Response Mode** | Multi-step tasks supported | Single response only |
| **History Records** | Player UUID isolation | UUID_personality isolation |
| **Typical Scenarios** | Player interaction, task execution | Third-party plugin integration |
| **Prompt Complexity** | Dynamic Skill injection | Fixed personality prompt |

### Core Advantages

- ✅ **Isolation**: Independent history records per UUID, no cross-contamination
- ✅ **Control**: Plugin can specify exact callback command and arguments
- ✅ **Performance**: No intent recognition overhead, direct knowledge retrieval
- ✅ **Flexibility**: Support multiple personalities with different styles

---

## 📊 Core Differences Comparison Table

| Feature | ChatListener/KilacraftCommand | Plugin Command |
|---------|-------------------------------|-----------------|
| **Agent Capability** | ✅ Enabled | ❌ Disabled |
| **Knowledge Retrieval** | Smart injection (context-aware) | Normal retrieval (keyword-based) |
| **Prompt Complexity** | Dynamic Skill injection | Fixed personality prompt |
| **Response Mode** | Supports multi-step tasks | Single response only |
| **History Records** | Player UUID isolation | UUID_personality isolation |
| **Callback Mechanism** | ❌ Not needed | ✅ Must provide callback_cmd |
| **Typical Scenarios** | Player interaction, task execution | Third-party plugin integration |

---

## 🎨 Design Philosophy Summary

### 1. Mode Specialization
- **Mode 1 & 2**: Optimized for player active interaction, supporting complex multi-step tasks
- **Mode 3**: Designed for third-party plugin integration, simple and reliable callback mechanism

### 2. Callback Command Standardization
**Semantic Clarity**: `/kilacraft plugins default Hello UUID callback_cmd`
- `default`: Use server's default personality
- `Hello`: Optional greeting message
- `UUID`: Unique identifier for history isolation
- `callback_cmd`: Command to execute with AI response as arguments

**Callback Execution**:
```
/myplugin callback {
  "from": "kilacraft",
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "result": "AI's complete response content"
}
```

### 3. Isolation Mechanism
**UUID_personality Key**:
- Format: `{UUID}_{personality_name}`
- Example: `550e8400-e29b-41d4-a716-446655440000_default`
- Purpose: Prevent history pollution between different callback commands

---

## 🎯 How to Choose Which Mode to Use?

### When to Use Mode 1 & 2 (ChatListener / KilacraftCommand)

✅ **Scenarios**:
- Player active interaction (e.g., "@ai", "/kilacraft")
- Task execution requiring multi-step chains
- Querying player/world/server information

✅ **Advantages**:
- Smart intent recognition
- Automatic task decomposition
- Context-aware responses
- History continuity

### When to Use Mode 3 (Plugin Command)

✅ **Scenarios**:
- Third-party plugin integration
- Need precise callback command
- Want history isolation
- Single-response requirement

✅ **Advantages**:
- Simple and reliable
- Predictable execution flow
- History isolation
- No complex parsing overhead

---

## 🆕 v2.0.0 New Subsystem Architecture

### 1. Database Persistence Layer

```
DatabaseManager (HikariCP connection pool)
  ├── H2Provider (embedded, zero-config)
  ├── MySQLProvider (multi-server sharing)
  ├── SchemaManager (auto DDL migration)
  ├── ConversationPersistenceService (30s batch flush)
  ├── DataCleanupService (configurable retention)
  └── DAOs (PlayerProfileDAO, SocialRelationDAO, SkillAuditDAO, etc.)

/database.yml → hot-reload via /kilacraft reload
```

### 2. Player Profile System

```
LoginEvent / ChatEvent / GoalEvent
  ↓
EventCollector → ServerEvent (type + data)
  ↓
OfflineEventAggregator (compresses offline events)
  ↓
ProfileAnalyzer → PlayerProfileDAO → DB
  ↓
AI Context Injection: "This player likes building, often trades..."
```

### 3. Social Relationship Graph

```
recordInteraction(playerA, playerB, weight)
  ↓
SocialGraph (ConcurrentHashMap + volatile lastDecayDate)
  ├── incrementInteraction() (real-time, memory-only)
  ├── performDailyDecay() (daily, direct SQL on async timer)
  └── SocialRelationExtractor
       ├── extractNewRelations() (periodic, direct SQL)
       └── SocialRelationDAO → DB

Two-layer mutual exclusion:
  Layer 1: TaskScheduler CAS (within server, prevent duplicate submission)
  Layer 2: SELECT FOR UPDATE row lock (cross-server, SocialGraph managed)
```

### 4. Server Event Collection System

```
Bukkit Events (PlayerDeath/PlayerJoin/AsyncChat/etc.)
  ↓
EventCollector.onEvent()
  ├── Type classification (ServerEventType enum)
  ├── Data extraction (relevant fields)
  └── Async write to DB (offline events aggregated on next login)
```

### 5. Unified Task Scheduler

```
TaskScheduler (CAS mutual exclusion protection)
  ├── Conversation flush (30s)
  ├── Data cleanup (configurable)
  ├── Social relation decay (daily)
  ├── Social relation extraction (periodic)
  └── Profile analysis trigger (on login/logout)

/kilacraft tasks → View all managed task statuses
```

### 6. AI Login Greeting System

```
PlayerJoinEvent → LoginGreetingHandler
  ├── First login → Welcome new player
  ├── Returning login → Three-category data aggregation
  │     ├── Category 1: Player's own offline events (death/advancement/level-up/market)
  │     ├── Category 2: Friend dynamics during offline (JOIN player_profile for names)
  │     └── Category 3: Summary stats (total playtime/login count/last session highlights)
  └── Cooldown check (greeting.yml)
       ↓
  GreetingPromptBuilder → LLM → AIResponsePipeline
```

### 7. Embedding Semantic Retrieval

```
Knowledge Base documents
  ↓
EmbeddingService (batch API call)
  ├── Vector embedding generation (via LLM embedding API)
  ├── EmbeddingCache (JSON file persistent)
  └── Query-time: similarity search + TF-IDF hybrid ranking
       ↓
  Top-K relevant chunks → injected into LLM context
```

> - [SPI Integration Guide](./Skill%20SPI%20Integration%20Guide)
> - [Changelog](./Changelog)
