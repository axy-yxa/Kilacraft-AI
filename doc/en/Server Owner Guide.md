# Kilacraft-AI - Lightweight AI Agent Built for Minecraft Servers

> **🚀 v1.4.0 Officially Released** | Zero Dependencies · Low Memory · High Performance · Easy to Extend · Fully Open Source  
> Enable every Minecraft server to have an intelligent AI assistant

---

## 🌐 Project Links

- **GitHub**: https://github.com/Zm-Mmm/Kilacraft-AI
- **Gitee**: https://gitee.com/zm_mmm/kilacraft-ai
- **License**: [MIT License](../LICENSE)

> 💡 **Welcome to Star ⭐, Fork 🔀 and submit Issues/Pull Requests!**

---

## 🎯 Why Choose Kilacraft-AI?

### ❌ Pain Points of Traditional AI Solutions

- **Complex Configuration**: Requires deploying vector databases, Redis, middleware services
- **High Resource Usage**: Often several GB of memory, small servers can't run it
- **High Maintenance Cost**: Requires dedicated technical personnel for operations
- **Bloated Features**: 90% of features are unused, but you pay for them

### ✅ Advantages of Kilacraft-AI

| Feature | Description |
|---------|-------------|
| **Zero Middleware Dependencies** | Just one JAR file, no database, cache or other extra services needed |
| **Extremely Low Memory Usage** | Small servers only need 8-12 MB, large servers about 30-50 MB |
| **Out of the Box** | Complete configuration in 5 minutes, ready to use immediately |
| **Performance Optimized** | HTTP connection pool reuse, asynchronous non-blocking execution, streaming response support |
| **Highly Customizable** | Personality system, knowledge base, language configuration all customizable |
| **Ecosystem Friendly** | Open SPI interface, third-party plugins can seamlessly integrate |

---

## 🌟 Core Feature Showcase

### 1️⃣ Intelligent Dialogue System

#### Three Interaction Modes for All Scenarios

**Command Mode** (Suitable for occasional inquiries)
```
Player: /kila How do I get diamonds?
AI: [Kilacraft-AI] You can obtain diamonds through the following methods:
    1. Mine at Y=-58 to Y=-53 layers
    2. Explore caves and ravines
    3. Trade with other players
```

**Continuous Chat Mode** (Suitable for in-depth conversations)
```
Player: /kilacraft chat
→ Entered continuous chat mode!

Player: I want to build a farm
AI: Great idea! What crops would you like to grow? Wheat, carrots, or beetroots?

Player: Wheat, what tools do I need?
AI: To plant wheat you need:
    1. Hoe (to turn dirt into farmland)
    2. Water bucket (to irrigate nearby land)
    3. Seeds (obtained by breaking grass)
```

**Keyword Trigger** (Suitable for public channels)
```
Player: @ai How do I do this?
AI: [Kilacraft-AI] What specifically are you referring to? I can help you answer questions about game mechanics, crafting recipes, etc.
```

---

### 2️⃣ Knowledge Base Enhancement (RAG Retrieval)

Let AI understand your server rules, gameplay, FAQ!

**How to Use:**
1. Create `.md` or `.txt` files in `plugins/Kilacraft-AI/knowledge/`
2. Write server-related knowledge (rules, tutorials, FAQ, etc.)
3. Execute `/kilacraft knowledge reload` to reload

**Example File (`server_rules.md`):**
```
# Server Rules

## Basic Rules
1. No cheating or using hacks
2. Be friendly, no insulting others
3. No destroying other players' builds

## Economy System
4. No using money duplication exploits
5. Trading must follow market principles

# Frequently Asked Questions

## How to claim land?
Use the /claim command to define your territory. Requires at least 10 gold coins.

## How to earn money?
You can earn money by mining, fishing, or selling items in player shops.
```

**Intelligent Retrieval Effect:**
```
Player: How can I claim land?
AI: [Kilacraft-AI] You can use the /claim command to define your territory. Requires at least 10 gold coins.
    (Based on "Server Rules" document in knowledge base)
```

**Technical Advantages:**
- 📑 Markdown headings automatically segmented, precisely locate relevant content
- 🔍 Comprehensive scoring algorithm: complete question matching + BM25 keyword scoring + position weighting + exact match bonus
- 💾 Cache optimization: secondary retrieval speed improved by ~70%
- 🇨🇳 Chinese word segmentation optimized: HanLP TF-IDF + intelligent stop word filtering

---

### 3️⃣ LLM Intelligent Intent Recognition + Multi-Step Task Orchestration

#### 🧠 What is Intelligent Intent Recognition?

Kilacraft-AI doesn't simply "answer questions", but **understands your true intent** and automatically calls corresponding functions!

This is the **brain** of the entire Skill system. Whether calling economy systems, Bukkit API, or third-party plugin Skills, all go through this intelligent engine.

```yaml
# Agent Workflow
User Input → LLM Intent Recognition → Determine Task Type → Execute Skill/Task → Analyze Results → Generate Response
```

**Core Advantages:**
- 🎯 **Natural Language Understanding**: Players don't need to remember commands, just speak naturally
- 🤖 **Automatic Routing**: AI automatically determines which Skill to call
- 🔗 **Multi-Step Orchestration**: Complex tasks automatically decomposed into multiple ordered steps
- 💡 **Intelligent Summary**: LLM comprehensively analyzes all results, generates friendly responses

---

#### 📊 Two Task Modes

**Mode 1: Single-Intent Fast Path** (Simple queries execute directly)

Suitable for simple information queries, zero additional overhead:

```
Player: How much are diamonds?
    ↓
AI Recognizes: Single intent → market_query.query_price
    ↓
Execute: Query price API
    ↓
Response: Diamond current price: $100.00
```

**Other Single-Intent Examples:**
```
Player: What am I holding?
→ AI Recognizes: bukkit_api.get_player_hand_item
→ Response: You are holding in main hand: Diamond Sword x1

Player: What time is it now?
→ AI Recognizes: bukkit_api.get_world_time
→ Response: World time: 06:00 (Morning)

Player: Do I have any mail?
→ AI Recognizes: market_query.query_mailbox
→ Response: Your mailbox has 2 pending mails...
```

---

**Mode 2: Multi-Step Task Orchestration** (Complex tasks automatically decomposed)

When user requests require multiple Skills to cooperate, AI automatically plans execution order:

```
Player: Check diamond price for me, if not over 100 then see if my balance is enough to buy 10
    ↓
AI Recognizes: Multi-step task
├─ Step 1: market_query.query_price (Query diamond price)
├─ Step 2: market_query.query_balance (Query player balance)
└─ Step 3: LLM Comprehensive Analysis (Compare price and balance, give suggestions)
    ↓
Execution Process:
✅ Step 1 Complete: Diamond price $80.00 each
✅ Step 2 Complete: Your balance $12,580
💡 Step 3 Analysis: Buying 10 diamonds requires $800, your balance is sufficient, you can purchase!
    ↓
Final Response:
[Kilacraft-AI] 
    Queried for you:
    • Diamond price: $80.00 each
    • Your balance: $12,580
    
    💡 Analysis: Buying 10 diamonds requires $800, your balance is sufficient,完全可以购买!
    Would you like me to place the order for you?
```

**More Complex Multi-Step Example:**

