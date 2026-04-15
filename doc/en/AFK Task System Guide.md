# Kilacraft-AI - AFK Task System Guide

> **Version**: v1.4.3  
> **Description**: This document provides a comprehensive guide to the AFK Task System, including architecture, usage, call chains, and best practices

---

## 📖 Overview

The AFK Task System is a core feature of the Kilacraft-AI plugin, providing **asynchronous event monitoring + delayed callback execution** capabilities. Players can ask the AI to continuously monitor certain event conditions in the background, and automatically execute operations and notify players when conditions are met.

### Core Features

- ✅ **Natural Language Creation**: Create tasks through conversation, no need to memorize commands
- ✅ **Dual Mode Support**: Notification mode (fast response) and callback mode (intelligent analysis)
- ✅ **Rich Listeners**: 11 event types (S-Tier 7 + A-Tier 4)
- ✅ **Custom Condition Polling**: CUSTOM type supports monitoring any Skill's numeric return value
- ✅ **Multi-Step Callbacks**: Support combining multiple APIs for complex operations
- ✅ **Automatic Resource Management**: Auto-cleanup after task completion/cancellation
- ✅ **Delayed Feedback Optimization**: Avoid expired context noise
- ✅ **Concurrency Control**: One task per player, avoid resource waste

### Use Cases

```
Player: Watch Steve for me, tell me when he comes online
AI: Done! AFK task created: Monitor player Steve login.

[30 minutes later...]
AI: 🔔 AFK Task Reminder

     Steve has come online! He is currently in the overworld (X=128, Y=64, Z=-256).
```

---

## 🏗️ System Architecture

### File Structure

```
skills/afktask/
├── AFKTask.java                    # Abstract base class (lifecycle management)
├── AFKTaskManager.java             # Task manager (CRUD + concurrency control)
├── AFKTaskSkill.java               # Built-in Skill (LLM routing entry)
├── AFKTaskCallback.java            # Callback configuration (multi-step task chain)
├── AFKTaskType.java                # Task type enum (12 types: 11 event-based + CUSTOM)
├── AFKTaskStatus.java              # Status enum
├── AFKTaskListener.java            # Global listener (creator offline cleanup)
├── ConditionPlan.java              # Condition plan data structure (for CUSTOM)
├── ConditionEvaluator.java         # Condition evaluator (Skill execution + field extraction + comparison)
├── AFKTaskSkill.yml                # LLM prompt configuration
└── impl/                           # Concrete implementations
    ├── PlayerOnlineWatchTask.java          # S-Tier - Monitor login
    ├── PlayerOfflineWatchTask.java         # S-Tier - Monitor logout
    ├── PlayerDeathWatchTask.java           # S-Tier - Monitor death
    ├── PlayerTeleportWatchTask.java        # S-Tier - Monitor teleport
    ├── PlayerLevelChangeWatchTask.java     # S-Tier - Monitor level change
    ├── PlayerChangedWorldWatchTask.java    # S-Tier - Monitor world change
    ├── WeatherChangeWatchTask.java         # S-Tier - Monitor weather change
    ├── PlayerBedEnterWatchTask.java        # A-Tier - Monitor enter bed
    ├── PlayerBedLeaveWatchTask.java        # A-Tier - Monitor leave bed
    ├── PlayerRespawnWatchTask.java         # A-Tier - Monitor respawn
    ├── PlayerItemBreakWatchTask.java       # A-Tier - Monitor item break
    └── CustomWatchTask.java                # CUSTOM-Tier - Custom condition polling
```

### Class Inheritance

