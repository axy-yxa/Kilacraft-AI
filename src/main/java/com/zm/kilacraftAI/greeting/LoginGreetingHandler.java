package com.zm.kilacraftAI.greeting;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.db.ConversationPersistenceService;
import com.zm.kilacraftAI.db.ConversationSource;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.event.OfflineEventAggregator;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.event.ServerEvent;
import com.zm.kilacraftAI.profile.PlayerProfile;
import com.zm.kilacraftAI.profile.ProfileManager;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 登录问候处理器
 *
 * <p>监听玩家登录事件，延迟一段时间后：</p>
 * <ol>
 *     <li>加载玩家画像和三大分类的离线数据：
 *         <ul>
 *             <li>分类一：玩家自己的离线事件</li>
 *             <li>分类二：好友离线期间的动态</li>
 *             <li>分类三：摘要统计（游玩时长/登录次数/上次亮点）</li>
 *         </ul>
 *     </li>
 *     <li>构建问候上下文和提示词</li>
 *     <li>调用 LLM 生成个性化问候语</li>
 *     <li>通过 {@link com.zm.kilacraftAI.output.AIResponsePipeline} 输出</li>
 *     <li>写入持久化队列（source=GREETING），同时写入内存对话历史以支持追问</li>
 * </ol>
 *
 * @author Zm_Mmm
 * @since 2026-05-01
 */
public class LoginGreetingHandler implements Listener {

    private final KilacraftAI plugin;
    private final GreetingPromptBuilder promptBuilder;
    private final OfflineEventAggregator eventAggregator;

    public LoginGreetingHandler(KilacraftAI plugin, OfflineEventAggregator eventAggregator) {
        this.plugin = plugin;
        this.promptBuilder = new GreetingPromptBuilder();
        this.eventAggregator = eventAggregator;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isGreetingEnabled() || !config.isApiKeyConfigured()) return;

        Player player = event.getPlayer();
        final String playerName = player.getName();
        final java.util.UUID playerUuid = player.getUniqueId();

        // 问候冷却检查（同步读取内存缓存，无性能开销）
        int cooldownMinutes = config.getGreetingCooldownMinutes();
        if (cooldownMinutes > 0) {
            ProfileManager profileManager = plugin.getProfileManager();
            if (profileManager != null) {
                PlayerProfile profile = profileManager.getCachedProfile(playerUuid);
                if (profile != null && profile.getLastGreetingTime() > 0) {
                    long elapsed = System.currentTimeMillis() - profile.getLastGreetingTime();
                    if (elapsed < cooldownMinutes * 60L * 1000L) {
                        PluginLogger.debug("问候系统", "玩家 {} 在冷却期内，跳过问候", playerName);
                        return;
                    }
                }
            }
        }

        int delayTicks = config.getGreetingDelayTicks();

