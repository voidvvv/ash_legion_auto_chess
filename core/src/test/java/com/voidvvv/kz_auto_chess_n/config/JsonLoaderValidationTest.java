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
            "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] },"
            + "{ \"id\": \"syn2\", \"name\": \"战士\", \"desc\": \"试作\", \"source\": \"CLASS\", \"key\": \"战士\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"armor\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";

    // —— scenes 最小合法集（Phase 2 起 loadFromDirectory 增读 scenes.json）——
    // 合法场景必须引用 Boss 模板（S3），故默认 units 含 u1/u2 杂兵 + b1/b2/b3 Boss

    private static String bossUnitObj(String id) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作Boss" + id + "\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 0,"
                + " \"baseStats\": { \"hp\": 200, \"attack\": 20, \"armor\": 5, \"attackSpeed\": 1.0,"
                + " \"range\": 1, \"moveSpeed\": 1.0 },"
                + " \"skillId\": \"sk1\", \"boss\": true }";
    }

    private static final String UNIT_U2_OBJ =
            "{ \"id\": \"u2\", \"name\": \"试作兵2\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
            + " \"baseStats\": { \"hp\": 100, \"attack\": 10, \"armor\": 5, \"attackSpeed\": 1.0,"
            + " \"range\": 3, \"moveSpeed\": 1.0 },"
            + " \"skillId\": \"sk1\" }";

    private static final String VALID_UNITS = "[" + UNIT_OBJ + ", " + UNIT_U2_OBJ + ", "
            + bossUnitObj("b1") + ", " + bossUnitObj("b2") + ", " + bossUnitObj("b3") + "]";

    private static final String VALID_SCENES =
            "[{ \"id\": \"sc1\", \"name\": \"试作场景\", \"unlockAfter\": null,"
            + " \"enemyPool\": ["
            + " { \"unitId\": \"u1\", \"weight\": 3, \"minRound\": 1 },"
            + " { \"unitId\": \"u2\", \"weight\": 2, \"minRound\": 2 } ],"
            + " \"bosses\": { \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\" } }]";

    /** 用给定四份内容加载（null = 用合法缺省），返回 GameData 或抛 DataValidationException */
    private GameData load(String units, String skills, String synergies, String scenes) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", units == null ? VALID_UNITS : units);
        write(dir, "skills.json", skills == null ? VALID_SKILL : skills);
        write(dir, "synergies.json", synergies == null ? VALID_SYNERGY : synergies);
        write(dir, "scenes.json", scenes == null ? VALID_SCENES : scenes);
        write(dir, "equipments.json", "[]"); // Phase 5 起 loadFromDirectory 增读 equipments.json（沿 scenes.json 先例）
        write(dir, "heroes.json", "[]");     // Phase 6 起 loadFromDirectory 增读 heroes.json
        return JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
    }

    private GameData load(String units, String skills, String synergies) throws IOException {
        return load(units, skills, synergies, null);
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
        assertThat(data.getUnits()).hasSize(5);
        assertThat(data.getSkills()).hasSize(1);
        assertThat(data.getSynergies()).hasSize(2);
        assertThat(data.getScenes()).hasSize(1);
        assertThat(data.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("BOM 容错：带 BOM 的文件可正常解析")
    void toleratesUtf8Bom() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        write(dir, "units.json", "﻿" + VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        write(dir, "scenes.json", VALID_SCENES);
        write(dir, "equipments.json", "[]");
        write(dir, "heroes.json", "[]"); // Phase 6 起 loadFromDirectory 增读 heroes.json
        GameData data = JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
        assertThat(data.getUnits()).hasSize(5);
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
        String broken = "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
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
                "{ \"id\": \"syn_orc_dup\", \"name\": \"兽人2\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
                + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] }");
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("key 重复登记");
    }

    @Test
    @DisplayName("synergies desc 必填：缺失即死且报错含字段路径（与 skills.json desc 同口径，Phase 5.1 裁决 2）")
    void missingSynergyDescIsFatal() {
        String broken = "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"source\": \"RACE\", \"key\": \"兽人\","
                + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("synergies.json#syn1/desc")
                .hasMessageContaining("缺必填字段");
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
        String withFlavor = withExtraElement(VALID_UNITS,
                "{ \"id\": \"u3\", \"name\": \"精灵\", \"race\": \"精灵\", \"class\": \"游侠\", \"cost\": 1,"
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
    @DisplayName("羁绊效果 stat / effect / mechanic 必须三选一")
    void synergyEffectStatXorEffect() {
        String both = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10, \"effect\": \"SHIELD\" }");
        assertThatThrownBy(() -> load(null, null, both))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("三选一");

        String neither = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"value\": 10 }");
        assertThatThrownBy(() -> load(null, null, neither))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("三选一");

        // stat + mechanic 双选同罪（v5 第三通道互斥）
        String statAndMechanic = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10,"
                + " \"mechanic\": \"DOUBLE_DEATHRATTLE\" }");
        assertThatThrownBy(() -> load(null, null, statAndMechanic))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("三选一");
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

    // —— v5 批0：机制三通道 / 亡语 / 不死 / 施放条件 逐词校验 ——

    @Test
    @DisplayName("机制通道正向：逐词参数解析通过（K1 参数表）")
    void mechanicChannelPositive() throws IOException {
        String synergies = "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
                + " \"thresholds\": [ { \"count\": 2, \"effects\": ["
                + " { \"mechanic\": \"BASIC_ATTACK_STATUS\", \"status\": \"BURN\", \"value\": 30, \"duration\": 3,"
                + " \"target\": \"TRAITS\", \"trait\": \"兽人\" },"
                + " { \"mechanic\": \"DEATH_TAX\", \"value\": 4, \"value2\": 20 },"
                + " { \"mechanic\": \"DOUBLE_DEATHRATTLE\", \"target\": \"TRAITS\", \"trait\": \"兽人\" },"
                + " { \"mechanic\": \"DEATH_AUDIT\", \"value\": 25 } ] } ] }]";
        GameData data = load(null, null, synergies);
        assertThat(data.getSynergy("syn1").getThresholds().get(0).getEffects()).hasSize(4);
    }

    @Test
    @DisplayName("机制通道负向：target=TRAITS 缺 trait 即死；无参词带 value 即死；BASIC_ATTACK_STATUS 缺 status 即死")
    void mechanicChannelValidation() {
        String missingTrait = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"mechanic\": \"DEATH_TAX\", \"value\": 4, \"value2\": 20,"
                + " \"target\": \"TRAITS\" }");
        assertThatThrownBy(() -> load(null, null, missingTrait))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("syn1/thresholds[0]/effects[0]/trait")
                .hasMessageContaining("TRAITS");

        String noParamWordWithValue = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"mechanic\": \"SHIELD_WALL\", \"value\": 3 }");
        assertThatThrownBy(() -> load(null, null, noParamWordWithValue))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("SHIELD_WALL")
                .hasMessageContaining("无参机制词");

        String missingStatus = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"mechanic\": \"BASIC_ATTACK_STATUS\", \"value\": 30, \"duration\": 3 }");
        assertThatThrownBy(() -> load(null, null, missingStatus))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("BASIC_ATTACK_STATUS")
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("机制词未知值即死（requireVocab 列合法值）")
    void unknownMechanicWordRejected() {
        String broken = VALID_SYNERGY.replace(
                "{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 }",
                "{ \"mechanic\": \"NO_SUCH_MECHANIC\" }");
        assertThatThrownBy(() -> load(null, null, broken))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("mechanic")
                .hasMessageContaining("DEATH_TAX");
    }

    @Test
    @DisplayName("skills 新词校验：GAIN_ENERGY 缺 value / KNOCKBACK 小数 / BANISH 带 value 均即死")
    void newSkillEffectWordsValidated() {
        String gainNoValue = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SELF\","
                + " \"effects\": [ { \"effect\": \"GAIN_ENERGY\" } ] }]";
        assertThatThrownBy(() -> load(null, gainNoValue, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("GAIN_ENERGY 必须有 value > 0");

        String knockbackFraction = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"KNOCKBACK\", \"value\": 1.5 } ] }]";
        assertThatThrownBy(() -> load(null, knockbackFraction, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("KNOCKBACK")
                .hasMessageContaining("整数 value ≥ 1");

        String banishWithValue = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"BANISH\", \"value\": 2 } ] }]";
        assertThatThrownBy(() -> load(null, banishWithValue, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("BANISH 不携带 value/status/duration");
    }

    @Test
    @DisplayName("condition 校验：缺 value 即死；value 越界（>1）即死")
    void castConditionValidated() {
        String missingValue = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"BANISH\" } ],"
                + " \"condition\": { \"type\": \"TARGET_HP_BELOW\" } }]";
        assertThatThrownBy(() -> load(null, missingValue, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("condition/value")
                .hasMessageContaining("缺必填字段");

        String outOfRange = "[{ \"id\": \"sk1\", \"name\": \"x\", \"desc\": \"x\", \"shape\": \"SINGLE_TARGET\","
                + " \"effects\": [ { \"effect\": \"BANISH\" } ],"
                + " \"condition\": { \"type\": \"TARGET_HP_BELOW\", \"value\": 1.5 } }]";
        assertThatThrownBy(() -> load(null, outOfRange, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("TARGET_HP_BELOW")
                .hasMessageContaining("0 < value ≤ 1");
    }

    @Test
    @DisplayName("deathrattle 校验：未知 kind 即死；ALLY_REGEN 缺 duration 即死")
    void deathrattleValidated() {
        String unknownKind = VALID_UNIT.replace("\"skillId\": \"sk1\"",
                "\"skillId\": \"sk1\", \"deathrattle\": { \"kind\": \"NOPE\", \"ratio\": 1.0 }");
        assertThatThrownBy(() -> load(unknownKind, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("u1/deathrattle/kind");

        String missingDuration = VALID_UNIT.replace("\"skillId\": \"sk1\"",
                "\"skillId\": \"sk1\", \"deathrattle\": { \"kind\": \"ALLY_REGEN\", \"regenRatio\": 0.02 }");
        assertThatThrownBy(() -> load(missingDuration, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("ALLY_REGEN")
                .hasMessageContaining("duration");
    }

    @Test
    @DisplayName("undying 校验：负数/零即死；reviveHpRatio 越 (0,1] 界即死")
    void undyingValidated() {
        String negative = VALID_UNIT.replace("\"skillId\": \"sk1\"",
                "\"skillId\": \"sk1\", \"undying\": { \"reviveDelaySeconds\": -1.0, \"reviveHpRatio\": 0.4 }");
        assertThatThrownBy(() -> load(negative, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("u1/undying/reviveDelaySeconds")
                .hasMessageContaining("必须 > 0");

        String ratioTooBig = VALID_UNIT.replace("\"skillId\": \"sk1\"",
                "\"skillId\": \"sk1\", \"undying\": { \"reviveDelaySeconds\": 1.0, \"reviveHpRatio\": 1.2 }");
        assertThatThrownBy(() -> load(ratioTooBig, null, null))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("reviveHpRatio")
                .hasMessageContaining("(0, 1]");
    }

    @Test
    @DisplayName("baseStats 暴击两键显式覆写解析（§五.5）：critChance 35 / critDamage 200")
    void critKeysExplicitOverwrite() throws IOException {
        String overwritten = VALID_UNITS.replace(
                "\"range\": 1, \"moveSpeed\": 1.0 }",
                "\"range\": 1, \"moveSpeed\": 1.0, \"critChance\": 35, \"critDamage\": 200 }");
        GameData data = load(overwritten, null, null);
        assertThat(data.getUnit("u1").getBaseStats().getCritChance()).isEqualTo(35);
        assertThat(data.getUnit("u1").getBaseStats().getCritDamage()).isEqualTo(200);
        assertThat(data.getUnit("u2").getBaseStats().getCritChance()).isEqualTo(20); // 缺省（range 3 不匹配替换锚点）
        assertThat(data.getUnit("u2").getBaseStats().getCritDamage()).isEqualTo(150);
    }

    // —— 文件级 ——

    @Test
    @DisplayName("文件不存在：启动即死")
    void missingFileIsFatal() {
        assertThatThrownBy(() -> JsonLoader.load(
                        new FileHandle(tempDir.resolve("nope/units.json").toString()),
                        new FileHandle(tempDir.resolve("nope/skills.json").toString()),
                        new FileHandle(tempDir.resolve("nope/synergies.json").toString()),
                        new FileHandle(tempDir.resolve("nope/scenes.json").toString())))
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
        write(dir, "scenes.json", VALID_SCENES);
        assertThatThrownBy(() -> JsonLoader.loadFromDirectory(new FileHandle(dir.toString())))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("根节点必须是数组");
    }
}
