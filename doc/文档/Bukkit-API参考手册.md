# Kilacraft-AI - Bukkit API 参考手册

> **最后更新**: 2026-08-04  
> **说明**: 本文档提供所有内置 Bukkit API 的详细说明、配置示例和使用场景

---

## 📊 Bukkit API 快速参考表

### 👤 玩家相关 API（44 个）

| API ID | 显示名称 | 功能 | 调用方式 | 权限 |
|--------|---------|------|----------|------|
| `get_player_hand_item` | 获取玩家主手物品 | 获取主手物品信息 | method_chain | `kilacraft.api.player.inventory` |
| `get_player_offhand_item` | 获取玩家副手物品 | 获取副手（盾牌槽）物品 | method_chain | `kilacraft.api.player.inventory` |
| `get_player_health` | 获取玩家生命值 | 获取当前/最大生命值 | additional_methods | `kilacraft.api.player.status` |
| `get_player_food` | 获取玩家饥饿值 | 获取饱食度和饱和度 | additional_methods | `kilacraft.api.player.status` |
| `get_player_oxygen` | 获取玩家氧气值 | 获取水下呼吸时间 | additional_methods | `kilacraft.api.player.status` |
| `get_player_location` | 获取玩家位置 | 获取坐标和世界 | additional_methods | `kilacraft.api.player.info` |
| `get_player_eye_location` | 获取玩家视线位置 | 获取眼睛精确坐标 | method_chain | `kilacraft.api.player.info` |
| `get_player_velocity` | 获取玩家速度向量 | 获取移动速度向量 | method_chain | `kilacraft.api.player.info` |
| `get_player_gamemode` | 获取玩家游戏模式 | 获取生存/创造/冒险/旁观 | method_chain | `kilacraft.api.player.info` |
| `get_player_fly_status` | 获取玩家飞行状态 | 获取允许飞行/正在飞行 | additional_methods | `kilacraft.api.player.info` |
| `get_player_fly_speed` | 获取玩家飞行速度 | 获取飞行速度设置 | method_chain | `kilacraft.api.player.info` |
| `get_player_walk_speed` | 获取玩家行走速度 | 获取行走速度设置 | method_chain | `kilacraft.api.player.info` |
| `get_player_exp` | 获取玩家经验值 | 获取等级和经验进度 | additional_methods | `kilacraft.api.player.status` |
| `get_player_exp_to_level` | 获取升到下一级所需经验 | 获取升级经验需求 | method_chain | `kilacraft.api.player.status` |
| `get_player_main_hand` | 获取玩家主手偏好 | 获取左撇子/右撇子设置 | method_chain | `kilacraft.api.player.info` |
| `get_player_ping` | 获取玩家延迟 | 获取网络延迟（ms） | method_chain | `kilacraft.api.player.info` |
| `get_player_sleep_status` | 获取玩家睡眠状态 | 获取是否睡觉及时间 | additional_methods | `kilacraft.api.player.status` |
| `get_player_last_death` | 获取玩家上次死亡位置 | 获取死亡地点坐标 | method_chain | `kilacraft.api.player.info` |
| `get_player_attack_cooldown` | 获取玩家攻击冷却 | 获取攻击冷却进度（0-1） | method_chain | `kilacraft.api.player.status` |
| `get_player_vehicle` | 获取玩家骑乘状态 | 获取是否在载具中 | additional_methods | `kilacraft.api.player.info` |
| `get_player_fire_status` | 获取玩家着火状态 | 获取是否着火及燃烧时间 | additional_methods | `kilacraft.api.player.status` |
| `get_player_freeze_status` | 获取玩家冰冻状态 | 获取是否冰冻及程度 | additional_methods | `kilacraft.api.player.status` |
| `get_player_pose` | 获取玩家姿势 | 获取站立/蹲下/游泳等 | method_chain | `kilacraft.api.player.info` |
| `get_player_armor` | 获取玩家盔甲装备 | 获取全套盔甲信息 | method_chain | `kilacraft.api.player.inventory` |
| `get_player_potion_effects` | 获取玩家药水效果 | 获取所有活跃药水效果 | method_chain | `kilacraft.api.player.status` |
| `get_player_target_block` | 获取玩家瞄准方块 | 获取准星瞄准的方块 | method_chain | `kilacraft.api.player.info` |
| `get_player_sneak_status` | 获取玩家潜行状态 | 获取是否潜行（Shift） | method_chain | `kilacraft.api.player.status` |
| `get_player_sprint_status` | 获取玩家冲刺状态 | 获取是否冲刺（双击W） | method_chain | `kilacraft.api.player.status` |
| `get_player_locale` | 获取玩家客户端语言 | 获取语言设置（zh_CN等） | method_chain | `kilacraft.api.player.info` |
| `get_player_display_name` | 获取玩家显示名称 | 获取显示名称（含前缀） | method_chain | `kilacraft.api.player.info` |
| `get_player_bed_spawn` | 获取玩家床重生点 | 获取床重生点位置 | method_chain | `kilacraft.api.player.info` |
| `get_player_total_exp` | 获取玩家总经验值 | 获取累积总经验 | method_chain | `kilacraft.api.player.status` |
| `get_player_inventory_usage` | 获取背包使用率 | 获取已占用格数/空格数 | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_inventory` | 获取背包物品摘要 | 获取背包内物品名称+数量列表 | method_chain | `kilacraft.api.player.inventory` |
| `get_player_ender_chest` | 获取末影箱摘要 | 获取末影箱物品名称+数量列表 | method_chain | `kilacraft.api.player.inventory` |
| `get_player_open_container` | 获取打开的容器内容 | 获取当前打开的容器物品摘要 | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_open_inventory` | 获取当前打开的界面 | 获取正在查看的容器/类型 | additional_methods | `kilacraft.api.player.inventory` |
| `get_player_absorption` | 获取玩家吸收之心 | 获取额外吸收生命值 | method_chain | `kilacraft.api.player.status` |
| `get_player_arrows_in_body` | 获取玩家身上箭数 | 获取嵌入身体的箭数量 | method_chain | `kilacraft.api.player.status` |
| `get_player_no_damage_ticks` | 获取玩家无敌帧 | 获取受伤后无敌时间（tick） | method_chain | `kilacraft.api.player.status` |
| `get_player_fall_distance` | 获取玩家下落距离 | 获取当前累积下落距离 | method_chain | `kilacraft.api.player.status` |
| `get_player_compass_target` | 获取指南针目标 | 获取指南针指向的坐标 | additional_methods | `kilacraft.api.player.info` |
| `get_player_feet_block` | 获取脚下方块 | 获取脚下站立的方块信息 | method_chain | `kilacraft.api.player.info` |
| `get_player_last_damage` | 获取上次受伤原因 | 获取上次受伤来源/原因/伤害量 | method_chain | `kilacraft.api.player.status` |

