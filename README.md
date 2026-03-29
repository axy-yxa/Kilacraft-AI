# Kilacraft-AI

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
| `/kilacraft reload` | `kilacraft.reload` | 重载主配置 |
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
