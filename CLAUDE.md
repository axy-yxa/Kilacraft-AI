# CLAUDE.md

> 本文件是 **Kilacraft-AI 的开发规范与项目导览**，面向在仓库内工作的开发者与 AI 协作代理。
> Claude Code 会在每次会话自动加载本文件；人类开发者也请在动手前通读。
> 它不是给服主/使用者的文档——那些在 `doc/` 下（见 §10）。

---

## 0. 一句话定位

Kilacraft-AI 是一个 **Minecraft LLM Agent 插件**：玩家在游戏内用自然语言对话，插件经两阶段意图识别路由到「技能（Skill）」执行，再用 LLM 做二次总结后输出；另有一套**守护系统（Guardian）**作为 AI 主动层，在玩家非即时感知的时机主动发声。支持知识库 RAG 检索、玩家画像、第三方插件集成，兼容 Spigot/Paper/Folia。

- **技术栈**：Java 17 · Maven · Bukkit/Paper/Folia（api-version 1.16）· OkHttp + Gson · HikariCP · H2/MySQL · HanLP（中文分词）· bStats · Lombok
- **主类**：`com.zm.kilacraftAI.KilacraftAI`（单例，`KilacraftAI.getInstance()`）
- **命令**：`/kilacraft`（别名 `kila` / `ai` / `zm`），文档统一用 `/kila`
- **分支**：`master` 为主干（PR 目标）；特性分支命名如 `future-<版本>-<日期>`
- **提交**：提交信息用中文（参考历史：`语言更新`、`国际化处理`）；**未经用户明确要求不要提交**；在 `master` 上改动前先开分支

---

## 1. 构建与验证

```bash
# 离线全量构建 + 测试（依赖已缓存时用 -o；首次或更新依赖去掉 -o）
mvn -o clean test
```

- **测试规模**：约 775 个单元测试（会持续增长），全部通过才算改动合格。JaCoCo 覆盖率报告生成在 `target/site/jacoco`。
- **超时**：surefire `forkedProcessTimeoutInSeconds=180`，单 JVM；`argLine` 含 `-Djdk.attach.allowAttachSelf=true`（JaCoCo agent 在 JDK 21 需要它）。
- **只跑某个测试类**：`mvn -o test -Dtest=KnowledgeRetrieverTest`
- **产物**：`target/Kilacraft-AI-<version>.jar`（shade 打包，已重定位 Gson/OkHttp/HikariCP/SLF4J/H2/bStats）；同时 `maven-assembly-plugin` 产出 `Kilacraft-Skill-API-<version>.jar`（第三方技能开发用的 SPI 包，见 §4）。
- **版本号**：改 `pom.xml` 的 `<version>`；`plugin.yml` 用 `${project.version}` 占位符自动跟随——两处不要写死不一致。
- **红线**：任何代码/资源改动后，提交前必须 `mvn -o clean test` 通过；只改注释也要编译验证。

---

## 2. 代码架构

### 2.1 启动与生命周期（`KilacraftAI.java`）

`onEnable()`（`KilacraftAI.java:225`）按严格顺序初始化：

1. `instance = this`（单例）→ `saveDefaultConfig()`
2. `initializeManagers()`（:251）：config → i18n → language → personalities → conversation → **数据库**（:268，含 EventCollector / Profile / SocialGraph / 持久化与定时任务；失败则 `databaseManager=null` 降级继续启动）
3. `initializeKnowledgeSystem()`（:326）：加载知识库 → **`new LLMManager()`**（:332）→ KnowledgeRetriever（注入 BM25 参数）→ buildChunkCache / computeAvgDocLength → 条件初始化 Embedding → 词典 → InternalEnumRegistry
4. StreamOutput → SoundEffect → **ResponsePipeline**（AIResponsePipeline）→ **LLMOutputCoordinator**（内含 LLMAnalysisService + **LLMBudgetManager**，全局 LLM 预算与熔断）
5. `initializeChatAndCommands()`（:663）：注册 `/kilacraft` + ChatListener + LoginGreetingHandler
6. MythicMobs 占位符反射注册（:691，需 JDK ≥21）
7. `initializeSkillsSystem()`（:715）：SkillConfig + IntentPrompt + **SkillManager + registerDefaultSkills**（:759）→ **SkillSecurityFilter**（:728）→ PendingResumeManager → IntentRecognizer → 延迟 20 tick 经 SkillRegistry 发现第三方 SPI 技能
8. Admin 系统（ServerHealthGuardian 三重依赖：DB + 开关 + 诊断模型；Spark 检测失败 2 分钟重试）→ **`initializeGuardianSystem()`**（守护系统：GuardianConfigManager + GuardianManager + GuardianEngine）→ Metrics → 启动横幅（含异步 UpdateChecker）

