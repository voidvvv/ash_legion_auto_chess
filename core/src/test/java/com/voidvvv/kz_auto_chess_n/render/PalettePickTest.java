package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PalettePick 测试：FNV-1a 纯字符串 hash → 32 色调色板（口径 #14：同 id 恒同色、零模拟 RNG）。
 */
class PalettePickTest {

    @Test
    @DisplayName("FNV-1a 已知向量锁定：空串 0x811c9dc5、\"a\" 0xe40c292c")
    void fnv1aKnownVectors() {
        assertThat(PalettePick.fnv1a("")).isEqualTo(0x811c9dc5);
        assertThat(PalettePick.fnv1a("a")).isEqualTo(0xe40c292c);
        assertThat(PalettePick.fnv1a("foobar")).isEqualTo(0xbf9cf968);
    }

    @Test
    @DisplayName("同 id 恒同色（确定性，跨调用同实例）")
    void sameIdSameColor() {
        assertThat(PalettePick.pick("unit_warrior_01")).isSameAs(PalettePick.pick("unit_warrior_01"));
    }

    @Test
    @DisplayName("不同 id 大概率异色（演示兵三名互不相同）")
    void differentIdsUsuallyDifferentColors() {
        Color warrior = PalettePick.pick("unit_warrior_01");
        Color assassin = PalettePick.pick("unit_assassin_01");
        Color ranger = PalettePick.pick("unit_ranger_01");
        assertThat(warrior).isNotEqualTo(assassin);
        assertThat(warrior).isNotEqualTo(ranger);
        assertThat(assassin).isNotEqualTo(ranger);
    }

    @Test
    @DisplayName("调色板 32 色且不透明（alpha=1）")
    void paletteHas32OpaqueColors() {
        for (int i = 0; i < 32; i++) {
            Color c = PalettePick.paletteColor(i);
            assertThat(c.a).isEqualTo(1f);
        }
        assertThat(PalettePick.paletteColor(0)).isNotNull();
        assertThat(PalettePick.paletteColor(31)).isNotNull();
    }
}
