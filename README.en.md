# Kilacraft-AI

> **🎉 v1.4.0 Major Update**: Introduced Third-party Skill SPI Extension! Seamless integration for plugin developers. See [Changelog](#-changelog)

A powerful Minecraft AI chat plugin integrating DeepSeek AI, providing intelligent interactive experiences for server players.

## 📋 Features

### 🤖 AI Agent Core Capabilities
- **LLM Intent Recognition Engine**: Intelligently understands user's true intentions and routes to corresponding skills
- **Skills System Framework**: Extensible AI skill execution framework with async non-blocking support
- **Generic LLM Provider Architecture (v1.3.6+)**: Supports LLM services following OpenAI standard API format
  - Configuration-driven, switch between different LLM vendors via config.yml
  - Supports DeepSeek, Zhipu AI, Moonshot, and all APIs following OpenAI standards
  - HTTP connection pool optimization for improved performance
  - Streaming response support with reduced first-token latency
  - Foundation for future expansion to more LLM providers
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

### ⚠️ Streaming Output Notice

Streaming output is not supported in the current version. The `enable_stream_output` configuration is reserved for future use.

**Tip for Third-party Plugin Developers**: If you need streaming effects, you can implement "pseudo-streaming output" in your own plugin by receiving the complete AI response and displaying it in batches using `BukkitRunnable` (e.g., show 10 characters every 500ms to simulate a typewriter effect).

**Note**: Callback commands execute on the main thread (required by Bukkit API), but third-party plugins should **return immediately** and process complex logic in async threads to avoid blocking.

---

### 💰 Economy Integration (Experimental)
- **GlobalMarketPlus Deep Integration**: Player balance inquiry, market price inquiry, product list inquiry
- **Item Availability Query (v1.3.4+)**: Check if item is available, stock quantity, seller info
- **My Items Query (v1.3.4+)**: Check player's own listed items
- **Mailbox Query (v1.3.4+)**: Check pending mailbox items
- **Market Stats (v1.3.4+)**: Query total market items and seller count
- **Multi-item Joint Query**: Check prices of multiple items at once, format: `diamond:2,stick:1`
- **Quantity Recognition**: Natural language understanding for "buy 5 sticks" etc.
- **Optimal Price Calculation**: Smart combination from cheapest to most expensive, considering actual stock
- **Insufficient Stock Notification**: Shows detailed prices and quantities of all available items

### 🎭 Personalization & Context
- **Personality System**: Multiple personality configurations, customizable AI roles and response styles
- **Contextual Conversations**: Automatically saves chat history, supports continuous context-aware conversations
- **Knowledge Base Enhancement**: Local knowledge base retrieval, making AI understand your server better

### 🔌 Third-party Plugin Support & Extensions
- **Skill SPI Extension (v1.4.0+)**: Support third-party plugins to register custom Skills via SPI
  - Zero-coupling integration: Just include `Kilacraft-Skill-API.jar` as compileOnly dependency
  - Auto-discovery: Scans automatically on startup via Bukkit ServicesManager
  - Error isolation: Third-party Skill exceptions won't affect core processes
  - Complete SPI documentation and examples provided
- **Plugin Command Mode Generalization (v1.4.0+)**: Decouple AI personality system from MythicMobs, providing two integration methods
  - **Method A: Plugin Message Fully Decoupled** (✅ Highly Recommended, no dependencies)
    ```java
    // Register listener in your plugin main class
    getServer().getMessenger().registerIncomingPluginChannel(
        this, "kilacraft:ai_response", 
        (channel, player, message) -> {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String type = in.readUTF(); // "AI_RESPONSE"
            String playerId = in.readUTF();
            String playerName = in.readUTF();
            String personality = in.readUTF();
            String response = in.readUTF(); // AI response content
            // Handle response...
        }
    );
    ```
  - **Method B: Console Command + Callback Command** (For configuration-driven plugins like MythicMobs)
    ```bash
    # Request AI response (optional 5th parameter: callback command)
    /kilacraft plugins <personality> <content> <playerUUID> [callback_command]
    
    # Example: Auto-execute myplugin command after AI completion
    /kilacraft plugins StrictTeacher Hello 00000000-0000-0000-0000-000000000000 "myplugin handleAI {response}"
    
    # Poll for latest response (returns UNDEFINED if incomplete)
    /kilacraft plugins get <personality> <playerUUID>
    ```
  - **One-time Consumption Cache**: Deleted on get, avoid data pollution
  - **Context Isolation**: Conversation history completely isolated per personality and player
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
# LLM Provider Configuration (v1.3.6+)
# Generic LLM Provider architecture, supports all vendors following OpenAI standard API format
api:
  key: "your-api-key"              # LLM API key (required)
  url: "https://api.deepseek.com/v1/chat/completions"  # API endpoint
  model: "deepseek-chat"            # Model name to use
  temperature: 0.7                  # Temperature parameter (0-2, higher = more random)
  max_tokens: 1000                  # Maximum response length (tokens)
  
  # Supported LLM vendor examples:
  # - DeepSeek: https://api.deepseek.com/v1/chat/completions
  # - Zhipu AI: https://open.bigmodel.cn/api/paas/v4/chat/completions
  # - Moonshot: https://api.moonshot.cn/v1/chat/completions
  # Just modify url and model to switch between different vendors

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
  enable_command: true                # Enable Agent for KilacraftCommand entry
  
  # Historical Conversation Context (v1.3.5+)
  intent_history_count: 5           # Number of historical turns for intent recognition
  analysis_history_count: 2         # Number of historical turns for result analysis
  
  prompts:
    system_prompt: "You are a professional Minecraft game assistant..."
    analysis_prompt: "{results}\nBased on the conversation history, current input, and execution results above, provide comprehensive analysis and suggestions. Please reply to the player in a concise and friendly manner."

# Knowledge Base Configuration
knowledge:
  enabled: true                     # Enable knowledge base
  max_relevant_chunks: 3            # Maximum relevant chunks
  segment:                          # Knowledge base segmentation configuration
    max_size: 500                   # Max characters per segment (will split further if exceeded)
    min_size: 25                    # Min characters per segment (segments smaller than this will be ignored)
    overlap: 30                     # Overlap characters between segments (maintains context coherence)
```

#### API Configuration Details

**Generic LLM Provider Architecture (v1.3.6+)**:
- Configuration-driven, switch between different LLM vendors by modifying `url` and `model`
- Supports all vendors following **OpenAI standard API format**
- HTTP connection pool optimization with auto-retry mechanism
- Streaming response support with reduced first-token latency

**Common LLM Vendor Configurations**:

| Vendor | API URL | Recommended Model |
|--------|---------|-------------------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `deepseek-chat` |
| Zhipu AI | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4` |
| Moonshot | `https://api.moonshot.cn/v1/chat/completions` | `moonshot-v1-8k` |

**Steps to Switch LLM Vendor**:
1. Modify `api.url` to target vendor's API endpoint
2. Modify `api.model` to target vendor's model name
3. Ensure `api.key` contains the correct API key
4. Use `/kilacraft reload` to reload configuration (or restart server)

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
    analysis_prompt: "{results}\nBased on the conversation history, current input, and execution results above, provide comprehensive analysis and suggestions. Please reply to the player in a concise and friendly manner."
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

Define different personality configurations in the `plugins/Kilacraft-AI/personalities.yml` file.

#### Basic Usage

```yaml
# Common prompt (optional, shared base prompt for all personalities)
common_prompt: "You are an NPC in Minecraft game, here to fulfill players' common requests."

# Strict Teacher personality
StrictTeacher: |
  You are a strict Minecraft teacher, currently teaching player {player}.
  You have high standards for your students, speak concisely and directly, but patiently answer questions.
  Focus on teaching game mechanics, redstone circuits, and building techniques.

# Adventure Companion personality
AdventureCompanion: |
  You are player {player}'s loyal adventure companion, cheerful and humorous.
  You love sharing exploration stories, providing combat advice, recommending equipment combinations, and always encourage players to explore bravely.

# Librarian personality
Librarian: |
  You are a knowledgeable librarian, providing knowledge services to adventurer {player}.
  You speak elegantly, enjoy quoting ancient texts, and are well-versed in Minecraft's history, creature characteristics, mineral distributions, and various trivia.

# Cunning Merchant personality
CunningMerchant: |
  You are a shrewd Minecraft merchant, currently conversing with customer {player}.
  You speak smoothly, always trying to promote your goods, know the economic system and trading prices inside out, and occasionally crack a joke.
```

#### Configuration Details

- **common_prompt** (Optional): Common prompt that automatically appends to each personality's prompt
  - Used to define shared base settings for all personalities, such as "You are an NPC in Minecraft"
  - If not needed, this item can be omitted
  
- **Personality Name**: Such as `StrictTeacher`, `AdventureCompanion`, etc., using YAML key-value format
  - Chinese names are recommended for easier recognition and invocation
  - Avoid special characters and spaces
  
- **Prompt Content**: Detailed settings for each personality
  - Supports YAML multi-line text format (using `|` or `>`)
  - Supports `{player}` placeholder, automatically replaced with current player name
  - Describes AI's role positioning, language style, professional field, and behavior guidelines

#### Advanced Features (v1.3.3+)

- **YAML Multi-line Text Support**: Use `|` or `>` for complex personality descriptions
  - `|` preserves line breaks, suitable for multi-line paragraphs
  - `>` folds line breaks, suitable for long sentences
  
- **JSON Format Fault Tolerance**: Auto-repair common JSON format errors
- **{player} Placeholder**: Automatically replaced with current player name
- **Hot-Reload Support**: Use `/kilacraft personalities reload` after modifying config
- **Common Prompt Mechanism**: Configure shared base settings via `common_prompt` to avoid repetition

#### Best Practices

- **Naming**: Use concise, clear names; avoid special characters
- **Prompt Design**: Clearly define AI's role, language style, and behavior guidelines
- **Common Prompt**: Put general settings in `common_prompt`, personality-specific settings in their own configs
- **Multi-scenario Application**: Create multiple personalities by scenario or function, such as teaching, adventure, trading, etc.

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

The plugin uses an LLM intent-based skill execution framework, supporting custom extensions.

#### Built-in Skills

- **Bukkit API Dynamic Calls**: Load vanilla API definitions from `apis.yml` without hardcoding
- **GlobalMarketPlus Economy System**: Balance checks, market prices, item lists, etc.

#### Third-party Skill SPI (v1.4.0+)

Starting from v1.4.0, third-party plugins can register custom Skills via SPI mechanism:

```java
// 1. Implement Skill interface
public class MyCustomSkill implements Skill {
    @Override
    public String getName() { return "my_skill"; }
    
    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        return CompletableFuture.completedFuture(SkillResult.success("Success!"));
    }
}

// 2. Register in main plugin class
public class MyPlugin extends JavaPlugin implements SkillProvider {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(SkillProvider.class, this, this, ServicePriority.Normal);
    }
    
    @Override
    public List<Skill> getSkills() {
        return List.of(new MyCustomSkill());
    }
}
```

For detailed integration guide, refer to `Kilacraft-AI-Skill-SPI-接入文档.md`.

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

## 🎯 Best Practices for Third-party Developers

> **Target Audience**: Third-party plugin developers integrating with Kilacraft-AI  
> **Core Principles**: Async execution, fast return, resource management, timeout protection

### 1. Async Processing (Required)

Kilacraft-AI invokes third-party plugin callbacks via `Bukkit.dispatchCommand()`, which **must execute on the main thread**. If your plugin performs time-consuming operations (database queries, network requests) in the callback, it will severely block server TPS.

**Correct Approach:**
```java
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (cmd.getName().equalsIgnoreCase("myplugin_handle_ai")) {
        // Start async task immediately
        new BukkitRunnable() {
            @Override
            public void run() {
                // Execute time-consuming operations in async thread
                // Return to main thread to send message when done
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(response);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        
        return true; // Main thread returns immediately
    }
    return false;
}
```

### 2. Pseudo-streaming Output (Optional)

For streaming effects, use `BukkitRunnable` scheduled tasks to display in batches:

```java
// Display 10 characters every 500ms, simulate typewriter effect
new BukkitRunnable() {
    int index = 0;
    StringBuilder accumulated = new StringBuilder();
    
    @Override
    public void run() {
        if (index >= message.length()) {
            this.cancel();
            return;
        }
        int end = Math.min(index + 10, message.length());
        accumulated.append(message.substring(index, end));
        player.sendMessage("§e[AI] §f" + accumulated.toString());
        index = end;
    }
}.runTaskTimer(plugin, 0L, 10L); // 10 ticks = 500ms
```

### 3. Understanding Timeout Protection

Kilacraft-AI's timeout protection mechanism only monitors **main thread command execution time**, and will NOT interrupt async tasks within third-party plugins.

- ✅ Timeout only monitors `dispatchCommand()` main thread execution time
- ✅ If your plugin uses async processing, `onCommand()` returns immediately (< 10ms)
- ✅ Async tasks continue running in background,不受 timeout restrictions
- ❌ Only when your plugin **synchronously blocks** main thread beyond threshold will it be interrupted

### 4. Resource Management

Plugin must clean up all tasks and resources when disabled:

```java
private final List<BukkitTask> activeTasks = new ArrayList<>();

@Override
public void onDisable() {
    activeTasks.forEach(task -> {
        if (!task.isCancelled()) task.cancel();
    });
    activeTasks.clear();
}
```

### 5. Performance Metrics Reference

| Metric | Recommended Value |
|--------|-------------------|
| `onCommand()` Execution Time | < 10ms |
| Async Task Timeout Setting | 5-10s |
| Thread Pool Size | 2-4 |
| Pseudo-streaming Output Interval | 300-500ms |
| Pseudo-streaming Output Chunk Size | 8-12 characters |

For complete examples and detailed explanations, please refer to [Skill-SPI Integration Guide](./knowledge/Skill-SPI-Integration-Guide.md).

---

## 🎯 Best Practices for Third-party Developers

> **Target Audience**: Third-party plugin developers integrating with Kilacraft-AI  
> **Core Principles**: Async execution, fast return, resource management, timeout protection

### 1. Async Processing (Required)

Kilacraft-AI invokes third-party plugin callbacks via `Bukkit.dispatchCommand()`, which **must execute on the main thread**. If your plugin performs time-consuming operations (database queries, network requests) in the callback, it will severely block server TPS.

**Correct Approach:**
```java
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (cmd.getName().equalsIgnoreCase("myplugin_handle_ai")) {
        // Start async task immediately
        new BukkitRunnable() {
            @Override
            public void run() {
                // Execute time-consuming operations in async thread
                // Return to main thread to send message when done
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(response);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        
        return true; // Main thread returns immediately
    }
    return false;
}
```

### 2. Pseudo-streaming Output (Optional)

For streaming effects, use `BukkitRunnable` scheduled tasks to display in batches:

```java
// Display 10 characters every 500ms, simulate typewriter effect
new BukkitRunnable() {
    int index = 0;
    StringBuilder accumulated = new StringBuilder();
    
    @Override
    public void run() {
        if (index >= message.length()) {
            this.cancel();
            return;
        }
        int end = Math.min(index + 10, message.length());
        accumulated.append(message.substring(index, end));
        player.sendMessage("§e[AI] §f" + accumulated.toString());
        index = end;
    }
}.runTaskTimer(plugin, 0L, 10L); // 10 ticks = 500ms
```

### 3. Understanding Timeout Protection

Kilacraft-AI's timeout protection mechanism only monitors **main thread command execution time**, and will NOT interrupt async tasks within third-party plugins.

- ✅ Timeout only monitors `dispatchCommand()` main thread execution time
- ✅ If your plugin uses async processing, `onCommand()` returns immediately (< 10ms)
- ✅ Async tasks continue running in background,不受 timeout restrictions
- ❌ Only when your plugin **synchronously blocks** main thread beyond threshold will it be interrupted

### 4. Resource Management

Plugin must clean up all tasks and resources when disabled:

```java
private final List<BukkitTask> activeTasks = new ArrayList<>();

@Override
public void onDisable() {
    activeTasks.forEach(task -> {
        if (!task.isCancelled()) task.cancel();
    });
    activeTasks.clear();
}
```

### 5. Performance Metrics Reference

| Metric | Recommended Value |
|--------|-------------------|
| `onCommand()` Execution Time | < 10ms |
| Async Task Timeout Setting | 5-10s |
| Thread Pool Size | 2-4 |
| Pseudo-streaming Output Interval | 300-500ms |
| Pseudo-streaming Output Chunk Size | 8-12 characters |

For complete examples and detailed explanations, please refer to [Skill-SPI Integration Guide](./knowledge/Skill-SPI-Integration-Guide.md).

---

## 🎯 Best Practices for Third-party Developers

> **Target Audience**: Third-party plugin developers integrating with Kilacraft-AI  
> **Core Principles**: Async execution, fast return, resource management, timeout protection

### 1. Async Processing (Required)

Kilacraft-AI invokes third-party plugin callbacks via `Bukkit.dispatchCommand()`, which **must execute on the main thread**. If your plugin performs time-consuming operations (database queries, network requests) in the callback, it will severely block server TPS.

**Correct Approach:**
```java
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (cmd.getName().equalsIgnoreCase("myplugin_handle_ai")) {
        // Start async task immediately
        new BukkitRunnable() {
            @Override
            public void run() {
                // Execute time-consuming operations in async thread
                // Return to main thread to send message when done
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(response);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        
        return true; // Main thread returns immediately
    }
    return false;
}
```

### 2. Pseudo-streaming Output (Optional)

For streaming effects, use `BukkitRunnable` scheduled tasks to display in batches:

```java
// Display 10 characters every 500ms, simulate typewriter effect
new BukkitRunnable() {
    int index = 0;
    StringBuilder accumulated = new StringBuilder();
    
    @Override
    public void run() {
        if (index >= message.length()) {
            this.cancel();
            return;
        }
        int end = Math.min(index + 10, message.length());
        accumulated.append(message.substring(index, end));
        player.sendMessage("§e[AI] §f" + accumulated.toString());
        index = end;
    }
}.runTaskTimer(plugin, 0L, 10L); // 10 ticks = 500ms
```

### 3. Understanding Timeout Protection

Kilacraft-AI's timeout protection mechanism only monitors **main thread command execution time**, and will NOT interrupt async tasks within third-party plugins.

- ✅ Timeout only monitors `dispatchCommand()` main thread execution time
- ✅ If your plugin uses async processing, `onCommand()` returns immediately (< 10ms)
- ✅ Async tasks continue running in background,不受 timeout restrictions
- ❌ Only when your plugin **synchronously blocks** main thread beyond threshold will it be interrupted

### 4. Resource Management

Plugin must clean up all tasks and resources when disabled:

```java
private final List<BukkitTask> activeTasks = new ArrayList<>();

@Override
public void onDisable() {
    activeTasks.forEach(task -> {
        if (!task.isCancelled()) task.cancel();
    });
    activeTasks.clear();
}
```

### 5. Performance Metrics Reference

| Metric | Recommended Value |
|--------|-------------------|
| `onCommand()` Execution Time | < 10ms |
| Async Task Timeout Setting | 5-10s |
| Thread Pool Size | 2-4 |
| Pseudo-streaming Output Interval | 300-500ms |
| Pseudo-streaming Output Chunk Size | 8-12 characters |

For complete examples and detailed explanations, please refer to [Skill-SPI Integration Guide](./knowledge/Skill-SPI-Integration-Guide.md).

---

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

For detailed version changelog, please see [CHANGELOG.md](doc/Kilacraft-AI%20更新日志.md)

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
