package com.zm.kilacraftAI.service.suggestion;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.common.util.TextWidthUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SuggestionConfigManager;
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
     * 聊天框单行保守宽度预算（约对应默认 310px 聊天框下 50 个标准 ASCII 字符）。
     */
    private static final int LINE_BUDGET = 50;
    /**
     * 单条推荐文本上限（字符数），超长截断避免推荐溢出聊天框。
     */
    private static final int MAX_ITEM_CHARS = 50;

    private final KilacraftAI plugin;
    private final SuggestionConfigManager config;

    public SuggestionDisplayer(KilacraftAI plugin, SuggestionConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void display(Player player, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }

        String title = config.getDisplayTitle();
        String separator = config.getDisplaySeparator();
        String hint = config.getDisplayClickHint();

        String prefix = MessageUtil.getAIPrefix();
        int titleWidth = TextWidthUtil.displayWidth(TextWidthUtil.stripColors(prefix + title));
        int lineRemaining = LINE_BUDGET - titleWidth;
        // 分隔符显示宽度（去色后测量）
        int sepWidth = TextWidthUtil.displayWidth(TextWidthUtil.stripColors(separator));

        TextComponent full = new TextComponent(prefix + title);
        boolean firstOnLine = true;

        for (String suggestion : suggestions) {
            String question = truncate(suggestion);
            String plainLabel = "[" + question + "]";
            String label = "§b" + plainLabel;
            int itemWidth = TextWidthUtil.displayWidth(plainLabel);

            // 当前行剩余空间不足时换行
            int needWidth = firstOnLine ? itemWidth : sepWidth + itemWidth;
            if (lineRemaining < needWidth) {
                full.addExtra(new TextComponent("\n"));
                lineRemaining = LINE_BUDGET;
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
        int count = suggestions.size();
        FoliaCompat.runTask(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().sendMessage(full);
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("已向 {} 展示 {} 个推荐", player.getName(), count));
            } else {
                PluginLoggerUtil.debug("对话推荐", I18nService.tr("玩家 {} 下线，跳过展示 {} 个推荐", player.getName(), count));
            }
        });
    }

    private static String truncate(String s) {
        return s.length() <= MAX_ITEM_CHARS ? s : s.substring(0, MAX_ITEM_CHARS);
    }
}
