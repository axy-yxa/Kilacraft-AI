package com.zm.kilacraftAI.service.suggestion;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.SuggestionConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.Skill;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * 构建推荐系统的 LLM 提示词。
 *
 * <p>技能摘要格式与 Phase 1 意图识别一致（name + description），本轮问/答由调用方传入的 history 提供。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-27
 */
public class SuggestionPromptBuilder {

    private final KilacraftAI plugin;
    private final SuggestionConfigManager config;

    public SuggestionPromptBuilder(KilacraftAI plugin, SuggestionConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public SuggestionPrompt build(Player player) {
        String skillsSummary = buildSkillsSummary(player);

        // {count} = max_suggestions × 2：产出上限跟随配置且为展示量的 2 倍，排序截断才有缓冲；
        // 配置值是全局的（跨玩家字节一致），不影响 system 前缀缓存
        String systemPrompt = config.getLocalizedSystemPrompt().replace("{available_skills}", skillsSummary).replace("{count}", String.valueOf(config.getMaxSuggestions() * 2));
        String userPrompt = config.getLocalizedUserPromptTemplate();
        return new SuggestionPrompt(systemPrompt, userPrompt);
    }

    /**
     * 构建技能摘要：遍历玩家可用 skill（已做权限过滤），排除黑名单，格式与 Phase 1 一致。
     */
    private String buildSkillsSummary(Player player) {
        String noSkills = I18nService.tr("（无可用技能，仅支持问答）");
        if (plugin.getSkillManager() == null) {
            return noSkills;
        }
        Set<String> excluded = config.getExcludeSkills();
        List<Skill> skills = plugin.getSkillManager().getAvailableSkills(player).stream().filter(s -> !excluded.contains(s.getName())).toList();

        if (skills.isEmpty()) {
            return noSkills;
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Skill skill : skills) {
            sb.append(index++).append(". ").append(skill.getName()).append(" - ").append(skill.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 推荐提示词快照（system + user），供 SuggestionService 发送给 LLM。
     */
    public record SuggestionPrompt(String systemPrompt, String userPrompt) {
    }
}
