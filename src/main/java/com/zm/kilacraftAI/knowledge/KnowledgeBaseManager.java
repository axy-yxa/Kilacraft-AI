package com.zm.kilacraftAI.knowledge;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.PluginLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库管理器
 *
 * <p>负责加载和管理本地知识文件</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class KnowledgeBaseManager {

    private final KilacraftAI plugin;
    private final Path knowledgeDir;
    private final Map<String, String> knowledgeCache;           // 原始文件内容缓存
    private final Map<String, List<String>> chunkCache;         // 分段后内容缓存

    public KnowledgeBaseManager(KilacraftAI plugin, String dataFolderPath) {
        this.plugin = plugin;
        this.knowledgeDir = Paths.get(dataFolderPath, "knowledge");
        this.knowledgeCache = new HashMap<>();
        this.chunkCache = new HashMap<>();

        // 确保知识库目录存在
        initializeKnowledgeDirectory();
    }

    /**
     * 初始化知识库目录
     */
    private void initializeKnowledgeDirectory() {
        if (!Files.exists(knowledgeDir)) {
            try {
                Files.createDirectories(knowledgeDir);
                PluginLogger.info("知识库", "已创建知识库目录：" + knowledgeDir);

                // 创建示例文件
                createExampleKnowledgeFile();
            } catch (IOException e) {
                PluginLogger.error("知识库", "创建知识库目录失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 创建示例知识文件
     */
    private void createExampleKnowledgeFile() {
        String exampleContent = """
                # 服务器规则
                
                ## 基本规则
                
                1. **禁止作弊** - 不允许使用任何外挂、修改客户端或利用漏洞
                2. **友好交流** - 文明用语，禁止辱骂、歧视性言论
                3. **爱护环境** - 禁止恶意破坏（Griefing）其他玩家的建筑
                4. **禁止偷窃** - 未经允许不得拿取他人物品
                5. **遵守管理** - 服从管理员的合理指示
                
                ## 违规处理
                
                - 第一次警告
                - 第二次禁言 1 小时
                - 第三次封禁 24 小时
                - 严重违规永久封禁
                
                # 经济系统
                
                ## 货币名称
                
                **金币** - 服务器的主要流通货币
                
                ## 获得方式
                
                1. **挖矿** - 煤矿、铁矿、金矿等可以卖给系统商店
                2. **打怪** - 击败怪物有几率掉落金币
                3. **完成任务** - 在主城接取任务，完成后获得奖励
                4. **玩家交易** - 与其他玩家买卖物品
                5. **钓鱼** - 钓到稀有物品可以出售
                
                ## 银行系统
                
                - `/money` - 查看余额
                - `/deposit <金额>` - 存款
                - `/withdraw <金额>` - 取款
                
                # 传送命令
                
                ## 基础传送
                
                - `/spawn` - 传送到主城
                - `/warp <地点名>` - 传送到指定传送点
                - `/home` - 回家（需要设置床）
                - `/sethome` - 设置家的位置
                
                ## TPA 传送
                
                - `/tpa <玩家>` - 请求传送到玩家
                - `/tpahere <玩家>` - 请求玩家传送到你
                - `/tpaccept` - 接受传送请求
                - `/tpdeny` - 拒绝传送请求
                
                # 常见问题 FAQ
                
                ## Q: 如何获得领地？
                A: 在主城使用 `/res create <领地名>` 创建领地，需要支付 100 金币
                
                ## Q: 如何保护箱子？
                A: 使用金铲子右键箱子可以上锁，只有你能打开
                
                ## Q: 有哪些副本可玩？
                A:\s
                - 暮色森林（坐标：x=1000, z=-500）
                - 下界要塞（坐标：x=-2000, z=3000）
                - 末地城堡（坐标：x=5000, z=5000）
                
                ## Q: 如何加入帮派？
                A: 使用 `/guild create <帮派名>` 创建帮派或 `/guild join <帮派名>` 加入
                
                ## Q: 在线玩家很多怎么办？
                A: 服务器支持最多 100 人同时在线，如果卡顿可以尝试减少视距
                """;

        Path exampleFile = knowledgeDir.resolve("服务器知识.md");
        try {
            Files.writeString(exampleFile, exampleContent, StandardCharsets.UTF_8);
            PluginLogger.info("知识库", "已创建示例知识文件：" + exampleFile.getFileName());
        } catch (IOException e) {
            PluginLogger.error("知识库", "创建示例文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载所有知识文件到缓存（同时清空分段缓存）
     */
    public void loadAllKnowledge() {
        knowledgeCache.clear();
        chunkCache.clear();

        if (!Files.exists(knowledgeDir)) {
            PluginLogger.warn("知识库", "知识库目录不存在：" + knowledgeDir);
            return;
        }

        try (var stream = Files.walk(knowledgeDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".md") || path.toString().endsWith(".txt")).toList();

            for (Path file : files) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    // 使用文件名作为 key
                    String fileName = file.getFileName().toString();
                    knowledgeCache.put(fileName, content);
                    PluginLogger.info("知识库", "已加载知识文件：" + fileName);
                } catch (IOException e) {
                    PluginLogger.error("知识库", "加载知识文件失败: " + file + " - " + e.getMessage(), e);
                }
            }

            PluginLogger.info("知识库", "共加载 " + knowledgeCache.size() + " 个知识文件");
        } catch (IOException e) {
            PluginLogger.error("知识库", "遍历知识库目录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有知识内容（用于检索）
     *
     * @return 所有知识的 Map（文件名 -> 内容）
     */
    public Map<String, String> getAllKnowledge() {
        return new HashMap<>(knowledgeCache);
    }

    /**
     * 获取或创建分段缓存
     *
     * @param fileName 文件名
     * @param chunks   分段列表
     */
    public void setChunkCache(String fileName, List<String> chunks) {
        chunkCache.put(fileName, chunks);
    }

    /**
     * 获取分段缓存
     *
     * @param fileName 文件名
     * @return 分段列表，如果不存在则返回 null
     */
    public List<String> getChunkCache(String fileName) {
        return chunkCache.get(fileName);
    }

    /**
     * 重新加载知识库
     */
    public void reload() {
        PluginLogger.info("知识库", "正在重新加载知识库...");
        loadAllKnowledge();
        PluginLogger.info("知识库", "知识库加载完成");
    }

    /**
     * 获取知识库统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        int fileCount = knowledgeCache.size();
        int totalChars = knowledgeCache.values().stream().mapToInt(String::length).sum();

        return String.format("知识库：%d 个文件，共 %d 字符", fileCount, totalChars);
    }
}
