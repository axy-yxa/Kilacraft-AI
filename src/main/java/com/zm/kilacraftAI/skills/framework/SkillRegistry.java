package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
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
            PluginLoggerUtil.info("技能注册", "未发现第三方 SkillProvider");
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
                    // SPI 兼容性预检：调用所有必须实现的方法，确保 Skill 实现了当前版本的接口
                    // 旧版 SPI 插件未实现新增方法时，会抛出 AbstractMethodError/NoSuchMethodError
                    skill.getName();
                    skill.getDescription();
                    skill.getRequiredPermission();

                    // 检查 Skill 是否已注册（防止同名覆盖）
                    Skill existingSkill = skillManager.getSkill(skill.getName());
                    if (existingSkill == null) {
                        skillManager.registerSkill(skill, sourcePlugin.getName());
                        registeredCount++;
                        PluginLoggerUtil.info("技能注册", "发现并注册第三方技能：{} (来自 {})", skill.getName(), sourcePlugin.getName());
                    } else {
                        // 已存在内置 Skill，不覆盖，跳过
                        PluginLoggerUtil.warn("技能注册", "跳过第三方技能 '{}'（来自 {}）：名称与已注册技能冲突", skill.getName(), sourcePlugin.getName());
                    }
                } catch (AbstractMethodError | NoSuchMethodError e) {
                    PluginLoggerUtil.warn("技能注册", I18nService.tr("跳过第三方技能 '{}'（来自 {}）：接口版本不兼容，开发者需更新依赖至 Kilacraft-Skill-API 2.0.2+ 后重新编译发布",
                            skill.getClass().getName(), sourcePlugin.getName()));
                } catch (Throwable e) {
                    PluginLoggerUtil.warn("技能注册", I18nService.tr("注册第三方技能失败:{} (来自 {}): {}", skill.getClass().getName(), sourcePlugin.getName(), e.getMessage()), e);
                }
            }
        }

        if (registeredCount > 0) {
            PluginLoggerUtil.info("技能注册", "共发现并注册 {} 个第三方技能", registeredCount);
        } else {
            PluginLoggerUtil.info("技能注册", "未发现有效的第三方 SkillProvider");
        }

        return registeredCount;
    }
}
