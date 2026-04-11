# Kilacraft-AI - AFK 挂机任务系统详解

> **版本**: v1.4.3  
> **说明**: 本文档详细介绍 AFK 挂机任务系统的架构、使用方式、调用链路和最佳实践

---

## 📖 概述

AFK 挂机任务系统是 Kilacraft-AI 插件的核心功能之一，提供**异步事件监听 + 延迟回调执行**能力。玩家可以让 AI 在后台持续监控某个事件条件，条件满足时自动执行操作并通知玩家。

### 核心特性

- ✅ **自然语言创建**：通过对话创建任务，无需记忆命令
- ✅ **双模式支持**：纯通知模式（快速响应）和回调模式（智能分析）
- ✅ **丰富的监听器**：11 种事件类型（S级 7 个 + A级 4 个）
- ✅ **多步骤回调**：支持组合多个 API 完成复杂操作
- ✅ **自动资源管理**：任务完成/取消后自动清理
- ✅ **延迟反馈优化**：避免过期上下文噪音
- ✅ **并发控制**：一人一任务，避免资源浪费

### 使用场景

```
玩家: 帮我盯着 Steve，他上线了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 上线。

[30 分钟后...]
AI: 🔔 挂机任务提醒

     Steve 已上线！他当前在主世界 (X=128, Y=64, Z=-256)。
```

---

## 🏗️ 系统架构

### 文件结构

```
skills/afktask/
├── AFKTask.java                    # 抽象基类（生命周期管理）
├── AFKTaskManager.java             # 任务管理器（CRUD + 并发控制）
├── AFKTaskSkill.java               # 内置 Skill（LLM 路由入口）
├── AFKTaskCallback.java            # 回调配置（多步骤任务链）
├── AFKTaskType.java                # 任务类型枚举（11 种）
├── AFKTaskStatus.java              # 状态枚举
├── AFKTaskListener.java            # 全局 Listener（创建者下线清理）
├── AFKTaskSkill.yml                # LLM 提示词配置
└── impl/                           # 具体实现
    ├── PlayerOnlineWatchTask.java          # S级 - 监视上线
    ├── PlayerOfflineWatchTask.java         # S级 - 监视下线
    ├── PlayerDeathWatchTask.java           # S级 - 监视死亡
    ├── PlayerTeleportWatchTask.java        # S级 - 监视传送
    ├── PlayerLevelChangeWatchTask.java     # S级 - 监视等级变化
    ├── PlayerChangedWorldWatchTask.java    # S级 - 监视世界切换
    ├── WeatherChangeWatchTask.java         # S级 - 监视天气变化
    ├── PlayerBedEnterWatchTask.java        # A级 - 监视进入床
    ├── PlayerBedLeaveWatchTask.java        # A级 - 监视离开床
    ├── PlayerRespawnWatchTask.java         # A级 - 监视重生
    └── PlayerItemBreakWatchTask.java       # A级 - 监视物品损坏
```

### 类的继承关系

```
AFKTask（抽象基类）
  ├── PlayerOnlineWatchTask（监听 PlayerJoinEvent）
  ├── PlayerOfflineWatchTask（监听 PlayerQuitEvent）
  ├── PlayerDeathWatchTask（监听 PlayerDeathEvent）
  ├── PlayerTeleportWatchTask（监听 PlayerTeleportEvent）
  ├── PlayerLevelChangeWatchTask（监听 PlayerLevelChangeEvent）
  ├── PlayerChangedWorldWatchTask（监听 PlayerChangedWorldEvent）
  ├── WeatherChangeWatchTask（监听 WeatherChangeEvent）
  ├── PlayerBedEnterWatchTask（监听 PlayerBedEnterEvent）
  ├── PlayerBedLeaveWatchTask（监听 PlayerBedLeaveEvent）
  ├── PlayerRespawnWatchTask（监听 PlayerRespawnEvent）
  └── PlayerItemBreakWatchTask（监听 PlayerItemBreakEvent）

AFKTaskSkill（实现 Skill 接口）
  └── 通过 AFKTaskManager.AFKTaskFactory 工厂创建具体任务

AFKTaskManager（管理器）
  ├── taskMap: UUID → AFKTask（一人一任务索引）
  └── taskIndex: taskId → AFKTask（任务 ID 索引）

AFKTaskCallback（回调配置）
  ├── CallbackTask（goal + steps）
  └── CallbackStep（单个步骤，含 depends_on）
```

