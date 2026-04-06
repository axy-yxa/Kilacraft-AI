# Kilacraft-AI Skill SPI 接入文档

> 版本：1.0 | 最后更新：2026-04-04  
> 适用插件版本：Kilacraft-AI 1.4.1+

---

## 目录

1. [概述](#1-概述)
2. [架构总览](#2-架构总览)
3. [快速开始（5 分钟接入）](#3-快速开始5-分钟接入)
4. [核心接口详解](#4-核心接口详解)
5. [Skill 开发规范](#5-skill-开发规范)
6. [多步骤任务数据传递](#6-多步骤任务数据传递)
7. [错误隔离与异常处理](#7-错误隔离与异常处理)
8. [权限与可用性控制](#8-权限与可用性控制)
9. [命名规范与冲突处理](#9-命名规范与冲突处理)
10. [配置化支持（可选）](#10-配置化支持可选)
11. [完整示例：玩家状态查询插件](#11-完整示例玩家状态查询插件)
12. [开发依赖配置](#12-开发依赖配置)
13. [生命周期与加载顺序](#13-生命周期与加载顺序)
14. [常见问题 FAQ](#14-常见问题-faq)
15. [API 参考](#15-api-参考)

---

## 1. 概述

Kilacraft-AI 通过 **SPI（Service Provider Interface）** 机制，允许第三方 Minecraft 插件将自己的功能封装为 **Skill（技能）**，注册到 AI Agent 中，使 AI 助手能够调用这些功能。

### 核心特性

- **零耦合接入**：第三方插件只需引入 `kilacraft-skill-api.jar`（compileOnly），实现接口即可
- **自动发现**：基于 Bukkit `ServicesManager`，启动时自动扫描注册，无需手动配置
- **错误隔离**：第三方 Skill 的异常不会影响 Kilacraft-AI 核心流程
- **LLM 意图驱动**：AI 自动识别用户意图并调用对应 Skill，用户无需记忆命令
- **多步骤任务支持**：Skill 返回的数据可被后续步骤引用，实现跨技能编排

### 适用场景

| 场景 | 示例 |
|------|------|
| 经济系统 | 查询余额、转账、商店购买 |
| 领地系统 | 查询领地信息、创建领地 |
| 排行榜 | 查询在线排行、财富排行 |
| RPG 系统 | 查询技能等级、任务进度 |
| 世界管理 | 查询区块信息、传送管理 |

---

## 2. 架构总览

```
┌──────────────────────────────────────────────────┐
│                  第三方插件 JAR                    │
│  ┌─────────────┐    ┌──────────────────────────┐  │
│  │ MyPlugin    │    │ MyCustomSkill            │  │
│  │ implements  │───>│ implements Skill          │  │
│  │ SkillProvider│    │                          │  │
│  └──────┬──────┘    └──────────────────────────┘  │
│         │ onEnable() 注册 SkillProvider            │
└─────────┼─────────────────────────────────────────┘
          │ Bukkit ServicesManager
          ▼
┌──────────────────────────────────────────────────┐
│              Kilacraft-AI 主插件                   │
│  ┌─────────────┐    ┌──────────────────────────┐  │
│  │ SkillRegistry│───>│ SkillManager             │  │
│  │ 自动发现     │    │ 注册/执行/错误隔离       │  │
│  └─────────────┘    └───────────┬──────────────┘  │
│                                 │                  │
│                     ┌───────────▼──────────────┐  │
│                     │ LLM 意图识别              │  │
│                     │ 用户消息 → Skill 调用     │  │
│                     └──────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

### 数据流

```
用户聊天消息
    → ChatListener 拦截
    → SkillIntentRecognizer (LLM 意图识别)
    → SkillManager.executeSkillByIntent()
    → Skill.execute(context)
    → SkillResult (message + data)
    → 返回给用户 / 传递给下一步骤
```

---

## 3. 快速开始（5 分钟接入）

### 第一步：添加开发依赖

在你的插件项目的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>1.4.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-1.4.0.jar</systemPath>
</dependency>
```

> **注意**：
> 1. 此依赖为 `compileOnly`，不会打包进你的插件 JAR
> 2. **JAR 文件名包含版本号**，请根据实际下载的文件名调整 `<systemPath>` 中的文件名（如 `Kilacraft-Skill-API-1.4.0.jar`）

### 第二步：实现 Skill 接口

```java
package com.example.myplugin.skills;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HelloWorldSkill implements Skill {

    @Override
    public String getName() {
        return "hello_world";  // 全局唯一标识
    }

    @Override
    public String getDescription() {
        return "向玩家打招呼。返回一个问候消息。";
    }

    @Override
    public Map<String, String> getActions() {
        return Map.of(
            "greet", "向指定玩家发送问候语"
        );
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        String name = player != null ? player.getName() : "陌生人";
        return CompletableFuture.completedFuture(
            SkillResult.success("你好，" + name + "！欢迎使用 AI 助手！")
        );
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return true;  // 始终可用
    }
}
```

### 第三步：注册 SkillProvider

在你的插件主类中：

```java
package com.example.myplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.spi.SkillProvider;
import com.example.myplugin.skills.HelloWorldSkill;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MyPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // 在 onEnable 中注册 SkillProvider
        // Kilacraft-AI 会在服务器启动后自动扫描发现
        getServer().getServicesManager().register(
            SkillProvider.class,
            this,
            this,
            org.bukkit.plugin.ServicePriority.Normal
        );
        getLogger().info("已注册 SkillProvider，等待 Kilacraft-AI 发现...");
    }

    @Override
    public void onDisable() {
        // Bukkit 会自动注销 ServicesManager 中的注册
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new HelloWorldSkill());
    }
}
```

### 第四步：配置 plugin.yml 依赖

```yaml
name: MyPlugin
version: 1.0
main: com.example.myplugin.MyPlugin
api-version: '1.21'
# 声明软依赖，确保 Kilacraft-AI 已加载
softdepend:
  - Kilacraft-AI
```

### 完成！

部署两个 JAR 到服务器 `plugins/` 目录，启动服务器即可。Kilacraft-AI 会自动在启动后扫描并注册你的 Skill，控制台会输出：

```
[Kilacraft-AI] 发现并注册第三方技能：hello_world (来自 MyPlugin)
```

---

## 4. 核心接口详解

### 4.1 Skill 接口

```java
public interface Skill {
    /** 技能名称（全局唯一标识符） */
    String getName();

    /** 技能描述（供 LLM 意图识别使用，非常重要） */
    String getDescription();

    /** 动作列表：key=动作名，value=动作描述（供 LLM 识别子意图） */
    default Map<String, String> getActions() {
        return Collections.emptyMap();
    }

    /** 额外提示信息（使用示例、注意事项等） */
    default List<String> getHints() {
        return Collections.emptyList();
    }

    /** 执行技能（核心方法） */
    CompletableFuture<SkillResult> execute(SkillContext context);

    /** 检查技能在当前上下文中是否可用 */
    default boolean isAvailable(SkillContext context) {
        return true;
    }
}
```

### 4.2 SkillProvider 接口

```java
public interface SkillProvider {
    /** 返回此 Provider 提供的所有 Skill 实例 */
    List<Skill> getSkills();
}
```

**要点：**
- 你的插件主类实现此接口
- `getSkills()` 不应返回 `null`（无 Skill 时返回空列表）
- 每个 Skill 使用独立实例（避免共享可变状态）

### 4.3 SkillContext 上下文

```java
public class SkillContext {
    /** 当前玩家（可能为 null，如控制台调用） */
    Player getPlayer();

    /** LLM 识别出的动作名称 */
    String getAction();

    /** LLM 提取的实体参数（key-value 形式） */
    Map<String, String> getEntities();

    /** 获取指定实体参数 */
    String getEntity(String key);
}
```

**字段说明：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `player` | 触发 AI 对话的玩家 | 对话的玩家实例 |
| `action` | LLM 识别的动作 | `"query_price"`, `"greet"` |
| `entities` | LLM 提取的参数 | `{"item": "钻石", "quantity": "10"}` |

### 4.4 SkillResult 结果

```java
public class SkillResult {
    /** 是否成功 */
    boolean isSuccess();

    /** 消息内容（展示给用户或传递给 LLM） */
    String getMessage();

    /** 数据对象（约定为 Map<String, Object>，供多步骤任务传递） */
    Object getData();

    /** 便捷方法：获取 data Map */
    Map<String, Object> getDataMap();

    // 静态工厂方法
    static SkillResult success(String message);
    static SkillResult success(String message, Object data);
    static SkillResult failure(String message);
    static SkillResult failure(String message, Throwable error);
}
```

---

## 5. Skill 开发规范

### 5.1 命名规范

| 项目 | 规范 | 示例 |
|------|------|------|
| Skill 名称 (name) | 小写字母 + 下划线，`插件前缀_功能` | `economy_balance`, `mcmmo_stats` |
| Action 名称 | 小写字母 + 下划线，`动词_名词` | `query_balance`, `transfer_money` |
| Entity 键名 | 小写字母 + 下划线 | `item_name`, `player_name`, `quantity` |
| Data 字段名 | 小写字母 + 下划线 | `health`, `max_health`, `food_level` |

**重要**：Skill 名称全局唯一，如果与内置 Skill 重名，第三方 Skill 会被跳过（内置 Skill 优先）。建议使用 `插件名_` 前缀避免冲突。

### 5.2 Description 编写要点

`getDescription()` 的返回值是 **LLM 意图识别的核心依据**，直接决定 AI 能否正确匹配你的 Skill。编写原则：

1. **明确描述功能**：用一句话说清楚这个 Skill 做什么
2. **包含关键词**：用户可能使用的同义词
3. **描述返回数据字段**（如有多步骤任务需求）

```java
// 好的描述
"查询玩家的 MCMMO 技能等级。支持查询具体技能的当前等级和经验值。
返回的 data 中包含 skill_name、level、xp 字段，供多步骤任务参数传递使用。"

// 差的描述
"查询信息"  // 太模糊，LLM 无法区分
```

### 5.3 Action 设计原则

每个 Skill 可以包含多个 Action，由 LLM 根据用户输入自动选择：

```java
@Override
public Map<String, String> getActions() {
    return Map.of(
        "query_balance", "查询玩家的余额。返回数据包含 balance 字段。",
        "transfer", "向指定玩家转账。需要参数：target_player（目标玩家名）、amount（金额）。"
    );
}
```

**Action 描述也至关重要**——LLM 根据描述来决定调用哪个 action，以及提取哪些参数。

### 5.4 Entity 参数提取

LLM 会从用户输入中提取参数，以 `Map<String, String>` 形式传入 `context.getEntities()`。你需要在 action 的描述中声明需要哪些参数：

```java
// Action 描述示例
"购买市场上的物品。需要参数：item（物品名称，支持中英文）、quantity（购买数量，默认为1）。"
```

用户说："帮我买10个钻石"，LLM 会提取：
```json
{
    "item": "钻石",
    "quantity": "10"
}
```

在 execute 中获取：
```java
String item = context.getEntity("item");       // "钻石"
String quantity = context.getEntity("quantity"); // "10"
```

> **注意**：entity 的 value 始终是 `String` 类型。如需数字，需自行转换并处理异常。

---

## 6. 多步骤任务数据传递

### 6.1 概述

Kilacraft-AI 支持多步骤任务编排——当用户的一个请求需要多个 Skill 按顺序执行时，前一个 Skill 的返回数据可以被后一个 Skill 引用。

### 6.2 三方约定

这是一个**三方约定**：开发者写代码和描述 → LLM 读描述生成占位符 → 框架解析占位符。

**Skill 开发者**需要做的事情：
1. 在 `SkillResult.success(message, data)` 中返回 `Map<String, Object>` 类型的 data
2. 在 Skill 的 `getDescription()` 或 action 描述中说明返回了哪些 data 字段

### 6.3 示例

**步骤 1：查询物品价格 Skill**

```java
private CompletableFuture<SkillResult> queryPrice(SkillContext context) {
    String itemName = context.getEntity("item");
    double price = getPrice(itemName);

    Map<String, Object> data = new HashMap<>();
    data.put("item_name", itemName);
    data.put("price", price);
    data.put("stock", getStock(itemName));

    return CompletableFuture.completedFuture(
        SkillResult.success(itemName + "的价格是 $" + price, data)
    );
}
```

描述中需要写明：`"返回数据包含 item_name、price、stock 字段"`

**步骤 2：LLM 自动编排**

当用户说 "帮我查一下钻石的价格，如果不超过 100 就帮我买 10 个"，LLM 会生成：

```json
{
    "steps": [
        {
            "id": "step_1",
            "skill": "market_query",
            "action": "query_price",
            "entities": {"item": "钻石"}
        },
        {
            "id": "step_2",
            "skill": "market_buy",
            "action": "buy_item",
            "entities": {
                "item": "{step_1.item_name}",
                "price": "{step_1.price}",
                "quantity": "10"
            }
        }
    ]
}
```

**步骤 3：框架自动解析占位符**

TaskExecutor 会自动将 `{step_1.item_name}` 替换为步骤 1 返回的 `data.get("item_name")` 的值。

### 6.4 占位符格式

```
{step_<步骤ID>.<字段名>}
```

- 步骤 ID 由 LLM 生成（如 `step_1`, `step_2`）
- 字段名对应 `SkillResult.data` Map 中的 key
- 只支持单层引用（不支持嵌套如 `{step_1.data.xxx}`）

---

## 7. 错误隔离与异常处理

### 7.1 自动隔离

Kilacraft-AI 的 `SkillManager` 对第三方 Skill 执行了完整的错误隔离：

```java
// SkillManager 内部逻辑（简化）
try {
    return skill.execute(context).exceptionally(ex -> {
        // 异步异常捕获
        plugin.getLogger().log(Level.SEVERE, "技能执行异常：" + skillName, ex);
        return SkillResult.failure("技能执行出错，请联系管理员");
    });
} catch (Exception e) {
    // 同步异常捕获
    plugin.getLogger().log(Level.SEVERE, "技能执行失败：" + skillName, e);
    return SkillResult.failure("技能执行出错，请联系管理员");
}
```

### 7.2 开发建议

虽然 Kilacraft-AI 会兜底，但你仍然应该做好自身的异常处理：

```java
@Override
public CompletableFuture<SkillResult> execute(SkillContext context) {
    try {
        // 参数校验
        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure("请指定物品名称")
            );
        }

        // 业务逻辑
        return doSomething(itemName);

    } catch (Exception e) {
        // 返回有意义的错误消息（会被展示给用户）
        return CompletableFuture.completedFuture(
            SkillResult.failure("查询失败：" + e.getMessage())
        );
    }
}
```

### 7.3 注意事项

- **不要抛出未捕获的 RuntimeException**：虽然会被隔离，但用户体验不好
- **failure 消息应该对用户友好**：用户会看到这条消息
- **异步操作中也要处理异常**：CompletableFuture 链中的异常也要 `.exceptionally()` 处理
- **isAvailable() 中的异常也会被隔离**：不要在此方法中执行复杂逻辑

---

## 8. 权限与可用性控制

### 8.1 isAvailable() 检查

```java
@Override
public boolean isAvailable(SkillContext context) {
    // 示例：检查玩家是否有权限
    Player player = context.getPlayer();
    if (player == null) {
        return false;  // 控制台不可用
    }
    return player.hasPermission("myplugin.skill.use");
}
```

**调用时机**：每次 Skill 执行前都会先调用此方法。

**返回 false 的效果**：用户会收到 "抱歉，该功能暂时不可用" 的提示。

### 8.2 权限体系

Kilacraft-AI 的权限体系是独立的，不影响你自身的权限检查。你有两种控制方式：

1. **在 `isAvailable()` 中检查**：适合简单的可用/不可用判断
2. **在 `execute()` 中检查**：适合需要返回具体原因的场景

```java
@Override
public CompletableFuture<SkillResult> execute(SkillContext context) {
    Player player = context.getPlayer();
    if (!player.hasPermission("economy.transfer")) {
        return CompletableFuture.completedFuture(
            SkillResult.failure("你没有转账权限")
        );
    }
    // ... 正常逻辑
}
```

---

## 9. 命名规范与冲突处理

### 9.1 命名空间建议

为避免与其他插件的 Skill 冲突，建议使用以下格式：

```
<你的插件名小写>_<功能描述>
```

示例：
- `mcmmo_query_level` — McMMO 的等级查询
- `essentials_balance` — Essentials 的余额查询
- `towny_query_info` — Towny 的领地查询

### 9.2 冲突处理规则

当第三方 Skill 名称与已注册 Skill（内置或其他第三方）冲突时：

- **内置 Skill 优先**：不会被第三方覆盖
- **先注册先得**：多个第三方 Skill 同名时，先被发现的保留
- **控制台告警**：冲突时会在日志中输出 Warning

```
[Kilacraft-AI] 跳过第三方技能 'market_query'（来自 SomePlugin）：名称与已注册技能冲突
```

---

## 10. 配置化支持（可选）

Kilacraft-AI 内置 Skill 使用 `SkillConfig` 进行配置化管理（描述、action、提示信息等均可配置）。但第三方 Skill **不需要**遵循这一机制，你完全可以用自己的配置方式。

如果你希望第三方 Skill 的描述也能热重载，建议：

```java
public class MySkill implements Skill {
    private final MyPlugin plugin;

    public MySkill(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getDescription() {
        // 从你自己的 config.yml 读取，支持热重载
        return plugin.getConfig().getString("skill.description", "默认描述");
    }

    @Override
    public Map<String, String> getActions() {
        // 从配置读取 actions
        Map<String, String> actions = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skill.actions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                actions.put(key, section.getString(key));
            }
        }
        return actions;
    }
}
```

然后在 `getSkills()` 中传入插件实例：

```java
@Override
public List<Skill> getSkills() {
    return List.of(new MySkill(this));
}
```

---

## 11. 完整示例：玩家状态查询插件

以下是一个完整的第三方插件示例，查询玩家的生命值、饥饿值和经验等级。

### Skill 实现

```java
package com.example.statsplugin.skills;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlayerStatsSkill implements Skill {

    private static final String NAME = "player_stats_query";
    private static final String DESCRIPTION =
        "查询玩家状态信息，包括生命值、饥饿值、经验等级。" +
        "返回的 data 中包含 health、max_health、food_level、level、total_exp 字段，" +
        "供多步骤任务参数传递使用。";

    private static final Map<String, String> ACTIONS = Map.of(
        "query_health", "查询玩家当前生命值。返回数据包含 health 和 max_health 字段。",
        "query_food", "查询玩家当前饥饿值。返回数据包含 food_level 字段。",
        "query_experience", "查询玩家当前经验等级。返回数据包含 level 和 total_exp 字段。"
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, String> getActions() {
        return ACTIONS;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("请指定玩家"));
        }

        String action = context.getAction();
        try {
            return switch (action) {
                case "query_health" -> queryHealth(player);
                case "query_food" -> queryFood(player);
                case "query_experience" -> queryExperience(player);
                default -> CompletableFuture.completedFuture(
                    SkillResult.failure("未知动作：" + action));
            };
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                SkillResult.failure("查询失败：" + e.getMessage()));
        }
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return context.getPlayer() != null;
    }

    private CompletableFuture<SkillResult> queryHealth(Player player) {
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        Map<String, Object> data = new HashMap<>();
        data.put("health", health);
        data.put("max_health", maxHealth);
        return CompletableFuture.completedFuture(
            SkillResult.success(
                String.format("生命值：%.1f/%.1f", health, maxHealth), data));
    }

    private CompletableFuture<SkillResult> queryFood(Player player) {
        int foodLevel = player.getFoodLevel();
        Map<String, Object> data = new HashMap<>();
        data.put("food_level", foodLevel);
        return CompletableFuture.completedFuture(
            SkillResult.success("饱食度：" + foodLevel + "/20", data));
    }

    private CompletableFuture<SkillResult> queryExperience(Player player) {
        int level = player.getLevel();
        int totalExp = player.getTotalExperience();
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("total_exp", totalExp);
        return CompletableFuture.completedFuture(
            SkillResult.success(
                String.format("等级：%d，总经验：%d", level, totalExp), data));
    }
}
```

### 插件主类

```java
package com.example.statsplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.spi.SkillProvider;
import com.example.statsplugin.skills.PlayerStatsSkill;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class StatsPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // 注册 SkillProvider 到 Bukkit ServicesManager（4个参数）
        getServer().getServicesManager().register(
            SkillProvider.class,
            this,
            this,
            org.bukkit.plugin.ServicePriority.Normal
        );
        getLogger().info("StatsPlugin 已注册 SkillProvider");
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new PlayerStatsSkill());
    }
}
```

### plugin.yml

```yaml
name: StatsPlugin
version: 1.0
main: com.example.statsplugin.StatsPlugin
api-version: '1.21'
softdepend:
  - Kilacraft-AI
```

### 用户交互示例

安装后，用户可以直接在聊天中对 AI 说：

```
用户：我的血量是多少？
AI：你的生命值是 18.5/20.0

用户：我现在的经验等级和饱食度呢？
AI：你的等级是 15，总经验：3200。饱食度：18/20。
```

---

## 12. 开发依赖配置

### Maven

```xml
<dependencies>
    <!-- Kilacraft-AI Skill API（compileOnly） -->
    <dependency>
        <groupId>com.zm.kilacraftAI</groupId>
        <artifactId>kilacraft-skill-api</artifactId>
        <version>1.3.6</version>
        <scope>system</scope>
        <systemPath>${project.basedir}/libs/kilacraft-skill-api.jar</systemPath>
    </dependency>

    <!-- Spigot API -->
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>1.21-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    // Kilacraft-AI Skill API（compileOnly）
    implementation files('libs/kilacraft-skill-api.jar')

    // Spigot API
    compileOnly 'org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT'
}
```

### 关于 kilacraft-skill-api.jar

此 JAR 包含以下类的编译产物，供第三方开发者编译时引用：

```
com.zm.kilacraftAI.skills.framework.Skill
com.zm.kilacraftAI.skills.framework.SkillContext
com.zm.kilacraftAI.skills.framework.SkillResult
com.zm.kilacraftAI.skills.framework.SkillIntent
com.zm.kilacraftAI.skills.framework.spi.SkillProvider
com.zm.kilacraftAI.skills.framework.spi.SkillRegistry
```

> **此 JAR 不需要也不能打包进你的插件**。运行时由 Kilacraft-AI 主插件提供。

---

## 13. 生命周期与加载顺序

```
服务器启动
    │
    ├── 第三方插件 onEnable()
    │   └── Bukkit.getServicesManager().register(SkillProvider.class, this, ...)
    │
    ├── Kilacraft-AI onEnable()
    │   ├── 初始化 SkillManager
    │   ├── 注册内置 Skill (MarketQuerySkill, GenericBukkitAPISkill)
    │   └── 调度延迟任务 (20 tick 后)
    │
    ├── 服务器启动完成
    │
    └── 延迟 20 tick 后 ── SkillRegistry.discoverAndRegister()
        ├── 扫描 ServicesManager 中的 SkillProvider
        ├── 遍历每个 Provider 的 getSkills()
        ├── 检查名称冲突
        └── 注册到 SkillManager

用户聊天
    └── ChatListener → SkillIntentRecognizer → SkillManager.executeSkillByIntent()
        ├── isAvailable() 检查（带错误隔离）
        └── Skill.execute(context)（带错误隔离）

服务器关闭
    └── Bukkit 自动注销 ServicesManager 中的所有注册
```

**为什么延迟 20 tick？**  
确保所有第三方插件已完成 `onEnable()` 中的 SkillProvider 注册，避免因加载顺序差异导致漏扫。

---

## 14. 常见问题 FAQ

### Q: 我的插件先加载还是 Kilacraft-AI 先加载？

A: 不确定，取决于 Bukkit 的插件加载顺序（通常是 alphabetically）。但无论谁先加载，自动发现机制都能正常工作：
- 如果你的插件先加载：你注册 SkillProvider → Kilacraft-AI 延迟扫描时发现
- 如果 Kilacraft-AI 先加载：Kilacraft-AI 注册内置 Skill → 延迟扫描时发现你注册的 SkillProvider

### Q: 支持热重载吗？

A: 不支持。自动发现机制仅在服务器启动时执行一次。安装或更新第三方 Skill 插件后，请重启服务器。

### Q: Skill 的 execute 方法可以执行耗时操作吗？

A: 可以。`execute()` 返回 `CompletableFuture`，支持异步操作。但注意：
- 不要在主线程执行阻塞操作（数据库查询、网络请求等），应使用 `CompletableFuture.supplyAsync()`
- Bukkit API 调用（如 `player.getHealth()`）必须在主线程，异步中调用需要 `Bukkit.getScheduler().runTask()`

### Q: 一个插件可以注册多个 Skill 吗？

A: 可以。在 `getSkills()` 中返回多个 Skill 实例即可：

```java
@Override
public List<Skill> getSkills() {
    return List.of(
        new QuerySkill(),
        new BuySkill(),
        new SellSkill()
    );
}
```

### Q: 控制台可以使用 Skill 吗？

A: 取决于你的 `isAvailable()` 和 `execute()` 逻辑。`context.getPlayer()` 在控制台调用时为 `null`，你需要在代码中处理这种情况。

### Q: Skill 名称可以使用中文吗？

A: 不建议。请使用小写英文 + 下划线格式，确保兼容性和可读性。

### Q: 如何调试我的 Skill？

1. 在 Kilacraft-AI 的 `config.yml` 中开启 Debug 模式
2. 查看控制台日志中 `[DEBUG]` 前缀的输出
3. 检查 Skill 的 `getName()`、`getDescription()`、`getActions()` 是否返回了正确的值
4. 确认 `getSkills()` 中的 Skill 实例已正确初始化

### Q: 多步骤任务中，我的 Skill 如何被 LLM 正确编排？

关键在于你的 **description** 和 **action 描述** 必须清楚地说明返回的 data 字段。LLM 会根据这些描述决定：
1. 是否需要多步骤
2. 后续步骤中如何引用前面步骤的 data

例如，在 description 中写明：`"返回数据包含 item_name、price 字段"`，LLM 就知道可以在后续步骤中用 `{step_1.item_name}` 和 `{step_1.price}`。

---

## 15. API 参考

### Skill 接口方法

| 方法 | 返回类型 | 必须实现 | 说明 |
|------|----------|----------|------|
| `getName()` | `String` | 是 | 技能唯一标识 |
| `getDescription()` | `String` | 是 | 供 LLM 识别的描述 |
| `getActions()` | `Map<String, String>` | 否 | 动作映射，默认空 |
| `getHints()` | `List<String>` | 否 | 提示信息，默认空 |
| `execute(SkillContext)` | `CompletableFuture<SkillResult>` | 是 | 核心执行逻辑 |
| `isAvailable(SkillContext)` | `boolean` | 否 | 可用性检查，默认 true |

### SkillProvider 接口方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getSkills()` | `List<Skill>` | 返回所有 Skill 实例 |

### SkillContext 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `player` | `Player` | 当前玩家（可 null） |
| `action` | `String` | LLM 识别的动作 |
| `entities` | `Map<String, String>` | LLM 提取的参数 |

### SkillResult 静态工厂方法

| 方法 | 说明 |
|------|------|
| `success(String message)` | 成功，无数据 |
| `success(String message, Object data)` | 成功，带数据 |
| `failure(String message)` | 失败 |
| `failure(String message, Throwable error)` | 失败，带异常 |

### SkillResult 实例方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `isSuccess()` | `boolean` | 是否成功 |
| `getMessage()` | `String` | 消息内容 |
| `getData()` | `Object` | 数据对象 |
| `getDataMap()` | `Map<String, Object>` | 便捷获取 data Map |
| `getData(Class<T>)` | `T` | 泛型获取 data |
| `toFuture()` | `CompletableFuture<SkillResult>` | 转为 Future |

---

## 16. 分享你的 Skill

当你开发完成一个 Skill 后，可以通过以下方式分享给社区：

### 方式一：集成到你的插件中（推荐）

如果你的插件已经有用户基础，直接将 Skill 集成到插件中是最简单的方式：

```java
// 在你的插件主类中实现 SkillProvider
public class MyPlugin extends JavaPlugin implements SkillProvider {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
            SkillProvider.class, this, this, ServicePriority.Normal
        );
    }
    
    @Override
    public List<Skill> getSkills() {
        return List.of(new MyCustomSkill());
    }
}
```

**优点：**
- ✅ 用户无需额外安装
- ✅ 自动通过 SPI 注册
- ✅ 维护成本低

### 方式二：创建独立 Skill 插件

如果你想让 Skill 独立于其他插件分发：

1. 创建一个独立的 Bukkit 插件项目
2. 实现 `SkillProvider` 接口
3. 发布到 MineBBS/SpigotMC/GitHub
4. 在 README 中标注 "Requires Kilacraft-AI 1.4.1+"

**示例 plugin.yml：**
```yaml
name: MyAwesomeSkill
version: 1.0.0
main: com.example.MySkillPlugin
api-version: 1.21
softdepend: [Kilacraft-AI]  # 软依赖
authors: [YourName]
description: A custom skill for Kilacraft-AI
```

### 方式三：提交到社区索引（未来）

当 Kilacraft-AI 用户基数增长后，我们将建立社区 Skill 索引平台，届时你可以：

1. 将 Skill 发布到 GitHub/Gitee
2. 提交 PR 到社区索引仓库
3. 通过审核后获得 "Verified" 徽章
4. 在官方文档中被推荐

> **注意**：社区索引平台尚在规划中，预计 3-6 个月后上线。当前阶段建议采用方式一或方式二。

### 最佳实践

- 📝 **完善文档**：提供清晰的使用说明和配置示例
- 🧪 **充分测试**：确保在不同场景下都能正常工作
- 🔒 **注意安全**：不要执行危险操作，做好权限检查
- 🎯 **聚焦单一功能**：一个 Skill 只做一件事，并做好
- 💬 **收集反馈**：关注用户 Issues，持续优化

---

> **文档版本**：1.0  
> **维护者**：Zm_Mmm  
> **问题反馈**：请通过 GitHub Issues 或相关社区渠道联系
