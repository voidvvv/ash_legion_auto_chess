package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FloatTextFormat 测试：事件 → 飘字规格（取整文本 / 分色 / 尺寸，口径 #16）。
 */
class FloatTextFormatTest {

    @Test
    @DisplayName("HIT 普攻：白色、scale 1、文本取整")
    void plainHitWhiteWithRoundedText() {
        CombatEvent event = CombatEvent.hit(10, 1, 2, 13.6f, false, null);
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        assertThat(spec.text).isEqualTo("14");
        assertThat(spec.color).isEqualTo(Color.WHITE);
        assertThat(spec.scale).isEqualTo(1f);
    }

    @Test
    @DisplayName("HIT 暴击：橙红、scale 1.2")
    void critHitOrangeRedScaled() {
        CombatEvent event = CombatEvent.hit(10, 1, 2, 20f, true, null);
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        assertThat(spec.text).isEqualTo("20");
        assertThat(spec.color).isSameAs(FloatTextFormat.ORANGE_RED);
        assertThat(spec.scale).isEqualTo(1.2f);
    }

    @Test
    @DisplayName("HIT 技能（skillId 非 null）：紫色、优先于暴击着色")
    void skillHitPurple() {
        CombatEvent event = CombatEvent.hit(10, 1, 2, 15f, true, "sk_fireball");
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        assertThat(spec.color).isSameAs(FloatTextFormat.PURPLE);
        assertThat(spec.scale).isEqualTo(1f);
    }

    @Test
    @DisplayName("HEALED：绿色")
    void healedGreen() {
        CombatEvent event = CombatEvent.healed(10, 1, 2, 8.2f);
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        assertThat(spec.text).isEqualTo("8");
        assertThat(spec.color).isEqualTo(Color.GREEN);
        assertThat(spec.scale).isEqualTo(1f);
    }

    @Test
    @DisplayName("SHIELDED：蓝色")
    void shieldedBlue() {
        CombatEvent event = CombatEvent.shielded(10, 1, 2, 30f);
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        assertThat(spec.text).isEqualTo("30");
        assertThat(spec.color).isEqualTo(Color.BLUE);
        assertThat(spec.scale).isEqualTo(1f);
    }

    @Test
    @DisplayName("取整口径 Math.round：0.4 → \"0\"、0.5 → \"1\"")
    void roundingHalfUp() {
        assertThat(FloatTextFormat.of(CombatEvent.hit(1, 1, 2, 0.4f, false, null)).text).isEqualTo("0");
        assertThat(FloatTextFormat.of(CombatEvent.hit(1, 1, 2, 0.5f, false, null)).text).isEqualTo("1");
    }

    @Test
    @DisplayName("ATTACK_LAUNCHED / CAST / UNIT_DIED 返回 null（不产飘字）")
    void nonNumericEventsReturnNull() {
        assertThat(FloatTextFormat.of(CombatEvent.attackLaunched(1, 1, 2))).isNull();
        assertThat(FloatTextFormat.of(CombatEvent.cast(1, 1, 2, "sk_fireball"))).isNull();
        assertThat(FloatTextFormat.of(CombatEvent.unitDied(1, 7))).isNull();
        assertThat(FloatTextFormat.of(CombatEvent.battleEnded(1, BattleOutcome.TIMEOUT))).isNull();
    }

    @Test
    @DisplayName("Spec 为不可变纯数据（同事件两次调用等价）")
    void specIsPure() {
        CombatEvent event = CombatEvent.hit(10, 1, 2, 12f, false, null);
        assertThat(FloatTextFormat.of(event).text).isEqualTo(FloatTextFormat.of(event).text);
        assertThat(FloatTextFormat.of(event).scale).isEqualTo(FloatTextFormat.of(event).scale);
    }
}
