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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真素材图集库存量回归锚（批 2 入库转正，2026-11-22）：
 * ①② 无条件断言（耐用的防漂移锚）——assets/art/units/ 目录清单 ≡ PlaceholderKeys 派生帧键集
 * （逐字符、恰 N 帧、无键外残留；N 随 units.json 模板数走）+ 全部 IHDR 32×32；
 * 生效契约链：Assets.region 先查 RealArt，文件存在即真素材、占位层不被咨询。
 * ③ 条件断言——SHA-256 与批 2 manifest 对账，<b>仅当 manifest 在库时执行</b>（manifest 已随
 * assets_src/draft_batch2/ 入库；未来换素材批时更新或移除对应 manifest，本用例自动跳过而非红测）。
 */
class ArtInventoryTest {

    private static final Path ART_DIR = Paths.get("../assets/art/units");
    private static final Path BATCH2_MANIFEST = Paths.get("../assets_src/draft_batch2/manifest.json");

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** enumerateFor 派生的单位帧键集（每模板 12 帧；随 units.json 模板数伸缩，非手抄清单） */
    private static Set<String> derivedFrameKeys() {
        Set<String> unitIds = data.getUnits().keySet();
        return PlaceholderKeys.enumerateFor(data).stream()
                .filter(key -> {
                    for (String unitId : unitIds) {
                        if (key.startsWith(unitId + "_")) {
                            return true; // {unitId}_{anim}_{frame} 帧键
                        }
                    }
                    return false;
                })
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("库存量对账：art/units 目录 ≡ 派生帧键集（逐字符含大小写、无键外残留/缺失）")
    void directoryReconcilesWithDerivedFrameKeys() throws IOException {
        Set<String> derived = derivedFrameKeys();
        int templates = data.getUnits().size();
        assertThat(derived).as("帧键数 = 模板数 × 12").hasSize(templates * 12); // 当前 33×12=396

        Set<String> pngStems = new HashSet<String>();
        Map<String, String> lowerToRaw = new HashMap<String, String>();
        for (Path p : Files.list(ART_DIR).collect(Collectors.toList())) {
            String name = p.getFileName().toString();
            if (name.endsWith(".png")) {
                String stem = name.substring(0, name.length() - 4);
                pngStems.add(stem);
                lowerToRaw.put(stem.toLowerCase(Locale.ROOT), stem); // 大小写陷阱防御（Windows 不区分、打包后会）
            }
        }
        assertThat(pngStems).as("目录恰为派生键集（无多余/缺失）").isEqualTo(derived);
        for (String key : derived) {
            assertThat(lowerToRaw.get(key.toLowerCase(Locale.ROOT)))
                    .as("文件名逐字符精确（含大小写）: %s", key).isEqualTo(key);
        }
    }

    @Test
    @DisplayName("尺寸锚：全部帧文件 IHDR 32×32（真素材画布口径不漂移）")
    void allFrameFilesAre32x32() throws IOException {
        for (String key : derivedFrameKeys()) {
            File file = new File(ART_DIR.toFile(), key + ".png");
            assertThat(file).as("帧文件存在: %s", key).exists();
            byte[] header = new byte[26];
            try (FileInputStream in = new FileInputStream(file)) {
                assertThat(in.read(header)).isEqualTo(26);
            }
            assertThat(((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                    | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF))
                    .as("%s 宽 32（IHDR）", key).isEqualTo(32);
            assertThat(((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                    | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF))
                    .as("%s 高 32（IHDR）", key).isEqualTo(32);
        }
    }

    @Test
    @DisplayName("完整性对账（条件）：批 2 manifest 在库时，全部帧 SHA-256 与之逐一对账")
    void shaMatchesBatch2ManifestWhenPresent() throws Exception {
        assumeTrue(Files.exists(BATCH2_MANIFEST),
                "批 2 manifest 不在库——跳过 SHA 对账（未来素材批随批更新/移除 manifest 即自洽）");
        Map<String, String> manifestSha = new HashMap<String, String>();
        for (JsonValue entry : new JsonReader().parse(
                new String(Files.readAllBytes(BATCH2_MANIFEST), java.nio.charset.StandardCharsets.UTF_8))) {
            manifestSha.put(entry.getString("key"), entry.getString("sha256"));
        }
        assertThat(manifestSha).hasSize(data.getUnits().size() * 12);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> e : manifestSha.entrySet()) {
            File file = new File(ART_DIR.toFile(), e.getKey() + ".png");
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                assertThat(in.read(bytes)).isEqualTo(bytes.length);
            }
            digest.reset();
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : digest.digest(bytes)) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            assertThat(sb.toString()).as("%s SHA-256 与 manifest 一致", e.getKey()).isEqualTo(e.getValue());
        }
    }
}