---

## 🔄 完整调用链路

### 链路一：创建挂机任务

```
玩家消息: "帮我盯着 Steve 上线"
  ↓
ChatListener.onPlayerChat()
  ↓
AIRequestHandler.handleAIRequest()
  ↓
SkillIntentRecognizer.recognizeIntent()  ← LLM 调用 1（意图识别）
  返回 JSON:
  {
    "skill_name": "AFKTask",
    "action": "create_task",
    "entities": {
      "task_type": "PLAYER_ONLINE_WATCH",
      "target_player": "Steve"
    },
    "confidence": 0.95,
    "reasoning": "玩家要求监视 Steve 上线"
  }
  ↓
AIRequestHandler.handleSkillIntent()
  ↓
SkillManager.executeSkillByIntent()
  ↓
AFKTaskSkill.execute(context)
  ↓
handleCreateTask(context)
  ① 校验 task_type 参数
  ② 检查是否已有任务（一人一任务）
  ③ 目标在线/离线合理性检查
     - ONLINE_WATCH + 目标已在线 → 失败，建议改用 OFFLINE_WATCH
     - OFFLINE_WATCH + 目标不在线 → 失败，建议改用 ONLINE_WATCH
  ④ getTaskFactory(taskType) 获取工厂
  ⑤ AFKTaskManager.createTask()
     - 再次检查并发限制
     - 生成 taskId: "afk_" + UUID前8位 + 时间戳后4位
     - 工厂创建具体 AFKTask 子类实例
     - 注册到 taskMap + taskIndex
     - task.start() → 注册 Bukkit 事件监听器 → markRunning()
  ⑥ 返回 SkillResult.success("挂机任务已创建并启动：监视玩家 Steve 上线")
  ↓
LLMAnalysisService.analyzeResult()  ← LLM 调用 2（二次分析）
  结构化摘要:
  - 用户输入: "帮我盯着 Steve 上线"
  - 技能结果: "[SUCCESS] 挂机任务已创建并启动：监视玩家 Steve 上线"
  ↓
生成自然语言 → 发送给玩家:
  "好的！已创建挂机任务：监视玩家 Steve 上线。当 Steve 上线时，我会立即通知你。"
```

**通知流向**：玩家只收到 1 条消息（LLM 二次分析结果）。`start()` 中不再 `notifyPlayer()`（避免重复）。

---

### 链路二：事件触发 + 回调执行

```
[30 分钟后...]
目标玩家 Steve 上线
  ↓
Bukkit Event: PlayerJoinEvent
  ↓
PlayerOnlineWatchTask.onPlayerJoin(event)
  ① 检查 status == RUNNING
  ② 检查是否是目标玩家（equalsIgnoreCase）
  ③ 判断是否有回调步骤
     
     ├─ [纯通知模式]（无 callback 或 callback.steps 为空）
     │   → notifyPlayer("🔔 监视任务完成\n\n• 目标玩家：Steve\n• 状态：已上线\n\nSteve 上线了！")
     │   → complete("目标玩家 Steve 已上线，监视任务完成。")
     │
     └─ [回调模式]（有 callback.steps）
         → executeCallback("Steve")
            ① callback.getCallbackTask().toTaskPlan() 构建 TaskPlan
            ② replacePlaceholdersInTaskPlan()
               - {triggered_player} → "Steve"
               - {creator} → "任务创建者名称"
            ③ 检查创建者是否在线
            ④ 构建 SkillContext（空 entities，goal 来自 callbackTask）
            ⑤ 传入空对话历史（延迟反馈优化）
               Deque<Message> history = new ArrayDeque<>();
            ⑥ new TaskExecutor(skillManager, new LLMAnalysisService())
            ⑦ executor.executeTask(plan, context, history, goal)
               → 拓扑排序（按依赖关系排序步骤）
               → 递归串行执行每个步骤
                  - 步骤 1: GenericBukkitAPI.get_player_location
                  - 步骤 2: ...（如果有）
               → synthesizeResults() → AnalysisSummary
               → LLMAnalysisService.analyzeResult()  ← LLM 调用 3（二次分析）
               → 返回 SkillResult（message=LLM 生成的自然语言）
            ⑧ future.thenAccept(result → notifyCallbackResult("Steve", result))
               - notifyTarget 为空或 "{creator}" → notifyPlayer()
               - 否则 → Bukkit.getPlayerExact() 发送给指定玩家
               - 通知内容 = "🔔 挂机任务提醒\n\n" + MessageUtil.convertMarkdownToMinecraft(result.getMessage())
            ⑨ complete("目标玩家 Steve 已上线，回调任务已执行。")
               - 更新状态为 COMPLETED
               - cleanup() → 从 taskMap 和 taskIndex 中移除
```

