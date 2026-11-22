package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UiArt 纯逻辑测试（UI 抽屉化批0；与 {@link RealArtTest} 同构）：pathOf 约定、缺素材回退、miss 缓存。
 *
 * <p>pathOf 纯函数直断；region 的 miss 路径用桩 Gdx.files（临时目录，文件恒不存在）headless 覆盖——
 * 不触 GL、不依赖真素材在位。命中路径需 Texture/GL，验收走 lwjgl3:run（沿 RealArtTest 先例）。
 */
class UiArtTest {

    private Files savedFiles;

    @BeforeEach
    void installStubFiles() {
        savedFiles = Gdx.files;
        final File tempRoot = new File(System.getProperty("java.io.tmpdir"),
                "uiart-test-" + System.nanoTime());
        Gdx.files = new Files() {
            @Override
            public FileHandle internal(String path) {
                return new FileHandle(new File(tempRoot, path));
            }

            @Override
            public FileHandle local(String path) {
                return internal(path);
            }

            @Override
            public FileHandle external(String path) {
                throw new UnsupportedOperationException("测试桩不支持 external");
            }

            @Override
            public FileHandle absolute(String path) {
                throw new UnsupportedOperationException("测试桩不支持 absolute");
            }

            @Override
            public FileHandle classpath(String path) {
                throw new UnsupportedOperationException("测试桩不支持 classpath");
            }

            @Override
            public FileHandle getFileHandle(String path, FileType type) {
                throw new UnsupportedOperationException("测试桩不支持 getFileHandle");
            }

            @Override
            public String getExternalStoragePath() {
                return "";
            }

            @Override
            public String getLocalStoragePath() {
                return tempRoot.getPath();
            }

            @Override
            public boolean isExternalStorageAvailable() {
                return false;
            }

            @Override
            public boolean isLocalStorageAvailable() {
                return true;
            }
        };
    }

    @AfterEach
    void restoreFiles() {
        Gdx.files = savedFiles;
    }

    @Test
    @DisplayName("pathOf：id → art/ui/{id}.png（UI 素材根目录，与 RealArt 的 art/units/ 分根）")
    void pathOfBuildsUiAssetPath() {
        assertThat(UiArt.pathOf("ui_panel_body_9p")).isEqualTo("art/ui/ui_panel_body_9p.png");
        assertThat(UiArt.pathOf("ui_handle")).isEqualTo("art/ui/ui_handle.png");
        assertThat(UiArt.ROOT).isEqualTo("art/ui/");
        assertThat(RealArt.ROOT).as("单位素材根不受影响").isEqualTo("art/units/");
    }

    @Test
    @DisplayName("缺素材回退不炸：文件不存在 region 返回 null（Assets 层落占位）——加分件未交付期即走此路")
    void regionReturnsNullWhenAssetMissing() {
        UiArt art = new UiArt();
        assertThat(art.region("ui_slot_9p")).isNull();
        assertThat(art.region("ui_icon_equip_weapon")).isNull();
    }

    @Test
    @DisplayName("miss 缓存：同 key 二次查询直接返回 null（miss 集合去重）")
    void regionCachesMisses() {
        UiArt art = new UiArt();
        assertThat(art.region("ui_slot_9p")).isNull();
        assertThat(art.region("ui_slot_9p")).isNull();
        assertThat(art.missCount()).isEqualTo(1);
        assertThat(art.region("ui_inset_9p")).isNull();
        assertThat(art.missCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("dispose 清空缓存：miss 集归零（后续同 key 重新探针）")
    void disposeClearsCaches() {
        UiArt art = new UiArt();
        assertThat(art.region("ui_slot_9p")).isNull();
        assertThat(art.missCount()).isEqualTo(1);
        art.dispose();
        assertThat(art.missCount()).isZero();
    }
}
