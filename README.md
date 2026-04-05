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

### v1.4.0 - 第三方 Skill SPI 扩展机制 & 插件命令模式通用化 🚀

**核心升级**：支持第三方插件通过 SPI 机制注册自定义 Skill，同时解耦 AI 人格系统与 MythicMobs 的绑定关系，实现真正的通用化！

#### 🎯 核心特性

- ✅ **Skill SPI 接口**（新增）：
  - `SkillProvider` 接口：第三方插件实现此接口提供 Skill 列表
  - `SkillRegistry` 自动发现：基于 Bukkit ServicesManager 自动扫描注册
  - 零耦合接入：只需引入 `Kilacraft-Skill-API.jar` 作为 compileOnly 依赖

- ✅ **错误隔离机制**：
  - 第三方 Skill 执行异常不会影响核心流程
  - 自动捕获异常并返回友好错误信息
  - 详细的日志记录便于问题排查

- ✅ **API JAR 打包**：
  - 新增 `Kilacraft-Skill-API-1.4.0.jar`（仅 5KB）
  - 包含 5 个 SPI 接口：Skill, SkillContext, SkillResult, SkillIntent, SkillProvider
  - 第三方开发者无需依赖完整插件

- ✅ **SkillContext 简化**：
  - 移除冗余的 `rawInput` 字段
  - 保留核心字段：player, action, entities
  - 更清晰的接口设计

- ✅ **插件命令模式通用化**（新增）：
  - **控制台命令 + 回调命令机制**（✅ 唯一推荐）：适配配置驱动型插件（如 MythicMobs）
    - `/kilacraft plugins <人格> <内容> <玩家UUID> [回调命令...]` - 请求 AI 回复并指定回调
    - **回调命令支持空格**：第 5 个及之后的所有参数自动合并为回调命令
    - 回调命令支持 `{response}` 占位符，AI 完成后自动执行
    - **执行角色**：回调命令以**控制台身份**执行，不是玩家角色
    - **一次性消费**：回调执行后立即删除缓存，避免数据污染
    - **设计理念**：插件命令模式是给第三方插件使用的，不是给玩家直接使用的
  - **上下文隔离**：不同人格和玩家的对话历史完全独立（格式：UUID_人格）
  - **解耦 MythicMobs**：任何第三方插件都能方便地使用 AI 人格系统
  - **回调命令机制**：
    - **优先级（唯一方式）**：指定回调命令 → 执行回调 → 删除缓存
    - ⚠️ **重要原则**：
      - 遵循**“使用一次，立即删除”**原则
      - **人格命名唯一性**：每个插件/模块必须使用独立的人格名称，避免缓存冲突
      - 缓存隔离机制：`UUID_人格` 作为缓存 key，不同人格天然隔离
      - **禁止重名**：多个插件使用同一个人格会导致缓存被意外删除
  - **DEBUG 日志增强**：关键步骤添加调试日志，便于问题排查

#### 📦 新增文件

- `src/main/java/com/zm/kilacraftAI/skills/framework/spi/SkillProvider.java` - SPI 接口
- `src/main/java/com/zm/kilacraftAI/skills/framework/spi/SkillRegistry.java` - 自动发现机制
- `src/main/java/com/zm/kilacraftAI/api/event/AIResponseReadyEvent.java` - AI 回复就绪事件
- `src/assembly/skill-api.xml` - Assembly 打包配置
- `knowledge/Skill-SPI-接入文档.md` - 完整的 SPI 接入文档

#### 📦 修改文件

- `src/main/java/com/zm/kilacraftAI/KilacraftAI.java` - 集成 SkillRegistry + 注册 Plugin Message 通道
- `src/main/java/com/zm/kilacraftAI/core/KilacraftCommand.java` - 移除 Bukkit Event + 新增回调命令支持 + Plugin Message 发送 + DEBUG 日志增强
- `src/main/java/com/zm/kilacraftAI/skills/framework/SkillManager.java` - 增强错误隔离
- `src/main/java/com/zm/kilacraftAI/skills/framework/SkillContext.java` - 简化字段
- `src/main/java/com/zm/kilacraftAI/skills/framework/SkillResult.java` - 新增 getDataMap() 方法
- `src/main/java/com/zm/kilacraftAI/manager/ConversationManager.java` - 优化缓存管理（一次性消费）
- `pom.xml` - 版本号更新 + Assembly 插件配置

#### ⚙️ 接入示例

**⚠️ 两种方案互斥与优先级说明**：
- **优先级 1（最高）**：指定回调命令 → 执行回调 → 删除缓存
- **优先级 2**：无回调命令 → 触发 Bukkit Event → 删除缓存
- **优先级 3（最低）**：手动轮询 `plugins get` → 获取并删除缓存
- **重要**：高优先级方式执行后，低优先级方式将无法获取缓存

**方式 A：Bukkit Event 事件通知（✅ 推荐）**
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
        UUID playerId = (UUID) eventClass.getMethod("getPlayerId").invoke(event);
        
        // 处理 AI 回复
        getLogger().info("收到 AI 回复 - 玩家: " + playerName + ", 人格: " + personality);
        getLogger().info("回复内容: " + response);
        
        // 执行你的逻辑...
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

