package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.AlertCategory;
import com.zm.kilacraftAI.service.guardian.AlertPriority;
import com.zm.kilacraftAI.service.guardian.monitor.Policy;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 守护系统配置管理器。独立 guardian.yml 文件，仿 {@link GreetingConfigManager} 模式。
 *
 * <p>管理全局开关、心跳间隔、冷却中心默认参数、monitor 模板库、默认套餐。
 * reload 用 volatile 快照发布（跨线程立即可见）——守护系统读配置只在 monitor 创建时（低频），
 * 但仍按规范保持一致性。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public class GuardianConfigManager {

    private static final String CONFIG_FILE = "guardian.yml";

    private final KilacraftAI plugin;
    private File configFile;

    @Getter private volatile boolean enabled;
    @Getter private volatile long heartbeatIntervalTicks;
    @Getter private volatile long globalCooldownMillis;
    @Getter private volatile long categoryCooldownMillis;
    /** 模板库（name → 定义）。不可变快照，reload 整体替换。loadConfig 前为空 Map（非 null，防 NPE）。 */
    @Getter private volatile Map<String, MonitorTemplate> templates = Collections.emptyMap();
    /** 默认套餐：/kila guardian on 启用的模板名列表。loadConfig 前为空列表（非 null）。 */
    @Getter private volatile List<String> defaultBundle = Collections.emptyList();

    public GuardianConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            PluginLoggerUtil.warn("守护系统", I18nService.tr("配置文件不存在: {}", CONFIG_FILE));
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = yaml.getBoolean("settings.enabled", true);
        this.heartbeatIntervalTicks = yaml.getLong("settings.heartbeat_interval_ticks", 20L);
        this.globalCooldownMillis = yaml.getLong("settings.global_cooldown_millis", 5000L);
        this.categoryCooldownMillis = yaml.getLong("settings.category_cooldown_millis", 300_000L);

        Map<String, MonitorTemplate> tmpl = new LinkedHashMap<>();
        ConfigurationSection templatesSec = yaml.getConfigurationSection("templates");
        if (templatesSec != null) {
            for (String name : templatesSec.getKeys(false)) {
                try {
                    MonitorTemplate t = parseTemplate(name, templatesSec.getConfigurationSection(name));
                    if (t != null) {
                        tmpl.put(name, t);
                    }
                } catch (Exception e) {
                    PluginLoggerUtil.warn("守护系统", I18nService.tr("解析 monitor 模板 {} 失败: {}", name, e.getMessage()));
                }
            }
        }
        this.templates = Collections.unmodifiableMap(tmpl);

        List<String> bundle = yaml.getStringList("default_bundle");
        this.defaultBundle = Collections.unmodifiableList(new ArrayList<>(bundle));

        PluginLoggerUtil.info("守护系统", I18nService.tr("已加载 {} 个守护模板，默认套餐 {} 个", tmpl.size(), bundle.size()));
    }

    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    public MonitorTemplate getTemplate(String name) {
        Map<String, MonitorTemplate> t = templates;
        return t != null ? t.get(name) : null;
    }

    private MonitorTemplate parseTemplate(String name, ConfigurationSection sec) {
        if (sec == null) return null;

        // predicate
        ConfigurationSection predSec = sec.getConfigurationSection("predicate");
        if (predSec == null) {
            throw new IllegalArgumentException("模板 " + name + " 缺少 predicate 段");
        }
        String predType = predSec.getString("type");
        if (predType == null || predType.isBlank()) {
            throw new IllegalArgumentException("模板 " + name + " predicate.type 为空");
        }
        Map<String, String> predParams = new LinkedHashMap<>();
        ConfigurationSection paramsSec = predSec.getConfigurationSection("params");
        if (paramsSec != null) {
            for (String k : paramsSec.getKeys(false)) {
                predParams.put(k, paramsSec.getString(k, ""));
            }
        }

        // action
        ConfigurationSection actSec = sec.getConfigurationSection("action");
        if (actSec == null) {
            throw new IllegalArgumentException("模板 " + name + " 缺少 action 段");
        }
        String actType = actSec.getString("type", "template");
        String template = actSec.getString("template", "");
        String skillName = actSec.getString("skill", "");
        String skillAction = actSec.getString("skill_action", "");
        Map<String, String> skillEntities = new LinkedHashMap<>();
        ConfigurationSection entSec = actSec.getConfigurationSection("entities");
        if (entSec != null) {
            for (String k : entSec.getKeys(false)) {
                skillEntities.put(k, entSec.getString(k, ""));
            }
        }

        // policy + cadence + category + priority
        Policy policy;
        try {
            policy = Policy.valueOf(sec.getString("policy", "WATCH_EDGE"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("模板 " + name + " policy 非法: " + sec.getString("policy"));
        }
        long cadence = sec.getLong("cadence_ticks", 20L);
        long cooldown = sec.getLong("cooldown_millis", 0L);

        AlertCategory category;
        try {
            category = AlertCategory.valueOf(sec.getString("category", "GENERAL"));
        } catch (IllegalArgumentException e) {
            category = AlertCategory.GENERAL;
        }
        AlertPriority priority;
        try {
            priority = AlertPriority.valueOf(sec.getString("priority", "NORMAL"));
        } catch (IllegalArgumentException e) {
            priority = AlertPriority.NORMAL;
        }

        return new MonitorTemplate(name, predType, Collections.unmodifiableMap(predParams),
                actType, template, skillName, skillAction, Collections.unmodifiableMap(skillEntities),
                policy, cadence, category, priority, cooldown);
    }

    /**
     * Monitor 模板定义（不可变）。由 {@link GuardianManager} 实例化为 {@link com.zm.kilacraftAI.service.guardian.monitor.Monitor}。
     *
     * @param name 模板名
     * @param predicateType 谓词原语名（PredicateRegistry key）
     * @param predicateParams 谓词参数（type→value 均为 string，由 PredicateRegistry.Factory 解析）
     * @param actionType 动作类型：template / skill / llm
     * @param actionTemplate 模板通知内容（type=template 时用）
     * @param skillName / skillAction / skillEntities 调 skill 时用（type=skill）
     * @param policy 策略
     * @param cadenceTicks 轮询间隔（Polling 源）
     * @param category / priority 冷却中心分类与优先级
     */
    public record MonitorTemplate(
            String name,
            String predicateType,
            Map<String, String> predicateParams,
            String actionType,
            String actionTemplate,
            String skillName,
            String skillAction,
            Map<String, String> skillEntities,
            Policy policy,
            long cadenceTicks,
            AlertCategory category,
            AlertPriority priority,
            long cooldownMillis
    ) {}
}
