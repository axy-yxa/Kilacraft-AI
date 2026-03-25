package com.zm.kilacraftAI;

import com.zm.kilacraftAI.api.DeepSeekAPI;
import com.zm.kilacraftAI.core.KilacraftCommand;
import com.zm.kilacraftAI.core.TabCompleter;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件入口
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:21:29
 */
@Getter
public final class KilacraftAI extends JavaPlugin {

    @Getter
    private static KilacraftAI instance;
    private ConfigManager configManager;
    private DeepSeekAPI deepSeekAPI;
    private ChatListener chatListener;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();
    
        // 初始化管理器
        configManager = new ConfigManager(this);
        deepSeekAPI = new DeepSeekAPI(configManager);
        chatListener = new ChatListener(this);
    
        // 注册命令
        var command = getCommand("kilacraft");
        if (command != null) {
            command.setExecutor(new KilacraftCommand(this));
            command.setTabCompleter(new TabCompleter());
        } else {
            getLogger().severe("无法注册命令：kilacraft，请检查 plugin.yml 配置");
        }
    
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(chatListener, this);

        // ASCII Art 启动标志
        getLogger().info("╻┏ ╻╻  ┏━┓┏━╸┏━┓┏━┓┏━╸╺┳╸   ┏━┓╻");
        getLogger().info("┣┻┓┃┃  ┣━┫┃  ┣┳┛┣━┫┣╸  ┃ ╺━╸┣━┫┃");
        getLogger().info("╹ ╹╹┗━╸╹ ╹┗━╸╹┗╸╹ ╹╹   ╹    ╹ ╹╹");
        getLogger().info("版本：" + getDescription().getVersion());
        getLogger().info("作者：Zm_Mmm");
        getLogger().info("状态：已启用 ✓");
    }

    @Override
    public void onDisable() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("  Kilacraft-AI 已停止运行");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

}