```
AFKTask (Abstract Base Class)
  ├── PlayerOnlineWatchTask (listens to PlayerJoinEvent)
  ├── PlayerOfflineWatchTask (listens to PlayerQuitEvent)
  ├── PlayerDeathWatchTask (listens to PlayerDeathEvent)
  ├── PlayerTeleportWatchTask (listens to PlayerTeleportEvent)
  ├── PlayerLevelChangeWatchTask (listens to PlayerLevelChangeEvent)
  ├── PlayerChangedWorldWatchTask (listens to PlayerChangedWorldEvent)
  ├── WeatherChangeWatchTask (listens to WeatherChangeEvent)
  ├── PlayerBedEnterWatchTask (listens to PlayerBedEnterEvent)
  ├── PlayerBedLeaveWatchTask (listens to PlayerBedLeaveEvent)
  ├── PlayerRespawnWatchTask (listens to PlayerRespawnEvent)
  └── PlayerItemBreakWatchTask (listens to PlayerItemBreakEvent)

AFKTaskSkill (implements Skill interface)
  └── Creates concrete tasks via AFKTaskManager.AFKTaskFactory

CustomWatchTask (extends AFKTask, BukkitRunnable polling)
  ├── ConditionPlan (condition data structure)
  └── ConditionEvaluator (condition evaluator, static methods)

AFKTaskManager (Manager)
  ├── taskMap: UUID → AFKTask (one task per player index)
  └── taskIndex: taskId → AFKTask (task ID index)

AFKTaskCallback (Callback Configuration)
  ├── CallbackTask (goal + steps)
  └── CallbackStep (single step, with depends_on)
```

---

## 🔄 Complete Call Chains

### Chain 1: Create AFK Task

```
Player message: "Watch Steve for me, tell me when he comes online"
  ↓
ChatListener.onPlayerChat()
  ↓
AIRequestHandler.handleAIRequest()
  ↓
SkillIntentRecognizer.recognizeIntent()  ← LLM Call 1 (Intent Recognition)
  Returns JSON:
  {
    "skill_name": "AFKTask",
    "action": "create_task",
    "entities": {
      "task_type": "PLAYER_ONLINE_WATCH",
      "target_player": "Steve"
    },
    "confidence": 0.95,
    "reasoning": "Player requests to monitor Steve login"
  }
  ↓
AIRequestHandler.handleSkillIntent()
  ↓
SkillManager.executeSkillByIntent()
  ↓
AFKTaskSkill.execute(context)
  ↓
handleCreateTask(context)
  ① Validate task_type parameter
  ② Check if task already exists (one task per player)
  ③ Target online/offline合理性 check
     - ONLINE_WATCH + target already online → Fail, suggest using OFFLINE_WATCH
     - OFFLINE_WATCH + target offline → Fail, suggest using ONLINE_WATCH
  ④ getTaskFactory(taskType) get factory
  ⑤ AFKTaskManager.createTask()
     - Recheck concurrency limits
     - Generate taskId: "afk_" + UUID first 8 chars + timestamp last 4 chars
     - Factory creates concrete AFKTask subclass instance
     - Register to taskMap + taskIndex
     - task.start() → Register Bukkit event listener → markRunning()
  ⑥ Return SkillResult.success("AFK task created and started: Monitor player Steve login")
  ↓
LLMAnalysisService.analyzeResult()  ← LLM Call 2 (Secondary Analysis)
  Structured summary:
  - User input: "Watch Steve for me, tell me when he comes online"
  - Skill result: "[SUCCESS] AFK task created and started: Monitor player Steve login"
  ↓
Generate natural language → Send to player:
  "Done! AFK task created: Monitor player Steve login. I will notify you immediately when Steve comes online."
```

**Notification Flow**: Player receives only 1 message (LLM secondary analysis result). `start()` does not call `notifyPlayer()` (avoid duplication).

---

### Chain 2: Event Trigger + Callback Execution

