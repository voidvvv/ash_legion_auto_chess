package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
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
 * JsonLoader 负向用例（data_schema §九 加载即校验）：坏数据逐条断言
 * DataValidationException 且报错含 文件#条目/字段路径。夹具用 @TempDir 现写。
 */
class JsonLoaderValidationTest {

    @TempDir
    Path tempDir;

    // —— 最小合法夹具（每个用例覆写其一）——

    private static final String UNIT_OBJ =
            "{ \"id\": \"u1\", \"name\": \"试作兵\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
            + " \"baseStats\": { \"hp\": 100, \"attack\": 10, \"armor\": 5, \"attackSpeed\": 1.0,"
            + " \"range\": 1, \"moveSpeed\": 1.0 },"
            + " \"skillId\": \"sk1\" }";

    private static final String VALID_UNIT = "[" + UNIT_OBJ + "]";

    private static final String VALID_SKILL =
            "[{ \"id\": \"sk1\", \"name\": \"试作技\", \"desc\": \"测试\", \"shape\": \"SINGLE_TARGET\","
            + " \"effects\": [ { \"effect\": \"DAMAGE\", \"value\": 2.0 } ] }]";

    private static final String VALID_SYNERGY =
            "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"source\": \"RACE\", \"key\": \"兽人\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] },"
            + "{ \"id\": \"syn2\", \"name\": \"战士\", \"source\": \"CLASS\", \"key\": \"战士\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"armor\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";

    /** 用给定三份内容加载（null = 用合法缺省），返回 GameData 或抛 DataValidationException */
    private GameData load(String units, String skills, String synergies) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", units == null ? VALID_UNIT : units);
        write(dir, "skills.json", skills == null ? VALID_SKILL : skills);
        write(dir, "synergies.json", synergies == null ? VALID_SYNERGY : synergies);
        return JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    /** 在 JSON 数组字面量末尾追加一个元素（修正夹具拼接：必须落在 ] 之内） */
    private static String withExtraElement(String arrayJson, String element) {
        int close = arrayJson.lastIndexOf(']');
        return arrayJson.substring(0, close) + ", " + element + "]";
    }

    // —— 正向基准 ——