### 🌍 世界相关 API（21 个）

| API ID | 显示名称 | 功能 | 调用方式 | 权限 |
|--------|---------|------|----------|------|
| `get_world_time` | 获取世界时间 | 获取游戏时间（HH:MM） | method_chain | `kilacraft.api.world.info` |
| `get_weather` | 获取天气状况 | 获取晴天/雨天/雷暴 | additional_methods | `kilacraft.api.world.info` |
| `get_world_info` | 获取世界基本信息 | 获取名称/环境/难度 | additional_methods | `kilacraft.api.world.info` |
| `get_world_seed` | 获取世界种子 | 获取世界种子值 | method_chain | `kilacraft.api.world.info` |
| `get_world_spawn` | 获取世界出生点 | 获取世界出生点位置 | method_chain | `kilacraft.api.world.info` |
| `get_world_height_limit` | 获取世界高度限制 | 获取最低/最高建筑高度 | additional_methods | `kilacraft.api.world.info` |
| `get_world_spawn_rules` | 获取世界生物生成规则 | 获取是否允许刷怪/动物 | additional_methods | `kilacraft.api.world.info` |
| `get_world_pvp` | 获取世界 PVP 设置 | 获取是否允许PVP | method_chain | `kilacraft.api.world.info` |
| `get_world_biome` | 获取世界生物群系 | 获取群系类型（平原/沙漠等） | method_chain | `kilacraft.api.world.info` |
| `get_world_temperature` | 获取世界温度 | 获取温度值（影响降雪） | method_chain | `kilacraft.api.world.info` |
| `get_world_humidity` | 获取世界湿度 | 获取湿度值（影响降雨） | method_chain | `kilacraft.api.world.info` |
| `get_world_player_count` | 获取世界玩家数量 | 获取世界中玩家数量 | additional_methods | `kilacraft.api.world.info` |
| `get_world_living_entities` | 获取世界生物数量 | 获取存活生物数量 | additional_methods | `kilacraft.api.world.info` |
| `get_world_entity_count` | 获取世界实体总数 | 获取所有实体总数 | additional_methods | `kilacraft.api.world.info` |
| `get_world_sea_level` | 获取世界海平面高度 | 获取海平面Y坐标 | method_chain | `kilacraft.api.world.info` |
| `get_world_clear_weather_duration` | 获取晴天剩余时间 | 获取晴天持续tick数 | method_chain | `kilacraft.api.world.info` |
| `get_world_thunder_duration` | 获取雷暴剩余时间 | 获取雷暴持续tick数 | method_chain | `kilacraft.api.world.info` |
| `get_world_full_time` | 获取世界总时间 | 获取总运行时间（不受睡眠影响） | method_chain | `kilacraft.api.world.info` |
| `get_world_game_time` | 获取世界游戏时间 | 获取自创建以来总时间 | method_chain | `kilacraft.api.world.info` |
| `get_world_raids` | 获取世界袭击事件 | 获取正在进行的袭击列表 | method_chain | `kilacraft.api.world.info` |
| `get_world_border` | 获取世界边界信息 | 获取世界边界的中心/大小/伤害 | method_chain | `kilacraft.api.world.info` |

### 🖥️ 服务器相关 API（6 个）

| API ID | 显示名称 | 功能 | 调用方式 | 权限 |
|--------|---------|------|----------|------|
| `get_online_players` | 获取在线玩家数量 | 获取在线玩家数量和列表 | method_chain | `kilacraft.api.server.info` |
| `get_max_players` | 获取最大玩家数 | 获取服务器最大容量 | method_chain | `kilacraft.api.server.info` |
| `get_server_version` | 获取服务器版本 | 获取Bukkit和MC版本 | additional_methods | `kilacraft.api.server.info` |
| `get_server_motd` | 获取服务器 MOTD | 获取服务器介绍消息 | method_chain | `kilacraft.api.server.info` |
| `get_server_worlds` | 获取服务器世界列表 | 获取所有已加载世界 | method_chain | `kilacraft.api.server.info` |
| `get_server_settings` | 获取服务器设置 | 获取飞行/下界/末地设置 | additional_methods | `kilacraft.api.server.info` |

**统计**：共计 **71 个 API**（玩家 44 + 世界 21 + 服务器 6）

---

## 📖 概述

Kilacraft-AI 内置了 **71 个只读 Bukkit API**，让 AI 能够访问 Minecraft 服务器的各种数据。这些 API 由 5 个独立的配置驱动 Skill 提供，均继承自 `AbstractBukkitQuerySkill`。

### 核心特性

- ✅ **标准配置驱动**：5 个独立技能（`BukkitPlayerInfo`/`BukkitPlayerStatus`/`BukkitPlayerInventory`/`BukkitWorld`/`BukkitServer`）各自通过 `description`+`action_descriptions`+`hints` 定义，与项目其他内置 Skill 完全一致
- ✅ **按技能控制权限**：每个技能一个权限节点，覆盖该技能下的全部 API（详见「权限管理」）
- ✅ **智能格式化**：由各技能的 Java 类（`AbstractBukkitQuerySkill` 及子类）自动处理复杂类型（Location、ItemStack、GameMode、ItemStack[]、Set<PotionEffect> 等）
- ✅ **错误隔离**：API 执行失败不影响其他功能

### 配置文件位置

5 个拆分后的技能配置文件，均位于 `skills/bukkit/` 目录下：

```
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerInfoSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerStatusSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitPlayerInventorySkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitWorldSkill.yml
plugins/Kilacraft-AI/skills/bukkit/BukkitServerSkill.yml
```

> 注：旧的单一 `apis.yml` / `apis_en.yml` 已在 commit b64cb0e 中移除，不再使用。

---

## 🔧 技能配置结构

> 自 commit b64cb0e 起，原先基于单一 `apis.yml` + `method_chain`/`additional_methods`/`result_template` 的数据驱动模型已被移除。5 个拆分技能现在采用与项目其他内置 Skill 完全一致的标准配置结构。

### 标准技能配置 Schema

每个 `Bukkit*Skill.yml` 文件遵循统一的 Skill 配置 schema，核心字段为 `description` + `action_descriptions`（文本块）+ `hints`：

