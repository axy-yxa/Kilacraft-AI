package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * 配置资源文件工具类
 * 统一处理内置配置文件/知识文件的拷贝逻辑
 *
 * @author Zm_Mmm
 * @since 2026-04-17
 */
public class ConfigResourceUtil {

    private static final String LOG_MODULE = "配置管理";

    /**
     * 私有构造函数,防止实例化
     */
    private ConfigResourceUtil() {
        throw new UnsupportedOperationException(I18nService.tr("工具类不允许实例化"));
    }

    /**
     * 保存内置资源文件(如果目标文件不存在)
     *
     * <p>标准行为: 仅当目标文件不存在时,从JAR包中拷贝资源文件</p>
     *
     * @param plugin       插件实例
     * @param resourcePath 资源文件路径(相对于resources目录)
     */
    public static void saveDefaultResource(KilacraftAI plugin, String resourcePath) {
        // 计算目标文件路径
        File targetFile = new File(plugin.getDataFolder(), resourcePath);

        // 文件已存在,不覆盖
        if (targetFile.exists()) {
            return;
        }

        // 确保父目录存在
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 拷贝资源文件
        try {
            plugin.saveResource(resourcePath, false);
            PluginLogger.info(LOG_MODULE, "已创建默认配置文件: {}", targetFile.getName());
        } catch (Exception e) {
            PluginLogger.error(LOG_MODULE, I18nService.tr("创建配置文件失败: {}", resourcePath), e);
        }
    }

    /**
     * 保存内置资源文件到指定目录(如果目标文件不存在)
     *
     * <p>适用于非标准路径的资源文件(如 skills/bukkit/apis.yml)</p>
     *
     * @param plugin       插件实例
     * @param resourcePath 资源文件路径(相对于resources目录)
     * @param targetDir    目标目录(如 skills/bukkit/)
     */
    public static void saveDefaultResourceToDir(KilacraftAI plugin, String resourcePath, File targetDir) {
        // 计算目标文件
        String fileName = resourcePath.contains("/") ? resourcePath.substring(resourcePath.lastIndexOf('/') + 1) : resourcePath;
        File targetFile = new File(targetDir, fileName);

        // 文件已存在,不覆盖
        if (targetFile.exists()) {
            return;
        }

        // 确保目标目录存在
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        // 拷贝资源文件
        try {
            plugin.saveResource(resourcePath, false);
            PluginLogger.info(LOG_MODULE, "已创建默认配置文件: {}", targetFile.getName());
        } catch (Exception e) {
            PluginLogger.error(LOG_MODULE, I18nService.tr("创建配置文件失败: {}", resourcePath), e);
        }
    }

    /**
     * 批量保存内置资源目录下的所有文件(仅当目标文件不存在时才复制)
     *
     * <p>扫描 JAR 包中指定资源目录下的所有文件，逐一调用 saveDefaultResource。</p>
     * <p>适用于 knowledge/ 等需要整体复制且后续可能新增文件的场景。</p>
     *
     * @param plugin      插件实例
     * @param resourceDir 资源目录路径(如 "knowledge")
     */
    public static void saveDefaultResourceDir(KilacraftAI plugin, String resourceDir) {
        saveDefaultResourceDir(plugin, resourceDir, Integer.MAX_VALUE);
    }

    /**
     * 批量保存内置资源目录下的文件(仅当目标文件不存在时才复制)
     *
     * <p>扫描 JAR 包中指定资源目录下的文件，逐一调用 saveDefaultResource。</p>
     * <p>maxDepth=1 表示只扫描根目录文件，不递归子目录（用于按语言过滤资源）。</p>
     *
     * @param plugin      插件实例
     * @param resourceDir 资源目录路径(如 "knowledge")
     * @param maxDepth    最大递归深度（1=仅根目录文件）
     */
    public static void saveDefaultResourceDir(KilacraftAI plugin, String resourceDir, int maxDepth) {
        var resource = plugin.getClass().getClassLoader().getResource(resourceDir);
        if (resource == null) {
            return;
        }

        try {
            URI uri = resource.toURI();
            try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path jarDir = fs.getPath(resourceDir);
                try (Stream<Path> stream = Files.walk(jarDir, maxDepth)) {
                    stream.filter(Files::isRegularFile).forEach(path -> {
                        String relative = jarDir.relativize(path).toString();
                        String resourcePath = resourceDir + "/" + relative.replace('\\', '/');
                        saveDefaultResource(plugin, resourcePath);
                    });
                }
            }
        } catch (URISyntaxException | IOException e) {
            PluginLogger.error(LOG_MODULE, I18nService.tr("扫描内置资源目录失败: {}", resourceDir), e);
        }
    }
}