**`onDisable()`（:1014）顺序敏感**，违反会丢消息或竞态：先 cleanup 各 Manager → **`guardianEngine.shutdown` + `guardianManager.shutdown`（必须在 taskScheduler.shutdownAll 之前，停主动层避免新调度）** → `taskScheduler.shutdownAll()`（必须在 persistence flushAll **之前**）→ persistence.shutdown → profile.flushAll → **`FoliaCompat.shutdownIOPool()`（必须在 database.shutdown **之前**，否则异步写入因连接池关闭失败丢消息）** → database.shutdown → llm.shutdownAll → **`PendingResumeManager.clearAll()`**（高风险待确认操作绝不跨重启复活）。改 onDisable 务必保持这个顺序。

**soft-depend 条件加载**（`plugin.yml:18`）：GlobalMarketPlus → 市场技能 + 事件采集；CMI → CMI 技能；MythicMobs → 占位符（JDK21+）；Spark → 健康守护采样；Vault 仅声明、代码内无硬集成。

### 2.2 核心 AI 请求流水线（玩家消息 → AI 回复）

```
ChatListener（玩家聊天 / 连续对话模式 / 关键词触发）
  └─ 异步懒加载历史（ConversationPersistenceService.loadHistoryIfNeeded）
     └─ AIRequestHandler.handleAIRequest → buildPlayerContext(RequestContext) → handleAIRequestInternal
        ├─ [短路] PendingResumeManager 有活跃槽位 → classifyPendingResponse（关键词免 LLM / 单次 LLM 分类）→ handlePendingAction
        └─ runNormalRecognition → SkillIntentRecognizer.recognizeIntent（两阶段，.orTimeout(120s)）
              ├─ Phase 1：精简描述选 Skill 分类（静默 Handler，不开知识检索，JSON 输出）
              └─ Phase 2：全量描述选 action + 提取参数（开知识检索）→ SkillIntent（单意图）/ TaskPlan（多步）
           → dispatchIntentResult
              ├─ TaskPlan 且多步 → TaskExecutor（拓扑排序、占位符解析、算术固化）→ AnalysisSummary
              ├─ SkillIntent 且合法 → SkillManager.executeSkillByIntent → AnalysisSummary
              └─ 其他 → 回退普通 AI
           → LLMOutputCoordinator.outputAnalysisResult → LLMAnalysisService（二次分析，LLM Provider）
           → AIResponsePipeline.send（音效 + Markdown→MC + 载体路由）→ MessageDispatcher（CHAT/ACTION_BAR/BOSS_BAR/TITLE/SIDEBAR）
```

**关键咽喉与不变量**：
- **玩家数据隔离唯一咽喉**：`SkillManager.executeSkillByIntent`（`SkillManager.java:183`）调 `SkillSecurityFilter.sanitize`，拦截器**始终运行、不可跳过**（§6.4）。
- **后台 LLM 调用绝不打扰玩家**：意图识别 / 续体分类统一用 `buildSilentHandler`（`SkillIntentRecognizer`），`isStreamOutputEnabled()=false`。
- **§c 错误信号协议**（§2.4）。
- **`RequestContext`**（`AIRequestHandler` 内 record）：`name/player/history/sendResponse/sendError/scenario/isBroadcast/source/executionSource`。仅玩家流程构建；控制台 AI 对话路径**已移除**（历史决策，勿重建）。

### 2.3 线程与并发模型

- **IO 线程池** `FoliaCompat.getIOPool()`（`FoliaCompat.java:76`）：核心=CPU，最大=`min(CPU*4,128)`，队列满**丢弃 + warn（绝不阻塞主线程）**，daemon。所有 LLM / 知识库 / Embedding I/O、审计日志写库都走这里。
- **整条链路用 `CompletableFuture` 串联**：识别 → 分发 → 执行 → 二次分析 → 输出；关键阶段 `.orTimeout(120s/60s)`；`.exceptionally` 经 `formatAsyncError` 解包 `CompletionException` / `TimeoutException`。
- **LLM 请求**：`CompletableFuture.supplyAsync(..., getIOPool())`，OkHttp 同步 `execute()` 跑在 IO 线程；OkHttp 连接池容量 = IO_POOL 最大线程数。
- **在途请求取消**：`GenericLLMProvider.inFlightCalls`（UUID→Set<Call>），玩家下线 `cancelInFlight` 中断未完成调用（`GenericLLMProvider.java:96`）。
- **必须主线程/区域线程**：一切 Bukkit API（玩家操作、BossBar、Scoreboard、命令派发）经 `FoliaCompat.runTask`/`callSync`/`dispatchCommand`。**玩家绑定 API（`getTargetBlock` 等）在 Folia 必须用 `callSyncOnEntity`**（EntityScheduler），否则报 `getCurrentWorldData() is null`（`FoliaCompat.java:347`）。
- **配置热重载用 volatile + 不可变快照发布**（非 clear+put），避免读线程命中空/半 Map（典型：`OutputConfigManager.java:46,113`）。新增任何缓存型 Manager 遵循此模式。

### 2.4 §c 错误信号协议（关键不变量）

所有 LLM 错误返回**必须**经 `LLMResponseUtil.errorResponse(msg)` 构造，统一 `§c` 前缀；正常 LLM 自然语言回复不会以 `§c` 开头。靠 `LLMResponseUtil.isErrorResponse(...)` 判定：