```
Player: I want to build an automated farm, first check if I have hoe and water bucket in my inventory, then tell me if it's daytime
    ↓
AI Recognizes: Multi-step task
├─ Step 1: bukkit_api.get_player_hand_item (Check main hand item)
├─ Step 2: bukkit_api.get_player_offhand_item (Check offhand item)
├─ Step 3: bukkit_api.get_world_time (Query world time)
└─ Step 4: LLM Comprehensive Analysis (Give suggestions based on materials and time)
    ↓
LLM Comprehensive Analysis Response:
[Kilacraft-AI]
    I see you already have some basic materials:
    • Main hand: Diamond Hoe x1 ✓
    • Offhand: Water Bucket x1 ✓
    
    Current time is 14:00 (Afternoon), plenty of light, suitable for building!
    
    💡 Suggestions:
    1. First find a flat piece of land
    2. Use hoe to turn dirt into farmland
    3. Use water bucket to irrigate surrounding land
    4. Plant seeds, wait for growth
    
    Would you like me to tell you specific redstone automation plans?
```

---

#### 🎯 LLM Result Analysis and Summary

After all steps complete, LLM performs **secondary analysis**, synthesizing all results to generate friendly responses:

```
# config.yml Configuration
agent:
  prompts:
    # System prompt: Define LLM's role in analysis phase
    system_prompt: "You are a professional Minecraft game assistant..."
    
    # Analysis prompt: Guide LLM how to analyze execution results
    analysis_prompt: |
      {results}  # ← All step execution results
      Please provide comprehensive analysis and suggestions based on the above conversation history, current input, and execution results. Reply to players in concise and friendly language.
```

**Why Need Secondary Analysis?**

Raw skill execution results are usually **structured data**, not suitable for direct display to players:

```
Raw Results:
• step_1: {"item_name": "diamond", "price": 80.0, "stock": 15}
• step_2: {"balance": 12580.0}

LLM Analyzed Response:
"Queried for you:
 • Diamond price: $80.00 each (Stock 15)
 • Your balance: $12,580
 
 💡 Analysis: Buying 10 diamonds requires $800, your balance is sufficient,完全可以购买!
 Would you like me to place the order for you?"
```

**Advantages of LLM Analysis:**
- 💬 **Natural Language Conversion**: Convert structured data to friendly dialogue
- 🎭 **Personality Expression**: Adjust tone according to configured personality style
- 📝 **Context Correlation**: Combine conversation history for more coherent responses
- 💡 **Intelligent Suggestions**: Provide valuable suggestions based on data

---

#### ⚙️ Flexible and Controllable Configuration

```
agent:
  enabled: true                    # Master switch (highest priority)
  enable_chat_listener: true       # Whether keyword trigger enables Agent
  enable_command: true             # Whether /kilacraft command enables Agent
  
  # Historical conversation context configuration
  intent_history_count: 5          # Use 5 rounds of history for intent recognition
  analysis_history_count: 2        # Use 2 rounds of history for result analysis
  
  prompts:
    system_prompt: "You are a professional Minecraft game assistant..."
    analysis_prompt: "{results}\nPlease provide comprehensive analysis and suggestions based on the above conversation history, current input, and execution results."
```

**Configuration Explanation:**
- `intent_history_count`: Helps LLM understand referential expressions like "check that item again"
- `analysis_history_count`: Lets LLM reference conversation context when analyzing results, making responses more natural
- Can disable Agent capability at specific entry points by turning off `enable_chat_listener` or `enable_command`

---

#### 🛡️ Failure Fallback Mechanism

If intent recognition fails or skill execution errors, system automatically falls back to normal AI dialogue:

```
Player: Help me check xxx (unrecognizable intent)
    ↓
Intent Recognition Failed
    ↓
Fallback to Normal AI Dialogue
    ↓
AI: Sorry, I don't quite understand what you mean. You can ask me:
    • "How much are diamonds?"
    • "How much health do I have?"
    • "What products are on the market?"
```

**Protection Measures:**
- ✅ **Error Isolation**: Third-party Skill exceptions don't affect core processes
- ✅ **Friendly Prompts**: Return meaningful error messages, not technical errors
- ✅ **Detailed Logs**: Console records complete error information for troubleshooting

---

#### 📈 Technical Advantages Summary

| Feature | Description |
|---------|-------------|
| 🧠 **LLM Intelligent Planning** | Automatically determine whether single-intent or multi-step task |
| 🔗 **Automatic Data Flow** | Previous step results injected into subsequent steps' entities |
| 🛡️ **Failure Fallback** | When intent recognition or skill execution fails, automatically convert to normal AI dialogue |
| ⚡ **Asynchronous Parallel Execution** | Non-dependent steps can execute in parallel, improving response speed |
| 📊 **Topological Sorting Algorithm** | DFS-based circular dependency detection, ensuring correct execution order |
| 💡 **Intelligent Result Analysis** | LLM comprehensively analyzes all step results, generates friendly responses |
| 🎭 **Personality Expression** | Adjust response tone according to configured personality style |
| 🌐 **Universality** | Applicable to all Skills (economy system, Bukkit API, third-party plugins) |

---

### 3.5️⃣ AI Call Chain and Mode Comparison (Architecture Details)

This section details the complete call chains of Kilacraft-AI's three interaction modes, applicable scenarios, and design differences, helping you deeply understand the system architecture.

#### 🔄 Overview of Three Interaction Modes

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenarios |
|------|---------------|------------------|-------------------------|-------------------|------------------|
| **ChatListener** | `@ai` keyword / continuous chat | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Player active interaction |
| **KilacraftCommand** | `/kilacraft <message>` | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Server owner/admin queries |
| **Plugin Command** | `/kilacraft plugins ...` | ❌ Disabled | ✅ Normal retrieval | ✅ Required | Third-party plugin integration |

---

#### 📊 Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)

These two modes share the same Agent processing flow, differing only in trigger method.

**Complete Call Chain:**

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

**Key Features:**

1. **Intelligent Intent Recognition**: LLM understands user's true intent, automatically selects Skills
2. **Multi-Step Orchestration**: Complex tasks decomposed into ordered steps, data auto-flows
3. **Knowledge Enhancement**: Inject relevant knowledge during secondary analysis, improve accuracy
4. **Failure Fallback**: Auto-convert to normal AI dialogue when intent recognition fails or Skill execution errors

**Example Flow:**

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

#### 📊 Mode 3: Plugin Command Mode (Agent Disabled)

Plugin command mode is an interface designed specifically for **third-party plugin integration**, with a completely different design philosophy from normal modes.

**Why doesn't plugin command mode enable Agent capabilities?**

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

**Complete Call Chain:**

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

**Key Features:**

1. **Pure Text Output**: Ensure callback commands can correctly receive and process
2. **Personality**: Use specified personality prompts, controllable style
3. **Knowledge Support**: Still can retrieve knowledge base, enhance answer quality
4. **Isolated History**: Independent history for each `UUID_personality` combination
5. **Callback Mechanism**: Automatically execute specified commands after AI completes

**Typical Application Scenarios:**

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

#### 🔍 Core Differences Comparison Table

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

#### 💡 Design Philosophy Summary

**ChatListener / KilacraftCommand Mode:**
> "Let players interact with the server in the most natural way, AI understands intent and executes tasks"

