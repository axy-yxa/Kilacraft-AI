package com.zm.kilacraftAI.metrics;

import com.google.gson.Gson;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计数据收集器
 *
 * @author Zm_Mmm
 * @since 2026-04-18
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    /**
     * Top N 统计数量
     */
    private static final int TOP_N_SKILL_ACTION = 10;

    // 技能调用计数：key = "SkillName:actionName"
    private final ConcurrentHashMap<String, AtomicLong> skillActionCounts = new ConcurrentHashMap<>();

    // 请求类型计数：key = "normal_chat" / "skill_execution"
    private final ConcurrentHashMap<String, AtomicLong> requestTypeCounts = new ConcurrentHashMap<>();

    // Skill 来源计数：key = "built_in" / "third_party"
    private final ConcurrentHashMap<String, AtomicLong> skillSourceCounts = new ConcurrentHashMap<>();

    /**
     * LLM 模型名
     */
    @Setter
    @Getter
    private volatile String llmModel;

    /**
     * 数据库类型（H2 / MYSQL）
     */
    @Setter
    @Getter
    private volatile String databaseType;

    /**
     * 流式输出是否启用
     */
    @Setter
    @Getter
    private volatile String streamingEnabled;

    /**
     * 全局默认输出载体
     */
    @Setter
    @Getter
    private volatile String outputChannel;

    /**
     * Embedding 模型名
     */
    @Setter
    @Getter
    private volatile String embeddingModel;

    /**
     * 推理模型名
     */
    @Setter
    @Getter
    private volatile String thinkingModel;

    /**
     * 所有 Skill 的元信息列表
     * 动态生成，避免第三方 Skill 延迟注册导致的数据不完整
     */
    @Setter
    private volatile SkillManager skillManager;

    private MetricsCollector() {
    }

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 记录技能调用
     *
     * @param skillName 技能名称（如 "GenericBukkitAPISkill"）
     * @param action    动作名称（如 "get_player_health"）
     */
    public void recordSkillAction(String skillName, String action) {
        String key = skillName + ":" + (action != null ? action : "unknown");
        skillActionCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录请求类型
     *
     * @param type "normal_chat" 或 "skill_execution"
     */
    public void recordRequestType(String type) {
        requestTypeCounts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录 Skill 来源
     *
     * @param skill Skill 实例
     */
    public void recordSkillSource(Skill skill) {
        String source = SkillManager.isThirdPartySkill(skill) ? "third_party" : "built_in";
        skillSourceCounts.computeIfAbsent(source, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取技能调用次数快照（Top 10）
     *
     * @return 不可变 Map，按调用次数降序排列，最多 10 条
     */
    public Map<String, Integer> getSkillActionSnapshot() {
        return getTopNSnapshot(skillActionCounts, TOP_N_SKILL_ACTION);
    }

    /**
     * 获取 Top N 排序快照
     *
     * @param counterMap 计数器 Map
     * @param topN       Top N 数量
     * @return 按调用次数降序排列的不可变 Map
     */
    private Map<String, Integer> getTopNSnapshot(ConcurrentHashMap<String, AtomicLong> counterMap, int topN) {

        if (counterMap.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 转成 List
        List<Map.Entry<String, AtomicLong>> entries = new ArrayList<>(counterMap.entrySet());

        // 2. 排序（按调用次数降序）
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));

        // 3. 取 Top N
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, AtomicLong> entry : entries) {
            if (count >= topN) break;
            snapshot.put(entry.getKey(), entry.getValue().intValue());
            count++;
        }

        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * 获取请求类型次数快照
     */
    public Map<String, Integer> getRequestTypeSnapshot() {
        return getSnapshot(requestTypeCounts);
    }

    /**
     * 获取 Skill 来源次数快照
     */
    public Map<String, Integer> getSkillSourceSnapshot() {
        return getSnapshot(skillSourceCounts);
    }

    /**
     * 获取所有 Skill 的元信息 JSON
     * 每次 bStats 读取时动态生成，确保包含最新注册的第三方 Skill
     *
     * @return JSON 字符串
     */
    public String getAllSkillsJson() {
        if (skillManager == null) {
            return "[]";
        }
        List<SkillInfo> skills = skillManager.getAllSkillInfoList();
        return new Gson().toJson(skills);
    }

    /**
     * 获取原始快照（不过滤）
     */
    private Map<String, Integer> getSnapshot(ConcurrentHashMap<String, AtomicLong> counterMap) {
        if (counterMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        counterMap.forEach((key, counter) -> snapshot.put(key, counter.intValue()));
        return Collections.unmodifiableMap(snapshot);
    }
}