```
[30 minutes later...]
Target player Steve logs in
  ↓
Bukkit Event: PlayerJoinEvent
  ↓
PlayerOnlineWatchTask.onPlayerJoin(event)
  ① Check status == RUNNING
  ② Check if target player (equalsIgnoreCase)
  ③ Determine if callback steps exist
     
     ├─ [Notification Mode] (no callback or callback.steps is empty)
     │   → notifyPlayer("🔔 Monitor Task Complete\n\n• Target Player: Steve\n• Status: Online\n\nSteve has come online!")
     │   → complete("Target player Steve has come online, monitor task complete.")
     │
     └─ [Callback Mode] (has callback.steps)
         → executeCallback("Steve")
            ① callback.getCallbackTask().toTaskPlan() build TaskPlan
            ② replacePlaceholdersInTaskPlan()
               - {triggered_player} → "Steve"
               - {creator} → "Task creator name"
            ③ Check if creator is online
            ④ Build SkillContext (empty entities, goal from callbackTask)
            ⑤ Pass empty conversation history (delayed feedback optimization)
               Deque<Message> history = new ArrayDeque<>();
            ⑥ new TaskExecutor(skillManager, new LLMAnalysisService())
            ⑦ executor.executeTask(plan, context, history, goal)
               → Topological sort (sort steps by dependencies)
               → Recursively execute each step serially
                  - Step 1: GenericBukkitAPI.get_player_location
                  - Step 2: ... (if any)
               → synthesizeResults() → AnalysisSummary
               → LLMAnalysisService.analyzeResult()  ← LLM Call 3 (Secondary Analysis)
               → Return SkillResult (message=LLM generated natural language)
            ⑧ future.thenAccept(result → notifyCallbackResult("Steve", result))
               - notifyTarget empty or "{creator}" → notifyPlayer()
               - Otherwise → Bukkit.getPlayerExact() send to specified player
               - Notification content = "🔔 AFK Task Reminder\n\n" + MessageUtil.convertMarkdownToMinecraft(result.getMessage())
            ⑨ complete("Target player Steve has come online, callback task executed.")
               - Update status to COMPLETED
               - cleanup() → Remove from taskMap and taskIndex
```

---

### Chain 3: Player Offline Auto-Cleanup

```
Creator player logs off
  ↓
Bukkit Event: PlayerQuitEvent
  ↓
AFKTaskListener.onPlayerQuit(event)
  ↓
AFKTaskManager.onPlayerQuit(playerUUID)
  - taskMap.remove(playerUUID)
  - taskIndex.remove(taskId)
  - task.stop()
     → status = CANCELLED
     → onStop() → HandlerList.unregisterAll()
```

**Features**: Does not go through LLM, directly cleans up resources.

---

### Chain 4: Manual Command Operations

```
Player inputs: /kilacraft afk query
  ↓
KilacraftCommand.handleAfkCommand()
  ↓
manager.getTask(playerUUID)
  ↓
Direct formatted output (no LLM):
  "Current AFK Task:
   • Task ID: afk_a1b2c3d4_1234
   • Type: Monitor player login
   • Description: Monitor player Steve login
   • Status: Running
   • Created: 2026-04-10 14:30:00"
```

```
Player inputs: /kilacraft afk cancel
  ↓
KilacraftCommand.handleAfkCommand()
  ↓
manager.cancelTask(playerUUID)
  ↓
task.stop()
  ↓
Direct output (no LLM):
  "✅ AFK task cancelled: Monitor player Steve login"
```

**Features**: Commands operate directly without LLM, fast response.

---

## 🎯 Usage Guide

### 1. Notification Mode

**Use Cases**: Only need simple reminders, no additional operations.

**Creation**:
```
Player: Watch Steve for me, tell me when he comes online
AI: Done! AFK task created: Monitor player Steve login. I will notify you immediately when Steve comes online.
```

**Trigger Notification**:
```
🔔 Monitor Task Complete

• Target Player: Steve
• Status: Online

Steve has come online!
```

**Technical Implementation**:
```java
// Check if callback steps exist
boolean hasCallback = callback != null 
    && callback.getCallbackTask() != null 
    && callback.getCallbackTask().getSteps() != null 
    && !callback.getCallbackTask().getSteps().isEmpty();

if (!hasCallback) {
    // Notification mode
    notifyPlayer("🔔 Monitor Task Complete\n\n• Target Player: " + triggeredPlayerName + "\n• Status: Online\n\n" + triggeredPlayerName + " has come online!");
    complete("Target player " + triggeredPlayerName + " has come online, monitor task complete.");
}
```

---

### 2. Callback Mode

**Use Cases**: Need to query additional information or execute multiple operations.

**Creation**:
```
Player: Watch Steve, query his location and tell me after he logs in
AI: Done! AFK task created: Monitor player Steve login, trigger callback task (2 steps), goal: Query Steve's location and notify creator.
```

**Callback Configuration (Internal JSON)**:
```json
{
  "callback_task": {
    "goal": "Query Steve's location and report",
    "steps": [
      {
        "skill_name": "GenericBukkitAPI",
        "action": "get_player_location",
        "entities": {
          "target_player": "{triggered_player}"
        },
        "description": "Query {triggered_player}'s current location"
      }
    ]
  },
  "notify_target": "{creator}"
}
```

