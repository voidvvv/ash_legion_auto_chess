package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.render.board.SideColors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StarColors 测试：星级=颜色轴（1★ 骨白 / 2★ 金 / 3★ 紫）与敌我框色错开（派单渲染要求二，CP9）。
 */
class StarColorsTest {

    @Test
    @DisplayName("1/2/3 星三色互异且不透明")
    void threeStarsDistinct() {
        assertThat(StarColors.STAR_1).isNotEqualTo(StarColors.STAR_2);
        assertThat(StarColors.STAR_1).isNotEqualTo(StarColors.STAR_3);
        assertThat(StarColors.STAR_2).isNotEqualTo(StarColors.STAR_3);
        assertThat(StarColors.STAR_1.a).isEqualTo(1f);
        assertThat(StarColors.STAR_2.a).isEqualTo(1f);
        assertThat(StarColors.STAR_3.a).isEqualTo(1f);
    }

    @Test
    @DisplayName("越界星级钳制：<=1 → 骨白，>=3 → 紫")
    void starClamped() {
        assertThat(StarColors.of(0)).isSameAs(StarColors.STAR_1);
        assertThat(StarColors.of(1)).isSameAs(StarColors.STAR_1);
        assertThat(StarColors.of(2)).isSameAs(StarColors.STAR_2);
        assertThat(StarColors.of(3)).isSameAs(StarColors.STAR_3);
        assertThat(StarColors.of(4)).isSameAs(StarColors.STAR_3);
    }

    @Test
    @DisplayName("星级色与敌我底角标色（品质改造 §1.3 #84C9AF/#E6816B）保持可辨色距（RGB 欧氏距离 > 0.3）")
    void distanceFromSideColors() {
        Color[] stars = {StarColors.STAR_1, StarColors.STAR_2, StarColors.STAR_3};
        for (Color star : stars) {
            assertThat(rgbDistance(star, SideColors.PLAYER_MARK)).isGreaterThan(0.3f);
            assertThat(rgbDistance(star, SideColors.ENEMY_MARK)).isGreaterThan(0.3f);
        }
    }

    private static float rgbDistance(Color a, Color b) {
        float dr = a.r - b.r;
        float dg = a.g - b.g;
        float db = a.b - b.b;
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
