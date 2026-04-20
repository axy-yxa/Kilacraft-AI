# Kilacraft-AI - Bukkit Event 监听器参考手册

> **版本**: v1.4.5  
> **说明**: 本文档提供所有已实现的 Bukkit Event 监听器（AFK 挂机任务）的详细说明、配置示例和使用场景

---

## 📊 Bukkit Event 监听器快速参考表

### ✅ 已实现的监听器（11 个）

| 任务类型 | 监听事件 | 监控目标 | 级别 | 状态 |
|---------|---------|---------|------|------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | 玩家上线 | S级 | ✅ 已实现 |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | 玩家下线 | S级 | ✅ 已实现 |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | 玩家死亡 | S级 | ✅ 已实现 |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | 玩家传送 | S级 | ✅ 已实现 |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | 玩家等级变化 | S级 | ✅ 已实现 |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | 玩家切换世界 | S级 | ✅ 已实现 |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | 天气变化 | S级 | ✅ 已实现 |
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | 玩家进入床 | A级 | ✅ 已实现 |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | 玩家离开床 | A级 | ✅ 已实现 |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | 玩家重生 | A级 | ✅ 已实现 |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | 玩家物品损坏 | A级 | ✅ 已实现 |

**统计**：已实现 **11 个**监听器（S级 7 个 + A级 4 个）

---

## 🎯 监听器详细说明

### 1. PLAYER_ONLINE_WATCH - 监视玩家上线

**事件**: `PlayerJoinEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerOnlineWatchTask.java`

#### 功能说明
监视指定玩家上线事件，当目标玩家加入服务器时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他上线了告诉我"
- "监视玩家A，他上线后查询他的位置并传送到他身边"
- "盯着管理员上线，上线后帮我发送消息"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称（被监视的玩家） |
| `callback` | JSON | ❌ | 回调配置（AFKTaskCallback 格式） |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称

#### 使用示例

**纯通知模式**：
```
玩家: 帮我盯着 Steve 上线
AI: 好的！已创建挂机任务：监视玩家 Steve 上线。当 Steve 上线时，我会立即通知你。
```

**回调模式**：
```
玩家: 帮我盯着 Steve，他上线后查询他的位置并告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 上线，触发回调任务（2步），目标：查询 Steve 位置并通知创建者。
```

#### 特殊逻辑
- ✅ 如果目标玩家**已在线**，创建任务时会失败，建议改用 `PLAYER_OFFLINE_WATCH`
- ✅ 目标玩家上线后立即触发，任务自动完成并清理

---

### 2. PLAYER_OFFLINE_WATCH - 监视玩家下线

**事件**: `PlayerQuitEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerOfflineWatchTask.java`

#### 功能说明
监视指定玩家下线事件，当目标玩家离开服务器时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他下线了告诉我"
- "监视玩家A，他下线后记录他的最后位置"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称

#### 使用示例
```
玩家: 帮我盯着 Steve 下线
AI: 好的！已创建挂机任务：监视玩家 Steve 下线。当 Steve 离开服务器时，我会通知你。
```

#### 特殊逻辑
- ✅ 如果目标玩家**不在线**，创建任务时会失败，建议改用 `PLAYER_ONLINE_WATCH`
- ⚠️ 玩家下线后无法执行需要目标玩家参与的回调（但可以执行查询类操作）

---

### 3. PLAYER_DEATH_WATCH - 监视玩家死亡

**事件**: `PlayerDeathEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerDeathWatchTask.java`

#### 功能说明
监视指定玩家死亡事件，当目标玩家死亡时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他死了告诉我"
- "监视玩家A，他死后查询死亡坐标并记录"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称

#### 使用示例
```
玩家: 帮我盯着 Steve，他死了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 死亡。当 Steve 死亡时，我会立即通知你。
```

---

### 4. PLAYER_TELEPORT_WATCH - 监视玩家传送

**事件**: `PlayerTeleportEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerTeleportWatchTask.java`

#### 功能说明
监视指定玩家传送事件，当目标玩家传送时触发通知或回调任务。可获取传送起点和终点坐标。

#### 使用场景
- "帮我盯着 Steve，他传送了告诉我"
- "监视玩家A，他传送后查询他的新位置"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{from_world}` - 传送前世界名称
- `{to_world}` - 传送后世界名称
- `{from_x}` / `{from_y}` / `{from_z}` - 传送前坐标
- `{to_x}` / `{to_y}` / `{to_z}` - 传送后坐标

#### 使用示例

**纯通知模式**：
```
玩家: 帮我盯着 Steve，他传送了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 传送。当 Steve 传送时，我会通知你。
```

**回调模式（使用占位符）**：
```
玩家: 盯着 Steve，他传送后告诉我他去了哪里
AI: 好的！已创建挂机任务：监视玩家 Steve 传送，触发回调任务，目标：报告传送位置。
```

回调任务中可以使用：
```yaml
steps:
  - skill_name: "GenericBukkitAPI"
    action: "get_player_location"
    entities:
      target_player: "{triggered_player}"
    description: "查询 {triggered_player} 在 {to_world} 的位置 ({to_x}, {to_y}, {to_z})"