```yaml
# BukkitPlayerInventorySkill.yml（节选示例）
skill_id: "player_inventoryentory"
display_name: "Bukkit 玩家物品栏查询"
description: |
  查询玩家物品栏相关信息：主手/副手物品、背包与末影箱摘要、
  背包占用、盔甲装备、当前打开的容器/界面类型等。
permission: "kilacraft.api.player.inventory"

action_descriptions: |
  get_player_hand_item — 获取玩家主手物品（类型/数量/附魔/耐久）
  get_player_offhand_item — 获取玩家副手物品（盾牌槽）
  get_player_armor — 获取全套盔甲（头盔/胸甲/护腿/靴子，含名称/类型/数量/附魔/耐久）
  get_player_inventory_usage — 获取背包已占用格数/空格数
  get_player_inventory — 获取背包内物品名称+数量列表
  ...

hints: |
  - 物品栏查询为只读操作，安全可异步。
  - 盔甲/背包/容器等需要主线程的方法已由技能内部调度处理。
```

### 关键变化

| 旧模型（已移除） | 新模型（当前） |
|------------------|----------------|
| 单一 `apis.yml`，71 条 API 条目 | 5 个独立 `Bukkit*Skill.yml` 技能文件 |
| 每条 API 的 `method_chain` / `additional_methods` / `result_template` | 调用与格式化逻辑下沉到 Java 类（`AbstractBukkitQuerySkill` + 子类） |
| 每条 API 一个 `required_permission` 节点 | 每个技能一个权限节点，覆盖该技能全部 API |
| `description` 作为单条 API 说明 | `action_descriptions` 文本块集中描述本技能所有 action |

> **注意**：action ID、返回字段（如 `helmet_name`、`item_count`、`raw_result` 等）以及输出格式均完整保留自原 `apis.yml`，仅是组织方式从「单文件每 API 一段配置」改为「每技能一段 yml + Java 实现」。本节下方各 API 的「返回字段」「多步骤数据传递」说明仍然有效。

---

## 👤 玩家相关 API

### 物品栏相关

#### get_player_hand_item

**功能**：获取玩家主手物品

```yaml
get_player_hand_item:
  id: "get_player_hand_item"
  display_name: "获取玩家主手物品"
  description: "获取玩家主要手持的物品信息，包括物品类型、数量等"
  usage_scenarios:
    - "当用户询问'我手上拿的是什么'"
    - "看看我的物品"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getItemInMainHand"
```

**使用示例**：
```
玩家: 我手上拿着什么？
AI: 你主手拿着：钻石剑 x1
```

---

#### get_player_offhand_item

**功能**：获取玩家副手物品

```yaml
get_player_offhand_item:
  id: "get_player_offhand_item"
  display_name: "获取玩家副手物品"
  description: "获取玩家副手（盾牌槽）持有的物品"
  usage_scenarios:
    - "副手拿了什么"
    - "看看我的副手"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getItemInOffHand"
```

---

### 生命与状态

#### get_player_health

**功能**：获取玩家生命值

```yaml
get_player_health:
  id: "get_player_health"
  display_name: "获取玩家生命值"
  description: "获取玩家当前的生命值和最大生命值"
  usage_scenarios:
    - "我还有多少血"
    - "我的生命值"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    health: "getHealth"
    max_health: "getMaxHealth"
  result_template: "生命值：{health}/{max_health}"
```

**使用示例**：
```
玩家: 我还有多少血？
AI: 生命值：18.5/20.0
```

---

#### get_player_food

**功能**：获取玩家饥饿值

```yaml
get_player_food:
  id: "get_player_food"
  display_name: "获取玩家饥饿值"
  description: "获取玩家当前的饥饿值（饱食度）和饱和度"
  usage_scenarios:
    - "我饿了"
    - "我的饥饿值"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    food_level: "getFoodLevel"
    saturation: "getSaturation"
  result_template: "饱食度：{food_level}/20, 饱和度：{saturation}"
```

---

#### get_player_oxygen

**功能**：获取玩家氧气值

```yaml
get_player_oxygen:
  id: "get_player_oxygen"
  display_name: "获取玩家氧气值"
  description: "获取玩家当前的氧气值（水下呼吸时间），单位为 tick"
  usage_scenarios:
    - "我能憋气多久"
    - "氧气还剩多少"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    remaining_air: "getRemainingAir"
    maximum_air: "getMaximumAir"
  result_template: "氧气：{remaining_air}/{maximum_air} tick"
```

---

### 位置与移动

#### get_player_location

**功能**：获取玩家位置坐标

```yaml
get_player_location:
  id: "get_player_location"
  display_name: "获取玩家位置"
  description: "获取玩家在游戏中的坐标位置（X, Y, Z）和所在世界"
  usage_scenarios:
    - "我在哪里"
    - "我的坐标是多少"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    x: "getLocation.getX"
    y: "getLocation.getY"
    z: "getLocation.getZ"
    world: "getLocation.getWorld.getName"
  result_template: "位置：X={x}, Y={y}, Z={z}, 世界={world}"
```

**使用示例**：
```
玩家: 我在哪？
AI: 位置：X=128, Y=64, Z=-256, 世界=world
```

---

#### get_player_eye_location

**功能**：获取玩家视线位置

```yaml
get_player_eye_location:
  id: "get_player_eye_location"
  display_name: "获取玩家视线位置"
  description: "获取玩家眼睛（视线起点）的精确坐标位置"
  usage_scenarios:
    - "我的眼睛在哪"
    - "我的视线起点在哪"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getEyeLocation"
```

---

#### get_player_velocity

**功能**：获取玩家速度向量

```yaml
get_player_velocity:
  id: "get_player_velocity"
  display_name: "获取玩家速度向量"
  description: "获取玩家当前的移动速度向量（X, Y, Z 方向的速度）"
  usage_scenarios:
    - "我的速度是多少"
    - "我在往哪个方向移动"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getVelocity"
```

---

### 游戏模式与飞行

#### get_player_gamemode

**功能**：获取玩家游戏模式

```yaml
get_player_gamemode:
  id: "get_player_gamemode"
  display_name: "获取玩家游戏模式"
  description: "获取玩家当前的游戏模式（生存/创造/冒险/旁观）"
  usage_scenarios:
    - "我是什么模式"
    - "我的游戏模式"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getGameMode"
```

**使用示例**：
```
玩家: 我是什么游戏模式？
AI: 生存模式
```

---

#### get_player_fly_status

**功能**：获取玩家飞行状态

```yaml
get_player_fly_status:
  id: "get_player_fly_status"
  display_name: "获取玩家飞行状态"
  description: "获取玩家是否允许飞行以及当前是否正在飞行"
  usage_scenarios:
    - "我能飞吗"
    - "我现在在飞吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    allow_flight: "getAllowFlight"
    is_flying: "isFlying"
  result_template: "允许飞行：{allow_flight}, 正在飞行：{is_flying}"
```

---

#### get_player_fly_speed

**功能**：获取玩家飞行速度

```yaml
get_player_fly_speed:
  id: "get_player_fly_speed"
  display_name: "获取玩家飞行速度"
  description: "获取玩家的飞行速度设置"
  usage_scenarios:
    - "我的飞行速度是多少"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getFlySpeed"
```