---

### 链路三：玩家下线自动清理

```
创建者玩家下线
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

**特点**：不经过 LLM，直接清理资源。

---

### 链路四：手动命令操作

```
玩家输入: /kilacraft afk query
  ↓
KilacraftCommand.handleAfkCommand()
  ↓
manager.getTask(playerUUID)
  ↓
直接格式化输出（不经过 LLM）:
  "当前挂机任务：
   • 任务ID: afk_a1b2c3d4_1234
   • 类型: 监视玩家上线
   • 描述: 监视玩家 Steve 上线
   • 状态: 运行中
   • 创建时间: 2026-04-10 14:30:00"
```

```
玩家输入: /kilacraft afk cancel
  ↓
KilacraftCommand.handleAfkCommand()
  ↓
manager.cancelTask(playerUUID)
  ↓
task.stop()
  ↓
直接输出（不经过 LLM）:
  "✅ 已取消挂机任务：监视玩家 Steve 上线"
```

**特点**：命令直接操作，不经过 LLM，响应速度快。

---

## 🎯 使用方式详解

### 1. 纯通知模式

**适用场景**：只需要简单提醒，不需要额外操作。

**创建方式**：
```
玩家: 帮我盯着 Steve 上线
AI: 好的！已创建挂机任务：监视玩家 Steve 上线。当 Steve 上线时，我会立即通知你。
```

**触发通知**：
```
🔔 监视任务完成

• 目标玩家：Steve
• 状态：已上线

Steve 上线了！
```

**技术实现**：
```java
// 判断是否有回调步骤
boolean hasCallback = callback != null 
    && callback.getCallbackTask() != null 
    && callback.getCallbackTask().getSteps() != null 
    && !callback.getCallbackTask().getSteps().isEmpty();

if (!hasCallback) {
    // 纯通知模式
    notifyPlayer("🔔 监视任务完成\n\n• 目标玩家：" + triggeredPlayerName + "\n• 状态：已上线\n\n" + triggeredPlayerName + " 上线了！");
    complete("目标玩家 " + triggeredPlayerName + " 已上线，监视任务完成。");
}
```

---

### 2. 回调模式

**适用场景**：需要查询额外信息或执行多个操作。

**创建方式**：
```
玩家: 帮我盯着 Steve，他上线后查询他的位置并告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 上线，触发回调任务（2步），目标：查询 Steve 位置并通知创建者。
```

**回调配置（内部 JSON）**：
```json
{
  "callback_task": {
    "goal": "查询 Steve 的位置并报告",
    "steps": [
      {
        "skill_name": "GenericBukkitAPI",
        "action": "get_player_location",
        "entities": {
          "target_player": "{triggered_player}"
        },
        "description": "查询 {triggered_player} 的当前位置"
      }
    ]
  },
  "notify_target": "{creator}"
}
```

**触发通知**：
```
🔔 挂机任务提醒

Steve 已上线！他当前在主世界 (X=128, Y=64, Z=-256)。
```

**技术实现**：
```java
if (hasCallback) {
    // 回调模式
    executeCallback(triggeredPlayerName);
}