**方式 B：控制台命令 + 回调命令（✅ 强烈推荐）**
```bash
# 1. 请求 AI 回复并指定回调命令
/kilacraft plugins 严厉教师 你好 00000000-0000-0000-0000-000000000000 "myplugin handleAI {response}"

# 2. AI 完成后自动执行：myplugin handleAI <实际回复内容>

# 3. 或者使用轮询方式
/kilacraft plugins get 严厉教师 00000000-0000-0000-0000-000000000000
# 返回 UNDEFINED 表示未完成，返回实际内容表示完成
```

**📌 完整使用示例**：
```bash
# 场景：第三方插件请求 AI 回复并指定回调
/kilacraft plugins mm_ai 你好 UUID testai handleAI {response} mm_ai
# → AI 完成后执行回调命令（以控制台身份）
# → 缓存已删除
```

**⚠️ 重要说明**：
- **回调命令执行角色**：所有回调命令均以**控制台身份**执行，而非玩家角色
- **回调命令格式**：支持包含空格，第 5 个及之后的所有参数自动合并
- **占位符替换**：`{response}` 会被替换为实际的 AI 回复内容
- **设计理念**：插件命令模式是给第三方插件使用的，不是给玩家直接使用的
- **唯一集成方式**：只支持回调命令方式，不支持轮询

**⚠️ 人格命名唯一性原则**：
```bash
# ✅ 正确：不同插件使用不同人格
MythicMobs 使用: mm_ai
第三方插件 A 使用: shop_assistant
第三方插件 B 使用: quest_guide
# → 缓存完全隔离，互不干扰

# ❌ 错误：多个插件使用同一个人格
MythicMobs 使用: default
第三方插件 A 也使用: default
# → 会导致缓存冲突，先执行的删除后执行的拿不到数据
```

**方式 C：MythicMobs 占位符**
```yaml
# MythicMobs 技能配置
- command{c="kilacraft plugins 严厉教师 {player.name} {player.uuid}"} @PIR{r=5}
- placeholder{p=ai.answer} @PIR{r=5}
```

