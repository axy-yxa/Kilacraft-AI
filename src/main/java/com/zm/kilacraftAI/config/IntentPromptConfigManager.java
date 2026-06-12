package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Set;

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
    private String playerSecurityRule;

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

    // Phase 1 配置（两阶段意图识别）
    @Getter
    private String phase1RoleDefinition;

    @Getter
    private String phase1OutputFormat;

    public IntentPromptConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        try {
            // 根据当前语言选择配置文件（zh=intent_prompts.yml, en=intent_prompts_en.yml）
            String lang = ((KilacraftAI) plugin).getConfigManager().getLanguage();
            String fileName = "zh".equals(lang) ? "intent_prompts.yml" : "intent_prompts_" + lang + ".yml";
            configFile = new File(plugin.getDataFolder(), fileName);

            // 复制默认配置
            ConfigResourceUtil.saveDefaultResource((KilacraftAI) plugin, fileName);

            // 加载配置
            config = YamlConfiguration.loadConfiguration(configFile);

            // 读取各部分配置
            loadPromptSections();
        } catch (Exception e) {
            PluginLoggerUtil.error("意图提示词", "加载意图识别提示词配置失败", e);
        }
    }

    /**
     * 加载提示词各部分内容
     */
    private void loadPromptSections() {
        // 第一部分:角色定义
        this.roleDefinition = config.getString("role_definition", I18nService.tr("你是一个智能的技能意图识别助手."));

        // 第二部分:响应格式规范
        this.singleIntentFormat = config.getString("response_format.single_intent", "");
        this.invalidIntentFormat = config.getString("response_format.invalid_intent", "");
        this.multiStepTaskFormat = config.getString("response_format.multi_step_task", "");

        // 第三部分:决策规则
        this.whenUseSingleIntent = config.getString("decision_rules.when_use_single_intent", "");
        this.whenUseMultiStep = config.getString("decision_rules.when_use_multi_step", "");
        this.whenReturnInvalid = config.getString("decision_rules.when_return_invalid", "");

        // 第四部分:关键约束规则
        this.playerSecurityRule = config.getString("critical_rules.player_security", "");
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

        // Phase 1 配置（两阶段意图识别 — Skill 分类）
        this.phase1RoleDefinition = config.getString("phase1.role_definition", "你是一个技能分类器。根据用户的输入，判断需要使用哪些技能类别来完成任务。\n" + "只需要返回技能名称列表，不需要选择具体动作或提取参数。\n" + "如果用户请求是闲聊、问候、现实生活话题，或没有任何技能能覆盖用户意图，返回 null。\n" + "\n" + "【核心匹配标准】\n" + "- **严格依据【可用技能列表】中每个技能的描述文本来判断匹配**，禁止基于你自己的外部知识做联想推理\n" + "- 技能的描述文本必须直接、明确地服务于用户问题的意图，\"获取的数据可能有助于回答\"不构成匹配理由。如果没有技能的描述文本能直接服务于用户问题的意图，就必须返回 null\n" + "- 正例：用户问\"我手上拿着什么\" → 技能描述中有\"获取玩家手持物品\"，直接服务于用户意图 → 匹配\n" + "- 正例：用户问\"减脂晚餐怎么做\" → 没有任何技能的描述文本服务于\"烹饪\"、\"食谱\"意图 → 不匹配，返回 null\n" + "- **\"技能获取的数据可能有助于回答用户问题\"≠\"技能匹配用户意图\"。只有当用户问题本身就要求获取该数据时才构成匹配**\n" + "- 注意：如果上一轮对话有明确上下文，应该结合历史理解意图，而不是直接返回 null\n" + "\n" + "【技能名称严格限制 - 最高优先级】\n" + "- 绝对禁止编造技能名称，只能使用【可用技能列表】中明确列出的技能名称\n" + "- 严禁使用任何不在列表中的名称，即使你认为它\"应该存在\"\n" + "- 严禁根据功能推测技能名称（如：看到\"查询配方\"就编造 \"knowledge_base\"、\"recipe_query\" 等）\n" + "- 把【可用技能列表】视为唯一的白名单\n" + "- 你的职责：识别意图 → 匹配已有技能。不是你的职责：创造新技能、推测技能名称、假设功能存在\n" + "- 原则：宁多选不少选——当对某个技能是否相关存疑时，应纳入而非排除");

        this.phase1OutputFormat = config.getString("phase1.output_format", "需要技能：{\"skill_names\": [\"技能名称1\", \"技能名称2\"]}\n无需技能：{\"skill_names\": null}");
    }

    /**
     * 重新加载配置（用于 /kilacraft reload 命令）
     */
    public void reload() {
        loadConfig();
        PluginLoggerUtil.info("意图提示词", "意图识别提示词配置重载完成");
    }

    /**
     * 构建 Phase 1 系统提示词（极轻量，只用于 Skill 分类）
     *
     * @param skillsDescription 精简技能描述（仅 name + description）
     * @return Phase 1 系统提示词
     */
    public String buildPhase1SystemPrompt(String skillsDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append(phase1RoleDefinition).append("\n\n");
        sb.append(I18nService.tr("【可用技能列表】")).append("\n");
        sb.append(skillsDescription).append("\n\n");
        sb.append(phase1OutputFormat).append("\n");
        return sb.toString();
    }

    /**
     * 构建完整的系统提示词（Phase 2 使用）
     * 将所有部分按固定模板组合
     *
     * @param skillsDescription 技能描述文本（由调用方动态生成）
     * @param selectedSkills    Phase 1 选中的 Skill 名称集合（用于条件注入 afk_task_rules）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(String skillsDescription, Set<String> selectedSkills) {
        StringBuilder sb = new StringBuilder();

        // 1. 角色定义
        sb.append(I18nService.tr("【角色定义】")).append("\n");
        sb.append(roleDefinition).append("\n\n");

        // 2. 可用技能列表（动态生成）
        sb.append(I18nService.tr("【可用技能列表】")).append("\n");
        sb.append(skillsDescription).append("\n\n");

        // 3. 响应格式规范
        sb.append(I18nService.tr("【响应格式规范】")).append("\n");
        sb.append(I18nService.tr("请严格按照以下 JSON 格式返回识别结果。")).append("\n\n");

        sb.append(I18nService.tr("### 单意图格式（简单任务）")).append("\n");
        sb.append(singleIntentFormat).append("\n\n");

        sb.append(I18nService.tr("### 无效意图格式（无法识别时）")).append("\n");
        sb.append(invalidIntentFormat).append("\n\n");

        sb.append(I18nService.tr("### 多步骤任务格式（复杂任务）")).append("\n");
        sb.append(multiStepTaskFormat).append("\n\n");

        // 输出格式强制要求
        if (outputFormatRules != null && !outputFormatRules.isEmpty()) {
            sb.append(outputFormatRules).append("\n\n");
        }

        // 4. 决策规则
        sb.append(I18nService.tr("【决策规则】")).append("\n");
        sb.append(I18nService.tr("### 何时使用单意图")).append("\n");
        sb.append(whenUseSingleIntent).append("\n\n");

        sb.append(I18nService.tr("### 何时使用多步骤任务")).append("\n");
        sb.append(whenUseMultiStep).append("\n\n");

        sb.append(I18nService.tr("### 何时返回无效意图")).append("\n");
        sb.append(whenReturnInvalid).append("\n\n");

        // 5. 关键约束规则
        sb.append(I18nService.tr("【关键约束规则】")).append("\n");
        if (playerSecurityRule != null && !playerSecurityRule.isEmpty()) {
            sb.append(playerSecurityRule).append("\n\n");
        }
        sb.append(placeholderUsageRule).append("\n\n");
        sb.append(continuousConversationGuide).append("\n\n");
        sb.append(entityFormatRule).append("\n\n");
        if (multiStepMandatoryRule != null && !multiStepMandatoryRule.isEmpty()) {
            sb.append(multiStepMandatoryRule).append("\n\n");
        }

        // 6. 特殊场景处理指南
        sb.append(I18nService.tr("【特殊场景处理指南】")).append("\n");
        sb.append(conflictingIntentsGuide).append("\n\n");
        sb.append(missingParametersGuide).append("\n\n");
        if (skillNameRestrictionRule != null && !skillNameRestrictionRule.isEmpty()) {
            sb.append(skillNameRestrictionRule).append("\n\n");
        }

        // 7. 挂机任务专用规则（条件注入：仅当 Phase 1 选中 AFKTask 时注入）
        if (selectedSkills != null && selectedSkills.contains("AFKTask") && afkTaskRules != null && !afkTaskRules.isEmpty()) {
            sb.append(afkTaskRules).append("\n\n");
        }

        // 8. 输出质量要求
        sb.append(I18nService.tr("【输出质量要求】")).append("\n");
        sb.append(outputQualityRequirements).append("\n");

        return sb.toString();
    }
}
