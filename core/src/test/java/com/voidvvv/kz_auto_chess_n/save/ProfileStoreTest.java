package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProfileStore 文件 IO 测试（Phase 6 CP7，裁决 D20）：缺失 → fresh；
 * 损坏 → 记日志重置不炸；save→load round-trip。临时目录 + FileHandle 直构（零 Gdx.app）。
 */
class ProfileStoreTest {

    @TempDir
    Path tempDir;

    private FileHandle file(String name) {
        return new FileHandle(tempDir.resolve(name).toString());
    }

    @Test
    @DisplayName("文件不存在 → 初始档案（fresh）")
    void missingFileYieldsFreshProfile() {
        ProfileStore store = new ProfileStore(file("profile.json"));
        assertThat(store.load()).isNotNull();
        assertThat(store.load().getHeroProgress()).isEmpty();
    }

    @Test
    @DisplayName("save → load round-trip：进度与通关记录完整落盘回读")
    void saveThenLoadRoundTrips() {
        FileHandle file = file("profile.json");
        ProfileStore store = new ProfileStore(file);
        Profile original = Profile.fresh()
                .withHeroProgress("hero_greg", new HeroProgress(4, 88))
                .withCompletedScene("scene_forest");
        assertThat(store.save(original)).isTrue();
        assertThat(file.exists()).isTrue();

        Profile loaded = new ProfileStore(file).load();
        assertThat(loaded.getHeroProgress().get("hero_greg")).isEqualTo(new HeroProgress(4, 88));
        assertThat(loaded.getCompletedScenes()).containsExactly("scene_forest");
    }

    @Test
    @DisplayName("损坏档案 → 重置为初始档案且不炸（裁决 D20；日志走 System.err）")
    void corruptedFileResetsToFresh() throws IOException {
        FileHandle file = file("profile.json");
        Files.write(tempDir.resolve("profile.json"),
                "{ 这不是合法 JSON ".getBytes(StandardCharsets.UTF_8));
        Profile loaded = new ProfileStore(file).load();
        assertThat(loaded.getHeroProgress()).isEmpty();
        assertThat(loaded.getCompletedScenes()).isEmpty();
    }

    @Test
    @DisplayName("null 句柄：load → fresh、save → false（防御装配错误不炸）")
    void nullHandleTolerated() {
        ProfileStore store = new ProfileStore(null);
        assertThat(store.load().getVersion()).isEqualTo(Profile.CURRENT_VERSION);
        assertThat(store.save(Profile.fresh())).isFalse();
    }
}