- **错误响应不写对话历史**：`handleNormalAIRequest` 的 `thenAccept`（`AIRequestHandler.java:376`）命中 `isErrorResponse` 直接 return；意图识别各 `parseXxx` 命中直接判非技能/无效返回。这避免把错误串当真实回复污染历史、避免误导性「JSON 解析失败」日志。
- **新增任何 LLM 错误返回路径必须用 `errorResponse()`**，否则破坏此不变量。

---

## 3. 包组织结构

包根 `src/main/java/com/zm/kilacraftAI/`（约 216 个类）。

| 包 | 职责 |
|----|------|
| `(root)` | `KilacraftAI` 主类（生命周期、单例、技能注册、Spark/Admin 初始化） |
| `command` / `command/impl` | `/kilacraft` 命令分发（`KilacraftCommand`）+ 各子命令处理器（reload/clear/chat/knowledge/plugins/personalities/guardian/tasks/profile/notify/usage/history/memory/skills/run/doctor/about）+ TabCompleter |
| `common/enums` | 枚举（权限 `PluginPermissionEnum`、输出载体/场景（含 `OutputScenarioEnum.GUARDIAN`）、对话来源（含 `ConversationSourceEnum.GUARDIAN`）、权限（含 `PluginPermissionEnum.GUARDIAN`）等） |
| `common/util` | 工具类：`PluginLoggerUtil`（自带 i18n）、`LLMResponseUtil`（§c 协议）、`FoliaCompat` 委托等 |
| `compat/folia` | `FoliaCompat` + `FoliaReflection`：Spigot/Folia 调度抽象、IO 线程池、命令派发 |
| `compat/{cmi,globalmarketplus,mythicmobs}` | 第三方插件条件集成（市场/CMI/MythicMobs 占位符） |
| `config` | 各 ConfigManager（config/llm/output/knowledge/personalities/intent_prompts/greeting/admin/database/skill/**guardian**/**watch**），`LanguageManager` |
| `db` / `db/dao` / `db/model` / `db/service` | 持久化：`DatabaseManager`/`SchemaManager`（迁移）、H2/MySQL Provider、各 DAO、`ConversationPersistenceService`（write-behind） |
| `handler` / `handler/impl` | `AIRequestHandler`（请求编排）、`AIResponseHandler`/`PlayerResponseHandler` |
| `i18n` | `I18nService`（zh-key 翻译引擎）、`TextProcessorFactory`/`ChineseTextProcessor`/`EnglishTextProcessor`（关键词提取） |
| `listener` | 事件监听：`ChatListener`、`PrivateChatListener`、`TpaListener`、`AdminListener` |
| `llm` | `LLMManager`、`LLMProvider` 接口、`GenericLLMProvider`（SSE 流式、在途取消、思考模式治理） |
| `metrics` | bStats 统计（`MetricsBootstrap`） |
| `model/**` | 数据模型：`bukkit`/`event`/`greeting`/`knowledge`/`notification`/`profile`/guardian |
| `scheduler` | 定时任务调度（分布式水位锁、社交衰减、对话/事件清理） |
| `service/guardian(/monitor/action/predicate/primitives)` | **守护系统（AI 主动层，纯内置）**：`GuardianManager`（生命周期/调度）→ `GuardianEngine`（事件驱动评估）→ `Guardian`（单个守护者）→ `Monitor`（状态采集）/ `Predicate`（确定性触发判定）/ `Action`（发声动作）/ `TriggerSource`、`PlayerStateService`。与玩家请求流水线独立。精简后只保留 3 个内置 monitor + 冷却 + AFK 暂停，无自定义 monitor / 画像过滤 / 谓词注册表 |
| `service/playerwatch` | **跨玩家上下线订阅**：`PlayerWatchService`（内存订阅 + Bukkit Listener）。独立于守护系统（社交订阅 vs 自我守护） |
| `service/watch` | **玩家自定义监听系统（WatchSkill）**：`WatchService`（内存态监听 + 双模式 + 轮询定时器 + 延迟删除 + 事件 Listener 管理）、`WatchMode`（POLLING/EVENT）、`PlayerWatchListener`（每玩家一实例，12 事件）、`WatchEventTypes`/`WatchConstants`、`ProbeValue`/`ConditionEvaluator`（取值+判定）。轮询型执行 skill action，事件型监听 Bukkit 事件 |
| `service/conversation` | `ConversationManager`（内存历史，上限 100） |
| `service/{event,greeting,health,knowledge,notification,output,player,profile,translate,update}` | 各业务服务（事件采集、问候、健康监控、RAG、通知、输出管线、玩家实时元数据采集、画像、物品翻译、版本检查） |
| `skills/framework(/resume,task)` | 技能框架核心：`Skill`/`SkillProvider` SPI、`SkillManager`/`SkillRegistry`、`SkillSecurityFilter`、`SkillIntentRecognizer`、`TaskExecutor`、`LLMAnalysisService`、`PendingResumeManager` |
| `skills/{admin,guardian,playerwatch,watch,bukkit,cmi,command,globalmarketplus,utility}` | 内置技能实现（`skills/guardian` = 守护系统自然语言入口 `GuardianSkill`；`skills/playerwatch` = 跨玩家订阅入口 `PlayerWatchSkill`；`skills/watch` = 玩家自定义监听入口 `WatchSkill`） |

