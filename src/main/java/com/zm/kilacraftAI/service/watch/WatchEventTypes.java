package com.zm.kilacraftAI.service.watch;

import com.zm.kilacraftAI.i18n.I18nService;

import java.util.Set;

/**
 * 支持的事件监听类型及其描述生成。
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
final class WatchEventTypes {

    private WatchEventTypes() {
    }

    /**
     * 支持的事件类型集合。
     */
    private static final Set<String> SUPPORTED = Set.of("furnace_smelt", "crop_mature", "entity_death", "entity_spawn", "player_death", "player_teleport", "player_level_change", "player_changed_world", "block_break", "player_fish", "player_chat");

    /**
     * 判定 eventType 是否受支持。
     */
    static boolean isSupported(String eventType) {
        return eventType != null && SUPPORTED.contains(eventType);
    }

    /**
     * 生成事件触发的自然语言描述（用于通知 AI + 写事件存档）。
     *
     * @param eventType   事件类型
     * @param filterValue 事件提供的 filter 值（如产物类型、实体类型），可为 null
     * @return 自然语言描述
     */
    static String describeEvent(String eventType, String filterValue) {
        String desc = switch (eventType != null ? eventType : "") {
            case "furnace_smelt" ->
                    filterValue != null ? I18nService.tr("熔炉烧好了{}", filterValue) : I18nService.tr("熔炉烧炼完成");
            case "crop_mature" ->
                    filterValue != null ? I18nService.tr("作物成熟了（{}）", filterValue) : I18nService.tr("附近的作物成熟了");
            case "entity_death" ->
                    filterValue != null ? I18nService.tr("击杀了{}", filterValue) : I18nService.tr("击杀了一个实体");
            case "entity_spawn" ->
                    filterValue != null ? I18nService.tr("附近生成了{}", filterValue) : I18nService.tr("附近生成了一个实体");
            case "player_death" -> I18nService.tr("你死了");
            case "player_teleport" -> I18nService.tr("你传送了");
            case "player_level_change" -> I18nService.tr("你的经验等级变化了");
            case "player_changed_world" -> I18nService.tr("你切换了世界");
            case "block_break" ->
                    filterValue != null ? I18nService.tr("你破坏了{}", filterValue) : I18nService.tr("你破坏了一个方块");
            case "player_fish" -> I18nService.tr("你钓到了东西");
            case "player_chat" ->
                    filterValue != null ? I18nService.tr("你说了包含「{}」的话", filterValue) : I18nService.tr("你发了消息");
            default -> I18nService.tr("监听事件触发：{}", eventType);
        };
        return desc;
    }
}
