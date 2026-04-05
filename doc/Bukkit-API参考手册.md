# Kilacraft-AI - Bukkit API 参考手册

> **版本**: v1.4.0  
> **说明**: 本文档提供所有内置 Bukkit API 的详细说明、配置示例和使用场景

---

## 📖 概述

Kilacraft-AI 内置了 **44+ 个 Bukkit API**，让 AI 能够访问 Minecraft 服务器的各种数据。这些 API 通过 YAML 配置定义，无需编写代码即可使用。

### 核心特性

- ✅ **数据驱动配置**：在 `apis.yml` 中定义 API，支持热重载
- ✅ **权限控制**：每个 API 可设置独立的访问权限
- ✅ **参数化查询**：支持动态参数（玩家名、世界名等）
- ✅ **类型安全**：自动类型转换和验证
- ✅ **错误处理**：友好的错误提示和回退机制

### 配置文件位置

```
plugins/Kilacraft-AI/skills/bukkit/apis.yml
```

---

## 🔧 API 配置结构

### 基本结构

```yaml
api_id:
  name: "API 显示名称"
  description: "API 功能描述"
  category: "分类（player/world/server/entity）"
  method: "Bukkit API 方法调用路径"
  return_type: "返回类型（STRING/NUMBER/BOOLEAN/LIST）"
  permission: "所需权限（可选）"
  parameters:  # 参数定义（可选）
    - name: "param_name"
      type: "PLAYER/WORLD/STRING/NUMBER"
      required: true/false
      description: "参数说明"
  examples:  # 使用示例（可选）
    - "示例输入"
    - "另一个示例"
```

### 返回类型说明

| 类型 | 说明 | 示例 |
|------|------|------|
| `STRING` | 字符串 | `"Steve"` |
| `NUMBER` | 数字 | `20.5` |
| `BOOLEAN` | 布尔值 | `true` / `false` |
| `LIST` | 列表 | `["Steve", "Alex"]` |

### 参数类型说明

| 类型 | 说明 | 自动解析 |
|------|------|----------|
| `PLAYER` | 玩家对象 | 从上下文自动获取或使用玩家名 |
| `WORLD` | 世界对象 | 从上下文自动获取或使用世界名 |
| `STRING` | 字符串 | 直接使用 |
| `NUMBER` | 数字 | 自动转换为整数或浮点数 |

---

## 👤 玩家相关 API

### 1. get_player_health

**功能**：获取玩家生命值

```yaml
get_player_health:
  name: "获取玩家生命值"
  description: "查询指定玩家的当前生命值"
  category: "player"
  method: "player.getHealth()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.health"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我还有多少血？"
    - "Steve 的生命值是多少？"
```

**使用示例**：
```
玩家: 我还有多少血？
AI: 你的生命值：18.5/20.0

玩家: Steve 有多少血？
AI: Steve 的生命值：15.0/20.0
```

---

### 2. get_player_hunger

**功能**：获取玩家饥饿值

```yaml
get_player_hunger:
  name: "获取玩家饥饿值"
  description: "查询指定玩家的当前饥饿值"
  category: "player"
  method: "player.getFoodLevel()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.hunger"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我饿吗？"
    - "我的饥饿值是多少？"
```

**使用示例**：
```
玩家: 我饿不饿？
AI: 你的饥饿值：16/20（状态良好）
```

---

### 3. get_player_hand_item

**功能**：获取玩家主手物品

```yaml
get_player_hand_item:
  name: "获取玩家主手物品"
  description: "查询玩家主手当前持有的物品"
  category: "player"
  method: "player.getInventory().getItemInMainHand()"
  return_type: "STRING"
  permission: "kilacraft.api.player.hand_item"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我手上拿着什么？"
    - "看看我的主手物品"
```

**使用示例**：
```
玩家: 我手上拿着什么？
AI: 你主手拿着：钻石剑 x1（耐久度 85%）
```

