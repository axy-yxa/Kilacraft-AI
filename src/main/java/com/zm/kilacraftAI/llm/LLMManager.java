package com.zm.kilacraftAI.llm;

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
    }

    /**
     * 刷新当前提供商的配置缓存
     *
     * <p>仅更新 volatile 缓存字段（API Key、URL、Model 等），不重建 HTTP 连接池。</p>
     * <p>连接池参数在构造时确定，热重载时不应销毁重建（会中断正在进行的 LLM 请求）。</p>
     */
    public void refreshProviderConfig() {
        if (currentProvider != null) {
            currentProvider.refreshConfigCache();
        } else {
            this.currentProvider = new GenericLLMProvider();
        }
    }

    /**
     * 关闭 LLM 提供商的资源
     */
    public void shutdownAll() {
        if (currentProvider != null) {
            currentProvider.shutdown();
        }
    }
}