---

## 4. 技能（Skill）框架要点

- **SPI 接入**：第三方以 `Kilacraft-Skill-API` jar 作 compileOnly 依赖，在 `onEnable` 用 Bukkit `ServicesManager` 注册 `SkillProvider`。**版本兼容**：`SkillRegistry.discoverAndRegister`（`SkillRegistry.java:36`）运行期反射预检，旧版（< Kilacraft-Skill-API 2.0.2）会被跳过并 warn，**不是构建期检查**。同名冲突不覆盖。
- **Phase 2 动态上下文注入（内部增强，不进 SPI）**：`SkillIntentRecognizer.buildPhase2SkillDescription` 组装每个选中 skill 的提示词时，按 `instanceof DynamicContextProvider` 判断——实现的 skill 可向 Phase 2 提示词追加运行时动态内容（如 WatchSkill 注入可监听列表：遍历 `instanceof ProbeSource` 的 skill 输出可监听 action + 列出事件型监听类型）。`DynamicContextProvider` 是 `skills/framework` 下的**内部接口**，不在 SPI jar 白名单内（`src/assembly/skill-api.xml` 只含 Skill/SkillContext/SkillResult/SkillStatus/SkillIntent/SkillProvider 6 个 class），第三方无法访问。需要注入动态内容的内置 skill 自己实现该接口即可，无需改 `Skill` 契约。`ProbeSource`（声明可监听的只读 action）同理是内部接口不进 SPI。
- **权限过滤唯一真源**：`SkillManager.getAvailableSkills`（`SkillManager.java:107`）。意图识别提示词、`/kila skills`、`/kila run` 三处都必须调它，保证同源。
- **执行咽喉**：`executeSkillByIntent`（`SkillManager.java:160`）= 校验 → **安全消毒** → 执行 → 统计 + 异步审计写库 + NEED_INFO 续体捕获。
- **`SkillResult.message` 必须裸文本**（无 `[STATUS]` 前缀），前缀由归一化层统一加；`needInfo()` 是第三方实现「二次确认/补全信息」的官方契约。
- **Bukkit API 数据驱动技能**：`apis.yml`（`apis_<lang>.yml`）+ `BukkitAPIExecutor` 反射执行。**字段名三处同步**：`BukkitAPIExecutor.extractThreadSafeData`（Folia 数据提取）↔ `GenericBukkitAPISkill` 格式化 ↔ `TaskExecutor` 占位符 `{step_x.field}`——改任一处字段名必须同改另两处。主线程访问的 API 须登记 `MAIN_THREAD_METHODS`（`BukkitAPIExecutor.java:170`）。
- **NEED_INFO 续体**：`PendingResumeManager` 每玩家一槽位，`save`/`claim` 原子。多步任务续体**只重放当前那一个 step**，不重展开整个 TaskPlan——这是**有意设计**（§7）。

---

## 5. 国际化（i18n）规范 — 每次含中文改动必读

> 这是本仓库最高频的踩坑区。**凡是 Java 代码里新增或修改的硬编码中文字符串，必须走国际化；改了文案就必须同步翻译资源，否则非中文服的日志/提示会回退成中文。**

### 双体系（按「文案给谁看」分流）

| 场景 | 走哪套 | 写法 | 资源 |
|------|--------|------|------|
| 玩家在游戏里直接看到的命令提示/交互文案 | language.yml | `LanguageManager.getXxx()`，代码里**不出现**中文面量 | `src/main/resources/language.yml`（+ `language_en.yml`） |
| 控制台日志、临时消息、内部提示、技能返回文本 | I18nService + messages_xx.yml | `I18nService.tr(...)` 或经 `PluginLoggerUtil` 自动翻译 | `src/main/resources/i18n/messages_en.yml` |

`I18nService` 以**中文原文为 key**：zh 模式原样返回；非 zh 模式查表，**key 不在表里 → 回退中文**（这就是漏翻的来源）。

### PluginLoggerUtil 自带翻译（日志最常用）— 三规则

所有日志**必须**经 `PluginLoggerUtil`，**禁止直接 `plugin.getLogger()`**。它自动 `tr()` 消息、`trModule()` 模块名，但须按内容形态选对重载：

| 形态 | 正确 | 错误 |
|------|------|------|
| 静态（无变量） | `info("模块", "已加载 5 个技能配置")` | — |
| 动态（含变量） | `info("模块", "已加载 {} 个技能配置", count)` | `"已加载 " + count + " 个"`（拼接串匹配不上 key） |
| 动态 + 异常 | `error("模块", I18nService.tr("失败: {}", msg), e)` | `error("模块", "失败: {}", msg, e)`（异常被当 `{}` 参数，丢堆栈） |

