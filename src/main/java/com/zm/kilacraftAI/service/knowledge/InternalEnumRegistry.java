package com.zm.kilacraftAI.service.knowledge;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置枚举注册表
 *
 * <p>从 JAR 内部加载音效、粒子、统计等枚举数据，提供关键词模糊匹配能力。</p>
 * <p>取代知识库中枚举型文件（sounds_particles.md、statistics.md）的检索功能。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-27
 */
public class InternalEnumRegistry {

    @Getter
    private static InternalEnumRegistry instance;

    /**
     * 枚举名 → 关键词列表
     */
    private final Map<String, List<String>> soundEntries = new ConcurrentHashMap<>();
    private final Map<String, List<String>> particleEntries = new ConcurrentHashMap<>();
    private final Map<String, List<String>> statisticEntries = new ConcurrentHashMap<>();

    public InternalEnumRegistry() {
        instance = this;
    }

    /**
     * 加载所有内置枚举数据
     */
    public void loadAll() {
        loadSection("internal/enum/sounds_particles.yml", "sounds", soundEntries);
        loadSection("internal/enum/sounds_particles.yml", "particles", particleEntries);
        loadSection("internal/enum/statistics.yml", null, statisticEntries);

        PluginLoggerUtil.info("枚举注册", I18nService.tr("已加载 {} 个音效、{} 个粒子、{} 个统计枚举", soundEntries.size(), particleEntries.size(), statisticEntries.size()));
    }

    /**
     * 从 YAML 资源加载指定 section
     *
     * @param resourcePath JAR 内资源路径
     * @param section      子节点名（null 表示顶层）
     * @param target       目标 Map
     */
    private void loadSection(String resourcePath, String section, Map<String, List<String>> target) {
        try {
            InputStream is = KilacraftAI.getInstance().getResource(resourcePath);
            if (is == null) {
                PluginLoggerUtil.warn("枚举注册", I18nService.tr("找不到内置资源: {}", resourcePath));
                return;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

            // 如果指定了 section，进入子节点
            org.bukkit.configuration.ConfigurationSection sec = section != null ? config.getConfigurationSection(section) : config;
            if (sec == null) {
                return;
            }

            for (String key : sec.getKeys(false)) {
                String keywords = sec.getString(key);
                List<String> allKeywords;
                if (keywords != null && !keywords.isEmpty()) {
                    allKeywords = new ArrayList<>(Arrays.asList(keywords.split("\\s+")));
                } else {
                    allKeywords = new ArrayList<>();
                }

                // 非中文模式下，自动从枚举名提取英文 token 作为补充关键词（支持英文用户模糊匹配）
                if (!I18nService.isZh()) {
                    appendEnumNameTokens(key, allKeywords);
                }

                target.put(key, allKeywords);
            }
        } catch (Exception e) {
            PluginLoggerUtil.warn("枚举注册", I18nService.tr("加载内置枚举失败: {} - {}", resourcePath, e.getMessage()));
        }
    }

    /**
     * 解析音效名：先精确匹配，再模糊匹配
     *
     * @return 匹配到的枚举名，无匹配返回 null
     */
    public String resolveSound(String input) {
        return resolve(input, soundEntries);
    }

    /**
     * 解析粒子名：先精确匹配，再模糊匹配
     */
    public String resolveParticle(String input) {
        return resolve(input, particleEntries);
    }

    /**
     * 解析统计名：先精确匹配，再模糊匹配
     */
    public String resolveStatistic(String input) {
        return resolve(input, statisticEntries);
    }

    /**
     * 英文停用词：枚举名分割后无语义价值的通用片段，不作为关键词
     */
    private static final Set<String> ENUM_STOP_WORDS = Set.of("BLOCK", "ENTITY", "ITEM", "MUSIC", "UI", "EVENT", "AMBIENT", "DEATH", "HURT", "STEP", "FALL", "HIT", "BREAK", "PLACE", "CLOSE", "OPEN", "LOOP", "SHORT", "SMALL", "BIG", "LONG", "LAND", "WATER", "LAVA", "INSIDE", "ABOVE", "ADDITIONS", "MOOD", "TARGET", "CLICK", "OFF", "ON", "UPWARDS", "WHIRLPOOL", "COLUMN", "BUBBLE");


    /**
     * 解析枚举名：精确匹配 → 大写匹配 → 包含匹配 → 关键词模糊匹配
     */
    private String resolve(String input, Map<String, List<String>> entries) {
        if (input == null || input.isEmpty()) return null;
        String trimmed = input.trim();

        // 1. 精确匹配
        if (entries.containsKey(trimmed)) return trimmed;

        // 2. 大写匹配
        String upper = trimmed.toUpperCase();
        if (entries.containsKey(upper)) return upper;

        // 3. 包含匹配（LLM 可能输出带前缀但不完整的名称）
        for (String key : entries.keySet()) {
            if (key.equals(upper)) return key;
        }

        // 4. 模糊关键词匹配，返回得分最高的
        List<Map.Entry<String, Integer>> results = fuzzyMatch(trimmed, entries, 1);
        if (!results.isEmpty() && results.get(0).getValue() > 0) {
            return results.get(0).getKey();
        }

        return null;
    }

    /**
     * 模糊匹配算法
     *
     * <p>将输入分词，统计每个枚举的关键词命中数，按命中数降序排列。</p>
     */
    private List<Map.Entry<String, Integer>> fuzzyMatch(String input, Map<String, List<String>> entries, int limit) {
        if (input == null || input.isEmpty()) return Collections.emptyList();

        // 对输入分词
        List<String> inputTokens = tokenize(input);

        // 计算每个枚举的得分
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
            int score = computeScore(inputTokens, entry.getKey(), entry.getValue());
            if (score > 0) {
                scored.add(new AbstractMap.SimpleEntry<>(entry.getKey(), score));
            }
        }

        // 按得分降序
        scored.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        if (limit > 0 && scored.size() > limit) {
            return scored.subList(0, limit);
        }
        return scored;
    }

