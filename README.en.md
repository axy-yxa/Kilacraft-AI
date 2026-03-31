# Kilacraft-AI

> **🎉 v1.3.0 Major Update**: Evolved from conversational AI to **AI Agent**! New features include Skills System, Intent Recognition, Economy Integration, and more. See [Changelog](#-changelog)

A powerful Minecraft AI chat plugin integrating DeepSeek AI, providing intelligent interactive experiences for server players.

## 📋 Features

### 🤖 AI Agent Core Capabilities
- **LLM Intent Recognition Engine**: Intelligently understands user's true intentions and routes to corresponding skills
- **Skills System Framework**: Extensible AI skill execution framework with async non-blocking support
- **Multi-modal Interaction**: Command mode, continuous chat mode, keyword trigger mode

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

### Personality Configuration (personalities.yml)

Create YAML files in `plugins/Kilacraft-AI/personalities/` to define different personalities:

```yaml
StrictTeacher:
  prompt: "You are a strict Minecraft teacher, strict but caring towards player {player}."
  
FriendlyHelper:
  prompt: "You are a friendly AI helper, assisting player {player} with various tasks."
```

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

### Permissions

| Permission Node | Default | Description |
|-----------------|---------|-------------|
| `kilacraft.clear.self` | true | Clear own chat history |
| `kilacraft.clear.other` | op | Clear other players' chat history |
| `kilacraft.reload` | op | Reload configuration |
| `kilacraft.knowledge` | op | Manage knowledge base |
| `kilacraft.personalities` | op | Manage personality configurations |

**Note**: Basic chat functionality requires no permissions, available to all players by default.

## 📚 Knowledge Base Feature

### Adding Server Knowledge

1. Create `.md` or `.txt` files in `plugins/Kilacraft-AI/knowledge/` directory
2. Add server-related knowledge content
3. Use `/kilacraft knowledge reload` to reload

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
