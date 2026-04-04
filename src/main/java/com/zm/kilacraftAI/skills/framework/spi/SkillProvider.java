package com.zm.kilacraftAI.skills.framework.spi;

import com.zm.kilacraftAI.skills.framework.Skill;

import java.util.List;

/**
 * Skill 提供者接口（SPI）
 *
 * <p>第三方插件开发者实现此接口，用于向 Kilacraft-AI 注册自定义 Skill。</p>
 * <p>使用 Bukkit ServicesManager 机制进行自动发现。</p>
 *
 * <h3>接入步骤：</h3>
 * <ol>
 *     <li>在自己的插件项目中引用 {@code Kilacraft-Skill-API.jar} 作为 compileOnly 依赖</li>
 *     <li>在自己的插件 {@code onEnable()} 中通过 Bukkit ServicesManager 注册：
 *         <pre>{@code
 *         Bukkit.getServicesManager().register(
 *             SkillProvider.class,
 *             this,                                    // 你的插件实例
 *             ServicePriority.Normal
 *         );
 *         }</pre>
 *     </li>
 *     <li>Kilacraft-AI 会在服务器启动后自动扫描并加载注册。</li>
 *     <li>在插件卸载时自动注销。</li>
 * </ol>
 *
 * <h3>示例代码：</h3>
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin implements SkillProvider {
 *     private final Skill myCustomSkill;
 *
 *     public MyPlugin() {
 *         this.myCustomSkill = new MyCustomSkill();
 *     }
 *
 *     public List<Skill> getSkills() {
 *         return List.of(myCustomSkill);
 *     }
 * }
 * }</pre>
 *
 * @author Zm_Mmm
 * @since 2026-04-04
 */
public interface SkillProvider {

    /**
     * 获取此 Provider 提供的所有 Skill 实例
     *
     * <p>每个 Skill 实例必须实现 {@link Skill} 接口。</p>
     * <p>建议每个 Skill 使用独立的实例（而非共享状态)。</p>
     *
     * @return Skill 实例列表，不应返回 null（无 Skill 时返回空列表）
     */
    List<Skill> getSkills();
}
