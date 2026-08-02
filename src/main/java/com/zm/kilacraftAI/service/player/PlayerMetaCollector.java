package com.zm.kilacraftAI.service.player;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 玩家实时元数据采集器：把 Bukkit Player 的内存字段格式化为带标签的结构化文本块，
 * 供 AI 调用链在构建 system prompt 时拼入，让 AI「此刻」就知道玩家位置/状态/装备等硬事实。\
 *
 * @author Zm_Mmm
 * @since 2026-06-29
 */
public final class PlayerMetaCollector {

    /**
     * 采集总耗时兜底阈值；正常情况下为微秒级，几乎永不触发。
     */
    private static final long TIMEOUT_NS = 50_000_000L;

    private PlayerMetaCollector() {
    }

    /**
     * 实时采集玩家结构化元数据，格式化为带标签的文本块。
     */
    public static String collect(Player player) {
        if (player == null || !player.isOnline()) {
            return "";
        }
        long start = System.nanoTime();
        String header = I18nService.tr("【玩家实时状态】");
        StringBuilder sb = new StringBuilder(320);
        sb.append(header);

        Section[] sections = {new Section("身份权限", () -> buildIdentitySection(player)), new Section("位置环境", () -> buildLocationSection(player)), new Section("生命状态", () -> buildVitalsSection(player)), new Section("装备", () -> buildEquipmentSection(player)), new Section("药水效果", () -> buildEffectsSection(player)), new Section("动作状态", () -> buildActionSection(player)),};
        for (Section section : sections) {
            // 超时兜底：跳过剩余分组，避免异常情况下拖延 system prompt 构建
            if (System.nanoTime() - start > TIMEOUT_NS) {
                PluginLoggerUtil.warn("玩家元数据", "采集耗时超过{}ms，已跳过剩余字段", TIMEOUT_NS / 1_000_000L);
                break;
            }
            try {
                String line = section.builder().get();
                if (line != null && !line.isEmpty()) {
                    sb.append("\n").append(line);
                }
            } catch (Exception e) {
                PluginLoggerUtil.warn("玩家元数据", "采集分组[{}]失败: {}", section.name(), e.getMessage());
            }
        }

        if (sb.length() <= header.length()) {
            return "";
        }
        return sb.toString();
    }

    /**
     * 身份与权限：玩家名称 + 游戏模式 + 是否管理员。
     */
    private static String buildIdentitySection(Player player) {
        String nameLine = I18nService.tr("玩家名称: {}", player.getName());
        String modeLine = I18nService.tr("游戏模式: {} | 管理员: {}", formatGameMode(player.getGameMode()), I18nService.tr(player.isOp() ? "是" : "否"));
        return nameLine + "\n" + modeLine;
    }

    /**
     * 位置与环境：世界+维度+难度+PVP、坐标+生物群系、白昼+天气。
     */
    private static String buildLocationSection(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();

        String worldLine = I18nService.tr("所在世界: {} ({}, 难度: {}, PVP: {})", world.getName(), formatEnvironment(world.getEnvironment()), formatDifficulty(world.getDifficulty()), I18nService.tr(world.getPVP() ? "开" : "关"));

        String biome = safeBiomeName(world, loc);
        String posLine = I18nService.tr("所在位置: X={}, Y={}, Z={} ({})", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);

        String envLine = I18nService.tr("环境信息: {} ({})", formatTimePhase(world.getTime()), formatWeather(world));

        return worldLine + "\n" + posLine + "\n" + envLine;
    }

    /**
     * 生命与生存：生命/吸收/饥饿/经验；在水中时附氧气剩余。
     */
    private static String buildVitalsSection(Player player) {
        String line = I18nService.tr("生命状态: {}/{}, 吸收 {}, 饥饿 {}, 经验 Lv.{}", (int) player.getHealth(), (int) player.getMaxHealth(), (int) player.getAbsorptionAmount(), player.getFoodLevel(), player.getLevel());
        if (player.isInWater()) {
            int airSec = Math.max(0, player.getRemainingAir()) / 20;
            line = line + " | " + I18nService.tr("水下, 剩余氧气 {} 秒", airSec);
        }
        return line;
    }

    /**
     * 装备：主手/副手/盔甲，可损坏物品带耐久。空手无装备时返回 null（该组跳过）。
     */
    private static String buildEquipmentSection(Player player) {
        PlayerInventory inv = player.getInventory();
        List<String> parts = new ArrayList<>(3);

        String main = formatItemStack(inv.getItemInMainHand());
        if (main != null) {
            parts.add(I18nService.tr("主手[{}]", main));
        }
        String off = formatItemStack(inv.getItemInOffHand());
        if (off != null) {
            parts.add(I18nService.tr("副手[{}]", off));
        }

        List<String> armor = new ArrayList<>(4);
        appendArmor(armor, inv.getHelmet());
        appendArmor(armor, inv.getChestplate());
        appendArmor(armor, inv.getLeggings());
        appendArmor(armor, inv.getBoots());
        if (!armor.isEmpty()) {
            parts.add(I18nService.tr("盔甲[{}]", String.join(", ", armor)));
        }

        if (parts.isEmpty()) {
            return null;
        }
        return I18nService.tr("装备信息: {}", String.join(", ", parts));
    }