**Trigger Notification**:
```
🔔 AFK Task Reminder

Steve has come online! He is currently in the overworld (X=128, Y=64, Z=-256).
```

**Technical Implementation**:
```java
if (hasCallback) {
    // Callback mode
    executeCallback(triggeredPlayerName);
}

private void executeCallback(String triggeredPlayerName) {
    // 1. Build TaskPlan
    TaskPlan plan = callback.getCallbackTask().toTaskPlan();
    replacePlaceholdersInTaskPlan(plan, triggeredPlayerName);
    
    // 2. Check if creator is online
    Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
    if (creatorPlayer == null || !creatorPlayer.isOnline()) {
        complete("Task creator offline, callback task cancelled.");
        return;
    }
    
    // 3. Build execution context
    SkillContext context = new SkillContext(
        creatorPlayer, 
        callback.getCallbackTask().getGoal(), 
        Map.of()
    );
    
    // 4. Delayed feedback optimization: no conversation history
    Deque<ConversationManager.Message> history = new ArrayDeque<>();
    
    // 5. Execute multi-step task
    TaskExecutor executor = new TaskExecutor(
        plugin.getSkillManager(), 
        new LLMAnalysisService()
    );
    
    CompletableFuture<SkillResult> future = executor.executeTask(
        plan, context, history, callback.getCallbackTask().getGoal()
    );
    
    // 6. Handle execution result
    future.thenAccept(result -> {
        notifyCallbackResult(triggeredPlayerName, result);
        complete("Target player " + triggeredPlayerName + " has come online, callback task executed.");
    });
}
```

---

## 📋 Supported Event Types

### S-Tier Listeners (7)

| Task Type | Event | Monitor Target | Special Placeholders |
|-----------|-------|----------------|---------------------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | Player login | `{triggered_player}`, `{creator}` |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | Player logout | `{triggered_player}`, `{creator}` |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | Player death | `{triggered_player}`, `{creator}` |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | Player teleport | `{from_world}`, `{to_world}`, `{from_x}`, `{from_y}`, `{from_z}`, `{to_x}`, `{to_y}`, `{to_z}` |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | Player level change | `{old_level}`, `{new_level}`, `{direction}` |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | Player world change | `{from_world}`, `{to_world}` |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | Weather change | `{world_name}`, `{weather_state}`, `{weather_type}` |

### A-Tier Listeners (4)

| Task Type | Event | Monitor Target | Special Placeholders |
|-----------|-------|----------------|---------------------|
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | Player enter bed | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | Player leave bed | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | Player respawn | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | Player item break | `{item_name}`, `{item_type}` |

> 📖 **Complete Event List**: See [Bukkit Event Listener Reference](./Bukkit%20Event%20Listener%20Reference.md) for detailed descriptions and configuration examples of all listeners.

---

## 🔧 Configuration

### config.yml Configuration

```yaml
# AFK Task System Configuration
afk-task:
  enabled: true                    # Enable AFK task system
  max-tasks: 100                   # Global max tasks (reserved extension point)
  max-tasks-per-player: 1          # Max tasks per player (currently fixed at 1)
```

### AFKTaskSkill.yml Configuration