---

### 4. get_player_offhand_item

**功能**：获取玩家副手物品

```yaml
get_player_offhand_item:
  name: "获取玩家副手物品"
  description: "查询玩家副手当前持有的物品"
  category: "player"
  method: "player.getInventory().getItemInOffHand()"
  return_type: "STRING"
  permission: "kilacraft.api.player.offhand_item"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 5. get_player_armor_items

**功能**：获取玩家装备的盔甲

```yaml
get_player_armor_items:
  name: "获取玩家盔甲"
  description: "查询玩家当前穿戴的盔甲套装"
  category: "player"
  method: "player.getInventory().getArmorContents()"
  return_type: "LIST"
  permission: "kilacraft.api.player.armor"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我穿了什么装备？"
    - "查看我的盔甲"
```

**使用示例**：
```
玩家: 我穿了什么装备？
AI: 你的盔甲：
    • 头盔：钻石头盔（耐久度 90%）
    • 胸甲：钻石胸甲（耐久度 85%）
    • 护腿：钻石护腿（耐久度 80%）
    • 靴子：钻石靴（耐久度 75%）
```

---

### 6. get_player_inventory_size

**功能**：获取背包物品数量

```yaml
get_player_inventory_size:
  name: "获取背包物品数量"
  description: "查询玩家背包中的物品总数"
  category: "player"
  method: "player.getInventory().getSize()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.inventory_size"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 7. get_player_level

**功能**：获取玩家经验等级

```yaml
get_player_level:
  name: "获取玩家等级"
  description: "查询玩家的当前经验等级"
  category: "player"
  method: "player.getLevel()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.level"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我多少级了？"
    - "我的经验等级"
```

---

### 8. get_player_exp

**功能**：获取玩家经验进度

```yaml
get_player_exp:
  name: "获取玩家经验进度"
  description: "查询玩家当前等级的经验进度（0.0-1.0）"
  category: "player"
  method: "player.getExp()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.exp"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 9. get_player_gamemode

**功能**：获取玩家游戏模式

```yaml
get_player_gamemode:
  name: "获取玩家游戏模式"
  description: "查询玩家当前的游戏模式"
  category: "player"
  method: "player.getGameMode().name()"
  return_type: "STRING"
  permission: "kilacraft.api.player.gamemode"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我是什么模式？"
    - "我的游戏模式"
```

**使用示例**：
```
玩家: 我是什么游戏模式？
AI: 你当前的游戏模式：生存模式
```

---

### 10. get_player_location

**功能**：获取玩家位置坐标

```yaml
get_player_location:
  name: "获取玩家位置"
  description: "查询玩家的当前位置坐标"
  category: "player"
  method: "player.getLocation()"
  return_type: "STRING"
  permission: "kilacraft.api.player.location"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我在哪？"
    - "我的坐标是多少？"
```

**使用示例**：
```
玩家: 我在哪？
AI: 你的位置：X: 128, Y: 64, Z: -256（主世界）
```

---

### 11. get_player_world

**功能**：获取玩家所在世界

```yaml
get_player_world:
  name: "获取玩家所在世界"
  description: "查询玩家当前所在的世界名称"
  category: "player"
  method: "player.getWorld().getName()"
  return_type: "STRING"
  permission: "kilacraft.api.player.world"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 12. get_player_fly_status

**功能**：获取玩家飞行状态

```yaml
get_player_fly_status:
  name: "获取玩家飞行状态"
  description: "查询玩家是否处于飞行模式"
  category: "player"
  method: "player.getAllowFlight()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.fly"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 13. get_player_op_status

**功能**：获取玩家 OP 状态

```yaml
get_player_op_status:
  name: "获取玩家 OP 状态"
  description: "查询玩家是否为服务器管理员"
  category: "player"
  method: "player.isOp()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.op"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 14. get_player_sleeping_status

**功能**：获取玩家睡眠状态

