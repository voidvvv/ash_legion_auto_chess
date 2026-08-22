package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
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
 * equipments.json 解析与校验用例（Phase 5 CP3；data_schema §八 结构锁定）：
 * fail-fast 负向 + 兼容重载空表 + 稀有度空池软告警 + 真实种子加载。
 * 夹具 @TempDir 现写（沿 JsonLoaderValidationTest 先例）。
 */
class JsonLoaderEquipmentsTest {

    @TempDir
    Path tempDir;

    // —— 最小合法基础集（units/skills/synergies/scenes 与 ValidationTest 同款）——

    private static final String VALID_UNITS =
            "[{ \"id\": \"u1\", \"name\": \"试作兵\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 1,"
            + " \"baseStats\": { \"hp\": 100, \"attack\": 10, \"armor\": 5, \"attackSpeed\": 1.0,"
            + " \"range\": 1, \"moveSpeed\": 1.0 }, \"skillId\": \"sk1\" },"
            + bossUnit("b1") + ", " + bossUnit("b2") + ", " + bossUnit("b3") + "]";

    private static final String VALID_SKILL =
            "[{ \"id\": \"sk1\", \"name\": \"试作技\", \"desc\": \"测试\", \"shape\": \"SINGLE_TARGET\","
            + " \"effects\": [ { \"effect\": \"DAMAGE\", \"value\": 2.0 } ] }]";

    private static final String VALID_SYNERGY =
            "[{ \"id\": \"syn1\", \"name\": \"兽人\", \"desc\": \"试作\", \"source\": \"RACE\", \"key\": \"兽人\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 10 } ] } ] },"
            + "{ \"id\": \"syn2\", \"name\": \"战士\", \"desc\": \"试作\", \"source\": \"CLASS\", \"key\": \"战士\","
            + " \"thresholds\": [ { \"count\": 2, \"effects\": [ { \"stat\": \"armor\", \"op\": \"ADD\", \"value\": 10 } ] } ] }]";

    private static final String VALID_SCENES =
            "[{ \"id\": \"sc1\", \"name\": \"试作场景\", \"unlockAfter\": null,"
            + " \"enemyPool\": [ { \"unitId\": \"u1\", \"weight\": 3, \"minRound\": 1 } ],"
            + " \"bosses\": { \"7\": \"b1\", \"15\": \"b2\", \"25\": \"b3\" } }]";

    private static String bossUnit(String id) {
        return "{ \"id\": \"" + id + "\", \"name\": \"试作Boss" + id + "\", \"race\": \"兽人\", \"class\": \"战士\", \"cost\": 0,"
                + " \"baseStats\": { \"hp\": 200, \"attack\": 20, \"armor\": 5, \"attackSpeed\": 1.0,"
                + " \"range\": 1, \"moveSpeed\": 1.0 }, \"skillId\": \"sk1\", \"boss\": true }";
    }

    /** 三稀有度 + 三槽各至少一件（避免稀有度空池告警） */
    private static final String VALID_EQUIPMENTS = "["
            + eq("eq_w1", "白武", "WEAPON", "WHITE",
                    "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]", null) + ", "
            + eq("eq_a1", "成甲", "ARMOR", "RARE",
                    "[{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 200 }]", null) + ", "
            + eq("eq_t1", "传饰", "TRINKET", "LEGENDARY",
                    "[{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 400 }]",
                    "{ \"type\": \"REGEN\", \"power\": 0.02, \"tick\": 5 }") + "]";

    private static String eq(String id, String name, String slot, String rarity,
                             String effects, String passiveStatus) {
        return "{ \"id\": \"" + id + "\", \"name\": \"" + name + "\", \"slot\": \"" + slot
                + "\", \"rarity\": \"" + rarity + "\", \"effects\": " + effects
                + (passiveStatus == null ? "" : ", \"passiveStatus\": " + passiveStatus) + " }";
    }

