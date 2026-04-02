# Kilacraft-AI

> **🎉 v1.3.2 Major Update**: Agent capabilities fully configurable! New multi-step task executor, intent recognition prompts configurable, unified AI request handler and more. See [Changelog](#-changelog)

A powerful Minecraft AI chat plugin integrating DeepSeek AI, providing intelligent interactive experiences for server players.

## 📋 Features

### 🤖 AI Agent Core Capabilities
- **LLM Intent Recognition Engine**: Intelligently understands user's true intentions and routes to corresponding skills
- **Skills System Framework**: Extensible AI skill execution framework with async non-blocking support
- **Bukkit API Dynamic Invocation (v1.3.3+)**: Data-driven vanilla API calling capability
  - Load API definitions from `apis.yml` configuration file, no hardcoding required
  - Supports method_chain and additional_methods invocation patterns
  - Reflection-based execution engine for dynamic Player/World/Server method calls
  - Permission control, hot-reload support, instant configuration changes
  - Pre-configured APIs for player status, world info, and server queries
- **Multi-modal Interaction**: Command mode, continuous chat mode, keyword trigger mode
- **Multi-Step Task Executor (v1.3.2+)**: Complex task automatic decomposition and sequential execution
  - Topological sort-based dependency management
  - Previous step results automatically passed as context to subsequent steps
  - LLM comprehensively analyzes all step results and generates friendly responses

### 💰 Economy Integration (Experimental)
- **GlobalMarketPlus Deep Integration**: Player balance inquiry, market price inquiry, product list inquiry
- **Multi-item Joint Query**: Check prices of multiple items at once, format: `diamond:2,stick:1`
- **Quantity Recognition**: Natural language understanding for "buy 5 sticks" etc.
- **Optimal Price Calculation**: Smart combination from cheapest to most expensive, considering actual stock
- **Insufficient Stock Notification**: Shows detailed prices and quantities of all available items

### 🎭 Personalization & Context
- **Personality System**: Multiple personality configurations, customizable AI roles and response styles
- **Contextual Conversations**: Automatically saves chat history, supports continuous context-aware conversations
- **Knowledge Base Enhancement**: Local knowledge base retrieval, making AI understand your server better

### 🔌 Third-party Plugin Support
- **MythicMobs Placeholders**: Use `%kilacraft_ai_answer%` to get latest AI responses
- **Console Command Calls**: Other plugins can integrate AI functionality via console commands

### ⚙️ Management & Security
- **Permission Management**: Fine-grained permission control, admins can clear other players' history
- **Cooldown & Limits**: Prevent abuse, supports customizable cooldown times and world restrictions
- **Streaming Output**: Real-time display of AI response generation (optional)

## 🔧 Installation

1. Download the latest version of `Kilacraft-AI.jar`
2. Place the jar file in your server's `plugins` folder
3. Start the server and wait for the plugin to generate configuration files
4. Stop the server, edit `plugins/Kilacraft-AI/config.yml` to configure API key and other settings
5. Restart the server

### Requirements

- **Required**:
  - Minecraft Server 1.21+
  - Java 17+
  
- **Optional** (for extended features):
  - MythicMobs 5.12.0+ (for placeholder functionality)
  - GlobalMarketPlus 1.3.8.0+ (for economy system skills, experimental)
  - Vault (for multi-currency support)

## ⚙️ Configuration

### Core Configuration (config.yml)

```yaml
# API Configuration
api:
  key: "your-deepseek-api-key"      # DeepSeek API key (required)
  url: "https://api.deepseek.com/v1/chat/completions"
  model: "deepseek-chat"              # Model to use
  temperature: 0.7                    # Temperature parameter (0-2)
  max_tokens: 1000                    # Maximum response length

# Plugin Settings
settings:
  debug_mode: false                   # Debug mode
  enable_chat_command: true           # Enable continuous chat mode
  enable_trigger: true                # Enable keyword trigger
  trigger_keywords: "@kila,@ai,@zm"   # Trigger keywords
  enable_stream_output: false         # Streaming output
  cooldown_seconds: 5                 # Cooldown time (seconds)
  plugins_cooldown_seconds: 3         # Plugin command specific cooldown
  max_history: 10                     # Maximum history records
  allowed_worlds: []                  # Allowed worlds (empty = all)
  banned_worlds: []                   # Banned worlds
  system_prompt: "You are a Minecraft assistant..."  # System prompt

# Message Formatting
messages:
  ai_name: "Kilacraft-AI"
  ai_prefix: "§7[Kilacraft-AI] §f"
  thinking_message: "Thinking..."

# Agent Configuration (v1.3.2+)
agent:
  enabled: true                       # Master switch (highest priority)
  enable_chat_listener: true          # Enable Agent for ChatListener entry
  enable_command: true                # Enable Agent for KilaccraftCommand entry
  prompts:
    system_prompt: "You are a professional Minecraft game assistant..."  # System prompt for result analysis
    analysis_prompt: "Please provide comprehensive analysis...\n\n{results}\n\nPlease reply to the player in a concise and friendly manner."

# Knowledge Base Configuration
knowledge:
  enabled: true                       # Enable knowledge base
  max_relevant_chunks: 3              # Maximum relevant chunks
```

### Language Configuration (language.yml)

All system prompt texts can be customized in `language.yml`, including:

- **Help Messages**: Help prompts for various commands
- **Permission Messages**: Error messages when lacking permissions
- **Feature Status**: Prompts for feature enabled/disabled states
- **Command Results**: Success/failure operation feedback
- **Log Messages**: Console output log formats

Example configuration:

```yaml
help:
  messages:
    - "§eUsage: /kilacraft <message>"
    - "§eShortcuts: /kila, /ai, /zm"
  clear-self: "§eClear history: /kilacraft clear"
  
permissions:
  reload: "§cYou don't have permission to reload configuration!"
  
features:
  chat-mode-enter: "§aEntered continuous chat mode!"
  
commands:
  reload-success: "§aConfiguration reloaded!"
```

Supports variable placeholders: `{player}`, `{sender}`, etc.

### Agent Configuration (v1.3.2+)

Agent capabilities provide fine-grained configuration control, allowing server administrators to decide which entry points enable AI's intelligent intent recognition features.

#### Configuration Details

```yaml
agent:
  # Master switch - Priority over all sub-switches
  # true: Enable Agent capabilities, AI will perform intent recognition first
  # false: Disable Agent capabilities, directly enter normal AI chat
  enabled: true
  
  # ChatListener entry independent switch
  # Controls keyword triggers (@ai etc.) and continuous chat mode (/kilacraft chat)
  enable_chat_listener: true
  
  # KilacraftCommand entry independent switch
  # Controls /kilacraft command entry
  enable_command: true
  
  # LLM prompts configuration
  prompts:
    # System prompt - Defines LLM's role when analyzing execution results
    system_prompt: "You are a professional Minecraft game assistant, please provide useful suggestions based on the provided data."
    
    # Analysis prompt - Guides LLM on how to analyze execution results
    # Supports {results} placeholder, replaced with task execution result summary
    analysis_prompt: "Please provide a comprehensive analysis and recommendations based on the following task execution results:\n\n{results}\n\nPlease reply to the player in a concise and friendly manner."
```

#### Workflow

**When Agent Capabilities Enabled**:
1. User input → LLM intent recognition
2. Determine if single intent or multi-step task
3. Execute skill or task plan
4. LLM analyzes execution results and generates friendly response
5. If intent recognition fails or skill execution fails → Fallback to normal AI chat

**When Agent Capabilities Disabled**:
1. User input → Directly enter normal AI chat
2. No intent recognition or skill invocation

#### Use Cases

- **All Entries Enabled**: Suitable for servers needing complex task handling, AI can automatically execute multi-step operations
- **Command Mode Only**: Suitable for providing intelligent features only in `/kilacraft` command, keeping simple chat in conversations
- **All Disabled**: Suitable for servers only needing basic AI chat functionality

### Personality Configuration (personalities.yml)

Create YAML files in the `plugins/Kilacraft-AI/personalities/` directory to define different personalities.

#### Basic Usage

```yaml
StrictTeacher:
  prompt: "You are a strict Minecraft teacher, strict but caring towards player {player}."
  
FriendlyHelper:
  prompt: "You are a friendly AI helper, assisting player {player} with various tasks."
```

#### Advanced Features (v1.3.3+)

- **YAML Multi-line Text Support**: Use `|` or `>` for complex personality descriptions
- **JSON Format Fault Tolerance**: Auto-repair common JSON format errors
- **{player} Placeholder**: Automatically replaced with current player name
- **Hot-Reload Support**: Use `/kilacraft personalities reload` after modifying config

#### Best Practices

- **Naming**: Use concise, clear names; avoid special characters
- **Prompt Design**: Clearly define AI's role, language style, and behavior guidelines
- **Multi-file Management**: Distribute personalities across multiple files by scenario or function

## 🎮 Usage

### Basic Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/kilacraft <message>` | None (all players) | Chat with AI |
| `/kila <message>` | None (all players) | Shortcut command |
| `/ai <message>` | None (all players) | Shortcut command |
| `/zm <message>` | None (all players) | Shortcut command |
| `/kilacraft chat` | None (all players) | Enter/Exit continuous chat mode |
| `/kilacraft clear` | `kilacraft.clear.self` | Clear your own chat history |
| `/kilacraft clear <player>` | `kilacraft.clear.other` | Clear specified player's chat history |
| `/kilacraft reload` | `kilacraft.reload` | Reload main and language configurations |
| `/kilacraft knowledge reload` | `kilacraft.knowledge` | Reload knowledge base |
| `/kilacraft personalities reload` | `kilacraft.personalities` | Reload personality configurations |
| `/kilacraft plugins <personality> <message> <UUID>` | Console only | Third-party plugin call |

### Three Interaction Modes

#### 1. Command Mode (Default)
Players use `/kilacraft <message>` or shortcuts to chat with AI.

```
Player: /kilacraft Hello
Kilacraft-AI: Hello! How can I help you today?
```

#### 2. Continuous Chat Mode
Enter `/kilacraft chat` to enable continuous chat mode, all subsequent chat messages will be automatically sent to AI.

```
Player: /kilacraft chat
→ Entered continuous chat mode!
Player: Nice weather today
Kilacraft-AI: Yes, perfect for going on an adventure!
Player: Let's go mining
Kilacraft-AI: Great idea! Remember to bring enough torches and food.
```

#### 3. Keyword Trigger Mode
Messages containing keywords (like `@ai`) in chat will automatically trigger AI responses.

```
Player: @ai How do I do this?
Kilacraft-AI: Let me help you...
```

#### Permissions

#### Basic Permissions

| Permission Node | Default | Description |
|-----------------|---------|-------------|
| `kilacraft.clear.self` | true | Clear own chat history |
| `kilacraft.clear.other` | op | Clear other players' chat history |
| `kilacraft.reload` | op | Reload configuration |
| `kilacraft.knowledge` | op | Manage knowledge base |
| `kilacraft.personalities` | op | Manage personality configurations |

#### Bukkit API Skill Permissions (v1.3.3+)

| Permission Node | Default | Description |
|-----------------|---------|-------------|
| `kilacraft.api.player.inventory` | true | Query player inventory info |
| `kilacraft.api.player.status` | true | Query player status (health, experience, etc.) |
| `kilacraft.api.player.info` | true | Query player info (location, game mode, etc.) |
| `kilacraft.api.world.info` | true | Query world info (time, weather, etc.) |
| `kilacraft.api.server.info` | true | Query server info (online players, etc.) |
| `kilacraft.api.*` | true | Use all Bukkit API skills (wildcard permission) |

**Notes**:
- Basic chat functionality requires no permissions, available to all players by default
- Bukkit API skill permissions control AI's ability to call vanilla APIs
- Use permission plugins (e.g., LuckPerms) for fine-grained per-player/group control

## 📚 Knowledge Base Feature

### Adding Server Knowledge

1. Create `.md` or `.txt` files in `plugins/Kilacraft-AI/knowledge/` directory
2. Add server-related knowledge content
3. Use `/kilacraft knowledge reload` to reload

### Intelligent Segmentation Rules (v1.3.1+)

The plugin uses a **three-level intelligent segmentation strategy** to automatically split knowledge base files into retrievable chunks:

#### 1. Markdown Header Splitting (Highest Priority)
- **Recognizes `#`, `##`, `###` and other header markers**
- **Each header and its content becomes an independent chunk**
- **Best for**: Rule lists, FAQs, categorized guides with clear structure

**Example**:
```markdown
# Server Rules
All content belongs to "Server Rules" chunk

## Economy System
All content belongs to "Economy System" chunk

## Land Protection
All content belongs to "Land Protection" chunk
```

#### 2. Paragraph Splitting (Medium Priority)
- **Automatically splits when paragraph exceeds configured size**
- **Maintains semantic integrity**, splits at natural paragraph boundaries
- **Best for**: Long descriptions, detailed explanations

#### 3. Fixed Size Splitting (Fallback Strategy)
- **Maximum chunk size**: Default 500 characters (adjustable in config.yml)
- **Minimum chunk size**: Default 25 characters (chunks smaller than this are ignored)
- **Overlap area**: Default 30 characters (maintains context coherence)

### Best Practices

#### ✅ Recommended Knowledge Base File Formats

**1. FAQ Q&A Format (Highly Recommended)**
```markdown
# Frequently Asked Questions

## How do I get land?
Use the /claim command to designate your land. Requires at least 10 gold coins.

## How do I earn money?
You can earn money by:
- Mining and selling ores
- Fishing and crafting food
- Selling items at player shops
- Completing quests for rewards
```

**2. Rule List Format**
```markdown
# Server Rules

## Basic Rules
1. No cheating or using hacks
2. Be friendly, no insulting others
3. No destroying other players' builds

## Economy Rules
4. No exploiting money glitches
5. Trading must follow market rates
```

**3. Categorized Guide Format**
```markdown
# Beginner's Guide

## Step 1: Get Familiar
Learn basic controls and interface

## Step 2: Gather Resources
Collect wood, stone and basic materials

## Step 3: Establish Base
Choose a suitable location for your home
```

#### ❌ Formats to Avoid

- **Oversized Paragraphs**: Continuous text exceeding 2000 characters
- **Unstructured Content**: Large blocks of text without headers or paragraphs
- **Pure Code/Command Lists**: Command listings without explanatory text

### Configuration Options

Adjust knowledge base segmentation parameters in `config.yml`:

```yaml
knowledge:
  segment:
    max_size: 500     # Maximum characters per chunk
    min_size: 25      # Minimum characters per chunk
    overlap: 30       # Overlap characters between chunks
```

### Caching Mechanism

- **Auto-cache on First Load**: No manual cache clearing needed after file modifications
- **~70% Faster Secondary Retrieval**: Cached files read segmentation results directly
- **Hot-Reload Support**: Use `/kilacraft knowledge reload` to refresh cache immediately

**Example File** (`server_rules.md`):
```markdown
# Server Rules

1. No cheating or using hacks
2. Be friendly, no insulting others
3. No destroying other players' builds
4. Economy system: Use /money to check balance

# FAQ

Q: How do I get land?
A: Use the /claim command to designate your land.

Q: How do I earn money?
A: You can earn money by mining, fishing, or selling items at player shops.
```

## 🔌 Developer API

### Skills Skill Framework (v1.3.0+)

The plugin uses an LLM intent-based skill execution framework, supporting custom extensions:

```java
// Implement custom skill
public class MyCustomSkill implements Skill {
    @Override
    public String getName() {
        return "my_skill";
    }
    
    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // Async skill execution
        return CompletableFuture.completedFuture(
            SkillResult.success("Success!")
        );
    }
}
```

### Console Command Call

Other plugins can call AI by executing console commands:

```java
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
    String.format("kilacraft plugins %s %s %s", 
        personality, 
        message, 
        player.getUniqueId().toString()
    )
);
```

### MythicMobs Placeholders

If MythicMobs is installed, you can use `%kilacraft_ai_answer%` placeholder to get the latest AI response.

## ⚠️ Important Notes

1. **API Costs**: DeepSeek API is a paid service, please monitor call frequency
2. **Cooldown Time**: Recommended to set reasonable cooldown to prevent abuse
3. **World Restrictions**: AI functionality can be disabled in specific worlds
4. **Memory Usage**: Chat history occupies memory, recommended to periodically clean offline player data

## 🐛 Troubleshooting

### AI Not Responding
- Check if API key is correct
- Verify network connection
- Check console for error messages
- Ensure not in a disabled world

### Cooldown Too Long
- Adjust `cooldown_seconds` configuration
- Check if multiple cooldowns are applying simultaneously

### Continuous Chat Mode Not Working
- Ensure `enable_chat_command: true`
- Check if player has appropriate permissions

## 📝 Changelog

### v1.3.3 - Bukkit API Capabilities & Automated System Prompt 🚀

**Major Upgrade**: Dynamic Bukkit API invocation with fully automated system prompt construction!

#### 🎯 Core Features

- ✅ **Bukkit API Skill System (GenericBukkitAPI)**: Data-driven vanilla API calling framework
  - **Dynamic Metadata-Driven**: Load API definitions from `apis.yml`, zero hardcoding
  - **Reflection Execution Engine**: Dynamic Bukkit API calls via reflection (Player/World/Server)
  - **Dual Mode Support**:
    - **method_chain**: Chained calls returning complex objects (ItemStack, Location, etc.)
    - **additional_methods**: Parallel independent method calls for simple values
  - **Smart Formatting**: Template placeholder replacement, special type handling (Location/GameMode/ItemStack)
  - **Permission Control**: Configurable permission nodes per API
  - **Hot-Reload Support**: Modify `apis.yml` and use `/kilacraft reload` to apply instantly

- ✅ **Fully Automated System Prompt**: Zero hardcoding dynamic prompt construction
  - **Auto-traverse All Skills**: Including traditional skills and Bukkit API skills
  - **Dynamic Action Generation**: Automatically lists all available actions per skill
  - **Automatic Hint Integration**: Skill hints automatically integrated into system prompt
  - **Multi-Step Task Examples**: Automatically generates practical example scenarios
  - **Maintenance Cost Reduced ~90%**: No code changes needed when adding new skills

#### 🔧 Technical Implementation

- ✅ **New Files**:
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIExecutor.java` - API execution engine
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIMetadata.java` - API metadata encapsulation
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIConfigLoader.java` - configuration loader
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/GenericBukkitAPISkill.java` - generic API skill implementation
  - `src/main/resources/skills/bukkit/apis.yml` - Bukkit API metadata configuration file

- ✅ **Architecture Refactoring**:
  - `SkillConfig.java` moved to `skills.framework.config` package
  - `TaskExecutor.java` moved to `skills.framework.task` package
  - `TaskPlan.java` moved to `skills.framework.task` package
  - `Skill` interface added `isAvailable()` default method

- ✅ **Configuration Management Enhancement**:
  - `SkillConfigManager` added `loadBukkitAPIs()` method
  - `SkillConfigManager` added `reloadAllConfigs()` hot-reload method
  - `MarketQuerySkill` fixed hot-reload issue (from fixed reference to dynamic retrieval)

- ✅ **Permission System Extension**:
  - Added 5 Bukkit API permission nodes (player/world/server queries)
  - Added wildcard permission `kilacraft.api.*`

- ✅ **Personality Configuration Optimization**:
  - **YAML Multi-line Text Support**: Use `|` or `>` for complex personality descriptions
  - **JSON Format Fault Tolerance**: Auto-repair common JSON format errors
  - **Empty Config Detection**: Auto-create example file when config is empty
  - **Error Recovery Mechanism**: Auto-generate default config on load failure
  - **Common Prompt Feature**: Shared base prompt for all personalities

- ✅ **Console AI Capabilities Enhancement**:
  - **Refactored Console Command Logic**: `handleConsoleMessageCommand()` method completely rewritten
  - **Unified AI Request Handling**: Console now supports same full features as players (intent recognition, skill invocation, multi-step tasks)
  - **No Cooldown or World Restrictions**: Console calls exempt from cooldown time and world limitations
  - **Fixed UUID Identifier**: Uses `00000000-0000-0000-0000-000000000000` as console's unique identifier
  - **Independent History**: Console has independent conversation history, isolated from players
  - **Simplified Response Handler**: `ConsoleResponseHandler` constructor optimized, removed unnecessary parameters

- ✅ **Code Quality Improvements**:
  - Optimized logging output, reduced redundant information
  - Improved exception handling mechanism
  - Optimized configuration loading process

#### 📦 Pre-configured Bukkit API Examples

**Player Related**:
- `get_player_hand_item` - Get main hand item (chain: getInventory → getItemInMainHand)
- `get_player_health` - Get health value (parallel: getHealth + getMaxHealth)
- `get_player_location` - Get position coordinates (supports getLocation.getX chain calls)
- `get_player_game_mode` - Get game mode
- `get_player_level` - Get level and experience

**World Related**:
- `get_world_time` - Get world time
- `get_weather` - Get weather status (hasStorm + isThundering)

**Server Related**:
- `get_server_online_players` - Get online player count

#### ⚙️ Configuration Example

```yaml
# apis.yml example
player:
  get_player_health:
    id: "get_player_health"
    display_name: "Get Player Health"
    description: "Get player's current and maximum health value"
    usage_scenarios:
      - "How much HP do I have left"
      - "My health"
    target_type: "Player"
    additional_methods:
      health: "getHealth"
      max_health: "getMaxHealth"
    result_template: "Health: {health}/{max_health}"
```

#### ⚠️ Compatibility Notes

- New `apis.yml` configuration file, auto-created on first startup
- New permission nodes, recommended to update permission configuration
- Package structure adjustments don't affect existing skill implementations

---

### v1.3.2 - Agent Configuration & Multi-Step Task Executor 🚀

**Major Upgrade**: Agent capabilities fully configurable with multi-step task planning and execution!

#### 🎯 Core Features

- ✅ **Fine-grained Agent Configuration**: Fully config-driven Agent capability switches
  - **Master Control**: `agent.enabled` takes priority over all sub-switches
  - **Independent Entry Control**: `enable_chat_listener` (keyword trigger/continuous chat) and `enable_command` (/kilacraft command)
  - **Flexible Fallback**: Directly enters normal AI chat when Agent is disabled, no intent analysis needed
  - **Config-driven Behavior**: Caller decides whether to enable Agent based on configuration, passing state instead of caching

- ✅ **Multi-Step Task Executor (TaskExecutor)**: Complex task planning and automatic execution
  - **Topological Sort Algorithm**: DFS-based dependency detection, automatic cycle identification
  - **Step Dependency Management**: Previous step results automatically passed as context to subsequent steps
  - **Result Summary Analysis**: LLM comprehensively analyzes all steps and generates friendly responses
  - **Optimized Debug Logs**: Detailed execution tracking for easy troubleshooting

- ✅ **Enhanced LLM Intent Recognition**: Automatic decomposition of complex tasks
  - **Single Intent Fast Path**: Simple tasks execute directly with zero overhead
  - **Multi-Step Task Planning**: Complex tasks automatically decomposed into ordered steps
  - **JSON Schema Validation**: Strict intent format validation ensuring parsing reliability
  - **Fallback Mechanism**: Automatically falls back to normal AI on intent recognition or skill execution failure

#### 🔧 Technical Optimizations

- ✅ **Unified AI Request Handler (AIRequestHandler)**:
  - Eliminated ~130 lines of duplicate logic in ChatListener and KilacraftCommand
  - Unified intent recognition + skill execution workflow
  - Config-based state passing design, no internal state caching
  - Supports dynamic enabling/disabling of Agent capabilities

- ✅ **Prompt Configuration**:
  - **system_prompt**: Defines LLM's role during result analysis phase (default: "You are a professional game assistant..."
  - **analysis_prompt**: Guides LLM on how to analyze execution results and generate responses (supports `{results}` placeholder)
  - Fully customizable prompts without code changes
  - Automatic placeholder replacement (`{player}`, `{results}`)

- ✅ **Architecture Refactoring**:
  - Removed redundant single-step processing logic from TaskExecutor
  - Deleted old compatibility methods from SkillIntentRecognizer
  - ChatListener now fully supports multi-step task handling
  - Separation of responsibilities: caller handles config checks, AIRequestHandler handles execution

- ✅ **Message Format Optimization**:
  - All AI responses automatically prefixed with `MessageUtil.getAIPrefix()`
  - Unified visual experience matching language.yml configuration
  - Optimized debug mode logging, using logger.info instead of System.out.println

#### 📦 New Files

- `src/main/java/com/zm/kilacraftAI/handler/AIRequestHandler.java` - Unified AI request handler
- `src/main/java/com/zm/kilacraftAI/skills/framework/TaskExecutor.java` - Multi-step task executor

#### ⚙️ Configuration Changes

```yaml
# config.yml new Agent configuration
agent:
  enabled: true                    # Master switch (highest priority)
  enable_chat_listener: true       # Enable Agent for ChatListener entry
  enable_command: true             # Enable Agent for KilacraftCommand entry
  prompts:
    system_prompt: "You are a professional game assistant..."  # System prompt for result analysis
    analysis_prompt: "Please provide a comprehensive analysis based on the following task execution results...\n\n{results}\n\nPlease reply to the player in a concise and friendly manner."
```

#### ⚠️ Compatibility Notes

- Agent configuration structure changed, recommended to backup and regenerate config files
- TaskExecutor prompt configuration, original hardcoded prompts migrated to config.yml
- AIRequestHandler location adjusted to `handler` package (not `handler.impl` subpackage)

---

### v1.3.1 - RAG Retrieval Optimization & Response Speed Improvement 🚀

**Core Upgrade**: Refactored knowledge retrieval architecture, optimized Chinese word segmentation, improved plugin response speed!

#### 🎯 Knowledge Retrieval Optimization

- ✅ **Standard RAG**: Industry-standard knowledge retrieval architecture supporting multi-format knowledge files
- ✅ **Intelligent Chunking**: Markdown headers → Paragraphs → Fixed size (3-level strategy)
- ✅ **Chinese Keyword Extraction**: n-gram segmentation + intelligent stop word filtering + automatic punctuation removal
- ✅ **Multi-level Scoring**: Complete question matching (+50) + Keyword matching (+5) + Title matching (+25) + Coverage multiplier
- ✅ **Cache Optimization**: Cache after first chunking, secondary retrieval speed improved ~70%

#### ⚡ Response Speed Optimization

- ✅ **Instant Thinking Message**: Moved from skill execution to command entry point, eliminates "plugin is slow" misconception
- ✅ **Unified Cooldown Management**: Unified handling in `handlePlayerMessageCommand()`, avoids duplicate cooldown during fallback
- ✅ **Code Architecture Optimization**: Separation of responsibilities, all branches reuse unified entry point

#### 🔧 Technical Details

- ✅ **Knowledge Retriever Refactoring**: Reimplemented standard RAG retrieval workflow
- ✅ **Configuration Enhancement**: Knowledge base chunking configuration (`knowledge.segment`) + YAML multi-line personality support
- ✅ **Debug Log Optimization**: Detailed word segmentation process and matching details

#### ⚙️ API Performance Optimization (DeepSeekAPINew)

- ✅ **HTTP Connection Pool**: Reuse connections, max idle=10, keep-alive=5min
- ✅ **Timeout Configuration**: Connect=30s, Read=60s, Write=30s
- ✅ **Streaming Response**: Reduced first-token latency using BufferedReader line-by-line reading
- ✅ **Configuration Caching**: Cache model/temperature/maxTokens values to reduce repeated lookups
- ✅ **Pre-allocated Buffers**: StringBuilder pre-sized (512/256) to reduce expansion overhead
- ✅ **Auto-retry Mechanism**: Enabled retryOnConnectionFailure(true)

#### ⚠️ Compatibility Notes

- Knowledge base chunking configuration structure changed, recommended to backup and regenerate
- Stop word list may need adjustment based on actual usage

---

### v1.3.0 - AI Agent Evolution 🚀

**Major Upgrade**: Evolved from conversational AI to AI Agent with skill execution and intent recognition capabilities!

#### 🎯 Core Features

- ✅ **Skills System Framework**: New extensible AI skill execution framework
  - LLM intent-based automatic skill routing
  - Asynchronous execution model, non-blocking
  - Easy-to-extend skill interface design
  - Read-only operations first, ensuring safety
  
- ✅ **LLM Intent Recognition Engine**: Intelligently understands user's true intentions
  - Dynamic skill prompt construction
  - Multi-entity extraction support (items, quantities, etc.)
  - Confidence evaluation and reasoning explanation
  - Fallback mechanism to ensure user experience

- ✅ **GlobalMarketPlus Deep Integration** (Experimental): Economy system skills
  - Player balance inquiry (multi-currency support)
  - Market price inquiry (intelligent exact matching)
  - Product list inquiry
  - **Multi-item Joint Query**: Check prices of multiple items at once
  - **Quantity Recognition**: Natural language understanding for "buy 5 sticks"
  - **Optimal Price Calculation**: Smart combination from cheapest to most expensive, considering actual stock
  - **Insufficient Stock Notification**: Shows detailed prices and quantities of all available items

#### 🔧 Technical Optimizations

- ✅ **Core Architecture Refactoring**:
  - Added `SkillContext` execution context
  - Added `SkillResult` result encapsulation
  - Added `SkillManager` skill manager
  - Added `Skill` base interface
  - Added `SkillConfig` configuration encapsulation
  
- ✅ **Intent Recognition System**:
  - Added `SkillIntentRecognizer` intent recognizer
  - Added `SkillIntent` intent encapsulation
  - Dynamic JSON Schema generation
  - Configurable skill descriptions

- ✅ **Anti-Duplication Mechanism**:
  - Fixed duplicate thinking message issue during skill fallback
  - Fixed duplicate cooldown issue during skill fallback
  - Unified logical consistency between command handler and chat listener

- ✅ **Intelligent Item Name Matching**:
  - Priority exact matching (searching "diamond" excludes "diamond sword")
  - Fallback fuzzy matching (available when searching "diamond sword")
  - Chinese-English translation mapping support

- ✅ **Configuration System Enhancement**:
  - Independent skill configuration management (`skills/` directory)
  - Item translation configuration (`translate/items_CN.yml`)
  - Hot-reload support for skill configurations
  - YAML configuration key-value order preservation

#### 📦 New Files

- `src/main/java/com/zm/kilacraftAI/skills/framework/` - Skill framework core
- `src/main/java/com/zm/kilacraftAI/skills/globalmarketplus/` - GlobalMarketPlus skill implementation
- `src/main/java/com/zm/kilacraftAI/skills/config/` - Skill configuration management
- `src/main/resources/skills/` - Skill configuration files
- `src/main/resources/translate/` - Item translation configuration

#### ⚠️ Compatibility Notes

- Configuration structure changed, recommended to backup and regenerate config files
- Skill system is experimental, API may be adjusted in future versions
- GlobalMarketPlus integration requires plugin version 1.3.8.0+

---

### v1.2.3
- ✅ **Language Configuration System**: Extracted all system prompt texts to `language.yml` configuration file
  - Supports customizing all command help, permission prompts, feature status messages
  - Supports color codes and variable placeholders (`{player}`, `{sender}`)
  - `/kilacraft reload` command now reloads both main and language configurations
  - Server administrators can fully customize all AI plugin system prompts
- ✅ **Dynamic Help Messages**: help command dynamically displays prompts based on player permissions
- ✅ **Architecture Optimization**: Added `LanguageManager` for unified management of all language configurations
- ✅ **Permission Management Optimization**: Created `PluginPermission` enum class for unified permission node management
  - Removed all hardcoded permission strings
  - All permission checks use enum class `PluginPermission.XXX.hasPermission(sender)`
  - Tab completion also dynamically displays based on permission enum
- ✅ **Prompt Text Optimization**: Integrated duplicate prompts for better reusability
  - Unified error message format
  - Optimized continuous chat mode disabled prompts
- ✅ **Validation Logic Refactoring**: Separated validation and notification in utility classes, following Single Responsibility Principle
  - `AIRequestValidator` only handles validation, no longer sends notifications directly
  - Cooldown notification: handled by caller based on validation result
  - World restriction notification: handled by caller based on validation result
  - All notification texts read from `language.yml`, support placeholders

### v1.2.2
- ✅ Removed `kilacraft.use` permission requirement, available to all players by default
- ✅ Optimized Tab completion, dynamically shows commands based on permissions
- ✅ Separated clear history prompts, shows different commands based on permissions
- ✅ Improved permission system and command handling

### v1.2.1
- ✅ Added AI latest response caching mechanism, optimized custom placeholder parsing performance
- ✅ Enhanced command help messages, permission checks, and logging
- ✅ Removed source declarations from knowledge base enhancements for smoother responses

### v1.2.0
- ✅ **Plugin Command System**: Support console commands to call AI for third-party plugin integration
  - Independent cooldown control (`plugins_cooldown_seconds`)
  - Configuration-driven personality prompts
  - Hot-reload support
- ✅ **Architecture Refactoring**:
  - Added `ConversationManager` for unified management of chat states, history, and plugin command records
  - Refactored `ChatListener` with separated responsibilities, focused on event listening
- ✅ **Extended Clear Command**: Support clearing specific player's context by player name
- ✅ **MythicMobs Integration**: Implemented custom `%kilacraft_ai_answer%` placeholder

### v1.1.0
- ✅ Architecture refactoring, configuration-driven encapsulation
- ✅ Strategy pattern and abstract base class encapsulation
- ✅ Local RAG index enhancement and prompt engineering optimization

### v1.0.0
- ✅ Basic conversation capabilities
- ✅ Continuous chat, chat listening
- ✅ Historical conversation context recording
- ✅ Rate limiting, world restriction checks

## 🤝 Contributing

1. Fork this repository
2. Create a Feat_xxx branch
3. Commit your changes
4. Create a Pull Request

## 📄 License

This project is licensed under [Please specify license, e.g., MIT/GPL]

## 👨‍💻 Author

Zm_Mmm

## 🔗 Related Links

- [DeepSeek API Documentation](https://platform.deepseek.com/api-docs/)
- [Issue Tracker](Please add link)
