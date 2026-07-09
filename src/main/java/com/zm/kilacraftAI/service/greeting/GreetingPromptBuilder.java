package com.zm.kilacraftAI.service.greeting;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.greeting.FriendStatus;
import com.zm.kilacraftAI.model.greeting.GreetingContext;
import com.zm.kilacraftAI.model.greeting.PlayerVanillaStats;
import com.zm.kilacraftAI.model.greeting.SummaryStats;
import com.zm.kilacraftAI.model.profile.PlayerProfile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 问候提示词构建器
 *
 * <p>根据 {@link GreetingContext} 构建发送给 LLM 的系统提示词。</p>
 * <p>支持两种场景：首次登录和回归登录，各自有独立的默认提示词模板。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-01
 */
public class GreetingPromptBuilder {

    private static final DateTimeFormatter ALERT_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static final String DEFAULT_FIRST_LOGIN_PROMPT = """
            你是这个 Minecraft 服务器的 AI 助手，{player} 第一次来到服务器。
            以平实自然的语气对 {player} 表示欢迎。
            规则：
            1. 简短介绍自己能做什么：回答问题、查物品、操作市场、挂机任务等，玩家用 /kila 就能找你
            2. 如果有服务器信息，顺便提一下；没有就不提
            3. 控制在 120 汉字以内
            
            {server_info}""";

    public static final String DEFAULT_RETURNING_PROMPT = """
            你是这个 Minecraft 服务器的 AI 助手。{player} 登录了，距上次离线 {offline_duration}。
            
            {own_events_section}
            {friend_events_section}
            {online_friends_section}
            {last_location}
            {summary_section}
            
            以平实的语气欢迎 {player} 回来。
            
            规则：
            1. 空内容分类（"没有""暂无"等）完全跳过，不提。不用"一切如故""没什么事""挺安静的"等概括句填充。
            2. 无好友时禁止提任何与好友在线状态相关的表述。
            3. 玩家数据仅在满足以下任一硬性条件时，才可用具体数字提及：(a)达到有意义的里程碑，(b)与本次离线事件有直接因果关联。不满足则完全不提任何玩家数据。提到时必须说出具体数字，禁止用"很长""相当多""打了不少""飞了好远"等模糊描述。
            4. 上次位置仅作背景参考。除非在上次位置发生了离线事件，否则不提及。提及时不报坐标，不说世界名。
            5. 不以疑问句结尾。
            6. 控制在200汉字以内。若无可提内容，说"欢迎回来，有什么需要随时找我。"即可。
            """;

    public static final String DEFAULT_FIRST_LOGIN_PROMPT_EN = """
            You are the AI assistant of this Minecraft server. {player} has just joined for the first time.
            Welcome {player} in a calm, natural tone.
            Rules:
            1. Briefly introduce what you can do: answer questions, look up items, operate the market, AFK tasks, etc. Players can find you with /kila
            2. If there is server info, mention it briefly; if not, skip it
            3. Keep it under 120 words
            
            {server_info}""";

    public static final String DEFAULT_RETURNING_PROMPT_EN = """
            You are the AI assistant of this Minecraft server. {player} just logged in, after being offline for {offline_duration}.
            
            {own_events_section}
            {friend_events_section}
            {online_friends_section}
            {last_location}
            {summary_section}
            
            Welcome {player} back in a calm, natural tone.
            
            Rules:
            1. Skip empty categories ("none", "nothing", etc.) entirely. Do not pad with phrases like "nothing special", "all quiet", or "everything's normal".
            2. If the player has no friends, do not mention anything about friend online status.
            3. Only mention player stats if they meet one of these HARD criteria: (a) reaches a meaningful milestone, (b) has a direct causal link to an offline event this session. If nothing qualifies, do not mention any stats at all. When mentioning, you MUST state exact numbers — do NOT use vague phrases like "quite a lot", "a long way", or "quite a few".
            4. Last location is for background context only. Do not mention it unless an offline event occurred there. When mentioned, do not reveal coordinates or world name.
            5. Do not end with a question.
            6. Keep it under 120 words. If there is nothing worth mentioning, just say "Welcome back, let me know if you need anything."
            """;

    public static String getDefaultFirstLoginPrompt() {
        return isEnglish() ? DEFAULT_FIRST_LOGIN_PROMPT_EN : DEFAULT_FIRST_LOGIN_PROMPT;
    }

    public static String getDefaultReturningPrompt() {
        return isEnglish() ? DEFAULT_RETURNING_PROMPT_EN : DEFAULT_RETURNING_PROMPT;
    }

