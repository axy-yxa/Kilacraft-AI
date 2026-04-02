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
            case org.bukkit.World.Environment environment -> {
                return formatEnvironment(environment);
            }
            case org.bukkit.Difficulty difficulty -> {
                return formatDifficulty(difficulty);
            }
            case org.bukkit.entity.Pose pose -> {
                return formatPose(pose);
            }
            case org.bukkit.inventory.MainHand mainHand -> {
                return formatMainHand(mainHand);
            }
            case org.bukkit.util.Vector vector -> {
                return formatVector(vector);
            }
            // Duration 类型（Paper 特有，用于 AFK 时间）
            case java.time.Duration duration -> {
                return formatDuration(duration);
            }
            // 在线玩家列表或世界列表
            case java.util.Collection<?> collection -> {
                return formatCollection(collection, api);
            }
            // 世界时间（刻数）
            case Long ticks when api.getId().equals("get_world_time") -> {
                return formatGameTime(ticks);
            }
            case Integer ticks when api.getId().equals("get_world_time") -> {
                return formatGameTime(ticks.longValue());
            }
            // 世界种子
            case Long seed when api.getId().equals("get_world_seed") -> {
                return "世界种子：" + seed;
            }
            // 飞行速度/行走速度（Float 类型）
            case Float speed when api.getId().contains("speed") -> {
                return String.format("速度：%.2f", speed);
            }
            // 攻击冷却（Float 类型，0-1）
            case Float cooldown when api.getId().equals("get_player_attack_cooldown") -> {
                int percentage = Math.round(cooldown * 100);
                return "攻击冷却进度：" + percentage + "%";
            }
            // 客户端视距
            case Integer viewDistance when api.getId().contains("view_distance") -> {
                return "视距：" + viewDistance + " 区块";
            }
            // Ping 延迟
            case Integer ping when api.getId().equals("get_player_ping") -> {
                String quality = ping < 100 ? "极好" : (ping < 200 ? "良好" : (ping < 300 ? "一般" : "较差"));
                return "延迟：" + ping + "ms (" + quality + ")";
            }
            // 累计总经验
            case Integer totalExp when api.getId().equals("get_player_total_exp") -> {
                return "累计总经验：" + totalExp + " 点";
            }
            // 升到下一级所需经验（Bukkit 原生方法）
            case Integer expNeeded when api.getId().equals("get_player_exp_to_level") -> {
                return "升到下一级需要：" + expNeeded + " 点经验";
            }
            // 服务器平均 tick 时间（Paper 特有）
            case Double tickTime when api.getId().equals("get_server_average_tick_time") -> {
                String status = tickTime < 50 ? "流畅" : (tickTime < 100 ? "轻微延迟" : "严重延迟");
                return String.format("平均 Tick 时间：%.2fms (%s)", tickTime, status);
            }

            // additional_methods 模式返回的 Map
            case java.util.Map<?, ?> resultMap -> {
                return formatWithAdditionalMethods(api, resultMap);
            }

            // Boolean 类型（如 isInsideVehicle、getAllowFlight 等）
            case Boolean bool -> {
                return bool ? "是" : "否";
            }

            // 其他类型默认返回 toString
            default -> {
                return result.toString();
            }
        }
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
                sb.append(key).append(": ").append(formatMapValue(value));
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

        // 特殊处理：着火状态 API 需要根据 fire_ticks 判断是否着火
        if ("get_player_fire_status".equals(api.getId())) {
            return formatFireResult(resultMap);
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
            String replacement = formatMapValue(value);
            template = template.replace(placeholder, replacement);
        }

        return template;
    }

    /**
     * 格式化 Map 中的值（处理特殊类型）
     */
    private String formatMapValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        // 处理特殊类型
        if (value instanceof org.bukkit.World.Environment env) {
            return formatEnvironment(env);
        }
        if (value instanceof org.bukkit.Difficulty diff) {
            return formatDifficulty(diff);
        }
        if (value instanceof Boolean bool) {
            return bool ? "是" : "否";
        }
        if (value instanceof Float || value instanceof Double) {
            return String.format("%.2f", ((Number) value).doubleValue());
        }
        return value.toString();
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
     * 格式化着火状态结果
     */
    private String formatFireResult(java.util.Map<?, ?> resultMap) {
        Object fireTicksObj = resultMap.get("fire_ticks");
        Object maxFireTicksObj = resultMap.get("max_fire_ticks");

        int fireTicks = fireTicksObj instanceof Number ? ((Number) fireTicksObj).intValue() : 0;
        int maxFireTicks = maxFireTicksObj instanceof Number ? ((Number) maxFireTicksObj).intValue() : 200;

        if (fireTicks <= 0) {
            return "着火状态：未着火";
        }

        // 将 tick 转换为秒（20 tick = 1 秒）
        double seconds = fireTicks / 20.0;
        return String.format("着火状态：正在燃烧！剩余 %.1f 秒 (%d/%d tick)", seconds, fireTicks, maxFireTicks);
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
     * 格式化集合类型（区分玩家列表和世界列表）
     */
    private String formatCollection(java.util.Collection<?> collection, BukkitAPIMetadata api) {
        if (collection == null || collection.isEmpty()) {
            // 根据API类型返回不同的空消息
            if (api.getId().equals("get_server_worlds")) {
                return "服务器暂无已加载的世界";
            }
            return "当前没有玩家在线";
        }

        // 判断集合元素类型
        Object first = collection.iterator().next();
        
        if (first instanceof org.bukkit.entity.Player) {
            return formatPlayerCollection(collection);
        } else if (first instanceof org.bukkit.World) {
            return formatWorldCollection(collection);
        }
        
        // 未知类型，默认输出
        return collection.toString();
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
     * 格式化世界列表
     */
    private String formatWorldCollection(java.util.Collection<?> worlds) {
        StringBuilder sb = new StringBuilder();
        sb.append("服务器世界列表（").append(worlds.size()).append("个）：\n");

        int count = 0;
        for (Object obj : worlds) {
            if (obj instanceof org.bukkit.World world) {
                if (count > 0) sb.append(", ");
                sb.append(world.getName());
                sb.append("(").append(formatEnvironment(world.getEnvironment())).append(")");
                count++;
            }
        }
        return sb.toString();
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

    /**
     * 格式化世界环境类型
     */
    private String formatEnvironment(org.bukkit.World.Environment environment) {
        String displayName = switch (environment) {
            case NORMAL -> "主世界";
            case NETHER -> "下界";
            case THE_END -> "末地";
            case CUSTOM -> "自定义";
        };
        return displayName;
    }

    /**
     * 格式化游戏难度
     */
    private String formatDifficulty(org.bukkit.Difficulty difficulty) {
        String displayName = switch (difficulty) {
            case PEACEFUL -> "和平";
            case EASY -> "简单";
            case NORMAL -> "普通";
            case HARD -> "困难";
        };
        return displayName;
    }

    /**
     * 格式化玩家姿势
     */
    private String formatPose(org.bukkit.entity.Pose pose) {
        String displayName = switch (pose) {
            case STANDING -> "站立";
            case FALL_FLYING -> "鞘翅飞行";
            case SLEEPING -> "睡觉";
            case SWIMMING -> "游泳";
            case SPIN_ATTACK -> "旋转攻击";
            case SNEAKING -> "潜行";
            case DYING -> "死亡";
            default -> pose.name().toLowerCase();
        };
        return "当前姿势：" + displayName;
    }

    /**
     * 格式化主手偏好
     */
    private String formatMainHand(org.bukkit.inventory.MainHand mainHand) {
        String displayName = switch (mainHand) {
            case LEFT -> "左手（左撇子）";
            case RIGHT -> "右手（右撇子）";
        };
        return "主手偏好：" + displayName;
    }

    /**
     * 格式化速度向量
     */
    private String formatVector(org.bukkit.util.Vector vector) {
        double speed = vector.length();
        
        // 判断是否静止（阈值设为 0.1，因为 MC 中站立时也有微小的重力影响）
        if (speed < 0.1) {
            return "移动状态：静止";
        }
        
        // 获取各方向的速度分量
        double vx = vector.getX();
        double vy = vector.getY();
        double vz = vector.getZ();
        
        // 构建移动方向描述
        java.util.List<String> directions = new java.util.ArrayList<>();
        
        // 水平方向（阈值设为 0.05）
        if (Math.abs(vx) > 0.05) {
            directions.add(vx > 0 ? "东" : "西");
        }
        if (Math.abs(vz) > 0.05) {
            directions.add(vz > 0 ? "南" : "北");
        }
        
        // 垂直方向（阈值设为 0.05，区分明显的上升/下降）
        if (Math.abs(vy) > 0.05) {
            directions.add(vy > 0 ? "上升" : "下降");
        }
        
        String directionStr = directions.isEmpty() ? "静止" : String.join("、", directions);
        
        return String.format("移动状态：%s，速度：%.2f", directionStr, speed);
    }

    /**
     * 格式化时长（用于 AFK 时间）
     */
    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return "挂机时间：" + seconds + " 秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return "挂机时间：" + minutes + " 分 " + remainingSeconds + " 秒";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return "挂机时间：" + hours + " 小时 " + minutes + " 分";
        }
    }
}
