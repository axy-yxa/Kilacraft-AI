# Kilacraft-AI Changelog

## 📝 Version Notes

This file records all important changes to the Kilacraft-AI plugin.

---

## v1.4.1 - Intent Recognition Prompt Configuration System Optimization & Documentation Enhancement

### ✨ New Features
- IntentPromptConfigManager: Independent intent recognition prompt configuration system
- intent_prompts.yml configuration file: Structured configuration with 6 major modules + output format rules
- Multi-step task planning rules (multi_step_mandatory): Semantic matching principle, degradation strategy
- Skill name strict restriction (skill_name_restriction): Whitelist mechanism, anti-hallucination protection
- Output format mandatory requirements (output_format_rules): Pure JSON output specification

### 🔧 Improvements
- Prompt construction flow optimization: Dynamic skill list insertion, output format follows response format
- Critical constraint rules refactoring: Removed non-existent vague_instruction_handling, reference_resolution
- Special scenario handling optimization: Removed skill_unavailable_fallback, strengthened skill_name_restriction
- Continuous conversation handling enhancement: continuous_conversation uniformly handles pronoun resolution and vague instructions
- Documentation completeness fix: Added missing testing/debugging, best practices, FAQ sections

### 🔄 Knowledge Base Refactoring
- KnowledgeRetriever retrieval algorithm refactoring: Introduced BM25 scoring algorithm to replace simple keyword matching
- HanLP TF-IDF keyword extraction: Intelligently filters stop words, automatically extracts core semantic keywords
- Multi-level scoring mechanism: Complete question match (+50) + BM25 keyword scoring + Title position weighting (+15) + Exact match reward (+10)
- Chunk caching optimization: Caches chunk results after first retrieval, second retrieval speed improved by ~70%
- Configurable BM25 parameters: Supports adjusting k1 (term frequency saturation point) and b (document length normalization) parameters
- Custom dictionary support: Can add server-specific vocabulary to improve Chinese word segmentation accuracy

### 📚 Documentation Updates
- Added "Intent Recognition Prompt Configuration Guide" in both Chinese and English
- Fixed "Personality System Guide" inconsistencies with actual code implementation
- Fixed "Bukkit API Reference Manual" configuration structure description errors
- Unified all documentation version numbers to v1.4.1

### ⚠️ Compatibility
- Added intent_prompts.yml configuration file, automatically created on first startup
- Existing features fully compatible, no other configuration changes required
- Supports hot reloading of intent recognition prompt configuration via `/kilacraft reload`

---

## v1.4.0 - Third-party Skill SPI Extension & Plugin Command Mode Generalization

### ✨ New Features
- Support for third-party plugins to register custom Skills via SPI mechanism (SkillProvider interface)
- SkillRegistry auto-discovery mechanism, scanning and registering through Bukkit ServicesManager
- Plugin command mode generalization: console command + callback command mechanism
- Callback command timeout protection (default 3 seconds) to prevent main thread blocking
- AIResponseReadyEvent event notification mechanism

### 🔧 Improvements
- Error isolation mechanism: third-party Skill exceptions do not affect core processes
- Simplified SkillContext, removed redundant rawInput field
- Separated TimeoutException, CancellationException and other exception handling
- Optimized ConversationManager cache management (one-time consumption mechanism)
- Enhanced DEBUG logging, added debug information at key steps

### 📦 Packaging Changes
- Added Kilacraft-Skill-API-1.4.0.jar (5KB, contains 5 SPI interfaces)
- Assembly packaging configuration, independent API JAR for third-party developers

### ⚠️ Compatibility
- Existing features fully compatible, no configuration changes required
- Third-party Skills should declare softdepend: [Kilacraft-AI] in plugin.yml
- Built-in Skills have higher priority (third-party Skills with same name will be skipped)

---

## v1.3.6 - Generic LLM Provider Architecture

### ✨ New Features
- LLMProvider unified interface, supports configurable switching between different LLM vendors
- GenericLLMProvider generic implementation, supports all OpenAI standard API format vendors
- LLMManager manager, unified management of LLM Provider lifecycle

### 🔧 Improvements
- HTTP connection pool optimization (max idle connections=10, keep-alive time=5 minutes)
- Streaming response support, reduced first-byte latency
- Configuration caching mechanism, reduced overhead of repeated configuration retrieval
- Automatic retry mechanism, enhanced stability
- Clear separation of responsibilities, improved maintainability and extensibility

