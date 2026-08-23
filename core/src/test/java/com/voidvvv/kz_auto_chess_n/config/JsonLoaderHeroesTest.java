package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
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
 * heroes.json 解析与交叉校验用例（Phase 6，裁决 D17 词表制）：
 * 合法三英雄透传 / 缺文件口径 / 交叉校验（synergyIds、传奇三规则）/ 未知字段即死。
 * shopUnlocks 与传奇互斥（CP4 块 11）在此覆盖——需同时控制 heroes 与 scenes 两文件。
 */
class JsonLoaderHeroesTest {

    @TempDir
    Path tempDir;

    // —— 最小合法夹具：u1 一费杂兵 / u3 三费（传奇位）+ 三 Boss + 兽人羁绊 ——

    private static final String VALID_UNITS = "[" + unit("u1", 1) + ", " + unit("u3", 3) + ", "
            + bossUnit("b1") + ", " + bossUnit("b2") + ", " + bossUnit("b3") + "]";

    private static final String VALID_SKILL =
            "[{ \"id\": \"sk1\", \"name\": \"试作技\", \"desc\": \"测试\", \"shape\": \"SINGLE_TARGET\","
            + " \"effects\": [ { \"effect\": \"DAMAGE\", \"value\": 2.0 } ] }]";

    private static final String VALID_SYNERGY =
            "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";

    private static final String VALID_SCENES =
            "[{ \"id\": \"sc1\", \"name\": \"试作场景\", \"unlockAfter\": null,"
            + " \"enemyPool\": [ { \"unitId\": \"u1\", \"weight\": 3, \"minRound\": 1 } ],"
            + " \"bosses\": { \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\" } }]";

