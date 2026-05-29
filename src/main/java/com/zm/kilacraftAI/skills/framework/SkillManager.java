package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.dao.SkillLogDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.metrics.SkillInfo;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能管理器
 *
 * <p>负责注册和管理所有技能（基于 LLM 意图识别）</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class SkillManager {

    private final Map<String, Skill> skills;
    private final Map<String, String> skillSourcePlugins;
    private final KilacraftAI plugin;

    public SkillManager() {
        this.skills = new ConcurrentHashMap<>();
        this.skillSourcePlugins = new ConcurrentHashMap<>();
        this.plugin = KilacraftAI.getInstance();
    }

    /**
     * 注册技能
     *
     * @param skill 技能实例
     */
    public void registerSkill(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException(I18nService.tr("技能不能为空"));
        }

        String name = skill.getName();
        if (skills.containsKey(name)) {
            throw new IllegalArgumentException(I18nService.tr("技能已注册：{}", name));
        }

        skills.put(name, skill);
    }

    /**
     * 注册技能（带来源插件信息）
     *
     * @param skill        技能实例
     * @param sourcePlugin 来源插件名
     */
    public void registerSkill(Skill skill, String sourcePlugin) {
        registerSkill(skill);
        skillSourcePlugins.put(skill.getName(), sourcePlugin);
    }

    /**
     * 注销技能
     *
     * @param skillName 技能名称
     */
    public void unregisterSkill(String skillName) {
        skills.remove(skillName);
        skillSourcePlugins.remove(skillName);
    }

    /**
     * 获取已注册的技能
     *
     * @param skillName 技能名称
     * @return 技能实例，不存在则返回 null
     */
    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    /**
     * 获取所有已注册的技能
     *
     * @return 技能列表
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取所有 Skill 的元信息列表（用于 bStats 上报）
     *
     * @return SkillInfo 列表
     */
    public List<SkillInfo> getAllSkillInfoList() {
        List<SkillInfo> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            String skillName = skill.getName();
            String sourcePlugin = skillSourcePlugins.getOrDefault(skillName, "KilacraftAI");
            String type = isThirdPartySkill(skill) ? "third_party" : "built_in";
            result.add(new SkillInfo(skillName, type, sourcePlugin));
        }
        return result;
    }

    /**
     * 判断是否为第三方 Skill
     *
     * @param skill Skill 实例
     * @return true 表示第三方，false 表示内置
     */
    public static boolean isThirdPartySkill(Skill skill) {
        if (skill == null) return true;
        Package pkg = skill.getClass().getPackage();
        if (pkg == null) return true;
        String packageName = pkg.getName();
        // 内置 Skill 都在 com.zm.kilacraftAI 包下
        return !packageName.startsWith("com.zm.kilacraftAI");
    }

    /**
     * 根据意图执行技能（带错误隔离和安全消毒）
     *
     * <p>第三方 Skill 的异常会被 try-catch 捕获，不会影响 KilacraftAI 核心流程。</p>
     * <p>执行前会通过 {@link SkillSecurityFilter} 对entities进行安全消毒。</p>
     *
     * @param intent  识别出的意图
     * @param context 执行上下文
     * @return 执行结果
     */
    public CompletableFuture<SkillResult> executeSkillByIntent(SkillIntent intent, SkillContext context) {
        if (intent == null || !intent.isValid()) {
            return CompletableFuture.completedFuture(SkillResult.failure("无法识别你的意图，请详细描述你的需求"));
        }

        String skillName = intent.getSkillName();
        Skill skill = skills.get(skillName);

        if (skill == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("抱歉，我没有找到名为 '{}' 的技能", skillName)));
        }

        // 检查技能是否可用（带错误隔离）
        try {
            if (!skill.isAvailable(context)) {
                return CompletableFuture.completedFuture(SkillResult.failure("抱歉，该功能暂时不可用"));
            }
        } catch (Exception e) {
            PluginLoggerUtil.warn("技能管理", I18nService.tr("检查技能可用性时异常：{} - {}", skillName, e.getMessage()), e);
            return CompletableFuture.completedFuture(SkillResult.failure("抱歉，该功能暂时不可用"));
        }

        // 安全消毒：扫描entities中所有Value，在线玩家名不是自己则替换
        Map<String, String> sanitizedEntities = SkillSecurityFilter.sanitize(skillName, intent.getAction(), context);
        if (sanitizedEntities == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("执行上下文异常"));
        }

        // 如果entities被消毒过，需要重建SkillContext
        SkillContext executionContext = context;
        if (sanitizedEntities != context.getEntities()) {
            executionContext = new SkillContext(context.getPlayer(), context.getAction(), sanitizedEntities);
            // 保留审计字段
            executionContext.withAudit(context.getTriggerMessage(), context.getExecutionSource());
        }

        PluginLoggerUtil.debug("技能管理", "开始执行技能：{}, action={}", skillName, intent.getAction());

        // 统计埋点：记录技能调用
        MetricsCollector.getInstance().recordSkillAction(skillName, intent.getAction());
        MetricsCollector.getInstance().recordSkillSource(skill);

        // 审计埋点：记录技能执行
        String playerUuid = context.getPlayer() != null ? context.getPlayer().getUniqueId().toString() : null;
        String triggerMessage = context.getTriggerMessage();
        String executionSource = context.getExecutionSource() != null ? context.getExecutionSource() : "agent";
        long startTime = System.currentTimeMillis();

        // 执行技能（带错误隔离，第三方 Skill 异常不影响核心流程）
        try {
            return skill.execute(executionContext).thenApply(result -> {
                submitSkillLog(playerUuid, skillName, intent.getAction(), sanitizedEntities, result, System.currentTimeMillis() - startTime, triggerMessage, executionSource);
                return result;
            }).exceptionally(ex -> {
                PluginLoggerUtil.error("技能管理", I18nService.tr("技能执行异常（可能为第三方技能）：{} - {}", skillName, ex.getMessage()), ex);
                submitSkillLog(playerUuid, skillName, intent.getAction(), sanitizedEntities, SkillResult.failure(ex.getMessage()), System.currentTimeMillis() - startTime, triggerMessage, executionSource);
                return SkillResult.failure(I18nService.tr("技能执行出错，请联系管理员"));
            });
        } catch (Exception e) {
            PluginLoggerUtil.error("技能管理", I18nService.tr("技能执行失败：{} - {}", skillName, e.getMessage()), e);
            submitSkillLog(playerUuid, skillName, intent.getAction(), sanitizedEntities, SkillResult.failure(e.getMessage()), System.currentTimeMillis() - startTime, triggerMessage, executionSource);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("技能执行出错，请联系管理员")));
        }
    }

    /**
     * 提交技能执行审计日志（异步写入 DB）
     */
    private void submitSkillLog(String playerUuid, String skillName, String action, Map<String, String> entities, SkillResult result, long elapsedMs, String triggerMessage, String executionSource) {
        if (plugin.getDatabaseManager() == null || playerUuid == null) return;

        try {
            var gson = new Gson();
            String entitiesJson = entities != null ? gson.toJson(entities) : null;

            FoliaCompat.getIOPool().submit(() -> {
                try (var conn = plugin.getDatabaseManager().getConnection()) {
                    var skillLogDao = new SkillLogDao(plugin.getDatabaseManager().getTablePrefix());
                    String serverId = plugin.getDatabaseManager().getConfig().getServerId();
                    skillLogDao.insert(conn, playerUuid, skillName, action, entitiesJson, result.isSuccess(), result.getMessage(), triggerMessage, elapsedMs, executionSource, serverId != null ? serverId : "");
                } catch (Exception e) {
                    PluginLoggerUtil.debug("技能管理", "写入审计日志失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            // 审计日志不应影响技能执行
            PluginLoggerUtil.debug("技能管理", "提交审计日志失败: {}", e.getMessage());
        }
    }

}
