package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;

/** 星级色（派单渲染要求二：颜色=级别，与图形正交）。
 *  1★ 骨白 / 2★ 金 / 3★ 紫——common→rare→epic 梯度；与 SideColors（蓝/红框）、
 *  血条红绿、能量黄全部错开（2★ 金较能量黄更饱和且载体为 24px 本体，可辨）。 */
public final class StarColors {
    public static final Color STAR_1 = new Color(0.82f, 0.82f, 0.78f, 1f);
    public static final Color STAR_2 = new Color(0.98f, 0.78f, 0.22f, 1f);
    public static final Color STAR_3 = new Color(0.72f, 0.40f, 0.96f, 1f);

    private StarColors() {
    }

    /** 星级 → tint 色（越界钳制到 1/3 星） */
    public static Color of(int star) {
        if (star <= 1) { return STAR_1; }
        if (star == 2) { return STAR_2; }
        return STAR_3;
    }
}