**方式 D：Skill SPI 注册**
```java
// 1. 添加依赖
<dependency>
    <groupId>com.zm</groupId>
    <artifactId>Kilacraft-Skill-API</artifactId>
    <version>1.4.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/Kilacraft-Skill-API.jar</systemPath>
</dependency>

// 2. 实现 Skill 接口
public class MyCustomSkill implements Skill {
    @Override
    public String getName() { return "my_skill"; }
    
    @Override
    public String getDescription() { return "我的自定义技能"; }
    
    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        return CompletableFuture.completedFuture(SkillResult.success("执行成功！"));
    }
}

// 3. 在插件主类中注册
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

#### ⚠️ 兼容性说明

- 现有功能完全兼容，无需修改配置
- 第三方 Skill 需要在 plugin.yml 中声明 `softdepend: [Kilacraft-AI]`
- 内置 Skill 优先级高于第三方 Skill（同名时第三方 Skill 被跳过）
- AI 回复缓存改为一次性消费机制（获取即删除），与 MythicMobs 占位符行为一致

---

### v1.3.6 - 通用 LLM Provider 架构 🚀

**核心升级**：引入通用 LLM Provider 架构，支持通过配置切换不同 LLM 厂商（DeepSeek、智谱 AI 等）！

#### 🎯 核心特性

- ✅ **LLMProvider 接口**（新增）：
  - 统一的 LLM 提供商标准接口
  - 定义核心方法：`processRequest`、`refreshConfigCache`、`shutdown`
  - 支持异步非阻塞请求处理
  - 为未来扩展更多 LLM 厂商奠定基础

- ✅ **GenericLLMProvider 实现**（新增）：
  - 通用 LLM 提供商实现，支持所有遵循 OpenAI 标准 API 格式的厂商
  - 配置驱动，通过 config.yml 即可切换不同 LLM 服务
  - HTTP 连接池优化，复用连接提升性能
  - 流式响应支持，降低首字延迟
  - 自动重试机制，增强稳定性

- ✅ **LLMManager 管理器**（新增）：
  - 统一管理 LLM Provider 的生命周期
  - 支持配置热重载，无需重启插件
  - 简洁的 API 封装，便于其他模块调用

- ✅ **架构优势**：
  - **解耦设计**：LLM 实现与业务逻辑完全分离
  - **可扩展性**：未来可轻松添加新的 Provider 实现（如 ClaudeProvider、GeminiProvider）
  - **配置灵活**：只需修改配置即可切换 LLM 服务商
  - **向后兼容**：现有功能完全兼容，不影响已有配置

#### 🔧 技术实现

- ✅ **代码架构重构**：
  - 从硬编码的 DeepSeekAPI 迁移到通用 Provider 架构
  - 删除旧的 `DeepSeekAPI.java` 和 `DeepSeekAPINew.java`
  - 新增 `api/LLMProvider.java` 接口
  - 新增 `api/provider/GenericLLMProvider.java` 实现
  - 新增 `manager/LLMManager.java` 管理器

- ✅ **HTTP 客户端优化**：
  - 预分配连接池（最大空闲连接数=10，保持时间=5 分钟）
  - 超时配置优化（连接=30s, 读取=60s, 写入=30s）
  - 自动重试失败连接
  - 流式读取使用 BufferedReader 逐行处理

- ✅ **配置缓存机制**：
  - 缓存 API Key、URL、Model 等配置值
  - 减少重复获取配置的开销
  - 支持动态刷新配置缓存

#### 📦 修改文件

- `src/main/java/com/zm/kilacraftAI/api/LLMProvider.java` - 新增通用接口
- `src/main/java/com/zm/kilacraftAI/api/provider/GenericLLMProvider.java` - 新增通用实现
- `src/main/java/com/zm/kilacraftAI/manager/LLMManager.java` - 新增管理器
- ~~`src/main/java/com/zm/kilacraftAI/api/DeepSeekAPI.java`~~ - 删除旧实现
- ~~`src/main/java/com/zm/kilacraftAI/api/DeepSeekAPINew.java`~~ - 删除旧实现
- `src/main/resources/plugin.yml` - 移除废弃的 `/llm` 命令及权限

#### ⚙️ 影响范围

- **配置变更**：config.yml 中的 `api.*` 配置项现在由 GenericLLMProvider 统一管理
- **性能提升**：HTTP 连接池优化减少连接建立开销
- **维护性提升**：清晰的职责划分，代码更易维护和扩展
- **兼容性**：现有功能完全兼容，无需修改配置

---

### v1.3.5 - 历史对话上下文增强 🚀

**核心升级**：LLM 意图识别和结果分析阶段全面支持历史对话上下文，提升连续对话理解能力！

#### 🎯 核心特性

- ✅ **历史对话上下文配置**：
  - **intent_history_count**：意图识别阶段的历史对话轮数（默认 5 轮）
    - 帮助 LLM 理解"再查一下那个物品"等指代性表达
    - 值越大上下文越丰富，但 token 消耗也越高
    - 建议值：3-5 轮
  - **analysis_history_count**：结果分析阶段的历史对话轮数（默认 2 轮）
    - 让 LLM 在分析技能执行结果时参考对话历史
    - 使最终回复更自然、能关联上下文
    - 建议值：1-2 轮，精简历史即可满足大部分场景

- ✅ **HistoryUtil 工具类**：
  - 统一的历史记录格式化逻辑
  - 支持配置化的轮数转换
  - 自动截取最近 N 轮对话
  - 清晰的对话历史展示格式

- ✅ **LLMAnalysisService 优化**：
  - 集成历史对话到分析提示词
  - 动态构建包含历史的完整上下文
  - 优化提示词模板结构

- ✅ **SkillIntentRecognizer 优化**：
  - 隐藏动态构建系统提示词的调试日志
  - 保持控制台输出清爽

- ✅ **ConfigManager 增强**：
  - 新增 `getAgentIntentHistoryCount()` 方法
  - 新增 `getAgentAnalysisHistoryCount()` 方法
  - 配置文件自动支持新参数

#### 🔧 技术实现

- ✅ **代码架构重构**：
  - 提取历史对话格式化逻辑到 `HistoryUtil` 工具类
  - `LLMAnalysisService` 简化历史处理逻辑
  - `AIRequestHandler` 优化响应摘要格式
  - `SkillIntentRecognizer` 减少冗余日志

- ✅ **提示词优化**：
  - `analysis_prompt` 模板调整，整合历史对话和当前输入
  - 更自然的对话流程，LLM 能理解上下文关联
  - 配置文件中明确历史记录的作用和使用场景

#### 📦 修改文件

- `src/main/java/com/zm/kilacraftAI/config/ConfigManager.java` - 新增历史记录配置方法
- `src/main/java/com/zm/kilacraftAI/handler/AIRequestHandler.java` - 优化响应摘要格式
- `src/main/java/com/zm/kilacraftAI/skills/framework/SkillIntentRecognizer.java` - 隐藏调试日志
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/LLMAnalysisService.java` - 集成历史对话
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/TaskExecutor.java` - 代码优化
- `src/main/java/com/zm/kilacraftAI/util/HistoryUtil.java` - 新增工具类
- `src/main/resources/config.yml` - 新增历史记录配置项
- `src/main/resources/plugin.yml` - 版本号更新
- `pom.xml` - 版本号更新

#### ⚙️ 配置示例

```yaml
agent:
  enabled: true
  enable_chat_listener: true
  enable_command: true
  
  # 历史对话上下文配置
  intent_history_count: 5      # 意图识别时使用 5 轮历史
  analysis_history_count: 2    # 结果分析时使用 2 轮历史
  
  prompts:
    system_prompt: "你是一个专业的 Minecraft 游戏助手..."
    analysis_prompt: "{results}\n请根据以上对话历史、当前输入、执行结果，给出综合性的分析和建议，请用简洁友好的语言回复玩家。"
```

#### 🎮 使用效果

**无历史对话时**：
```
玩家：查询钻石价格
AI: 钻石价格为$100

