# Kilacraft-AI - Bukkit Event Listener Reference

> **Version**: v1.4.3  
> **Description**: This document provides detailed information about all implemented Bukkit Event listeners (AFK Task System), including configuration examples and usage scenarios

---

## 📊 Bukkit Event Listener Quick Reference

### ✅ Implemented Listeners (11)

| Task Type | Event | Monitor Target | Level | Status |
|-----------|-------|----------------|-------|--------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | Player login | S-Tier | ✅ Implemented |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | Player logout | S-Tier | ✅ Implemented |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | Player death | S-Tier | ✅ Implemented |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | Player teleport | S-Tier | ✅ Implemented |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | Player level change | S-Tier | ✅ Implemented |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | Player world change | S-Tier | ✅ Implemented |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | Weather change | S-Tier | ✅ Implemented |
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | Player enter bed | A-Tier | ✅ Implemented |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | Player leave bed | A-Tier | ✅ Implemented |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | Player respawn | A-Tier | ✅ Implemented |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | Player item break | A-Tier | ✅ Implemented |

**Statistics**: **11** implemented listeners (S-Tier 7 + A-Tier 4)

---

## 🎯 Listener Details

### 1. PLAYER_ONLINE_WATCH - Monitor Player Login

**Event**: `PlayerJoinEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerOnlineWatchTask.java`

#### Description
Monitors specified player login events. Triggers notification or callback task when the target player joins the server.

#### Use Cases
- "Watch Steve for me, tell me when he comes online"
- "Monitor PlayerA, query his location and teleport to him after he logs in"
- "Watch for admin login, send me a message when they come online"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name (player being monitored) |
| `callback` | JSON | ❌ | Callback configuration (AFKTaskCallback format) |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator

#### Usage Examples

**Notification Mode**:
```
Player: Watch Steve for me, tell me when he comes online
AI: Done! AFK task created: Monitor player Steve login. I will notify you immediately when Steve comes online.
```

**Callback Mode**:
```
Player: Watch Steve, query his location and tell me after he logs in
AI: Done! AFK task created: Monitor player Steve login, trigger callback task (2 steps), goal: Query Steve's location and notify creator.
```

#### Special Logic
- ✅ If target player is **already online**, task creation will fail, suggest using `PLAYER_OFFLINE_WATCH` instead
- ✅ Triggers immediately when target player logs in, task auto-completes and cleans up

---

### 2. PLAYER_OFFLINE_WATCH - Monitor Player Logout

**Event**: `PlayerQuitEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerOfflineWatchTask.java`

#### Description
Monitors specified player logout events. Triggers notification or callback task when the target player leaves the server.

#### Use Cases
- "Watch Steve for me, tell me when he logs off"
- "Monitor PlayerA, record his last location after he logs off"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator

#### Usage Example
```
Player: Watch Steve for me, tell me when he logs off
AI: Done! AFK task created: Monitor player Steve logout. I will notify you when Steve leaves the server.
```

#### Special Logic
- ✅ If target player is **offline**, task creation will fail, suggest using `PLAYER_ONLINE_WATCH` instead
- ⚠️ Cannot execute callbacks requiring target player participation after logout (but can execute query operations)

---

### 3. PLAYER_DEATH_WATCH - Monitor Player Death

**Event**: `PlayerDeathEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerDeathWatchTask.java`

#### Description
Monitors specified player death events. Triggers notification or callback task when the target player dies.

#### Use Cases
- "Watch Steve for me, tell me when he dies"
- "Monitor PlayerA, query death coordinates and record after he dies"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator

#### Usage Example
```
Player: Watch Steve for me, tell me when he dies
AI: Done! AFK task created: Monitor player Steve death. I will notify you immediately when Steve dies.
```

---

### 4. PLAYER_TELEPORT_WATCH - Monitor Player Teleport

**Event**: `PlayerTeleportEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerTeleportWatchTask.java`

#### Description
Monitors specified player teleport events. Triggers notification or callback task when the target player teleports. Can obtain teleport source and destination coordinates.

#### Use Cases
- "Watch Steve for me, tell me when he teleports"
- "Monitor PlayerA, query his new location after he teleports"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{from_world}` - Source world name
- `{to_world}` - Destination world name
- `{from_x}` / `{from_y}` / `{from_z}` - Source coordinates
- `{to_x}` / `{to_y}` / `{to_z}` - Destination coordinates

#### Usage Examples

**Notification Mode**:
```
Player: Watch Steve for me, tell me when he teleports
AI: Done! AFK task created: Monitor player Steve teleport. I will notify you when Steve teleports.
```

**Callback Mode (with placeholders)**:
```
Player: Watch Steve, tell me where he went after he teleports
AI: Done! AFK task created: Monitor player Steve teleport, trigger callback task, goal: Report teleport location.
```

Callback task can use:
```yaml
steps:
  - skill_name: "GenericBukkitAPI"
    action: "get_player_location"
    entities:
      target_player: "{triggered_player}"
    description: "Query {triggered_player}'s location in {to_world} ({to_x}, {to_y}, {to_z})"
