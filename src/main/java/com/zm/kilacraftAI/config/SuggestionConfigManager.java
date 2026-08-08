package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 对话推荐系统配置管理器。配置段位于 behavior.yml 的 suggestion.* 下。
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionConfigManager {

    private static final String CONFIG_FILE = "behavior.yml";

    private final KilacraftAI plugin;

    @Getter
    private volatile boolean enabled = true;
    @Getter
    private volatile int maxSuggestions = 2;
    @Getter
    private volatile int timeoutSeconds = 15;
    /**
     * 不生成推荐的输出场景名集合（OutputScenarioEnum.name()）。
     */
    private volatile Set<String> excludeScenarios = Set.of();
    /**
     * 从技能摘要中排除的 skill 名集合（存 skill.getName() 值；黑名单模式，新增 skill 自动纳入）。
     */
    private volatile Set<String> excludeSkills = Set.of();

    private volatile String displayTitle = "";
    private volatile String displaySeparator = "";
    private volatile String displayClickHint = "";
    private volatile String displayTitleEn = "";
    private volatile String displaySeparatorEn = "";
    private volatile String displayClickHintEn = "";

    private volatile String systemPrompt = "";
    private volatile String systemPromptEn = "";
    private volatile String userPromptTemplate = "";
    private volatile String userPromptTemplateEn = "";

    public SuggestionConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            PluginLoggerUtil.warn("对话推荐", I18nService.tr("配置文件不存在: {}", CONFIG_FILE));
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("suggestion.enabled", true);
        // max_suggestions 钳位 1~5，防配置越界
        int max = yaml.getInt("suggestion.max_suggestions", 2);
        this.maxSuggestions = Math.max(1, Math.min(5, max));
        this.timeoutSeconds = Math.max(1, yaml.getInt("suggestion.timeout_seconds", 15));

        this.excludeScenarios = new HashSet<>(yaml.getStringList("suggestion.exclude_scenarios"));
        this.excludeSkills = new HashSet<>(yaml.getStringList("suggestion.exclude_skills"));

        this.displayTitle = yaml.getString("suggestion.display.title", "§7继续问：");
        this.displaySeparator = yaml.getString("suggestion.display.separator", "§7 | ");
        this.displayClickHint = yaml.getString("suggestion.display.click_hint", "点击发送此问题");
        this.displayTitleEn = yaml.getString("suggestion.display.title_en", "§7Ask more:");
        this.displaySeparatorEn = yaml.getString("suggestion.display.separator_en", "§7 | ");
        this.displayClickHintEn = yaml.getString("suggestion.display.click_hint_en", "Click to send this question");

        this.systemPrompt = yaml.getString("suggestion.prompts.system_prompt", "");
        this.systemPromptEn = yaml.getString("suggestion.prompts.system_prompt_en", "");
        this.userPromptTemplate = yaml.getString("suggestion.prompts.user_prompt_template", "");
        this.userPromptTemplateEn = yaml.getString("suggestion.prompts.user_prompt_template_en", "");

        PluginLoggerUtil.info("对话推荐", I18nService.tr("配置加载完成"));
    }

    public void reload() {
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
        loadConfig();
    }

    public boolean isScenarioEnabled(OutputScenarioEnum scenario) {
        return !excludeScenarios.contains(scenario.name());
    }

    public Set<String> getExcludeSkills() {
        return Collections.unmodifiableSet(excludeSkills);
    }

    public String getDisplayTitle() {
        return I18nService.isZh() ? displayTitle : displayTitleEn;
    }

    public String getDisplaySeparator() {
        return I18nService.isZh() ? displaySeparator : displaySeparatorEn;
    }

    public String getDisplayClickHint() {
        return I18nService.isZh() ? displayClickHint : displayClickHintEn;
    }

    public String getLocalizedSystemPrompt() {
        String prompt = I18nService.isZh() ? systemPrompt : systemPromptEn;
        return prompt == null || prompt.isBlank() ? (I18nService.isZh() ? DEFAULT_SYSTEM_PROMPT : DEFAULT_SYSTEM_PROMPT_EN) : prompt;
    }

    public String getLocalizedUserPromptTemplate() {
        String prompt = I18nService.isZh() ? userPromptTemplate : userPromptTemplateEn;
        return prompt == null || prompt.isBlank() ? (I18nService.isZh() ? DEFAULT_USER_PROMPT_TEMPLATE : DEFAULT_USER_PROMPT_TEMPLATE_EN) : prompt;
    }

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个Minecraft服务器AI助手，刚刚回复了玩家的一条消息。
            你的任务是预测玩家接下来最可能想问的问题或想做的操作。
            
            【对话历史角色说明】
            下面对话历史中，user 是玩家说过的话，assistant 是你自己的回复。推荐的每一句都必须是玩家没有说过的新内容，严禁原样或近似重复历史中的任何句子。
            
            【推荐主轴：延续当前话题】
            推荐的核心是顺着玩家当前正在聊的话题自然延伸，无论是游戏知识、攻略还是生活话题，都顺着当前方向深入。不要脱离对话语境去推荐不相干的内容。
            
            【操作型推荐必须来自系统能力】
            如果你要推荐的是让AI执行的操作（查询、设置、监听、购买等），必须确认该操作在下方【系统能力】列表中存在，不存在的操作不要推荐。
            知识性问题（"怎么做""为什么""是什么"等）不受此约束，可以自由延伸。
            
            【系统能力是背景，不是推荐目标】
            下方【系统能力】列表仅供你了解服务器能做什么，不是推荐清单。只有当当前话题本身与这些能力相关时，才把它们作为话题的自然延伸来推荐。话题与系统能力无关时（知识问答、攻略、生活话题等），就纯粹顺着话题深入，不要硬塞系统能力。
            
            【反重复规则（重要）】
            - 玩家在历史中问过或说过的任何内容，一律禁止再次推荐（原话、去标点变体、近似改写都算重复）
            - 玩家刚问过 X，可以推荐 X 的下一层问题：细节追问、前置准备、后续步骤、相关延伸——但不能把 X 本身再推荐一遍
            - 例：玩家问"怎么去末地"，可以推荐"末地传送门怎么搭""末影之眼在哪里找"，但不能推荐"怎么去末地"
            
            要求：
            - 按推荐价值从高到低输出，第一行是最值得推荐的
            - 最多输出 {count} 条，没有把握的不要输出，宁缺毋滥
            - 只预测玩家确实可能感兴趣的，不确定就不要输出
            - 没有值得推荐的内容时，返回空即可
            - 每个问题或操作请求必须简短（不超过20字）、自然、像玩家会说的话
            - 不要推荐与当前话题明显不相干的内容
            - 每行只写内容本身，不要加任何编号、序号、符号前缀（✗「1. xxx」/「① xxx」/「- xxx」；✓「xxx」）
            
            【系统能力】
            {available_skills}""";

    private static final String DEFAULT_SYSTEM_PROMPT_EN = """
            You are a Minecraft server AI assistant who just responded to a player.
            Your task is to predict what the player would most likely want to ask or do next.
            
            [Roles in the conversation history]
            In the conversation history below, user is what the player said, assistant is your own reply. Every recommendation must be NEW content the player has never said — never repeat any sentence from the history, verbatim or paraphrased.
            
            [Recommendation axis: continue the current topic]
            The core of recommendation is to naturally extend the topic the player is currently discussing — whether it is game knowledge, guides, or everyday topics, always go deeper in the current direction. Do NOT derail the conversation to recommend unrelated content.
            
            [Operational recommendations must come from system capabilities]
            If you are recommending something for the AI to execute (query, set, monitor, purchase, etc.), it must exist in the [System capabilities] list below. Do not recommend operations that are not listed.
            Knowledge questions ("how to", "why", "what is", etc.) are not subject to this constraint and can be freely extended.
            
            [System capabilities are background, not the recommendation target]
            The [System capabilities] list below only tells you what the server can do — it is NOT a recommendation menu. Only treat them as natural topic extensions when the current topic is actually related to these capabilities. When the topic has nothing to do with system capabilities (knowledge Q&A, guides, life topics, etc.), just go deeper on the topic itself — do NOT force system capabilities into the recommendation.
            
            [Anti-repetition rule (important)]
            - Never recommend anything the player has already asked or said in the history (verbatim, stripped punctuation, or paraphrased — all count as repetition)
            - If the player just asked X, you may recommend the next level of X: follow-up details, prerequisites, next steps, related extensions — but never X itself again
            - Example: player asked "how to go to the End" — you may recommend "how to build an End portal" or "where to find Eyes of Ender", but NOT "how to go to the End"
            
            Requirements:
            - Output in order of recommendation value; the first line is the most recommended
            - Output at most {count} lines; skip anything you are not confident about — quality over quantity
            - Only predict questions or actions the player would genuinely be interested in; if unsure, skip
            - If nothing is worth recommending, return nothing
            - Each question or action must be short (max 12 words), natural, like something a player would say
            - Do not recommend content that is clearly unrelated to the current topic
            - Output only the content itself per line, no numbering, no list markers, no symbols (✗ "1. xxx" / "- xxx"; ✓ "xxx")
            
            [System capabilities]
            {available_skills}""";

    private static final String DEFAULT_USER_PROMPT_TEMPLATE = "基于以上对话历史，预测玩家接下来最可能想问的问题或想做的操作。按推荐价值从高到低输出，每行一条；没有值得推荐的内容时输出空；严禁重复玩家已经问过的问题：";

    private static final String DEFAULT_USER_PROMPT_TEMPLATE_EN = "Based on the above conversation history, predict what the player would most likely want to ask or do next. Output in order of value, one per line; return nothing if there is nothing worth recommending; never repeat questions the player has already asked:";
}
