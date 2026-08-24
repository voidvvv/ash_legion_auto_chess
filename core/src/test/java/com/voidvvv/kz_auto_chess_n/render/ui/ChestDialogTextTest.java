package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.ChestOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChestDialog 静态函数测试（机会制 CP12）：titleText 三值（败箱「战败补给」/ Boss 箱 / 普通箱）。
 */
class ChestDialogTextTest {

    @Test
    @DisplayName("titleText：origin=DEFEAT → 战败补给；VICTORY+boss → BOSS 宝箱；VICTORY → 宝箱；null → 宝箱")
    void titleTextThreeOrigins() {
        ChestOffer defeat = new ChestOffer(7, true, ChestOrigin.DEFEAT, Arrays.asList(
                ChestOption.gold(5), ChestOption.expBook(5)));
        assertThat(ChestDialog.titleText(defeat)).isEqualTo("战败补给");
        assertThat(ChestDialog.titleText(null)).isEqualTo("宝箱");

        ChestOffer boss = new ChestOffer(7, true, ChestOrigin.VICTORY, Arrays.asList(
                ChestOption.gold(10), ChestOption.expBook(5), ChestOption.gold(10)));
        assertThat(ChestDialog.titleText(boss)).isEqualTo("BOSS 宝箱");

        ChestOffer normal = new ChestOffer(4, false, ChestOrigin.VICTORY, Arrays.asList(
                ChestOption.gold(4), ChestOption.expBook(4), ChestOption.gold(4)));
        assertThat(ChestDialog.titleText(normal)).isEqualTo("宝箱");
    }
}