    private static String unit(String id, int cost) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作兵" + id + "\", \"race\": \"兽人\", \"class\": \"战士\","
            + " \"cost\": " + cost + ","
            + " \"baseStats\": { \"hp\": 100, \"attack\": 10, \"armor\": 5, \"attackSpeed\": 1.0,"
            + " \"range\": 1, \"moveSpeed\": 1.0 },"
            + " \"skillId\": \"sk1\" }";
    }

    private static String bossUnit(String id) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作Boss" + id + "\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 0,"
            + " \"baseStats\": { \"hp\": 200, \"attack\": 20, \"armor\": 5, \"attackSpeed\": 1.0,"
            + " \"range\": 1, \"moveSpeed\": 1.0 },"
            + " \"skillId\": \"sk1\", \"boss\": true }";
    }

    private static String hero(String id, String passive, String legendary) {
        return "{ \"id\": \"" + id + "\", \"name\": \"英雄" + id + "\", \"desc\": \"试作英雄\","
            + " \"passive\": " + passive
            + (legendary == null ? "" : ", \"legendaryUnitId\": \"" + legendary + "\"") + " }";
    }

    /** 写全套最小合法数据，heroes 用给定内容；返回 GameData 或抛 DataValidationException */
    private GameData loadHeroes(String heroes) throws IOException {
        return loadAll(heroes, null);
    }

    /** heroes 与 scenes 双覆写（互斥校验需同时控制两文件） */
    private GameData loadAll(String heroes, String scenes) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        write(dir, "scenes.json", scenes == null ? VALID_SCENES : scenes);
        write(dir, "equipments.json", "[]");
        write(dir, "heroes.json", heroes);
        return JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    // —— 正向解析 ——

    @Test
    @DisplayName("合法三英雄：type/value/synergyIds/legendaryUnitId 全量透传")
    void parsesAllPassiveShapes() throws IOException {
        GameData data = loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "u3") + ", "
                + hero("h2",
                "{ \"type\": \"SYNERGY_AMP\", \"value\": 25, \"synergyIds\": [\"syn1\"] }", null) + ", "
                + hero("h3",
                "{ \"type\": \"ENERGY_GAIN\", \"value\": 15 }", null) + "]");
        assertThat(data.getHeroes()).hasSize(3);
        assertThat(data.getHeroes().keySet()).containsExactly("h1", "h2", "h3");

        HeroData h1 = data.getHero("h1");
        assertThat(h1.getName()).isEqualTo("英雄h1");
        assertThat(h1.getPassiveType()).isEqualTo(HeroPassiveType.START_GOLD);
        assertThat(h1.getPassiveValue()).isEqualTo(2f);
        assertThat(h1.getPassiveSynergyIds()).isEmpty();
        assertThat(h1.getLegendaryUnitId()).isEqualTo("u3");

        HeroData h2 = data.getHero("h2");
        assertThat(h2.getPassiveType()).isEqualTo(HeroPassiveType.SYNERGY_AMP);
        assertThat(h2.getPassiveSynergyIds()).containsExactly("syn1");
        assertThat(h2.getLegendaryUnitId()).isNull();

        assertThat(data.getHero("h3").getPassiveType()).isEqualTo(HeroPassiveType.ENERGY_GAIN);
        assertThat(data.getHero("h3").getPassiveValue()).isEqualTo(15f);
    }

    @Test
    @DisplayName("兼容重载：6 参 load 传 null heroes 文件 → 空英雄表不炸")
    void nullHeroesFileYieldsEmptyTable() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        write(dir, "scenes.json", VALID_SCENES);
        write(dir, "equipments.json", "[]");
        GameData data = JsonLoader.load(
                new FileHandle(dir.resolve("units.json").toString()),
                new FileHandle(dir.resolve("skills.json").toString()),
                new FileHandle(dir.resolve("synergies.json").toString()),
                new FileHandle(dir.resolve("scenes.json").toString()),
                new FileHandle(dir.resolve("equipments.json").toString()), null);
        assertThat(data.getHeroes()).isEmpty();
    }

    @Test
    @DisplayName("loadFromDirectory 缺 heroes.json：启动即死（生产路径必存在，沿 equipments 先例）")
    void missingHeroesFileIsFatal() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        write(dir, "scenes.json", VALID_SCENES);
        write(dir, "equipments.json", "[]");
        assertThatThrownBy(() -> JsonLoader.loadFromDirectory(new FileHandle(dir.toString())))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("文件不存在")
                .hasMessageContaining("heroes.json");
    }

    // —— passive 结构校验 ——

    @Test
    @DisplayName("非法被动类型即死：报错列合法词表")
    void illegalPassiveType() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"NO_SUCH\", \"value\": 2 }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/passive/type")
                .hasMessageContaining("START_GOLD");
    }

    @Test
    @DisplayName("value ≤ 0 即死")
    void nonPositiveValue() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 0 }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/passive/value")
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("SYNERGY_AMP 必须带 synergyIds；其余类型禁止 synergyIds")
    void synergyIdsOnlyForAmp() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"SYNERGY_AMP\", \"value\": 25 }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/passive/synergyIds")
                .hasMessageContaining("至少 1 个羁绊");

        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2, \"synergyIds\": [\"syn1\"] }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/passive/synergyIds")
                .hasMessageContaining("仅 SYNERGY_AMP");
    }

    @Test
    @DisplayName("未知字段 / 被动未知字段 / id 重复声明均即死")
    void unknownFieldsAndDuplicateId() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", null) + ", "
                + hero("h1", "{ \"type\": \"START_GOLD\", \"value\": 2 }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1")
                .hasMessageContaining("唯一");

        assertThatThrownBy(() -> loadHeroes(
                "[{ \"id\": \"h1\", \"name\": \"x\", \"desc\": \"y\", \"foo\": 1,"
                + " \"passive\": { \"type\": \"START_GOLD\", \"value\": 2 } }]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段");

        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2, \"extra\": true }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("未知字段");
    }

    // —— 交叉校验（块 10）——

    @Test
    @DisplayName("synergyIds 悬空引用即死")
    void danglingSynergyReference() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"SYNERGY_AMP\", \"value\": 25, \"synergyIds\": [\"syn_missing\"] }", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/passive/synergyIds")
                .hasMessageContaining("不存在的羁绊: syn_missing");
    }

    @Test
    @DisplayName("legendaryUnitId 悬空 / Boss 模板 / cost≠3 均即死")
    void legendaryMustBeNonBossCost3Unit() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "u_missing") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/legendaryUnitId")
                .hasMessageContaining("不存在的单位: u_missing");

        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "b1") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/legendaryUnitId")
                .hasMessageContaining("非 Boss 且 cost=3");

        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "u1") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h1/legendaryUnitId")
                .hasMessageContaining("非 Boss 且 cost=3");
    }

    @Test
    @DisplayName("两名英雄不得共用同一传奇棋子")
    void legendaryCannotBeShared() {
        assertThatThrownBy(() -> loadHeroes("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "u3") + ", "
                + hero("h2", "{ \"type\": \"ENERGY_GAIN\", \"value\": 15 }", "u3") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("heroes.json#h2/legendaryUnitId")
                .hasMessageContaining("共用");
    }

    // —— 交叉校验（块 11：shopUnlocks 与传奇互斥）——

    @Test
    @DisplayName("场景 shopUnlocks 登记英雄专属传奇即死（两机制互斥——裁决 D8）")
    void shopUnlocksCannotIncludeHeroLegendary() {
        String scenes = "[{ \"id\": \"sc1\", \"name\": \"试作场景\", \"unlockAfter\": null,"
                + " \"enemyPool\": [ { \"unitId\": \"u1\", \"weight\": 3, \"minRound\": 1 } ],"
                + " \"bosses\": { \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\" },"
                + " \"shopUnlocks\": [\"u3\"] }]";
        assertThatThrownBy(() -> loadAll("[" + hero("h1",
                "{ \"type\": \"START_GOLD\", \"value\": 2 }", "u3") + "]", scenes))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("scenes.json#sc1/shopUnlocks")
                .hasMessageContaining("互斥");
    }

    // —— 容器不可变 ——

    @Test
    @DisplayName("GameData heroes 为不可变视图（put 抛 UnsupportedOperationException）")
    void heroesMapUnmodifiable() throws IOException {
        GameData data = loadHeroes("[" + hero("h1", "{ \"type\": \"START_GOLD\", \"value\": 2 }", null) + "]");
        assertThatThrownBy(() -> data.getHeroes().put("h2", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
