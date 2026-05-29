package com.zm.kilacraftAI.skills.framework;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.IntentPromptConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.skills.framework.task.TaskStep;
import com.zm.kilacraftAI.common.util.HistoryUtil;
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
     * 构建系统提示词（根据调用者权限过滤 Skill）
     *
     * @param caller 调用者（Player），null 表示控制台（控制台视为拥有所有权限）
     */
    private String buildSystemPrompt(Player caller) {
        // 动态生成技能描述部分（按权限预检过滤）
        StringBuilder skillsDescription = new StringBuilder();
        int index = 1;
        int skippedCount = 0;
        for (Skill skill : skillManager.getAllSkills()) {
            // 权限预检：如果 Skill 声明了权限要求，且调用者没有该权限，跳过
            String requiredPermission = skill.getRequiredPermission();
            if (requiredPermission != null && caller != null && !caller.hasPermission(requiredPermission)) {
                skippedCount++;
                continue;
            }

            skillsDescription.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");

            // 如果技能有多个动作，自动列出
            if (!skill.getActions().isEmpty()) {
                skillsDescription.append(I18nService.tr("可用动作：")).append("\n");
                for (Map.Entry<String, String> entry : skill.getActions().entrySet()) {
                    skillsDescription.append("    - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            // 如果有额外提示，也加上
            if (!skill.getHints().isEmpty()) {
                skillsDescription.append(I18nService.tr("提示：")).append("\n");
                for (String hint : skill.getHints()) {
                    skillsDescription.append("    - ").append(hint).append("\n");
                }
            }
        }

        if (skippedCount > 0) {
            PluginLoggerUtil.debug("意图识别", "权限预检过滤：跳过了 {} 个无权限的 Skill", skippedCount);
        }

        // 使用配置管理器构建完整提示词
        return promptConfigManager.buildSystemPrompt(skillsDescription.toString());
    }

    public SkillIntentRecognizer(ConfigManager configManager, IntentPromptConfigManager promptConfigManager, SkillManager skillManager) {
        this.configManager = configManager;
        this.promptConfigManager = promptConfigManager;
        this.gson = new Gson();
        this.skillManager = skillManager;
    }

    /**
     * 识别用户意图（统一入口，支持单意图和多步骤任务）
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

        // 构建系统提示词（根据调用者权限过滤 Skill）
        String systemPrompt = buildSystemPrompt(caller);

        // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
//        PluginLoggerUtil.warn("意图识别", "动态构建系统提示词: {}", systemPrompt);

        // 调用 LLM 进行意图识别
        // 优化：启用知识检索，支持命令文档等定制知识
        // 注：经过优化，ChineseTextProcessor 和 BM25 已支持短文本和英文命令名
        return llmProvider.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, systemPrompt, true, false, true).thenApply(this::parseIntentFromResponse);
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

            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

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
     * 解析单意图
     */
    private SkillIntent parseSingleIntentFromResponse(JsonObject json) {
        // 解析字段（需要检查是否为 JsonNull）
        String skillName = null;
        if (json.has("skill_name") && !json.get("skill_name").isJsonNull()) {
            skillName = json.get("skill_name").getAsString();
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

