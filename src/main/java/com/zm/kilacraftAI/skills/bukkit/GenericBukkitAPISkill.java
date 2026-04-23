package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.util.PluginLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        SkillConfigManager configManager = SkillConfigManager.getInstance();
        if (configManager != null) {
            Map<String, String> actions = new java.util.HashMap<>();
            for (BukkitAPIMetadata api : configManager.getBukkitApiMap().values()) {
                // displayName + description
                String displayName = api.getDisplayName();
                String desc = api.getDescription();
                String value;
                if (displayName != null && !displayName.isEmpty()) {
                    value = desc != null && !desc.isEmpty() && !desc.equals(displayName) ? displayName + "：" + desc : displayName;
                } else {
                    value = desc != null ? desc : "";
                }
                actions.put(api.getId(), value);
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
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未找到 API：{}", action)));
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

            // 根据 API 配置自动提取 data 字段
            // 对于 additional_methods 模式：自动提取所有方法返回值
            // 对于 method_chain + lophine 模式：executor 返回的 Map 也走此路径
            if (result instanceof java.util.Map<?, ?> resultMap) {
                for (Map.Entry<?, ?> entry : resultMap.entrySet()) {
                    String key = entry.getKey().toString();
                    Object value = entry.getValue();
                    // 添加基本类型和可安全序列化的容器类型（Map/Collection）
                    if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                        dataMap.put(key, value);
                    } else if (value instanceof java.util.Map<?, ?> || value instanceof java.util.Collection<?>) {
                        // lophine ItemStack 提取的 enchantments(Map)、lore(List) 等容器类型
                        // Gson 可正常序列化，允许加入 dataMap
                        dataMap.put(key, value);
                    }
                }
            }
            // 对于 method_chain 模式：根据返回类型自动提取常用字段
            else if (result != null) {
                extractDataFromResult(api, result, dataMap);
            }

            // 通用语义化字段注入：如果配置了 data_field，将主结果值以该字段名注入 dataMap
            // 使 LLM 可通过 {step_x.field_name} 引用，而非只有 raw_result
            String dataField = api.getDataField();
            if (dataField != null && !dataField.isEmpty() && result != null) {
                // 避免与 additional_methods 自动提取的字段冲突
                if (!dataMap.containsKey(dataField)) {
                    // 对简单标量直接注入
                    if (result instanceof Number || result instanceof Boolean || result instanceof String) {
                        dataMap.put(dataField, result);
                    } else if (result instanceof java.util.Collection<?> collection) {
                        // 集合类型注入元素数量（如在线玩家数、世界数）
                        dataMap.put(dataField, collection.size());
                    } else {
                        // enum 等其他类型，转 String 注入
                        dataMap.put(dataField, result.toString());
                    }
                }
            }

            // 打开容器 API 的额外元数据注入（需要 player 对象，extractDataFromResult 没有）
            if ("get_player_open_container".equals(api.getId()) && context.getPlayer() != null) {
                try {
                    var openInv = context.getPlayer().getOpenInventory();
                    dataMap.put("container_type", openInv.getType().name());
                    dataMap.put("container_title", openInv.getTitle());
                } catch (Exception ignored) {
                    // 异步环境下可能无法访问
                }
            }

            return CompletableFuture.completedFuture(SkillResult.success(formatted, dataMap));
        } catch (Exception e) {
            PluginLogger.error("BukkitAPI", I18nService.tr("执行 Bukkit API 失败：{}", api.getId()), e);
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("执行失败：{}", e.getMessage())));
        }
    }

    /**
     * 从复杂对象中自动提取常用字段到 dataMap
     */
    private void extractDataFromResult(BukkitAPIMetadata api, Object result, Map<String, Object> dataMap) {
        // ItemStack：提取物品信息（与 BukkitAPIExecutor.extractThreadSafeData 保持字段名一致）
        if (result instanceof org.bukkit.inventory.ItemStack itemStack) {
            if (itemStack.getType() != org.bukkit.Material.AIR) {
                dataMap.put("item_type", itemStack.getType().name());
                dataMap.put("item_amount", itemStack.getAmount());

                // item_name 优先使用自定义名称，否则使用中文翻译
                String itemName;
                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                    itemName = itemStack.getItemMeta().getDisplayName();
                } else {
                    itemName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(itemStack.getType().name());
                }
                dataMap.put("item_name", itemName);

                // 提取 ItemMeta 详细信息
                if (itemStack.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();

                    // 附魔列表
                    if (meta.hasEnchants()) {
                        Map<String, Integer> enchantments = new HashMap<>();
                        meta.getEnchants().forEach((ench, level) -> enchantments.put(ench.getName(), level));
                        dataMap.put("enchantments", enchantments);
                    }

                    // 耐久度（损伤值）
                    if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                        if (damageable.hasDamage()) {
                            dataMap.put("damage", damageable.getDamage());
                            int maxDurability = itemStack.getType().getMaxDurability();
                            if (maxDurability > 0) {
                                dataMap.put("max_durability", maxDurability);
                                dataMap.put("remaining_durability", maxDurability - damageable.getDamage());
                            }
                        }
                        if (meta.isUnbreakable()) {
                            dataMap.put("unbreakable", true);
                        }
                    }

                    // Lore（物品描述）
                    if (meta.hasLore()) {
                        dataMap.put("lore", meta.getLore());
                    }

                    // 自定义模型数据
                    if (meta.hasCustomModelData()) {
                        dataMap.put("custom_model_data", meta.getCustomModelData());
                    }

                    // ItemFlags
                    if (!meta.getItemFlags().isEmpty()) {
                        dataMap.put("item_flags", meta.getItemFlags().stream().map(Enum::name).toList());
                    }
                }
            }
        }
        // Location：提取坐标信息
        else if (result instanceof org.bukkit.Location location) {
            dataMap.put("x", location.getX());
            dataMap.put("y", location.getY());
            dataMap.put("z", location.getZ());
            dataMap.put("yaw", location.getYaw());
            dataMap.put("pitch", location.getPitch());
            if (location.getWorld() != null) {
                dataMap.put("world", location.getWorld().getName());
            }
        }
        // Vector：提取向量分量
        else if (result instanceof org.bukkit.util.Vector vector) {
            dataMap.put("x", vector.getX());
            dataMap.put("y", vector.getY());
            dataMap.put("z", vector.getZ());
        }
        // ItemStack[]：盔甲装备数组
        else if (result instanceof org.bukkit.inventory.ItemStack[] armorContents && api != null && "get_player_armor".equals(api.getId())) {
            // Bukkit 盔甲数组顺序：[靴子, 护腿, 胸甲, 头盔]
            String[] slotNames = {"boots", "leggings", "chestplate", "helmet"};
            for (int i = 0; i < armorContents.length; i++) {
                org.bukkit.inventory.ItemStack item = armorContents[i];
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    String itemName = (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) ? item.getItemMeta().getDisplayName() : item.getType().name();
                    dataMap.put(slotNames[i] + "_name", itemName);
                    dataMap.put(slotNames[i] + "_type", item.getType().name());
                    dataMap.put(slotNames[i] + "_amount", item.getAmount());
                    // 附魔信息
                    if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                        Map<String, Integer> enchants = new java.util.HashMap<>();
                        item.getItemMeta().getEnchants().forEach((ench, level) -> enchants.put(ench.getKey().getKey().toUpperCase(), level));
                        dataMap.put(slotNames[i] + "_enchantments", enchants);
                    }
                    // 耐久度信息
                    if (item.getType().getMaxDurability() > 0) {
                        dataMap.put(slotNames[i] + "_max_durability", (int) item.getType().getMaxDurability());
                        dataMap.put(slotNames[i] + "_remaining_durability", (int) (item.getType().getMaxDurability() - item.getDurability()));
                    }
                }
            }
        }
        // ItemStack[]：背包占用格数（极轻量，只计数不读 ItemMeta）
        else if (result instanceof org.bukkit.inventory.ItemStack[] contents && api != null && "get_player_inventory_usage".equals(api.getId())) {
            extractInventoryUsage(contents, dataMap);
        }
        // ItemStack[]：背包物品摘要（名称+数量，不含附魔/耐久）
        else if (result instanceof org.bukkit.inventory.ItemStack[] contents && api != null && "get_player_inventory".equals(api.getId())) {
            extractInventoryUsage(contents, dataMap);
            extractInventorySummary(contents, dataMap);
        }
        // ItemStack[]：末影箱物品摘要
        else if (result instanceof org.bukkit.inventory.ItemStack[] contents && api != null && "get_player_ender_chest".equals(api.getId())) {
            extractInventoryUsage(contents, dataMap);
            extractInventorySummary(contents, dataMap);
        }
        // ItemStack[]：打开的容器物品摘要（容器元数据在 formatResult 中提取，因为 extractDataFromResult 没有 player 参数）
        else if (result instanceof org.bukkit.inventory.ItemStack[] contents && api != null && "get_player_open_container".equals(api.getId())) {
            extractInventoryUsage(contents, dataMap);
            extractInventorySummary(contents, dataMap);
        }
        // Collection<PotionEffect>：药水效果集合（仅对 get_player_potion_effects 生效）
        else if (result instanceof java.util.Collection<?> potionEffects && api != null && "get_player_potion_effects".equals(api.getId())) {
            java.util.List<Map<String, Object>> effectsList = new java.util.ArrayList<>();
            for (Object obj : potionEffects) {
                if (obj instanceof org.bukkit.potion.PotionEffect effect) {
                    Map<String, Object> effectData = new java.util.HashMap<>();
                    effectData.put("type", effect.getType().getName());
                    effectData.put("amplifier", effect.getAmplifier() + 1);
                    effectData.put("duration_seconds", effect.getDuration() / 20);
                    effectsList.add(effectData);
                }
            }
            dataMap.put("effects", effectsList);
            dataMap.put("effect_count", effectsList.size());
        }
        // Block：脚下方块（Spigot 路径，Folia 走 Map）
        else if (result instanceof org.bukkit.block.Block block && api != null && "get_player_feet_block".equals(api.getId())) {
            dataMap.put("block_type", block.getType().name());
            dataMap.put("x", block.getX());
            dataMap.put("y", block.getY());
            dataMap.put("z", block.getZ());
            block.getWorld();
            dataMap.put("world", block.getWorld().getName());
        }
        // EntityDamageEvent：上次受伤原因（Spigot 路径，Folia 走 Map）
        else if (result instanceof org.bukkit.event.entity.EntityDamageEvent damageEvent && api != null && "get_player_last_damage".equals(api.getId())) {
            dataMap.put("damage_cause", formatDamageCause(damageEvent.getCause()));
            dataMap.put("damage_amount", damageEvent.getDamage());
            if (damageEvent instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntityEvent) {
                org.bukkit.entity.Entity damager = byEntityEvent.getDamager();
                dataMap.put("damager_type", damager.getType().name());
                if (damager instanceof org.bukkit.entity.Player attacker) {
                    dataMap.put("damager_name", attacker.getName());
                } else if (damager instanceof org.bukkit.entity.LivingEntity livingMob) {
                    livingMob.getName();
                    dataMap.put("damager_name", livingMob.getName());
                }
            }
        }
        // 其他类型不提取额外字段，只保留 raw_result
        // Map 格式的数据由 execute() 方法中的通用逻辑处理
        // Collection 类型（如在线玩家、世界列表、袭击列表）由 data_field 机制统一注入 size
    }

    /**
     * 格式化执行结果
     */
    private String formatResult(BukkitAPIMetadata api, Object result, org.bukkit.entity.Player player) {
        if (result == null) {
            return I18nService.tr("无结果");
        }

        // 特殊类型处理（lophine 优化：已在区域线程内提取为 Map/String）
        // 背包/末影箱/打开容器 Map（必须在通用 item/location Map 分支之前）
        if (result instanceof java.util.Map<?, ?> inventoryMap && (api.getId().equals("get_player_inventory_usage") || api.getId().equals("get_player_inventory") || api.getId().equals("get_player_ender_chest") || api.getId().equals("get_player_open_container"))) {
            return formatInventoryFromMap(api, inventoryMap);
        }
        // 盔甲装备 Map（Paper 系异步/主线程可能返回 Map 而非 ItemStack[]）
        if (result instanceof java.util.Map<?, ?> armorMap && api.getId().equals("get_player_armor")) {
            return formatArmorFromMap(armorMap);
        }
        if (result instanceof java.util.Map<?, ?> locationMap && api.getId().contains("location")) {
            return formatLocationFromMap(locationMap);
        }
        if (result instanceof java.util.Map<?, ?> itemMap && api.getId().contains("item")) {
            return formatItemStackFromMap(api, itemMap);
        }
        if (result instanceof java.util.Map<?, ?> vectorMap && (api.getId().contains("velocity") || api.getId().contains("direction"))) {
            return formatVectorFromMap(vectorMap);
        }
        // 枚举类型（线程安全）
        if (result instanceof org.bukkit.GameMode gameMode) {
            return formatGameMode(gameMode);
        }
        if (result instanceof org.bukkit.World.Environment environment) {
            return formatEnvironment(environment);
        }
        if (result instanceof org.bukkit.Difficulty difficulty) {
            return formatDifficulty(difficulty);
        }
        if (result instanceof org.bukkit.entity.Pose pose) {
            return formatPose(pose);
        }
        if (result instanceof org.bukkit.inventory.MainHand mainHand) {
            return formatMainHand(mainHand);
        }
        // Duration 类型（Paper 特有，用于 AFK 时间）
        if (result instanceof java.time.Duration duration) {
            return formatDuration(duration);
        }
        // 药水效果集合（必须在通用 Collection 分支之前）
        if (result instanceof java.util.Collection<?> potionEffects && api.getId().equals("get_player_potion_effects")) {
            return formatPotionEffects(potionEffects);
        }
        // 袭击列表（必须在通用 Collection 分支之前）
        if (result instanceof java.util.Collection<?> raids && api.getId().equals("get_world_raids")) {
            return formatRaids(raids);
        }
        // 在线玩家列表或世界列表
        if (result instanceof java.util.Collection<?> collection) {
            return formatCollection(collection, api);
        }
        // 世界时间（刻数）
        if ((result instanceof Long || result instanceof Integer) && api.getId().equals("get_world_time")) {
            long ticks = result instanceof Long ? (Long) result : ((Integer) result).longValue();
            return formatGameTime(ticks);
        }
        // 世界种子
        if (result instanceof Long seed && api.getId().equals("get_world_seed")) {
            return I18nService.tr("世界种子：{}", seed);
        }
        // 飞行速度/行走速度（Float 类型）
        if (result instanceof Float speed && api.getId().contains("speed")) {
            return I18nService.tr("速度：{}", String.format("%.2f", speed));
        }
        // 攻击冷却（Float 类型，0-1）
        if (result instanceof Float cooldown && api.getId().equals("get_player_attack_cooldown")) {
            int percentage = Math.round(cooldown * 100);
            return I18nService.tr("攻击冷却进度：{}%", percentage);
        }
        // 吸收之心（Double/Float 类型，结构化输出）
        if ((result instanceof Double || result instanceof Float) && api.getId().equals("get_player_absorption")) {
            double absorption = ((Number) result).doubleValue();
            return I18nService.tr("吸收之心：{}", String.format("%.1f", absorption));
        }
        // 手持物品（单个 ItemStack，Spigot 路径）
        if (result instanceof org.bukkit.inventory.ItemStack itemStack && (api.getId().equals("get_player_hand_item") || api.getId().equals("get_player_offhand_item"))) {
            String label = api.getId().equals("get_player_hand_item") ? I18nService.tr("主手物品") : I18nService.tr("副手物品");
            if (itemStack.getType() == org.bukkit.Material.AIR) {
                return label + I18nService.tr("：空手");
            }
            return formatSingleItemStack(label, itemStack);
        }
        // 盔甲装备（ItemStack 数组）
        if (result instanceof org.bukkit.inventory.ItemStack[] armorContents && api.getId().equals("get_player_armor")) {
            return formatArmorContents(armorContents);
        }
        // 背包占用格数（极轻量）
        if (result instanceof org.bukkit.inventory.ItemStack[] contents && api.getId().equals("get_player_inventory_usage")) {
            return formatInventoryUsage(contents, I18nService.tr("背包"));
        }
        // 背包物品摘要
        if (result instanceof org.bukkit.inventory.ItemStack[] contents && api.getId().equals("get_player_inventory")) {
            return formatInventorySummary(contents, I18nService.tr("背包"));
        }
        // 末影箱物品摘要
        if (result instanceof org.bukkit.inventory.ItemStack[] contents && api.getId().equals("get_player_ender_chest")) {
            return formatInventorySummary(contents, I18nService.tr("末影箱"));
        }
        // 打开的容器物品摘要
        if (result instanceof org.bukkit.inventory.ItemStack[] contents && api.getId().equals("get_player_open_container")) {
            String containerLabel = I18nService.tr("容器");
            if (player != null) {
                try {
                    var invType = player.getOpenInventory().getType();
                    containerLabel = formatInventoryType(invType).replace(I18nService.tr("当前界面："), "");
                } catch (Exception ignored) {
                }
            }
            return formatInventorySummary(contents, containerLabel);
        }
        // 瞄准的方块（lophine 优化：Block 对象已在区域线程内提取为 Map）
        if (result instanceof java.util.Map<?, ?> blockMap && api.getId().equals("get_player_target_block")) {
            return formatBlockFromMap(blockMap);
        }
        // 脚下方块（Folia Map 路径）
        if (result instanceof java.util.Map<?, ?> feetBlockMap && api.getId().equals("get_player_feet_block")) {
            return formatFeetBlockFromMap(feetBlockMap);
        }
        // 脚下方块（Spigot Block 路径）
        if (result instanceof org.bukkit.block.Block block && api.getId().equals("get_player_feet_block")) {
            String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(block.getType().name());
            return I18nService.tr("脚下方块：{}（位置：X={}, Y={}, Z={}）", chineseName, block.getX(), block.getY(), block.getZ());
        }
        // 上次受伤原因（Folia Map 路径）
        if (result instanceof java.util.Map<?, ?> damageMap && api.getId().equals("get_player_last_damage")) {
            return formatDamageFromMap(damageMap);
        }
        // 上次受伤原因（Spigot EntityDamageEvent 路径）
        if (result instanceof org.bukkit.event.entity.EntityDamageEvent damageEvent && api.getId().equals("get_player_last_damage")) {
            return formatDamageEvent(damageEvent);
        }
        // 当前打开的界面类型（InventoryType 枚举）
        if (result instanceof org.bukkit.event.inventory.InventoryType inventoryType && api.getId().equals("get_player_open_inventory")) {
            return formatInventoryType(inventoryType);
        }
        // 生物群系（lophine 优化：Biome 对象已在区域线程内提取为 String）
        if (result instanceof String biomeName && api.getId().equals("get_world_biome")) {
            return formatBiomeByName(biomeName);
        }
        // 温度/湿度（Double 类型）
        if (result instanceof Double temp && (api.getId().equals("get_world_temperature") || api.getId().equals("get_world_humidity"))) {
            return String.format("%.2f", temp);
        }
        // 世界总时间/游戏时间（Long 类型）
        if (result instanceof Long timeTicks && (api.getId().equals("get_world_full_time") || api.getId().equals("get_world_game_time"))) {
            return formatWorldTime(timeTicks, api.getId());
        }
        // Ping 延迟
        if (result instanceof Integer ping && api.getId().equals("get_player_ping")) {
            String quality = ping < 100 ? (I18nService.isZh() ? "极好" : "Excellent") : (ping < 200 ? (I18nService.isZh() ? "良好" : "Good") : (ping < 300 ? (I18nService.isZh() ? "一般" : "Fair") : (I18nService.isZh() ? "较差" : "Poor")));
            return I18nService.tr("延迟：{}ms ({})", ping, quality);
        }
        // 升到下一级所需经验（Bukkit 原生方法）
        if (result instanceof Integer exp && api.getId().equals("get_player_exp_to_level")) {
            return I18nService.tr("升到下一级需要：{} 点经验", exp);
        }
        // 身上的箭（Integer 类型，结构化输出）
        if (result instanceof Integer arrows && api.getId().equals("get_player_arrows_in_body")) {
            return I18nService.tr("身上的箭矢：{} 支", arrows);
        }

        // additional_methods 模式返回的 Map
        if (result instanceof java.util.Map<?, ?> mapResult) {
            return formatWithAdditionalMethods(api, mapResult);
        }

        // Boolean 类型（如 isInsideVehicle、getAllowFlight 等）— 附加上下文描述
        if (result instanceof Boolean bool) {
            return formatBooleanResult(api, bool);
        }

        // 其他类型默认返回 toString
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
            return bool ? (I18nService.isZh() ? "是" : "Yes") : (I18nService.isZh() ? "否" : "No");
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
            return I18nService.tr("天气：晴朗");
        } else if (isThundering != null && isThundering) {
            return I18nService.tr("天气：雷暴");
        } else {
            return I18nService.tr("天气：雨天");
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
        return I18nService.tr("等级：{}，经验进度：{}%", level, percentage);
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
            return I18nService.tr("着火状态：未着火");
        }

        // 将 tick 转换为秒（20 tick = 1 秒）
        double seconds = fireTicks / 20.0;
        return I18nService.tr("着火状态：正在燃烧！剩余 {} 秒 ({}{} tick)", String.format("%.1f", seconds), fireTicks, "/" + maxFireTicks);
    }

    /**
     * 格式化世界时间结果
     */
    private String formatTimeResult(java.util.Map<?, ?> resultMap) {
        return I18nService.tr("该 API 不应返回 Map 类型");
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

        return I18nService.tr("游戏时间：{}", String.format("%02d:%02d", hours, minutes));
    }

    /**
     * 格式化集合类型（区分玩家列表和世界列表）
     */
    private String formatCollection(java.util.Collection<?> collection, BukkitAPIMetadata api) {
        if (collection == null || collection.isEmpty()) {
            // 根据API类型返回不同的空消息
            if (api.getId().equals("get_server_worlds")) {
                return I18nService.tr("服务器暂无已加载的世界");
            }
            return I18nService.tr("当前没有玩家在线");
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
            return I18nService.tr("当前没有玩家在线");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("在线玩家（{}人）：\n", players.size()));

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
                        sb.append(I18nService.tr(" ... 等 {} 人", remaining));
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
        sb.append(I18nService.tr("服务器世界列表（{}个）：\n", worlds.size()));

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
     * 格式化 Location（lophine 兼容版本：接收 Map）
     */
    private String formatLocationFromMap(java.util.Map<?, ?> locMap) {
        if (locMap == null || locMap.isEmpty()) {
            return I18nService.tr("位置：未知");
        }
        double x = locMap.containsKey("x") ? ((Number) locMap.get("x")).doubleValue() : 0;
        double y = locMap.containsKey("y") ? ((Number) locMap.get("y")).doubleValue() : 0;
        double z = locMap.containsKey("z") ? ((Number) locMap.get("z")).doubleValue() : 0;
        String world = locMap.containsKey("world") ? (String) locMap.get("world") : "未知";
        return I18nService.tr("位置：X={}, Y={}, Z={}, 世界={}", String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z), world);
    }

    /**
     * 数字转罗马数字（用于附魔等级）
     */
    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    /**
     * 格式化单个 ItemStack（Spigot 路径，直接持有 Bukkit 对象）
     */
    private String formatSingleItemStack(String label, org.bukkit.inventory.ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(I18nService.tr("："));
        // 优先使用自定义名称，否则使用中文翻译
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            sb.append(item.getItemMeta().getDisplayName());
        } else {
            String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(item.getType().name());
            sb.append(chineseName);
        }
        if (item.getAmount() > 1) {
            sb.append(" x").append(item.getAmount());
        }
        // 耐久度
        if (item.getType().getMaxDurability() > 0) {
            int max = item.getType().getMaxDurability();
            int remaining = max - item.getDurability();
            sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
        }
        // 附魔
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            sb.append(I18nService.tr(" [附魔:"));
            boolean first = true;
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : item.getItemMeta().getEnchants().entrySet()) {
                if (!first) sb.append("; ");
                sb.append(entry.getKey().getKey().getKey().toUpperCase()).append(" ").append(toRoman(entry.getValue()));
                first = false;
            }
            sb.append("]");
        }
        return sb.toString();
    }

    /**
     * 格式化 ItemStack（lophine 兼容版本：接收 Map）
     *
     * <p>注意：字段名必须与 extractThreadSafeData 和 extractDataFromResult 保持一致，
     * 使用标准化命名：item_type, item_name, item_amount</p>
     */
    private String formatItemStackFromMap(BukkitAPIMetadata api, java.util.Map<?, ?> itemMap) {
        // 根据API类型确定标签
        String label = I18nService.tr("物品");
        if (api != null) {
            if (api.getId().equals("get_player_hand_item")) {
                label = I18nService.tr("主手物品");
            } else if (api.getId().equals("get_player_offhand_item")) {
                label = I18nService.tr("副手物品");
            }
        }
        if (itemMap == null || itemMap.isEmpty()) {
            return label + I18nService.tr("：空手");
        }

        // 使用标准化字段名（与 extractThreadSafeData 和 extractDataFromResult 一致）
        String type = (String) itemMap.get("item_type");
        if (type == null || type.equals("AIR")) {
            return label + I18nService.tr("：空手");
        }

        int amount = itemMap.containsKey("item_amount") ? ((Number) itemMap.get("item_amount")).intValue() : 1;

        // 优先使用 item_name（自定义名称或类型名）
        String displayName = (String) itemMap.get("item_name");

        StringBuilder sb = new StringBuilder();
        if (displayName != null) {
            sb.append(label).append("：").append(displayName);
        } else {
            String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(type);
            sb.append(label).append("：").append(chineseName);
        }
        if (amount > 1) {
            sb.append(" x").append(amount);
        }

        // 耐久度
        if (itemMap.containsKey("remaining_durability") && itemMap.containsKey("max_durability")) {
            int remaining = ((Number) itemMap.get("remaining_durability")).intValue();
            int max = ((Number) itemMap.get("max_durability")).intValue();
            sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
        } else if (itemMap.containsKey("damage")) {
            // 只有 damage 没有 max_durability 的情况
            int damage = ((Number) itemMap.get("damage")).intValue();
            sb.append(I18nService.tr(" [损伤:{}]", damage));
        }

        // 附魔
        if (itemMap.containsKey("enchantments") && itemMap.get("enchantments") instanceof Map<?, ?> enchantments) {
            sb.append(I18nService.tr(" [附魔:"));
            boolean first = true;
            for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
                if (!first) sb.append("; ");
                String enchName = entry.getKey().toString();
                int level = ((Number) entry.getValue()).intValue();
                sb.append(enchName).append(" ").append(toRoman(level));
                first = false;
            }
            sb.append("]");
        }

        // Lore（物品描述）
        if (itemMap.containsKey("lore") && itemMap.get("lore") instanceof java.util.List<?> lore) {
            if (!lore.isEmpty()) {
                sb.append(I18nService.tr(" [描述:{}行]", lore.size()));
            }
        }

        // 特殊属性（无法破坏）
        if (itemMap.containsKey("unbreakable") && Boolean.TRUE.equals(itemMap.get("unbreakable"))) {
            sb.append(I18nService.tr(" [无法破坏]"));
        }

        return sb.toString();
    }

    /**
     * 格式化 Vector（lophine 兼容版本：接收 Map）
     */
    private String formatVectorFromMap(java.util.Map<?, ?> vecMap) {
        if (vecMap == null || vecMap.isEmpty()) {
            return I18nService.tr("移动状态：未知");
        }
        double x = vecMap.containsKey("x") ? ((Number) vecMap.get("x")).doubleValue() : 0;
        double y = vecMap.containsKey("y") ? ((Number) vecMap.get("y")).doubleValue() : 0;
        double z = vecMap.containsKey("z") ? ((Number) vecMap.get("z")).doubleValue() : 0;
        double speed = Math.sqrt(x * x + y * y + z * z);

        if (speed < 0.1) {
            return I18nService.tr("移动状态：静止");
        }

        return I18nService.tr("移动状态：速度={} (X={}, Y={}, Z={})", String.format("%.2f", speed), String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z));
    }

    /**
     * 格式化游戏模式
     */
    private String formatGameMode(org.bukkit.GameMode gameMode) {
        String displayName = switch (gameMode) {
            case SURVIVAL -> I18nService.isZh() ? "生存模式" : "Survival";
            case CREATIVE -> I18nService.isZh() ? "创造模式" : "Creative";
            case ADVENTURE -> I18nService.isZh() ? "冒险模式" : "Adventure";
            case SPECTATOR -> I18nService.isZh() ? "旁观模式" : "Spectator";
        };
        return I18nService.tr("游戏模式：{}", displayName);
    }

    /**
     * 格式化世界环境类型
     */
    private String formatEnvironment(org.bukkit.World.Environment environment) {
        return switch (environment) {
            case NORMAL -> I18nService.isZh() ? "主世界" : "Overworld";
            case NETHER -> I18nService.isZh() ? "下界" : "Nether";
            case THE_END -> I18nService.isZh() ? "末地" : "The End";
            case CUSTOM -> I18nService.isZh() ? "自定义" : "Custom";
        };
    }

    /**
     * 格式化游戏难度
     */
    private String formatDifficulty(org.bukkit.Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> I18nService.isZh() ? "和平" : "Peaceful";
            case EASY -> I18nService.isZh() ? "简单" : "Easy";
            case NORMAL -> I18nService.isZh() ? "普通" : "Normal";
            case HARD -> I18nService.isZh() ? "困难" : "Hard";
        };
    }

    /**
     * 格式化玩家姿势
     */
    private String formatPose(org.bukkit.entity.Pose pose) {
        String displayName = switch (pose) {
            case STANDING -> I18nService.isZh() ? "站立" : "Standing";
            case FALL_FLYING -> I18nService.isZh() ? "鞘翅飞行" : "Elytra flying";
            case SLEEPING -> I18nService.isZh() ? "睡觉" : "Sleeping";
            case SWIMMING -> I18nService.isZh() ? "游泳" : "Swimming";
            case SPIN_ATTACK -> I18nService.isZh() ? "旋转攻击" : "Spin attack";
            case SNEAKING -> I18nService.isZh() ? "潜行" : "Sneaking";
            case DYING -> I18nService.isZh() ? "死亡" : "Dead";
        };
        return I18nService.tr("当前姿势：{}", displayName);
    }

    /**
     * 格式化主手偏好
     */
    private String formatMainHand(org.bukkit.inventory.MainHand mainHand) {
        String displayName = switch (mainHand) {
            case LEFT -> I18nService.isZh() ? "左手（左撇子）" : "Left hand (Left-handed)";
            case RIGHT -> I18nService.isZh() ? "右手（右撇子）" : "Right hand (Right-handed)";
        };
        return I18nService.tr("主手偏好：{}", displayName);
    }

    /**
     * 格式化盔甲装备（ItemStack 数组）
     */
    private String formatArmorContents(org.bukkit.inventory.ItemStack[] armor) {
        if (armor == null || armor.length == 0) {
            return I18nService.tr("盔甲：无");
        }

        // Bukkit 盔甲数组顺序：[靴子, 护腿, 胸甲, 头盔]
        String[] slotNames = {I18nService.tr("靴子"), I18nService.tr("护腿"), I18nService.tr("胸甲"), I18nService.tr("头盔")};
        StringBuilder sb = new StringBuilder();
        boolean hasArmor = false;

        for (int i = 0; i < armor.length; i++) {
            org.bukkit.inventory.ItemStack item = armor[i];
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                if (hasArmor) sb.append("; ");
                sb.append(slotNames[i]).append(I18nService.tr("："));
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    sb.append(item.getItemMeta().getDisplayName());
                } else {
                    String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(item.getType().name());
                    sb.append(chineseName);
                }
                if (item.getAmount() > 1) {
                    sb.append(" x").append(item.getAmount());
                }
                // 耐久度
                if (item.getType().getMaxDurability() > 0) {
                    int max = item.getType().getMaxDurability();
                    int remaining = max - item.getDurability();
                    sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
                }
                // 附魔
                if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                    sb.append(I18nService.tr(" [附魔:"));
                    boolean first = true;
                    for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : item.getItemMeta().getEnchants().entrySet()) {
                        if (!first) sb.append("; ");
                        sb.append(entry.getKey().getKey().getKey().toUpperCase()).append(" ").append(toRoman(entry.getValue()));
                        first = false;
                    }
                    sb.append("]");
                }
                hasArmor = true;
            }
        }

        return hasArmor ? I18nService.tr("盔甲：") + sb : I18nService.tr("盔甲：无");
    }

    /**
     * 格式化药水效果集合
     */
    private String formatPotionEffects(java.util.Collection<?> effects) {
        if (effects == null || effects.isEmpty()) {
            return I18nService.tr("药水效果：无");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("药水效果："));
        int count = 0;

        for (Object obj : effects) {
            if (obj instanceof org.bukkit.potion.PotionEffect effect) {
                if (count > 0) sb.append("; ");

                // 效果名称（中文）
                String typeName = effect.getType().getName();
                sb.append(typeName);

                // 等级（放大器 + 1，因为 0 = I）
                int amplifier = effect.getAmplifier() + 1;
                sb.append(I18nService.tr(" {}级", amplifier));

                // 剩余时间（tick 转秒）
                int seconds = effect.getDuration() / 20;
                if (seconds >= 60) {
                    int minutes = seconds / 60;
                    int remainingSeconds = seconds % 60;
                    sb.append(I18nService.tr(" ({}分{}秒)", minutes, remainingSeconds));
                } else {
                    sb.append(I18nService.tr(" ({}秒)", seconds));
                }

                count++;
                // 最多显示 5 个效果
                if (count >= 5) {
                    int remaining = effects.size() - count;
                    if (remaining > 0) {
                        sb.append(I18nService.tr(" ... 等 {} 个效果", remaining));
                    }
                    break;
                }
            }
        }

        return sb.toString();
    }

    /**
     * 格式化 Block（lophine 兼容版本：接收 Map）
     *
     * <p>注意：字段名必须与 extractThreadSafeData 保持一致，
     * 使用标准化命名：block_type, x, y, z, world</p>
     */
    private String formatBlockFromMap(java.util.Map<?, ?> blockMap) {
        if (blockMap == null || blockMap.isEmpty()) {
            return I18nService.tr("瞄准方块：无（距离太远或没有方块）");
        }

        String materialName = (String) blockMap.get("block_type");
        if (materialName == null) {
            return I18nService.tr("瞄准方块：未知");
        }

        String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(materialName);
        int x = blockMap.containsKey("x") ? ((Number) blockMap.get("x")).intValue() : 0;
        int y = blockMap.containsKey("y") ? ((Number) blockMap.get("y")).intValue() : 0;
        int z = blockMap.containsKey("z") ? ((Number) blockMap.get("z")).intValue() : 0;

        return I18nService.tr("瞄准方块：{}（位置：X={}, Y={}, Z={}）", chineseName, x, y, z);
    }

    /**
     * 格式化脚下方块（Folia Map 路径）
     */
    private String formatFeetBlockFromMap(java.util.Map<?, ?> blockMap) {
        if (blockMap == null || blockMap.isEmpty()) {
            return I18nService.tr("脚下方块：未知");
        }
        String materialName = blockMap.get("block_type") != null ? blockMap.get("block_type").toString() : null;
        if (materialName == null) {
            return I18nService.tr("脚下方块：未知");
        }
        String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(materialName);
        int x = blockMap.containsKey("x") ? ((Number) blockMap.get("x")).intValue() : 0;
        int y = blockMap.containsKey("y") ? ((Number) blockMap.get("y")).intValue() : 0;
        int z = blockMap.containsKey("z") ? ((Number) blockMap.get("z")).intValue() : 0;
        return I18nService.tr("脚下方块：{}（位置：X={}, Y={}, Z={}）", chineseName, x, y, z);
    }

    /**
     * 格式化盔甲装备（Map 格式，来自 Paper 异步或 Folia extractThreadSafeData）
     * 字段名与 BukkitAPIExecutor.extractThreadSafeData 盔甲分支一致：
     * {slot}_name, {slot}_type, {slot}_enchantments, {slot}_max_durability, {slot}_remaining_durability
     * slot 名称：boots, leggings, chestplate, helmet
     */
    private String formatArmorFromMap(java.util.Map<?, ?> armorMap) {
        if (armorMap == null || armorMap.isEmpty() || armorMap.containsKey("empty") || armorMap.containsKey("item_count")) {
            // 空 Map 或标记为 empty 或旧格式（只有 item_count/empty_slots）
            if (armorMap != null && armorMap.containsKey("item_count")) {
                int count = armorMap.get("item_count") instanceof Number n ? n.intValue() : 0;
                if (count > 0) {
                    // 有盔甲但旧格式无法提取详情，提示重新查询
                    return I18nService.tr("盔甲：有 {} 件装备", count);
                }
            }
            return I18nService.tr("盔甲：无");
        }

        // Bukkit 盔甲数组顺序：[靴子, 护腿, 胸甲, 头盔]
        String[] slotKeys = {"boots", "leggings", "chestplate", "helmet"};
        String[] slotNames = {I18nService.tr("靴子"), I18nService.tr("护腿"), I18nService.tr("胸甲"), I18nService.tr("头盔")};
        StringBuilder sb = new StringBuilder();
        boolean hasArmor = false;

        for (int i = 0; i < slotKeys.length; i++) {
            String prefix = slotKeys[i];
            String nameKey = prefix + "_name";
            if (armorMap.containsKey(nameKey)) {
                if (hasArmor) sb.append("; ");
                sb.append(slotNames[i]).append(I18nService.tr("："));

                // 名称
                Object nameObj = armorMap.get(nameKey);
                sb.append(nameObj != null ? nameObj.toString() : "未知");

                // 耐久度
                String remainingKey = prefix + "_remaining_durability";
                String maxKey = prefix + "_max_durability";
                if (armorMap.containsKey(remainingKey) && armorMap.containsKey(maxKey)) {
                    int remaining = armorMap.get(remainingKey) instanceof Number n ? n.intValue() : 0;
                    int max = armorMap.get(maxKey) instanceof Number n ? n.intValue() : 0;
                    sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
                }

                // 附魔
                String enchKey = prefix + "_enchantments";
                if (armorMap.containsKey(enchKey) && armorMap.get(enchKey) instanceof Map<?, ?> enchants) {
                    sb.append(I18nService.tr(" [附魔:"));
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                        if (!first) sb.append("; ");
                        String enchName = entry.getKey().toString();
                        int level = entry.getValue() instanceof Number n ? n.intValue() : 0;
                        sb.append(enchName).append(" ").append(toRoman(level));
                        first = false;
                    }
                    sb.append("]");
                }
                hasArmor = true;
            }
        }

        return hasArmor ? I18nService.tr("盔甲：") + sb : I18nService.tr("盔甲：无");
    }

    /**
     * 格式化上次受伤原因（Folia Map 路径）
     */
    private String formatDamageFromMap(java.util.Map<?, ?> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return I18nService.tr("无受伤记录");
        }
        String causeName = damageMap.get("damage_cause") != null ? damageMap.get("damage_cause").toString() : "未知";
        String causeDisplay = formatDamageCauseByName(causeName);
        double amount = damageMap.containsKey("damage_amount") ? ((Number) damageMap.get("damage_amount")).doubleValue() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("上次受伤：")).append(causeDisplay).append(I18nService.tr("（{} 伤害）", String.format("%.1f", amount)));
        if (damageMap.containsKey("damager_name")) {
            sb.append(I18nService.tr("，攻击者：")).append(damageMap.get("damager_name"));
        }
        return sb.toString();
    }

    /**
     * 格式化上次受伤原因（Spigot EntityDamageEvent 路径）
     */
    private String formatDamageEvent(org.bukkit.event.entity.EntityDamageEvent damageEvent) {
        String causeDisplay = formatDamageCause(damageEvent.getCause());
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("上次受伤：")).append(causeDisplay).append(I18nService.tr("（{} 伤害）", String.format("%.1f", damageEvent.getDamage())));
        if (damageEvent instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntityEvent) {
            org.bukkit.entity.Entity damager = byEntityEvent.getDamager();
            if (damager != null) {
                String name = damager instanceof org.bukkit.entity.Player p ? p.getName() : (damager instanceof org.bukkit.entity.LivingEntity m ? (m.getName() != null ? m.getName() : m.getType().name()) : damager.getType().name());
                sb.append(I18nService.tr("，攻击者：")).append(name);
            }
        }
        return sb.toString();
    }

    /**
     * 将 DamageCause 枚举转为中文描述
     */
    private String formatDamageCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (I18nService.isZh()) {
            return switch (cause) {
                case ENTITY_ATTACK -> "被实体攻击";
                case ENTITY_SWEEP_ATTACK -> "被横扫攻击";
                case PROJECTILE -> "被弹射物击中";
                case SUFFOCATION -> "窒息（卡在方块中）";
                case FALL -> "摔落伤害";
                case FIRE -> "火焰伤害";
                case FIRE_TICK -> "燃烧伤害";
                case MELTING -> "融化伤害";
                case LAVA -> "岩浆伤害";
                case DROWNING -> "漏水";
                case BLOCK_EXPLOSION -> "方块爆炸";
                case ENTITY_EXPLOSION -> "实体爆炸（苦力怕/TNT）";
                case VOID -> "掉入虚空";
                case LIGHTNING -> "雷击";
                case SUICIDE -> "自杀";
                case STARVATION -> "饥饿";
                case POISON -> "中毒";
                case MAGIC -> "魔法伤害";
                case WITHER -> "调零";
                case FALLING_BLOCK -> "被掉落方块砸中";
                case THORNS -> "荆棘反伤";
                case DRAGON_BREATH -> "龙息";
                case CUSTOM -> "自定义伤害";
                case FLY_INTO_WALL -> "动能伤害（撞墙）";
                case HOT_FLOOR -> "踩到热地板";
                case CRAMMING -> "实体挤压";
                case CONTACT -> "接触伤害（仙人掌/浆果丛）";
                default -> cause.name();
            };
        }
        return switch (cause) {
            case ENTITY_ATTACK -> "Attacked by entity";
            case ENTITY_SWEEP_ATTACK -> "Hit by sweep attack";
            case PROJECTILE -> "Hit by projectile";
            case SUFFOCATION -> "Suffocation (stuck in block)";
            case FALL -> "Fall damage";
            case FIRE -> "Fire damage";
            case FIRE_TICK -> "Burning damage";
            case MELTING -> "Melting damage";
            case LAVA -> "Lava damage";
            case DROWNING -> "Drowning";
            case BLOCK_EXPLOSION -> "Block explosion";
            case ENTITY_EXPLOSION -> "Entity explosion (Creeper/TNT)";
            case VOID -> "Fell into void";
            case LIGHTNING -> "Lightning strike";
            case SUICIDE -> "Suicide";
            case STARVATION -> "Starvation";
            case POISON -> "Poison";
            case MAGIC -> "Magic damage";
            case WITHER -> "Wither";
            case FALLING_BLOCK -> "Hit by falling block";
            case THORNS -> "Thorns damage";
            case DRAGON_BREATH -> "Dragon breath";
            case CUSTOM -> "Custom damage";
            case FLY_INTO_WALL -> "Kinetic damage (hit wall)";
            case HOT_FLOOR -> "Stepped on hot floor";
            case CRAMMING -> "Entity cramming";
            case CONTACT -> "Contact damage (cactus/sweet berry bush)";
            default -> cause.name();
        };
    }

    /**
     * 将 DamageCause 枚举名称（String）转为中文描述（用于 Folia Map 路径）
     */
    private String formatDamageCauseByName(String causeName) {
        try {
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(causeName);
            return formatDamageCause(cause);
        } catch (IllegalArgumentException e) {
            return causeName;
        }
    }

    /**
     * 格式化生物群系（lophine 兼容版本：接收 String）
     */
    private String formatBiomeByName(String biomeName) {
        if (biomeName == null) {
            return I18nService.tr("未知群系");
        }

        // 转换为更友好的显示格式
        String displayName = biomeName.replace('_', ' ').toLowerCase();
        // 首字母大写
        if (!displayName.isEmpty()) {
            displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
        }
        return I18nService.tr("生物群系：{}", displayName);
    }

    /**
     * 从 ItemStack[] 中提取占用格数和空格数（极轻量，不读取 ItemMeta）
     */
    private void extractInventoryUsage(org.bukkit.inventory.ItemStack[] contents, Map<String, Object> dataMap) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : contents) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                count++;
            }
        }
        dataMap.put("item_count", count);
        dataMap.put("empty_slots", contents.length - count);
    }

    /**
     * 从 ItemStack[] 中提取物品摘要（仅物品名称+数量，不含附魔/耐久）
     */
    private void extractInventorySummary(org.bukkit.inventory.ItemStack[] contents, Map<String, Object> dataMap) {
        java.util.List<Map<String, Object>> itemsList = new java.util.ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                Map<String, Object> itemData = new java.util.HashMap<>();
                itemData.put("slot", i);
                itemData.put("item_type", item.getType().name());
                // 仅读取名称，不读取附魔/耐久等详情
                String itemName;
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    itemName = item.getItemMeta().getDisplayName();
                } else {
                    itemName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(item.getType().name());
                }
                itemData.put("item_name", itemName);
                itemData.put("item_amount", item.getAmount());
                itemsList.add(itemData);
            }
        }
        dataMap.put("items", itemsList);
    }

    /**
     * 格式化背包占用情况（极轻量，仅显示格数）
     */
    private String formatInventoryUsage(org.bukkit.inventory.ItemStack[] contents, String label) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : contents) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                count++;
            }
        }
        int empty = contents.length - count;
        return I18nService.tr("{}占用：{}/{} 格（空 {} 格）", label, count, contents.length, empty);
    }

    /**
     * 格式化背包/末影箱物品摘要
     */
    private String formatInventorySummary(org.bukkit.inventory.ItemStack[] contents, String label) {
        int count = 0;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                if (count == 0) {
                    // 第一行时统计总数
                    int total = 0;
                    for (org.bukkit.inventory.ItemStack c : contents) {
                        if (c != null && c.getType() != org.bukkit.Material.AIR) total++;
                    }
                    sb.append(label).append(I18nService.tr("物品（已用 {}/{} 格）：\n", total, contents.length));
                }
                String itemName;
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    itemName = item.getItemMeta().getDisplayName();
                } else {
                    itemName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance().translateToChinese(item.getType().name());
                }
                sb.append("  [").append(i).append("] ").append(itemName);
                if (item.getAmount() > 1) {
                    sb.append(" x").append(item.getAmount());
                }
                sb.append("\n");
                count++;
            }
        }

        return count > 0 ? sb.toString().trim() : label + I18nService.tr("：空");
    }

    /**
     * 格式化当前打开的界面类型
     */
    private String formatInventoryType(org.bukkit.event.inventory.InventoryType type) {
        String displayName = switch (type.name()) {
            case "CHEST" -> I18nService.isZh() ? "箱子" : "Chest";
            case "CRAFTING" -> I18nService.isZh() ? "合成栏" : "Crafting";
            case "FURNACE" -> I18nService.isZh() ? "熔炉" : "Furnace";
            case "WORKBENCH" -> I18nService.isZh() ? "工作台" : "Crafting table";
            case "ANVIL" -> I18nService.isZh() ? "铁砧" : "Anvil";
            case "ENCHANTING" -> I18nService.isZh() ? "附魔台" : "Enchanting table";
            case "BREWING" -> I18nService.isZh() ? "酿造台" : "Brewing stand";
            case "PLAYER" -> I18nService.isZh() ? "玩家背包" : "Player inventory";
            case "CREATIVE" -> I18nService.isZh() ? "创造模式背包" : "Creative inventory";
            case "MERCHANT" -> I18nService.isZh() ? "村民交易" : "Villager trade";
            case "ENDER_CHEST" -> I18nService.isZh() ? "末影箱" : "Ender Chest";
            case "BEACON" -> I18nService.isZh() ? "信标" : "Beacon";
            case "HOPPER" -> I18nService.isZh() ? "漏斗" : "Hopper";
            case "DROPPER" -> I18nService.isZh() ? "投掷器" : "Dropper";
            case "DISPENSER" -> I18nService.isZh() ? "发射器" : "Dispenser";
            case "SHULKER_BOX" -> I18nService.isZh() ? "潜影盒" : "Shulker box";
            case "SMITHING" -> I18nService.isZh() ? "锻造台" : "Smithing table";
            case "STONECUTTER" -> I18nService.isZh() ? "切石机" : "Stonecutter";
            case "GRINDSTONE" -> I18nService.isZh() ? "砂轮" : "Grindstone";
            case "LECTERN" -> I18nService.isZh() ? "讲台" : "Lectern";
            case "LOOM" -> I18nService.isZh() ? "织布机" : "Loom";
            case "BLAST_FURNACE" -> I18nService.isZh() ? "高炉" : "Blast furnace";
            case "SMOKER" -> I18nService.isZh() ? "烟熏炉" : "Smoker";
            case "CARTOGRAPHY" -> I18nService.isZh() ? "制图台" : "Cartography table";
            default -> type.name();
        };
        return I18nService.tr("当前界面：{}", displayName);
    }

    /**
     * 格式化背包/末影箱 Map（Folia 兼容版本：接收 extractThreadSafeData 返回的 Map）
     *
     * <p>Map 结构：item_count, empty_slots, items(List<Map>，仅摘要模式)</p>
     */
    private String formatInventoryFromMap(BukkitAPIMetadata api, java.util.Map<?, ?> invMap) {
        String label = api.getId().equals("get_player_ender_chest") ? I18nService.tr("末影箱") : (api.getId().equals("get_player_open_container") ? I18nService.tr("容器") : I18nService.tr("背包"));

        if (invMap == null || invMap.isEmpty()) {
            return label + I18nService.tr("：空");
        }

        int itemCount = invMap.containsKey("item_count") ? ((Number) invMap.get("item_count")).intValue() : 0;
        int totalSlots = itemCount + (invMap.containsKey("empty_slots") ? ((Number) invMap.get("empty_slots")).intValue() : 0);

        // 极轻量模式：只显示格数
        if (api.getId().equals("get_player_inventory_usage")) {
            return I18nService.tr("{}占用：{}/{} 格（空 {} 格）", label, itemCount, totalSlots, totalSlots - itemCount);
        }

        // 摘要模式：显示物品列表
        Object itemsObj = invMap.get("items");
        if (!(itemsObj instanceof java.util.List<?> itemsList) || itemsList.isEmpty()) {
            return label + I18nService.tr("：空");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(label).append(I18nService.tr("物品（已用 {}/{} 格）：\n", itemCount, totalSlots));

        for (Object obj : itemsList) {
            if (obj instanceof java.util.Map<?, ?> itemData) {
                int slot = itemData.containsKey("slot") ? ((Number) itemData.get("slot")).intValue() : -1;
                String itemName = itemData.get("item_name") != null ? itemData.get("item_name").toString() : (itemData.get("item_type") != null ? itemData.get("item_type").toString() : "未知");
                int amount = itemData.containsKey("item_amount") ? ((Number) itemData.get("item_amount")).intValue() : 1;
                sb.append("  [").append(slot).append("] ").append(itemName);
                if (amount > 1) {
                    sb.append(" x").append(amount);
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * 格式化袭击列表
     */
    private String formatRaids(java.util.Collection<?> raids) {
        if (raids == null || raids.isEmpty()) {
            return I18nService.tr("袭击事件：无");
        }

        int raidCount = raids.size();
        return I18nService.tr("当前正在进行 {} 个袭击", raidCount);
    }

    /**
     * 格式化世界时间（tick 转可读格式）
     */
    private String formatWorldTime(long ticks, String apiId) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (apiId.equals("get_world_full_time")) {
            if (days > 0) {
                return I18nService.tr("世界总时间：{} 天 {} 小时", days, hours % 24);
            } else if (hours > 0) {
                return I18nService.tr("世界总时间：{} 小时 {} 分钟", hours, minutes % 60);
            } else {
                return I18nService.tr("世界总时间：{} 分钟", minutes);
            }
        } else {
            if (days > 0) {
                return I18nService.tr("世界游戏时间：{} 天 {} 小时", days, hours % 24);
            } else if (hours > 0) {
                return I18nService.tr("世界游戏时间：{} 小时 {} 分钟", hours, minutes % 60);
            } else {
                return I18nService.tr("世界游戏时间：{} 分钟", minutes);
            }
        }
    }

    /**
     * 格式化时长（用于 AFK 时间）
     */
    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return I18nService.tr("挂机时间：{} 秒", seconds);
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return I18nService.tr("挂机时间：{} 分 {} 秒", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return I18nService.tr("挂机时间：{} 小时 {} 分", hours, minutes);
        }
    }

    /**
     * 格式化 Boolean 类型结果，附加上下文描述
     */
    private String formatBooleanResult(BukkitAPIMetadata api, boolean value) {
        String apiId = api.getId();

        // 根据 API ID 提供语义化的描述
        if (apiId.contains("pvp")) {
            return value ? I18nService.tr("PVP 已开启：这个世界允许玩家互相攻击") : I18nService.tr("PVP 已关闭：这个世界禁止玩家互相攻击");
        }
        if (apiId.contains("flight") || apiId.contains("fly")) {
            return value ? I18nService.tr("允许飞行") : I18nService.tr("禁止飞行");
        }
        if (apiId.contains("whitelist")) {
            return value ? I18nService.tr("白名单已开启") : I18nService.tr("白名单已关闭");
        }
        if (apiId.contains("hardcore")) {
            return value ? I18nService.tr("硬核模式已开启") : I18nService.tr("硬核模式未开启");
        }
        if (apiId.contains("generate")) {
            return value ? I18nService.tr("已启用") : I18nService.tr("未启用");
        }
        if (apiId.contains("autosave")) {
            return value ? I18nService.tr("自动保存已开启") : I18nService.tr("自动保存已关闭");
        }
        if (apiId.contains("sneak") || apiId.contains("sprinting")) {
            return value ? I18nService.tr("是") : I18nService.tr("否");
        }
        if (apiId.contains("vehicle")) {
            return value ? I18nService.tr("玩家正在骑乘中") : I18nService.tr("玩家未骑乘");
        }
        if (apiId.contains("op")) {
            return value ? I18nService.tr("是管理员（OP）") : I18nService.tr("不是管理员");
        }
        if (apiId.contains("sleep") || apiId.contains("sleeping")) {
            return value ? I18nService.tr("玩家正在睡觉") : I18nService.tr("玩家未在睡觉");
        }
        if (apiId.contains("dead")) {
            return value ? I18nService.tr("玩家已死亡") : I18nService.tr("玩家存活");
        }

        // 兜底：使用 API 的 displayName
        String displayName = api.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return displayName + I18nService.tr("：") + (value ? I18nService.tr("是") : I18nService.tr("否"));
        }

        return value ? I18nService.tr("是") : I18nService.tr("否");
    }

}