```

---

### 5. PLAYER_LEVEL_CHANGE_WATCH - Monitor Player Level Change

**Event**: `PlayerLevelChangeEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerLevelChangeWatchTask.java`

#### Description
Monitors specified player level change events. Triggers notification or callback task when the target player levels up or down.

#### Use Cases
- "Watch Steve for me, tell me when he levels up"
- "Monitor PlayerA, query his new level after level change"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{old_level}` - Level before change
- `{new_level}` - Level after change
- `{direction}` - Change direction ("level up" or "level down")

#### Usage Example
```
Player: Watch Steve for me, tell me when he levels up
AI: Done! AFK task created: Monitor player Steve level change. I will notify you when Steve levels up or down.
```

---

### 6. PLAYER_CHANGED_WORLD_WATCH - Monitor Player World Change

**Event**: `PlayerChangedWorldEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/PlayerChangedWorldWatchTask.java`

#### Description
Monitors specified player world change events. Triggers notification or callback task when the target player teleports from one world to another. Supports all world types (including custom worlds created by plugins like Multiverse-Core).

#### Use Cases
- "Watch Steve for me, tell me when he goes to the Nether"
- "Monitor PlayerA, query which world he went to after world change"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{from_world}` - Source world name
- `{to_world}` - Destination world name

#### Usage Example
```
Player: Watch Steve for me, tell me when he goes to the Nether
AI: Done! AFK task created: Monitor player Steve world change. I will notify you when Steve teleports from current world to another world.
```

#### Technical Notes
- ✅ Supports all world types: Overworld (NORMAL), Nether (NETHER), End (THE_END)
- ✅ Supports custom worlds: Worlds created by Multiverse-Core, MultiWorld, and other plugins
- ✅ Obtains source world via `event.getFrom()`, destination world via `player.getWorld()`

---

### 7. WEATHER_CHANGE_WATCH - Monitor Weather Change

**Event**: `WeatherChangeEvent`  
**Tier**: S-Tier  
**File**: `skills/afktask/impl/WeatherChangeWatchTask.java`

#### Description
Monitors world weather change events. Triggers notification or callback task when weather changes in the specified world.

#### Use Cases
- "Tell me when it rains"
- "Monitor the overworld, query current weather after weather change"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_world` | String | ❌ | Target world name (empty monitors player's current world) |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event (here, the creator)
- `{creator}` - Name of the task creator
- `{world_name}` - World name where weather changed
- `{weather_state}` - Weather state description ("Clear", "Rain", "Thunder")
- `{weather_type}` - Weather type (CLEAR, RAIN, THUNDER)

#### Usage Examples

**Monitor Current World**:
```
Player: Tell me when it rains
AI: Done! AFK task created: Monitor weather change (current world). I will notify you when weather changes.
```

**Monitor Specific World**:
```
Player: Watch the overworld weather for me, tell me when it changes
AI: Done! AFK task created: Monitor weather change (target world: world). I will notify you when overworld weather changes.
```

#### Special Logic
- ⚠️ This is the only **world-level listener** (not player-level)
- ✅ If `target_world` is not specified, defaults to monitoring the task creator's current world
- ✅ Can monitor weather changes in any world

---

### 8. PLAYER_BED_ENTER_WATCH - Monitor Player Enter Bed

**Event**: `PlayerBedEnterEvent`  
**Tier**: A-Tier  
**File**: `skills/afktask/impl/PlayerBedEnterWatchTask.java`

#### Description
Monitors specified player enter bed (sleep) events. Triggers notification or callback task when the target player enters a bed.

#### Use Cases
- "Watch Steve for me, tell me when he sleeps"
- "Monitor PlayerA, query his location after he sleeps"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{x}` / `{y}` / `{z}` - Bed location coordinates
- `{world}` - World name where bed is located

#### Usage Example
```
Player: Watch Steve for me, tell me when he sleeps
AI: Done! AFK task created: Monitor player Steve enter bed. I will notify you when Steve sleeps.
```

---

### 9. PLAYER_BED_LEAVE_WATCH - Monitor Player Leave Bed

**Event**: `PlayerBedLeaveEvent`  
**Tier**: A-Tier  
**File**: `skills/afktask/impl/PlayerBedLeaveWatchTask.java`

#### Description
Monitors specified player leave bed events. Triggers notification or callback task when the target player leaves a bed (wakes up).

#### Use Cases
- "Watch Steve for me, tell me when he wakes up"
- "Monitor PlayerA, query his location after he wakes up"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{x}` / `{y}` / `{z}` - Bed location coordinates
- `{world}` - World name where bed is located

#### Usage Example
```
Player: Watch Steve for me, tell me when he wakes up
AI: Done! AFK task created: Monitor player Steve leave bed. I will notify you when Steve wakes up.
```

---

### 10. PLAYER_RESPAWN_WATCH - Monitor Player Respawn

**Event**: `PlayerRespawnEvent`  
**Tier**: A-Tier  
**File**: `skills/afktask/impl/PlayerRespawnWatchTask.java`

#### Description
Monitors specified player respawn events. Triggers notification or callback task when the target player respawns.

#### Use Cases
- "Watch Steve for me, tell me when he respawns"
- "Monitor PlayerA, query his location after he respawns"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{x}` / `{y}` / `{z}` - Respawn point coordinates
- `{world}` - World name where respawn point is located

#### Usage Example
```
Player: Watch Steve for me, tell me when he respawns
AI: Done! AFK task created: Monitor player Steve respawn. I will notify you when Steve respawns.
```

---

### 11. PLAYER_ITEM_BREAK_WATCH - Monitor Player Item Break

**Event**: `PlayerItemBreakEvent`  
**Tier**: A-Tier  
**File**: `skills/afktask/impl/PlayerItemBreakWatchTask.java`

#### Description
Monitors specified player item break events. Triggers notification or callback task when the target player's item breaks.

#### Use Cases
- "Watch Steve for me, tell me when his tool breaks"
- "Monitor PlayerA, query his inventory after his equipment breaks"

#### Configuration Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `target_player` | String | ✅ | Target player name |
| `callback` | JSON | ❌ | Callback configuration |

#### Available Placeholders
- `{triggered_player}` - Name of the player who triggered the event
- `{creator}` - Name of the task creator
- `{item_name}` - Broken item name (custom name or type name)
- `{item_type}` - Item type (e.g., "DIAMOND_SWORD")

#### Usage Examples

**Notification Mode**:
```
Player: Watch Steve for me, tell me when his tool breaks
AI: Done! AFK task created: Monitor player Steve item break. I will notify you when Steve's item breaks.
```

**Callback Mode**:
```
Player: Watch Steve, query his inventory after his equipment breaks
AI: Done! AFK task created: Monitor player Steve item break, trigger callback task, goal: Query inventory status.
```

Callback task can use:
```yaml
steps:
  - skill_name: "GenericBukkitAPI"
    action: "get_player_hand_item"
    entities:
      target_player: "{triggered_player}"
    description: "Query {triggered_player}'s main hand item (his {item_name} just broke)"
```

---

## 🔧 Common Listener Features

### Dual Mode Support

All listeners support two operation modes:

#### 1. Notification Mode
Sends notification message directly to task creator after event triggers, without LLM secondary analysis.

**Features**:
- ⚡ Fast response
- 📝 Uses preset notification template
- 💬 Suitable for simple scenarios

**Example**:
```
Player: Watch Steve for me, tell me when he comes online
→ Event triggers → "🔔 Monitor Task Complete\n\n• Target Player: Steve\n• Status: Online\n\nSteve has come online!"
```

#### 2. Callback Mode
Executes multi-step callback task after event triggers, sends results after LLM secondary analysis.

**Features**:
- 🧠 Intelligent analysis
- 🔗 Supports multi-step task chains
- 📊 Can combine multiple APIs
- 💬 Suitable for complex scenarios

**Example**:
```
Player: Watch Steve, query his location and teleport to him after he logs in
→ Event triggers → Execute callback task (query location API) → LLM analysis → "Steve has come online! He is currently at X=128, Y=64, Z=-256, World=world"
```

### Task Lifecycle

```
PENDING → start() → RUNNING → Event Trigger → complete() → COMPLETED
                                    ↓
                              cleanup() → Remove from taskMap + taskIndex
```

**Detailed Explanation**:
1. **PENDING** - Task created, waiting to start
2. **RUNNING** - Listening for events
3. **COMPLETED** - Event triggered, task complete
4. **CANCELLED** - Manually cancelled or creator offline

### Resource Cleanup

All listeners automatically clean up resources in the following scenarios:
- ✅ Task complete (event triggered)
- ✅ Manual cancellation (`/kilacraft afk cancel`)
- ✅ Creator offline (`AFKTaskListener` auto-cleanup)

Cleanup operation:
```java
@Override
public void onStop() {
    if (listenerRegistered) {
        HandlerList.unregisterAll(this);
        listenerRegistered = false;
    }
}
```

### Concurrency Control

- 🔒 **One Task Per Player**: Each player can only have one AFK task at a time
- 📋 **Global Queue**: Supports task queue (reserved extension point)
- ⚠️ **Conflict Detection**: Checks for existing tasks when creating

### Delayed Feedback Optimization

Callback execution passes **empty conversation history**:

**Reason**: AFK task may trigger after a long time, original conversation context may be flooded, injecting history would create noise.

**Implementation**:
```java
Deque<ConversationManager.Message> history = new ArrayDeque<>();  // Empty history
executor.executeTask(plan, context, history, goal);
```

---

## 📝 Configuration Example

### AFKTaskSkill.yml Configuration

```yaml
description: 'AFK Task System: Continuously monitor an event condition in the background, automatically execute a complete multi-step callback task when condition is met.'

action_descriptions:
  create_task: 'Create new AFK task. Required parameters: task_type (monitor type), target_player (monitor target player). Optional parameter: callback (callback task configuration).'
  cancel_task: 'Cancel player''s currently running AFK task.'
  query_task: 'Query player''s current AFK task status.'

hints:
  - '**Task Type Description**: PLAYER_ONLINE_WATCH=Monitor player login, PLAYER_OFFLINE_WATCH=Monitor player logout, PLAYER_DEATH_WATCH=Monitor player death, PLAYER_TELEPORT_WATCH=Monitor player teleport, PLAYER_LEVEL_CHANGE_WATCH=Monitor player level change, PLAYER_CHANGED_WORLD_WATCH=Monitor player world change, WEATHER_CHANGE_WATCH=Monitor weather change, PLAYER_BED_ENTER_WATCH=Monitor player enter bed, PLAYER_BED_LEAVE_WATCH=Monitor player leave bed, PLAYER_RESPAWN_WATCH=Monitor player respawn, PLAYER_ITEM_BREAK_WATCH=Monitor player item break.'
  - '**PLAYER_TELEPORT_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {from_world}/{to_world}/{from_x}/{from_y}/{from_z}/{to_x}/{to_y}/{to_z}'
  - '**PLAYER_LEVEL_CHANGE_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {old_level}/{new_level}/{direction}'
  - '**PLAYER_CHANGED_WORLD_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {from_world}/{to_world}'
  - '**WEATHER_CHANGE_WATCH Optional Parameters**: target_world (target world name, empty monitors player''s current world). callback is optional, placeholders available in callback: {world_name}/{weather_state}/{weather_type}'
  - '**PLAYER_BED_ENTER_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {x}/{y}/{z}/{world}'
  - '**PLAYER_BED_LEAVE_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {x}/{y}/{z}/{world}'
  - '**PLAYER_RESPAWN_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {x}/{y}/{z}/{world}'
  - '**PLAYER_ITEM_BREAK_WATCH Required Parameters**: target_player (target player name). callback is optional, placeholders available in callback: {item_name}/{item_type}'
```

---

## 🎓 Best Practices

### 1. Choose the Right Listener

| Requirement | Recommended Listener |
|-------------|---------------------|
| Player login/logout | `PLAYER_ONLINE_WATCH` / `PLAYER_OFFLINE_WATCH` |
| Player death/respawn | `PLAYER_DEATH_WATCH` / `PLAYER_RESPAWN_WATCH` |
| Player movement | `PLAYER_TELEPORT_WATCH` / `PLAYER_CHANGED_WORLD_WATCH` |
| Player status change | `PLAYER_LEVEL_CHANGE_WATCH` |
| Player sleeping | `PLAYER_BED_ENTER_WATCH` / `PLAYER_BED_LEAVE_WATCH` |
| Item related | `PLAYER_ITEM_BREAK_WATCH` |
| Environment change | `WEATHER_CHANGE_WATCH` |

### 2. Notification Mode vs Callback Mode

**Use Notification Mode**:
- ✅ Only need simple reminder
- ✅ No additional data needed
- ✅追求 fast response

**Use Callback Mode**:
- ✅ Need to query additional information
- ✅ Need to execute multiple operations
- ✅ Need intelligent analysis

### 3. Placeholder Usage Tips

```yaml
# ✅ Good example: Use placeholders to provide context
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

### 4. Performance Considerations

- ⚡ Listener itself has minimal performance overhead (only event filtering)
- 🧠 Callback mode performance depends on task complexity
- 📊 Recommended callback tasks should not exceed 3-5 steps
- ⏱️ Avoid time-consuming operations in callbacks

---

## 🔗 Related Documentation

- [Bukkit API Reference](./Bukkit%20API%20Reference.md) - View all available Bukkit APIs
- [AFK Task System Guide](./AFK%20Task%20System%20Guide.md) - Complete guide to AFK task system
- [Server Owner Guide](./Server%20Owner%20Guide.md) - Complete configuration and usage instructions

---

> **Last Updated**: 2026-04-10  
> **Plugin Version**: 1.4.3+  
> **Implemented Listeners**: 11 (S-Tier 7 + A-Tier 4)
