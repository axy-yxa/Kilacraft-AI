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
            1. 根据离线事件自然地提及重要的事，没有则跳过，不要编造
            2. 根据好友动态提及好友的重要动态（如好友击杀了BOSS、完成了袭击、宠物战死、被雷劈等趣事），没有则跳过
            3. 在线好友列表为空时，绝对不要提及任何好友相关内容，不要说"好友们在忙"之类的话
            4. 玩家数据中只有出现有趣的数值（如666、888等吉利数字、整数里程碑、顺子数字）时才提及，无意义的零值（如0小时、0天）必须忽略；上次游玩亮点若有则提及，没有则跳过
            5. 如果离线时间很短（如"刚刚"），不要说"好久不见"或"休息了很久"之类的话，简单打招呼即可
            6. 不超过 200 个汉字，像朋友聊天一样自然
            7. 不要列举所有事件，挑选最有价值的 1-5 件提及
            8. 上次位置仅供参考，除非在特殊世界（末地/下界），否则不要提及""";

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
        String ownEventsSection = buildOfflineEventsSection(context.getOfflineEvents());
        String friendEventsSection = buildFriendEventsSection(context.getFriendEvents());
        String onlineFriendsSection = buildOnlineFriendsSection(context.getOnlineFriends());
        String lastLocationSection = buildLastLocationSection(context.getProfile());
        String summarySection = buildSummarySection(context.getSummaryStats());

        return customPrompt.replace("{player}", playerName).replace("{offline_duration}", offlineDuration).replace("{own_events_section}", ownEventsSection).replace("{friend_events_section}", friendEventsSection).replace("{online_friends_section}", onlineFriendsSection).replace("{last_location}", lastLocationSection).replace("{summary_section}", summarySection);
    }

    /**
     * 构建离线事件文本段落
     */
    public String buildOfflineEventsSection(List<ServerEvent> events) {
        if (events == null || events.isEmpty()) {
            return "【离线期间发生的事】\n没有特别的事情发生。";
        }

        Map<ServerEventType, List<ServerEvent>> grouped = events.stream().collect(Collectors.groupingBy(ServerEvent::getEventType));

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("【离线期间发生的事】");

        for (Map.Entry<ServerEventType, List<ServerEvent>> entry : grouped.entrySet()) {
            String summary = summarizeEventType(entry.getKey(), entry.getValue());
            joiner.add(summary);
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
                yield count == 1 ? "你收到了一笔款项" + appendDataSuffix(events.get(0)) : "你收到了 " + count + " 笔款项";
            }
            case PLAYER_DEATH -> "你挂了 " + events.size() + " 次";
            case PLAYER_ADVANCEMENT -> "你达成了 " + events.size() + " 个成就";
            case PLAYER_LEVEL_UP -> "你升了 " + events.size() + " 级";
            case PLAYER_USE_TOTEM -> "你触发了不死图腾" + (data.isEmpty() ? "" : "（" + formatDamageCause(data) + "）");
            case PLAYER_DEFEAT_BOSS -> "你击杀了 " + formatEntityName(data);
            case PLAYER_COMPLETE_RAID -> "你完成了袭击（" + data + "）";
            case PLAYER_PET_DEATH -> "你的宠物战死了（" + formatPetDeathData(data) + "）";
            case PLAYER_PVP_KILL -> "你在PVP中击杀了 " + data;
            case PLAYER_TOOL_BREAK -> "你的" + formatMaterialName(data) + " 断了";
            case PLAYER_CATCH_TREASURE -> "你钓到了 " + formatMaterialName(data);
            case PLAYER_LIGHTNING_STRIKE -> "你被雷劈了";
            case PLAYER_CURE_VILLAGER -> "你救了一个僵尸村民";
            default -> type.getDescription() + " x" + events.size();
        };
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
     */
    public String buildOnlineFriendsSection(List<String> onlineFriends) {
        if (onlineFriends == null || onlineFriends.isEmpty()) {
            return "【目前在线的好友】\n暂无好友在线。";
        }
        return "【目前在线的好友】\n" + String.join("、", onlineFriends);
    }

    /**
     * 构建好友动态文本段落（分类二：好友动态）
     */
    public String buildFriendEventsSection(List<ServerEvent> friendEvents) {
        if (friendEvents == null || friendEvents.isEmpty()) {
            return "【好友动态】\n好友们最近没什么特别的事。";
        }

        // 按玩家名分组
        Map<String, List<ServerEvent>> byPlayer = new LinkedHashMap<>();
        for (ServerEvent e : friendEvents) {
            String name = e.getPlayerName() != null ? e.getPlayerName() : "某位好友";
            byPlayer.computeIfAbsent(name, k -> new ArrayList<>()).add(e);
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("【好友动态】");
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
        return joiner.toString();
    }

    /**
     * 生成单个好友的事件摘要
     */
    private String summarizeFriendEvent(String playerName, ServerEventType type, List<ServerEvent> events) {
        // 大多数好友动态事件只有1条，取第一条的 data 作为详情
        String data = events.get(0).getData() != null ? events.get(0).getData() : "";
        return switch (type) {
            case PLAYER_DEATH -> playerName + " 挂了 " + events.size() + " 次";
            case PLAYER_ADVANCEMENT -> playerName + " 达成了 " + events.size() + " 个成就";
            case PLAYER_LEVEL_UP -> playerName + " 升了 " + events.size() + " 级";
            case PLAYER_USE_TOTEM -> playerName + " 触发了不死图腾（" + data + "）";
            case PLAYER_DEFEAT_BOSS -> playerName + " 击杀了 " + formatEntityName(data);
            case PLAYER_COMPLETE_RAID -> playerName + " 完成了袭击（" + data + "）";
            case PLAYER_PET_DEATH -> playerName + " 的宠物战死了（" + formatPetDeathData(data) + "）";
            case PLAYER_PVP_KILL -> playerName + " 在PVP中击杀了 " + data;
            case PLAYER_TOOL_BREAK -> playerName + " 的" + formatMaterialName(data) + " 断了";
            case PLAYER_CATCH_TREASURE -> playerName + " 钓到了 " + formatMaterialName(data);
            case PLAYER_LIGHTNING_STRIKE -> playerName + " 被雷劈了";
            case PLAYER_CURE_VILLAGER -> playerName + " 救了一个僵尸村民";
            default -> playerName + " 触发了 " + events.size() + " 次 " + type.getDescription();
        };
    }

    /**
     * 实体类型名称友好化（如 ENDER_DRAGON → 末影龙）
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

        // 提取杀手信息 [...]
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
     * 物品类型名称友好化（如 DIAMOND_PICKAXE → 钻石镐）
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
     * 构建玩家数据文本段落（分类三：其他摘要）
     */
    public String buildSummarySection(SummaryStats stats) {
        if (stats == null) return "";

        long totalHours = TimeUnit.MILLISECONDS.toHours(stats.totalPlaytimeMs());

        StringBuilder sb = new StringBuilder("【玩家数据】");
        sb.append("\n累计在线时长: ").append(totalHours).append(" 小时");
        sb.append("\n累计登录: ").append(stats.loginCount()).append(" 次");
        sb.append("\n加入服务器: ").append(stats.daysSinceFirstLogin()).append(" 天前");

        // 上次游玩亮点
        List<ServerEvent> highlights = stats.lastSessionHighlights();
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
}
