# Kilacraft-AI - Lightweight AI Agent Built for Minecraft Servers

> **🚀 v1.4.3 Officially Released** | Zero Dependencies · Low Memory · High Performance · Easy to Extend · Fully Open Source  
> Enable every Minecraft server to have an intelligent AI assistant

---

## 💎 Core Advantages

- 🚀 **Modern Agent Architecture (Plan-and-Execute + Function Calling)**
  - **Harness Engineering** design pattern
  - **Plan Phase**: LLM intelligently plans tasks (single intent or multi-step)
  - **Execute Phase**: Topological sorting + recursive serial execution
  - **Function Calling**: Dynamic Skill registration and invocation mechanism
  - **Error Tolerance**: Step failures don't interrupt entire flow, intelligent degradation
  - **RAG-Enhanced Architecture**: HanLP TF-IDF + BM25 algorithm
- **🚀 Zero Middleware Dependencies**: Only one JAR file, no database, Redis or other extra services needed
- **💾 Extremely Low Memory Usage**: Small servers only need 8-12 MB, large servers about 30-50 MB (traditional solutions need 2-5 GB)
- **⚡ Out of the Box**: Complete configuration in 5 minutes, ready to use immediately
- **🔧 Highly Customizable**: Personality system, knowledge base, language configuration all customizable
- **🌐 Ecosystem Friendly**: Open SPI interface, third-party plugins can seamlessly integrate

---

## 🌐 Project Links & Contact