    /** 写全套最小合法数据（equipments 用给定内容），返回 GameData 或抛 DataValidationException */
    private GameData loadEquipments(String equipments) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        writeBase(dir);
        write(dir, "equipments.json", equipments);
        return JsonLoader.loadFromDirectory(new FileHandle(dir.toString()));
    }

    private static void writeBase(Path dir) throws IOException {
        write(dir, "units.json", VALID_UNITS);
        write(dir, "skills.json", VALID_SKILL);
        write(dir, "synergies.json", VALID_SYNERGY);
        write(dir, "scenes.json", VALID_SCENES);
        write(dir, "heroes.json", "[]"); // Phase 6 起 loadFromDirectory 增读 heroes.json
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    // —— 正向解析 ——

    @Test
    @DisplayName("全字段解析：词表/效果条目保序/passive 透传，零软告警")
    void parsesAllFields() throws IOException {
        GameData data = loadEquipments(VALID_EQUIPMENTS);
        assertThat(data.getEquipments()).hasSize(3);
        assertThat(data.getEquipments().keySet()).containsExactly("eq_w1", "eq_a1", "eq_t1");

        EquipmentData weapon = data.getEquipment("eq_w1");
        assertThat(weapon.getName()).isEqualTo("白武");
        assertThat(weapon.getSlot()).isEqualTo(EquipmentSlot.WEAPON);
        assertThat(weapon.getRarity()).isEqualTo(EquipmentRarity.WHITE);
        assertThat(weapon.getEffects()).hasSize(1);
        assertThat(weapon.getEffects().get(0).getStat().jsonName()).isEqualTo("attack");
        assertThat(weapon.getEffects().get(0).getOp().jsonName()).isEqualTo("PCT");
        assertThat(weapon.getEffects().get(0).getValue()).isEqualTo(20f);
        assertThat(weapon.getPassive()).isNull();

        EquipmentData legendary = data.getEquipment("eq_t1");
        assertThat(legendary.getPassive().getType()).isEqualTo(StatusType.REGEN);
        assertThat(legendary.getPassive().getPower()).isEqualTo(0.02f);
        assertThat(legendary.getPassive().getTickInterval()).isEqualTo(5f);

        assertThat(data.getWarnings()).isEmpty(); // 三稀有度池全非空 → 无装备类软告警
    }

    @Test
    @DisplayName("兼容重载：4 参 load 与显式 null 装备文件均为空表且零告警")
    void nullEquipmentsFileYieldsEmptyTable() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        writeBase(dir);
        FileHandle u = new FileHandle(dir.resolve("units.json").toString());
        FileHandle s = new FileHandle(dir.resolve("skills.json").toString());
        FileHandle sy = new FileHandle(dir.resolve("synergies.json").toString());
        FileHandle sc = new FileHandle(dir.resolve("scenes.json").toString());

        GameData byFourArg = JsonLoader.load(u, s, sy, sc);
        assertThat(byFourArg.getEquipments()).isEmpty();
        assertThat(byFourArg.getWarnings()).isEmpty();

        GameData byNull = JsonLoader.load(u, s, sy, sc, null);
        assertThat(byNull.getEquipments()).isEmpty();
    }

    @Test
    @DisplayName("loadFromDirectory 缺 equipments.json：启动即死（生产路径必存在）")
    void missingEquipmentsFileIsFatal() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        writeBase(dir);
        assertThatThrownBy(() -> JsonLoader.loadFromDirectory(new FileHandle(dir.toString())))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("文件不存在")
                .hasMessageContaining("equipments.json");
    }

    // —— 软告警 ——

    @Test
    @DisplayName("稀有度空池软告警：只有白装 → RARE/LEGENDARY 两条告警进 getWarnings()")
    void warnsOnEmptyRarityPools() throws IOException {
        GameData data = loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]", null) + "]");
        assertThat(data.getEquipments()).hasSize(1);
        assertThat(data.getWarnings()).hasSize(2);
        assertThat(data.getWarnings()).anyMatch(w -> w.contains("RARE"));
        assertThat(data.getWarnings()).anyMatch(w -> w.contains("LEGENDARY"));
    }

    // —— fail-fast 负向 ——

    @Test
    @DisplayName("未知字段报错：条目级与效果条目级")
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]", null)
                .replaceFirst("\\}$", ", \"extra\": 1 }") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("extra")
                .hasMessageContaining("未知字段");
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20, \"dur\": 1 }]", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("dur")
                .hasMessageContaining("未知字段");
    }

    @Test
    @DisplayName("passiveStatus 非 REGEN 报错（实现口径 #7：本期仅允许 REGEN）")
    void rejectsNonRegenPassive() {
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]",
                "{ \"type\": \"BLEED\", \"power\": 5, \"tick\": 1 }") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("REGEN");
    }

    @Test
    @DisplayName("passiveStatus 数值域：power ≤ 0 与 tick ≤ 0 均报错")
    void rejectsNonPositivePassiveValues() {
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]",
                "{ \"type\": \"REGEN\", \"power\": 0, \"tick\": 5 }") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("power");
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]",
                "{ \"type\": \"REGEN\", \"power\": 0.02, \"tick\": 0 }") + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("tick");
    }

    @Test
    @DisplayName("id 重复报错")
    void rejectsDuplicateIds() {
        String one = eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"attack\", \"op\": \"PCT\", \"value\": 20 }]", null);
        assertThatThrownBy(() -> loadEquipments("[" + one + ", " + one + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("重复声明");
    }

    @Test
    @DisplayName("效果条数域：空数组与超 MAX_EFFECTS_PER_SKILL(3) 条均报错")
    void rejectsEffectCountOutOfRange() {
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE", "[]", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("非空数组");
        assertThatThrownBy(() -> loadEquipments("[" + eq("eq_w1", "白武", "WEAPON", "WHITE",
                "[{ \"stat\": \"hp\", \"op\": \"ADD\", \"value\": 1 },"
                + " { \"stat\": \"attack\", \"op\": \"ADD\", \"value\": 1 },"
                + " { \"stat\": \"armor\", \"op\": \"ADD\", \"value\": 1 },"
                + " { \"stat\": \"attackSpeed\", \"op\": \"PCT\", \"value\": 1 }]", null) + "]"))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("至多");
    }

    // —— 真实种子（CP4：equipments.json 可加载且零软告警）——

    @Test
    @DisplayName("真实种子：8 件装备、三稀有度池全非空、零装备类软告警")
    void realSeedLoadsCleanly() {
        GameData data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
        assertThat(data.getEquipments()).hasSize(8);
        for (EquipmentRarity rarity : EquipmentRarity.values()) {
            long count = 0;
            for (EquipmentData equipment : data.getEquipments().values()) {
                if (equipment.getRarity() == rarity) {
                    count++;
                }
            }
            assertThat(count).as("稀有度 %s 池非空", rarity.jsonName()).isPositive();
        }
        EquipmentData dragonHeart = data.getEquipment("eq_dragon_heart");
        assertThat(dragonHeart).isNotNull();
        assertThat(dragonHeart.getSlot()).isEqualTo(EquipmentSlot.ARMOR);
        assertThat(dragonHeart.getPassive().getType()).isEqualTo(StatusType.REGEN);
        assertThat(data.getWarnings()).noneMatch(w -> w.contains("装备稀有度池"));
    }
}
