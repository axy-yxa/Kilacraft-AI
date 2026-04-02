package com.zm.kilacraftAI.skills.framework;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.DeepSeekAPINew;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.IntentRecognitionResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;

import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 技能意图识别器 - 使用 LLM 识别用户意图
 */
public class SkillIntentRecognizer {

    private final KilacraftAI plugin = KilacraftAI.getInstance();
    private final DeepSeekAPINew deepSeekAPI;
    private final ConfigManager configManager;
    private final Gson gson;
    private final SkillManager skillManager; // 用于获取所有技能的描述

    /**
     * 构建系统提示词（完全自动化，无需硬编码）
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个技能意图识别助手。你的任务是分析用户的输入，识别他们想要使用的技能。\n");
        sb.append("\n可用技能列表：\n");

        // 自动遍历所有技能，无需硬编码判断
        int index = 1;
        for (Skill skill : skillManager.getAllSkills()) {
            sb.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");

            // 如果技能有多个动作，自动列出
            if (!skill.getActions().isEmpty()) {
                sb.append("  可用动作：\n");
                for (Map.Entry<String, String> entry : skill.getActions().entrySet()) {
                    sb.append("    - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            // 如果有额外提示，也加上
            if (!skill.getHints().isEmpty()) {
                sb.append("  提示：\n");
                for (String hint : skill.getHints()) {
                    sb.append("    - ").append(hint).append("\n");
                }
            }
        }

        // ============================================
        // 通用响应格式（所有技能都需要遵循）
        // ============================================
        sb.append("\n请按照以下 JSON 格式返回识别结果：\n");
        sb.append("{\n");
        sb.append("  \"skill_name\": \"技能名称\",\n");
        sb.append("  \"action\": \"具体动作\",\n");
        sb.append("  \"entities\": { \"item\": \"物品 1:数量 1，物品 2:数量 2\" },\n");
        sb.append("  \"confidence\": 0.9,\n");
        sb.append("  \"reasoning\": \"识别理由\"\n");
        sb.append("}\n");

        // 无效意图的返回格式
        sb.append("\n如果用户输入与任何技能都不相关，返回：\n");
        sb.append("{\n");
        sb.append("  \"skill_name\": null,\n");
        sb.append("  \"action\": null,\n");
        sb.append("  \"entities\": {},\n");
        sb.append("  \"confidence\": 0.0,\n");
        sb.append("  \"reasoning\": \"无法识别到相关技能\"\n");
        sb.append("}\n");

        // 多步骤任务支持
        sb.append("\n【多步骤任务】复杂任务可以分解为多个有序步骤：\n");
        sb.append("{\n");
        sb.append("  \"goal\": \"总体目标\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_1\",\n");
        sb.append("      \"skill_name\": \"技能名称\",\n");
        sb.append("      \"action\": \"具体动作\",\n");
        sb.append("      \"entities\": {},\n");
        sb.append("      \"depends_on\": []  // 依赖的步骤 ID 列表，空表示第一步\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        sb.append("\n何时使用多步骤任务：\n");
        sb.append("- 当用户的问题需要多个独立操作才能完成时\n");
        sb.append("- 当后续步骤依赖于前一步骤的结果时\n");
        sb.append("- 当某些步骤可能失败，需要条件判断时\n");

        sb.append("\n多步骤任务示例：\n");
        sb.append("示例 1 - 查询手持物品并检查余额：\n");
        sb.append("用户问：\"我手上的东西市场上有卖吗？我的余额够买吗？\"\n");
        sb.append("分解步骤：\n");
        sb.append("  step_1: get_player_hand_item（获取玩家主手物品）\n");
        sb.append("  step_2: query_price（查询该物品的市场价格，依赖 step_1 的结果）\n");
        sb.append("  step_3: query_balance（查询玩家余额）\n");
        sb.append("  执行完毕后：综合所有结果，回答用户问题\n");
        sb.append("注意：如果 step_2 发现市场上没有该物品，则停止执行后续步骤\n");

        sb.append("\n示例 2 - 比较多个物品价格：\n");
        sb.append("用户问：\"钻石和绿宝石哪个更贵？\"\n");
        sb.append("分解步骤：\n");
        sb.append("  step_1: query_price（查询钻石价格）\n");
        sb.append("  step_2: query_price（查询绿宝石价格）\n");
        sb.append("  执行完毕后：比较两个价格，返回结果\n");

        sb.append("\n示例 3 - 综合信息查询：\n");
        sb.append("用户问：\"我现在在哪里？这个世界现在是什么时间？天气如何？\"\n");
        sb.append("分解步骤：\n");
        sb.append("  step_1: get_player_location（获取玩家位置）\n");
        sb.append("  step_2: get_world_time（获取世界时间）\n");
        sb.append("  step_3: get_weather（获取天气状况）\n");
        sb.append("  执行完毕后：整合所有信息，返回完整回答\n");

        sb.append("\n重要说明：\n");
        sb.append("- 只需列出需要调用技能的步骤，不需要添加'分析结果'之类的虚拟步骤\n");
        sb.append("- 所有步骤执行完毕后，系统会自动整合结果并生成最终回复\n");
        sb.append("- depends_on 字段用于指定步骤间的依赖关系，确保正确的执行顺序\n");
        sb.append("- 如果某个步骤失败或返回空结果，后续依赖它的步骤将不会执行\n");

        return sb.toString();
    }

    public SkillIntentRecognizer(DeepSeekAPINew deepSeekAPI, ConfigManager configManager, SkillManager skillManager) {
        this.deepSeekAPI = deepSeekAPI;
        this.configManager = configManager;
        this.gson = new Gson();
        this.skillManager = skillManager;
    }

    /**
     * 识别用户意图（统一入口，支持单意图和多步骤任务）
     *
     * @param userInput 用户输入
     * @param history   对话历史（用于上下文理解，可选）
     * @return 识别结果（可能是 SkillIntent 或 TaskPlan，异步）
     */
    public CompletableFuture<Object> recognizeIntent(String userInput, Deque<ConversationManager.Message> history) {
        if (configManager == null || deepSeekAPI == null) {
            return CompletableFuture.completedFuture(null);
        }

        // 构建用户提示词
        String userPrompt = buildUserPrompt(userInput, history);

        // 使用专用的意图识别 Handler（不显示任何响应给玩家）
        AIResponseHandler handler = new IntentRecognitionResponseHandler();

        // 动态构建系统提示词
        String systemPrompt = buildSystemPrompt();

        if (configManager.isDebugMode()) {
            plugin.getLogger().warning("[DEBUG] 动态构建系统提示词：" + systemPrompt);
        }

        // 调用 LLM 进行意图识别（禁用知识检索，因为意图识别是分类任务，不需要知识增强）
        return deepSeekAPI.processRequestWithCustomSystemPrompt(userPrompt, "IntentRecognizer", null, handler, systemPrompt, false, false).thenApply(this::parseIntentFromResponse);
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userInput, Deque<ConversationManager.Message> history) {
        StringBuilder prompt = new StringBuilder();

        // 添加聊天历史
        if (history != null && !history.isEmpty()) {
            prompt.append("[聊天历史]\n");
            int count = 0;
            for (ConversationManager.Message msg : history) {
                // 最多显示 5 条历史记录
                if (count++ >= 5) break;

                // 根据角色显示不同的标识
                String roleDisplay = switch (msg.getRole()) {
                    case "user" -> "用户";
                    case "assistant" -> "AI";
                    default -> msg.getRole();
                };

                prompt.append("-").append(roleDisplay).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // 添加当前输入
        prompt.append("[当前输入]\n");
        prompt.append("用户说：").append(userInput).append("\n\n");

        // 添加指令
        prompt.append("请分析用户想要使用什么技能，并返回 JSON 格式的识别结果。");

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
                return createInvalidIntent("无法解析响应为 JSON");
            }

            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

            // 检查是否是多步骤任务（包含 goal 和 steps 字段）
            if (json.has("goal") && json.has("steps")) {
                return parseTaskPlanFromResponse(json);
            }

            // 否则按单意图处理
            return parseSingleIntentFromResponse(json);
        } catch (Exception e) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 解析意图失败：" + e.getMessage());
            }
            return createInvalidIntent("解析失败：" + e.getMessage());
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

        // 解析实体
        Map<String, String> entities = new HashMap<>();
        if (json.has("entities") && json.get("entities").isJsonObject()) {
            JsonObject entitiesObj = json.getAsJsonObject("entities");
            for (String key : entitiesObj.keySet()) {
                var value = entitiesObj.get(key);
                if (!value.isJsonNull()) {
                    entities.put(key, value.getAsString());
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

                    // 解析实体
                    Map<String, String> entities = new HashMap<>();
                    if (stepObj.has("entities") && stepObj.get("entities").isJsonObject()) {
                        JsonObject entitiesObj = stepObj.getAsJsonObject("entities");
                        for (String key : entitiesObj.keySet()) {
                            var value = entitiesObj.get(key);
                            if (!value.isJsonNull()) {
                                entities.put(key, value.getAsString());
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
                        plan.addStep(new TaskPlan.TaskStep(id, skillName, action, entities, dependsOn));
                    }
                }
            }
            return plan;

        } catch (Exception e) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 解析任务计划失败：" + e.getMessage());
            }
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
        if (configManager.isDebugMode()) {
            plugin.getLogger().warning("[DEBUG] 创建无效意图：" + reason);
        }
        return new SkillIntent(null, null, new HashMap<>(), 0.0, reason);
    }
}
