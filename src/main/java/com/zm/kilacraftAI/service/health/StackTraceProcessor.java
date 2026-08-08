package com.zm.kilacraftAI.service.health;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.JsonSafeGetUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 调用栈精简处理器
 *
 * <p>处理 Spark Profiler 的调用栈数据（原始 Protobuf 解析后的结构），
 * 进行多维度过滤，提取关键热点信息供 LLM 诊断分析。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class StackTraceProcessor {

    private static final String LOG_PREFIX = "健康监控";
    /**
     * 默认最大输出热点数量
     */
    private static final int DEFAULT_MAX_HOTSPOTS = 20;
    /**
     * 默认最低耗时占比阈值（百分比）
     */
    private static final double DEFAULT_MIN_PERCENTAGE = 2.0;
    /**
     * 自身插件包名前缀（用于标注自身插件）
     */
    private static final String SELF_PACKAGE_PREFIX = "com.zm.kilacraftAI";

    /**
     * 容器帧类名 — 这些是纯等待/委托调用，自身不执行业务逻辑，self time 可能突破阈值但无诊断价值。
     * 在 self time 算法下，只有这些帧的 self time 可能 > 2%（采样到空闲等待），需要显式过滤。
     * 其他容器帧（MinecraftServer、Thread）的 self time ≈ 0，自然被 2% 阈值过滤。
     */
    private static final Set<String> CONTAINER_FRAME_CLASSES = Set.of(
            // parkNanos / park — 纯空闲等待，低负载时 self time 可能很高
            "java.util.concurrent.locks.LockSupport",
            // park — 纯空闲等待，LockSupport 的底层实现
            "jdk.internal.misc.Unsafe");

    /**
     * 热点方法信息
     *
     * @param pluginName 插件名称（可能为 "unknown"）
     * @param className  类名
     * @param methodName 方法名
     * @param percentage 耗时占比百分比
     * @param threadName 线程名
     * @param callChain  完整调用链（如 "A.method → B.method → C.method"），可能为空字符串
     */
    public record HotspotMethod(String pluginName, String className, String methodName, double percentage,
                                String threadName, String callChain) {
    }

    /**
     * 精简结果
     *
     * @param hotspots             热点方法列表（按 self time 降序）
     * @param topPlugins           Top N 插件 self time 耗时占比
     * @param serverThreadRatio    Server thread 占总采样比例
     * @param installedPluginNames 已安装插件名称集合（用于区分插件 vs 系统/核心）
     */
    public record ProcessedResult(List<HotspotMethod> hotspots, Map<String, Double> topPlugins,
                                  double serverThreadRatio, Set<String> installedPluginNames) {
        /**
         * 从 topPlugins 中筛选出已安装插件的热点
         */
        public Map<String, Double> pluginHotspots() {
            return topPlugins.entrySet().stream().filter(e -> installedPluginNames.contains(e.getKey())).sorted(Map.Entry.<String, Double>comparingByValue().reversed()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        }

        /**
         * 从 topPlugins 中筛选出系统/核心（非插件）的热点
         */
        public Map<String, Double> systemHotspots() {
            return topPlugins.entrySet().stream().filter(e -> !installedPluginNames.contains(e.getKey())).sorted(Map.Entry.<String, Double>comparingByValue().reversed()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        }
    }

    /**
     * 解析后的线程数据（Protobuf ThreadNode 映射）
     */
    private record ParsedThread(String name, double[] times, List<ParsedStackNode> children) {
    }

    /**
     * 递归展开调用栈的最大深度
     */
    private static final int MAX_CALL_DEPTH = 5;

    /**
     * 解析后的栈节点数据（Protobuf StackTraceNode 映射）
     *
     * @param childrenRefs 子节点在 ThreadNode.children 列表中的索引，-1 表示无子节点
     */
    private record ParsedStackNode(String className, String methodName, double[] times, int[] childrenRefs) {
    }

    /**
     * 流式解析 Protobuf Profiler 数据
     *
     * @param protobufFile         Protobuf 数据文件（临时文件）
     * @param maxHotspots          最大热点数量
     * @param installedPluginNames 已安装插件名称集合（用于区分插件 vs 系统/核心）
     * @return 精简结果
     */
    public ProcessedResult process(File protobufFile, int maxHotspots, Set<String> installedPluginNames) {
        if (protobufFile == null || !protobufFile.exists()) {
            PluginLoggerUtil.warn(LOG_PREFIX, "Protobuf 文件不存在，跳过解析");
            return new ProcessedResult(List.of(), Map.of(), 0.0, Set.of());
        }

        if (maxHotspots <= 0) {
            maxHotspots = DEFAULT_MAX_HOTSPOTS;
        }

        PluginLoggerUtil.info(LOG_PREFIX, "开始流式解析 Protobuf 数据（{}）...", AdminSkillUtil.formatFileSize(protobufFile.length()));
        long startTime = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream(protobufFile)) {
            CodedInputStream cis = CodedInputStream.newInstance(fis);
            // 128MB 上限，超过此大小会抛异常
            cis.setSizeLimit(128 * 1024 * 1024);

            // 流式解析 Protobuf 结构
            Map<String, String> classSources = new HashMap<>();
            List<ParsedThread> parsedThreads = new ArrayList<>();

            parseSamplerData(cis, classSources, parsedThreads);

            long parseTime = System.currentTimeMillis() - startTime;
            PluginLoggerUtil.debug(LOG_PREFIX, "Protobuf 解析完成：线程 {} 个，类映射 {} 条，耗时 {}ms", parsedThreads.size(), classSources.size(), parseTime);

            return applyFilteringAlgorithm(parsedThreads, classSources, maxHotspots, installedPluginNames);

        } catch (Exception e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("Protobuf 解析异常: {}", e.getMessage()), e);
            return new ProcessedResult(List.of(), Map.of(), 0.0, Set.of());
        }
    }

    /**
     * 解析顶层 SamplerData 消息
     */
    private void parseSamplerData(CodedInputStream cis, Map<String, String> classSources, List<ParsedThread> parsedThreads) throws IOException {
        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            if (tag == 0) break;

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            switch (fieldNumber) {
                // metadata，暂不解析
                case 1 -> skipField(cis, fieldNumber, wireType);
                // ThreadNode（length-delimited）
                case 2 -> {
                    int length = cis.readRawVarint32();
                    int oldLimit = cis.pushLimit(length);
                    ParsedThread thread = parseThreadNode(cis);
                    parsedThreads.add(thread);
                    cis.popLimit(oldLimit);
                }
                // class_sources map entry（map<string, string>）
                case 3 -> parseMapStringString(cis, classSources);
                default -> skipField(cis, fieldNumber, wireType);
            }
        }
    }

    /**
     * 解析 ThreadNode 消息
     */
    private ParsedThread parseThreadNode(CodedInputStream cis) throws IOException {
        String name = "";
        double[] times = new double[0];
        List<ParsedStackNode> children = new ArrayList<>();

        while (!cis.isAtEnd() && cis.getBytesUntilLimit() > 0) {
            int tag = cis.readTag();
            if (tag == 0) break;

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            switch (fieldNumber) {
                // thread name
                case 1 -> name = cis.readString();
                // StackTraceNode child（length-delimited）
                case 3 -> {
                    int length = cis.readRawVarint32();
                    int oldLimit = cis.pushLimit(length);
                    ParsedStackNode node = parseStackTraceNode(cis);
                    children.add(node);
                    cis.popLimit(oldLimit);
                }
                // times（packed double）
                case 4 -> times = readPackedDoubles(cis);
                default -> skipField(cis, fieldNumber, wireType);
            }
        }

        return new ParsedThread(name, times, children);
    }

    /**
     * 解析 StackTraceNode 消息
     */
    private ParsedStackNode parseStackTraceNode(CodedInputStream cis) throws IOException {
        String className = "";
        String methodName = "";
        double[] times = new double[0];
        int[] childrenRefs = new int[0];

        while (!cis.isAtEnd() && cis.getBytesUntilLimit() > 0) {
            int tag = cis.readTag();
            if (tag == 0) break;

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            switch (fieldNumber) {
                // class_name
                case 3 -> className = cis.readString();
                // method_name
                case 4 -> methodName = cis.readString();
                // parent_line_number / line_number，跳过
                case 5, 6 -> cis.readInt32();
                // method_desc，跳过
                case 7 -> cis.readString();
                // times（packed double）
                case 8 -> times = readPackedDoubles(cis);
                // children_refs
                case 9 -> childrenRefs = readPackedInt32sToArray(cis);
                default -> skipField(cis, fieldNumber, wireType);
            }
        }

        return new ParsedStackNode(className, methodName, times, childrenRefs);
    }

    /**
     * 解析 map<string, string> 的一个 entry
     */
    private void parseMapStringString(CodedInputStream cis, Map<String, String> map) throws IOException {
        int length = cis.readRawVarint32();
        int oldLimit = cis.pushLimit(length);

        String key = "";
        String value = "";

        while (!cis.isAtEnd() && cis.getBytesUntilLimit() > 0) {
            int tag = cis.readTag();
            if (tag == 0) break;

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            switch (fieldNumber) {
                case 1 -> key = cis.readString();
                case 2 -> value = cis.readString();
                default -> skipField(cis, fieldNumber, wireType);
            }
        }

        cis.popLimit(oldLimit);

        if (!key.isEmpty()) {
            map.put(key, value);
        }
    }

    /**
     * 读取 packed repeated double
     *
     * <p>packed 编码：length prefix + 连续 8 字节 little-endian double 值</p>
     */
    private double[] readPackedDoubles(CodedInputStream cis) throws IOException {
        int length = cis.readRawVarint32();
        int count = length / 8;
        double[] result = new double[count];

        int oldLimit = cis.pushLimit(length);
        for (int i = 0; i < count; i++) {
            result[i] = cis.readDouble();
        }
        cis.popLimit(oldLimit);

        return result;
    }

    /**
     * 读取 packed repeated int32 并返回数组（用于 children_refs）
     */
    private int[] readPackedInt32sToArray(CodedInputStream cis) throws IOException {
        int length = cis.readRawVarint32();
        int oldLimit = cis.pushLimit(length);
        List<Integer> refs = new ArrayList<>();
        while (cis.getBytesUntilLimit() > 0) {
            refs.add(cis.readRawVarint32());
        }
        cis.popLimit(oldLimit);
        return refs.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 跳过未知字段
     */
    private void skipField(CodedInputStream cis, int fieldNumber, int wireType) throws IOException {
        switch (wireType) {
            case WireFormat.WIRETYPE_VARINT -> cis.readRawVarint64();
            case WireFormat.WIRETYPE_FIXED64 -> cis.readFixed64();
            case WireFormat.WIRETYPE_LENGTH_DELIMITED -> cis.skipRawBytes(cis.readRawVarint32());
            case WireFormat.WIRETYPE_START_GROUP -> skipGroup(cis, fieldNumber);
            case WireFormat.WIRETYPE_END_GROUP -> {
                // END_GROUP 应该在 skipGroup 中处理，这里遇到说明解析状态异常
                // 不抛异常，继续解析后续数据
            }
            case WireFormat.WIRETYPE_FIXED32 -> cis.readFixed32();
            default -> {
                // wire type 6 和 7 在 Protobuf 规范中是保留类型
                // wire type 6: 按照 FIXED32 处理（4 字节）
                // wire type 7: 按照 FIXED64 处理（8 字节）
                if (wireType == 6) {
                    cis.readFixed32();
                } else if (wireType == 7) {
                    cis.readFixed64();
                } else {
                    throw new IOException(I18nService.tr("未知 wire type: {}", wireType));
                }
            }
        }
    }

    /**
     * 跳过 Protobuf group 字段
     *
     * <p>group 字段以 START_GROUP tag 开始，以相同 field number 的 END_GROUP tag 结束。
     * 需要递归读取并丢弃所有内部字段。</p>
     *
     * @param cis              CodedInputStream
     * @param groupFieldNumber group 的 field number（用于匹配 END_GROUP）
     */
    private void skipGroup(CodedInputStream cis, int groupFieldNumber) throws IOException {
        while (!cis.isAtEnd()) {
            int tag = cis.readTag();
            if (tag == 0) break;

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            // 遇到相同 field number 的 END_GROUP，表示 group 结束
            if (wireType == WireFormat.WIRETYPE_END_GROUP && fieldNumber == groupFieldNumber) {
                return;
            }

            // 递归跳过内部字段（可能包含嵌套 group）
            skipField(cis, fieldNumber, wireType);
        }
        // 如果到达流末尾仍未遇到 END_GROUP，记录警告
        PluginLoggerUtil.warn(LOG_PREFIX, "group 字段 {} 未找到 END_GROUP 标记", groupFieldNumber);
    }

    /**
     * 线程过滤 → 插件映射 → 百分比剪枝 → Top-N 提取
     *
     * @param parsedThreads 解析后的线程数据
     * @param classSources  类名 → 来源名映射（class_sources）
     * @param maxHotspots   最大热点数量
     * @return 精简结果
     */
    private ProcessedResult applyFilteringAlgorithm(List<ParsedThread> parsedThreads, Map<String, String> classSources, int maxHotspots, Set<String> installedPluginNames) {
        // 线程过滤 — 仅关注 Server thread（主线程）
        ParsedThread serverThread = null;
        double totalAllThreadsTime = 0;
        double serverThreadTime = 0;

        for (ParsedThread thread : parsedThreads) {
            double threadTime = getFirstTime(thread.times());
            totalAllThreadsTime += threadTime;

            if (isServerThread(thread.name())) {
                serverThread = thread;
                serverThreadTime = threadTime;
            }
        }

        double serverThreadRatio = totalAllThreadsTime > 0 ? (serverThreadTime / totalAllThreadsTime) * 100.0 : 0.0;

        if (serverThread == null) {
            PluginLoggerUtil.warn(LOG_PREFIX, "未找到 Server thread，跳过热点分析");
            return new ProcessedResult(List.of(), Map.of(), serverThreadRatio, Set.of());
        }

        PluginLoggerUtil.debug(LOG_PREFIX, "Server thread 采样占比: {}（{} 个栈节点）", String.format("%.1f%%", serverThreadRatio), serverThread.children().size());

        // 预计算每个节点的 self time（自身耗时，不含子调用）
        // self time = 节点总时间 - 所有直接子节点时间之和
        // 这与 Spark Profiler 的 Plugins View 算法一致：按 self time 聚合到插件，避免父子帧重复累加
        List<ParsedStackNode> allChildren = serverThread.children();
        double[] selfTimes = new double[allChildren.size()];
        for (int i = 0; i < allChildren.size(); i++) {
            double nodeTime = getFirstTime(allChildren.get(i).times());
            double childSum = 0;
            for (int ref : allChildren.get(i).childrenRefs()) {
                if (ref >= 0 && ref < allChildren.size()) {
                    childSum += getFirstTime(allChildren.get(ref).times());
                }
            }
            selfTimes[i] = Math.max(0, nodeTime - childSum);
        }

        // 构建父索引：从 childrenRefs 反向映射，用于向上回溯调用链
        // Spark Protobuf 的 childrenRefs 是“父→子”关系，反转为“子→父”后可从任意节点回溯到根
        int[] parentIndex = new int[allChildren.size()];
        Arrays.fill(parentIndex, -1);
        for (int i = 0; i < allChildren.size(); i++) {
            for (int ref : allChildren.get(i).childrenRefs()) {
                if (ref >= 0 && ref < allChildren.size()) {
                    parentIndex[ref] = i;
                }
            }
        }

        // 统一按 self time 聚合：Top 热点方法和插件耗时使用同一套 self time 数据
        Map<String, Double> pluginTimeMap = new HashMap<>();
        List<HotspotMethod> allCandidates = new ArrayList<>();

        // 计算归一化基数：Server thread 的总采样时间（times[0]）
        double serverTotalTime = getFirstTime(serverThread.times());
        if (serverTotalTime <= 0) serverTotalTime = 1.0;

        for (int i = 0; i < allChildren.size(); i++) {
            ParsedStackNode node = allChildren.get(i);
            double selfPercent = (selfTimes[i] / serverTotalTime) * 100.0;

            // 按 self time 剪枝：低于阈值的直接跳过
            // self time ≈ 0 意味着该帧是纯委托调用（时间全在子调用），无诊断价值
            if (selfPercent < DEFAULT_MIN_PERCENTAGE) {
                continue;
            }

            // 容器帧过滤：排除空闲等待帧（parkNanos/park）
            // 在 self time 算法下，只有这些帧的 self time 可能 > 2%（采样到空闲等待）
            if (isContainerFrame(node.className(), node.methodName())) {
                continue;
            }

            // 插件映射：classSources[class_name] → plugin name
            String pluginName = mapClassToPlugin(node.className(), classSources);

            // 标注自身插件（不排除，保留完整诊断数据）
            if (node.className().startsWith(SELF_PACKAGE_PREFIX)) {
                pluginName = I18nService.tr("Kilacraft-AI (自身)");
            }

            // 简化类名（取最后一段）
            String simpleClassName = simplifyClassName(node.className());

            // 向上回溯构建触发路径（从当前热点方法向根方向，展示“怎么被触发的”）
            String callChain = buildTriggerPath(i, allChildren, parentIndex);

            // Top 热点方法使用 self time（与插件耗时一致，每个热点只出现一次）
            allCandidates.add(new HotspotMethod(pluginName, simpleClassName, node.methodName(), selfPercent, serverThread.name(), callChain));

            // 聚合到插件耗时（self time 不会重复累加）
            pluginTimeMap.merge(pluginName, selfPercent, Double::sum);
        }

        // Top-N 热点方法（按 self time 降序）
        allCandidates.sort((a, b) -> Double.compare(b.percentage(), a.percentage()));
        List<HotspotMethod> topHotspots = allCandidates.stream().limit(maxHotspots).collect(Collectors.toList());

        // 插件 Top-N 排序
        Map<String, Double> topPlugins = pluginTimeMap.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(10).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        PluginLoggerUtil.debug(LOG_PREFIX, "热点提取完成：候选 {} 个，保留 Top {} 个", allCandidates.size(), topHotspots.size());

        return new ProcessedResult(topHotspots, topPlugins, serverThreadRatio, installedPluginNames);
    }

    /**
     * 判断线程是否为 Server thread（主线程）
     */
    private boolean isServerThread(String threadName) {
        if (threadName == null) return false;
        // 精确匹配：避免 contains("main") 误匹配 "maintenance-thread" / "DomainWorker-main" 等
        return threadName.equals("Server thread")  // Paper/Spigot
                || threadName.equals("main");        // 部分最小化平台
    }

    /**
     * 判断是否为容器帧（无诊断价值的入口/框架/空闲等待调用）
     *
     * @param className  完整类名
     * @param methodName 方法名
     * @return true 表示应跳过该帧
     */
    private boolean isContainerFrame(String className, String methodName) {
        if (className == null) return false;

        // 精确匹配容器帧类名
        if (CONTAINER_FRAME_CLASSES.contains(className)) {
            return true;
        }

        if (className.contains("$$Lambda") && ("accept".equals(methodName) || "test".equals(methodName) || "run".equals(methodName) || "get".equals(methodName))) {
            return true;
        }

        return methodName != null && methodName.startsWith("lambda$") && methodName.contains("$");
    }

    /**
     * 从 class_sources 映射类名到插件名
     *
     * <p>class_sources 中 key 是完整类名（如 "com.example.MyPlugin"），
     * value 是来源名（如 "MyPlugin"）。
     * 精确匹配优先，无匹配时尝试包名前缀匹配。</p>
     */
    private String mapClassToPlugin(String className, Map<String, String> classSources) {
        if (className == null || className.isEmpty()) {
            return "unknown";
        }

        // 精确匹配
        String exact = classSources.get(className);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }

        // 包名前缀匹配（逐级缩短包名尝试）
        int lastDot = className.lastIndexOf('.');
        while (lastDot > 0) {
            String prefix = className.substring(0, lastDot);
            String mapped = classSources.get(prefix);
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
            lastDot = prefix.lastIndexOf('.');
        }

        // 已知基础包名归类
        // 在 self time 算法下，只有Minecraft 内核与官方库的 self time 可能 > 2%
        // 均归类为 Minecraft
        String[] parts = className.split("\\.");
        if (parts.length >= 2) {
            String secondPkg = parts[0] + "." + parts[1];
            return switch (secondPkg) {
                case "net.minecraft", "ca.spottedleaf" -> "Minecraft";
                default -> secondPkg;
            };
        }
        return "unknown";
    }

    /**
     * 简化类名：com.example.plugin.MyClass → MyClass
     */
    private String simplifyClassName(String className) {
        if (className == null || className.isEmpty()) return "unknown";
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * 向上回溯构建触发路径
     *
     * <p>从当前热点节点出发，沿 parentIndex 向根方向回溯，构建完整触发路径。
     * 输出格式："Root.method → ... → Parent.method → Current.method"，
     * 帮助 AI 理解热点方法是如何被触发的。</p>
     *
     * @param nodeIndex   当前热点节点在 allChildren 中的索引
     * @param allChildren ThreadNode 的全部子节点列表
     * @param parentIndex 子→父 反向索引
     * @return 触发路径字符串
     */
    private String buildTriggerPath(int nodeIndex, List<ParsedStackNode> allChildren, int[] parentIndex) {
        List<String> path = new ArrayList<>();
        path.add(simplifyClassName(allChildren.get(nodeIndex).className()) + "." + allChildren.get(nodeIndex).methodName());

        int current = parentIndex[nodeIndex];
        int depth = 0;
        while (current >= 0 && depth < MAX_CALL_DEPTH) {
            ParsedStackNode parent = allChildren.get(current);
            path.add(simplifyClassName(parent.className()) + "." + parent.methodName());
            current = parentIndex[current];
            depth++;
        }

        // 反转：从根到当前热点方法
        Collections.reverse(path);
        return String.join(" → ", path);
    }

    /**
     * 获取 times 数组第一个值（首个时间窗口），默认 0.0
     */
    private double getFirstTime(double[] times) {
        return (times != null && times.length > 0) ? times[0] : 0.0;
    }

    /**
     * 从元数据 JSON 中提取热点方法信息
     *
     * <p>解析 Spark 元数据中的 platformStatistics 部分，
     * 提取可用于 LLM 诊断的结构化信息。</p>
     *
     * @param metadataJson 元数据 JSON 字符串
     * @return 格式化的热点摘要文本
     */
    public String extractHotspotSummary(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return I18nService.tr("无 Profiler 数据");
        }

        StringBuilder sb = new StringBuilder();

        try {
            JsonObject meta = JsonParser.parseString(metadataJson).getAsJsonObject();
            if (meta.has("metadata") && meta.get("metadata").isJsonObject()) {
                JsonObject metadata = meta.getAsJsonObject("metadata");
                if (metadata.has("platformStatistics") && metadata.get("platformStatistics").isJsonObject()) {
                    sb.append(formatPlatformStats(metadata.getAsJsonObject("platformStatistics")));
                }
            }
        } catch (Exception e) {
            sb.append(I18nService.tr("解析失败: {}", e.getMessage()));
        }

        return sb.toString();
    }

    private String formatPlatformStats(JsonObject stats) {
        StringBuilder sb = new StringBuilder();

        if (stats.has("tps") && stats.get("tps").isJsonObject()) {
            var tps = stats.getAsJsonObject("tps");
            sb.append("TPS: 1m=").append(JsonSafeGetUtil.fmtDouble(tps, "last1m")).append(" 5m=").append(JsonSafeGetUtil.fmtDouble(tps, "last5m")).append(" 15m=").append(JsonSafeGetUtil.fmtDouble(tps, "last15m")).append("\n");
        }
        if (stats.has("mspt") && stats.get("mspt").isJsonObject()) {
            var mspt = stats.getAsJsonObject("mspt");
            if (mspt.has("last1m") && mspt.get("last1m").isJsonObject()) {
                var last1m = mspt.getAsJsonObject("last1m");
                sb.append("MSPT: mean=").append(JsonSafeGetUtil.fmtDouble(last1m, "mean")).append("ms max=").append(JsonSafeGetUtil.fmtDouble(last1m, "max")).append("ms p95=").append(JsonSafeGetUtil.fmtDouble(last1m, "percentile95")).append("ms\n");
            }
        }
        if (stats.has("ping") && stats.get("ping").isJsonObject()) {
            var ping = stats.getAsJsonObject("ping");
            if (ping.has("last15m") && ping.get("last15m").isJsonObject()) {
                var ping15m = ping.getAsJsonObject("last15m");
                sb.append("Ping: mean=").append(JsonSafeGetUtil.fmtDouble(ping15m, "mean")).append("ms max=").append(JsonSafeGetUtil.fmtDouble(ping15m, "max")).append("ms\n");
            }
        }
        if (stats.has("memory") && stats.get("memory").isJsonObject()) {
            var mem = stats.getAsJsonObject("memory");
            if (mem.has("heap") && mem.get("heap").isJsonObject()) {
                var heap = mem.getAsJsonObject("heap");
                sb.append("Memory: ").append(JsonSafeGetUtil.fmtMemLong(heap, "used")).append("/").append(JsonSafeGetUtil.fmtMemLong(heap, "max")).append("\n");
            }
        }
        if (stats.has("world") && stats.get("world").isJsonObject()) {
            JsonObject world = stats.getAsJsonObject("world");
            sb.append("Entities: ").append(JsonSafeGetUtil.fmtInt(world, "totalEntities")).append("\n");
        }
        if (stats.has("playerCount")) {
            sb.append("Players: ").append(stats.get("playerCount").getAsInt()).append("\n");
        }

        return sb.toString();
    }
}