玩家：再查一下那个物品
AI: ❌ 无法理解"那个物品"指什么
```

**有历史对话时**（intent_history_count=5）：
```
玩家：查询钻石价格
AI: 钻石价格为$100

玩家：再查一下那个物品
AI: ✅ 您是指钻石吗？当前价格为$100
```

#### ⚠️ 兼容性说明

- 配置文件新增 `intent_history_count` 和 `analysis_history_count` 项
- 建议备份后重新生成配置文件
- 现有功能完全兼容，新增配置为可选

---

### v1.3.4 - MarketQuerySkill 能力扩展与多步骤任务优化 🚀

**重大升级**：经济系统技能全面扩展，多步骤任务意图识别与数据传递机制优化！

#### 🎯 核心特性

- ✅ **MarketQuerySkill 新增 4 个只读动作**：
  - **query_availability**：查询指定物品是否在售、库存数量、卖家信息
    - 支持精确匹配和模糊匹配
    - 卖家名称自动去重（同一玩家多件商品只显示一次）
    - 无结果时智能净化物品名称并翻译为中文
  - **query_my_items**：查询玩家自己在售的商品列表
  - **query_mailbox**：查询玩家邮箱待领取的邮件（发件人、数量、时间）
  - **query_market_stats**：查询市场统计信息（总商品数、总卖家数）

- ✅ **GlobalMarketPlusAPI 扩展**：
  - 新增 `MailItem` 类：封装邮件信息（物品名、数量、发件人、发送时间）
  - 新增 `MarketStats` 类：封装市场统计（总商品数、总卖家数）
  - 新增 `getMyMerchandises()` 方法：获取玩家自己的在售商品
  - 新增 `getMailboxItems()` 方法：获取玩家邮箱待领取邮件
  - 新增 `getMarketStats()` 方法：获取市场统计信息
  - 新增 `MarketItem` 和 `MarketItemDetail` 类：封装商品信息

- ✅ **多步骤任务提示词优化**：
  - 添加单意图格式示例，帮助 LLM 正确区分单意图和多步骤任务
  - 优化多步骤任务示例，使用完整 JSON 格式展示
  - 添加占位符语法说明 `{step_xxx.field}` 用于步骤间数据传递
  - 提高意图识别准确率，减少误判

- ✅ **多步骤任务数据传递机制**：
  - TaskExecutor 新增 `resolvePlaceholders()` 方法
  - 支持 `{step_xxx.field}` 占位符解析
  - 前置步骤结果自动注入后续步骤的 entities 中
  - GenericBukkitAPISkill 对 ItemStack 结果添加 `item_name` 和 `item_amount` 字段

- ✅ **Bukkit API 能力扩展**：
  - 新增 **44 个 API** 定义，覆盖 Player/World/Server 全部核心查询能力
  - **Player API（29 个）**：物品栏（主手/副手）、状态（生命/饥饿/氧气/经验/睡眠/攻击冷却/着火/冰冻/挂机）、信息（位置/飞行/游戏模式/Ping/客户端/载具/死亡点/姿势）
  - **World API（9 个）**：时间、天气、世界信息、种子、出生点、高度限制、生物生成规则、PVP 设置、视距
  - **Server API（6 个）**：在线玩家、最大玩家数、版本、MOTD、世界列表、服务器设置、平均 Tick 时间
  - **Paper 特有 API**：客户端品牌、客户端视距、挂机时间、累计总经验、服务器平均 Tick 时间
  - **权限系统完善**：5 类权限节点全部配置 `required_permission`，支持粗粒度权限分组

- ✅ **显示格式优化**：
  - **物品名称净化**：移除 `:1` 等后缀，自动翻译为中文
  - **经验进度百分比**：`0.47826084` → `47%`
  - **游戏时间格式化**：刻数自动转换为 `HH:MM` 格式（MC 时间系统）
  - **在线玩家精简**：移除 UUID，聚合格式显示

#### 🔧 技术实现

- ✅ **意图识别逻辑修复**：
  - AIRequestHandler 支持识别 TaskPlan 格式（无论单步骤还是多步骤）
  - 调试日志显示步骤数量，便于问题排查
  - 新增多步骤任务识别 hints，引导 LLM 正确识别多意图场景

- ✅ **BukkitAPI 执行器优化**：
  - `findMethod()` 优先选择无参方法，修复 `getLocation()` 等带重载的方法调用失败
  - 解决方法签名冲突问题（如 `getLocation(Location)` vs `getLocation()`）

- ✅ **占位符解析容错增强**：
  - 实现「快速失败」策略：占位符解析失败时终止执行
  - 新增 `PlaceholderResolveResult` 和 `BuildContextResult` 类封装解析结果
  - 明确的错误提示，便于问题排查

- ✅ **代码架构重构**：
  - 提取内部类为独立文件，提高代码可维护性
  - 新增 `compat/globalmarketplus/model/` 子包存放数据模型类
  - 新增 `skills/framework/task/` 包下的 `TaskStep`、`PlaceholderResolveResult`、`BuildContextResult` 类
  - 删除未使用的反射方法 `getPropertyValue()`
  - 优化代码格式和缩进

#### 📦 修改文件

- `src/main/java/com/zm/kilacraftAI/compat/globalmarketplus/GlobalMarketPlusAPI.java`
- `src/main/java/com/zm/kilacraftAI/compat/globalmarketplus/model/MarketItem.java` （新增）
- `src/main/java/com/zm/kilacraftAI/compat/globalmarketplus/model/MarketItemDetail.java` （新增）
- `src/main/java/com/zm/kilacraftAI/compat/globalmarketplus/model/MailItem.java` （新增）
- `src/main/java/com/zm/kilacraftAI/compat/globalmarketplus/model/MarketStats.java` （新增）
- `src/main/java/com/zm/kilacraftAI/skills/globalmarketplus/MarketQuerySkill.java`
- `src/main/java/com/zm/kilacraftAI/skills/framework/SkillIntentRecognizer.java`
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/TaskExecutor.java`
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/TaskPlan.java`
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/TaskStep.java` （新增）
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/PlaceholderResolveResult.java` （新增）
- `src/main/java/com/zm/kilacraftAI/skills/framework/task/BuildContextResult.java` （新增）
- `src/main/java/com/zm/kilacraftAI/skills/bukkit/GenericBukkitAPISkill.java`
- `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIExecutor.java` （findMethod 优化）
- `src/main/java/com/zm/kilacraftAI/handler/AIRequestHandler.java`
- `src/main/resources/skills/globalmarketplus/MarketQuerySkill.yml`
- `src/main/resources/skills/bukkit/apis.yml` （v1.3.4 扩展：44 个 API + 权限配置 + 显示格式优化）
- `src/main/resources/plugin.yml` （v1.3.4 权限描述优化）
- `README.md` / `README.en.md` （双语文档同步更新）

#### 🎮 使用示例

```
玩家：附魔瓶有人卖吗？
AI：附魔瓶 有在售
    库存：15 个
    价格：$100.00 - $150.00
    卖家：Steve, Alex, ... 等5人

