package com.voidvvv.kz_auto_chess_n.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChestOffer 胜败语义与 2~3 选项校验测试（机会制 CP3）：
 * 败箱二选一（origin=DEFEAT）/ 胜箱三选一（便捷构造默认 VICTORY）/ equals 含 origin 维度。
 */
class ChestOfferTest {

    private static List<ChestOption> twoOptions() {
        return Arrays.asList(ChestOption.gold(5), ChestOption.expBook(4));
    }

    private static List<ChestOption> threeOptions() {
        return Arrays.asList(ChestOption.gold(3), ChestOption.expBook(4),
                ChestOption.equipment("eq_w"));
    }

    @Test
    @DisplayName("canonical 构造：2 选项成功且 origin=DEFEAT 可查；3 选项 origin=VICTORY")
    void canonicalConstructorAcceptsTwoAndThreeOptions() {
        ChestOffer defeat = new ChestOffer(7, true, ChestOrigin.DEFEAT, twoOptions());
        assertThat(defeat.getOrigin()).isEqualTo(ChestOrigin.DEFEAT);
        assertThat(defeat.getOptions()).hasSize(2);
        assertThat(defeat.optionAt(0)).isEqualTo(ChestOption.gold(5));
        assertThat(defeat.optionAt(1)).isEqualTo(ChestOption.expBook(4));
        assertThat(defeat.optionAt(2)).isNull(); // 越界返 null（败箱无装备槽）

        ChestOffer victory = new ChestOffer(4, false, ChestOrigin.VICTORY, threeOptions());
        assertThat(victory.getOrigin()).isEqualTo(ChestOrigin.VICTORY);
        assertThat(victory.getOptions()).hasSize(3);
    }

    @Test
    @DisplayName("便捷构造（3 参）默认 VICTORY——存量胜箱调用点零改动")
    void legacyConstructorDefaultsToVictory() {
        ChestOffer offer = new ChestOffer(4, false, threeOptions());
        assertThat(offer.getOrigin()).isEqualTo(ChestOrigin.VICTORY);
    }

    @Test
    @DisplayName("选项数校验：1 个与 4 个均抛 IllegalArgumentException")
    void rejectsOneAndFourOptions() {
        assertThatThrownBy(() -> new ChestOffer(1, false, ChestOrigin.DEFEAT,
                Collections.singletonList(ChestOption.gold(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~3");
        assertThatThrownBy(() -> new ChestOffer(1, false, ChestOrigin.VICTORY,
                Arrays.asList(ChestOption.gold(1), ChestOption.expBook(1),
                        ChestOption.equipment("eq_w"), ChestOption.gold(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~3");
    }

    @Test
    @DisplayName("equals/hashCode 含 origin 维度：同 round/boss/options 胜败两箱不相等")
    void equalsIncludesOrigin() {
        ChestOffer victory = new ChestOffer(7, true, ChestOrigin.VICTORY, twoOptions());
        ChestOffer defeat = new ChestOffer(7, true, ChestOrigin.DEFEAT, twoOptions());
        assertThat(victory).isNotEqualTo(defeat);
        assertThat(victory).isEqualTo(new ChestOffer(7, true, ChestOrigin.VICTORY, twoOptions()));
        assertThat(victory.hashCode())
                .isEqualTo(new ChestOffer(7, true, ChestOrigin.VICTORY, twoOptions()).hashCode());
    }

    @Test
    @DisplayName("origin 拒绝 null")
    void rejectsNullOrigin() {
        assertThatThrownBy(() -> new ChestOffer(1, false, null, twoOptions()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("origin");
    }
}
