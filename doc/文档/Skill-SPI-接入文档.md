# Kilacraft-AI — Skill SPI 接入文档

> **文档版本**: v2.1.1 ｜ **适用插件版本**: Kilacraft-AI ≥ 2.1.1 ｜ **SPI Jar**: `Kilacraft-Skill-API-2.1.1.jar`
> **说明**: 指导第三方插件开发者通过 Skill SPI 将自定义技能接入 Kilacraft-AI。

---

## 目录

1. [概述](#1-概述)
2. [版本与架构演进（新旧差异）](#2-版本与架构演进新旧差异)
3. [架构总览与数据流](#3-架构总览与数据流)
4. [快速开始（5 分钟接入）](#4-快速开始5-分钟接入)
5. [核心接口](#5-核心接口)
6. [结果状态标记与归一化](#6-结果状态标记与归一化)
7. [需补充信息 / 二次确认（needInfo）](#7-需补充信息--二次确认needinfo)
8. [多步骤任务数据传递](#8-多步骤任务数据传递)
9. [Skill 开发规范](#9-skill-开发规范)
10. [错误隔离与异常处理](#10-错误隔离与异常处理)
11. [权限与可用性控制](#11-权限与可用性控制)
12. [安全拦截器（重要）](#12-安全拦截器重要)
13. [命名规范与冲突处理](#13-命名规范与冲突处理)
14. [完整示例](#14-完整示例)
15. [开发依赖配置](#15-开发依赖配置)
16. [生命周期与加载顺序](#16-生命周期与加载顺序)
17. [常见问题 FAQ](#17-常见问题-faq)
18. [API 参考](#18-api-参考)
19. [发布与审查](#19-发布与审查)

---

## 1. 概述

Kilacraft-AI 通过 **SPI（Service Provider Interface）** 机制，允许第三方 Minecraft 插件把自身功能封装为 **Skill（技能）** 注册到 AI Agent，使 AI 助手能自动识别用户意图并调用。

### 核心特性

- **零耦合接入**：只需 `compileOnly` 引入 `kilacraft-skill-api.jar`，实现接口即可
- **自动发现**：基于 Bukkit `ServicesManager`，启动后自动扫描注册
- **错误隔离**：第三方 Skill 的异常不会影响主插件核心流程
- **LLM 意图驱动**：AI 自动识别意图并调用对应 Skill，用户无需记忆命令
- **结构化响应（v2.1.1）**：`SkillResult` 携带类型化 `SkillStatus`，框架统一输出 `[SUCCESS]/[FAILURE]/[NEED_INFO]` 标记
- **二次确认一等公民（v2.1.1）**：`needInfo(...)` 让 Skill 可结构化声明"需补全信息/需玩家确认"
- **多步骤任务**：Skill 返回的 `data` 可被后续步骤用 `{step_x.field}` 占位符引用，实现跨技能编排

### 适用场景

| 场景 | 示例 |
|------|------|
| 经济系统 | 查询余额、转账、商店购买 |
| 领地系统 | 查询领地信息、创建领地 |
| 排行榜 | 查询在线排行、财富排行 |
| RPG 系统 | 查询技能等级、任务进度 |
| 世界管理 | 查询区块信息、传送管理 |

---

## 2. 版本与架构演进（新旧差异）

> 如果你是从 **2.0.x 或更早** 升级，请务必阅读本节。

### 2.1 v2.1.1 引入了什么

v2.1.1 为 `SkillResult` 引入了**类型化状态**与**二次确认**能力：

| 维度 | 说明 |
|------|------|
| 类型化状态 | 新增 `SkillStatus` 枚举与 `getStatus()`；Skill 用 `success()`/`failure()`/`needInfo()` 表达结果，框架在输出给 LLM 时统一加 `[SUCCESS]/[FAILURE]/[NEED_INFO]` 标记 |
| 二次确认 | 新增 `needInfo(...)` 官方契约，支持"缺参数引导补充"与"高风险操作需玩家确认"两种场景（见 [§7](#7-需补充信息--二次确认needinfo)） |
| 裸文本 message | Skill 返回裸文本即可，bracket 标记由框架统一添加，无需、也禁止自行拼接前缀 |

> 旧版本（≤ 2.0.x）的 `SkillResult` 只有 success/failure 两态，不支持类型化状态标记与二次确认。**要使用这些新能力，请以 2.1.1+ 的 SPI Jar 编译。**

### 2.2 向后兼容性

v2.1.1 对 `SkillResult` 与 SPI Jar 的改动是**纯加法**，已编译的旧第三方 Jar **无需重新编译、无需改动**即可在新版本运行：

- `SkillResult.success(...)` / `failure(...)` 签名一字未变；旧调用在运行时自动得到正确的 `SkillStatus`
- 旧的公开构造器 `new SkillResult(boolean, String, Object)` 保留（内部推导 status）
- 新增 `SkillStatus` 枚举类进入 SPI Jar，旧 Jar 不引用它 → 不会触发 `NoSuchMethodError`/`NoSuchFieldError`

> 原理：SPI Jar 仅用于编译期；运行时服务器上只有一份 `SkillResult` 类（v2.1.1 版本），旧 Skill 调用的 `success()`/`failure()` 实际执行的是 v2.1.1 的实现，产出的对象自带 status，框架读 `getStatus()` 一切正常。

### 2.3 使用新功能

需要 `needInfo(...)` 等新能力时，用 2.1.1+ 的 SPI Jar 重新编译即可。旧 Skill 无需任何改动，继续按原样运行。

---

## 3. 架构总览与数据流

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
│  SkillRegistry 自动发现 → SkillManager 注册/执行   │
│                                 │                  │
│                     LLM 意图识别 (Phase1/Phase2)   │
│                     用户消息 → Skill 调用          │
└──────────────────────────────────────────────────┘
```

### 数据流

```
用户聊天消息
  → ChatListener / KilacraftCommand 拦截
  → SkillIntentRecognizer (LLM 两阶段意图识别)
  → SkillManager.executeSkillByIntent()
  → Skill.execute(context) → SkillResult(status, message, data)
  → 归一化层 SkillResultFormatter 统一输出 [STATUS] message
  → 返回给用户 / 注入回退 LLM / 传递给下一步骤
```

---

## 4. 快速开始（5 分钟接入）

### 第一步：添加开发依赖

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>2.1.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-2.1.1.jar</systemPath>
</dependency>
```

> 此依赖为 `compileOnly`，不会打包进你的插件 JAR。文件名含版本号，按实际下载文件调整 `systemPath`。

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
    public String getName() { return "hello_world"; }   // 全局唯一标识

    @Override
    public String getDescription() {
        return "向玩家打招呼。返回一个问候消息。";
    }

    @Override
    public Map<String, String> getActions() {
        return Map.of("greet", "向指定玩家发送问候语");
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        String name = player != null ? player.getName() : "陌生人";
        return CompletableFuture.completedFuture(
                SkillResult.success("你好，" + name + "！欢迎使用 AI 助手！"));
    }

    @Override
    public String getRequiredPermission() { return "myplugin.hello"; }
}
```

### 第三步：注册 SkillProvider

```java
package com.example.myplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillProvider;
import com.example.myplugin.skills.HelloWorldSkill;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MyPlugin extends JavaPlugin implements SkillProvider {

    @Override
    public void onEnable() {
        // Kilacraft-AI 会在服务器启动后自动扫描发现
        getServer().getServicesManager().register(
                SkillProvider.class, this, this, ServicePriority.Normal);
        getLogger().info("已注册 SkillProvider，等待 Kilacraft-AI 发现...");
    }

    @Override
    public List<Skill> getSkills() {
        return List.of(new HelloWorldSkill());
    }
}
```

### 第四步：配置 plugin.yml

```yaml
name: MyPlugin
version: 1.0
main: com.example.myplugin.MyPlugin
api-version: '1.21'
softdepend: [Kilacraft-AI]   # 软依赖，确保主插件已加载

permissions:
  myplugin.hello:
    description: 允许使用 AI 问候技能
    default: true
```

部署两个 JAR 到 `plugins/`，启动服务器即可。控制台会输出：

```
[Kilacraft-AI] 发现并注册第三方技能：hello_world (来自 MyPlugin)
```

---

## 5. 核心接口

### 5.1 Skill 接口

```java
public interface Skill {
    /** 技能名称（全局唯一标识符，≤32 字符） */
    String getName();

    /** 技能描述（供 LLM 意图识别使用，非常重要） */
    String getDescription();

    /** 动作列表：key=动作名，value=动作描述（默认空） */
    default Map<String, String> getActions() { return Collections.emptyMap(); }

    /** 额外提示信息（使用示例、注意事项，默认空） */
    default List<String> getHints() { return Collections.emptyList(); }

    /** 执行技能（核心方法，异步） */
    CompletableFuture<SkillResult> execute(SkillContext context);

    /** 检查技能在当前上下文中是否可用（默认 true） */
    default boolean isAvailable(SkillContext context) { return true; }

    /** 使用此技能所需的权限节点（必须实现，不允许 null） */
    String getRequiredPermission();
}
```

### 5.2 SkillProvider 接口

```java
public interface SkillProvider {
    List<Skill> getSkills();   // 不应返回 null，无 Skill 时返回空列表
}
```

你的插件主类实现此接口；建议每个 Skill 使用独立实例（避免共享可变状态）。

### 5.3 SkillContext 上下文

```java
public class SkillContext {
    Player getPlayer();                 // 当前玩家（控制台调用时为 null）
    String getAction();                 // LLM 识别的动作
    Map<String, String> getEntities();  // LLM 提取的参数
    String getEntity(String key);       // 获取指定参数（始终为 String，需自行转数字）
}
```

| 字段 | 说明 | 示例 |
|------|------|------|
| `player` | 触发对话的玩家 | 玩家实例 |
| `action` | LLM 识别的动作 | `"query_price"`, `"greet"` |
| `entities` | LLM 提取的参数 | `{"item": "钻石", "quantity": "10"}` |

### 5.4 SkillResult 与 SkillStatus（结构化响应）

```java
public class SkillResult {
    boolean isSuccess();           // SUCCESS→true，FAILURE/NEED_INFO→false（控制流用它）
    SkillStatus getStatus();        // 结构化状态（呈现层打标用）
    String getMessage();            // 裸文本消息（不要自带 [STATUS] 前缀）
    Object getData();               // 数据对象（约定为 Map，供多步骤传递）
    Map<String, Object> getDataMap();

    // 静态工厂方法
    static SkillResult success(String message);
    static SkillResult success(String message, Object data);
    static SkillResult failure(String message);
    static SkillResult failure(String message, Throwable error);
    static SkillResult needInfo(String message);   // 需补充信息 / 二次确认
}

public enum SkillStatus {
    SUCCESS, FAILURE, NEED_INFO;
    String prefix();   // → "[SUCCESS]" 等，框架内部用于打标
}
```

---

## 6. 结果状态标记与归一化

框架在**输出给 LLM 的边界**统一为每个结果加上 bracket marker：

| 工厂方法 | `isSuccess()` | 输出 marker | 含义 |
|----------|:-------------:|:-----------:|------|
| `success(...)` | true | `[SUCCESS]` | 执行成功 |
| `failure(...)` | false | `[FAILURE]` | 硬失败：无法继续（权限/余额/未找到/参数非法/插件缺失） |
| `needInfo(...)` | false | `[NEED_INFO]` | 软失败：需玩家补全参数或二次确认 |

**关键规则：message 必须是裸文本，不要自己拼 `[FAILURE]`/`[NEED_INFO]` 等前缀。** 框架会自动添加；自行拼接会导致标记错乱或与归一化冲突。

这条规则对 LLM 可见性很重要：玩家最终看到的是 LLM 用自然语言转述的内容，而 LLM 看到的是统一的 `[STATUS] 正文`，系统提示词要求 LLM 不得把这些 marker 暴露给玩家。

---

## 7. 需补充信息 / 二次确认（needInfo）

这是 v2.1.1 新增的官方契约，用于两种场景：

1. **缺参数**：必需参数缺失，需要玩家补充
2. **需确认**：操作有风险或金额较大，需玩家明确确认后才执行

### 7.1 基本用法

返回 `SkillResult.needInfo(message)`，**message 里给出含具体值的提示**：

```java
// 缺参数：引导玩家补充
return CompletableFuture.completedFuture(SkillResult.needInfo("你想以多少单价上架？"));

// 需确认：给出已计算的具体值
return CompletableFuture.completedFuture(
        SkillResult.needInfo("即将向 " + target + " 转账 " + amount + "，确认转账吗？"));
```

> `needInfo(...)` 的 `isSuccess()` 返回 `false`，与 `failure(...)` 一致；区别仅在于呈现给 LLM 的 marker（`[NEED_INFO]` vs `[FAILURE]`），让 LLM 能区分"需补充/确认"与"真失败"。

### 7.2 完整示例：转账二次确认（精简自内置 MarketActionSkill）

下面是一个"大额转账需确认"的完整逻辑，演示 `needInfo` 与确认参数 `confirmed` 的配合：

```java
private CompletableFuture<SkillResult> transferMoney(SkillContext context) {
    Player player = context.getPlayer();
    if (player == null) return CompletableFuture.completedFuture(SkillResult.failure("仅限在线玩家使用"));

    String targetPlayer = context.getEntity("target_player");
    if (targetPlayer == null || targetPlayer.isEmpty()) {
        // ① 缺收款人 → 需补充
        return CompletableFuture.completedFuture(SkillResult.needInfo("请告诉我要转给谁？"));
    }

    String amountStr = context.getEntity("amount");
    if (amountStr == null || amountStr.isEmpty()) {
        // ② 缺金额 → 需补充（顺带告诉余额，方便玩家决策）
        double balance = getBalance(player);
        return CompletableFuture.completedFuture(
                SkillResult.needInfo("你当前余额 " + balance + "，要转多少给 " + targetPlayer + "？"));
    }

    double amount;
    try {
        amount = Double.parseDouble(amountStr);
    } catch (NumberFormatException e) {
        return CompletableFuture.completedFuture(SkillResult.failure("金额格式不正确: " + amountStr));
    }

    // ③ 大额转账（超过余额 50%）→ 需二次确认
    double balance = getBalance(player);
    if (balance > 0 && amount > balance * 0.5) {
        String confirmed = context.getEntity("confirmed");
        if (!"true".equalsIgnoreCase(confirmed)) {
            // 返回 needInfo，message 含已计算的具体金额
            return CompletableFuture.completedFuture(
                    SkillResult.needInfo("即将向 " + targetPlayer + " 转账 " + amount
                            + "，占你余额的较大比例。确认转账吗？"));
        }
        // confirmed=true → 继续往下真正执行
    }

    // 真正执行转账
    return doTransfer(player, targetPlayer, amount);
}
```

### 7.3 二次确认流程是怎么跑通的（端到端）

整个过程是 **Skill 代码 + 框架 + 意图识别提示词** 三方协作：

```
[轮 1] 玩家："给 ZookeeR 转一半余额"
   │
   ├─ 意图识别 Phase2 识别为多步骤：step_0 查余额 → step_1 转账({step_0.balance}/2)
   ├─ 执行 step_0 得余额 1177.75；step_1 求值得 588.87，占比 >50%
   └─ transferMoney 返回 needInfo("即将向 ZookeeR 转账 588.87，确认转账吗？")
        │
        ├─ 框架归一化层输出 [NEED_INFO] 即将向 ZookeeR 转账 588.87...
        ├─ 该结果 isSuccess()=false → 回退到普通 LLM 对话
        └─ LLM 按系统提示词规范，用自然语言转述给玩家："转账需要确认，是否向 ZookeeR 转 588.87？"
              （这条回复进入对话历史，含具体值 588.87）

[轮 2] 玩家："确认"
   │
   ├─ 意图识别 Phase2 看到"确认" + 历史里的 [NEED_INFO] 上下文
   ├─ 按提示词"二次确认流"规则：从历史读取具体值 588.87，单意图调用 transfer_money
   │   entities = {target_player: "ZookeeR", amount: "588.87", confirmed: "true"}
   ├─ transferMoney 检测到 confirmed=true → 跳过 needInfo 分支，真正执行
   └─ 返回 success("已成功向 ZookeeR 转账 588.87")
```

框架保证：

1. **统一 marker**：归一化层把 `needInfo` 结果输出为 `[NEED_INFO] 正文`，LLM 能稳定识别。
2. **不会半执行**：多步骤中某步返回 `needInfo`（`isSuccess()=false`）时，**声明了依赖该步的下游**会被框架自动跳过，不会带着残缺数据继续跑。
3. **具体值回传**：needInfo 的 message 会被回退 LLM 转述进对话历史；玩家下轮确认时，意图识别从历史读到这个具体值再发起调用。

### 7.4 Skill 开发者的提示词（description / hints）怎么写

确认流能跑通，除了代码返回 `needInfo`，**还必须在 Skill 的 action 描述里声明"确认契约"**，告诉 LLM：看到 `[NEED_INFO]` 且玩家肯定回复时，用什么参数再次调用。以内置转账为例：

```yaml
# action_descriptions（节选自 MarketActionSkill.yml）
transfer_money: "向其他玩家转账。需要参数：target_player、amount（纯数字，或算术占位符如 {step_0.balance}/2）。
  可选参数：confirmed（确认为 'true'）。当用户说'转账'、'给XX转100'时使用。
  如果此操作返回 [NEED_INFO] 要求确认，且用户回复'是'、'确认'、'好'等肯定回答时，
  你必须带上相同参数再加上 confirmed='true' 再次调用 transfer_money。"
```

要点：

- **声明确认参数**：明确哪个参数表示"已确认"（如 `confirmed='true'`），框架不强制参数名，由你定义。
- **描述触发词与再调用方式**：告诉 LLM "玩家肯定回复 → 带确认参数再次调用同一动作"。
- **message 要含具体值**：needInfo 的正文里带上已计算/已查到的具体值（金额、编号等），方便玩家下轮确认时意图识别从历史读取。

> 直接返回 `needInfo(...)` 即可，`[NEED_INFO]` 标记由框架自动添加。

---

## 8. 多步骤任务数据传递

### 8.1 概述

当用户的一个请求需要多个 Skill 按顺序执行时，前一个 Skill 返回的 `data` 可被后一个 Skill 用 `{step_x.field}` 占位符引用。这是一个**三方约定**：开发者写代码和描述 → LLM 读描述生成占位符 → 框架解析占位符。

### 8.2 Skill 开发者要做的

1. 在 `SkillResult.success(message, data)` 中返回 `Map<String, Object>` 类型的 data
2. 在 description / action 描述中说明返回了哪些 data 字段

```java
private CompletableFuture<SkillResult> queryPrice(SkillContext context) {
    String itemName = context.getEntity("item");
    double price = getPrice(itemName);
    Map<String, Object> data = new HashMap<>();
    data.put("item_name", itemName);
    data.put("price", price);
    return CompletableFuture.completedFuture(
            SkillResult.success(itemName + "的价格是 $" + price, data));
}
```

描述里写明：`"返回数据包含 item_name、price 字段"`。

### 8.3 占位符格式

```
{step_<步骤ID>.<字段名>}                       // 普通字段
{step_<步骤ID>.<数组字段>[<索引>].<子字段>}    // 数组索引访问
```

示例：

```json
{"item": "{step_1.item_name}", "quantity": "10"}
{"warp_name": "{step_1.warps[0].warp_name}"}
```

- 步骤 ID 由 LLM 生成（如 `step_1`）
- 字段名对应 `data` Map 的 key
- 支持单层数组索引，不支持多层嵌套数组

当用户说"查钻石价格，不超过 100 就买 10 个"，LLM 会自动编排两步并在第二步用 `{step_1.price}` 引用第一步结果，框架的 `TaskExecutor` 自动解析。

---

## 9. Skill 开发规范

### 9.1 命名规范

| 项目 | 规范 | 示例 |
|------|------|------|
| Skill 名称 | 小写+下划线，`插件前缀_功能` | `economy_balance`, `mcmmo_stats` |
| Action 名称 | 小写+下划线，`动词_名词` | `query_balance`, `transfer_money` |
| Entity 键名 | 小写+下划线 | `item_name`, `player_name`, `quantity` |
| Data 字段名 | 小写+下划线 | `health`, `max_health`, `food_level` |

Skill 名称全局唯一，与内置 Skill 重名时第三方会被跳过（内置优先）。建议用 `插件名_` 前缀避免冲突。

### 9.2 Description 编写要点

`getDescription()` 是 LLM 意图识别的核心依据。原则：明确功能 + 包含用户可能用的关键词 + 描述返回的 data 字段。

```java
// 好
"查询玩家的 MCMMO 技能等级。返回的 data 包含 skill_name、level、xp 字段，供多步骤任务参数传递。"
// 差
"查询信息"   // 太模糊
```

### 9.3 Action 设计

每个 Skill 可含多个 Action，由 LLM 根据用户输入自动选择。Action 描述决定 LLM 调哪个 action、提取哪些参数：

```java
return Map.of(
    "query_balance", "查询玩家的余额。返回数据包含 balance 字段。",
    "transfer", "向指定玩家转账。需要参数：target_player、amount。"
);
```

### 9.4 Entity 参数提取

LLM 从用户输入提取参数，以 `Map<String,String>` 传入。在 action 描述里声明需要哪些参数：

```java
"购买市场上的物品。需要参数：item（物品名称，支持中英文）、quantity（默认1）。"
```

用户说"帮我买10个钻石" → `{"item":"钻石","quantity":"10"}`。entity 的 value 始终是 String，需自行转数字并处理异常。

---

## 10. 错误隔离与异常处理

### 10.1 自动隔离

`SkillManager` 对第三方 Skill 执行了完整错误隔离——异步与同步异常都会被兜底转为 `SkillResult.failure(...)`，不会击穿主流程：

```java
try {
    return skill.execute(context).exceptionally(ex -> {
        plugin.getLogger().log(Level.SEVERE, "技能执行异常：" + skillName, ex);
        return SkillResult.failure("技能执行出错，请联系管理员");
    });
} catch (Exception e) {
    return SkillResult.failure("技能执行出错，请联系管理员");
}
```

### 10.2 开发建议

虽然框架兜底，你仍应做好自身异常处理，并返回对用户友好的 `failure(...)` 消息（用户会看到）：

```java
public CompletableFuture<SkillResult> execute(SkillContext context) {
    try {
        String itemName = context.getEntity("item");
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("请指定物品名称"));
        }
        return doSomething(itemName);
    } catch (Exception e) {
        return CompletableFuture.completedFuture(SkillResult.failure("查询失败：" + e.getMessage()));
    }
}
```

注意：不要抛未捕获 RuntimeException；异步链也要 `.exceptionally()` 处理；`isAvailable()` 中不要做复杂逻辑（其异常也会被隔离）。

---

## 11. 权限与可用性控制

### 11.1 权限预检过滤（getRequiredPermission）

```java
@Override
public String getRequiredPermission() { return "myplugin.query.stats"; }
```

`getRequiredPermission()` 返回 **Skill 级**权限，控制整个 Skill（含所有 Action）的描述是否注入 LLM 提示词。作用时机：意图识别阶段（构建提示词时）。若调用者无此权限，Skill 的名称/描述/动作**不会**注入提示词，LLM 根本不知道它存在——避免误匹配、节省 Token、防止信息泄露。

| 层级 | 控制范围 | 作用阶段 | 实现方式 |
|------|----------|----------|----------|
| Skill 级 | 整个 Skill 可见性 | 意图识别（提示词构建时） | `getRequiredPermission()` |
| Action 级（可选） | 单个 Action 可执行性 | Skill 执行时 | `execute()` 内部检查 |

第三方权限在自身 `plugin.yml` 声明即可，框架通过 `Player.hasPermission()` 实时查询：

```yaml
permissions:
  myplugin.admin.stats:
    description: 允许使用 AI 查询玩家状态统计
    default: op
```

> **Skill 内聚原则**：建议一个 Skill 的所有 Action 面向同类权限用户，避免在同一 Skill 内混合管理员 Action 和普通玩家 Action，否则 AI 可能编排到玩家无权执行的 Action。不同权限级别应拆分为独立 Skill。

### 11.2 isAvailable() 检查

```java
@Override
public boolean isAvailable(SkillContext context) {
    return Bukkit.getPluginManager().getPlugin("MyEconomy") != null;  // 前置插件是否安装
}
```

`isAvailable()` 用于**运行时可用性检查**（如前置插件是否安装），每次执行前调用；返回 false 时用户收到"该功能暂时不可用"。**不建议**在此做权限检查——权限用 `getRequiredPermission()` 声明，由框架在意图识别阶段过滤。

---

## 12. 安全拦截器（重要）

框架内置**非合作式安全过滤器**（`SkillSecurityFilter`），每次 Skill 执行前自动运行，保护玩家数据不被恶意 Skill 访问/篡改。

### 12.1 核心机制

```
Skill 执行前
  → SkillSecurityFilter.sanitize(skillName, action, context)
  → 遍历 context.entities 所有 Value
  → Value 是否匹配在线玩家名？
       ├─ 是当前玩家自己 → 放行
       ├─ 是其他在线玩家 + 在白名单 → 放行
       └─ 是其他在线玩家 + 不在白名单 → 消毒（替换为当前玩家名）
  → 返回消毒后的 entities 给 Skill.execute()
```

特性：非合作式（直接扫描所有 Value，不依赖 Skill 声明参数名）、消毒而非阻断、始终运行不可绕过。

### 12.2 对你的 Skill 意味着什么

- **只操作当前玩家**：无需任何处理，正常开发即可。
- **需要操作其他玩家（如转账）**：需服主在 `config.yml` 加白名单：

```yaml
security:
  player_isolation:
    allowed_actions:
      - "economy.transfer"        # 技能级白名单
      - "economy.send_payment"    # 或 "技能名.动作名" 动作级
```

白名单由服主配置，开发者无法自行决定；技能名须与 `getName()` 完全一致（大小写敏感）。

- **不在白名单却操作其他玩家**：entities 中的他人玩家名会被替换为当前玩家名，Skill"以为"在给自己操作——这是安全设计，恶意 Skill 无法绕过。

已内置白名单示例：`cmi.send_tp_request`、`AFKTask.create_task`、`command.execute_command`。

### 12.3 注意事项

不要尝试绕过；不要在 entities 里嵌入玩家名（如 `"msg Hub 你好"` 无法被正确识别）；玩家名正则 `^[a-zA-Z0-9_]{1,16}$`，不符合的值直接跳过扫描。

---

## 13. 命名规范与冲突处理

建议命名空间：`<插件名小写>_<功能>`，如 `mcmmo_query_level`、`towny_query_info`。

冲突处理：内置 Skill 优先；多个第三方同名时先注册先得；冲突时控制台输出 Warning。

```
[Kilacraft-AI] 跳过第三方技能 'market_query'（来自 SomePlugin）：名称与已注册技能冲突
```

---

## 14. 完整示例

### 14.1 玩家状态查询 Skill

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

    @Override public String getName() { return "player_stats_query"; }

    @Override public String getDescription() {
        return "查询玩家状态信息（生命值、饥饿值、经验等级）。"
                + "返回 data 包含 health、max_health、food_level、level、total_exp 字段。";
    }

    @Override public Map<String, String> getActions() {
        return Map.of(
            "query_health", "查询玩家当前生命值。返回数据包含 health 和 max_health 字段。",
            "query_food", "查询玩家当前饥饿值。返回数据包含 food_level 字段。",
            "query_experience", "查询玩家当前经验等级。返回数据包含 level 和 total_exp 字段。");
    }

    @Override public String getRequiredPermission() { return "statsplugin.query"; }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("请指定玩家"));
        }
        return switch (context.getAction()) {
            case "query_health" -> CompletableFuture.completedFuture(
                    SkillResult.success(String.format("生命值：%.1f/%.1f", player.getHealth(), player.getMaxHealth()),
                            Map.of("health", player.getHealth(), "max_health", player.getMaxHealth())));
            case "query_food" -> CompletableFuture.completedFuture(
                    SkillResult.success("饱食度：" + player.getFoodLevel() + "/20",
                            Map.of("food_level", player.getFoodLevel())));
            case "query_experience" -> CompletableFuture.completedFuture(
                    SkillResult.success(String.format("等级：%d，总经验：%d", player.getLevel(), player.getTotalExperience()),
                            Map.of("level", player.getLevel(), "total_exp", player.getTotalExperience())));
            default -> CompletableFuture.completedFuture(SkillResult.failure("未知动作：" + context.getAction()));
        };
    }
}
```

### 14.2 插件主类与 plugin.yml

```java
package com.example.statsplugin;

import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class StatsPlugin extends JavaPlugin implements SkillProvider {
    @Override public void onEnable() {
        getServer().getServicesManager().register(SkillProvider.class, this, this, ServicePriority.Normal);
    }
    @Override public List<Skill> getSkills() { return List.of(new PlayerStatsSkill()); }
}
```

```yaml
name: StatsPlugin
version: 1.0
main: com.example.statsplugin.StatsPlugin
api-version: '1.21'
softdepend: [Kilacraft-AI]
permissions:
  statsplugin.query:
    description: 允许使用 AI 查询玩家状态信息
    default: true
```

### 14.3 用户交互

```
用户：我的血量是多少？
AI：你的生命值是 18.5/20.0
用户：经验等级和饱食度呢？
AI：等级 15，总经验 3200。饱食度 18/20。
```

---

## 15. 开发依赖配置

### Maven

```xml
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>2.1.1</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API-2.1.1.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.spigotmc</groupId>
    <artifactId>spigot-api</artifactId>
    <version>1.21-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation files('libs/Kilacraft-Skill-API-2.1.1.jar')
    compileOnly 'org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT'
}
```

### 关于 kilacraft-skill-api.jar

此 JAR 包含以下 **6 个**类的编译产物，供第三方编译时引用：

```
com.zm.kilacraftAI.skills.framework.Skill
com.zm.kilacraftAI.skills.framework.SkillContext
com.zm.kilacraftAI.skills.framework.SkillResult
com.zm.kilacraftAI.skills.framework.SkillStatus      ← v2.1.1 新增
com.zm.kilacraftAI.skills.framework.SkillIntent
com.zm.kilacraftAI.skills.framework.SkillProvider
```

> 此 JAR **不需要也不能**打包进你的插件，运行时由 Kilacraft-AI 主插件提供。

---

## 16. 生命周期与加载顺序

```
服务器启动
  ├─ 第三方插件 onEnable() → ServicesManager.register(SkillProvider.class, ...)
  ├─ Kilacraft-AI onEnable() → 初始化 SkillManager、注册内置 Skill、调度延迟任务
  ├─ 服务器启动完成
  └─ 延迟 20 tick → SkillRegistry.discoverAndRegister()
        ├─ 扫描 ServicesManager 中的 SkillProvider
        ├─ 遍历 getSkills()、检查名称冲突
        └─ 注册到 SkillManager

用户聊天
  └─ ChatListener → SkillIntentRecognizer → SkillManager.executeSkillByIntent()
        ├─ isAvailable() 检查（带错误隔离）
        └─ Skill.execute(context)（带错误隔离）

服务器关闭
  └─ Bukkit 自动注销 ServicesManager 中的注册
```

> 延迟 20 tick 是为确保所有第三方插件完成 `onEnable()` 注册，避免加载顺序差异导致漏扫。**自动发现仅在启动时执行一次，安装/更新第三方 Skill 后需重启**（不支持热重载发现）。

---

## 17. 常见问题 FAQ

**Q: 我的插件先加载还是 Kilacraft-AI 先加载？**
A: 取决于 Bukkit 加载顺序（通常字母序），但无论谁先，自动发现都能工作：先注册的会被延迟扫描发现。

**Q: 支持热重载吗？**
A: 自动发现不支持（仅启动时一次）。但若你的 Skill 描述从自身 config 读取，可自行热重载描述内容。

**Q: execute 可以执行耗时操作吗？**
A: 可以，返回 `CompletableFuture` 支持异步。但不要在主线程阻塞；Bukkit API 调用须在主线程（用 `Bukkit.getScheduler().runTask()`）。

**Q: 一个插件可注册多个 Skill 吗？**
A: 可以，`getSkills()` 返回多个实例即可。

**Q: 控制台能用 Skill 吗？**
A: 取决于你的 `isAvailable()`/`execute()`。控制台调用时 `context.getPlayer()` 为 null，需自行处理。

**Q: 我需要"二次确认"功能，怎么实现？**
A: 用 `SkillResult.needInfo(msg)` 返回，并在 action 描述里声明确认参数契约。详见 [§7](#7-需补充信息--二次确认needinfo)。

**Q: 我应该在 message 里写 `[FAILURE]` 前缀吗？**
A: **不要**。v2.1.1 起框架统一加标，你写裸文本即可；自己加前缀会导致双标。

**Q: 多步骤任务中如何被 LLM 正确编排？**
A: 关键是 description / action 描述清楚说明返回的 data 字段，LLM 据此用 `{step_x.field}` 引用。

---

## 18. API 参考

### Skill 接口方法

| 方法 | 返回类型 | 必须实现 | 说明 |
|------|----------|:--------:|------|
| `getName()` | `String` | 是 | 技能唯一标识（≤32 字符） |
| `getDescription()` | `String` | 是 | 供 LLM 识别的描述 |
| `getActions()` | `Map<String,String>` | 否 | 动作映射，默认空 |
| `getHints()` | `List<String>` | 否 | 提示信息，默认空 |
| `execute(SkillContext)` | `CompletableFuture<SkillResult>` | 是 | 核心执行逻辑 |
| `isAvailable(SkillContext)` | `boolean` | 否 | 可用性检查，默认 true |
| `getRequiredPermission()` | `String` | **是** | 权限预检节点，必须声明 |

### SkillResult 静态工厂方法

| 方法 | SkillStatus | isSuccess | 说明 |
|------|:-----------:|:---------:|------|
| `success(String message)` | SUCCESS | true | 成功，无数据 |
| `success(String message, Object data)` | SUCCESS | true | 成功，带数据（多步骤传递） |
| `failure(String message)` | FAILURE | false | 硬失败 |
| `failure(String message, Throwable error)` | FAILURE | false | 硬失败，带异常 |
| `needInfo(String message)` | NEED_INFO | false | 需补充信息/二次确认 |

### SkillResult 实例方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `isSuccess()` | `boolean` | 控制流判断（SUCCESS→true） |
| `getStatus()` | `SkillStatus` | 结构化状态（呈现层打标） |
| `getMessage()` | `String` | 裸文本消息 |
| `getData()` | `Object` | 数据对象 |
| `getDataMap()` | `Map<String,Object>` | 便捷获取 data Map |
| `getData(Class<T>)` | `T` | 泛型获取 data |
| `toFuture()` | `CompletableFuture<SkillResult>` | 转为 Future |

### SkillStatus 枚举

| 值 | prefix() | 含义 |
|----|:--------:|------|
| `SUCCESS` | `[SUCCESS]` | 成功 |
| `FAILURE` | `[FAILURE]` | 硬失败 |
| `NEED_INFO` | `[NEED_INFO]` | 需补充/确认 |

---

## 19. 发布与审查

### 19.1 分发方式

**集成到你的插件（推荐）**：用户无需额外安装，通过 SPI 自动注册，维护成本低。

**独立 Skill 插件**：创建独立 Bukkit 插件项目，发布到 MineBBS/SpigotMC/GitHub，README 标注 "Requires Kilacraft-AI 2.1.1+"。

### 19.2 安全审查（必做）

所有第三方 Skill 必须提交安全审查，通过后在 [Skill 全球台账](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html) 标记为 🟢 已审查。

提交方式：在 [GitHub Issues](https://github.com/axy-yxa/Kilacraft-AI/issues) 创建 Issue，标题 `[Skill 审查] 你的 Skill 名称`，提供：Skill 名称、源码或 JAR、功能描述、权限说明（`getRequiredPermission()` 返回值 + plugin.yml 声明 + default 值）、文档链接（可选）。

审查标准：`getRequiredPermission()` 正确实现且非 null；权限节点已在 plugin.yml 声明且 default 合理；不直接操作其他玩家数据（除非声明且合理）；不执行危险命令；无恶意网络请求；资源释放正确。

### 19.3 最佳实践

- 📝 完善文档与配置示例
- 🧪 充分测试（含 needInfo 确认流、多步骤编排）
- 🔒 做好权限检查，不执行危险操作
- 🎯 一个 Skill 聚焦单一功能
- 💬 关注用户反馈持续优化
