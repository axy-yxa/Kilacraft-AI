# Kilacraft-AI

> **🎉 v1.3.0 重大更新**：从对话式 AI 进化为 **AI Agent**！新增技能系统、意图识别、经济系统集成等强大功能。详见 [更新日志](#-更新日志)

一个功能强大的 Minecraft AI 对话插件，集成 DeepSeek AI，为服务器玩家提供智能交互体验。

## 📋 特性亮点

- **多种交互模式**：命令模式、连续对话模式、关键词触发模式
- **人格系统**：支持多个人格配置，可自定义 AI 角色和回复风格
- **上下文对话**：自动保存历史对话，支持连续的上下文交流
- **知识库增强**：支持本地知识库检索，让 AI 更了解你的服务器
- **权限管理**：细粒度的权限控制，支持管理员清除其他玩家历史
- **冷却限制**：防止滥用，支持自定义冷却时间和世界限制
- **流式输出**：支持实时显示 AI 回复生成过程（可选）
- **第三方插件集成**：支持 MythicMobs 等插件调用 AI 功能
- **MythicMobs 占位符**：支持自定义占位符获取 AI 最新回复

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

## ⚙️ 配置说明

### 核心配置 (config.yml)

```yaml
# API 配置
api:
  key: "your-deepseek-api-key"      # DeepSeek API 密钥（必填）
  url: "https://api.deepseek.com/v1/chat/completions"
  model: "deepseek-chat"              # 使用的模型
  temperature: 0.7                    # 温度参数（0-2）
  max_tokens: 1000                    # 最大回复长度

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

# 知识库配置
knowledge:
  enabled: true                       # 启用知识库
  max_relevant_chunks: 3              # 最大相关知识数量
```

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

### 人格配置 (personalities.yml)

在 `plugins/Kilacraft-AI/personalities/` 目录下创建 YAML 文件来定义不同的人格：

```yaml
严厉教师:
  prompt: "你是一位严厉的 Minecraft 教师，对玩家 {player} 要求严格但关心。"
  
友好助手:
  prompt: "你是友好的 AI 助手，帮助玩家 {player} 解决各种问题。"
```

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

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `kilacraft.clear.self` | true | 清除自己的对话历史 |
| `kilacraft.clear.other` | op | 清除其他玩家的对话历史 |
| `kilacraft.reload` | op | 重载配置 |
| `kilacraft.knowledge` | op | 管理知识库 |
| `kilacraft.personalities` | op | 管理人格配置 |

**注意**：基础对话功能无需任何权限，所有玩家默认可用。

## 📚 知识库功能

### 添加服务器知识

1. 在 `plugins/Kilacraft-AI/knowledge/` 目录下创建 `.md` 或 `.txt` 文件
2. 添加服务器相关的知识内容
3. 使用 `/kilacraft knowledge reload` 重新加载

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
- [SpigotMC 插件页面](请添加链接)
- [问题反馈](请添加链接)
