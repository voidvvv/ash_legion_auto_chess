package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;

/**
 * 占位配色（render §7.5；口径 #14）：FNV-1a 纯字符串 hash → 32 色调色板索引。
 * 同 id 恒同色（确定性），零模拟 RNG 消耗。纯 JVM Color 可测。
 */
public final class PalettePick {

    /** FNV-1a 32 位偏移基 */
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    /** FNV-1a 32 位素数 */
    private static final int FNV_PRIME = 0x01000193;

    /** 32 色调色板（手挑可区分色，静态只读） */
    private static final Color[] PALETTE = {
            new Color(0.90f, 0.20f, 0.20f, 1f), new Color(0.95f, 0.45f, 0.15f, 1f),
            new Color(0.98f, 0.75f, 0.20f, 1f), new Color(0.80f, 0.85f, 0.20f, 1f),
            new Color(0.55f, 0.80f, 0.25f, 1f), new Color(0.25f, 0.75f, 0.35f, 1f),
            new Color(0.20f, 0.70f, 0.60f, 1f), new Color(0.20f, 0.65f, 0.80f, 1f),
            new Color(0.25f, 0.50f, 0.90f, 1f), new Color(0.40f, 0.35f, 0.85f, 1f),
            new Color(0.60f, 0.30f, 0.80f, 1f), new Color(0.80f, 0.30f, 0.60f, 1f),
            new Color(0.50f, 0.35f, 0.25f, 1f), new Color(0.45f, 0.50f, 0.40f, 1f),
            new Color(0.35f, 0.45f, 0.55f, 1f), new Color(0.95f, 0.60f, 0.55f, 1f),
            new Color(0.70f, 0.15f, 0.35f, 1f), new Color(0.15f, 0.45f, 0.30f, 1f),
            new Color(0.65f, 0.55f, 0.15f, 1f), new Color(0.15f, 0.30f, 0.55f, 1f),
            new Color(0.55f, 0.15f, 0.55f, 1f), new Color(0.85f, 0.65f, 0.30f, 1f),
            new Color(0.30f, 0.65f, 0.50f, 1f), new Color(0.50f, 0.75f, 0.85f, 1f),
            new Color(0.75f, 0.40f, 0.30f, 1f), new Color(0.40f, 0.30f, 0.20f, 1f),
            new Color(0.20f, 0.20f, 0.35f, 1f), new Color(0.88f, 0.88f, 0.45f, 1f),
            new Color(0.45f, 0.85f, 0.65f, 1f), new Color(0.65f, 0.45f, 0.85f, 1f),
            new Color(0.85f, 0.45f, 0.70f, 1f), new Color(0.60f, 0.60f, 0.60f, 1f),
    };

    private PalettePick() {
    }

    /** id → 调色板色（同 id 恒同实例） */
    public static Color pick(String id) {
        return PALETTE[fnv1a(id) & 31];
    }

    /** 调色板色按索引（0~31） */
    public static Color paletteColor(int index) {
        if (index < 0 || index >= PALETTE.length) {
            throw new IllegalArgumentException("调色板索引必须在 0~" + (PALETTE.length - 1) + "，实际=" + index);
        }
        return PALETTE[index];
    }

    /** FNV-1a 32 位字符串 hash（小写输入的确定性向量与标准一致） */
    public static int fnv1a(String s) {
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