private void executeCallback(String triggeredPlayerName) {
    // 1. 构建 TaskPlan
    TaskPlan plan = callback.getCallbackTask().toTaskPlan();
    replacePlaceholdersInTaskPlan(plan, triggeredPlayerName);
    
    // 2. 检查创建者是否在线
    Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
    if (creatorPlayer == null || !creatorPlayer.isOnline()) {
        complete("任务创建者不在线，回调任务已取消。");
        return;
    }
    
    // 3. 构建执行上下文
    SkillContext context = new SkillContext(
        creatorPlayer, 
        callback.getCallbackTask().getGoal(), 
        Map.of()
    );
    
    // 4. 延迟反馈优化：不传入对话历史
    Deque<ConversationManager.Message> history = new ArrayDeque<>();
    
    // 5. 执行多步骤任务
    TaskExecutor executor = new TaskExecutor(
        plugin.getSkillManager(), 
        new LLMAnalysisService()
    );
    
    CompletableFuture<SkillResult> future = executor.executeTask(
        plan, context, history, callback.getCallbackTask().getGoal()
    );
    
    // 6. 处理执行结果
    future.thenAccept(result -> {
        notifyCallbackResult(triggeredPlayerName, result);
        complete("目标玩家 " + triggeredPlayerName + " 已上线，回调任务已执行。");
    });
}
```

---

## 📋 支持的事件类型

### S 级监听器（7 个）

| 任务类型 | 监听事件 | 监控目标 | 特殊占位符 |
|---------|---------|---------|-----------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | 玩家上线 | `{triggered_player}`, `{creator}` |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | 玩家下线 | `{triggered_player}`, `{creator}` |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | 玩家死亡 | `{triggered_player}`, `{creator}` |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | 玩家传送 | `{from_world}`, `{to_world}`, `{from_x}`, `{from_y}`, `{from_z}`, `{to_x}`, `{to_y}`, `{to_z}` |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | 玩家等级变化 | `{old_level}`, `{new_level}`, `{direction}` |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | 玩家切换世界 | `{from_world}`, `{to_world}` |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | 天气变化 | `{world_name}`, `{weather_state}`, `{weather_type}` |

### A 级监听器（4 个）

| 任务类型 | 监听事件 | 监控目标 | 特殊占位符 |
|---------|---------|---------|-----------|
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | 玩家进入床 | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | 玩家离开床 | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | 玩家重生 | `{x}`, `{y}`, `{z}`, `{world}` |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | 玩家物品损坏 | `{item_name}`, `{item_type}` |

> 📖 **完整事件列表**：查看 [Bukkit Event 监听器参考手册](./Bukkit-Event监听器参考手册.md) 了解所有监听器的详细说明和配置示例。

---

## 🔧 配置说明

### config.yml 配置

```yaml
# AFK 挂机任务系统配置
afk-task:
  enabled: true                    # 是否启用挂机任务系统
  max-tasks: 100                   # 全局最大任务数（预留扩展点）
  max-tasks-per-player: 1          # 每个玩家最大任务数（当前固定为 1）
```

### AFKTaskSkill.yml 配置

```yaml
description: '挂机任务系统：在后台持续监控某个事件条件，条件满足时自动执行一个完整的多步骤回调任务。当用户涉及"后台监控"、"盯着"、"一旦...就..."、"帮我看着"、"当...的时候..."等持续监控类需求时使用此技能。'

action_descriptions:
  create_task: '创建新的挂机任务。必填参数：task_type(监控类型)、target_player(监视目标玩家)。可选参数：callback(回调任务配置，仅当用户明确要求事件触发后执行额外操作时才提供)。如果用户只是要求"帮我盯着xxx上线"、"告诉我xxx上线了"等纯通知需求，不要提供callback参数。**只接受一次性任务（触发一次就结束），不接受"每隔"、"定期"、"定时"、"每天"等长期循环任务**'
  cancel_task: '取消玩家当前正在运行的挂机任务。当玩家已有挂机任务且想创建新任务时，应主动帮玩家取消旧任务后再创建新的。'
  query_task: "查询玩家当前的挂机任务状态，包括任务ID、类型、状态等信息"

