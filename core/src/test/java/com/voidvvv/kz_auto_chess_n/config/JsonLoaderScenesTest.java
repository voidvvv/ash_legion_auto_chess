package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * scenes.json 解析与交叉校验用例（Phase 2，data_schema §七 结构锁定 +
 * S1~S6 校验组，编号见实施计划 §5.4）。夹具 @TempDir 现写，自成最小合法集。
 */
class JsonLoaderScenesTest {

    @TempDir
    Path tempDir;

    // —— 最小合法夹具（场景引用 u1/u2 杂兵 + b1/b2/b3 Boss）——

    private static final String VALID_UNITS = "[" + unit("u1", 1) + ", " + unit("u2", 3) + ", "
            + bossUnit("b1") + ", " + bossUnit("b2") + ", " + bossUnit("b3") + "]";

    private static final String VALID_SKILL =
            "[{ \"id\": \"sk1\", \"name\": \"试作技\", \"desc\": \"测试\", \"shape\": \"SINGLE_TARGET\","
            + " \"effects\": [ { \"effect\": \"DAMAGE\", \"value\": 2.0 } ] }]";

    private static final String DEFAULT_POOL =
            "[ { \"unitId\": \"u1\", \"weight\": 3, \"minRound\": 1 },"
            + " { \"unitId\": \"u2\", \"weight\": 2, \"minRound\": 2 } ]";

    private static final String DEFAULT_BOSSES = "{ \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\" }";