- **GitHub**: https://github.com/Zm-Mmm/Kilacraft-AI
- **Gitee**: https://gitee.com/zm_mmm/kilacraft-ai
- **Official Chinese Wiki**: [Click Here](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3%E7%B4%A2%E5%BC%95)
- **Official English Wiki**: [Click Here](https://github.com/Zm-Mmm/Kilacraft-AI/wiki)
- **QQ Group**: 1094391147
- **Email**: 1456133139@qq.com

> 💡 **Welcome to Star ⭐, Fork 🔀 and submit Issues/Pull Requests!**

---

## 📦 Dependency Requirements & Compatibility

### ✅ Version Compatibility

**Minimum Compatible Version: Minecraft 1.16.5 + Java 17**

Kilacraft-AI is developed based on Spigot 1.16.5 API. One JAR package compatible with all subsequent versions.

| Minecraft Version | Java Required | Server Core Support | Description |
|------------------|--------------|---------------------|-------------|
| **1.16.x (1.16.1-1.16.4)** | - | ❌ Not Supported | Official cores don't support this range |
| **1.16.5** | Java 17+ | ⚠️ Paper/Purpur/Leaf/Folia require `-DPaper.IgnoreJavaVersion=true` | CraftBukkit/Spigot don't support Java 17+ |
| **1.17.x - 1.19.x** | Java 17+ | ✅ Fully Supported | Spigot/Paper/Purpur/Leaf/Folia/CraftBukkit |
| **1.20.x - 1.21.x** | Java 21+ | ✅ Fully Supported | Server cores require Java 21 to start |

### 🎁 Optional Dependencies

When these plugins are not installed, corresponding features are automatically disabled, **core dialogue functionality remains unaffected**:

| Plugin | Minimum Version | Features |
|--------|----------------|----------|
| **MythicMobs** | 5.12.0+ | NPC placeholders (let NPCs display AI replies) |
| **GlobalMarketPlus** | 1.3.8.0+ | Economy system (balance, price, product queries) |
| **Vault** | Latest | Multi-currency system support |

---

## 🌟 Feature Showcase

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
- 📚 **Custom Dictionary Support**: Configure server-specific terminology (e.g., "claim land", "redstone", "villager trading") to significantly improve word segmentation accuracy and retrieval effectiveness

**Custom Dictionary Configuration (`config.yml`):**
```yaml
knowledge:
  custom_dictionary:
    enabled: true  # Enable custom dictionary
    words:
      - "圈地"  # claim land
      - "领地"  # territory
      - "红石"  # redstone
      - "附魔"  # enchantment
      - "村民交易"  # villager trading
      - "刷怪塔"  # mob farm
      - "末影龙"  # Ender Dragon
      # Add your server-specific terms...
```

> 💡 **Tip**: Adding server-specific game terms and gameplay names to the custom dictionary can significantly improve AI's understanding accuracy of player questions.

---

#### 📚 Detailed Documentation

For more technical details, please check [Knowledge Base Enhancement Guide](./Knowledge%20Base%20Guide).

---

### 3️⃣ LLM Intelligent Intent Recognition + Multi-Step Task Orchestration

#### 🧠 What is Intelligent Intent Recognition?

Kilacraft-AI doesn't simply "answer questions", but **understands your true intent** and automatically calls corresponding functions!

This is the **brain** of the entire Skill system. Whether calling economy systems, Bukkit API, or third-party plugin Skills, all go through this intelligent engine.

```
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

Other common single-intent scenarios: query items, check status, get time, etc.

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
Final Response:
[Kilacraft-AI] 
    Queried for you:
    • Diamond price: $80.00 each
    • Your balance: $12,580
    
    💡 Analysis: Buying 10 diamonds requires $800, your balance is sufficient, you can purchase!
    Would you like me to place the order for you?
```

---

#### 📐 Unified Structured Format

**AnalysisSummary Unified Format:**

Whether single-intent or multi-step tasks, LLM secondary analysis uses a unified structured output format:

```
[User Input]
Is there anyone on the global market selling the item in my hand? How much is it?

[Execution Results]
- step_1: [SUCCESS] Item: Diamond
- step_2: [SUCCESS] Diamond is for sale, stock: 2

[Statistics] Success: 2, Failed: 0, Skipped: 0
```

**Advantages:**
- Unified format, easy for debugging and troubleshooting
- Clearly displays execution status of each step
- Statistics at a glance

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

The system implements a comprehensive error guarantee mechanism, so even if some steps fail, the entire flow won't be interrupted:

**Fault Tolerance for Multi-Step Tasks:**
- ✅ **Dependency Check**: When a preceding step fails, automatically skip tasks dependent on that step, continue executing other steps
- ✅ **Placeholder Resolution Failure**: Record failure reason, skip this step, continue executing subsequent steps
- ✅ **Skill Execution Failure**: Record failure reason, don't interrupt flow, continue to next step
- ✅ **Intelligent Result Synthesis**: LLM answers user questions to the best of its ability based on all successful step results

**Global Failure Fallback:**
- When intent recognition fails, automatically convert to normal AI dialogue

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

**Technical Advantages:**
- ✅ **Error Isolation**: Third-party Skill exceptions don't affect core processes
- ✅ **Friendly Prompts**: Return meaningful error messages, not technical errors
- ✅ **Detailed Logs**: Console records complete error information for troubleshooting
- ✅ **Flow Resilience**: Step failures don't interrupt the entire flow, maximizing task success rate

---

#### 📚 Detailed Documentation

For more technical details, please check [Intent Recognition Prompt Configuration Guide](./Intent%20Recognition%20Prompt%20Configuration%20Guide).

---

### 4️⃣ Plugin Command Mode + Personality System (Advanced Features)

Plugin command mode is a dedicated interface designed by Kilacraft-AI for **third-party plugin integration**, allowing other plugins to call AI from console with specified personalities.

**Core Design Philosophy:**
> Plugin command mode is not a command for server owners to execute manually, but an **API interface for third-party plugins to call in code or configuration**.

#### 📋 Basic Usage

```
/kilacraft plugins <personality_name> <message_content> <player_uuid> [callback_commands...]
```

**Parameter Explanation:**
- `<personality_name>`: Personality defined in `personalities.yml` (e.g., `Fox`, `StrictTeacher`)
- `<message_content>`: Message to send to AI
- `<player_uuid>`: Target player's UUID
- `[callback_commands...]`: **Optional but strongly recommended**, commands automatically executed after AI completes (supports `{response}` placeholder)

**Important Features:**
- 🔒 **Console Only**: Players cannot use this command directly
- 🌐 **Independent History Records**: Each `UUID_personality` combination is independent
- ⏱️ **Dedicated Cooldown**: Uses `plugins_cooldown_seconds` configuration (default 3 seconds)
- 💬 **Callback Command Support**: Automatically execute after AI completes, `{response}` replaced with actual reply

---

#### 💡 Typical Application Scenario

**NPC Intelligent Dialogue** (most common use): Give MythicMobs NPCs different personalities for natural player interaction.

```
# MythicMobs skill configuration example
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

**Workflow:**
```
Player right-clicks "Fox NPC"
    ↓
Plugin executes console command
    ↓
AI generates reply (async, about 2-5 seconds)
    ↓
Automatically executes callback: /myplugin handle_ai "Heehee~ Server rules are simple! ✨..." playerName
    ↓
Your plugin receives and displays:
[NPC Fox] Heehee~ Server rules are simple! ✨
```

---

#### 📚 Detailed Documentation

For more technical details, please check [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide).

---

#### ⚠️ Quick Precautions

1. **Strongly recommend using callback commands**: This is the only officially supported integration method
2. **Callback handler should return immediately**: Put time-consuming operations in async thread to avoid blocking main thread
3. **Personality must exist**: Used personality must be defined in `personalities.yml`
4. **Player must exist**: Player corresponding to UUID must have records on server
5. **Parameter parsing**: If your callback command parameters contain spaces, need proper parsing (see detailed guide)

> 💡 **Tip**: If you're a third-party plugin developer, we recommend reading the [Plugin Command Mode Detailed Guide](./Plugin%20Command%20Mode%20Detailed%20Guide) first to understand complete integration flow and best practices.

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
- ✅ Insufficient stock notification: Display all sale details

**Security Notes:**
- 📖 **Read-Only Operations**: MarketQuerySkill only queries information, won't consume items or money
- 🔒 **Permission Control**: Each action has independent permission nodes
- 🛡️ **Error Isolation**: Execution errors don't affect other features

---

### 6️⃣ Bukkit API Dynamic Invocation (58 Built-in APIs) (Extended Feature)

No coding required, AI directly calls vanilla APIs to query player status, world info, server info!

**Core Features:**
- ✅ **Data-driven configuration**: Define APIs in YAML, supports hot reload
- ✅ **Multi-step data passing**: API return values automatically extracted to dataMap, subsequent steps can reference via `{step_x.field}`
- ✅ **Permission control**: Independent permission nodes for each API
- ✅ **Error isolation**: API execution failures don't affect other features

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
- 📦 **Player Inventory**: Main hand/offhand items, full armor set
- ❤️ **Player Status**: Health, hunger, oxygen, experience, sleep, attack cooldown, on fire, frozen, pose, sneak/sprint, potion effects
- 📍 **Player Info**: Location coordinates, game mode, fly status, ping, vehicle, death point, target block, locale, display name, respawn point
- 🌍 **World Info**: Time, weather, world type, seed, spawn point, height limit, mob spawning rules, PVP settings, biome, temperature, humidity, sea level, entity statistics, raids, weather duration
- 🖥️ **Server Info**: Online players, max players, version, MOTD, world list

**Fine-Grained Permission Control:**
```
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

#### 📚 Detailed Documentation

For more technical details, please check [Bukkit API Reference Manual](./Bukkit%20API%20Reference).

---

### 7️⃣ AFK Task System (11 Event Listeners) (Extended Feature)

Let AI "keep an eye out" for you! Create background monitoring tasks through natural language, with automatic notification or action execution when conditions are met.

**Core Features:**
- ✅ **Natural Language Creation**: Just tell AI "help me watch for xxx to come online"
- ✅ **Notification / Callback Dual Mode**: Simple reminder or automatic multi-step task execution
- ✅ **11 Event Listeners**: Covering player online/offline, death, teleport, level change, world switch, weather, sleeping, respawn, item break
- ✅ **Automatic Resource Management**: Tasks auto-cancel when player goes offline, auto-cleanup on completion

#### Usage Examples

**Notification Mode** (direct reminder when event triggers):
```
Player: Help me watch for Steve to come online
AI: OK! Monitoring task created. You'll be notified as soon as Steve comes online.

(After Steve comes online...)
🔔 AFK Task Alert: Steve has come online!
```

**Callback Mode** (automatically execute actions when event triggers):
```
Player: Watch for Steve to come online, then check what he's holding
AI: OK! Monitoring task created. When Steve comes online, his held item will be automatically checked.

(After Steve comes online...)
🔔 AFK Task Alert:
Steve is online! Detected him holding Diamond Sword x1 in main hand.
```

**Supported Monitoring Events:**
- 👤 **Player Activity**: Online, offline, death, teleport, level change, world switch
- 🌙 **Life Events**: Enter bed, leave bed, respawn, item break
- 🌦️ **Environment**: Weather change

**Manual Task Management:**
```
/kilacraft afk          # Query current AFK task
/kilacraft afk cancel   # Cancel current AFK task
```

> 💡 **Tip**: Each player can only have one AFK task at a time. Tasks can be created and managed through AI conversation or commands.

---

#### 📚 Detailed Documentation

For more technical details, please check [AFK Task System Guide](./AFK%20Task%20System%20Guide) and [Bukkit Event Listener Reference](./Bukkit%20Event%20Listener%20Reference).

---

## ⚙️ Quick Start (5 Minutes to Get Started)

### Step 1: Install Plugin

1. Download `Kilacraft-AI-1.4.2.jar`
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

### Step 3: Reload Configuration

After modifying configuration, there are two ways to apply changes:

**Method 1: Use reload command (Recommended)**
```
/kilacraft reload  # Reload main configuration and language configuration
```

**Method 2: Restart server**
```
/restart  # or fully restart server
```

> 💡 **Tip**: If you only modified `config.yml` or `language.yml`, use `/kilacraft reload`, no server restart needed. If you modified knowledge base or personality configuration, need to use corresponding dedicated reload commands (see command list below).

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
- 📦 **Plugin Core**: Only occupies about 3 MB (JAR file size)
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
| `/kilacraft afk` | `kilacraft.afk` (default: all players) | Query current AFK task |
| `/kilacraft afk cancel` | `kilacraft.afk` (default: all players) | Cancel current AFK task |

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
- ❌ Avoid large paragraphs: Entire paragraph exceeding 2000 characters

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

### 4. Personality System Configuration

Personality system allows you to customize AI's speaking style and behavioral characteristics, making AI better fit your server atmosphere.

**Basic Usage:**
```yaml
# personalities.yml
Fox: |
  You are a clever fox NPC who speaks playfully and cutely.
  Like to end sentences with "~", often use emojis.
  Well-versed in server rules, answer player questions in interesting ways.
```

**Reload Personality Configuration:**
```
/kilacraft personalities reload
```

---

#### 📚 Detailed Documentation

For more technical details, please check [Personality System Configuration Guide](./Personality%20System%20Guide).

---

## ⚠️ Security and Permission Management

### Built-in Skill Security

All Skills built into Kilacraft-AI follow **security first** principle:

| Skill Name | Function | Security |
|-----------|----------|----------|
| **MarketQuerySkill** | Query market info (balance, prices, product lists, etc.) | ✅ Read-only, won't consume items or money |
| **GenericBukkitAPISkill** | Query player status, world info, server info | ✅ Read-only, only calls getter methods |
| **AFKTaskSkill** | Create background monitoring tasks (online, offline, death, etc.) | ✅ Only registers event listeners, doesn't modify game state |

**Why Secure?**
- 📖 **Read-Only Operations**: All built-in Skills only query information, don't modify any game data
- 🔒 **Permission Control**: Each API has independent permission nodes, can be finely controlled through permission plugins
- 🛡️ **Error Isolation**: Even if a Skill execution fails, it won't affect other functions
- 📝 **Transparent**: All Skill definitions are in YAML files under `skills/` directory, can be reviewed anytime

### Third-Party Skill Risk Warning

If you install Skills developed by third-party plugins, please note:

**Security Risk Sources:**
- Third-party developers may implement **write operations** (such as transfers, teleports, giving items, etc.)
- May have **logic vulnerabilities** leading to unexpected consequences
- May **bypass permission checks** to execute sensitive operations

**Protection Measures:**
1. **Review Skill Source**: Only install plugins from trusted developers
2. **Check Skill Description**: Carefully read Skill's function description and return values
3. **Test Environment Verification**: First verify functionality is normal and safe on test server
4. **Minimum Permissions**: Limit who can use which Skills through permission plugins
5. **Monitor Logs**: Regularly check console logs, handle anomalies promptly

**Advice for Developers:**
If you're a plugin developer, please follow these principles when developing Skills:
- ✅ Prioritize implementing **read-only query** functions
- ⚠️ If implementing **write operations**, must clearly mark risks in documentation
- 🔒 Perform **strict permission checks** in `execute()` method
- 📝 Provide clear **usage instructions** and **precautions**
- 🛡️ Implement proper **exception handling** to avoid crashes or data corruption

See [SPI Integration Guide](./Skill%20SPI%20Integration%20Guide) Chapter 7 "Error Isolation and Exception Handling".

---

#### 📚 Related Technical Documentation

For in-depth understanding of internal mechanisms, please check [System Architecture Details](./System%20Architecture%20Details.md).

---

## 🚀 Ecosystem Expansion: Invite Developers to Build AI Agent Ecosystem Together

### 🌈 Vision

We firmly believe: **Every Minecraft plugin should be able to converse with AI!**

Kilacraft-AI through open **Skill SPI interface**, allows third-party plugin developers to easily expose their functionality to AI Agent, achieving:

- 💬 **Natural Language Interaction**: Players don't need to remember complex commands, just describe needs in language
- 🤖 **Intelligent Intent Recognition**: AI automatically understands player intent and calls corresponding functions
- 🔗 **Multi-Step Task Orchestration**: Cross-plugin function combination, achieve complex workflows

### 📚 Developer Resources

- **SPI Integration Guide**: [Kilacraft-AI-Skill-SPI-Integration-Guide.md](Skill-SPI-Integration-Guide)
- **API JAR**: `Kilacraft-Skill-API-1.4.2.jar` (only 5 KB, compileOnly dependency)
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
- ✅ **Bukkit API**: 58 vanilla API dynamic invocation (player status, world info, server info) with multi-step data passing
- ✅ **AFK Task System**: 11 event listeners, natural language background monitoring tasks, supports notification and callback dual mode
- ✅ **MythicMobs**: Placeholder support (NPC displays AI replies)

**Looking forward to your plugin being next!** 🎉

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
3. Verify API Key is correct, network connection is stable
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

## 🤝 Support and Contribution

### 💖 Support Project Development

If Kilacraft-AI has helped you, consider supporting the project's continued development:

- **[Afdian](https://afdian.com/a/Zm_Mmm)** - Supports WeChat/Alipay

Your support will be used for:
- 🚀 Continuous feature updates and performance optimization
- 🐛 Bug fixes and stability improvements
- 📚 Documentation improvement and tutorial creation
- 💬 Community support and problem solving

Thank you to every supporter! 🙏

---

### 🌟 Participate in Community Contributions

We welcome community contributions! If you have any suggestions or discover problems:

1. **⭐ Star the Project**: Give us a Star on [GitHub](https://github.com/Zm-Mmm/Kilacraft-AI) or [Gitee](https://gitee.com/zm_mmm/kilacraft-ai)
2. **📢 Share with Friends**: Recommend to other server owners and developers
3. **🐛 Submit Issues**: Report problems or suggest improvements
4. **💻 Submit PRs**: Fix bugs or add new features
5. **📝 Share Experience**: Share your usage experiences and configuration tips in community
6. **🔧 Develop Skills**: Develop custom Skills for your plugins, enrich ecosystem

**Your support is our motivation for continuous optimization!** ❤️

---

## 🔗 Related Links

- **📚 Complete Document Index**: [View all technical documents](./Document%20Index.md)
- **DeepSeek API Documentation**: https://platform.deepseek.com/api-docs/
- **Zhipu AI Documentation**: https://open.bigmodel.cn/dev/api
- **Moonshot Documentation**: https://platform.moonshot.cn/docs

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

> **Last Updated**: 2026-04-10  
> **Applicable Plugin Version**: Kilacraft-AI 1.4.3+  
> **Open Source License**: MIT License
