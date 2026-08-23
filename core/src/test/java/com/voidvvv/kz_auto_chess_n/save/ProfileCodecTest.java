package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.config.DataValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProfileCodec 编解码测试（Phase 6 CP7）：round-trip 等价 / 空档语义 /
 * 版本不符 / 未知字段 / 非法 level（沿 JsonLoader fail-fast 口径）。
 */
class ProfileCodecTest {

    @Test
    @DisplayName("round-trip：多英雄 + 多通关场景 等价回读")
    void roundTripPreservesEverything() {
        Profile original = Profile.fresh()
                .withHeroProgress("hero_greg", new HeroProgress(3, 17))
                .withHeroProgress("hero_vera", new HeroProgress(1, 0))
                .withCompletedScene("scene_forest")
                .withCompletedScene("scene_crypt");
        Profile read = ProfileCodec.read(ProfileCodec.write(original));
        assertThat(read.getVersion()).isEqualTo(original.getVersion());
        assertThat(read.getHeroProgress().keySet())
                .containsExactlyElementsOf(original.getHeroProgress().keySet());
        assertThat(read.getHeroProgress().get("hero_greg")).isEqualTo(new HeroProgress(3, 17));
        assertThat(read.getHeroProgress().get("hero_vera")).isEqualTo(new HeroProgress(1, 0));
        assertThat(read.getCompletedScenes())
                .containsExactlyElementsOf(original.getCompletedScenes());
    }

    @Test
    @DisplayName("空档：\"{}\" / null / 空白 → fresh 语义（version=1、零进度）")
    void emptyJsonYieldsFreshProfile() {
        for (String json : Arrays.asList("{}", null, "   ")) {
            Profile read = ProfileCodec.read(json);
            assertThat(read.getVersion()).isEqualTo(Profile.CURRENT_VERSION);
            assertThat(read.getHeroProgress()).isEmpty();
            assertThat(read.getCompletedScenes()).isEmpty();
        }
    }

    @Test
    @DisplayName("fresh 档写读：JSON 形态即档案域格式锚点")
    void freshProfileRoundTrip() {
        String json = ProfileCodec.write(Profile.fresh());
        assertThat(json).isEqualTo("{\"version\":1,\"heroes\":{},\"completedScenes\":[]}");
        assertThat(ProfileCodec.read(json)).isNotNull();
    }

    @Test
    @DisplayName("版本不符 → DataValidationException（交 Store 重置）")
    void versionMismatchRejected() {
        assertThatThrownBy(() -> ProfileCodec.read("{\"version\":2,\"heroes\":{},\"completedScenes\":[]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("不支持的档案版本");
    }

    @Test
    @DisplayName("未知字段即死（根级与英雄条目级）")
    void unknownFieldsRejected() {
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{},\"completedScenes\":[],\"extra\":1}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段 extra");
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{\"h1\":{\"level\":1,\"exp\":0,\"foo\":2}},\"completedScenes\":[]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段 foo");
    }

    @Test
    @DisplayName("非法值即死：level 越界 / level 缺失 / exp 非整数 / completedScenes 元素非字符串")
    void invalidValuesRejected() {
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{\"h1\":{\"level\":6,\"exp\":0}},\"completedScenes\":[]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("1~5");
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{\"h1\":{\"exp\":0}},\"completedScenes\":[]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("level");
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{\"h1\":{\"level\":1,\"exp\":1.5}},\"completedScenes\":[]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("整数");
        assertThatThrownBy(() -> ProfileCodec.read(
                "{\"version\":1,\"heroes\":{},\"completedScenes\":[3]}"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("非空字符串");
    }
}
