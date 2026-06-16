# Kilacraft-AI Changelog

> **Last Updated**: 2026-06-16  
> **Description**: This file records all important changes to the Kilacraft-AI plugin  

---

## v2.1.1 - Two-Phase Intent Recognition Architecture, Prompt System Restructuring, LLM Thinking Mode Governance

### ✨ New Features
- **Two-Phase Intent Recognition Architecture (Core of this release)**: Splits the original "single full skill list to LLM" into two phases, significantly reducing Token consumption
  - **Phase 1 (Coarse Selection)**: Sends only one line of `name + description` per skill; LLM determines which skill categories are needed; pure small talk returns `null` directly, skipping Phase 2 for normal AI conversation
  - **Phase 2 (Precise Selection)**: Sends full details (actions + hints) only for skills selected in Phase 1, completing action selection and parameter extraction; validates `skill_name` against whitelist, invalid names rejected
  - **Quantified benefits**:
    - **Pre-refactor baseline** (single-phase, per recognition): main rules ~26K + all skills full ~80K (incl. BukkitAPI 77 actions ~38K) ≈ **106K chars**
    - **Pure small talk**: only Phase 1 (returns `null`, no Phase 2) ≈ **8K chars**, **↓ ~92%** vs baseline
    - **Normal skill** (1 built-in skill hit, median full size ~3.1K): Phase 1 + Phase 2 ≈ **38K chars**, **↓ ~65%**
    - **Complex skill** (hit incl. BukkitAPI 77 actions): Phase 1 + Phase 2 ≈ **73K chars**, **↓ ~32%**
    - Token conversion (Chinese-mixed content ~0.6 token/char): baseline ≈ 64K → three scenarios ≈ 4.8K / 22.6K / 43.5K
  - Response speed: two-phase adds one ultra-lightweight Phase 1 call, but each recognition no longer carries the full skill list; net latency depends on model and network
