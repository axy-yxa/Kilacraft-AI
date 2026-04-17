package com.zm.kilacraftAI.skills.framework.task;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务步骤 - 多步骤任务中的单个步骤
 */
@Getter
public class TaskStep {

    /**
     * 步骤 ID（如 step_1, step_2）
     */
    private final String id;

    /**
     * 技能名称
     */
    private final String skillName;

    /**
     * 动作
     */
    private final String action;

    /**
     * 实体（物品、数量等）
     */
    private final Map<String, String> entities;

    /**
     * 依赖的前置步骤 ID 列表
     */
    private final List<String> dependsOn;

    public TaskStep(String id, String skillName, String action,
                    Map<String, String> entities, List<String> dependsOn) {
        this.id = id;
        this.skillName = skillName;
        this.action = action;
        this.entities = entities != null ? entities : new HashMap<>();
        this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
    }
}
