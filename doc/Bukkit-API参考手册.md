# Kilacraft-AI - Bukkit API 参考手册

> **版本**: v1.4.2  
> **说明**: 本文档提供所有内置 Bukkit API 的详细说明、配置示例和使用场景

---

## 📖 概述

Kilacraft-AI 内置了 **37 个 Bukkit API**，让 AI 能够访问 Minecraft 服务器的各种数据。这些 API 通过 YAML 配置定义，无需编写代码即可使用。

### 核心特性

- ✅ **数据驱动配置**：在 `apis.yml` 中定义 API，支持热重载
- ✅ **权限控制**：每个 API 可设置独立的访问权限
- ✅ **双模式执行**：支持 method_chain（链式调用）和 additional_methods（并行调用）
- ✅ **智能格式化**：自动处理复杂类型（Location、ItemStack、GameMode 等）
- ✅ **错误隔离**：API 执行失败不影响其他功能

### 配置文件位置

```
plugins/Kilacraft-AI/skills/bukkit/apis.yml
```

---

## 🔧 API 配置结构

### 基本结构

```yaml
player:  # 分类（player/world/server/paper_player/paper_world/paper_server）
  api_id:  # API 唯一标识符
    id: "api_id"  # API ID（与键名一致）
    display_name: "API 显示名称"
    description: "API 功能描述，会发送给 LLM"
    usage_scenarios:  # 使用场景示例（可选）
      - "当用户询问'我手上拿的是什么'"
      - "看看我的物品"
    target_type: "Player"  # 目标类型：Player/World/Server
    required_permission: "kilacraft.api.player.inventory"  # 所需权限（可选）
    
    # 以下两个配置二选一：
    
    # 模式 1：method_chain（链式调用，返回复杂对象）
    method_chain:
      - "getInventory"
      - "getItemInMainHand"
    
    # 模式 2：additional_methods（并行调用多个方法）
    additional_methods:
      health: "getHealth"
      max_health: "getMaxHealth"
    result_template: "生命值：{health}/{max_health}"  # 结果模板（仅用于 additional_methods）
```

### 重要规则

#### 1. method_chain vs additional_methods

| 特性 | method_chain | additional_methods |
|------|--------------|-------------------|
| **用途** | 链式调用（接力），返回复杂对象 | 并行调用多个独立方法，获取简单值 |
| **返回值** | ItemStack, Location, GameMode 等 | Map<String, Object> |
| **格式化** | 代码特殊处理（formatItemStack 等） | 使用 result_template 模板替换 |
| **典型应用** | 获取物品、位置、游戏模式 | 获取生命值、坐标、经验值等 |

**示例对比**：

```yaml
# ✅ method_chain：获取主手物品（返回 ItemStack）
get_player_hand_item:
  target_type: "Player"
  method_chain:
    - "getInventory"
    - "getItemInMainHand"
  # 不需要 result_template，由代码自动格式化 ItemStack

# ✅ additional_methods：获取生命值（返回多个数值）
get_player_health:
  target_type: "Player"
  additional_methods:
    health: "getHealth"
    max_health: "getMaxHealth"
  result_template: "生命值：{health}/{max_health}"
```

#### 2. additional_methods 支持简单链式调用

在 `additional_methods` 中，方法名可以使用点号实现两层链式调用：

```yaml
get_player_location:
  target_type: "Player"
  additional_methods:
    x: "getLocation.getX"        # player.getLocation().getX()
    y: "getLocation.getY"        # player.getLocation().getY()
    z: "getLocation.getZ"        # player.getLocation().getZ()
    world: "getLocation.getWorld.getName"  # player.getLocation().getWorld().getName()
  result_template: "位置：X={x}, Y={y}, Z={z}, 世界={world}"
```

**限制**：
- ✅ 最多支持 2 层链式（如 `"a.b"`）
- ❌ 不支持 3 层+ 链式（如 `"a.b.c"`）
- ❌ 不支持带参数的方法

#### 3. result_template 占位符规则

`result_template` 中的占位符 `{key}` 必须与 `additional_methods` 的 key 完全一致：

