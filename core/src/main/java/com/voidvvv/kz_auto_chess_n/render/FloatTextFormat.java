package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

/**
 * 飘字规格映射（render §5.2；纯 JVM Color 可测，口径 #16/#24）。
 *
 * <p>事件 → 文本（Math.round 取整，Phase 3 口径 #7 的显示层兑现）/ 分色 / 尺寸。
 * 返回共享常量实例（渲染段零分配）；非数值事件返回 null（不产飘字）。
 */
public final class FloatTextFormat {

    /** 技能命中紫（skillId 非 null 的 HIT） */
    public static final Color PURPLE = new Color(0.62f, 0.36f, 0.92f, 1f);
    /** 暴击橙红（普攻 crit） */
    public static final Color ORANGE_RED = new Color(1f, 0.42f, 0.12f, 1f);
    /** 暴击飘字尺寸倍率（render §5.4 表现层自由项） */
    public static final float CRIT_SCALE = 1.2f;

    private FloatTextFormat() {
    }

    /** 飘字规格（不可变纯数据） */
    public static final class Spec {
        public final String text;
        public final Color color;
        public final float scale;

        public Spec(String text, Color color, float scale) {
            this.text = text;
            this.color = color;
            this.scale = scale;
        }
    }

    /**
     * @return HIT（普攻白 / 暴击橙红×1.2 / 技能紫）、HEALED 绿、SHIELDED 蓝 的规格；
     *         其余事件 null
     */
    public static Spec of(CombatEvent event) {
        switch (event.getType()) {
            case HIT:
                if (event.getSkillId() != null) {
                    return new Spec(round(event.getAmount()), PURPLE, 1f);
                }
                if (event.isCrit()) {
                    return new Spec(round(event.getAmount()), ORANGE_RED, CRIT_SCALE);
                }
                return new Spec(round(event.getAmount()), Color.WHITE, 1f);
            case HEALED:
                return new Spec(round(event.getAmount()), Color.GREEN, 1f);
            case SHIELDED:
                return new Spec(round(event.getAmount()), Color.BLUE, 1f);
            default:
                return null;
        }
    }

    private static String round(float amount) {
        return String.valueOf(Math.round(amount));
    }
}