新增日志**模块名**（第一参，如 `"LLM请求"`）也要登记到 `messages_en.yml` 顶部 `modules:` 段。

### 键写法

- key = 中文原文，**逐字符一致**（标点、全/半角、空格都要和代码里完全相同——一个字节不符就静默回退中文）。
- 动态内容用 `{}` 占位符（SLF4J 风格，按顺序填充）；英文翻译里 `{}` 顺序要与中文参数对应。
- 新增消息 → `messages:` 段对应模块分组下加一条（文件头约定「按代码调用位置顺序」）。

### 流程铁律

**任何代码变更或新增——只要出现新的或被修改的中文文案（玩家提示、日志、技能返回文本），就必须检查 i18n 并同步新增 key。** 这条对**方案设计 / plan 模式**同样生效：方案里一出现中文文案就标注「需同步 i18n」，实现时一并补齐。

### 提交前检查清单

- [ ] 新增/修改的中文字符串都走了 `LanguageManager` 或 `I18nService.tr` / `PluginLoggerUtil`。
- [ ] 玩家可见文案在 `language.yml`；日志/内部文本在 `messages_en.yml`。
- [ ] 每个 key 与代码逐字符一致，已补进资源文件。
- [ ] 动态文案用 `{}` 模板重载，没有字符串拼接。
- [ ] 动态 + 异常用了「先 `tr()` 再传 Throwable」。
- [ ] 新日志模块名已登记 `modules:` 段。

---

## 6. 代码与改动约定

### 6.1 文件改动：CRLF 行尾（禁 sed）

仓库 `autocrlf=true`，工作区为 **CRLF**。**禁止用 `sed -i` 等批量改文件**——会把 CRLF 转成 LF，制造整篇伪 diff。改文件用 Edit 工具（保留行尾）；必须批量替换时用「保留 CRLF 的 python 脚本」。同文件多处编辑分多条消息进行，避免 read-state 失效。

### 6.2 代码注释规范

- **不得**在注释里暴露方案设计/对话期间的过程标签：修复编号（F1-F20 之类）、优先级（P0-P3）、「供单元测试」「与改动前一致」「向后兼容」、事件日期叙述（如「堵死 X/XX 卡死」)等。
- 只写两类：(1) 非显而易见的算法/并发/安全设计理由；(2) 简短的不变量/行为说明。精简中文、维护者视角。
- **禁止类体级分隔注释**：方法与方法之间（含字段组之间）**不得**写 `// ===== text =====` / `// ==================== text ====================` / `// ---- text ----` 这类分隔横幅注释——它们用空行 + Javadoc 即可分隔，横幅纯属噪声。同类风格（无论等号、连字符、星号长度）一律不得出现在方法外。**唯一例外**：方法体内部分段可用此风格注释（如把一个长方法的若干步骤切开），但应尽量精简、能少则少。

### 6.3 命令前缀

所有命令示例与文档统一 `/kila`（子命令如 `/kila reload`、`/kila skills`、`/kila guardian` 守护系统管理）；`/kilacraft` `/ai` `/zm` 仅作别名列出（`/ai` 有冲突风险，不作示例）。`/kila plugins` **仅控制台可用**（玩家执行会被拒绝）。守护子命令需 `kilacraft.guardian` 权限。

### 6.4 玩家数据隔离（SkillSecurityFilter）

零信任、非合作式扫描：不信任 Skill 声明的参数名，直接扫 `entities` 的 Value，命中「其他已知玩家名（在线 `ONLINE_PLAYER_NAMES` + 近期活跃 `RECENT_PLAYER_NAMES`）且非自身、非白名单」→ 替换为当前玩家名。复合值（如命令文本）拆 token 逐个扫。**白名单 action（合法跨玩家操作，如转账/TPA）仅审计、不替换**——这是有意边界，不是疏漏（替换会破坏命令）。该拦截器始终运行、不可跳过。

### 6.5 Folia 兼容

**一切调度经 `FoliaCompat`，绝不直接 `Bukkit.getScheduler()`**（Folia 无全局主线程）。Folia 检测靠 `RegionizedServer` 类探测（非接口存在，避免 Paper 分支误判）。命令派发：Folia 下对 Player 用 `EntityScheduler`、非玩家用 `GlobalRegionScheduler`。

### 6.6 批量修改安全规范（事故预防 | 2026-07-25）

**背景**：一次跨 26 个文件的批量代码修改导致了 100+ 编译错误，用户通过 IDE 本地历史耗时恢复。根因是无约束的批量修改 + 无安全网。

**强制规则**：

1. **批量修改前必须先 snapshot**：任何涉及超过 3 个文件的代码修改操作，执行前必须跑 `mvn -o clean test` 确认当前基准通过，并提示用户 `git stash` 或 commit 保护未提交工作。确认后才可开始。