```

---

### 5. PLAYER_LEVEL_CHANGE_WATCH - 监视玩家等级变化

**事件**: `PlayerLevelChangeEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerLevelChangeWatchTask.java`

#### 功能说明
监视指定玩家等级变化事件，当目标玩家升级或降级时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他升级了告诉我"
- "监视玩家A，他等级变化后查询他的新等级"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{old_level}` - 变化前的等级
- `{new_level}` - 变化后的等级
- `{direction}` - 变化方向（"升级" 或 "降级"）

#### 使用示例
```
玩家: 帮我盯着 Steve，他升级了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 等级变化。当 Steve 升级或降级时，我会通知你。
```

---

### 6. PLAYER_CHANGED_WORLD_WATCH - 监视玩家切换世界

**事件**: `PlayerChangedWorldEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/PlayerChangedWorldWatchTask.java`

#### 功能说明
监视指定玩家切换世界事件，当目标玩家从一个世界传送到另一个世界时触发通知或回调任务。支持所有世界类型（包括 Multiverse-Core 等插件创建的自定义世界）。

#### 使用场景
- "帮我盯着 Steve，他去了下界告诉我"
- "监视玩家A，他切换世界后查询他去了哪个世界"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{from_world}` - 来源世界名称
- `{to_world}` - 目标世界名称

#### 使用示例
```
玩家: 帮我盯着 Steve，他去了下界告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 切换世界。当 Steve 从当前世界传送到其他世界时，我会通知你。
```

#### 技术说明
- ✅ 支持所有世界类型：主世界（NORMAL）、下界（NETHER）、末地（THE_END）
- ✅ 支持自定义世界：Multiverse-Core、MultiWorld 等插件创建的世界
- ✅ 通过 `event.getFrom()` 获取来源世界，通过 `player.getWorld()` 获取目标世界

---

### 7. WEATHER_CHANGE_WATCH - 监视天气变化

**事件**: `WeatherChangeEvent`  
**级别**: S级  
**文件**: `skills/afktask/impl/WeatherChangeWatchTask.java`

#### 功能说明
监视世界天气变化事件，当指定世界的天气发生变化时触发通知或回调任务。

#### 使用场景
- "下雨了告诉我"
- "监视主世界，天气变化后查询当前天气"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_world` | String | ❌ | 目标世界名称（为空则监视玩家当前世界） |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称（此处为创建者）
- `{creator}` - 任务创建者名称
- `{world_name}` - 发生天气变化的世界名称
- `{weather_state}` - 天气状态描述（"晴天"、"雨天"、"雷暴"）
- `{weather_type}` - 天气类型（CLEAR、RAIN、THUNDER）

#### 使用示例

**监视当前世界**：
```
玩家: 下雨了告诉我
AI: 好的！已创建挂机任务：监视天气变化（当前世界）。当天气发生变化时，我会通知你。
```

**监视指定世界**：
```
玩家: 帮我盯着主世界的天气，变化了告诉我
AI: 好的！已创建挂机任务：监视天气变化（目标世界：world）。当主世界天气变化时，我会通知你。
```

#### 特殊逻辑
- ⚠️ 这是唯一的**世界级监听器**（非玩家级）
- ✅ 如果未指定 `target_world`，默认挂机任务创建者当前所在的世界
- ✅ 可以监视任意世界的天气变化

---

### 8. PLAYER_BED_ENTER_WATCH - 监视玩家进入床

**事件**: `PlayerBedEnterEvent`  
**级别**: A级  
**文件**: `skills/afktask/impl/PlayerBedEnterWatchTask.java`

#### 功能说明
监视指定玩家进入床（睡觉）事件，当目标玩家进入床时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他睡觉了告诉我"
- "监视玩家A，他睡觉后查询他的位置"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{x}` / `{y}` / `{z}` - 床的位置坐标
- `{world}` - 床所在世界名称

#### 使用示例
```
玩家: 帮我盯着 Steve，他睡觉了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 进入床。当 Steve 睡觉时，我会通知你。
```

---

### 9. PLAYER_BED_LEAVE_WATCH - 监视玩家离开床

**事件**: `PlayerBedLeaveEvent`  
**级别**: A级  
**文件**: `skills/afktask/impl/PlayerBedLeaveWatchTask.java`

#### 功能说明
监视指定玩家离开床事件，当目标玩家离开床（起床）时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他起床了告诉我"
- "监视玩家A，他起床后查询他的位置"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{x}` / `{y}` / `{z}` - 床的位置坐标
- `{world}` - 床所在世界名称

