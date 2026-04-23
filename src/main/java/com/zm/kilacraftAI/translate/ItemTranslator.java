package com.zm.kilacraftAI.translate;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品名称翻译管理器
 *
 * <p>负责加载和管理 MC 原版物品的中英文对照表</p>
 * <p>从 resources/translate/items_CN.yml 加载，无需生成到插件数据目录</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class ItemTranslator {

    /**
     * 获取实例
     */
    @Getter
    private static ItemTranslator instance;

    // 中文 -> 英文 (用于查询时翻译)
    private final Map<String, String> chineseToEnglish;

    // 英文 -> 中文 (用于展示时翻译)
    private final Map<String, String> englishToChinese;

    private boolean loaded = false;

    public ItemTranslator() {
        this.chineseToEnglish = new ConcurrentHashMap<>();
        this.englishToChinese = new ConcurrentHashMap<>();
        instance = this;
    }

    /**
     * 加载物品翻译表
     */
    public void loadTranslationTable() {
        // 确保内置翻译表不重复加载
        if (loaded) {
            return;
        }

        try {
            // 从 resources/translate/items_CN.yml 加载
            InputStream inputStream = KilacraftAI.getInstance().getResource("internal/translate/items_CN.yml");

            if (inputStream == null) {
                PluginLogger.error("物品翻译", "无法找到 internal/translate/items_CN.yml 文件");
                return;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            int count = 0;
            // 遍历配置中的所有键值对
            for (String key : config.getKeys(false)) {
                String chineseName = config.getString(key);

                if (chineseName != null && !chineseName.isEmpty()) {
                    // 双向映射
                    englishToChinese.put(key, chineseName);
                    chineseToEnglish.put(chineseName, key);
                    count++;
                }
            }

            PluginLogger.info("物品翻译", "已加载 {} 个物品翻译", count);
            loaded = true;

        } catch (Exception e) {
            PluginLogger.error("物品翻译", I18nService.tr("加载物品翻译表失败: {}", e.getMessage()), e);
        }
    }

    /**
     * 将中文物品名称翻译成英文
     *
     * @param chineseName 中文名称
     * @return 英文名称，如果找不到则返回原值
     */
    public String translateToEnglish(String chineseName) {
        if (chineseName == null || chineseName.isEmpty()) {
            return chineseName;
        }

        // 直接查找
        String english = chineseToEnglish.get(chineseName);
        if (english != null) {
            return english;
        }

        // 尝试模糊匹配（忽略大小写）
        for (Map.Entry<String, String> entry : chineseToEnglish.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(chineseName)) {
                return entry.getValue();
            }
        }

        // 找不到翻译，返回原值（可能是英文或其他语言）
        return chineseName;
    }

    /**
     * 将英文物品名称翻译成中文
     *
     * <p>在英文模式下直接返回英文原名（英文不需要翻译为英文）</p>
     *
     * @param englishName 英文名称
     * @return 中文名称，如果找不到或英文模式则返回原值
     */
    public String translateToChinese(String englishName) {
        if (englishName == null || englishName.isEmpty()) {
            return englishName;
        }

        // 英文模式下不需要翻译为中文，直接返回英文原名
        if (!I18nService.isZh()) {
            return englishName;
        }

        // 直接查找
        String chinese = englishToChinese.get(englishName);
        if (chinese != null) {
            return chinese;
        }

        // 找不到翻译，返回原值
        return englishName;
    }

}
