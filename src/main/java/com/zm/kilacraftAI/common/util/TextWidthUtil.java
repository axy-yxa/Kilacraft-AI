package com.zm.kilacraftAI.common.util;

import org.bukkit.ChatColor;

/**
 * 文本显示宽度计算工具。
 *
 * <p>提供 Minecraft 聊天框/控制台等宽环境下字符串可见列宽的计算能力，
 * 核心模型：全角字符（CJK 表意文字、假名、全角符号等）计 2 列，半角计 1 列。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-29
 */
public final class TextWidthUtil {

    private TextWidthUtil() {
    }

    /**
     * 计算字符串在等宽终端的可见显示宽度。
     *
     * <p>全角字符（中日韩表意、假名、全角符号等）计 2 列，半角计 1 列。</p>
     *
     * @param s 文本
     * @return 可见列宽
     */
    public static int displayWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            width += isFullWidth(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    /**
     * 判断码点是否为全角字符（占用 2 个等宽列）。
     *
     * <p>覆盖主要的中日韩表意文字、假名、谚文及全角符号区间。</p>
     *
     * @param cp Unicode 码点
     * @return true 表示该字符为全角
     */
    public static boolean isFullWidth(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)   // 谚文 Jamo
                || (cp >= 0x2E80 && cp <= 0x303E) // CJK 部首与标点
                || (cp >= 0x3041 && cp <= 0x33FF) // 假名 / 谚文 / CJK 符号
                || (cp >= 0x3400 && cp <= 0x4DBF) // CJK 扩展 A
                || (cp >= 0x4E00 && cp <= 0x9FFF) // CJK 统一表意
                || (cp >= 0xA000 && cp <= 0xA4CF) // 彝文
                || (cp >= 0xAC00 && cp <= 0xD7A3) // 谚文音节
                || (cp >= 0xF900 && cp <= 0xFAFF) // CJK 兼容表意
                || (cp >= 0xFE30 && cp <= 0xFE4F) // CJK 兼容形式
                || (cp >= 0xFF00 && cp <= 0xFF60) // 全角 ASCII
                || (cp >= 0xFFE0 && cp <= 0xFFE6) // 全角符号
                || (cp >= 0x20000 && cp <= 0x2FFFD) // CJK 扩展 B-F
                || (cp >= 0x30000 && cp <= 0x3FFFD); // CJK 扩展 G+
    }

    /**
     * 移除字符串中的 Minecraft 颜色/格式码（{@code §x}），返回纯文本。
     *
     * @param s 含颜色码的字符串
     * @return 去色后的纯文本，{@code s} 为 null 时返回空串
     */
    public static String stripColors(String s) {
        if (s == null) return "";
        return ChatColor.stripColor(s);
    }
}