    private static boolean isEnglish() {
        try {
            return !"zh".equals(KilacraftAI.getInstance().getConfigManager().getLanguage());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建问候提示词
     *
     * @param context      问候上下文
     * @param customPrompt 提示词模板（ConfigManager 保证不为空）
     * @return 完整的系统提示词
     */
    public String build(GreetingContext context, String customPrompt) {
        String playerName = context.getPlayer().getName();

        if (context.isFirstLogin()) {
            return buildFirstLoginPrompt(context, customPrompt, playerName);
        } else {
            return buildReturningPrompt(context, customPrompt, playerName);
        }
    }

    /**
     * 构建首次登录提示词
     */
    private String buildFirstLoginPrompt(GreetingContext context, String customPrompt, String playerName) {
        String serverInfo = context.getServerInfo();
        if (serverInfo == null || serverInfo.isBlank()) {
            serverInfo = "";
        }

        return customPrompt.replace("{player}", playerName).replace("{server_info}", serverInfo);
    }

    /**
     * 构建回归登录提示词
     */
    private String buildReturningPrompt(GreetingContext context, String customPrompt, String playerName) {
        String offlineDuration = formatDuration(context.getOfflineDurationMs());
        String ownEventsSection = buildOfflineEventsSection(context.getOfflineEvents(), context.getGlobalEventCount(), playerName);
        String friendEventsSection = buildFriendEventsSection(context.getFriendEvents(), context.getFriendLoginCounts());
        String onlineFriendsSection = buildOnlineFriendsSection(context.getOnlineFriends(), context.getOfflineFriends());
        String lastLocationSection = buildLastLocationSection(context.getProfile(), playerName);
        String summarySection = buildSummarySection(context.getSummaryStats(), context.getVanillaStats());
        String healthAlertsSection = buildHealthAlertsSection(context.getHealthAlerts(), playerName);
        String updateReminderSection = buildUpdateReminderSection(context.getUpdateReminders(), playerName);

        String prompt = customPrompt.replace("{player}", playerName).replace("{offline_duration}", offlineDuration).replace("{own_events_section}", ownEventsSection).replace("{friend_events_section}", friendEventsSection).replace("{online_friends_section}", onlineFriendsSection).replace("{last_location}", lastLocationSection).replace("{summary_section}", summarySection);

        // 更新提醒段落前置拼接（优先级次于告警）
        if (updateReminderSection != null && !updateReminderSection.isEmpty()) {
            prompt = updateReminderSection + "\n\n" + prompt;
        }
        // 告警段落插入到系统提示词最前面（最高优先级）
        if (healthAlertsSection != null && !healthAlertsSection.isEmpty()) {
            return healthAlertsSection + "\n\n" + prompt + "\n";
        }
        return prompt;
    }

    /**
     * 构建离线事件文本段落
     *
     * @param events           离线事件列表
     * @param globalEventCount 离线期间全服事件总数
     */
    public String buildOfflineEventsSection(List<ServerEvent> events, int globalEventCount, String playerName) {
        if (events == null || events.isEmpty()) {
            StringBuilder emptySb = new StringBuilder(I18nService.tr("【离线期间发生的事】\n没有特别的事情发生。"));
            if (globalEventCount > 0) {
                emptySb.append("\n").append(I18nService.tr("{}不在的时候，全服共发生了 {} 件事", playerName, globalEventCount));
            }
            return emptySb.toString();
        }

        Map<ServerEventTypeEnum, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(I18nService.tr("【离线期间发生的事】"));

        for (Map.Entry<ServerEventTypeEnum, List<ServerEvent>> entry : grouped.entrySet()) {
            String summary = summarizeEvent(I18nService.tr("你"), entry.getKey(), entry.getValue());
            joiner.add(summary);
        }

        // 追加全服事件总数
        if (globalEventCount > 0) {
            joiner.add(I18nService.tr("{}不在的时候，全服共发生了 {} 件事", playerName, globalEventCount));
        }

        return joiner.toString();
    }

    /**
     * 按事件类型生成摘要（统一方法，支持任意玩家名）
     *
     * @param playerName 玩家名（自身传 "你"，好友传好友名）
     * @param type       事件类型
     * @param events     事件列表
     */
    private String summarizeEvent(String playerName, ServerEventTypeEnum type, List<ServerEvent> events) {
        String data = events.get(0).getData() != null ? events.get(0).getData() : "";
        return switch (type) {
            case MARKET_ITEM_SOLD -> {
                int count = events.size();
                yield count == 1 ? I18nService.tr("{}上架的商品已卖出", playerName) + appendDataSuffix(events.get(0)) : I18nService.tr("{}上架的 {} 件商品已卖出", playerName, count);
            }
            case MARKET_MONEY_RECEIVED -> {
                int count = events.size();
                String amountStr = formatMoneyAmount(events.get(0).getData());
                yield count == 1 ? I18nService.tr("{}收到了 {} 收入", playerName, amountStr) : I18nService.tr("{}收到了 {} 笔款项（共 {}）", playerName, count, sumMoneyAmounts(events));
            }
            case PLAYER_DEATH -> {
                int count = events.size();
                if (count == 1) {
                    yield data.isEmpty() ? I18nService.tr("{}挂了 1 次", playerName) : I18nService.tr("{}挂了 1 次（{}）", playerName, data);
                } else {
                    Map<String, Long> deathMsgCount = events.stream().collect(Collectors.groupingBy(e -> e.getData() != null ? e.getData() : "未知", Collectors.counting()));
                    StringJoiner dj = new StringJoiner("、");
                    deathMsgCount.forEach((msg, cnt) -> dj.add(msg + (cnt > 1 ? " x" + cnt : "")));
                    yield I18nService.tr("{}挂了 {} 次（{}）", playerName, count, dj);
                }
            }
            case PLAYER_ADVANCEMENT -> {
                int count = events.size();
                if (count == 1) {
                    yield I18nService.tr("{}达成了成就 {}", playerName, data);
                } else {
                    StringJoiner aj = new StringJoiner(", ");
                    for (ServerEvent e : events) {
                        aj.add(e.getData() != null ? e.getData() : "未知");
                    }
                    yield I18nService.tr("{}达成了 {} 个成就（{}）", playerName, count, aj);
                }
            }
            case PLAYER_LEVEL_UP -> {
                int count = events.size();
                if (count == 1) {
                    String levelDesc = formatLevelChange(data);
                    yield levelDesc.isEmpty() ? I18nService.tr("{}升级了", playerName) : I18nService.tr("{}升级了（{}）", playerName, levelDesc);
                } else {
                    String oldestData = events.get(events.size() - 1).getData();
                    String startLevel = extractStartLevel(oldestData);
                    String endLevel = extractEndLevel(data);
                    if (!startLevel.isEmpty() && !endLevel.isEmpty()) {
                        yield I18nService.tr("{}从 {} 级升到了 {} 级（共{}级）", playerName, startLevel, endLevel, count);
                    } else {
                        yield I18nService.tr("{}升了 {} 级", playerName, count);
                    }
                }
            }
            case PLAYER_USE_TOTEM -> {
                String suffix = data.isEmpty() ? "" : "（" + formatDamageCause(data) + "）";
                yield events.size() > 1 ? I18nService.tr("{}触发了 {} 次不死图腾", playerName, events.size()) + suffix : I18nService.tr("{}触发了不死图腾", playerName) + suffix;
            }
            case PLAYER_DEFEAT_BOSS ->
                    events.size() > 1 ? I18nService.tr("{}击杀了 {} 共 {} 次", playerName, formatEntityName(data), events.size()) : I18nService.tr("{}击杀了 {}", playerName, formatEntityName(data));
            case PLAYER_COMPLETE_RAID -> I18nService.tr("{}完成了袭击（{}）", playerName, data);
            case PLAYER_PET_DEATH -> I18nService.tr("{}的宠物战死了（{}）", playerName, formatPetDeathData(data));
            case PLAYER_PVP_KILL ->
                    events.size() > 1 ? I18nService.tr("{}在PVP中击杀了 {} 共 {} 次", playerName, data, events.size()) : I18nService.tr("{}在PVP中击杀了 {}", playerName, data);
            case PLAYER_PVP_DEATH ->
                    events.size() > 1 ? I18nService.tr("{}在PVP中被 {} 击杀了 {} 次", playerName, data, events.size()) : I18nService.tr("{}在PVP中被 {} 击杀了", playerName, data);
            case PLAYER_TOOL_BREAK -> I18nService.tr("{}的{}断了", playerName, formatMaterialName(data));
            case PLAYER_CATCH_TREASURE -> I18nService.tr("{}钓到了 {}", playerName, formatMaterialName(data));
            case PLAYER_LIGHTNING_STRIKE ->
                    events.size() > 1 ? I18nService.tr("{}被雷劈了 {} 次", playerName, events.size()) : I18nService.tr("{}被雷劈了", playerName);
            case PLAYER_CURE_VILLAGER ->
                    events.size() > 1 ? I18nService.tr("{}救了 {} 个僵尸村民", playerName, events.size()) : I18nService.tr("{}救了一个僵尸村民", playerName);
            case PLAYER_MINE_ANCIENT_DEBRIS ->
                    events.size() > 1 ? I18nService.tr("{}挖到了 {} 块远古残骸", playerName, events.size()) : I18nService.tr("{}挖到了远古残骸", playerName);
            case PLAYER_TAME_ANIMAL -> I18nService.tr("{}驯服了{}", playerName, formatPetEntityName(data));
            case PLAYER_CRAFT_ENCH_GOLDEN_APPLE ->
                    events.size() > 1 ? I18nService.tr("{}合成了 {} 个附魔金苹果", playerName, events.size()) : I18nService.tr("{}合成了附魔金苹果", playerName);
            case PLAYER_BUILD_WITHER ->
                    events.size() > 1 ? I18nService.tr("{}召唤了 {} 次凋零", playerName, events.size()) : I18nService.tr("{}召唤了凋零", playerName);
            default ->
                    I18nService.tr("{}触发了 {} 次 {}", playerName, events.size(), I18nService.tr(type.getDescription()));
        };
    }

    /**
     * 解析等级变化描述（如 "13 → 14" → "13级→14级"）
     */
    private String formatLevelChange(String data) {
        if (data == null || data.isEmpty()) return "";
        String[] parts = data.split("\\s*→\\s*");
        if (parts.length == 2) {
            return I18nService.tr("{}级→{}级", parts[0].trim(), parts[1].trim());
        }
        return data;
    }

    private String extractStartLevel(String data) {
        if (data == null || data.isEmpty()) return "";
        String[] parts = data.split("\\s*→\\s*");
        return parts.length >= 1 ? parts[0].trim() : "";
    }

    private String extractEndLevel(String data) {
        if (data == null || data.isEmpty()) return "";
        String[] parts = data.split("\\s*→\\s*");
        return parts.length >= 2 ? parts[1].trim() : "";
    }

    /**
     * 格式化金额（data 存储的是 price 字符串，如 "50.0" 或 "-1.0"）
     */
    private String formatMoneyAmount(String data) {
        if (data == null || data.isEmpty()) return I18nService.tr("一笔款项");
        try {
            double amount = Double.parseDouble(data);
            if (amount < 0) {
                // 负数表示扣费/税收，取绝对值展示
                return String.format("%.1f", Math.abs(amount));
            }
            return String.format("%.1f", amount);
        } catch (NumberFormatException e) {
            return data;
        }
    }

    /**
     * 汇总多笔款项的总金额
     */
    private String sumMoneyAmounts(List<ServerEvent> events) {
        double total = 0;
        for (ServerEvent e : events) {
            try {
                total += Double.parseDouble(e.getData() != null ? e.getData() : "0");
            } catch (NumberFormatException ignored) {
            }
        }
        return String.format("%.1f", Math.abs(total));
    }

    /**
     * 追加事件数据后缀
     */
    private String appendDataSuffix(ServerEvent event) {
        if (event.getData() != null && !event.getData().isEmpty()) {
            return "（" + event.getData() + "）";
        }
        return "";
    }

    /**
     * 构建健康告警段落（仅对有 kilacraft.admin.health 权限的管理员）
     *
     * <p>从 HEALTH_ALERT 事件的 data JSON 中解析结构化告警信息，
     * 格式化为纯文本注入到问候提示词的最前面。</p>
     *
     * @param healthAlerts 离线期间的健康告警事件列表
     * @param playerName   玩家名称
     * @return 告警段落文本，无告警时返回空字符串
     */
    public String buildHealthAlertsSection(List<ServerEvent> healthAlerts, String playerName) {
        if (healthAlerts == null || healthAlerts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("【最高优先级 - 服务器异常告警】\n"));
        sb.append(I18nService.tr("管理员 {} 离线期间，服务器检测到 {} 次性能异常：\n\n", playerName, healthAlerts.size()));

        for (int i = 0; i < healthAlerts.size(); i++) {
            ServerEvent alert = healthAlerts.get(i);
            String time = ALERT_DTF.format(Instant.ofEpochMilli(alert.getCreatedAt()));
            sb.append(I18nService.tr("【告警 {}】", i + 1)).append(time).append("\n");
            sb.append(formatHealthAlert(alert.getData()));
            sb.append("\n\n");
        }

        sb.append(I18nService.tr("完整诊断报告保存在 plugins/Kilacraft-AI/reports/ 目录下。\n"));
        sb.append(I18nService.tr("你必须在问候的第一时间主动告知管理员以上异常，用口语化的方式简述时间、严重程度和涉及插件，并指出可以查看完整报告。然后再进行常规问候。"));

        return sb.toString();
    }

    /**
     * 构建新版本提醒段落（仅对有 kilacraft.admin.info 权限的管理员）
     *
     * <p>从 UPDATE_AVAILABLE 事件的 data JSON 中解析版本信息，格式化为纯文本
     * 注入到问候提示词的前部（优先级次于服务器异常告警）。</p>
     *
     * @param updateReminders 离线期间检测到的新版本提醒事件列表
     * @param playerName      玩家名称
     * @return 提醒段落文本，无提醒时返回空字符串
     */
    public String buildUpdateReminderSection(List<ServerEvent> updateReminders, String playerName) {
        if (updateReminders == null || updateReminders.isEmpty()) {
            return "";
        }

        String currentVersion = KilacraftAI.getInstance().getDescription().getVersion();

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("【Kilacraft-AI 插件新版本可用】\n"));
        sb.append(I18nService.tr("当前运行版本: v{}\n", currentVersion));
        for (int i = 0; i < updateReminders.size(); i++) {
            sb.append(formatUpdateReminder(updateReminders.get(i).getData()));
            if (i < updateReminders.size() - 1) {
                sb.append("\n\n");
            }
        }
        sb.append("\n").append(I18nService.tr("以上是本 AI 插件（Kilacraft-AI）的新版本更新信息，不是服务器或其他插件的内容。"));
        sb.append(I18nService.tr("你必须在问候中完整转达以上全部信息：当前版本号、新版本号、发布日期、更新标题与内容、下载地址，一个都不能漏。"));
        sb.append(I18nService.tr("下载地址必须原样给出，方便管理员直接点击跳转。"));
        sb.append(I18nService.tr("先用一段话说明有新版本可用并列出全部信息，而后再进行常规问候。"));
        return sb.toString();
    }

    /**
     * 格式化单条 UPDATE_AVAILABLE 的 data JSON 为 AI 可读的完整文本
     *
     * <p>全字段输出：新版本号、发布日期、更新标题（含内容说明）、下载地址，不遗漏。</p>
     */
    private String formatUpdateReminder(String dataJson) {
        if (dataJson == null || dataJson.isEmpty()) return I18nService.tr("版本信息不可用");

        try {
            var root = com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            String tag = root.has("tag") && !root.get("tag").isJsonNull() ? root.get("tag").getAsString() : "";
            String name = root.has("name") && !root.get("name").isJsonNull() ? root.get("name").getAsString() : "";
            String url = root.has("url") && !root.get("url").isJsonNull() ? root.get("url").getAsString() : "";
            String date = root.has("date") && !root.get("date").isJsonNull() ? root.get("date").getAsString() : "";

            StringBuilder sb = new StringBuilder();
            sb.append(I18nService.tr("新版本号: {}", tag));
            if (!date.isEmpty()) {
                sb.append("\n").append(I18nService.tr("发布日期: {}", date));
            }
            if (!name.isEmpty() && !name.equals(tag)) {
                sb.append("\n").append(I18nService.tr("更新标题: {}", name));
            }
            if (!url.isEmpty()) {
                sb.append("\n").append(I18nService.tr("下载地址: {}", url));
            }
            return sb.toString();
        } catch (Exception e) {
            return I18nService.tr("版本信息解析失败");
        }
    }

    /**
     * 格式化单条 HEALTH_ALERT 的 data JSON 为 AI 可读的简洁文本
     *
     * <p>分层输出：告警指标（是什么）、涉及插件（哪个插件）、热点方法+触发路径（插件内的具体原因）。</p>
     */
    private String formatHealthAlert(String dataJson) {
        if (dataJson == null || dataJson.isEmpty()) return I18nService.tr("告警详情不可用");

        try {
            var root = com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            List<String> parts = new ArrayList<>();

            // 告警指标
            if (root.has("alerts")) {
                var alertsArr = root.getAsJsonArray("alerts");
                for (var elem : alertsArr) {
                    var alertObj = elem.getAsJsonObject();
                    String metric = alertObj.has("metric") ? alertObj.get("metric").getAsString() : "";
                    double value = alertObj.has("value") ? alertObj.get("value").getAsDouble() : 0;
                    double threshold = alertObj.has("threshold") ? alertObj.get("threshold").getAsDouble() : 0;
                    String desc = formatMetricDescription(metric, value, threshold);
                    parts.add(desc);
                }
            }

            if (parts.isEmpty()) {
                return I18nService.tr("告警详情不可用");
            }

            StringBuilder sb = new StringBuilder();
            sb.append(I18nService.tr("异常指标: ")).append(String.join(I18nService.tr("、"), parts));

            // 涉及插件（快速定位哪个插件有问题）
            List<String> pluginParts = new ArrayList<>();
            if (root.has("plugin_hotspots") && root.get("plugin_hotspots").isJsonObject()) {
                var pluginObj = root.getAsJsonObject("plugin_hotspots");
                for (var entry : pluginObj.entrySet()) {
                    pluginParts.add(entry.getKey() + "(" + String.format("%.1f", entry.getValue().getAsDouble()) + "%)");
                }
            }
            if (!pluginParts.isEmpty()) {
                sb.append("\n").append(I18nService.tr("涉及插件: ")).append(String.join(", ", pluginParts));
            }

            // 热点方法 + 触发路径（插件内的具体原因）
            if (root.has("top_hotspots") && root.get("top_hotspots").isJsonArray()) {
                var hotspots = root.getAsJsonArray("top_hotspots");
                int limit = Math.min(hotspots.size(), 3);
                sb.append("\n").append(I18nService.tr("热点方法: "));
                for (int i = 0; i < limit; i++) {
                    var h = hotspots.get(i).getAsJsonObject();
                    String method = h.has("method") ? h.get("method").getAsString() : "";
                    String pct = h.has("percentage") ? String.format("%.1f", h.get("percentage").getAsDouble()) : "";
                    String triggerPath = h.has("trigger_path") ? h.get("trigger_path").getAsString() : "";
                    if (!method.isEmpty()) {
                        String shortMethod = simplifyMethodName(method);
                        sb.append("\n  ").append(shortMethod).append(" (").append(pct).append("%)");
                        if (!triggerPath.isEmpty()) {
                            sb.append("  ← ").append(simplifyTriggerPath(triggerPath));
                        }
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return I18nService.tr("告警详情解析失败");
        }
    }

    /**
     * 简化方法名，移除内部类编号和调用栈噪音
     */
    private String simplifyMethodName(String method) {
        if (method == null || method.isEmpty()) return method;
        int hashIdx = method.lastIndexOf('#');
        String simple = hashIdx >= 0 ? method.substring(hashIdx + 1) : method;
        simple = simple.replaceAll("\\$\\d+", "");
        return simple;
    }

    /**
     * 简化触发路径，去除包名/Lambda内部编号等噪音，使其可读
     *
     * <p>"DedicatedServer.tickServer → ... → DemoSkill$1.run"
     * → "tickServer → ... → DemoSkill.run"</p>
     */
    private String simplifyTriggerPath(String path) {
        if (path == null || path.isEmpty()) return path;
        String[] frames = path.split("\\s*→\\s*");
        List<String> simplified = new ArrayList<>();
        for (String frame : frames) {
            String s = frame.trim();
            if (s.isEmpty()) continue;
            // 去掉 FQN 类名前缀（如 "net.minecraft.server.MinecraftServer."）
            int lastDot = s.lastIndexOf('.');
            if (lastDot >= 0) {
                s = s.substring(lastDot + 1);
            }
            // 去掉 Lambda 内部编号（如 "$$Lambda$1234/0x..."）
            s = s.replaceAll("\\$\\$Lambda\\$[^/]+/0x[0-9a-fA-F]+", "");
            // 去掉匿名内部类编号
            s = s.replaceAll("\\$\\d+", "");
            // 去掉 # 前缀
            int hashIdx = s.indexOf('#');
            if (hashIdx >= 0) {
                s = s.substring(hashIdx + 1);
            }
            if (!s.isEmpty()) {
                simplified.add(s);
            }
        }
        // 帧太多时只保留前2和后2，中间用 ... 表示
        if (simplified.size() > 5) {
            return String.join(" → ", simplified.subList(0, 2)) + " → ... → " + String.join(" → ", simplified.subList(simplified.size() - 2, simplified.size()));
        }
        return String.join(" → ", simplified);
    }

    /**
     * 格式化告警指标描述
     */
    private String formatMetricDescription(String metric, double value, double threshold) {
        String formattedValue = String.format("%.1f", value);
        String formattedThreshold = String.format("%.1f", threshold);
        return switch (metric) {
            case "tps_1m" -> I18nService.tr("TPS过低({})，阈值<{}", formattedValue, formattedThreshold);
            case "mspt_max" -> I18nService.tr("MSPT峰值({}ms)，阈值>{}ms", formattedValue, formattedThreshold);
            case "mspt_p95" -> I18nService.tr("MSPT P95({}ms)，阈值>{}ms", formattedValue, formattedThreshold);
            case "cpu_process" -> I18nService.tr("CPU过高({}%)，阈值>{}%", formattedValue, formattedThreshold);
            default -> metric + "(" + formattedValue + ")";
        };
    }

    /**
     * 构建上次位置文本段落
     */
    public String buildLastLocationSection(PlayerProfile profile, String playerName) {
        if (profile == null) return "";

        String world = profile.getLastWorld();
        if (world == null || world.isEmpty()) return "";

        String friendlyWorld = formatWorldName(world);

        return I18nService.tr("【上次位置】\n{} 上次在世界 {}，坐标 ({}, {}, {})", playerName, friendlyWorld, (int) profile.getLastX(), (int) profile.getLastY(), (int) profile.getLastZ());
    }

    /**
     * 世界名友好化
     */
    private String formatWorldName(String world) {
        return switch (world) {
            case "world" -> I18nService.tr("主世界");
            case "world_nether" -> I18nService.tr("下界");
            case "world_the_end" -> I18nService.tr("末地");
            default -> world;
        };
    }

    /**
     * 构建在线好友文本段落
     *
     * @param onlineFriends  在线好友状态列表
     * @param offlineFriends 离线好友状态列表
     */
    public String buildOnlineFriendsSection(List<FriendStatus> onlineFriends, List<FriendStatus> offlineFriends) {
        boolean hasOnline = onlineFriends != null && !onlineFriends.isEmpty();
        boolean hasOffline = offlineFriends != null && !offlineFriends.isEmpty();

        if (!hasOnline && !hasOffline) {
            return I18nService.tr("【好友在线状态】\n暂无好友。");
        }

        int onlineCount = org.bukkit.Bukkit.getOnlinePlayers().size();
        StringBuilder sb = new StringBuilder(I18nService.tr("【好友在线状态】\n"));

        // 在线好友：名称 + 世界信息
        if (hasOnline) {
            StringJoiner joiner = new StringJoiner("、");
            for (FriendStatus f : onlineFriends) {
                StringBuilder desc = new StringBuilder(f.name());
                if (f.world() != null && !f.world().isEmpty()) {
                    desc.append("（").append(formatWorldName(f.world())).append(")");
                }
                joiner.add(desc);
            }
            sb.append(joiner);
        } else {
            sb.append(I18nService.tr("没有好友在线"));
        }

        // 在线人数
        sb.append("\n").append(I18nService.tr("（服务器当前在线 {} 人）", onlineCount));

        // 在线好友会话时长
        if (hasOnline) {
            boolean hasSession = false;
            StringBuilder sessionSb = new StringBuilder();
            for (FriendStatus f : onlineFriends) {
                if (f.sessionMinutes() > 0) {
                    sessionSb.append(I18nService.tr("{} 已在线 {}", f.name(), formatSessionDuration(f.sessionMinutes()))).append("; ");
                    hasSession = true;
                }
            }
            if (hasSession) {
                sb.append("\n").append(sessionSb);
            }
        }

        // 离线好友最后在线时间
        if (hasOffline) {
            sb.append("\n").append(I18nService.tr("离线好友："));
            StringJoiner offlineJoiner = new StringJoiner("、");
            for (FriendStatus f : offlineFriends) {
                if (f.sessionMinutes() > 0) {
                    offlineJoiner.add(f.name() + "（" + formatSessionDuration(f.sessionMinutes()) + I18nService.tr("前下线）"));
                }
            }
            sb.append(offlineJoiner);
        }

        return sb.toString();
    }

    /**
     * 构建好友动态文本段落
     *
     * @param friendEvents      好友事件列表
     * @param friendLoginCounts 离线期间好友登录次数
     */
    public String buildFriendEventsSection(List<ServerEvent> friendEvents, Map<String, Integer> friendLoginCounts) {
        boolean hasEvents = friendEvents != null && !friendEvents.isEmpty();
        boolean hasLogins = friendLoginCounts != null && !friendLoginCounts.isEmpty();

        if (!hasEvents && !hasLogins) {
            return I18nService.tr("【好友动态】\n好友们最近没什么特别的事。");
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(I18nService.tr("【好友动态】"));

        // 好友事件摘要
        if (hasEvents) {
            // 按玩家名分组
            Map<String, List<ServerEvent>> byPlayer = new LinkedHashMap<>();
            for (ServerEvent e : friendEvents) {
                String name = e.getPlayerName() != null ? e.getPlayerName() : I18nService.tr("某位好友");
                byPlayer.computeIfAbsent(name, k -> new ArrayList<>()).add(e);
            }

            for (Map.Entry<String, List<ServerEvent>> entry : byPlayer.entrySet()) {
                String playerName = entry.getKey();
                List<ServerEvent> events = entry.getValue();

                // 按事件类型分组后生成摘要
                Map<ServerEventTypeEnum, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

                for (Map.Entry<ServerEventTypeEnum, List<ServerEvent>> ge : grouped.entrySet()) {
                    String summary = summarizeEvent(playerName, ge.getKey(), ge.getValue());
                    joiner.add(summary);
                }
            }
        }

        // 好友登录频次
        if (hasLogins) {
            StringJoiner loginJoiner = new StringJoiner("、");
            friendLoginCounts.forEach((name, count) -> loginJoiner.add(I18nService.tr("{} {} 次", name, count)));
            joiner.add(I18nService.tr("好友登录频次：{}", loginJoiner));
        }

        return joiner.toString();
    }

    /**
     * 实体类型名称友好化
     */
    private String formatEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) return I18nService.tr("未知生物");
        return switch (entityType) {
            case "ENDER_DRAGON" -> I18nService.tr("末影龙");
            case "WITHER" -> I18nService.tr("凋零");
            case "ELDER_GUARDIAN" -> I18nService.tr("远古守卫者");
            case "WARDEN" -> I18nService.tr("监守者");
            default -> entityType;
        };
    }

    /**
     * 宠物死亡事件 data 友好化
     *
     * <p>data 格式为 "WOLF (ENTITY_ATTACK)"，拆分为实体类型 + 死因两部分分别翻译。</p>
     */
    private String formatPetDeathData(String data) {
        if (data == null || data.isEmpty()) return I18nService.tr("未知宠物");
        // data 格式："WOLF (ENTITY_ATTACK) [ZOMBIE]" 或 "WOLF (ENTITY_ATTACK)"
        String killerInfo = "";
        String entityType = data;
        String cause = "";

        // 提取杀手信息
        int bracketStart = data.indexOf('[');
        if (bracketStart > 0) {
            int bracketEnd = data.indexOf(']', bracketStart);
            if (bracketEnd > bracketStart) {
                killerInfo = data.substring(bracketStart + 1, bracketEnd);
                entityType = data.substring(0, bracketStart).trim();
            }
        }

        // 拆分 "WOLF (ENTITY_ATTACK)" → entityType="WOLF", cause="ENTITY_ATTACK"
        int parenStart = entityType.indexOf(' ');
        if (parenStart > 0 && entityType.length() > parenStart + 2) {
            cause = entityType.substring(parenStart + 2, entityType.length() - 1);
            entityType = entityType.substring(0, parenStart);
        }

        StringBuilder result = new StringBuilder(formatPetEntityName(entityType));
        if (!cause.isEmpty()) {
            result.append(I18nService.tr("，死因: ")).append(formatDamageCause(cause));
        }
        if (!killerInfo.isEmpty()) {
            result.append(I18nService.tr("，被 ")).append(formatEntityName(killerInfo)).append(I18nService.tr(" 击杀"));
        }
        return result.toString();
    }

    /**
     * 宠物实体类型友好化
     */
    private String formatPetEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) return I18nService.tr("未知宠物");
        return switch (entityType) {
            case "WOLF" -> I18nService.tr("狼");
            case "CAT" -> I18nService.tr("猫");
            case "HORSE" -> I18nService.tr("马");
            case "DONKEY" -> I18nService.tr("驴");
            case "MULE" -> I18nService.tr("骡");
            case "PARROT" -> I18nService.tr("鹦鹉");
            case "FOX" -> I18nService.tr("狐狸");
            default -> entityType;
        };
    }

    /**
     * 伤害原因友好化
     */
    private String formatDamageCause(String cause) {
        if (cause == null || cause.isEmpty()) return I18nService.tr("未知");
        return switch (cause) {
            case "ENTITY_ATTACK" -> I18nService.tr("被攻击");
            case "ENTITY_EXPLOSION" -> I18nService.tr("爆炸");
            case "BLOCK_EXPLOSION" -> I18nService.tr("方块爆炸");
            case "FALL" -> I18nService.tr("摔落");
            case "FIRE" -> I18nService.tr("火焰");
            case "FIRE_TICK" -> I18nService.tr("燃烧");
            case "LAVA" -> I18nService.tr("岩浆");
            case "DROWNING" -> I18nService.tr("溺水");
            case "SUFFOCATION" -> I18nService.tr("窒息");
            case "LIGHTNING" -> I18nService.tr("雷击");
            case "POISON" -> I18nService.tr("中毒");
            case "WITHER" -> I18nService.tr("凋零效果");
            case "MAGIC" -> I18nService.tr("魔法");
            case "VOID" -> I18nService.tr("虚空");
            case "CRAMMING" -> I18nService.tr("挤压");
            case "DRYOUT" -> I18nService.tr("干涸");
            case "STARVATION" -> I18nService.tr("饥饿");
            case "PROJECTILE" -> I18nService.tr("投射物");
            case "THORNS" -> I18nService.tr("荆棘");
            default -> cause;
        };
    }

    /**
     * 物品类型名称友好化
     */
    private String formatMaterialName(String material) {
        if (material == null || material.isEmpty()) return I18nService.tr("未知物品");
        return switch (material) {
            case "DIAMOND_PICKAXE" -> I18nService.tr("钻石镐");
            case "DIAMOND_SWORD" -> I18nService.tr("钻石剑");
            case "DIAMOND_AXE" -> I18nService.tr("钻石斧");
            case "DIAMOND_SHOVEL" -> I18nService.tr("钻石锹");
            case "DIAMOND_HOE" -> I18nService.tr("钻石锄");
            case "NETHERITE_SWORD" -> I18nService.tr("下界合金剑");
            case "NETHERITE_PICKAXE" -> I18nService.tr("下界合金镐");
            case "NETHERITE_AXE" -> I18nService.tr("下界合金斧");
            case "NETHERITE_SHOVEL" -> I18nService.tr("下界合金锹");
            case "NETHERITE_HOE" -> I18nService.tr("下界合金锄");
            case "IRON_SWORD" -> I18nService.tr("铁剑");
            case "IRON_PICKAXE" -> I18nService.tr("铁镐");
            case "IRON_AXE" -> I18nService.tr("铁斧");
            case "IRON_SHOVEL" -> I18nService.tr("铁锹");
            case "ENCHANTED_BOOK" -> I18nService.tr("附魔书");
            case "NAME_TAG" -> I18nService.tr("命名牌");
            case "SADDLE" -> I18nService.tr("鞍");
            case "NAUTILUS_SHELL" -> I18nService.tr("鹦鹉螺壳");
            case "LILY_PAD" -> I18nService.tr("睡莲");
            case "BOW" -> I18nService.tr("弓");
            case "FISHING_ROD" -> I18nService.tr("钓鱼竿");
            case "ELYTRA" -> I18nService.tr("鞘翅");
            case "TRIDENT" -> I18nService.tr("三叉戟");
            case "SHIELD" -> I18nService.tr("盾牌");
            default -> material;
        };
    }

    /**
     * 构建玩家数据文本段落
     */
    public String buildSummarySection(SummaryStats stats, PlayerVanillaStats vanillaStats) {
        StringBuilder sb = new StringBuilder(I18nService.tr("【玩家数据】"));

        if (vanillaStats != null) {
            // 基础/战斗
            sb.append("\n").append(I18nService.tr("游戏时长: {} 分钟", vanillaStats.playMinutes()));
            sb.append("\n").append(I18nService.tr("死亡: {} 次", vanillaStats.deaths()));
            sb.append("\n").append(I18nService.tr("击杀生物: {} 只", vanillaStats.mobKills()));
            if (vanillaStats.playerKills() > 0) {
                sb.append("\n").append(I18nService.tr("击杀玩家: {} 人", vanillaStats.playerKills()));
            }
            if (vanillaStats.damageDealt() > 0) {
                sb.append("\n").append(I18nService.tr("造成伤害: {} 颗心", vanillaStats.damageDealt() / 2));
            }
            if (vanillaStats.damageTaken() > 0) {
                sb.append("\n").append(I18nService.tr("承受伤害: {} 颗心", vanillaStats.damageTaken() / 2));
            }
            if (vanillaStats.damageShielded() > 0) {
                sb.append("\n").append(I18nService.tr("盾牌格挡: {} 颗心", vanillaStats.damageShielded() / 2));
            }
            if (vanillaStats.animalsBred() > 0) {
                sb.append("\n").append(I18nService.tr("繁殖动物: {} 次", vanillaStats.animalsBred()));
            }
            if (vanillaStats.jumps() > 0) {
                sb.append("\n").append(I18nService.tr("跳跃: {} 次", vanillaStats.jumps()));
            }
            if (vanillaStats.sleepCount() > 0) {
                sb.append("\n").append(I18nService.tr("睡觉: {} 次", vanillaStats.sleepCount()));
            }

            // 稀有BOSS（只在有数据时输出）
            if (vanillaStats.dragonKills() > 0 || vanillaStats.dragonDeaths() > 0) {
                sb.append("\n").append(I18nService.tr("击杀末影龙: {} 次，被末影龙击杀: {} 次", vanillaStats.dragonKills(), vanillaStats.dragonDeaths()));
            }
            if (vanillaStats.witherKills() > 0 || vanillaStats.witherDeaths() > 0) {
                sb.append("\n").append(I18nService.tr("击杀凋零: {} 次，被凋零击杀: {} 次", vanillaStats.witherKills(), vanillaStats.witherDeaths()));
            }
            if (vanillaStats.elderGuardianKills() > 0) {
                sb.append("\n").append(I18nService.tr("击杀远古守卫者: {} 次", vanillaStats.elderGuardianKills()));
            }
            if (vanillaStats.wardenKills() > 0) {
                sb.append("\n").append(I18nService.tr("击杀监守者: {} 次", vanillaStats.wardenKills()));
            }
            if (vanillaStats.ironGolemKills() > 0) {
                sb.append("\n").append(I18nService.tr("击杀铁傀儡: {} 次", vanillaStats.ironGolemKills()));
            }

            // 探索/距离（统一输出格数：1格 = 100cm）
            if (vanillaStats.walkCm() > 0) {
                sb.append("\n").append(I18nService.tr("行走距离: {} 格", vanillaStats.walkCm() / 100));
            }
            if (vanillaStats.sprintCm() > 0) {
                sb.append("\n").append(I18nService.tr("疾跑距离: {} 格", vanillaStats.sprintCm() / 100));
            }
            if (vanillaStats.flyCm() > 0) {
                sb.append("\n").append(I18nService.tr("飞行距离: {} 格", vanillaStats.flyCm() / 100));
            }
            if (vanillaStats.elytraCm() > 0) {
                sb.append("\n").append(I18nService.tr("鞘翅飞行距离: {} 格", vanillaStats.elytraCm() / 100));
            }
            if (vanillaStats.swimCm() > 0) {
                sb.append("\n").append(I18nService.tr("游泳距离: {} 格", vanillaStats.swimCm() / 100));
            }
            if (vanillaStats.boatCm() > 0) {
                sb.append("\n").append(I18nService.tr("划船距离: {} 格", vanillaStats.boatCm() / 100));
            }
            if (vanillaStats.minecartCm() > 0) {
                sb.append("\n").append(I18nService.tr("矿车行驶距离: {} 格", vanillaStats.minecartCm() / 100));
            }
            if (vanillaStats.horseCm() > 0) {
                sb.append("\n").append(I18nService.tr("骑马距离: {} 格", vanillaStats.horseCm() / 100));
            }
            if (vanillaStats.climbCm() > 0) {
                sb.append("\n").append(I18nService.tr("攀爬距离: {} 格", vanillaStats.climbCm() / 100));
            }
            if (vanillaStats.fallCm() > 0) {
                sb.append("\n").append(I18nService.tr("摔落距离: {} 格", vanillaStats.fallCm() / 100));
            }

            // 生活/趣味
            if (vanillaStats.fishCaught() > 0) {
                sb.append("\n").append(I18nService.tr("钓鱼: {} 次", vanillaStats.fishCaught()));
            }
            if (vanillaStats.enchantCount() > 0) {
                sb.append("\n").append(I18nService.tr("附魔: {} 次", vanillaStats.enchantCount()));
            }
            if (vanillaStats.raidTriggered() > 0) {
                sb.append("\n").append(I18nService.tr("触发袭击: {} 次", vanillaStats.raidTriggered()));
            }
            if (vanillaStats.raidWon() > 0) {
                sb.append("\n").append(I18nService.tr("袭击胜利: {} 次", vanillaStats.raidWon()));
            }
            if (vanillaStats.diamondOreMined() > 0) {
                sb.append("\n").append(I18nService.tr("挖钻石矿: {} 个", vanillaStats.diamondOreMined()));
            }
        }

        // 加入天数（来自 profile，Bukkit Stats 无对应项）
        if (stats != null && stats.daysSinceFirstLogin() > 0) {
            sb.append("\n").append(I18nService.tr("加入服务器: {} 天前", stats.daysSinceFirstLogin()));
        }

        // 上次游玩时长（原始分钟数，LLM自行换算）
        if (stats != null && stats.lastSessionDurationMs() > 0) {
            long lastSessionMin = TimeUnit.MILLISECONDS.toMinutes(stats.lastSessionDurationMs());
            if (lastSessionMin > 0) {
                sb.append("\n").append(I18nService.tr("上次游玩时长: {} 分钟", lastSessionMin));
            }
        }

        // 上次游玩亮点
        List<ServerEvent> highlights = stats != null ? stats.lastSessionHighlights() : null;
        if (highlights != null && !highlights.isEmpty()) {
            Map<ServerEventTypeEnum, List<ServerEvent>> grouped = highlights.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));
            StringJoiner hj = new StringJoiner("，");
            for (Map.Entry<ServerEventTypeEnum, List<ServerEvent>> entry : grouped.entrySet()) {
                hj.add(summarizeEvent(I18nService.tr("你"), entry.getKey(), entry.getValue()));
            }
            sb.append("\n").append(I18nService.tr("【上次游玩亮点】\n")).append(hj);
        }

        return sb.toString();
    }

    /**
     * 格式化时长（ms → 可读）
     */
    public String formatDuration(long ms) {
        if (ms <= 0) return I18nService.tr("刚刚");

        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;

        if (hours > 24) {
            long days = hours / 24;
            return I18nService.tr("{} 天", days);
        } else if (hours > 0) {
            return minutes > 0 ? I18nService.tr("{} 小时 {} 分钟", hours, minutes) : I18nService.tr("{} 小时", hours);
        } else if (minutes > 0) {
            return I18nService.tr("{} 分钟", minutes);
        } else {
            return I18nService.tr("刚刚");
        }
    }

    /**
     * 格式化会话时长（分钟 → 可读，不附带“分钟”字样）
     */
    private String formatSessionDuration(long totalMinutes) {
        if (totalMinutes <= 0) return "";
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        if (hours >= 24) {
            long days = hours / 24;
            return I18nService.tr("{} 天", days);
        } else if (hours > 0) {
            return mins > 0 ? I18nService.tr("{} 小时 {} 分钟", hours, mins) : I18nService.tr("{} 小时", hours);
        } else {
            return I18nService.tr("{} 分钟", mins);
        }
    }
}