---

#### get_player_walk_speed

**功能**：获取玩家行走速度

```yaml
get_player_walk_speed:
  id: "get_player_walk_speed"
  display_name: "获取玩家行走速度"
  description: "获取玩家的行走速度设置"
  usage_scenarios:
    - "我的移动速度是多少"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getWalkSpeed"
```

---

### 经验与等级

#### get_player_exp

**功能**：获取玩家经验值

```yaml
get_player_exp:
  id: "get_player_exp"
  display_name: "获取玩家经验值"
  description: "获取玩家当前的经验值和等级"
  usage_scenarios:
    - "我有多少经验"
    - "我的等级是多少"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    exp_progress: "getExp"
    level: "getLevel"
  result_template: "等级：{level}, 经验进度：{exp_progress}"
```

---

#### get_player_exp_to_level

**功能**：获取升到下一级所需经验

```yaml
get_player_exp_to_level:
  id: "get_player_exp_to_level"
  display_name: "获取升到下一级所需经验"
  description: "获取从当前经验进度升到下一级所需的经验值点数"
  usage_scenarios:
    - "升到下一级需要多少经验"
    - "我还要多少经验才能升级"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getExpToLevel"
```

---

### 其他状态

#### get_player_main_hand

**功能**：获取玩家主手偏好

```yaml
get_player_main_hand:
  id: "get_player_main_hand"
  display_name: "获取玩家主手偏好"
  description: "获取玩家的左右手偏好设置（左撇子/右撇子）"
  usage_scenarios:
    - "我是左撇子还是右撇子"
    - "我的主手设置"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getMainHand"
```

---

#### get_player_ping

**功能**：获取玩家延迟

```yaml
get_player_ping:
  id: "get_player_ping"
  display_name: "获取玩家延迟"
  description: "获取玩家的网络延迟（ping），单位为毫秒"
  usage_scenarios:
    - "我的延迟是多少"
    - "我卡不卡"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getPing"
```

---

#### get_player_sleep_status

**功能**：获取玩家睡眠状态

```yaml
get_player_sleep_status:
  id: "get_player_sleep_status"
  display_name: "获取玩家睡眠状态"
  description: "获取玩家是否正在睡觉以及睡眠时间"
  usage_scenarios:
    - "我在睡觉吗"
    - "我的睡眠状态"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    is_sleeping: "isSleeping"
    sleep_ticks: "getSleepTicks"
  result_template: "正在睡觉：{is_sleeping}, 睡眠时间：{sleep_ticks} tick"
```

---

#### get_player_last_death

**功能**：获取玩家上次死亡位置

```yaml
get_player_last_death:
  id: "get_player_last_death"
  display_name: "获取玩家上次死亡位置"
  description: "获取玩家上次死亡的位置坐标"
  usage_scenarios:
    - "我上次死在哪里"
    - "我的死亡地点"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getLastDeathLocation"
```

---

#### get_player_attack_cooldown

**功能**：获取玩家攻击冷却

```yaml
get_player_attack_cooldown:
  id: "get_player_attack_cooldown"
  display_name: "获取玩家攻击冷却"
  description: "获取玩家当前的攻击冷却进度（0-1，1表示冷却完成）"
  usage_scenarios:
    - "我的攻击冷却"
    - "我可以攻击了吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getAttackCooldown"
```

---

#### get_player_vehicle

**功能**：获取玩家骑乘状态

```yaml
get_player_vehicle:
  id: "get_player_vehicle"
  display_name: "获取玩家骑乘状态"
  description: "获取玩家是否在载具中以及载具类型"
  usage_scenarios:
    - "我在骑什么"
    - "我在坐船吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  additional_methods:
    in_vehicle: "isInsideVehicle"
  result_template: "是否在载具中：{in_vehicle}"
```

---

#### get_player_fire_status

**功能**：获取玩家着火状态

```yaml
get_player_fire_status:
  id: "get_player_fire_status"
  display_name: "获取玩家着火状态"
  description: "获取玩家当前是否着火，未着火时显示'未着火'，着火时显示剩余燃烧时间（秒）"
  usage_scenarios:
    - "我着火了吗"
    - "还要烧多久"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    fire_ticks: "getFireTicks"
    max_fire_ticks: "getMaxFireTicks"
  result_template: "着火时间：{fire_ticks}/{max_fire_ticks} tick"
```

---

#### get_player_freeze_status

**功能**：获取玩家冰冻状态

```yaml
get_player_freeze_status:
  id: "get_player_freeze_status"
  display_name: "获取玩家冰冻状态"
  description: "获取玩家是否被冰冻（在细雪中）以及冰冻程度"
  usage_scenarios:
    - "我被冻住了吗"
    - "我的冰冻状态"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  additional_methods:
    is_frozen: "isFrozen"
    freeze_ticks: "getFreezeTicks"
    max_freeze_ticks: "getMaxFreezeTicks"
  result_template: "是否冰冻：{is_frozen}, 冰冻程度：{freeze_ticks}/{max_freeze_ticks} tick"
```

---

#### get_player_pose

**功能**：获取玩家姿势

```yaml
get_player_pose:
  id: "get_player_pose"
  display_name: "获取玩家姿势"
  description: "获取玩家当前的姿势状态（站立/蹲下/游泳/睡觉等）"
  usage_scenarios:
    - "我现在的姿势是什么"
    - "我在蹲着吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getPose"
```

---

#### get_player_armor

**功能**：获取玩家盔甲装备

```yaml
get_player_armor:
  id: "get_player_armor"
  display_name: "获取玩家盔甲装备"
  description: "获取玩家当前穿戴的全套盔甲（头盔、胸甲、护腿、靴子）。返回的 data 中按槽位展开为系列字段：helmet_name/helmet_type/helmet_amount/helmet_enchantments/helmet_max_durability/helmet_remaining_durability（以及 chestplate_*/leggings_*/boots_* 同结构），可供后续步骤引用。"
  usage_scenarios:
    - "我穿了什么装备"
    - "看看我的盔甲"
    - "我的护甲"
  target_type: "Player"
  required_permission: "kilacraft.api.player.inventory"
  method_chain:
    - "getInventory"
    - "getArmorContents"
```

**使用示例**：
```
玩家: 我穿了什么装备？
AI: 你的盔甲装备：
• 头盔：钻石头盔
• 胸甲：钻石胸甲
• 护腿：钻石护腿
• 靴子：铁靴子
```

**多步骤数据传递**：
返回的 data 中每个槽位展开为一组字段：`<slot>_name`、`<slot>_type`、`<slot>_amount`，若有附魔/耐久则额外包含 `<slot>_enchantments`、`<slot>_max_durability`、`<slot>_remaining_durability`。其中 `<slot>` ∈ `helmet`/`chestplate`/`leggings`/`boots`。后续步骤可通过 `{step_xxx.helmet_name}`、`{step_xxx.boots_remaining_durability}` 等引用。空槽位对应的字段不会被写入。