-面向终端玩家
- Emphasize intelligence and automation
- Support complex task orchestration
- Flexible and diverse output

**Plugin Command Mode:**
> "Provide stable and reliable AI text generation interface for third-party plugins"

-面向开发者
- Emphasize stability and predictability
- Output must be pure text
- Support callback mechanism

**Why design this way?**

1. **Responsibility Separation**: Two modes serve different target groups
2. **Performance Optimization**: Plugin integration doesn't need intent recognition overhead
3. **Reliability**: Pure text output ensures callback mechanism stability
4. **Flexibility**: Keep both modes to meet different needs

---

#### 🎯 How to Choose Which Mode to Use?

**Use ChatListener / KilacraftCommand if:**
- ✅ Players need intelligent interaction with AI
- ✅ Need to execute server operations (query, purchase, management, etc.)
- ✅ Want AI to understand complex multi-step tasks
- ✅ Output can be diverse (data + suggestions)

**Use Plugin Command Mode if:**
- ✅ You are a plugin developer needing to call AI in code
- ✅ Only need AI-generated text content
- ✅ Need to pass AI replies to other systems
- ✅ Need personality-based reply styles
- ✅ Need callback mechanism for automated workflows

---

### 4️⃣ Plugin Command Mode + Personality System (Advanced Features)

Plugin command mode is a dedicated interface designed by Kilacraft-AI for **third-party plugin integration**, allowing other plugins to call AI from console with specified personalities.

**Core Design Philosophy:**
> Plugin command mode is not a command for server owners to execute manually, but an **API interface for third-party plugins to call in code or configuration**.

#### 📋 Command Format

```
/kilacraft plugins <personality_name> <message_content> <player_uuid> [callback_commands...]
```

**Parameter Explanation:**
- `<personality_name>`: Personality defined in `personalities.yml`
- `<message_content>`: Message to send to AI
- `<player_uuid>`: Target player's UUID
- `[callback_commands...]`: **Optional**, commands automatically executed after AI completes (supports `{response}` placeholder)

**Important Features:**
- 🔒 **Console Only**
- 🌐 **Independent History Records**: Each `UUID_personality` combination is independent
- ⏱️ **Dedicated Cooldown**: Uses `plugins_cooldown_seconds` configuration
- 💬 **Callback Command Support**: Automatically execute after AI completes, `{response}` replaced with actual reply

Complete examples in Chapter 7.

---

### 4.5️⃣ AI Call Chain and Mode Comparison (Architecture Details)

This section details the complete call chains of Kilacraft-AI's three interaction modes, applicable scenarios, and design differences.

#### 🔄 Overview of Three Interaction Modes

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenarios |
|------|---------------|------------------|-------------------------|-------------------|------------------|
| **ChatListener** | `@ai` keyword / continuous chat | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Player active interaction |
| **KilacraftCommand** | `/kilacraft <message>` | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Server owner/admin queries |
| **Plugin Command** | `/kilacraft plugins ...` | ❌ Disabled | ✅ Normal retrieval | ✅ Required | Third-party plugin integration |

---

#### 📊 Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)

These two modes share the same Agent processing flow, differing only in trigger method.

**Complete Call Chain:**

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

**Key Features:**

1. **Intelligent Intent Recognition**: LLM understands user's true intent, automatically selects Skills
2. **Multi-Step Orchestration**: Complex tasks decomposed into ordered steps, data auto-flows
3. **Knowledge Enhancement**: Inject relevant knowledge during secondary analysis, improve accuracy
4. **Failure Fallback**: Auto-convert to normal AI dialogue when intent recognition fails or Skill execution errors

**Example Flow:**

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

#### 📊 Mode 3: Plugin Command Mode (Agent Disabled)

Plugin command mode is an interface designed specifically for **third-party plugin integration**, with a completely different design philosophy from normal modes.

**Why doesn't plugin command mode enable Agent capabilities?**

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

**Complete Call Chain:**

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

**Key Features:**

1. **Pure Text Output**: Ensure callback commands can correctly receive and process
2. **Personality**: Use specified personality prompts, controllable style
3. **Knowledge Support**: Still can retrieve knowledge base, enhance answer quality
4. **Isolated History**: Independent history for each `UUID_personality` combination
5. **Callback Mechanism**: Automatically execute specified commands after AI completes

**Typical Application Scenarios:**

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

#### 🔍 Core Differences Comparison Table

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

#### 💡 Design Philosophy Summary

**ChatListener / KilacraftCommand Mode:**
> "Let players interact with the server in the most natural way, AI understands intent and executes tasks"

-面向终端玩家
- Emphasize intelligence and automation
- Support complex task orchestration
- Flexible and diverse output

**Plugin Command Mode:**
> "Provide stable and reliable AI text generation interface for third-party plugins"

-面向开发者
- Emphasize stability and predictability
- Output must be pure text
- Support callback mechanism

**Why design this way?**

1. **Responsibility Separation**: Two modes serve different target groups
2. **Performance Optimization**: Plugin integration doesn't need intent recognition overhead
3. **Reliability**: Pure text output ensures callback mechanism stability
4. **Flexibility**: Keep both modes to meet different needs

---

#### 🎯 How to Choose Which Mode to Use?

**Use ChatListener / KilacraftCommand if:**
- ✅ Players need intelligent interaction with AI
- ✅ Need to execute server operations (query, purchase, management, etc.)
- ✅ Want AI to understand complex multi-step tasks
- ✅ Output can be diverse (data + suggestions)

**Use Plugin Command Mode if:**
- ✅ You are a plugin developer needing to call AI in code
- ✅ Only need AI-generated text content
- ✅ Need to pass AI replies to other systems
- ✅ Need personality-based reply styles
- ✅ Need callback mechanism for automated workflows

---

### 5️⃣ Economy System Integration (GlobalMarketPlus) (Extended Feature)

Make your market plugin "talk"! Query market information through natural language.

```
Player: How much are diamonds?
AI: [Kilacraft-AI] Diamond current price: $100.00 each

Player: How much balance do I have left?
AI: [Kilacraft-AI] Your balance: $12,580

Player: What products are on the market?
AI: [Kilacraft-AI] Current products for sale list:
    • Diamond x15 - $100.00 each
    • Iron Ingot x32 - $20.00 each
    • ...
```

**Special Features:**
- ✅ Natural language understanding: "buy 5 sticks" automatically recognizes item and quantity
- ✅ Multi-item joint query: "diamond:2,stick:1" query multiple at once
- ✅ Optimal price calculation: Smart combination from cheap to expensive
- ✅ Insufficient stock提示: Display all sale details

**Security Notes:**
- 📖 **Read-Only Operations**: MarketQuerySkill only queries information, won't consume items or money
- 🔒 **Permission Control**: Each action has independent permission nodes
- 🛡️ **Error Isolation**: Execution errors don't affect other features

---

### 6️⃣ Bukkit API Dynamic Invocation (50+ Built-in APIs) (Extended Feature)

No coding required, AI directly calls vanilla APIs to query player status, world info, server info!

```
Player: What am I holding?
AI: [Kilacraft-AI] You are holding in main hand: Diamond Sword x1

Player: How much health do I have left?
AI: [Kilacraft-AI] Health: 18.5/20.0

Player: What time is it now?
AI: [Kilacraft-AI] World time: 06:00 (Morning)

Player: How's the weather?
AI: [Kilacraft-AI] Current weather: Clear

Player: How many people are online on the server?
AI: [Kilacraft-AI] Online players: 15/100
```