玩家：我在卖什么？
AI：你在售的商品 (共 3 个):
    1. 钻石 x10 - $50.00/个
    2. ...

玩家：我有邮件吗？
AI：你的邮箱有 2 封待领取邮件：
    1. 钻石 x5 - 来自 Steve
    2. ...

玩家：市场有多少商品？
AI：市场统计：
    总商品数：1234
    总卖家数：56
```

---

### v1.3.3 - Bukkit API 能力与系统提示词自动化 🚀

**重大升级**：新增原版 Bukkit API 动态调用能力，系统提示词完全自动化构建！

#### 🎯 核心特性

- ✅ **Bukkit API 技能系统（GenericBukkitAPI）**：基于数据驱动的原版 API 调用框架
  - **动态元数据驱动**：从 `apis.yml` 配置文件加载 API 定义，无需硬编码
  - **反射执行引擎**：使用反射动态调用 Bukkit API（Player/World/Server）
  - **双模式支持**：
    - **method_chain**：链式调用（接力），返回复杂对象（如 ItemStack、Location）
    - **additional_methods**：并行调用多个独立方法，获取简单值（如 health/maxHealth）
  - **智能格式化**：支持模板占位符替换、特殊类型处理（Location/GameMode/ItemStack）
  - **权限控制**：每个 API 可配置独立权限节点
  - **热重载支持**：修改 `apis.yml` 后使用 `/kilacraft reload` 立即生效

- ✅ **系统提示词完全自动化**：零硬编码的动态提示词构建
  - **自动遍历所有技能**：包括传统技能和 Bukkit API 技能
  - **动态生成动作列表**：自动列出每个技能的所有可用动作
  - **自动添加提示信息**：技能的 hints 自动整合到系统提示词
  - **多步骤任务示例**：自动生成贴近实际的示例场景
  - **维护成本降低 ~90%**：新增技能无需修改提示词代码

#### 🔧 技术实现

- ✅ **新增文件**：
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIExecutor.java` - API 执行引擎
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIMetadata.java` - API 元数据封装
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/BukkitAPIConfigLoader.java` - 配置加载器
  - `src/main/java/com/zm/kilacraftAI/skills/bukkit/GenericBukkitAPISkill.java` - 通用 API 技能实现
  - `src/main/resources/skills/bukkit/apis.yml` - Bukkit API 元数据配置文件

- ✅ **架构重构**：
  - `SkillConfig.java` 移动到 `skills.framework.config` 包
  - `TaskExecutor.java` 移动到 `skills.framework.task` 包
  - `TaskPlan.java` 移动到 `skills.framework.task` 包
  - `Skill` 接口新增 `isAvailable()` 默认方法

- ✅ **配置管理增强**：
  - `SkillConfigManager` 新增 `loadBukkitAPIs()` 方法
  - `SkillConfigManager` 新增 `reloadAllConfigs()` 热重载方法
  - `MarketQuerySkill` 修复热重载失效问题（从固定引用改为动态获取）

- ✅ **权限系统扩展**：
  - 新增 5 个 Bukkit API 权限节点（玩家/世界/服务器查询）
  - 新增通配符权限 `kilacraft.api.*`