---

#### get_player_potion_effects

**功能**：获取玩家药水效果

```yaml
get_player_potion_effects:
  id: "get_player_potion_effects"
  display_name: "获取玩家药水效果"
  description: "获取玩家当前所有活跃的药水效果列表（包括效果名称、等级、剩余时间）。返回的 data 中包含 effects 字段（效果列表），可供后续步骤引用。"
  usage_scenarios:
    - "我有什么药水效果"
    - "我的 BUFF"
    - "我中毒了吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getActivePotionEffects"
```

**使用示例**：
```
玩家: 我有什么药水效果？
AI: 当前药水效果：
• 速度 II (剩余 2:30)
• 生命恢复 I (剩余 0:45)
```

**多步骤数据传递**：
返回的 data 中包含 `effects` 字段（药水效果列表，每个效果包含 type、duration、amplifier 字段），后续步骤可通过 `{step_xxx.effects[0].type}` 等引用列表中的特定元素。

---

#### get_player_target_block

**功能**：获取玩家瞄准方块

```yaml
get_player_target_block:
  id: "get_player_target_block"
  display_name: "获取玩家瞄准方块"
  description: "获取玩家准星当前瞄准的方块（最大距离 100 格）。返回的 data 中包含 block_type、x、y、z 字段，可供后续步骤引用。"
  usage_scenarios:
    - "我面前是什么方块"
    - "我盯着的方块"
    - "我瞄准的位置"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getTargetBlock"
```

**使用示例**：
```
玩家: 我面前是什么方块？
AI: 你瞄准的方块：钻石矿石 (X=128, Y=16, Z=-256)
```

**多步骤数据传递**：
返回的 data 中包含 `block_type`、`x`、`y`、`z` 字段，后续步骤可通过 `{step_xxx.block_type}` 等引用。

---

#### get_player_sneak_status

**功能**：获取玩家潜行状态

```yaml
get_player_sneak_status:
  id: "get_player_sneak_status"
  display_name: "获取玩家潜行状态"
  description: "获取玩家当前是否在潜行（Shift 键）"
  usage_scenarios:
    - "我在潜行吗"
    - "我蹲下了吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "isSneaking"
```

**使用示例**：
```
玩家: 我在蹲着吗？
AI: 是的，你正在潜行
```

---

#### get_player_sprint_status

**功能**：获取玩家冲刺状态

```yaml
get_player_sprint_status:
  id: "get_player_sprint_status"
  display_name: "获取玩家冲刺状态"
  description: "获取玩家当前是否在冲刺（双击 W）"
  usage_scenarios:
    - "我在跑步吗"
    - "我在冲刺吗"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "isSprinting"
```

**使用示例**：
```
玩家: 我在跑步吗？
AI: 不，你当前没有在冲刺
```

---

#### get_player_locale

**功能**：获取玩家客户端语言

```yaml
get_player_locale:
  id: "get_player_locale"
  display_name: "获取玩家客户端语言"
  description: "获取玩家客户端的语言设置（如 zh_CN、en_US）"
  usage_scenarios:
    - "我的游戏语言是什么"
    - "我的客户端语言"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getLocale"
```

**使用示例**：
```
玩家: 我的游戏语言是什么？
AI: 你的客户端语言：zh_CN（简体中文）
```

---

#### get_player_display_name

**功能**：获取玩家显示名称

```yaml
get_player_display_name:
  id: "get_player_display_name"
  display_name: "获取玩家显示名称"
  description: "获取玩家的显示名称（可能包含前缀/后缀，不同于 getName）"
  usage_scenarios:
    - "我的显示名字是什么"
    - "别人看到我叫什么"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getDisplayName"
```

**使用示例**：
```
玩家: 别人看到我叫什么？
AI: 你的显示名称：[VIP] Player123
```

---

#### get_player_bed_spawn

**功能**：获取玩家床重生点

```yaml
get_player_bed_spawn:
  id: "get_player_bed_spawn"
  display_name: "获取玩家床重生点"
  description: "获取玩家设置的床重生点位置（如果未设置返回 null）。返回的 data 中包含 x、y、z、world 字段，可供后续步骤引用。"
  usage_scenarios:
    - "我的床在哪"
    - "我的重生点"
    - "我设置的床位置"
  target_type: "Player"
  required_permission: "kilacraft.api.player.info"
  method_chain:
    - "getBedSpawnLocation"
```

**使用示例**：
```
玩家: 我的床在哪？
AI: 你的床重生点：X=256, Y=64, Z=128, 世界=world
```

**多步骤数据传递**：
返回的 data 中包含 `x`、`y`、`z`、`world` 字段，后续步骤可通过 `{step_xxx.x}` 等引用。

---

#### get_player_total_exp

**功能**：获取玩家总经验值

```yaml
get_player_total_exp:
  id: "get_player_total_exp"
  display_name: "获取玩家总经验值"
  description: "获取玩家从开始游戏至今累积的总经验值（区别于当前等级的经验进度）。返回的 data 中包含 total_exp 字段，可供后续步骤引用。"
  usage_scenarios:
    - "我总共获得了多少经验"
    - "我的总经验值"
    - "我从开服到现在攒了多少经验"
  target_type: "Player"
  required_permission: "kilacraft.api.player.status"
  method_chain:
    - "getTotalExperience"
```

**使用示例**：
```
玩家: 我总共获得了多少经验？
AI: 你的总经验值：125,680
```

**多步骤数据传递**：
返回的 data 中包含 `total_exp` 字段，后续步骤可通过 `{step_xxx.total_exp}` 引用。

---

## 🌍 世界相关 API

### 时间与天气

#### get_world_time

**功能**：获取世界时间

```yaml
get_world_time:
  id: "get_world_time"
  display_name: "获取世界时间"
  description: "获取当前世界的游戏时间（刻数），会被自动格式化为 HH:MM 格式。返回的 data 中包含 time_ticks 字段（当前世界刻数），可供后续步骤引用或挂机任务条件比较"
  usage_scenarios:
    - "现在几点了"
    - "世界时间"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getTime"
```

**使用示例**：
```
玩家: 现在几点？
AI: 世界时间：06:00
```

**多步骤数据传递**：
返回的 data 中包含 `time_ticks` 字段（Long 类型，世界刻数），后续步骤可通过 `{step_xxx.time_ticks}` 引用。挂机任务的条件评估也可使用该字段进行数值比较。

---

#### get_weather

**功能**：获取天气状况

