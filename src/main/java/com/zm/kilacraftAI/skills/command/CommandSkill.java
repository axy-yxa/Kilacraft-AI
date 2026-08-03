package com.zm.kilacraftAI.skills.command;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.BukkitCommandUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.command.CommandDocument;
import com.zm.kilacraftAI.service.command.CommandDocumentParser;
import com.zm.kilacraftAI.service.command.CommandEntry;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 命令执行技能
 *
 * <p>以玩家身份执行服务器命令，权限边界等于玩家自身的权限。玩家没有的命令权限，
 * AI 代执行也会被服务器拒绝（Bukkit 权限系统是最终守卫）。</p>
 *
 * <p>命令文档机制：启动时释放预置命令文档（含插件自身命令），服主可补充第三方命令。
 * Phase1 经 {@link CallerDescriptionProvider#getCallerDescription(Player)} 注入当前玩家可见命令摘要，
 * Phase2 经 {@link #getDynamicContext(Player)} 注入完整命令列表——均按玩家权限过滤，无权命令不展示给 AI。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-27
 */
public class CommandSkill implements Skill, DynamicContextProvider, CallerDescriptionProvider {

    private static final String SKILL_NAME = "command";
    private static final String LOG_PREFIX = "命令技能";

    /**
     * 命令文档缓存（volatile 支持热重载）
     */
    private volatile CommandDocument commandDocument;

    private final SkillConfigManager configManager;

    public CommandSkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }

        this.commandDocument = CommandDocumentParser.parse(resolveCommandDocPath());
    }

    /**
     * 获取当前最新的技能配置（支持热重载）
     */
    private SkillConfig getConfig() {
        if (configManager == null) {
            return null;
        }
        return configManager.getSkillConfig(this);
    }

    /**
     * 获取静态技能描述（yml 配置）
     */
    private String getStaticDescription() {
        SkillConfig config = getConfig();
        if (config != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        return null;
    }

    @Override
    public String getDescription() {
        return getStaticDescription();
    }

    /**
     * Phase1 按调用者权限定制描述：静态定位说明 + 当前玩家可见命令摘要。
     * 玩家无可见命令或文档为空时只返回静态部分。
     */
    @Override
    public String getCallerDescription(Player caller) {
        String staticDesc = getStaticDescription();
        String summary = buildPhase1Summary(caller);
        if (summary == null) {
            return staticDesc;
        }
        if (staticDesc == null) {
            return summary;
        }
        return staticDesc + "\n" + summary;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        if (config != null && config.getActionDescriptions() != null) {
            return new LinkedHashMap<>(config.getActionDescriptions());
        }
        return Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        if (config != null && config.getHints() != null && !config.getHints().isEmpty()) {
            return new ArrayList<>(config.getHints());
        }
        return new ArrayList<>();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.COMMAND_EXECUTE.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return context.getPlayer() != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }
        if (!PluginPermissionEnum.COMMAND_EXECUTE.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.COMMAND_EXECUTE.getNode())));
        }

        String action = context.getAction();
        if (!"execute_command".equals(action)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
        }
        return executeCommand(player, context);
    }

    /**
     * 执行命令
     *
     * <p>以玩家身份执行命令，通过 BukkitCommandUtil 确保在主线程执行。</p>
     */
    private CompletableFuture<SkillResult> executeCommand(Player player, SkillContext context) {
        String rawCommand = SkillEntityHelper.getString(context, "command");
        if (rawCommand == null) {
            // 缺命令是「用户没说完」而非调用错误，用 needInfo 引导补全（与 yml hints 描述一致）
            return CompletableFuture.completedFuture(SkillResult.needInfo(I18nService.tr("请告诉我要执行什么命令，例如「传送到出生点」或「领取每日礼包」")));
        }

        // 移除前导 /（用户可能包含也可能不包含）
        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        final String finalCommand = command;

        PluginLoggerUtil.debug(LOG_PREFIX, "玩家 {} 通过 AI 执行命令: /{}", player.getName(), finalCommand);

        // 使用 dispatchAsync 获取执行结果
        return BukkitCommandUtil.dispatchAsync(player, finalCommand).thenApply(success -> {
            if (success) {
                return SkillResult.success(I18nService.tr("已执行命令: /{}", finalCommand));
            } else {
                return SkillResult.failure(I18nService.tr("命令执行失败，可能没有权限或命令不存在: /{}", finalCommand));
            }
        }).exceptionally(ex -> {
            PluginLoggerUtil.warn(LOG_PREFIX, I18nService.tr("命令执行异常: /{} - {}", finalCommand, ex.getMessage()), ex);
            return SkillResult.failure(I18nService.tr("命令执行异常: /{}", finalCommand));
        });
    }

    /**
     * Phase2 动态上下文：当前玩家可见命令的完整列表（命令名 + 说明 + 示例）。
     * 玩家无可见命令或文档为空时返回空字符串（buildPhase2SkillDescription 会跳过空白内容）。
     */
    @Override
    public String getDynamicContext(Player player) {
        CommandDocument doc = commandDocument;
        if (doc == null || doc.isEmpty()) {
            return "";
        }
        List<CommandEntry> visible = filterByPermission(doc.entries(), player);
        if (visible.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("可执行命令列表：")).append("\n");
        for (CommandEntry entry : visible) {
            sb.append("  ").append(entry.command()).append(" - ").append(entry.description());
            if (entry.example() != null && !entry.example().isEmpty()) {
                sb.append(I18nService.tr("。示例: {}", entry.example()));
            }
            sb.append("\n");
        }
        sb.append(I18nService.tr("command 参数应优先使用上方命令列表中记录的命令。如果用户需求不在列表中，优先告知用户「当前没有找到该命令的使用说明」而非自行编造命令。"));
        return sb.toString();
    }

    /**
     * Phase1 摘要：当前玩家可见命令的命令名 + 首个关键词摘要，逗号分隔。
     * 玩家无可见命令或文档为空时返回 null（description 不追加动态部分）。
     */
    private String buildPhase1Summary(Player caller) {
        CommandDocument doc = commandDocument;
        if (doc == null || doc.isEmpty()) {
            return null;
        }
        List<CommandEntry> visible = filterByPermission(doc.entries(), caller);
        if (visible.isEmpty()) {
            return null;
        }

        String summary = visible.stream().map(entry -> entry.command() + "（" + firstKeyword(entry) + "）").collect(Collectors.joining("、"));
        return I18nService.tr("当前可执行命令：{} 等 {} 条。", summary, visible.size()) + "\n" + I18nService.tr("command 参数应优先使用上述命令列表中记录的命令。");
    }

    /**
     * 取条目的首个关键词，无关键词时回退用命令名本身。
     */
    private String firstKeyword(CommandEntry entry) {
        if (entry.keywords() != null && !entry.keywords().isEmpty()) {
            return entry.keywords().get(0);
        }
        return entry.command();
    }

    /**
     * 按玩家权限过滤命令条目。player 为 null（控制台）视为拥有所有权限。
     */
    private List<CommandEntry> filterByPermission(List<CommandEntry> entries, Player player) {
        if (player == null) {
            return entries;
        }
        return entries.stream().filter(entry -> entry.permission() == null || player.hasPermission(entry.permission())).toList();
    }

    /**
     * 解析命令文档路径：中文读 commands/commands.md，非中文读 commands/&lt;lang&gt;/commands.md。
     */
    private static Path resolveCommandDocPath() {
        Path commandsDir = Paths.get(KilacraftAI.getInstance().getDataFolder().toString(), "commands");
        String lang = KilacraftAI.getInstance().getI18nService().getLanguage();
        if ("zh".equals(lang)) {
            return commandsDir.resolve("commands.md");
        }
        return commandsDir.resolve(lang).resolve("commands.md");
    }

    /**
     * 热重载命令文档（/kila reload 级联调用）。
     */
    public void reloadCommandDocument() {
        this.commandDocument = CommandDocumentParser.parse(resolveCommandDocPath());
        PluginLoggerUtil.info(LOG_PREFIX, "命令文档已重载");
    }
}
