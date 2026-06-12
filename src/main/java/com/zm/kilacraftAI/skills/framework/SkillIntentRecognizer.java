package com.zm.kilacraftAI.skills.framework;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.HistoryUtil;
import com.zm.kilacraftAI.common.util.JsonSafeGetUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.IntentPromptConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.skills.framework.task.TaskStep;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
        for (Skill skill : skillManager.getAllSkills()) {
            // 权限预检
            String requiredPermission = skill.getRequiredPermission();
            if (requiredPermission != null && caller != null && !caller.hasPermission(requiredPermission)) {
                continue;
            }
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
        int skippedCount = 0;
        for (Skill skill : skillManager.getAllSkills()) {
            // 过滤非选中 Skill
            if (!selectedSkillNames.contains(skill.getName())) continue;

            // 权限预检
            String requiredPermission = skill.getRequiredPermission();
            if (requiredPermission != null && caller != null && !caller.hasPermission(requiredPermission)) {
                skippedCount++;
                continue;
            }

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
        }

        if (skippedCount > 0) {
            PluginLoggerUtil.debug("意图识别", "Phase 2 权限预检过滤：跳过了 {} 个无权限的 Skill", skippedCount);
        }

        return sb.toString();
    }

    /**
     * 获取所有可用 Skill 的名称集合（经过权限预检过滤）
     */
    private Set<String> getAllSkillNames(Player caller) {
        Set<String> names = new HashSet<>();
        for (Skill skill : skillManager.getAllSkills()) {
            String perm = skill.getRequiredPermission();
            if (perm != null && caller != null && !caller.hasPermission(perm)) continue;
            names.add(skill.getName());
        }
        return names;
    }

    /**
     * 解析 Phase 1 响应 → 提取 skill_names 集合
     * 包含 Skill 名称校验，过滤不存在的名称并记录警告日志
     *
     * @param response Phase 1 LLM 响应文本
     * @return 选中的 Skill 名称集合（空集合表示无效意图）
     */
    private Set<String> parsePhase1Response(String response) {
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
                        PluginLoggerUtil.debug("意图识别", I18nService.tr("JSON 自动修复成功"));
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
                    PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 1 返回了不存在的技能名称: {}，已忽略"), name);
                }
            }
            return result;
        } catch (Exception e) {
            PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 1 解析失败: {}"), e.getMessage());
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

        // 使用空的 Handler（意图识别不显示任何响应给玩家）
        AIResponseHandler handler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return null;
            }

            @Override
            public String getPlayerName() {
                return "IntentRecognizer";
            }

            @Override
            public void showResponse(String response) {
                // 意图识别场景：仅 debug 日志
                PluginLoggerUtil.debug("意图识别", "意图识别结果: {}", response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
                // 不需要流式输出
            }

            @Override
            public void handleError(String errorMessage) {
                PluginLoggerUtil.debug("意图识别", "意图识别错误: {}", errorMessage);
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        // === Phase 1：Skill 分类（关闭知识检索） ===
        String phase1Skills = buildPhase1SkillDescription(caller);
        String phase1SystemPrompt = promptConfigManager.buildPhase1SystemPrompt(phase1Skills);

        PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 1 Skill 分类开始"));

        return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, phase1SystemPrompt, false, false, true).thenCompose(phase1Response -> {
            Set<String> selectedSkills = parsePhase1Response(phase1Response);

            // 快速路径：无效意图，不调 Phase 2
            if (selectedSkills.isEmpty()) {
                PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 1 判定为非技能请求"));
                return CompletableFuture.completedFuture(createInvalidIntent(I18nService.tr("非技能请求")));
            }

            // === Phase 2：Action 选择 + 参数提取（开启知识检索） ===
            // AFK 挂机任务特殊处理：callback 的 condition_plan 和 callback_steps 可以引用任意 Skill（包括 SPI 注册的），
            // 但 Phase 1 可能漏选这些 Skill。因此当 AFKTask 被选中时，Phase 2 必须拿到全量 Skill 的完整信息，
            // 确保 LLM 能为 condition_plan 和 callback_steps 选择正确的技能和 API。
            boolean hasAfkTask = selectedSkills.contains("AFKTask");
            Set<String> phase2SkillFilter = hasAfkTask ? getAllSkillNames(caller) : selectedSkills;
            String phase2Skills = buildPhase2SkillDescription(caller, phase2SkillFilter);
            String phase2SystemPrompt = promptConfigManager.buildSystemPrompt(phase2Skills, selectedSkills);

            PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 2 开始，选中技能: {}"), selectedSkills);

            return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, phase2SystemPrompt, true, false, true).thenApply(phase2Response -> {
                PluginLoggerUtil.debug("意图识别", I18nService.tr("Phase 2 完成"));
                // 解析 LLM 响应
                return parseIntentFromResponse(phase2Response);
            });
        });
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
        prompt.append(I18nService.tr("请分析用户想要使用什么技能，并返回 JSON 格式的识别结果。"));
        prompt.append("\n");
        prompt.append(I18nService.tr("**重要：只返回纯 JSON 对象，不要包含任何 Markdown 标记、注释或额外说明文本。**"));
        return prompt.toString();
    }

    /**
     * 解析 LLM 响应（支持单意图和多步骤任务）
     */
    private Object parseIntentFromResponse(String response) {
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
                        PluginLoggerUtil.debug("意图识别", I18nService.tr("JSON 自动修复成功"));
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
        } catch (Exception e) {
            PluginLoggerUtil.debug("意图识别", "解析意图失败：{}", e.getMessage());
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
        if (!isValidSkillName(skillName)) {
            PluginLoggerUtil.warn("意图识别", I18nService.tr("Phase 2 返回了不存在的技能名称: {}，已拒绝执行", skillName));
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
                            PluginLoggerUtil.debug("意图识别", I18nService.tr("多步骤任务中跳过无效技能名称: {}"), skillName);
                            continue;
                        }
                        plan.addStep(new TaskStep(id, skillName, action, entities, dependsOn));
                    }
                }
            }
            return plan;

        } catch (Exception e) {
            PluginLoggerUtil.debug("意图识别", "解析任务计划失败：{}", e.getMessage());
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