#### 使用示例
```
玩家: 帮我盯着 Steve，他起床了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 离开床。当 Steve 起床时，我会通知你。
```

---

### 10. PLAYER_RESPAWN_WATCH - 监视玩家重生

**事件**: `PlayerRespawnEvent`  
**级别**: A级  
**文件**: `skills/afktask/impl/PlayerRespawnWatchTask.java`

#### 功能说明
监视指定玩家重生事件，当目标玩家重生时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他重生了告诉我"
- "监视玩家A，他重生后查询他的位置"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{x}` / `{y}` / `{z}` - 重生点坐标
- `{world}` - 重生点所在世界名称

#### 使用示例
```
玩家: 帮我盯着 Steve，他重生了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 重生。当 Steve 重生时，我会通知你。
```

---

### 11. PLAYER_ITEM_BREAK_WATCH - 监视玩家物品损坏

**事件**: `PlayerItemBreakEvent`  
**级别**: A级  
**文件**: `skills/afktask/impl/PlayerItemBreakWatchTask.java`

#### 功能说明
监视指定玩家物品损坏事件，当目标玩家的物品损坏时触发通知或回调任务。

#### 使用场景
- "帮我盯着 Steve，他的工具坏了告诉我"
- "监视玩家A，他的装备坏了后帮他查询背包"

#### 配置参数
| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `target_player` | String | ✅ | 目标玩家名称 |
| `callback` | JSON | ❌ | 回调配置 |

#### 可用占位符
- `{triggered_player}` - 触发事件的玩家名称
- `{creator}` - 任务创建者名称
- `{item_name}` - 损坏的物品名称（自定义名称或类型名称）
- `{item_type}` - 物品类型（如 "DIAMOND_SWORD"）

#### 使用示例

**纯通知模式**：
```
玩家: 帮我盯着 Steve，他的工具坏了告诉我
AI: 好的！已创建挂机任务：监视玩家 Steve 的物品损坏。当 Steve 的物品损坏时，我会通知你。
```

**回调模式**：
```
玩家: 盯着 Steve，他的装备坏了后查询他的背包
AI: 好的！已创建挂机任务：监视玩家 Steve 的物品损坏，触发回调任务，目标：查询背包状态。
```

回调任务中可以使用：
```yaml
steps:
  - skill_name: "GenericBukkitAPI"
    action: "get_player_hand_item"
    entities:
      target_player: "{triggered_player}"
    description: "查询 {triggered_player} 的主手物品（他的 {item_name} 刚坏了）"
```

---

## 🔧 监听器通用特性

### 双模式支持

所有监听器都支持两种运行模式：

#### 1. 纯通知模式
事件触发后直接发送通知消息给任务创建者，不经过 LLM 二次分析。

**特点**：
- ⚡ 快速响应
- 📝 使用预设通知模板
- 💬 适合简单场景

**示例**：
```
玩家: 帮我盯着 Steve 上线
→ 事件触发 → "🔔 挂机任务完成\n\n• 目标玩家：Steve\n• 状态：已上线\n\nSteve 上线了！"
```

#### 2. 回调模式
事件触发后执行多步骤回调任务，经过 LLM 二次分析后发送结果。

**特点**：
- 🧠 智能分析
- 🔗 支持多步骤任务链
- 📊 可组合多个 API
- 💬 适合复杂场景

**示例**：
```
玩家: 帮我盯着 Steve，他上线后查询他的位置并告诉我
→ 事件触发 → 执行回调任务（查询位置API） → LLM分析 → "Steve 已上线！他当前在 X=128, Y=64, Z=-256, 世界=world"
```

### 任务生命周期

```
PENDING → start() → RUNNING → 事件触发 → complete() → COMPLETED
                                    ↓
                              cleanup() → 从 taskMap + taskIndex 移除
```

**详细说明**：
1. **PENDING** - 任务已创建，等待启动
2. **RUNNING** - 正在监听事件
3. **COMPLETED** - 事件触发，任务完成
4. **CANCELLED** - 手动取消或创建者下线

### 资源清理

所有监听器都会在以下情况自动清理资源：
- ✅ 任务完成（事件触发）
- ✅ 手动取消（`/kilacraft afk cancel`）
- ✅ 创建者下线（`AFKTaskListener` 自动清理）

清理操作：
```java
@Override
public void onStop() {
    if (listenerRegistered) {
        HandlerList.unregisterAll(this);
        listenerRegistered = false;
    }
}
```

### 并发控制

- 🔒 **一人一任务**：每个玩家同时只能拥有一个挂机任务
- 📋 **全局队列**：支持任务队列（预留扩展点）
- ⚠️ **冲突检测**：创建任务时检查是否已有任务

### 延迟反馈优化

回调执行时传入**空对话历史**：

**原因**：挂机任务触发时可能已过去很久，原始对话上下文已被淹没，注入历史会产生噪音。

**实现**：
```java
Deque<ConversationManager.Message> history = new ArrayDeque<>();  // 空历史
executor.executeTask(plan, context, history, goal);
```

---

## 📝 配置示例

### AFKTaskSkill.yml 配置

```yaml
description: '挂机任务系统：在后台持续监控某个事件条件，条件满足时自动执行一个完整的多步骤回调任务。'

