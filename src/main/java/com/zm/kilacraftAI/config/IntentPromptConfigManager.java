package com.zm.kilacraftAI.config;

 
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
 
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;
 
/**
 * 意图识别提示词配置管理器
 * 负责加载和管理意图识别的系统提示词配置
 * 
 * @author Zm_Mmm
 * @since 2026-04-06
 */
public class IntentPromptConfigManager {
    
    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;
    
    // 提示词各部分配置
    @Getter
    private String roleDefinition;
    
    @Getter
    private String singleIntentFormat;
    
    @Getter
    private String invalidIntentFormat;
    
    @Getter
    private String multiStepTaskFormat;
    
    @Getter
    private String whenUseSingleIntent;
    
    @Getter
    private String whenUseMultiStep;
    
    @Getter
    private String whenReturnInvalid;
    
    @Getter
    private String placeholderUsageRule;
    
    @Getter
    private String continuousConversationGuide;
    
    @Getter
    private String entityFormatRule;
    
    @Getter
    private String multiStepMandatoryRule;
    
    @Getter
    private String conflictingIntentsGuide;
    
    @Getter
    private String missingParametersGuide;
    
    @Getter
    private String skillNameRestrictionRule;
    
    @Getter
    private String outputQualityRequirements;
    
    @Getter
    private String outputFormatRules;
    
    @Getter
    private String afkTaskRules;
    
    public IntentPromptConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    public void loadConfig() {
        try {
            // 确保插件数据目录存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // 配置文件路径
            configFile = new File(plugin.getDataFolder(), "intent_prompts.yml");
            
            // 如果配置文件不存在 复制默认配置
            if (!configFile.exists()) {
                saveDefaultConfig();
            }
            
            // 加载配置
            config = YamlConfiguration.loadConfiguration(configFile);
            
            // 读取各部分配置
            loadPromptSections();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载意图识别提示词配置失败", e);
        }
    }
    
    /**
     * 保存默认配置
     */
    private void saveDefaultConfig() {
        try (InputStream inputStream = plugin.getResource("intent_prompts.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, configFile.toPath());
                plugin.getLogger().info("已创建默认 intent_prompts.yml 意图识别提示词配置文件");
            } else {
                plugin.getLogger().warning("未找到默认的 intent_prompts.yml 资源文件");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存默认配置失败", e);
        }
    }
    
    /**
     * 加载提示词各部分内容
     */
    private void loadPromptSections() {
        // 第一部分:角色定义
        this.roleDefinition = config.getString("role_definition",
            "你是一个智能的技能意图识别助手.");
        
        // 第二部分:响应格式规范
        this.singleIntentFormat = config.getString("response_format.single_intent", "");
        this.invalidIntentFormat = config.getString("response_format.invalid_intent", "");
        this.multiStepTaskFormat = config.getString("response_format.multi_step_task", "");
        
        // 第三部分:决策规则
        this.whenUseSingleIntent = config.getString("decision_rules.when_use_single_intent", "");
        this.whenUseMultiStep = config.getString("decision_rules.when_use_multi_step", "");
        this.whenReturnInvalid = config.getString("decision_rules.when_return_invalid", "");
        
        // 第四部分:关键约束规则
        this.placeholderUsageRule = config.getString("critical_rules.placeholder_usage", "");
        this.continuousConversationGuide = config.getString("critical_rules.continuous_conversation", "");
        this.entityFormatRule = config.getString("critical_rules.entity_format", "");
        this.multiStepMandatoryRule = config.getString("critical_rules.multi_step_mandatory", "");
        
        // 第五部分:特殊场景处理指南
        this.conflictingIntentsGuide = config.getString("special_scenarios.conflicting_intents", "");
        this.missingParametersGuide = config.getString("special_scenarios.missing_parameters", "");
        this.skillNameRestrictionRule = config.getString("special_scenarios.skill_name_restriction", "");
        
        // 第六部分:输出质量要求
        this.outputQualityRequirements = config.getString("output_quality_requirements", "");
        
        // 输出格式强制要求
        this.outputFormatRules = config.getString("output_format_rules", "");
        
        // 第七部分：挂机任务专用规则
        this.afkTaskRules = config.getString("afk_task_rules", "");
    }
    
    /**
     * 重新加载配置（用于 /kilacraft reload 命令）
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("意图识别提示词配置重载完成！");
    }
    
    /**
     * 构建完整的系统提示词
     * 将所有部分按固定模板组合
     * 
     * @param skillsDescription 技能描述文本（由 SkillManager 动态生成）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(String skillsDescription) {
        StringBuilder sb = new StringBuilder();
        
        // 1. 角色定义
        sb.append("【角色定义】\n");
        sb.append(roleDefinition).append("\n\n");
        
        // 2. 可用技能列表（动态生成）
        sb.append("【可用技能列表】\n");
        sb.append(skillsDescription).append("\n\n");
        
        // 3. 响应格式规范
        sb.append("【响应格式规范】\n");
        sb.append("请严格按照以下 JSON 格式返回识别结果。\n\n");
        
        sb.append("### 单意图格式（简单任务）\n");
        sb.append(singleIntentFormat).append("\n\n");
        
        sb.append("### 无效意图格式（无法识别时）\n");
        sb.append(invalidIntentFormat).append("\n\n");
        
        sb.append("### 多步骤任务格式（复杂任务）\n");
        sb.append(multiStepTaskFormat).append("\n\n");
        
        // 输出格式强制要求
        if (outputFormatRules != null && !outputFormatRules.isEmpty()) {
            sb.append(outputFormatRules).append("\n\n");
        }
        
        // 4. 决策规则
        sb.append("【决策规则】\n");
        sb.append("### 何时使用单意图\n");
        sb.append(whenUseSingleIntent).append("\n\n");
        
        sb.append("### 何时使用多步骤任务\n");
        sb.append(whenUseMultiStep).append("\n\n");
        
        sb.append("### 何时返回无效意图\n");
        sb.append(whenReturnInvalid).append("\n\n");
        
        // 5. 关键约束规则
        sb.append("【关键约束规则】\n");
        sb.append(placeholderUsageRule).append("\n\n");
        sb.append(continuousConversationGuide).append("\n\n");
        sb.append(entityFormatRule).append("\n\n");
        if (multiStepMandatoryRule != null && !multiStepMandatoryRule.isEmpty()) {
            sb.append(multiStepMandatoryRule).append("\n\n");
        }
        
        // 6. 特殊场景处理指南
        sb.append("【特殊场景处理指南】\n");
        sb.append(conflictingIntentsGuide).append("\n\n");
        sb.append(missingParametersGuide).append("\n\n");
        if (skillNameRestrictionRule != null && !skillNameRestrictionRule.isEmpty()) {
            sb.append(skillNameRestrictionRule).append("\n\n");
        }

        // 7. 挂机任务专用规则
        if (afkTaskRules != null && !afkTaskRules.isEmpty()) {
            sb.append(afkTaskRules).append("\n\n");
        }

        // 8. 输出质量要求
        sb.append("【输出质量要求】\n");
        sb.append(outputQualityRequirements).append("\n");
        
        return sb.toString();
    }
}