        FoliaCompat.runTaskLater(plugin, () -> generateGreeting(player, playerUuid, playerName), delayTicks);
    }

    private void generateGreeting(Player player, java.util.UUID playerUuid, String playerName) {
        if (!player.isOnline()) return;
        if (!plugin.getConfigManager().isGreetingEnabled() || !plugin.getConfigManager().isApiKeyConfigured()) return;

        ConfigManager config = plugin.getConfigManager();
        ProfileManager profileManager = plugin.getProfileManager();

        if (profileManager == null) return;

        String serverInfo = config.getGreetingServerInfo();

        profileManager.getProfile(playerUuid, profile -> {
            if (profile == null) {
                PluginLogger.warn("问候系统", "玩家画像加载失败，跳过问候: {}", playerName);
                return;
            }

            boolean isFirstLogin = profile.getLoginCount() <= 1;
            // loginCount == 0: loadOrCreate 刚创建但 updateLogin 尚未执行（极端竞态）
            // loginCount == 1: 首次登录的正常路径（onPlayerJoin 中 updateLogin 后 loginCount = 1）

            if (isFirstLogin) {
                GreetingContext context = GreetingContext.builder().player(player).profile(profile).firstLogin(true).offlineDurationMs(0).offlineEvents(Collections.emptyList()).onlineFriends(Collections.emptyList()).serverInfo(serverInfo).build();
                generateAndSend(context, playerName, playerUuid);

            } else {
                // lastLogout == 0 的场景：
                // 1. MySQL 全新建表后首次登录（last_logout 默认 0）
                // 2. 从 H2 迁移时丢失登出时间
                // 兜底：使用 lastLogin 作为参考点，避免产生 20578 天这种荒谬数值
                long rawLastLogout = profile.getLastLogout();
                if (rawLastLogout <= 0) {
                    rawLastLogout = profile.getLastLogin();
                }
                final long lastLogout = rawLastLogout;
                long offlineDuration = System.currentTimeMillis() - lastLogout;

                int maxOwnEvents = config.getGreetingMaxOwnOfflineEvents();
                int maxFriendEvents = config.getGreetingMaxFriendOfflineEvents();
                int maxSummaryEvents = config.getGreetingMaxSummaryEvents();

                // 三路查询合并为单次 IO 任务，共享一个数据库连接
                eventAggregator.loadAllOfflineDataForGreeting(playerUuid, playerName,
                        lastLogout, profile.getLastGreetingTime(), maxOwnEvents, maxFriendEvents, maxSummaryEvents, data -> {
                    SummaryStats summaryStats = computeSummaryStats(profile, data.highlights());

                    GreetingContext context = GreetingContext.builder()
                            .player(player).profile(profile).firstLogin(false)
                            .offlineDurationMs(offlineDuration).offlineEvents(data.ownEvents())
                            .friendEvents(data.friendEvents()).summaryStats(summaryStats)
                            .onlineFriends(data.onlineFriends()).serverInfo(serverInfo)
                            .build();

                    generateAndSend(context, playerName, playerUuid);
                });
            }
        });
    }

    /**
     * 调用 LLM 生成问候语并发送
     *
     * <p>注意：本方法可能在 IO 线程执行（被三大分类查询回调调用），
     * 不在此处访问 Player 对象（generateGreeting 已在 Bukkit 线程完成在线检查）。</p>
     */
    private void generateAndSend(GreetingContext context, String playerName, java.util.UUID playerUuid) {
        Player player = context.getPlayer();

        ConfigManager config = plugin.getConfigManager();

        // 优先读配置文件，配置文件没有才读硬编码默认值
        String customPrompt = context.isFirstLogin() ? config.getGreetingFirstLoginPrompt() : config.getGreetingReturningPrompt();

        String systemPrompt = promptBuilder.build(context, customPrompt);

        // 画像注入：在系统提示词末尾追加玩家画像摘要
        ProfileManager pm = plugin.getProfileManager();
        if (config.isProfileInjectionEnabled() && pm != null) {
            String profileSummary = pm.buildProfileSummary(playerUuid);
            if (!profileSummary.isEmpty()) {
                systemPrompt = systemPrompt + "\n\n" + profileSummary;
            }
        }

        PluginLogger.debug("问候系统", "问候语摘要: {}", systemPrompt);

        Deque<ConversationManager.Message> emptyHistory = new ArrayDeque<>();
        String userMessage = context.isFirstLogin() ? "请欢迎新玩家 " + playerName : "请欢迎 " + playerName + " 回来";

        PlayerResponseHandler handler = new PlayerResponseHandler(player, OutputScenario.GREETING);

        OutputConfigManager outputConfig = plugin.getConfigManager().getOutputConfigManager();
        if (outputConfig.isStreamEnabled()) {
            OutputChannel channel = plugin.getResponsePipeline().getChannelForScenario(OutputScenario.GREETING);
            plugin.getResponsePipeline().startStream(player, channel, true);
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(userMessage, playerName, emptyHistory, handler, systemPrompt, false, false).thenAccept(greeting -> {
            if (greeting != null && player.isOnline()) {
                ConversationPersistenceService persistence = plugin.getPersistenceService();
                if (persistence != null) {
                    persistence.submit(playerUuid, "assistant", greeting, "", ConversationSource.GREETING.getValue());
                }

                // 写入内存对话历史，使玩家能对问候内容进行追问
                Deque<ConversationManager.Message> history = plugin.getConversationManager().getOrCreateHistory(playerUuid);
                history.add(new ConversationManager.Message("assistant", greeting));

                updateGreetingTime(playerUuid);
            }
        }).exceptionally(throwable -> {
            PluginLogger.warn("问候系统", "生成问候语失败: {}", throwable.getMessage());
            return null;
        });
    }

    private void updateGreetingTime(java.util.UUID playerUuid) {
        ProfileManager profileManager = plugin.getProfileManager();
        if (profileManager != null) {
            profileManager.updateGreetingTime(playerUuid);
        }
    }

    /**
     * 根据玩家画像和上次游玩亮点计算摘要统计数据（分类三）
     *
     * @param profile    玩家画像
     * @param highlights 上次游玩亮点事件列表
     * @return 摘要统计数据
     */
    private SummaryStats computeSummaryStats(PlayerProfile profile, List<ServerEvent> highlights) {
        long daysSinceFirstLogin = 0;
        if (profile.getFirstLogin() > 0) {
            daysSinceFirstLogin = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - profile.getFirstLogin());
        }
        return new SummaryStats(
                profile.getTotalPlaytimeMs(),
                profile.getLoginCount(),
                daysSinceFirstLogin,
                highlights != null ? highlights : Collections.emptyList()
        );
    }
}