    /**
     * 计算匹配得分
     *
     * <p>枚举名本身完全匹配 +5，部分匹配 +2，关键词完全匹配 +3，包含匹配 +1</p>
     */
    private int computeScore(List<String> inputTokens, String enumName, List<String> keywords) {
        int score = 0;
        String enumUpper = enumName.toUpperCase();

        for (String token : inputTokens) {
            String tokenUpper = token.toUpperCase();

            // 枚举名匹配
            if (enumUpper.equals(tokenUpper)) {
                score += 5;
            } else if (enumUpper.contains(tokenUpper)) {
                score += 2;
            }

            // 关键词匹配
            for (String keyword : keywords) {
                if (keyword.equals(token) || keyword.equalsIgnoreCase(token)) {
                    score += 3;
                } else if (keyword.contains(token) || token.contains(keyword)) {
                    score += 1;
                }
            }
        }

        return score;
    }

    /**
     * 分词：按空格、标点分割，同时对连续中文字符按 2-gram 分词
     */
    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        // 按非字母数字中文字符分割
        String[] parts = input.split("[^\\w\\u4e00-\\u9fff]+");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            tokens.add(part);
            // 对纯中文且长度>=2的，提取 2-gram 子串增强匹配
            if (isChinese(part) && part.length() >= 2) {
                for (int i = 0; i <= part.length() - 2; i++) {
                    tokens.add(part.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean isChinese(String s) {
        for (char c : s.toCharArray()) {
            if (c < '\u4e00' || c > '\u9fff') return false;
        }
        return true;
    }

    /**
     * 从枚举名自动提取英文 token 作为补充关键词。
     *
     * <p>按 '_' 分割枚举名，过滤掉停用词（BLOCK、ENTITY、DEATH 等无区分度的通用片段），
     * 将有语义价值的片段转小写后去重追加到关键词列表。</p>
     *
     * <p>例如 ENTITY_CREEPER_PRIMED 会追加 [creeper, primed]，
     * 使英文用户输入 "creeper hiss" 时能通过 creeper 命中该条目。</p>
     *
     * @param enumName 枚举名（如 ENTITY_CREEPER_PRIMED）
     * @param keywords 已有的关键词列表（会被追加）
     */
    private void appendEnumNameTokens(String enumName, List<String> keywords) {
        String[] parts = enumName.split("_");
        Set<String> existing = new HashSet<>(keywords);
        for (String part : parts) {
            if (part.length() <= 1) continue;          // 跳过单字符（如 1, 2, 3）
            if (ENUM_STOP_WORDS.contains(part)) continue; // 跳过通用片段
            String lower = part.toLowerCase();
            if (!existing.contains(lower)) {
                keywords.add(lower);
                existing.add(lower);
            }
        }
    }
}
