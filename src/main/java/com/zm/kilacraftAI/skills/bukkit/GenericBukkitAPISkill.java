package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 通用 Bukkit API 执行器
 *
 * <p>根据元数据动态执行 Bukkit API</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-01
 */
public class GenericBukkitAPISkill implements Skill {

    private final BukkitAPIExecutor executor;

    public GenericBukkitAPISkill() {
        this.executor = new BukkitAPIExecutor();
    }

    @Override
    public String getName() {
        return "GenericBukkitAPI";
    }

    @Override
    public String getDescription() {
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        if (configManager != null && !configManager.getBukkitApiSkillDescription().isEmpty()) {
            return configManager.getBukkitApiSkillDescription();
        }
        return null;
    }

    @Override
    public Map<String, String> getActions() {
        // 返回所有可用的 Bukkit API 及其描述
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        if (configManager != null) {
            Map<String, String> actions = new java.util.HashMap<>();
            for (BukkitAPIMetadata api : configManager.getBukkitApiMap().values()) {
                // 构建描述，同时包含displayName和description
                StringBuilder desc = new StringBuilder();

                // 优先使用displayName作为标题，然后添加description作为详细说明
                if (api.getDisplayName() != null && !api.getDisplayName().isEmpty()) {
                    desc.append(api.getDisplayName());
                    // 如果description不为空且与displayName不同，则添加详细说明
                    if (api.getDescription() != null && !api.getDescription().isEmpty() && !api.getDescription().equals(api.getDisplayName())) {
                        desc.append("：").append(api.getDescription());
                    }
                } else {
                    desc.append(api.getDescription());
                }

                // 添加使用场景
                if (api.getUsageScenarios() != null && !api.getUsageScenarios().isEmpty()) {
                    desc.append(" 使用场景：");
                    for (String scenario : api.getUsageScenarios()) {
                        desc.append(" - ").append(scenario);
                    }
                }

                actions.put(api.getId(), desc.toString());
            }
            return actions;
        }
        return java.util.Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        // 从 SkillConfigManager 获取全局 hints
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        if (configManager != null) {
            List<String> globalHints = configManager.getBukkitApiGlobalHints();
            if (globalHints != null && !globalHints.isEmpty()) {
                return new ArrayList<>(globalHints);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        Map<String, String> entities = context.getEntities();

        // 从 SkillConfigManager 获取 API 元数据
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        if (configManager == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("SkillConfigManager 未初始化"));
        }

        // 查找对应的 API 元数据
        BukkitAPIMetadata api = configManager.getBukkitApiMap().get(action);
        if (api == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("未找到 API：" + action));
        }

        try {
            // 执行 API 调用
            Object result = executor.execute(api, context.getPlayer(), entities);

            // 格式化结果
            String formatted = formatResult(api, result, context.getPlayer());

            // 构建返回结果
            var dataMap = new HashMap<String, Object>();
            dataMap.put("raw_result", result);
            dataMap.put("api_id", api.getId());

            // 对于 ItemStack 类型，额外添加 item_name 和 item_amount 字段
            // 供后续步骤通过 {step_x.item_name} 引用
            if (result instanceof org.bukkit.inventory.ItemStack itemStack) {
                if (itemStack.getType() != org.bukkit.Material.AIR) {
                    String itemName;
                    if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                        itemName = itemStack.getItemMeta().getDisplayName();
                    } else {
                        itemName = itemStack.getType().name();
                    }
                    dataMap.put("item_name", itemName);
                    dataMap.put("item_amount", itemStack.getAmount());
                }
            }

            return CompletableFuture.completedFuture(SkillResult.success(formatted, dataMap));
        } catch (Exception e) {
            KilacraftAI.getInstance().getLogger().log(Level.SEVERE, "执行 Bukkit API 失败：" + api.getId(), e);
            return CompletableFuture.completedFuture(SkillResult.failure("执行失败：" + e.getMessage()));
        }
    }

    /**
     * 格式化执行结果
     */
    private String formatResult(BukkitAPIMetadata api, Object result, org.bukkit.entity.Player player) {
        switch (result) {
            case null -> {
                return "无结果";
            }

            // 特殊类型处理（method_chain 模式返回的复杂对象）
            case org.bukkit.Location location -> {
                return formatLocation(location);
            }
            case org.bukkit.inventory.ItemStack itemStack -> {
                return formatItemStack(itemStack);
            }
            case org.bukkit.GameMode gameMode -> {
                return formatGameMode(gameMode);
            }
            // 在线玩家列表
            case java.util.Collection<?> collection -> {
                return formatPlayerCollection(collection);
            }
            // 世界时间（刻数）
            case Long ticks when api.getId().equals("get_world_time") -> {
                return formatGameTime(ticks);
            }
            case Integer ticks when api.getId().equals("get_world_time") -> {
                return formatGameTime(ticks.longValue());
            }

            // additional_methods 模式返回的 Map
            case java.util.Map<?, ?> resultMap -> {
                return formatWithAdditionalMethods(api, resultMap);
            }

            default -> {
            }
        }

        // 使用模板格式化（旧逻辑，兼容单个值返回）
        String template = api.getResultTemplate();
        if (template != null && !template.isEmpty()) {
            // 替换通用占位符 {result}
            template = template.replace("{result}", result.toString());

            // 替换特定属性占位符（基于反射）
            template = replacePropertyPlaceholders(template, result, api, player);

            return template;
        }

        // 默认直接返回 toString
        return result.toString();
    }

    /**
     * 格式化 additional_methods 返回的结果
     */
    private String formatWithAdditionalMethods(BukkitAPIMetadata api, java.util.Map<?, ?> resultMap) {
        String template = api.getResultTemplate();
        if (template == null || template.isEmpty()) {
            // 如果没有模板，返回所有结果的字符串表示
            StringBuilder sb = new StringBuilder();
            resultMap.forEach((key, value) -> {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(key).append(": ").append(value != null ? value.toString() : "null");
            });
            return sb.toString();
        }

        // 特殊处理：天气 API 需要根据 boolean 组合生成描述
        if ("get_weather".equals(api.getId())) {
            return formatWeatherResult(resultMap);
        }

        // 特殊处理：经验值 API 需要将进度转为百分比
        if ("get_player_exp".equals(api.getId())) {
            return formatExpResult(resultMap);
        }

        // 特殊处理：世界时间 API 需要将刻数转为可读时间
        if ("get_world_time".equals(api.getId())) {
            return formatTimeResult(resultMap);
        }

        // 替换模板中的占位符
        for (Map.Entry<?, ?> entry : resultMap.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            String placeholder = "{" + key + "}";
            String replacement = value != null ? value.toString() : "N/A";
            template = template.replace(placeholder, replacement);
        }

        return template;
    }

    /**
     * 格式化天气结果（特殊处理）
     */
    private String formatWeatherResult(java.util.Map<?, ?> resultMap) {
        Boolean hasStorm = (Boolean) resultMap.get("has_storm");
        Boolean isThundering = (Boolean) resultMap.get("is_thundering");

        if (hasStorm == null || !hasStorm) {
            return "天气：晴朗";
        } else if (isThundering != null && isThundering) {
            return "天气：雷暴";
        } else {
            return "天气：雨天";
        }
    }

    /**
     * 格式化经验值结果
     */
    private String formatExpResult(java.util.Map<?, ?> resultMap) {
        Object levelObj = resultMap.get("level");
        Object expObj = resultMap.get("exp_progress");

        int level = levelObj instanceof Number ? ((Number) levelObj).intValue() : 0;
        float expProgress = expObj instanceof Number ? ((Number) expObj).floatValue() : 0f;

        // 将经验进度转为百分比
        int percentage = Math.round(expProgress * 100);
        return String.format("等级：%d，经验进度：%d%%", level, percentage);
    }

    /**
     * 格式化世界时间结果
     */
    private String formatTimeResult(java.util.Map<?, ?> resultMap) {
        return "该 API 不应返回 Map 类型";
    }

    /**
     * 格式化游戏时间（刻数转可读时间）
     */
    private String formatGameTime(long ticks) {
        // MC 中 24000 刻 = 1 天
        // 0 刻 = 日出（6:00）
        // 6000 刻 = 正午（12:00）
        // 12000 刻 = 日落（18:00）
        // 18000 刻 = 午夜（0:00）

        long dayTicks = ticks % 24000;
        if (dayTicks < 0) dayTicks += 24000;

        // 转换为小时和分钟（MC 中 1 小时 = 1000 刻，1 分钟 = 16.67 刻）
        int hours = (int) ((dayTicks / 1000.0 + 6) % 24);  // +6 因为 0 刻 = 6:00
        int minutes = (int) ((dayTicks % 1000) * 60 / 1000.0);

        return String.format("游戏时间：%02d:%02d", hours, minutes);
    }

    /**
     * 格式化在线玩家列表
     */
    private String formatPlayerCollection(java.util.Collection<?> players) {
        if (players == null || players.isEmpty()) {
            return "当前没有玩家在线";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("在线玩家（").append(players.size()).append("人）：\n");

        int count = 0;
        for (Object obj : players) {
            if (obj instanceof org.bukkit.entity.Player player) {
                if (count > 0) sb.append(", ");
                sb.append(player.getName());
                count++;
                // 最多显示 20 个玩家名
                if (count >= 20) {
                    int remaining = players.size() - count;
                    if (remaining > 0) {
                        sb.append(" ... 等 ").append(remaining).append(" 人");
                    }
                    break;
                }
            }
        }
        return sb.toString();
    }

    /**
     * 替换模板中的属性占位符（如 {health}, {maxHealth} 等）
     *
     * @param template 原始模板
     * @param result   返回值对象
     * @param api      API 元数据
     * @param player   玩家对象
     * @return 替换后的模板
     */
    private String replacePropertyPlaceholders(String template, Object result, BukkitAPIMetadata api, org.bukkit.entity.Player player) {
        if (result == null || api.getAdditionalMethods() == null || api.getAdditionalMethods().isEmpty()) {
            return template;
        }

        // 查找所有 {xxx} 格式的占位符
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{(\\w+)}");
        java.util.regex.Matcher matcher = pattern.matcher(template);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String value = null;

            // 1. 尝试从额外方法中获取（在 player 上调用）
            if (api.getAdditionalMethods().containsKey(placeholderName)) {
                String methodName = api.getAdditionalMethods().get(placeholderName);
                value = invokeMethodOnTarget(player, methodName);
            }

            // 替换占位符
            if (value != null) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
            } else {
                // 保留原始占位符
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 在目标对象上调用方法
     */
    private String invokeMethodOnTarget(Object target, String methodName) {
        try {
            if (target == null) {
                return null;
            }

            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化 Location
     */
    private String formatLocation(org.bukkit.Location loc) {
        return String.format("位置：X=%.2f, Y=%.2f, Z=%.2f, 世界=%s", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "未知");
    }

    /**
     * 格式化 ItemStack
     */
    private String formatItemStack(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return "空手";
        }

        StringBuilder sb = new StringBuilder();

        // 优先使用自定义名称（displayName）
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            sb.append("物品：").append(displayName);
        } else {
            // 没有自定义名称，使用原版物品名称并翻译成中文
            String englishName = item.getType().name();
            String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(englishName);
            sb.append("物品：").append(chineseName);
        }

        if (item.getAmount() > 1) {
            sb.append(" x").append(item.getAmount());
        }

        return sb.toString();
    }

    /**
     * 格式化游戏模式
     */
    private String formatGameMode(org.bukkit.GameMode gameMode) {
        String displayName = switch (gameMode) {
            case SURVIVAL -> "生存模式";
            case CREATIVE -> "创造模式";
            case ADVENTURE -> "冒险模式";
            case SPECTATOR -> "旁观模式";
        };
        return "游戏模式：" + displayName;
    }
}
