package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.command.QueryTarget;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /kila memory [玩家名]：查看玩家画像与运行时统计（双维度，query.self / memory.other）。
 * 数据源 kca_player_profile：展示登录统计、AI 画像分析状态，以及画像八维度详情（解析 extendedData）。
 */
public final class MemoryCommand {

    private MemoryCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null) {
            sender.sendMessage(lm.getCommandMemoryNotInit());
            return;
        }

        String nameArg = args.length >= 2 ? args[1] : null;
        QueryTarget.Resolved target = QueryTarget.resolve(sender, nameArg, PluginPermissionEnum.QUERY_SELF, PluginPermissionEnum.MEMORY_OTHER, false);
        if (target == null) return;

        FoliaCompat.getIOPool().execute(() -> {
            try (Connection conn = dbManager.getConnection()) {
                UUID uuid = target.uuid();
                if (uuid == null) {
                    uuid = QueryTarget.resolveOfflineUuid(conn, dbManager.getTablePrefix(), target.displayName());
                    if (uuid == null) {
                        FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.replacePlaceholders(lm.getCommandMemoryPlayerNotFound(), "player", target.displayName())));
                        return;
                    }
                }
                PlayerProfileDao dao = new PlayerProfileDao(dbManager.getTablePrefix());
                PlayerProfile profile = dao.loadByUuid(conn, uuid);
                List<String> lines = buildLines(target.displayName(), profile, lm);
                FoliaCompat.runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (Exception e) {
                PluginLoggerUtil.error("画像", I18nService.tr("查询玩家画像失败: {}", e.getMessage()), e);
                FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandMemoryQueryFailed()));
            }
        });
    }

    public static List<String> buildLines(String displayName, PlayerProfile profile, LanguageManager lm) {
        List<String> lines = new ArrayList<>();
        lines.add(lm.replacePlaceholders(lm.getCommandMemoryTitle(), "player", displayName));
        if (profile == null) {
            lines.add(lm.getCommandMemoryEmpty());
            return lines;
        }
        lines.add(lm.replacePlaceholders(lm.getCommandMemoryFirstLogin(), "value", AdminSkillUtil.formatTimestamp(profile.getFirstLogin())));
        lines.add(lm.replacePlaceholders(lm.getCommandMemoryLastLogin(), "value", AdminSkillUtil.formatTimestamp(profile.getLastLogin())));
        lines.add(lm.replacePlaceholders(lm.getCommandMemoryLoginCount(), "value", String.valueOf(profile.getLoginCount())));
        lines.add(lm.replacePlaceholders(lm.getCommandMemoryPlaytime(), "value", formatDuration(profile.getTotalPlaytimeMs(), lm)));
        if (profile.getProfileAnalyzedAt() > 0) {
            lines.add(lm.replacePlaceholders(lm.getCommandMemoryAnalyzed(), "value", AdminSkillUtil.formatTimestamp(profile.getProfileAnalyzedAt())));
            // 画像八维度详情（仅已分析时展示；空值显示 -；标签优先取 language.yml 配置，缺失回退内置常量）
            Map<String, Object> ext = profile.getExtendedData();
            List<String> labels = lm.getCommandMemoryProfileLabels();
            for (int i = 0; i < PROFILE_FIELDS.length; i++) {
                String label = (labels != null && i < labels.size() && labels.get(i) != null && !labels.get(i).isEmpty()) ? labels.get(i) : PROFILE_LABELS[i];
                Object raw = ext != null ? ext.get(PROFILE_FIELDS[i]) : null;
                String value = raw != null ? String.valueOf(raw).trim() : "";
                lines.add(lm.replacePlaceholders(lm.getCommandMemoryProfileField(), "label", label, "value", value.isEmpty() ? "§7-" : value));
            }
        } else {
            lines.add(lm.getCommandMemoryNotAnalyzed());
        }
        return lines;
    }

    public static String formatDuration(long ms, LanguageManager lm) {
        if (ms <= 0) return lm.replacePlaceholders(lm.getCommandMemoryDurationMinutes(), "n", "0");
        long totalMin = ms / 60000;
        long days = totalMin / (60 * 24);
        long hours = (totalMin % (60 * 24)) / 60;
        long mins = totalMin % 60;
        if (days > 0)
            return lm.replacePlaceholders(lm.getCommandMemoryDurationDays(), "n", String.valueOf(days), "m", String.valueOf(hours));
        if (hours > 0)
            return lm.replacePlaceholders(lm.getCommandMemoryDurationHours(), "n", String.valueOf(hours), "m", String.valueOf(mins));
        return lm.replacePlaceholders(lm.getCommandMemoryDurationMinutes(), "n", String.valueOf(mins));
    }

    /**
     * 画像八维度 key（与 ProfileAnalysisService 写入侧一致）
     */
    private static final String[] PROFILE_FIELDS = {"playstyle", "personality", "interests", "boundaries", "communication", "spatial", "facts", "notes"};
    /**
     * 八维度中文标签（tr 经 messages_en.yml 翻译，与 ProfileManager 注入提示词同源）
     */
    private static final String[] PROFILE_LABELS = {"游戏风格", "性格特征", "兴趣偏好", "交互禁忌", "回复偏好", "空间记忆", "已知事实", "特别观察"};
}
