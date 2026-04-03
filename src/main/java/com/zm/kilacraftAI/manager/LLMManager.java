package com.zm.kilacraftAI.manager;

import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.api.provider.GenericLLMProvider;
import com.zm.kilacraftAI.KilacraftAI;
import lombok.Getter;

/**
 * LLM 提供商管理器
 * <p>
 * 统一管理通用 LLM 提供商，支持配置驱动的多厂商切换
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class LLMManager {

    private final KilacraftAI plugin;
    /**
     * 当前使用的 LLM 提供商
     */
    @Getter
    private LLMProvider currentProvider;

    public LLMManager() {
        this.plugin = KilacraftAI.getInstance();
        initializeProvider();
    }

    /**
     * 初始化通用 LLM 提供商
     */
    private void initializeProvider() {
        this.currentProvider = new GenericLLMProvider();
        plugin.getLogger().info("[LLMManager] 已初始化通用 LLM 提供商");
    }

    /**
     * 刷新当前提供商的配置缓存
     */
    public void refreshProviderConfig() {
        if (currentProvider != null) {
            currentProvider.shutdown();
            currentProvider = null;
        }
        this.currentProvider = new GenericLLMProvider();
        plugin.getLogger().info("[LLMManager] 已刷新 LLM 提供商配置");
    }

    /**
     * 关闭 LLM 提供商的资源
     */
    public void shutdownAll() {
        plugin.getLogger().info("[LLMManager] 正在关闭 LLM 提供商...");
        if (currentProvider != null) {
            currentProvider.shutdown();
            plugin.getLogger().info("[LLMManager] 已关闭 LLM 提供商");
        }
        plugin.getLogger().info("[LLMManager] LLM 提供商已关闭");
    }
}
