# Kilacraft-AI - Known Bugs

> **Last Updated**: 2026-05-06  
> **Description**: Documents confirmed but unfixed bugs, including reproduction steps, root cause analysis, and planned fix approaches

---

## BUG-001: AI Believes AFK Task Is Still Running After Completion

**Status**: 🔄 Confirmed / Pending Fix  
**Priority**: Medium  
**Affected Versions**: v1.4.3 and all prior versions  
**Impact**: All players using the AFK task feature

---

### Problem Description

After a player creates an AFK task (e.g., "Watch Hub for me, report his location when he comes online") and the task completes (Hub comes online and the player has been notified), the AI in subsequent conversations still believes the task is running based on chat history, giving incorrect responses.

**Observed Behavior**:
```
[19:36] Player: /ai Watch Hub, report his location when he comes online
[19:36] AI: Done! I've set up a monitor task. I'll notify you as soon as Hub comes online.
[19:38] [System] Hub online → AFK task completed → Player notified of Hub's location
[19:39] Player: /ai Good evening!
[19:39] AI: Good evening! Hub hasn't come online yet, but I'm still monitoring.  ← ❌ Wrong! Hub is online, task completed
```

**Expected Behavior**: AI should know the AFK task has completed and not mention "still monitoring" in its response.

---

### Reproduction Steps

1. Player A creates an AFK task: `/ai Watch [PlayerB], tell me when they come online`
2. Wait for target Player B to come online, AFK task triggers and completes, Player A receives notification
3. Player A continues conversation: `/ai Good evening!`
4. AI incorrectly believes the task is still running in its response

---

### Root Cause Analysis

#### Call Chain Analysis

```
Player sends message "Good evening!"
  ↓
AIRequestHandler.handleNormalAIRequest()
  ↓
GenericLLMProvider.processRequest()
  → Uses configManager.getSystemPrompt() as system prompt
  → Injects conversation history (contains AFK task creation history)
  ↓
LLM sees this context:
  System: "You are a Minecraft game assistant..."
  History[1] (user): "Watch Hub, report his location when he comes online"    ← Task creation history preserved
  History[2] (assistant): "Done! I've set up a monitor task..."               ← AI response preserved
  User: "Good evening!"
  ↓
LLM inference: History mentions "monitor task", no "task completed" info → responds "still monitoring"
```

#### Core Reasons

1. **AFK tasks execute asynchronously with delay**: Tasks may trigger minutes or even hours after creation
2. **No "task completed" record in conversation history**: Task completion notifications are sent independently, not written to conversation history
3. **System prompt doesn't include real-time status**: `system_prompt` is a static configuration, doesn't include current AFK task status
4. **LLM can only infer status from conversation history**: History has "create task" but no "task completed", leading to incorrect inference

---

### Constraints

The following constraints must be satisfied when designing a fix:

| Constraint | Reason |
|------------|--------|
| ❌ **Cannot delay-adding conversation context** | AFK tasks are asynchronous and may complete after multiple conversation rounds. Delayed insertion of "task completed" messages would pollute conversation history and disrupt normal dialogue flow |
| ❌ **Cannot delete AFK task conversations from history** | If the "Watch Hub for me" history is deleted, subsequent conversations like "cancel that task" or "watch someone else instead" would lose context, causing intent recognition failures |
| ❌ **Cannot modify saved conversation history** | Conversation history represents actual player interactions; modifications could cause unpredictable issues |

---

### Planned Fix: Dynamic AFK Task Status Injection in System Prompt

**Core Approach**: Similar to knowledge base retrieval (RAG), dynamically query the player's current AFK task status when building the system prompt for each LLM request, and inject it as additional information in the system prompt. This information is **not saved to conversation history** and only takes effect in the current request's system message.

#### Technical Design

**Injection Point**: `GenericLLMProvider.processRequestWithCustomSystemPrompt()` method, appending AFK status information when building the system prompt.

**Injection Content Examples**:

When task is running:
```
You are a Minecraft game assistant...

【AFK Task Status】
Currently running AFK task: Monitor player Hub login (task created at 19:36)
```

When task is completed:
```
You are a Minecraft game assistant...

【AFK Task Status】
No running AFK tasks. (If previous conversations mentioned an AFK task, it has already completed or been cancelled)
```

When no task exists (never created):
```
You are a Minecraft game assistant...
(No additional information injected)
```

#### Implementation Key Points

1. **In `GenericLLMProvider`**: Append AFK task status when building `systemPrompt`
   ```java
   // Pseudocode
   String systemPrompt = customSystemPrompt.replace("{player}", playerName);
   
   // Dynamically inject AFK task status (player requests only)
   if (playerName != null && !"IntentRecognizer".equals(playerName)) {
       String afkStatus = buildAfkStatus(playerName);
       if (afkStatus != null) {
           systemPrompt += "\n\n" + afkStatus;
       }
   }
   ```

2. **Status Query**: Get current task via `plugin.getAfkTaskManager().getTask(uuid)`
   - Has task → inject running task info
   - No task but mentioned in history → inject "task completed or cancelled"
   - No task and never mentioned → no injection

3. **Optimization**: Only inject "task completed" prompt when AFK task-related keywords appear in conversation history, avoiding unnecessary information in unrelated conversations

#### Advantages

- ✅ **No conversation history pollution** - System prompt is dynamically generated each time, not saved to history
- ✅ **No impact on intent recognition** - AFK task conversations in history are fully preserved
- ✅ **Real-time accuracy** - Latest status checked on every request
- ✅ **Lightweight** - Only requires one `manager.hasTask(uuid)` query, no performance overhead
- ✅ **No impact on non-player requests** - Console requests and intent recognition requests skip injection
- ✅ **Consistent with existing architecture** - Similar to knowledge base retrieval injection, unified architectural style

#### Risk Assessment

- ⚠️ **Increased token consumption**: Each request injects approximately 50-100 additional tokens of AFK status info
- ⚠️ **Detecting "AFK task mentioned in history" requires scanning history**: Can be implemented via simple keyword matching

---

### Related Code Files

| File | Description |
|------|-------------|
| `GenericLLMProvider.java` | System prompt construction and LLM request sending |
| `AIRequestHandler.java` | Normal AI request handling entry point |
| `ConfigManager.java` | `systemPrompt` configuration management |
| `AFKTaskManager.java` | AFK task status management (`hasTask()`, `getTask()`) |
| `ConversationManager.java` | Conversation history management |

---

## Changelog

| Date | Action | Description |
|------|--------|-------------|
| 2026-04-10 | Created | Initial BUG-001 record |