- **Intent Recognition Prompt System Restructuring** (Chinese/English synced): Added "Three Inviolable Rules" top-priority rule (default to multi-step when uncertain); single-intent/multi-step decision refactored to three-condition check (required param provided by user + completable in one action + no dependency on other actions' return values); arithmetic placeholders unified for `amount`/`quantity`/`price`/`threshold`; parameter missing enforced "query-then-act" (null required params prohibited); Phase 1 coarse-selection positioning strengthened (favor recall over precision)
- **LLM Thinking Mode Governance**: Normal conversation path auto-disables thinking for models with thinking on by default (MiMo, DeepSeek V4+, GLM 4.5+, Kimi K2+, Qwen3, Grok 4, Doubao thinking, MiniMax-M3, etc.); admin reasoning path injects enable params per model family; adapted OpenAI o-series / Doubao thinking `max_completion_tokens`. Resolves thinking tokens sharing `max_tokens` budget with output tokens causing empty output in MC scenarios (small quota)
- **Placeholder Arithmetic Evaluation Utility (ArithmeticUtil)**: Evaluation logic extracted to a public utility class; application scope expanded from multi-step task scalar params (amount/quantity/price) to CUSTOM task `threshold`, supporting relative thresholds like "5 below current health" (`{step_0.health}-5`). Single binary operation only (+ - * /), pure regex, no injection risk
- **JSON Auto-Repair Utility (JsonSafeGetUtil)**: Three-phase repair (filter excess closing → complete missing closing → remove trailing commas), abandons on cross-nesting, unified replacement of multiple duplicate implementations
- **Startup Version Update Detection**: Async GitHub latest release check, colored console box notification on new version, silent on failure

### 🔧 Improvements
- **Diagnostic Model Fallback Mechanism**: When `admin.yml` has no reasoning model configured, auto-falls back to `llm.yml` base chat model (reuses url/key/model only, max_tokens/timeout still use diagnostic-specific values to avoid report truncation), lowering the barrier for health monitoring
- **Health Monitoring Unavailable Layered Diagnostics**: Checks prerequisites one by one (model / guardian switch / Spark installed), gives precise hints; diagnostic report header adds a "Diagnostic Model" row marking the actual generation model
- **DB History Load Count Tuning**: `loadFromDB` limit changed from `maxHistory×2` to `maxHistory` (default 20), reducing redundant loading
- **AFK Task String Condition Value Display Optimization**: `EvaluationResult` adds `actualValueStr`; string conditions (e.g., block types) display real values instead of 0/1 placeholders
- **LLM Empty Response Friendly Hint**: Returns "AI temporarily unavailable" to the player when streaming response is empty instead of an empty message (also logs SSE chunk count and recent raw data for troubleshooting)
- **JSON Scenarios Disable `max_tokens`**: Intent recognition, profile analysis, etc. no longer set `max_tokens`, preventing complex JSON truncation; GenericBukkitAPI description enhanced to improve Phase 1 classification accuracy
- **Concurrency & Thread Safety**: `ConversationManager` history queue upgraded to `ConcurrentLinkedDeque` + auto trim (MAX_HISTORY_SIZE=100); `AFKTaskManager` atomic registration prevents overflow; `ProfileManager.flushAllProfiles` single-connection batch update prevents cascade failure
- **Code Cleanup & Observability**: LLMProvider interface simplified, ThinkingModelCapable upgraded to functional interface; SkillIntentRecognizer exception catching with full stack trace; GreetingPromptBuilder event summary merge eliminates ~140 lines of duplication; unified history access; cleaned up redundant i18n wrappers and translation dead keys
- **Configuration Hot-Reload Hardening**: `PersonalitiesConfigManager` switched to snapshot replacement pattern, `LLMConfigManager` fields fully `volatile`
- **Embedding Retrieval Optimization**: chunk vector norm precomputed and cached, cosine similarity computation reduced by 2/3
- **`reconcileOnlineProfiles` Single-Connection Batch**: aligned with `flushAllProfiles` pattern

### 🐛 Bug Fixes
- Fixed `ConversationPersistenceService.mergeLoadedHistory` clearing itself when `loadedHistory` and `playerHistory` are the same object via `clear()`, causing history loss (triggered when a player sends a message for the second time or later with valid in-memory history, via `/ai` command or chat listener path)
- Fixed thinking/reasoning models producing empty output in normal conversation (covered by thinking mode governance)
- Fixed profile analysis JSON parsing occasional failure (auto-repair then re-parse), CUSTOM task `condition_plan` null NPE, shutdown `flushAllProfiles` connection exception cascade failure, `ConversationManager` non-thread-safe `ArrayDeque` concurrent data loss
- **Security & Stability Hardening**: Fixed multiple stability and security boundary issues; added recent active player cache to strengthen data isolation

### ⚠️ Compatibility

#### Upgrading from v2.1.0
1. Stop server, replace JAR, start
2. **MUST remove `intent_prompts.yml` / `intent_prompts_en.yml`**: the two-phase architecture + prompt system restructuring changed this file's structure significantly (new `phase1` section, full rewrite into 9 sections, new "Three Inviolable Rules", etc.). Old file contents override the code's built-in new defaults, so the two-phase architecture and this prompt refactor **will not take effect** — **you must delete and restart to let the plugin regenerate it**
3. **Recommended** (for full skill / model config optimization effects): the following config files are recommended to be deleted and regenerated (works without deletion, just missing some optimizations):
   - `skills/afktask/AFKTaskSkill.yml` / `AFKTaskSkill_en.yml`, `skills/globalmarketplus/MarketActionSkill.yml` / `MarketActionSkill_en.yml`, `skills/bukkit/apis.yml` / `apis_en.yml`
   - `llm.yml` / `admin.yml` (thinking mode governance notes + diagnostic model fallback notes + default model updates)
   - `config.yml` (new `security.player_isolation.offline_cache` section, recent active player cache)
   - `database.yml` (new `h2.tcp` section, H2 TCP Server access control)
4. Except for the `intent_prompts.yml` in step 2, all other config entries have code-level default fallbacks — **works without deleting them**
5. **Lower diagnostic feature barrier**: when no `admin.yml` reasoning model is configured, health monitoring auto-falls back to `llm.yml`, usable without extra config (diagnostic quota still controlled by `admin.yml`, no conflict with normal conversation)

---

## v2.1.0 - Server Admin Management (Health Monitoring, Player Analysis, Audit Log), Code Architecture Restructuring

### ✨ New Features
- **Server Health Monitoring System**: AI-powered intelligent server monitoring and diagnostics system, solving the problems of difficult-to-locate server lag and time-consuming performance troubleshooting
  - **Zero-Configuration Auto-Monitoring**: Guardian thread runs 24/7 in the background, checking TPS, MSPT, CPU and other metrics every 10 seconds, automatically triggering diagnostics on anomaly detection
  - **AI-Powered Diagnostics**: Uses reasoning models (e.g. DeepSeek R1) for deep performance analysis, automatically generating Markdown diagnostic reports with precise bottleneck identification
  - **Server Activity Metrics Collection**: Captures chunk load changes and player distribution across worlds during the sampling window, compensating for Profiler's blind spot for non-CPU activities (I/O, disk, network)
  - **Method-Level Hotspot Identification**: Based on Spark Profiler's call stack analysis, pinpointing issues at the code level and identifying specific plugin performance impacts. Auto mode uses full-duration sampling, consistent with manual mode
  - **On-Demand Precision Analysis**: Supports `/kilacraft profile start [duration]` command for admins to analyze specific time periods as needed, solving intermittent performance issues
  - **Intelligent Debouncing**: Alert cooldown, sliding window frequency limits prevent resource exhaustion and ensure system stability
  - **External Notification**: When auto-triggered diagnostic analysis completes, push alert notifications to Discord or DingTalk group bots so admins are informed even when offline. Discord supports Embed card messages; DingTalk supports Markdown text + optional HMAC-SHA256 signature security. Push content only includes alert triggers, real-time snapshot metrics, and AI diagnosis summary — no full report file attachments to prevent sensitive information leakage
  - **Independent Configuration Management**: New `admin.yml` config file for centralized management of monitoring thresholds, reasoning model settings, external notification channels, and diagnostic prompts
- **Server Health Alert Query (ServerHealthSkill)**: Query historical health alert records via natural language
- **Player Behavior Analysis (PlayerAnalysisSkill)**: Online player trends, active player rankings, new player inflow, profile analysis coverage, social graph insights, specific player social relations query
- **Audit Log Query (AuditLogSkill)**: View AI skill execution records, usage statistics, and failure logs
- New admin permission nodes: `kilacraft.admin.health`, `kilacraft.admin.player`, `kilacraft.admin.audit` (OP only by default)
- Spark plugin added as a soft dependency; admin features auto-disable when Spark is absent without affecting other functionality. Paper 1.21+/Folia/Purpur/Leaf/Pufferfish bundles Spark built-in — no separate installation needed
- New `/kilacraft notify test` command to test Discord/DingTalk notification channel configuration
- Diagnostic report file output (Markdown format, auto-saved to `plugins/Kilacraft-AI/reports/`)

### 🔧 Improvements
- **Profile Analysis Prompt Temporal Optimization**: First-time analysis excludes temporary transient states; incremental analysis actively cleans up outdated temporary content, preventing short-lived activities from persisting across multiple versions
- **Self-Monitoring Text Logic Reuse**: Extracted `DiagnosticReportGenerator.appendSelfMonitoring()` as a public static method, `ServerHealthGuardian` delegates to it, eliminating duplicate code
- **Admin Skill Query Output Comprehensive Formatting**: All admin Skills (PlayerAnalysisSkill 6 actions, AuditLogSkill 3 actions) message output fully optimized — timestamp humanization (epoch→yyyy-MM-dd HH:mm), UUID→player name resolution (online cache + DB query two-level strategy), event type translation (PLAYER_LOGIN→Login), relationship strength leveling (numeric→Stranger/Acquaintance/Friend/Good Friend/Close Friend), improving AI secondary analysis input readability
- **AI Prompt Tone Unification**: Normal conversation and secondary analysis prompts unified to concise and plain style, consistent with greeting system. Removed "enthusiastic" / "like chatting with a friend" wording, synchronized config files and hardcoded default values
- **MySQL Connection Failure Auto-Fallback to H2**: Automatically falls back to H2 embedded database when MySQL is unavailable during startup and hot-reload, ensuring persistence functionality remains available
- **Audit Log Query Supports Querying Other Players**: `audit_log.query_logs` added to security whitelist, admins can query a specific player's skill usage logs
- **Hot-Reload Database Switch Resource Release Order**: Adjusted to close old Provider before creating new Provider, avoiding H2 TCP port conflicts
- **Hot-Reload DAO tablePrefix Refresh Consistency**: All modules holding DAOs rebuild them during hot-reload to reflect the latest table prefix
- **AI Greeting System Prompt & Injected Content Comprehensive Optimization**: Three-layer structured health alert output (abnormal metrics / involved plugins / hotspot methods + trigger paths), injected content perspective correction (AI perspective uses player name instead of "you"), last location world prefix, prompt rules comprehensive strengthening (rule #3 milestone generalization & vague description prevention), and full i18n audit governance

### 🐛 Bug Fixes
- **Fixed player profile version reset after hot-reload database switch (V8→V1)**: `reconcileOnlineProfiles()` did not reload profile from new database into memory cache, causing runtime state to overwrite new database's historical profile data
- **Fixed NPE in `initializeAdminSystem` / `syncGuardianState` when `databaseManager` is not initialized**

### ⚠️ Compatibility

#### Upgrading from v2.0.2
1. Stop server, replace JAR, start — no database migration needed
2. `admin.yml` config file is auto-generated on first startup
3. Spark plugin and a configured reasoning model API key in `admin.yml` are required for server health monitoring features
4. Admin management features default to OP-only access, no additional permission configuration needed
5. **`database.yml` profile analysis prompts updated** (new rule #5 for temporal relevance). To get the full prompt optimization effect:
   - Back up your custom config values in `database.yml`
   - Delete `database.yml` and restart the server to let the plugin regenerate the latest version
   - Manually merge your custom config values back into the new file
6. **`greeting.yml` greeting prompts optimized** (prompt rules comprehensively strengthened, injected content perspective corrected, health alerts structured). To get the full optimization effect:
   - Back up your custom config values in `greeting.yml` (and `greeting_en.yml` if using English mode)
   - Delete `greeting.yml` (and `greeting_en.yml`) and restart the server to let the plugin regenerate the latest version
   - Manually merge your custom config values back into the new file

#### New Permission Nodes
- `kilacraft.admin.health` (server health management, default OP)
- `kilacraft.admin.player` (player behavior analysis, default OP)
- `kilacraft.admin.audit` (audit log query, default OP)
- `kilacraft.admin.*` (all admin features, default OP)

---

## v2.0.2 - Group Server Data Isolation, Skill Permission Pre-Filter, Incremental Profile Analysis, Profile Historical Snapshots, Placeholder Arithmetic, JSON Auto-Repair

### ✨ New Features
- **Incremental Profile Analysis**: Profile analysis upgraded from "generate fresh each time" to "old profile + new conversations fusion update", long-term player traits are not overridden by short-term fluctuations
  - Prompts support Chinese/English bilingual configuration (first analysis / incremental analysis configured separately), supports hot-reload
- **Profile Historical Snapshots**: New `kca_profile_snapshot` table, automatically saves a profile snapshot after each analysis, supports tracing any version's profile content and analysis time range, permanently retained
- **Group Server Data Isolation**: Supports BungeeCord/Velocity cross-server data sharing and isolation
  - `database.yml` adds `group.server_id` config, requires deleting old config file to regenerate
  - Conversation history, server events, skill audit logs isolated by `server_id`; player profiles & social relations are inherently cross-server shared (no `server_id` field, not configurable)
  - Schema upgraded to v2, auto-creates `server_id` indexes (H2 / MySQL compatible)
  - Supports group config hot-reload
- **Skill Permission Pre-Filter**: Dynamically filters available skills by player permissions during intent recognition; unauthorized skills excluded from LLM candidates
  - `Skill` interface adds `getRequiredPermission()`, all built-in Skills implement permission declaration
  - `PluginPermissionEnum` adds 8 Skill-level permission enums
- **Placeholder Arithmetic Expressions**: Multi-step tasks support `{step_x.field + 10}` style arithmetic
- **LLM JSON Auto-Repair**: Auto-completes missing closing braces in AFK task callback JSON

### 🐛 Bug Fixes
- Fixed API_KEY unconfigured prompt pointing to wrong config file (`config.yml` → `llm.yml`)
- Fixed `getBoolean()` silently returning default for `"shared"`/`"isolated"` string values (Critical)
- Fixed `DatabaseManager.reload()` not updating `currentConfig` on hot-reload
- Fixed enchanted golden apple event name exceeding length limit
- Fixed SQL syntax incompatibility in expired conversation/event/audit log cleanup using derived table syntax
- Fixed `ProfileManager` not rebuilding DAO on hot-reload, causing `table_prefix` changes to not take effect
- Fixed `update()` overwriting profile analysis fields with cached stale values: `profile_data` / `profile_analyzed_at` now exclusively written by `updateProfileData()`, runtime state updates no longer overwrite AI analysis results
- Fixed `/ai clear` not preventing DB history reload on next conversation: introduced one-time `cleared` marker to skip DB loading in `loadHistoryIfNeeded`
- Fixed AI greeting writing to memory history causing `loadHistoryIfNeeded` to skip DB load: empty check now excludes assistant-only history
- Fixed AI request errors being silently swallowed without console output
- Unified AI call timeout settings, fixed profile analysis timeout inconsistency with other AI calls

### 🔧 Improvements
- Distributed scheduled task race safety: watermark markers + `SELECT FOR UPDATE` row locks for distributed mutual exclusion
- `database.yml` group config section moved up + unified comment style
- Removed `DataCleanupService.serverId` dead code
- Improved key field length documentation comments
- Removed unused parameter from social relation interaction type enum
- ConditionPlan operator descriptions migrated to `I18nService.tr()` system
- Profile injection extended to all player-facing LLM output paths (secondary analysis, stage notifications, broadcast) with unified `ProfileManager.injectProfileSummary()` API
- Player profile dimensions expanded from 5 to 8 (added interests/boundaries/communication/spatial/facts), profile injection wording changed to "reference context" to avoid interfering with AI conversation judgment, incremental analysis auto-migrates legacy fields
- Incremental profile analysis now filters out `version`/`analyzed_at` metadata fields when injecting old profile, preventing LLM misinterpretation
- SPI integration guide, database config guide, and player profile & social relations system guide updated in both Chinese and English (corrected table field descriptions to match actual DDL, added incremental analysis workflow and snapshot mechanism)
- Intent recognition prompt global optimization: strengthened skill semantic matching standard, clarified "data retrieved could help answer" ≠ "skill matches user intent", constrained question decomposition to user's explicitly stated information needs
- BukkitStatsSkill prompt narrowed: limited to Minecraft vanilla statistics only, prohibited guessing or fabricating enum names
- CommandSkill prompt optimization: clarified matching boundary between "executable action" and "information query", removed examples unsuitable for survival players
- LLM secondary analysis prompt enhanced with boundary constraints, reducing hallucination and unreasonable inference
- Third-party Skill registration adds compatibility pre-check mechanism, registration failure does not affect main process

### ⚠️ Compatibility

#### Upgrading from v2.0.1
1. Stop server, replace JAR, start — defaults to standalone mode, no config changes needed
2. Database Schema auto-upgrades to v2 (adds `profile_snapshot` table + 3 `server_id` indexes), no manual migration needed
3. All new config entries have code-level default values — **the plugin works correctly even without deleting any config files**
4. **Recommended** (to get the full v2.0.2 prompt optimization effects): delete the following config files and restart the server to let the plugin regenerate the latest versions:
   - `intent_prompts.yml` / `intent_prompts_en.yml` (intent recognition prompts strengthened with semantic matching standards)
   - `skills/bukkit/BukkitStatsSkill.yml` / `BukkitStatsSkill_en.yml` (narrowed stats query matching scope)
   - `skills/command/CommandSkill.yml` / `CommandSkill_en.yml` (optimized command matching boundary)
   - `llm.yml` (strengthened prohibition on exposing statistics, old version has similar rules but weaker wording, optional)
   - `database.yml` (new group server config section + incremental profile prompts + profile dimensions upgraded to 8 + profile timeout adjustment, can manually add or delete to regenerate)
5. For group server mode, add `group.server_id` config to `database.yml` (leave empty for single-server)
6. v2.0.2 adds `profile.incremental_system_prompt` / `incremental_system_prompt_en` config entries (incremental analysis prompt when existing profile is present), leave empty to use built-in defaults

#### Upgrading from v2.0.0
1. Follow the "Upgrading from v2.0.1" steps above first
2. v2.0.1 added configurable profile analysis prompts (`profile.analysis_system_prompt` etc. in `database.yml`), delete `database.yml` to regenerate if you want to customize prompts
3. If using English mode (`language: en`), a `greeting_en.yml` English greeting config file will be auto-generated on first startup

#### Upgrading from versions before v2.0.0
1. **You must read the v2.0.0 compatibility section first** — that version introduced database persistence, config architecture refactoring, and other major changes
2. Upgrade steps:
   - **Stop server and backup** the entire `plugins/Kilacraft-AI/` directory
   - Replace `Kilacraft-AI.jar` with the new version
   - On **first startup**, the plugin will auto-create new config files and database tables (Schema auto-upgrades to v2); old config files will not be deleted
   - Follow log prompts to manually migrate custom settings from the old `config.yml` to new config files (`llm:` → `llm.yml`, `agent:` → `llm.yml`, `output:` → `output.yml`, `knowledge:` → `knowledge.yml`)
   - If using MySQL, create the database in advance and configure `database.yml`
   - **Strongly recommended** to delete `intent_prompts.yml` / `intent_prompts_en.yml` and Skill config files under `skills/` directory to let the plugin regenerate the latest versions
3. Conversation history in versions <2.0.0 was stored in memory only — there is no historical data to migrate, data accumulation starts fresh after upgrade

#### New Permission Nodes
- `kilacraft.afk.task` / `kilacraft.bukkit_fx` / `kilacraft.bukkit_stats` / `kilacraft.bukkit_api` (default true)
- `kilacraft.cmi` / `kilacraft.command.execute` / `kilacraft.market.action` (default OP)
- `kilacraft.market.query` / `kilacraft.utility` (default true)

#### Version Recommendation
> Versions prior to v2.0.2 (including all v1.x releases) are no longer available for direct download. All users are recommended to upgrade to v2.0.2 for better system stability, security, and lower token consumption.

---

## v2.0.1 - AI Greeting Enhancement, Rare Event Collection, Global Market Query Expansion, Prompt Governance & Comprehensive i18n Upgrade

### ✨ New Features
- **AI Returning Greeting Enhancement**
  - Added Bukkit vanilla stats collection (32 items), covering Basic/Combat, Rare Bosses, Exploration/Distance, Life/Fun categories
  - Greeting data dimensions expanded: online/offline friend status (with world name & logout time), last session duration, global event activity, friend login frequency
  - Friend dynamics add PVP death perspective (PLAYER_PVP_DEATH); offline event descriptions enhanced (death grouping, per-achievement listing, money summary, repeat counting)
  - Greeting prompt requirements refined from 8 to 12 rules, with clear integer milestone trigger conditions for player stats
- **Server Event Collection Additions**: 4 new rare events (Ancient Debris, Tame Animal, Craft Enchanted Golden Apple, Build Wither) + PVP kill bidirectional recording
- **Global Market Query New `query_seller_items` Action**: Query items by seller name using GMP dedicated API, added to security isolation whitelist

- **Global Language Directive for AI**: Automatically injects language constraint directives into all LLM calls, preventing third-party SPI Skill multilingual data from interfering with AI output language consistency
- **Configurable Profile Analysis Prompt**: Profile analysis system prompt migrated from hardcoded to `database.yml` config file, supports Chinese/English bilingual, supports hot-reload (instant via `/kilacraft reload`)

### 🐛 Bug Fixes
- **Fixed global market transaction amount recorded as -1.0**: `TransactionEvent.getPrice()` returns default value, switched to `TransactionResultEvent` for real transaction data
- **Fixed player level-up info format reversal**: Events sorted by `created_at DESC`, original code reversed causing "leveled from 13 to 1"
- **Fixed Bukkit stats damage unit display error**: Half-hearts → hearts (values divided by 2)
- **Fixed Embedding initialization state anomaly**: Config was complete but `available` was not set to `true`, causing Embedding semantic retrieval to always downgrade to BM25
- **Fixed Embedding hot-reload disable not working**: When hot-reload set `enabled` to `false`, `KnowledgeRetriever` did not receive the disable signal and continued using Embedding retrieval
- **Fixed Chinese greeting.yml created during English mode startup**: Constructor unconditionally extracted Chinese config file, changed to language-aware delayed extraction
- **Fixed phantom profile reconciliation of offline players after hot-reload**: `reconcileOnlineProfiles()` only checked memory cache without verifying actual online status, causing offline players' stale profiles to be incorrectly reconciled

### 🔧 Improvements
- **Global Market Skill Prompt Governance**: Transfer action adds formula prohibition; fixed ghost reference `bukkit_api.get_player_balance` → `market_query.query_balance`; hints add mandatory balance real-time query rule
- **Global Intent Recognition Prompt Optimization**: `continuous_conversation` rule 4 adds balance absolute prohibition sub-rule
- **Greeting Format Optimization**: Session duration/logout time auto-converted to readable format; death messages require Chinese translation; unit conversion rules (minutes→hours, blocks→meters, half-hearts→hearts)
- **Greeting System Prompt Multilingual Support**: Greeting system default prompts migrated from hardcoded to dynamic retrieval, returning language-appropriate defaults; config loading changed to language-aware delayed extraction
- **Offline Data Aggregation Architecture Upgrade**: `OfflineEventAggregator` expanded to multi-dimensional aggregation; `ServerEventDao` adds 3 new query methods; `PlayerProfileDao` adds batch offline friend query; `FriendStatus` expanded to record with world name and session duration
- **Internationalization (i18n) Comprehensive Upgrade**: All hardcoded Chinese strings migrated to `I18nService.tr()` + `messages_en.yml` translation system, expanded coverage to: greeting prompt builder (265+ lines), 7 AFK task implementations, all 5 scheduled task execution logic, DatabaseManager, KilacraftAI, KnowledgeRetriever, ProfileAnalysisService, ProfileManager, TaskScheduler, SkillResult, CommandSkill, etc.; PluginLogger exception overload compatibility hardened; event count and time format concatenations consolidated into complete templates; standalone English config files (greeting_en.yml etc.) — removed redundant English comments from Chinese source files

### ⚠️ Compatibility

#### Upgrading from v2.0.0
1. **Stop server** and replace `Kilacraft-AI.jar` with the new version
2. **Start the server** — all new config entries have default values, the plugin uses built-in default prompts automatically
3. To customize profile analysis prompts, back up the current `database.yml` and delete it, then restart the server to let the plugin regenerate a new config file with the full default template, then modify as needed
4. If using English mode (`language: en`), a `greeting_en.yml` English greeting config file will be auto-generated on first startup
5. No database schema changes (version remains v1), **no database migration needed**

#### Upgrading from versions before v2.0.0
1. **You must read the v2.0.0 changelog first** — that version introduced database persistence, config architecture refactoring, and other major changes
2. v2.0.0 introduced a standalone config file system (`database.yml`, `llm.yml`, `greeting.yml`, etc.), some settings from the old `config.yml` have been migrated
3. Upgrade steps:
   - **Stop server and backup** the entire `plugins/Kilacraft-AI/` directory
   - Replace `Kilacraft-AI.jar` with the new version
   - On **first startup**, the plugin will auto-create new config files and database tables (Schema v1); old config files will not be deleted
   - Follow log prompts to manually migrate custom settings from the old `config.yml` to new config files
   - If using MySQL, create the database in advance and configure `database.yml`
4. Conversation history in versions <2.0.0 was stored in memory only — there is no historical data to migrate, data accumulation starts fresh after upgrade

---

## v2.0.0 - Database Persistence & Conversation History, Player Profiles & Social Relations, AI Login Greeting, Config Architecture Refactor, 8 New AFK Task Types, Global Market Action Skill, Utility Skill, Embedding Semantic Retrieval, Smart AFK Callback

### ✨ New Features
- **8 Brand New AFK Task Types**: AFK tasks expanded from 12 to 20 types, covering more gameplay scenarios
  - **Player Fish Watch**: Monitor your own fishing activity, automatically notify or trigger follow-up actions when you catch something
    - Example: "Tell me when I catch a fish and check the market price"
  - **Player Chat Watch**: Monitor your own chat messages, useful for codeword-triggered automation
    - Example: "When I say 'start grinding', execute the mob farm toggle command for me"
  - **Block Break Watch**: Monitor your own block breaking, trigger follow-up actions when mining specific ores
    - Example: "Tell me how many diamonds are in my inventory when I mine one"
  - **Entity Death Watch**: Monitor nearby entity deaths, useful for BOSS kill detection
    - Example: "Tell me when the Wither dies"
  - **Entity Spawn Watch**: Monitor nearby entity spawns, useful for mob farm efficiency monitoring
    - Example: "Tell me when a zombie villager spawns in the mob farm"
  - **Entity Explode Watch**: Monitor nearby explosions, useful for griefing alerts
    - Example: "Tell me the coordinates when there's an explosion"
  - **Furnace Smelt Watch**: Monitor furnace smelting completion, notify when all items are done smelting
    - Example: "Tell me when the furnace is done"
  - **Block Grow Watch**: Monitor crop maturity, useful for automated farm management
    - Example: "Tell me when the wheat is grown and check the market price"
  - All new task types support both notification-only and callback modes
- **Global Market Action Skill (MarketActionSkill)**: Brand new skill enabling AI to execute global market trading operations on behalf of players
  - Independent from the existing MarketQuerySkill (read-only queries), providing write-capable market operations
  - Auto-registers only when GlobalMarketPlus plugin is present
  - Supports 9 market operations:
    - **Search Item** (search_item): Search and open purchase GUI
    - **Sell Item** (sell_item): List held item for sale, with guided price confirmation
    - **Pickup Mail** (pickup_mail): Pick up all or specific mail items
    - **Buy Item** (buy_item): Place a buy order at a specified unit price
    - **Cancel Listing** (cancel_listing): Show active listings and delist selected items
    - **Transfer Money** (transfer_money): Transfer money to other players, large amounts require confirmation
    - **Auction Item** (auction_item): Auction held item with starting bid
    - **Sell Inventory** (sell_inventory): Bulk list all items of the same type from inventory
    - **Buy Inventory** (buy_inventory): Place bulk buy order at a specified unit price
  - All write operations executed via Bukkit commands, with GlobalMarketPlus plugin ensuring atomicity
- **UtilitySkill (Utility Skill)**: New utility skill providing three basic actions for flexible orchestration in multi-step tasks
  - **delay_wait**: Non-blocking delay of 1-60 seconds, uses a dedicated scheduler without occupying the I/O thread pool
    - Example: "Wait 10 seconds, then check the market price for me"
  - **notify_player**: Proactively notifies the player with LLM-generated summary of stage results via independent LLM call, with timeout protection and respect for server's stream output config
    - Example: "Check my balance and inventory first, then summarize the results to me, and then list the diamonds"
  - **broadcast_message**: OP admin-only feature, broadcasts AI-beautified messages server-wide (CHAT carrier), supports both single-intent and multi-step task orchestration
    - Example: "Help me write an announcement: Double XP weekend this week"
    - Multi-step orchestration: "Check online player list first, then broadcast an event notification for me"
- **Knowledge Base Embedding Semantic Retrieval**: Uses Embedding API to obtain text vectors, replacing BM25 algorithm with cosine similarity for semantic retrieval
  - Supports different Embedding providers (e.g. ZhipuAI, SiliconFlow, OpenAI), can differ from LLM provider
  - Vector cache persistence, avoids recomputing on every startup
  - Configurable minimum similarity threshold, vector dimensions, API timeout, etc.
  - Automatically degrades to original BM25 algorithm when unconfigured
- **Database Persistence Layer**: Supports both H2 embedded database (zero-config, works out of the box) and MySQL external database (multi-server data sharing)
  - HikariCP connection pool, supports `/kilacraft reload` hot-reload database type switching (auto-rollback on failure)
  - `database.yml` standalone config file (type/connection params/table prefix/data retention days)
  - Schema auto-migration, supports both H2 and MySQL dialects
- **Conversation History Persistence Service**: Player conversations automatically saved to database, persistent across restarts
  - Write-Behind async flushing: in-memory message queue → batch write every 30 seconds, threshold (≥20 messages) triggers immediate flush
  - Lazy Loading history: automatically loads conversation history from DB to memory on first message, seamless context continuation
  - Flush on player quit, ensures no data loss; expired data periodically cleaned (configurable retention days)
- **Player Profile System**: AI remembers each player's behavioral preferences and playstyle, providing more personalized service
  - Login stats (first login, total playtime, login count), behavioral preferences, playstyle, LLM analysis results all persisted
  - In-memory cache + DB async read/write + version-stamped anti-race, sub-second response
  - Auto-triggers LLM profile analysis on login/logout (triple gate: time interval + message count + sliding window)
  - Five profile dimensions: Playstyle, Personality, Preferences, Communication Style, Notes
  - Profile summary can be dynamically injected into AI system prompts, making AI understand players better
  - Example: "AI will remember a PvP-loving player and adjust its response style accordingly"
- **Social Relation Graph**: AI automatically tracks relationship strength between players, forming a social network
  - Three interaction sources auto-weighted: Private Chat (+0.01), TPA Teleport (+0.02), Skill Interaction (+0.005)
  - Daily relationship decay (5%) + weak relation auto-cleanup, keeping data fresh
  - Smart Skill log extraction: periodically scans whitelisted Skill player interactions, auto-enhances social relations
  - Watermark-based distributed safety mechanism (DB row lock `SELECT FOR UPDATE`), safe across multiple sub-servers
  - Example: AI greeting at login can mention "Your friend Steve is also online"
- **Server Event Collection System**: Automatically records milestone events on the server, reviewable after going offline
  - Player death, advancement completion, level-up automatically recorded to `kca_server_event` table
  - GlobalMarketPlus trade events (listing, sold, payment received) auto-recorded (seller's perspective)
  - Private chat and TPA commands auto-enhance social relations without extra config
  - Offline event aggregator: players can review server happenings during their absence on login
- **AI Login Greeting System**: AI automatically sends personalized greetings when players log in
  - First login: welcomes new players, introduces AI assistant capabilities and server info
  - Returning login: three-category data aggregation architecture
    - **Category 1**: Player's own offline events (market trades, achievements, level-ups, etc.)
    - **Category 2**: Friend dynamics during offline (JOIN player_profile for names, shows friends' milestone events)
    - **Category 3**: Summary stats (playtime/login count/days since join — only mentioned at significant milestones; last session highlights)
    - Example: "Welcome back! You've been offline for 3 days. Steve completed 2 achievements, and your diamonds sold out"
  - Cooldown mechanism (configurable), avoids spamming
  - `greeting.yml` standalone config file (toggle/prompt templates/cooldown/three-category event counts/server info)
  - New `OutputScenario.GREETING` scenario, output carrier independently configurable
- **Unified Task Scheduler**: All periodic background tasks centrally managed
  - `ManagedTask` interface + `TaskScheduler` unified manager, 5 managed tasks
  - CAS mutual exclusion + structured logging + runtime statistics
  - `/kilacraft tasks` command to view task status (requires `kilacraft.tasks` permission, default OP)
- **Skill Execution Audit Log**: All skill executions automatically recorded for retrospective analysis
  - Records: player UUID, skill name, action, parameters, execution result, elapsed time, trigger source
  - Async writes to `kca_skill_log` table, no impact on skill execution performance
  - Social relation extractor consumes this log table, auto-discovers player Skill interactions

### 🔧 Improvements
- **Built-in Enum Registry Replaces Knowledge Base Files**: Sound, particle, and statistic game enum data migrated from knowledge base Markdown files to a dedicated enum registry, providing more accurate retrieval, freeing knowledge base space, and faster loading
- **Custom I/O Thread Pool**: Replaces JDK's default ForkJoinPool.commonPool() with an adaptive bounded thread pool
  - Core threads = CPU cores, max threads = min(CPU×4, 128), queue capacity aligned with max threads
  - Safe rejection policy: discards tasks and logs warnings when full, never blocks the Bukkit main thread
  - CPU-aware auto-scaling: reasonable configuration from 2-core VPS to 64-core servers
  - Detailed initialization/shutdown logs (core/max/queue/server mode)
- **HTTP Connection Pool Optimization**: OkHttp ConnectionPool size aligned with IO_POOL max threads, preventing threads from blocking while waiting for connections
- **Smart AFK Task Callback Mechanism**: When callback tasks trigger, LLM can see the event trigger reason in the execution results area, producing more accurate responses
- **AFK Task First-Person Perspective**: When monitoring themselves, AI responds in first person ("You said: OK"), no longer describing from a bystander's perspective
- **Unified AFK Task Callback Event Description**: All 20 AFK task types use consistent event description format for both callback and notification paths
- **Intent Recognition Temporal Semantics**: LLM can correctly understand temporal expressions like "check the price after the wheat is grown" and "tell me when he comes online", placing follow-up actions in callback instead of parallel steps
- **AI Response Quality Optimization**:
  - No longer addresses players by ID in responses, more natural conversations
  - When skill execution fails, AI explains the failure naturally instead of triggering absolute prohibition
  - AI no longer exposes internal technical details (step IDs, raw enum values, statistics counts, etc.), all converted to player-friendly natural language
- **config.yml Comment Reading Experience**: All bilingual comments unified to "Chinese first, English second" continuous layout, more coherent reading regardless of language
- **Config Architecture Refactor**: 4 new independent config Managers replace the 847-line `config.yml`
  - `LLMConfigManager` → `llm.yml` (temperature/tokens/system prompt/Agent capabilities)
  - `OutputConfigManager` → `output.yml` (output pipeline/stream/sound effects/5 carrier scenario configs)
  - `KnowledgeConfigManager` → `knowledge.yml` (segmentation/BM25/Embedding/custom dictionary)
  - `GreetingConfigManager` → `greeting.yml` (AI greeting system config)
  - `ConfigManager` refactored to proxy pattern, consumers are unaware of the underlying split
- **Conversation Source Tagging**: All conversation records carry source identification
  - Supports 6 sources: Chat Listener, `/ai` Command, Plugin (console), Console, Login Greeting, AFK Callback
  - Auto-tags source when persisting AI responses, facilitating data analysis and troubleshooting
- **Hot-Reload Database Config**: `/kilacraft reload` supports hot-reloading database config (including H2/MySQL type switching), auto-rollback on failure
- **Shutdown Sequence Refactor**: Optimized plugin unloading flow to ensure zero data loss
  - New order: cancel scheduled tasks → flush remaining messages → write all player profiles → wait for IO pool completion → close database pool
- **Thread Safety Enhancement**: `ConversationManager.chatMode` upgraded from `HashMap` to `ConcurrentHashMap`
- **Stream Output Fix**: Fixed stream output carrier being hardcoded to `NORMAL_CHAT` for special scenarios, ensuring correct stream output for greetings etc.
- **SkillSecurityFilter Enhancement**: Added `getOnlinePlayerNames()` / `getOnlineUuidToName()` public APIs for social relation modules to safely access online player info from async threads
- **pom.xml New Dependencies**: HikariCP 5.1.0 (connection pool), H2 2.2.224 (embedded database), MySQL Connector/J 8.4.0 (provided), all relocated via Maven Shade to avoid conflicts

### ⚠️ Compatibility — Config Migration Guide

> **IMPORTANT: Fully backup your `plugins/Kilacraft-AI/` directory before upgrading!**

This version splits the 847-line `config.yml` into 6 independent files by config domain for better maintainability. On first startup, the plugin will automatically generate the following new files (with built-in defaults):
- `database.yml` (Database config, default H2 embedded, also supports MySQL external database)
- `llm.yml` (LLM API config + Agent capability config)
- `output.yml` (Output pipeline config, including sound effects)
- `knowledge.yml` (Knowledge base retrieval/segmentation/BM25/Embedding/custom dictionary)
- `greeting.yml` (AI greeting system config, disabled by default)

**Migration steps:**
1. Backup the entire `plugins/Kilacraft-AI/` directory
2. Start the server to let the plugin generate new config files
3. Refer to the table below and copy your custom config values from the old `config.yml` to the corresponding new files
4. Run `/kilacraft reload` to apply the migrated config
5. After migration, the `llm:`, `agent:`, `output:`, `knowledge:` sections in the old `config.yml` can be deleted (keeping them has no effect, the plugin no longer reads them)

**Config Migration Mapping Table:**

| Old Location (config.yml) | New File | New Location (key path unchanged) |
|---|---|---|
| `llm:` entire section | `llm.yml` | `llm:` |
| `agent:` entire section | `llm.yml` | `agent:` |
| `output:` entire section | `output.yml` | `output:` |
| `knowledge:` entire section | `knowledge.yml` | `knowledge:` |
| (New, no old config) | `database.yml` | `database:` |
| (New, no old config) | `greeting.yml` | `greeting:` |

**Remaining in config.yml (unchanged):**
- `settings:` (language, debug mode)
- `messages:` (AI message prefixes)
- `command_skill:` (command Skill security control)
- `afk_task:` (AFK tasks)
- `security:` (security config)

**New Database Tables (auto-created on first startup):**
- `kca_conversation`: Conversation history persistence
- `kca_player_profile`: Player profile data
- `kca_server_event`: Server event records
- `kca_social_relation`: Social relation strength
- `kca_skill_log`: Skill execution audit log
- `kca_watermark`: Watermark (distributed mutual exclusion)

**New Permission Node:**
- `kilacraft.tasks`: View scheduled task running status (default OP)

**New Dependencies (bundled in JAR):**
- HikariCP 5.1.0 (database connection pool)
- H2 2.2.224 (embedded database)

**Fully backward compatible**: Defaults to H2 embedded database (zero config), while also supporting MySQL external database. Existing functionality requires no changes.

---

## v1.5.0 - Comprehensive Internationalization, Prompt Optimization, Tokenization Noise Exclusion, Skill Registry Upgrade

### ✨ New Features
- **Comprehensive Internationalization (i18n) Architecture**: New I18nService with Chinese original text as key translation engine, all player output messages fully support multiple languages (built-in 821 lines English translation, complete English versions for Skill configs/knowledge base/prompts/vocabulary)
- **Tokenization Noise Exclusion Optimization**: Refactored ChineseTextUtil and EnglishTextProcessor, new TextProcessor interface supporting automatic Chinese/English tokenization strategy switching, unified text normalization
- **Sidebar Output Carrier Internationalization**: ScoreboardManager supports dynamic sidebar content generation based on player language, auto-pagination and multi-language character length adaptation
- **Skill Registry Optimization**: Updated skill-registry.html, optimized Skill metadata reporting logic, supporting real-time usage statistics and security review status display

### 🔧 Improvements
- **Prompt Optimization**: Streamlined intent_prompts.yml redundant descriptions, enhanced continuous conversation rules, improved LLM intent recognition accuracy
- **GenericLLMProvider Refactor**: Optimized HTTP connection pool, retry mechanism and streaming response processing, reduced first-character latency
- **Knowledge Base Format Optimization**: Unified sounds_particles.md and statistics.md structure, reduced minimum segment character size, improved BM25 retrieval matching rate
- **Configuration System Enhancement**: ConfigManager/IntentPromptConfigManager/SkillConfigManager support automatic detection and fallback mechanism for Chinese and English configuration files
- **Output Pipeline Internationalization**: KilacraftCommand, ChatListener, AIRequestHandler, AIResponsePipeline, SoundEffectManager and other core entries fully integrated with I18nService
- **AFK Task Internationalization**: 12 AFK task types and ConditionPlan/ConditionEvaluator support multi-language message output and threshold display
- **Core Component Optimization**: BukkitStatsSkill, BukkitAPIExecutor, KnowledgeBaseManager, KnowledgeRetriever, SkillIntentRecognizer, TaskExecutor, MarketQuerySkill, CMISkill, CommandSkill and others support multi-language formatted output and retrieval

### 📚 Documentation Updates
- **README / README.zh.md**: Version updated to 1.5.0, Bukkit API count 58 → 72
- **Changelog**: Chinese and English versions synchronized with new v1.5.0 record
- **All technical documents**: Version uniformly updated to 1.5.0, last update date synchronized to 2026-04-23
- **Skill Registry page**: Real-time update of Skill usage statistics and security review status (51 updates)

### ⚠️ Compatibility
- New `i18n/`, `knowledge/en/`, `internal/vocabulary/en/` directories (auto-created on first startup)
- All Skill configuration files add `_en.yml` English versions, new `intent_prompts_en.yml`, `language_en.yml`, `personalities_en.yml`
- `config.yml` adds `language` configuration item (default `zh`, supports `zh`/`en`)
- Fully backward compatible, defaults to Chinese, no configuration changes needed to run normally

---

## v1.4.6 - BukkitStatsSkill Statistics Query, Environment Perception APIs, Three-Layer Inventory Design

### ✨ New Features
- **BukkitStatsSkill Vanilla Statistics Query**: Query player Minecraft vanilla cumulative statistics (career records)
  - Knowledge base driven: `knowledge/statistics.md` provides BM25 retrieval for 80+ statistic enums
  - Supports four statistic types: UNTYPED (no param), ITEM (requires material), BLOCK (requires material), ENTITY (requires entity_type)
  - Smart formatting: distance auto-converts cm → meters/km, time auto-converts ticks → readable time
  - Multi-step data passing: returns statistic/value/statistic_type fields, supports AFK CUSTOM polling condition monitoring
  - 30+ common EntityType Chinese translations, Material reuses ItemTranslator
- **14 New BukkitAPI Configurations**:
  - **Three-layer Inventory Design**: Ultra-lightweight (inventory_usage) → Summary (inventory/ender_chest) → Container content (open_container)
  - **Environment Perception**: Feet block (feet_block), last damage cause (last_damage), world border (world_border)
  - **Combat Assistance**: Absorption hearts, arrows in body, invincibility ticks, fall distance
  - **Exploration Assistance**: Currently open inventory, compass target location
- **Full DamageCause Enum Translation**: 33 damage causes covered (1.16.5 compatible)

### 🔧 Improvements
- **Knowledge Base Driven Skill Pattern**: BukkitStatsSkill and BukkitFXSkill adopt unified architecture — knowledge base retrieval + LLM parameter passing
- **Knowledge Base Tokenization Optimization**: Unified text normalization, all whitespace characters (including newlines, tabs) normalized to single space, preventing HanLP tokenization anomalies
- **Skill Description Standard**: All query Skill action descriptions must declare returned data fields, ensuring multi-step task placeholder references
- **Hints Conflict Avoidance**: Clear boundary between "cumulative statistics vs current state", preventing conflicts with HP/hunger real-time state queries
- **Custom AFK Task String Threshold Support**: CUSTOM task condition_plan now supports string type thresholds (e.g., block name "GRASS_BLOCK"), fixing original NumberFormatException issue
  - ConditionPlan added thresholdStr field, distinguishing numeric/string comparison paths
  - ConditionEvaluator added extractStringValue/compareString methods, supporting equal/not_equal string operators
  - Log display optimization: string thresholds and current values display as readable names, avoiding LLM confusion

### 📚 Documentation Updates
- **Built-in Skills and Events Capability List**: Added BukkitStatsSkill and 14 new APIs
- **Bukkit API Reference**: Added 14 new API details
- **Server Owner Guide**: Added Skill Global Registry & Review Process section, Discord contact
- **Changelog**: Chinese and English synchronized
- **README**: Updated Bukkit API count 58 → 72, added Skill registry entry, Discord community entry, sponsor perks description, Skill review process

### ⚠️ Compatibility
- New `bukkit_stats` Skill (knowledge base `statistics.md` auto-loads)
- New permission node: `kilacraft.bukkit_stats` (default true)
- Fully backward compatible, no existing config changes needed

---

## v1.4.5 - Folia/lophine Thread Safety, Stream Output Feature, AI Response Pipeline Refactor, SIDEBAR Carrier

### ✨ New Features
- **Non-Cooperative Skill Security Filter**: Value scanning + sanitization for player data isolation
  - Interceptor always runs and cannot be skipped, independent of Skill parameter declarations (non-cooperative)
  - Directly scans all Values in entities, validates against online player names
  - Validation failure → Sanitization (auto-replace with current player name), Skill continues execution instead of blocking
  - Whitelist mechanism: config.yml controls which Skills/actions can operate on other players
  - Event-driven online player name cache (PlayerJoin/Quit), async thread-safe reads
  - Pre-filter: Minecraft player name regex validation, non-matching values skipped immediately
  - Built-in Skill whitelist: cmi.send_tp_request, AFKTask.create_task, command.execute_command
- **BukkitFXSkill Sound & Particle Effects**: AI can play sounds or show particle effects for players (only caller hears/sees)
  - 2 Actions: `play_sound`, `spawn_particle`
  - Natural language trigger: task completion celebration, warning prompts, atmosphere creation
  - YML configuration driven, supports hot reload
- **AI Response Sound Effect**: Automatically plays sound effect when AI starts responding, enhancing immersive experience
  - Sound synchronized with output content, only triggering player hears
  - Configurable toggle: `output.sound.enabled` (default ON), supports custom sound/volume/pitch
  - Supports all scenarios: normal chat/skill result/task result/AFK callback
- **Stream Output Functionality**: LLM responses display character-by-character in real-time, eliminating waiting anxiety
  - Immediately shows "Generating..." placeholder when request initiates, solving first-character latency
  - Real-time display of LLM returned content, supports 5 configurable carriers (CHAT/ACTION_BAR/BOSS_BAR/TITLE/SIDEBAR)
  - Optional keep final result in default carrier after stream completion
  - Configurable toggle: `output.stream.enabled` controls enable/disable
- **Unified AI Response Output Pipeline**: Refactored AI reply output architecture with scenario-level carrier configuration
  - 5 output carriers: CHAT (chat box), ACTION_BAR (above hotbar), BOSS_BAR (top bar), TITLE (screen center), SIDEBAR (right sidebar)
  - Normal chat/skill result/task result/AFK callback/error messages can independently configure carriers
  - Configuration is contract: uses exactly what you configure, no implicit degradation
  - Public broadcast carrier configuration: when `public_reply=true`, follows `default_channel` configuration
- **LLM Secondary Analysis Coordinator**: Brand new intermediate layer uniformly dispatches all LLM analysis outputs
  - Encapsulates complete analysis + output flow, callers don't need to handle stream/non-stream details
  - Supports AFK task callbacks without placeholder, active requests with placeholder
  - Eliminates duplicate output logic, all modules call through coordinator
- **Knowledge Base Retrieval in Intent Recognition**: Enables knowledge base enhancement during intent recognition for better skill identification accuracy
  - Intelligently extracts real user input, excludes prompt template interference
  - Combines server documentation (command guides/rules/gameplay) to assist intent judgment
- **Scoreboard Sidebar Output Carrier**: Brand new right sidebar output method, perfect for long AI responses
  - Supports up to 15 lines with 128 characters per line (Minecraft 1.13+)
  - Auto-pagination: automatically splits into multiple pages when exceeding 15 lines
  - Doesn't block game view, ideal for long AI responses (200-1500 characters)
  - Title auto-sync: directly uses `ai_prefix` configuration
- **bStats Anonymous Metrics Integration**: Collects skill usage distribution, request types, AFK task types and other anonymous usage data via bStats (can be disabled via metrics config option)
- **Folia/lophine Thread Safety Compatibility**: Full support for Folia and its branches region-based thread scheduling
  - Player-related APIs automatically execute in player's region thread
  - Zero impact on Spigot end, fully backward compatible
- **Independent Thinking Message Configuration**: `output.thinking_channel` independently controls "Thinking..." prompt output carrier

### 🔧 Improvements
- **Built-in Config File Management**: Encapsulated ConfigResourceUtil utility class, unified all config file reading and initialization logic
- **Logging System Standardization**: Fully adopted PluginLogger replacing scattered logging calls, structured tag output
- **Bukkit API Data Return Standardization**: Unified field naming for all API returned data, item names auto-translated to Chinese
- **Intent Classifier Removal**: Completely removed BM25 intent classifier, all requests go directly to LLM intent recognition for better accuracy
- **Handler Architecture Refactor**: Each Handler directly implements AIResponseHandler interface with clearer responsibilities
- **Package Structure Optimization**: Reorganized code by functionality (enums/manager/output/handler)
- **Configuration Streamlining**: Unified stream output configuration to `output.stream.*`, removed redundant `output.broadcast.*` and `settings.enable_stream_output`

### ⚠️ Compatibility
- Added `output/` configuration section (includes stream/default_channel/scenarios/boss_bar/title/sidebar/thinking_channel)
- New SIDEBAR output carrier
- Removed `output.broadcast.*` configuration section (public broadcast now uses default_channel)
- Removed `settings.enable_stream_output` configuration item (unified to output.stream.enabled)
- Fully backward compatible, default configuration maintains CHAT carrier

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
- Single-character query optimization: supports single-character queries like "bow", "sword" through custom dictionary and stop word checks
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
- **Server Owner Guide**: Added AFK Task System section (feature showcase, usage examples, command list)
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