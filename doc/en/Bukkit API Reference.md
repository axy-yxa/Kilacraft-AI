# Kilacraft-AI - Bukkit API Reference Manual

> **Version**: v1.4.0  
> **Description**: This document provides detailed explanations, configuration examples, and usage scenarios for all built-in Bukkit APIs

---

## 📖 Overview

Kilacraft-AI includes **44+ built-in Bukkit APIs**, allowing AI to access various data from the Minecraft server. These APIs are defined through YAML configuration and can be used without writing code.

### Core Features

- ✅ **Data-driven configuration**: Define APIs in `apis.yml`, supports hot reload
- ✅ **Permission control**: Each API can have independent access permissions
- ✅ **Parameterized queries**: Supports dynamic parameters (player name, world name, etc.)
- ✅ **Type safety**: Automatic type conversion and validation
- ✅ **Error handling**: Friendly error messages and fallback mechanisms

### Configuration File Location

```
plugins/Kilacraft-AI/skills/bukkit/apis.yml
```

---

## 🔧 API Configuration Structure

### Basic Structure

```yaml
api_id:
  name: "API Display Name"
  description: "API Function Description"
  category: "Category (player/world/server/entity)"
  method: "Bukkit API method call path"
  return_type: "Return type (STRING/NUMBER/BOOLEAN/LIST)"
  permission: "Required permission (optional)"
  parameters:  # Parameter definitions (optional)
    - name: "param_name"
      type: "PLAYER/WORLD/STRING/NUMBER"
      required: true/false
      description: "Parameter description"
  examples:  # Usage examples (optional)
    - "Example input"
    - "Another example"
```

### Return Type Descriptions

| Type | Description | Example |
|------|-------------|---------|
| `STRING` | String | `"Steve"` |
| `NUMBER` | Number | `20.5` |
| `BOOLEAN` | Boolean | `true` / `false` |
| `LIST` | List | `["Steve", "Alex"]` |

### Parameter Type Descriptions

| Type | Description | Auto-resolution |
|------|-------------|-----------------|
| `PLAYER` | Player object | Automatically obtained from context or use player name |
| `WORLD` | World object | Automatically obtained from context or use world name |
| `STRING` | String | Used directly |
| `NUMBER` | Number | Automatically converted to integer or float |

---

## 👤 Player-related APIs

### 1. get_player_health

**Function**: Get player health

```yaml
get_player_health:
  name: "Get Player Health"
  description: "Query the current health of a specified player"
  category: "player"
  method: "player.getHealth()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.health"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "How much health do I have?"
    - "What is Steve's health?"
```

**Usage Examples**:
```
Player: How much health do I have?
AI: Your health: 18.5/20.0

Player: How much health does Steve have?
AI: Steve's health: 15.0/20.0
```

---

### 2. get_player_hunger

**Function**: Get player hunger level

```yaml
get_player_hunger:
  name: "Get Player Hunger"
  description: "Query the current hunger level of a specified player"
  category: "player"
  method: "player.getFoodLevel()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.hunger"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "Am I hungry?"
    - "What is my hunger level?"
```

**Usage Examples**:
```
Player: Am I hungry?
AI: Your hunger level: 16/20 (Good condition)
```

---

### 3. get_player_hand_item

**Function**: Get player main hand item

```yaml
get_player_hand_item:
  name: "Get Player Main Hand Item"
  description: "Query the item currently held in the player's main hand"
  category: "player"
  method: "player.getInventory().getItemInMainHand()"
  return_type: "STRING"
  permission: "kilacraft.api.player.hand_item"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "What am I holding?"
    - "Check my main hand item"
```

**Usage Examples**:
```
Player: What am I holding?
AI: You are holding in main hand: Diamond Sword x1 (Durability 85%)
```

---

### 4. get_player_offhand_item

**Function**: Get player offhand item

```yaml
get_player_offhand_item:
  name: "Get Player Offhand Item"
  description: "Query the item currently held in the player's offhand"
  category: "player"
  method: "player.getInventory().getItemInOffHand()"
  return_type: "STRING"
  permission: "kilacraft.api.player.offhand_item"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 5. get_player_armor_items

**Function**: Get player armor items

```yaml
get_player_armor_items:
  name: "Get Player Armor"
  description: "Query the armor set currently worn by the player"
  category: "player"
  method: "player.getInventory().getArmorContents()"
  return_type: "LIST"
  permission: "kilacraft.api.player.armor"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "What armor am I wearing?"
    - "Check my armor"
```

**Usage Examples**:
```
Player: What armor am I wearing?
AI: Your armor:
    • Helmet: Diamond Helmet (Durability 90%)
    • Chestplate: Diamond Chestplate (Durability 85%)
    • Leggings: Diamond Leggings (Durability 80%)
    • Boots: Diamond Boots (Durability 75%)