action_descriptions:
  create_task: '创建新的挂机任务。必填参数：task_type(监控类型)、target_player(监视目标玩家)。可选参数：callback(回调任务配置)。'
  cancel_task: '取消玩家当前正在运行的挂机任务。'
  query_task: '查询玩家当前的挂机任务状态。'

hints:
  - '**任务类型说明**：PLAYER_ONLINE_WATCH=监视玩家上线, PLAYER_OFFLINE_WATCH=监视玩家下线, PLAYER_DEATH_WATCH=监视玩家死亡, PLAYER_TELEPORT_WATCH=监视玩家传送, PLAYER_LEVEL_CHANGE_WATCH=监视玩家等级变化, PLAYER_CHANGED_WORLD_WATCH=监视玩家切换世界, WEATHER_CHANGE_WATCH=监视天气变化, PLAYER_BED_ENTER_WATCH=监视玩家进入床, PLAYER_BED_LEAVE_WATCH=监视玩家离开床, PLAYER_RESPAWN_WATCH=监视玩家重生, PLAYER_ITEM_BREAK_WATCH=监视玩家物品损坏。'
  - '**PLAYER_TELEPORT_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{from_world}/{to_world}/{from_x}/{from_y}/{from_z}/{to_x}/{to_y}/{to_z}占位符'
  - '**PLAYER_LEVEL_CHANGE_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{old_level}/{new_level}/{direction}占位符'
  - '**PLAYER_CHANGED_WORLD_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{from_world}/{to_world}占位符'
  - '**WEATHER_CHANGE_WATCH 可选参数**：target_world（目标世界名称，为空则监视玩家当前世界）。callback为可选参数，回调中可使用{world_name}/{weather_state}/{weather_type}占位符'
  - '**PLAYER_BED_ENTER_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{x}/{y}/{z}/{world}占位符'
  - '**PLAYER_BED_LEAVE_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{x}/{y}/{z}/{world}占位符'
  - '**PLAYER_RESPAWN_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{x}/{y}/{z}/{world}占位符'
  - '**PLAYER_ITEM_BREAK_WATCH 必填参数**：target_player（目标玩家名称）。callback为可选参数，回调中可使用{item_name}/{item_type}占位符'
```

---

## 🎓 使用最佳实践

### 1. 选择合适的监听器

| 需求 | 推荐监听器 |
|------|-----------|
| 玩家上线/下线 | `PLAYER_ONLINE_WATCH` / `PLAYER_OFFLINE_WATCH` |
| 玩家死亡/重生 | `PLAYER_DEATH_WATCH` / `PLAYER_RESPAWN_WATCH` |
| 玩家移动 | `PLAYER_TELEPORT_WATCH` / `PLAYER_CHANGED_WORLD_WATCH` |
| 玩家状态变化 | `PLAYER_LEVEL_CHANGE_WATCH` |
| 玩家睡觉 | `PLAYER_BED_ENTER_WATCH` / `PLAYER_BED_LEAVE_WATCH` |
| 物品相关 | `PLAYER_ITEM_BREAK_WATCH` |
| 环境变化 | `WEATHER_CHANGE_WATCH` |

### 2. 纯通知 vs 回调模式

**使用纯通知模式**：
- ✅ 只需要简单提醒
- ✅ 不需要额外数据
- ✅ 追求快速响应

**使用回调模式**：
- ✅ 需要查询额外信息
- ✅ 需要执行多个操作
- ✅ 需要智能分析

### 3. 占位符使用技巧

```yaml
# ✅ 好的示例：使用占位符提供上下文
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

### 4. 性能考虑

- ⚡ 监听器本身性能开销极低（仅事件过滤）
- 🧠 回调模式的性能取决于任务复杂度
- 📊 建议回调任务不超过 3-5 步
- ⏱️ 避免在回调中执行耗时操作

---

## 🔗 相关文档

- [Bukkit API 参考手册](./Bukkit-API参考手册.md) - 查看所有可用的 Bukkit API
- [AFK 挂机任务系统详解](./AFK挂机任务系统详解.md) - 挂机任务系统完整指南
- [服主指南](./服主指南.md) - 完整的配置和使用说明

---

> **最后更新**: 2026-04-19  
> **插件版本**: 1.4.5+  
> **已实现监听器**: 11 个（S级 7 个 + A级 4 个）