    @Test
    @DisplayName("最小合法集：零告警加载通过")
    void minimalValidSetLoadsCleanly() throws IOException {
        GameData data = load(null, null, null);
        assertThat(data.getUnits()).hasSize(1);
        assertThat(data.getSkills()).hasSize(1);
        assertThat(data.getSynergies()).hasSize(2);
        assertThat(data.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("BOM 容错：带 BOM 的文件可正常解析")
    void toleratesUtf8Bom() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", "﻿" + VALID_UNIT);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        GameData data = JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
        assertThat(data.getUnits()).hasSize(1);
    }

    // —— §九.1 必填字段与枚举 ——

    @Test
    @DisplayName("缺必填字段：报错含条目 id 与字段路径")
    void missingRequiredField() {
        String broken = "[{ \"id\": \"u1\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
                + " \"baseStats\": { \"hp\": 1, \"attack\": 1, \"armor\": 0, \"attackSpeed\": 1, \"range\": 1, \"moveSpeed\": 1 },"
                + " \"skillId\": \"sk1\" }]";
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("units.json#u1/name")
                .hasMessageContaining("缺必填字段");
    }

    @Test
    @DisplayName("非法枚举值：列出全部合法值")
    void invalidEnumValue() {
        String broken = "[{ \"id\": \"u1\", \"name\": \"x\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
                + " \"baseStats\": { \"hp\": 1, \"attack\": 1, \"armor\": 0, \"attackSpeed\": 1, \"range\": 1, \"moveSpeed\": 1 },"
                + " \"defaultPriority\": \"NEARBY\", \"skillId\": \"sk1\" }]";
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("defaultPriority")
                .hasMessageContaining("NEARBY")
                .hasMessageContaining("NEAREST / BACKLINE / LOWEST_HP / HIGHEST_ATK");
    }

    @Test
    @DisplayName("未知字段：fail-fast 防拼写错误静默失效")
    void unknownFieldRejected() {
        String broken = "[{ \"id\": \"u1\", \"name\": \"x\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
                + " \"baseStats\": { \"hp\": 1, \"attack\": 1, \"armor\": 0, \"atackSpeed\": 1, \"range\": 1, \"moveSpeed\": 1 },"
                + " \"skillId\": \"sk1\" }]";
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("baseStats/atackSpeed")
                .hasMessageContaining("未知字段");
    }

    // —— §九.2 数值边界 ——

    @Test
    @DisplayName("cost 越界（非 Boss 必须 ∈ {1,2,3}）")
    void costOutOfRange() {
        String broken = VALID_UNIT.replace("\"cost\": 1", "\"cost\": 5");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("units.json#u1/cost")
                .hasMessageContaining("{1,2,3}");
    }

    @Test
    @DisplayName("Boss 模板 cost 必须 = 0")
    void bossCostMustBeZero() {
        String broken = VALID_UNIT.replace("\"cost\": 1", "\"cost\": 2")
                .replace("\"skillId\": \"sk1\"", "\"skillId\": \"sk1\", \"boss\": true");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("Boss 模板 cost 必须 = 0");
    }

    @Test
    @DisplayName("baseStats 数值边界：hp 必须 > 0")
    void hpMustBePositive() {
        String broken = VALID_UNIT.replace("\"hp\": 100", "\"hp\": 0");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("baseStats/hp")
                .hasMessageContaining("必须 > 0");
    }

    @Test
    @DisplayName("range 必须 ≥ 1")
    void rangeMustBeAtLeastOne() {
        String broken = VALID_UNIT.replace("\"range\": 1", "\"range\": 0");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("range")
                .hasMessageContaining("≥ 1");
    }

    @Test
    @DisplayName("整数字段拒绝小数")
    void integerFieldRejectsFraction() {
        String broken = VALID_UNIT.replace("\"cost\": 1", "\"cost\": 1.5");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("cost")
                .hasMessageContaining("必须为整数");
    }

    // —— §九.3 唯一性与结构 ——

    @Test
    @DisplayName("id 全文件唯一：重复声明即死")
    void duplicateIdRejected() {
        String broken = withExtraElement(VALID_UNIT, UNIT_OBJ);
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("units.json#u1")
                .hasMessageContaining("唯一");
    }

    @Test
    @DisplayName("thresholds.count 必须严格升序唯一")
    void thresholdsMustBeStrictlyAscending() {
        String broken = "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"source\": \"RACE\", \"key\": \"兽人\","
                + " \"thresholds\": ["
                + " { \"count\": 4, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] },"
                + " { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("thresholds[1]/count")
                .hasMessageContaining("升序");
    }

    @Test
    @DisplayName("同 source 下 key 重复登记即死")
    void duplicateSynergyKeyRejected() {
        String broken = withExtraElement(VALID_SYNERGY,
                "{ \"id\": \"syn_orc_dup\", \"name\": \"兽人2\", \"source\": \"RACE\", \"key\": \"兽人\","
                + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] }");
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("key 重复登记");
    }

    // —— §九.4 引用完整性 ——

