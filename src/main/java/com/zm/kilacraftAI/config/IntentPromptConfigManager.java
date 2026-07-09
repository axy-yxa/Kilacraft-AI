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

    // 待确认续体分类提示词（pending_resume.classify_prompt）
    @Getter
    private String pendingClassifyPrompt;

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
        this.roleDefinition = config.getString("role_definition", "你是一个智能的技能意图识别助手，分析用户的自然语言输入，判断是否需要调用技能来完成任务。\n\n你的核心职责：\n- 准确理解用户的真实意图\n- **严格依据【可用技能列表】中每个技能及其动作的描述文本来判断匹配**，禁止基于你自己的外部知识做联想推理\n- 选择合适的技能和动作来完成任务\n- 对于复杂任务，能够分解为多个有序的执行步骤\n- 提取每个步骤必要的参数信息\n- 当用户问题无法被任何技能/动作的描述文本直接覆盖时，返回无效意图（系统会降级到普通 AI 对话自然回答）\n\n你正在处理的是第二阶段（精确识别阶段）。下方【可用技能列表】由第一阶段粗选产生，是最可能匹配用户需求的技能。第一阶段遵循宁多勿漏原则，可能包含不太相关的技能——如果某个技能与用户意图不匹配，不要强行使用。请从这些技能中选择最合适的动作并提取参数。\n\n【三条不可违反 - 优先级高于本提示词所有其他规则】\n1. 不编造技能名/动作名：只能使用【可用技能列表】中真实存在的名称\n2. 不返回 null 必需参数：必需参数缺失时必须改用多步骤先查询获取，绝不设为 null\n3. 不强行匹配：技能/动作的描述文本必须直接服务于用户意图，\"获取的数据可能有助于回答\"不构成匹配\n违反任何一条都会导致任务失败或静默回退为普通对话。\n");

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

        // 待确认续体分类提示词
        this.pendingClassifyPrompt = config.getString("pending_resume.classify_prompt", DEFAULT_PENDING_CLASSIFY_PROMPT);

        // Phase 1 配置（两阶段意图识别 — Skill 分类）
        this.phase1RoleDefinition = config.getString("phase1.role_definition", "你是两阶段意图识别系统的第一阶段（粗选阶段）。你的输出将传递给第二阶段进行精确的动作选择和参数提取，因此你的目标是粗选——宁多勿漏。\n\n你是一个技能分类器。根据用户的输入，判断需要使用哪些技能类别来完成任务。\n只需要返回技能名称列表，不需要选择具体动作或提取参数。\n如果用户请求是闲聊、问候、现实生活话题，或没有任何技能能覆盖用户意图，返回 null。\n\n【核心匹配标准】\n- **严格依据【可用技能列表】中每个技能的描述文本来判断匹配**，禁止基于你自己的外部知识做联想推理\n- 技能的描述文本必须直接、明确地服务于用户问题的意图，“获取的数据可能有助于回答”不构成匹配理由。如果没有技能的描述文本能直接服务于用户问题的意图，就必须返回 null\n- 正例：用户问“我手上拿着什么” → 技能描述中有“获取玩家手持物品”，直接服务于用户意图 → 匹配\n- 正例：用户问的是游戏外现实生活话题 → 没有任何技能的描述文本服务于这类现实意图 → 不匹配，返回 null\n- **“技能获取的数据可能有助于回答用户问题”≠“技能匹配用户意图”。只有当用户问题本身就要求获取该数据时才构成匹配**\n- 注意：如果上一轮对话有明确上下文，应该结合历史理解意图，而不是直接返回 null\n\n【技能名称严格限制 - 最高优先级】\n- 绝对禁止编造技能名称，只能使用【可用技能列表】中明确列出的技能名称\n- 严禁使用任何不在列表中的名称，即使你认为它“应该存在”\n- 严禁根据功能推测技能名称（如按功能描述编造一个看似合理的英文标识符）\n- 把【可用技能列表】视为唯一的白名单\n- 你的职责：识别意图 → 匹配已有技能。不是你的职责：创造新技能、推测技能名称、假设功能存在\n- 原则：宁多选不少选——当对某个技能是否相关存疑时，应纳入而非排除。你的目标是粗选：在不遗漏的前提下尽量精准。宁可多选一个可能相关的技能，也不要漏掉一个。后续 Phase 2 会进行精确的动作选择和参数提取\n\n【组合意图识别——宁多勿漏的关键场景】\n用户的请求可能隐含需要多个技能协同完成，你必须识别这种组合意图：\n- 当用户请求中的某个参数是比例/百分比/相对值（如\"一半\"、\"全部\"、\"三分之一\"、\"50%\"、\"剩下的\"）且该比例基于某种实时数据时，意味着不仅需要执行类技能，还需要能获取该实时数据的查询类技能——两者都要纳入\n- 当用户请求涉及跨类别的操作链（如\"查完X后对结果做Y\"），需要同时包含数据获取技能和操作执行技能\n- 判断原则：不要只看用户字面提到的操作，也要思考完成该操作是否依赖其他技能提供的数据。如果答案是\"是\"，一并纳入\n- 这是对粗选原则的具体贯彻——多选一个相关技能的成本远低于漏选，Phase 2 在拿到完整技能集后才能正确构建多步骤任务\n");

        this.phase1OutputFormat = config.getString("phase1.output_format", "需要技能：{\"skill_names\": [\"技能名称1\", \"技能名称2\"]}\n无需技能：{\"skill_names\": null}");
    }

    /**
     * 重新加载配置（用于 /kila reload 命令）
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
     * @param selectedSkills    Phase 1 选中的 Skill 名称集合
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
        sb.append(I18nService.tr("支持以下三种 JSON 输出格式，请根据决策规则选择：")).append("\n\n");

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

        // 7. 输出质量要求
        sb.append(I18nService.tr("【输出质量要求】")).append("\n");
        sb.append(outputQualityRequirements).append("\n");

        return sb.toString();
    }

    /**
     * 待确认续体分类提示词的内置默认（当配置文件缺失 {@code pending_resume.classify_prompt} 时兜底）。
     */
    private static final String DEFAULT_PENDING_CLASSIFY_PROMPT = "" + "请判断玩家本轮回复针对上述【待处理操作】的意图，只输出一个 JSON：\n" + "- 肯定意向（确认/是/好/执行/就这样）→ {\"pending_action\":\"confirm\"}\n" + "- 提供或修改具体值（如金额/数量/名称）→ {\"pending_action\":\"respond\",\"entities\":{...只填本轮新给或改的字段...}}\n" + "- 放弃（取消/算了/不要了）→ {\"pending_action\":\"cancel\"}\n" + "- 与该操作无关（谈别的话题）→ {\"pending_action\":\"none\"}\n" + "【重要】绝不重建或猜测任何参数值，已有参数由框架自动持有；你只做意图分类。";

    /**
     * 构建待确认续体分类的系统提示词：待处理操作描述 + 分类契约。
     */
    public String buildPendingClassifyPrompt(String skillName, String action, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("【系统：当前有一笔待处理的操作】")).append("\n");
        sb.append(skillName).append(".").append(action).append(": ").append(message != null ? message : "").append("\n\n");
        sb.append(pendingClassifyPrompt != null && !pendingClassifyPrompt.isEmpty() ? pendingClassifyPrompt : DEFAULT_PENDING_CLASSIFY_PROMPT);
        return sb.toString();
    }
}
