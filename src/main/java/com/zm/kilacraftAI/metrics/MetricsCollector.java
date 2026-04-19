package com.zm.kilacraftAI.metrics;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计数据收集器
 *
 * <p>纯内存累加器，线程安全。
 * bStats 定时通过 getter 读取数据进行上报。</p>
 *
 * <p>设计原则：与业务代码零耦合，只做计数，不碰任何业务逻辑。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-18
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    // 技能调用计数：key = "SkillName:actionName"
    private final ConcurrentHashMap<String, AtomicLong> skillActionCounts = new ConcurrentHashMap<>();

    // 请求类型计数：key = "normal_chat" / "skill_execution"
    private final ConcurrentHashMap<String, AtomicLong> requestTypeCounts = new ConcurrentHashMap<>();

    // 挂机任务类型计数：key = "PLAYER_ONLINE_WATCH" / "CUSTOM" / ...
    private final ConcurrentHashMap<String, AtomicLong> afkTaskTypeCounts = new ConcurrentHashMap<>();

    /**
     * LLM 模型名
     */
    @Setter
    @Getter
    private volatile String llmModel;
    /**
     * 服务端类型
     */
    @Setter
    @Getter
    private volatile String serverType;

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
     * 记录挂机任务类型创建
     *
     * @param taskType 任务类型名称（如 "PLAYER_ONLINE_WATCH"）
     */
    public void recordAfkTaskType(String taskType) {
        afkTaskTypeCounts.computeIfAbsent(taskType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取技能调用次数快照
     *
     * @return 不可变 Map，key = "SkillName:actionName", value = 调用次数
     */
    public Map<String, Integer> getSkillActionSnapshot() {
        return getSnapshot(skillActionCounts);
    }

    /**
     * 获取请求类型次数快照
     */
    public Map<String, Integer> getRequestTypeSnapshot() {
        return getSnapshot(requestTypeCounts);
    }

    /**
     * 获取挂机任务类型次数快照
     */
    public Map<String, Integer> getAfkTaskTypeSnapshot() {
        return getSnapshot(afkTaskTypeCounts);
    }

    private Map<String, Integer> getSnapshot(ConcurrentHashMap<String, AtomicLong> counterMap) {
        if (counterMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        counterMap.forEach((key, counter) -> snapshot.put(key, counter.intValue()));
        return Collections.unmodifiableMap(snapshot);
    }
}
