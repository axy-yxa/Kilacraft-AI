# Kilacraft-AI

> **🚀 v1.4.2** | 零依赖 · 低内存 · 高性能 · 完全开源  
> 专为 Minecraft 服务器打造的轻量级 AI Agent 插件，支持自然语言交互。

[![GitHub](https://img.shields.io/badge/GitHub-Kilacraft--AI-blue?logo=github)](https://github.com/Zm-Mmm/Kilacraft-AI)
[![Gitee](https://img.shields.io/badge/Gitee-Kilacraft--AI-red?logo=gitee)](https://gitee.com/zm_mmm/kilacraft-ai)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.4.2-orange)](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9B%B4%E6%96%B0%E6%97%A5%E5%BF%97)

---

## 🎯 什么是 Kilacraft-AI？

Kilacraft-AI 为你的 Minecraft 服务器带来智能 AI 助手，理解自然语言。玩家可以通过简单对话与 AI 聊天、查询游戏数据、检查市场价格，甚至执行复杂的多步骤任务。

**核心优势：**
- ⚡ **零中间件依赖**：单个 JAR 文件，无需数据库或缓存
- 💾 **超低内存占用**：8-50 MB 动态使用（传统方案需 2-5 GB）
- 🔌 **高度可扩展**：开放 SPI 接口供第三方插件集成
- 🎭 **完全可定制**：人格系统、知识库、语言配置

---

## ✨ 核心功能

### 🤖 AI 智能引擎
- **LLM 意图识别**：理解玩家意图并路由到对应技能
- **多步骤任务规划**：自动将复杂查询分解为可执行步骤
- **历史对话上下文**：保持对话连贯性
- **RAG 增强架构**：HanLP TF-IDF + BM25 算法
- **智能分段策略**：Markdown 标题→段落、固定大小三级策略
- **中文优化**：TF-IDF 自动过滤停用词
- **性能优化**：分段缓存，二次检索速度提升约70%

### 💰 经济系统集成
- **GlobalMarketPlus 支持**：余额查询、价格查询、商品在售检查
- **自然语言查询**：“钻石多少钱？”或“查看我的邮箱”
- **多物品搜索**：同时查询多个物品

### 🔍 Bukkit API 访问
- **37 个内置 API**：查询玩家状态、世界信息、服务器统计
- **数据驱动配置**：YAML 定义 API，无需编码
- **权限控制**：每个 API 的细粒度访问控制

### 🎭 个性化定制
- **人格系统**：多个 AI 人设，独特风格
- **知识库增强**：基于服务器文档的 RAG 增强回复
- **语言自定义**：完全可配置的 UI 文本和提示词

### 🔌 开发者友好
- **Skill SPI 扩展**：第三方插件可注册自定义技能
- **插件命令模式**：支持回调的控制台命令
- **Bukkit 事件集成**：实时 AI 响应通知
- **完整文档**：SPI 指南、示例和最佳实践

---

## 🚀 快速开始

### 环境要求

#### 运行环境
- Minecraft Server 1.16.5+
- Java 17+
- 可选：GlobalMarketPlus 1.3.8.0+, MythicMobs 5.12.0+

#### 源码编译环境（仅开发者需要）
- **JDK 21+**（必需，因为集成了 MythicMobs 5.12+ 占位符功能）
- Maven 3.6+

> **💡 为什么编译需要 JDK 21？**  
> 本项目集成了 MythicMobs 5.12+ 的占位符系统，该版本的 MythicMobs 使用 Java 21 编译。为了能够读取其类文件，编译时必须使用 Java 21+ 的编译器。
> 
> 但通过 `<release>17</release>` 配置，生成的 JAR 包字节码仍然兼容 **Java 17+** 的运行环境，因此服务器只需 Java 17 即可运行。
> 
> **大多数用户无需编译**,直接从 [Releases](https://gitee.com/zm_mmm/kilacraft-ai/releases) 下载预编译的 JAR 包即可。

### 安装步骤（5 分钟）

1. **下载** `Kilacraft-AI-1.4.2.jar` 放入 `plugins/` 目录
2. **启动** 服务器生成配置文件
3. **编辑** `plugins/Kilacraft-AI/config.yml`：
   ```yaml
   api:
     key: "your-api-key-here"  # 从 DeepSeek/智谱/Moonshot 获取
     url: "https://api.deepseek.com/v1/chat/completions"
     model: "deepseek-chat"
   ```
4. **重启** 服务器
5. **测试**：`/kila 你好！`

完成！🎉

---

## 📖 文档导航

### 服主文档
- **[服主指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9C%8D%E4%B8%BB%E6%8C%87%E5%8D%97)** - 完整配置、故障排除
- **[更新日志](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E6%9B%B4%E6%96%B0%E6%97%A5%E5%BF%97)** - 版本历史和更新

### 技术参考文档
- **[Bukkit API 参考手册](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/Bukkit-API%E5%8F%82%E8%80%83%E6%89%8B%E5%86%8C)** - 37 个 API 详细说明
- **[人格系统配置指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E4%BA%BA%E6%A0%BC%E7%B3%BB%E7%BB%9F%E9%85%8D%E7%BD%AE%E6%8C%87%E5%8D%97)** - 多个人格的管理和定制
- **[知识库增强指南](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/%E7%9F%A5%E8%AF%86%E5%BA%93%E5%A2%9E%E5%BC%BA%E6%8C%87%E5%8D%97)** - RAG 知识库使用详解

### 开发者文档
- **[Skill SPI 接入文档](https://gitee.com/zm_mmm/kilacraft-ai/wikis/%E6%96%87%E6%A1%A3/Skill-SPI-%E6%8E%A5%E5%85%A5%E6%96%87%E6%A1%A3)** - 如何扩展 Kilacraft-AI（包含异步处理、错误隔离等最佳实践）

---

## 🎮 使用示例

### 基础对话
```
玩家：/kila 怎么获得钻石？
AI: 你可以在 Y=-58 到 Y=-53 层挖矿找到钻石，或者探索洞穴和峡谷！
```

### 市场查询
```
玩家：钻石多少钱？
AI: 钻石当前价格：$100.00/个（库存 15 个）
```

### 玩家状态
```
玩家：我还有多少血？
AI: 你的生命值：18.5/20.0
```

### 多步骤任务
```
玩家：查一下钻石价格，看看我能不能买 10 个
AI: 钻石：$80.00/个，你的余额：$12,580
    💡 分析：购买 10 个钻石需要 $800，你的余额充足！
```

---

## 🔧 配置亮点

### 切换 LLM 提供商
只需修改 `config.yml`：
```yaml
api:
  url: "https://open.bigmodel.cn/api/paas/v4/chat/completions"  # 智谱 AI
  model: "glm-4-plus"
```

### 启用/禁用功能
```yaml
agent:
  enabled: true                    # 总开关
  enable_chat_listener: true       # 关键词触发 (@ai)
  enable_command: true             # /kilacraft 命令
```

### 添加知识库
在 `plugins/Kilacraft-AI/knowledge/` 创建 `.md` 文件：
```markdown
# 服务器规则
1. 禁止作弊
2. 保持友好
```
然后执行：`/kilacraft knowledge reload`

**高级配置**：
```yaml
knowledge:
  enabled: true                    # 是否启用知识库
  max_relevant_chunks: 3           # 每次检索返回的最大片段数
  segment:
    max_size: 500                  # 每个片段最大字符数
    min_size: 25                   # 最小字符数
    overlap: 30                    # 片段重叠字符数
  keywords:
    top_k: 10                      # 每次提取的关键词数量
  bm25:
    k1: 1.5                        # 词频饱和参数
    b: 0.75                        # 文档长度归一化参数
```

**知识库特性**：
- ✅ **HanLP TF-IDF 算法**：智能提取关键词，自动过滤停用词
- ✅ **BM25 评分算法**：精确计算文档相关性
- ✅ **智能分段**：Markdown 标题 → 段落 → 固定大小三级策略
- ✅ **性能优化**：分段缓存，二次检索速度提升约70%

---

## 📊 性能指标

| 指标 | 数值 |
|------|------|
| **内存占用** | 8-50 MB（动态） |
| **JAR 大小** | ~3 MB |
| **启动时间** | < 2 秒 |
| **API 响应** | 2-5 秒（取决于 LLM） |
| **并发用户** | 无限制（异步非阻塞） |

---

## 🤝 贡献指南

欢迎贡献！请：
1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 提交 Pull Request

**贡献方式：**
- 🐛 通过 [Issues](https://gitee.com/zm_mmm/kilacraft-ai/issues) 报告 Bug
- 💡 提出功能建议或改进意见
- 📝 改进文档
- 🔌 为你的插件开发自定义 Skills

---

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

**你可以：**
- ✅ 使用、复制、修改、合并、发布、分发
- ✅ 用于商业项目
- ✅ 创建衍生作品

**你必须：**
- 📝 包含原始版权声明和许可证

---

## ❤️ 支持开发

如果 Kilacraft-AI 帮助到了你，可以考虑支持项目的持续发展：

- **[爱发电](https://afdian.com/a/Zm_Mmm)** - 支持微信/支付宝

你的支持将用于：
- 🚀 持续的功能更新与性能优化
- 🐛 Bug 修复与稳定性提升
- 📚 文档完善与教程制作
- 💬 社区支持与问题解答

感谢每一位支持者！🙏

---

## 👨‍💻 作者

**Zm_Mmm**
- GitHub: [@Zm-Mmm](https://github.com/Zm-Mmm)
- Gitee: [@zm_mmm](https://gitee.com/zm_mmm)
- QQ群: 1094391147

---

## 🌟 支持项目

如果 Kilacraft-AI 对你有帮助，请考虑：
- ⭐ **Star 项目**：在 GitHub 或 Gitee 上给我们一个 Star
- 📢 **分享给朋友**：推荐给其他服主
- 💬 **提供反馈**：告诉我们你的使用体验和改进建议
- 🔌 **开发 Skills**：为你的插件集成 AI 能力
- 🐛 **报告问题**：发现 Bug 及时提交 Issue

**你的支持是我们持续优化的动力！** ❤️

---

## 🔗 相关链接

- [DeepSeek API 文档](https://platform.deepseek.com/api-docs/)
- [智谱 AI 文档](https://open.bigmodel.cn/dev/api)
- [Moonshot 文档](https://platform.moonshot.cn/docs)

---

> **最后更新**: 2026-04-07  
> **插件版本**: 1.4.2+  
> **开源协议**: MIT
