package com.voidvvv.kz_auto_chess_n.screens;

import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunSetupScreen 纯函数文案测试（CP12）：三类被动一行文案 + 熟练度满级/未满级文案。
 * 屏体为 Gdx 绑定（沿 MainMenuScreen 无直测先例），逻辑断言全部下沉包级静态。
 */
class RunSetupScreenTextTest {

    private static HeroData hero(HeroPassiveType type, float value, String... synergyIds) {
        return new HeroData("h1", "英雄甲", "desc", type, value,
                new ArrayList<String>(Arrays.asList(synergyIds)), null);
    }

    @Test
    @DisplayName("三类被动文案：金币加成 / N 系羁绊增幅百分比 / 全队回能百分比")
    void passiveTextThreeShapes() {
        assertThat(RunSetupScreen.passiveText(hero(HeroPassiveType.START_GOLD, 2f)))
                .isEqualTo("被动：开局金币 +2");
        assertThat(RunSetupScreen.passiveText(hero(HeroPassiveType.SYNERGY_AMP, 25f, "syn_beast", "syn_ranger")))
                .isEqualTo("被动：2 系羁绊效果 +25%");
        assertThat(RunSetupScreen.passiveText(hero(HeroPassiveType.ENERGY_GAIN, 15f)))
                .isEqualTo("被动：全队能量获取 +15%");
    }

    @Test
    @DisplayName("熟练度文案：Lv.1 常规提示 / Lv.5 满级")
    void masteryTextLevels() {
        assertThat(RunSetupScreen.masteryText(1)).isEqualTo("熟练度 Lv.1（升级解锁加成）");
        assertThat(RunSetupScreen.masteryText(3)).isEqualTo("熟练度 Lv.3（升级解锁加成）");
        assertThat(RunSetupScreen.masteryText(5)).isEqualTo("熟练度 Lv.5（满级）");
    }
}
