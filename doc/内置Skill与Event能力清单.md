# Kilacraft-AI - 内置 Skill 与 Event 能力清单

> **版本**: v1.4.5  
> **说明**: 本文档汇总了 Kilacraft-AI 内置的所有 Skill 动作和支持的 Bukkit Event 监听器，帮助服主和插件开发者快速了解插件的能力边界、集成的第三方插件以及安全风险。

---

## 📋 目录

1. [Skill 能力清单](#skill-能力清单)
2. [Bukkit Event 监听器清单](#bukkit-event-监听器清单)
3. [第三方插件依赖](#第三方插件依赖)
4. [能力边界](#能力边界)

---

## Skill 能力清单

### 1. AFKTaskSkill - 挂机任务系统

**能力类型**: 事件监听 + 延迟回调任务链  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/afktask/AFKTaskSkill.yml`  
**实现类**: `AFKTaskSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `create_task` | 创建新的挂机任务 | `task_type`, `target_player` | `callback` |
| `cancel_task` | 取消玩家当前的挂机任务 | 无 | 无 |
| `query_task` | 查询玩家当前的挂机任务状态 | 无 | 无 |

#### 支持的 11 种事件监听类型

| 任务类型 | 监控目标 | 级别 | 依赖事件 |
|---------|---------|------|---------|
| `PLAYER_ONLINE_WATCH` | 玩家上线 | S级 | PlayerJoinEvent |
| `PLAYER_OFFLINE_WATCH` | 玩家下线 | S级 | PlayerQuitEvent |
| `PLAYER_DEATH_WATCH` | 玩家死亡 | S级 | PlayerDeathEvent |
| `PLAYER_TELEPORT_WATCH` | 玩家传送 | S级 | PlayerTeleportEvent |
| `PLAYER_LEVEL_CHANGE_WATCH` | 玩家等级变化 | S级 | PlayerLevelChangeEvent |
| `PLAYER_CHANGED_WORLD_WATCH` | 玩家切换世界 | S级 | PlayerChangedWorldEvent |
| `WEATHER_CHANGE_WATCH` | 天气变化 | S级 | WeatherChangeEvent |
| `PLAYER_BED_ENTER_WATCH` | 玩家进入床 | A级 | PlayerBedEnterEvent |
| `PLAYER_BED_LEAVE_WATCH` | 玩家离开床 | A级 | PlayerBedLeaveEvent |
| `PLAYER_RESPAWN_WATCH` | 玩家重生 | A级 | PlayerRespawnEvent |
| `PLAYER_ITEM_BREAK_WATCH` | 玩家物品损坏 | A级 | PlayerItemBreakEvent |
| `CUSTOM` | 自定义条件轮询 | A级 | 任意 Skill |

#### 核心特性

- ✅ **双模式支持**: 纯通知模式（快速响应）vs 回调模式（多步骤任务链）
- ✅ **占位符系统**: 事件触发时提供丰富的上下文占位符（`{triggered_player}`, `{from_world}`, `{to_x}`, 等等）
- ✅ **延迟反馈优化**: 回调执行时注入空对话历史，避免过期上下文噪音
- ✅ **一人一任务**: 每个玩家同时只能拥有一个挂机任务，自动冲突检测
- ✅ **资源自动清理**: 任务完成、手动取消、玩家下线时自动清理监听器和任务索引

#### 典型使用场景

```
玩家: 帮我盯着 Steve 上线，他上线后帮我查询他的位置
→ AFKTaskSkill (创建 PLAYER_ONLINE_WATCH 任务)
    → PlayerJoinEvent 触发
      → 执行回调任务（包含 get_player_location）
        → LLM 二次分析
          → 通知玩家位置信息
```

---

### 2. GenericBukkitAPI - 通用 Bukkit API 执行器

**能力类型**: 原生 API 数据查询  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/bukkit/apis.yml`  
**实现类**: `GenericBukkitAPISkill.java`

#### 支持的 API 动作（60+ 个）

**玩家相关**（27 个）

| API 动作 | 说明 | 额外数据字段 |
|----------|------|--------------|
| 物品栏 | | |
| `get_player_hand_item` | 获取玩家主手物品 | `item_name`, `item_amount` |
| `get_player_offhand_item` | 获取玩家副手物品 | `item_name`, `item_amount` |
| 生命与状态 | | |
| `get_player_health` | 获取玩家生命值 | `health`, `max_health` |
| `get_player_food` | 获取玩家饥饿值 | `food_level`, `saturation` |
| `get_player_oxygen` | 获取玩家氧气值 | `remaining_air`, `maximum_air` |
| 位置与移动 | | |
| `get_player_location` | 获取玩家位置 | `x`, `y`, `z`, `yaw`, `pitch`, `world` |
| `get_player_eye_location` | 获取玩家视线位置 | `x`, `y`, `z` |
| `get_player_velocity` | 获取玩家速度向量 | - |
| 游戏模式与飞行 | | |
| `get_player_gamemode` | 获取玩家游戏模式 | - |
| `get_player_fly_status` | 获取玩家飞行状态 | `allow_flight`, `is_flying` |
| `get_player_fly_speed` | 获取玩家飞行速度 | - |
| `get_player_walk_speed` | 获取玩家行走速度 | - |
| 经验与等级 | | |
| `get_player_exp` | 获取玩家经验值 | `level`, `exp_progress` |
| `get_player_exp_to_level` | 获取升到下一级所需经验 | - |
| 其他状态 | | |
| `get_player_main_hand` | 获取玩家主手偏好 | - |
| `get_player_ping` | 获取玩家网络延迟 | - |
| `get_player_sleep_status` | 获取玩家睡眠状态 | `is_sleeping`, `sleep_ticks` |
| `get_player_last_death` | 获取玩家上次死亡位置 | - |
| `get_player_attack_cooldown` | 获取玩家攻击冷却 | - |
| `get_player_vehicle` | 获取玩家骑乘状态 | `in_vehicle` |
| `get_player_fire_status` | 获取玩家着火状态 | `fire_ticks`, `max_fire_ticks` |
| `get_player_freeze_status` | 获取玩家冰冻状态 | `is_frozen`, `freeze_ticks`, `max_freeze_ticks` |
| `get_player_pose` | 获取玩家姿势 | - |
| 装备与效果 | | |
| `get_player_armor` | 获取玩家盔甲装备 | `helmet`, `chestplate`, `leggings`, `boots` |
| `get_player_potion_effects` | 获取玩家药水效果 | `effects` |
| `get_player_target_block` | 获取玩家瞄准方块 | `block_type`, `x`, `y`, `z` |
| 动作状态 | | |
| `get_player_sneak_status` | 获取玩家潜行状态 | - |
| `get_player_sprint_status` | 获取玩家冲刺状态 | - |
| 客户端信息 | | |
| `get_player_locale` | 获取玩家客户端语言 | - |
| `get_player_display_name` | 获取玩家显示名称 | - |
| 重生点 | | |
| `get_player_bed_spawn` | 获取玩家床重生点 | `x`, `y`, `z`, `world` |
| 经验详细 | | |
| `get_player_total_exp` | 获取玩家总经验值 | `total_exp` |

**世界相关**（20 个）

| API 动作 | 说明 | 额外数据字段 |
|----------|------|--------------|
| 时间与天气 | | |
| `get_world_time` | 获取世界时间 | `time_ticks` |
| `get_weather` | 获取天气状况 | `weather_desc` |
| 世界信息 | | |
| `get_world_info` | 获取世界基本信息 | `name`, `environment`, `difficulty` |
| `get_world_seed` | 获取世界种子 | - |
| `get_world_spawn` | 获取世界出生点 | - |
| `get_world_height_limit` | 获取世界高度限制 | `min_height`, `max_height` |
| 生物生成规则 | | |
| `get_world_spawn_rules` | 获取世界生物生成规则 | `allow_monsters`, `allow_animals` |
| `get_world_pvp` | 获取世界 PVP 设置 | - |
| 生物群系与环境 | | |
| `get_world_biome` | 获取世界生物群系 | `biome` |
| `get_world_temperature` | 获取世界温度 | `temperature` |
| `get_world_humidity` | 获取世界湿度 | `humidity` |
| 实体统计 | | |
| `get_world_player_count` | 获取世界玩家数量 | `player_count` |
| `get_world_living_entities` | 获取世界生物数量 | `living_entities` |
| `get_world_entity_count` | 获取世界实体总数 | `entity_count` |
| 世界属性 | | |
| `get_world_sea_level` | 获取世界海平面高度 | - |
| 天气持续时间 | | |
| `get_world_clear_weather_duration` | 获取晴天剩余时间 | - |
| `get_world_thunder_duration` | 获取雷暴剩余时间 | - |
| 世界时间详细 | | |
| `get_world_full_time` | 获取世界总时间 | `full_time` |
| `get_world_game_time` | 获取世界游戏时间 | `game_time` |
| 袭击事件 | | |
| `get_world_raids` | 获取世界袭击事件 | `raids` |

**服务器相关**（7 个）

| API 动作 | 说明 | 额外数据字段 |
|----------|------|--------------|
| 玩家信息 | | |
| `get_online_players` | 获取在线玩家数量和列表 | - |
| `get_max_players` | 获取最大玩家数 | - |
| 版本信息 | | |
| `get_server_version` | 获取服务器版本 | `version`, `bukkit_version` |
| `get_server_motd` | 获取服务器 MOTD | - |
| 世界列表 | | |
| `get_server_worlds` | 获取服务器世界列表 | - |
| 服务器设置 | | |
| `get_server_settings` | 获取服务器设置 | `allow_flight`, `allow_nether`, `allow_end` |

#### 核心特性

- ✅ **只读操作**: 所有 API 都是数据查询，不会改变游戏状态
- ✅ **链式调用支持**: 支持最多 2 层链式调用（如 `getLocation.getX`）
- ✅ **并行调用**: `additional_methods` 支持同时获取多个独立属性
- ✅ **结果模板化**: `result_template` 支持占位符替换

---

### 3. CMISkill - CMI 插件集成

**能力类型**: 传送 + 玩家信息查询  
**依赖插件**: CMI (v9.8.6.4+)  
**文件位置**: `skills/cmi/CMISkill.yml`  
**实现类**: `CMISkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `query_homes` | 查询玩家自己的家列表 | 无 | 无 |
| `query_warps` | 查询服务器公共地标列表 | 无 | 无 |
| `query_player_info` | 查询指定玩家的 CMI 增强信息 | 无 | `target_player` |
| `query_kits` | 查询服务器可用套装列表 | 无 | 无 |
| `query_online_players` | 查询在线玩家列表（增强版） | 无 | 无 |
| `teleport_home` | 传送到玩家自己的家 | `home_name` | 无 |
| `teleport_to_warp` | 传送到公共地标 | `warp_name` | 无 |
| `send_tp_request` | 发送传送请求给目标玩家（TPA） | `target_player` | 无 |

#### 核心特性

- ✅ **AFK 状态识别**: 查询玩家信息时自动识别 AFK 状态
- ✅ **隐身状态识别**: 查询在线玩家时自动识别隐身状态
- ✅ **前置查询约束**: `teleport_home` 必须先调用 `query_homes`，`teleport_to_warp` 必须先调用 `query_warps`
- ✅ **数组索引占位符**: 多步骤任务中通过 `{step_X.homes[0].home_name}` 引用查询结果
- ✅ **增强玩家信息**: 游戏时长、飞行状态、游戏模式等详细数据

#### 典型使用场景

```
玩家: 帮我回家
→ CMISkill (创建多步骤任务)
    1. query_homes (查询家列表)
    2. teleport_home (传送到家，使用 {step_1.homes[0].home_name})
```

---

### 4. CommandSkill - 命令执行

**能力类型**: 服务器命令执行（玩家身份）  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/command/CommandSkill.yml`  
**实现类**: `CommandSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 |
|------|------|----------|
| `execute_command` | 以玩家身份执行一条服务器命令 | `command` |

#### 核心特性

- ✅ **权限边界**: AI 以玩家身份执行命令，受服务器权限系统约束
- ✅ **兜底机制**: 当专用 Skill 无法覆盖用户需求时，可尝试执行命令
- ✅ **安全机制**: 不绕过任何服务器安全机制（权限、冷却、安全区域）

---

### 5. BukkitFXSkill - 音效与粒子效果

**能力类型**: 客户端效果播放（仅调用者可见/可听）  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/bukkit/BukkitFXSkill.yml`  
**实现类**: `BukkitFXSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `play_sound` | 播放音效（仅调用者听到） | `sound` | `volume`, `pitch` |
| `spawn_particle` | 显示粒子效果（仅调用者看到） | `particle` | `count`, `offset_x`, `offset_y`, `offset_z` |

#### 音效分类示例

| 分类 | 示例 | 适用场景 |
|------|------|---------|
| 环境音效 | `AMBIENT_CAVE` | 洞穴探索氛围 |
| 方块音效 | `BLOCK_ANVIL_BREAK` | 建筑/破坏提示 |
| 实体音效 | `ENTITY_PLAYER_LEVELUP` | 升级庆祝、任务完成 |
| 物品音效 | `ITEM_ARMOR_EQUIP_DIAMOND` | 装备提示 |

#### 粒子分类示例

| 分类 | 示例 | 适用场景 |
|------|------|---------|
| 庆祝类 | `HEART`, `VILLAGER_HAPPY` | 任务完成、庆祝 |
| 警告类 | `VILLAGER_ANGRY`, `DAMAGE_INDICATOR` | 危险提醒 |
| 战斗类 | `CRIT`, `SWEEP_ATTACK` | 战斗反馈 |
| 魔法类 | `ENCHANTMENT_TABLE`, `SPELL` | 附魔/药水效果 |
| 自然类 | `FLAME`, `SMOKE_NORMAL` | 环境氛围 |
| 爆炸类 | `EXPLOSION_NORMAL`, `EXPLOSION_LARGE` | 爆炸效果 |
| 传送类 | `PORTAL`, `END_ROD` | 传送提示 |

#### 核心特性

- ✅ **安全隔离**: 所有效果仅对 `context.getPlayer()` 生效，不影响其他玩家
- ✅ **参数范围限制**: 音量 0.0-1.0，音调 0.5-2.0，粒子数量 1-100
- ✅ **YML 配置驱动**: 描述和提示词通过配置文件定义，支持热重载
- ✅ **知识库增强**: 支持通过知识库文件扩展支持的音效/粒子列表
- ✅ **主线程执行**: 自动检测线程环境，确保效果在主线程播放

#### 典型使用场景

```
玩家: 给我放个升级音效庆祝一下
→ BukkitFXSkill (play_sound)
    sound: ENTITY_PLAYER_LEVELUP
    volume: 1.0, pitch: 1.0

玩家: 显示一些爱心粒子
→ BukkitFXSkill (spawn_particle)
    particle: HEART
    count: 10, offset: 0.5/0.5/0.5
```

---

### 6. MarketQuerySkill - GlobalMarketPlus 插件集成

**能力类型**: 市场信息查询  
**依赖插件**: GlobalMarketPlus (v1.3.8.0+)  
**文件位置**: `skills/globalmarketplus/MarketQuerySkill.yml`  
**实现类**: `MarketQuerySkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `query_balance` | 查询玩家账户余额 | 无 | 无 |
| `query_price` | 查询指定物品的市场价格 | `item` | 无 |
| `query_items` | 查询市场上架的商品列表 | 无 | 无 |
| `query_availability` | 查询指定物品是否在售 | `item` | 无 |
| `query_my_items` | 查询玩家自己在售的商品 | 无 | 无 |
| `query_mailbox` | 查询玩家邮箱待领取邮件 | 无 | 无 |
| `query_market_stats` | 查询市场统计信息 | 无 | 无 |

#### 核心特性

- ✅ **英文逗号分隔**: `entities.item` 格式为 `物品名:数量`
- ✅ **前置查询约束**: `query_price` 需先调用 `get_player_hand_item` 获取物品名
- ✅ **格式化价格**: 金额自动格式化显示

---

## 安全拦截器

Kilacraft-AI v1.4.5 引入了**非合作式安全过滤机制**（SkillSecurityFilter），在所有 Skill 执行前自动扫描参数中的玩家名，保护玩家数据不被恶意 Skill 访问或篡改。

### 核心机制

- **Value 扫描 + 消毒**: 直接扫描 Skill 参数中所有值，检测到在线玩家名时校验权限
- **非合作式**: 不依赖 Skill 声明，直接检测实际传递的数据值
- **自动消毒**: 校验不通过时替换为当前玩家名，Skill 继续执行而非阻断

### 内置 Skill 白名单

| Skill/动作 | 白名单类型 | 说明 |
|-----------|-----------|------|
| `cmi.send_tp_request` | 动作级 | CMI 传送请求（TPA），允许向其他玩家发送传送请求 |
| `AFKTask.create_task` | 动作级 | AFK 任务可监听其他玩家事件 |
| `command.execute_command` | 动作级 | 命令以玩家身份执行，权限边界 = 玩家自身权限 |

### 第三方 Skill 防护

- 即使第三方 Skill 尝试操作其他玩家，安全过滤器会自动消毒（替换为当前玩家名）
- 建议服主在安装第三方 Skill 前审查代码，确认其行为符合预期

---

## Bukkit Event 监听器清单

### S 级监听器（7 个）

| 任务类型 | 监听事件 | 监控目标 | 触发时机 |
|---------|---------|---------|---------|
| `PLAYER_ONLINE_WATCH` | PlayerJoinEvent | 玩家上线 | 玩家通过身份验证后触发 |
| `PLAYER_OFFLINE_WATCH` | PlayerQuitEvent | 玩家下线 | 玩家退出服务器时触发 |
| `PLAYER_DEATH_WATCH` | PlayerDeathEvent | 玩家死亡 | 玩家死亡时触发 |
| `PLAYER_TELEPORT_WATCH` | PlayerTeleportEvent | 玩家传送 | 玩家传送时触发 |
| `PLAYER_LEVEL_CHANGE_WATCH` | PlayerLevelChangeEvent | 玩家等级变化 | 玩家升级或降级时触发 |
| `PLAYER_CHANGED_WORLD_WATCH` | PlayerChangedWorldEvent | 玩家切换世界 | 玩家在不同世界间传送时触发 |
| `WEATHER_CHANGE_WATCH` | WeatherChangeEvent | 天气变化 | 世界天气变化时触发 |

### A 级监听器（4 个）

| 任务类型 | 监听事件 | 监控目标 | 触发时机 |
|---------|---------|---------|---------|
| `PLAYER_BED_ENTER_WATCH` | PlayerBedEnterEvent | 玩家进入床 | 玩家进入床开始睡觉时触发 |
| `PLAYER_BED_LEAVE_WATCH` | PlayerBedLeaveEvent | 玩家离开床 | 玩家起床时触发 |
| `PLAYER_RESPAWN_WATCH` | PlayerRespawnEvent | 玩家重生 | 玩家重生时触发 |
| `PLAYER_ITEM_BREAK_WATCH` | PlayerItemBreakEvent | 玩家物品损坏 | 玩家物品损坏时触发 |

### 自定义任务类型（1 个）

| 任务类型 | 监控方式 | 监控目标 | 支持条件 |
|---------|---------|---------|---------|
| `CUSTOM` | 定时轮询 | 任意 Skill 返回值 | 支持单条件比较（less_than, greater_than, equal 等） |

### 特殊占位符

| 监听器类型 | 可用占位符 |
|-------------|------------|
| PLAYER_ONLINE/OFFLINE/DEATH | `{triggered_player}`, `{creator}` |
| PLAYER_TELEPORT | `{from_world}`, `{to_world}`, `{from_x}`, `{from_y}`, `{from_z}`, `{to_x}`, `{to_y}`, `{to_z}` |
| PLAYER_LEVEL_CHANGE | `{old_level}`, `{new_level}`, `{direction}` |
| PLAYER_CHANGED_WORLD | `{from_world}`, `{to_world}` |
| WEATHER_CHANGE | `{world_name}`, `{weather_state}`, `{weather_type}` |
| PLAYER_BED/RESPAWN | `{x}`, `{y}`, `{z}`, `{world}` |
| PLAYER_ITEM_BREAK | `{item_name}`, `{item_type}` |

---

## 第三方插件依赖

### 必需依赖

| 插件名称 | 版本要求 | 功能 | 提供能力 |
|---------|---------|------|---------|
| **无** | - | - | 核心功能无需第三方插件 |

### 可选依赖（推荐安装以获得完整功能）

| 插件名称 | 版本要求 | 功能 | 提供能力 | 依赖的 Skill |
|---------|---------|------|---------|----------|
| **CMI** | v9.8.6.4+ | 传送、家、地标、套装、玩家增强、TPA | 传送、玩家信息查询 | CMISkill |
| **GlobalMarketPlus** | v1.3.8.0+ | 全球市场、商品交易、邮箱、套装 | 市场查询 | MarketQuerySkill |

### 兼容性说明

- ✅ **Folia 支持**: 插件通过反射完全兼容 Folia 服务端架构
- ✅ **无软依赖**: 未安装可选插件时，对应 Skill 自动不可用，不影响核心功能
- ✅ **SPI 扩展**: 第三方插件可通过 Bukkit ServicesManager 注册自己的 Skill

---

## 能力边界

### AI 插件能做什么

✅ **可以**:
- 查询 Minecraft 原生 API 数据（玩家、世界、服务器状态）
- 监听 11 种 Bukkit Event 事件（S 级 7 个 + A 级 4 个）
- 创建挂机任务（事件触发后自动执行多步骤回调）
- 执行服务器命令（以玩家身份，受权限约束）
- 查询第三方插件数据（CMI 传送/信息、GlobalMarketPlus 市场）
- 多步骤任务链（支持步骤间数据引用和占位符替换）
- LLM 二次分析（将 API 执行结果转换为自然语言）

### AI 插件不能做什么

❌ **不能**:
- 修改 Minecraft 核心数据（修改世界配置、服务器设置等）
- 绕过服务器权限系统（所有操作都受权限约束）
- 执行需要 OP 权限的命令（除非玩家拥有该权限）
- 直接操作游戏物理引擎（无法修改方块/实体等）
- 访问玩家隐私数据（只查询公开的游戏状态）
- 自动化重复任务（AFKTask 只支持一次性触发，不支持循环/定时任务）
- 递归嵌套挂机任务（回调中不能再次创建 AFKTask）

### 数据访问边界

| 数据类型 | 读/写 | 边界说明 |
|---------|-------|----------|
| 玩家状态 | 只读 | 可查询生命值、位置、物品等，无法修改 |
| 世界状态 | 只读 | 可查询时间、天气、生物群系等，无法修改 |
| 服务器配置 | 只读 | 可查询版本、MOTD、世界列表，无法修改 |
| 命令执行 | 写（间接） | 通过 dispatchCommand 执行，受权限约束 |
| CMI 数据 | 只读 | 查询家、地标、玩家信息，无法直接修改 |
| 市场数据 | 只读 | 查询价格、商品，无法直接修改 |

---

> **最后更新**: 2026-04-17  
> **插件版本**: 1.4.5+  
> **Skill 总数**: 6 个（AFKTaskSkill、GenericBukkitAPI、CMISkill、CommandSkill、BukkitFXSkill、MarketQuerySkill）  
> **API 动作总数**: 60+ 个（GenericBukkitAPI）+ 8 个（CMISkill）+ 2 个（BukkitFXSkill）+ 7 个（MarketQuerySkill）  
> **Event 监听器总数**: 11 个（S 级 7 个 + A 级 4 个）
