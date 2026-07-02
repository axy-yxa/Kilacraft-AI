package com.zm.kilacraftAI.service.guardian.predicate;

/**
 * 数值比较运算符。原语用它把读到的数值转为布尔，并附带阈值语义。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum Comparison {
    GREATER_EQUAL(">="),
    GREATER(">"),
    LESS_EQUAL("<="),
    LESS("<"),
    EQUAL("==");

    private final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** 按 this 比较 actual 与 threshold。 */
    public boolean test(double actual, double threshold) {
        return switch (this) {
            case GREATER_EQUAL -> actual >= threshold;
            case GREATER -> actual > threshold;
            case LESS_EQUAL -> actual <= threshold;
            case LESS -> actual < threshold;
            case EQUAL -> actual == threshold;
        };
    }
}