**Supported API Categories:**
- 📦 **Player Inventory**: Main hand/offhand item queries
- ❤️ **Player Status**: Health, hunger, oxygen, experience, sleep, attack cooldown, on fire, frozen, AFK
- 📍 **Player Info**: Location coordinates, game mode, fly status, ping, client brand, vehicle, death point
- 🌍 **World Info**: Time, weather, world type, seed, spawn point, height limit, mob spawning rules, PVP settings
- 🖥️ **Server Info**: Online players, max players, version, MOTD, world list, TPS

**Fine-Grained Permission Control:**
```yaml
# Can control access permissions for each player/group through permission plugins (like LuckPerms)
kilacraft.api.player.inventory  # Inventory query
kilacraft.api.player.status     # Status query
kilacraft.api.player.info       # Basic info query
kilacraft.api.world.info        # World info query
kilacraft.api.server.info       # Server info query
```

**Security Notes:**
- 📖 **Read-Only Operations**: GenericBukkitAPISkill only calls getter methods, doesn't modify any data
- 🔒 **Permission Control**: Each API has independent permission nodes
- 🛡️ **Error Isolation**: Execution errors don't affect other features

---

### 7️⃣ Plugin Command Mode Complete Example (Advanced Feature)

Plugin command mode is a dedicated interface designed by Kilacraft-AI for **third-party plugin integration**, allowing other plugins to call AI from console with specified personalities, enabling:
- 🤖 **NPC Intelligent Dialogue**: Give MythicMobs NPCs different personalities, communicate naturally with players
- 🎮 **Custom Interactions**: Dynamically generate AI replies matching character settings based on your plugin logic
- 🔗 **Callback Command Integration**: Automatically execute specified commands after AI completes, seamlessly embed into third-party plugins

**Core Design Philosophy:**
> Plugin command mode is not a command for server owners to execute manually, but an **API interface for third-party plugins to call in code or configuration**.

#### 📋 Command Format

```
/kilacraft plugins <personality_name> <message_content> <player_uuid> [callback_commands...]
```

**Parameter Explanation:**
- `<personality_name>`: Personality defined in `personalities.yml` (e.g., `Fox`, `StrictTeacher`, `AdventurePartner`)
- `<message_content>`: Message to send to AI (can be static text or contain dynamic placeholders)
- `<player_uuid>`: Target player's UUID (used to replace `{player}` placeholder and save history)
- `[callback_commands...]`: **Optional**, commands automatically executed after AI completes (supports `{response}` placeholder)

**Important Features:**
- 🔒 **Console Only**: Players cannot use this command directly (prevent abuse)
- 🌐 **Independent History Records**: Each `UUID_personality` combination has independent history, no interference
- ⏱️ **Dedicated Cooldown**: Uses `plugins_cooldown_seconds` configuration (default 3 seconds)
- 🌍 **World Restriction Check**: If target player is online, checks if in banned worlds
- 💬 **Callback Command Support**: Automatically execute specified commands after AI completes, `{response}` replaced with actual reply

---

#### 💡 Core Application Scenarios: Third-Party Plugin Integration

Plugin command mode applies to **any third-party plugin needing AI capabilities**, including but not limited to:
- 🤖 **NPC Dialogue Systems** (like MythicMobs): Give NPCs intelligent dialogue capabilities
- 🎮 **Quest Systems**: Dynamically generate quest descriptions and rewards based on player behavior
- 📊 **Data Analysis**: Analyze player behavior and generate personalized suggestions
- 🔧 **Management Tools**: Automatically generate server reports, log summaries, etc.

##### Working Principle

```
Third-party plugin triggers event
    ↓
Execute console command: /kilacraft plugins <personality> <question> <playerUUID> [callback_commands]
    ↓
Kilacraft-AI generates reply with specified personality (async, 2-5 seconds)
    ↓
Automatically execute callback commands after AI completes (as console)
    ↓
Your plugin receives callback command, processes AI reply
    ↓
Cache immediately deleted (one-time consumption)
```

##### Complete Configuration Example

Suppose you want to develop a plugin that lets players ask about server rules through NPCs.

**Step 1: Define Personality (`personalities.yml`)**

```yaml
# Fox personality
Fox: |
  You are a clever fox NPC, currently dialoguing with player {player}.
  You speak playfully and cutely, like to end with "~", frequently use emojis.
  You know server rules inside out, will answer player questions in interesting ways.
```

**Step 2: Call Kilacraft-AI in Your Plugin**

Two ways to initiate requests:

**Method A: Direct call in code (recommended for Java plugins)**

```java
// When player interacts with NPC
@EventHandler
public void onNPCInteract(PlayerInteractEntityEvent event) {
    Player player = event.getPlayer();
    Entity npc = event.getRightClicked();
    
    // Check if it's a specific NPC
    if (npc.getCustomName() != null && npc.getCustomName().equals("Fox NPC")) {
        // Build callback command
        String callbackCommand = String.format(
            "myplugin handle_ai %s %s", 
            "{response}",  // Placeholder, will be replaced with actual reply
            player.getName()
        );
        
        // Call Kilacraft-AI plugin command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(
            "kilacraft plugins Fox What are the server rules %s %s",
            player.getUniqueId().toString(),
            callbackCommand
        ));
        
        player.sendMessage("§eFox is thinking...");
    }
}
```

**Method B: Call in configuration file (recommended for configuration-driven plugins like MythicMobs)**

```yaml
# MythicMobs skill configuration example
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

> 💡 **Tip**: Method B suits plugins that support executing commands in configuration (like MythicMobs, Skript, etc.), no Java coding required.

**Step 3: Implement Callback Command Handler**

```java
// Register command in your plugin main class
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("myplugin")) {
        return false;
    }
    
    if (args.length == 0) {
        return false;
    }
    
    String subCommand = args[0];
    
    // Handle AI reply callback
    if (subCommand.equals("handle_ai")) {
        // args format: ["handle_ai", "reply content...", "player name"]
        // Note: Reply content may contain spaces, need proper parsing
        
        // Last parameter is player name
        String playerName = args[args.length - 1];
        
        // All middle parameters are AI reply (merged)
        StringBuilder responseBuilder = new StringBuilder();
        for (int i = 1; i < args.length - 1; i++) {
            if (i > 1) responseBuilder.append(" ");
            responseBuilder.append(args[i]);
        }
        String aiResponse = responseBuilder.toString();
        
        // Get player and display message
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage("§e[NPC Fox] §f" + aiResponse);
        }
        
        return true;
    }
    
    return false;
}
```

**Step 4: Register Command in plugin.yml**

```yaml
commands:
  myplugin:
    description: MyPlugin main command
    usage: /<command>
```

##### Actual Effect

```
Player right-clicks "Fox NPC"
    ↓
Console executes: /kilacraft plugins Fox What are the server rules xxx-uuid myplugin handle_ai {response} playername
    ↓
AI generates reply (async, about 2-5 seconds)
    ↓
After AI completes, automatically executes: /myplugin handle_ai "Heehee~ Server rules are simple! ✨..." playername
    ↓
