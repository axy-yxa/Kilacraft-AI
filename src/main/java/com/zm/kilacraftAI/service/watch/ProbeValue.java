package com.zm.kilacraftAI.service.watch;

import com.zm.kilacraftAI.i18n.I18nService;

import java.util.Objects;

/**
 * 监听取值结果：带类型标签的值包装。
 *
 * <p>三种类型对应三种判定路径：
 * <ul>
 *   <li>{@link Type#NUMBER} — 数值比较（{@code > >= < <= == !=}）</li>
 *   <li>{@link Type#BOOLEAN} — true 即触发（false 永不触发）</li>
 *   <li>{@link Type#STRING} — 相等/包含匹配（{@code == != contains}）</li>
 * </ul>
 *
 * <p>{@link #from(Object)} 自动识别 Java 类型并宽容匹配边界情况
 * （字符串 "true"/"false" → BOOLEAN，字符串 "1"/"0" → NUMBER 等）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class ProbeValue {

    public enum Type {NUMBER, BOOLEAN, STRING}

    private final Type type;
    private final Object raw;

    private ProbeValue(Type type, Object raw) {
        this.type = type;
        this.raw = raw;
    }

    public static ProbeValue number(double value) {
        return new ProbeValue(Type.NUMBER, value);
    }

    public static ProbeValue booleanValue(boolean value) {
        return new ProbeValue(Type.BOOLEAN, value);
    }

    public static ProbeValue string(String value) {
        return new ProbeValue(Type.STRING, value);
    }

    /**
     * 从任意 Java 对象自动识别类型构造 ProbeValue。
     * <ul>
     *   <li>Number → NUMBER</li>
     *   <li>Boolean → BOOLEAN</li>
     *   <li>字符串 "true"/"false"（忽略大小写）→ BOOLEAN</li>
     *   <li>字符串纯数字 → NUMBER</li>
     *   <li>其他字符串 → STRING</li>
     *   <li>null → STRING("")</li>
     * </ul>
     */
    public static ProbeValue from(Object raw) {
        if (raw == null) {
            return string("");
        }
        if (raw instanceof Boolean b) {
            return booleanValue(b);
        }
        if (raw instanceof Number n) {
            return number(n.doubleValue());
        }
        String s = raw.toString();
        String lower = s.toLowerCase();
        if ("true".equals(lower)) {
            return booleanValue(true);
        }
        if ("false".equals(lower)) {
            return booleanValue(false);
        }
        try {
            double d = Double.parseDouble(s);
            if (Double.isFinite(d)) {
                return number(d);
            }
            // NaN / Infinity 不当数值，回退为字符串
        } catch (NumberFormatException ignored) {
            // 不是数字
        }
        return string(s);
    }

    public Type type() {
        return type;
    }

    /**
     * NUMBER 类型的 double 值；非 NUMBER 抛异常。
     */
    public double asNumber() {
        if (type != Type.NUMBER) {
            throw new IllegalStateException(I18nService.tr("ProbeValue 不是 NUMBER 类型: {}", type));
        }
        return (Double) raw;
    }

    /**
     * BOOLEAN 类型的 boolean 值；非 BOOLEAN 抛异常。
     */
    public boolean asBoolean() {
        if (type != Type.BOOLEAN) {
            throw new IllegalStateException(I18nService.tr("ProbeValue 不是 BOOLEAN 类型: {}", type));
        }
        return (Boolean) raw;
    }

    /**
     * STRING 类型的字符串值；非 STRING 抛异常。
     */
    public String asString() {
        if (type != Type.STRING) {
            throw new IllegalStateException(I18nService.tr("ProbeValue 不是 STRING 类型: {}", type));
        }
        return (String) raw;
    }

    /**
     * 原始值的字符串表示（通知 AI / 事件存档用）。
     */
    public String displayValue() {
        return Objects.toString(raw);
    }

    @Override
    public String toString() {
        return type + ":" + displayValue();
    }
}
