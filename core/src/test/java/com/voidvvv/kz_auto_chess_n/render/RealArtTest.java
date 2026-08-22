package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RealArt 纯逻辑测试（CP18：真素材层 key→路径约定与缺素材回退）。
 *
 * <p>pathOf 为纯函数直接断言；region 的 miss 路径（文件不存在返回 null + miss 缓存）
 * 用桩 Gdx.files（临时目录 + 探针计数 FileHandle）headless 覆盖——不触 GL、不依赖真素材存在
 * （Q4=B 守卫：测试不得依赖素材入库）。命中路径需 Texture/GL，验收走 lwjgl3:run 手验
 * （沿 PlaceholderArt 先例）。
 */
class RealArtTest {

    private static final String[] REAL_ART_UNITS = {"unit_warrior_01", "unit_ranger_01", "unit_assassin_01"};

    private Files savedFiles;
    private int existsProbes;

    // 探针 FileHandle：exists() 计数（验证 miss 缓存不再每帧磁盘 stat）
    private final class ProbingFileHandle extends FileHandle {
        ProbingFileHandle(File file) {
            super(file);
        }

        @Override
        public boolean exists() {
            existsProbes++;
            return super.exists();
        }
    }

    @BeforeEach
    void installStubFiles() {
        savedFiles = Gdx.files;
        final File tempRoot = new File(System.getProperty("java.io.tmpdir"),
                "realart-test-" + System.nanoTime());
        Gdx.files = new Files() {
            @Override
            public FileHandle internal(String path) {
                return new ProbingFileHandle(new File(tempRoot, path));
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
    @DisplayName("pathOf：key → art/units/{key}.png（真素材目录约定）")
    void pathOfBuildsUnitFramePath() {
        assertThat(RealArt.pathOf("unit_warrior_01_idle_0")).isEqualTo("art/units/unit_warrior_01_idle_0.png");
        assertThat(RealArt.pathOf("unit_ranger_01_attack_2")).isEqualTo("art/units/unit_ranger_01_attack_2.png");
        assertThat(RealArt.pathOf("unit_assassin_01_death_2")).isEqualTo("art/units/unit_assassin_01_death_2.png");
    }

    @Test
    @DisplayName("pathOf 与 PlaceholderKeys.unitFrame 帧表对齐：3 棋子 × idle2/walk2/attack3/cast2/death3 = 36 帧互异（WARNING-8）")
    void pathOfCoversAllPlaceholderFrames() {
        Set<String> paths = new HashSet<String>();
        for (String unitId : REAL_ART_UNITS) {
            for (String anim : PlaceholderKeys.ANIMS) {
                for (int frame = 0; frame < PlaceholderKeys.frameCount(anim); frame++) {
                    String key = PlaceholderKeys.unitFrame(unitId, anim, frame);
                    assertThat(RealArt.pathOf(key)).isEqualTo("art/units/" + key + ".png");
                    paths.add(RealArt.pathOf(key));
                }
            }
        }
        assertThat(paths).hasSize(3 * (2 + 2 + 3 + 2 + 3));
    }

    @Test
    @DisplayName("缺素材回退不炸：文件不存在 region 返回 null（Assets 层落占位）")
    void regionReturnsNullWhenAssetMissing() {
        RealArt art = new RealArt();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.region("fx_white")).isNull();
    }

    @Test
    @DisplayName("miss 缓存：同 key 二次查询不再磁盘 stat（每 key 恰探针 1 次）")
    void regionCachesMisses() {
        RealArt art = new RealArt();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(existsProbes).isEqualTo(1);
        assertThat(art.region("unit_ranger_01_walk_1")).isNull();
        assertThat(existsProbes).isEqualTo(2);
    }

    @Test
    @DisplayName("dispose 清空 miss 缓存：之后同 key 会重新探针")
    void disposeClearsMissCache() {
        RealArt art = new RealArt();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(existsProbes).isEqualTo(1);
        art.dispose();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(existsProbes).isEqualTo(2);
    }
}