- ✅ **人格配置优化**：
  - **YAML 多行文本支持**：使用 `|` 或 `>` 编写复杂的人格描述
  - **JSON 格式容错**：自动修复常见的 JSON 格式错误
  - **空配置检测**：配置文件为空时自动创建示例文件
  - **错误恢复机制**：加载失败时自动生成默认配置
  - **公共提示词功能**：支持所有人格共享的基础提示词

- ✅ **控制台 AI 能力增强**：
  - **重构控制台命令调用逻辑**：`handleConsoleMessageCommand()` 方法完全重写
  - **统一 AI 请求处理**：控制台现在支持与玩家相同的完整功能（意图识别、技能调用、多步骤任务）
  - **无冷却和世界限制**：控制台调用不受冷却时间和世界限制约束
  - **固定 UUID 标识**：使用 `00000000-0000-0000-0000-000000000000` 作为控制台的唯一标识
  - **独立历史记录**：控制台拥有独立的对话历史记录，与玩家隔离
  - **简化响应处理器**：`ConsoleResponseHandler` 构造函数优化，移除不必要的参数

- ✅ **代码质量提升**：
  - 优化日志输出，减少冗余信息
  - 改进异常处理机制
  - 优化配置加载流程

#### 📦 预置 Bukkit API 示例

**玩家相关**：
- `get_player_hand_item` - 获取主手物品（链式调用：getInventory → getItemInMainHand）
- `get_player_health` - 获取生命值（并行调用：getHealth + getMaxHealth）
- `get_player_location` - 获取位置坐标（支持 getLocation.getX 等链式调用）
- `get_player_game_mode` - 获取游戏模式
- `get_player_level` - 获取等级和经验

**世界相关**：
- `get_world_time` - 获取世界时间
- `get_weather` - 获取天气状况（hasStorm + isThundering）

**服务器相关**：
- `get_server_online_players` - 获取在线玩家数量

#### ⚙️ 配置示例

```yaml
# apis.yml 配置示例
player:
  get_player_health:
    id: "get_player_health"
    display_name: "获取玩家生命值"
    description: "获取玩家当前的生命值和最大生命值"
    usage_scenarios:
      - "我还有多少血"
      - "我的生命值"
    target_type: "Player"
    additional_methods:
      health: "getHealth"
      max_health: "getMaxHealth"
    result_template: "生命值：{health}/{max_health}"
```

#### ⚠️ 兼容性说明

- 新增 `apis.yml` 配置文件，首次启动会自动创建
- 新增权限节点，建议更新权限配置
- 包结构调整不影响现有技能实现

---

### v1.3.2 - Agent 能力配置化与多步骤任务执行器 🚀

**重大升级**：Agent 能力全面配置化，新增多步骤任务规划与执行能力！

#### 🎯 核心特性

- ✅ **Agent 能力细粒度配置**：完全由配置文件控制的 Agent 能力开关
  - **总开关控制**：`agent.enabled` 优先级高于所有分开关
  - **入口独立控制**：`enable_chat_listener`（关键词触发/连续对话）和 `enable_command`（/kilacraft 命令）
  - **灵活回退机制**：关闭 Agent 后直接进入普通 AI 对话，无需意图分析
  - **配置驱动行为**：调用方根据配置决定是否启用 Agent，传递状态而非缓存状态

- ✅ **多步骤任务执行器（TaskExecutor）**：复杂的任务规划与自动执行
  - **拓扑排序算法**：基于 DFS 的依赖关系检测，自动识别循环依赖
  - **步骤依赖管理**：前置步骤结果自动传递给后续步骤作为上下文
  - **结果汇总分析**：所有步骤执行完成后，LLM 综合分析并生成友好回复
  - **调试日志优化**：详细的执行过程追踪，便于问题排查

- ✅ **LLM 意图识别增强**：支持复杂任务的自动分解
  - **单意图快速路径**：简单任务直接执行，零额外开销
  - **多步骤任务规划**：复杂任务自动分解为多个有序步骤
  - **JSON Schema 验证**：严格的意图格式校验，确保解析可靠性
  - **失败回退机制**：意图识别失败或技能执行失败时自动回退到普通 AI

#### 🔧 技术优化

- ✅ **统一 AI 请求处理器（AIRequestHandler）**：
  - 消除 ChatListener 和 KilacraftCommand 中的重复逻辑（约 130 行代码）
  - 统一的意图识别 + 技能执行流程
  - 基于配置的状态传递设计，无内部状态缓存
  - 支持动态启用/禁用 Agent 能力

- ✅ **提示词配置化**：
  - **system_prompt**：定义 LLM 在结果分析阶段的角色（默认："你是一个专业的游戏助手..."
  - **analysis_prompt**：指导 LLM 如何分析执行结果并生成回复（支持 `{results}` 占位符）
  - 完全可自定义的提示词配置，无需修改代码
  - 占位符自动替换机制（`{player}`, `{results}`）

