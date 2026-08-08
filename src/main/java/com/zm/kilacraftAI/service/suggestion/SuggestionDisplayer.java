package com.zm.kilacraftAI.service.suggestion;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.common.util.TextWidthUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 推荐展示器：构造可点击的 {@link TextComponent} 并发送到玩家聊天框。
 *
 * <p>不走 {@code AIResponsePipeline}（那处理纯 String 的 Markdown→MC 转换 + 载体路由），
 * 推荐是带 ClickEvent/HoverEvent 的 UI 元素，必须直接 {@code player.spigot().sendMessage}。</p>
 *
 * <p>布局采用贪婪宽度换行：首行以 AI 前缀 + title 起始，后续推荐项按显示宽度
 * 逐项追加；当前行剩余空间不足时自动换行。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionDisplayer {

    /**
     * 聊天框单行像素宽度预算（默认 GUI 下约 320px）。
     */
    private static final int LINE_BUDGET_PX = 320;
    /**
     * 单条推荐文本上限（字符数），超长截断避免推荐溢出聊天框。
     */
    private static final int MAX_ITEM_CHARS = 50;
    /**
     * Minecraft 默认字体下单个 ASCII 字符的平均像素宽度（含间距）。
     */
    private static final int ASCII_PX = 6;
    /**
     * Minecraft 默认字体下单个全角字符（CJK）的近似像素宽度。
     */
    private static final int FULLWIDTH_PX = 9;

    private final KilacraftAI plugin;

    public SuggestionDisplayer(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 计算字符串在 Minecraft 默认字体下的近似像素宽度。
     */
    private static int pixelWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            width += TextWidthUtil.isFullWidth(cp) ? FULLWIDTH_PX : ASCII_PX;
            i += Character.charCount(cp);
        }
        return width;
    }

    /**
     * 通用展示：标题/点击提示/分隔符参数化，供推荐系统与触发通知（可执行操作）等调用者复用。
     *
     * @param player    目标玩家
     * @param items     可点击项文本列表（点击后执行 /ai + 文本）
     * @param title     展示标题
     * @param hint      悬停提示（点击行为说明）
     * @param separator 项间分隔符
     */
    public void display(Player player, List<String> items, String title, String hint, String separator) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String prefix = MessageUtil.getAIPrefix();
        int titleWidth = pixelWidth(TextWidthUtil.stripColors(prefix + title));
        int lineRemaining = LINE_BUDGET_PX - titleWidth;
        // 分隔符显示宽度（去色后测量）
        int sepWidth = pixelWidth(TextWidthUtil.stripColors(separator));

        TextComponent full = new TextComponent(prefix + title);
        boolean firstOnLine = true;

        for (String item : items) {
            String question = truncate(item);
            String plainLabel = "[" + question + "]";
            String label = "§b" + plainLabel;
            int itemWidth = pixelWidth(plainLabel);

            // 当前行剩余空间不足时换行
            int needWidth = firstOnLine ? itemWidth : sepWidth + itemWidth;
            if (lineRemaining < needWidth) {
                full.addExtra(new TextComponent("\n"));
                lineRemaining = LINE_BUDGET_PX;
                firstOnLine = true;
                needWidth = itemWidth;
            }

            if (!firstOnLine) {
                full.addExtra(new TextComponent(separator));
            }

            TextComponent clickable = new TextComponent(label);
            clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ai " + question));
            clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.GRAY + hint).create()));
            full.addExtra(clickable);

            lineRemaining -= needWidth;
            firstOnLine = false;
        }

        // runTask 是 fire-and-forget 投递，排队期间玩家可能下线；
        // 必须在主线程执行时再次检查 isOnline，避免对离线玩家 sendMessage
        FoliaCompat.runTask(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().sendMessage(full);
            } else {
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("玩家 {} 下线，跳过展示 {} 个推荐", player.getName(), items.size()));
            }
        });
    }

    private static String truncate(String s) {
        return s.length() <= MAX_ITEM_CHARS ? s : s.substring(0, MAX_ITEM_CHARS);
    }
}
