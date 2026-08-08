package com.zm.kilacraftAI.service.bukkit;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Bukkit API 查询结果的共享格式化与字段提取工具
 *
 * <p>5 个 {@code Bukkit*Skill} 子技能共享的纯函数集合，承担两类职责：
 * <ul>
 *   <li>格式化（{@code format*}）：把数据转为面向玩家的中文/英文文案，文案与字段名严格沿用原
 *       {@code GenericBukkitAPISkill} 实现，零行为回归。</li>
 *   <li>字段提取（{@code put*}）：把 Bukkit 对象的有用字段填入 {@code dataMap}，
 *       供 {@code TaskExecutor} 的 {@code {step_x.field}} 占位符与 {@code WatchService} 阈值比较使用。
 *       字段名与原 {@code extractDataFromResult}（Spigot 路径）/
 *       {@code BukkitAPIExecutor.extractThreadSafeData}（Folia 路径）逐字一致。</li>
 * </ul>
 *
 * <p>两类方法均为无状态纯函数，不持有任何调用上下文型实例字段（§7 执行器无状态原则）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public final class BukkitAPIResultFormatter {

    private BukkitAPIResultFormatter() {
    }

    /**
     * 数字转罗马数字（用于附魔等级显示）
     */
    public static String toRoman(int number) {
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
     * 布尔值的本地化文案（zh→是/否，en→Yes/No），与 {@link #formatMapValue} 的 Boolean 分支一致。
     */
    public static String formatBoolean(boolean value) {
        return value ? (I18nService.isZh() ? "是" : "Yes") : (I18nService.isZh() ? "否" : "No");
    }

    /**
     * 格式化 Map 中的值（处理枚举/布尔/浮点等特殊类型，用于 additional_methods 模板替换）
     */
    public static String formatMapValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        if (value instanceof World.Environment env) {
            return formatEnvironment(env);
        }
        if (value instanceof Difficulty diff) {
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
     * 格式化天气结果（晴朗/雨天/雷暴，由 has_storm + is_thundering 组合判定）
     */
    public static String formatWeatherResult(Map<?, ?> resultMap) {
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
     * 格式化游戏时间（刻数转 HH:MM，0 刻=6:00 日出）
     */
    public static String formatGameTime(long ticks) {
        long dayTicks = ticks % 24000;
        if (dayTicks < 0) dayTicks += 24000;

        int hours = (int) ((dayTicks / 1000.0 + 6) % 24);
        int minutes = (int) ((dayTicks % 1000) * 60 / 1000.0);

        return I18nService.tr("游戏时间：{}", String.format("%02d:%02d", hours, minutes));
    }

    /**
     * 格式化世界总时间/游戏时间（满刻数转「天/小时/分钟」动态格式）
     *
     * @param apiId {@code get_world_full_time} 或 {@code get_world_game_time}，决定文案前缀
     */
    public static String formatWorldTime(long ticks, String apiId) {
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
     * 格式化在线玩家集合（最多列 20 个名字，超出显示「等 N 人」）
     */
    public static String formatPlayerCollection(Collection<?> players) {
        if (players == null || players.isEmpty()) {
            return I18nService.tr("当前没有玩家在线");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("在线玩家（{}人）：\n", players.size()));

        int count = 0;
        for (Object obj : players) {
            if (obj instanceof Player player) {
                if (count > 0) sb.append(", ");
                sb.append(player.getName());
                count++;
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
     * 格式化世界列表（名称+环境类型）
     */
    public static String formatWorldCollection(Collection<?> worlds) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("服务器世界列表（{}个）：\n", worlds.size()));

        int count = 0;
        for (Object obj : worlds) {
            if (obj instanceof World world) {
                if (count > 0) sb.append(", ");
                sb.append(world.getName());
                sb.append("(").append(formatEnvironment(world.getEnvironment())).append(")");
                count++;
            }
        }
        return sb.toString();
    }

    /**
     * 格式化 Location（从 Map 读取，Folia 路径）
     */
    public static String formatLocationFromMap(Map<?, ?> locMap) {
        if (locMap == null || locMap.isEmpty()) {
            return I18nService.tr("位置：未知");
        }
        double x = locMap.containsKey("x") ? ((Number) locMap.get("x")).doubleValue() : 0;
        double y = locMap.containsKey("y") ? ((Number) locMap.get("y")).doubleValue() : 0;
        double z = locMap.containsKey("z") ? ((Number) locMap.get("z")).doubleValue() : 0;
        String world = locMap.containsKey("world") ? (String) locMap.get("world") : I18nService.tr("未知");
        return I18nService.tr("位置：X={}, Y={}, Z={}, 世界={}", String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z), world);
    }

    /**
     * 格式化单个 ItemStack（Spigot 路径，直接持有 Bukkit 对象）
     *
     * @param label 文案前缀（如「主手物品」）
     */
    public static String formatSingleItemStack(String label, ItemStack item) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(I18nService.tr("："));
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            sb.append(meta.getDisplayName());
        } else {
            String chineseName = ItemTranslator.getInstance().translateToChinese(item.getType().name());
            sb.append(chineseName);
        }
        if (item.getAmount() > 1) {
            sb.append(" x").append(item.getAmount());
        }
        if (item.getType().getMaxDurability() > 0) {
            int max = item.getType().getMaxDurability();
            int remaining = max - item.getDurability();
            sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
        }
        if (meta != null && meta.hasEnchants()) {
            sb.append(I18nService.tr(" [附魔:"));
            boolean first = true;
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                if (!first) sb.append("; ");
                sb.append(entry.getKey().getKey().getKey().toUpperCase()).append(" ").append(toRoman(entry.getValue()));
                first = false;
            }
            sb.append("]");
        }
        return sb.toString();
    }

    /**
     * 格式化 ItemStack（从 Map 读取，Folia 路径）
     *
     * @param label   文案前缀（如「主手物品」）
     * @param itemMap {@code extractThreadSafeData} 提取出的物品 Map
     */
    public static String formatItemStackFromMap(String label, Map<?, ?> itemMap) {
        if (itemMap == null || itemMap.isEmpty()) {
            return label + I18nService.tr("：空手");
        }

        String type = (String) itemMap.get("item_type");
        if (type == null || type.equals("AIR")) {
            return label + I18nService.tr("：空手");
        }

        int amount = itemMap.containsKey("item_amount") ? ((Number) itemMap.get("item_amount")).intValue() : 1;
        String displayName = (String) itemMap.get("item_name");

        StringBuilder sb = new StringBuilder();
        if (displayName != null) {
            sb.append(label).append("：").append(displayName);
        } else {
            String chineseName = ItemTranslator.getInstance().translateToChinese(type);
            sb.append(label).append("：").append(chineseName);
        }
        if (amount > 1) {
            sb.append(" x").append(amount);
        }

        if (itemMap.containsKey("remaining_durability") && itemMap.containsKey("max_durability")) {
            int remaining = ((Number) itemMap.get("remaining_durability")).intValue();
            int max = ((Number) itemMap.get("max_durability")).intValue();
            sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
        } else if (itemMap.containsKey("damage")) {
            int damage = ((Number) itemMap.get("damage")).intValue();
            sb.append(I18nService.tr(" [损伤:{}]", damage));
        }

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

        if (itemMap.containsKey("lore") && itemMap.get("lore") instanceof List<?> lore) {
            if (!lore.isEmpty()) {
                sb.append(I18nService.tr(" [描述:{}行]", lore.size()));
            }
        }

        if (itemMap.containsKey("unbreakable") && Boolean.TRUE.equals(itemMap.get("unbreakable"))) {
            sb.append(I18nService.tr(" [无法破坏]"));
        }

        return sb.toString();
    }

    /**
     * 格式化移动向量（从 Map 读取，速度<0.1 视为静止）
     */
    public static String formatVectorFromMap(Map<?, ?> vecMap) {
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
     * 格式化游戏模式枚举
     */
    public static String formatGameMode(GameMode gameMode) {
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
    public static String formatEnvironment(World.Environment environment) {
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
    public static String formatDifficulty(Difficulty difficulty) {
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
    public static String formatPose(Pose pose) {
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
    public static String formatMainHand(MainHand mainHand) {
        String displayName = switch (mainHand) {
            case LEFT -> I18nService.isZh() ? "左手（左撇子）" : "Left hand (Left-handed)";
            case RIGHT -> I18nService.isZh() ? "右手（右撇子）" : "Right hand (Right-handed)";
        };
        return I18nService.tr("主手偏好：{}", displayName);
    }

    /**
     * 格式化盔甲装备（Spigot 路径，ItemStack 数组）
     *
     * <p>Bukkit 盔甲数组顺序：[靴子, 护腿, 胸甲, 头盔]</p>
     */
    public static String formatArmorContents(ItemStack[] armor) {
        if (armor == null || armor.length == 0) {
            return I18nService.tr("盔甲：无");
        }

        String[] slotNames = {I18nService.tr("靴子"), I18nService.tr("护腿"), I18nService.tr("胸甲"), I18nService.tr("头盔")};
        StringBuilder sb = new StringBuilder();
        boolean hasArmor = false;

        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item != null && item.getType() != Material.AIR) {
                if (hasArmor) sb.append("; ");
                sb.append(slotNames[i]).append(I18nService.tr("："));
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    sb.append(meta.getDisplayName());
                } else {
                    String chineseName = ItemTranslator.getInstance().translateToChinese(item.getType().name());
                    sb.append(chineseName);
                }
                if (item.getAmount() > 1) {
                    sb.append(" x").append(item.getAmount());
                }
                if (item.getType().getMaxDurability() > 0) {
                    int max = item.getType().getMaxDurability();
                    int remaining = max - item.getDurability();
                    sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
                }
                if (meta != null && meta.hasEnchants()) {
                    sb.append(I18nService.tr(" [附魔:"));
                    boolean first = true;
                    for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
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
     * 格式化盔甲装备（Folia 路径，从 Map 读取，字段名 {@code boots_*}/{@code leggings_*}/{@code chestplate_*}/{@code helmet_*}）
     */
    public static String formatArmorFromMap(Map<?, ?> armorMap) {
        if (armorMap == null || armorMap.isEmpty() || armorMap.containsKey("empty") || armorMap.containsKey("item_count")) {
            if (armorMap != null && armorMap.containsKey("item_count")) {
                int count = armorMap.get("item_count") instanceof Number n ? n.intValue() : 0;
                if (count > 0) {
                    return I18nService.tr("盔甲：有 {} 件装备", count);
                }
            }
            return I18nService.tr("盔甲：无");
        }

        String[] slotKeys = {"boots", "leggings", "chestplate", "helmet"};
        String[] slotNames = {I18nService.tr("靴子"), I18nService.tr("护腿"), I18nService.tr("胸甲"), I18nService.tr("头盔")};
        StringBuilder sb = new StringBuilder();
        boolean hasArmor = false;

        for (int i = 0; i < slotKeys.length; i++) {
            String prefix = slotKeys[i];
            String typeKey = prefix + "_type";
            // 槽位是否有装备以 *_type 为准（*_name 仅在有自定义名时存在）
            if (armorMap.containsKey(typeKey)) {
                if (hasArmor) sb.append("; ");
                sb.append(slotNames[i]).append(I18nService.tr("："));

                // *_name 仅有自定义名时存在；无则按 *_type 翻译展示
                String nameKey = prefix + "_name";
                Object nameObj = armorMap.get(nameKey);
                String display = nameObj != null ? nameObj.toString() : ItemTranslator.getInstance().translateToChinese(armorMap.get(typeKey).toString());
                sb.append(display);

                String remainingKey = prefix + "_remaining_durability";
                String maxKey = prefix + "_max_durability";
                if (armorMap.containsKey(remainingKey) && armorMap.containsKey(maxKey)) {
                    int remaining = armorMap.get(remainingKey) instanceof Number n ? n.intValue() : 0;
                    int max = armorMap.get(maxKey) instanceof Number n ? n.intValue() : 0;
                    sb.append(I18nService.tr(" [耐久:{}/{}]", remaining, max));
                }

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
     * 格式化药水效果集合（最多列 5 个，超出显示「等 N 个效果」）
     */
    public static String formatPotionEffects(Collection<?> effects) {
        if (effects == null || effects.isEmpty()) {
            return I18nService.tr("药水效果：无");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("药水效果："));
        int count = 0;

        for (Object obj : effects) {
            if (obj instanceof PotionEffect effect) {
                if (count > 0) sb.append("; ");

                String typeName = effect.getType().getName();
                sb.append(typeName);

                int amplifier = effect.getAmplifier() + 1;
                sb.append(I18nService.tr(" {}级", amplifier));

                int seconds = effect.getDuration() / 20;
                if (seconds >= 60) {
                    int minutes = seconds / 60;
                    int remainingSeconds = seconds % 60;
                    sb.append(I18nService.tr(" ({}分{}秒)", minutes, remainingSeconds));
                } else {
                    sb.append(I18nService.tr(" ({}秒)", seconds));
                }

                count++;
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
     * 格式化瞄准的方块（从 Map 读取）
     */
    public static String formatBlockFromMap(Map<?, ?> blockMap) {
        if (blockMap == null || blockMap.isEmpty()) {
            return I18nService.tr("瞄准方块：无（距离太远或没有方块）");
        }

        String materialName = (String) blockMap.get("block_type");
        if (materialName == null) {
            return I18nService.tr("瞄准方块：未知");
        }

        String chineseName = ItemTranslator.getInstance().translateToChinese(materialName);
        int x = blockMap.containsKey("x") ? ((Number) blockMap.get("x")).intValue() : 0;
        int y = blockMap.containsKey("y") ? ((Number) blockMap.get("y")).intValue() : 0;
        int z = blockMap.containsKey("z") ? ((Number) blockMap.get("z")).intValue() : 0;

        return I18nService.tr("瞄准方块：{}（位置：X={}, Y={}, Z={}）", chineseName, x, y, z);
    }

    /**
     * 格式化脚下方块（Folia Map 路径）
     */
    public static String formatFeetBlockFromMap(Map<?, ?> blockMap) {
        if (blockMap == null || blockMap.isEmpty()) {
            return I18nService.tr("脚下方块：未知");
        }
        String materialName = blockMap.get("block_type") != null ? blockMap.get("block_type").toString() : null;
        if (materialName == null) {
            return I18nService.tr("脚下方块：未知");
        }
        String chineseName = ItemTranslator.getInstance().translateToChinese(materialName);
        int x = blockMap.containsKey("x") ? ((Number) blockMap.get("x")).intValue() : 0;
        int y = blockMap.containsKey("y") ? ((Number) blockMap.get("y")).intValue() : 0;
        int z = blockMap.containsKey("z") ? ((Number) blockMap.get("z")).intValue() : 0;
        return I18nService.tr("脚下方块：{}（位置：X={}, Y={}, Z={}）", chineseName, x, y, z);
    }

    /**
     * 格式化上次受伤原因（Folia Map 路径）
     */
    public static String formatDamageFromMap(Map<?, ?> damageMap) {
        if (damageMap == null || damageMap.isEmpty()) {
            return I18nService.tr("无受伤记录");
        }
        String causeName = damageMap.get("damage_cause") != null ? damageMap.get("damage_cause").toString() : I18nService.tr("未知");
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
    public static String formatDamageEvent(EntityDamageEvent damageEvent) {
        String causeDisplay = formatDamageCause(damageEvent.getCause());
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("上次受伤：")).append(causeDisplay).append(I18nService.tr("（{} 伤害）", String.format("%.1f", damageEvent.getDamage())));
        if (damageEvent instanceof EntityDamageByEntityEvent byEntityEvent) {
            Entity damager = byEntityEvent.getDamager();
            String name;
            if (damager instanceof Player p) {
                name = p.getName();
            } else {
                if (damager instanceof LivingEntity m) {
                    m.getName();
                    name = m.getName();
                } else {
                    name = (damager.getType().name());
                }
            }
            sb.append(I18nService.tr("，攻击者：")).append(name);
        }
        return sb.toString();
    }

    /**
     * 将 DamageCause 枚举转为本地化描述
     */
    public static String formatDamageCause(EntityDamageEvent.DamageCause cause) {
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
     * 将 DamageCause 枚举名称（String）转为本地化描述（用于 Folia Map 路径，cause 以 name() 存储）
     */
    public static String formatDamageCauseByName(String causeName) {
        try {
            EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.valueOf(causeName);
            return formatDamageCause(cause);
        } catch (IllegalArgumentException e) {
            return causeName;
        }
    }

    /**
     * 格式化生物群系名称（PLAINS → Plains）
     */
    public static String formatBiomeByName(String biomeName) {
        if (biomeName == null) {
            return I18nService.tr("未知群系");
        }

        String displayName = biomeName.replace('_', ' ').toLowerCase();
        if (!displayName.isEmpty()) {
            displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
        }
        return I18nService.tr("生物群系：{}", displayName);
    }

    /**
     * 格式化背包占用情况（极轻量，仅显示格数）
     */
    public static String formatInventoryUsage(ItemStack[] contents, String label) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                count++;
            }
        }
        int empty = contents.length - count;
        return I18nService.tr("{}占用：{}/{} 格（空 {} 格）", label, count, contents.length, empty);
    }

    /**
     * 格式化背包/末影箱物品摘要（显示已用格数 + 物品列表）
     */
    public static String formatInventorySummary(ItemStack[] contents, String label) {
        int count = 0;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                if (count == 0) {
                    int total = 0;
                    for (ItemStack c : contents) {
                        if (c != null && c.getType() != Material.AIR) total++;
                    }
                    sb.append(label).append(I18nService.tr("物品（已用 {}/{} 格）：\n", total, contents.length));
                }
                ItemMeta meta = item.getItemMeta();
                String itemName;
                if (meta != null && meta.hasDisplayName()) {
                    itemName = meta.getDisplayName();
                } else {
                    itemName = ItemTranslator.getInstance().translateToChinese(item.getType().name());
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
     * 格式化当前打开的界面类型（InventoryType 枚举翻译）
     */
    public static String formatInventoryType(InventoryType type) {
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
     * 格式化背包/末影箱 Map（Folia 路径，从 {@code extractThreadSafeData} 返回的 Map 读取）
     *
     * @param label     文案前缀（「背包」/「末影箱」/「容器」/「{容器类型}」）
     * @param usageOnly true=仅显示格数（inventory_usage），false=显示物品列表
     */
    public static String formatInventoryFromMap(String label, boolean usageOnly, Map<?, ?> invMap) {
        if (invMap == null || invMap.isEmpty()) {
            return label + I18nService.tr("：空");
        }

        int itemCount = invMap.containsKey("item_count") ? ((Number) invMap.get("item_count")).intValue() : 0;
        int totalSlots = itemCount + (invMap.containsKey("empty_slots") ? ((Number) invMap.get("empty_slots")).intValue() : 0);

        if (usageOnly) {
            return I18nService.tr("{}占用：{}/{} 格（空 {} 格）", label, itemCount, totalSlots, totalSlots - itemCount);
        }

        Object itemsObj = invMap.get("items");
        if (!(itemsObj instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return label + I18nService.tr("：空");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(label).append(I18nService.tr("物品（已用 {}/{} 格）：\n", itemCount, totalSlots));

        for (Object obj : itemsList) {
            if (obj instanceof Map<?, ?> itemData) {
                int slot = itemData.containsKey("slot") ? ((Number) itemData.get("slot")).intValue() : -1;
                // item_name 仅有自定义名时存在；无则按 item_type 翻译展示
                String itemName = itemData.get("item_name") != null ? itemData.get("item_name").toString() : (itemData.get("item_type") != null ? ItemTranslator.getInstance().translateToChinese(itemData.get("item_type").toString()) : I18nService.tr("未知"));
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
    public static String formatRaids(Collection<?> raids) {
        if (raids == null || raids.isEmpty()) {
            return I18nService.tr("袭击事件：无");
        }

        int raidCount = raids.size();
        return I18nService.tr("当前正在进行 {} 个袭击", raidCount);
    }

    /**
     * 格式化 Boolean 类型结果，附加上下文描述
     *
     * @param apiId       API id（用于关键词匹配语义化文案）
     * @param displayName API 显示名（兜底文案前缀，可为 null）
     */
    public static String formatBooleanResult(String apiId, boolean value, String displayName) {
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

        if (displayName != null && !displayName.isEmpty()) {
            return displayName + I18nService.tr("：") + (value ? I18nService.tr("是") : I18nService.tr("否"));
        }

        return value ? I18nService.tr("是") : I18nService.tr("否");
    }

    /**
     * 提取 ItemStack 字段到 dataMap（Spigot 路径，字段集与原 extractDataFromResult 一致）
     *
     * <p>字段：item_type/item_amount/item_name[+enchantments/damage/max_durability/
     * remaining_durability/unbreakable/lore/custom_model_data/item_flags]</p>
     */
    public static void putItemStackFields(ItemStack itemStack, Map<String, Object> dataMap) {
        if (itemStack.getType() == Material.AIR) {
            return;
        }
        dataMap.put("item_type", itemStack.getType().name());
        dataMap.put("item_amount", itemStack.getAmount());

        // item_name 仅存自定义名；无自定义名时不写入，由 message 层按 item_type 翻译展示
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            dataMap.put("item_name", meta.getDisplayName());
        }

        if (meta != null) {
            if (meta.hasEnchants()) {
                Map<String, Integer> enchantments = new HashMap<>();
                meta.getEnchants().forEach((ench, level) -> enchantments.put(ench.getName(), level));
                dataMap.put("enchantments", enchantments);
            }

            if (meta instanceof Damageable damageable) {
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

            if (meta.hasLore()) {
                dataMap.put("lore", meta.getLore());
            }

            if (meta.hasCustomModelData()) {
                dataMap.put("custom_model_data", meta.getCustomModelData());
            }

            if (!meta.getItemFlags().isEmpty()) {
                dataMap.put("item_flags", meta.getItemFlags().stream().map(Enum::name).toList());
            }
        }
    }

    /**
     * 提取 ItemStack 字段到 dataMap（Folia 路径，字段集与原 extractThreadSafeData 一致）
     *
     * <p>相比 Spigot 路径额外包含 {@code attributes}（属性修饰词）。其余字段名一致。</p>
     */
    public static void putItemStackFieldsFolia(ItemStack itemStack, Map<String, Object> dataMap) {
        putItemStackFields(itemStack, dataMap);

        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasAttributeModifiers()) {
            Map<String, Object> attributes = new HashMap<>();
            meta.getAttributeModifiers().entries().forEach(entry -> {
                org.bukkit.attribute.Attribute attr = entry.getKey();
                org.bukkit.attribute.AttributeModifier modifier = entry.getValue();
                attributes.put(attr.name(), modifier.getAmount());
            });
            if (!attributes.isEmpty()) {
                dataMap.put("attributes", attributes);
            }
        }
    }

    /**
     * 提取 Location 字段到 dataMap（字段：x/y/z/yaw/pitch/world）
     */
    public static void putLocationFields(org.bukkit.Location location, Map<String, Object> dataMap) {
        dataMap.put("x", location.getX());
        dataMap.put("y", location.getY());
        dataMap.put("z", location.getZ());
        dataMap.put("yaw", location.getYaw());
        dataMap.put("pitch", location.getPitch());
        if (location.getWorld() != null) {
            dataMap.put("world", location.getWorld().getName());
        }
    }

    /**
     * 提取 Vector 字段到 dataMap（字段：x/y/z）
     */
    public static void putVectorFields(Vector vector, Map<String, Object> dataMap) {
        dataMap.put("x", vector.getX());
        dataMap.put("y", vector.getY());
        dataMap.put("z", vector.getZ());
    }

    /**
     * 提取 Block 字段到 dataMap（字段：block_type/x/y/z/world）
     */
    public static void putBlockFields(Block block, Map<String, Object> dataMap) {
        dataMap.put("block_type", block.getType().name());
        dataMap.put("x", block.getX());
        dataMap.put("y", block.getY());
        dataMap.put("z", block.getZ());
        dataMap.put("world", block.getWorld().getName());
    }

    /**
     * 提取 ItemStack[] 占用格数到 dataMap（极轻量，不读 ItemMeta；字段：item_count/empty_slots）
     */
    public static void putInventoryUsageFields(ItemStack[] contents, Map<String, Object> dataMap) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                count++;
            }
        }
        dataMap.put("item_count", count);
        dataMap.put("empty_slots", contents.length - count);
    }

    /**
     * 提取 ItemStack[] 物品摘要到 dataMap（仅名称+数量，不含附魔/耐久；字段：items(List<Map>)）
     */
    public static void putInventorySummaryFields(ItemStack[] contents, Map<String, Object> dataMap) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("slot", i);
                itemData.put("item_type", item.getType().name());
                // item_name 仅存自定义名；无自定义名时由 message 层按 item_type 翻译
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    itemData.put("item_name", meta.getDisplayName());
                }
                itemData.put("item_amount", item.getAmount());
                itemsList.add(itemData);
            }
        }
        dataMap.put("items", itemsList);
    }

    /**
     * 提取盔甲数组字段到 dataMap（Spigot 路径，字段名 {@code boots_*}/{@code leggings_*}/{@code chestplate_*}/{@code helmet_*}，
     * 每槽 _name/_type/_amount[/_enchantments/_max_durability/_remaining_durability]）
     *
     * <p>Bukkit 盔甲数组顺序：[靴子, 护腿, 胸甲, 头盔]</p>
     */
    public static void putArmorFields(ItemStack[] armorContents, Map<String, Object> dataMap) {
        String[] slotNames = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack item = armorContents[i];
            if (item != null && item.getType() != Material.AIR) {
                // *_name 仅存自定义名；无自定义名时由 message 层按 *_type 翻译
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    dataMap.put(slotNames[i] + "_name", meta.getDisplayName());
                }
                dataMap.put(slotNames[i] + "_type", item.getType().name());
                dataMap.put(slotNames[i] + "_amount", item.getAmount());
                if (meta != null && meta.hasEnchants()) {
                    Map<String, Integer> enchants = new HashMap<>();
                    meta.getEnchants().forEach((ench, level) -> enchants.put(ench.getKey().getKey().toUpperCase(), level));
                    dataMap.put(slotNames[i] + "_enchantments", enchants);
                }
                if (item.getType().getMaxDurability() > 0) {
                    dataMap.put(slotNames[i] + "_max_durability", (int) item.getType().getMaxDurability());
                    dataMap.put(slotNames[i] + "_remaining_durability", (int) (item.getType().getMaxDurability() - item.getDurability()));
                }
            }
        }
    }

    /**
     * 提取盔甲数组字段到 dataMap（Folia 路径，与 Spigot 路径字段一致，额外在无盔甲时写入 {@code empty:true}）
     */
    public static void putArmorFieldsFolia(ItemStack[] armorContents, Map<String, Object> dataMap) {
        String[] slotNames = {"boots", "leggings", "chestplate", "helmet"};
        boolean hasArmor = false;
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack item = armorContents[i];
            if (item != null && item.getType() != Material.AIR) {
                hasArmor = true;
                String prefix = slotNames[i];
                dataMap.put(prefix + "_type", item.getType().name());
                dataMap.put(prefix + "_amount", item.getAmount());
                // *_name 仅存自定义名；无自定义名时由 message 层按 *_type 翻译
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    dataMap.put(prefix + "_name", meta.getDisplayName());
                }
                if (meta != null && meta.hasEnchants()) {
                    Map<String, Integer> enchants = new HashMap<>();
                    meta.getEnchants().forEach((ench, level) -> enchants.put(ench.getKey().getKey().toUpperCase(), level));
                    dataMap.put(prefix + "_enchantments", enchants);
                }
                if (item.getType().getMaxDurability() > 0) {
                    dataMap.put(prefix + "_max_durability", (int) item.getType().getMaxDurability());
                    dataMap.put(prefix + "_remaining_durability", (int) (item.getType().getMaxDurability() - item.getDurability()));
                }
            }
        }
        if (!hasArmor) {
            dataMap.put("empty", true);
        }
    }

    /**
     * 提取药水效果集合字段到 dataMap（字段：effects(List<Map>: type/amplifier/duration_seconds), effect_count）
     *
     * <p>注意：amplifier 已 +1（0=I 级），duration 已 /20 转秒，与原 {@code extractDataFromResult} 行为一致。</p>
     */
    public static void putPotionEffectFields(Collection<?> potionEffects, Map<String, Object> dataMap) {
        List<Map<String, Object>> effectsList = new ArrayList<>();
        for (Object obj : potionEffects) {
            if (obj instanceof PotionEffect effect) {
                Map<String, Object> effectData = new HashMap<>();
                effectData.put("type", effect.getType().getName());
                effectData.put("amplifier", effect.getAmplifier() + 1);
                effectData.put("duration_seconds", effect.getDuration() / 20);
                effectsList.add(effectData);
            }
        }
        dataMap.put("effects", effectsList);
        dataMap.put("effect_count", effectsList.size());
    }

    /**
     * 提取上次受伤事件字段到 dataMap（Spigot 路径）。
     *
     * <p>字段与 {@link #putDamageFieldsFolia} 完全一致：damage_cause(枚举 name)/damage_amount/final_damage
     * [+damager_type/damager_name]。data 统一存结构化枚举名，message 由调用方经 formatDamageCause 转文案。</p>
     */
    public static void putDamageFields(EntityDamageEvent damageEvent, Map<String, Object> dataMap) {
        dataMap.put("damage_cause", damageEvent.getCause().name());
        dataMap.put("damage_amount", damageEvent.getDamage());
        dataMap.put("final_damage", damageEvent.getFinalDamage());
        if (damageEvent instanceof EntityDamageByEntityEvent byEntityEvent) {
            Entity damager = byEntityEvent.getDamager();
            dataMap.put("damager_type", damager.getType().name());
            if (damager instanceof Player attacker) {
                dataMap.put("damager_name", attacker.getName());
            } else if (damager instanceof LivingEntity livingMob) {
                livingMob.getName();
                dataMap.put("damager_name", livingMob.getName());
            }
        }
    }

    /**
     * 提取上次受伤事件字段到 dataMap（Folia 路径，区域线程安全提取）。
     *
     * <p>字段与 {@link #putDamageFields} 完全一致，两端 data 结构对齐。</p>
     */
    public static void putDamageFieldsFolia(EntityDamageEvent damageEvent, Map<String, Object> dataMap) {
        dataMap.put("damage_cause", damageEvent.getCause().name());
        dataMap.put("damage_amount", damageEvent.getDamage());
        dataMap.put("final_damage", damageEvent.getFinalDamage());
        if (damageEvent instanceof EntityDamageByEntityEvent byEntityEvent) {
            Entity damager = byEntityEvent.getDamager();
            dataMap.put("damager_type", damager.getType().name());
            if (damager instanceof Player attacker) {
                dataMap.put("damager_name", attacker.getName());
            } else if (damager instanceof LivingEntity livingMob) {
                livingMob.getName();
                dataMap.put("damager_name", livingMob.getName());
            }
        }
    }
}
