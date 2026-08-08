package com.zm.kilacraftAI.service.command;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 命令文档解析器
 *
 * <p>解析命令文档（{@code commands/commands.md} 中文默认 / {@code commands/commands_en.md} 英文），
 * 提取结构化命令条目。格式：{@code ## 命令名} 标题 + 紧跟的字段行
 * （说明/description、示例/example、权限/permission、关键词/keywords，字段名别名大小写不敏感）。</p>
 *
 * <p>容错策略：单条目解析失败记 warn 跳过，不中断整体解析；缺「说明」的条目跳过，
 * 缺「示例」回退用命令名，缺「权限」视为无限制，缺「关键词」用空列表。</p>
 *
 * @author Zm_Mmm
 * @since 2026-08-03
 */
public final class CommandDocumentParser {

    private static final String LOG_PREFIX = "命令文档";

    private CommandDocumentParser() {
    }

    /**
     * 解析命令文档文件。
     *
     * @param path 命令文档路径（不存在时返回空文档）
     * @return 解析结果；文件不存在或读取失败返回空文档（isEmpty=true）
     */
    public static CommandDocument parse(Path path) {
        if (path == null || !Files.exists(path)) {
            return new CommandDocument(true, List.of());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("读取命令文档失败：{}", path), e);
            return new CommandDocument(true, List.of());
        }

        Map<String, CommandEntry> entries = new LinkedHashMap<>();
        String currentCommand = null;
        String currentDescription = null;
        String currentExample = null;
        String currentPermission = null;
        List<String> currentKeywords = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 标题行：## 命令名 → 结束当前条目，开始新条目（须在注释行判断之前，## 也以 # 开头）
            if (line.startsWith("## ")) {
                // 提交当前条目
                if (currentCommand != null) {
                    commitEntry(entries, currentCommand, currentDescription, currentExample, currentPermission, currentKeywords);
                }
                String command = line.substring(3).trim();
                // 去除前导 /
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                currentCommand = command.isEmpty() ? null : command;
                currentDescription = null;
                currentExample = null;
                currentPermission = null;
                currentKeywords = null;
                continue;
            }

            // 注释行（非字段行）忽略
            if (line.startsWith("#")) {
                continue;
            }

            // 字段行：字段名: 值（中英文别名，大小写不敏感）
            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0 || currentCommand == null) {
                // 命令名外的孤立行：跳过（可能是格式错误标题行或杂散文本）
                continue;
            }
            String fieldName = line.substring(0, colonIdx).trim().toLowerCase(Locale.ROOT);
            String fieldValue = line.substring(colonIdx + 1).trim();
            switch (fieldName) {
                case "说明", "description" -> currentDescription = fieldValue;
                case "示例", "example" -> currentExample = fieldValue;
                case "权限", "permission" -> currentPermission = fieldValue.isEmpty() ? null : fieldValue;
                case "关键词", "keywords" -> currentKeywords = parseKeywords(fieldValue);
                default -> {
                    // 未知字段：忽略
                }
            }
        }

        // 提交最后一个条目
        if (currentCommand != null) {
            commitEntry(entries, currentCommand, currentDescription, currentExample, currentPermission, currentKeywords);
        }

        if (entries.isEmpty()) {
            return new CommandDocument(true, List.of());
        }
        return new CommandDocument(false, List.copyOf(entries.values()));
    }

    /**
     * 提交单条命令条目。缺「说明」的条目记 warn 跳过；重复命令名后者覆盖前者 + warn。
     */
    private static void commitEntry(Map<String, CommandEntry> entries, String command, String description, String example, String permission, List<String> keywords) {
        if (description == null || description.isEmpty()) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("命令文档条目缺少说明，已跳过: {}", command));
            return;
        }
        if (entries.containsKey(command)) {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("命令文档重复命令名，后者覆盖前者: {}", command));
        }
        String resolvedExample = (example == null || example.isEmpty()) ? command : example;
        List<String> resolvedKeywords = keywords != null ? keywords : List.of();
        entries.put(command, new CommandEntry(command, description, resolvedExample, permission, resolvedKeywords));
    }

    /**
     * 解析关键词字段：逗号分隔，trim 后去空。
     */
    private static List<String> parseKeywords(String value) {
        List<String> keywords = new ArrayList<>();
        for (String part : value.split(",")) {
            String kw = part.trim();
            if (!kw.isEmpty()) {
                keywords.add(kw);
            }
        }
        return keywords;
    }
}
