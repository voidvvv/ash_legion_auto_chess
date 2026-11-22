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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * UI 真素材三方对账（UI 抽屉化批0；仿 {@link ArtInventoryTest} 的条件断言——文件缺失即 assumeTrue 跳过，不红测）。
 *
 * <p>三方 = 采购单（{@link UiKeys} 常量，由 UiKeysTest 逐字锁定）↔ 交付 manifest（尺寸/切分线/SHA-256）
 * ↔ 实际 PNG（IHDR）。生效契约链：{@code Assets.uiRegion/uiPatch} 先查 {@link UiArt}，文件存在即真素材。
 * 方向口径：加分件可暂缺——只断言「目录文件 ⊆ 键集」，不断言加分键在位；必需 12 张在位时逐一核对尺寸。
 */
class UiArtInventoryTest {

    private static final Path UI_DIR = Paths.get("../assets/art/ui");
    private static final Path MANIFEST = Paths.get("../assets_src/ui_redesign/ui_asset_manifest.json");

    /** art/ui 顶层 PNG 文件名主干（不含 1_25x/ 子目录——1.25× 备用列不进代码） */
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

    private static boolean allRequiredPresent() {
        for (UiKeys.Entry entry : UiKeys.REQUIRED) {
            if (!Files.exists(UI_DIR.resolve(entry.fileName()))) {
                return false;
            }
        }
        return true;
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
    @DisplayName("目录对账（条件）：art/ui 顶层 PNG ⊆ UiKeys 全键集——文件名逐字符精确（含大小写）、无键外残留；加分件可暂缺")
    void deliveredFilesAreSubsetOfKeySet() throws IOException {
        assumeTrue(Files.isDirectory(UI_DIR), "art/ui 目录不在库——跳过（占位兜底期）");
        List<String> knownIds = UiKeys.allIds();
        List<String> delivered = deliveredIds();
        assertThat(delivered).as("目录文件 ⊆ 键集（⊆ 方向：不断言加分键在位）")
                .isSubsetOf(knownIds);
        // 大小写陷阱防御（Windows 不区分、打包后会）：键 → 实际文件名必须逐字符一致
        for (String id : knownIds) {
            Path exact = UI_DIR.resolve(id + ".png");
            if (!Files.exists(exact)) {
                continue; // 未交付（加分件）
            }
            assertThat(delivered.contains(id))
                    .as("文件名逐字符精确（含大小写）: %s", id).isTrue();
            assertThat(delivered.stream().filter(name -> name.toLowerCase(Locale.ROOT).equals(id)
                    && !name.equals(id)).count()).as("%s 无仅大小写不同的重复件", id).isZero();
        }
    }

    @Test
    @DisplayName("必需 12 张对账（条件）：在位时 IHDR 尺寸 == UiKeys 源尺寸（1:1 列）；切分线恒在界内（UiKeysTest 锁定）")
    void requiredFilesMatchExpectedSizeWhenPresent() throws IOException {
        assumeTrue(Files.isDirectory(UI_DIR) && allRequiredPresent(),
                "12 张必需未齐——跳过尺寸对账（缺件走占位兜底）");
        for (UiKeys.Entry entry : UiKeys.REQUIRED) {
            int[] size = ihdrSize(UI_DIR.resolve(entry.fileName()).toFile());
            assertThat(size[0]).as("%s 宽（IHDR）== 源尺寸", entry.id()).isEqualTo(entry.width());
            assertThat(size[1]).as("%s 高（IHDR）== 源尺寸", entry.id()).isEqualTo(entry.height());
        }
    }

    @Test
    @DisplayName("在位的任意键（含加分）：IHDR 尺寸 == UiKeys 源尺寸——后续补交加分件时自动纳入对账")
    void anyPresentFileMatchesExpectedSize() throws IOException {
        assumeTrue(Files.isDirectory(UI_DIR), "art/ui 目录不在库——跳过（占位兜底期）");
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
        assertThat(checked).as("当前在位件数 > 0（目录存在即至少有已交付件）").isPositive();
    }

    @Test
    @DisplayName("manifest 对账（条件）：交付清单在库时，尺寸 / 九宫格切分线 / SHA-256 与 UiKeys、实际文件逐一对账")
    void manifestAgreesWithKeysAndFiles() throws Exception {
        assumeTrue(Files.exists(MANIFEST) && Files.isDirectory(UI_DIR),
                "交付 manifest 或 art/ui 不在库——跳过 manifest 对账");
        Map<String, JsonValue> rows = new HashMap<String, JsonValue>();
        for (JsonValue row : new JsonReader().parse(
                new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8))) {
            rows.put(row.getString("id"), row);
        }
        List<String> reconciled = new ArrayList<String>();
        for (Map.Entry<String, JsonValue> e : rows.entrySet()) {
            Path file = UI_DIR.resolve(e.getKey() + ".png");
            if (!Files.exists(file)) {
                continue; // 清单登记但未入库（如另行下架）——不纳入
            }
            UiKeys.Entry entry = UiKeys.find(e.getKey());
            assertThat(entry).as("manifest 条目 %s 必须是已知键", e.getKey()).isNotNull();
            assertThat(entry.width()).as("%s UiKeys 源宽 == manifest", e.getKey())
                    .isEqualTo(e.getValue().getInt("w_1x"));
            assertThat(entry.height()).as("%s UiKeys 源高 == manifest", e.getKey())
                    .isEqualTo(e.getValue().getInt("h_1x"));
            assertThat(sliceNotation(entry))
                    .as("%s UiKeys 切分线（左/右/上/下；四边 0 = manifest 的 none）== manifest.slice", e.getKey())
                    .isEqualTo(e.getValue().getString("slice"));
            assertThat(sha256Hex(file.toFile())).as("%s 实际文件 SHA-256 == manifest", e.getKey())
                    .isEqualTo(e.getValue().getString("sha256_1x"));
            reconciled.add(e.getKey());
        }
        assertThat(reconciled).as("已入库的 manifest 条目全部完成对账").isNotEmpty();
    }

    /** manifest 的 slice 记法：左/右/上/下；四边全 0（不拉伸件）记作 none */
    private static String sliceNotation(UiKeys.Entry entry) {
        if (entry.left() + entry.right() + entry.top() + entry.bottom() == 0) {
            return "none";
        }
        return entry.left() + "/" + entry.right() + "/" + entry.top() + "/" + entry.bottom();
    }

    private static String sha256Hex(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            assertThat(in.read(bytes)).isEqualTo(bytes.length);
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