Your plugin receives command and displays:
[NPC Fox] Heehee~ Server rules are simple! ✨
    1. No cheating or hacking, fox will catch you! 🦊
    2. Be friendly, no cursing~
    ...
```

---

#### ⚙️ Configuration Explanation

**1. Define Personalities (`personalities.yml`)**

```yaml
# Common prompt (shared by all personalities)
common_prompt: "You are a Minecraft server NPC, currently dialoguing with player {player}."

# Fox personality
Fox: |
  You are a clever fox NPC, speaking playfully and cutely.
  Like to end with "~", frequently use emojis.
  Know server rules inside out, will answer player questions in interesting ways.

# Strict Teacher personality
StrictTeacher: |
  You are a strict Minecraft teacher, with high standards for students.
  Speak concisely and directly, but patiently answer questions.
  Focus on teaching game mechanics, redstone circuits and building techniques.

# Adventure Partner personality
AdventurePartner: |
  You are player {player}'s loyal adventure partner, cheerful and humorous.
  Like to share adventure stories, provide combat advice, always encourage players to explore bravely.
```

**2. Adjust Cooldown Time (`config.yml`)**

```yaml
settings:
  plugins_cooldown_seconds: 3  # Plugin command dedicated cooldown (default 3 seconds)
```

**3. Reload Personality Configuration**

```
/kilacraft personalities reload
```

---

#### 📊 Complete Workflow Example

```bash
# Scenario 1: With callback command (✅ Recommended)
/kilacraft plugins Fox What are the server rules 069a79f4-44e9-4726-a5be-fca90e38aaf5 myplugin handle_ai {response} playername

# After AI generates reply, automatically executes callback:
/myplugin handle_ai "Heehee~ Server rules are simple! ✨..." playername

# Cache deleted, cannot retrieve again

# Scenario 2: Without callback command (❌ Not recommended)
/kilacraft plugins Fox Hello 069a79f4-44e9-4726-a5be-fca90e38aaf5

# AI generates reply and keeps in cache
# ⚠️ Note: No standard method to retrieve reply this way, not recommended
# If you need custom retrieval methods, please refer to MythicMobs placeholder implementation and develop yourself
```

---

#### ⚠️ Precautions

1. **Player Must Exist**: Player corresponding to UUID must have records on server (online or offline)
2. **Personality Must Exist**: Used personality must be defined in `personalities.yml`
3. **Console Only**: Players attempting to execute will be blocked and warning logged
4. **Parameter Format**: UUID must be standard format (like `069a79f4-44e9-4726-a5be-fca90e38aaf5`)
5. **Callback Commands Recommended**: Strongly recommend using callback command method, this is the only officially supported integration method
6. **Placeholder Escaping**: If AI reply contains double quotes, automatically escaped as `\"`
7. **Personality Matching**: Ensure personality name exactly matches definition in `personalities.yml`
8. **History Record Isolation**: Same player's different personalities have independent history records, no interference
9. **Callback Command Parsing**: If your callback command parameters contain spaces, need proper parsing (refer to example code above)

---

## ⚠️ Security and Permission Management

### Built-in Skill Security

All Skills built into Kilacraft-AI follow **security first** principle:

| Skill Name | Function | Security |
|-----------|----------|----------|
| **MarketQuerySkill** | Query market info (balance, prices, product lists, etc.) | ✅ Read-only, won't consume items or money |
| **GenericBukkitAPISkill** | Query player status, world info, server info | ✅ Read-only, only calls getter methods |

**Why Secure?**
- 📖 **Read-Only Operations**: All built-in Skills only query information, don't modify any game data
- 🔒 **Permission Control**: Each API has independent permission nodes, can be finely controlled through permission plugins
- 🛡️ **Error Isolation**: Even if a Skill execution fails, won't affect other features
- 📝 **Transparent and Visible**: All Skill definitions are in YAML files under `skills/` directory, can be reviewed anytime

### Third-Party Skill Risk Warnings

If you install Skills developed by third-party plugins, please note:

**Security Risk Sources:**
- Third-party developers may implement **write operations** (like transfers, teleportation, giving items, etc.)
- May have **logic vulnerabilities** causing unintended consequences
- May **bypass permission checks** to execute sensitive operations

**Protection Measures:**
1. **Review Skill Sources**: Only install plugins from trusted developers
2. **Read Skill Descriptions**: Carefully read Skill function descriptions and return values
3. **Test Environment Verification**: First verify functionality is normal and safe on test server
4. **Minimize Permissions**: Limit who can use which Skills through permission plugins
5. **Monitor Logs**: Regularly check console logs, handle anomalies promptly

**Advice for Developers:**
If you're a plugin developer creating Skills, please follow these principles:
- ✅ Prioritize implementing **read-only query** functions
- ⚠️ If implementing **write operations**, must clearly标注 risks in documentation
- 🔒 Perform **strict permission checks** in `execute()` method
- 📝 Provide clear **usage instructions** and **precautions**
- 🛡️ Do proper **exception handling**, avoid crashes or data corruption

See [SPI Integration Guide](Skill-SPI-接入文档) Chapter 7 "Error Isolation and Exception Handling".

---

## ⚙️ Quick Start (5 Minutes to Get Started)

### Step 1: Install Plugin

1. Download `Kilacraft-AI-1.4.0.jar`
2. Put into server `plugins/` directory
3. Start server, wait for configuration files to generate

### Step 2: Configure API Key

Edit `plugins/Kilacraft-AI/config.yml`:

```
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "sk-your-api-key-here"  # ← Enter your API Key here
  model: "deepseek-chat"
  temperature: 0.7
  max_tokens: 1000
```

**Get API Key:**
- DeepSeek: https://platform.deepseek.com/
- Zhipu AI: https://open.bigmodel.cn/
- Moonshot: https://platform.moonshot.cn/

> 💡 **Tip**: Just modify `api_url` and `model` to switch between different vendors, no code changes needed!

### Step 3: Restart Server

```
/reload  # or restart server
```

### Step 4: Test Dialogue

```
/kila Hello
```

If you see AI reply, congratulations! Configuration successful! 🎉

---

## 📊 Performance and Resource Usage

### Memory Usage Comparison

| Solution | Memory Usage | Middleware Dependencies | Suitable Server Scale |
|----------|--------------|------------------------|----------------------|
| **Kilacraft-AI** | **8-50 MB** (dynamic) | **None** | **All scales** |
| LangChain + Vector DB | 2-5 GB | Redis, PostgreSQL, Elasticsearch | Large commercial servers |
| Self-built RAG System | 1-3 GB | MySQL, Redis | Medium-large servers |

> 💡 **Note**: Kilacraft-AI's memory usage dynamically changes based on knowledge base size and online player count. Small servers typically only need 8-12 MB, large servers about 30-50 MB. See detailed evaluation below.

### Conversation History Memory Evaluation

```
max_history: 10 (default)
Each round of conversation ≈ 200 characters ≈ 0.2 KB
Each message includes role + content fields, actually about 0.3-0.4 KB
100 active players × 20 messages × 0.4 KB = 800 KB
1000 active players × 20 messages × 0.4 KB = 8 MB
```

**Conclusion**: Even with thousands online, conversation history uses less than 10 MB!

