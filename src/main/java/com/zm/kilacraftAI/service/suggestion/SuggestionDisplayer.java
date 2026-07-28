package com.zm.kilacraftAI.service.suggestion;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.SuggestionConfigManager;
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
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionDisplayer {

    /**
     * 紧凑单行布局与逐行布局的分界：≤3 项单行，≥4 项逐行（避免超出聊天框宽度）。
     */
    private static final int LIST_LAYOUT_THRESHOLD = 4;
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

        boolean listLayout = suggestions.size() >= LIST_LAYOUT_THRESHOLD;

        TextComponent full = new TextComponent(MessageUtil.getAIPrefix() + title);
        if (listLayout) {
            full.addExtra(new TextComponent("\n"));
        }

        int size = suggestions.size();
        for (int i = 0; i < size; i++) {
            String question = truncate(suggestions.get(i));
            String label;
            if (listLayout) {
                label = " §b[" + (i + 1) + "] " + question;
            } else {
                label = "§b[" + question + "]";
            }

            TextComponent clickable = new TextComponent(label);
            clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ai " + question));
            clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.GRAY + hint).create()));
            full.addExtra(clickable);

            if (i < size - 1) {
                full.addExtra(new TextComponent(listLayout ? "\n" : separator));
            }
        }

        // runTask 是 fire-and-forget 投递，排队期间玩家可能下线；
        // 必须在主线程执行时再次检查 isOnline，避免对离线玩家 sendMessage
        FoliaCompat.runTask(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().sendMessage(full);
            }
        });
    }

    private static String truncate(String s) {
        return s.length() <= MAX_ITEM_CHARS ? s : s.substring(0, MAX_ITEM_CHARS);
    }
}
