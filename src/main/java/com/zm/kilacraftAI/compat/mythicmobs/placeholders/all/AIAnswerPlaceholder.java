package com.zm.kilacraftAI.compat.mythicmobs.placeholders.all;

import com.zm.kilacraftAI.KilacraftAI;
import io.lumine.mythic.core.skills.placeholders.PlaceholderContext;
import io.lumine.mythic.core.skills.placeholders.segments.types.ResolvedPlaceholderSegment;
import io.lumine.mythic.core.skills.placeholders.types.EntityScopedPlaceholder;
import io.lumine.mythic.core.skills.placeholders.types.GenericPlaceholderTypes.StringPlaceholder;
import io.lumine.mythic.core.utils.annotations.MythicPlaceholder;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * AI 回答占位符
 * 用法: <caster.ai.answer{type=人偶类型}>
 */
@MythicPlaceholder(placeholder = "ai.answer", usedPlaceholderArguments = -1)
public class AIAnswerPlaceholder extends EntityScopedPlaceholder<String> implements StringPlaceholder {

    private final ResolvedPlaceholderSegment<PlaceholderString> typeSegment;

    public AIAnswerPlaceholder(EntityScopedPlaceholderArguments arguments) {
        super(arguments);
        // 获取 type 参数（使用 mlcGetter 从 MythicLineConfig 获取 {type=xxx} 格式）
        this.typeSegment = this.<PlaceholderString>getResolver().mlcGetter(mythicLineConfig -> {
            String typeValue = mythicLineConfig.getString(new String[]{"type"}, null);
            return typeValue == null ? null : PlaceholderString.of(typeValue);
        }).segIndex(0).build().get();
        // 初始化动态参数支持
        initializeMetaKeywords();
    }

    @Nullable
    @Override
    public String applyToScope(PlaceholderContext placeholderContext) {
        try {
            // 获取实体
            var entity = getEntity.get(placeholderContext);
            if (entity == null) {
                return "[错误：无法获取实体]";
            }

            // 获取 UUID
            UUID casterId = null;
            var bukkitEntity = entity.getBukkitEntity();
            if (bukkitEntity instanceof Player player) {
                casterId = player.getUniqueId();
            } else if (bukkitEntity instanceof org.bukkit.entity.Entity bukkitEntity2) {
                casterId = bukkitEntity2.getUniqueId();
            }

            if (casterId == null) {
                return "[错误：无法获取 UUID]";
            }

            // 获取人偶类型参数
            var placeholderString = typeSegment.value();
            if (placeholderString == null) {
                return "[错误：必须指定 type 参数]";
            }
            String type = placeholderString.get();

            // 从 ConversationManager 获取 AI 回复
            String response = KilacraftAI.getInstance().getConversationManager().getLatestAIResponse(casterId, type);
            return Objects.requireNonNullElse(response, "[暂无 AI 回复]");
        } catch (Exception e) {
            // 尝试获取插件实例记录错误
            try {
                var plugin = KilacraftAI.getInstance();
                if (plugin != null && plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().severe("AI 占位符解析失败：" + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception ignored) {
            }
            return "[占位符解析错误]";
        }
    }
}
