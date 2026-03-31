package com.zm.kilacraftAI.knowledge;

import com.zm.kilacraftAI.KilacraftAI;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

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
     * 获取知识库目录路径
     *
     * @return 知识库目录的 Path 对象
     */
    public Path getKnowledgeDirectory() {
        return knowledgeDir;
    }

    /**
     * 初始化知识库目录
     */
    private void initializeKnowledgeDirectory() {
        if (!Files.exists(knowledgeDir)) {
            try {
                Files.createDirectories(knowledgeDir);
                plugin.getLogger().info("已创建知识库目录：" + knowledgeDir);

                // 创建示例文件
                createExampleKnowledgeFile();
            } catch (IOException e) {
                plugin.getLogger().severe("创建知识库目录失败：" + e.getMessage());
            }
        }

        plugin.getLogger().info("知识库路径：" + knowledgeDir.toAbsolutePath());
    }

    /**
     * 创建示例知识文件
     */
    private void createExampleKnowledgeFile() {
        String exampleContent = "# 服务器规则\n" +
                "\n" +
                "## 基本规则\n" +
                "\n" +
                "1. **禁止作弊** - 不允许使用任何外挂、修改客户端或利用漏洞\n" +
                "2. **友好交流** - 文明用语，禁止辱骂、歧视性言论\n" +
                "3. **爱护环境** - 禁止恶意破坏（Griefing）其他玩家的建筑\n" +
                "4. **禁止偷窃** - 未经允许不得拿取他人物品\n" +
                "5. **遵守管理** - 服从管理员的合理指示\n" +
                "\n" +
                "## 违规处理\n" +
                "\n" +
                "- 第一次警告\n" +
                "- 第二次禁言 1 小时\n" +
                "- 第三次封禁 24 小时\n" +
                "- 严重违规永久封禁\n" +
                "\n" +
                "# 经济系统\n" +
                "\n" +
                "## 货币名称\n" +
                "\n" +
                "**金币** - 服务器的主要流通货币\n" +
                "\n" +
                "## 获得方式\n" +
                "\n" +
                "1. **挖矿** - 煤矿、铁矿、金矿等可以卖给系统商店\n" +
                "2. **打怪** - 击败怪物有几率掉落金币\n" +
                "3. **完成任务** - 在主城接取任务，完成后获得奖励\n" +
                "4. **玩家交易** - 与其他玩家买卖物品\n" +
                "5. **钓鱼** - 钓到稀有物品可以出售\n" +
                "\n" +
                "## 银行系统\n" +
                "\n" +
                "- `/money` - 查看余额\n" +
                "- `/deposit <金额>` - 存款\n" +
                "- `/withdraw <金额>` - 取款\n" +
                "\n" +
                "# 传送命令\n" +
                "\n" +
                "## 基础传送\n" +
                "\n" +
                "- `/spawn` - 传送到主城\n" +
                "- `/warp <地点名>` - 传送到指定传送点\n" +
                "- `/home` - 回家（需要设置床）\n" +
                "- `/sethome` - 设置家的位置\n" +
                "\n" +
                "## TPA 传送\n" +
                "\n" +
                "- `/tpa <玩家>` - 请求传送到玩家\n" +
                "- `/tpahere <玩家>` - 请求玩家传送到你\n" +
                "- `/tpaccept` - 接受传送请求\n" +
                "- `/tpdeny` - 拒绝传送请求\n" +
                "\n" +
                "# 常见问题 FAQ\n" +
                "\n" +
                "## Q: 如何获得领地？\n" +
                "A: 在主城使用 `/res create <领地名>` 创建领地，需要支付 100 金币\n" +
                "\n" +
                "## Q: 如何保护箱子？\n" +
                "A: 使用金铲子右键箱子可以上锁，只有你能打开\n" +
                "\n" +
                "## Q: 有哪些副本可玩？\n" +
                "A: \n" +
                "- 暮色森林（坐标：x=1000, z=-500）\n" +
                "- 下界要塞（坐标：x=-2000, z=3000）\n" +
                "- 末地城堡（坐标：x=5000, z=5000）\n" +
                "\n" +
                "## Q: 如何加入帮派？\n" +
                "A: 使用 `/guild create <帮派名>` 创建帮派或 `/guild join <帮派名>` 加入\n" +
                "\n" +
                "## Q: 在线玩家很多怎么办？\n" +
                "A: 服务器支持最多 100 人同时在线，如果卡顿可以尝试减少视距\n";

        Path exampleFile = knowledgeDir.resolve("服务器知识.md");
        try {
            Files.writeString(exampleFile, exampleContent, StandardCharsets.UTF_8);
            plugin.getLogger().info("已创建示例知识文件：" + exampleFile.getFileName());
        } catch (IOException e) {
            plugin.getLogger().severe("创建示例文件失败：" + e.getMessage());
        }
    }

    /**
     * 加载所有知识文件到缓存（同时清空分段缓存）
     */
    public void loadAllKnowledge() {
        knowledgeCache.clear();
        chunkCache.clear();  // 清空分段缓存，重新加载

        if (!Files.exists(knowledgeDir)) {
            plugin.getLogger().warning("知识库目录不存在：" + knowledgeDir);
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
                    plugin.getLogger().info("已加载知识文件：" + fileName);
                } catch (IOException e) {
                    plugin.getLogger().severe("加载知识文件失败：" + file + " - " + e.getMessage());
                }
            }

            plugin.getLogger().info("共加载 " + knowledgeCache.size() + " 个知识文件");
        } catch (IOException e) {
            plugin.getLogger().severe("遍历知识库目录失败：" + e.getMessage());
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
     * 输出 INFO 级别日志（使用 Bukkit）
     *
     * @param message 日志消息
     */
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    /**
     * 输出 WARNING 级别日志（使用 Bukkit）
     *
     * @param message 日志消息
     */
    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    /**
     * 检查是否开启调试模式
     *
     * @return 如果是调试模式返回 true
     */
    public boolean isDebugMode() {
        try {
            KilacraftAI instance = KilacraftAI.getInstance();
            if (instance != null && instance.getConfigManager() != null) {
                return instance.getConfigManager().isDebugMode();
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return false;
    }

    /**
     * 获取单个知识文件内容
     *
     * @param fileName 文件名
     * @return 知识内容
     */
    public String getKnowledge(String fileName) {
        return knowledgeCache.get(fileName);
    }

    /**
     * 重新加载知识库
     */
    public void reload() {
        plugin.getLogger().info("正在重新加载知识库...");
        loadAllKnowledge();
        plugin.getLogger().info("知识库加载完成");
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