```yaml
description: 'AFK Task System: Continuously monitor an event condition in the background, automatically execute a complete multi-step callback task when condition is met. Use this skill when users involve "background monitoring", "watch", "once...then...", "keep an eye on", "when..." and other continuous monitoring needs.'

action_descriptions:
  create_task: 'Create new AFK task. Required parameters: task_type (monitor type), target_player (monitor target player). Optional parameter: callback (callback task configuration, only provide when user explicitly requests additional operations after event trigger). If user only requests "watch xxx login", "tell me when xxx logs in" and other notification-only needs, do not provide callback parameter. **Only accepts one-time tasks (trigger once and end), does not accept "every", "periodic", "scheduled", "daily" and other long-term recurring tasks**'
  cancel_task: 'Cancel player''s currently running AFK task. When player already has AFK task and wants to create new task, should proactively help player cancel old task before creating new one.'
  query_task: "Query player's current AFK task status, including task ID, type, status, etc."

hints:
  - '**Important: Output format must be single_intent**: All AFKTask actions (create_task, cancel_task, query_task) must be returned in single-intent JSON format, absolutely not in multi-step task format. callback is a parameter of entities, not an independent step.'
  - '**Task Type Description**: PLAYER_ONLINE_WATCH=Monitor player login, PLAYER_OFFLINE_WATCH=Monitor player logout, PLAYER_DEATH_WATCH=Monitor player death, PLAYER_TELEPORT_WATCH=Monitor player teleport, PLAYER_LEVEL_CHANGE_WATCH=Monitor player level change, PLAYER_CHANGED_WORLD_WATCH=Monitor player world change, WEATHER_CHANGE_WATCH=Monitor weather change, PLAYER_BED_ENTER_WATCH=Monitor player enter bed, PLAYER_BED_LEAVE_WATCH=Monitor player leave bed, PLAYER_RESPAWN_WATCH=Monitor player respawn, PLAYER_ITEM_BREAK_WATCH=Monitor player item break.'
  - '**One-time vs Long-term Tasks (Important)**: This skill only supports one-time tasks, i.e., trigger once and end. Does not accept long-term recurring tasks (e.g., "remind me every 1 hour", "tell me every day at noon"), if user requests such needs, should explain not supported in reasoning and explain reasons.'
  - '**Concurrency Limit and Task Replacement**: Each player can only have one AFK task at a time. If task creation fails with提示 "already has a running AFK task", should inform player can use /kilacraft afk cancel command to cancel old task, then try creating new task.'
```

---

## 💡 Best Practices

### 1. Choose the Right Listener

| Use Case | Recommended Listener | Example |
|----------|---------------------|---------|
| Player login/logout | `PLAYER_ONLINE_WATCH` / `PLAYER_OFFLINE_WATCH` | "Watch Steve login" |
| Player death/respawn | `PLAYER_DEATH_WATCH` / `PLAYER_RESPAWN_WATCH` | "Watch Steve, tell me when he dies" |
| Player movement | `PLAYER_TELEPORT_WATCH` / `PLAYER_CHANGED_WORLD_WATCH` | "Monitor Steve teleport" |
| Player status change | `PLAYER_LEVEL_CHANGE_WATCH` | "Watch Steve level up" |
| Player sleeping | `PLAYER_BED_ENTER_WATCH` / `PLAYER_BED_LEAVE_WATCH` | "Monitor Steve sleep" |
| Item related | `PLAYER_ITEM_BREAK_WATCH` | "Watch Steve's tool break" |
| Environment change | `WEATHER_CHANGE_WATCH` | "Tell me when it rains" |

### 2. Notification Mode vs Callback Mode Selection

**Use Notification Mode**:
```
✅ Only need simple reminder
✅ No additional data needed
✅ Pursue fast response

Example: "Watch Steve login for me"
```

**Use Callback Mode**:
```
✅ Need to query additional information
✅ Need to execute multiple operations
✅ Need intelligent analysis

Example: "Watch Steve, query his location and teleport to him after he logs in"
```

### 3. Placeholder Usage Tips

```yaml
# ✅ Good example: Fully utilize placeholders to provide context
callback:
  callback_task:
    goal: "Report {triggered_player}'s teleport information"
    steps:
      - skill_name: "GenericBukkitAPI"
        action: "get_player_location"
        entities:
          target_player: "{triggered_player}"
        description: "Query {triggered_player}'s new location in {to_world} ({to_x}, {to_y}, {to_z})"

# ❌ Avoid: Placeholder spelling errors
entities:
  target_player: "{triggerd_player}"  # Wrong! Missing 'e'
```

### 4. Callback Task Design Principles

**✅ Recommended Practices**:
- Callback tasks should not exceed 3-5 steps
- Use placeholders to pass event data
- Clear and specific goal description
- Clear dependencies between steps

**❌ Avoid**:
- Overly complex callback tasks (>10 steps)
- Creating AFK tasks again in callbacks (nested prohibition)
- Ignoring placeholder replacement causing missing information
- Callback tasks relying on expired conversation history

### 5. Performance Optimization

- ⚡ Listener itself has minimal performance overhead (only event filtering)
- 🧠 Callback mode performance depends on task complexity
- 📊 Recommended callback tasks should not exceed 3-5 steps
- ⏱️ Avoid time-consuming operations in callbacks (e.g.,大量 database queries)
- 🔄 Timely cancel unneeded tasks (avoid resource waste)

