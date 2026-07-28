package com.zm.kilacraftAI.service.greeting;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.enums.OutputChannelEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.greeting.GreetingContext;
import com.zm.kilacraftAI.model.greeting.PlayerVanillaStats;
import com.zm.kilacraftAI.model.greeting.SummaryStats;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.event.OfflineEventAggregator;
import com.zm.kilacraftAI.service.profile.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI 登录问候处理器
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
        final UUID playerUuid = player.getUniqueId();

        // 问候冷却检查
        int cooldownMinutes = config.getGreetingCooldownMinutes();
        if (cooldownMinutes > 0) {
            ProfileManager profileManager = plugin.getProfileManager();
            if (profileManager != null) {
                PlayerProfile profile = profileManager.getCachedProfile(playerUuid);
                if (profile != null && profile.getLastGreetingTime() > 0) {
                    long elapsed = System.currentTimeMillis() - profile.getLastGreetingTime();
                    if (elapsed < cooldownMinutes * 60L * 1000L) {
                        PluginLoggerUtil.debug("问候系统", "玩家 {} 在冷却期内，跳过问候", playerName);
                        return;
                    }
                }
            }
        }

        int delayTicks = config.getGreetingDelayTicks();

        FoliaCompat.runTaskLater(plugin, () -> generateGreeting(player, playerUuid, playerName), delayTicks);
    }

    private void generateGreeting(Player player, UUID playerUuid, String playerName) {
        if (!player.isOnline()) return;
        if (!plugin.getConfigManager().isGreetingEnabled() || !plugin.getConfigManager().isApiKeyConfigured()) return;

        ConfigManager config = plugin.getConfigManager();
        ProfileManager profileManager = plugin.getProfileManager();

        if (profileManager == null) return;

        String serverInfo = config.getGreetingServerInfo();

        // 主线程采集 Bukkit 原版统计（getStatistic() 必须在主线程调用）
        PlayerVanillaStats vanillaStats;
        try {
            vanillaStats = PlayerVanillaStats.collect(player);
        } catch (Exception e) {
            PluginLoggerUtil.debug("问候系统", "Bukkit Stats 采集失败，降级跳过: {}", e.getMessage());
            vanillaStats = null;
        }

        final PlayerVanillaStats finalVanillaStats = vanillaStats;

        // 主线程提前缓存权限检查结果
        final boolean isAdminHealth = PluginPermissionEnum.ADMIN_HEALTH.hasPermission(player);
        final boolean isAdminInfo = PluginPermissionEnum.ADMIN_INFO.hasPermission(player);
        // 守护状态（主线程读取，传入 context 供问候推荐）
        // guardianManager==null 时设为 true——守护系统不可用，不注入推荐段落（避免玩家开启后失败）
        final boolean guardianEnabled = plugin.getGuardianManager() == null || plugin.getGuardianManager().isGuardianEnabled(playerUuid);

        profileManager.getProfile(playerUuid, profile -> {
            if (profile == null) {
                PluginLoggerUtil.warn("问候系统", "玩家画像加载失败，跳过问候: {}", playerName);
                return;
            }

            boolean isFirstLogin = profile.getLoginCount() <= 1;
            // loginCount == 0: loadOrCreate 刚创建但 updateLogin 尚未执行（极端竞态）
            // loginCount == 1: 首次登录的正常路径（onPlayerJoin 中 updateLogin 后 loginCount = 1）

            if (isFirstLogin) {
                GreetingContext context = GreetingContext.builder().player(player).profile(profile).firstLogin(true).offlineDurationMs(0).offlineEvents(Collections.emptyList()).onlineFriends(Collections.emptyList()).serverInfo(serverInfo).vanillaStats(finalVanillaStats).healthAlerts(Collections.emptyList()).updateReminders(Collections.emptyList()).guardianEnabled(guardianEnabled).build();
                generateAndSend(context, playerName, playerUuid);

            } else {
                long rawLastLogout = profile.getLastLogout();
                if (rawLastLogout <= 0) {
                    rawLastLogout = profile.getLastLogin();
                }
                final long lastLogout = rawLastLogout;
                long offlineDuration = System.currentTimeMillis() - lastLogout;

                int maxOwnEvents = config.getGreetingMaxOwnOfflineEvents();
                int maxFriendEvents = config.getGreetingMaxFriendOfflineEvents();
                int maxSummaryEvents = config.getGreetingMaxSummaryEvents();

                eventAggregator.loadAllOfflineDataForGreeting(playerUuid, lastLogout, profile.getLastGreetingTime(), maxOwnEvents, maxFriendEvents, maxSummaryEvents, isAdminHealth, isAdminInfo, data -> {
                    SummaryStats summaryStats = computeSummaryStats(profile, data.highlights(), data.lastSessionDurationMs());

                    List<ServerEvent> healthAlerts = isAdminHealth && data.healthAlerts() != null ? data.healthAlerts() : Collections.emptyList();
                    List<ServerEvent> updateReminders = isAdminInfo && data.updateReminders() != null ? data.updateReminders() : Collections.emptyList();

                    GreetingContext context = GreetingContext.builder().player(player).profile(profile).firstLogin(false).offlineDurationMs(offlineDuration).offlineEvents(data.ownEvents()).friendEvents(data.friendEvents()).summaryStats(summaryStats).onlineFriends(data.onlineFriends()).serverInfo(serverInfo).vanillaStats(finalVanillaStats).offlineFriends(data.offlineFriends()).globalEventCount(data.globalEventCount()).friendLoginCounts(data.friendLoginCounts()).healthAlerts(healthAlerts).updateReminders(updateReminders).guardianEnabled(guardianEnabled).build();

                    generateAndSend(context, playerName, playerUuid);
                });
            }
        });
    }

    /**
     * 调用 LLM 生成问候语并发送
     */
    private void generateAndSend(GreetingContext context, String playerName, UUID playerUuid) {
        Player player = context.getPlayer();

        ConfigManager config = plugin.getConfigManager();

        // 优先读配置文件，配置文件没有才读硬编码默认值
        String customPrompt = context.isFirstLogin() ? config.getGreetingFirstLoginPrompt() : config.getGreetingReturningPrompt();

        String systemPrompt = promptBuilder.build(context, customPrompt);

        // 画像注入
        ProfileManager pm = plugin.getProfileManager();
        if (pm != null) {
            systemPrompt = pm.injectProfileSummary(systemPrompt, playerUuid);
        }

        PluginLoggerUtil.debug("问候系统", "问候语摘要: {}", systemPrompt);

        // 全局预算熔断闸门：问候属被动输出，runaway 时降级跳过（问候自身 30min 冷却保证正常极低频）
        if (plugin.getLlmOutputCoordinator() != null) {
            var budget = plugin.getLlmOutputCoordinator().getBudgetManager();
            if (!budget.tryAcquire(playerUuid, com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager.Priority.PASSIVE)) {
                PluginLoggerUtil.debug("问候系统", "LLM 预算熔断中，跳过本次问候（玩家 {}）", playerName);
                return;
            }
        }

        Deque<ConversationManager.Message> emptyHistory = new ArrayDeque<>();
        String userMessage = context.isFirstLogin() ? I18nService.tr("请欢迎新玩家 {}", playerName) : I18nService.tr("请欢迎 {} 回来", playerName);

        PlayerResponseHandler handler = new PlayerResponseHandler(KilacraftAI.getInstance(), player, OutputScenarioEnum.GREETING, null);

        OutputConfigManager outputConfig = plugin.getConfigManager().getOutputConfigManager();
        if (outputConfig.isStreamEnabled()) {
            OutputChannelEnum channel = plugin.getResponsePipeline().getChannelForScenario(OutputScenarioEnum.GREETING);
            plugin.getResponsePipeline().startStream(player, channel, true);
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(userMessage, playerName, emptyHistory, handler, systemPrompt, false, false, false).thenAccept(greeting -> {
            // 错误响应（§c 开头）已由 handleError 提示玩家，不持久化到 DB、不写入对话历史，避免污染
            if (greeting != null && !LLMResponseUtil.isErrorResponse(greeting) && player.isOnline()) {
                ConversationPersistenceService persistence = plugin.getPersistenceService();
                if (persistence != null) {
                    persistence.submit(playerUuid, "assistant", greeting, "", ConversationSourceEnum.GREETING.getValue());
                }

                // 写入内存对话历史，使玩家能对问候内容进行追问
                Deque<ConversationManager.Message> history = plugin.getConversationManager().getOrCreateHistory(playerUuid);
                history.add(new ConversationManager.Message("assistant", greeting, ConversationSourceEnum.GREETING.getValue()));

                updateGreetingTime(playerUuid);
            }
        }).exceptionally(throwable -> {
            PluginLoggerUtil.warn("问候系统", "生成问候语失败: {}", throwable.getMessage());
            return null;
        });
    }

    private void updateGreetingTime(UUID playerUuid) {
        ProfileManager profileManager = plugin.getProfileManager();
        if (profileManager != null) {
            profileManager.updateGreetingTime(playerUuid);
        }
    }

    /**
     * 根据玩家画像和上次游玩亮点计算摘要统计数据
     *
     * @param profile               玩家画像
     * @param highlights            上次游玩亮点事件列表
     * @param lastSessionDurationMs 上次会话时长（ms）
     * @return 摘要统计数据
     */
    private SummaryStats computeSummaryStats(PlayerProfile profile, List<ServerEvent> highlights, long lastSessionDurationMs) {
        long daysSinceFirstLogin = 0;
        if (profile.getFirstLogin() > 0) {
            daysSinceFirstLogin = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - profile.getFirstLogin());
        }
        return new SummaryStats(profile.getTotalPlaytimeMs(), profile.getLoginCount(), daysSinceFirstLogin, highlights != null ? highlights : Collections.emptyList(), lastSessionDurationMs);
    }
}
