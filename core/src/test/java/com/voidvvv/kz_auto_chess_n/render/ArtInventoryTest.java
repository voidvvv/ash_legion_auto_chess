package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 原创单位图集库存量对账（品质改造第二阶段，2026-11-24 重写自 art/units 逐帧对账）：
 * generated_v2 单位图集 = 每单位一张 128×96 PNG（四列三行 12 帧，atlas_rect 裁切）。
 *
 * <p>① 目录清单 ≡ units.json 模板集（33 张图集，逐字符、无残留）；② 全部 IHDR 128×96；
 * ③ manifest 在库时帧键全集 ≡ PlaceholderKeys 派生帧集、每张图集的 12 个裁切矩形
 * 在界内且互不重叠。生效契约链：Assets.region 先查 GeneratedArt，manifest+PNG 在位即真素材。
 * 旧 assets/art/units/（itch 逐帧开源素材）已不再被运行期引用，不参与对账。
 */
class ArtInventoryTest {

    private static final Path ATLAS_DIR = Paths.get("../assets/art/generated_v2/units");
    private static final Path MANIFEST = Paths.get("../assets/art/generated_v2/manifest.json");

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** PNG IHDR 宽高（字节 16~23，网络字节序；不触 GL） */
    private static int[] ihdrSize(File file) throws IOException {
        byte[] header = new byte[26];
        try (FileInputStream in = new FileInputStream(file)) {
            assertThat(in.read(header)).as("IHDR 头可读: %s", file.getName()).isEqualTo(26);
        }
        return new int[]{
                ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF),
                ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF)};
    }

    @Test
    @DisplayName("图集目录 ≡ units.json 模板集（33 张 128×96 PNG，逐字符含大小写、无残留）")
    void atlasDirectoryReconcilesWithUnitTemplates() throws IOException {
        assumeTrue(Files.isDirectory(ATLAS_DIR), "generated_v2/units 不在库——跳过（占位兜底期）");
        Set<String> expected = data.getUnits().keySet().stream()
                .map(id -> id + ".png").collect(Collectors.toSet());
        Set<String> actual;
        try (Stream<Path> files = Files.list(ATLAS_DIR)) {
            actual = files.filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".png"))
                    .collect(Collectors.toSet());
        }
        assertThat(actual).as("图集目录恰为模板集").isEqualTo(expected);
        for (String unitId : data.getUnits().keySet()) {
            int[] size = ihdrSize(ATLAS_DIR.resolve(unitId + ".png").toFile());
            assertThat(size[0]).as("%s 图集宽 128（IHDR）", unitId).isEqualTo(GeneratedArt.UNIT_ATLAS_W);
            assertThat(size[1]).as("%s 图集高 96（IHDR）", unitId).isEqualTo(GeneratedArt.UNIT_ATLAS_H);
        }
    }

    @Test
    @DisplayName("manifest 帧键 ≡ 派生帧集（33×12=396）；每图集 12 帧矩形在界内且互不重叠")
    void manifestFrameRectsCoverDerivedKeysWithoutOverlap() throws IOException {
        assumeTrue(Files.isRegularFile(MANIFEST), "manifest 不在库——跳过对账（占位兜底期）");
        Set<String> derived = new HashSet<String>();
        for (String unitId : data.getUnits().keySet()) {
            for (String anim : PlaceholderKeys.ANIMS) {
                for (int frame = 0; frame < PlaceholderKeys.frameCount(anim); frame++) {
                    derived.add(PlaceholderKeys.unitFrame(unitId, anim, frame));
                }
            }
        }
        Map<String, JsonValue> rows = new HashMap<String, JsonValue>();
        for (JsonValue row : new JsonReader().parse(
                new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8)).get("assets")) {
            if ("unit".equals(row.getString("category"))) {
                rows.put(row.getString("replacement_key"), row);
            }
        }
        assertThat(rows.keySet()).as("manifest 单位帧键 ≡ 派生帧集").isEqualTo(derived);

        Map<String, int[]> placedByPath = new HashMap<String, int[]>(); // path → 位图掩码累计重叠检测
        for (Map.Entry<String, JsonValue> e : rows.entrySet()) {
            JsonValue rect = e.getValue().get("atlas_rect");
            assertThat(rect).as("%s 带 atlas_rect", e.getKey()).isNotNull();
            int x = rect.getInt(0);
            int y = rect.getInt(1);
            int w = rect.getInt(2);
            int h = rect.getInt(3);
            assertThat(w).as("%s 帧宽 32", e.getKey()).isEqualTo(GeneratedArt.UNIT_FRAME_PX);
            assertThat(h).as("%s 帧高 32", e.getKey()).isEqualTo(GeneratedArt.UNIT_FRAME_PX);
            String path = e.getValue().getString("path");
            assertThat(x).isBetween(0, GeneratedArt.UNIT_ATLAS_W - w);
            assertThat(y).isBetween(0, GeneratedArt.UNIT_ATLAS_H - h);
            int[] mask = placedByPath.get(path);
            if (mask == null) {
                mask = new int[GeneratedArt.UNIT_ATLAS_W * GeneratedArt.UNIT_ATLAS_H];
                placedByPath.put(path, mask);
            }
            for (int yy = y; yy < y + h; yy++) {
                for (int xx = x; xx < x + w; xx++) {
                    int idx = yy * GeneratedArt.UNIT_ATLAS_W + xx;
                    assertThat(mask[idx]).as("%s 帧矩形与同图集前帧重叠", e.getKey()).isZero();
                    mask[idx] = 1;
                }
            }
        }
        assertThat(placedByPath).as("图集张数 = 模板数（每 PNG 恰 12 帧）").hasSize(data.getUnits().size());
    }
}
