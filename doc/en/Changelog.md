# Kilacraft-AI Changelog

## 📝 Version Notes

This file records all important changes to the Kilacraft-AI plugin.

---

## v1.4.5 - AI Response Output Pipeline Refactor, Stream Output Feature, Package Structure Optimization

### ✨ New Features
- **Unified AI Response Output Pipeline**: Refactored AI reply output architecture with configurable carrier selection
  - `AIResponsePipeline`: Unified routing and dispatching for all AI outputs
  - `MessageDispatcher`: Intelligent routing to different carriers (CHAT/ACTION_BAR/BOSS_BAR/TITLE)
  - `OutputChannel` enum: 4 output carriers (chat/actionBar/BossBar/title)
  - `OutputScenario` enum: 5 output scenarios (normal chat/skill result/task result/AFK callback/error)
  - `OutputConfigManager` configuration class: Encapsulates all output carrier configurations
- **Stream Output Functionality**: Real-time display of LLM stream responses, eliminating waiting anxiety
  - `StreamOutputManager`: Manages stream state machine and placeholder window period
  - Window period placeholder: Immediately shows "Generating..." when AI request initiates, solving LLM first-character latency
  - Stream chunk updates: Real-time display of LLM returned content, supports ACTION_BAR/BOSS_BAR carriers
  - Configurable toggle: `output.stream.enabled` controls enable/disable
- **Thinking Message Configurability**: Unified AI prompt message management
  - `MessageUtil.sendThinkingMessage()` integrates with output pipeline, supports configurable carriers
  - Stream mode automatically disables thinking messages (replaced by placeholders), avoiding duplicate prompts
  - All AI prompt messages uniformly use `messages.thinking_message` configuration

### 🔧 Improvements
- **Handler Architecture Deep Refactor**:
  - Removed `BaseResponseHandler` abstract base class, each Handler directly implements `AIResponseHandler` interface
  - Removed `IntentRecognitionResponseHandler` standalone class, changed to anonymous Handler inlined in `SkillIntentRecognizer`
  - `PlayerResponseHandler`/`ConsoleResponseHandler`/`PluginCommandResponseHandler` have clearer responsibilities
- **Stream State Machine Ensures Concurrent Safety**:
  - `GenerationState` three-state state machine (IDLE → GENERATING → COMPLETED) prevents race conditions
  - `updateStreamChunk()` only accepts updates in GENERATING state, preventing residual chunk reception after completion
  - `completeGeneration()` sets COMPLETED state and immediately rejects subsequent updates
- **Resource Release and Memory Leak Protection**:
  - `StreamOutputManager.cleanup()`: Cleans up all stream state mappings when plugin disables
  - `AIResponsePipeline.cleanup()`: Releases all BossBar instances and active states
  - `ChatListener.onPlayerQuit()`: Instantly cleans up stream states when player quits
  - Complete resource release chain: Plugin disable/Player quit/Normal completion/Exception cancellation four-fold protection
- **Package Structure Optimization**: Reorganized code structure by functionality
  - `enums/` package: Unified management of all enum classes (OutputChannel/OutputScenario/PluginPermissionEnum)
  - `manager/` package: Unified management of Manager classes (StreamOutputManager/ConversationManager/LLMManager)
  - `output/` package: Output pipeline core components (AIResponsePipeline/MessageDispatcher)
  - `handler/` package: Simplified to Handler-related classes only

### 🗑️ Removed
- `BaseResponseHandler` abstract base class (Handlers no longer need shared base class)
- `IntentRecognitionResponseHandler` standalone class (changed to anonymous Handler inlined)
- `output.broadcast.*` configuration (public broadcast fixed to use CHAT+prefix)

### 📚 Documentation Updates
- **System Architecture Details.md**: Complete architecture diagram and data flow description for AI response output pipeline
- **Server Owner Guide.md**: Added stream output configuration instructions and usage examples
- **Changelog.md**: Detailed v1.4.5 version change records (this document)
- **All documentation synchronized**: README, System Architecture, Document Index, etc. in both Chinese and English