hints:
  - '**重要：输出格式必须是单意图（single_intent）**：AFKTask的所有动作都必须以单意图JSON格式返回，绝对不能以多步骤任务格式返回。callback是entities的一个参数，不是独立的步骤。'
  - '**任务类型说明**：PLAYER_ONLINE_WATCH=监视玩家上线, PLAYER_OFFLINE_WATCH=监视玩家下线, PLAYER_DEATH_WATCH=监视玩家死亡, PLAYER_TELEPORT_WATCH=监视玩家传送, PLAYER_LEVEL_CHANGE_WATCH=监视玩家等级变化, PLAYER_CHANGED_WORLD_WATCH=监视玩家切换世界, WEATHER_CHANGE_WATCH=监视天气变化, PLAYER_BED_ENTER_WATCH=监视玩家进入床, PLAYER_BED_LEAVE_WATCH=监视玩家离开床, PLAYER_RESPAWN_WATCH=监视玩家重生, PLAYER_ITEM_BREAK_WATCH=监视玩家物品损坏。'
  - '**PLAYER_TELEPORT_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{from_world}/{to_world}/{from_x}/{from_y}/{from_z}/{to_x}/{to_y}/{to_z}占位符'
  - '**一次性 vs 长期任务（重要）**：本技能只支持一次性任务，即触发条件满足一次就结束。不接受长期循环任务。'
  - '**并发限制与任务替换**：每个玩家同时只能拥有一个挂机任务。如果创建任务时失败提示"已有一个正在运行的挂机任务"，应告知玩家可以使用 /kilacraft afk cancel 命令取消旧任务。'
```

---

## 💡 最佳实践

### 1. 选择合适的监听器

| 需求场景 | 推荐监听器 | 示例 |
|---------|-----------|------|
| 玩家上线/下线 | `PLAYER_ONLINE_WATCH` / `PLAYER_OFFLINE_WATCH` | "帮我盯着 Steve 上线" |
| 玩家死亡/重生 | `PLAYER_DEATH_WATCH` / `PLAYER_RESPAWN_WATCH` | "盯着 Steve，他死了告诉我" |
| 玩家移动 | `PLAYER_TELEPORT_WATCH` / `PLAYER_CHANGED_WORLD_WATCH` | "监视 Steve 传送" |
| 玩家状态变化 | `PLAYER_LEVEL_CHANGE_WATCH` | "盯着 Steve 升级" |
| 玩家睡觉 | `PLAYER_BED_ENTER_WATCH` / `PLAYER_BED_LEAVE_WATCH` | "监视 Steve 睡觉" |
| 物品相关 | `PLAYER_ITEM_BREAK_WATCH` | "盯着 Steve 的工具坏了告诉我" |
| 环境变化 | `WEATHER_CHANGE_WATCH` | "下雨了告诉我" |

### 2. 纯通知 vs 回调模式选择

**使用纯通知模式**：
```
✅ 只需要简单提醒
✅ 不需要额外数据
✅ 追求快速响应

示例："帮我盯着 Steve 上线"
```

**使用回调模式**：
```
✅ 需要查询额外信息
✅ 需要执行多个操作
✅ 需要智能分析

示例："帮我盯着 Steve，他上线后查询他的位置并传送过去"
```

### 3. 占位符使用技巧

```yaml
# ✅ 好的示例：充分利用占位符提供上下文
callback:
  callback_task:
    goal: "报告 {triggered_player} 的传送信息"
    steps:
      - skill_name: "GenericBukkitAPI"
        action: "get_player_location"
        entities:
          target_player: "{triggered_player}"
        description: "查询 {triggered_player} 在 {to_world} 的新位置 ({to_x}, {to_y}, {to_z})"

# ❌ 避免：占位符拼写错误
entities:
  target_player: "{triggerd_player}"  # 错误！少了 'e'