2. **出问题立即停止，禁止自行修复**：如果批量操作后 `mvn -o compile` 失败，第一条规则是**停止一切操作，报告用户**。绝不执行 `git checkout`、`git stash apply`、`git show :$f` 等对工作目录/暂存区有副作用的命令——这些操作的连锁影响对用户不可见，会毁灭性恶化局面。

3. **强制验证门槛**：每完成一个独立功能模块的代码修改后，必须 `mvn -o clean test` 通过。上一个模块验证未通过前，绝不进入下一个模块。

---

## 7. 关键设计约束 — 勿擅改（改动前先与维护者确认）

这些是经过深思的既有设计，看起来像 bug 或「可以更好」，但改了会破坏正确性或返工：

- **NEED_INFO 续体只重放单步**：多步任务遇到 NEED_INFO，玩家确认后只重放当前那一步，不重展开整个 TaskPlan。是否做多步断点续传待深度讨论，勿自行改成整 plan 重建。
- **知识库统一无结构、不加元数据**：召回靠内容优化 + 检索引擎调优（语料播种词典、软阈值、RRF），不引入结构化标签。
- **默认 BM25、不引入 Rerank**：目标用户是非技术服主，几乎无人开 Embedding。检索优化聚焦 BM25 默认路径。
- **IDF 用 `ln(1+x)`（BM25+ 形式）而非经典 `ln(x)`**（`BM25Scorer.computeIdf`）：后者在 df>N/2 时为负，会与外层 `score>0` 过滤冲突。`totalDocs≤0` 返回 1.0 等价不做 IDF。
- **软阈值无需「兜底返回 1 条」**（`KnowledgeRetriever.applySoftThreshold`）：数学保证最高分过噪声地板时必返回 ≥1 条，别画蛇添足。
- **`DatabaseManager.createProvider` 先 initialize 再发布 volatile**：否则 reload 期间异步刷盘拿到半成品 Provider 抛 NPE。
- **H2/MySQL 双兼容 SQL 约束**：`value` 列名反引号、`UPDATE`-first + `INSERT`-fallback（不用 `ON DUPLICATE KEY UPDATE`）、派生表包裹 `LIMIT`、`INFORMATION_SCHEMA` 查索引存在性。改任何 SQL 必须两库都验。
- **共享表 vs 隔离表 + 水位策略两类**：`player_profile`/`social_relation` 跨服共享（无 server_id）；`conversation`/`server_event`/`skill_log` 按子服隔离。清理/衰减用全局水位（替所有子服执行）；社交提取用带 server_id 后缀水位（各子服独立）。改分布式定时任务前先确认用哪类。
- **画像分析 prompt 红线**：严禁记录任何会变的数值快照（余额/库存/坐标/在售商品等），只记定性关系与稳定倾向。改 prompt 要同步 yml 和代码两份。
- **§c 错误信号协议**（§2.4）：所有 LLM 错误返回走 `LLMResponseUtil.errorResponse()`。
- **`language.yml` 的 `commands:` 段 key 必须无前缀嵌套写**（段内 `reload-success:`，读取为 `commands.reload-success`）；带 `commands.` 前缀的扁平 key 在 Bukkit `getString` 下静默失败、服主改了不生效。
- **H2 TCP Server 默认无认证**：`allow_others=true` 会暴露全部数据，仅可信内网调试用。
- **守护系统（Guardian）——纯内置 AI 主动看护**：
  - **常驻内置 monitor 专用框架（勿加回通用 monitor 抽象）**：`Monitor` 是为「常驻、不可取消、无目标达成、无永久死亡」的内置提醒单元量身写的——只有 ACTIVE/PAUSED 两态 + 内部 `cooldownMillis`/`lastFireMillis` 防刷屏。**不要加回** Policy 枚举、MonitorState 状态机、Outcome 多态、TriggerSource/GuardianAction 接口、maxRetries/退避预算/goalPredicate——那些是为已删除的「自定义 monitor」准备的，内置场景不需要。动作失败（玩家离线/守护被关/LLM 失败/预算熔断）一律视为本轮没触发，下轮照常——monitor 永不因失败死亡。
  - **触发方式内化为字段**：轮询型（`cadenceTicks>0`，引擎心跳按 cadence 驱动 eval）或事件型（`eventType`+`eventFilter` 非空，引擎在事件命中 filter 后即时求值 triggerPredicate）。有 triggerPredicate → 边沿触发（假→真才开火）；无 → 每轮到点即触发。不再用 Policy 枚举区分。
  - **谓词层零 LLM**：`Predicate` 必须是确定性代码（纯函数判定 PlayerState → bool），**绝不调用 LLM**；LLM 只在 `GuardianLlmAction` 发声阶段出现。只保留 3 个内置谓词原语（`InventoryFreeSlotsPredicate`/`InventoryOpenPredicate`/`ThreatOutOfViewAndNearPredicate`），无注册表、无 LLM 可发现谓词。
  - **动作统一 LLM 输出**：所有内置 monitor 共用 `GuardianLlmAction`（具体类，不经接口）——LLM 能根据实体类型个性化措辞，比固定模板自然。返回 Boolean（true=已发声，false=本轮跳过）。
  - **价值轴：只对玩家非即时感知、且窗口宽于 LLM 延迟的内容发声**：守护只在玩家「当下没盯着、但事后会在意」的时机主动发声（视野外威胁锁定、罕见生物生成、背包快满）。已感知抑制：玩家正打开容器时跳过背包快满告警。**玩家挂机时彻底暂停守护**。
  - **窗口 > LLM 延迟原则（有意裁剪，勿补回）**：苦力怕点燃（1.5s）、投射物飞行（1-2s）等窗口短于延迟的场景**有意不做**；破门有游戏音效即玩家可感知，也不做。内置只保留威胁锁定与稀有生物。
  - **挂机暂停（AFK pause）**：`PlayerActivityTracker` 判定挂机（默认 5 分钟无操作）。挂机时 `GuardianEngine` 的 `tickPlayer`+`submitSignal` 双入口跳过 eval——pause 而非 disable。
  - **默认套餐 Java 硬编码**：`/kila guardian on` 启用 3 个 monitor（事件型 2 个 + 轮询型 1 个）由 `BuiltInMonitors` 硬编码，不开放配置。新增 monitor 改 Java 代码发版。
  - **opt-in 纯内存态、不持久化**：守护开关存 `GuardianManager` 内存 map，与 WatchSkill/PlayerWatch 同模式——重启/重登默认关。**无 `guardian_profile` 表**（已删）；跨服共享 opt-in 语义不成立（生存服开、小游戏服无监听场景）。
  - **无治理层**：不再有告警优先级、分类、冷却中心、静音/安静点功能。防刷屏完全由每个 monitor 自身的 `cooldownMillis` 承担。玩家不想被骚扰直接关守护。GuardianSkill 只有 enable/disable/status 三个 action。
