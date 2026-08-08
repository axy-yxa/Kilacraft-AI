package com.zm.kilacraftAI.skills.admin;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.update.UpdateChecker;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 插件版本信息查询技能
 *
 * <p>提供当前版本自检、最新版本查询、指定版本更新日志读取能力。
 * 数据源为 Gitee/GitHub Release API（按 i18n 语言选源），纯只读查询。</p>
 * <ul>
 *   <li>{@code check_update} — 当前版本自检 + 查最新版（对比是否有更新 + 最新版完整信息含更新日志和下载地址）</li>
 *   <li>{@code read_changelog} — 读取指定版本的完整更新日志</li>
 *   <li>{@code list_versions} — 列出近期所有版本（tag + 标题 + 日期）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-09
 */
public class VersionInfoSkill implements Skill {

    private static final String SKILL_NAME = "version_info";
    private static final String LOG_PREFIX = "版本更新";

    private final SkillConfigManager configManager;

    public VersionInfoSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return (config != null && !config.getDescription().isEmpty()) ? config.getDescription() : null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null && !config.getHints().isEmpty()) ? new ArrayList<>(config.getHints()) : Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.ADMIN_INFO.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        // 权限校验：player 为 null 视为无权限（正常调用路径必有在线 player）
        Player caller = context.getPlayer();
        if (caller == null || !PluginPermissionEnum.ADMIN_INFO.hasPermission(caller)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.ADMIN_INFO.getNode())));
        }

        String action = context.getAction();
        if (action == null || action.isEmpty()) {
            action = "check_update";
        }

        return switch (action) {
            case "check_update" -> executeCheckUpdate(context);
            case "read_changelog" -> executeReadChangelog(context);
            case "list_versions" -> executeListVersions(context);
            default -> SkillResult.failure(I18nService.tr("未知动作: {}", action)).toFuture();
        };
    }

    /**
     * 当前版本自检 + 查最新版
     */
    private CompletableFuture<SkillResult> executeCheckUpdate(SkillContext context) {
        return CompletableFuture.supplyAsync(() -> {
            UpdateChecker checker = KilacraftAI.getInstance().getUpdateChecker();
            if (checker == null) {
                return SkillResult.failure(I18nService.tr("版本检测器未初始化"));
            }
            String current = KilacraftAI.getInstance().getDescription().getVersion();

            try {
                UpdateChecker.ReleaseInfo latest = checker.fetchLatestRelease();
                if (latest == null) {
                    return SkillResult.failure(I18nService.tr("无法获取最新版本信息，请检查网络连接或稍后重试"));
                }

                boolean hasUpdate = UpdateChecker.isNewerVersion(current, latest.tagName());

                Map<String, Object> dataMap = new LinkedHashMap<>();
                dataMap.put("current_version", current);
                dataMap.put("latest_version", latest.tagName());
                dataMap.put("has_update", hasUpdate);
                dataMap.put("release_url", latest.htmlUrl());
                dataMap.put("published_at", latest.publishedAt());
                dataMap.put("changelog", latest.body());

                StringBuilder msg = new StringBuilder();
                if (hasUpdate) {
                    msg.append(I18nService.tr("当前版本 v{} 不是最新版。", current)).append("\n");
                } else {
                    msg.append(I18nService.tr("当前版本 v{} 已是最新版。", current)).append("\n");
                }
                msg.append(I18nService.tr("最新版本: {}", latest.tagName())).append("\n");
                if (!latest.publishedAt().isEmpty()) {
                    msg.append(I18nService.tr("发布日期: {}", latest.publishedAt())).append("\n");
                }
                if (!latest.htmlUrl().isEmpty()) {
                    msg.append(I18nService.tr("下载地址: {}", latest.htmlUrl())).append("\n");
                }
                if (!latest.body().isEmpty()) {
                    msg.append(I18nService.tr("更新内容:\n{}", latest.body()));
                }
                return SkillResult.success(msg.toString().trim(), dataMap);
            } catch (Exception e) {
                PluginLoggerUtil.warn(LOG_PREFIX, "check_update 失败: {}", e.getMessage());
                return SkillResult.failure(I18nService.tr("版本检查失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 读取指定版本的完整更新日志
     */
    private CompletableFuture<SkillResult> executeReadChangelog(SkillContext context) {
        String versionRaw = SkillEntityHelper.getString(context, "version");
        if (versionRaw == null || versionRaw.isBlank()) {
            return SkillResult.needInfo(I18nService.tr("请指定要查询的版本号，例如 v2.1.2")).toFuture();
        }

        final String version = versionRaw.trim();

        return CompletableFuture.supplyAsync(() -> {
            UpdateChecker checker = KilacraftAI.getInstance().getUpdateChecker();
            if (checker == null) {
                return SkillResult.failure(I18nService.tr("版本检测器未初始化"));
            }

            try {
                UpdateChecker.ReleaseInfo release = checker.fetchReleaseByTag(version);
                if (release == null) {
                    return SkillResult.failure(I18nService.tr("未找到版本 {} 的发布信息，请确认版本号是否正确", version));
                }

                Map<String, Object> dataMap = new LinkedHashMap<>();
                dataMap.put("version", release.tagName());
                dataMap.put("changelog", release.body());
                dataMap.put("release_url", release.htmlUrl());
                dataMap.put("published_at", release.publishedAt());

                String message = I18nService.tr("版本 {}（发布于 {}）的更新日志：\n{}", release.tagName(), release.publishedAt().isEmpty() ? I18nService.tr("未知") : release.publishedAt(), release.body());
                return SkillResult.success(message, dataMap);
            } catch (Exception e) {
                PluginLoggerUtil.warn(LOG_PREFIX, "read_changelog 失败: {}", e.getMessage());
                return SkillResult.failure(I18nService.tr("查询版本更新日志失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 列出近期所有版本
     */
    private CompletableFuture<SkillResult> executeListVersions(SkillContext context) {
        final int finalLimit = SkillEntityHelper.getInt(context, "limit", 10);

        return CompletableFuture.supplyAsync(() -> {
            UpdateChecker checker = KilacraftAI.getInstance().getUpdateChecker();
            if (checker == null) {
                return SkillResult.failure(I18nService.tr("版本检测器未初始化"));
            }

            try {
                List<UpdateChecker.ReleaseInfo> releases = checker.fetchReleases(finalLimit);
                if (releases.isEmpty()) {
                    return SkillResult.failure(I18nService.tr("无法获取版本列表，请检查网络连接或稍后重试"));
                }

                String current = KilacraftAI.getInstance().getDescription().getVersion();

                StringBuilder sb = new StringBuilder(I18nService.tr("近期发布的版本（共 {} 个）：\n", releases.size()));
                List<Map<String, Object>> versionsList = new ArrayList<>();

                for (int i = 0; i < releases.size(); i++) {
                    UpdateChecker.ReleaseInfo r = releases.get(i);
                    boolean isCurrent = r.tagName().equalsIgnoreCase("v" + current) || r.tagName().equalsIgnoreCase(current);
                    String marker = isCurrent ? I18nService.tr("（当前运行）") : "";
                    String date = r.publishedAt().isEmpty() ? I18nService.tr("未知") : r.publishedAt();
                    sb.append(i + 1).append(". ").append(r.tagName()).append(" — ").append(r.name()).append("（").append(date).append("）").append(marker).append("\n");

                    Map<String, Object> versionInfo = new LinkedHashMap<>();
                    versionInfo.put("tag", r.tagName());
                    versionInfo.put("name", r.name());
                    versionInfo.put("date", r.publishedAt());
                    versionInfo.put("is_current", isCurrent);
                    versionsList.add(versionInfo);
                }

                Map<String, Object> dataMap = new LinkedHashMap<>();
                dataMap.put("count", releases.size());
                dataMap.put("current_version", current);
                dataMap.put("versions", versionsList);

                return SkillResult.success(sb.toString().trim(), dataMap);
            } catch (Exception e) {
                PluginLoggerUtil.warn(LOG_PREFIX, "list_versions 失败: {}", e.getMessage());
                return SkillResult.failure(I18nService.tr("获取版本列表失败: {}", e.getMessage()));
            }
        }, FoliaCompat.getIOPool());
    }
}