```

### 4. 回调任务设计原则

**✅ 推荐做法**：
- 回调任务不超过 3-5 步
- 使用占位符传递事件数据
- goal 描述清晰明确
- 步骤之间有明确的依赖关系

**❌ 避免做法**：
- 回调任务过于复杂（>10 步）
- 在回调中再次创建挂机任务（禁止嵌套）
- 忽略占位符替换导致信息缺失
- 回调任务依赖过期的对话历史

### 5. 性能优化建议

- ⚡ 监听器本身性能开销极低（仅事件过滤）
- 🧠 回调模式的性能取决于任务复杂度
- 📊 建议回调任务不超过 3-5 步
- ⏱️ 避免在回调中执行耗时操作（如大量数据库查询）
- 🔄 及时取消不需要的任务（避免资源浪费）

---

## 🐛 常见问题

### Q1: 为什么我创建任务时提示"目标玩家已在线"？

**原因**：你使用了 `PLAYER_ONLINE_WATCH`，但目标玩家当前已经在线，任务会立即触发，没有监视意义。

**解决方案**：
- 如果目标已在线，改用 `PLAYER_OFFLINE_WATCH`（监视下线）
- 或等待目标玩家下线后再创建上线监视任务

**示例**：
```
玩家: 帮我盯着 Steve 上线
AI: 目标玩家 Steve 当前已在线，PLAYER_ONLINE_WATCH 任务无意义。如果需要监视 TA 下线，可以说"帮我盯着 Steve 下线"。
```

---

### Q2: 为什么回调执行后收到的消息和预期不一样？

**原因**：回调执行时传入的是**空对话历史**（延迟反馈优化），LLM 只能基于回调任务的结果生成消息。

**解决方案**：
- 确保回调任务的 goal 描述清晰
- 在步骤 description 中提供足够的上下文
- 使用占位符传递事件相关数据

**示例**：
```yaml
callback_task:
  goal: "报告 Steve 的传送信息，包括从哪来到哪去"  # 清晰的 goal
  steps:
    - skill_name: "GenericBukkitAPI"
      action: "get_player_location"
      entities:
        target_player: "{triggered_player}"
      description: "查询 {triggered_player} 在 {to_world} 的位置 ({to_x}, {to_y}, {to_z})"  # 提供上下文
```

---

### Q3: 为什么我收到"任务创建者不在线，回调任务已取消"？

**原因**：挂机任务触发时，任务创建者已经下线。

**解决方案**：
- 这是正常行为，避免向离线玩家发送消息
- 上线后可以重新创建监视任务
- 可以使用 `/kilacraft afk query` 查看是否有未完成的任务

---

### Q4: 可以创建长期循环任务吗？

**答案**：❌ 不可以。

**原因**：挂机任务系统设计为**一次性任务**，触发一次就结束。

**示例**：
```
玩家: 每隔 1 小时提醒我喝水
AI: 抱歉，挂机任务系统只支持一次性任务（触发一次就结束），不支持长期循环任务（如"每隔1小时提醒我"、"每天中午告诉我"）。你可以每次需要时手动创建任务。
```

---

### Q5: 如何查看当前的挂机任务？

**方法 1**：使用命令
```
/kilacraft afk        # 查询当前任务
/kilacraft afk query  # 同上
```

**方法 2**：通过 AI 对话
```
玩家: 我有什么挂机任务？
AI: 你当前有一个挂机任务：
    • 任务ID: afk_a1b2c3d4_1234
    • 类型: 监视玩家上线
    • 描述: 监视玩家 Steve 上线
    • 状态: 运行中
    • 创建时间: 2026-04-10 14:30:00
```

---

### Q6: 如何取消挂机任务？

**方法 1**：使用命令
```
/kilacraft afk cancel  # 取消当前任务
```

**方法 2**：通过 AI 对话
```
玩家: 帮我取消挂机任务
AI: ✅ 已取消挂机任务：监视玩家 Steve 上线
```

**方法 3**：自动取消
- 创建者下线时自动取消所有任务

---

## 📊 任务状态流转

```
创建任务
   ↓
PENDING（待启动）
   ↓ start()
RUNNING（运行中）← 正在监听事件
   ↓ 事件触发
   ├─ 纯通知模式 → COMPLETED（已完成）
   └─ 回调模式 → 执行回调 → COMPLETED（已完成）
   
   ↓ 手动取消 / 创建者下线
CANCELLED（已取消）
```

**状态说明**：
- `PENDING` - 任务已创建，等待启动
- `RUNNING` - 正在监听事件
- `COMPLETED` - 事件触发，任务完成
- `CANCELLED` - 手动取消或创建者下线

---

## 🔗 相关文档

- [Bukkit API 参考手册](./Bukkit-API参考手册.md) - 查看所有可用的 Bukkit API
- [Bukkit Event 监听器参考手册](./Bukkit-Event监听器参考手册.md) - 查看所有已实现的 Event 监听器
- [服主指南](./服主指南.md) - 完整的配置和使用说明
- [系统架构详解](./系统架构详解.md) - 插件整体架构

---

> **最后更新**: 2026-04-10  
> **插件版本**: 1.4.3+  
> **已实现监听器**: 11 个（S级 7 个 + A级 4 个）  
> **文档版本**: v1.0
