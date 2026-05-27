package com.zm.kilacraftAI.service.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.common.util.MessageUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoreboard Sidebar 管理器
 *
 * <p>负责 Scoreboard 的创建、更新、清理和生命周期管理。</p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>支持最多 15 行（Sidebar 限制）</li>
 *   <li>每行最多 128 字符（Minecraft 1.13+）</li>
 *   <li>自动分页：超过 15 行时自动拆分多页</li>
 *   <li>持久显示：直到新消息覆盖或手动清理</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class ScoreboardManager {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;

    /**
     * 玩家活跃的 Scoreboard 映射
     * <p>Key: Player UUID, Value: ScoreboardInfo</p>
     */
    private final Map<UUID, ScoreboardInfo> activeScoreboards = new ConcurrentHashMap<>();

    public ScoreboardManager(KilacraftAI plugin, OutputConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 发送 Sidebar 消息
     *
     * <p>逻辑：</p>
     * <ol>
     *   <li>将消息按行分割（支持 \n 换行）</li>
     *   <li>如果超过 15 行，自动分页</li>
     *   <li>如果玩家已有 Scoreboard，更新内容</li>
     *   <li>如果没有，创建新的 Scoreboard</li>
     * </ol>
     *
     * @param player  目标玩家
     * @param message 消息内容（支持 \n 换行）
     */
    public void sendSidebar(Player player, String message) {
        // Scoreboard API 必须在主线程执行
        if (!FoliaCompat.isPrimaryThread()) {
            FoliaCompat.runTask(plugin, () -> sendSidebarInternal(player, message));
            return;
        }

        sendSidebarInternal(player, message);
    }

    /**
     * 发送 Sidebar（必须在主线程调用）
     */
    private void sendSidebarInternal(Player player, String message) {
        UUID playerId = player.getUniqueId();

        // 先取消旧的定时器（防止并发问题）
        ScoreboardInfo oldInfo = activeScoreboards.get(playerId);
        if (oldInfo != null && oldInfo.getRemovalTask() != null) {
            oldInfo.getRemovalTask().cancel();
        }

        // 移除旧的 Scoreboard
        removeSidebar(playerId);

        // 去掉前缀后的纯内容
        String content = removePrefix(message);

        // 分割消息为行
        List<String> lines = splitMessageToLines(content);

        // 分页
        List<List<String>> pages = splitToPages(lines);

        // 创建新的 Scoreboard
        ScoreboardInfo info = new ScoreboardInfo(player, pages, MessageUtil.getAIPrefix());
        activeScoreboards.put(playerId, info);

        // 显示第一页
        info.showPage(0);

        // 定时清理
        int durationSeconds = config.getSidebarDurationSeconds();
        if (durationSeconds > 0) {
            scheduleRemoval(playerId, durationSeconds);
        }
    }

    /**
     * 移除消息中的 AI 前缀
     */
    private String removePrefix(String message) {
        String prefix = MessageUtil.getAIPrefix();
        if (message.startsWith(prefix)) {
            return message.substring(prefix.length());
        }
        return message;
    }

    /**
     * 将消息分割为行
     * <p>颜色代码（§x）不计入可见字符长度，但切片时保留完整颜色代码</p>
     * <p>根据当前语言自动选择每行最大字符数（中文/英文视觉宽度不同）</p>
     */
    private List<String> splitMessageToLines(String message) {
        List<String> lines = new ArrayList<>();
        String[] rawLines = message.split("\n");

        // 根据当前语言获取每行最大字符数
        int maxCharsPerLine = config.getSidebarMaxCharsPerLine(plugin.getConfigManager().isChinese());

        for (String rawLine : rawLines) {
            // 移除颜色代码后再计算可见字符长度
            String stripped = ChatColor.stripColor(rawLine);

            if (stripped.length() <= maxCharsPerLine) {
                // 单行不超过限制，直接添加
                lines.add(rawLine);
            } else {
                // 超长行：按可见字符数分割，保留颜色代码
                int[] rawIndexMap = buildStrippedToRawIndexMap(rawLine);
                int visibleStart = 0;
                while (visibleStart < stripped.length()) {
                    int visibleEnd = Math.min(visibleStart + maxCharsPerLine, stripped.length());
                    // 通过映射获取 rawLine 中对应的起止位置
                    int rawStart = rawIndexMap[visibleStart];
                    int rawEnd = (visibleEnd < stripped.length()) ? rawIndexMap[visibleEnd] : rawLine.length();
                    lines.add(rawLine.substring(rawStart, rawEnd));
                    visibleStart = visibleEnd;
                }
            }
        }

        return lines;
    }

    /**
     * 构建 stripped 位置 → rawLine 位置的映射数组
     * <p>rawIndexMap[i] 表示 stripped 的第 i 个可见字符在 rawLine 中的起始位置</p>
     * <p>颜色代码格式：§ + 1字符，共2字符</p>
     */
    private int[] buildStrippedToRawIndexMap(String rawLine) {
        String stripped = ChatColor.stripColor(rawLine);
        int[] map = new int[stripped.length()];
        int rawPos = 0;
        int visiblePos = 0;
        while (rawPos < rawLine.length() && visiblePos < stripped.length()) {
            if (rawLine.charAt(rawPos) == '§' && rawPos + 1 < rawLine.length()) {
                // 跳过颜色代码（§ + 1字符）
                rawPos += 2;
            } else {
                map[visiblePos] = rawPos;
                visiblePos++;
                rawPos++;
            }
        }
        return map;
    }

    /**
     * 将行分割为页
     */
    private List<List<String>> splitToPages(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();

        for (int i = 0; i < lines.size(); i += config.getSidebarMaxLinesPerPage()) {
            int end = Math.min(i + config.getSidebarMaxLinesPerPage(), lines.size());
            pages.add(new ArrayList<>(lines.subList(i, end)));
        }

        return pages;
    }

    /**
     * 定时移除 Scoreboard
     */
    private void scheduleRemoval(UUID playerId, int delaySeconds) {
        ScoreboardInfo info = activeScoreboards.get(playerId);
        if (info == null) {
            return;
        }

        // 取消旧的定时器（如果存在）
        if (info.getRemovalTask() != null && !info.getRemovalTask().isCancelled()) {
            info.getRemovalTask().cancel();
        }

        // 创建新的定时器
        info.removalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeSidebar(playerId), delaySeconds * 20L);
    }

    /**
     * 移除玩家的 Scoreboard
     */
    public void removeSidebar(UUID playerId) {
        ScoreboardInfo info = activeScoreboards.remove(playerId);
        if (info != null) {
            info.remove();
        }
    }

    /**
     * 清理所有 Scoreboard
     */
    public void cleanup() {
        activeScoreboards.values().forEach(ScoreboardInfo::remove);
        activeScoreboards.clear();
    }

    /**
     * Scoreboard 信息
     */
    @Getter
    private static class ScoreboardInfo {
        private final Player player;
        private final List<List<String>> pages;
        private final String title;
        private Scoreboard scoreboard;
        private Objective objective;
        private BukkitTask removalTask;  // 定时清理任务

        public ScoreboardInfo(Player player, List<List<String>> pages, String title) {
            this.player = player;
            this.pages = pages;
            this.title = title;
        }

        /**
         * 显示指定页
         */
        public void showPage(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pages.size()) {
                return;
            }

            // 创建新的 Scoreboard
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            // 转换颜色代码（支持 & 和 § 格式）
            String displayTitle = ChatColor.translateAlternateColorCodes('&', title);
            objective = scoreboard.registerNewObjective("kilacraft_ai", "dummy", displayTitle);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<String> lines = pages.get(pageIndex);

            // 从下往上添加行（Scoreboard 的分数越高越靠上）
            int score = lines.size();
            for (String line : lines) {
                objective.getScore(line).setScore(score--);
            }

            // 设置玩家的 Scoreboard
            player.setScoreboard(scoreboard);
        }

        /**
         * 移除 Scoreboard
         */
        public void remove() {
            // 取消定时清理任务（防止内存泄漏）
            if (removalTask != null && !removalTask.isCancelled()) {
                removalTask.cancel();
                removalTask = null;
            }

            if (scoreboard != null) {
                // 恢复玩家的原始 Scoreboard
                Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                player.setScoreboard(mainScoreboard);
            }
        }
    }
}