```yaml
# ✅ 正确
additional_methods:
  health: "getHealth"
result_template: "生命值：{health}"

# ❌ 错误：大小写不一致
result_template: "生命值：{Health}"
```

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

## 🌍 世界相关 API

### 时间与天气

#### get_world_time

**功能**：获取世界时间

```yaml
get_world_time:
  id: "get_world_time"
  display_name: "获取世界时间"
  description: "获取当前世界的游戏时间（刻数），会被自动格式化为 HH:MM 格式"
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

### 自定义 API

你可以添加自己的 Bukkit API 调用。例如，添加一个获取玩家击杀数的 API：

```yaml
player:
  get_player_kills:
    id: "get_player_kills"
    display_name: "获取玩家击杀数"
    description: "查询玩家的总击杀数"
    usage_scenarios:
      - "我杀了多少人"
      - "我的击杀数"
    target_type: "Player"
    required_permission: "kilacraft.api.player.stats"
    method_chain:
      - "getStatistic"  # 注意：此方法需要参数，当前版本暂不支持
```

**注意**：当前版本只支持无参数方法调用，带参数的方法（如 `getStatistic(Statistic.PLAYER_KILLS)`）暂时无法使用。

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

所有 Bukkit API 都有独立的权限节点，格式为：`kilacraft.api.<category>.<type>`

**分类**：
- `player.info` - 玩家基本信息（位置、游戏模式、延迟等）
- `player.status` - 玩家状态信息（生命值、饥饿值、经验等）
- `player.inventory` - 玩家物品栏信息
- `world.info` - 世界信息
- `server.info` - 服务器信息

**通配符权限**：
```yaml
kilacraft.api.*              # 所有 API 权限
kilacraft.api.player.*       # 所有玩家相关 API
kilacraft.api.world.*        # 所有世界相关 API
kilacraft.api.server.*       # 所有服务器相关 API
```

### 授予权限

使用 LuckPerms 插件授予权限：

```bash
# 授予单个 API 权限
/lp user <player> permission set kilacraft.api.player.health true

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

每个 API 都有独立的权限检查，确保只有授权玩家才能访问敏感信息。

### 3. 反射缓存

BukkitAPIExecutor 使用反射调用方法，JVM 会自动优化频繁调用的方法。

---

## 🐛 故障排除

### API 返回 null

**问题**：某些 API 调用返回 `null` 值

**原因**：
- 玩家离线
- 世界不存在
- 方法调用失败

**解决**：
- 检查 API 配置中的 `target_type` 是否正确
- 确认 `method_chain` 或 `additional_methods` 中的方法名存在
- 查看控制台是否有错误日志

---

### 权限不足

**问题**：玩家收到"你没有权限执行此操作"的错误提示

**原因**：玩家没有对应的权限节点

**解决**：
```bash
/lp user <player> permission set kilacraft.api.player.health true
```

---

### API 未注册

**问题**：AI 无法识别某个 API

**原因**：
- `apis.yml` 文件格式错误
- 未重新加载配置

**解决**：
1. 检查 YAML 格式是否正确
2. 执行 `/kilacraft reload` 重新加载配置
3. 查看控制台是否有错误日志

---

### method_chain 和 additional_methods 同时配置

**问题**：API 执行失败，提示"API 必须配置 method_chain 或 additional_methods"

**原因**：两个配置只能选其一，不能同时使用

**解决**：
- 如果需要返回复杂对象（ItemStack、Location 等）→ 使用 `method_chain`
- 如果需要返回多个简单值 → 使用 `additional_methods` + `result_template`

---

## 📚 相关文档

- [服主指南](./服主指南) - 完整的配置和使用说明
- [Skill SPI 接入文档](./Skill-SPI-接入文档) - 如何扩展自定义技能
- [更新日志](./更新日志) - 版本历史和变更

---

> **最后更新**: 2026-04-07  
> **插件版本**: 1.4.2+  
> **API 总数**: 37（玩家 23 + 世界 7 + 服务器 7）