**Optimization Suggestions:**
- ✅ Adjust `max_history` to control memory usage per player
- ✅ Player exit history remains in memory (cleared on restart)
- ✅ Use `/kilacraft clear` to manually clear history records

**Memory Usage Explanation:**
- 📦 **Plugin本体**: Only occupies about 3 MB (JAR file size)
- 💾 **Total Memory Usage ~50 MB**: Includes runtime cache
  - **Knowledge Base Cache** (depends on knowledge base size)
    * Raw content cache: Complete text of each file, small servers typically 2-5 files, about 2-5 KB/file
    * Segment cache: Fragments split by Markdown headings or paragraphs, each segment max 500 characters
    * Typical scale: 10-20 files × average 10 segments × 500 characters ≈ 100-200 KB
    * Large knowledge base (50+ files): About 500 KB - 1 MB
  - **Conversation Context Cache** (depends on active player count)
    * Each player saves up to 10 rounds of conversation (20 messages)
    * Each message averages 200 characters ≈ 0.2 KB
    * 100 active players × 20 messages × 0.2 KB = 400 KB
    * 1000 active players × 20 messages × 0.2 KB = 4 MB
  - **HTTP Connection Pool** (fixed overhead)
    * OkHttpClient connection pool configuration: 10 idle connections, 5 minute timeout
    * Each connection about 50-100 KB (including buffers, Socket, etc.)
    * 10 connections × 100 KB = About 1 MB
  - **Other Runtime Objects**
    * Configuration objects (ConfigManager, LanguageManager, etc.): About 100-200 KB
    * Skill metadata cache (Bukkit API definitions, MarketQuerySkill, etc.): About 50-100 KB
    * JVM object headers, class loading and other basic overhead: About 2-3 MB
  - **Actual Test Data**:
    * Small server (10 knowledge base files + 50 online players): About 8-12 MB
    * Medium server (30 knowledge base files + 200 online players): About 15-25 MB
    * Large server (100+ knowledge base files + 500+ online players): About 30-50 MB

### Performance Optimization Measures

- ✅ **HTTP Connection Pool**: Reuse connections, reduce handshake overhead
- ✅ **Asynchronous Non-Blocking**: API requests don't block main thread
- ✅ **Configuration Caching**: Reduce repeated configuration reading overhead
- ✅ **Smart Retry**: Automatic retry on network fluctuations, improve stability
- ⚠️ **Streaming Output**: Currently not supported in this version, may be provided as Premium feature in future

---

### ⚠️ Callback Execution and Timeout Protection

**Callback commands execute on main thread**: Kilacraft-AI uses `Bukkit.dispatchCommand()` to execute callbacks, this API must be called on main thread (required by AsyncCatcher mechanism in modern servers like Leaf/Paper).

**Timeout Protection Mechanism:**
```yaml
plugin_command:
  callback_timeout_seconds: 3  # Monitor main thread command execution time
```

**Scope of Action:**
- ✅ Monitor `Bukkit.dispatchCommand()` execution time
- ✅ Force interrupt main thread command execution after set time exceeded
- ❌ **Will not** interrupt async tasks inside third-party plugins

**Why?**

When third-party plugins use async processing, `onCommand()` returns immediately, but async tasks still run in background. Kilacraft-AI's timeout protection only monitors main thread command execution time, cannot control third-party plugins' async tasks.

**Advice for Server Owners:**

1. **Set Reasonable Timeout**
   ```yaml
   plugin_command:
     # Should be ≥ third-party plugin's expected processing time
     callback_timeout_seconds: 5
   ```

2. **Understand Timeout Logs**
   ```
   [WARN] Callback command execution timeout (3s), forcibly interrupted. Command: myplugin handle_ai ...
   ```
   - This indicates main thread command execution was interrupted
   - But if third-party plugin uses async processing, its async tasks still running
   - This is **normal behavior**, not an error

**Requirements for Third-Party Plugin Developers:**

If your plugin uses async processing, you must:
1. Manage timeouts yourself
2. Check player online status
3. Set reasonable expected time and inform server owners


---

## 🎮 Complete Command List

| Command | Permission | Description |
|---------|------------|-------------|
| `/kilacraft <message>` | None | Dialogue with AI |
| `/kila <message>` | None | Shorthand command |
| `/ai <message>` | None | Shorthand command |
| `/zm <message>` | None | Shorthand command |
| `/kilacraft chat` | None | Enter/exit continuous chat mode |
| `/kilacraft clear` | `kilacraft.clear.self` | Clear your own conversation history |
| `/kilacraft clear <player>` | `kilacraft.clear.other` | Clear specified player's history |
| `/kilacraft reload` | `kilacraft.reload` | Reload main configuration and language configuration |
| `/kilacraft knowledge reload` | `kilacraft.knowledge` | Reload knowledge base |
| `/kilacraft personalities reload` | `kilacraft.personalities` | Reload personality configuration |
| `/kilacraft plugins <personality> <content> <UUID> [callback]` | Console only | Third-party plugin call (supports callback commands) |

---

## 📦 Dependency Requirements

### Required

- Minecraft Server 1.21+
- Java 17+

### Optional (For Extended Features)

| Plugin | Version Requirement | Function |
|--------|--------------------|----------|
| MythicMobs | 5.12.0+ | Placeholder functionality |
| GlobalMarketPlus | 1.3.8.0+ | Economy system skills |
| Vault | Latest | Multi-currency support |

> 💡 **Note**: When optional plugins are not installed, corresponding features automatically disabled, doesn't affect core dialogue functionality.

---

## 🔧 Advanced Configuration Details

### 1. Agent Capability Configuration

```
agent:
  enabled: true                    # Master switch (highest priority)
  enable_chat_listener: true       # Whether keyword trigger enables Agent
  enable_command: true             # Whether /kilacraft command enables Agent
  
  # Historical conversation context configuration
  intent_history_count: 5          # Use 5 rounds of history for intent recognition
  analysis_history_count: 2        # Use 2 rounds of history for result analysis
  
  prompts:
    system_prompt: "You are a professional Minecraft game assistant..."
    analysis_prompt: "{results}\nPlease provide comprehensive analysis and suggestions based on the above conversation history, current input, and execution results."
```

**Use Cases:**
- **Enable All Entry Points**: Suitable for servers needing complex task processing
- **Command Mode Only**: Only want to provide intelligent features in `/kilacraft`
- **All Disabled**: Only need basic AI dialogue functionality

### 2. Knowledge Base Segmentation Configuration

```
knowledge:
  enabled: true
  max_relevant_chunks: 3           # Maximum 3 segments returned per retrieval
  segment:
    max_size: 500                  # Each segment max 500 characters
    min_size: 25                   # Minimum 25 characters (ignore if smaller)
    overlap: 30                    # Segment overlap 30 characters (maintain coherence)
```

**Best Practices:**
- ✅ FAQ Q&A Style (Most Recommended): Each question one `##` heading
- ✅ Rule List Style: Each rule one paragraph
- ❌ Avoid超大段落: Entire paragraph exceeding 2000 characters

### 3. Language Customization

All system prompt texts can be customized in `language.yml`:

```
help:
  messages:
    - "§eUsage: /kilacraft <message>"
    - "§eShorthand: /kila <message>"
  
features:
  chat-mode-enter: "§aEntered continuous chat mode!"
  
commands:
  reload-success: "§aConfiguration reloaded!"
  cooldown-warning: "§cPlease wait {seconds} seconds before trying again!"
```

