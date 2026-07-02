package com.zm.kilacraftAI.service.guardian.predicate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 谓词原语注册表：按名字登记原语描述符（含语义说明 + 参数规格 + 工厂）。
 *
 * <p>用途：
 * <ol>
 *   <li>反序列化守护配置 JSON 重建谓词树（Step 3 接入）；</li>
 *   <li>聚合描述符喂给「建-monitor」LLM 提示词，让 LLM 可发现可用原语（§4.7：注册即 LLM 可发现）。</li>
 * </ol>
 * 首批内置原语由 {@link BuiltInPredicates#registerDefaults} 落地。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class PredicateRegistry {

    private final Map<String, Descriptor> descriptors = new ConcurrentHashMap<>();

    public void register(Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        descriptors.put(descriptor.name(), descriptor);
    }

    public Optional<Descriptor> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(name));
    }

    /** 全部描述符的不可变视图。 */
    public Map<String, Descriptor> descriptors() {
        return Map.copyOf(descriptors);
    }

    /** 已注册原语数量。 */
    public int size() {
        return descriptors.size();
    }

    /**
     * 单条原语描述符：名字 + 语义说明 + 参数规格 + 工厂。
     * description / params 供「建-monitor」LLM 提示词使用，须带语义、按需更新。
     */
    public record Descriptor(String name,
                             String description,
                             List<ParamSpec> params,
                             Factory factory) {

        public Descriptor {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            params = params == null ? List.of() : List.copyOf(params);
            Objects.requireNonNull(factory, "factory");
        }
    }

    /**
     * 参数规格：名字 + 类型 + 说明 + 是否必填。
     * {@code type} 为约定字符串：{@code number} / {@code string} / {@code material} / {@code entityType} / {@code comparison}。
     */
    public record ParamSpec(String name, String type, String description, boolean required) {

        public ParamSpec {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    /** 从配置参数重建谓词实例。Step 3 的 JSON 反序列化器按 Descriptor.params 解析 Map 后调用。 */
    @FunctionalInterface
    public interface Factory {
        Predicate create(Map<String, ?> params);
    }
}