```yaml
get_weather:
  id: "get_weather"
  display_name: "获取天气状况"
  description: "获取当前世界的天气（晴天/雨天/雷暴）"
  usage_scenarios:
    - "现在天气如何"
    - "会下雨吗"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    has_storm: "hasStorm"
    is_thundering: "isThundering"
  result_template: "天气：{weather_desc}"
```

---

### 世界信息

#### get_world_info

**功能**：获取世界基本信息

```yaml
get_world_info:
  id: "get_world_info"
  display_name: "获取世界基本信息"
  description: "获取当前世界的名称、环境类型（主世界/下界/末地）和难度"
  usage_scenarios:
    - "这个世界是什么类型"
    - "这个世界的难度"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    name: "getName"
    environment: "getEnvironment"
    difficulty: "getDifficulty"
  result_template: "世界：{name}, 类型：{environment}, 难度：{difficulty}"
```

---

#### get_world_seed

**功能**：获取世界种子

```yaml
get_world_seed:
  id: "get_world_seed"
  display_name: "获取世界种子"
  description: "获取当前世界的种子值"
  usage_scenarios:
    - "这个世界种子是多少"
    - "世界种子"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSeed"
```

---

#### get_world_spawn

**功能**：获取世界出生点

```yaml
get_world_spawn:
  id: "get_world_spawn"
  display_name: "获取世界出生点"
  description: "获取当前世界的出生点位置"
  usage_scenarios:
    - "出生点在哪"
    - "世界的重生点"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSpawnLocation"
```

---

#### get_world_height_limit

**功能**：获取世界高度限制

```yaml
get_world_height_limit:
  id: "get_world_height_limit"
  display_name: "获取世界高度限制"
  description: "获取当前世界的最低和最高建筑高度"
  usage_scenarios:
    - "这个世界能建多高"
    - "最低能挖到哪"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    min_height: "getMinHeight"
    max_height: "getMaxHeight"
  result_template: "高度范围：{min_height} ~ {max_height}"
```

---

#### get_world_spawn_rules

**功能**：获取世界生物生成规则

```yaml
get_world_spawn_rules:
  id: "get_world_spawn_rules"
  display_name: "获取世界生物生成规则"
  description: "获取当前世界是否允许生成怪物和动物"
  usage_scenarios:
    - "这里会刷怪吗"
    - "这里会刷动物吗"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    allow_monsters: "getAllowMonsters"
    allow_animals: "getAllowAnimals"
  result_template: "允许怪物：{allow_monsters}, 允许动物：{allow_animals}"
```

---

#### get_world_pvp

**功能**：获取世界 PVP 设置

```yaml
get_world_pvp:
  id: "get_world_pvp"
  display_name: "获取世界 PVP 设置"
  description: "获取当前世界是否允许 PVP"
  usage_scenarios:
    - "这里能打架吗"
    - "这个世界能 PVP 吗"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getPVP"
```

---

#### get_world_biome

**功能**：获取世界生物群系

```yaml
get_world_biome:
  id: "get_world_biome"
  display_name: "获取世界生物群系"
  description: "获取指定坐标的生物群系类型（如平原、沙漠、森林等）。默认查询玩家当前位置。返回的 data 中包含 biome 字段，可供后续步骤引用。"
  usage_scenarios:
    - "我所在的是什么群系"
    - "这里是沙漠吗"
    - "当前生物群系"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getBiome"
```

**使用示例**：
```
玩家: 我所在的是什么群系？
AI: 当前生物群系：平原
```

**多步骤数据传递**：
返回的 data 中包含 `biome` 字段，后续步骤可通过 `{step_xxx.biome}` 引用。

---

#### get_world_temperature

**功能**：获取世界温度

```yaml
get_world_temperature:
  id: "get_world_temperature"
  display_name: "获取世界温度"
  description: "获取指定坐标的温度值（影响生物生成、降雪等）。默认查询玩家当前位置。返回的 data 中包含 temperature 字段，可供后续步骤引用。"
  usage_scenarios:
    - "这里的温度是多少"
    - "这里会下雪吗"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getTemperature"
```

**使用示例**：
```
玩家: 这里的温度是多少？
AI: 当前温度：0.8（不会下雪）
```

**多步骤数据传递**：
返回的 data 中包含 `temperature` 字段，后续步骤可通过 `{step_xxx.temperature}` 引用。

---

#### get_world_humidity

**功能**：获取世界湿度

```yaml
get_world_humidity:
  id: "get_world_humidity"
  display_name: "获取世界湿度"
  description: "获取指定坐标的湿度值（影响降雨概率）。默认查询玩家当前位置。返回的 data 中包含 humidity 字段，可供后续步骤引用。"
  usage_scenarios:
    - "这里的湿度是多少"
    - "这里容易下雨吗"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getHumidity"
```

**使用示例**：
```
玩家: 这里容易下雨吗？
AI: 当前湿度：0.3（降雨概率较低）
```

**多步骤数据传递**：
返回的 data 中包含 `humidity` 字段，后续步骤可通过 `{step_xxx.humidity}` 引用。

---

#### get_world_sea_level

**功能**：获取世界海平面高度

```yaml
get_world_sea_level:
  id: "get_world_sea_level"
  display_name: "获取世界海平面高度"
  description: "获取当前世界的海平面高度（Y 坐标）"
  usage_scenarios:
    - "海平面有多高"
    - "这个世界的海平面"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getSeaLevel"
```

**使用示例**：
```
玩家: 海平面有多高？
AI: 当前世界的海平面高度：Y=63
```

---

#### get_world_player_count

**功能**：获取世界玩家数量

```yaml
get_world_player_count:
  id: "get_world_player_count"
  display_name: "获取世界玩家数量"
  description: "获取当前世界中的玩家数量"
  usage_scenarios:
    - "这个世界有几个人"
    - "主世界有多少玩家"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    player_count: "getPlayers.size"
  result_template: "当前世界玩家数量：{player_count}"
```

**使用示例**：
```
玩家: 主世界有多少玩家？
AI: 当前世界玩家数量：5
```

---

#### get_world_living_entities

**功能**：获取世界生物数量

```yaml
get_world_living_entities:
  id: "get_world_living_entities"
  display_name: "获取世界生物数量"
  description: "获取当前世界中所有存活生物（包括玩家、怪物、动物等）的数量"
  usage_scenarios:
    - "这个世界有多少生物"
    - "这里有多少活物"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    living_entities: "getLivingEntities.size"
  result_template: "当前世界生物数量：{living_entities}"
```

**使用示例**：
```
玩家: 这个世界有多少生物？
AI: 当前世界生物数量：128
```

---

#### get_world_entity_count

**功能**：获取世界实体总数

