package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * UI 素材对账（品质改造第二阶段，2026-11-24 重写：目录自 art/ui 迁至 generated_v2/ui）。
 *
 * <p>generated_v2/ui 文件 ⊆ UiKeys 全键集 ∪ {ui_panel_9slice}（旧通用件归
 * {@link PlaceholderKeys#PANEL_9SLICE}，经 Assets.region 走同一真图层）；在位键逐一核对
 * IHDR 尺寸 == UiKeys 源尺寸。manifest 在库时另对账 nine_patch 切分线与 UiKeys 一致。
 * 旧 assets/art/ui/（含 1_25x/ 备用列与旧主菜单背景）不再被运行期引用，不参与对账。
 */
class UiArtInventoryTest {

    private static final Path UI_DIR = Paths.get("../assets/art/generated_v2/ui");
    private static final Path MANIFEST = Paths.get("../assets/art/generated_v2/manifest.json");

    /** generated_v2/ui 顶层 PNG 文件名主干 */
    private static List<String> deliveredIds() throws IOException {
        try (Stream<Path> files = Files.list(UI_DIR)) {
            return files.filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .sorted()
                    .collect(Collectors.toList());
        }
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
    @DisplayName("目录对账（条件）：generated_v2/ui PNG ⊆ UiKeys 全键集 ∪ {ui_panel_9slice}——逐字符精确、无键外残留")
    void deliveredFilesAreSubsetOfKeySet() throws IOException {
        assumeTrue(Files.isDirectory(UI_DIR), "generated_v2/ui 目录不在库——跳过（占位兜底期）");
        List<String> knownIds = UiKeys.allIds();
        List<String> delivered = deliveredIds();
        java.util.Set<String> allowed = new java.util.HashSet<String>(knownIds);
        allowed.add(PlaceholderKeys.PANEL_9SLICE);
        assertThat(allowed.containsAll(delivered)).as("目录文件 ⊆ 键集 ∪ {ui_panel_9slice}").isTrue();
        for (String id : allowed) {
            Path exact = UI_DIR.resolve(id + ".png");
            if (!Files.exists(exact)) {
                continue; // 未交付键（当前全键已交付）
            }
            assertThat(delivered.contains(id))
                    .as("文件名逐字符精确（含大小写）: %s", id).isTrue();
            assertThat(delivered.stream().filter(name -> name.toLowerCase(Locale.ROOT).equals(id)
                    && !name.equals(id)).count()).as("%s 无仅大小写不同的重复件", id).isZero();
        }
    }

    @Test
    @DisplayName("在位键（全量）：IHDR 尺寸 == UiKeys 源尺寸（ui_panel_9slice 按 manifest 24×24）")
    void anyPresentFileMatchesExpectedSize() throws IOException {
        assumeTrue(Files.isDirectory(UI_DIR), "generated_v2/ui 目录不在库——跳过（占位兜底期）");
        int checked = 0;
        for (UiKeys.Entry entry : UiKeys.ALL) {
            Path file = UI_DIR.resolve(entry.fileName());
            if (!Files.exists(file)) {
                continue;
            }
            int[] size = ihdrSize(file.toFile());
            assertThat(size[0]).as("%s 宽（IHDR）== 源尺寸", entry.id()).isEqualTo(entry.width());
            assertThat(size[1]).as("%s 高（IHDR）== 源尺寸", entry.id()).isEqualTo(entry.height());
            checked++;
        }
        Path panel = UI_DIR.resolve(PlaceholderKeys.PANEL_9SLICE + ".png");
        if (Files.exists(panel)) {
            int[] size = ihdrSize(panel.toFile());
            assertThat(size[0]).as("ui_panel_9slice 宽 24").isEqualTo(24);
            assertThat(size[1]).as("ui_panel_9slice 高 24").isEqualTo(24);
            checked++;
        }
        assertThat(checked).as("当前在位件数 > 0").isPositive();
    }

    @Test
    @DisplayName("manifest 对账（条件）：ui/button 类条目 nine_patch 切分线与 UiKeys 逐键一致")
    void manifestSlicesAgreeWithUiKeys() throws IOException {
        assumeTrue(Files.isRegularFile(MANIFEST) && Files.isDirectory(UI_DIR),
                "manifest 或 generated_v2/ui 不在库——跳过对账");
        for (JsonValue row : new JsonReader().parse(
                new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8)).get("assets")) {
            String category = row.getString("category", "");
            if (!"ui".equals(category) && !"button".equals(category)) {
                continue;
            }
            String key = row.getString("replacement_key");
            int[] ninePatch = new int[4];
            JsonValue slice = row.get("nine_patch");
            if (slice != null && slice.isArray()) {
                for (int i = 0; i < 4; i++) {
                    ninePatch[i] = slice.getInt(i);
                }
            }
            if (PlaceholderKeys.PANEL_9SLICE.equals(key)) {
                assertThat(ninePatch).as("ui_panel_9slice 切分 5/5/5/5").containsExactly(5, 5, 5, 5);
                continue;
            }
            UiKeys.Entry entry = UiKeys.find(key);
            assertThat(entry).as("manifest 条目 %s 必须是已知 UI 键", key).isNotNull();
            assertThat(new int[]{entry.left(), entry.right(), entry.top(), entry.bottom()})
                    .as("%s UiKeys 切分线 == manifest nine_patch", key).containsExactly(ninePatch);
        }
    }
}