- ✅ **架构重构**：
  - 移除 TaskExecutor 中的单步骤处理冗余逻辑
  - 删除 SkillIntentRecognizer 中的旧兼容方法
  - ChatListener 完整支持多步骤任务处理
  - 职责分离：调用方负责配置判断，AIRequestHandler 负责执行

- ✅ **消息格式优化**：
  - 所有 AI 回复自动添加前缀（`MessageUtil.getAIPrefix()`）
  - 统一的视觉体验，符合 language.yml 配置
  - 调试模式日志优化，使用 logger.info 替代 System.out.println

#### 📦 新增文件

- `src/main/java/com/zm/kilacraftAI/handler/AIRequestHandler.java` - 统一 AI 请求处理器
- `src/main/java/com/zm/kilacraftAI/skills/framework/TaskExecutor.java` - 多步骤任务执行器

#### ⚙️ 配置变更

```yaml
# config.yml 新增 Agent 能力配置
agent:
  enabled: true                    # 总开关（优先级最高）
  enable_chat_listener: true       # ChatListener 入口是否启用 Agent
  enable_command: true             # KilacraftCommand 入口是否启用 Agent
  prompts:
    system_prompt: "你是一个专业的游戏助手..."  # 结果分析的系统提示词
    analysis_prompt: "请根据以下任务执行结果...\n\n{results}\n\n请用简洁友好的语言回复玩家。"
```

#### ⚠️ 兼容性说明

- Agent 能力配置结构变更，建议备份后重新生成配置文件
- TaskExecutor 提示词配置化，原有的硬编码提示词已迁移到 config.yml
- AIRequestHandler 位置调整到 `handler` 包（非 `handler.impl` 子包）

---

### v1.3.1 - RAG 检索优化与响应速度提升 🚀

**核心升级**：重构知识检索架构，优化中文分词，提升插件响应速度！

#### 🎯 知识检索优化

- ✅ **标准 RAG 方案**：业界通用的知识检索架构，支持多格式知识库文件
- ✅ **智能分段策略**：Markdown 标题分割 → 段落分割 → 固定大小分割
- ✅ **中文关键词提取优化**：n-gram 分词 + 智能停用词过滤 + 标点自动移除
- ✅ **多级评分机制**：完整问题匹配 (+50) + 关键词匹配 (+5) + 标题匹配 (+25) + 覆盖率加成
- ✅ **缓存优化**：首次分段后缓存，二次检索速度提升 ~70%

#### ⚡ 响应速度优化

- ✅ **Thinking 消息即时发送**：从技能执行内部提前到命令入口，消除"插件反应慢"的误解
- ✅ **冷却时间统一管理**：统一在 `handlePlayerMessageCommand()` 中处理，避免重复启动冷却
- ✅ **代码架构优化**：职责分离，所有分支复用统一的入口处理

#### 🔧 技术细节

- ✅ **知识库检索器重构**：重新实现标准的 RAG 检索流程
- ✅ **配置系统完善**：知识库分段配置化 (`knowledge.segment`) + 人格配置 YAML 多行文本支持
- ✅ **调试日志优化**：输出详细的分词过程和匹配详情

#### ⚙️ API 性能优化（DeepSeekAPINew）

- ✅ **HTTP 连接池优化**：复用连接，最大空闲连接数=10，保持时间=5 分钟
- ✅ **超时配置优化**：连接=30s, 读取=60s, 写入=30s
- ✅ **流式读取响应**：降低首字延迟，使用 BufferedReader 逐行读取
- ✅ **配置缓存**：缓存 model/temperature/maxTokens 等配置值，减少重复获取
- ✅ **预分配缓冲区**：StringBuilder 预分配容量 (512/256)，减少扩容开销
- ✅ **自动重试机制**：启用 retryOnConnectionFailure(true)

#### ⚠️ 兼容性说明

- 知识库分段配置结构变更，建议备份后重新生成
- 停词表可能需要根据实际使用场景调整

---

### v1.3.0 - AI Agent 进化版 🚀

**重大升级**：从对话式 AI 进化为 AI Agent，具备技能执行和意图识别能力！

#### 🎯 核心特性

- ✅ **Skills 技能系统框架**：全新的可扩展 AI 技能执行框架
  - 基于 LLM 意图识别的自动技能路由
  - 异步执行模型，不阻塞主线程
  - 易于扩展的技能接口设计
  - 只读操作优先，保证安全性
  
- ✅ **LLM 意图识别引擎**：智能理解用户真实意图
  - 动态构建技能提示词
  - 支持多实体提取（物品、数量等）
  - 置信度评估和推理说明
  - 失败回退机制，保证用户体验

- ✅ **GlobalMarketPlus 深度集成**（实验性）：经济系统技能
  - 玩家余额查询（支持多货币）
  - 市场价格查询（智能精确匹配）
  - 商品列表查询
  - **多物品联合查询**：一次查询多个商品价格
  - **数量识别**：支持"买 5 个木棍"的自然语言理解
  - **最优价格计算**：从便宜到贵智能组合，考虑实际库存
  - **库存不足提示**：显示所有在售商品的详细价格和数量

#### 🔧 技术优化

