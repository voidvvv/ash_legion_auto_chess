package com.voidvvv.kz_auto_chess_n.render;

/** 单位占位图形目录（render 表达层；纯 JVM 可测）。图形=种族族、变体=职业（派单渲染要求一）。
 *  行规格：{y, halfWidth}（中心对齐逐行半宽，y 自本体顶部起）——PlaceholderArt 按行填充。 */
public final class UnitShapeKit {

    /** 图形族：种族身份的第一读法轴 */
    public enum ShapeFamily { TRIANGLE, DIAMOND, PENTAGON, HEXAGON, STAR }

    /** 族内变体：职业的第二读法轴（标记色深于本体，tint 后仍可辨） */
    public enum ClassVariant { WARRIOR, MAGE, ASSASSIN, RANGER, PLAIN }

    private UnitShapeKit() {
    }

    /** 种族 → 图形族；未登记风味种族（植物/构造/独眼等 Boss 种族）→ STAR 兜底 */
    public static ShapeFamily familyOf(String race) {
        switch (race) {
            case "兽人": return ShapeFamily.TRIANGLE;
            case "野兽": return ShapeFamily.DIAMOND;
            case "亡灵": return ShapeFamily.PENTAGON;
            case "巨人": return ShapeFamily.HEXAGON;
            default: return ShapeFamily.STAR;
        }
    }

    /** 职业 → 族内变体；Boss/未登记职业 → PLAIN */
    public static ClassVariant variantOf(String unitClass) {
        switch (unitClass) {
            case "战士": return ClassVariant.WARRIOR;
            case "法师": return ClassVariant.MAGE;
            case "刺客": return ClassVariant.ASSASSIN;
            case "游侠": return ClassVariant.RANGER;
            default: return ClassVariant.PLAIN;
        }
    }

    /** 本体高 22 行的逐行半宽表（0 顶 → 21 底；执行期允许按冒烟观感微调，行数不变） */
    public static int[] halfWidths(ShapeFamily family) {
        final int[] TRIANGLE = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11};      // 12 行（底 23 宽→顶尖）
        final int[] DIAMOND  = {1, 3, 5, 7, 9, 11, 11, 9, 7, 5, 3, 1};      // 12 行
        final int[] PENTAGON = {4, 6, 8, 10, 11, 11, 11, 11, 11, 11, 11, 11}; // 上梯形+下矩形
        final int[] HEXAGON  = {7, 9, 11, 11, 11, 11, 11, 11, 11, 11, 9, 7};
        final int[] STAR     = {1, 2, 2, 5, 8, 10, 8, 5, 3, 5, 9, 4};        // 五角星近似行表
        switch (family) {
            case TRIANGLE: return TRIANGLE;
            case DIAMOND: return DIAMOND;
            case PENTAGON: return PENTAGON;
            case HEXAGON: return HEXAGON;
            case STAR: return STAR;
        }
        throw new IllegalArgumentException("未知图形族: " + family);
    }

    /** 变体标记绘制参数（PlaceholderArt 消费；均为深色标记，乘法 tint 下保持可辨） */
    public static int[] variantMarkRows(ClassVariant variant) {
        // {起始行, 行数}：战士顶带 0~1；法师核点中心 5~6；刺客斜切 3~9；游侠空心=减行宽由画法处理
        switch (variant) {
            case WARRIOR: return new int[]{0, 2};
            case MAGE: return new int[]{5, 2};
            case ASSASSIN: return new int[]{3, 7};
            default: return new int[]{0, 0}; // RANGER/PLAIN 无附加实心带
        }
    }
}
