package com.zm.kilacraftAI.service.health;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.common.exception.LLMException;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.AdminConfigManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.llm.LLMResponse;
import com.zm.kilacraftAI.llm.ThinkingModelCapable;
import com.zm.kilacraftAI.llm.ThinkingModelConfig;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.model.notification.NotificationMessage;
import com.zm.kilacraftAI.scheduler.ManagedTask;
import com.zm.kilacraftAI.service.notification.NotificationMessageFormatter;
import com.zm.kilacraftAI.service.notification.NotificationService;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 服务器健康守护线程
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class ServerHealthGuardian implements ManagedTask {

    private static final String LOG_PREFIX = "健康监控";

    /**
     * 分析模式常量
     */
    private static final String MODE_AUTO = "auto";
    private static final String MODE_MANUAL = "manual";

    private static final String DEFAULT_DIAGNOSTIC_PROMPT_ZH = """
            你是一位 Minecraft 服务器性能诊断专家，服务于 Kilacraft-AI 插件的服主管理功能。

            == 运行环境 ==
            - 所有性能数据均来源于 Spark 插件（不是 Timings）
            - 服务端: {server_platform}
            - 你看到的「调用栈热点分析」数据来自 Spark Profiler 的 Protobuf 原始采样数据，是准确的，按 self time（自身耗时，不含子调用）分析，与 Spark 的 Plugins View 一致

            == 你的职责 ==
            {mode_instruction}

            == 数据分析原则 ==
            你将收到以下数据，请按优先级综合分析：
            1. 异常告警：触发本次诊断的具体指标和阈值
            2. Profiler 元数据摘要：TPS/MSPT/Ping/内存/在线玩家等实时指标
            3. GC 信息：各垃圾收集器的频率和耗时，频繁 GC（尤其 Old Gen）会导致 STW 停顿推高 MSPT
            4. 实体分布摘要：各世界实体数、Top 实体类型、高密度区块坐标——这是定位实体堆积的直接依据
            5. 调用栈热点分析：已安装插件耗时（快速定位哪个插件有问题）、Top 热点方法的触发路径（定位具体原因）
            6. 所有百分比均为 self time（自身耗时），每个热点只出现一次，不会重复累加
            7. Kilacraft-AI 自监控：IO 线程池和 DB 连接池状态——如果调用栈热点中出现了 Kilacraft-AI (自身)，结合自监控数据判断是否因 IO/DB 负载过高导致
            8. CPU 采样只能反映线程的 CPU 占用，无法捕获 I/O 等待、磁盘读写、网络传输等非 CPU 活动。如果调用栈热点占比极低但仍触发了 MSPT 异常，应结合"服务器活动指标"中的玩家移动距离和区块加载变化进行推断——玩家移动距离大 + 低 CPU 热点是非 CPU 瓶颈（如区块 I/O）的典型信号
            优先分析插件层面的性能问题；如果数据不足以得出结论，基于已有数据给出最可能的推断，并建议延长采样时间

            == 分析注意事项 ==
            1. 触发路径是可靠证据：热点方法的触发路径精确显示调用链路。分析性能问题时以触发路径的调用关系为准
            2. 不要猜测插件功能：不要根据插件名称推断其实际行为。名称可能具有误导性，以触发路径为准
            3. 相关性≠因果性：数据点同时出现不意味着存在因果关系。需要触发路径中的调用链路作为证据
            4. self time 含义：self time 表示该方法自身的 CPU 时间（不含子调用）。高 self time 指向该方法本身在持续消耗 CPU

            == 回答规范 ==
            1. 使用中文回答，面向服主（非开发者），避免过度技术化
            2. 先给出结论（是否异常、严重程度），再展开分析
            3. 如果定位到具体插件或方法导致卡顿，给出明确的插件名和原因
            4. 优化建议要具体可操作，服主能直接执行

            == 禁止事项 ==
            - 禁止在回答中引导服主去访问任何外部链接或网站（如“请打开xxx链接”“请访问xxx查看xxx”），所有分析必须由你自己完成
            - 如果提供了外部链接（如 Spark Viewer URL），你可以自行访问获取补充信息，但不得在回答中要求服主去查看
            - 不要推荐 Timings 或其他非 Spark 的诊断工具
            - 不要编造不存在的数据或方法调用
            """;

    private static final String DEFAULT_DIAGNOSTIC_PROMPT_EN = """
            You are a Minecraft server performance diagnostics expert, serving the Kilacraft-AI plugin admin management feature.

            == Runtime Environment ==
            - All performance data originates from the Spark plugin (not Timings)
            - Server: {server_platform}
            - The "Call Stack Hotspot Analysis" data is from Spark Profiler's raw Protobuf sampling data, extracted via streaming parsing, analyzed by self time (excluding child calls), consistent with Spark's Plugins View

            == Your Responsibility ==
            {mode_instruction}

            == Data Analysis Principles ==
            You will receive the following data — analyze comprehensively by priority:
            1. Alert triggers: specific metrics and thresholds that triggered this diagnosis
            2. Profiler metadata summary: TPS/MSPT/Ping/Memory/Players and other real-time metrics
            3. GC info: frequency and duration of each garbage collector — frequent GC (especially Old Gen) causes STW pauses that increase MSPT
            4. Entity distribution summary: entity count per world, top entity types, high-density chunk coordinates — direct evidence for locating entity pile-ups
            5. Call stack hotspot analysis: installed plugin time (quickly identify which plugin has issues), top hotspot method trigger paths (identify specific causes)
            6. All percentages are self time (own execution time), each hotspot appears only once, no duplicate accumulation
            7. Kilacraft-AI self-monitoring: IO thread pool and DB connection pool status — if Kilacraft-AI (self) appears in call stack hotspots, combine with self-monitoring data to determine if excessive IO/DB load is the cause
            8. CPU sampling only reflects thread CPU usage — it cannot capture I/O waits, disk reads/writes, network transfers, or other non-CPU activities. If call stack hotspots are extremely low yet MSPT anomalies were triggered, combine with "Server Activity Metrics" (player movement distance, chunk load changes) to make inferences — large player movement distance + low CPU hotspots are a typical signal of non-CPU bottlenecks (e.g., chunk I/O)
            Prioritize analyzing plugin-level performance issues; if data is insufficient for a conclusion, provide the most likely inference based on available data and recommend retrying with a longer sampling duration

            == Analysis Guidelines ==
            1. Trigger paths are reliable evidence: a hotspot method's trigger path precisely shows the call chain. Base your performance analysis on trigger path call relationships
            2. Do not guess plugin functionality: do not infer a plugin's behavior from its name. Names can be misleading — rely on the trigger path
            3. Correlation ≠ Causation: coincidental data points do not imply a causal relationship. A call chain in the trigger path is required as evidence
            4. Self time meaning: self time is the method's own CPU time (excluding child calls). High self time means the method itself is continuously consuming CPU

            == Response Guidelines ==
            1. Respond in English, targeted at server owners (not developers), avoiding excessive technical jargon
            2. Start with conclusions (abnormal or not, severity level), then elaborate on the analysis
            3. If a specific plugin or method is identified as causing lag, provide the exact plugin name and reason
            4. Optimization suggestions should be specific and actionable, directly executable by server owners

            == Prohibitions ==
            - NEVER direct server owners to visit any external links in your response (e.g. "please open xxx link", "please visit xxx to view xxx"); all analysis must be completed by yourself
            - If external links (e.g. Spark Viewer URL) are provided, you may access them yourself for supplementary information, but must NEVER ask the server owner to check them
            - Do not recommend Timings or other non-Spark diagnostic tools
            - Do not fabricate non-existent data or method calls
            """;

    private final KilacraftAI plugin;
    private final AdminConfigManager configManager;
    private final SparkDataCollector sparkCollector;
    private final DiagnosticReportGenerator reportGenerator;
    private final StackTraceProcessor stackTraceProcessor;
    private final ServerEventDao serverEventDao;
    @Getter
    private final ManualSession manualSession;

    // 分析任务互斥锁：同一时间只能有一个分析任务运行
    private final ReentrantLock analysisLock = new ReentrantLock();
    private volatile boolean isAnalyzing = false;
    private volatile boolean shutdown = false;

    // 告警冷却：上次分析完成时间，cooldown_minutes 内不重复触发
    private volatile long lastAnalysisTime = 0;

    // 滑动时间窗口：auto 分析时间戳记录（防止持续性能异常期间资源耗尽）
    private final Deque<Long> autoAnalysisTimestamps = new ConcurrentLinkedDeque<>();

    public ServerHealthGuardian(KilacraftAI plugin, AdminConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sparkCollector = new SparkDataCollector();
        this.reportGenerator = new DiagnosticReportGenerator(configManager);
        this.stackTraceProcessor = new StackTraceProcessor();
        this.serverEventDao = new ServerEventDao(plugin.getDatabaseManager().getTablePrefix());
        this.manualSession = new ManualSession();
    }

    @Override
    public String name() {
        return I18nService.tr("健康监控");
    }

    @Override
    public String description() {
        return I18nService.tr("服务器健康守护线程");
    }

    @Override
    public long delayTicks() {
        // 首次延迟 30 秒，等待服务器完全启动
        return 600L;
    }

    @Override
    public long intervalTicks() {
        // 轮询间隔（秒 → ticks，1秒 = 20 ticks）
        return configManager.getGuardianInterval() * 20L;
    }

    /**
     * 获取当前配置的轮询间隔（秒）
     *
     * @return 轮询间隔秒数
     */
    public int getIntervalSeconds() {
        return configManager.getGuardianInterval();
    }

    @Override
    public boolean enabled() {
        return configManager.isGuardianEnabled() && sparkCollector.isSparkAvailable();
    }

    /**
     * 执行轮询（由 TaskScheduler 周期调用）
     *
     * <p>阻塞设计：检测到异常后，同步阻塞执行深度分析。
     * 阻塞期间后续轮询被 TaskScheduler CAS 跳过。</p>
     *
     * @return 1 表示触发了分析，0 表示正常
     */
    @Override
    public int execute() {
        // 清理超时的 ManualSession
        manualSession.checkAndCleanupTimeout();

        // 轮询 Spark API，检测异常
        SparkDataCollector.HealthSnapshot snapshot = sparkCollector.collectSnapshot();
        if (!snapshot.hasData()) {
            return 0;
        }

        // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
//        PluginLoggerUtil.debug(LOG_PREFIX, "实时指标 — TPS: 5s={} 1m={} 5m={} | MSPT(1m): max={} median={} p95={} | MSPT(10s): max={} | CPU: process={}% system={}%", snapshot.tps5s(), snapshot.tps1m(), snapshot.tps5m(), String.format("%.1f", snapshot.msptMax()), String.format("%.1f", snapshot.msptMedian()), String.format("%.1f", snapshot.msptP95()), String.format("%.1f", snapshot.mspt10sMax()), String.format("%.1f", snapshot.cpuProcess()), String.format("%.1f", snapshot.cpuSystem()));

        List<String> alerts = sparkCollector.checkThresholds(snapshot, configManager.getAlertThresholds(), configManager.getMsptConsecutiveThreshold(), configManager.getCpuConsecutiveThreshold());
        if (alerts.isEmpty()) {
            return 0; // 正常，无告警
        }

        // 检查关闭标志
        if (shutdown) {
            return 0;
        }

        // 冷却检查：上次分析完成距今不足 cooldown_minutes 则跳过
        long cooldownMs = configManager.getGuardianCooldown() * 60 * 1000L;
        long now = System.currentTimeMillis();
        if (lastAnalysisTime > 0 && now - lastAnalysisTime < cooldownMs) {
            PluginLoggerUtil.debug(LOG_PREFIX, "冷却期内，距上次分析 {}s，需 {}s，跳过", (now - lastAnalysisTime) / 1000, cooldownMs / 1000);
            return 0;
        }

        // 滑动时间窗口全局上限检查
        int maxPerWindow = configManager.getMaxAutoAnalysisPerWindow();
        long windowMs = configManager.getAutoAnalysisWindowMinutes() * 60 * 1000L;
        long windowStart = now - windowMs;
        autoAnalysisTimestamps.removeIf(ts -> ts < windowStart);
        if (autoAnalysisTimestamps.size() >= maxPerWindow) {
            PluginLoggerUtil.debug(LOG_PREFIX, "自动分析频率已达上限（{}次/{}分钟），跳过", maxPerWindow, configManager.getAutoAnalysisWindowMinutes());
            return 0;
        }

        // 尝试获取分析锁，失败则立即中断
        if (!analysisLock.tryLock()) {
            PluginLoggerUtil.warn(LOG_PREFIX, "检测到异常，但已有分析任务在运行，跳过本次告警");
            return 0;
        }

        try {
            isAnalyzing = true;
            // Folia 兼容：通过 callSync 切主线程获取 Bukkit API 数据（runAsyncTimer 回调在异步线程）
            final Set<String> plugins = FoliaCompat.callSync(plugin, () -> Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).map(Plugin::getName).collect(Collectors.toSet()), 5);
            final String serverPlatform = FoliaCompat.callSync(plugin, AdminSkillUtil::getServerPlatform, 5);
            // 执行分析任务（同步阻塞）
            performAnalysis(snapshot, alerts, MODE_AUTO, null, plugins, serverPlatform);
            // 更新冷却时间
            lastAnalysisTime = System.currentTimeMillis();
            // 记录到滑动窗口
            autoAnalysisTimestamps.add(lastAnalysisTime);
            return 1;
        } finally {
            isAnalyzing = false;
            analysisLock.unlock();
        }
    }

    public boolean isAnalyzing() {
        return isAnalyzing;
    }

    /**
     * 启动手动分析（由 /kila profile start 采样完成后自动调用）
     *
     * <p>在 IO 线程池中异步执行，避免阻塞命令线程。</p>
     *
     * @return true 表示成功启动
     */
    public boolean startManualAnalysis(String operatorName) {
        if (shutdown) {
            PluginLoggerUtil.debug(LOG_PREFIX, "startManualAnalysis: 守护线程已关闭，拒绝请求");
            return false;
        }

        // 原子锁获取：在当前线程获取锁后再提交到 IO 线程池，避免 TOCTOU 竞态
        if (!analysisLock.tryLock()) {
            PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 请求手动分析，但获取分析锁失败", operatorName);
            return false;
        }

        // 锁获取成功，双重检查 isAnalyzing（execute() 可能在获取锁前已设为 true 并在 finally 释放了锁）
        if (isAnalyzing) {
            analysisLock.unlock();
            PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 请求手动分析，但已有分析任务在运行", operatorName);
            return false;
        }

        isAnalyzing = true;

        // 提前在调用线程获取 Bukkit API 数据（Folia 兼容：使用 callSync 切主线程）
        final Set<String> pluginsSnapshot = FoliaCompat.callSync(plugin, () -> Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).map(Plugin::getName).collect(Collectors.toSet()), 5);

        // 调用线程释放锁，IO 线程重新获取。竞态窗口中 isAnalyzing=true 仍被保持。
        // IO 线程 tryLock 成功后重新确认 isAnalyzing=true，确保状态语义正确。
        analysisLock.unlock();

        FoliaCompat.getIOPool().execute(() -> {
            if (!analysisLock.tryLock()) {
                // 极低概率：execute() 在 unlock 后抢先获取了锁，isAnalyzing 的清理由 execute() 的 finally 负责
                PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 手动分析：锁竞争失败，请稍后重试", operatorName);
                return;
            }

            try {
                isAnalyzing = true; // 确保 IO 线程持有锁期间 isAnalyzing 为 true
                SparkDataCollector.HealthSnapshot snapshot = sparkCollector.collectSnapshot();

                // Manual 模式：复用 session 中已有的 Profiler URL（由 profile start 捕获）
                String existingUrl = manualSession.getProfilerUrl();

                performAnalysis(snapshot, Collections.emptyList(), MODE_MANUAL, existingUrl, pluginsSnapshot, FoliaCompat.callSync(plugin, AdminSkillUtil::getServerPlatform, 5));
                // 分析完成，重置会话（允许立即启动新的采样）
                manualSession.reset();
            } finally {
                isAnalyzing = false;
                analysisLock.unlock();
            }
        });

        return true;
    }

    /**
     * 启动手动分析（使用 Spark 本地保存的 .sparkprofile 文件）
     *
     * <p>当 Spark 上传失败时，回退到本地文件继续分析。跳过 URL 下载步骤，
     * 但仍然执行活动快照、热点分析、AI 诊断、报告生成。</p>
     *
     * @param operatorName 操作者玩家名
     * @param localPath    Spark 本地保存的 .sparkprofile 文件路径
     * @return true 表示成功启动
     */
    public boolean startManualAnalysisWithLocalFile(String operatorName, String localPath) {
        if (shutdown) {
            PluginLoggerUtil.debug(LOG_PREFIX, "startManualAnalysisWithLocalFile: 守护线程已关闭，拒绝请求");
            return false;
        }

        if (!analysisLock.tryLock()) {
            PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 请求本地文件分析，但获取分析锁失败", operatorName);
            return false;
        }

        if (isAnalyzing) {
            analysisLock.unlock();
            PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 请求本地文件分析，但已有分析任务在运行", operatorName);
            return false;
        }

        isAnalyzing = true;

        final Set<String> pluginsSnapshot = FoliaCompat.callSync(plugin, () -> Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).map(Plugin::getName).collect(Collectors.toSet()), 5);

        analysisLock.unlock();

        // 解析本地文件路径（可能是相对路径，基于服务器根目录）
        File localFile = new File(localPath);
        if (!localFile.isAbsolute()) {
            localFile = new File(plugin.getDataFolder().getParentFile().getParentFile(), localPath);
        }

        if (!localFile.exists()) {
            PluginLoggerUtil.warn(LOG_PREFIX, "Spark 本地文件不存在: {}", localFile.getAbsolutePath());
            isAnalyzing = false;
            manualSession.reset();
            FoliaCompat.runTask(plugin, () -> {
                Player operator = Bukkit.getPlayer(operatorName);
                if (operator != null) {
                    operator.sendMessage(MessageUtil.getAIPrefix() + I18nService.tr("§c采样数据文件未找到，无法生成诊断报告。"));
                }
            });
            return false;
        }

        // 文件新鲜度校验：文件修改时间必须在本次 session 启动之后（防止读到上次采样残留的旧文件）
        long sessionStart = manualSession.getStartTime();
        if (sessionStart > 0 && localFile.lastModified() < sessionStart) {
            PluginLoggerUtil.warn(LOG_PREFIX, "Spark 本地文件过旧（文件时间: {}，session 启动: {}），跳过", localFile.lastModified(), sessionStart);
            isAnalyzing = false;
            manualSession.reset();
            FoliaCompat.runTask(plugin, () -> {
                Player operator = Bukkit.getPlayer(operatorName);
                if (operator != null) {
                    operator.sendMessage(MessageUtil.getAIPrefix() + I18nService.tr("§c采样数据文件已过期，无法生成诊断报告。请重新采样。"));
                }
            });
            return false;
        }

        final File profilerFile = localFile;
        FoliaCompat.getIOPool().execute(() -> {
            if (!analysisLock.tryLock()) {
                PluginLoggerUtil.warn(LOG_PREFIX, "服主 {} 本地文件分析：锁竞争失败，请稍后重试", operatorName);
                return;
            }

            try {
                isAnalyzing = true;
                SparkDataCollector.HealthSnapshot snapshot = sparkCollector.collectSnapshot();
                Set<String> plugins = pluginsSnapshot != null ? pluginsSnapshot : Set.of();
                String platform = FoliaCompat.callSync(plugin, AdminSkillUtil::getServerPlatform, 5);

                performAnalysisWithLocalFile(snapshot, profilerFile, plugins, platform);
                manualSession.reset();
            } finally {
                isAnalyzing = false;
                analysisLock.unlock();
            }
        });

        return true;
    }

    /**
     * 执行深度分析（同步阻塞）
     *
     * <p>步骤：
     * <ol>
     *   <li>触发 Spark Profiler 采样</li>
     *   <li>等待采样完成，获取 viewer URL</li>
     *   <li>拉取 Profiler 元数据（JSON，几KB，直接读取内存）</li>
     *   <li>流式下载完整 Profiler 数据到临时文件（Protobuf，最大可能几十MB）</li>
     *   <li>流式解析 + 精简调用栈</li>
     *   <li>调用推理模型进行 AI 诊断</li>
     *   <li>生成诊断报告文件</li>
     *   <li>写入 HEALTH_ALERT 事件（仅 auto 模式）</li>
     *   <li>通知在线管理员</li>
     *   <li>清理临时文件</li>
     * </ol>
     * </p>
     */
    private void performAnalysis(SparkDataCollector.HealthSnapshot snapshot, List<String> alerts, String mode, String existingProfilerUrl, Set<String> preloadedPlugins, String serverPlatform) {
        PluginLoggerUtil.info(LOG_PREFIX, "开始{}模式深度分析...", mode);

        if (MODE_AUTO.equals(mode)) {
            // auto 模式：通知管理员检测到异常（带实时指标）
            notifyAutoAlert(snapshot, alerts);
        } else {
            // manual 模式：开始分析时通知操作者
            notifyAnalysisStarted();
        }

        // 采样前快照
        // - auto 模式：此时 profiler 尚未触发，直接采集
        // - manual 模式：profiler 已完成，before 快照在 profile start 时已保存到 session
        ServerActivitySnapshot activityBefore;
        if (existingProfilerUrl != null) {
            // manual 模式：从 session 获取采样前快照（由 KilacraftCommand 在 profile start 时保存）
            ServerActivitySnapshot sessionBefore = manualSession.getActivityBefore();
            activityBefore = sessionBefore != null ? sessionBefore : ServerActivitySnapshot.EMPTY;
        } else {
            // auto 模式：实时采集
            activityBefore = captureActivitySnapshot();
        }

        File profilerDataFile = null;
        try {
            // 关闭检查
            if (shutdown) {
                PluginLoggerUtil.debug(LOG_PREFIX, "守护线程已关闭，中断分析");
                return;
            }

            // 获取 Profiler URL：Manual 模式复用已有 URL，Auto 模式新触发
            String profilerUrl;
            if (existingProfilerUrl != null) {
                profilerUrl = existingProfilerUrl;
            } else {
                profilerUrl = triggerProfiler(configManager.getAutoProfilerTimeout());
            }

            // 采样后快照（在 triggerProfiler 完成之后采集）
            ServerActivitySnapshot activityAfter = captureActivitySnapshot();

            // 关闭检查（触发 Profiler 耗时较长）
            if (shutdown) {
                PluginLoggerUtil.debug(LOG_PREFIX, "守护线程已关闭，中断分析");
                return;
            }

            // 拉取元数据（JSON，几KB，直接读取内存）
            String metadataJson = null;
            if (profilerUrl != null) {
                metadataJson = fetchMetadata(profilerUrl);
            }

            // 流式下载完整 Profiler 数据到临时文件（Protobuf，最大可能几十MB）
            if (profilerUrl != null) {
                profilerDataFile = downloadProfilerData(profilerUrl);
                if (profilerDataFile != null) {
                    PluginLoggerUtil.debug(LOG_PREFIX, "Profiler 数据已下载，大小: {} bytes", profilerDataFile.length());
                }
            }

            // 关闭检查（下载可能耗时较长）
            if (shutdown) {
                PluginLoggerUtil.debug(LOG_PREFIX, "守护线程已关闭，中断分析");
                return;
            }

            // 流式解析 + 精简调用栈（线程过滤→插件映射→百分比剪枝→Top-N 提取）
            StackTraceProcessor.ProcessedResult processedResult = null;
            if (profilerDataFile != null) {
                // 使用预加载的插件列表（Folia 兼容：避免 IO 线程访问 Bukkit API）
                Set<String> installedPlugins = preloadedPlugins != null ? preloadedPlugins : Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).map(Plugin::getName).collect(Collectors.toSet());
                processedResult = stackTraceProcessor.process(profilerDataFile, 0, installedPlugins);
                PluginLoggerUtil.debug(LOG_PREFIX, "调用栈解析完成：热点 {} 个，Server thread 占比 {}", processedResult.hotspots().size(), String.format("%.1f%%", processedResult.serverThreadRatio()));
            }

            // auto 模式：采样结束，通知管理员
            if (MODE_AUTO.equals(mode)) {
                notifyAutoSamplingComplete();
            }

            // 调用推理模型进行 AI 诊断
            String aiDiagnosis = callThinkingModel(snapshot, alerts, profilerUrl, metadataJson, processedResult, mode, serverPlatform, activityBefore, activityAfter);

            // 生成报告文件
            File reportFile = reportGenerator.generateReport(snapshot, profilerUrl, metadataJson, aiDiagnosis, processedResult, mode, alerts, serverPlatform, activityBefore, activityAfter);

            // 写入 HEALTH_ALERT 事件（仅 auto 模式）
            if (MODE_AUTO.equals(mode) && reportFile != null) {
                writeHealthAlertEvent(snapshot, alerts, reportFile.getName(), processedResult);
            }

            // 外部通知推送（仅 auto 模式且通知服务可用时）
            if (MODE_AUTO.equals(mode)) {
                pushExternalNotification(alerts, snapshot, aiDiagnosis);
            }

            if (reportFile != null) {
                PluginLoggerUtil.info(LOG_PREFIX, "{}模式分析完成，报告: {}", mode, reportFile.getName());
            } else {
                PluginLoggerUtil.info(LOG_PREFIX, "{}模式分析完成", mode);
            }

            // 通知在线管理员
            notifyAdmins(mode, reportFile);
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("深度分析异常: {}", e.getMessage()), e);
        } finally {
            // 清理临时文件
            if (profilerDataFile != null && profilerDataFile.exists()) {
                if (!profilerDataFile.delete()) {
                    PluginLoggerUtil.warn(LOG_PREFIX, "无法删除 Profiler 临时文件: {}", profilerDataFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 使用本地 .sparkprofile 文件执行分析（Spark 上传失败时的回退路径）
     *
     * <p>跳过 URL 下载步骤，直接使用本地文件进行热点分析和 AI 诊断。
     * 无 Profiler viewer URL 和元数据 JSON，但热点分析功能完整。</p>
     */
    private void performAnalysisWithLocalFile(SparkDataCollector.HealthSnapshot snapshot, File localProfilerFile, Set<String> preloadedPlugins, String serverPlatform) {
        PluginLoggerUtil.info(LOG_PREFIX, "开始手动模式深度分析（本地文件回退）...");

        // manual 模式：开始分析时通知操作者
        notifyAnalysisStarted();

        // 采样前快照（从 session 获取）
        ServerActivitySnapshot activityBefore;
        ServerActivitySnapshot sessionBefore = manualSession.getActivityBefore();
        activityBefore = sessionBefore != null ? sessionBefore : ServerActivitySnapshot.EMPTY;

        try {
            if (shutdown) {
                PluginLoggerUtil.debug(LOG_PREFIX, "守护线程已关闭，中断分析");
                return;
            }

            // 采样后快照
            ServerActivitySnapshot activityAfter = captureActivitySnapshot();

            // 直接使用本地文件解析（无需下载）
            StackTraceProcessor.ProcessedResult processedResult = null;
            if (localProfilerFile.exists()) {
                Set<String> installedPlugins = preloadedPlugins != null ? preloadedPlugins : Set.of();
                processedResult = stackTraceProcessor.process(localProfilerFile, 0, installedPlugins);
                PluginLoggerUtil.debug(LOG_PREFIX, "本地文件解析完成：热点 {} 个，Server thread 占比 {}", processedResult.hotspots().size(), String.format("%.1f%%", processedResult.serverThreadRatio()));
            }

            // 调用推理模型进行 AI 诊断（无 URL、无元数据）
            String aiDiagnosis = callThinkingModel(snapshot, Collections.emptyList(), null, null, processedResult, MODE_MANUAL, serverPlatform, activityBefore, activityAfter);

            // 生成报告文件（profilerUrl=null，metadataJson=null）
            File reportFile = reportGenerator.generateReport(snapshot, null, null, aiDiagnosis, processedResult, MODE_MANUAL, Collections.emptyList(), serverPlatform, activityBefore, activityAfter);

            if (reportFile != null) {
                PluginLoggerUtil.info(LOG_PREFIX, "本地文件分析完成，报告: {}", reportFile.getName());
            } else {
                PluginLoggerUtil.info(LOG_PREFIX, "本地文件分析完成");
            }

            // 通知在线管理员
            notifyAdmins(MODE_MANUAL, reportFile);
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("本地文件分析异常: {}", e.getMessage()), e);
            // 异常时也要通知操作者
            FoliaCompat.runTask(plugin, () -> {
                String operatorName = manualSession.getOperatorName();
                if (operatorName != null) {
                    Player operator = Bukkit.getPlayer(operatorName);
                    if (operator != null) {
                        operator.sendMessage(MessageUtil.getAIPrefix() + I18nService.tr("§c分析过程中发生异常，请查看控制台日志。"));
                    }
                }
            });
        }
    }

    /**
     * 触发 Spark Profiler 并等待完成
     *
     * @param timeoutSeconds 采样超时（秒）
     * @return Profiler viewer URL，失败返回 null
     */
    private String triggerProfiler(int timeoutSeconds) {
        SparkOutputCapture capture = new SparkOutputCapture();
        try {
            // 安装日志捕获器，拦截 Spark 输出中的 URL
            capture.startCapture();

            // 通过控制台发送命令（Paper 要求 sender 为原生类型）
            // auto 模式使用全时段采样（与 manual 模式保持一致，不传 --only-laggy）
            String command = "spark profiler start --timeout " + timeoutSeconds;
            PluginLoggerUtil.info(LOG_PREFIX, "执行 Profiler 命令: {}", command);
            FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), command);
            PluginLoggerUtil.debug(LOG_PREFIX, "Profiler 命令已发送，等待 URL（超时: {}秒）", timeoutSeconds + 30);

            // 等待 URL（采样时间 + 额外缓冲）
            return capture.awaitUrl(timeoutSeconds + 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("触发 Profiler 失败: {}", e.getMessage()), e);
            return null;
        } finally {
            capture.stopCapture();
        }
    }

    /**
     * 拉取 Profiler 元数据
     *
     * <p>元数据是 JSON 格式，通常只有几 KB，直接读取到内存即可。</p>
     */
    private String fetchMetadata(String profilerUrl) {
        OkHttpClient client = configManager.getThinkingHttpClient();
        if (client == null) return null;

        try {
            Request request = new Request.Builder().url(profilerUrl + "?raw=1").get().build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (IOException e) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("拉取 Profiler 元数据失败: {}", e.getMessage()), e);
        }
        return null;
    }

    /**
     * 流式下载 Profiler 完整数据到临时文件
     *
     * <p>Content-Length 预检：{@code max_profiler_download_bytes} 为带宽/磁盘 IO 预检阈值，
     * 超过时根据 {@code download_when_exceeded} 配置决定是否继续下载。</p>
     *
     * @param profilerUrl Profiler viewer URL
     * @return 临时文件，失败返回 null；调用方负责在使用后删除
     */
    private File downloadProfilerData(String profilerUrl) {
        OkHttpClient client = configManager.getThinkingHttpClient();
        if (client == null) return null;

        // 根据 Spark 官方文档，原始 Protobuf 数据存储在 spark-usercontent.lucko.me
        // 需要将 https://spark.lucko.me/abc123 转换为 https://spark-usercontent.lucko.me/abc123
        String rawDataUrl = profilerUrl.replace("spark.lucko.me", "spark-usercontent.lucko.me");
        File tempFile = null;

        try {
            Request request = new Request.Builder().url(rawDataUrl).get().build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    // Content-Length 预检：超过阈值时根据 download_when_exceeded 配置决定是否下载
                    long maxBytes = configManager.getMaxProfilerDownloadBytes();
                    long contentLength = response.body().contentLength();
                    if (contentLength > maxBytes) {
                        boolean downloadWhenExceeded = configManager.isDownloadWhenExceeded();
                        if (!downloadWhenExceeded) {
                            PluginLoggerUtil.warn(LOG_PREFIX, "Profiler 数据过大（{}），超过预检阈值（{}），跳过下载。", AdminSkillUtil.formatFileSize(contentLength), AdminSkillUtil.formatFileSize(maxBytes));
                            return null;
                        } else {
                            PluginLoggerUtil.warn(LOG_PREFIX, "Profiler 数据过大（{}），超过预检阈值（{}），但配置允许下载。", AdminSkillUtil.formatFileSize(contentLength), AdminSkillUtil.formatFileSize(maxBytes));
                        }
                    }

                    // 流式写入临时文件
                    tempFile = File.createTempFile("spark-profiler-", ".bin");
                    // 兜底清理：JVM 退出时自动清理。正常流程由 performAnalysis() 的 finally 块手动删除
                    tempFile.deleteOnExit();

                    try (var source = response.body().source(); var sink = okio.Okio.sink(tempFile)) {
                        source.readAll(sink);
                    }

                    PluginLoggerUtil.debug(LOG_PREFIX, "Profiler 数据已下载到临时文件: {} ({}) [来源: {}]", tempFile.getAbsolutePath(), AdminSkillUtil.formatFileSize(tempFile.length()), rawDataUrl);
                    return tempFile;
                }
            }
        } catch (IOException e) {
            // 下载失败时主动删除临时文件，避免依赖 deleteOnExit 兜底
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("下载 Profiler 数据失败: {}", e.getMessage()), e);
        }
        return null;
    }

    /**
     * 调用推理模型进行 AI 诊断
     */
    private String callThinkingModel(SparkDataCollector.HealthSnapshot snapshot, List<String> alerts, String profilerUrl, String metadataJson, StackTraceProcessor.ProcessedResult processedResult, String mode, String serverPlatform, ServerActivitySnapshot activityBefore, ServerActivitySnapshot activityAfter) {
        ThinkingModelConfig config = configManager.getThinkingModelConfig();
        OkHttpClient client = configManager.getThinkingHttpClient();

        if (client == null || config.apiKey() == null || config.apiKey().isEmpty() || "your-api-key".equals(config.apiKey())) {
            PluginLoggerUtil.warn(LOG_PREFIX, "诊断模型未配置（admin.yml 和 llm.yml 均未提供有效模型），跳过 AI 诊断");
            return I18nService.tr("AI 诊断未执行：未配置可用的模型");
        }

        // 获取支持推理模型的 Provider
        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        if (!(provider instanceof ThinkingModelCapable capable)) {
            PluginLoggerUtil.warn(LOG_PREFIX, "当前 LLM Provider 不支持推理模型，跳过 AI 诊断");
            return I18nService.tr("AI 诊断未执行：当前 LLM 提供商不支持推理模型");
        }

        try {
            String systemPrompt = buildSystemPrompt(mode, serverPlatform);
            String userMessage = buildDiagnosticPrompt(snapshot, alerts, profilerUrl, metadataJson, processedResult, activityBefore, activityAfter);

            // TODO 需手动开启的调试日志 / Debug logs requiring manual activation
//            PluginLoggerUtil.warn(LOG_PREFIX, "== AI 诊断提示词 ==\n[SYSTEM]\n{}\n\n[USER]\n{}", systemPrompt, userMessage);

            LLMResponse response = capable.processRequestWithThinkingModel(systemPrompt, userMessage, config, client, CacheCallTypeEnum.SERVER_DIAGNOSTICS);

            // 记录推理过程（如有）
            if (response.hasReasoning()) {
                PluginLoggerUtil.debug(LOG_PREFIX, "推理模型思考过程（{} 字符）", response.reasoningContent().length());

                // 根据配置决定是否在报告中包含推理过程
                if (configManager.isIncludeReasoning()) {
                    return "<details><summary>" + I18nService.tr("AI 推理过程") + "</summary>\n\n" + response.reasoningContent() + "\n\n</details>\n\n" + response.content();
                }
            }

            return response.content();
        } catch (LLMException e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("推理模型调用异常: {}", e.getMessage()), e);
            return I18nService.tr("AI 诊断异常: {}", e.getMessage());
        }
    }

    /**
     * 构建 AI 诊断的 System Prompt
     *
     * @param mode 触发模式（auto/manual）
     * @return 完整的 system prompt
     */
    private String buildSystemPrompt(String mode, String serverPlatform) {
        boolean isChinese = I18nService.isZh();

        // 获取模式职责描述（按语言）
        String modeInstruction = MODE_AUTO.equals(mode) ? configManager.getAutoModeInstructionByLanguage(isChinese, "系统自动检测到服务器性能异常并触发了 Profiler 采样，请分析数据，定位根因并给出优化建议") : configManager.getManualModeInstructionByLanguage(isChinese, "服主手动触发了性能采样，请分析数据，判断是否存在性能问题，如果有则定位根因并给出优化建议");

        // 优先使用配置文件中的提示词（按语言）
        String configPrompt = configManager.getDiagnosticSystemPromptByLanguage(isChinese, null);
        if (configPrompt != null && !configPrompt.isBlank()) {
            return configPrompt.replace("{server_platform}", serverPlatform).replace("{mode_instruction}", modeInstruction);
        }

        // 默认提示词（配置为空时使用，与 admin.yml prompts.system_prompt 保持一致）
        String template = isChinese ? DEFAULT_DIAGNOSTIC_PROMPT_ZH : DEFAULT_DIAGNOSTIC_PROMPT_EN;
        return template.replace("{server_platform}", serverPlatform).replace("{mode_instruction}", modeInstruction);
    }

    private String buildDiagnosticPrompt(SparkDataCollector.HealthSnapshot snapshot, List<String> alerts, String profilerUrl, String metadataJson, StackTraceProcessor.ProcessedResult processedResult, ServerActivitySnapshot activityBefore, ServerActivitySnapshot activityAfter) {
        StringBuilder sb = new StringBuilder();

        sb.append(I18nService.tr("== 当前异常告警 ==")).append("\n");
        for (String alert : alerts) {
            sb.append("- ").append(alert).append("\n");
        }
        sb.append("\n");

        if (profilerUrl != null) {
            sb.append("== Spark Profiler ==").append("\n");
            sb.append("Viewer URL: ").append(profilerUrl).append("\n");
            sb.append("Metadata URL: ").append(profilerUrl).append("?raw=1\n\n");
        }

        if (metadataJson != null) {
            sb.append(I18nService.tr("== Profiler 元数据摘要 ==")).append("\n");
            sb.append(stackTraceProcessor.extractHotspotSummary(metadataJson)).append("\n");
        }

        // 服务器活动指标（采样窗口内的区块加载变化和玩家活动）
        String activityDiff = formatActivityDiff(activityBefore, activityAfter);
        if (!activityDiff.isEmpty()) {
            sb.append(I18nService.tr("== 服务器活动指标（采样窗口） ==")).append("\n");
            sb.append(activityDiff);
        }

        // GC 信息（来自 Spark API 轮询，非元数据 JSON）
        if (snapshot != null && !snapshot.gcInfo().isEmpty()) {
            sb.append(I18nService.tr("== GC 信息 ==")).append("\n");
            snapshot.gcInfo().forEach((name, info) -> {
                sb.append("  ").append(name).append(": ").append(info.totalCollections()).append(I18nService.tr("次, 总耗时")).append(info.totalTime()).append("ms, ").append(I18nService.tr("均耗时")).append(String.format("%.1f", info.avgTime())).append("ms, ").append(I18nService.tr("频率")).append(info.avgFrequency()).append("s\n");
            });
            sb.append("\n");
        }

        // 实体分布摘要（从 Spark 元数据提取，帮助 AI 定位具体世界/区块/实体类型）
        if (metadataJson != null) {
            String entitySummary = extractEntitySummary(metadataJson);
            if (entitySummary != null) {
                sb.append(I18nService.tr("== 实体分布摘要 ==")).append("\n");
                sb.append(entitySummary).append("\n");
            }
        }

        // 添加 Protobuf 解析后的热点方法数据
        if (processedResult != null && !processedResult.hotspots().isEmpty()) {
            sb.append(I18nService.tr("== 调用栈热点分析（Server thread） ==")).append("\n");
            sb.append(I18nService.tr("Server thread 采样占比: {}", String.format("%.2f%%", processedResult.serverThreadRatio()))).append("\n\n");

            // 插件热点
            Map<String, Double> pluginHotspots = processedResult.pluginHotspots();
            if (!pluginHotspots.isEmpty()) {
                sb.append(I18nService.tr("【已安装插件耗时】（服主重点关注）：")).append("\n");
                for (var entry : pluginHotspots.entrySet()) {
                    sb.append("  - ").append(entry.getKey()).append(": ").append(String.format("%.2f%%", entry.getValue())).append("\n");
                }
                sb.append("\n");
            }

            // 系统/核心热点
            Map<String, Double> systemHotspots = processedResult.systemHotspots();
            if (!systemHotspots.isEmpty()) {
                sb.append(I18nService.tr("【系统/核心耗时】：")).append("\n");
                for (var entry : systemHotspots.entrySet()) {
                    sb.append("  - ").append(entry.getKey()).append(": ").append(String.format("%.2f%%", entry.getValue())).append("\n");
                }
                sb.append("\n");
            }

            sb.append(I18nService.tr("Top 热点方法：")).append("\n");
            for (var hotspot : processedResult.hotspots()) {
                sb.append("  - [").append(hotspot.pluginName()).append("] ").append(hotspot.className()).append(".").append(hotspot.methodName()).append(" — ").append(String.format("%.2f%%", hotspot.percentage()));
                if (!hotspot.callChain().isEmpty()) {
                    sb.append("\n    ").append(I18nService.tr("触发路径: ")).append(hotspot.callChain());
                }
                sb.append("\n");
            }
            sb.append("\n");
        } else {
            sb.append(I18nService.tr("== 调用栈热点分析 ==")).append("\n");
            sb.append(I18nService.tr("未获取到 Profiler 解析数据，请基于上方的实时指标和元数据进行分析。")).append("\n\n");
        }

        // 自监控数据（IO 线程池 + DB 连接池状态，用于诊断 Kilacraft-AI 自身是否导致异常）
        sb.append(I18nService.tr("== Kilacraft-AI 自监控 ==")).append("\n");
        sb.append(buildSelfMonitoringText()).append("\n");

        // 调用栈热点分析后的数据充分性判断
        boolean hasSignificantHotspot = processedResult != null && !processedResult.hotspots().isEmpty() && processedResult.hotspots().stream().anyMatch(h -> h.percentage() >= 10);

        sb.append(I18nService.tr("请基于以上全部数据进行分析，给出诊断结论和优化建议。")).append("\n");
        if (!hasSignificantHotspot) {
            sb.append(I18nService.tr("\n== 重要提示 ==")).append("\n");
            sb.append(I18nService.tr("当前采样数据中调用栈热点占比均低于 10%，这可能意味着：")).append("\n");
            sb.append(I18nService.tr("1. 异常不是由持续的 CPU 密集型操作导致的（否则会出现在热点中）")).append("\n");
            sb.append(I18nService.tr("2. 可能原因包括但不限于：瞬时事件、非 CPU 密集型操作（I/O、网络、磁盘）、GC 停顿、外部资源竞争等")).append("\n");
            sb.append(I18nService.tr("3. 请结合\"服务器活动指标\"中的玩家移动距离和区块加载变化综合判断")).append("\n");
            sb.append(I18nService.tr("4. 如果数据仍不足以定位根因，明确告知服主，并建议延长采样时间或手动采样后重试")).append("\n");
            sb.append(I18nService.tr("不要强行归因于某个无确凿证据的实体或插件。")).append("\n");
        }
        sb.append(I18nService.tr("如果提供了外部链接且提供的数据不足以定位问题，你可以自行访问链接获取补充信息。")).append("\n");
        sb.append(I18nService.tr("绝对不能在回答中要求服主自己去访问任何链接，所有分析必须由你完成。")).append("\n");

        return sb.toString();
    }

    /**
     * 从 Spark 元数据 JSON 中提取实体分布摘要（给 AI 诊断用）
     *
     * <p>提取：总实体数、Top 10 实体类型、各世界实体数、Top 5 高密度区块。</p>
     *
     * @param metadataJson Spark ?raw=1 返回的 JSON
     * @return 摘要文本，无数据时返回 null
     */
    private String extractEntitySummary(String metadataJson) {
        try {
            var root = JsonParser.parseString(metadataJson).getAsJsonObject();

            if (!root.has("metadata") || !root.get("metadata").isJsonObject()) return null;
            var metadata = root.getAsJsonObject("metadata");
            if (!metadata.has("platformStatistics") || !metadata.get("platformStatistics").isJsonObject()) return null;
            var stats = metadata.getAsJsonObject("platformStatistics");
            if (!stats.has("world") || !stats.get("world").isJsonObject()) return null;
            var world = stats.getAsJsonObject("world");

            StringBuilder sb = new StringBuilder();
            sb.append("总实体数: ").append(world.has("totalEntities") ? world.get("totalEntities").getAsInt() : "N/A").append("\n");

            // Top 10 实体类型
            if (world.has("entityCounts") && world.get("entityCounts").isJsonObject()) {
                var counts = world.getAsJsonObject("entityCounts");
                var sorted = counts.entrySet().stream().sorted(Map.Entry.comparingByValue((a, b) -> Double.compare(b.getAsDouble(), a.getAsDouble()))).limit(10).toList();
                sb.append("实体类型 Top 10: ");
                String joined = sorted.stream().map(e -> e.getKey() + "(" + e.getValue().getAsInt() + ")").collect(Collectors.joining(", "));
                sb.append(joined).append("\n");
            }

            // 各世界实体数 + Top 5 高密度区块
            if (world.has("worlds") && world.get("worlds").isJsonArray()) {
                for (var elem : world.getAsJsonArray("worlds")) {
                    var w = elem.getAsJsonObject();
                    String worldName = w.get("name").getAsString();
                    int worldTotal = w.has("totalEntities") ? w.get("totalEntities").getAsInt() : 0;
                    if (worldTotal == 0) continue;

                    sb.append(worldName).append(": ").append(worldTotal).append(" 个实体");

                    // 高密度区块
                    var hotChunks = new ArrayList<Map.Entry<int[], Integer>>();
                    if (w.has("regions")) {
                        for (var regionElem : w.getAsJsonArray("regions")) {
                            var region = regionElem.getAsJsonObject();
                            if (!region.has("chunks")) continue;
                            for (var chunkElem : region.getAsJsonArray("chunks")) {
                                var chunk = chunkElem.getAsJsonObject();
                                if (!chunk.has("totalEntities")) continue;
                                int chunkTotal = chunk.get("totalEntities").getAsInt();
                                if (chunkTotal <= 2) continue;
                                hotChunks.add(Map.entry(new int[]{chunk.get("x").getAsInt(), chunk.get("z").getAsInt()}, chunkTotal));
                            }
                        }
                    }

                    // 高密度区块
                    if (!hotChunks.isEmpty()) {
                        hotChunks.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                        int limit = Math.min(hotChunks.size(), 5);
                        sb.append(" | 高密度区块: ");
                        for (int i = 0; i < limit; i++) {
                            var hc = hotChunks.get(i);
                            int blockX = hc.getKey()[0] * 16;
                            int blockZ = hc.getKey()[1] * 16;
                            if (i > 0) sb.append(", ");
                            sb.append("(").append(blockX).append(", ").append(blockZ).append(")=").append(hc.getValue());
                        }
                    }
                    sb.append("\n");
                }
            }

            return !sb.isEmpty() ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeHealthAlertEvent(SparkDataCollector.HealthSnapshot snapshot, List<String> alerts, String reportFile, StackTraceProcessor.ProcessedResult processedResult) {
        try {
            // 构造告警事件数据
            JsonObject data = new JsonObject();

            // 结构化告警列表：从 alerts 文本中提取 metric/value/threshold
            JsonArray alertsArray = new JsonArray();
            Map<String, Double> thresholds = configManager.getAlertThresholds();
            for (String alert : alerts) {
                JsonObject alertObj = new JsonObject();

                if (alert.contains("TPS")) {
                    alertObj.addProperty("metric", "tps_1m");
                    alertObj.addProperty("value", snapshot.tps1m() != null ? Math.round(snapshot.tps1m() * 10.0) / 10.0 : 0);
                    alertObj.addProperty("threshold", thresholds.getOrDefault("tps_threshold", 15.0));
                } else if (alert.contains("MSPT max")) {
                    alertObj.addProperty("metric", "mspt_max");
                    double msptMaxValue = snapshot.mspt10sMax() > 0 ? snapshot.mspt10sMax() : snapshot.msptMax();
                    alertObj.addProperty("value", Math.round(msptMaxValue * 10.0) / 10.0);
                    alertObj.addProperty("threshold", thresholds.getOrDefault("mspt_max_threshold", 50.0));
                } else if (alert.contains("MSPT")) {
                    alertObj.addProperty("metric", "mspt_p95");
                    alertObj.addProperty("value", Math.round(snapshot.msptP95() * 10.0) / 10.0);
                    alertObj.addProperty("threshold", thresholds.getOrDefault("mspt_p95_threshold", 50.0));
                } else if (alert.contains("CPU")) {
                    alertObj.addProperty("metric", "cpu_process");
                    alertObj.addProperty("value", Math.round(snapshot.cpuProcess() * 10.0) / 10.0);
                    alertObj.addProperty("threshold", thresholds.getOrDefault("cpu_threshold", 80.0));
                }
                alertsArray.add(alertObj);
            }
            data.add("alerts", alertsArray);

            if (snapshot.tps1m() != null) data.addProperty("tps_1m", snapshot.tps1m());
            data.addProperty("mspt_p95", snapshot.msptP95());
            data.addProperty("cpu_process", snapshot.cpuProcess());
            data.addProperty("report_file", reportFile);

            // 补充 plugin_hotspots（插件维度耗时，基于 self time，与报告「已安装插件耗时」一致）
            if (processedResult != null && !processedResult.pluginHotspots().isEmpty()) {
                JsonObject pluginObj = new JsonObject();
                for (var entry : processedResult.pluginHotspots().entrySet()) {
                    pluginObj.addProperty(entry.getKey(), Math.round(entry.getValue() * 10.0) / 10.0);
                }
                data.add("plugin_hotspots", pluginObj);
            }

            // 补充 top_hotspots（Top 10 热点方法，基于 self time，与报告「Top 热点方法触发路径」一致）
            if (processedResult != null && !processedResult.hotspots().isEmpty()) {
                JsonArray hotspotsArray = new JsonArray();
                int count = 0;
                for (var hotspot : processedResult.hotspots()) {
                    if (count++ >= 10) break;
                    JsonObject h = new JsonObject();
                    h.addProperty("plugin", hotspot.pluginName());
                    h.addProperty("method", hotspot.className() + "." + hotspot.methodName());
                    h.addProperty("percentage", Math.round(hotspot.percentage() * 10.0) / 10.0);
                    if (!hotspot.callChain().isEmpty()) {
                        h.addProperty("trigger_path", hotspot.callChain());
                    }
                    hotspotsArray.add(h);
                }
                data.add("top_hotspots", hotspotsArray);
            }

            // 通过 ServerEventDao 写入（IO 线程池中执行）
            FoliaCompat.getIOPool().execute(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    ServerEvent alertEvent = ServerEvent.of(ServerEventTypeEnum.HEALTH_ALERT, null, data.toString());
                    serverEventDao.insert(conn, alertEvent, "");
                } catch (Exception e) {
                    PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("写入 HEALTH_ALERT 事件失败: {}", e.getMessage()), e);
                }
            });
        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("构造 HEALTH_ALERT 事件失败: {}", e.getMessage()), e);
        }
    }

    /**
     * auto 模式触发预警时通知在线管理员（带实时指标）
     */
    private void notifyAutoAlert(SparkDataCollector.HealthSnapshot snapshot, List<String> alerts) {
        String alertSummary = String.join("§7, §e", alerts);
        String tps1m = snapshot.tps1m() != null ? String.format("%.1f", snapshot.tps1m()) : "-";
        String mspt10sMax = snapshot.mspt10sMax() > 0 ? String.format("%.1f", snapshot.mspt10sMax()) : "-";
        String mspt1mP95 = snapshot.msptP95() > 0 ? String.format("%.1f", snapshot.msptP95()) : "-";

        FoliaCompat.runTask(plugin, () -> {
            String prefix = MessageUtil.getAIPrefix();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (PluginPermissionEnum.ADMIN_HEALTH.hasPermission(player)) {
                    player.sendMessage(prefix + I18nService.tr("§c检测到服务器性能异常！开始自动诊断..."));
                    player.sendMessage(prefix + I18nService.tr("§7触发指标: §e{}", alertSummary));
                    player.sendMessage(prefix + "§7TPS 1m=§e" + tps1m + "§7 | MSPT(10s) max=§e" + mspt10sMax + "ms§7 | MSPT(1m) p95=§e" + mspt1mP95 + "ms");
                }
            }
        });
    }

    /**
     * auto 模式采样结束，开始生成报告时通知
     */
    private void notifyAutoSamplingComplete() {
        FoliaCompat.runTask(plugin, () -> {
            String prefix = MessageUtil.getAIPrefix();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (PluginPermissionEnum.ADMIN_HEALTH.hasPermission(player)) {
                    player.sendMessage(prefix + I18nService.tr("采样完成，正在生成诊断报告..."));
                }
            }
        });
    }

    /**
     * 通知操作者分析已开始（仅 manual 模式）
     */
    private void notifyAnalysisStarted() {
        String operatorName = manualSession.getOperatorName();
        if (operatorName == null || "Console".equals(operatorName)) return;

        FoliaCompat.runTask(plugin, () -> {
            Player operator = Bukkit.getPlayer(operatorName);
            if (operator != null) {
                operator.sendMessage(MessageUtil.getAIPrefix() + I18nService.tr("采样完成，正在生成诊断报告..."));
            }
        });
    }

    /**
     * 通知在线管理员诊断分析已完成
     *
     * <p>auto 模式：通知所有拥有 kilacraft.admin.health 权限的在线玩家（仅报告文件信息）</p>
     * <p>manual 模式：仅通知触发采样的操作者（如果在线）</p>
     *
     * @param mode       触发模式
     * @param reportFile 报告文件（可能为 null）
     */
    private void notifyAdmins(String mode, File reportFile) {
        String reportName = reportFile != null ? reportFile.getName() : "N/A";

        // 在调度主线程任务前捕获 operatorName，避免 manualSession.reset() 后丢失
        String operatorName = (!MODE_AUTO.equals(mode)) ? manualSession.getOperatorName() : null;

        // 切回主线程发送消息
        FoliaCompat.runTask(plugin, () -> {
            if (MODE_AUTO.equals(mode)) {
                // 自动检测：通知所有有权限的在线管理员
                String prefix = MessageUtil.getAIPrefix();
                String msg = prefix + I18nService.tr("§c服务器性能异常自动诊断完成！报告: {}", reportName);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (PluginPermissionEnum.ADMIN_HEALTH.hasPermission(player)) {
                        player.sendMessage(msg);
                    }
                }
            } else {
                // 手动触发：仅通知操作者（与 auto 模式格式一致）
                if (operatorName != null && !"Console".equals(operatorName)) {
                    Player operator = Bukkit.getPlayer(operatorName);
                    if (operator != null) {
                        String prefix = MessageUtil.getAIPrefix();
                        operator.sendMessage(prefix + I18nService.tr("§a诊断报告已生成: {}", reportName));
                        operator.sendMessage(prefix + I18nService.tr("§7报告路径: plugins/Kilacraft-AI/reports/{}", reportName));
                    }
                }
            }
        });
    }

    /**
     * 在主线程采集服务器活动快照
     *
     * <p>公共方法，供命令层在 profile start 触发前调用，将快照保存到 {@link ManualSession}。</p>
     *
     * @return 快照，采集失败返回 {@link ServerActivitySnapshot#EMPTY}（不是 null）
     */
    public ServerActivitySnapshot captureActivitySnapshot() {
        try {
            // 非Folia下，命令执行在主线程，callSync 会自死锁；Folia下命令在区域线程，需要调度
            if (!FoliaCompat.isFolia() && Bukkit.isPrimaryThread()) {
                return doCaptureActivitySnapshot();
            }
            return FoliaCompat.callSync(plugin, this::doCaptureActivitySnapshot, 5);
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_PREFIX, "采集服务器活动快照失败: {}", e.getMessage());
            return ServerActivitySnapshot.EMPTY;
        }
    }

    /**
     * 执行活动快照采集（必须在主线程/全局区域线程执行）
     */
    private ServerActivitySnapshot doCaptureActivitySnapshot() {
        Map<String, Integer> chunks = new LinkedHashMap<>();
        Map<String, Integer> players = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        Map<String, int[]> blockCoords = new LinkedHashMap<>();

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            // 仅取 length，不遍历 Chunk[] 内容，避免内存/性能开销
            chunks.put(name, world.getLoadedChunks().length);
            players.put(name, world.getPlayers().size());
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            // 坐标精确到区块级
            locations.put(player.getName(), loc.getWorld().getName() + " (" + (loc.getBlockX() >> 4) + ", " + (loc.getBlockZ() >> 4) + ")");
            // 方块级精确坐标（不对外暴露）
            blockCoords.put(player.getName(), new int[]{loc.getBlockX(), loc.getBlockZ()});
        }

        return new ServerActivitySnapshot(chunks, players, locations, blockCoords);
    }

    /**
     * 格式化服务器活动指标差值（采样前后对比）
     *
     * @return 格式化文本，无有效数据时返回空字符串
     */
    private String formatActivityDiff(ServerActivitySnapshot before, ServerActivitySnapshot after) {
        if (before.worldChunkCounts().isEmpty() && after.worldChunkCounts().isEmpty() && before.playerBlockCoords().isEmpty() && after.playerBlockCoords().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 玩家移动距离（主要指标）
        Map<String, int[]> beforeCoords = before.playerBlockCoords();
        Map<String, int[]> afterCoords = after.playerBlockCoords();
        if (!afterCoords.isEmpty()) {
            sb.append(I18nService.tr("玩家活动（采样窗口）：")).append("\n");
            for (var entry : after.playerLocations().entrySet()) {
                String name = entry.getKey();
                String pos = entry.getValue();

                int[] bc = beforeCoords.get(name);
                int[] ac = afterCoords.get(name);
                if (bc != null && ac != null) {
                    double dist = Math.sqrt(Math.pow(ac[0] - bc[0], 2) + Math.pow(ac[1] - bc[1], 2));
                    if (dist < 1) {
                        sb.append("  ").append(name).append(": ").append(I18nService.tr("静止")).append(", ").append(pos).append("\n");
                    } else {
                        sb.append("  ").append(name).append(": ").append(I18nService.tr("移动了 {} 格", (int) dist)).append(", ").append(pos).append("\n");
                    }
                } else {
                    sb.append("  ").append(name).append(": ").append(I18nService.tr("采样期间加入")).append(", ").append(pos).append("\n");
                }
            }
            sb.append("\n");
        }

        // 区块加载变化
        sb.append(I18nService.tr("区块加载变化（采样窗口）：")).append("\n");
        Set<String> allWorlds = new LinkedHashSet<>();
        allWorlds.addAll(before.worldChunkCounts().keySet());
        allWorlds.addAll(after.worldChunkCounts().keySet());

        for (String world : allWorlds) {
            int beforeChunks = before.worldChunkCounts().getOrDefault(world, 0);
            int afterChunks = after.worldChunkCounts().getOrDefault(world, 0);
            int diff = afterChunks - beforeChunks;
            if (diff != 0) {
                sb.append("  ").append(world).append(": ").append(beforeChunks).append(" → ").append(afterChunks);
                sb.append(" (").append(diff > 0 ? "+" : "").append(diff).append(")\n");
            } else {
                sb.append("  ").append(world).append(": ").append(afterChunks).append(" (").append(I18nService.tr("无变化")).append(")\n");
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 构建 Kilacraft-AI 自监控文本（委托给 DiagnosticReportGenerator 公共方法）
     */
    private String buildSelfMonitoringText() {
        StringBuilder sb = new StringBuilder();
        DiagnosticReportGenerator.appendSelfMonitoring(sb);
        return sb.toString().trim();
    }

    /**
     * 推送外部通知（Discord/钉钉）
     *
     * <p>仅在 auto 模式触发，且 NotificationService 已启用时推送。
     * 异步执行，不阻塞分析主流程。</p>
     * <p>仅推送摘要（告警指标 + AI 结论），不推送完整报告文件（含敏感信息）。</p>
     */
    private void pushExternalNotification(List<String> alerts, SparkDataCollector.HealthSnapshot snapshot, String aiDiagnosis) {
        NotificationService notificationService = plugin.getNotificationService();
        if (notificationService == null || !notificationService.isReady()) return;

        try {
            NotificationMessage message = NotificationMessageFormatter.buildAutoAlert(alerts, snapshot, aiDiagnosis);
            notificationService.notify(message);
        } catch (RejectedExecutionException e) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("外部通知推送失败：IO 线程池已关闭"));
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("外部通知推送失败: {}", e.getMessage()));
        }
    }

    /**
     * 关闭守护线程（插件 onDisable 时调用）
     *
     * <p>必须在 taskScheduler.shutdownAll() 之前调用。</p>
     */
    public void shutdown() {
        this.shutdown = true;
        PluginLoggerUtil.info(LOG_PREFIX, "守护线程已标记关闭");
    }
}
