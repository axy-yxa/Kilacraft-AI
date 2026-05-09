package com.zm.kilacraftAI.greeting;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.event.ServerEvent;
import com.zm.kilacraftAI.event.ServerEventType;
import com.zm.kilacraftAI.profile.PlayerProfile;

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

    // ==================== 默认提示词模板（配置文件为空时使用） ====================

    public static final String DEFAULT_FIRST_LOGIN_PROMPT = """
            你是这个 Minecraft 服务器的 AI 助手，{player} 第一次来到服务器。
            请直接对 {player} 说话，像朋友一样自然地表示欢迎。
            要求：
            1. 简短介绍自己能做什么：回答问题、查物品、操作市场、挂机任务等，玩家用 /ai 就能找你
            2. 如果有服务器信息，顺便提一下；没有就不提
            3. 不超过 120 个汉字，像朋友聊天一样自然
            
            {server_info}""";

    public static final String DEFAULT_RETURNING_PROMPT = """
            你是这个 Minecraft 服务器的 AI 助手。{player} 回来了，距离上次登录已经过去了 {offline_duration}。
            请直接对 {player} 说话，像朋友一样自然地打招呼。
            
            {own_events_section}
            {friend_events_section}
            {online_friends_section}
            {last_location}
            {summary_section}
            
            要求：
            1. 离线事件、好友动态、在线好友三个分类中，只有包含实际事件（非"没有""暂无"等空内容）的才需要提及。若某分类无内容，就完全跳过，不要用"没什么特别的事""一切如常""安静着"等概括性话语去填充。
            2. 当在线好友和离线好友均为空时，绝对禁止提及任何与好友在线状态有关的表述，包括"没人在线""好友们都在忙""没谁上线""没人玩"等。好友动态和登录频次是独立数据，不受此限制。如果有离线好友数据，最多只提离线最久的一个，不要逐个报告。
            3. 【最强禁令】玩家数据全是生涯累计统计，绝对不许直接或间接透露。只有下面两种情况下才能提：(a)达到整数里程碑；(b)与本次离线事件有直接因果关系。除此以外，任何数据都当作不存在——即使你觉得它再有意思，也不准用"打了不少""挨了很多""飞了好远""跳来跳去"等模糊方式形容，连"你的数据有些变化"这种话都不许说。如果没有任何数据满足条件，就把整个玩家数据分类彻底忘掉，当作没看到。
            4. 通过规则3审核的数据可以直接说出具体数值和单位，这是最自然清晰的表达方式。口语化描述（如"挨了不少打"）可作为替代风格，但不是强制要求。
            5. 离线时间很短（半小时以内）时，不要说"好久不见"，用"刚走一会儿""才离开没多久"这类说法。
            6. 语气：平实自然，朋友闲聊，不夸张也不冷淡。禁止用"哟""嘿兄弟""欢迎回来！"等夸张语气，别堆感叹号，别滥用破折号。也不要说一两句就结束，保持内容充实但控制在300个汉字内。
            7. 上次位置除非在末地或下界，否则别提坐标或世界名。
            8. 死亡消息翻译成自然中文（如"was slain by Zombie"→"被僵尸打死了"），成就名翻译成易懂中文。
            9. 好友世界名：world=主世界，world_nether=下界，world_the_end=末地，其他用原名。
            10. 禁止从玩家画像编造社交故事（如"他不在你自己玩吧"），画像只用于调整说话风格。
            11. 提到此前游玩亮点时，必须说"你上次在线的时候"，不能只说"你上次"。
            12. 禁止用"你忙你的""继续忙吧"这类打发人的话收尾。如果实在没什么可说的，简单问声好就行，例如"这边挺安静的，别的没啥。""";

    public static final String DEFAULT_FIRST_LOGIN_PROMPT_EN = """
            You are the AI assistant of this Minecraft server. {player} has just joined for the first time.
            Speak directly to {player}, naturally and warmly, like a friend.
            Requirements:
            1. Briefly introduce what you can do: answer questions, look up items, operate the market, AFK tasks, etc. Players can find you with /ai
            2. If there is server info, mention it briefly; if not, skip it
            3. Keep it under 120 words, be natural and friendly like chatting with a friend
            
            {server_info}""";

    public static final String DEFAULT_RETURNING_PROMPT_EN = """
            You are the AI assistant of this Minecraft server. {player} is back, and it has been {offline_duration} since their last login.
            Speak directly to {player}, greet them naturally like a friend.
            
            {own_events_section}
            {friend_events_section}
            {online_friends_section}
            {last_location}
            {summary_section}
            
            Requirements:
            1. Only mention categories that contain actual events (not "nothing happened" or "no friends"). If a category has no real content, skip it entirely — do not pad with phrases like "nothing special", "all quiet", or "everything's normal".
            2. When both online and offline friend lists are empty, it is absolutely forbidden to mention anything about friend online status — no "nobody's on", "friends are busy", "no one's playing", etc. Friend activity and login frequency are independent data and not affected by this. If offline friend data exists, mention at most the one offline the longest; do not report each friend individually.
            3. [STRONGEST RULE] Player stats are all lifetime cumulative — never reveal them directly or indirectly. You may only mention a stat if: (a) it reaches a round-number milestone; (b) it has a direct causal link to an offline event this session. Otherwise, treat every number as if it doesn't exist — no matter how interesting, do NOT use vague descriptions like "killed quite a few", "took a lot of damage", "traveled really far" to hint at forbidden stats. Not even "your stats changed a bit" is allowed. If nothing qualifies, forget the entire player stats category completely.
            4. Stats that pass Rule 3 can be stated with exact numbers and units — this is the most natural and clear way. Casual descriptions (e.g., "took a beating") are optional alternatives, not mandatory.
            5. If offline time is very short (under 30 minutes), don't say "long time no see". Use phrases like "you were gone for a bit" or "just stepped away".
            6. Tone: calm and natural, like chatting with a friend — not exaggerated, not cold. No "Hey buddy!", "Welcome back!!", excessive exclamation marks, or dash overuse. Don't end after just one or two sentences; keep content substantial but under 200 words.
            7. Last location: only mention if in the End or Nether; otherwise don't mention coordinates or world name.
            8. Death messages: translate into natural language (e.g., "was slain by Zombie" → "got killed by a Zombie"). Achievement names: translate into player-friendly descriptions.
            9. Friend world names: world=Overworld, world_nether=Nether, world_the_end=The End. Others use raw names as-is.
            10. Do not fabricate social narratives from player profile (e.g., "they're not here so play by yourself"). Profile is only for adjusting tone and style.
            11. When mentioning previous session highlights, you must say "while you were online last time" — never just "last time".
            12. Do not end with dismissive phrases like "carry on" or "go do your thing". If there's really nothing to say, just a simple greeting will do, e.g., "Pretty quiet around here, nothing else to report.""";

    public static String getDefaultFirstLoginPrompt() {
        return isEnglish() ? DEFAULT_FIRST_LOGIN_PROMPT_EN : DEFAULT_FIRST_LOGIN_PROMPT;
    }

    public static String getDefaultReturningPrompt() {
        return isEnglish() ? DEFAULT_RETURNING_PROMPT_EN : DEFAULT_RETURNING_PROMPT;
    }

    private static boolean isEnglish() {
        try {
            return !"zh".equals(com.zm.kilacraftAI.KilacraftAI.getInstance().getConfigManager().getLanguage());
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
        String ownEventsSection = buildOfflineEventsSection(context.getOfflineEvents(), context.getGlobalEventCount());
        String friendEventsSection = buildFriendEventsSection(context.getFriendEvents(), context.getFriendLoginCounts());
        String onlineFriendsSection = buildOnlineFriendsSection(context.getOnlineFriends(), context.getOfflineFriends());
        String lastLocationSection = buildLastLocationSection(context.getProfile());
        String summarySection = buildSummarySection(context.getSummaryStats(), context.getVanillaStats());

        return customPrompt.replace("{player}", playerName).replace("{offline_duration}", offlineDuration).replace("{own_events_section}", ownEventsSection).replace("{friend_events_section}", friendEventsSection).replace("{online_friends_section}", onlineFriendsSection).replace("{last_location}", lastLocationSection).replace("{summary_section}", summarySection);
    }

    /**
     * 构建离线事件文本段落
     *
     * @param events           离线事件列表
     * @param globalEventCount 离线期间全服事件总数
     */
    public String buildOfflineEventsSection(List<ServerEvent> events, int globalEventCount) {
        if (events == null || events.isEmpty()) {
            StringBuilder emptySb = new StringBuilder(I18nService.tr("【离线期间发生的事】\n没有特别的事情发生。"));
            if (globalEventCount > 0) {
                emptySb.append("\n").append(I18nService.tr("你不在的时候，全服共发生了 {} 件事", globalEventCount));
            }
            return emptySb.toString();
        }

        Map<ServerEventType, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(I18nService.tr("【离线期间发生的事】"));

        for (Map.Entry<ServerEventType, List<ServerEvent>> entry : grouped.entrySet()) {
            String summary = summarizeEventType(entry.getKey(), entry.getValue());
            joiner.add(summary);
        }

        // 追加全服事件总数
        if (globalEventCount > 0) {
            joiner.add(I18nService.tr("你不在的时候，全服共发生了 {} 件事", globalEventCount));
        }

        return joiner.toString();
    }

    /**
     * 按事件类型生成摘要
     */
    private String summarizeEventType(ServerEventType type, List<ServerEvent> events) {
        String data = events.get(0).getData() != null ? events.get(0).getData() : "";
        return switch (type) {
            case MARKET_ITEM_SOLD -> {
                int count = events.size();
                yield count == 1
                        ? I18nService.tr("你上架的商品已卖出") + appendDataSuffix(events.get(0))
                        : I18nService.tr("你上架的 {} 件商品已卖出", count);
            }
            case MARKET_MONEY_RECEIVED -> {
                int count = events.size();
                String amountStr = formatMoneyAmount(events.get(0).getData());
                yield count == 1
                        ? I18nService.tr("你收到了 {} 收入", amountStr)
                        : I18nService.tr("你收到了 {} 笔款项（共 {}）", count, sumMoneyAmounts(events));
            }
            case PLAYER_DEATH -> {
                int count = events.size();
                if (count == 1) {
                    yield data.isEmpty() ? I18nService.tr("你挂了 1 次") : I18nService.tr("你挂了 1 次（{}）", data);
                } else {
                    Map<String, Long> deathMsgCount = events.stream().collect(Collectors.groupingBy(e -> e.getData() != null ? e.getData() : "未知", Collectors.counting()));
                    StringJoiner dj = new StringJoiner("、");
                    deathMsgCount.forEach((msg, cnt) -> dj.add(msg + (cnt > 1 ? " x" + cnt : "")));
                    yield I18nService.tr("你挂了 {} 次（{}）", count, dj);
                }
            }
            case PLAYER_ADVANCEMENT -> {
                int count = events.size();
                if (count == 1) {
                    yield I18nService.tr("你达成了成就 {}", data);
                } else {
                    StringJoiner aj = new StringJoiner(", ");
                    for (ServerEvent e : events) {
                        aj.add(e.getData() != null ? e.getData() : "未知");
                    }
                    yield I18nService.tr("你达成了 {} 个成就（{}）", count, aj);
                }
            }
            case PLAYER_LEVEL_UP -> {
                int count = events.size();
                if (count == 1) {
                    String levelDesc = formatLevelChange(data);
                    yield levelDesc.isEmpty() ? I18nService.tr("你升级了") : I18nService.tr("你升级了（{}）", levelDesc);
                } else {
                    String oldestData = events.get(events.size() - 1).getData();
                    String startLevel = extractStartLevel(oldestData);
                    String endLevel = extractEndLevel(data);
                    if (!startLevel.isEmpty() && !endLevel.isEmpty()) {
                        yield I18nService.tr("你从 {} 级升到了 {} 级（共{}级）", startLevel, endLevel, count);
                    } else {
                        yield I18nService.tr("你升级 {} 次", count);
                    }
                }
            }
            case PLAYER_USE_TOTEM -> {
                String suffix = data.isEmpty() ? "" : I18nService.tr("（{}）", formatDamageCause(data));
                yield events.size() > 1
                        ? I18nService.tr("你触发了 {} 次不死图腾", events.size()) + suffix
                        : I18nService.tr("你触发了不死图腾") + suffix;
            }
            case PLAYER_DEFEAT_BOSS ->
                    events.size() > 1 ? I18nService.tr("你击杀了 {} 共 {} 次", formatEntityName(data), events.size()) : I18nService.tr("你击杀了 {}", formatEntityName(data));
            case PLAYER_COMPLETE_RAID -> I18nService.tr("你完成了袭击（{}）", data);
            case PLAYER_PET_DEATH -> I18nService.tr("你的宠物战死了（{}）", formatPetDeathData(data));
            case PLAYER_PVP_KILL ->
                    events.size() > 1 ? I18nService.tr("你在PVP中击杀了 {} 共 {} 次", data, events.size()) : I18nService.tr("你在PVP中击杀了 {}", data);
            case PLAYER_PVP_DEATH ->
                    events.size() > 1 ? I18nService.tr("你在PVP中被 {} 击杀了 {} 次", data, events.size()) : I18nService.tr("你在PVP中被 {} 击杀了", data);
            case PLAYER_TOOL_BREAK -> I18nService.tr("你的{} 断了", formatMaterialName(data));
            case PLAYER_CATCH_TREASURE -> I18nService.tr("你钓到了 {}", formatMaterialName(data));
            case PLAYER_LIGHTNING_STRIKE ->
                    events.size() > 1 ? I18nService.tr("你被雷劈了 {} 次", events.size()) : I18nService.tr("你被雷劈了");
            case PLAYER_CURE_VILLAGER ->
                    events.size() > 1 ? I18nService.tr("你救了 {} 个僵尸村民", events.size()) : I18nService.tr("你救了一个僵尸村民");
            case PLAYER_MINE_ANCIENT_DEBRIS ->
                    events.size() > 1 ? I18nService.tr("你挖到了 {} 块远古残骸", events.size()) : I18nService.tr("你挖到了远古残骸");
            case PLAYER_TAME_ANIMAL -> I18nService.tr("你驯服了{}", formatPetEntityName(data));
            case PLAYER_CRAFT_ENCHANTED_GOLDEN_APPLE ->
                    events.size() > 1 ? I18nService.tr("你合成了 {} 个附魔金苹果", events.size()) : I18nService.tr("你合成了附魔金苹果");
            case PLAYER_BUILD_WITHER ->
                    events.size() > 1 ? I18nService.tr("你召唤了 {} 次凋零", events.size()) : I18nService.tr("你召唤了凋零");
            default -> I18nService.tr(type.getDescription()) + " x" + events.size();
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
            return I18nService.tr("（{}）", event.getData());
        }
        return "";
    }

    /**
     * 构建上次位置文本段落
     */
    public String buildLastLocationSection(PlayerProfile profile) {
        if (profile == null) return "";

        String world = profile.getLastWorld();
        if (world == null || world.isEmpty()) return "";

        String friendlyWorld = formatWorldName(world);

        return I18nService.tr("【上次位置】\n你上次在 {}，坐标 ({}, {}, {})", friendlyWorld, (int) profile.getLastX(), (int) profile.getLastY(), (int) profile.getLastZ());
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
                Map<ServerEventType, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

                for (Map.Entry<ServerEventType, List<ServerEvent>> ge : grouped.entrySet()) {
                    String summary = summarizeFriendEvent(playerName, ge.getKey(), ge.getValue());
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
     * 生成单个好友的事件摘要
     */
    private String summarizeFriendEvent(String playerName, ServerEventType type, List<ServerEvent> events) {
        // 大多数好友动态事件只有1条，取第一条的 data 作为详情
        String data = events.get(0).getData() != null ? events.get(0).getData() : "";
        return switch (type) {
            case PLAYER_DEATH -> {
                int count = events.size();
                if (count == 1) {
                    yield data.isEmpty() ? I18nService.tr("{} 挂了 1 次", playerName) : I18nService.tr("{} 挂了 1 次（{}）", playerName, data);
                } else {
                    Map<String, Long> deathMsgCount = events.stream().collect(Collectors.groupingBy(e -> e.getData() != null ? e.getData() : "未知", Collectors.counting()));
                    StringJoiner dj = new StringJoiner("、");
                    deathMsgCount.forEach((msg, cnt) -> dj.add(msg + (cnt > 1 ? " x" + cnt : "")));
                    yield I18nService.tr("{} 挂了 {} 次（{}）", playerName, count, dj);
                }
            }
            case PLAYER_ADVANCEMENT -> {
                int count = events.size();
                if (count == 1) {
                    yield I18nService.tr("{} 达成了成就 {}", playerName, data);
                } else {
                    StringJoiner aj = new StringJoiner(", ");
                    for (ServerEvent e : events) {
                        aj.add(e.getData() != null ? e.getData() : "未知");
                    }
                    yield I18nService.tr("{} 达成了 {} 个成就（{}）", playerName, count, aj);
                }
            }
            case PLAYER_LEVEL_UP -> {
                int count = events.size();
                if (count == 1) {
                    String levelDesc = formatLevelChange(data);
                    yield levelDesc.isEmpty() ? I18nService.tr("{} 升级了", playerName) : I18nService.tr("{} 升级了（{}）", playerName, levelDesc);
                } else {
                    String oldestData = events.get(events.size() - 1).getData();
                    String startLevel = extractStartLevel(oldestData);
                    String endLevel = extractEndLevel(data);
                    if (!startLevel.isEmpty() && !endLevel.isEmpty()) {
                        yield I18nService.tr("{} 从 {} 级升到了 {} 级", playerName, startLevel, endLevel);
                    } else {
                        yield I18nService.tr("{} 升了 {} 级", playerName, count);
                    }
                }
            }
            case PLAYER_USE_TOTEM -> {
                String suffix = data.isEmpty() ? "" : I18nService.tr("（{}）", formatDamageCause(data));
                yield events.size() > 1
                        ? I18nService.tr("{} 触发了 {} 次不死图腾", playerName, events.size()) + suffix
                        : I18nService.tr("{} 触发了不死图腾", playerName) + suffix;
            }
            case PLAYER_DEFEAT_BOSS ->
                    events.size() > 1 ? I18nService.tr("{} 击杀了 {} 共 {} 次", playerName, formatEntityName(data), events.size()) : I18nService.tr("{} 击杀了 {}", playerName, formatEntityName(data));
            case PLAYER_COMPLETE_RAID -> I18nService.tr("{} 完成了袭击（{}）", playerName, data);
            case PLAYER_PET_DEATH -> I18nService.tr("{} 的宠物战死了（{}）", playerName, formatPetDeathData(data));
            case PLAYER_PVP_KILL ->
                    events.size() > 1 ? I18nService.tr("{} 在PVP中击杀了 {} 共 {} 次", playerName, data, events.size()) : I18nService.tr("{} 在PVP中击杀了 {}", playerName, data);
            case PLAYER_PVP_DEATH ->
                    events.size() > 1 ? I18nService.tr("{} 在PVP中被 {} 击杀了 {} 次", playerName, data, events.size()) : I18nService.tr("{} 在PVP中被 {} 击杀了", playerName, data);
            case PLAYER_TOOL_BREAK -> I18nService.tr("{} 的{} 断了", playerName, formatMaterialName(data));
            case PLAYER_CATCH_TREASURE -> I18nService.tr("{} 钓到了 {}", playerName, formatMaterialName(data));
            case PLAYER_LIGHTNING_STRIKE ->
                    events.size() > 1 ? I18nService.tr("{} 被雷劈了 {} 次", playerName, events.size()) : I18nService.tr("{} 被雷劈了", playerName);
            case PLAYER_CURE_VILLAGER ->
                    events.size() > 1 ? I18nService.tr("{} 救了 {} 个僵尸村民", playerName, events.size()) : I18nService.tr("{} 救了一个僵尸村民", playerName);
            case PLAYER_MINE_ANCIENT_DEBRIS ->
                    events.size() > 1 ? I18nService.tr("{} 挖到了 {} 块远古残骸", playerName, events.size()) : I18nService.tr("{} 挖到了远古残骸", playerName);
            case PLAYER_TAME_ANIMAL -> I18nService.tr("{} 驯服了{}", playerName, formatPetEntityName(data));
            case PLAYER_CRAFT_ENCHANTED_GOLDEN_APPLE ->
                    events.size() > 1 ? I18nService.tr("{} 合成了 {} 个附魔金苹果", playerName, events.size()) : I18nService.tr("{} 合成了附魔金苹果", playerName);
            case PLAYER_BUILD_WITHER ->
                    events.size() > 1 ? I18nService.tr("{} 召唤了 {} 次凋零", playerName, events.size()) : I18nService.tr("{} 召唤了凋零", playerName);
            default -> I18nService.tr("{} 触发了 {} 次 {}", playerName, events.size(), I18nService.tr(type.getDescription()));
        };
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
            // === 基础/战斗 ===
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

            // === 稀有BOSS（只在有数据时输出） ===
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

            // === 探索/距离（统一输出格数：1格 = 100cm） ===
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

            // === 生活/趣味 ===
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
            Map<ServerEventType, List<ServerEvent>> grouped = highlights.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));
            StringJoiner hj = new StringJoiner("，");
            for (Map.Entry<ServerEventType, List<ServerEvent>> entry : grouped.entrySet()) {
                hj.add(summarizeEventType(entry.getKey(), entry.getValue()));
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