```yaml
get_player_sleeping_status:
  name: "获取玩家睡眠状态"
  description: "查询玩家是否正在床上睡觉"
  category: "player"
  method: "player.isSleeping()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sleeping"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 15. get_player_sneaking_status

**功能**：获取玩家潜行状态

```yaml
get_player_sneaking_status:
  name: "获取玩家潜行状态"
  description: "查询玩家是否正在潜行（Shift）"
  category: "player"
  method: "player.isSneaking()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sneaking"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

### 16. get_player_sprinting_status

**功能**：获取玩家奔跑状态

```yaml
get_player_sprinting_status:
  name: "获取玩家奔跑状态"
  description: "查询玩家是否正在奔跑"
  category: "player"
  method: "player.isSprinting()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sprinting"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
```

---

## 🌍 世界相关 API

### 17. get_world_time

**功能**：获取世界时间

```yaml
get_world_time:
  name: "获取世界时间"
  description: "查询指定世界的当前时间"
  category: "world"
  method: "world.getTime()"
  return_type: "STRING"
  permission: "kilacraft.api.world.time"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
  examples:
    - "现在几点？"
    - "世界时间是多少？"
```

**使用示例**：
```
玩家: 现在几点？
AI: 世界时间：06:00（早晨）
```

---

### 18. get_world_difficulty

**功能**：获取世界难度

```yaml
get_world_difficulty:
  name: "获取世界难度"
  description: "查询指定世界的难度等级"
  category: "world"
  method: "world.getDifficulty().name()"
  return_type: "STRING"
  permission: "kilacraft.api.world.difficulty"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
```

---

### 19. get_world_weather

**功能**：获取世界天气

```yaml
get_world_weather:
  name: "获取世界天气"
  description: "查询指定世界的当前天气状况"
  category: "world"
  method: "world.hasStorm()"
  return_type: "STRING"
  permission: "kilacraft.api.world.weather"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
  examples:
    - "外面下雨了吗？"
    - "天气怎么样？"
```

**使用示例**：
```
玩家: 外面下雨了吗？
AI: 当前天气：晴朗 ☀️
```

---

### 20. get_world_seed

**功能**：获取世界种子

```yaml
get_world_seed:
  name: "获取世界种子"
  description: "查询指定世界的生成种子"
  category: "world"
  method: "world.getSeed()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.seed"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
```

---

### 21. get_world_players_count

**功能**：获取世界玩家数量

```yaml
get_world_players_count:
  name: "获取世界玩家数量"
  description: "查询指定世界中的在线玩家数量"
  category: "world"
  method: "world.getPlayers().size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.players_count"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
  examples:
    - "这个世界有多少人？"
    - "主世界有多少玩家？"
```

---

### 22. get_world_max_height

**功能**：获取世界最大高度

```yaml
get_world_max_height:
  name: "获取世界最大高度"
  description: "查询指定世界的最大建筑高度"
  category: "world"
  method: "world.getMaxHeight()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.max_height"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "世界名称（默认为玩家所在世界）"
```

---

## 🖥️ 服务器相关 API

### 23. get_server_online_players

**功能**：获取在线玩家列表

```yaml
get_server_online_players:
  name: "获取在线玩家列表"
  description: "查询当前服务器的所有在线玩家"
  category: "server"
  method: "Bukkit.getOnlinePlayers()"
  return_type: "LIST"
  permission: "kilacraft.api.server.online_players"
  examples:
    - "有哪些人在线？"
    - "列出在线玩家"
```

**使用示例**：
```
玩家: 有哪些人在线？
AI: 当前在线玩家（5 人）：
    • Steve
    • Alex
    • Notch
    • Jeb_
    • Dinnerbone
```

---

### 24. get_server_online_players_count

**功能**：获取在线玩家数量