```

---

### 6. get_player_inventory_size

**Function**: Get inventory size

```yaml
get_player_inventory_size:
  name: "Get Inventory Size"
  description: "Query the total number of items in the player's inventory"
  category: "player"
  method: "player.getInventory().getSize()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.inventory_size"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 7. get_player_level

**Function**: Get player experience level

```yaml
get_player_level:
  name: "Get Player Level"
  description: "Query the player's current experience level"
  category: "player"
  method: "player.getLevel()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.level"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "What level am I?"
    - "My experience level"
```

---

### 8. get_player_exp

**Function**: Get player experience progress

```yaml
get_player_exp:
  name: "Get Player Experience Progress"
  description: "Query the player's experience progress within current level (0.0-1.0)"
  category: "player"
  method: "player.getExp()"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.exp"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 9. get_player_gamemode

**Function**: Get player game mode

```yaml
get_player_gamemode:
  name: "Get Player Game Mode"
  description: "Query the player's current game mode"
  category: "player"
  method: "player.getGameMode().name()"
  return_type: "STRING"
  permission: "kilacraft.api.player.gamemode"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "What mode am I in?"
    - "My game mode"
```

**Usage Examples**:
```
Player: What game mode am I in?
AI: Your current game mode: Survival
```

---

### 10. get_player_location

**Function**: Get player location coordinates

```yaml
get_player_location:
  name: "Get Player Location"
  description: "Query the player's current location coordinates"
  category: "player"
  method: "player.getLocation()"
  return_type: "STRING"
  permission: "kilacraft.api.player.location"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "Where am I?"
    - "What are my coordinates?"
```

**Usage Examples**:
```
Player: Where am I?
AI: Your location: X: 128, Y: 64, Z: -256 (Overworld)
```

---

### 11. get_player_world

**Function**: Get player's current world

```yaml
get_player_world:
  name: "Get Player World"
  description: "Query the name of the world the player is currently in"
  category: "player"
  method: "player.getWorld().getName()"
  return_type: "STRING"
  permission: "kilacraft.api.player.world"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 12. get_player_fly_status

**Function**: Get player fly status

```yaml
get_player_fly_status:
  name: "Get Player Fly Status"
  description: "Query whether the player is in fly mode"
  category: "player"
  method: "player.getAllowFlight()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.fly"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 13. get_player_op_status

**Function**: Get player OP status

```yaml
get_player_op_status:
  name: "Get Player OP Status"
  description: "Query whether the player is a server administrator"
  category: "player"
  method: "player.isOp()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.op"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 14. get_player_sleeping_status

**Function**: Get player sleeping status

```yaml
get_player_sleeping_status:
  name: "Get Player Sleeping Status"
  description: "Query whether the player is currently sleeping in a bed"
  category: "player"
  method: "player.isSleeping()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sleeping"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 15. get_player_sneaking_status

**Function**: Get player sneaking status

```yaml
get_player_sneaking_status:
  name: "Get Player Sneaking Status"
  description: "Query whether the player is currently sneaking (Shift)"
  category: "player"
  method: "player.isSneaking()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sneaking"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

### 16. get_player_sprinting_status

**Function**: Get player sprinting status

```yaml
get_player_sprinting_status:
  name: "Get Player Sprinting Status"
  description: "Query whether the player is currently sprinting"
  category: "player"
  method: "player.isSprinting()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.player.sprinting"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
```

---

## 🌍 World-related APIs

### 17. get_world_time

**Function**: Get world time

```yaml
get_world_time:
  name: "Get World Time"
  description: "Query the current time of a specified world"
  category: "world"
  method: "world.getTime()"
  return_type: "STRING"
  permission: "kilacraft.api.world.time"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
  examples:
    - "What time is it?"
    - "What is the world time?"
```

**Usage Examples**:
```
Player: What time is it?
AI: World time: 06:00 (Morning)
```

---

### 18. get_world_difficulty

**Function**: Get world difficulty

```yaml
get_world_difficulty:
  name: "Get World Difficulty"
  description: "Query the difficulty level of a specified world"
  category: "world"
  method: "world.getDifficulty().name()"
  return_type: "STRING"
  permission: "kilacraft.api.world.difficulty"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
```

---

### 19. get_world_weather

**Function**: Get world weather

```yaml
get_world_weather:
  name: "Get World Weather"
  description: "Query the current weather conditions of a specified world"
  category: "world"
  method: "world.hasStorm()"
  return_type: "STRING"
  permission: "kilacraft.api.world.weather"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
  examples:
    - "Is it raining outside?"
    - "How's the weather?"
```

**Usage Examples**:
```
Player: Is it raining outside?
AI: Current weather: Clear ☀️
```

---

### 20. get_world_seed

**Function**: Get world seed

