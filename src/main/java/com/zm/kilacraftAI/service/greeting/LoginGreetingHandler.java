package com.zm.kilacraftAI.service.greeting;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.*;
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
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.event.OfflineEventAggregator;
import com.zm.kilacraftAI.service.profile.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

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

        // 主线程提前缓存权限检查结果
        final boolean isAdminHealth = PluginPermissionEnum.ADMIN_HEALTH.hasPermission(player);
        final boolean isAdminInfo = PluginPermissionEnum.ADMIN_INFO.hasPermission(player);

        profileManager.getProfile(playerUuid, profile -> {
            if (profile == null) {
                PluginLoggerUtil.warn("问候系统", "玩家画像加载失败，跳过问候: {}", playerName);
                return;
            }

            boolean isFirstLogin = profile.getLoginCount() <= 1;
            // loginCount == 0: loadOrCreate 刚创建但 updateLogin 尚未执行（极端竞态）
            // loginCount == 1: 首次登录的正常路径（onPlayerJoin 中 updateLogin 后 loginCount = 1）

            if (isFirstLogin) {
                GreetingContext context = GreetingContext.builder().player(player).profile(profile).firstLogin(true).offlineDurationMs(0).offlineEvents(Collections.emptyList()).onlineFriends(Collections.emptyList()).serverInfo(serverInfo).healthAlerts(Collections.emptyList()).updateReminders(Collections.emptyList()).build();
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
                    List<ServerEvent> healthAlerts = isAdminHealth && data.healthAlerts() != null ? data.healthAlerts() : Collections.emptyList();
                    List<ServerEvent> updateReminders = isAdminInfo && data.updateReminders() != null ? data.updateReminders() : Collections.emptyList();

                    GreetingContext context = GreetingContext.builder().player(player).profile(profile).firstLogin(false).offlineDurationMs(offlineDuration).offlineEvents(data.ownEvents()).friendEvents(data.friendEvents()).highlights(data.highlights()).onlineFriends(data.onlineFriends()).serverInfo(serverInfo).offlineFriends(data.offlineFriends()).globalEventCount(data.globalEventCount()).friendLoginCounts(data.friendLoginCounts()).healthAlerts(healthAlerts).updateReminders(updateReminders).build();

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

        // system 保持纯静态（角色定义+行为规则，跨玩家共享前缀缓存）；动态数据 + 玩家身份经 user 消息注入
        String systemPrompt = promptBuilder.buildStaticSystem(context, customPrompt);
        String dynamicUserData = promptBuilder.buildDynamicUserData(context);

        // 全局预算熔断闸门：问候属被动输出，runaway 时降级跳过（问候自身 30min 冷却保证正常极低频）
        if (plugin.getLlmOutputCoordinator() != null) {
            var budget = plugin.getLlmOutputCoordinator().getBudgetManager();
            if (!budget.tryAcquire(playerUuid, com.zm.kilacraftAI.skills.framework.task.LLMBudgetManager.Priority.PASSIVE)) {
                PluginLoggerUtil.debug("问候系统", "LLM 预算熔断中，跳过本次问候（玩家 {}）", playerName);
                return;
            }
        }

        Deque<ConversationManager.Message> emptyHistory = new ArrayDeque<>();
        // user 消息：动态数据块前置（供 LLM 基于离线事件等生成个性化问候），欢迎指令在后
        StringBuilder userMsgBuilder = new StringBuilder();
        if (!dynamicUserData.isEmpty()) {
            userMsgBuilder.append(dynamicUserData).append("\n\n");
        }
        userMsgBuilder.append(context.isFirstLogin() ? I18nService.tr("请欢迎新玩家 {}", playerName) : I18nService.tr("请欢迎 {} 回来", playerName));
        String userMessage = userMsgBuilder.toString();

        PlayerResponseHandler handler = new PlayerResponseHandler(KilacraftAI.getInstance(), player, OutputScenarioEnum.GREETING, null);

        OutputConfigManager outputConfig = plugin.getConfigManager().getOutputConfigManager();
        if (outputConfig.isStreamEnabled()) {
            OutputChannelEnum channel = plugin.getResponsePipeline().getChannelForScenario(OutputScenarioEnum.GREETING);
            plugin.getResponsePipeline().startStream(player, channel, true);
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(userMessage, player, emptyHistory, handler, systemPrompt, false, true, false, CacheCallTypeEnum.GREETING).thenAccept(greeting -> {
            // 错误响应（§c 开头）已由 handleError 提示玩家，不持久化到 DB、不写入对话历史，避免污染
            if (greeting != null && !LLMResponseUtil.isErrorResponse(greeting) && player.isOnline()) {
                // 问候仅写 DB（source=greeting，DB 加载历史时不回读），不注入内存对话历史：
                // 问候是孤立 assistant（无配对 user），写入会破坏历史成对结构、并在裁剪时拆散成对消息。
                ConversationPersistenceService persistence = plugin.getPersistenceService();
                if (persistence != null) {
                    persistence.submit(playerUuid, "assistant", greeting, "", ConversationSourceEnum.GREETING.getValue());
                }
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

}