    /**
     * 状态效果：药水列表，空则返回 null（该组跳过）。
     */
    private static String buildEffectsSection(Player player) {
        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(formatPotionEffect(effect));
        }
        if (effects.isEmpty()) {
            return null;
        }
        return I18nService.tr("药水效果: {}", String.join(", ", effects));
    }

    /**
     * 动作状态：飞行/鞘翅滑翔/游泳/着火，合并成一行；无激活项返回 null（该组跳过）。
     */
    private static String buildActionSection(Player player) {
        List<String> states = new ArrayList<>(4);
        if (player.isFlying()) {
            states.add(I18nService.tr("飞行中"));
        }
        if (player.isGliding()) {
            states.add(I18nService.tr("鞘翅滑翔中"));
        }
        if (player.isSwimming()) {
            states.add(I18nService.tr("游泳中"));
        }
        if (player.getFireTicks() > 0) {
            states.add(I18nService.tr("着火中"));
        }
        if (states.isEmpty()) {
            return null;
        }
        return I18nService.tr("动作状态: {}", String.join(", ", states));
    }

    private static void appendArmor(List<String> armor, ItemStack item) {
        String formatted = formatItemStack(item);
        if (formatted != null) {
            armor.add(formatted);
        }
    }

    /**
     * 格式化单个物品为 名称 (耐久 x/y)；空手/AIR 返回 null。
     * 优先用物品自定义名，否则走 ItemTranslator 翻译材质名。
     */
    private static String formatItemStack(ItemStack item) {
        if (item == null) {
            return null;
        }
        Material type = item.getType();
        if (type == Material.AIR) {
            return null;
        }
        String name = displayName(item);
        int max = type.getMaxDurability();
        if (max > 0) {
            int remaining = Math.max(0, max - item.getDurability());
            return name + " " + I18nService.tr("(耐久 {}/{})", remaining, max);
        }
        return name;
    }

    private static String displayName(ItemStack item) {
        try {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
        } catch (Exception ignored) {
            // 自定义名读取失败，回退到材质名
        }
        ItemTranslator translator = ItemTranslator.getInstance();
        return translator != null ? translator.translateToChinese(item.getType().name()) : item.getType().name();
    }

    private static String formatPotionEffect(PotionEffect effect) {
        PotionEffectType type = effect.getType();
        String name = type.getName();
        return name + " " + levelToText(effect.getAmplifier() + 1) + " " + I18nService.tr("(剩余 {})", formatDuration(effect.getDuration()));
    }

    private static String formatGameMode(GameMode gm) {
        if (gm == null) return "?";
        return switch (gm) {
            case SURVIVAL -> I18nService.tr("生存模式");
            case CREATIVE -> I18nService.tr("创造模式");
            case ADVENTURE -> I18nService.tr("冒险模式");
            case SPECTATOR -> I18nService.tr("旁观模式");
            default -> gm.name();
        };
    }

    private static String formatEnvironment(World.Environment env) {
        if (env == null) return "?";
        return switch (env) {
            case NORMAL -> I18nService.tr("主世界");
            case NETHER -> I18nService.tr("下界");
            case THE_END -> I18nService.tr("末地");
            case CUSTOM -> I18nService.tr("自定义");
            default -> env.name();
        };
    }

    private static String formatDifficulty(Difficulty d) {
        if (d == null) return "?";
        return switch (d) {
            case PEACEFUL -> I18nService.tr("和平");
            case EASY -> I18nService.tr("简单");
            case NORMAL -> I18nService.tr("普通");
            case HARD -> I18nService.tr("困难");
            default -> d.name();
        };
    }

    /**
     * 世界时间（0~24000）映射为四段可读白昼阶段，不展示游戏刻数值。
     */
    private static String formatTimePhase(long ticks) {
        long t = ((ticks % 24000L) + 24000L) % 24000L;
        if (t < 6000L) return I18nService.tr("清晨");
        if (t < 12000L) return I18nService.tr("正午");
        if (t < 18000L) return I18nService.tr("黄昏");
        return I18nService.tr("夜晚");
    }

    private static String formatWeather(World world) {
        if (world.isThundering()) return I18nService.tr("雷雨");
        if (world.hasStorm()) return I18nService.tr("雨天");
        return I18nService.tr("晴天");
    }

    private static String safeBiomeName(World world, Location loc) {
        try {
            return world.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).name();
        } catch (Exception e) {
            return "?";
        }
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0) return "∞";
        int totalSec = ticks / 20;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" + s : s);
    }

    /**
     * 等级转罗马数字（1~5），其余用阿拉伯数字；罗马数字通用无需翻译。
     */
    private static String levelToText(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private record Section(String name, Supplier<String> builder) {
    }
}
