package com.voidvvv.kz_auto_chess_n.render.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 棋盘原创素材键约定测试（品质改造第二阶段；纯函数零 Gdx）：
 * 框键/地砖键形与 generated_v2 manifest board 类同源；行→区域映射与 (x+y) 交错口径锁定。
 */
class BoardArtKeysTest {

    @Test
    @DisplayName("frame：board_frame_<sceneId>")
    void frameKeyDerivation() {
        assertThat(BoardArtKeys.frame("scene_forest")).isEqualTo("board_frame_scene_forest");
        assertThat(BoardArtKeys.frame("scene_snow")).isEqualTo("board_frame_scene_snow");
    }

    @Test
    @DisplayName("area：行 0~2 敌 / 3 缓冲 / 4~6 友（与白纹理时代 zoneTint 同口径）")
    void areaMappingByRow() {
        assertThat(BoardArtKeys.area(0)).isEqualTo("enemy");
        assertThat(BoardArtKeys.area(2)).isEqualTo("enemy");
        assertThat(BoardArtKeys.area(3)).isEqualTo("neutral");
        assertThat(BoardArtKeys.area(4)).isEqualTo("ally");
        assertThat(BoardArtKeys.area(6)).isEqualTo("ally");
    }

    @Test
    @DisplayName("tile：tile_<sceneId>_<area>_<a|b>，(x+y) 偶数取 a、奇数取 b")
    void tileKeyAlternation() {
        assertThat(BoardArtKeys.tile("scene_crypt", 0, 0)).isEqualTo("tile_scene_crypt_enemy_a");
        assertThat(BoardArtKeys.tile("scene_crypt", 1, 0)).isEqualTo("tile_scene_crypt_enemy_b");
        assertThat(BoardArtKeys.tile("scene_crypt", 2, 1)).isEqualTo("tile_scene_crypt_enemy_b"); // 2+1=3 奇
        assertThat(BoardArtKeys.tile("scene_forest", 0, 3)).isEqualTo("tile_scene_forest_neutral_b"); // 3 奇
        assertThat(BoardArtKeys.tile("scene_forest", 1, 3)).isEqualTo("tile_scene_forest_neutral_a"); // 4 偶
        assertThat(BoardArtKeys.tile("scene_snow", 2, 5)).isEqualTo("tile_scene_snow_ally_b"); // 7 奇
        assertThat(BoardArtKeys.tile("scene_snow", 2, 4)).isEqualTo("tile_scene_snow_ally_a"); // 6 偶
    }
}