```yaml
get_world_seed:
  name: "Get World Seed"
  description: "Query the generation seed of a specified world"
  category: "world"
  method: "world.getSeed()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.seed"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
```

---

### 21. get_world_players_count

**Function**: Get world player count

```yaml
get_world_players_count:
  name: "Get World Player Count"
  description: "Query the number of online players in a specified world"
  category: "world"
  method: "world.getPlayers().size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.players_count"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
  examples:
    - "How many people are in this world?"
    - "How many players are in the overworld?"
```

---

### 22. get_world_max_height

**Function**: Get world max height

```yaml
get_world_max_height:
  name: "Get World Max Height"
  description: "Query the maximum build height of a specified world"
  category: "world"
  method: "world.getMaxHeight()"
  return_type: "NUMBER"
  permission: "kilacraft.api.world.max_height"
  parameters:
    - name: "world"
      type: "WORLD"
      required: false
      description: "World name (defaults to player's current world)"
```

---

## 🖥️ Server-related APIs

### 23. get_server_online_players

**Function**: Get online player list

```yaml
get_server_online_players:
  name: "Get Online Player List"
  description: "Query all online players on the current server"
  category: "server"
  method: "Bukkit.getOnlinePlayers()"
  return_type: "LIST"
  permission: "kilacraft.api.server.online_players"
  examples:
    - "Who is online?"
    - "List online players"
```

**Usage Examples**:
```
Player: Who is online?
AI: Current online players (5):
    • Steve
    • Alex
    • Notch
    • Jeb_
    • Dinnerbone
```

---

### 24. get_server_online_players_count

**Function**: Get online player count

```yaml
get_server_online_players_count:
  name: "Get Online Player Count"
  description: "Query the total number of online players on the current server"
  category: "server"
  method: "Bukkit.getOnlinePlayers().size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.online_count"
  examples:
    - "How many people are online?"
    - "Server player count"
```

---

### 25. get_server_max_players

**Function**: Get server max players

```yaml
get_server_max_players:
  name: "Get Server Max Players"
  description: "Query the maximum number of online players allowed on the server"
  category: "server"
  method: "Bukkit.getMaxPlayers()"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.max_players"
  examples:
    - "How many players can the server hold?"
    - "Max player count"
```

---

### 26. get_server_version

**Function**: Get server version

```yaml
get_server_version:
  name: "Get Server Version"
  description: "Query the version information of the current server"
  category: "server"
  method: "Bukkit.getVersion()"
  return_type: "STRING"
  permission: "kilacraft.api.server.version"
  examples:
    - "What is the server version?"
    - "What version is this server?"
```

---

### 27. get_server_tps

**Function**: Get server TPS

```yaml
get_server_tps:
  name: "Get Server TPS"
  description: "Query the current TPS (ticks per second) of the server"
  category: "server"
  method: "Custom (requires TPS tracking)"
  return_type: "NUMBER"
  permission: "kilacraft.api.server.tps"
  examples:
    - "Is the server lagging?"
    - "What is the TPS?"
```

**Usage Examples**:
```
Player: Is the server lagging?
AI: Current TPS: 19.8/20.0 (Running smoothly)
```

---

### 28. get_server_uptime

**Function**: Get server uptime

```yaml
get_server_uptime:
  name: "Get Server Uptime"
  description: "Query how long the server has been running"
  category: "server"
  method: "Custom (calculated from start time)"
  return_type: "STRING"
  permission: "kilacraft.api.server.uptime"
  examples:
    - "How long has the server been up?"
    - "Uptime"
```

---

### 29. get_server_motd

**Function**: Get server MOTD

```yaml
get_server_motd:
  name: "Get Server MOTD"
  description: "Query the server's message of the day (MOTD)"
  category: "server"
  method: "Bukkit.getMotd()"
  return_type: "STRING"
  permission: "kilacraft.api.server.motd"
  examples:
    - "What is the server MOTD?"
    - "MOTD"
```

---

### 30. get_server_whitelist_status

**Function**: Get whitelist status

```yaml
get_server_whitelist_status:
  name: "Get Whitelist Status"
  description: "Query whether the server has whitelist enabled"
  category: "server"
  method: "Bukkit.hasWhitelist()"
  return_type: "BOOLEAN"
  permission: "kilacraft.api.server.whitelist"
```

---

## 🐾 Entity-related APIs

### 31. get_nearby_entities_count

**Function**: Get nearby entity count

```yaml
get_nearby_entities_count:
  name: "Get Nearby Entity Count"
  description: "Query the number of entities within a specified radius around the player"
  category: "entity"
  method: "player.getNearbyEntities(radius, radius, radius).size()"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_count"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "Search radius (default 10 blocks)"
  examples:
    - "How many mobs are nearby?"
    - "How many entities within 20 blocks?"
```

---

### 32. get_nearby_players_count

**Function**: Get nearby player count