    @Test
    @DisplayName("悬空 skillId 引用即死，报错指向条目")
    void danglingSkillReferenceIsFatal() {
        String broken = VALID_UNIT.replace("\"sk1\"", "\"sk_missing\"");
        assertThatThrownBy(() -> load(broken, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("units.json#u1/skillId")
                .hasMessageContaining("sk_missing");
    }

    @Test
    @DisplayName("孤儿技能只告警不阻断")
    void orphanSkillOnlyWarns() throws IOException {
        String withOrphan = withExtraElement(VALID_SKILL,
                "{ \"id\": \"sk_orphan\", \"name\": \"孤儿\", \"desc\": \"无引用\", \"shape\": \"SELF\","
                + " \"effects\": [ { \"effect\": \"APPLY_STATUS\", \"status\": \"ATK_UP\", \"value\": 10, \"duration\": 3 } ] }");
        GameData data = load(null, withOrphan, null);
        assertThat(data.getSkills()).hasSize(2);
        assertThat(data.getWarnings()).anyMatch(w -> w.contains("孤儿技能") && w.contains("sk_orphan"));
    }

    @Test
    @DisplayName("风味种族/职业只聚合告警，不报错（Q2 口径）")
    void flavorRaceAggregatedWarning() throws IOException {
        String withFlavor = withExtraElement(VALID_UNIT,
                "{ \"id\": \"u2\", \"name\": \"精灵\", \"race\": \"精灵\", \"class\": \"游侠\", \"cost\": 1,"
                + " \"baseStats\": { \"hp\": 1, \"attack\": 1, \"armor\": 0, \"attackSpeed\": 1, \"range\": 3, \"moveSpeed\": 1 },"
                + " \"skillId\": \"sk1\" }");
        GameData data = load(withFlavor, null, null);
        assertThat(data.getWarnings()).anyMatch(w -> w.contains("风味") && w.contains("精灵") && w.contains("游侠"));
    }

    @Test
    @DisplayName("ALL_ENEMIES 被非 Boss 单位引用：软告警")
    void allEnemiesOnNonBossWarns() throws IOException {
        String skill = "[{ \"id\": \"sk1\", \"name\": \"全屏\", \"desc\": \"x\", \"shape\": \"ALL_ENEMIES\","
                + " \"effects\": [ { \"effect\": \"DAMAGE\", \"value\": 1.0 } ] }]";
        GameData data = load(null, skill, null);
        assertThat(data.getWarnings()).anyMatch(w -> w.contains("ALL_ENEMIES") && w.contains("u1"));
    }

    // —— §九.5 效果字段配平 ——

    @Test
    @DisplayName("DAMAGE/HEAL/SHIELD 必有 value > 0")
    void damageRequiresPositiveValue() {
        String broken = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"DAMAGE\" } ] }]";
        assertThatThrownBy(() -> load(null, broken, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("value")
                .hasMessageContaining("DAMAGE 必须有 value > 0");
    }

    @Test
    @DisplayName("APPLY_STATUS 必有合法 status 与 duration > 0")
    void applyStatusRequiresStatusAndDuration() {
        String broken = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"APPLY_STATUS\", \"status\": \"STUN\" } ] }]";
        assertThatThrownBy(() -> load(null, broken, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("duration")
                .hasMessageContaining("APPLY_STATUS");
    }

    @Test
    @DisplayName("非 APPLY_STATUS 不允许 status/duration")
    void damageForbidsStatusFields() {
        String broken = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"HEAL\", \"value\": 0.2, \"status\": \"REGEN\", \"duration\": 3 } ] }]";
        assertThatThrownBy(() -> load(null, broken, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("不允许 status/duration");
    }

    @Test
    @DisplayName("每技能效果 1~3 条：空数组与 4 条均拒绝")
    void skillEffectCountBounds() {
        String empty = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SELF\", \"effects\": [] }]";
        assertThatThrownBy(() -> load(null, empty, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("effects")
                .hasMessageContaining("非空数组");

        StringBuilder four = new StringBuilder(
                "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SELF\", \"effects\": [");
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                four.append(", ");
            }
            four.append("{ \"effect\": \"DAMAGE\", \"value\": 1.0 }");
        }
        four.append("] }]");
        assertThatThrownBy(() -> load(null, four.toString(), null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("至多 3");
    }

    @Test
    @DisplayName("羁绊效果 stat 与 effect 必须二选一")
    void synergyEffectStatXorEffect() {
        String both = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10, \"effect\": \"SHIELD\" }");
        assertThatThrownBy(() -> load(null, null, both))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("二选一");

        String neither = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"value\": 10 }");
        assertThatThrownBy(() -> load(null, null, neither))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    @DisplayName("stat 通道必配 op")
    void statChannelRequiresOp() {
        String broken = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"stat\": \"hp\", \"value\": 10 }");
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("op")
                .hasMessageContaining("缺必填字段");
    }

    // —— 文件级 ——

    @Test
    @DisplayName("文件不存在：启动即死")
    void missingFileIsFatal() {
        assertThatThrownBy(() -> JsonLoader.load(
                        new FileHandle(tempDir.resolve("nope/units.json").toString()),
                        new FileHandle(tempDir.resolve("nope/skills.json").toString()),
                        new FileHandle(tempDir.resolve("nope/synergies.json").toString())))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("根节点必须是数组")
    void rootMustBeArray() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", "{\"id\": \"u1\"}");
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        assertThatThrownBy(() -> JsonLoader.loadFromDirectory(new FileHandle(dir.toString())))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("根节点必须是数组");
    }
}
