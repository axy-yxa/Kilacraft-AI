package com.zm.kilacraftAI.skills.framework;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.*;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.IntentPromptConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.player.PlayerMetaCollector;
import com.zm.kilacraftAI.skills.framework.resume.PendingAction;
import com.zm.kilacraftAI.skills.framework.resume.PendingResume;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.skills.framework.task.TaskStep;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 技能意图识别器 - 使用 LLM 识别用户意图
 */
public class SkillIntentRecognizer {

    private final KilacraftAI plugin = KilacraftAI.getInstance();
    private final ConfigManager configManager;
    private final IntentPromptConfigManager promptConfigManager; // 提示词配置管理器
    private final Gson gson;
    private final SkillManager skillManager; // 用于获取所有技能的描述

    /**
     * Phase 1：构建精简 Skill 描述（仅 name + description，无 action/hints）
     *
     * @param caller 调用者（Player），null 表示控制台（控制台视为拥有所有权限）
     * @return 精简技能描述文本
     */
    private String buildPhase1SkillDescription(Player caller) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Skill skill : skillManager.getAvailableSkills(caller)) {
            sb.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Phase 2：构建过滤后的完整 Skill 描述（仅选中 Skill 的全量信息）
     *
     * @param caller             调用者（Player），null 表示控制台
     * @param selectedSkillNames Phase 1 选中的 Skill 名称集合
     * @return 过滤后的完整技能描述文本
     */
    private String buildPhase2SkillDescription(Player caller, Set<String> selectedSkillNames) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        // getAvailableSkills 已做权限预检，此处仅按选中集合过滤
        for (Skill skill : skillManager.getAvailableSkills(caller)) {
            if (!selectedSkillNames.contains(skill.getName())) continue;

            sb.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");

            if (!skill.getActions().isEmpty()) {
                sb.append(I18nService.tr("可用动作：")).append("\n");
                for (Map.Entry<String, String> entry : skill.getActions().entrySet()) {
                    sb.append("    - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            if (!skill.getHints().isEmpty()) {
                sb.append(I18nService.tr("提示：")).append("\n");
                for (String hint : skill.getHints()) {
                    sb.append("    - ").append(hint).append("\n");
                }
            }

            if (skill instanceof DynamicContextProvider dcp) {
                String dynCtx = dcp.getDynamicContext(caller);
                if (dynCtx != null && !dynCtx.isBlank()) {
                    sb.append(dynCtx).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 解析 Phase 1 响应 → 提取 skill_names 集合
     * 包含 Skill 名称校验，过滤不存在的名称并记录警告日志
     *
     * @param response Phase 1 LLM 响应文本
     * @return 选中的 Skill 名称集合（空集合表示无效意图）
     */
    private Set<String> parsePhase1Response(String response) {
        // Provider 错误响应（HTTP/异常/空响应）以 §c 开头，真实原因已由 handleError 的 WARN 覆盖。
        // 直接判为非技能请求优雅回退，避免误导性的"JSON 解析失败"日志。
        if (LLMResponseUtil.isErrorResponse(response)) {
            return Collections.emptySet();
        }
        try {
            // 从响应中提取 JSON
            String jsonStr = extractJson(response);
            if (jsonStr == null) return Collections.emptySet();

            JsonObject json;
            try {
                json = gson.fromJson(jsonStr, JsonObject.class);
            } catch (Exception parseError) {
                // LLM 生成嵌套 JSON 时常见错误：缺少闭合 }
                String repaired = JsonSafeGetUtil.repairJsonBraces(jsonStr);
                if (!repaired.equals(jsonStr)) {
                    try {
                        json = gson.fromJson(repaired, JsonObject.class);
                        PluginLoggerUtil.debug("意图识别", "JSON 自动修复成功");
                    } catch (Exception ignored) {
                        throw parseError;
                    }
                } else {
                    throw parseError;
                }
            }
            if (json == null) return Collections.emptySet();

            // {"skill_names": null} → 无效意图
            if (!json.has("skill_names") || json.get("skill_names").isJsonNull()) {
                return Collections.emptySet();
            }

            Set<String> result = new HashSet<>();
            for (var element : json.getAsJsonArray("skill_names")) {
                String name = element.getAsString();
                if (isValidSkillName(name)) {
                    result.add(name);
                } else {
                    PluginLoggerUtil.debug("意图识别", "Phase 1 返回了不存在的技能名称: {}，已忽略", name);
                }
            }
            return result;
        } catch (JsonSyntaxException | JsonIOException e) {
            // 真实解析失败（自动修复后仍失败）→ 升级 WARN，附原始响应片段方便定位，无需开 debug 即可见
            PluginLoggerUtil.warn("意图识别", "Phase 1 JSON 解析失败: {}。原始响应: {}", e.getMessage(), LogSnippetUtil.truncateForLog(response, 150));
            return Collections.emptySet();
        } catch (RuntimeException e) {
            // Gson 解析异常已在上方单独捕获，能走到这里的 RuntimeException 多为代码缺陷（如 NPE）。
            // 记录完整堆栈以便定位，同时保持原有降级语义（空集合 → 回退普通对话）。
            PluginLoggerUtil.error("意图识别", "Phase 1 意外异常", e);
            return Collections.emptySet();
        }
    }

    public SkillIntentRecognizer(ConfigManager configManager, IntentPromptConfigManager promptConfigManager, SkillManager skillManager) {
        this.configManager = configManager;
        this.promptConfigManager = promptConfigManager;
        this.gson = new Gson();
        this.skillManager = skillManager;
    }

    /**
     * 识别用户意图（两阶段：Phase 1 Skill 分类 → Phase 2 Action 选择 + 参数提取）
     *
     * @param userInput  用户输入
     * @param history    对话历史（用于上下文理解，可选）
     * @param playerName 当前玩家名称（用于提示词注入，可为 null 表示控制台）
     * @param caller     调用者（Player），用于权限预检过滤 Skill 描述，null 表示控制台
     * @return 识别结果（可能是 SkillIntent 或 TaskPlan，异步）
     */
    public CompletableFuture<Object> recognizeIntent(String userInput, Deque<ConversationManager.Message> history, String playerName, Player caller) {
        if (configManager == null) {
            return CompletableFuture.completedFuture(null);
        }

        // 每次都获取最新的实例
        LLMProvider llmProvider = plugin.getLlmManager().getCurrentProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(null);
        }

        // 构建用户提示词
        String userPrompt = buildUserPrompt(userInput, history, playerName);

        // 静默 Handler：意图识别不向玩家输出
        AIResponseHandler handler = buildSilentHandler("意图识别");

        // === Phase 1：Skill 分类（关闭知识检索） ===
        String phase1Skills = buildPhase1SkillDescription(caller);
        String phase1SystemPrompt = withPlayerMeta(promptConfigManager.buildPhase1SystemPrompt(phase1Skills), caller);

        PluginLoggerUtil.debug("意图识别", "Phase 1 Skill 分类开始");

        // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
//        PluginLoggerUtil.warn("意图识别", "Phase 1提示词: {}", phase1SystemPrompt);

        return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, phase1SystemPrompt, false, false, true).thenCompose(phase1Response -> {
            Set<String> selectedSkills = parsePhase1Response(phase1Response);

            // 快速路径：无效意图，不调 Phase 2
            if (selectedSkills.isEmpty()) {
                PluginLoggerUtil.debug("意图识别", "Phase 1 判定为非技能请求");
                return CompletableFuture.completedFuture(createInvalidIntent(I18nService.tr("非技能请求")));
            }

            // === Phase 2：Action 选择 + 参数提取（开启知识检索） ===
            String phase2Skills = buildPhase2SkillDescription(caller, selectedSkills);
            String phase2SystemPrompt = withPlayerMeta(promptConfigManager.buildSystemPrompt(phase2Skills, selectedSkills), caller);

            PluginLoggerUtil.debug("意图识别", "Phase 2 开始，选中技能: {}", selectedSkills);

            // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
//            PluginLoggerUtil.warn("意图识别", "Phase 2提示词: {}", phase2SystemPrompt);

            return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, phase2SystemPrompt, true, false, true).thenApply(phase2Response -> {
                PluginLoggerUtil.debug("意图识别", "Phase 2 完成");
                // 解析 LLM 响应
                return parseIntentFromResponse(phase2Response);
            });
        });
    }

    /**
     * 构建静默响应处理器：后台 LLM 调用（意图识别等）专用，不向玩家输出，仅 debug 日志。
     * logLabel 日志标识（同时作为 getPlayerName 返回值），返回静默处理器。
     */
    private AIResponseHandler buildSilentHandler(String logLabel) {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return null;
            }

            @Override
            public String getPlayerName() {
                return logLabel;
            }

            @Override
            public void showResponse(String response) {
                PluginLoggerUtil.debug("意图识别", "{} 结果: {}", I18nService.tr(logLabel), response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                PluginLoggerUtil.debug("意图识别", "{} 错误: {}", I18nService.tr(logLabel), errorMessage);
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };
    }

    /**
     * 强制技能意图识别：跳过 Phase1 分类，对指定技能跑 Phase2（动作选择 + 参数提取）。
     * 用于 /kila run 命令——玩家显式指定技能，绕过 Phase1 的不确定性。
     * 仅当目标技能在调用者的可用集合内时执行，与 /kila skills 列举、聊天触发同源。
     * 返回 SkillIntent（单意图，置信度置 1.0）或 TaskPlan（多步骤）。Phase2 提示词只暴露
     * 强制技能，产生的步骤必属该技能，由调用方交 TaskExecutor 执行。
     * userInput       玩家输入（参数提取来源）
     * history         对话历史（可选，用于上下文理解）
     * playerName      玩家名（提示词注入）
     * caller          调用者
     * forcedSkillName 强制的技能名
     * 返回 SkillIntent / TaskPlan；技能不存在/无权限/LLM 不可用/解析失败返回 null。
     */
    public CompletableFuture<Object> recognizeForcedSkill(String userInput, Deque<ConversationManager.Message> history, String playerName, Player caller, String forcedSkillName) {
        if (configManager == null || forcedSkillName == null) {
            return CompletableFuture.completedFuture(null);
        }
        // 校验技能在调用者可用集合内（与 skills 列举/聊天触发同源）
        boolean permitted = false;
        for (Skill skill : skillManager.getAvailableSkills(caller)) {
            if (forcedSkillName.equals(skill.getName())) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            return CompletableFuture.completedFuture(null);
        }

        LLMProvider llmProvider = plugin.getLlmManager().getCurrentProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(null);
        }

        String userPrompt = buildUserPrompt(userInput, history, playerName);
        Set<String> selected = Set.of(forcedSkillName);
        String phase2Skills = buildPhase2SkillDescription(caller, selected);
        String phase2SystemPrompt = withPlayerMeta(promptConfigManager.buildSystemPrompt(phase2Skills, selected), caller);

        PluginLoggerUtil.debug("意图识别", "强制技能 Phase 2 开始：{}", forcedSkillName);

        return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, buildSilentHandler("强制技能识别"), phase2SystemPrompt, true, false, true).thenApply(response -> {
            Object parsed = parseIntentFromResponse(response);
            if (parsed instanceof SkillIntent intent && forcedSkillName.equals(intent.getSkillName())) {
                // 玩家显式指定技能：置信度置 1.0 通过 isValid 校验，绕过 Phase1 不确定性
                return new SkillIntent(intent.getSkillName(), intent.getAction(), intent.getEntities(), 1.0, userInput);
            }
            if (parsed instanceof TaskPlan taskPlan) {
                // 多步骤任务：Phase2 提示词仅暴露强制技能，产生的步骤必属该技能，直接交调用方走 TaskExecutor
                return taskPlan;
            }
            PluginLoggerUtil.debug("意图识别", "强制技能 {} Phase 2 未返回有效意图", forcedSkillName);
            return null;
        });
    }

    /**
     * 待确认续体恢复分类。先做关键词短路（免 LLM），命中确认/取消直接返回；
     * 歧义或补值回复走单次 LLM 分类。返回 null 表示与待确认操作无关，由调用方落回正常意图识别。
     *
     * @param userInput 玩家本轮原始输入
     * @param slot      活跃续体
     * @return 恢复动作，或 null（无关 / 解析失败）
     */
    public CompletableFuture<PendingAction> classifyPendingResponse(String userInput, PendingResume slot) {
        if (slot == null) {
            return CompletableFuture.completedFuture(null);
        }
        // 关键词短路：明显的确认/取消词直接分类，免 LLM 调用
        PendingAction keywordMatch = classifyByKeyword(userInput, I18nService.isZh());
        if (keywordMatch != null) {
            PluginLoggerUtil.debug("意图识别", "待确认续体关键词短路：{} → {}", userInput, keywordMatch.getType());
            return CompletableFuture.completedFuture(keywordMatch);
        }
        LLMProvider llmProvider = plugin.getLlmManager().getCurrentProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(null);
        }

        String systemPrompt = promptConfigManager.buildPendingClassifyPrompt(slot.getSkillName(), slot.getAction(), slot.getMessage());
        AIResponseHandler handler = buildSilentHandler("待确认续体分类");

        PluginLoggerUtil.debug("意图识别", "待确认续体分类开始：{}.{}", slot.getSkillName(), slot.getAction());

        return llmProvider.processRequestWithCustomSystemPrompt(userInput, "IntentRecognizer", null, handler, systemPrompt, false, false, true).thenApply(this::parsePendingAction);
    }

    /**
     * 中文肯定词
     */
    private static final Set<String> AFFIRM_ZH = Set.of("确认", "确定", "是", "好", "好的", "对", "行", "可以", "没问题", "嗯", "执行", "就这样", "转吧", "同意");
    /**
     * 中文取消词（放弃）
     */
    private static final Set<String> CANCEL_ZH = Set.of("取消", "算了", "不要了", "放弃", "不用了", "作废", "不转了", "不买了", "不要", "别转了");
    /**
     * 英文肯定词
     */
    private static final Set<String> AFFIRM_EN = Set.of("yes", "ok", "okay", "confirm", "confirmed", "sure", "yeah", "yep", "do it", "go ahead", "affirmative", "proceed");
    /**
     * 英文取消词
     */
    private static final Set<String> CANCEL_EN = Set.of("cancel", "no", "nope", "never mind", "nevermind", "abort", "stop", "forget it", "don't", "do not");

    /**
     * 前导填充噪声（emmm/呃/啊/那个…）。不含"嗯"——"嗯"本身是肯定关键词。
     */
    private static final Pattern LEADING_NOISE = Pattern.compile("^(?:m+|em+|呃+|啊+|哦+|那个|那么|就)+", Pattern.CASE_INSENSITIVE);
    /**
     * 结尾标点（第一遍剥离，不含语气词——避免破坏"好的/算了"等已收录带尾词）
     */
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[!！。？?~\\s]+$");
    /**
     * 结尾语气词（第二遍剥离：吧/了/的/啊…），用于"确认吧/确认了"等命中 base 词
     */
    private static final Pattern TRAILING_PARTICLE = Pattern.compile("[吧了的啊呢哦啦哟哈呀呗]+$");

    /**
     * 关键词短路分类：明显的确认/取消词直接判定，免 LLM。按 zh 选词集，整句精确匹配（避免"不确认"误判）。
     * 补值类回复（如"转300"）返回 null，由调用方走 LLM。zh 由调用方从 configManager 传入以便测试。
     *
     * @param userInput 玩家原始输入
     * @param zh        是否中文词集
     * @return CONFIRM / CANCEL，或 null（需走 LLM）
     */
    static PendingAction classifyByKeyword(String userInput, boolean zh) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        Set<String> affirm = zh ? AFFIRM_ZH : AFFIRM_EN;
        Set<String> cancel = zh ? CANCEL_ZH : CANCEL_EN;

        // 归一化：小写 + 剥离前导填充 + 结尾标点
        String n = userInput.trim().toLowerCase();
        n = LEADING_NOISE.matcher(n).replaceFirst("");
        n = TRAILING_PUNCT.matcher(n).replaceAll("").trim();
        if (n.isEmpty()) {
            return null;
        }
        // 第一遍：原样匹配（覆盖 好的/算了 等已收录带尾形式）
        PendingAction first = matchKeyword(n, affirm, cancel);
        if (first != null) {
            return first;
        }
        // 第二遍：剥离结尾语气词后再匹配（覆盖 确认吧/确认了 → 确认）
        String stripped = TRAILING_PARTICLE.matcher(n).replaceAll("");
        if (!stripped.isEmpty() && !stripped.equals(n)) {
            return matchKeyword(stripped, affirm, cancel);
        }
        return null;
    }

    private static PendingAction matchKeyword(String n, Set<String> affirm, Set<String> cancel) {
        if (affirm.contains(n)) {
            return PendingAction.confirm();
        }
        if (cancel.contains(n)) {
            return PendingAction.cancel();
        }
        return null;
    }

    /**
     * 解析待确认续体分类响应。
     *
     * <p>包级可见以供单元测试直接覆盖 JSON 解析分支。</p>
     *
     * @param response LLM 响应文本
     * @return 恢复动作，或 null（none / 解析失败）
     */
    PendingAction parsePendingAction(String response) {
        // Provider 错误响应以 §c 开头，真实原因已由 handleError 的 WARN 覆盖；返回 null 落回正常识别。
        if (LLMResponseUtil.isErrorResponse(response)) {
            return null;
        }
        try {
            String jsonStr = extractJson(response);
            if (jsonStr == null) return null;

            JsonObject json;
            try {
                json = gson.fromJson(jsonStr, JsonObject.class);
            } catch (Exception parseError) {
                String repaired = JsonSafeGetUtil.repairJsonBraces(jsonStr);
                try {
                    json = !repaired.equals(jsonStr) ? gson.fromJson(repaired, JsonObject.class) : null;
                } catch (Exception ignored) {
                    json = null;
                }
            }

            if (json == null || !json.has("pending_action") || json.get("pending_action").isJsonNull()) {
                return null;
            }
            String action = json.get("pending_action").getAsString();
            return switch (action) {
                case "confirm" -> PendingAction.confirm();
                case "cancel" -> PendingAction.cancel();
                case "respond" -> PendingAction.respond(extractEntities(json));
                default -> null; // "none" 或未知 → 无关，落回正常识别
            };
        } catch (RuntimeException e) {
            // 真实解析失败（自动修复后仍失败）→ 升级 WARN，附原始响应片段方便定位
            // 动态内容 + 异常：先 tr() 翻译模板，再传 throwable 重载（否则 throwable 会被当作 {} 参数丢堆栈）
            PluginLoggerUtil.warn("意图识别", I18nService.tr("待确认续体分类解析失败：{}。原始响应: {}", e.getMessage(), LogSnippetUtil.truncateForLog(response, 150)), e);
            return null;
        }
    }

    /**
     * 在 system prompt 尾部追加玩家实时元数据块（位置/状态/装备等硬事实），
     * 帮助选 Skill、提参数与消歧义（如"帮我回去"需坐标判断是传送还是步行）。
     * 追加而非前置：意图识别的静态提示词（角色/技能列表/规则，跨所有玩家相同）保持为可缓存前缀。
     * 控制台（caller=null）或无元数据时原样返回。
     */
    private static String withPlayerMeta(String systemPrompt, Player caller) {
        if (caller == null) {
            return systemPrompt;
        }
        String meta = PlayerMetaCollector.collect(caller);
        return meta.isEmpty() ? systemPrompt : systemPrompt + "\n\n" + meta;
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userInput, Deque<ConversationManager.Message> history, String playerName) {
        StringBuilder prompt = new StringBuilder();

        // 注入当前玩家上下文（供 LLM 识别"我"、"自己"等指向自身的语义）
        if (playerName != null && !playerName.isEmpty()) {
            prompt.append(I18nService.tr("[当前玩家上下文]\n"));
            prompt.append(I18nService.tr("当前玩家名称：")).append(playerName).append("\n\n");
        }

        // 添加对话历史
        if (history != null && !history.isEmpty()) {
            prompt.append(HistoryUtil.buildHistoryDisplay(history, configManager, configManager.getAgentIntentHistoryCount()));
        }

        // 添加当前输入
        prompt.append(I18nService.tr("[当前输入]\n"));
        prompt.append(I18nService.tr("用户说：")).append(userInput).append("\n\n");

        // 添加指令
        prompt.append(I18nService.tr("请根据系统提示词中的规则分析用户意图，输出对应的 JSON 识别结果。"));
        return prompt.toString();
    }

    /**
     * 解析 LLM 响应（支持单意图和多步骤任务）
     */
    private Object parseIntentFromResponse(String response) {
        // Provider 错误响应以 §c 开头，真实原因已由 handleError 的 WARN 覆盖，优雅回退避免误导日志。
        if (LLMResponseUtil.isErrorResponse(response)) {
            return createInvalidIntent(I18nService.tr("非技能请求"));
        }
        try {
            // 提取 JSON 部分
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                return createInvalidIntent(I18nService.tr("无法解析响应为 JSON"));
            }

            JsonObject json;
            try {
                json = gson.fromJson(jsonStr, JsonObject.class);
            } catch (Exception parseError) {
                // LLM 生成嵌套 JSON 时常见错误：缺少闭合 }
                // 尝试自动修复：补全缺失的 }
                String repaired = JsonSafeGetUtil.repairJsonBraces(jsonStr);
                if (!repaired.equals(jsonStr)) {
                    try {
                        json = gson.fromJson(repaired, JsonObject.class);
                        PluginLoggerUtil.debug("意图识别", "JSON 自动修复成功");
                    } catch (Exception ignored) {
                        // 修复后仍然失败，用原始错误
                        throw parseError;
                    }
                } else {
                    throw parseError;
                }
            }

            // 检查是否是多步骤任务（包含 goal 和 steps 字段）
            if (json.has("goal") && json.has("steps")) {
                // 防护：LLM可能错误地把单意图包装成只有1个step的TaskPlan
                // 当只有一个step且skill_name不是多步骤场景时，降级为单意图处理
                if (json.getAsJsonArray("steps").size() == 1) {
                    try {
                        var stepObj = json.getAsJsonArray("steps").get(0).getAsJsonObject();
                        if (stepObj.has("skill_name") && stepObj.get("skill_name").isJsonPrimitive()) {
                            // 单步骤TaskPlan → 降级为单意图，避免isMultiStep()=false导致回退普通AI
                            String skillName = stepObj.get("skill_name").getAsString();
                            String action = stepObj.has("action") && stepObj.get("action").isJsonPrimitive() ? stepObj.get("action").getAsString() : null;
                            Map<String, String> entities = new HashMap<>();
                            if (stepObj.has("entities") && stepObj.get("entities").isJsonObject()) {
                                JsonObject entitiesObj = stepObj.getAsJsonObject("entities");
                                for (String key : entitiesObj.keySet()) {
                                    var value = entitiesObj.get(key);
                                    if (!value.isJsonNull()) {
                                        entities.put(key, valueToString(value));
                                    }
                                }
                            }
                            return new SkillIntent(skillName, action, entities, 0.95, "");
                        }
                    } catch (Exception ignored) {
                        // 降级失败，继续走正常TaskPlan解析
                    }
                }
                return parseTaskPlanFromResponse(json);
            }

            // 否则按单意图处理
            return parseSingleIntentFromResponse(json);
        } catch (JsonSyntaxException | JsonIOException e) {
            // 真实解析失败（自动修复后仍失败）→ 升级 WARN，附原始响应片段方便定位
            PluginLoggerUtil.warn("意图识别", "意图识别 JSON 解析失败：{}。原始响应: {}", e.getMessage(), LogSnippetUtil.truncateForLog(response, 150));
            return createInvalidIntent(I18nService.tr("解析失败：{}", e.getMessage()));
        } catch (RuntimeException e) {
            PluginLoggerUtil.error("意图识别", "意图识别意外异常", e);
            return createInvalidIntent(I18nService.tr("解析失败：{}", e.getMessage()));
        }
    }

    /**
     * 校验 skill_name 是否为已注册的合法名称（精确匹配）
     */
    private boolean isValidSkillName(String skillName) {
        if (skillName == null) return false;
        return skillManager.getSkill(skillName) != null;
    }

    /**
     * 解析单意图
     */
    private SkillIntent parseSingleIntentFromResponse(JsonObject json) {
        // 解析字段（需要检查是否为 JsonNull）
        String skillName = null;
        if (json.has("skill_name") && !json.get("skill_name").isJsonNull()) {
            skillName = json.get("skill_name").getAsString();
        }

        // 校验技能名称：不合法则直接返回无效意图
        if (skillName == null) {
            PluginLoggerUtil.debug("意图识别", "Phase 2 未返回 skill_name");
            return createInvalidIntent(I18nService.tr("技能名称无效"));
        }
        if (!isValidSkillName(skillName)) {
            PluginLoggerUtil.warn("意图识别", "Phase 2 返回了不存在的技能名称: {}，已拒绝执行", skillName);
            return createInvalidIntent(I18nService.tr("技能名称无效"));
        }

        String action = null;
        if (json.has("action") && !json.get("action").isJsonNull()) {
            action = json.get("action").getAsString();
        }

        double confidence = 0.0;
        if (json.has("confidence") && !json.get("confidence").isJsonNull()) {
            confidence = json.get("confidence").getAsDouble();
        }

        // 解析实体（支持嵌套 JSON 对象/数组，自动序列化为字符串）
        Map<String, String> entities = new HashMap<>();
        if (json.has("entities") && json.get("entities").isJsonObject()) {
            JsonObject entitiesObj = json.getAsJsonObject("entities");
            for (String key : entitiesObj.keySet()) {
                var value = entitiesObj.get(key);
                if (!value.isJsonNull()) {
                    entities.put(key, valueToString(value));
                }
            }
        }
        return new SkillIntent(skillName, action, entities, confidence, "");
    }

    /**
     * 解析多步骤任务计划
     */
    private TaskPlan parseTaskPlanFromResponse(JsonObject json) {
        try {
            String goal = json.get("goal").getAsString();
            TaskPlan plan = new TaskPlan(goal);

            // 解析步骤列表
            if (json.has("steps") && json.get("steps").isJsonArray()) {
                var stepsArray = json.getAsJsonArray("steps");
                for (var stepElement : stepsArray) {
                    JsonObject stepObj = stepElement.getAsJsonObject();

                    String id = stepObj.has("id") ? stepObj.get("id").getAsString() : "step_" + (plan.getStepCount() + 1);

                    String skillName = null;
                    if (stepObj.has("skill_name") && !stepObj.get("skill_name").isJsonNull()) {
                        skillName = stepObj.get("skill_name").getAsString();
                    }

                    String action = null;
                    if (stepObj.has("action") && !stepObj.get("action").isJsonNull()) {
                        action = stepObj.get("action").getAsString();
                    }

                    // 解析实体（支持嵌套 JSON 对象/数组，自动序列化为字符串）
                    Map<String, String> entities = new HashMap<>();
                    if (stepObj.has("entities") && stepObj.get("entities").isJsonObject()) {
                        JsonObject entitiesObj = stepObj.getAsJsonObject("entities");
                        for (String key : entitiesObj.keySet()) {
                            var value = entitiesObj.get(key);
                            if (!value.isJsonNull()) {
                                entities.put(key, valueToString(value));
                            }
                        }
                    }

                    // 解析依赖
                    List<String> dependsOn = new ArrayList<>();
                    if (stepObj.has("depends_on") && stepObj.get("depends_on").isJsonArray()) {
                        var dependsArray = stepObj.getAsJsonArray("depends_on");
                        for (var depElement : dependsArray) {
                            dependsOn.add(depElement.getAsString());
                        }
                    }

                    if (skillName != null && action != null) {
                        if (!isValidSkillName(skillName)) {
                            // 多步骤任务中某个步骤的技能名无效，跳过该步骤
                            PluginLoggerUtil.debug("意图识别", "多步骤任务中跳过无效技能名称: {}", skillName);
                            continue;
                        }
                        plan.addStep(new TaskStep(id, skillName, action, entities, dependsOn));
                    }
                }
            }
            return plan;

        } catch (RuntimeException e) {
            PluginLoggerUtil.error("意图识别", "解析任务计划失败", e);
            return null;
        }
    }

    /**
     * 从响应中提取 JSON
     */
    private String extractJson(String response) {
        if (response == null) return null;

        // 尝试查找 JSON 块
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.trim();
    }

    /**
     * 创建无效的意图
     */
    private SkillIntent createInvalidIntent(String reason) {
        PluginLoggerUtil.debug("意图识别", "创建无效意图：{}", reason);
        return new SkillIntent(null, null, new HashMap<>(), 0.0, reason);
    }

    /**
     * 从 JSON 提取 entities（支持嵌套 JSON 对象/数组，自动序列化为字符串）。
     * 供待确认续体分类的 respond 分支提取玩家本轮新给/改的字段。
     */
    private Map<String, String> extractEntities(JsonObject json) {
        Map<String, String> entities = new HashMap<>();
        if (json.has("entities") && json.get("entities").isJsonObject()) {
            JsonObject entitiesObj = json.getAsJsonObject("entities");
            for (String key : entitiesObj.keySet()) {
                var value = entitiesObj.get(key);
                if (!value.isJsonNull()) {
                    entities.put(key, valueToString(value));
                }
            }
        }
        return entities;
    }

    /**
     * 将 JsonElement 转换为字符串
     * 简单值直接返回字符串，嵌套对象/数组序列化为 JSON 字符串
     */
    private String valueToString(com.google.gson.JsonElement value) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        // JsonObject 或 JsonArray，序列化为 JSON 字符串
        return gson.toJson(value);
    }
}

