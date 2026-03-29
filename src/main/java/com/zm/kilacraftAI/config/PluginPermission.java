package com.zm.kilacraftAI.config;

/**
 * 权限枚举类
 * 统一管理所有权限节点，避免硬编码字符串
 *
 * @author Zm_Mmm
 * @since 2026-03-29
 */
public enum PluginPermission {
    
    /**
     * 清除自己的对话历史
     */
    CLEAR_SELF("kilacraft.clear.self"),
    
    /**
     * 清除其他玩家的对话历史
     */
    CLEAR_OTHER("kilacraft.clear.other"),
    
    /**
     * 重载配置
     */
    RELOAD("kilacraft.reload"),
    
    /**
     * 管理知识库
     */
    KNOWLEDGE("kilacraft.knowledge"),
    
    /**
     * 管理人格配置
     */
    PERSONALITIES("kilacraft.personalities");
    
    private final String node;
    
    PluginPermission(String node) {
        this.node = node;
    }
    
    /**
     * 获取权限节点字符串
     *
     * @return 权限节点
     */
    public String getNode() {
        return node;
    }
    
    /**
     * 检查命令发送者是否拥有此权限
     *
     * @param sender 命令发送者
     * @return 是否拥有权限
     */
    public boolean hasPermission(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission(node);
    }
}