---

## 🐛 FAQ

### Q1: Why does it提示 "target player already online" when creating task?

**Reason**: You used `PLAYER_ONLINE_WATCH`, but target player is currently already online, task would trigger immediately, no monitoring significance.

**Solution**:
- If target is already online, use `PLAYER_OFFLINE_WATCH` (monitor logout) instead
- Or wait for target player to log off before creating login monitor task

**Example**:
```
Player: Watch Steve login for me
AI: Target player Steve is currently already online, PLAYER_ONLINE_WATCH task is meaningless. If you need to monitor them logout, you can say "watch Steve logout for me".
```

---

### Q2: Why is the message I receive after callback execution different from expected?

**Reason**: Callback execution passes **empty conversation history** (delayed feedback optimization), LLM can only generate message based on callback task results.

**Solution**:
- Ensure callback task goal description is clear
- Provide sufficient context in step description
- Use placeholders to pass event-related data

**Example**:
```yaml
callback_task:
  goal: "Report Steve's teleport information, including where from and where to"  # Clear goal
  steps:
    - skill_name: "GenericBukkitAPI"
      action: "get_player_location"
      entities:
        target_player: "{triggered_player}"
      description: "Query {triggered_player}'s location in {to_world} ({to_x}, {to_y}, {to_z})"  # Provide context
```

---

### Q3: Why do I receive "task creator offline, callback task cancelled"?

**Reason**: When AFK task triggered, task creator has logged off.

**Solution**:
- This is normal behavior, avoid sending messages to offline players
- Can recreate monitor task after logging back in
- Can use `/kilacraft afk query` to check if there are incomplete tasks

---

### Q4: Can I create long-term recurring tasks?

**Answer**: ❌ No.

**Reason**: AFK task system is designed as **one-time tasks**, trigger once and end.

**Example**:
```
Player: Remind me to drink water every 1 hour
AI: Sorry, AFK task system only supports one-time tasks (trigger once and end), does not support long-term recurring tasks (e.g., "remind me every 1 hour", "tell me every day at noon"). You can manually create task each time needed.
```

---

### Q5: How to view current AFK tasks?

**Method 1**: Use command
```
/kilacraft afk        # Query current task
/kilacraft afk query  # Same as above
```

**Method 2**: Through AI conversation
```
Player: What AFK tasks do I have?
AI: You currently have one AFK task:
    • Task ID: afk_a1b2c3d4_1234
    • Type: Monitor player login
    • Description: Monitor player Steve login
    • Status: Running
    • Created: 2026-04-10 14:30:00
```

---

### Q6: How to cancel AFK task?

**Method 1**: Use command
```
/kilacraft afk cancel  # Cancel current task
```

**Method 2**: Through AI conversation
```
Player: Help me cancel AFK task
AI: ✅ AFK task cancelled: Monitor player Steve login
```

**Method 3**: Auto-cancel
- Automatically cancels all tasks when creator logs off

---

## 📊 Task State Flow

```
Create Task
   ↓
PENDING (Waiting to start)
   ↓ start()
RUNNING (Running) ← Listening for events
   ↓ Event Trigger
   ├─ Notification Mode → COMPLETED (Completed)
   └─ Callback Mode → Execute Callback → COMPLETED (Completed)
   
   ↓ Manual Cancel / Creator Offline
CANCELLED (Cancelled)
```

**State Descriptions**:
- `PENDING` - Task created, waiting to start
- `RUNNING` - Listening for events
- `COMPLETED` - Event triggered, task complete
- `CANCELLED` - Manually cancelled or creator offline

---

## 🔗 Related Documentation

- [Bukkit API Reference](./Bukkit%20API%20Reference.md) - View all available Bukkit APIs
- [Bukkit Event Listener Reference](./Bukkit%20Event%20Listener%20Reference.md) - View all implemented Event listeners
- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration and usage instructions
- [System Architecture Details](./System%20Architecture%20Details.md) - Overall plugin architecture

---

> **Last Updated**: 2026-04-13  
> **Plugin Version**: 1.4.5+  
> **Implemented Listeners**: 11 (S-Tier 7 + A-Tier 4) + CUSTOM generic condition polling  
> **Document Version**: v2.0