```yaml
get_world_entity_count:
  id: "get_world_entity_count"
  display_name: "获取世界实体总数"
  description: "获取当前世界中所有实体（包括生物、物品、矿车、画等）的总数"
  usage_scenarios:
    - "这个世界有多少实体"
    - "这里的实体总数"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  additional_methods:
    entity_count: "getEntities.size"
  result_template: "当前世界实体总数：{entity_count}"
```

**使用示例**：
```
玩家: 这里的实体总数？
AI: 当前世界实体总数：456
```

---

#### get_world_clear_weather_duration

**功能**：获取晴天剩余时间

```yaml
get_world_clear_weather_duration:
  id: "get_world_clear_weather_duration"
  display_name: "获取晴天剩余时间"
  description: "获取当前晴天天气剩余的持续时间（tick）"
  usage_scenarios:
    - "晴天还能持续多久"
    - "多久后会下雨"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getClearWeatherDuration"
```

**使用示例**：
```
玩家: 晴天还能持续多久？
AI: 晴天剩余时间：12000 tick（约 10 分钟）
```

---

#### get_world_thunder_duration

**功能**：获取雷暴剩余时间

```yaml
get_world_thunder_duration:
  id: "get_world_thunder_duration"
  display_name: "获取雷暴剩余时间"
  description: "获取当前雷暴天气剩余的持续时间（tick）"
  usage_scenarios:
    - "雷暴还要持续多久"
    - "雷暴什么时候结束"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getThunderDuration"
```

**使用示例**：
```
玩家: 雷暴还要持续多久？
AI: 雷暴剩余时间：6000 tick（约 5 分钟）
```

---

#### get_world_full_time

**功能**：获取世界总时间

```yaml
get_world_full_time:
  id: "get_world_full_time"
  display_name: "获取世界总时间"
  description: "获取世界的总运行时间（tick），不受睡眠影响，持续累加。返回的 data 中包含 full_time 字段，可供后续步骤引用。"
  usage_scenarios:
    - "这个世界运行了多久"
    - "世界总时间"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getFullTime"
```

**使用示例**：
```
玩家: 这个世界运行了多久？
AI: 世界总运行时间：2,400,000 tick（约 33 小时 20 分钟）
```

**多步骤数据传递**：
返回的 data 中包含 `full_time` 字段，后续步骤可通过 `{step_xxx.full_time}` 引用。

---

#### get_world_game_time

**功能**：获取世界游戏时间

```yaml
get_world_game_time:
  id: "get_world_game_time"
  display_name: "获取世界游戏时间"
  description: "获取世界自创建以来的总游戏时间（tick），不受 /time set 影响。返回的 data 中包含 game_time 字段，可供后续步骤引用。"
  usage_scenarios:
    - "这个世界创建多久了"
    - "世界游戏时间"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getGameTime"
```

**使用示例**：
```
玩家: 这个世界创建多久了？
AI: 世界游戏时间：5,000,000 tick（约 69 小时 26 分钟）
```

**多步骤数据传递**：
返回的 data 中包含 `game_time` 字段，后续步骤可通过 `{step_xxx.game_time}` 引用。

---

#### get_world_raids

**功能**：获取世界袭击事件

```yaml
get_world_raids:
  id: "get_world_raids"
  display_name: "获取世界袭击事件"
  description: "获取当前世界中正在进行的所有袭击（Raid）列表。返回的 data 中包含 raids 字段（袭击数量），可供后续步骤引用。"
  usage_scenarios:
    - "现在有袭击吗"
    - "村庄在被攻击吗"
    - "当前的袭击事件"
  target_type: "World"
  required_permission: "kilacraft.api.world.info"
  method_chain:
    - "getRaids"
```

**使用示例**：
```
玩家: 现在有袭击吗？
AI: 当前正在进行 2 个袭击事件
```

**多步骤数据传递**：
返回的 data 中包含 `raids` 字段（袭击事件列表），后续步骤可通过 `{step_xxx.raids[0]}` 等引用列表中的特定袭击事件。

---

## 🖥️ 服务器相关 API

### 玩家信息

#### get_online_players

**功能**：获取在线玩家数量

```yaml
get_online_players:
  id: "get_online_players"
  display_name: "获取在线玩家数量"
  description: "获取当前服务器上的在线玩家数量和列表"
  usage_scenarios:
    - "有多少人在线"
    - "服务器有几个人"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getOnlinePlayers"
```

**使用示例**：
```
玩家: 有多少人在线？
AI: 当前在线玩家：5 人
```

---

#### get_max_players

**功能**：获取最大玩家数

```yaml
get_max_players:
  id: "get_max_players"
  display_name: "获取最大玩家数"
  description: "获取服务器允许的最大玩家数量"
  usage_scenarios:
    - "服务器能容纳多少人"
    - "最大玩家数量"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getMaxPlayers"
```

---

### 版本信息

#### get_server_version

**功能**：获取服务器版本

```yaml
get_server_version:
  id: "get_server_version"
  display_name: "获取服务器版本"
  description: "获取服务器的版本信息（Bukkit 版本和 Minecraft 版本）"
  usage_scenarios:
    - "服务器是什么版本"
    - "服务器版本"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  additional_methods:
    version: "getVersion"
    bukkit_version: "getBukkitVersion"
  result_template: "服务器版本：{version}, Bukkit 版本：{bukkit_version}"
```

---

#### get_server_motd

**功能**：获取服务器 MOTD

```yaml
get_server_motd:
  id: "get_server_motd"
  display_name: "获取服务器 MOTD"
  description: "获取服务器的 MOTD（每日消息/服务器介绍）"
  usage_scenarios:
    - "服务器的介绍是什么"
    - "服务器 MOTD"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getMotd"
```

---

### 世界列表

#### get_server_worlds

**功能**：获取服务器世界列表

```yaml
get_server_worlds:
  id: "get_server_worlds"
  display_name: "获取服务器世界列表"
  description: "获取服务器上所有已加载的世界列表"
  usage_scenarios:
    - "服务器有哪些世界"
    - "世界列表"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  method_chain:
    - "getWorlds"
```

---

### 服务器设置

#### get_server_settings

**功能**：获取服务器设置

```yaml
get_server_settings:
  id: "get_server_settings"
  display_name: "获取服务器设置"
  description: "获取服务器的基本设置（是否允许飞行、下界、末地）"
  usage_scenarios:
    - "服务器有下界吗"
    - "服务器有末地吗"
    - "服务器允许飞行吗"
  target_type: "Server"
  required_permission: "kilacraft.api.server.info"
  additional_methods:
    allow_flight: "getAllowFlight"
    allow_nether: "getAllowNether"
    allow_end: "getAllowEnd"
  result_template: "允许飞行：{allow_flight}, 允许下界：{allow_nether}, 允许末地：{allow_end}"
```

---

## 🎯 高级用法

### 关于「自定义只读查询 API」

