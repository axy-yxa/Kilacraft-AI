<div align="center">

# Kilacraft-AI <sub>[English](README.md)</sub>

**首个让每个服务器都拥有「越用越懂你」的AI智能体插件**

[![Version](https://img.shields.io/badge/Version-2.0.2-orange)](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9B%B4%E6%96%B0%E6%97%A5%E5%BF%97)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/nNmhcZHDxr)
[![GitHub](https://img.shields.io/badge/GitHub-Kilacraft--AI-blue?logo=github)](https://github.com/axy-yxa/Kilacraft-AI)
[![QQ Group](https://img.shields.io/badge/QQ%E7%BE%A4-1094391147-12B7F5?logo=tencent-qq)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=FlgSknDUfFcXVYsU7tqPZ8xMMBLgPJjg&authKey=pAJFrXh%2BPpr4V%2BbXhhVUWzoQN4j%2BOWitvi%2BcB59jx7S1JM21yL8kikQBhqm3Cgff&noverify=0&group_code=1094391147)

</div>

---

## 特性一览

**核心能力**

| | 功能 | 说明 |
|:---:|:---|:---|
| 🤖 | AI 智能引擎 | LLM 意图识别 · 多步骤任务规划与执行 · 5 种输出载体 · 流式输出 · 公屏广播 |
| 🔍 | Bukkit API | 72 个只读 API · YAML 数据驱动 · 多步骤数据传递 · 细粒度权限 |
| 💰 | 经济系统 | GlobalMarketPlus 集成 · 自然语言查询余额/价格 · AI 代玩家上架/购买/转账 |
| 🎮 | CMI 集成 | 传送（家/地标/TPA）· 玩家增强信息（时长/AFK/隐身/在线列表） |
| 📊 | 原版统计 | 80+ 统计查询 · 知识库驱动检索 · 距离/时长自动换算 |
| 🔧 | 命令执行 | 以玩家身份执行命令 · 继承权限 · config 开关 + 权限双重保护 |
| 🎨 | 音效与粒子 | AI 触发音效/粒子 · 仅调用者感知 · YML 配置驱动 |

**智能化**

| | 功能 | 说明 |
|:---:|:---|:---|
| 🧠 | 玩家画像 | 八维度行为分析 · 增量累积更新 · 历史快照 · 动态注入对话上下文 |
| 🕸️ | 社交关系 | 私聊/TPA/技能交互追踪 · 强度递减累加 · 好友里程碑互推问候 |
| 👋 | 智能问候 | 登录个性化问候 · 三大分类聚合（自有事件/好友动态/游玩亮点） |
| 🎭 | 个性化 | 多人格系统 · RAG 知识库增强（HanLP + BM25 + Embedding）· 自定义词典 |
| 🔔 | 挂机任务 | 20 种类型（19 事件 + 自定义轮询）· 自然语言创建 · 通知/回调双模式 |
| 🏥 | 服主管理 | Spark + AI 诊断 · 玩家行为分析 · 审计日志 · Discord/钉钉告警 |

**基础设施**

| | 功能 | 说明 |
|:---:|:---|:---|
| 💾 | 数据持久化 | 对话/画像/社交/事件/审计全持久化 · H2 零配置 / MySQL 热切换 |
| 🔌 | SPI 扩展 | 第三方 Skill 注册 · 插件命令模式 · 全球 Skill 台账监管 |
| 🛡️ | 安全隔离 | 非合作式 Value 扫描 · 跨玩家操作拦截 · Skill 权限预检过滤 |

## 快速开始

**1.** 下载 [Kilacraft-AI-2.0.2.jar](https://gitee.com/zm_mmm/kilacraft-ai/releases) 放入 `plugins/`

**2.** 编辑 `plugins/Kilacraft-AI/llm.yml`，填入 API 密钥：

```yaml
llm:
  api_url: "https://api.deepseek.com/v1/chat/completions"
  api_key: "your-api-key"
  model: "deepseek-chat"
```

**3.** 重启服务器，输入 `/ai 你好` 测试

> 支持所有 OpenAI 兼容 API — DeepSeek / 智谱AI / Moonshot / 硅基流动（国内）· OpenAI / Groq / Gemini / OpenRouter（国际），修改 `api_url` 和 `model` 即可切换。

## 使用示例

```
玩家：钻石多少钱？
  AI：钻石当前价格：$100.00/个（库存 15 个）

玩家：查一下钻石价格，看看我能不能买 10 个
  AI：钻石：$80.00/个，你的余额：$12,580 → 购买 10 个需 $800，余额充足！

玩家：帮我盯着 Steve 上线
  AI：已创建监控任务。
  🔔 Steve 已上线！

玩家：帮我盯着自己血量低于 5
  AI：已创建监控任务。
  🔔 你的血量已降至 4.0！
```

## 兼容性

| Minecraft | Java | 服务端 | 状态 |
|---|---|---|---|
| 1.16.5 | 17+ | Paper/Purpur/Leaf/Folia | ⚠️ 需 `-DPaper.IgnoreJavaVersion=true` |
| 1.17 - 1.19 | 17+ | 全部主流 | ✅ |
| 1.20 - 1.21+ | 21+ | 全部主流 | ✅ |

> 可选依赖：MythicMobs 5.12+（NPC 占位符）· GlobalMarketPlus 1.3.8+（经济）· CMI（传送/查询）· Spark（性能分析，Paper 1.21+ 等已内置）· Vault（多货币）
> 未安装时对应功能自动禁用，不影响核心对话。

## 技能体系

| 类别 | 能力 | 依赖插件 |
|:---:|:---|:---:|
| **Bukkit API** | 72 个只读接口：玩家物品栏/状态/信息、世界信息、服务器信息、环境感知 | 无 |
| **原版统计** | 80+ 原版累计统计查询，知识库驱动检索，自动单位转换 | 无 |
| **全球市场** | 搜索/上架/领取/求购/下架/转账/拍卖/批量操作（9 种） | GlobalMarketPlus |
| **CMI 集成** | 5 查询（家/地标/玩家信息/在线/AFK）+ 3 传送 | CMI |
| **挂机任务** | 19 事件监听 + 自定义轮询，自然语言创建 | 无 |
| **通用工具** | 延时等待、主动通知、全服广播 | 无 |
| **命令执行** | 以玩家身份执行命令，继承权限（默认关闭） | 无 |
| **音效粒子** | AI 触发音效/粒子，仅调用者感知 | 无 |
| **服主管理** | 健康监控、玩家分析、审计日志 | Spark（可选） |

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/kila <消息>` | 无 | 与 AI 对话（`/kilacraft`、`/ai`、`/zm` 均可） |
| `/kilacraft chat` | 无 | 进入/退出连续对话模式 |
| `/kilacraft clear [玩家]` | `clear.self` / `clear.other` | 清除对话历史 |
| `/kilacraft reload` | OP | 重载配置和语言文件 |
| `/kilacraft knowledge reload` | OP | 重载知识库 |
| `/kilacraft personalities reload` | OP | 重载人格配置 |
| `/kilacraft afk [cancel]` | 无 | 查看/取消挂机任务 |
| `/kilacraft tasks` | `kilacraft.tasks` | 查看定时任务运行状态（默认 OP） |
| `/kilacraft profile start [秒]` | `admin.health` | 启动手动性能采样（30-120 秒） |
| `/kilacraft profile status` | `admin.health` | 查看采样状态 |
| `/kilacraft profile stop` | `admin.health` | 中断采样并丢弃 |
| `/kilacraft notify test` | `admin.health` | 测试外部通知渠道 |
| `/kilacraft plugins <人格> <内容> <UUID> [回调]` | 控制台 | 第三方插件集成 |

## 文档

| 文档 | 说明 |
|---|---|
| [服主指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9C%8D%E4%B8%BB%E6%8C%87%E5%8D%97) | 完整配置与故障排除 |
| [服主管理功能指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9C%8D%E4%B8%BB%E7%AE%A1%E7%90%86%E5%8A%9F%E8%83%BD%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97) | 健康监控/玩家分析/审计日志 |
| [更新日志](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9B%B4%E6%96%B0%E6%97%A5%E5%BF%97) | 版本历史 |
| [Bukkit API 参考](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/Bukkit-API%E5%8F%82%E8%80%83%E6%89%8B%E5%86%8C) | 72 个 API 详解 |
| [AFK 任务详解](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/AFK%E6%8C%82%E6%9C%BA%E4%BB%BB%E5%8A%A1%E7%B3%BB%E7%BB%9F%E8%AF%A6%E8%A7%A3) | 挂机任务系统架构 |
| [人格系统指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E4%BA%BA%E6%A0%BC%E7%B3%BB%E7%BB%9F%E9%85%8D%E7%BD%AE%E6%8C%87%E5%8D%97) | AI 人设管理 |
| [知识库指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E7%9F%A5%E8%AF%86%E5%BA%93%E5%A2%9E%E5%BC%BA%E6%8C%87%E5%8D%97) | RAG 知识库配置 |
| [Skill SPI 文档](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/Skill-SPI-%E6%8E%A5%E5%85%A5%E6%96%87%E6%A1%A3) | 第三方 Skill 开发 |
| [Skill 全球台账](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html) | 实时 Skill 使用统计与安全审查 |

## 开发者

- **源码编译**需 JDK 21+（MythicMobs 5.12+ 依赖），生成的 JAR 兼容 Java 17+ 运行
- 第三方插件可通过 **Skill SPI** 注册自定义技能 → [接入文档](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/Skill-SPI-%E6%8E%A5%E5%85%A5%E6%96%87%E6%A1%A3)
- 欢迎 [Issues](https://gitee.com/zm_mmm/kilacraft-ai/issues) / Pull Request

## 提交 Skill 审查

想让 Skill 进入全球台账白名单？提交审查后，用户可在 [Skill 全球台账](https://axy-yxa.github.io/Kilacraft-AI/skill-registry.html) 看到 🟢 已审查 标记。

**提交方式**：在 [Issues](https://gitee.com/zm_mmm/kilacraft-ai/issues) 创建新 Issue，标题格式 `[Skill 审查] 你的 Skill 名称`

**需要提供：**
1. **Skill 名称**（与注册时一致）
2. **源码或 JAR 文件**（必须，用于安全审查）
3. **功能描述**（简要说明 Skill 做什么）
4. **权限说明**（需要哪些 Bukkit 权限或依赖哪些插件）
5. **文档链接**（可选，如有使用文档或 Wiki）

**审查标准：**
- ✅ 不直接操作其他玩家数据（除非明确声明且合理）
- ✅ 不执行危险命令（op 级别命令、文件读写等）
- ✅ 无恶意网络请求或数据外传
- ✅ 资源释放正确（无内存泄漏）

审查通过后，我会将你的 Skill 加入白名单，并在台账页面标记为 🟢 已审查。

---

<div align="center">

本项目完全开源免费，赞助纯属自愿。你的支持是我持续更新的动力 ❤️

**🎁 赞助可享：** 💬 独立 Discord 频道 · 🎫 工单优先响应 · 🚀 抢先体验新版本

<img src="sponsor.jpg" width="350" alt="赞助码" />

*赞助后请保留截图，联系我开通权益*

</div>

---

<div align="center">

MIT License · Made by [Zm_Mmm](https://gitee.com/zm_mmm) · <a href="http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=FlgSknDUfFcXVYsU7tqPZ8xMMBLgPJjg&authKey=pAJFrXh%2BPpr4V%2BbXhhVUWzoQN4j%2BOWitvi%2BcB59jx7S1JM21yL8kikQBhqm3Cgff&noverify=0&group_code=1094391147"><img src="https://img.shields.io/badge/QQ%E7%BE%A4-1094391147-12B7F5?logo=tencent-qq" height="20" valign="middle" alt="QQ群" /></a> · <a href="https://discord.gg/nNmhcZHDxr"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white" height="20" valign="middle" alt="Discord" /></a>

大服私人定制 / 针对性优化 · QQ 1456133139 · 微信 lyh1456133139 · [Discord](https://discord.gg/nNmhcZHDxr)

</div>
