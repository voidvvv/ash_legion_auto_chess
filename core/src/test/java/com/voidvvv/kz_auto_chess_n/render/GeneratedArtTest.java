package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GeneratedArt 测试（品质改造第二阶段：generated_v2 原创素材真图层）。
 *
 * <p>三层覆盖：① 纯函数——manifest 解析（key→path/atlas_rect、缺 path 条目跳过、重复 key 即死）；
 * ② headless miss 行为——桩 Gdx.files（文件恒不存在）下 region 返回 null + miss 缓存（Q4=B 守卫：
 * 测试不依赖素材在库）；③ 条件对账（manifest 在库时）——键集覆盖派生帧键/UI 键/棋盘键/头像与
 * 数据图标键（与 units.json 等真实数据交叉，沿 ArtInventoryTest 的 assumeTrue 先例不红测）。
 */
class GeneratedArtTest {

    private static final Path MANIFEST = Paths.get("../assets/art/generated_v2/manifest.json");
    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    private com.badlogic.gdx.Files savedFiles;

    @BeforeEach
    void installStubFiles() {
        savedFiles = Gdx.files;
        final File tempRoot = new File(System.getProperty("java.io.tmpdir"),
                "generated-art-test-" + System.nanoTime());
        Gdx.files = new com.badlogic.gdx.Files() {
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
            public FileHandle getFileHandle(String path, com.badlogic.gdx.Files.FileType type) {
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
    @DisplayName("parse：key → path/atlas_rect；缺 path 条目跳过（未交付项）；重复 key 抛错")
    void parseBuildsIndexFromManifest() {
        String json = "{\"assets\":["
                + "{\"category\":\"unit\",\"replacement_key\":\"unit_a_idle_0\",\"path\":\"art/generated_v2/units/a.png\",\"atlas_rect\":[32,0,32,32]},"
                + "{\"category\":\"fx\",\"replacement_key\":\"fx_white\",\"path\":\"art/generated_v2/primitive/fx_white.png\"},"
                + "{\"category\":\"font\",\"replacement_key\":\"font/x.png\"}"
                + "]}";
        GeneratedArt art = GeneratedArt.parse(json);
        assertThat(art.entryCount()).isEqualTo(2);
        GeneratedArt.Entry frame = art.entry("unit_a_idle_0");
        assertThat(frame.path()).isEqualTo("art/generated_v2/units/a.png");
        assertThat(frame.hasRect()).isTrue();
        assertThat(frame.x()).isEqualTo(32);
        assertThat(frame.y()).isZero();
        assertThat(frame.w()).isEqualTo(32);
        assertThat(frame.h()).isEqualTo(32);
        assertThat(art.entry("fx_white").hasRect()).isFalse();
        assertThat(art.containsKey("font/x.png")).isFalse(); // 无 path 的未交付项不入索引
        assertThat(art.containsKey("unit_a_idle_0")).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> GeneratedArt.parse("{\"assets\":["
                                + "{\"replacement_key\":\"k\",\"path\":\"a.png\"},"
                                + "{\"replacement_key\":\"k\",\"path\":\"b.png\"}]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key 重复");
    }

    @Test
    @DisplayName("缺素材回退不炸：文件不存在 region 返回 null（Assets 层落占位）+ miss 缓存去重")
    void regionReturnsNullAndCachesMissesWhenAssetsAbsent() {
        GeneratedArt art = GeneratedArt.parse("{\"assets\":["
                + "{\"replacement_key\":\"unit_warrior_01_idle_0\",\"path\":\"art/generated_v2/units/u.png\",\"atlas_rect\":[0,0,32,32]}"
                + "]}");
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.missCount()).isEqualTo(1); // miss 缓存：同 key 只探一次盘
        assertThat(art.region("fx_white")).isNull();
        assertThat(art.missCount()).isEqualTo(2); // 索引外 key 也入 miss
        assertThat(art.hasArt("unit_warrior_01_idle_0")).isFalse();
    }

    @Test
    @DisplayName("dispose 清空 miss 缓存：之后同 key 会重新探针")
    void disposeClearsCaches() {
        GeneratedArt art = GeneratedArt.parse("{\"assets\":["
                + "{\"replacement_key\":\"k\",\"path\":\"nope.png\"}]}");
        assertThat(art.region("k")).isNull();
        assertThat(art.missCount()).isEqualTo(1);
        art.dispose();
        assertThat(art.missCount()).isZero();
        assertThat(art.region("k")).isNull();
        assertThat(art.missCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("loadDefault：manifest 缺失 → 空索引软告警（占位兜底期，不炸）")
    void loadDefaultFallsBackToEmptyWhenManifestMissing() {
        GeneratedArt art = GeneratedArt.loadDefault(); // 桩 files：临时目录恒无 manifest
        assertThat(art.entryCount()).isZero();
        assertThat(art.region("unit_warrior_01_idle_0")).isNull();
        assertThat(art.containsKey("anything")).isFalse();
    }

    // —— 条件对账：manifest 在库时（沿 ArtInventoryTest assumeTrue 先例——缺库不红测） ——

    private static GeneratedArt manifestArt() throws Exception {
        assumeTrue(Files.isRegularFile(MANIFEST), "generated_v2 manifest 不在库——跳过对账");
        return GeneratedArt.parse(new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("清单对账：605/605 全量 key（含栅格化字体页）；全部 path 指向 generated_v2")
    void manifestCoversAllKeysUnderGeneratedRoot() throws Exception {
        GeneratedArt art = manifestArt();
        assertThat(art.entryCount()).isEqualTo(605);
        for (String key : art.keys()) {
            assertThat(art.entry(key).path()).as("%s path 在 generated_v2 下", key)
                    .startsWith("art/generated_v2/");
        }
        assertThat(art.containsKey("font/fusion_pixel_12.png")).as("字体页已栅格化收口（605/605）").isTrue();
    }

    @Test
    @DisplayName("清单对账：单位帧键 ≡ PlaceholderKeys 派生帧集（33×12=396，atlas_rect 全界内）")
    void manifestCoversDerivedUnitFrameKeys() throws Exception {
        GeneratedArt art = manifestArt();
        Set<String> derived = new HashSet<String>();
        for (String unitId : data.getUnits().keySet()) {
            for (String anim : PlaceholderKeys.ANIMS) {
                for (int frame = 0; frame < PlaceholderKeys.frameCount(anim); frame++) {
                    derived.add(PlaceholderKeys.unitFrame(unitId, anim, frame));
                }
            }
        }
        for (String key : derived) {
            assertThat(art.containsKey(key)).as("单位帧在清单: %s", key).isTrue();
            GeneratedArt.Entry e = art.entry(key);
            assertThat(e.hasRect()).as("%s 带裁切矩形", key).isTrue();
            assertThat(e.w()).as("%s 帧宽 32", key).isEqualTo(GeneratedArt.UNIT_FRAME_PX);
            assertThat(e.h()).as("%s 帧高 32", key).isEqualTo(GeneratedArt.UNIT_FRAME_PX);
            assertThat(e.x() + e.w()).as("%s 裁切右界 ≤ 图集宽", key)
                    .isLessThanOrEqualTo(GeneratedArt.UNIT_ATLAS_W);
            assertThat(e.y() + e.h()).as("%s 裁切下界 ≤ 图集高", key)
                    .isLessThanOrEqualTo(GeneratedArt.UNIT_ATLAS_H);
        }
    }

    @Test
    @DisplayName("清单对账：运行时枚举的所有键均有真图，包括合成亡语特效")
    void manifestCoversRequestableKeys() throws Exception {
        GeneratedArt art = manifestArt();
        assertThat(art.keys()).containsAll(PlaceholderKeys.enumerateFor(data));
    }

    @Test
    @DisplayName("清单对账：头像/英雄/装备/羁绊/棋盘键与真实数据 ID 一一对应")
    void manifestCoversDataDrivenKeys() throws Exception {
        GeneratedArt art = manifestArt();
        for (String unitId : data.getUnits().keySet()) {
            assertThat(art.containsKey("portrait_" + unitId)).as("单位头像: %s", unitId).isTrue();
        }
        for (com.voidvvv.kz_auto_chess_n.data.HeroData hero : data.getHeroes().values()) {
            assertThat(art.containsKey("portrait_" + hero.getId())).as("英雄肖像: %s", hero.getId()).isTrue();
        }
        for (com.voidvvv.kz_auto_chess_n.data.EquipmentData eq : data.getEquipments().values()) {
            assertThat(art.containsKey("icon_" + eq.getId())).as("装备图标: %s", eq.getId()).isTrue();
        }
        for (com.voidvvv.kz_auto_chess_n.data.SynergyData syn : data.getSynergies().values()) {
            assertThat(art.containsKey("icon_" + syn.getId())).as("羁绊图标: %s", syn.getId()).isTrue();
        }
        for (com.voidvvv.kz_auto_chess_n.data.SceneData scene : data.getScenes().values()) {
            assertThat(art.containsKey("board_frame_" + scene.getId())).as("棋盘框: %s", scene.getId()).isTrue();
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 6; x++) {
                    assertThat(art.containsKey(
                            com.voidvvv.kz_auto_chess_n.render.board.BoardArtKeys.tile(scene.getId(), x, y)))
                            .as("地砖: %s (%d,%d)", scene.getId(), x, y).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("字体页对账（条件）：.fnt 与 assets/font 度量契约字节一致；页 IHDR 1024×1024")
    void fontPageMatchesFntContract() throws Exception {
        GeneratedArt art = manifestArt();
        GeneratedArt.Entry font = art.entry("font/fusion_pixel_12.png");
        Path page = Paths.get("..", "assets", font.path()); // manifest path 为 assets/ 根相对
        Path fnt = Paths.get("../assets/art/generated_v2/font/fusion_pixel_12.fnt");
        assumeTrue(Files.isRegularFile(page) && Files.isRegularFile(fnt), "字体页未生成——跳过");
        assertThat(Files.readAllBytes(fnt))
                .as("generated_v2 .fnt 与 assets/font 度量契约字节一致")
                .isEqualTo(Files.readAllBytes(Paths.get("../assets/font/fusion_pixel_12.fnt")));
        byte[] header = new byte[26];
        try (java.io.InputStream in = Files.newInputStream(page)) {
            assertThat(in.read(header)).isEqualTo(26);
        }
        int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
        int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
        assertThat(w).as("页宽 1024（IHDR）").isEqualTo(1024);
        assertThat(h).as("页高 1024（IHDR）").isEqualTo(1024);
        // .fnt 引用的页文件名与本目录页一致（BitmapFont 按相对路径找页）
        String fntText = new String(Files.readAllBytes(fnt), StandardCharsets.UTF_8);
        assertThat(fntText).contains("file=\"fusion_pixel_12.png\"");
        assertThat(fntText.split("\nchar ", -1).length - 1).as("字形数 4508").isEqualTo(4508);
    }
}
