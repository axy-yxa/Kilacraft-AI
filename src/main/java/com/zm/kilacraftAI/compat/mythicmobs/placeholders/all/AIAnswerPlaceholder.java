package com.zm.kilacraftAI.compat.mythicmobs.placeholders.all;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.PluginLogger;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.core.skills.placeholders.PlaceholderContext;
import io.lumine.mythic.core.skills.placeholders.segments.types.ResolvedPlaceholderSegment;
import io.lumine.mythic.core.skills.placeholders.types.EntityScopedPlaceholder;
import io.lumine.mythic.core.skills.placeholders.types.GenericPlaceholderTypes.StringPlaceholder;
import io.lumine.mythic.core.utils.annotations.MythicPlaceholder;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * AI 回答占位符
 * 用法：<caster.ai.answer{type=人偶类型}>
 * 支持动态占位符：<caster.ai.answer{type=<skill.puppet>}>
 */
@MythicPlaceholder(placeholder = "ai.answer", usedPlaceholderArguments = -1)
public class AIAnswerPlaceholder extends EntityScopedPlaceholder<String> implements StringPlaceholder {

    private final ResolvedPlaceholderSegment<PlaceholderString> typeSegment;

    public AIAnswerPlaceholder(EntityScopedPlaceholderArguments arguments) {
        super(arguments);
        // 获取 type 参数（保存为 ResolvedPlaceholderSegment，以便在 applyToScope 中解析动态占位符）
        this.typeSegment = this.<PlaceholderString>getResolver().mlcGetter(mythicLineConfig -> {
            String typeValue = mythicLineConfig.getString(new String[]{"type"}, null);
            return typeValue == null ? null : PlaceholderString.of(typeValue);
        }).segIndex(0).build().get();
        // 初始化动态参数支持
        initializeMetaKeywords();
    }

    @Override
    public String applyToScope(PlaceholderContext placeholderContext) {
        try {
            // 获取实体
            AbstractEntity entity = getEntity.get(placeholderContext);
            if (entity == null) {
                return I18nService.tr("[错误：无法获取实体]");
            }

            // 获取 UUID
            UUID casterId = null;
            org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
            if (bukkitEntity instanceof Player) {
                casterId = bukkitEntity.getUniqueId();
            } else if (bukkitEntity != null) {
                casterId = bukkitEntity.getUniqueId();
            }

            if (casterId == null) {
                return I18nService.tr("[错误：无法获取 UUID]");
            }

            // 使用 placeholderContext 解析 typeSegment，支持动态占位符
            String type = null;
            if (typeSegment != null) {
                PlaceholderString resolvedPlaceholderString = typeSegment.value();
                if (resolvedPlaceholderString != null) {
                    // 使用 PlaceholderContext 解析占位符
                    type = resolvedPlaceholderString.get(placeholderContext);
                }
            }

            if (type == null || type.isEmpty()) {
                return I18nService.tr("[错误：必须指定 type 参数]");
            }

            // 从 ConversationManager 获取并清除 AI 回复（读取后自动删除）
            String response = KilacraftAI.getInstance().getConversationManager().pollLatestAIResponse(casterId, type);

            // 如果没有数据，说明 AI 正在思考还未回复，返回等待标识
            return Objects.requireNonNullElse(response, "UNDEFINED");

        } catch (Exception e) {
            // 尝试获取插件实例记录错误
            try {
                PluginLogger.error("AI占位符", I18nService.tr("占位符解析失败: {}", e.getMessage()), e);
            } catch (Exception ignored) {
            }
            return I18nService.tr("[占位符解析错误]");
        }
    }
}