### 🗑️ Removed
- Deleted old DeepSeekAPI.java and DeepSeekAPINew.java
- Removed deprecated /llm command and permissions

### ⚠️ Compatibility
- api.* configuration items in config.yml are now managed by GenericLLMProvider
- Existing features fully compatible, only need to modify configuration to switch LLM providers

---

## v1.3.5 - Enhanced Historical Conversation Context

### ✨ New Features
- intent_history_count: number of historical conversation rounds for intent recognition phase (default 5 rounds)
- analysis_history_count: number of historical conversation rounds for result analysis phase (default 2 rounds)
- HistoryUtil utility class, unified history record formatting logic

### 🔧 Improvements
- LLMAnalysisService integrated historical conversations into analysis prompts
- SkillIntentRecognizer hides debug logs for dynamically building system prompts
- ConfigManager added getAgentIntentHistoryCount() and getAgentAnalysisHistoryCount() methods
- Prompt template optimization, integrating historical conversations and current input

### ⚠️ Compatibility
- Configuration file added intent_history_count and analysis_history_count items
- Recommended to backup and regenerate configuration file

---

## v1.3.4 - MarketQuerySkill Expansion & Multi-Step Task Optimization

### ✨ New Features
- MarketQuerySkill added 4 read-only actions: query_availability, query_my_items, query_mailbox, query_market_stats
- GlobalMarketPlusAPI extension: MailItem, MarketStats, MarketItem and other data model classes
- Bukkit API capability expansion: added 44 APIs (Player/World/Server)
- Multi-step task placeholder parsing mechanism: supports {step_xxx.field} data passing

### 🔧 Improvements
- Multi-step task prompt optimization, added single-intent format examples
- BukkitAPIExecutor findMethod() prioritizes parameterless methods, fixed overloaded method call failures
- Item name purification: removed :1 suffix, automatic translation to Chinese
- Experience progress percentage display, game time formatting (HH:MM), online player list simplification
- Enhanced placeholder parsing fault tolerance, implemented "fail-fast" strategy
- Code architecture refactoring, extracted inner classes into independent files

### ⚠️ Compatibility
- Configuration file apis.yml expanded with 44 APIs + permission configuration
- plugin.yml permission description optimization

---

## v1.3.3 - Bukkit API Capabilities & Automated System Prompt

### ✨ New Features
- Bukkit API skill system (GenericBukkitAPI): data-driven vanilla API calling framework
- Fully automated system prompt: zero hard-coding dynamic prompt construction, automatically traverses all skills to generate action lists
- Enhanced console AI capabilities: supports same full functionality as players (intent recognition, skill invocation, multi-step tasks)
- Personality configuration optimization: YAML multi-line text support, JSON format fault tolerance, empty configuration detection, error recovery mechanism

### 🔧 Improvements
- Dynamic metadata-driven: loads API definitions from apis.yml, supports hot reload
- Reflection execution engine: dynamically calls Bukkit API through reflection (Player/World/Server)
- Dual-mode support: method_chain (chained calls) and additional_methods (parallel independent method calls)
- Smart formatting: template placeholder replacement, special type handling (Location/GameMode/ItemStack)
- Moved SkillConfig.java to skills.framework.config package
- Moved TaskExecutor.java and TaskPlan.java to skills.framework.task package
- Added 5 Bukkit API permission nodes and wildcard permission kilacraft.api.*

### ⚠️ Compatibility
- Added apis.yml configuration file, automatically created on first startup
- Added permission nodes, recommended to update permission configuration

---

## v1.3.2 - Agent Configuration & Multi-Step Task Executor

### ✨ New Features
- Fine-grained Agent configuration: master switch, independent entry control (chat_listener/command)
- Multi-step task executor (TaskExecutor): topological sorting algorithm, step dependency management, result summary analysis
- Enhanced LLM intent recognition: single-intent fast path, multi-step task planning, JSON Schema validation
- Prompt configurability: system_prompt and analysis_prompt fully customizable

### 🔧 Improvements
- Unified AI request handler (AIRequestHandler): eliminated approximately 130 lines of duplicate logic
- Configuration-based state transfer design, no internal state caching
- All AI responses automatically add MessageUtil.getAIPrefix() prefix
- Optimized debug mode logging, using logger.info instead of System.out.println
- Removed redundant single-step processing logic from TaskExecutor
- ChatListener now fully supports multi-step task processing

### ⚠️ Compatibility
- Agent configuration structure changed, recommended to backup and regenerate configuration file
- TaskExecutor prompts made configurable, original hard-coded prompts migrated to config.yml

