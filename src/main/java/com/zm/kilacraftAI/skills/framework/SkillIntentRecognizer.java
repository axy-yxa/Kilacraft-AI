package com.zm.kilacraftAI.skills.framework;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.DeepSeekAPINew;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.IntentRecognitionResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;

import java.util.Deque;
import java.util.HashMap;
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
     * 构建系统提示词（动态从 Skill 实例中读取描述）
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个技能意图识别助手。你的任务是分析用户的输入，识别他们想要使用的技能。\n");
        sb.append("\n可用技能列表：\n");
        
        // 遍历所有已注册的技能，动态构建技能描述
        int index = 1;
        for (Skill skill : skillManager.getAllSkills()) {
            sb.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");
            
            // 如果是 MarketQuerySkill，列出它的动作描述
            if ("market_query".equals(skill.getName())) {
                sb.append("可用动作：\n");
                sb.append("  - query_balance: ").append(getActionDescription(skill, "query_balance")).append("\n");
                sb.append("  - query_price: ").append(getActionDescription(skill, "query_price")).append("\n");
                sb.append("  - query_items: ").append(getActionDescription(skill, "query_items")).append("\n");
            }
        }
        
        sb.append("\n请按照以下 JSON 格式返回识别结果：\n");
        sb.append("{\n");
        sb.append("  \"skill_name\": \"技能名称\",\n");
        sb.append("  \"action\": \"具体动作\",\n");
        sb.append("  \"entities\": { \"item\": \"物品 1:数量 1，物品 2:数量 2\" },\n");
        sb.append("  \"confidence\": 0.9,\n");
        sb.append("  \"reasoning\": \"识别理由\"\n");
        sb.append("}\n");
        sb.append("\n重要规范：\n");
        sb.append("- 当用户询问多个物品时，entities.item 字段必须使用英文逗号 ',' 分隔每个物品\n");
        sb.append("- 每个物品的格式为'物品名称：数量'，例如：'木棍:2，钻石:1'\n");
        sb.append("- 如果用户没有指定数量，默认数量为 1，例如：'钻石'应写为'钻石:1'\n");
        sb.append("- 不要使用'和'、'与'、'及'等连接词，统一用逗号分隔\n");
        sb.append("- **关键**：当用户输入中提到具体物品名称时，必须提取到 entities.item 字段，不能为空\n");
        sb.append("\n如果用户输入与任何技能都不相关，返回：\n");
        sb.append("{\n");
        sb.append("  \"skill_name\": null,\n");
        sb.append("  \"action\": null,\n");
        sb.append("  \"entities\": {},\n");
        sb.append("  \"confidence\": 0.0,\n");
        sb.append("  \"reasoning\": \"无法识别到相关技能\"\n");
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * 获取动作描述（优先从配置文件读取）
     */
    private String getActionDescription(Skill skill, String action) {
        var config = skill.getSkillConfig();
        if (config != null && config.getActionDescriptions() != null) {
            // Map 格式：直接通过 action 名称获取
            String description = config.getActionDescriptions().get(action);
            if (description != null && !description.isEmpty()) {
                return description;
            }
        }
        
        // 默认描述
        return switch (action) {
            case "query_balance" -> "查询玩家账户余额，当用户问'我有多少钱'、'余额'时使用";
            case "query_price" -> "查询指定物品的市场价格，当用户询问物品价格、购买物品（如'买 5 个木棍'、'钻石多少钱'）时使用，需提取物品名称和数量";
            case "query_items" -> "查询市场上架的商品列表，当用户问'市场上有什么'、'列出所有商品'时使用";
            default -> "未知动作";
        };
    }
    
    public SkillIntentRecognizer(DeepSeekAPINew deepSeekAPI, ConfigManager configManager, SkillManager skillManager) {
        this.deepSeekAPI = deepSeekAPI;
        this.configManager = configManager;
        this.gson = new Gson();
        this.skillManager = skillManager;
    }
    
    /**
     * 识别用户意图（纯 LLM 方式，不需要知识检索增强）
     * 
     * @param userInput 用户输入
     * @param history 对话历史（用于上下文理解，可选）
     * @return 识别出的意图（异步）
     */
    public CompletableFuture<SkillIntent> recognize(String userInput, Deque<ConversationManager.Message> history) {
        if (configManager == null || deepSeekAPI == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // 构建用户提示词
        String userPrompt = buildUserPrompt(userInput, history);
        
        // 使用专用的意图识别 Handler（不显示任何响应给玩家）
        AIResponseHandler handler = new IntentRecognitionResponseHandler();

        // 动态构建系统提示词
        String systemPrompt = buildSystemPrompt();
        // 调用 LLM 进行意图识别（禁用知识检索，因为意图识别是分类任务，不需要知识增强）
        return deepSeekAPI.processRequestWithCustomSystemPrompt(
                userPrompt, "IntentRecognizer", null, handler, systemPrompt, false, false)
            .thenApply(this::parseIntentFromResponse);
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
     * 解析 LLM 响应为 SkillIntent
     */
    private SkillIntent parseIntentFromResponse(String response) {
        try {
            // 提取 JSON 部分
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                return createInvalidIntent("无法解析响应为 JSON");
            }
            
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
            
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
            
        } catch (Exception e) {
            if (configManager.isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 解析意图失败：" + e.getMessage());
            }
            return createInvalidIntent("解析失败：" + e.getMessage());
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