### ⚠️ Compatibility
- Added `output/` configuration section (includes default_channel/scenarios/boss_bar/title/stream)
- Added configuration file references: OutputChannel/OutputScenario enum classes
- New permission nodes: None (output carrier configuration does not affect permission system)
- Default configuration maintains CHAT carrier, all original behavior has zero changes
- Handler interface signatures unchanged, no impact on third-party plugins
- Fully backward compatible, recommend using `/kilacraft reload` to reload configuration

---

## v1.4.4 - Multi-Step Array Index, CUSTOM Generic AFK Task, BM25 Semantic Scoring Intent Classifier, Skill Prompt Optimization

### ✨ New Features
- **Multi-Step Task Array Index Access**: supports `{step_x.array_field[0].field_name}` format to reference specific elements in list data
- **CMI Skill Integration**: supports CMI plugin features for homes, warps, teleportation, player info queries (8 Actions)
- **CUSTOM Generic Condition Polling Task**: AFK task system now supports generic custom task type, monitoring any Skill's numeric return values
  - Supports 6 comparison operators: less_than / less_than_or_equal / greater_than / greater_than_or_equal / equal / not_equal
  - Use cases: health monitoring, level monitoring, balance monitoring, any numeric condition monitoring
- **BM25 Semantic Scoring Intent Classifier**: Zero maintenance Skill index + LLM intent recognition two-stage architecture
  - Intelligent pre-classifier based on BM25 semantic scoring, automatically builds virtual document index from Skill metadata
  - 7-step pre-classification: keyword short-circuit → chat sentence recognition → BM25 scoring → imperative sentence bonus → threshold judgment
  - Response speed optimization: from ~3-8 seconds to ~2-8 seconds
  - Classification simplified: from 5 types to 2 types (NORMAL_CHAT + SKILL_INTENT)

### 🔧 Improvements
- **Enhanced placeholder parsing**: supports array index format `{step_1.warps[0].warp_name}`, backward compatible with old format `{step_1.field_name}`
- **Skill Prompt Compression**: significantly reduced LLM prompt token consumption (approximately 1300-1900 tokens saved per request)
  - apis.yml: removed 15 usage_scenarios data blocks (-20.8%)
  - CMISkill.yml: hints reduced from 6 to 3 (-50%)
  - MarketQuerySkill.yml: hints reduced from 8 to 3 (-62.5%)
- **Intent Recognition Prompt Optimization**: simplified Skill descriptions, removed redundant data field explanations
- **LLM Secondary Analysis Word Limit**: added 400 character limit in analysis_prompt_suffix to prevent overly long responses
- **CUSTOM task re-entry prevention**: stops polling before executing callback to prevent duplicate triggers
- **CUSTOM task fault tolerance**: Skill execution failure or timeout doesn't interrupt task, continues next polling cycle
- **CUSTOM task online check**: each polling cycle checks if creator is online, auto-cancels if offline
- **Display format optimization**: home/warp name display adds clear labels to avoid LLM confusing name with world name
- **CMI feature trimming**: removed unnecessary economy system features (query_item_worth)

### 🗑️ Removed
- `HEALTH_WATCH` enum value (replaced by generic CUSTOM type)
- `BukkitAPIMetadata.usageScenarios` field and loading logic

### 📚 Documentation Updates
- **Skill-SPI-Integration-Guide.md**: added array index placeholder format description
- **Bukkit API Reference.md**: updated multi-step data passing description for potion effects API
- **AFK Task System Guide.md**: added complete CUSTOM condition polling documentation
- **System Architecture Details.md**: complete description of BM25 semantic scoring intent classifier
- **Intent Recognition Prompt Configuration Guide.md**: BM25 Stage One detailed process and configuration examples
- **Built-in Skills and Events Capability List.md**: renamed and added zero maintenance Skill index description
- **All documentation synchronized**: README, Server Owner Guide, System Architecture, Document Index, Changelog in both Chinese and English

