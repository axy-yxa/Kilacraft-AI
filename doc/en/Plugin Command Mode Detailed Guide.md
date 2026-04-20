# Kilacraft-AI Plugin Command Mode Detailed Guide

> **Target Audience**: Third-party plugin developers, advanced server owners   
> **Last Updated**: 2026-04-19

---

## 📋 Table of Contents

- [Overview](#overview)
- [Core Concepts](#core-concepts)
- [Command Format and Parameters](#command-format-and-parameters)
- [Callback Mechanism Details](#callback-mechanism-details)
- [Callback Method Optimization Best Practices](#callback-method-optimization-best-practices)
- [Complete Integration Examples](#complete-integration-examples)
- [Personality System Configuration](#personality-system-configuration)
- [History Isolation Mechanism](#history-isolation-mechanism)
- [FAQ](#faq)

---

## Overview

Plugin command mode is a dedicated interface designed by Kilacraft-AI for **third-party plugin integration**, allowing other plugins to call AI with specified personalities through the console.

**Core Design Philosophy:**
> Plugin command mode is not a command for server owners to execute manually, but an **API interface for third-party plugins to call in code or configuration**.

**Main Application Scenarios:**
- 🤖 **NPC Intelligent Dialogue**: Give MythicMobs NPCs different personalities for natural player interaction
- 🎮 **Custom Interaction**: Dynamically generate AI responses that fit character settings based on plugin logic
- 🔗 **Callback Command Integration**: Automatically execute specified commands after AI completion, seamlessly embedding into third-party plugins

---

## Core Concepts

### Three Interaction Modes Comparison

| Mode | Trigger Method | Agent Capability | Knowledge Base Retrieval | Callback Mechanism | Typical Scenario |
|------|---------------|------------------|-------------------------|-------------------|------------------|
| **ChatListener** | `@ai` keyword / continuous chat | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Player active interaction |
| **KilacraftCommand** | `/kilacraft <message>` | ✅ Enabled | ✅ Smart injection | ❌ Not needed | Server owner/admin queries |
| **Plugin Command** | `/kilacraft plugins ...` | ❌ Disabled | ✅ Normal retrieval | ✅ **Required** | Third-party plugin integration |

### Why Doesn't Plugin Command Mode Enable Agent Capability?

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
   - Avoid unnecessary intent recognition overhead (about 2-5 seconds)
   - Direct normal AI calls are faster (about 1-3 seconds)

4. **Semantic clarity**
   - `/kilacraft plugins default Hello UUID` = "Answer 'hello' with default personality"
   - Not "Help me execute certain tasks"

---

## Command Format and Parameters

### Basic Format

```
/kilacraft plugins <personality_name> <message_content> <player_uuid> [callback_command...]
```

### Parameter Details

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `<personality_name>` | String | ✅ | Personality name defined in `personalities.yml` |
| `<message_content>` | String | ✅ | Message to send to AI (supports spaces) |
| `<player_uuid>` | UUID | ✅ | Target player's UUID (standard format) |
| `[callback_command...]` | String | ❌ | Command to auto-execute after AI completion (supports `{response}` placeholder) |

### Important Features

- 🔒 **Console execution only**: Players cannot use this command directly (prevents abuse)
- 🌐 **Independent history records**: Each `UUID_personality` combination has independent history, non-interfering
- ⏱️ **Dedicated cooldown**: Uses `plugins_cooldown_seconds` configuration (default 3 seconds)
- 🌍 **World restriction check**: If target player is online, checks if in prohibited worlds
- 💬 **Callback command support**: Auto-executes specified command after AI completion, `{response}` replaced with actual reply

---

## Callback Mechanism Details

### Callback Workflow

```
Third-party plugin triggers event
    ↓
Execute console command: /kilacraft plugins <personality> <question> <playerUUID> [callback_command]
    ↓
Kilacraft-AI generates response with specified personality (async, 2-5 seconds)
    ↓
Auto-execute callback command after AI completion (as console)
    ↓
Your plugin receives callback command, processes AI response
    ↓
Cache immediately deleted (one-time consumption)
```

### Callback Command Format

**Callback command with placeholder:**
```
myplugin handle_ai {response} player_name
```

**Automatically replaced during execution:**
```
myplugin handle_ai "Hehe~ Server rules are simple! ✨..." player_name
```

### Callback Command Parsing Considerations

If your callback command parameters contain spaces, parse correctly:

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("myplugin")) {
        return false;
    }
    
    if (args.length == 0 || !args[0].equals("handle_ai")) {
        return false;
    }
    
    // Last parameter is player name
    String playerName = args[args.length - 1];
    
    // All middle parameters are AI response (merge)
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
```

---

## Callback Method Optimization Best Practices

### ⚠️ Key Optimization: Avoid Blocking Main Thread

**Problem:**
When third-party plugins execute time-consuming operations in callback commands, they block Minecraft main thread, causing server lag.

**Solution:**
**Return immediately** in callback command handler, then execute business logic asynchronously.

### ✅ Recommended Callback Handling Method

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("myplugin") || args.length == 0) {
        return false;
    }
    
    if (args[0].equals("handle_ai")) {
        // Parse parameters
        String playerName = args[args.length - 1];
        StringBuilder responseBuilder = new StringBuilder();
        for (int i = 1; i < args.length - 1; i++) {
            if (i > 1) responseBuilder.append(" ");
            responseBuilder.append(args[i]);
        }
        String aiResponse = responseBuilder.toString();
        
        // ✅ Key: Return immediately, tell Bukkit command completed
        // This won't block main thread
        
        // Execute business logic asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            // Execute time-consuming operations here
            // For example: database queries, API calls, complex calculations
            
            // If need to send message to player, switch back to main thread
            Bukkit.getScheduler().runTask(this, () -> {
                Player player = Bukkit.getPlayer(playerName);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§e[NPC Fox] §f" + aiResponse);
                }
            });
        });
        
        // ✅ Return true immediately, indicating command successfully processed
        return true;
    }
    
    return false;
}
```

### ❌ Not Recommended Approach

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args[0].equals("handle_ai")) {
        // ❌ Wrong: Execute time-consuming operation on main thread
        Thread.sleep(5000);  // Simulate time-consuming operation
        
        // ❌ This blocks main thread for 5 seconds, causing server lag!
        Player player = Bukkit.getPlayer(playerName);
        player.sendMessage("§e[NPC Fox] §f" + aiResponse);
        
        return true;
    }
    return false;
}
```

### Why Do This?

1. **Kilacraft-AI's timeout protection only monitors main thread command execution time**
   ```yaml
   plugin_command:
     callback_timeout_seconds: 3  # Monitor main thread command execution time
   ```

2. **If callback command executes over 3 seconds on main thread**
   - Kilacraft-AI will forcibly interrupt command execution
   - Log warning: `[WARN] Callback command execution timeout (3s), forcibly interrupted`
   - But this **won't** interrupt async tasks you've already started

3. **Advantages of async processing**
   - ✅ Don't block main thread, server runs smoothly
   - ✅ Can execute any time-consuming business logic
   - ✅ Kilacraft-AI's timeout protection won't affect your async tasks
   - ✅ Need to switch back to main thread at appropriate time to send messages

### Complete Async Processing Example

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("myplugin") || args.length == 0) {
        return false;
    }
    
    if (args[0].equals("handle_ai")) {
        // 1. Quickly parse parameters (on main thread)
        String playerName = args[args.length - 1];
        StringBuilder responseBuilder = new StringBuilder();
        for (int i = 1; i < args.length - 1; i++) {
            if (i > 1) responseBuilder.append(" ");
            responseBuilder.append(args[i]);
        }
        String aiResponse = responseBuilder.toString();
        
        // 2. Return immediately, don't block main thread
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // 3. Execute time-consuming operations asynchronously
                // Example: Query database for extra player info
                String extraInfo = queryDatabase(playerName);
                
                // Example: Call external API
                String translated = callTranslationAPI(aiResponse);
                
                // 4. Prepare final message
                String finalMessage = String.format("§e[NPC Fox] §f%s\n§7Extra Info: %s", 
                    translated, extraInfo);
                
                // 5. Switch back to main thread to send message
                Bukkit.getScheduler().runTask(this, () -> {
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(finalMessage);
                        
                        // Can play sound effects, particle effects, etc.
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                });
                
            } catch (Exception e) {
                // 6. Exception handling (in async thread)
                getLogger().severe("Error processing AI response: " + e.getMessage());
                e.printStackTrace();
                
                // Notify player of error (switch back to main thread)
                Bukkit.getScheduler().runTask(this, () -> {
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§cSorry, an error occurred while processing your request. Please try again later.");
                    }
                });
            }
        });
        
        // 7. Return true immediately
        return true;
    }
    
    return false;
}

// Simulate database query
private String queryDatabase(String playerName) {
    // Time-consuming operation here, executed in async thread
    try {
        Thread.sleep(1000);  // Simulate database query
        return "Player Level: 50";
    } catch (InterruptedException e) {
        return "Unknown";
    }
}

// Simulate API call
private String callTranslationAPI(String text) {
    // Time-consuming operation here, executed in async thread
    try {
        Thread.sleep(500);  // Simulate API call
        return text;  // Should actually call translation API
    } catch (InterruptedException e) {
        return text;
    }
}
```

---

## Complete Integration Examples

### Scenario: MythicMobs NPC Intelligent Dialogue

Assume you want to develop a plugin allowing players to ask about server rules through NPCs.

#### Step 1: Define Personalities (`personalities.yml`)

```yaml
# Common prompt (shared by all personalities)
common_prompt: "You are an NPC on a Minecraft server, conversing with player {player}."

# Fox personality
Fox: |
  You are a clever fox NPC who speaks playfully and cutely.
  Like to end sentences with "~", often use emojis.
  Well-versed in server rules, answer player questions in interesting ways.

# Strict Teacher personality
Strict Teacher: |
  You are a strict Minecraft teacher with high standards for students.
  Speak concisely and directly, but patiently answer questions.
  Focus on teaching game mechanics, redstone circuits, and building techniques.

# Adventure Partner personality
Adventure Partner: |
  You are player {player}'s loyal adventure partner, cheerful and humorous.
  Love sharing adventure stories, providing combat advice, always encouraging players to explore bravely.
```

#### Step 2: Call in Your Plugin

**Method A: Java Code Call (Recommended for Java plugins)**

```java
@EventHandler
public void onNPCInteract(PlayerInteractEntityEvent event) {
    Player player = event.getPlayer();
    Entity npc = event.getRightClicked();
    
    // Check if it's a specific NPC
    if (npc.getCustomName() != null && npc.getCustomName().equals("Fox NPC")) {
        // Build callback command
        String callbackCommand = String.format(
            "myplugin handle_ai %s %s", 
            "{response}",  // Placeholder, will be replaced with actual response
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

**Method B: Configuration File Call (Recommended for config-driven plugins like MythicMobs)**

```yaml
# MythicMobs skill configuration example
fox_npc_skill:
  Skills:
  - cmd{c="kilacraft plugins Fox What are the server rules <caster.uuid> myplugin handle_ai {response} <caster.name>"} @self
```

> 💡 **Tip**: Method B suits plugins supporting command execution in configuration (like MythicMobs, Skript, etc.), no Java coding required.

#### Step 3: Implement Callback Command Handler

Refer to complete example in "Callback Method Optimization Best Practices" section above.

#### Step 4: Register Command in plugin.yml

```yaml
commands:
  myplugin:
    description: MyPlugin main command
    usage: /<command>
```

### Actual Effect Demonstration

```
Player right-clicks "Fox NPC"
    ↓
Console executes: /kilacraft plugins Fox What are the server rules xxx-uuid myplugin handle_ai {response} player_name
    ↓
AI generates response (async, about 2-5 seconds)
    ↓
After AI completion, auto-execute: /myplugin handle_ai "Hehe~ Server rules are simple! ✨..." player_name
    ↓
Your plugin receives command and displays:
[NPC Fox] Hehe~ Server rules are simple! ✨
    1. No cheating or hacking, fox will catch you! 🦊
    2. Be friendly, no cursing~
    ...
```

---

## Personality System Configuration

### Configuration File Location

`plugins/Kilacraft-AI/personalities.yml`

### Configuration Example

```yaml
# Common prompt (shared by all personalities)
common_prompt: "You are an NPC on a Minecraft server, conversing with player {player}."

# Custom personalities
Fox: |
  You are a clever fox NPC who speaks playfully and cutely.
  Like to end sentences with "~", often use emojis.
  Well-versed in server rules, answer player questions in interesting ways.

Strict Teacher: |
  You are a strict Minecraft teacher with high standards for students.
  Speak concisely and directly, but patiently answer questions.
  Focus on teaching game mechanics, redstone circuits, and building techniques.

Adventure Partner: |
  You are player {player}'s loyal adventure partner, cheerful and humorous.
  Love sharing adventure stories, providing combat advice, always encouraging players to explore bravely.
```

### Reload Personality Configuration

```
/kilacraft personalities reload
```

### Personality Matching Rules

- Personality name must **exactly match** definition in `personalities.yml`
- Case-sensitive
- Dynamic personality creation not supported, must pre-configure

---

## History Isolation Mechanism

### Isolation Rules

Each `UUID_personality` combination has independent history:

```
Player A + Fox personality → Independent History 1
Player A + Strict Teacher personality → Independent History 2
Player B + Fox personality → Independent History 3
```

### Advantages

1. **Personality consistency**: When same player talks with different personalities, contexts don't mix
2. **Privacy protection**: Different players' conversation histories completely isolated
3. **Flexible switching**: Players can switch personalities anytime without affecting other conversations

### Configuration

History count configured in `config.yml`:

```yaml
settings:
  max_history: 10  # Each UUID_personality combination saves up to 10 rounds of conversation
```

### Clear History

```
# Clear specific player's all history
/kilacraft clear <player_name>

# Player clears own history
/kilacraft clear
```

---

## FAQ

### Q1: Why not recommend using commands without callbacks?

**A:** Commands without callbacks cache AI responses in memory, but there's no standard method to retrieve responses. This approach:
- ❌ No officially supported retrieval method
- ❌ Requires self-developed polling or placeholder mechanisms
- ❌ May cause memory leaks (cache not cleaned)

**Recommended practice:** Always use callback commands, the only officially supported integration method.

### Q2: What if callback command execution times out?

**A:** If you see this log:
```
[WARN] Callback command execution timeout (3s), forcibly interrupted. Command: myplugin handle_ai ...
```

This means:
- ✅ Main thread command execution was interrupted (normal behavior)
- ⚠️ If your plugin uses async processing, async tasks still running
- 💡 This is not an error, expected behavior

**Solutions:**
1. Ensure callback command handler returns immediately (refer to best practices above)
2. Put time-consuming operations in async thread
3. If need to adjust timeout, modify `config.yml`:
   ```yaml
   plugin_command:
     callback_timeout_seconds: 5  # Increase to 5 seconds
   ```

### Q3: How to debug plugin command mode?

**A:** Enable debug mode:
```yaml
# config.yml
settings:
  debug_mode: true
```

Console outputs detailed execution logs including:
- Command received
- Personality loaded
- AI request sent
- Callback command executed

### Q4: Does it support offline players?

**A:** 
- ✅ **Supported**: UUID corresponding player can be offline
- ⚠️ **Limitation**: If player offline, cannot send messages
- 💡 **Suggestion**: Check player online status in callback handler

```java
Player player = Bukkit.getPlayer(playerName);
if (player != null && player.isOnline()) {
    player.sendMessage(aiResponse);
} else {
    // Player offline, can save message to database, send when online
    saveOfflineMessage(playerName, aiResponse);
}
```

### Q5: How to test plugin command mode?

**A:** Can manually test in console:

```
# Test basic functionality
/kilacraft plugins default Hello 069a79f4-44e9-4726-a5be-fca90e38aaf5 myplugin test_callback {response}

# Test different personalities
/kilacraft plugins Fox What are the server rules 069a79f4-44e9-4726-a5be-fca90e38aaf5 myplugin handle_ai {response} player_name
```

---

## Related Documentation

- [Server Owner Guide - Plugin Command Mode Introduction](./Server%20Owner%20Guide#4-plugin-command-mode--personality-system-advanced-features)
- [Personality System Configuration Guide](./Personality%20System%20Guide)
- [Skill SPI Integration Guide](./Skill%20SPI%20Integration%20Guide)
- [System Architecture Details](./System%20Architecture%20Details)

---

> **Last Updated**: 2026-04-19  
> **Applicable Plugin Version**: Kilacraft-AI 1.4.6+