> ⚠️ 重要变更：旧的单一 `apis.yml` 允许用户通过 `method_chain`/`additional_methods` 自行添加查询 API；该模型已被移除。

自 commit b64cb0e 拆分为 5 个标准配置驱动技能后，**这 71 个只读查询 API 不再通过用户配置扩展**。原因：

- 查询逻辑（方法调用、参数处理、结果格式化）已下沉到各技能的 Java 类（`AbstractBukkitQuerySkill` + 子类），不再读取用户编写的 `method_chain` 配置；
- 添加新的查询 API 现在需要修改对应的 Java 技能类 + 更新该技能 yml 的 `action_descriptions`，属于代码层面的开发工作，不再是无代码配置。

如果你希望覆盖更多查询场景，推荐做法：

1. **扩展知识库**：在 `knowledge/` 目录下补充文档，让 AI 利用现有的 71 个 API + 知识库回答更广泛的问题；
2. **开发自定义 Skill**：参照《Skill SPI 接入文档》开发独立的 Skill（完全脱离 bukkit 只读查询技能体系），实现任意自定义逻辑。

---

### 组合查询

AI 可以自动组合多个 API 调用来回答复杂问题：

```
玩家: 我现在状态怎么样？
→ AI 识别为多步骤任务：
   1. get_player_health（生命值）
   2. get_player_food（饥饿值）
   3. get_player_gamemode（游戏模式）
   4. get_player_location（位置）
→ 综合分析后回复：
   你当前状态：
   • 生命值：18.5/20.0
   • 饱食度：16/20, 饱和度：5.2
   • 游戏模式：生存模式
   • 位置：X=128, Y=64, Z=-256, 世界=world
```

---

## 🔒 权限管理

### 权限节点

权限按**技能（skill）**粒度授予，**一个权限节点覆盖该技能下的全部 API**（不再是每条 API 一个节点）。共 5 个节点，与 5 个拆分技能一一对应：

| 权限节点 | 所属技能 | 覆盖的 API 范围 |
|----------|----------|-----------------|
| `kilacraft.api.player.info` | `player_info` | 玩家基本信息：位置、视线、速度、游戏模式、飞行、主手偏好、延迟、上次死亡、载具、姿势、瞄准方块、客户端语言、显示名、床重生点、指南针目标、脚下方块等 |
| `kilacraft.api.player.status` | `player_status` | 玩家状态：生命值、饥饿值、氧气、经验/等级、睡眠、攻击冷却、着火、冰冻、药水效果、潜行、冲刺、吸收之心、身上箭、无敌帧、下落距离、上次受伤等 |
| `kilacraft.api.player.inventory` | `player_inventoryentory` | 玩家物品栏：主/副手物品、背包摘要、末影箱摘要、背包占用、盔甲、打开的容器、打开的界面类型 |
| `kilacraft.api.world.info` | `world_info` | 世界信息：时间、天气、种子、出生点、高度限制、生成规则、PVP、生物群系、温度、湿度、海平面、实体数量、袭击、边界等 |
| `kilacraft.api.server.info` | `server_info` | 服务器信息：在线玩家、最大玩家数、版本、MOTD、世界列表、服务器设置 |

> ⚠️ 注意：不再存在 `kilacraft.api.player.health`、`kilacraft.api.player.stats` 等节点。生命值/饥饿值等状态类 API 统一归入 `kilacraft.api.player.status`。

**通配符权限**：
```yaml
kilacraft.api.*              # 所有 API 权限
kilacraft.api.player.*       # 所有玩家相关 API（info + status + inventory）
kilacraft.api.world.*        # 所有世界相关 API
kilacraft.api.server.*       # 所有服务器相关 API
```

### 授予权限

使用 LuckPerms 插件授予权限：

```bash
# 授予某个技能的权限（例如玩家状态技能，覆盖生命值/饥饿值等全部状态 API）
/lp user <player> permission set kilacraft.api.player.status true

# 授予所有玩家相关 API 权限
/lp user <player> permission set kilacraft.api.player.* true

# 授予所有 API 权限
/lp user <player> permission set kilacraft.api.* true
```

---

## ⚙️ 性能优化建议

### 1. 只读操作

所有 Bukkit API 都是**只读操作**，不会修改游戏状态：
- ✅ 安全：不会意外改变玩家数据
- ✅ 隔离：API 执行失败不影响其他功能
- ✅ 并发：可在异步线程中安全执行

### 2. 权限检查

权限按技能粒度检查：执行某条 API 前会先复查该 API 所属技能的权限节点，确保只有授权玩家才能访问敏感信息。

### 3. 直接调用

各技能的 Java 类（`AbstractBukkitQuerySkill` + 子类）直接调用 Bukkit API，不再经过旧的反射执行器（`BukkitAPIExecutor` 已随 `apis.yml` 一并移除），调用路径更短、更易被 JVM 内联优化。

---

## 🐛 故障排除

### API 返回 null

**问题**：某些 API 调用返回 `null` 值

**原因**：
- 玩家离线
- 世界不存在
- 目标对象不存在（如未打开容器时查询 `get_player_open_container`）

**解决**：
- 确认查询的目标玩家在线、世界已加载
- 查看控制台是否有错误日志
- 若仍异常，可能是对应技能 Java 类的内部调度失败，请提交问题反馈

---

### 权限不足

**问题**：玩家收到"你没有权限执行此操作"的错误提示

**原因**：玩家没有对应技能的权限节点（权限按技能粒度授予）

**解决**：
```bash
# 例如玩家查询生命值/饥饿值等状态类 API，需要 player.status 技能权限
/lp user <player> permission set kilacraft.api.player.status true
```

---

### API 未注册

**问题**：AI 无法识别某个 API

**原因**：
- 对应的 `Bukkit*Skill.yml` 技能配置文件格式错误或缺失（`BukkitPlayerInfoSkill.yml` / `BukkitPlayerStatusSkill.yml` / `BukkitPlayerInventorySkill.yml` / `BukkitWorldSkill.yml` / `BukkitServerSkill.yml`）
- 技能未正确加载
- 未重新加载配置

**解决**：
1. 检查对应技能的 `Bukkit*.yml` 文件 YAML 格式是否正确
2. 确认 5 个技能文件均存在于 `skills/bukkit/` 目录下
3. 执行 `/kila reload` 重新加载配置
4. 查看控制台是否有错误日志

> 注：旧的 `apis.yml` 已被移除，不再被读取，请勿再创建或编辑该文件。

---

## 📚 相关文档

- [服主指南](./服主指南.md) - 完整的配置和使用说明
- [Skill SPI 接入文档](./Skill-SPI-接入文档.md) - 如何扩展自定义技能
- [更新日志](./更新日志.md) - 版本历史和变更