    private static String unit(String id, int range) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作兵" + id + "\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
                + " \"baseStats\": { \"hp\": 100, \"attack\": 10, \"armor\": 5, \"attackSpeed\": 1.0,"
                + " \"range\": " + range + ", \"moveSpeed\": 1.0 },"
                + " \"skillId\": \"sk1\" }";
    }

    private static String bossUnit(String id) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作Boss" + id + "\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 0,"
                + " \"baseStats\": { \"hp\": 200, \"attack\": 20, \"armor\": 5, \"attackSpeed\": 1.0,"
                + " \"range\": 1, \"moveSpeed\": 1.0 },"
                + " \"skillId\": \"sk1\", \"boss\": true }";
    }

    private static String scene(String id, String unlockAfter, String pool, String bosses) {
        return "{ \"id\": \"" + id + "\", \"name\": \"场景" + id + "\", \"unlockAfter\": "
                + (unlockAfter == null ? "null" : ("\"" + unlockAfter + "\""))
                + ", \"enemyPool\": " + pool + ", \"bosses\": " + bosses + " }";
    }

    /** 写全套最小合法数据，scenes 用给定内容；返回 GameData 或抛 DataValidationException */
    private GameData loadScenes(String scenes) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", "[]");
        write(dir, "scenes.json", scenes);
        return JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    // —— 正向基准 ——

    @Test
    @DisplayName("种子正向全字段：池条目保序、三 Boss 键齐全、unlockAfter null 放行")
    void positiveFullFields() throws IOException {
        GameData data = loadScenes("[" + scene("sc1", null, DEFAULT_POOL, DEFAULT_BOSSES) + "]");
        assertThat(data.getScenes()).hasSize(1);
        assertThat(data.getScenes().keySet()).containsExactly("sc1");

        SceneData scene = data.getScene("sc1");
        assertThat(scene.getId()).isEqualTo("sc1");
        assertThat(scene.getName()).isEqualTo("场景sc1");
        assertThat(scene.getUnlockAfter()).isNull();
        assertThat(scene.getEnemyPool())
                .extracting(SceneData.EnemyPoolEntry::getUnitId)
                .containsExactly("u1", "u2"); // 声明序（权重数组序的确定性前提）
        assertThat(scene.getEnemyPool())
                .extracting(SceneData.EnemyPoolEntry::getWeight)
                .containsExactly(3, 2);
        assertThat(scene.getEnemyPool())
                .extracting(SceneData.EnemyPoolEntry::getMinRound)
                .containsExactly(1, 2);
        assertThat(scene.getBosses()).hasSize(3);
        assertThat(scene.getBosses()).containsEntry(7, "b1");
        assertThat(scene.getBosses()).containsEntry(15, "b2");
        assertThat(scene.getBosses()).containsEntry(25, "b3");
    }

    @Test
    @DisplayName("getBossUnitId：7/15/25 命中对应 Boss，其余轮次返回 null")
    void getBossUnitIdLookup() throws IOException {
        GameData data = loadScenes("[" + scene("sc1", null, DEFAULT_POOL, DEFAULT_BOSSES) + "]");
        SceneData scene = data.getScene("sc1");
        assertThat(scene.getBossUnitId(7)).isEqualTo("b1");
        assertThat(scene.getBossUnitId(15)).isEqualTo("b2");
        assertThat(scene.getBossUnitId(25)).isEqualTo("b3");
        assertThat(scene.getBossUnitId(1)).isNull();
        assertThat(scene.getBossUnitId(8)).isNull();
        assertThat(scene.getBossUnitId(20)).isNull();
    }

    @Test
    @DisplayName("unlockAfter 合法前置链：多场景加载通过且保持声明序")
    void unlockChainPositive() throws IOException {
        String scenes = "[" + scene("sc1", null, DEFAULT_POOL, DEFAULT_BOSSES) + ", "
                + scene("sc2", "sc1", DEFAULT_POOL, DEFAULT_BOSSES) + "]";
        GameData data = loadScenes(scenes);
        assertThat(data.getScenes().keySet()).containsExactly("sc1", "sc2");
        assertThat(data.getScene("sc1").getUnlockAfter()).isNull();
        assertThat(data.getScene("sc2").getUnlockAfter()).isEqualTo("sc1");
    }

    // —— S1/S2/S3 引用完整性 ——

    @Test
    @DisplayName("S1：enemyPool unitId 悬空引用即死")
    void s1DanglingPoolUnitId() {
        String pool = "[ { \"unitId\": \"u_missing\", \"weight\": 1, \"minRound\": 1 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, pool, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/enemyPool")
                .hasMessageContaining("不存在的单位: u_missing");
    }

    @Test
    @DisplayName("S2：Boss 模板不得出现在 enemyPool 权重位")
    void s2BossTemplateInPool() {
        String pool = "[ { \"unitId\": \"b1\", \"weight\": 1, \"minRound\": 1 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, pool, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/enemyPool")
                .hasMessageContaining("Boss 模板不得出现在 enemyPool");
    }

    @Test
    @DisplayName("S3：bosses 值悬空引用即死，报错含轮次键")
    void s3DanglingBossReference() {
        String bosses = "{ \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b_missing\" }";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, DEFAULT_POOL, bosses) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/bosses/25")
                .hasMessageContaining("不存在的单位: b_missing");
    }

    @Test
    @DisplayName("S3：bosses 值引用非 Boss 模板即死（对称防御）")
    void s3NonBossInBossSlot() {
        String bosses = "{ \"7\": \"u1\", \"15\": \"b2\", \"25\": \"b3\" }";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, DEFAULT_POOL, bosses) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/bosses/7")
                .hasMessageContaining("非 Boss 模板");
    }

    // —— S4 unlockAfter ——

    @Test
    @DisplayName("S4：unlockAfter 悬空引用即死")
    void s4DanglingUnlockAfter() {
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", "scene_missing", DEFAULT_POOL, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/unlockAfter")
                .hasMessageContaining("不存在的场景: scene_missing");
    }

    @Test
    @DisplayName("S4：unlockAfter 禁自指")
    void s4SelfReference() {
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", "sc1", DEFAULT_POOL, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/unlockAfter")
                .hasMessageContaining("禁自指");
    }

    @Test
    @DisplayName("S4：多场景前置链 A→B→A 成环报错")
    void s4UnlockChainCycle() {
        String scenes = "[" + scene("scA", "scB", DEFAULT_POOL, DEFAULT_BOSSES) + ", "
                + scene("scB", "scA", DEFAULT_POOL, DEFAULT_BOSSES) + "]";
        assertThatThrownBy(() -> loadScenes(scenes))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("unlockAfter")
                .hasMessageContaining("成环");
    }

    // —— S5/S6 池条目边界 ——

    @Test
    @DisplayName("S5：enemyPool 无 minRound ≤ 1 条目即死（第 1 轮将无兵可抽）")
    void s5PoolMustHaveRoundOneEntry() {
        String pool = "[ { \"unitId\": \"u2\", \"weight\": 2, \"minRound\": 2 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, pool, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/enemyPool")
                .hasMessageContaining("至少一条 minRound");
    }

    @Test
    @DisplayName("S6：weight=0 即死（正整数 ≥ 1）")
    void s6WeightMustBePositive() {
        String pool = "[ { \"unitId\": \"u1\", \"weight\": 0, \"minRound\": 1 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, pool, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("enemyPool[0]/weight")
                .hasMessageContaining("正整数");
    }

    @Test
    @DisplayName("S6：minRound=0 与 minRound=26 均即死（1~25）")
    void s6MinRoundBounds() {
        String tooEarly = "[ { \"unitId\": \"u1\", \"weight\": 1, \"minRound\": 0 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, tooEarly, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("enemyPool[0]/minRound")
                .hasMessageContaining("1~25");

        String tooLate = "[ { \"unitId\": \"u1\", \"weight\": 1, \"minRound\": 26 } ]";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, tooLate, DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("enemyPool[0]/minRound")
                .hasMessageContaining("26");
    }

    // —— bosses 键 ——

    @Test
    @DisplayName("bosses 缺键（只有 7/15）即死：三键必须齐全")
    void bossesMissingKey() {
        String bosses = "{ \"7\": \"b1\", \"15\": \"b2\" }";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, DEFAULT_POOL, bosses) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/bosses")
                .hasMessageContaining("三键 {7, 15, 25} 必须齐全")
                .hasMessageContaining("缺 \"25\"");
    }

    @Test
    @DisplayName("bosses 键非法：非 Boss 轮（8）与非整数（x）均即死")
    void bossesIllegalKey() {
        String notBossRound = "{ \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\", \"8\": \"b1\" }";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, DEFAULT_POOL, notBossRound) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("键必须 ∈ {7, 15, 25}")
                .hasMessageContaining("8");

        String notInteger = "{ \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\", \"x\": \"b1\" }";
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, DEFAULT_POOL, notInteger) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("键必须为整数轮次")
                .hasMessageContaining("x");
    }

    // —— 结构 ——

    @Test
    @DisplayName("enemyPool 空数组即死")
    void emptyEnemyPoolArray() {
        assertThatThrownBy(() -> loadScenes("[" + scene("sc1", null, "[]", DEFAULT_BOSSES) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/enemyPool")
                .hasMessageContaining("非空数组");
    }

    @Test
    @DisplayName("场景未知字段即死（fail-fast）")
    void unknownSceneField() {
        String scenes = "[{ \"id\": \"sc1\", \"name\": \"x\", \"unlockAfter\": null,"
                + " \"enemyPool\": " + DEFAULT_POOL + ", \"bosses\": " + DEFAULT_BOSSES + ", \"foo\": 1 }]";
        assertThatThrownBy(() -> loadScenes(scenes))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段");
    }

    @Test
    @DisplayName("场景 id 全文件唯一：重复声明即死")
    void duplicateSceneId() {
        String scenes = "[" + scene("sc1", null, DEFAULT_POOL, DEFAULT_BOSSES) + ", "
                + scene("sc1", null, DEFAULT_POOL, DEFAULT_BOSSES) + "]";
        assertThatThrownBy(() -> loadScenes(scenes))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1")
                .hasMessageContaining("唯一");
    }
}
