# Kilacraft-AI — Skill SPI Integration Guide

> **Doc version**: v2.2.0 ｜ **Plugin version**: Kilacraft-AI ≥ 2.2.0 ｜ **SPI Jar**: `Kilacraft-Skill-API-2.2.0.jar`
> **Purpose**: Guide third-party plugin developers on integrating custom skills into Kilacraft-AI via the Skill SPI.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Contract at a Glance](#2-core-contract-at-a-glance)
3. [Architecture & Data Flow](#3-architecture--data-flow)
4. [Quick Start (5-Minute Integration)](#4-quick-start-5-minute-integration)
5. [Core Interfaces](#5-core-interfaces)
6. [Result Status Markers & Normalization](#6-result-status-markers--normalization)
7. [Needs-Info / Confirmation (needInfo)](#7-needs-info--confirmation-needinfo)
8. [Multi-Step Task Data Passing](#8-multi-step-task-data-passing)
9. [Skill Development Guidelines](#9-skill-development-guidelines)
10. [Error Isolation & Exception Handling](#10-error-isolation--exception-handling)
11. [Permission & Availability Control](#11-permission--availability-control)
12. [Security Filter (Important)](#12-security-filter-important)
13. [Naming Conventions & Conflict Handling](#13-naming-conventions--conflict-handling)
14. [Complete Example](#14-complete-example)
15. [Build Dependency Configuration](#15-build-dependency-configuration)
16. [Lifecycle & Load Order](#16-lifecycle--load-order)
17. [FAQ](#17-faq)
18. [API Reference](#18-api-reference)
19. [Publishing & Review](#19-publishing--review)

---

## 1. Overview

Kilacraft-AI uses an **SPI (Service Provider Interface)** mechanism that allows third-party Minecraft plugins to wrap their own features as **Skills** registered with the AI agent, so the AI assistant automatically recognizes user intent and invokes the right Skill.

### Core Features

- **Zero-coupling integration**: just add `kilacraft-skill-api.jar` as `compileOnly` and implement the interfaces
- **Auto-discovery**: based on Bukkit `ServicesManager`; scanned and registered at startup
- **Error isolation**: third-party Skill exceptions never break the host plugin's core flow
- **LLM intent-driven**: the AI recognizes intent and invokes the right Skill; users need no commands
- **Structured responses**: `SkillResult` carries a typed `SkillStatus`; the framework uniformly emits `[SUCCESS]/[FAILURE]/[NEED_INFO]` markers
- **Confirmation / supplement**: `needInfo(...)` lets a Skill structurally declare "needs info / needs confirmation", and the framework automatically manages continuation-resume
- **Multi-step tasks**: a Skill's returned `data` can be referenced by later steps via `{step_x.field}` placeholders, enabling cross-skill orchestration

### Use Cases

| Area | Examples |
|------|----------|
| Economy | query balance, transfer, shop purchase |
| Land / protection | query land info, create land |
| Leaderboards | online ranking, wealth ranking |
| RPG | query skill level, quest progress |
| World management | query chunk info, teleport |

---

## 2. Core Contract at a Glance

Before diving in, establish a high-level mental model:

- **Skill**: implement the `Skill` interface; discovered and executed by the framework through `SkillProvider` (see [§3](#3-architecture--data-flow), [§4](#4-quick-start-5-minute-integration)).
- **SkillResult has three states**: `success` (success, can carry data), `failure` (hard failure), `needInfo` (soft pause: needs a parameter supplemented or needs player confirmation). The message is plain text; `[STATUS]` markers are added uniformly by the framework (see [§6](#6-result-status-markers--normalization)).
- **needInfo continuation**: when a Skill pauses via `needInfo(...)`, the framework snapshots the parameters and resumes execution after the player's next reply (confirm / supply a value / cancel). The confirmation state is read via `context.isConfirmed()` (see [§7](#7-needs-info--confirmation-needinfo)).
- **Multi-step data**: the `data` from `success(msg, data)` can be referenced by later steps using `{step_x.field}` placeholders (see [§8](#8-multi-step-task-data-passing)).

Integration requires only `compileOnly` on `Kilacraft-Skill-API-2.2.0.jar` (containing `Skill` / `SkillContext` / `SkillResult` / `SkillStatus` / `SkillIntent` / `SkillProvider` / `SkillEntityHelper` — 7 classes). **Do not package it** into your plugin — it is provided at runtime by the host plugin.

---

## 3. Architecture & Data Flow

```
┌──────────────────────────────────────────────────┐
│                  Third-party JAR                  │
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
│              Kilacraft-AI host plugin             │
│  SkillRegistry auto-discovery → SkillManager      │
│                                 │                 │
│                     LLM intent recognition        │
│                     (Phase1 / Phase2)             │
│                     User message → Skill call     │
└──────────────────────────────────────────────────┘
```

### Data Flow

```
User chat message
  → ChatListener / KilacraftCommand intercepts
  → SkillIntentRecognizer (LLM two-phase intent recognition)
  → SkillManager.executeSkillByIntent()
  → Skill.execute(context) → SkillResult(status, message, data)
  → SkillResultFormatter uniformly emits "[STATUS] message"
  → returned to user / injected into fallback LLM / passed to next step
```

---

## 4. Quick Start (5-Minute Integration)

### Step 1: Add the build dependency

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>2.2.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-2.2.0.jar</systemPath>
</dependency>
```

> This dependency is `compileOnly` and is NOT packaged into your plugin JAR. The filename includes the version; adjust `systemPath` to the file you downloaded.

### Step 2: Implement the Skill interface

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
    public String getName() { return "hello_world"; }   // globally unique id

    @Override
    public String getDescription() {
        return "Greets the player. Returns a greeting message.";
    }

    @Override
    public Map<String, String> getActions() {
        return Map.of("greet", "Send a greeting to the specified player");
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        String name = player != null ? player.getName() : "stranger";
        return CompletableFuture.completedFuture(
                SkillResult.success("Hello, " + name + "! Welcome to the AI assistant!"));
    }

    @Override
    public String getRequiredPermission() { return "myplugin.hello"; }
}
```

### Step 3: Register the SkillProvider

```java
package com.example.myplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillProvider;
import com.example.myplugin.skills.HelloWorldSkill;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MyPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // Kilacraft-AI auto-discovers this after server startup
        getServer().getServicesManager().register(
                SkillProvider.class, this, this, ServicePriority.Normal);
        getLogger().info("SkillProvider registered, waiting for Kilacraft-AI to discover...");
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new HelloWorldSkill());
    }
}
```

### Step 4: Configure plugin.yml

```yaml
name: MyPlugin
version: 1.0
main: com.example.myplugin.MyPlugin
api-version: '1.21'
softdepend: [Kilacraft-AI]   # soft-depend so the host plugin is loaded first

permissions:
  myplugin.hello:
    description: Allows using the AI greeting skill
    default: true
```

Drop both JARs into `plugins/` and start the server. The console will print:

```
[Kilacraft-AI] Discovered and registered third-party skill: hello_world (from MyPlugin)
```

### Step 5: Verify Registration & Debug

Once registered, use Kilacraft-AI's built-in commands to quickly verify your Skill:

- **Check registration**: Run `/kila skills` in-game — your Skill's name and description should appear (marked "(third-party)"). Only skills the current caller has permission to trigger are listed, so check with an account that holds the required permission
- **Execute & debug directly**: `/kila run <skill> <prompt>` skips intent recognition and calls your Skill directly — ideal for precise triggering and debugging, e.g. `/kila run hello_world hello`. If it says the skill isn't found, the permission is missing or registration didn't take effect

> These two commands are the fastest way to diagnose "why isn't the AI triggering my skill": first confirm registration & permission with `/kila skills`, then verify the execution logic itself with `/kila run`.

---

## 5. Core Interfaces

### 5.1 Skill interface

```java
public interface Skill {
    /** Skill name (globally unique id, ≤32 chars) */
    String getName();

    /** Skill description (used by LLM intent recognition — very important) */
    String getDescription();

    /** Action list: key=action name, value=action description (default empty) */
    default Map<String, String> getActions() { return Collections.emptyMap(); }

    /** Extra hints (usage examples, caveats; default empty) */
    default List<String> getHints() { return Collections.emptyList(); }

    /** Execute the skill (core method, async) */
    CompletableFuture<SkillResult> execute(SkillContext context);

    /** Whether the skill is available in this context (default true) */
    default boolean isAvailable(SkillContext context) { return true; }

    /** Permission node required to use this skill (must implement, never null) */
    String getRequiredPermission();
}
```

### 5.2 SkillProvider interface

```java
public interface SkillProvider {
    List<Skill> getSkills();   // never null; return empty list if no skills
}
```

Your plugin main class implements this; prefer a separate instance per Skill (avoid shared mutable state).

### 5.3 SkillContext

```java
public class SkillContext {
    Player getPlayer();                 // current player (null for console)
    String getAction();                 // LLM-recognized action
    Map<String, String> getEntities();  // LLM-extracted parameters
    String getEntity(String key);       // get a parameter (always String; convert numbers yourself)
    boolean isConfirmed();              // framework confirmation flag: set true when resuming after player confirmation, used for needInfo flow
}
```

| Field | Description | Example |
|-------|-------------|---------|
| `player` | player who triggered the chat | player instance |
| `action` | LLM-recognized action | `"query_price"`, `"greet"` |
| `entities` | LLM-extracted params | `{"item": "diamond", "quantity": "10"}` |

### 5.4 SkillResult & SkillStatus (Structured Response)

```java
public class SkillResult {
    boolean isSuccess();           // SUCCESS→true, FAILURE/NEED_INFO→false (control flow uses this)
    SkillStatus getStatus();        // structured status (for presentation tagging)
    String getMessage();            // plain-text message (do NOT add a [STATUS] prefix)
    Object getData();               // data object (conventionally a Map, for multi-step passing)
    Map<String, Object> getDataMap();

    // Static factory methods
    static SkillResult success(String message);
    static SkillResult success(String message, Object data);
    static SkillResult failure(String message);
    static SkillResult failure(String message, Throwable error);
    static SkillResult needInfo(String message);   // needs info / confirmation
}

public enum SkillStatus {
    SUCCESS, FAILURE, NEED_INFO;
    String prefix();   // → "[SUCCESS]" etc.; used internally for tagging
}
```

---

## 6. Result Status Markers & Normalization

At the **output boundary to the LLM**, the framework prepends a bracket marker to every result:

| Factory | `isSuccess()` | Marker | Meaning |
|---------|:-------------:|:------:|---------|
| `success(...)` | true | `[SUCCESS]` | executed successfully |
| `failure(...)` | false | `[FAILURE]` | hard failure: cannot proceed (permission / balance / not found / invalid param / plugin missing) |
| `needInfo(...)` | false | `[NEED_INFO]` | soft pause: needs the player to supply a parameter or confirm |

**Key rule: the message must be plain text — do NOT prepend `[FAILURE]`/`[NEED_INFO]` yourself.** The framework adds the marker; doing it yourself causes marker corruption or conflicts with normalization.

This matters for LLM visibility: the player ultimately sees the LLM's natural-language relay, while the LLM sees the uniform `[STATUS] body`; the system prompt instructs the LLM never to expose these markers to the player.

---

## 7. Needs-Info / Confirmation (needInfo)

The `needInfo` contract covers two scenarios:

1. **Missing parameter**: a required parameter is missing and the player must supply it
2. **Needs confirmation**: an operation is risky or large and requires explicit player confirmation before executing

### 7.1 Basic Usage

Return `SkillResult.needInfo(message)` with a **message containing concrete values**:

```java
// Missing parameter: prompt the player to supply it
return CompletableFuture.completedFuture(SkillResult.needInfo("At what unit price do you want to list?"));

// Needs confirmation: provide the computed concrete value
return CompletableFuture.completedFuture(
        SkillResult.needInfo("About to transfer " + amount + " to " + target + ". Confirm transfer?"));
```

> `needInfo(...)` returns `isSuccess() == false`, same as `failure(...)`; the only difference is the marker shown to the LLM (`[NEED_INFO]` vs `[FAILURE]`), letting the LLM distinguish "needs info/confirmation" from "truly failed".

### 7.2 Full Example: Transfer Confirmation (streamlined from the built-in MarketActionSkill)

Below is the full logic for "large transfer requires confirmation", showing `needInfo` cooperating with the framework confirmation flag `context.isConfirmed()`:

```java
private CompletableFuture<SkillResult> transferMoney(SkillContext context) {
    Player player = context.getPlayer();
    if (player == null) return CompletableFuture.completedFuture(SkillResult.failure("Online players only"));

    String targetPlayer = context.getEntity("target_player");
    if (targetPlayer == null || targetPlayer.isEmpty()) {
        // ① missing recipient → needs info
        return CompletableFuture.completedFuture(SkillResult.needInfo("Who do you want to transfer to?"));
    }

    String amountStr = context.getEntity("amount");
    if (amountStr == null || amountStr.isEmpty()) {
        // ② missing amount → needs info (include balance to help the player decide)
        double balance = getBalance(player);
        return CompletableFuture.completedFuture(
                SkillResult.needInfo("Your balance is " + balance + ". How much to transfer to " + targetPlayer + "?"));
    }

    double amount;
    try {
        amount = Double.parseDouble(amountStr);
    } catch (NumberFormatException e) {
        return CompletableFuture.completedFuture(SkillResult.failure("Invalid amount format: " + amountStr));
    }

    // ③ large transfer (>50% of balance) → needs confirmation (flag expressed via context.isConfirmed())
    double balance = getBalance(player);
    if (balance > 0 && amount > balance * 0.5) {
        if (!context.isConfirmed()) {
            // return needInfo with the concrete amount
            return CompletableFuture.completedFuture(
                    SkillResult.needInfo("About to transfer " + amount + " to " + targetPlayer
                            + ", a large portion of your balance. Confirm transfer?"));
        }
        // framework sets isConfirmed=true when the player confirms → fall through and execute
    }

    // actually perform the transfer
    return doTransfer(player, targetPlayer, amount);
}
```

### 7.3 How the Confirmation Flow Works End-to-End

Confirmation / supplement is driven by the **framework continuation mechanism**. A Skill simply returns `needInfo(...)`, and the framework automatically handles "snapshot params → classify player reply → resume execution":

```
[Turn 1] Player: "Transfer half my balance to ZookeeR"
   │
   ├─ Intent recognition Phase2 → multi-step: step_0 query balance → step_1 transfer({step_0.balance}/2)
   ├─ step_0 returns balance 1177.75; step_1 evaluates to 588.88, >50%
   └─ transferMoney returns needInfo("About to transfer $588.88 to ZookeeR. Confirm transfer?")
        │
        ├─ The unified chokepoint SkillManager.executeSkillByIntent detects NEED_INFO:
        │    snapshots {target_player:"ZookeeR", amount:"588.88"} + message → stores in a per-player continuation slot
        ├─ Normalization layer emits [NEED_INFO] body; fallback LLM relays it to the player naturally
        └─ Key detail: concrete values are only stored in the framework slot — the LLM is no longer required to read them back from chat history

[Turn 2] Player: "Confirm"
   │
   ├─ The framework detects an active continuation slot → runs lightweight classification (not two-phase)
   ├─ Classified as confirm → framework resumes transferMoney directly from the slot snapshot
   │   with context.isConfirmed()=true (amount is still "588.88" from the slot — never went through chat,
   │   so no $-prefix pollution, no placeholder re-injection)
   ├─ transferMoney sees isConfirmed=true → skips the needInfo branch, actually executes
   └─ returns success("Successfully transferred $588.88 to ZookeeR"), slot is cleared on terminal outcome
```

The framework guarantees:

1. **Uniform marker**: the normalization layer emits `needInfo` results as `[NEED_INFO] body`, reliably recognized by the LLM.
2. **No partial execution**: when a step returns `needInfo` (`isSuccess()==false`) in a multi-step task, **downstream steps that depend on it are automatically skipped** — they never run with incomplete data.
3. **Params never round-trip through chat**: at `needInfo`, the framework snapshots the entities already passed to the Skill (sanitized, placeholder-resolved). On the player's next confirm/supplement turn, execution resumes directly from the snapshot, avoiding LLM text pollution of parameter values.
4. **Real-time data validated live by the Skill**: the framework only stores param snapshots — it **never caches world state** (balance, inventory, etc.). On resume, the Skill re-reads live data; if drift means a previously-confirmed value is no longer valid (e.g. the player manually moved funds), the Skill can return a fresh `needInfo` to re-prompt the player, rather than silently executing a stale result.
5. **Lifecycle**: one slot per player, last-write-wins (N-th confirmation / supplement follows the same rule); cleared on terminal (SUCCESS/FAILURE), player cancel, TTL expiry, exceeding `max_rounds`, player quit, or plugin disable. Purely in-memory — never persisted to disk (high-risk pending operations never auto-resurrect across restarts).

### 7.4 How to Write the Skill's Action Description

Confirmation / supplement is managed automatically by the framework. **The Skill's action description only needs to declare that the action may return needInfo** — no need to teach the LLM to re-call with a confirmation flag:

```yaml
# action_descriptions (excerpt from MarketActionSkill.yml)
transfer_money: "Transfer money to another player. Required params: target_player, amount (plain number,
  or an arithmetic placeholder like {step_0.balance}/2). Use when the user says 'transfer', 'send 100 to XX'.
  This action may return [NEED_INFO] to ask for a missing amount or to confirm a large transfer;
  the continuation is managed automatically by the framework — just follow the message to guide the player."
```

Key points:

- **Confirmation signal via `context.isConfirmed()`**: the framework manages the confirmation flag (sets `true` on resume). Skill code reads `context.isConfirmed()` — **do not define your own `confirmed` entity parameter**.
- **Include concrete values in the message**: the `needInfo` body should carry the computed/queried concrete value (amount, index, etc.) so the player knows what they are confirming.
- **Re-validate live data on resume**: the Skill should re-read world state (balance, etc.) on resume; if drift occurs, return a fresh `needInfo` to re-prompt.
- **No need for LLM to reconstruct params**: params are held by the framework slot; no need to teach the LLM in the description how to read values from history or re-call with confirmation flags.

> Just return `needInfo(...)`; `[NEED_INFO]` is added automatically. Resumed execution goes through the full execution pipeline (security sanitize, permission, error isolation).

---

## 8. Multi-Step Task Data Passing

### 8.1 Overview

When a single user request needs several Skills in sequence, an earlier Skill's returned `data` can be referenced by a later Skill via `{step_x.field}` placeholders. This is a **three-party contract**: developer writes code + descriptions → LLM reads descriptions and generates placeholders → framework resolves them.

### 8.2 What the Skill Developer Must Do

1. Return a `Map<String, Object>` data in `SkillResult.success(message, data)`
2. Document the returned data fields in the description / action description

```java
private CompletableFuture<SkillResult> queryPrice(SkillContext context) {
    String itemName = context.getEntity("item");
    double price = getPrice(itemName);
    Map<String, Object> data = new HashMap<>();
    data.put("item_name", itemName);
    data.put("price", price);
    return CompletableFuture.completedFuture(
            SkillResult.success(itemName + " price is $" + price, data));
}
```

Document: `"Returned data contains item_name and price fields."`

### 8.3 Placeholder Format

```
{step_<stepID>.<field>}                       // plain field
{step_<stepID>.<arrayField>[<index>].<sub>}   // array index access
```

Examples:

```json
{"item": "{step_1.item_name}", "quantity": "10"}
{"warp_name": "{step_1.warps[0].warp_name}"}
```

- Step IDs are generated by the LLM (e.g. `step_1`)
- Field names correspond to keys in the `data` Map
- Single-level array indexing is supported; multi-level nested arrays are not

When the user says "check the diamond price, and if it's under 100 buy 10", the LLM orchestrates two steps and references the first step's result with `{step_1.price}` in the second; the framework's `TaskExecutor` resolves it automatically.

---

## 9. Skill Development Guidelines

### 9.1 Naming Conventions

| Item | Convention | Example |
|------|------------|---------|
| Skill name | lowercase + underscore, `plugin_feature` | `economy_balance`, `mcmmo_stats` |
| Action name | lowercase + underscore, `verb_noun` | `query_balance`, `transfer_money` |
| Entity key | lowercase + underscore | `item_name`, `player_name`, `quantity` |
| Data field | lowercase + underscore | `health`, `max_health`, `food_level` |

Skill names are globally unique; a third-party Skill whose name collides with a built-in one is skipped (built-ins win). Use a `pluginname_` prefix to avoid collisions.

### 9.2 Writing the Description

`getDescription()` is the primary basis for LLM intent recognition. Be clear about the feature, include keywords users might say, and document returned data fields.

```java
// Good
"Query the player's MCMMO skill level. Returned data contains skill_name, level, xp fields for multi-step passing."
// Bad
"Query info"   // too vague
```

### 9.3 Action Design

A Skill may have multiple actions; the LLM picks one based on the user input. Action descriptions determine which action the LLM calls and which params it extracts:

```java
return Map.of(
    "query_balance", "Query the player's balance. Returned data contains the balance field.",
    "transfer", "Transfer to a specified player. Required params: target_player, amount."
);
```

### 9.4 Entity Extraction

The LLM extracts params from the user input as a `Map<String,String>`. Declare required params in the action description:

```java
"Buy items on the market. Required params: item (name, supports aliases), quantity (default 1)."
```

User: "buy 10 diamonds for me" → `{"item":"diamond","quantity":"10"}`. Entity values are always String; convert numbers yourself and handle exceptions.

---

## 10. Error Isolation & Exception Handling

### 10.1 Auto Isolation

`SkillManager` fully isolates third-party Skill execution — async and sync exceptions are caught and converted to `SkillResult.failure(...)`, never breaking the host flow:

```java
try {
    return skill.execute(context).exceptionally(ex -> {
        plugin.getLogger().log(Level.SEVERE, "Skill exception: " + skillName, ex);
        return SkillResult.failure("Skill execution error, please contact admin");
    });
} catch (Exception e) {
    return SkillResult.failure("Skill execution error, please contact admin");
}
```

### 10.2 Development Advice

Even though the framework catches everything, handle your own exceptions and return user-friendly `failure(...)` messages (the player sees them):

```java
public CompletableFuture<SkillResult> execute(SkillContext context) {
    try {
        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("Please specify an item name"));
        }
        return doSomething(itemName);
    } catch (Exception e) {
        return CompletableFuture.completedFuture(SkillResult.failure("Query failed: " + e.getMessage()));
    }
}
```

Don't throw uncaught RuntimeExceptions; handle async chains with `.exceptionally()`; avoid complex logic in `isAvailable()` (its exceptions are also isolated).

---

## 11. Permission & Availability Control

### 11.1 Permission Pre-filter (getRequiredPermission)

```java
@Override
public String getRequiredPermission() { return "myplugin.query.stats"; }
```

`getRequiredPermission()` returns a **Skill-level** permission controlling whether the whole Skill (all actions) is injected into the LLM prompt. Timing: intent recognition (prompt building). If the caller lacks it, the Skill's name/description/actions are **not** injected — the LLM doesn't know it exists — avoiding mismatches, saving tokens, and preventing info leakage.

| Level | Scope | Phase | How |
|-------|-------|-------|-----|
| Skill-level | whole Skill visibility | intent recognition (prompt build) | `getRequiredPermission()` |
| Action-level (optional) | single action executability | during Skill execution | check inside `execute()` |

Declare third-party permissions in your own `plugin.yml`; the framework queries via `Player.hasPermission()`:

```yaml
permissions:
  myplugin.admin.stats:
    description: Allows using AI to query player stats
    default: op
```

> **Skill cohesion principle**: keep all actions of a Skill aimed at the same permission audience; avoid mixing admin actions and normal-player actions in one Skill, or the AI may orchestrate an action the player can't execute. Split different permission levels into separate Skills.

### 11.2 isAvailable() Check

```java
@Override
public boolean isAvailable(SkillContext context) {
    return Bukkit.getPluginManager().getPlugin("MyEconomy") != null;  // dependency plugin installed?
}
```

`isAvailable()` is for **runtime availability** (e.g. dependency plugin present), called before each execution; returning false shows "this feature is temporarily unavailable". **Don't** do permission checks here — use `getRequiredPermission()` (filtered at intent recognition).

---

## 12. Security Filter (Important)

The framework ships a **non-cooperative security filter** (`SkillSecurityFilter`) that runs before every Skill execution, protecting player data from malicious Skills.

### 12.1 Core Mechanism

```
Before Skill execution
  → SkillSecurityFilter.sanitize(skillName, action, context)
  → iterate all Values in context.entities
  → does a Value match an online player name?
       ├─ it's the current player → allow
       ├─ it's another online player + whitelisted → allow
       └─ it's another online player + not whitelisted → sanitize (replace with current player)
  → return sanitized entities to Skill.execute()
```

Properties: non-cooperative (scans all Values regardless of param names), sanitizes instead of blocking, always runs and cannot be bypassed.

### 12.2 What It Means for Your Skill

- **Only operates on the current player**: no action needed.
- **Needs to operate on other players (e.g. transfer)**: the server owner must whitelist it in `config.yml`:

```yaml
security:
  player_isolation:
    allowed_actions:
      - "economy.transfer"        # skill-level whitelist
      - "economy.send_payment"    # or "skillname.actionname" action-level
```

The whitelist is configured by the server owner; developers can't decide it themselves; the skill name must exactly match `getName()` (case-sensitive).

- **Not whitelisted but operates on others**: other-player names in entities are replaced with the current player's name; the Skill "thinks" it's operating on itself — a security design malicious Skills cannot bypass.

Built-in whitelisted examples: `cmi.send_tp_request`, `player_watch.subscribe`, `command.execute_command`.

### 12.3 Notes

Don't try to bypass; don't embed player names inside entity values (e.g. `"msg Hub hello"` can't be recognized); player-name regex `^[a-zA-Z0-9_]{1,16}$`; non-matching values are skipped.

---

## 13. Naming Conventions & Conflict Handling

Recommended namespace: `<pluginname>_<feature>`, e.g. `mcmmo_query_level`, `towny_query_info`.

Conflict handling: built-in Skills win; for third-party collisions, first-registered wins; the console warns on conflict.

```
[Kilacraft-AI] Skipped third-party skill 'market_query' (from SomePlugin): name conflict with a registered skill
```

---

## 14. Complete Example

### 14.1 Player Stats Query Skill

```java
package com.example.statsplugin.skills;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlayerStatsSkill implements Skill {

    @Override public String getName() { return "player_stats_query"; }

    @Override public String getDescription() {
        return "Query player status (health, food, experience level). "
                + "Returned data contains health, max_health, food_level, level, total_exp fields.";
    }

    @Override public Map<String, String> getActions() {
        return Map.of(
            "query_health", "Query the player's current health. Returned data contains health and max_health.",
            "query_food", "Query the player's current food level. Returned data contains food_level.",
            "query_experience", "Query the player's current experience level. Returned data contains level and total_exp.");
    }

    @Override public String getRequiredPermission() { return "statsplugin.query"; }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("Please specify a player"));
        }
        return switch (context.getAction()) {
            case "query_health" -> CompletableFuture.completedFuture(
                    SkillResult.success(String.format("Health: %.1f/%.1f", player.getHealth(), player.getMaxHealth()),
                            Map.of("health", player.getHealth(), "max_health", player.getMaxHealth())));
            case "query_food" -> CompletableFuture.completedFuture(
                    SkillResult.success("Food: " + player.getFoodLevel() + "/20",
                            Map.of("food_level", player.getFoodLevel())));
            case "query_experience" -> CompletableFuture.completedFuture(
                    SkillResult.success(String.format("Level: %d, Total XP: %d", player.getLevel(), player.getTotalExperience()),
                            Map.of("level", player.getLevel(), "total_exp", player.getTotalExperience())));
            default -> CompletableFuture.completedFuture(SkillResult.failure("Unknown action: " + context.getAction()));
        };
    }
}
```

### 14.2 Plugin Main Class & plugin.yml

```java
package com.example.statsplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class StatsPlugin extends JavaPlugin implements SkillProvider {
    @Override public void onEnable() {
        getServer().getServicesManager().register(SkillProvider.class, this, this, ServicePriority.Normal);
    }
    @Override public List<Skill> getSkills() { return List.of(new PlayerStatsSkill()); }
}
```

```yaml
name: StatsPlugin
version: 1.0
main: com.example.statsplugin.StatsPlugin
api-version: '1.21'
softdepend: [Kilacraft-AI]
permissions:
  statsplugin.query:
    description: Allows using AI to query player stats
    default: true
```

### 14.3 User Interaction

```
User: How much health do I have?
AI: Your health is 18.5/20.0
User: What about my level and food?
AI: Level 15, total XP 3200. Food 18/20.
```

---

## 15. Build Dependency Configuration

### Maven

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>2.2.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-2.2.0.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.spigotmc</groupId>
    <artifactId>spigot-api</artifactId>
    <version>1.21-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation files('libs/Kilacraft-Skill-API-2.2.0.jar')
    compileOnly 'org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT'
}
```

### About kilacraft-skill-api.jar

This JAR contains the compiled **7 classes** below for third-party compile-time reference:

```
com.zm.kilacraftAI.skills.framework.Skill
com.zm.kilacraftAI.skills.framework.SkillContext
com.zm.kilacraftAI.skills.framework.SkillResult
com.zm.kilacraftAI.skills.framework.SkillStatus
com.zm.kilacraftAI.skills.framework.SkillIntent
com.zm.kilacraftAI.skills.framework.SkillProvider
com.zm.kilacraftAI.skills.framework.SkillEntityHelper
```

> `SkillEntityHelper` is a static utility class added to the SPI in v2.2.0, providing safe parameter extraction and type-conversion methods (`getString` / `getInt` / `getIntClamped` / `getLong` / `getDouble` / `getBoolean`). All methods throw no exceptions (return a default value on failure), eliminating the scattered `try { Integer.parseInt(...) } catch` boilerplate inside skills.
>
> This JAR must **not** be packaged into your plugin; it's provided at runtime by the Kilacraft-AI host plugin.

---

## 16. Lifecycle & Load Order

```
Server startup
  ├─ Third-party plugin onEnable() → ServicesManager.register(SkillProvider.class, ...)
  ├─ Kilacraft-AI onEnable() → init SkillManager, register built-in Skills, schedule delayed task
  ├─ Server finished starting
  └─ After 20 ticks → SkillRegistry.discoverAndRegister()
        ├─ scan ServicesManager for SkillProviders
        ├─ iterate getSkills(), check name conflicts
        └─ register into SkillManager

User chat
  └─ ChatListener → SkillIntentRecognizer → SkillManager.executeSkillByIntent()
        ├─ isAvailable() check (with error isolation)
        └─ Skill.execute(context) (with error isolation)

Server shutdown
  └─ Bukkit auto-unregisters ServicesManager entries
```

> The 20-tick delay ensures all third-party plugins have finished `onEnable()` registration, avoiding missed scans from load-order differences. **Auto-discovery runs only once at startup; restart the server after installing/updating a third-party Skill** (no hot-reload of discovery).

---

## 17. FAQ

**Q: Does my plugin load before or after Kilacraft-AI?**
A: Depends on Bukkit load order (usually alphabetical), but either way auto-discovery works: whatever registers first is found by the delayed scan.

**Q: Is hot-reload supported?**
A: Auto-discovery is not (once at startup). But if your Skill reads its description from its own config, you can hot-reload the description content yourself.

**Q: Can execute do long-running work?**
A: Yes, returning a `CompletableFuture` supports async. Don't block the main thread; Bukkit API calls must be on the main thread (use `Bukkit.getScheduler().runTask()`).

**Q: Can one plugin register multiple Skills?**
A: Yes, return multiple instances from `getSkills()`.

**Q: Can the console use a Skill?**
A: Depends on your `isAvailable()`/`execute()`. For console calls, `context.getPlayer()` is null; handle it yourself.

**Q: How do I implement "secondary confirmation"?**
A: Return `SkillResult.needInfo(msg)` (include concrete values in the message); read the confirmation flag via `context.isConfirmed()`, which the framework sets `true` on resume. **Do not define your own `confirmed` entity parameter, and do not teach the LLM in the description to re-call with a confirmation flag** — the continuation is managed automatically by the framework. Live data (balance, etc.) should be re-validated by the Skill on resume. See [§7](#7-needs-info--confirmation-needinfo).

**Q: Should I write a `[FAILURE]` prefix in the message?**
A: **No.** The framework tags uniformly; write plain text or you'll double-tag.

**Q: How do I get orchestrated correctly in multi-step tasks?**
A: Document the returned data fields clearly in the description / action description; the LLM references them with `{step_x.field}`.

---

## 18. API Reference

### Skill interface methods

| Method | Return type | Required | Description |
|--------|-------------|:--------:|-------------|
| `getName()` | `String` | yes | unique skill id (≤32 chars) |
| `getDescription()` | `String` | yes | description for LLM recognition |
| `getActions()` | `Map<String,String>` | no | action map, default empty |
| `getHints()` | `List<String>` | no | hints, default empty |
| `execute(SkillContext)` | `CompletableFuture<SkillResult>` | yes | core execution |
| `isAvailable(SkillContext)` | `boolean` | no | availability check, default true |
| `getRequiredPermission()` | `String` | **yes** | permission node, must declare |

### SkillResult static factory methods

| Method | SkillStatus | isSuccess | Description |
|--------|:-----------:|:---------:|-------------|
| `success(String message)` | SUCCESS | true | success, no data |
| `success(String message, Object data)` | SUCCESS | true | success, with data (multi-step) |
| `failure(String message)` | FAILURE | false | hard failure |
| `failure(String message, Throwable error)` | FAILURE | false | hard failure, with exception |
| `needInfo(String message)` | NEED_INFO | false | needs info / confirmation |

### SkillResult instance methods

| Method | Return type | Description |
|--------|-------------|-------------|
| `isSuccess()` | `boolean` | control-flow check (SUCCESS→true) |
| `getStatus()` | `SkillStatus` | structured status (presentation tagging) |
| `getMessage()` | `String` | plain-text message |
| `getData()` | `Object` | data object |
| `getDataMap()` | `Map<String,Object>` | convenience data-Map accessor |
| `getData(Class<T>)` | `T` | typed data accessor |
| `toFuture()` | `CompletableFuture<SkillResult>` | convert to Future |

### SkillStatus enum

| Value | prefix() | Meaning |
|-------|:--------:|---------|
| `SUCCESS` | `[SUCCESS]` | success |
| `FAILURE` | `[FAILURE]` | hard failure |
| `NEED_INFO` | `[NEED_INFO]` | needs info / confirmation |

---

## 19. Publishing & Review

### 19.1 Distribution

**Integrate into your plugin (recommended)**: users need no extra install; auto-registered via SPI; low maintenance.

**Standalone Skill plugin**: create a standalone Bukkit plugin project, publish to MineBBS/SpigotMC/GitHub, label the README "Requires Kilacraft-AI 2.2.0+".

### 19.2 Security Review (Required)

All third-party Skills must pass a security review; once approved they're marked 🟢 reviewed in the [Skill Registry](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html).

Submit by opening an Issue at [GitHub Issues](https://github.com/axy-yxa/Kilacraft-AI/issues) titled `[Skill Review] your skill name`, providing: Skill name, source or JAR, feature description, permission notes (`getRequiredPermission()` return value + plugin.yml declaration + default), and an optional doc link.

Review criteria: `getRequiredPermission()` correctly implemented and non-null; permission declared in plugin.yml with a sensible default; doesn't directly operate on other players' data (unless declared and justified); doesn't run dangerous commands; no malicious network requests; correct resource cleanup.

### 19.3 Best Practices

- 📝 Provide clear docs and config examples
- 🧪 Test thoroughly (including needInfo confirmation flows and multi-step orchestration)
- 🔒 Do permission checks; avoid dangerous operations
- 🎯 One Skill, one focused feature
- 💬 Listen to user feedback and keep improving
