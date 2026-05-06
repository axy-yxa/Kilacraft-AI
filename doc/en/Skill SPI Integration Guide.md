# Kilacraft-AI - Skill SPI Integration Guide

> **Last Updated**: 2026-05-06  
> **Description**: This document guides plugin developers on how to integrate custom skills into Kilacraft-AI through the Skill SPI interface

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Quick Start (5-Minute Integration)](#3-quick-start-5-minute-integration)
4. [Core Interfaces Explained](#4-core-interfaces-explained)
5. [Skill Development Guidelines](#5-skill-development-guidelines)
6. [Multi-Step Task Data Passing](#6-multi-step-task-data-passing)
7. [Error Isolation and Exception Handling](#7-error-isolation-and-exception-handling)
8. [Permission and Availability Control](#8-permission-and-availability-control)
9. [Security Interceptor (Important)](#9-security-interceptor-important)
10. [Naming Conventions and Conflict Resolution](#10-naming-conventions-and-conflict-resolution)
11. [Configuration Support (Optional)](#11-configuration-support-optional)
12. [Complete Example: Player Stats Query Plugin](#12-complete-example-player-stats-query-plugin)
13. [Development Dependency Configuration](#13-development-dependency-configuration)
14. [Lifecycle and Loading Order](#14-lifecycle-and-loading-order)
15. [FAQ](#15-faq)
16. [API Reference](#16-api-reference)

---

## 1. Overview

Kilacraft-AI uses the **SPI (Service Provider Interface)** mechanism to allow third-party Minecraft plugins to encapsulate their functionality as **Skills**, register them with the AI Agent, enabling the AI assistant to call these features.

### Core Features

- **Zero-coupling integration**: Third-party plugins only need to include `kilacraft-skill-api.jar` (compileOnly) and implement interfaces
- **Auto-discovery**: Based on Bukkit `ServicesManager`, automatically scans and registers on startup, no manual configuration needed
- **Error isolation**: Exceptions from third-party Skills do not affect Kilacraft-AI core processes
- **LLM intent-driven**: AI automatically recognizes user intent and calls corresponding Skills, users don't need to remember commands
- **Multi-step task support**: Data returned by Skills can be referenced by subsequent steps, enabling cross-Skill orchestration

### Use Cases

| Scenario | Examples |
|----------|----------|
| Economy System | Query balance, transfer money, shop purchases |
| Land System | Query land info, create land claims |
| Leaderboards | Query online rankings, wealth rankings |
| RPG System | Query skill levels, quest progress |
| World Management | Query chunk info, teleportation management |

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────┐
│              Third-Party Plugin JAR                │
│  ┌─────────────┐    ┌──────────────────────────┐  │
│  │ MyPlugin    │    │ MyCustomSkill            │  │
│  │ implements  │───>│ implements Skill          │  │
│  │ SkillProvider│    │                          │  │
│  └──────┬──────┘    └──────────────────────────┘  │
│         │ onEnable() registers SkillProvider       │
└─────────┼─────────────────────────────────────────┘
          │ Bukkit ServicesManager
          ▼
┌──────────────────────────────────────────────────┐
│              Kilacraft-AI Main Plugin              │
│  ┌─────────────┐    ┌──────────────────────────┐  │
│  │ SkillRegistry│───>│ SkillManager             │  │
│  │ Auto-discover│    │ Register/Execute/Error   │  │
│  └─────────────┘    └───────────┬──────────────┘  │
│                                 │                  │
│                     ┌───────────▼──────────────┐  │
│                     │ LLM Intent Recognition    │  │
│                     │ User Message → Skill Call │  │
│                     └──────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

### Data Flow

```
User Chat Message
    → ChatListener intercepts
    → SkillIntentRecognizer (LLM intent recognition)
    → SkillManager.executeSkillByIntent()
    → Skill.execute(context)
    → SkillResult (message + data)
    → Return to user / Pass to next step
```

---

## 3. Quick Start (5-Minute Integration)

### Step 1: Add Development Dependencies

Add to your plugin project's `pom.xml`:

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>1.4.3</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-1.4.3.jar</systemPath>
</dependency>
```

> **Note**:
> 1. This dependency is `compileOnly`, will not be packaged into your plugin JAR
> 2. **JAR filename includes version number**, adjust the filename in `<systemPath>` according to the actual downloaded file (e.g., `Kilacraft-Skill-API-1.4.3.jar`)

### Step 2: Implement Skill Interface

```java
package com.example.myplugin.skills;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HelloWorldSkill implements Skill {

    @Override
    public String getName() {
        return "hello_world";  // Globally unique identifier
    }

    @Override
    public String getDescription() {
        return "Greet the player. Returns a greeting message.";
    }

    @Override
    public Map<String, String> getActions() {
        return Map.of(
            "greet", "Send a greeting to the specified player"
        );
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        String name = player != null ? player.getName() : "Stranger";
        return CompletableFuture.completedFuture(
            SkillResult.success("Hello, " + name + "! Welcome to use AI Assistant!")
        );
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return true;  // Always available
    }
}
```

### Step 3: Register SkillProvider

In your plugin main class:

```java
package com.example.myplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.spi.SkillProvider;
import com.example.myplugin.skills.HelloWorldSkill;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MyPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // Register SkillProvider in onEnable
        // Kilacraft-AI will automatically scan and discover after server starts
        getServer().getServicesManager().register(
            SkillProvider.class,
            this,
            this,
            org.bukkit.plugin.ServicePriority.Normal
        );
        getLogger().info("SkillProvider registered, waiting for Kilacraft-AI discovery...");
    }

    @Override
    public void onDisable() {
        // Bukkit will automatically unregister from ServicesManager
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new HelloWorldSkill());
    }
}
```

### Step 4: Configure plugin.yml Dependencies

```yaml
name: MyPlugin
version: 1.0
main: com.example.myplugin.MyPlugin
api-version: '1.21'
# Declare soft dependency to ensure Kilacraft-AI is loaded
softdepend:
  - Kilacraft-AI
```

### Done!

Deploy both JARs to the server's `plugins/` directory and start the server. Kilacraft-AI will automatically scan and register your Skill after startup, console will output:

```
[Kilacraft-AI] Discovered and registered third-party skill: hello_world (from MyPlugin)
```

---

## 4. Core Interfaces Explained

### 4.1 Skill Interface

```java
public interface Skill {
    /** Skill name (globally unique identifier) */
    String getName();

    /** Skill description (used for LLM intent recognition, very important) */
    String getDescription();

    /** Action list: key=action name, value=action description (for LLM to recognize sub-intents) */
    default Map<String, String> getActions() {
        return Collections.emptyMap();
    }

    /** Additional hint information (usage examples, precautions, etc.) */
    default List<String> getHints() {
        return Collections.emptyList();
    }

    /** Execute skill (core method) */
    CompletableFuture<SkillResult> execute(SkillContext context);

    /** Check if skill is available in current context */
    default boolean isAvailable(SkillContext context) {
        return true;
    }
}
```

### 4.2 SkillProvider Interface

```java
public interface SkillProvider {
    /** Return all Skill instances provided by this Provider */
    List<Skill> getSkills();
}
```

**Key Points:**
- Your plugin main class implements this interface
- `getSkills()` should not return `null` (return empty list when no Skills)
- Each Skill uses independent instance (avoid sharing mutable state)

### 4.3 SkillContext Context

```java
public class SkillContext {
    /** Current player (may be null, e.g., console call) */
    Player getPlayer();

    /** Action name recognized by LLM */
    String getAction();

    /** Entity parameters extracted by LLM (key-value form) */
    Map<String, String> getEntities();

    /** Get specific entity parameter */
    String getEntity(String key);
}
```

**Field Descriptions:**

| Field | Description | Example |
|-------|-------------|---------|
| `player` | Player who triggered AI dialogue | Dialogue player instance |
| `action` | Action recognized by LLM | `"query_price"`, `"greet"` |
| `entities` | Parameters extracted by LLM | `{"item": "diamond", "quantity": "10"}` |

### 4.4 SkillResult Result

```java
public class SkillResult {
    /** Whether successful */
    boolean isSuccess();

    /** Message content (displayed to user or passed to LLM) */
    String getMessage();

    /** Data object (conventionally Map<String, Object>, for multi-step task passing) */
    Object getData();

    /** Convenience method: get data Map */
    Map<String, Object> getDataMap();

    // Static factory methods
    static SkillResult success(String message);
    static SkillResult success(String message, Object data);
    static SkillResult failure(String message);
    static SkillResult failure(String message, Throwable error);
}
```

---

## 5. Skill Development Guidelines

### 5.1 Naming Conventions

| Item | Convention | Example |
|------|------------|---------|
| Skill name (name) | Lowercase letters + underscores, `plugin_prefix_function` | `economy_balance`, `mcmmo_stats` |
| Action name | Lowercase letters + underscores, `verb_noun` | `query_balance`, `transfer_money` |
| Entity key names | Lowercase letters + underscores | `item_name`, `player_name`, `quantity` |
| Data field names | Lowercase letters + underscores | `health`, `max_health`, `food_level` |

**Important**: Skill names must be globally unique. If conflicting with built-in Skills, third-party Skills will be skipped (built-in Skills take priority). It's recommended to use `plugin_name_` prefix to avoid conflicts.

### 5.2 Description Writing Guidelines

The return value of `getDescription()` is the **core basis for LLM intent recognition**, directly determining whether AI can correctly match your Skill. Writing principles:

1. **Clearly describe functionality**: Explain what this Skill does in one sentence
2. **Include keywords**: Synonyms that users might use
3. **Describe returned data fields** (if multi-step task requirements exist)

```java
// Good description
"Query player's MCMMO skill levels. Supports querying current level and experience of specific skills.
The returned data contains skill_name, level, xp fields for multi-step task parameter passing."

// Bad description
"Query information"  // Too vague, LLM cannot distinguish
```

### 5.3 Action Design Principles

Each Skill can contain multiple Actions, automatically selected by LLM based on user input:

```java
@Override
public Map<String, String> getActions() {
    return Map.of(
        "query_balance", "Query player's balance. Returned data contains balance field.",
        "transfer", "Transfer money to specified player. Requires parameters: target_player (target player name), amount (amount)."
    );
}
```

**Action descriptions are also crucial** — LLM decides which action to call and which parameters to extract based on descriptions.

### 5.4 Entity Parameter Extraction

LLM extracts parameters from user input and passes them as `Map<String, String>` to `context.getEntities()`. You need to declare required parameters in action descriptions:

```java
// Action description example
"Purchase items from market. Requires parameters: item (item name, supports Chinese and English), quantity (purchase quantity, defaults to 1)."
```

When user says: "Help me buy 10 diamonds", LLM will extract:
```json
{
    "item": "diamond",
    "quantity": "10"
}
```

Get in execute:
```java
String item = context.getEntity("item");       // "diamond"
String quantity = context.getEntity("quantity"); // "10"
```

> **Note**: Entity values are always `String` type. If you need numbers, convert yourself and handle exceptions.

---

## 6. Multi-Step Task Data Passing

### 6.1 Overview

Kilacraft-AI supports multi-step task orchestration — when a user request requires multiple Skills to execute in sequence, the return data from the previous Skill can be referenced by the next Skill.

### 6.2 Three-Party Agreement

This is a **three-party agreement**: Developer writes code and descriptions → LLM reads descriptions to generate placeholders → Framework parses placeholders.

**What Skill Developers need to do:**
1. Return `Map<String, Object>` type data in `SkillResult.success(message, data)`
2. Explain which data fields are returned in Skill's `getDescription()` or action description

### 6.3 Example

**Step 1: Query Item Price Skill**

```java
private CompletableFuture<SkillResult> queryPrice(SkillContext context) {
    String itemName = context.getEntity("item");
    double price = getPrice(itemName);

    Map<String, Object> data = new HashMap<>();
    data.put("item_name", itemName);
    data.put("price", price);
    data.put("stock", getStock(itemName));

    return CompletableFuture.completedFuture(
        SkillResult.success("Price of " + itemName + " is $" + price, data)
    );
}
```

Description should state: `"Returned data contains item_name, price, stock fields"`

**Step 2: LLM Automatic Orchestration**

When user says "Check diamond price for me, if not over 100 then help me buy 10", LLM will generate:

```json
{
    "steps": [
        {
            "id": "step_1",
            "skill": "market_query",
            "action": "query_price",
            "entities": {"item": "diamond"}
        },
        {
            "id": "step_2",
            "skill": "market_buy",
            "action": "buy_item",
            "entities": {
                "item": "{step_1.item_name}",
                "price": "{step_1.price}",
                "quantity": "10"
            }
        }
    ]
}
```

**Step 3: Framework Automatically Parses Placeholders**

TaskExecutor will automatically replace `{step_1.item_name}` with the value of `data.get("item_name")` returned from step 1.

### 6.4 Placeholder Format

#### Basic Format (Single Field)

```
{step_<step_id>.<field_name>}
```

- Step ID is generated by LLM (e.g., `step_1`, `step_2`)
- Field name corresponds to key in `SkillResult.data` Map
- Only single-level references supported (no nested like `{step_1.data.xxx}`)

**Example**:
```json
{
    "item": "{step_1.item_name}",
    "price": "{step_1.price}"
}
```

#### Advanced Format (Array Index Access)

When a Skill returns data containing a List, you can access array elements by index:

```
{step_<step_id>.<array_field>[<index>].<sub_field>}
```

**Examples**:
```json
{
    "warp_name": "{step_1.warps[0].warp_name}",
    "home_name": "{step_1.homes[2].home_name}"
}
```

**Use Cases**:
- Previous step returns list data (e.g., home list, warp list, product list)
- Subsequent steps need to reference fields from specific elements in the list
- LLM autonomously chooses index based on user intent (e.g., "first", "last", "random")

**Skill Developer Guidelines**:
1. Clearly describe the returned list structure in action descriptions
2. Example of returned data format:

```java
Map<String, Object> data = new HashMap<>();
data.put("warps", List.of(
    Map.of(
        "warp_name", "spawn",
        "world", "world",
        "x", 100, "y", 64, "z", 200
    ),
    Map.of(
        "warp_name", "market",
        "world", "world_nether",
        "x", -50, "y", 70, "z", 150
    )
));
```

#### Path Resolution Rules

Placeholder path resolution supports:
- **Regular field access**: `{step_1.item_name}` → `data.get("item_name")`
- **Array element access**: `{step_1.warps[0]}` → `((List)data.get("warps")).get(0)`
- **Nested access**: `{step_1.warps[0].warp_name}` → `((Map)((List)data.get("warps")).get(0)).get("warp_name")`

> **Note**: Currently supports single-level array indexing (e.g., `list[0].field`), does not support multi-level nested arrays (e.g., `list[0].sublist[1].field`).

---

## 7. Error Isolation and Exception Handling

### 7.1 Automatic Isolation

Kilacraft-AI's `SkillManager` performs complete error isolation for third-party Skills:

```java
// SkillManager internal logic (simplified)
try {
    return skill.execute(context).exceptionally(ex -> {
        // Async exception capture
        plugin.getLogger().log(Level.SEVERE, "Skill execution exception: " + skillName, ex);
        return SkillResult.failure("Skill execution error, please contact administrator");
    });
} catch (Exception e) {
    // Sync exception capture
    plugin.getLogger().log(Level.SEVERE, "Skill execution failed: " + skillName, e);
    return SkillResult.failure("Skill execution error, please contact administrator");
}
```

### 7.2 Development Recommendations

Although Kilacraft-AI provides fallback, you should still handle your own exceptions properly:

```java
@Override
public CompletableFuture<SkillResult> execute(SkillContext context) {
    try {
        // Parameter validation
        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure("Please specify item name")
            );
        }

        // Business logic
        return doSomething(itemName);

    } catch (Exception e) {
        // Return meaningful error message (will be displayed to user)
        return CompletableFuture.completedFuture(
            SkillResult.failure("Query failed: " + e.getMessage())
        );
    }
}
```

### 7.3 Precautions

- **Don't throw uncaught RuntimeException**: Although isolated, user experience is poor
- **Failure messages should be user-friendly**: Users will see this message
- **Handle exceptions in async operations too**: Exceptions in CompletableFuture chain should also use `.exceptionally()`
- **Exceptions in isAvailable() are also isolated**: Don't execute complex logic in this method

---

## 8. Permission and Availability Control

### 8.1 isAvailable() Check

```java
@Override
public boolean isAvailable(SkillContext context) {
    // Example: Check if player has permission
    Player player = context.getPlayer();
    if (player == null) {
        return false;  // Console not available
    }
    return player.hasPermission("myplugin.skill.use");
}
```

**Call timing**: This method is called before each Skill execution.

**Effect of returning false**: User receives "Sorry, this feature is temporarily unavailable" message.

### 8.2 Permission System

Kilacraft-AI's permission system is independent and doesn't affect your own permission checks. You have two control methods:

1. **Check in `isAvailable()`**: Suitable for simple available/unavailable judgments
2. **Check in `execute()`**: Suitable for scenarios requiring specific reasons

```java
@Override
public CompletableFuture<SkillResult> execute(SkillContext context) {
    Player player = context.getPlayer();
    if (!player.hasPermission("economy.transfer")) {
        return CompletableFuture.completedFuture(
            SkillResult.failure("You don't have transfer permission")
        );
    }
    // ... normal logic
}
```

---

## 9. Security Interceptor (Important)

Kilacraft-AI includes a **non-cooperative security filter** (SkillSecurityFilter) that automatically runs before every Skill execution, protecting player data from malicious Skill access or tampering.

### 9.1 Core Mechanism: Value Scanning + Sanitization

The security interceptor workflow:

```
Before Skill Execution
    → SkillSecurityFilter.sanitize(skillName, action, context)
    → Iterate through all Values in context.entities
    → Check: Does Value match an online player name?
        ├─ Is current player's own name → ✅ Allow
        ├─ Is another online player + in whitelist → ✅ Allow
        └─ Is another online player + not in whitelist → 🔄 Sanitize (replace with current player name)
    → Return sanitized entities to Skill.execute()
```

**Key Features:**
- **Non-cooperative**: Does not rely on Skill declaring parameter names, scans all Values directly
- **Sanitize instead of block**: When validation fails, replaces with current player name, Skill continues execution
- **Always runs**: Cannot be skipped or bypassed

### 9.2 What Does This Mean for Your Skill?

#### Scenario 1: Your Skill Only Operates on Current Player

```java
// Player queries their own status
// entities: {} or {"field": "health"}
// No other player names involved → Security interceptor allows directly
```

**No action needed**, develop normally.

#### Scenario 2: Your Skill Needs to Operate on Other Players (e.g., Transfer)

If your Skill needs to operate on other players (e.g., economy system transfer), you need to add it to the whitelist:

```yaml
# config.yml
security:
  player_isolation:
    allowed_actions:
      - "economy.transfer"      # Skill-level whitelist (all actions)
      - "economy.send_payment"  # Or action-level whitelist
```

**Note:**
- Whitelist is configured by server owner in config.yml, Skill developers cannot decide themselves
- Skill name must exactly match `getName()` return value (case-sensitive)
- Format: `"skill_name.action_name"` (action-level) or `"skill_name"` (skill-level)

#### Scenario 3: Your Skill Tries to Operate on Other Players but Not in Whitelist

```java
// Suppose your Skill tries to transfer to "Hub", but not in whitelist
// Original entities: {"target_player": "Hub", "amount": "100"}
// Sanitized entities: {"target_player": "current_player_name", "amount": "100"}
// Your Skill actually executes with target_player replaced by current player
```

In this case, your Skill will "think" the player is transferring to themselves, **no error thrown, but behavior is altered**. This is part of the security design — malicious Skills cannot bypass it.

### 9.3 Whitelist Admission Guidelines

If your Skill truly needs to operate on other players, when requesting server owner to add to whitelist:

1. **Clear permission boundaries**: Explain the permission boundaries of the operation (e.g., transfers constrained by economy system permissions)
2. **Controllable risk**: Explain why operating on other players is safe
3. **Permission inheritance**: Prefer executing as the player, inheriting server's native permissions (like CommandSkill)

**Examples of Built-in Whitelisted Skills:**
- `cmi.send_tp_request`: CMI teleport request (TPA, requires target's consent)
- `AFKTask.create_task`: AFK tasks (can monitor other players)
- `command.execute_command`: Command execution (executes as player, permission boundary = player's own)

### 9.4 Development Notes

- **Do not attempt to bypass the security interceptor**: It always runs and cannot be skipped
- **Do not embed player names in entities**: Formats like `"msg Hub hello"` won't be correctly recognized and cannot be parsed by Skills
- **Player name format**: Minecraft player name regex `^[a-zA-Z0-9_]{1,16}$`, values not matching this format are skipped immediately
- **Online player cache**: Maintained based on PlayerJoin/Quit events, async thread-safe reads

---

## 10. Naming Conventions and Conflict Resolution

### 10.1 Namespace Recommendations

To avoid conflicts with other plugins' Skills, use the following format:

```
<your_plugin_name_lowercase>_<function_description>
```

Examples:
- `mcmmo_query_level` — McMMO level query
- `essentials_balance` — Essentials balance query
- `towny_query_info` — Towny land query

### 10.2 Conflict Resolution Rules

When third-party Skill names conflict with already registered Skills (built-in or other third-party):

- **Built-in Skills take priority**: Cannot be overridden by third-party
- **First come, first served**: When multiple third-party Skills have same name, first discovered is kept
- **Console warning**: Conflicts output Warning in logs

```
[Kilacraft-AI] Skipped third-party skill 'market_query' (from SomePlugin): Name conflicts with already registered skill
```

---

## 10. Configuration Support (Optional)

Kilacraft-AI built-in Skills use `SkillConfig` for configuration management (description, actions, hints, etc. are all configurable). But third-party Skills **don't need** to follow this mechanism, you can use your own configuration approach.

If you want third-party Skill descriptions to support hot reload, suggest:

```java
public class MySkill implements Skill {
    private final MyPlugin plugin;

    public MySkill(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        // Read from your own config.yml, supports hot reload
        return plugin.getConfig().getString("skill.description", "Default description");
    }

    @Override
    public Map<String, String> getActions() {
        // Read actions from configuration
        Map<String, String> actions = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skill.actions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                actions.put(key, section.getString(key));
            }
        }
        return actions;
    }
}
```

Then pass plugin instance in `getSkills()`:

```java
@Override
public List<Skill> getSkills() {
    return List.of(new MySkill(this));
}
```

---

## 11. Complete Example: Player Stats Query Plugin

Here's a complete third-party plugin example that queries player health, hunger, and experience level.

### Skill Implementation

```java
package com.example.statsplugin.skills;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlayerStatsSkill implements Skill {

    private static final String NAME = "player_stats_query";
    private static final String DESCRIPTION =
        "Query player status information, including health, hunger, and experience level. " +
        "Returned data contains health, max_health, food_level, level, total_exp fields, " +
        "for multi-step task parameter passing.";

    private static final Map<String, String> ACTIONS = Map.of(
        "query_health", "Query player's current health. Returned data contains health and max_health fields.",
        "query_food", "Query player's current hunger. Returned data contains food_level field.",
        "query_experience", "Query player's current experience level. Returned data contains level and total_exp fields."
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, String> getActions() {
        return ACTIONS;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("Please specify player"));
        }

        String action = context.getAction();
        try {
            return switch (action) {
                case "query_health" -> queryHealth(player);
                case "query_food" -> queryFood(player);
                case "query_experience" -> queryExperience(player);
                default -> CompletableFuture.completedFuture(
                    SkillResult.failure("Unknown action: " + action));
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                SkillResult.failure("Query failed: " + e.getMessage()));
        }
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return context.getPlayer() != null;
    }

    private CompletableFuture<SkillResult> queryHealth(Player player) {
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        Map<String, Object> data = new HashMap<>();
        data.put("health", health);
        data.put("max_health", maxHealth);
        return CompletableFuture.completedFuture(
            SkillResult.success(
                String.format("Health: %.1f/%.1f", health, maxHealth), data));
    }

    private CompletableFuture<SkillResult> queryFood(Player player) {
        int foodLevel = player.getFoodLevel();
        Map<String, Object> data = new HashMap<>();
        data.put("food_level", foodLevel);
        return CompletableFuture.completedFuture(
            SkillResult.success("Hunger: " + foodLevel + "/20", data));
    }

    private CompletableFuture<SkillResult> queryExperience(Player player) {
        int level = player.getLevel();
        int totalExp = player.getTotalExperience();
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("total_exp", totalExp);
        return CompletableFuture.completedFuture(
            SkillResult.success(
                String.format("Level: %d, Total XP: %d", level, totalExp), data));
    }
}
```

### Plugin Main Class

```java
package com.example.statsplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.spi.SkillProvider;
import com.example.statsplugin.skills.PlayerStatsSkill;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class StatsPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // Register SkillProvider to Bukkit ServicesManager (4 parameters)
        getServer().getServicesManager().register(
            SkillProvider.class,
            this,
            this,
            org.bukkit.plugin.ServicePriority.Normal
        );
        getLogger().info("StatsPlugin has registered SkillProvider");
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new PlayerStatsSkill());
    }
}
```

### plugin.yml

```yaml
name: StatsPlugin
version: 1.0
main: com.example.statsplugin.StatsPlugin
api-version: '1.21'
softdepend:
  - Kilacraft-AI
```

### 12. Complete Example: Command Execution Plugin

Following is a complete example showing how CommandSkill works. Note: CommandSkill is a built-in skill of Kilacraft-AI, available for all users.

**Capability Boundaries**:
- Bukkit.dispatchCommand() returns boolean (command recognition/execution), cannot capture command output
- Command output is sent directly to player, AI only knows "command executed"
- Therefore CommandSkill only applies to execution-type commands (e.g., /back, /spawn), query commands should use dedicated Skills

**User Interaction Example**:
```
User: Teleport me to my death point
AI: Executed command: /back
  (CMI/Essentials directly sends teleport result to player)
```

### User Interaction Example

After installation, users can directly say to AI in chat:

```
User: What is my health?
AI: Your health is 18.5/20.0

User: What about my current experience level and hunger?
AI: Your level is 15, total XP: 3200. Hunger: 18/20.
```

---

## 12. Development Dependency Configuration

### Maven

```xml
<dependencies>
    <!-- Kilacraft-AI Skill API (compileOnly) -->
    <dependency>
        <groupId>com.zm.kilacraftAI</groupId>
        <artifactId>kilacraft-skill-api</artifactId>
        <version>1.4.3</version>
        <scope>system</scope>
        <systemPath>${project.basedir}/libs/kilacraft-skill-api.jar</systemPath>
    </dependency>

    <!-- Spigot API -->
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>1.21-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    // Kilacraft-AI Skill API (compileOnly)
    implementation files('libs/kilacraft-skill-api.jar')

    // Spigot API
    compileOnly 'org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT'
}
```

### About kilacraft-skill-api.jar

This JAR contains compiled classes for third-party developers to reference during compilation:

```
com.zm.kilacraftAI.skills.framework.Skill
com.zm.kilacraftAI.skills.framework.SkillContext
com.zm.kilacraftAI.skills.framework.SkillResult
com.zm.kilacraftAI.skills.framework.SkillIntent
com.zm.kilacraftAI.skills.framework.spi.SkillProvider
com.zm.kilacraftAI.skills.framework.spi.SkillRegistry
```

> **This JAR should not and cannot be packaged into your plugin**. Provided by Kilacraft-AI main plugin at runtime.

---

## 13. Lifecycle and Loading Order

```
Server Startup
    │
    ├── Third-party plugin onEnable()
    │   └── Bukkit.getServicesManager().register(SkillProvider.class, this, ...)
    │
    ├── Kilacraft-AI onEnable()
    │   ├── Initialize SkillManager
    │   ├── Register built-in Skills (MarketQuerySkill, GenericBukkitAPISkill)
    │   └── Schedule delayed task (after 20 ticks)
    │
    ├── Server startup complete
    │
    └── After 20 tick delay ── SkillRegistry.discoverAndRegister()
        ├── Scan SkillProviders in ServicesManager
        ├── Iterate through each Provider's getSkills()
        ├── Check name conflicts
        └── Register to SkillManager

User Chat
    └── ChatListener → SkillIntentRecognizer → SkillManager.executeSkillByIntent()
        ├── isAvailable() check (with error isolation)
        └── Skill.execute(context) (with error isolation)

Server Shutdown
    └── Bukkit automatically unregisters all registrations in ServicesManager
```

**Why delay 20 ticks?**  
Ensures all third-party plugins have completed SkillProvider registration in `onEnable()`, avoiding missed scans due to loading order differences.

---

## 14. FAQ

### Q: Does my plugin load first or does Kilacraft-AI load first?

A: Uncertain, depends on Bukkit's plugin loading order (usually alphabetically). But regardless of loading order, auto-discovery mechanism works correctly:
- If your plugin loads first: You register SkillProvider → Kilacraft-AI discovers it in delayed scan
- If Kilacraft-AI loads first: Kilacraft-AI registers built-in Skills → Discovers your registered SkillProvider in delayed scan

### Q: Does it support hot reload?

A: No. Auto-discovery mechanism executes only once at server startup. After installing or updating third-party Skill plugins, please restart the server.

### Q: Can Skill's execute method perform time-consuming operations?

A: Yes. `execute()` returns `CompletableFuture`, supporting async operations. But note:
- Don't execute blocking operations on main thread (database queries, network requests, etc.), should use `CompletableFuture.supplyAsync()`
- Bukkit API calls (like `player.getHealth()`) must be on main thread, calling in async requires `Bukkit.getScheduler().runTask()`

### Q: Can one plugin register multiple Skills?

A: Yes. Just return multiple Skill instances in `getSkills()`:

```java
@Override
public List<Skill> getSkills() {
    return List.of(
        new QuerySkill(),
        new BuySkill(),
        new SellSkill()
    );
}
```

### Q: Can console use Skills?

A: Depends on your `isAvailable()` and `execute()` logic. `context.getPlayer()` is `null` when called from console, you need to handle this situation in code.

### Q: Can Skill names use Chinese characters?

A: Not recommended. Please use lowercase English + underscore format to ensure compatibility and readability.

### Q: How to debug my Skill?

1. Enable Debug mode in Kilacraft-AI's `config.yml`
2. Check console logs with `[DEBUG]` prefix
3. Verify Skill's `getName()`, `getDescription()`, `getActions()` return correct values
4. Confirm Skill instances in `getSkills()` are properly initialized

### Q: In multi-step tasks, how can my Skill be correctly orchestrated by LLM?

The key is that your **description** and **action descriptions** must clearly explain returned data fields. LLM decides based on these descriptions:
1. Whether multi-steps are needed
2. How to reference previous steps' data in subsequent steps

For example, clearly state in description: `"Returned data contains item_name, price fields"`, LLM knows it can use `{step_1.item_name}` and `{step_1.price}` in subsequent steps.

---

## 15. API Reference

### Skill Interface Methods

| Method | Return Type | Required | Description |
|--------|-------------|----------|-------------|
| `getName()` | `String` | Yes | Skill unique identifier |
| `getDescription()` | `String` | Yes | Description for LLM recognition |
| `getActions()` | `Map<String, String>` | No | Action mapping, default empty |
| `getHints()` | `List<String>` | No | Hint information, default empty |
| `execute(SkillContext)` | `CompletableFuture<SkillResult>` | Yes | Core execution logic |
| `isAvailable(SkillContext)` | `boolean` | No | Availability check, default true |

### SkillProvider Interface Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getSkills()` | `List<Skill>` | Return all Skill instances |

### SkillContext Fields

| Field | Type | Description |
|-------|------|-------------|
| `player` | `Player` | Current player (can be null) |
| `action` | `String` | Action recognized by LLM |
| `entities` | `Map<String, String>` | Parameters extracted by LLM |

### SkillResult Static Factory Methods

| Method | Description |
|--------|-------------|
| `success(String message)` | Success, no data |
| `success(String message, Object data)` | Success, with data |
| `failure(String message)` | Failure |
| `failure(String message, Throwable error)` | Failure, with exception |

### SkillResult Instance Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `isSuccess()` | `boolean` | Whether successful |
| `getMessage()` | `String` | Message content |
| `getData()` | `Object` | Data object |
| `getDataMap()` | `Map<String, Object>` | Convenience get data Map |
| `getData(Class<T>)` | `T` | Generic get data |
| `toFuture()` | `CompletableFuture<SkillResult>` | Convert to Future |


