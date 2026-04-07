# Kilacraft-AI

> **🚀 v1.4.2** | Zero Dependencies · Low Memory · High Performance · Fully Open Source  
> A lightweight AI Agent plugin for Minecraft servers with natural language interaction.

[![GitHub](https://img.shields.io/badge/GitHub-Kilacraft--AI-blue?logo=github)](https://github.com/Zm-Mmm/Kilacraft-AI)
[![Gitee](https://img.shields.io/badge/Gitee-Kilacraft--AI-red?logo=gitee)](https://gitee.com/zm_mmm/kilacraft-ai)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.4.2-orange)](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Changelog)

---

## 🎯 What is Kilacraft-AI?

Kilacraft-AI transforms your Minecraft server with an intelligent AI assistant that understands natural language. Players can chat with AI, query game data, check market prices, and execute complex multi-step tasks — all through simple conversation.

**Key Advantages:**
- ⚡ **Zero Middleware**: Single JAR file, no database or cache required
- 💾 **Ultra-Low Memory**: 8-50 MB dynamic usage (vs 2-5 GB for traditional solutions)
- 🔌 **Extensible**: Open SPI interface for third-party plugin integration
- 🎭 **Customizable**: Personality system, knowledge base, and language configuration

---

## 📦 Requirements & Compatibility

### ✅ Version Compatibility

**Minimum Compatible Version: Minecraft 1.16.5 + Java 17**

Kilacraft-AI is developed based on Spigot 1.16.5 API. One JAR package compatible with all subsequent versions.

| Minecraft Version | Java Required | Server Core Support | Description |
|------------------|--------------|---------------------|-------------|
| **1.16.x (1.16.1-1.16.4)** | - | ❌ Not Supported | Official cores don't support this range |
| **1.16.5** | Java 17+ | ⚠️ Paper/Purpur/Leaf/Folia require `-DPaper.IgnoreJavaVersion=true` | CraftBukkit/Spigot don't support Java 17+ |
| **1.17.x - 1.19.x** | Java 17+ | ✅ Fully Supported | Spigot/Paper/Purpur/Leaf/Folia/CraftBukkit |
| **1.20.x - 1.21.x** | Java 21+ | ✅ Fully Supported | Server cores require Java 21 to start |

### 🎁 Optional Dependencies

When these plugins are not installed, corresponding features are automatically disabled, **core dialogue functionality remains unaffected**:

| Plugin | Minimum Version | Features |
|--------|----------------|----------|
| **MythicMobs** | 5.12.0+ | NPC placeholders (let NPCs display AI replies) |
| **GlobalMarketPlus** | 1.3.8.0+ | Economy system (balance, price, product queries) |
| **Vault** | Latest | Multi-currency system support |

---

## ✨ Core Features

### 🤖 AI-Powered Intelligence
- **LLM Intent Recognition**: Understands player intentions and routes to appropriate skills
- **Multi-Step Task Planning**: Automatically decomposes complex queries into executable steps
- **Historical Context**: Maintains conversation history for coherent dialogues
- **Generic LLM Provider**: Supports DeepSeek, Zhipu AI, Moonshot, and any OpenAI-compatible API

### 💰 Economy Integration
- **GlobalMarketPlus Support**: Balance checks, price queries, item availability
- **Natural Language Queries**: "How much is diamond?" or "Check my mailbox"
- **Multi-Item Search**: Query multiple items simultaneously

### 🔍 Bukkit API Access
- **37 Built-in APIs**: Query player status, world info, server stats
- **Data-Driven Configuration**: Define APIs in YAML, no coding required
- **Permission Control**: Fine-grained access control for each API

### 🎭 Personalization
- **Personality System**: Multiple AI personas with unique styles
- **Knowledge Base**: RAG-enhanced responses using your server's documentation
- **Language Customization**: Fully configurable UI text and prompts

### 🔌 Developer-Friendly
- **Skill SPI Extension**: Third-party plugins can register custom skills
- **Plugin Command Mode**: Console commands with callback support
- **Bukkit Event Integration**: Real-time AI response notifications
- **Complete Documentation**: SPI guide, examples, and best practices

---

## 🚀 Quick Start

### Requirements

#### Runtime Requirements
- Minecraft Server 1.16.5+
- Java 17+
- Optional: GlobalMarketPlus 1.3.8.0+, MythicMobs 5.12.0+

#### Build Requirements (Developers Only)
- **JDK 21+** (Required, because of MythicMobs 5.12+ placeholder integration)
- Maven 3.6+

> **💡 Why JDK 21 for building?**  
> This project integrates MythicMobs 5.12+ placeholder system, which is compiled with Java 21. To read its class files, compilation requires a Java 21+ compiler.
> 
> However, with `<release>17</release>` configuration, the generated JAR bytecode remains compatible with **Java 17+** runtime environments, so servers only need Java 17 to run.
> 
> **Most users don't need to compile**, simply download the pre-built JAR from [Releases](https://github.com/Zm-Mmm/Kilacraft-AI/releases).

### Installation (5 Minutes)

1. **Download** `Kilacraft-AI-1.4.2.jar` and place it in `plugins/`
2. **Start** the server to generate configuration files
3. **Edit** `plugins/Kilacraft-AI/config.yml`:
   ```yaml
   api:
     key: "your-api-key-here"  # Get from DeepSeek/Zhipu/Moonshot
     url: "https://api.deepseek.com/v1/chat/completions"
     model: "deepseek-chat"
   ```
4. **Restart** the server
5. **Test**: `/kila Hello!`

That's it! 🎉

---

## 📖 Documentation

### For Server Owners
- **[Server Owner Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Server-Owner-Guide)** - Complete setup, configuration, and troubleshooting
- **[Changelog](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Changelog)** - Version history and updates

### Technical References
- **[Bukkit API Reference](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Bukkit-API-Reference)** - Detailed documentation for 37 APIs
- **[Personality System Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Personality-System-Guide)** - Managing and customizing multiple AI personalities
- **[Knowledge Base Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Knowledge-Base-Guide)** - RAG knowledge base usage guide

### For Plugin Developers
- **[Skill SPI Integration Guide](https://github.com/Zm-Mmm/Kilacraft-AI/wiki/Skill-SPI-Integration-Guide)** - How to extend Kilacraft-AI with custom skills (includes async handling, error isolation best practices)

---

## 🎮 Usage Examples

### Basic Chat
```
Player: /kila How do I get diamonds?
AI: You can find diamonds at Y=-58 to Y=-53 by mining, or explore caves and canyons!
```

### Market Query
```
Player: How much is diamond?
AI: Diamond current price: $100.00/item (15 in stock)
```

### Player Status
```
Player: How much health do I have?
AI: Your health: 18.5/20.0
```

### Multi-Step Task
```
Player: Check diamond price and see if I can afford 10
AI: Diamond: $80.00/item, Your balance: $12,580
    💡 Analysis: Buying 10 diamonds costs $800, you have sufficient funds!
```

---

## 🔧 Configuration Highlights

### Switch LLM Providers
Just modify `config.yml`:
```yaml
api:
  url: "https://open.bigmodel.cn/api/paas/v4/chat/completions"  # Zhipu AI
  model: "glm-4-plus"
```

### Enable/Disable Features
```yaml
agent:
  enabled: true                    # Master switch
  enable_chat_listener: true       # Keyword triggers (@ai)
  enable_command: true             # /kilacraft command
```

### Add Knowledge Base
Create `.md` files in `plugins/Kilacraft-AI/knowledge/`:
```markdown
# Server Rules
1. No cheating
2. Be friendly
```
Then run: `/kilacraft knowledge reload`

**Advanced Configuration**:
```yaml
knowledge:
  enabled: true                    # Enable knowledge base
  max_relevant_chunks: 3           # Max chunks returned per query
  segment:
    max_size: 500                  # Max characters per chunk
    min_size: 25                   # Minimum characters
    overlap: 30                    # Chunk overlap characters
  keywords:
    top_k: 10                      # Keywords extracted per query
  bm25:
    k1: 1.5                        # Term frequency saturation parameter
    b: 0.75                        # Document length normalization parameter
```

**Knowledge Base Features**:
- ✅ **HanLP TF-IDF Algorithm**: Intelligent keyword extraction with automatic stopword filtering
- ✅ **BM25 Scoring Algorithm**: Precise document relevance calculation
- ✅ **Smart Segmentation**: Markdown headers → Paragraphs → Fixed size (3-level strategy)
- ✅ **Performance Optimization**: Chunk caching, 70% faster retrieval on second access

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| **Memory Usage** | 8-50 MB (dynamic) |
| **JAR Size** | ~3 MB |
| **Startup Time** | < 2 seconds |
| **API Response** | 2-5 seconds (depends on LLM) |
| **Concurrent Users** | Unlimited (async non-blocking) |

---

## 🤝 Contributing

We welcome contributions! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Ways to Contribute:**
- 🐛 Report bugs via [Issues](https://github.com/Zm-Mmm/Kilacraft-AI/issues)
- 💡 Suggest features or improvements
- 📝 Improve documentation
- 🔌 Develop custom Skills for your plugins

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

**You can:**
- ✅ Use, copy, modify, merge, publish, distribute
- ✅ Use in commercial projects
- ✅ Create derivative works

**You must:**
- 📝 Include the original copyright notice and license

---

## ❤️ Support Development

If Kilacraft-AI has been helpful to you, consider supporting the project's continued development:

- **[Afdian](https://afdian.com/a/Zm_Mmm)** - Support via WeChat Pay/Alipay

Your support will be used for:
- 🚀 Continuous feature updates and performance optimization
- 🐛 Bug fixes and stability improvements
- 📚 Documentation enhancement and tutorial creation
- 💬 Community support and Q&A

Thank you to every supporter! 🙏

---

## 👨‍💻 Author

**Zm_Mmm**
- GitHub: [@Zm-Mmm](https://github.com/Zm-Mmm)
- Gitee: [@zm_mmm](https://gitee.com/zm_mmm)
- QQ Group: 1094391147

---

## 🌟 Support the Project

If Kilacraft-AI helps you, please consider:
- ⭐ **Starring** the repository on GitHub or Gitee
- 📢 **Sharing** with other server owners
- 💬 **Providing feedback** and suggestions
- 🔌 **Developing Skills** for your plugins
- 🐛 **Reporting issues** to help improve the plugin

**Your support drives continuous improvement!** ❤️

---

## 🔗 Related Links

- **📚 Complete Document Index**: [View all technical documents](https://github.com/Zm-Mmm/Kilacraft-AI/wiki)
- [DeepSeek API Docs](https://platform.deepseek.com/api-docs/)
- [Zhipu AI Docs](https://open.bigmodel.cn/dev/api)
- [Moonshot Docs](https://platform.moonshot.cn/docs)

---

> **Last Updated**: 2026-04-07  
> **Plugin Version**: 1.4.2+  
> **License**: MIT