### ⚠️ Compatibility
- Placeholder parsing is backward compatible, old format `{step_x.field}` continues to work
- Added CMI plugin optional dependency (features auto-disabled when not installed)
- New permission node: kilacraft.cmi.teleport
- New configuration file: intent_keywords.yml (BM25 semantic scoring parameters)
- New configuration item: `check-interval-ticks` in config.yml (CUSTOM task polling interval)
- analysis_prompt_suffix in config.yml added 400 character limit
- Fully backward compatible, recommend using /kilacraft reload to reload configuration

---

## v1.4.3 - Multi-Step Task Error Resilience, Analysis Architecture Upgrade, Bukkit API Expansion, Response Experience Optimization & AFK Task System

### ✨ New Features
- Multi-step task error resilience: process continues even if some steps fail
- AnalysisSummary unified object: single-intent and multi-step tasks share the same structured format for LLM comprehension and keyword extraction
- Unified LLM secondary analysis: all skill execution results (including single-intent) go through LLM secondary analysis for more natural responses with context awareness
- **AFK Task System**: background monitoring task system supporting 11 event listeners
  - Player dynamics: online/offline/death/teleport/level change/world switch
  - Life behaviors: bed entry/bed leave/respawn/item break
  - Environment: weather change
  - Dual mode: notification (direct alert) + callback (auto-execute multi-step tasks)
  - Auto management: auto-cancel on player offline, auto-cleanup on task completion
  - Command integration: `/kilacraft afk` (query), `/kilacraft afk cancel` (cancel)
- Built-in vocabulary loading: loads vocabulary files from internal/vocabulary/ directory in JAR package
- Three-layer keyword extraction strategy: original query + segmentation result + TF-IDF keywords, compatible with both short text and long documents
- Single-character query optimization: supports queries like "弓"、"剑" through custom dictionary and stop word checks
- Structured result output: step execution status marked as [SUCCESS]/[FAILURE]/[SKIPPED], facilitating LLM secondary analysis
- AI response Markdown auto-conversion: LLM output in Markdown format (**bold**, *italic*, `code`) is automatically converted to Minecraft color codes
- Enhanced continuous conversation rules: added [Real-time Data Re-fetch Rule] and [Multi-step Task Repetition Rule] to resolve pronoun reference and multi-step truncation issues
- **Bukkit API Expansion**: added 21 new APIs, total reaches 58 APIs (Player 31, World 20, Server 6)
- **Multi-Step Data Passing Enhancement**: API return values automatically extracted to dataMap, subsequent steps can reference via `{step_x.field}`
- Armor equipment query: get full armor set (helmet, chestplate, leggings, boots) with multi-step data passing support
- Potion effects query: query all current potion effects (type, level, duration)
- Target block query: get block player is looking at
- Movement status detection: sneak/sprint status real-time query
- Client info query: player locale, display name
- Respawn point query: bed/respawn anchor respawn location
- Total experience query: cumulative experience query
- World details query: biome, temperature, humidity, sea level, entity statistics, raids, weather duration, world time details

### 🔧 Improvements
- Plugin entry class refactoring: extracted onEnable() flat code into 6 clearly-named private methods, strictly preserving initialization order
- Knowledge base keyword extraction noise reduction: precisely extracts user input + execution result data, removing structural noise like step_id, status tags, color codes, and leading hyphens
- Enhanced dependency checking: not only checks if dependency step exists, but also whether it executed successfully
- Placeholder parsing fault tolerance: skip failed steps and continue executing subsequent steps
- Vocabulary merging and deduplication: uses LinkedHashSet to automatically deduplicate, merging built-in and custom vocabulary
- LLM secondary analysis prompt optimization: layered strategy (execution results = core facts, knowledge base = supplement, fabrication = prohibited), fixed title templates prohibited
- system_prompt style positioning: warm and fun player-friend style for more lively and natural responses
- Response length control: max_tokens adjusted to 500, normal AI conversation limited to 200 Chinese characters
- Configuration comment optimization: clarified analysis_prompt_suffix dual usage for prompting and keyword extraction boundary
- GenericBukkitAPISkill enhancement: added 6 formatting methods (formatArmorContents, formatPotionEffects, formatBlock, formatBiome, formatRaids, formatWorldTime)
- extractDataFromResult expansion: supports automatic extraction for 5 return value types: ItemStack[], Set<PotionEffect>, Block, Biome, Collection<Raid>
- Multi-step compatibility: all API descriptions explicitly declare referenceable data fields
- Security review: removed server network info APIs (port, IP, view_distance, idle_timeout) to prevent sensitive information leakage
- Spigot 1.16.5 compatibility verification: all APIs validated against official documentation
- AFK Task System optimization: factory pattern decoupling, context consistency optimization, delayed feedback without history injection

