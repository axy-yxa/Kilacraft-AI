# Kilacraft-AI - 内置 Skill 与 Event 能力清单

> **最后更新**: 2026-08-01  
> **说明**: 本文档汇总了 Kilacraft-AI 内置的所有 Skill 动作和支持的 Bukkit Event 监听器，帮助服主和插件开发者快速了解插件的能力边界、集成的第三方插件以及安全风险。当前内置 **17 个 Skill**。

---

## 📋 目录

1. [Skill 能力清单](#skill-能力清单)
2. [Bukkit Event 监听器清单](#bukkit-event-监听器清单)
3. [第三方插件依赖](#第三方插件依赖)
4. [能力边界](#能力边界)

---

## Skill 能力清单

> v2.2.0 移除了挂机任务系统（AFKTaskSkill），其能力由下方 WatchSkill、PlayerWatchSkill 两个新 Skill 替代。

### 1. WatchSkill - 玩家自定义监听

**能力类型**: 条件监听（轮询）+ 事件监听  
**依赖插件**: 纯 Bukkit（依赖内置 WatchService）  
**文件位置**: `skills/watch/WatchSkill.yml`  
**实现类**: `WatchSkill.java`（实现 `DynamicContextProvider`）

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `create_watch` | 创建监听（mode=polling/event 二选一） | `mode`, `description` | `single_shot` 等 |
| `cancel_watch` | 取消监听（watch_id 精确或 description 模糊） | `watch_id` 或 `description` | 无 |
| `list_watches` | 列出当前玩家所有活跃监听 | 无 | 无 |

#### 支持的 11 种事件监听类型

| 事件类型 | 监控目标 | 可用 filter |
|---------|---------|------------|
| `furnace_smelt` | 熔炉烧好 | `result_type`（产物材质） |
| `crop_mature` | 作物成熟 | `crop_type`（作物类型） |
| `entity_death` | 实体死亡 | `entity_type`（实体类型） |
| `entity_spawn` | 实体生成 | `entity_type`（实体类型） |
| `player_death` | 玩家死亡 | 无 |
| `player_teleport` | 玩家传送 | `cause`（传送原因） |
| `player_level_change` | 经验等级变化 | 无 |
| `player_changed_world` | 切换世界 | 无 |
| `block_break` | 方块破坏 | `block_type`（方块类型） |
| `player_fish` | 钓鱼成功 | 无 |
| `player_chat` | 聊天消息 | `keyword`（关键词） |

#### 核心特性

- ✅ **两种模式**：条件监听（polling，定时跑某 skill.action 取返回字段比较）+ 事件监听（event，Bukkit 事件命中即触发）
- ✅ **动态上下文注入**：实现 `DynamicContextProvider`，向 Phase 2 提示词动态注入【条件监听清单】（遍历玩家有权限 skill 中实现 `ProbeSource` 的）+【事件监听清单】
- ✅ **触发后只通知 AI，不自动回调**（比旧挂机任务更安全）
- ✅ **上限**：每玩家条件监听 ≤ 3 / 事件监听 ≤ 5 / 全服合计 200
- ✅ **下线延迟删除窗口**（默认 5 分钟，便于重连恢复）
- ✅ 全局单例事件监听器 + 反向索引，无人订阅时事件零成本

---

### 2. PlayerWatchSkill - 跨玩家上下线订阅

**能力类型**: 玩家社交轻交互（上下线通知订阅）  
**依赖插件**: 纯 Bukkit（依赖内置 PlayerWatchService）  
**文件位置**: `skills/playerwatch/PlayerWatchSkill.yml`  
**实现类**: `PlayerWatchSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `subscribe` | 订阅某玩家上线/下线通知 | `target_player` | `trigger_event`（JOIN/QUIT/BOTH，默认 BOTH） |
| `unsubscribe` | 取消对某玩家的订阅 | `target_player` | `trigger_event`（不填则取消全部） |
| `list` | 列出当前玩家名下所有活跃订阅 | 无 | 无 |
| `unsubscribe_all` | 取消当前玩家全部订阅 | 无 | 无 |

#### 核心特性

- ✅ **单向订阅**：被订阅方不感知，仅订阅者在线期间有效
- ✅ **支持多目标**：一次可订阅多个玩家（旧系统只能盯一个）
- ✅ **防乱序**：下线通知会取消尚未发出的上线通知，避免"下线早于上线"的倒序
- ✅ **上线通知延迟 2 秒**：等玩家完全进服
- ✅ **不持久化**：重启不恢复，订阅者下线自动清空；每玩家上限 5 个订阅

---

### 3. GenericBukkitAPI - 通用 Bukkit API 执行器

**能力类型**: 原生 API 数据查询  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/bukkit/apis.yml`  
**实现类**: `GenericBukkitAPISkill.java`

#### 支持的 API 动作（71 个）

> 完整 API 清单（含每个 API 的配置、权限、返回字段）见《Bukkit-API 参考手册》。下方按类别列出代表性动作。

**玩家相关**（44 个）

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
| `get_player_location` | 获取玩家位置 | `x`, `y`, `z`, `world` |
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

**世界相关**（21 个）

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

**服务器相关**（6 个）

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

### 4. CMISkill - CMI 插件集成

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

### 5. CommandSkill - 命令执行

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

### 6. BukkitFXSkill - 音效与粒子效果

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

### 7. BukkitStatsSkill - 原版统计数据查询

**能力类型**: 玩家原版累计统计数据查询（生涯记录）  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/bukkit/BukkitStatsSkill.yml`  
**实现类**: `BukkitStatsSkill.java`  
**知识库**: `knowledge/statistics.md`（BM25 语义检索，80+ 统计枚举）

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 | 返回 data 字段 |
|------|------|----------|----------|---------------|
| `query_statistic` | 查询指定统计项的值 | `statistic` | `entity_type`, `material` | `statistic`, `value`, `statistic_type` |

#### 四种统计类型

| 类型 | 说明 | 示例统计项 | 额外参数 |
|------|------|-----------|----------|
| UNTYPED | 无参数，直接查询 | DEATHS, PLAYER_KILLS, JUMP | 无 |
| ITEM | 需要物品参数 | CRAFT_ITEM, USE_ITEM, BREAK_ITEM | `material` |
| BLOCK | 需要方块参数 | MINE_BLOCK | `material` |
| ENTITY | 需要实体参数 | KILL_ENTITY, ENTITY_KILLED_BY | `entity_type` |

#### 典型统计项

| 统计项 | 类型 | 说明 |
|--------|------|------|
| DEATHS | UNTYPED | 总死亡次数 |
| PLAYER_KILLS | UNTYPED | 击杀玩家总数 |
| MOB_KILLS | UNTYPED | 击杀生物总数 |
| PLAY_ONE_MINUTE | UNTYPED | 游戏总时长（tick） |
| TIME_SINCE_DEATH | UNTYPED | 距上次死亡的时间（tick） |
| WALK_ONE_CM | UNTYPED | 行走总距离（厘米） |
| JUMP | UNTYPED | 跳跃总次数 |
| KILL_ENTITY | ENTITY | 击杀指定生物次数 |
| ENTITY_KILLED_BY | ENTITY | 被指定生物击杀次数 |
| MINE_BLOCK | BLOCK | 挖掘指定方块次数 |
| CRAFT_ITEM | ITEM | 合成指定物品次数 |

#### 智能格式化

- **距离统计**：自动转换厘米 → 米/公里（如 1234567 厘米 → 12.3 公里）
- **时长统计**：自动转换 tick → 可读时间（如 72000 tick → 1 小时）
- **EntityType 翻译**：30+ 常见实体中文名称
- **Material 翻译**：复用 ItemTranslator

#### 核心特性

- ✅ **知识库驱动**: 统计枚举通过 BM25 检索匹配，LLM 自动获取正确枚举名
- ✅ **多步骤数据传递**: 返回 value 字段，支持 AFK CUSTOM 轮询条件监控
- ✅ **参数校验**: 自动验证 Material/EntityType 合法性
- ✅ **累计统计边界**: 明确与当前状态（血量/饱食度/等级）查询的区分

#### 典型使用场景

```
玩家: 我总共死了多少次
→ BukkitStatsSkill (query_statistic)
    statistic: DEATHS
    返回: 总死亡次数：42

玩家: 我杀了多少僵尸
→ BukkitStatsSkill (query_statistic)
    statistic: KILL_ENTITY
    entity_type: ZOMBIE
    返回: 击杀生物（僵尸）：15

玩家: 我走了多远
→ BukkitStatsSkill (query_statistic)
    statistic: WALK_ONE_CM
    返回: 行走距离：12.5 公里

玩家: 帮我盯着我的飞行距离，突破10万格就放烟花
→ 多步骤任务：
    Step 1: BukkitStatsSkill (query_statistic)
            statistic: AVIATE_ONE_CM
    Step 2: CUSTOM 挂机任务
            condition: "{step_1.value} > 10000000"
            callback: BukkitFXSkill (spawn_particle)
                    particle: FIREWORKS_SPARK, count: 50
```

---

### 8. MarketQuerySkill - GlobalMarketPlus 插件集成

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
| `query_seller_items` | 查询指定卖家的在售商品 | `seller_name` | 无 |
| `query_mailbox` | 查询玩家邮箱待领取邮件 | 无 | 无 |
| `query_market_stats` | 查询市场统计信息 | 无 | 无 |

#### 核心特性

- ✅ **英文逗号分隔**: `entities.item` 格式为 `物品名:数量`
- ✅ **前置查询约束**: `query_price` 需先调用 `get_player_hand_item` 获取物品名
- ✅ **格式化价格**: 金额自动格式化显示

---

### 9. MarketActionSkill - 全球市场操作技能

**能力类型**: 市场交易操作（写入类）  
**依赖插件**: GlobalMarketPlus (v1.3.8.0+)  
**文件位置**: `skills/globalmarketplus/MarketActionSkill.yml`  
**实现类**: `MarketActionSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 |
|------|------|----------|
| `search_item` | 搜索商品并打开购买 GUI | `item` |
| `sell_item` | 将手中物品上架 | `item`, `price` |
| `pickup_mail` | 一键领取所有或指定邮件 | 无 |
| `buy_item` | 以指定单价发起收购订单 | `item`, `price`, `amount` |
| `cancel_listing` | 展示在售列表，选择后下架 | 无 |
| `transfer_money` | 向其他玩家转账 | `target_player`, `amount` |
| `auction_item` | 将手中物品发起拍卖 | `item` |
| `sell_inventory` | 批量出售背包内所有同类物品 | `item`, `price` |
| `buy_inventory` | 批量收购 | `item`, `price`, `amount` |

#### 核心特性

- ✅ **仅当 GlobalMarketPlus 存在时自动注册**
- ✅ **命令代执行**：所有写操作通过 Bukkit 命令代执行，由 GMP 内部保证原子性
- ✅ **引导式价格确认**：上架时 AI 引导玩家确认价格
- ✅ **大额转账二次确认**：防止误操作

---

### 10. UtilitySkill - 通用工具技能

**能力类型**: 延时等待 + 主动通知 + 全服广播  
**依赖插件**: 纯 Bukkit 原生 API  
**文件位置**: `skills/utility/UtilitySkill.yml`  
**实现类**: `UtilitySkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `delay_wait` | 非阻塞延迟 1-60 秒 | `seconds` | 无 |
| `notify_player` | 将阶段性结果通过 LLM 概括后主动通知玩家 | `message` | 无 |
| `broadcast_message` | OP 管理员专用，将消息通过 AI 美化后全服广播 | `message` | 无 |

#### 核心特性

- ✅ **delay_wait**：使用专用调度器，不占用 I/O 线程池
- ✅ **notify_player**：支持超时保护，尊重服主流式输出配置
- ✅ **broadcast_message**：CHAT 载体全服广播，支持单意图和多步骤编排
- ✅ 可在多步骤任务中灵活编排组合

---

### 11. WebSearchSkill - 联网搜索

**能力类型**: 实时联网信息查询  
**依赖插件**: 纯 Bukkit（自管 HTTP 调用，需服主在 `web.yml` 配 API Key）  
**文件位置**: `skills/websearch/WebSearchSkill.yml`  
**实现类**: `WebSearchSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `search` | 按关键词联网搜索，返回标题/URL/摘要 | `query` | `count`、`time_range` 等 |

#### 支持的搜索引擎供应商（9 家）

| 类别 | 供应商 | 说明 |
|------|--------|------|
| 国内 | `zhipu` | 智谱 AI |
| 国内 | `baidu_qianfan` | 百度千帆 |
| 国内 | `volcengine_doubao` | 火山引擎豆包 |
| 国内 | `qiniu_baidu` | 七牛云百度 |
| 国内 | `alibaba_iqs` | 阿里云百炼 IQS |
| 国际 | `tavily` | Tavily |
| 国际 | `brave` | Brave |
| 国际 | `exa` | Exa |
| 国际 | `you_com` | You.com |

#### 核心特性

- ✅ **多供应商可插拔**：`provider: auto` 按服务器语言自动路由（中文走国内、其他走国际），也可手动指定
- ✅ **时间范围筛选**：今天/最近一周/最近一月
- ✅ **自动多步搜索**：复杂问题拆成最多 5 个子搜索
- ✅ 自管 15 秒超时，snippet 按 `max_snippet_chars` 截断
- ✅ 需 `kilacraft.websearch` 权限，且需服主配置 API Key 才生效

---

### 12. WebFetchSkill - 网页抓取

**能力类型**: 抓取指定网址正文并回答  
**依赖插件**: 纯 Bukkit（OkHttp + Jsoup 本地实现，零配置无 API Key）  
**文件位置**: `skills/webfetch/WebFetchSkill.yml`  
**实现类**: `WebFetchSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `fetch` | 抓取指定 URL 提取正文 | `url` | `question` 等 |

#### SSRF 安全防护（ssrf_protection: true 时生效）

| 防护点 | 说明 |
|--------|------|
| 内网地址拦截 | 默认禁止访问本机/局域网（127.x/10.x/192.168.x/172.16-31.x） |
| 防 DNS 重绑定 | IP 校验内嵌进 OkHttp DNS 解析，消除"先检查后连接"的 TOCTOU 窗口 |
| 强制 HTTPS | `http://` 自动升级为 `https://`，协议白名单收紧 |
| 逐跳重定向复检 | 最多 3 跳，每跳重做协议与 IP 校验 |
| 响应体字节硬上限 | `readBodyWithLimit` 严格按 `max_body_size_mb`（默认 2MB）限制，防 OOM |

#### 核心特性

- ✅ **零配置纯本地**，无需任何 API Key
- ✅ 自动移除 script/style/nav 等噪声后取正文，超 `max_text_chars` 截断
- ✅ 异步抓取（走 IO 线程池），skill 自管超时
- ✅ 需 `kilacraft.webfetch` 权限（默认所有玩家可用）

---

### 13. VersionInfoSkill - 版本信息查询

**能力类型**: 插件版本与更新信息查询（只读）  
**依赖插件**: 纯 Bukkit（数据源 Gitee/GitHub Release API，按 i18n 语言选源）  
**文件位置**: `skills/admin/VersionInfoSkill.yml`  
**实现类**: `VersionInfoSkill.java`

#### 支持的动作

| 动作 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|----------|
| `check_update` | 当前版本自检 + 查最新版（对比 + 下载地址 + 更新日志） | 无 | 无 |
| `read_changelog` | 读指定版本的完整更新日志 | `version` | 无 |
| `list_versions` | 列出近期所有版本 | 无 | `limit`（默认 10） |

#### 核心特性

- ✅ 纯只读查询，不负责下载安装（地址在 data 里返回）
- ✅ 默认动作 `check_update` 一次覆盖三类需求
- ✅ 查询走异步 IO 池
- ✅ 需 `kilacraft.admin.info` 权限（默认 OP）

---

### 14-16. 服主管理类 Skill

服主管理类 Skill 共 3 个，详细使用方法见《服主管理功能使用指南》：

| # | Skill | 技能名 | 动作 | 权限 |
|---|-------|--------|------|------|
| 15 | ServerHealthSkill | `server_health` | `health_report` / `list_reports` / `read_report` | `kilacraft.admin.health` |
| 16 | PlayerAnalysisSkill | `player_analysis` | `online_trend` / `top_active` / `new_players` / `profile_coverage` / `social_insights` / `player_relations` | `kilacraft.admin.player` |
| 17 | AuditLogSkill | `audit_log` | `query_logs` / `skill_stats` / `error_logs` | `kilacraft.admin.audit` |

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
| `player_watch.subscribe` | 动作级 | 跨玩家上下线订阅，允许订阅其他玩家的上下线通知 |
| `command.execute_command` | 动作级 | 命令以玩家身份执行，权限边界 = 玩家自身权限 |

> v2.2.0 移除了 `AFKTask.create_task`（挂机任务系统已删除），新增 `player_watch.subscribe`。白名单 action 仅审计、不替换（替换会破坏跨玩家操作命令）；该拦截器始终运行、不可跳过。

### 第三方 Skill 防护

- 即使第三方 Skill 尝试操作其他玩家，安全过滤器会自动消毒（替换为当前玩家名）
- 建议服主在安装第三方 Skill 前审查代码，确认其行为符合预期

---

## Bukkit Event 监听（WatchSkill 事件监听）

> v2.2.0 起，事件监听能力由 WatchSkill 提供（替代已移除的挂机任务系统的 19 个监听器）。玩家通过自然语言创建事件监听，WatchSkill 用**全局单例 Listener** 监听以下 11 种高价值 Bukkit 事件，命中 filter 后触发通知。

### 事件监听类型（11 种）

| 事件类型 | 监听的 Bukkit 事件 | 触发时机 | 可用 filter |
|---------|-------------------|---------|------------|
| `furnace_smelt` | FurnaceExtractEvent | 玩家从熔炉取出烧好的物品 | `result_type`（产物材质） |
| `crop_mature` | BlockGrowEvent | 作物长到最大成熟度 | `crop_type`（作物类型） |
| `entity_death` | EntityDeathEvent | 实体死亡 | `entity_type`（实体类型） |
| `entity_spawn` | CreatureSpawnEvent | 实体生成于玩家附近 | `entity_type`（实体类型） |
| `player_death` | PlayerDeathEvent | 玩家死亡 | 无 |
| `player_teleport` | PlayerTeleportEvent | 玩家传送 | `cause`（传送原因） |
| `player_level_change` | PlayerLevelChangeEvent | 经验等级变化 | 无 |
| `player_changed_world` | PlayerChangedWorldEvent | 切换世界 | 无 |
| `block_break` | BlockBreakEvent | 破坏方块 | `block_type`（方块类型） |
| `player_fish` | PlayerFishEvent | 钓鱼成功（过滤未钓中状态） | 无 |
| `player_chat` | AsyncPlayerChatEvent | 聊天消息 | `keyword`（关键词包含匹配） |

### 性能优化机制

- **全局单例 Listener**：整个服务器只注册一个 `PlayerWatchListener`（非每玩家一实例），避免高频事件（如 BlockGrowEvent）在无人订阅时的事件放大开销
- **反向索引短路**：`eventType → Set<WatchRef>` 反向索引，无人订阅该事件类型时直接 return（零成本）
- **事件归属三模式**：玩家自身（O(1)）/ 击杀者归属 / 坐标距离（**基于 watch 创建时快照位置**，非玩家实时位置——消除 Folia 跨区域读 getLocation 的风险）
- **CAS 防重入**：事件型冷却用 `AtomicLong` CAS（`Watch.casFireTime`），并发事件下仅一个通过
- **同一玩家的所有条件监听合并为单个定时器**（轮询型）

### 条件监听（polling，补充说明）

除事件监听外，WatchSkill 还支持**条件监听**（polling 模式）：定时执行某个内置 skill 的只读 action（实现 `ProbeSource` 接口的），取返回值字段做阈值比较。取值类型（number/boolean/string）运行时自动识别。

> 详细的监听创建、取消、管理见《服主指南》「玩家自定义监听」节。完整设计约束见《系统架构详解》v2.2.0 子系统架构。

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
- 玩家自定义监听（11 种事件 + 条件轮询，WatchSkill）
- 跨玩家上下线订阅（PlayerWatchSkill）
- 联网搜索与网页抓取（WebSearch / WebFetch）
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
- 在监听触发时自动执行写操作（WatchSkill 触发后只通知 AI，不自动回调——这是有意设计，比旧挂机任务更安全）

### 数据访问边界

| 数据类型 | 读/写 | 边界说明 |
|---------|-------|----------|
| 玩家状态 | 只读 | 可查询生命值、位置、物品等，无法修改 |
| 世界状态 | 只读 | 可查询时间、天气、生物群系等，无法修改 |
| 服务器配置 | 只读 | 可查询版本、MOTD、世界列表，无法修改 |
| 命令执行 | 写（间接） | 通过 dispatchCommand 执行，受权限约束 |
| CMI 数据 | 只读 | 查询家、地标、玩家信息，无法直接修改 |
| 市场数据 | 读写 | 查询价格/商品（只读 MarketQuerySkill）；交易操作（写入 MarketActionSkill，通过命令代执行） |
| 联网信息 | 只读 | WebSearch 搜索、WebFetch 抓取（受 SSRF 防护约束） |


> **内置 Skill 总数**: 17 个  
> **API 动作总数**: 71 个（GenericBukkitAPI）+ 8 个（CMISkill）+ 2 个（BukkitFXSkill）+ 8 个（MarketQuerySkill，含 query_seller_items）+ 9 个（MarketActionSkill）+ 3 个（UtilitySkill）  
> **事件监听类型**: 11 种（WatchSkill，全局单例 Listener + 反向索引）