- **玩家自定义监听（WatchSkill）独立于守护系统**：玩家指定监听条件（如"盯铁锭到64个""BOSS刷新了提醒我"）由 `WatchSkill` + `WatchService` 实现，不走守护架构。**两类监听来源**（按触发机制分）：**轮询型（POLLING）**——定时执行内置 skill 的只读查询 action 取返回值字段比较（复刻旧挂机任务系统 CUSTOM 类型的成熟执行链路：直接 `skill.execute` 带 5s 超时 + SkillSecurityFilter 消毒，绕过审计表）；**事件型（EVENT）**——`PlayerWatchListener`（每玩家一实例）监听 12 种高价值 Bukkit 事件（熔炉烧好/作物成熟/实体死亡/生成/爆炸/玩家死亡/传送/升级/换世界/方块破坏/钓鱼/聊天），命中 filter 即触发。事件归属三模式：玩家自身（O(1)）、击杀者归属、坐标距离（世界名粗筛+distance）。**触发后只通知 AI，不做回调执行**（复用 `LLMOutputCoordinator.outputAnalysisResult`）。取值类型 number/boolean/string 运行时自动识别（`ProbeValue.from`）。监听**内存态、不持久化**——下线暂停 + 延迟删除窗口。完成写 `server_event`（`PLAYER_WATCH_TRIGGERED`）。**放弃第三方 SPI Skill 自动注入**（风险不可控）——只支持内置 skill 的 `ProbeSource` 标注 action（内部接口不进 SPI）。分类上限：轮询≤3/事件≤5 + 全局 200 + 同玩家轮询合并为单定时器。CAS 防重入 + 连续失败 3 次自动删除。旧系统技术细节已融入：熔炉末次烧炼判定（source.amount<=1）、作物 getNewState()+Ageable maxAge 成熟度判定、EntityExplode entity null 防护、PlayerFish state 过滤、聊天 keyword 包含匹配。
- **跨玩家监控（PlayerWatch）独立于守护系统**：玩家订阅其他玩家上下线通知由 `PlayerWatchSkill` + `PlayerWatchService` 实现，不走守护架构。订阅**内存态、不持久化**——订阅者下线自动清空（上限 5 个/玩家）。通知走统一 `LLMOutputCoordinator.outputAnalysisResult(... GUARDIAN ...)` 输出载体。
- **`SkillResult.data` 不进 LLM，仅用于 step 间传递与 Watch 条件评估**：`SkillResult.success(message, data)` 中 `data` 的消费者仅有两处：(1) `TaskExecutor.resolvePlaceholders()` 做多步任务占位符解析（`{step_1.field}`）；(2) `WatchService` 做轮询型 Watch 的阈值比较。**data 绝不会被拼入 LLM 提示词**。LLM 二次分析经 `AIRequestHandler.outputSingleSkillResult()` → `AnalysisSummary.addResult(status, message)` → `buildPrompt()`，全程只取 `message` 字符串，`AnalysisSummary` 连 `data` 字段都没有。回退普通 AI 同理（`SkillResultFormatter.toLlmText(status, message)`）。因此需要 LLM 解读的内容（搜索结果摘要、网页正文、诊断报告全文等）**必须手动拼进 `message`**。参见 `ServerHealthSkill.executeReadReport()`（行 481-483 注释）、`WebSearchSkill.buildSummary()`、`WebFetchSkill.buildResult()`。
- **WebFetch SSRF 防护三件套（有意设计，勿回退）**：`WebFetchSkill` 的 SSRF 防护由三个互补机制组成，缺一不可——(1) **IP 检查内嵌 DNS 解析**：`SSRF_GUARD_DNS`（静态 `Dns` 实例）在 OkHttp 取 IP 时同步校验内网/回环/链路本地地址，校验失败的地址以 `UnknownHostException` 抛出。**OkHttp 实际连接的 IP 就是校验通过的 IP**，从根上消除「校验一次、连接另一次」两次独立 DNS 解析之间的 DNS rebinding（TTL=0 攻击者中途切 IP 到内网）TOCTOU 窗口。**禁止改回 `InetAddress.getAllByName` 预检查 + OkHttp 默认 DNS 连接**的写法。(2) **响应体字节级硬限制**：`readBodyWithLimit` 用 Okio `source.readByteArray(maxBytes+1)`，峰值内存严格受 `max_body_size_mb` 限制（默认 2MB）。**禁止改回 `response.body().string()` 全量读取后 `substring` 截断**——后者峰值内存不受控，超大页面可致 OOM（VPS 场景致命）。(3) **强制 HTTPS**：`normalizeScheme` 把 `http://` 升级为 `https://`（SSRF 开启时），`checkScheme` 随之收紧协议白名单为只允许 https，防降级/中间人注入。SSRF 检查（含 DNS）**全部走异步**（`doFetch` 内），不在 `execute()` 同步路径执行——避免阻塞 `SkillManager.executeSkillByIntent` 调用栈（可能在主线程/区域线程）。