Supports color codes (`§`) and variable placeholders (`{player}`, `{seconds}`).

---

## 🚀 Ecosystem Expansion: Invite Developers to Build AI Agent Ecosystem Together

### 🌈 Vision

We firmly believe: **Every Minecraft plugin should be able to converse with AI!**

Kilacraft-AI through open **Skill SPI interface**, allows third-party plugin developers to easily expose their functionality to AI Agent, achieving:

- 💬 **Natural Language Interaction**: Players don't need to remember complex commands, just describe needs in language
- 🤖 **Intelligent Intent Recognition**: AI automatically understands player intent and calls corresponding functions
- 🔗 **Multi-Step Task Orchestration**: Cross-plugin function combination,实现 complex workflows

### 📚 Developer Resources

- **SPI Integration Guide**: [Kilacraft-AI-Skill-SPI-Integration-Guide.md](Skill-SPI-接入文档)
- **API JAR**: `Kilacraft-Skill-API-1.4.0.jar` (only 5 KB, compileOnly dependency)
- **Example Code**: Documentation includes complete Hello World and PlayerStats examples

### 🎯 Plugin Types Suitable for Integration

| Plugin Type | Example Skill | User Scenario |
|------------|---------------|---------------|
| **Economy System** | Balance query, transfer, shop purchase | "Help me transfer 100 to Steve" |
| **Land System** | Query land, create land, add members | "How big is my land?" |
| **Leaderboards** | Wealth ranking, online time ranking | "Who is the server's richest?" |
| **RPG System** | Query skill levels, quest progress | "What is my mining level?" |
| **Guild System** | Guild info, member list | "How many people in our guild?" |
| **Achievement System** | Achievement progress, incomplete achievements | "Which achievements haven't I completed?" |
| **World Management** | Chunk info, teleport management | "Teleport to main city" |

### 🤝 Contact Us

If you're a plugin developer, welcome to:

1. **Read SPI Documentation**, learn integration method (get started in 5 minutes)
2. **Develop Skills for Your Plugin**, let players call your features with natural language
3. **Provide Feedback**, help us improve Kilacraft-AI
4. **Share in Community**, help more server owners understand AI Agent value

### 🏆 Integrated Plugins

- ✅ **GlobalMarketPlus**: Deep economy system integration (balance, price, product queries)
- ✅ **Bukkit API**: 44+ vanilla API dynamic invocation (player status, world info, server info)
- ✅ **MythicMobs**: Placeholder support (NPC displays AI replies)

**Looking forward to your plugin being next!** 🎉

---

## 🏗️ System Architecture Details

This section provides an in-depth analysis of Kilacraft-AI's core architecture design, helping you fully understand the working principles, call chains, and design philosophy of the three interaction modes.

### 🔄 Overview of Three Interaction Modes

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenarios |
|------|---------------|------------------|-------------------------|-------------------|------------------|
| **ChatListener** | `@ai` keyword / continuous chat | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Player active interaction |
| **KilacraftCommand** | `/kilacraft <message>` | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Server owner/admin queries |
| **Plugin Command** | `/kilacraft plugins ...` | ❌ Disabled | ✅ Normal retrieval | ✅ Required | Third-party plugin integration |

---

### 📊 Mode 1 & 2: ChatListener / KilacraftCommand (Agent Enabled)

These two modes share the same Agent processing flow, differing only in trigger method.

#### Complete Call Chain

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

#### Key Features

1. **Intelligent Intent Recognition**: LLM understands user's true intent, automatically selects Skills
2. **Multi-Step Orchestration**: Complex tasks decomposed into ordered steps, data auto-flows
3. **Knowledge Enhancement**: Inject relevant knowledge during secondary analysis, improve accuracy
4. **Failure Fallback**: Auto-convert to normal AI dialogue when intent recognition fails or Skill execution errors

#### Example Flow

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

### 📊 Mode 3: Plugin Command Mode (Agent Disabled)

Plugin command mode is an interface designed specifically for **third-party plugin integration**, with a completely different design philosophy from normal modes.

#### Why doesn't plugin command mode enable Agent capabilities?

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

#### Complete Call Chain

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

#### Key Features

1. **Pure Text Output**: Ensure callback commands can correctly receive and process
2. **Personality**: Use specified personality prompts, controllable style
3. **Knowledge Support**: Still can retrieve knowledge base, enhance answer quality
4. **Isolated History**: Independent history for each `UUID_personality` combination
5. **Callback Mechanism**: Automatically execute specified commands after AI completes

#### Typical Application Scenarios

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

### 🔍 Core Differences Comparison Table

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

### 💡 Design Philosophy Summary

#### ChatListener / KilacraftCommand Mode

> "Let players interact with the server in the most natural way, AI understands intent and executes tasks"

-面向终端玩家
- Emphasize intelligence and automation
- Support complex task orchestration
- Flexible and diverse output

#### Plugin Command Mode

> "Provide stable and reliable AI text generation interface for third-party plugins"

-面向开发者
- Emphasize stability and predictability
- Output must be pure text
- Support callback mechanism

#### Why design this way?

1. **Responsibility Separation**: Two modes serve different target groups
2. **Performance Optimization**: Plugin integration doesn't need intent recognition overhead
3. **Reliability**: Pure text output ensures callback mechanism stability
4. **Flexibility**: Keep both modes to meet different needs

---

### 🎯 How to Choose Which Mode to Use?

#### Use ChatListener / KilacraftCommand if:

- ✅ Players need intelligent interaction with AI
- ✅ Need to execute server operations (query, purchase, management, etc.)
- ✅ Want AI to understand complex multi-step tasks
- ✅ Output can be diverse (data + suggestions)

#### Use Plugin Command Mode if:

- ✅ You are a plugin developer needing to call AI in code
- ✅ Only need AI-generated text content
- ✅ Need to pass AI replies to other systems
- ✅ Need personality-based reply styles
- ✅ Need callback mechanism for automated workflows

---

## ❓ Frequently Asked Questions FAQ

### Q1: Are API costs high?

**A:** Depends on usage frequency. Taking DeepSeek as example:
- `deepseek-chat` model: Input ¥0.5/million Tokens, Output ¥2/million Tokens
- Single conversation consumes about 500-1000 Tokens (about ¥0.001-0.002)
- Recommend setting reasonable `cooldown_seconds` to prevent abuse

**Money-Saving Tips:**
- Enable cooldown time (default 5 seconds)
- Limit allowed worlds (avoid use in high-frequency scenarios like mob farms)
- Adjust `max_tokens` to control reply length

### Q2: Which LLM vendors are supported?

**A:** Supports all vendors following **OpenAI standard API format**:

| Vendor | API URL | Recommended Model |
|--------|---------|------------------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `deepseek-chat` |
| Zhipu AI | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4-plus` |
| Moonshot | `https://api.moonshot.cn/v1/chat/completions` | `moonshot-v1-8k` |

Just modify `api_url` and `model` in `config.yml` to switch, no code changes needed!

### Q3: Will it lag the server?

