package com.zm.kilacraftAI.handler;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.MessageUtil;

/**
 * AI 响应处理器抽象基类
 * 
 * <p>提供公共的默认实现，减少子类重复代码</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public abstract class BaseResponseHandler implements AIResponseHandler {
    
    protected final KilacraftAI plugin;
    
    public BaseResponseHandler() {
        this.plugin = KilacraftAI.getInstance();
    }
    
    @Override
    public void showResponse(String response) {
        sendMessage(MessageUtil.getAIPrefix() + response);
    }
    
    @Override
    public void showStreamChunk(String chunk, String currentMessage) {
        // 流式模式下每收到一个片段就显示
        sendMessage(MessageUtil.getAIPrefix() + currentMessage);
    }
    
    @Override
    public void handleError(String errorMessage) {
        sendMessage(errorMessage);
    }
    
    @Override
    public boolean isStreamOutputEnabled() {
        // TODO 现阶段不支持流式输出，未来会在付费版本中扩展
        return false;
        /*
        if (plugin == null) {
            return false; // 默认关闭流式输出
        }
        return plugin.getConfigManager().isEnableStreamOutput();
        */
    }
    
    /**
     * 发送消息到目标接收者
     * 
     * @param message 要发送的消息
     */
    protected abstract void sendMessage(String message);
}
