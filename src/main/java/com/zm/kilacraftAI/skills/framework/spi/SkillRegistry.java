package com.zm.kilacraftAI.skills.framework.spi;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.List;

/**
 * Skill 自动发现与注册中心
 *
 * <p>通过 Bukkit {@link org.bukkit.plugin.ServicesManager} 机制自动发现第三方插件注册的 {@link SkillProvider}。</p>
 * <p>KilacraftAI 会在启动后延迟扫描已注册的 SkillProvider，并注册到 {@link SkillManager}。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-04
 */
public class SkillRegistry {

    private final KilacraftAI plugin;
    private final SkillManager skillManager;

    public SkillRegistry(KilacraftAI plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    /**
     * 扫描并注册所有第三方 SkillProvider 提供的 Skill
     * <p>在服务器启动完成后延迟调用（延迟 20 tick），确保第三方插件已完成 onEnable。</p>
     *
     * @return 成功注册的第三方 Skill 数量
     */
    public int discoverAndRegister() {
        Collection<RegisteredServiceProvider<SkillProvider>> registrations = plugin.getServer().getServicesManager().getRegistrations(SkillProvider.class);

        if (registrations.isEmpty()) {
            PluginLogger.info("技能注册", "未发现第三方 SkillProvider");
            return 0;
        }

        int registeredCount = 0;
        for (RegisteredServiceProvider<SkillProvider> registration : registrations) {
            SkillProvider provider = registration.getProvider();
            org.bukkit.plugin.Plugin sourcePlugin = registration.getPlugin();

            List<Skill> skills = provider.getSkills();
            if (skills == null || skills.isEmpty()) {
                continue;
            }

            for (Skill skill : skills) {
                try {
                    // 检查 Skill 是否已注册（防止同名覆盖）
                    Skill existingSkill = skillManager.getSkill(skill.getName());
                    if (existingSkill == null) {
                        skillManager.registerSkill(skill);
                        registeredCount++;
                        PluginLogger.info("技能注册", String.format("发现并注册第三方技能：%s (来自 %s)", skill.getName(), sourcePlugin.getName()));
                    } else {
                        // 已存在内置 Skill，不覆盖，跳过
                        PluginLogger.warn("技能注册", String.format("跳过第三方技能 '%s'（来自 %s）：名称与已注册技能冲突", skill.getName(), sourcePlugin.getName()));
                    }
                } catch (Exception e) {
                    PluginLogger.warn("技能注册", String.format("注册第三方技能失败:%s (来自 %s): %s", skill.getName(), sourcePlugin.getName(), e.getMessage()), e);
                }
            }
        }

        if (registeredCount > 0) {
            PluginLogger.info("技能注册", String.format("共发现并注册 %d 个第三方技能", registeredCount));
        } else {
            PluginLogger.info("技能注册", "未发现有效的第三方 SkillProvider");
        }

        return registeredCount;
    }
}