**A:** No. Plugin adopts multiple optimization measures:
- **Extremely low memory usage**: Small servers only need 8-12 MB, large servers about 30-50 MB (far lower than traditional solutions' 2-5 GB)
- **Asynchronous non-blocking execution**: API requests performed in background threads, don't block main thread
- **HTTP connection pool reuse**: 10 idle connections reused, reduce handshake overhead
- **Configurable cooldown**: Prevent high-frequency calls (default 5 seconds)
- **Streaming response optional**: Reduce first-byte latency

### Q4: How to ensure data security?

**A:** 
- API Key stored in local configuration file, won't upload to any server
- Conversation history only saved in memory, cleared after restart
- Support world restrictions, can disable AI features in sensitive areas
- Fine-grained permission control, can limit who can use which features

### Q5: Does it support offline player dialogue?

**A:** 
- **Normal dialogue mode**: Not supported, AI dialogue requires player online to receive replies.
- **Plugin command mode**: Supported! Console can call via `/kilacraft plugins` command, even if player offline can generate AI replies (processed through callback commands).
- **Application Scenario**: MythicMobs NPCs can pre-generate replies when player offline, display when player comes online.

### Q6: How to update plugin?

**A:** 
1. Backup `plugins/Kilacraft-AI/` directory (preserve configuration)
2. Replace `Kilacraft-AI.jar` file
3. Restart server
4. Check if new version configuration items have changed

### Q7: What to do if encountering problems?

**A:** 
1. Enable debug mode: `settings.debug_mode: true`
2. Check console logs, look for error messages
3. Verify API Key is correct, network is通畅
4. Consult "Troubleshooting" chapter in this document
5. Ask questions in community forums or contact author

---

## 🐛 Troubleshooting

### AI Not Replying

**Possible Causes:**
1. API Key incorrect or expired → Check `api_key` in `config.yml`
2. Network connection issues → Check if server can access API address
3. In disabled world → Check `banned_worlds` configuration
4. Cooldown time not reached → Wait a few seconds and try again

**Solution:**
```
# Enable debug mode to view detailed logs
settings:
  debug_mode: true
```

### Cooldown Time Too Long

**Adjustment Method:**
```
settings:
  cooldown_seconds: 2  # Change to 2 seconds
```

### Continuous Chat Mode Not Working

**Check Items:**
1. Whether `enable_chat_command: true` is enabled
2. Whether player has corresponding permissions
3. Check console for error logs

### Knowledge Base Retrieval Not Accurate

**Optimization Suggestions:**
1. Use Markdown heading segmentation (`#`, `##`, `###`)
2. Each knowledge point independent paragraph, avoid large blocks of text
3. Adjust `max_relevant_chunks` to increase return count
4. Use `/kilacraft knowledge reload` to reload

### GlobalMarketPlus Features Unavailable

**Check Items:**
1. Whether GlobalMarketPlus plugin is installed
2. Whether version is 1.3.8.0+
3. Check console for integration failure logs

---

## 📝 Changelog

### v1.4.0 (2026-04-05) - Third-party Skill SPI + Plugin Command Callback Mechanism 🚀

**Core Upgrade 1**: Support third-party plugins to register custom Skills via SPI mechanism!

- ✅ **Skill SPI Interface**: `SkillProvider` + auto-discovery mechanism
- ✅ **Error Isolation**: Third-party Skill exceptions don't affect core processes
- ✅ **API JAR Packaging**: `Kilacraft-Skill-API-1.4.0.jar` (only 5 KB)
- ✅ **Complete Documentation**: SPI integration guide + example code

**Core Upgrade 2**: Plugin command mode supports callback commands!

- ✅ **Callback Command Mechanism**: Automatically execute specified commands after AI completes, `{response}` placeholder replaced with actual reply
- ✅ **One-Time Consumption**: Delete cache immediately after callback execution, avoid data accumulation
- ✅ **Flexible Integration**: Applicable to MythicMobs, custom plugins and all scenarios
- ✅ **Removed Polling Method**: No longer support `plugins get` subcommand and placeholder polling
- ✅ **Security Hardening**: Don't provide API to directly read cache, prevent malicious plugins from stealing data

### v1.3.6 - Generic LLM Provider Architecture

- ✅ Support all LLM vendors following OpenAI standard format
- ✅ HTTP connection pool optimization, performance improved 30%
- ✅ Streaming response support, reduce first-byte latency

### v1.3.5 - Historical Conversation Context Enhancement

- ✅ Intent recognition supports 5 rounds of historical conversation
- ✅ Result analysis supports 2 rounds of historical conversation
- ✅ Continuous conversation understanding ability improved

### v1.3.4 - MarketQuerySkill Capability Expansion

- ✅ Added 4 read-only actions: Product availability query, my products, mailbox, market statistics
- ✅ Multi-item joint query support
- ✅ 44 Bukkit API dynamic invocations

### v1.3.0 - AI Agent Evolution Edition

- ✅ Skills skill system framework
- ✅ LLM intent recognition engine
- ✅ GlobalMarketPlus deep integration

---

## 🤝 Contribution and Feedback

We welcome community contributions! If you have any suggestions or discover problems:

1. **Submit Issue**: Report problems or suggest improvements on GitHub or Gitee
2. **Submit PR**: Fix bugs or add new features
3. **Share Experience**: Share your usage experiences and configuration tips in community
4. **Develop Skills**: Develop custom Skills for your plugins, enrich ecosystem

**Contact Information:**
- GitHub Issues: https://github.com/Zm-Mmm/Kilacraft-AI/issues
- Gitee Issues: https://gitee.com/zm_mmm/kilacraft-ai/issues
- Email: 1456133139@qq.com

---

## 📄 License

This project uses **MIT License** - See [LICENSE](../LICENSE) file for details

**You Can:**
- ✅ Freely use, copy, modify, merge, publish, distribute
- ✅ Use for commercial projects
- ✅ Create derivative works

**You Need To:**
- 📝 Include original copyright notice and license in copies

---

## 👨‍💻 Author

**Zm_Mmm**

- GitHub: https://github.com/Zm-Mmm
- Gitee: https://gitee.com/zm_mmm
- Email: 1456133139@qq.com

---

## 🔗 Related Links

- **SPI Integration Guide**: [Kilacraft-AI-Skill-SPI-Integration-Guide.md](Skill-SPI-接入文档)
- **Changelog**: [Kilacraft-AI-Changelog.md](Kilacraft-AI-\ Changelog.md)
- **DeepSeek API Documentation**: https://platform.deepseek.com/api-docs/
- **Zhipu AI Documentation**: https://open.bigmodel.cn/dev/api
- **Moonshot Documentation**: https://platform.moonshot.cn/docs

---

## 🌟 Support the Project

If you find Kilacraft-AI helpful, welcome to:

- ⭐ **Star the Project**: Give us a Star on GitHub or Gitee
- 📢 **Share with Friends**: Recommend to other server owners and developers
- 💬 **Provide Feedback**: Tell us your usage experience and improvement suggestions
- 🤝 **Develop Third-Party Skills**: Integrate AI capabilities for your plugins
- 🐛 **Report Issues**: Submit Issues promptly when discovering bugs

**Your support is our motivation for continuous optimization!** ❤️

---

> **Last Updated**: 2026-04-06  
> **Applicable Plugin Version**: Kilacraft-AI 1.4.1+  
> **Open Source License**: MIT License