```yaml
get_nearby_players_count:
  name: "Get Nearby Player Count"
  description: "Query the number of other players within a specified radius around the player"
  category: "entity"
  method: "Filter nearby entities by Player type"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_players"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "Search radius (default 10 blocks)"
  examples:
    - "Are there other players nearby?"
    - "How many people within 50 blocks?"
```

---

### 33. get_nearby_monsters_count

**Function**: Get nearby monster count

```yaml
get_nearby_monsters_count:
  name: "Get Nearby Monster Count"
  description: "Query the number of hostile mobs within a specified radius around the player"
  category: "entity"
  method: "Filter nearby entities by Monster type"
  return_type: "NUMBER"
  permission: "kilacraft.api.entity.nearby_monsters"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
    - name: "radius"
      type: "NUMBER"
      required: false
      description: "Search radius (default 10 blocks)"
  examples:
    - "Are there monsters nearby?"
    - "How many zombies around?"
```

---

## 🎯 Advanced Usage

### Custom APIs

You can add your own Bukkit API calls. For example, add an API to get player kill count:

```yaml
get_player_kills:
  name: "Get Player Kills"
  description: "Query the player's total kill count"
  category: "player"
  method: "player.getStatistic(Statistic.PLAYER_KILLS)"
  return_type: "NUMBER"
  permission: "kilacraft.api.player.kills"
  parameters:
    - name: "player"
      type: "PLAYER"
      required: false
      description: "Player name (defaults to requester)"
  examples:
    - "How many players have I killed?"
    - "My kill count"
```

### Combined Queries

AI can automatically combine multiple API calls to answer complex questions:

```
Player: How is my current status?
→ AI identifies as multi-step task:
   1. get_player_health (health)
   2. get_player_hunger (hunger)
   3. get_player_gamemode (game mode)
   4. get_player_location (location)
→ Comprehensive response:
   Your current status:
   • Health: 18.5/20.0
   • Hunger: 16/20
   • Game mode: Survival
   • Location: X: 128, Y: 64, Z: -256
```

---

## 🔒 Permission Management

### Default Permissions

All Bukkit APIs require `kilacraft.api.*` permission by default. You can view the complete permission list in `plugin.yml`:

```yaml
permissions:
  kilacraft.api.player.*:
    description: "Allows access to all player-related APIs"
  kilacraft.api.world.*:
    description: "Allows access to all world-related APIs"
  kilacraft.api.server.*:
    description: "Allows access to all server-related APIs"
  kilacraft.api.entity.*:
    description: "Allows access to all entity-related APIs"
```

### Custom Permissions

Set independent permissions for each API in `apis.yml`:

```yaml
get_player_health:
  permission: "myplugin.api.health"  # Use custom permission
```

### Disable Permission Check

If you want all players to access an API, omit the `permission` field or set it to empty:

```yaml
get_server_version:
  permission: ""  # No permission required
```

---

## ⚙️ Performance Optimization Tips

### 1. Cache Frequently Queried Data

For data that doesn't change often (such as server version), you can cache at the plugin level:

```java
// Cache on plugin startup
private static String serverVersion;

@Override
public void onEnable() {
    serverVersion = Bukkit.getVersion();
}
```

### 2. Limit Query Frequency

For resource-intensive queries (such as nearby entities), it's recommended to set cooldown in configuration:

```yaml
agent:
  cooldown_seconds: 3  # 3 seconds cooldown
```

### 3. Asynchronous Execution

All API calls are executed in asynchronous threads and will not block the main thread. Ensure your custom APIs are also thread-safe.

---

## 🐛 Troubleshooting

### API Returns null

**Problem**: Some API calls return `null` values

**Cause**: Player offline, world doesn't exist, or method call failed

**Solution**: Check if the `method` path in API configuration is correct, ensure target object exists

---

### Insufficient Permissions

**Problem**: Player receives "Insufficient permissions" error message

**Cause**: Player doesn't have the corresponding permission node

**Solution**: Use permission plugin (such as LuckPerms) to grant player appropriate permissions:
```
/lp user <player> permission set kilacraft.api.player.health true
```

---

### API Not Registered

**Problem**: AI cannot recognize an API

**Cause**: `apis.yml` file format error or not reloaded

**Solution**:
1. Check if YAML format is correct
2. Execute `/kilacraft reload` to reload configuration
3. Check console for error logs

---

## 📚 Related Documentation

- [Server Owner Guide](./Kilacraft-AI-服主指南.md) - Complete configuration and usage instructions
- [Skill SPI Integration Guide](./Kilacraft-AI-Skill-SPI-接入文档.md) - How to extend custom skills
- [Changelog](./Kilacraft-AI-%20更新日志.md) - Version history and changes

---

> **Last Updated**: 2026-04-05  
> **Plugin Version**: 1.4.0+  
> **Total APIs**: 44+
