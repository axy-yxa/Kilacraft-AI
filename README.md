# Kilacraft-AI

> **🎉 v1.4.0 重大更新**：引入第三方 Skill SPI 扩展机制！支持插件开发者无缝接入 AI Agent。详见 [更新日志](#-更新日志)

一个功能强大的 Minecraft AI 对话插件，集成 DeepSeek AI，为服务器玩家提供智能交互体验。

## 📋 特性亮点

### 🤖 AI Agent 核心能力
- **第三方 Skill SPI 扩展机制（v1.4.0+）**：支持插件开发者无缝接入 AI Agent
  - `SkillProvider` 接口与自动发现机制
  - 零耦合接入与错误隔离
- **LLM 意图识别引擎**：智能理解用户真实意图，自动路由到对应技能
- **Skills 技能系统框架**：可扩展的 AI 技能执行框架，支持异步非阻塞执行
- **通用 LLM Provider 架构（v1.3.6+）**：支持 OpenAI 标准 API 格式的 LLM 服务
  - 配置驱动，通过 config.yml 即可切换不同 LLM 厂商
  - 支持 DeepSeek、智谱 AI、Moonshot 等所有遵循 OpenAI 标准的 API
  - HTTP 连接池优化，复用连接提升性能
  - 流式响应支持，降低首字延迟
  - 为未来扩展更多 LLM 提供商奠定基础
- **Bukkit API 动态调用（v1.3.3+）**：基于数据驱动的原版 API 调用能力
  - 从 `apis.yml` 配置文件加载 API 定义，无需硬编码代码
  - 支持链式调用（method_chain）和并行调用（additional_methods）两种模式
  - 反射执行引擎，动态调用 Player/World/Server 的各种方法
  - 权限控制、热重载支持，修改配置立即生效
  - 预置玩家状态查询、世界信息查询、服务器信息查询等 API
- **多模态交互**：命令模式、连续对话模式、关键词触发模式
- **多步骤任务执行器（v1.3.2+）**：复杂任务自动分解与顺序执行
  - 基于拓扑排序的依赖关系管理
  - 前置步骤结果自动传递给后续步骤
  - LLM 综合分析所有步骤结果并生成友好回复

### ⚠️ 流式输出说明

当前版本暂不支持真正的流式输出功能，`enable_stream_output` 配置项为预留项。

**第三方插件开发者提示**：如需流式效果，可在自己的插件中实现"伪流式输出"——接收完整 AI 回复后，使用 `BukkitRunnable` 定时任务分批显示（例如每 500ms 显示 10 个字符），模拟打字机效果。

**注意**：回调命令在主线程执行（Bukkit API 要求），但第三方插件应**立即返回**，将复杂逻辑放到异步线程处理，避免阻塞主线程。详见文档末尾的[第三方开发者最佳实践](#-第三方开发者最佳实践)章节。

### 💰 经济系统集成（实验性）
- **GlobalMarketPlus 深度集成**：玩家余额查询、市场价格查询、商品列表查询
- **商品在售查询（v1.3.4+）**：查询指定物品是否在售、库存数量、卖家信息
- **我的商品查询（v1.3.4+）**：查询玩家自己在售的商品列表
- **邮箱查询（v1.3.4+）**：查询玩家邮箱待领取的邮件
- **市场统计（v1.3.4+）**：查询市场总商品数和卖家数
- **多物品联合查询**：一次查询多个商品价格，格式：`钻石：2,木棍:1`
- **数量识别**：自然语言理解，支持"买 5 个木棍"等表达
- **最优价格计算**：从便宜到贵智能组合，考虑实际库存
- **库存不足提示**：显示所有在售商品的详细价格和数量

### 🎭 个性化与上下文
- **人格系统**：支持多个人格配置，可自定义 AI 角色和回复风格
- **上下文对话**：自动保存历史对话，支持连续的上下文交流
- **知识库增强**：支持本地知识库检索，让 AI 更了解你的服务器

### 🔌 第三方插件支持与扩展
- **Skill SPI 扩展机制（v1.4.0+）**：支持第三方插件通过 SPI 注册自定义 Skill
  - 零耦合接入：只需引入 `Kilacraft-Skill-API.jar` 作为 compileOnly 依赖
  - 自动发现：基于 Bukkit ServicesManager 启动时自动扫描
  - 错误隔离：第三方 Skill 异常不影响核心流程
  - 提供完整的 SPI 接入文档与示例
- **插件命令模式通用化（v1.4.0+）**：解耦 AI 人格系统与 MythicMobs 的绑定关系，提供两种零耦合集成方式
  - **方式 A：Bukkit Event 事件通知**（✅ 推荐，实时性最好）
    ```java
    // 在你的插件中监听事件（完全零耦合，通过反射）
    @EventHandler
    public void onAIResponse(org.bukkit.event.Event event) {
        try {
            Class<?> eventClass = event.getClass();
            if (!eventClass.getName().equals("com.zm.kilacraftAI.api.event.AIResponseReadyEvent")) {
                return;
            }
            
            String playerName = (String) eventClass.getMethod("getPlayerName").invoke(event);
            String response = (String) eventClass.getMethod("getResponse").invoke(event);
            String personality = (String) eventClass.getMethod("getPersonality").invoke(event);
            
            // 处理 AI 回复
            getLogger().info("收到 AI 回复: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    ```
  - **方式 B：控制台命令 + 回调命令**（✅ 强烈推荐，配置驱动）
    ```bash
    # 请求 AI 回复并指定回调命令
    /kilacraft plugins <人格> <内容> <玩家UUID> "myplugin handleAI {response}"
    
    # AI 完成后自动执行：myplugin handleAI <实际回复内容>
    
    # 或者使用轮询方式
    /kilacraft plugins get <人格> <玩家UUID>
    # 返回 UNDEFINED 表示未完成，返回实际内容表示完成
    ```
  - **一次性消费缓存**：获取即删除，避免数据污染
  - **上下文隔离**：不同人格和玩家的对话历史完全独立
- **MythicMobs 占位符**：支持 `%kilacraft_ai_answer%` 获取 AI 最新回复
- **控制台命令调用**：其他插件可通过控制台命令集成 AI 功能

### ⚙️ 管理与安全
- **权限管理**：细粒度的权限控制，管理员可清除其他玩家历史
- **冷却限制**：防止滥用，支持自定义冷却时间和世界限制
- **流式输出**：支持实时显示 AI 回复生成过程（可选）

## 🔧 安装方法

1. 下载最新版本的 `Kilacraft-AI.jar` 文件
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 启动服务器，等待插件生成配置文件
4. 关闭服务器，编辑 `plugins/Kilacraft-AI/config.yml` 配置 API 密钥和其他设置
5. 重新启动服务器

### 依赖要求

- **必需**：
  - Minecraft Server 1.21+
  - Java 17+
  
- **可选**（用于扩展功能）：
  - MythicMobs 5.12.0+（用于占位符功能）
  - GlobalMarketPlus 1.3.8.0+（用于经济系统技能，实验性）
  - Vault（用于多货币支持）

## ⚙️ 配置说明

### 核心配置 (config.yml)

```yaml
# LLM Provider 配置（v1.3.6+）
# 通用 LLM Provider 架构，支持所有遵循 OpenAI 标准 API 格式的厂商
api:
  key: "your-api-key"              # LLM API 密钥（必填）
  url: "https://api.deepseek.com/v1/chat/completions"  # API 地址
  model: "deepseek-chat"            # 使用的模型名称
  temperature: 0.7                  # 温度参数（0-2，越高越随机）
  max_tokens: 1000                  # 最大回复长度（Token 数）
  
  # 支持的 LLM 厂商示例：
  # - DeepSeek: https://api.deepseek.com/v1/chat/completions
  # - 智谱 AI: https://open.bigmodel.cn/api/paas/v4/chat/completions
  # - Moonshot: https://api.moonshot.cn/v1/chat/completions
  # 只需修改 url 和 model 即可切换不同厂商

# 插件设置
settings:
  debug_mode: false                   # 调试模式
  enable_chat_command: true           # 启用连续对话模式
  enable_trigger: true                # 启用关键词触发
  trigger_keywords: "@kila,@ai,@zm"   # 触发关键词
  enable_stream_output: false         # 流式输出
  cooldown_seconds: 5                 # 冷却时间（秒）
  plugins_cooldown_seconds: 3         # 插件命令专用冷却
  max_history: 10                     # 最大历史记录数
  allowed_worlds: []                  # 允许的世界（留空表示全部）
  banned_worlds: []                   # 禁止的世界
  system_prompt: "你是 Minecraft 助手..."  # 系统提示词

# 消息格式
messages:
  ai_name: "Kilacraft-AI"
  ai_prefix: "§7[Kilacraft-AI] §f"
  thinking_message: "正在思考中..."

# Agent 能力配置（v1.3.2+）
agent:
  enabled: true                       # 总开关（优先级最高）
  enable_chat_listener: true          # ChatListener 入口是否启用 Agent
  enable_command: true                # KilacraftCommand 入口是否启用 Agent
  
  # 历史对话上下文配置（v1.3.5+）
  intent_history_count: 5             # 意图识别时的历史对话轮数，用于理解连续对话意图
  analysis_history_count: 2           # 结果分析时的历史对话轮数，使回复更自然
  
  prompts:
    system_prompt: "你是一个专业的 Minecraft 游戏助手..."
    analysis_prompt: "{results}\n请根据以上对话历史、当前输入、执行结果，给出综合性的分析和建议，请用简洁友好的语言回复玩家。"

# 知识库配置
knowledge:
  enabled: true                       # 启用知识库
  max_relevant_chunks: 3              # 最大相关知识数量
  segment:                            # 知识库分段配置
    max_size: 500                     # 每个片段最大字符数（超过此值会继续分割）
    min_size: 25                      # 每个片段最小字符数（小于此值的片段会被忽略）
    overlap: 30                       # 片段重叠字符数（保持上下文连贯性）
```

#### API 配置说明

**通用 LLM Provider 架构（v1.3.6+）**：
- 配置驱动，通过修改 `url` 和 `model` 即可切换不同 LLM 厂商
- 支持所有遵循 **OpenAI 标准 API 格式** 的服务商
- HTTP 连接池优化，自动重试机制
- 流式响应支持，降低首字延迟

**常用 LLM 厂商配置**：

| 厂商 | API URL | 推荐模型 |
|------|---------|----------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `deepseek-chat` |
| 智谱 AI | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4` |
| Moonshot | `https://api.moonshot.cn/v1/chat/completions` | `moonshot-v1-8k` |

**切换 LLM 厂商步骤**：
1. 修改 `api.url` 为目标厂商的 API 地址
2. 修改 `api.model` 为目标厂商的模型名称
3. 确保 `api.key` 填写正确的 API 密钥
4. 使用 `/kilacraft reload` 重载配置（或重启服务器）

### 语言配置 (language.yml)

所有系统提示文本都可以在 `language.yml` 中自定义，包括：

- **帮助消息**：各种命令的帮助提示
- **权限相关**：无权限时的错误消息
- **功能状态**：功能启用/禁用状态的提示
- **命令执行结果**：成功/失败等操作反馈
- **日志消息**：控制台输出的日志格式

示例配置：

```yaml
help:
  messages:
    - "§e使用方法：/kilacraft <消息>"
    - "§e简写：/kila <消息>"
  clear-self: "§e 清除历史：/kilacraft clear"
  
permissions:
  reload: "§c你没有权限重载配置！"
  
features:
  chat-mode-enter: "§a已进入连续对话模式！"
  
commands:
  reload-success: "§a配置已重载！"
```

支持变量占位符：`{player}`, `{sender}` 等

### Agent 能力配置（v1.3.2+）

Agent 能力提供细粒度的配置控制，允许服务器管理员决定在哪些入口启用 AI 的智能意图识别功能。

#### 配置说明

```yaml
agent:
  # 总开关 - 优先级高于所有分开关
  # true: 启用 Agent 能力，AI 会先进行意图识别
  # false: 禁用 Agent 能力，直接进入普通 AI 对话
  enabled: true
  
  # ChatListener 入口独立开关
  # 控制关键词触发（@ai 等）和连续对话模式（/kilacraft chat）
  enable_chat_listener: true
  
  # KilacraftCommand 入口独立开关
  # 控制 /kilacraft 命令入口
  enable_command: true
  
  # LLM 提示词配置
  prompts:
    # 系统提示词 - 定义 LLM 在分析执行结果时的角色
    system_prompt: "你是一个专业的 Minecraft 游戏助手，请根据提供的数据给出有用的建议。"
    
    # 分析提示词 - 指导 LLM 如何分析执行结果
    # 支持 {results} 占位符，会被替换为任务执行结果摘要
    analysis_prompt: "{results}\n请根据以上对话历史、当前输入、执行结果，给出综合性的分析和建议，请用简洁友好的语言回复玩家。"
```

#### 工作流程

**启用 Agent 能力时**：
1. 用户输入 → LLM 意图识别
2. 判断是单意图还是多步骤任务
3. 执行技能或任务计划
4. LLM 分析执行结果并生成友好回复
5. 如果意图识别失败或技能执行失败 → 回退到普通 AI 对话

**禁用 Agent 能力时**：
1. 用户输入 → 直接进入普通 AI 对话
2. 不进行意图识别和技能调用

#### 使用场景

- **全入口启用**：适合需要复杂任务处理的服务器，AI 可以自动执行多步骤操作
- **仅命令模式启用**：适合只想在 `/kilacraft` 命令中提供智能功能，聊天中保持简单对话
- **全部禁用**：适合只需要基础 AI 对话功能的服务器

### 人格配置 (personalities.yml)

在 `plugins/Kilacraft-AI/personalities.yml` 文件中定义不同的人格配置。

#### 基本用法

```yaml
# 公共提示词（所有人格共享的基础提示词，可选）
common_prompt: "你是一个 Minecraft 游戏的 NPC，需要满足玩家的常见要求。"

# 严厉教师人格
严厉教师: |
  你是一位严厉的 Minecraft 教师，正在教导玩家 {player}。
  你对学生的要求很高，说话简洁直接，但会耐心解答问题。
  专注于教授游戏机制、红石电路和建筑技巧。

# 冒险伙伴人格
冒险伙伴: |
  你是玩家 {player} 的忠实冒险伙伴，性格开朗幽默。
  你喜欢分享探险故事，提供战斗建议，推荐装备搭配，总是鼓励玩家勇敢探索。

# 图书管理员人格
图书管理员: |
  你是一位博学的图书管理员，正在为冒险者 {player} 提供知识服务。
  你说话文雅，喜欢引用古籍，精通 Minecraft 的历史、生物特性、矿物分布和各种冷知识。

# 奸商人格
奸商: |
  你是一个精明的 Minecraft 商人，正在和顾客 {player} 交谈。
  你说话圆滑，总想推销自己的商品，对经济系统和交易价格了如指掌，时不时会开个玩笑。
```

#### 配置说明

- **common_prompt**（可选）：公共提示词，会自动追加到每个人格的提示词前面
  - 用于定义所有人格共享的基础设定，如"你是 Minecraft 游戏的 NPC"
  - 如果不需要公共提示词，可以不配置此项
  
- **人格名称**：如 `严厉教师 `、` 冒险伙伴` 等，使用 YAML 键值对格式
  - 建议使用中文名称，便于识别和调用
  - 避免使用特殊字符和空格
  
- **提示词内容**：每个人格的详细设定
  - 支持 YAML 多行文本格式（使用 `|` 或 `>`）
  - 支持 `{player}` 占位符，自动替换为当前玩家名称
  - 描述 AI 的角色定位、语言风格、专业领域和行为准则

#### 高级特性（v1.3.3+）

- **YAML 多行文本支持**：使用 `|` 或 `>` 编写复杂的人格描述
  - `|` 保留换行符，适合多行段落
  - `>` 折叠换行符，适合长句子
  
- **JSON 格式容错**：自动修复常见的 JSON 格式错误
- **{player} 占位符**：自动替换为当前玩家名称
- **热重载支持**：修改配置文件后使用 `/kilacraft personalities reload` 立即生效
- **公共提示词机制**：通过 `common_prompt` 统一配置基础设定，避免重复

#### 使用建议

- **人格命名**：使用简洁明了的名称，避免特殊字符
- **提示词设计**：明确 AI 的角色定位、语言风格和行为准则
- **公共提示词**：将通用设定放入 `common_prompt`，人格特定设定放入各自配置
- **多场景应用**：可以按场景或功能创建多个人格，如教学、冒险、交易等

## 🎮 使用说明

### 基础命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/kilacraft <消息>` | 无（所有玩家） | 与 AI 对话 |
| `/kila <消息>` | 无（所有玩家） | 简写命令 |
| `/ai <消息>` | 无（所有玩家） | 简写命令 |
| `/zm <消息>` | 无（所有玩家） | 简写命令 |
| `/kilacraft chat` | 无（所有玩家） | 进入/退出连续对话模式 |
| `/kilacraft clear` | `kilacraft.clear.self` | 清除自己的对话历史 |
| `/kilacraft clear <玩家>` | `kilacraft.clear.other` | 清除指定玩家的对话历史 |
| `/kilacraft reload` | `kilacraft.reload` | 重载主配置和语言配置 |
| `/kilacraft knowledge reload` | `kilacraft.knowledge` | 重载知识库 |
| `/kilacraft personalities reload` | `kilacraft.personalities` | 重载人格配置 |
| `/kilacraft plugins <人格> <内容> <UUID>` | 控制台专用 | 第三方插件调用 |

### 三种交互模式

#### 1. 命令模式（默认）
玩家使用 `/kilacraft <消息>` 或简写与 AI 对话。

```
玩家：/kilacraft 你好
Kilacraft-AI: 你好！有什么我可以帮助你的吗？
```

#### 2. 连续对话模式
输入 `/kilacraft chat` 进入连续对话模式，之后发送的所有聊天消息都会自动发送给 AI。

```
玩家：/kilacraft chat
→ 已进入连续对话模式！
玩家：今天天气不错
Kilacraft-AI: 是啊，适合出去冒险呢！
玩家：我们去挖矿吧
Kilacraft-AI: 好主意！记得带上足够的火把和食物哦。
```

#### 3. 关键词触发模式
在聊天中输入包含关键词（如 `@ai`）的消息，会自动触发 AI 回复。

```
玩家：@ai 这个怎么做？
Kilacraft-AI: 让我来帮你...
```

### 权限列表

#### 基础权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `kilacraft.clear.self` | true | 清除自己的对话历史 |
| `kilacraft.clear.other` | op | 清除其他玩家的对话历史 |
| `kilacraft.reload` | op | 重载配置 |
| `kilacraft.knowledge` | op | 管理知识库 |
| `kilacraft.personalities` | op | 管理人格配置 |

#### Bukkit API 技能权限（v1.3.3+）

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `kilacraft.api.player.inventory` | true | 查询玩家物品栏信息 |
| `kilacraft.api.player.status` | true | 查询玩家状态（生命值、经验等） |
| `kilacraft.api.player.info` | true | 查询玩家信息（位置、游戏模式等） |
| `kilacraft.api.world.info` | true | 查询世界信息（时间、天气等） |
| `kilacraft.api.server.info` | true | 查询服务器信息（在线玩家等） |
| `kilacraft.api.*` | true | 使用所有 Bukkit API 技能（通配符权限） |

**注意**：
- 基础对话功能无需任何权限，所有玩家默认可用
- Bukkit API 技能权限用于控制 AI 调用原版 API 的能力
- 可通过权限插件（如 LuckPerms）细粒度控制每个玩家/组的权限

## 📚 知识库功能

### 添加服务器知识

1. 在 `plugins/Kilacraft-AI/knowledge/` 目录下创建 `.md` 或 `.txt` 文件
2. 添加服务器相关的知识内容
3. 使用 `/kilacraft knowledge reload` 重新加载

### 智能分段规则（v1.3.1+）

插件采用**三级智能分段策略**，自动将知识库文件分割成适合检索的片段：

#### 1. Markdown 标题分割（最高优先级）
- **识别 `#`、`##`、`###` 等标题标记**
- **每个标题及其内容作为一个独立片段**
- **适用于**：规则列表、FAQ、分类指南等有明确结构的内容

**示例**：
```markdown
# 服务器规则
所有内容属于“服务器规则”片段

## 经济系统
所有内容属于“经济系统”片段

## 领地保护
所有内容属于“领地保护”片段
```

#### 2. 段落分割（中等优先级）
- **当段落超过设定大小时自动分割**
- **保持语义完整性**，按自然段落边界切分
- **适用于**：长段描述、详细说明等内容

#### 3. 固定大小分割（兜底策略）
- **最大片段大小**：默认 500 字符（可在 config.yml 中调整）
- **最小片段大小**：默认 25 字符（小于此值的片段会被忽略）
- **重叠区域**：默认 30 字符（保持上下文连贯性）

### 最佳实践

#### ✅ 推荐的知识库文件格式

**1. FAQ 问答式（最推荐）**
```markdown
# 常见问题解答

## 如何获得领地？
使用 /claim 命令来圈定你的领地。需要至少 10 个金币。

## 怎么赚钱？
可以通过以下方式赚钱：
- 挖矿出售矿物
- 钓鱼制作食物
- 在玩家商店出售物品
- 完成任务获得奖励
```

**2. 规则列表式**
```markdown
# 服务器规则

## 基本规则
1. 禁止作弊和使用外挂
2. 保持友好，禁止辱骂他人
3. 禁止破坏其他玩家的建筑

## 经济规则
4. 禁止使用刷钱漏洞
5. 交易需遵循市场规律
```

**3. 分类指南式**
```markdown
# 新手指南

## 第一步：熟悉环境
了解基本操作和界面

## 第二步：收集资源
采集木材、石头等基础材料

## 第三步：建立基地
选择合适的地点建造家园
```

#### ❌ 避免的格式

- **超大段落**：整段超过 2000 字符的连续文本
- **无结构内容**：没有任何标题或分段的大段文字
- **纯代码/命令列表**：缺少解释说明的命令罗列

### 配置选项

在 `config.yml` 中可以调整知识库分段参数：

```yaml
knowledge:
  segment:
    max_size: 500     # 每个片段最大字符数
    min_size: 25      # 每个片段最小字符数
    overlap: 30       # 片段重叠字符数
```

### 缓存机制

- **首次加载自动缓存**：文件修改后无需手动清除缓存
- **二次检索提速 ~70%**：已缓存的文件直接读取分段结果
- **热重载支持**：使用 `/kilacraft knowledge reload` 立即刷新缓存

**示例文件** (`server_rules.md`)：
```markdown
# 服务器规则

1. 禁止作弊和使用外挂
2. 保持友好，禁止辱骂他人
3. 禁止破坏其他玩家的建筑
4. 经济系统：使用 /money 查看余额

# 常见问题

Q: 如何获得领地？
A: 使用 /claim 命令来圈定你的领地。

Q: 怎么赚钱？
A: 可以通过挖矿、钓鱼或在玩家商店出售物品来赚钱。
```

## 🔌 开发者 API

### Skills 技能框架（v1.3.0+）

插件采用基于 LLM 意图识别的技能执行框架，支持自定义扩展。

#### 内置 Skills

- **Bukkit API 动态调用**：从 `apis.yml` 加载原版 API 定义，无需硬编码
- **GlobalMarketPlus 经济系统**：余额查询、市场价格、商品列表等

#### 第三方 Skill SPI（v1.4.0+）

从 v1.4.0 开始，支持第三方插件通过 SPI 机制注册自定义 Skill：

```java
// 1. 实现 Skill 接口
public class MyCustomSkill implements Skill {
    @Override
    public String getName() { return "my_skill"; }
    
    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        return CompletableFuture.completedFuture(SkillResult.success("执行成功！"));
    }
}

// 2. 在插件主类中注册
public class MyPlugin extends JavaPlugin implements SkillProvider {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(SkillProvider.class, this, this, ServicePriority.Normal);
    }
    
    @Override
    public List<Skill> getSkills() {
        return List.of(new MyCustomSkill());
    }
}
```

详细接入指南请参考 `Kilacraft-AI-Skill-SPI-接入文档.md`。

### 控制台命令调用

其他插件可以通过执行控制台命令来调用 AI：

```java
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
    String.format("kilacraft plugins %s %s %s", 
        personality, 
        message, 
        player.getUniqueId().toString()
    )
);
```

### MythicMobs 占位符

如果安装了 MythicMobs，可以使用 `%kilacraft_ai_answer%` 占位符获取 AI 最新回复。

## 🎯 第三方开发者最佳实践

> **适用对象**：需要与 Kilacraft-AI 集成的第三方插件开发者  
> **核心原则**：异步执行、快速返回、资源管理、超时保护

### 1. 异步处理（必须）

Kilacraft-AI 通过 `Bukkit.dispatchCommand()` 调用第三方插件的回调命令，该 API **必须在主线程执行**。如果你的插件在回调中执行耗时操作（如数据库查询、网络请求），会严重阻塞服务器 TPS。

**正确做法：**
```java
@Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (cmd.getName().equalsIgnoreCase("myplugin_handle_ai")) {
        // 立即启动异步任务
        new BukkitRunnable() {
            @Override
            public void run() {
                // 在异步线程中执行耗时操作
                // 完成后回到主线程发送消息
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(response);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        
        return true; // 主线程立即返回
    }
    return false;
}
```

### 2. 伪流式输出（可选）

如需流式效果，可使用 `BukkitRunnable` 定时任务分批显示：

```java
// 每 500ms 显示 10 个字符，模拟打字机效果
new BukkitRunnable() {
    int index = 0;
    StringBuilder accumulated = new StringBuilder();
    
    @Override
    public void run() {
        if (index >= message.length()) {
            this.cancel();
            return;
        }
        int end = Math.min(index + 10, message.length());
        accumulated.append(message.substring(index, end));
        player.sendMessage("§e[AI] §f" + accumulated.toString());
        index = end;
    }
}.runTaskTimer(plugin, 0L, 10L); // 10 ticks = 500ms
```

### 3. 超时保护理解

Kilacraft-AI 的超时保护机制只监控**主线程的命令执行时间**，不会中断第三方插件内部的异步任务。

- ✅ 超时只监控 `dispatchCommand()` 的主线程执行时间
- ✅ 如果你的插件使用异步处理，`onCommand()` 会立即返回（< 10ms）
- ✅ 异步任务在后台继续运行，不受超时限制
- ❌ 只有当你的插件**同步阻塞**主线程超过阈值时才会被中断

### 4. 资源管理

插件禁用时必须清理所有任务和资源：

```java
private final List<BukkitTask> activeTasks = new ArrayList<>();

@Override
public void onDisable() {
    activeTasks.forEach(task -> {
        if (!task.isCancelled()) task.cancel();
    });
    activeTasks.clear();
}
```

### 5. 性能指标参考

| 指标 | 推荐值 |
|------|--------|
| `onCommand()` 执行时间 | < 10ms |
| 异步任务超时设置 | 5-10s |
| 线程池大小 | 2-4 |
| 伪流式输出间隔 | 300-500ms |
| 伪流式输出块大小 | 8-12 字符 |

完整示例和详细说明请参考 [Skill-SPI-接入文档](./knowledge/Skill-SPI-接入文档.md)。

---

## ⚠️ 注意事项

1. **API 费用**：DeepSeek API 是付费服务，请注意控制调用频率
2. **冷却时间**：建议设置合理的冷却时间防止滥用
3. **世界限制**：可以在特定世界禁用 AI 功能
4. **内存占用**：历史对话会占用一定内存，建议定期清理离线玩家数据

## 🐛 故障排除

### AI 不回复
- 检查 API 密钥是否正确
- 检查网络连接
- 查看控制台是否有错误信息
- 确认未在被禁用的世界中

### 冷却时间过长
- 调整 `cooldown_seconds` 配置项
- 检查是否有多个冷却同时生效

### 连续对话模式无效
- 确认 `enable_chat_command: true`
- 检查玩家是否有相应权限

## 📝 更新日志

详细的版本更新记录请查看 [CHANGELOG.md](doc/Kilacraft-AI%20更新日志.md)

