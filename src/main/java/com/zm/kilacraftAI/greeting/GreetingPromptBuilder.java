package com.zm.kilacraftAI.greeting;

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
            1. 离线事件、好友动态、在线好友三个分类有实际内容的都要提及。玩家数据分类受规则3约束，不满足条件则整个分类跳过不提
            2. 在线好友列表和离线好友列表均为空时，绝对不要提及任何好友在线状态，不要说"好友们在忙"之类的话。好友动态和登录频次是独立数据，不受此限制。好友状态最多提1个离线最久的，不要逐人报告
            3. 【绝对禁令】玩家数据是生涯累计统计。只有满足以下条件之一的数据才能向玩家提及：(a)整数里程碑（如游戏满1000小时、击杀满10000）；(b)与本次离线事件有关联的数字。不满足这两个条件的普通数值，无论你觉得多有趣，都绝对不能提。如果没有任何数据满足条件，整个玩家数据分类跳过，不要勉强找一个来提。零值忽略
            4. 数据后附的单位（分钟、颗心、格等）仅供你理解数据含义。向玩家描述时，禁止直接报出带单位的原始数值（如"75万颗心""14万格"），必须换算为日常说法（如"挨了不少打""飞了好远"）
            5. 如果离线时间很短（如"刚刚"），不要说"好久不见"，轻松打招呼即可
            6. 语气平实自然，像平时朋友说话，不要用"哟""嘿兄弟""欢迎回来！"等夸张热情的语气，不要堆砌感叹号，不要"——"破折号过多。输出示例参考："zmjiushizhemoz，你不在的时候被雷劈了，还挺倒霉的。zmjiushizhemoz那边也挺热闹，他的狼被苦力怕炸死了。其他没啥事，服务器现在就你俩在线。"内容丰富充实但不要只说一两句话就结束；控制在 300 个汉字以内
            7. 上次位置仅供参考，除非在特殊世界（末地/下界），否则不要提及
            8. 死亡消息为Minecraft原版英文消息，需翻译为自然中文（如"was slain by Zombie"→"被僵尸打死了"）；成就名为命名空间格式，需翻译为玩家易懂的中文
            9. 好友世界名为服务器原始名（world=主世界、world_nether=下界、world_the_end=末地），非标准名直接使用原名
            10. 不要从玩家画像推断社交叙事（如"他不在你自己玩吧"），画像仅用于调整语气风格
            11. 提到【上次游玩亮点】中的事件时，必须说"你上次在线的时候"，不能只说"你上次"，避免玩家不知道"上次"指什么时候""";

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
            StringBuilder emptySb = new StringBuilder("【离线期间发生的事】\n没有特别的事情发生。");
            if (globalEventCount > 0) {
                emptySb.append("\n你不在的时候，全服共发生了 ").append(globalEventCount).append(" 件事");
            }
            return emptySb.toString();
        }

        Map<ServerEventType, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("【离线期间发生的事】");

        for (Map.Entry<ServerEventType, List<ServerEvent>> entry : grouped.entrySet()) {
            String summary = summarizeEventType(entry.getKey(), entry.getValue());
            joiner.add(summary);
        }

        // 追加全服事件总数
        if (globalEventCount > 0) {
            joiner.add("你不在的时候，全服共发生了 " + globalEventCount + " 件事");
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
                yield count == 1 ? "你上架的商品已卖出" + appendDataSuffix(events.get(0)) : "你上架的 " + count + " 件商品已卖出";
            }
            case MARKET_MONEY_RECEIVED -> {
                int count = events.size();
                String amountStr = formatMoneyAmount(events.get(0).getData());
                yield count == 1 ? "你收到了 " + amountStr + " 收入" : "你收到了 " + count + " 笔款项（共 " + sumMoneyAmounts(events) + "）";
            }
            case PLAYER_DEATH -> {
                int count = events.size();
                if (count == 1) {
                    yield "你挂了 1 次" + (data.isEmpty() ? "" : "（" + data + "）");
                } else {
                    // 多次死亡：按死亡消息分组，输出原文（LLM自行归纳）
                    Map<String, Long> deathMsgCount = events.stream().collect(Collectors.groupingBy(e -> e.getData() != null ? e.getData() : "未知", Collectors.counting()));
                    StringJoiner dj = new StringJoiner("、");
                    deathMsgCount.forEach((msg, cnt) -> dj.add(msg + (cnt > 1 ? " x" + cnt : "")));
                    yield "你挂了 " + count + " 次（" + dj + "）";
                }
            }
            case PLAYER_ADVANCEMENT -> {
                int count = events.size();
                if (count == 1) {
                    yield "你达成了成就 " + data;
                } else {
                    // 多个成就：逐条输出成就 key（LLM自行判断有趣程度）
                    StringJoiner aj = new StringJoiner(", ");
                    for (ServerEvent e : events) {
                        aj.add(e.getData() != null ? e.getData() : "未知");
                    }
                    yield "你达成了 " + count + " 个成就（" + aj + "）";
                }
            }
            case PLAYER_LEVEL_UP -> {
                int count = events.size();
                if (count == 1) {
                    // 单次升级：解析 "13 → 14" 格式
                    String levelDesc = formatLevelChange(data);
                    yield "你升级了" + (levelDesc.isEmpty() ? "" : "（" + levelDesc + "）");
                } else {
                    // 多次升级：events 按 created_at DESC（最新在前）
                    // 取最早事件的起始等级 → 最新事件的结束等级
                    String oldestData = events.get(events.size() - 1).getData();
                    String startLevel = extractStartLevel(oldestData);
                    String endLevel = extractEndLevel(data); // data = events.get(0)，即最新事件
                    if (!startLevel.isEmpty() && !endLevel.isEmpty()) {
                        yield "你从 " + startLevel + " 级升到了 " + endLevel + " 级（共" + count + "级）";
                    } else {
                        yield "你升级 " + count + " 次";
                    }
                }
            }
            case PLAYER_USE_TOTEM ->
                    "你触发了不死图腾" + (events.size() > 1 ? " " + events.size() + " 次" : "") + (data.isEmpty() ? "" : "（" + formatDamageCause(data) + "）");
            case PLAYER_DEFEAT_BOSS ->
                    "你击杀了 " + formatEntityName(data) + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_COMPLETE_RAID -> "你完成了袭击（" + data + "）";
            case PLAYER_PET_DEATH -> "你的宠物战死了（" + formatPetDeathData(data) + "）";
            case PLAYER_PVP_KILL -> "你在PVP中击杀了 " + data + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_PVP_DEATH ->
                    "你在PVP中被 " + data + " 击杀了" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_TOOL_BREAK -> "你的" + formatMaterialName(data) + " 断了";
            case PLAYER_CATCH_TREASURE -> "你钓到了 " + formatMaterialName(data);
            case PLAYER_LIGHTNING_STRIKE -> "你被雷劈了" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_CURE_VILLAGER -> "你救了一个僵尸村民" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_MINE_ANCIENT_DEBRIS ->
                    "你挖到了远古残骸" + (events.size() > 1 ? " " + events.size() + " 块" : "");
            case PLAYER_TAME_ANIMAL -> "你驯服了" + formatPetEntityName(data);
            case PLAYER_CRAFT_ENCHANTED_GOLDEN_APPLE ->
                    "你合成了附魔金苹果" + (events.size() > 1 ? " " + events.size() + " 个" : "");
            case PLAYER_BUILD_WITHER -> "你召唤了凋零" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            default -> type.getDescription() + " x" + events.size();
        };
    }

    /**
     * 解析等级变化描述（如 "13 → 14" → "13级→14级"）
     */
    private String formatLevelChange(String data) {
        if (data == null || data.isEmpty()) return "";
        String[] parts = data.split("\\s*→\\s*");
        if (parts.length == 2) {
            return parts[0].trim() + "级→" + parts[1].trim() + "级";
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
        if (data == null || data.isEmpty()) return "一笔款项";
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
     * 构建上次位置文本段落
     */
    public String buildLastLocationSection(PlayerProfile profile) {
        if (profile == null) return "";

        String world = profile.getLastWorld();
        if (world == null || world.isEmpty()) return "";

        String friendlyWorld = switch (world) {
            case "world" -> "主世界";
            case "world_nether" -> "下界";
            case "world_the_end" -> "末地";
            default -> world;
        };

        return "【上次位置】\n你上次在 " + friendlyWorld + "，坐标 (" + (int) profile.getLastX() + ", " + (int) profile.getLastY() + ", " + (int) profile.getLastZ() + ")";
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
            return "【好友在线状态】\n暂无好友。";
        }

        int onlineCount = org.bukkit.Bukkit.getOnlinePlayers().size();
        StringBuilder sb = new StringBuilder("【好友在线状态】\n");

        // 在线好友：名称 + 世界信息
        if (hasOnline) {
            StringJoiner joiner = new StringJoiner("、");
            for (FriendStatus f : onlineFriends) {
                StringBuilder desc = new StringBuilder(f.name());
                if (f.world() != null && !f.world().isEmpty()) {
                    desc.append("（").append(f.world()).append(")");
                }
                joiner.add(desc);
            }
            sb.append(joiner);
        } else {
            sb.append("没有好友在线");
        }

        // 在线人数
        sb.append("\n（服务器当前在线 ").append(onlineCount).append(" 人）");

        // 在线好友会话时长
        if (hasOnline) {
            boolean hasSession = false;
            StringBuilder sessionSb = new StringBuilder();
            for (FriendStatus f : onlineFriends) {
                if (f.sessionMinutes() > 0) {
                    sessionSb.append(f.name()).append(" 已在线 ").append(formatSessionDuration(f.sessionMinutes())).append("; ");
                    hasSession = true;
                }
            }
            if (hasSession) {
                sb.append("\n").append(sessionSb);
            }
        }

        // 离线好友最后在线时间
        if (hasOffline) {
            sb.append("\n离线好友：");
            StringJoiner offlineJoiner = new StringJoiner("、");
            for (FriendStatus f : offlineFriends) {
                if (f.sessionMinutes() > 0) {
                    offlineJoiner.add(f.name() + "（" + formatSessionDuration(f.sessionMinutes()) + "前下线）");
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
            return "【好友动态】\n好友们最近没什么特别的事。";
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("【好友动态】");

        // 好友事件摘要
        if (hasEvents) {
            // 按玩家名分组
            Map<String, List<ServerEvent>> byPlayer = new LinkedHashMap<>();
            for (ServerEvent e : friendEvents) {
                String name = e.getPlayerName() != null ? e.getPlayerName() : "某位好友";
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
            friendLoginCounts.forEach((name, count) -> loginJoiner.add(name + " " + count + " 次"));
            joiner.add("好友登录频次：" + loginJoiner);
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
                    yield playerName + " 挂了 1 次" + (data.isEmpty() ? "" : "（" + data + "）");
                } else {
                    Map<String, Long> deathMsgCount = events.stream().collect(Collectors.groupingBy(e -> e.getData() != null ? e.getData() : "未知", Collectors.counting()));
                    StringJoiner dj = new StringJoiner("、");
                    deathMsgCount.forEach((msg, cnt) -> dj.add(msg + (cnt > 1 ? " x" + cnt : "")));
                    yield playerName + " 挂了 " + count + " 次（" + dj + "）";
                }
            }
            case PLAYER_ADVANCEMENT -> {
                int count = events.size();
                if (count == 1) {
                    yield playerName + " 达成了成就 " + data;
                } else {
                    StringJoiner aj = new StringJoiner(", ");
                    for (ServerEvent e : events) {
                        aj.add(e.getData() != null ? e.getData() : "未知");
                    }
                    yield playerName + " 达成了 " + count + " 个成就（" + aj + "）";
                }
            }
            case PLAYER_LEVEL_UP -> {
                int count = events.size();
                if (count == 1) {
                    String levelDesc = formatLevelChange(data);
                    yield playerName + " 升级了" + (levelDesc.isEmpty() ? "" : "（" + levelDesc + "）");
                } else {
                    // events 按 created_at DESC（最新在前）
                    String oldestData = events.get(events.size() - 1).getData();
                    String startLevel = extractStartLevel(oldestData);
                    String endLevel = extractEndLevel(data); // data = events.get(0)，即最新事件
                    if (!startLevel.isEmpty() && !endLevel.isEmpty()) {
                        yield playerName + " 从 " + startLevel + " 级升到了 " + endLevel + " 级";
                    } else {
                        yield playerName + " 升了 " + count + " 级";
                    }
                }
            }
            case PLAYER_USE_TOTEM ->
                    playerName + " 触发了不死图腾" + (events.size() > 1 ? " " + events.size() + " 次" : "") + (data.isEmpty() ? "" : "（" + formatDamageCause(data) + "）");
            case PLAYER_DEFEAT_BOSS ->
                    playerName + " 击杀了 " + formatEntityName(data) + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_COMPLETE_RAID -> playerName + " 完成了袭击（" + data + "）";
            case PLAYER_PET_DEATH -> playerName + " 的宠物战死了（" + formatPetDeathData(data) + "）";
            case PLAYER_PVP_KILL ->
                    playerName + " 在PVP中击杀了 " + data + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_PVP_DEATH ->
                    playerName + " 在PVP中被 " + data + " 击杀了" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_TOOL_BREAK -> playerName + " 的" + formatMaterialName(data) + " 断了";
            case PLAYER_CATCH_TREASURE -> playerName + " 钓到了 " + formatMaterialName(data);
            case PLAYER_LIGHTNING_STRIKE ->
                    playerName + " 被雷劈了" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_CURE_VILLAGER ->
                    playerName + " 救了一个僵尸村民" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            case PLAYER_MINE_ANCIENT_DEBRIS ->
                    playerName + " 挖到了远古残骸" + (events.size() > 1 ? " " + events.size() + " 块" : "");
            case PLAYER_TAME_ANIMAL -> playerName + " 驯服了" + formatPetEntityName(data);
            case PLAYER_CRAFT_ENCHANTED_GOLDEN_APPLE ->
                    playerName + " 合成了附魔金苹果" + (events.size() > 1 ? " " + events.size() + " 个" : "");
            case PLAYER_BUILD_WITHER ->
                    playerName + " 召唤了凋零" + (events.size() > 1 ? " " + events.size() + " 次" : "");
            default -> playerName + " 触发了 " + events.size() + " 次 " + type.getDescription();
        };
    }

    /**
     * 实体类型名称友好化
     */
    private String formatEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) return "未知生物";
        return switch (entityType) {
            case "ENDER_DRAGON" -> "末影龙";
            case "WITHER" -> "凋零";
            case "ELDER_GUARDIAN" -> "远古守卫者";
            case "WARDEN" -> "监守者";
            default -> entityType;
        };
    }

    /**
     * 宠物死亡事件 data 友好化
     *
     * <p>data 格式为 "WOLF (ENTITY_ATTACK)"，拆分为实体类型 + 死因两部分分别翻译。</p>
     */
    private String formatPetDeathData(String data) {
        if (data == null || data.isEmpty()) return "未知宠物";
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
            result.append("，死因: ").append(formatDamageCause(cause));
        }
        if (!killerInfo.isEmpty()) {
            result.append("，被 ").append(formatEntityName(killerInfo)).append(" 击杀");
        }
        return result.toString();
    }

    /**
     * 宠物实体类型友好化
     */
    private String formatPetEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) return "未知宠物";
        return switch (entityType) {
            case "WOLF" -> "狼";
            case "CAT" -> "猫";
            case "HORSE" -> "马";
            case "DONKEY" -> "驴";
            case "MULE" -> "骡";
            case "PARROT" -> "鹦鹉";
            case "FOX" -> "狐狸";
            default -> entityType;
        };
    }

    /**
     * 伤害原因友好化
     */
    private String formatDamageCause(String cause) {
        if (cause == null || cause.isEmpty()) return "未知";
        return switch (cause) {
            case "ENTITY_ATTACK" -> "被攻击";
            case "ENTITY_EXPLOSION" -> "爆炸";
            case "BLOCK_EXPLOSION" -> "方块爆炸";
            case "FALL" -> "摔落";
            case "FIRE" -> "火焰";
            case "FIRE_TICK" -> "燃烧";
            case "LAVA" -> "岩浆";
            case "DROWNING" -> "溺水";
            case "SUFFOCATION" -> "窒息";
            case "LIGHTNING" -> "雷击";
            case "POISON" -> "中毒";
            case "WITHER" -> "凋零效果";
            case "MAGIC" -> "魔法";
            case "VOID" -> "虚空";
            case "CRAMMING" -> "挤压";
            case "DRYOUT" -> "干涸";
            case "STARVATION" -> "饥饿";
            case "PROJECTILE" -> "投射物";
            case "THORNS" -> "荆棘";
            default -> cause;
        };
    }

    /**
     * 物品类型名称友好化
     */
    private String formatMaterialName(String material) {
        if (material == null || material.isEmpty()) return "未知物品";
        return switch (material) {
            case "DIAMOND_PICKAXE" -> "钻石镐";
            case "DIAMOND_SWORD" -> "钻石剑";
            case "DIAMOND_AXE" -> "钻石斧";
            case "DIAMOND_SHOVEL" -> "钻石锹";
            case "DIAMOND_HOE" -> "钻石锄";
            case "NETHERITE_SWORD" -> "下界合金剑";
            case "NETHERITE_PICKAXE" -> "下界合金镐";
            case "NETHERITE_AXE" -> "下界合金斧";
            case "NETHERITE_SHOVEL" -> "下界合金锹";
            case "NETHERITE_HOE" -> "下界合金锄";
            case "IRON_SWORD" -> "铁剑";
            case "IRON_PICKAXE" -> "铁镐";
            case "IRON_AXE" -> "铁斧";
            case "IRON_SHOVEL" -> "铁锹";
            case "ENCHANTED_BOOK" -> "附魔书";
            case "NAME_TAG" -> "命名牌";
            case "SADDLE" -> "鞍";
            case "NAUTILUS_SHELL" -> "鹦鹉螺壳";
            case "LILY_PAD" -> "睡莲";
            case "BOW" -> "弓";
            case "FISHING_ROD" -> "钓鱼竿";
            case "ELYTRA" -> "鞘翅";
            case "TRIDENT" -> "三叉戟";
            case "SHIELD" -> "盾牌";
            default -> material;
        };
    }

    /**
     * 构建玩家数据文本段落
     */
    public String buildSummarySection(SummaryStats stats, PlayerVanillaStats vanillaStats) {
        StringBuilder sb = new StringBuilder("【玩家数据】");

        if (vanillaStats != null) {
            // === 基础/战斗 ===
            sb.append("\n游戏时长: ").append(vanillaStats.playMinutes()).append(" 分钟");
            sb.append("\n死亡: ").append(vanillaStats.deaths()).append(" 次");
            sb.append("\n击杀生物: ").append(vanillaStats.mobKills()).append(" 只");
            if (vanillaStats.playerKills() > 0) {
                sb.append("\n击杀玩家: ").append(vanillaStats.playerKills()).append(" 人");
            }
            if (vanillaStats.damageDealt() > 0) {
                sb.append("\n造成伤害: ").append(vanillaStats.damageDealt() / 2).append(" 颗心");
            }
            if (vanillaStats.damageTaken() > 0) {
                sb.append("\n承受伤害: ").append(vanillaStats.damageTaken() / 2).append(" 颗心");
            }
            if (vanillaStats.damageShielded() > 0) {
                sb.append("\n盾牌格挡: ").append(vanillaStats.damageShielded() / 2).append(" 颗心");
            }
            if (vanillaStats.animalsBred() > 0) {
                sb.append("\n繁殖动物: ").append(vanillaStats.animalsBred()).append(" 次");
            }
            if (vanillaStats.jumps() > 0) {
                sb.append("\n跳跃: ").append(vanillaStats.jumps()).append(" 次");
            }
            if (vanillaStats.sleepCount() > 0) {
                sb.append("\n睡觉: ").append(vanillaStats.sleepCount()).append(" 次");
            }

            // === 稀有BOSS（只在有数据时输出） ===
            if (vanillaStats.dragonKills() > 0 || vanillaStats.dragonDeaths() > 0) {
                sb.append("\n击杀末影龙: ").append(vanillaStats.dragonKills()).append(" 次，被末影龙击杀: ").append(vanillaStats.dragonDeaths()).append(" 次");
            }
            if (vanillaStats.witherKills() > 0 || vanillaStats.witherDeaths() > 0) {
                sb.append("\n击杀凋零: ").append(vanillaStats.witherKills()).append(" 次，被凋零击杀: ").append(vanillaStats.witherDeaths()).append(" 次");
            }
            if (vanillaStats.elderGuardianKills() > 0) {
                sb.append("\n击杀远古守卫者: ").append(vanillaStats.elderGuardianKills()).append(" 次");
            }
            if (vanillaStats.wardenKills() > 0) {
                sb.append("\n击杀监守者: ").append(vanillaStats.wardenKills()).append(" 次");
            }
            if (vanillaStats.ironGolemKills() > 0) {
                sb.append("\n击杀铁傀儡: ").append(vanillaStats.ironGolemKills()).append(" 次");
            }

            // === 探索/距离（统一输出格数：1格 = 100cm） ===
            if (vanillaStats.walkCm() > 0) {
                sb.append("\n行走距离: ").append(vanillaStats.walkCm() / 100).append(" 格");
            }
            if (vanillaStats.sprintCm() > 0) {
                sb.append("\n疾跑距离: ").append(vanillaStats.sprintCm() / 100).append(" 格");
            }
            if (vanillaStats.flyCm() > 0) {
                sb.append("\n飞行距离: ").append(vanillaStats.flyCm() / 100).append(" 格");
            }
            if (vanillaStats.elytraCm() > 0) {
                sb.append("\n鞘翅飞行距离: ").append(vanillaStats.elytraCm() / 100).append(" 格");
            }
            if (vanillaStats.swimCm() > 0) {
                sb.append("\n游泳距离: ").append(vanillaStats.swimCm() / 100).append(" 格");
            }
            if (vanillaStats.boatCm() > 0) {
                sb.append("\n划船距离: ").append(vanillaStats.boatCm() / 100).append(" 格");
            }
            if (vanillaStats.minecartCm() > 0) {
                sb.append("\n矿车行驶距离: ").append(vanillaStats.minecartCm() / 100).append(" 格");
            }
            if (vanillaStats.horseCm() > 0) {
                sb.append("\n骑马距离: ").append(vanillaStats.horseCm() / 100).append(" 格");
            }
            if (vanillaStats.climbCm() > 0) {
                sb.append("\n攀爬距离: ").append(vanillaStats.climbCm() / 100).append(" 格");
            }
            if (vanillaStats.fallCm() > 0) {
                sb.append("\n摔落距离: ").append(vanillaStats.fallCm() / 100).append(" 格");
            }

            // === 生活/趣味 ===
            if (vanillaStats.fishCaught() > 0) {
                sb.append("\n钓鱼: ").append(vanillaStats.fishCaught()).append(" 次");
            }
            if (vanillaStats.enchantCount() > 0) {
                sb.append("\n附魔: ").append(vanillaStats.enchantCount()).append(" 次");
            }
            if (vanillaStats.raidTriggered() > 0) {
                sb.append("\n触发袭击: ").append(vanillaStats.raidTriggered()).append(" 次");
            }
            if (vanillaStats.raidWon() > 0) {
                sb.append("\n袭击胜利: ").append(vanillaStats.raidWon()).append(" 次");
            }
            if (vanillaStats.diamondOreMined() > 0) {
                sb.append("\n挖钻石矿: ").append(vanillaStats.diamondOreMined()).append(" 个");
            }
        }

        // 加入天数（来自 profile，Bukkit Stats 无对应项）
        if (stats != null && stats.daysSinceFirstLogin() > 0) {
            sb.append("\n加入服务器: ").append(stats.daysSinceFirstLogin()).append(" 天前");
        }

        // 上次游玩时长（原始分钟数，LLM自行换算）
        if (stats != null && stats.lastSessionDurationMs() > 0) {
            long lastSessionMin = TimeUnit.MILLISECONDS.toMinutes(stats.lastSessionDurationMs());
            if (lastSessionMin > 0) {
                sb.append("\n上次游玩时长: ").append(lastSessionMin).append(" 分钟");
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
            sb.append("\n【上次游玩亮点】\n").append(hj);
        }

        return sb.toString();
    }

    /**
     * 格式化时长（ms → 可读）
     */
    public String formatDuration(long ms) {
        if (ms <= 0) return "刚刚";

        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;

        if (hours > 24) {
            long days = hours / 24;
            return days + " 天";
        } else if (hours > 0) {
            return hours + " 小时" + (minutes > 0 ? " " + minutes + " 分钟" : "");
        } else if (minutes > 0) {
            return minutes + " 分钟";
        } else {
            return "刚刚";
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
            return days + " 天";
        } else if (hours > 0) {
            return hours + " 小时" + (mins > 0 ? " " + mins + " 分钟" : "");
        } else {
            return mins + " 分钟";
        }
    }
}
