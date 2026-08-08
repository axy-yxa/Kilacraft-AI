package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.ProbeSource;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit 只读查询类 Skill 的共享基类
 *
 * <p>5 个 {@code Bukkit*Skill} 子技能（player.inventory/status/info、world、server）的共同骨架，
 * 承载 skill-creator-kilacraft 标准结构：常量区、构造器自注册配置、getConfig 实时读、标准 getter、
 * execute 固定执行序（玩家空检 → 权限复查 → action 派发）。</p>
 *
 * <p><b>线程模型（零回归核心）</b>：{@link #execute} 直接在调用方 IO 线程执行，不包 {@code runTask}——
 * 与原 {@code GenericBukkitAPISkill} 一致。58 个异步安全的只读 API 由子类 handler 直接在 IO 线程调用；
 * 13 个触及 chunk/inventory/world 状态的 API 由 handler 内部按 target 类型选
 * {@code FoliaCompat.callSync}/{@code callSyncOnEntity}（阻塞 5s）。<br>
 * 不引入「整体上主线程」的调度——那会把 58 个并发查询串行化，改变并发模型。</p>
 *
 * <p>子类职责：定义 {@code SKILL_NAME}/{@code LOG_PREFIX} 常量、实现 {@link #getName()}/
 * {@link #getRequiredPermission()}/{@link #getLogPrefix()}/{@link #executeActions}，
 * 并实现 {@link ProbeSource#getProbeableActions()} 返回各自只读 action 集合。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public abstract class AbstractBukkitQuerySkill implements Skill, ProbeSource {

    private final SkillConfigManager configManager;

    protected AbstractBukkitQuerySkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    /**
     * 实时读取当前 Skill 的配置快照（支持热重载）。
     */
    protected SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig(this);
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null && !config.getActionDescriptions().isEmpty()) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Player player = context.getPlayer();

        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }

        String permission = getRequiredPermission();
        if (!player.hasPermission(permission)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", permission)));
        }

        try {
            // 直接在调用方 IO 线程执行（与原 GenericBukkitAPISkill 一致，不包 runTask）。
            // 主线程/区域线程调度由各 handler 按需在内部决定。
            SkillResult result = executeActions(action, player, context.getEntities());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            PluginLoggerUtil.error(getLogPrefix(), I18nService.tr("执行失败: {}", e.getMessage()), e);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("执行失败: {}", e.getMessage())));
        }
    }

    /**
     * 子类实现：按 action 派发到具体 handler。
     */
    protected abstract SkillResult executeActions(String action, Player player, Map<String, String> entities);

    /**
     * 子类提供的日志模块名（如「Bukkit状态查询」），用于 {@link PluginLoggerUtil}。
     */
    protected abstract String getLogPrefix();
}