### 📚 Documentation Updates
- **Server Owner Guide / 服主指南.md**: Added AFK Task System section (feature showcase, usage examples, command list)
- **README.md / README.en.md**: Added AFK Task System to core features, added callback mode to usage examples
- **AFK Task System Guide.md**: 658 lines system architecture documentation
- **Bukkit Event Listener Reference Manual.md**: 642 lines detailed documentation

### ⚠️ Compatibility
- Added internal/vocabulary/ directory for built-in vocabulary files
- Updated analysis_prompt_suffix and system_prompt configuration in config.yml
- Added continuous conversation processing rules in intent_prompts.yml (real-time data re-fetch + multi-step task repetition)
- Added 21 new Bukkit API configurations, automatically created on first startup
- Existing 37 APIs fully compatible, no configuration changes required
- Total APIs expanded from 37 to 58
- Supports hot reloading of API configuration via `/kilacraft reload`
- New permission nodes: kilacraft.api.player.status (armor, potion effects, etc.)
- New permission nodes: kilacraft.afk (default: all players)
- Existing features fully compatible, recommended to use /kilacraft reload to reload configuration
- Fully backward compatible, no configuration changes required

---

## v1.4.2 - Paper API Dependency Removal and Java 17 Compatibility Optimization

### ✨ New Features
- Fully compatible with Spigot 1.16.5+ standard API, removed all Paper-specific interface dependencies
- Support for more server cores: Leaf, Folia and other Bukkit/Spigot API-based cores run perfectly
- Reduced environment requirements: downgraded from Java 21 to Java 17, expanding server applicability

### 🔧 Improvements
- BukkitAPIExecutor refactoring: removed Paper-specific method calls like `getHealth()`, `getMaxHealth()`
- Player status query optimization: use standard Bukkit API's `getHealthScale()` instead of Paper proprietary interfaces
- Inventory operation optimization: use standard `getItemInMainHand()` / `getItemInOffHand()` instead of Paper extension methods
- Java syntax downgrade: removed Java 21 features (such as record, switch pattern matching) to ensure Java 17 compatibility
- Documentation synchronization: both Chinese and English server owner guides mark full support status for Leaf, Folia and other cores

### 📚 Documentation Updates
- "Server Owner Guide" Chinese version: added complete Minecraft version support table (1.16.x - 1.22+)
- "Server Owner Guide" English version: synchronized structure optimization and content adjustments with Chinese version
- Server core compatibility classification: clearly marked in three levels - 🟢Fully Supported / 🟡Experimental Support / 🔴Not Supported
- Core advantages module moved to document beginning, highlighting zero dependencies, low memory, out-of-box features

### ⚠️ Compatibility
- **Minimum Java Version**: Downgraded from Java 21 to Java 17 (Java 8/11 still not supported)
- **Server Cores**: Fully compatible with Spigot 1.16.5+ and all derivative cores (Paper/Purpur/Leaf/Folia)
- **Existing Features**: Fully backward compatible, no configuration changes required
- **Performance Impact**: No performance loss, pure Spigot API implementation actually improves compatibility in some scenarios

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

**Last Updated**: 2026-04-15