---

## v1.3.1 - RAG Retrieval Optimization & Response Speed Improvement

### ✨ New Features
- Standard RAG knowledge retrieval architecture: supports multiple knowledge file formats
- Intelligent segmentation strategy: Markdown headings → paragraphs → fixed size (three-level strategy)
- Chinese keyword extraction: n-gram tokenization + intelligent stop word filtering + automatic punctuation removal
- Multi-level scoring mechanism: complete question matching (+50) + keyword matching (+5) + title matching (+25) + coverage multiplier

### 🔧 Improvements
- Instant thinking message: moved from skill execution to command entry point, eliminating the illusion of "slow plugin"
- Unified cooldown management: handled uniformly in handlePlayerMessageCommand()
- HTTP connection pool optimization: connection reuse, max idle=10, keep-alive=5min
- Streaming response support: using BufferedReader to read line by line, reducing first-byte latency
- Configuration caching mechanism: caching model/temperature/maxTokens values
- Pre-allocated buffers: StringBuilder pre-allocated size (512/256), reducing expansion overhead
- Automatic retry mechanism: enabled retryOnConnectionFailure(true)

### ⚠️ Compatibility
- Knowledge base segmentation configuration structure changed, recommended to backup and regenerate

---

## v1.3.0 - AI Agent Evolution

### ✨ New Features
- Skills skill system framework: LLM intent-based automatic skill routing, asynchronous execution model
- LLM intent recognition engine: dynamic skill prompt construction, multi-entity extraction support, confidence evaluation
- GlobalMarketPlus deep integration: player balance query, market price query, product list query
- Multi-item joint query: query multiple item prices at once, quantity recognition, optimal price calculation

### 🔧 Improvements
- Core architecture refactoring: added SkillContext, SkillResult, SkillManager, Skill base interfaces
- Anti-duplication mechanism: fixed issue of repeatedly sending thinking messages and duplicate cooldowns during skill fallback
- Smart item name matching: prioritize exact match, downgrade to fuzzy match, Chinese-English translation mapping support
- Enhanced configuration system: independent skill configuration management (skills/ directory), item translation configuration

### ⚠️ Compatibility
- Configuration structure changed, recommended to backup and regenerate configuration file
- Skill system is experimental, API may be adjusted in future versions
- GlobalMarketPlus integration requires plugin version 1.3.8.0+

---

## v1.2.3

### ✨ New Features
- Language configuration system: extracted all system prompt texts to language.yml configuration file
- Dynamic help messages: help command dynamically displays tips based on player permissions
- Permission management optimization: created PluginPermission enum class to unified management of permission nodes

### 🔧 Improvements
- Architecture optimization: added LanguageManager to unified management of all language configurations
- Removed all hard-coded permission strings, all permission checks use enum class
- Tab completion also dynamically displays based on permission enum
- Consolidated duplicate prompt texts, improved reusability
- Validation logic refactoring: AIRequestValidator only responsible for validation, no longer directly sends notifications

---

## v1.2.2

### 🔧 Improvements
- Removed kilacraft.use permission requirement, open to all players by default
- Optimized Tab completion, dynamically displays commands based on permissions
- Separated clear history prompts, displays different commands based on permissions
- Improved permission system and command handling

---

## v1.2.1

### 🔧 Improvements
- Added AI latest response caching mechanism, optimized custom placeholder parsing performance
- Enhanced command help messages, permission checks, and logging
- Removed knowledge base enhancement source declarations, making responses smoother

---

## v1.2.0

### ✨ New Features
- Plugin command system: supports console command to call AI, for third-party plugin integration
- Independent cooldown control (plugins_cooldown_seconds)
- Extended clear command: supports clearing specific player's context by player name
- MythicMobs integration: implemented custom %kilacraft_ai_answer% placeholder

### 🔧 Improvements
- Architecture refactoring: added ConversationManager to unified management of chat state, history, and plugin command records
- Refactored ChatListener, separation of concerns, focused on event listening

---

## v1.1.0

### 🔧 Improvements
- Architecture refactoring, configuration-driven encapsulation
- Strategy pattern and abstract base class encapsulation
- Local RAG index enhancement and prompt engineering optimization

---

## v1.0.0

### ✨ New Features
- Basic conversation capabilities
- Continuous chat, chat listener
- Historical conversation context recording
- Rate limiting, world restriction checks

---

**Last Updated**: 2026-04-05
