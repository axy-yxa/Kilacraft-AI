package com.zm.kilacraftAI.compat.mythicmobs;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.mythicmobs.placeholders.all.AIAnswerPlaceholder;
import com.zm.kilacraftAI.i18n.I18nService;
import io.lumine.mythic.core.utils.annotations.MythicPlaceholder;
import org.bukkit.Bukkit;

/**
 * MythicMobs 占位符管理器
 */
public class MythicMobsPlaceholderManager {

    private final KilacraftAI plugin;
    private boolean registered = false;

    public MythicMobsPlaceholderManager(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册所有自定义占位符
     */
    public void registerPlaceholders() {
        if (registered) {
            PluginLoggerUtil.warn("MythicMobs", I18nService.tr("占位符已经注册过了"));
            return;
        }

        try {
            // 检查 MythicMobs 是否已加载
            if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                PluginLoggerUtil.warn("MythicMobs", I18nService.tr("MythicMobs 未启用，跳过占位符注册"));
                return;
            }

            // 获取 MythicBukkit 实例
            var mythicApi = io.lumine.mythic.bukkit.MythicBukkit.inst();
            if (mythicApi == null) {
                throw new RuntimeException(I18nService.tr("无法获取 MythicBukkit 实例"));
            }

            // 获取 PlaceholderManager
            var placeholderManager = mythicApi.getPlaceholderManager();
            if (placeholderManager == null) {
                throw new RuntimeException(I18nService.tr("无法获取 PlaceholderManager"));
            }

            // 重要：对于 GenericPlaceholder，必须传入注解以便 MythicMobs 正确初始化
            MythicPlaceholder annotation = AIAnswerPlaceholder.class.getAnnotation(MythicPlaceholder.class);
            placeholderManager.register(AIAnswerPlaceholder.class, annotation);

            registered = true;
            PluginLoggerUtil.info("MythicMobs", I18nService.tr("已加载 MythicMobs 占位符"));
        } catch (Exception e) {
            PluginLoggerUtil.error("MythicMobs", I18nService.tr("占位符注册失败: {}", e.getMessage()), e);
        }
    }
}