```yaml
get_server_online_players_count:
  name: "获取在线玩家数量"
  description: "查询当前服务器的在线玩家总数"
  category: "server"
  method: "Bukkit.getOnlinePlayers().size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.online_count"
  examples:
    - "有多少人在线？"
    - "服务器人数"
```

---

### 25. get_server_max_players

**功能**：获取服务器最大玩家数

```yaml
get_server_max_players:
  name: "获取服务器最大玩家数"
  description: "查询服务器允许的最大在线玩家数量"
  category: "server"
  method: "Bukkit.getMaxPlayers()"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.max_players"
  examples:
    - "服务器最多容纳多少人？"
    - "最大玩家数"
```

---

### 26. get_server_version

**功能**：获取服务器版本

```yaml
get_server_version:
  name: "获取服务器版本"
  description: "查询当前服务器的版本信息"
  category: "server"
  method: "Bukkit.getVersion()"
  return_type: "STRING"
  permission: "kilacraft.api.server.version"
  examples:
    - "服务器版本是多少？"
    - "这是什么版本的服务器？"
```

---

### 27. get_server_tps

**功能**：获取服务器 TPS

```yaml
get_server_tps:
  name: "获取服务器 TPS"
  description: "查询服务器当前的 TPS（每秒刻数）"
  category: "server"
  method: "Custom (requires TPS tracking)"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.tps"
  examples:
    - "服务器卡吗？"
    - "TPS 是多少？"
```

**使用示例**：
```
玩家: 服务器卡不卡？
AI: 当前 TPS：19.8/20.0（运行流畅）
```

---

### 28. get_server_uptime

**功能**：获取服务器运行时间

```yaml
get_server_uptime:
  name: "获取服务器运行时间"
  description: "查询服务器已运行的时长"
  category: "server"
  method: "Custom (calculated from start time)"
  return_type: "STRING"
  permission: "kilacraft.api.server.uptime"
  examples:
    - "服务器开了多久？"
    - "运行时间"
```

---

### 29. get_server_motd

**功能**：获取服务器 MOTD

```yaml
get_server_motd:
  name: "获取服务器 MOTD"
  description: "查询服务器的消息公告（MOTD）"
  category: "server"
  method: "Bukkit.getMotd()"
  return_type: "STRING"
  permission: "kilacraft.api.server.motd"
  examples:
    - "服务器公告是什么？"
    - "MOTD"
```

---

### 30. get_server_whitelist_status

**功能**：获取白名单状态

```yaml
get_server_whitelist_status:
  name: "获取白名单状态"
  description: "查询服务器是否启用了白名单"
  category: "server"
  method: "Bukkit.hasWhitelist()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.server.whitelist"
```

---

## 🐾 实体相关 API

### 31. get_nearby_entities_count

**功能**：获取附近实体数量

```yaml
get_nearby_entities_count:
  name: "获取附近实体数量"
  description: "查询玩家周围指定半径内的实体数量"
  category: "entity"
  method: "player.getNearbyEntities(radius, radius, radius).size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_count"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "搜索半径（默认 10 格）"
  examples:
    - "附近有多少生物？"
    - "周围 20 格内有多少实体？"
```

---

### 32. get_nearby_players_count

**功能**：获取附近玩家数量

```yaml
get_nearby_players_count:
  name: "获取附近玩家数量"
  description: "查询玩家周围指定半径内的其他玩家数量"
  category: "entity"
  method: "Filter nearby entities by Player type"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_players"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "搜索半径（默认 10 格）"
  examples:
    - "附近有其他玩家吗？"
    - "周围 50 格内有多少人？"
```

---

### 33. get_nearby_monsters_count

**功能**：获取附近怪物数量

```yaml
get_nearby_monsters_count:
  name: "获取附近怪物数量"
  description: "查询玩家周围指定半径内的敌对生物数量"
  category: "entity"
  method: "Filter nearby entities by Monster type"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_monsters"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "搜索半径（默认 10 格）"
  examples:
    - "附近有怪物吗？"
    - "周围有多少僵尸？"
```