---

## 8. 发布与版本

- **双仓库镜像**：GitHub + Gitee 同步发版。**版本检测按 i18n 语言选源**：中文 → Gitee，其他 → GitHub（`UpdateChecker`）。Gitee API 缺字段需做兼容（下载 URL 按 tag 合成、日期回退 `created_at`）。
- **版本号**：`pom.xml` 的 `<version>` 是唯一真源；`plugin.yml` 用 `${project.version}` 占位符。发版三处对齐：pom 版本 → 更新日志中文（`doc/文档/更新日志.md`）→ 更新日志英文（`doc/en/Changelog.md`），格式与历史版本一致，站在使用者/服主角度精简撰写，勿堆技术细节。
- **`/kila reload`** 级联所有 ConfigManager（含 `guardian.yml` → `GuardianConfigManager`）；重载前快照 `isChinese`/`isEmbeddingEnabled`，变更则全量重建知识库（分块 + BM25 统计 + Embedding 缓存），并调 `TextProcessorFactory.reset()`、`LLMBudgetManager.refreshBudget()`（重载全局 LLM 预算与熔断阈值）。

---

## 9. 文档边界（东西放哪）

| 位置 | 内容 | 受众 |
|------|------|------|
| `CLAUDE.md`（本文件） | 开发规范、架构导览、约定、勿改约束 | 开发者 + AI 协作代理 |
| `doc/文档/` | 功能文档（服主指南、配置指南、架构详解等）+ `文档索引.md` | **使用者**（服主/玩家/第三方开发者） |
| `doc/en/` | 上述文档英文版 | 国际使用者 |
| `wiki/` | 内部设计文档、调研报告、待实施方案 | 维护者内部 |
| `src/main/resources/` | 配置默认值（config/llm/knowledge 等 yml）+ i18n（language.yml、i18n/messages_en.yml）+ plugin.yml | 运行时资源 |

**勿把开发规范塞进 `doc/`**——那是给使用者的；开发规范只放本文件。

---

## 10. 何时回来更新本文件

CLAUDE.md 会随项目演进而过时。出现以下情况**必须**同步修订本文件：

- **架构/流水线变更**：新增/移除核心阶段（如改两阶段意图识别、新增请求入口、调整 onEnable/onDisable 顺序与依赖）。
- **新增顶层包或大改包职责**（§3）。
- **i18n 体系变更**：新增语言包、改翻译引擎、`PluginLoggerUtil` 翻译规则变化、新增配置文件。
- **构建/测试方式变更**：换 JDK 版本、改构建命令、测试基础设施变动（如不再用 JaCoCo、surefire 配置大改）。
- **新增或废除「勿改」约束**（§7）：做出新的有意设计决策、或某项约束已解除。
- **发布流程变更**（§8）：仓库迁移、版本检测逻辑变化、发版步骤调整。
- **命令/权限模型变更**：新增子命令、改前缀约定、调整权限节点结构。
- **Folia 兼容策略变更**：调度抽象重写、检测方式变化。

**修订原则**：保持精简可扫描（本文件每次会话都会加载进上下文）；引用关键 `file:line` 便于定位，但行号会漂移——优先描述「类 + 职责」，行号作辅助。改完跑一遍 `mvn -o clean test` 确认描述与现状一致。
