package com.zm.kilacraftAI.i18n;

import java.util.List;

/**
 * 文本处理器工厂，用于国际化支持。
 * <p>根据当前语言配置返回对应的 {@link TextProcessor} 实现。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-21
 */
public final class TextProcessorFactory {

    private static volatile TextProcessor instance;

    private TextProcessorFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 根据语言配置获取当前 TextProcessor 实例。
     * 如果语言已变更，则创建新实例。
     *
     * @return 当前语言的 TextProcessor
     */
    public static TextProcessor get() {
        if (instance == null) {
            synchronized (TextProcessorFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    /**
     * 强制重新创建 TextProcessor（语言变更或 reload 时调用）
     */
    public static synchronized void reset() {
        instance = null;
    }

    /**
     * 根据当前语言配置创建 TextProcessor
     */
    private static TextProcessor create() {
        if (I18nService.isZh()) {
            return new ChineseTextProcessor();
        }
        return new EnglishTextProcessor();
    }

    /**
     * 使用自定义词典初始化 TextProcessor。
     * 应在插件启动且配置加载完毕后调用。
     *
     * @param customWords 配置中的自定义词典词汇
     */
    public static void initialize(List<String> customWords) {
        TextProcessor processor = get();
        processor.initCustomDictionary(customWords);
    }
}
