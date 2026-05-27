package com.zm.kilacraftAI.model.profile;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家画像数据模型
 *
 * <p>存储玩家的登录统计、行为偏好、LLM 分析结果等画像信息。</p>
 *
 * @author Zm_Mmm
 */
@Getter
@Setter
@Builder
public class PlayerProfile {

    /**
     * 玩家 UUID（主键）
     */
    private final UUID uuid;

    /**
     * 玩家名称（每次登录更新）
     */
    private String name;

    /**
     * 首次登录时间戳（ms）
     */
    private long firstLogin;

    /**
     * 最近登录时间戳（ms）
     */
    private long lastLogin;

    /**
     * 最近登出时间戳（ms，退出时更新）
     */
    private long lastLogout;

    /**
     * 累计登录次数
     */
    @Builder.Default
    private int loginCount = 0;

    /**
     * 累计在线时长（ms，每次登出时增量更新）
     */
    @Builder.Default
    private long totalPlaytimeMs = 0;

    /**
     * 最近登出时所在世界名
     */
    private String lastWorld;

    /**
     * 最近登出 X 坐标
     */
    @Builder.Default
    private double lastX = 0;

    /**
     * 最近登出 Y 坐标
     */
    @Builder.Default
    private double lastY = 0;

    /**
     * 最近登出 Z 坐标
     */
    @Builder.Default
    private double lastZ = 0;

    /**
     * 最近一次 AI 问候时间戳（ms）
     */
    @Builder.Default
    private long lastGreetingTime = 0;

    /**
     * 最近一次 LLM 画像分析时间戳（ms，控制滑动窗口分析间隔）
     */
    @Builder.Default
    private long profileAnalyzedAt = 0;

    /**
     * LLM 分析扩展数据（JSON 平铺存储）
     *
     * <p>由 ProfileAnalysisService 异步写入，包含 LLM 从对话历史中提取的行为特征，
     * 如 playstyle/personality/preferences 等。读取方通过 key 按需取值。</p>
     */
    private Map<String, Object> extendedData;

    /**
     * 累加在线时长（增量策略，避免一次性计算丢失）
     *
     * @param sessionDurationMs 本次会话时长（ms）
     */
    public void addPlaytime(long sessionDurationMs) {
        this.totalPlaytimeMs += sessionDurationMs;
    }

    /**
     * 更新登录信息
     *
     * @param name 玩家名
     */
    public void updateLogin(String name) {
        this.name = name;
        this.lastLogin = System.currentTimeMillis();
        this.loginCount++;
    }

    /**
     * 更新登出信息（含坐标）
     *
     * @param world 登出时所在世界
     * @param x     X 坐标
     * @param y     Y 坐标
     * @param z     Z 坐标
     */
    public void updateLogout(String world, double x, double y, double z) {
        this.lastLogout = System.currentTimeMillis();
        this.lastWorld = world;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    /**
     * 计算本次会话时长
     *
     * @return 会话时长（ms）
     */
    public long calculateSessionDuration() {
        if (lastLogin <= 0) return 0;
        long end = lastLogout > 0 ? lastLogout : System.currentTimeMillis();
        return Math.max(0, end - lastLogin);
    }
}