- ✅ **核心架构重构**：
  - 新增 `SkillContext` 执行上下文
  - 新增 `SkillResult` 结果封装
  - 新增 `SkillManager` 技能管理器
  - 新增 `Skill` 基础接口
  - 新增 `SkillConfig` 配置封装
  
- ✅ **意图识别系统**：
  - 新增 `SkillIntentRecognizer` 意图识别器
  - 新增 `SkillIntent` 意图封装
  - 动态 JSON Schema 生成
  - 支持技能描述配置化

- ✅ **防重复机制**：
  - 修复技能回退时的重复 thinking message 问题
  - 修复技能回退时的重复 cooldown 问题
  - 统一命令处理器和聊天监听器的逻辑一致性

- ✅ **物品名称智能匹配**：
  - 优先精确匹配（搜索"钻石"排除"钻石剑"）
  - 降级模糊匹配（搜索"钻石剑"时可用）
  - 支持中英文翻译映射

- ✅ **配置系统完善**：
  - 技能配置独立管理（`skills/` 目录）
  - 物品翻译配置（`translate/items_CN.yml`）
  - 支持热重载技能配置
  - YAML 配置保持键值顺序

#### 📦 新增文件

- `src/main/java/com/zm/kilacraftAI/skills/framework/` - 技能框架核心
- `src/main/java/com/zm/kilacraftAI/skills/globalmarketplus/` - GlobalMarketPlus 技能实现
- `src/main/java/com/zm/kilacraftAI/skills/config/` - 技能配置管理
- `src/main/resources/skills/` - 技能配置文件
- `src/main/resources/translate/` - 物品翻译配置

#### ⚠️ 兼容性说明

- 配置结构变更，建议备份后重新生成配置文件
- 技能系统处于实验阶段，API 可能在未来版本调整
- GlobalMarketPlus 集成需要插件版本 1.3.8.0+

---

### v1.2.3
- ✅ **新增语言配置系统**：将所有系统提示文本提取到 `language.yml` 配置文件
  - 支持自定义所有命令帮助、权限提示、功能状态等消息
  - 支持颜色代码和变量占位符（`{player}`, `{sender}`）
  - `/kilacraft reload` 命令现在会同时重载主配置和语言配置
  - 服务器管理员可以完全自定义 AI 插件的所有系统提示
- ✅ **动态帮助消息**：help 命令根据玩家权限动态展示对应的提示内容
- ✅ **架构优化**：新增 `LanguageManager` 统一管理所有语言配置
- ✅ **权限管理优化**：创建 `PluginPermission` 枚举类，统一管理所有权限节点
  - 移除所有硬编码的权限字符串
  - 所有权限检查使用枚举类 `PluginPermission.XXX.hasPermission(sender)`
  - Tab 补全也基于权限枚举动态显示
- ✅ **提示文本优化**：整合重复的提示文本，提高复用性
  - 统一错误消息格式
  - 优化连续对话模式禁用提示
- ✅ **校验逻辑重构**：将工具类的校验与提示分离，遵循单一职责原则
  - `AIRequestValidator` 只负责校验，不再直接发送提示
  - 冷却时间提示：调用方根据校验结果自行处理
  - 世界限制提示：调用方根据校验结果自行处理
  - 所有提示文本从 `language.yml` 读取，支持占位符

### v1.2.2
- ✅ 移除 `kilacraft.use` 权限要求，所有玩家默认可用
- ✅ 优化 Tab 补全，根据权限动态显示命令
- ✅ 分离清除历史提示，按权限显示不同命令
- ✅ 完善权限系统和命令处理

### v1.2.1
- ✅ 新增 AI 最新回复缓存机制，优化自定义占位符解析性能
- ✅ 完善命令帮助提示、权限检查和日志输出
- ✅ 移除知识库增强中的来源声明，提升回答流畅度

### v1.2.0
- ✅ **新增插件命令系统**：支持通过控制台命令调用 AI，实现第三方插件集成
  - 独立冷却时间控制（`plugins_cooldown_seconds`）
  - 配置文件驱动的人格提示词
  - 支持热重载配置
- ✅ **架构重构**：
  - 新增 `ConversationManager` 统一管理对话状态、历史记录和插件命令记录
  - 重构 `ChatListener`，职责分离，专注事件监听
- ✅ **扩展 clear 命令**：支持根据玩家名称清除指定玩家的上下文记录
- ✅ **MythicMobs 集成**：实现自定义 `%kilacraft_ai_answer%` 占位符

### v1.1.0
- ✅ 架构重构，配置驱动封装
- ✅ 策略模式与抽象基类封装
- ✅ 本地 RAG 索引增强与提示词工程优化

### v1.0.0
- ✅ 基础对话能力
- ✅ 连续对话、聊天监听
- ✅ 历史对话上下文记录
- ✅ 限流、世界限制检查

## 🤝 参与贡献

1. Fork 本仓库
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request

## 📄 许可证

本项目采用 [请指定许可证，如 MIT/GPL]

## 👨‍💻 作者

Zm_Mmm

## 🔗 相关链接

- [DeepSeek API 文档](https://platform.deepseek.com/api-docs/)
- [问题反馈](请添加链接)