---

## 🎯 高级用法

### 自定义 API

你可以添加自己的 Bukkit API 调用。例如，添加一个获取玩家击杀数的 API：

```yaml
get_player_kills:
  name: "获取玩家击杀数"
  description: "查询玩家的总击杀数"
  category: "player"
  method: "player.getStatistic(Statistic.PLAYER_KILLS)"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.kills"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "玩家名称（默认为请求者）"
  examples:
    - "我杀了多少人？"
    - "我的击杀数"
```

### 组合查询

AI 可以自动组合多个 API 调用来回答复杂问题：

```
玩家: 我现在状态怎么样？
→ AI 识别为多步骤任务：
   1. get_player_health（生命值）
   2. get_player_hunger（饥饿值）
   3. get_player_gamemode（游戏模式）
   4. get_player_location（位置）
→ 综合分析后回复：
   你当前状态：
   • 生命值：18.5/20.0
   • 饥饿值：16/20
   • 游戏模式：生存模式
   • 位置：X: 128, Y: 64, Z: -256
```

---

## 🔒 权限管理

### 默认权限

所有 Bukkit API 默认需要 `kilacraft.api.*` 权限。你可以在 `plugin.yml` 中查看完整权限列表：

```yaml
permissions:
  kilacraft.api.player.*:
    description: "允许访问所有玩家相关 API"
  kilacraft.api.world.*:
    description: "允许访问所有世界相关 API"
  kilacraft.api.server.*:
    description: "允许访问所有服务器相关 API"
  kilacraft.api.entity.*:
    description: "允许访问所有实体相关 API"
```

### 自定义权限

在 `apis.yml` 中为每个 API 设置独立权限：

```yaml
get_player_health:
  permission: "myplugin.api.health"  # 使用自定义权限
```

### 禁用权限检查

如果希望所有玩家都能访问某个 API，可以省略 `permission` 字段或设置为空：

```yaml
get_server_version:
  permission: ""  # 无需权限
```

---

## ⚙️ 性能优化建议

### 1. 缓存频繁查询的数据

对于不经常变化的数据（如服务器版本），可以在插件层面进行缓存：

```java
// 在插件启动时缓存
private static String serverVersion;

@Override
public void onEnable() {
    serverVersion = Bukkit.getVersion();
}
```

### 2. 限制查询频率

对于资源密集型查询（如附近实体），建议在配置中设置冷却时间：

```yaml
agent:
  cooldown_seconds: 3  # 3 秒冷却时间
```

### 3. 异步执行

所有 API 调用都在异步线程中执行，不会阻塞主线程。确保你的自定义 API 也是线程安全的。

---

## 🐛 故障排除

### API 返回 null

**问题**：某些 API 调用返回 `null` 值

**原因**：玩家离线、世界不存在或方法调用失败

**解决**：检查 API 配置中的 `method` 路径是否正确，确保目标对象存在

---

### 权限不足

**问题**：玩家收到"权限不足"的错误提示

**原因**：玩家没有对应的权限节点

**解决**：使用权限插件（如 LuckPerms）授予玩家相应权限：
```
/lp user <player> permission set kilacraft.api.player.health true
```

---

### API 未注册

**问题**：AI 无法识别某个 API

**原因**：`apis.yml` 文件格式错误或未重新加载

**解决**：
1. 检查 YAML 格式是否正确
2. 执行 `/kilacraft reload` 重新加载配置
3. 查看控制台是否有错误日志

---

## 📚 相关文档

- [服主指南](./服主指南) - 完整的配置和使用说明
- [Skill SPI 接入文档](./Skill-SPI-接入文档) - 如何扩展自定义技能
- [更新日志](./Kilacraft-AI-%20更新日志.md) - 版本历史和变更

---

> **最后更新**: 2026-04-05  
> **插件版本**: 1.4.0+  
> **API 总数**: 44+
