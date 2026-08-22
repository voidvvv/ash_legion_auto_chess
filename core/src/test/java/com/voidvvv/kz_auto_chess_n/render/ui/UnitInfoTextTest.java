package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 棋子文案格式化纯函数测试（Phase 5.1 CP1）：词表 / 档位数值行 / 羁绊查表 /
 * 悬停预览与详情行集 / 折行与卡高截断——零 Gdx 依赖，headless 直测。
 *
 * <p>夹具沿 {@link UnitDetailDialogTest} 手搓 GameData 先例；羁绊用带 desc 的 6 参构造
 * （裁决 2：synergy 级 desc 手写 + 档位行由 thresholds/effects 生成，混合口径）。
 */
class UnitInfoTextTest {

    private static final String ORC_DESC = "焰痕部族的雇佣兵，越战越硬的正面铁壁";
    private static final String WARRIOR_DESC = "执旗卫队的老兵，以纪律与护甲结阵而战";

    // —— 夹具（口径对齐 UnitDetailDialogTest） ——

    private static UnitData tpl(String id, String race, String unitClass) {
        return new UnitData(id, "夹具" + id, race, unitClass, 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static SkillData skill(String name, String desc) {
        return new SkillData("sk_u1", name, desc, SkillShape.SELF, Delivery.MELEE_INSTANT,
                Collections.<SkillEffect>emptyList());
    }

    /** 兽人（RACE）：2/4/6 档含 SHIELD effect 通道（镜像 syn_orc 真实结构） */
    private static SynergyData orcSynergy() {
        List<SynergyData.Threshold> tiers = Arrays.asList(
                new SynergyData.Threshold(2, Collections.singletonList(
                        new EffectData(StatKey.HP, null, EffectOp.ADD, 150f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(4, Arrays.asList(
                        new EffectData(StatKey.HP, null, EffectOp.ADD, 400f, EffectTarget.ALLIES),
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 20f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(6, Arrays.asList(
                        new EffectData(null, StatusType.SHIELD, null, 0.3f, EffectTarget.ALLIES),
                        new EffectData(StatKey.LIFESTEAL, null, EffectOp.ADD, 20f, EffectTarget.ALLIES))));
        return new SynergyData("syn_orc", "兽人", ORC_DESC, SynergySource.RACE, "兽人", tiers);
    }

    /** 战士（CLASS）：2/4/6 档全 stat 通道（镜像 syn_warrior 真实结构） */
    private static SynergyData warriorSynergy() {
        List<SynergyData.Threshold> tiers = Arrays.asList(
                new SynergyData.Threshold(2, Collections.singletonList(
                        new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 20f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(4, Arrays.asList(
                        new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 50f, EffectTarget.ALLIES),
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 15f, EffectTarget.ALLIES))),
                new SynergyData.Threshold(6, Arrays.asList(
                        new EffectData(StatKey.ARMOR, null, EffectOp.ADD, 100f, EffectTarget.ALLIES),
                        new EffectData(StatKey.ATTACK, null, EffectOp.PCT, 30f, EffectTarget.ALLIES),
                        new EffectData(StatKey.LIFESTEAL, null, EffectOp.ADD, 20f, EffectTarget.ALLIES))));
        return new SynergyData("syn_warrior", "战士", WARRIOR_DESC, SynergySource.CLASS, "战士", tiers);
    }

    private static GameData dataWith(SkillData skill) {
        LinkedHashMap<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put(skill.getId(), skill);
        LinkedHashMap<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_orc", orcSynergy());
        synergies.put("syn_warrior", warriorSynergy());
        return new GameData(new LinkedHashMap<String, UnitData>(), skills, synergies,
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
    }

    private static EffectData effect(StatKey stat, EffectOp op, float value) {
        return new EffectData(stat, null, op, value, EffectTarget.ALLIES);
    }

    private static EffectData shield(float value) {
        return new EffectData(null, StatusType.SHIELD, null, value, EffectTarget.ALLIES);
    }

    // —— 词表（§2.1 属性短名，9 键全断言） ——

    @Test
    @DisplayName("属性短名词表：9 键与 GDD 术语一一对应")
    void statLabelAllNineKeys() {
        assertThat(UnitInfoText.statLabel(StatKey.HP)).isEqualTo("生命");
        assertThat(UnitInfoText.statLabel(StatKey.ATTACK)).isEqualTo("攻击");
        assertThat(UnitInfoText.statLabel(StatKey.ARMOR)).isEqualTo("护甲");
        assertThat(UnitInfoText.statLabel(StatKey.ATTACK_SPEED)).isEqualTo("攻速");
        assertThat(UnitInfoText.statLabel(StatKey.MOVE_SPEED)).isEqualTo("移速");
        assertThat(UnitInfoText.statLabel(StatKey.RANGE)).isEqualTo("射程");
        assertThat(UnitInfoText.statLabel(StatKey.LIFESTEAL)).isEqualTo("吸血");
        assertThat(UnitInfoText.statLabel(StatKey.ENERGY_GAIN_RATE)).isEqualTo("回能");
        assertThat(UnitInfoText.statLabel(StatKey.SKILL_POWER)).isEqualTo("技能强度");
    }

    // —— 档位效果数值文案 ——

    @Test
    @DisplayName("单条效果文案：ADD 整数 / PCT / 百分比刻度 ADD 附 % / 浮点去尾 / SHIELD")
    void effectTextCoversChannels() {
        assertThat(UnitInfoText.effectText(effect(StatKey.HP, EffectOp.ADD, 150f))).isEqualTo("生命+150");
        assertThat(UnitInfoText.effectText(effect(StatKey.ATTACK, EffectOp.PCT, 20f))).isEqualTo("攻击+20%");
        assertThat(UnitInfoText.effectText(effect(StatKey.LIFESTEAL, EffectOp.ADD, 20f))).isEqualTo("吸血+20%");
        assertThat(UnitInfoText.effectText(effect(StatKey.MOVE_SPEED, EffectOp.ADD, 1.0f))).isEqualTo("移速+1");
        assertThat(UnitInfoText.effectText(shield(0.3f))).isEqualTo("护盾30%");
    }

    @Test
    @DisplayName("羁绊档位行：档间分号、同档效果间中点、结构化生成（R2 混合口径）")
    void synergyTierLineStructuredGeneration() {
        assertThat(UnitInfoText.synergyTierLine(warriorSynergy()))
                .isEqualTo("2:护甲+20；4:护甲+50·攻击+15%；6:护甲+100·攻击+30%·吸血+20%");
        assertThat(UnitInfoText.synergyTierLine(orcSynergy()))
                .isEqualTo("2:生命+150；4:生命+400·攻击+20%；6:护盾30%·吸血+20%");
    }

    // —— 羁绊查表 ——

    @Test
    @DisplayName("按 source+key 查羁绊：RACE/CLASS 命中，风味种族返回 null（不显示）")
    void findSynergyBySourceAndKey() {
        GameData data = dataWith(skill("战吼", "号令全军，攻击提升"));
        assertThat(UnitInfoText.findSynergy(data, SynergySource.RACE, "兽人"))
                .isSameAs(data.getSynergy("syn_orc"));
        assertThat(UnitInfoText.findSynergy(data, SynergySource.CLASS, "战士"))
                .isSameAs(data.getSynergy("syn_warrior"));
        assertThat(UnitInfoText.findSynergy(data, SynergySource.RACE, "暗夜")).isNull();
        assertThat(UnitInfoText.findSynergy(data, SynergySource.CLASS, "游侠")).isNull();
    }

    // —— 悬停预览行集（模板级；棋盘卡 maxColumns=7） ——

    @Test
    @DisplayName("预览行集：名/费阶首行、属性 3 行、技能名 + desc 折行、双羁绊块，每行不超列宽")
    void previewLinesTemplateLevel() {
        GameData data = dataWith(skill("战吼", "号令全军，全军攻击力提升，持续整场")); // 17 全角字
        List<String> lines = UnitInfoText.previewLines(tpl("u1", "兽人", "战士"), data, 7);

        assertThat(lines.get(0)).isEqualTo("夹具u1 1费");
        assertThat(lines).contains("生命100 攻击10", "护甲5 攻速1", "射程1 移速1");
        assertThat(lines).contains("技能 战吼");
        // 17 字 desc → 7/7/3 三行
        assertThat(lines).containsSubsequence("号令全军，全军", "攻击力提升，持", "续整场");
        // 双羁绊块（名 + desc + 档位行）；desc 折行后无分隔拼接仍完整
        assertThat(lines).contains("羁绊 兽人", "羁绊 战士");
        assertThat(String.join("", lines)).contains(ORC_DESC, WARRIOR_DESC);
        // 每行（含硬断的档位行）列宽 ≤ 7
        assertThat(lines).allSatisfy(line ->
                assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(7f));
    }

    @Test
    @DisplayName("预览行集：风味种族无羁绊块（data_schema §六 V1.3），职业块照常")
    void previewLinesSkipsFlavorRace() {
        GameData data = dataWith(skill("战吼", "号令全军，攻击提升"));
        List<String> lines = UnitInfoText.previewLines(tpl("u9", "暗夜", "战士"), data, 7);

        assertThat(lines).noneMatch(line -> line.contains("暗夜"));
        assertThat(lines).noneMatch(line -> line.contains("羁绊 兽人"));
        assertThat(lines).contains("羁绊 战士");
    }

    // —— 详情行集（实例级：星 / 累计花费） ——

    @Test
    @DisplayName("详情行集：标题（名/星/费/累计花费）、中文属性两行、技能行、两条羁绊 desc + 档位行")
    void detailLinesInstanceLevel() {
        GameData data = dataWith(skill("战吼", "号令全军，攻击提升"));
        Unit unit = new Unit(7, tpl("u1", "兽人", "战士"), 2, 33);

        List<String> lines = UnitInfoText.detailLines(unit, data);

        assertThat(lines.get(0)).isEqualTo("夹具u1 2星（1费）· 累计花费 33");
        assertThat(lines.get(1)).isEqualTo("生命 100   攻击 10   护甲 5");
        assertThat(lines.get(2)).isEqualTo("攻速 1   射程 1   移速 1");
        assertThat(lines).contains("技能 战吼：号令全军，攻击提升");
        assertThat(lines).contains("羁绊 兽人：" + ORC_DESC, "羁绊 战士：" + WARRIOR_DESC);
        // 羁绊行与档位行成对（档位行原样不折——弹窗 380px 宽）
        assertThat(lines).contains(UnitInfoText.synergyTierLine(orcSynergy()),
                UnitInfoText.synergyTierLine(warriorSynergy()));
    }

    // —— 折行与截断（纯函数） ——

    @Test
    @DisplayName("折行：全角 1 列 / 半角 0.5 列、空格断点贪心、单 token 超宽硬断、maxColumns≤0 原样")
    void wrapGreedyAndHardSplit() {
        assertThat(UnitInfoText.columns("生命")).isEqualTo(2f);
        assertThat(UnitInfoText.columns("ab")).isEqualTo(1f);
        // 中英混排：空格断点贪心（生命 攻击 = 4.5 列，+armor 超 5 → 折）
        assertThat(UnitInfoText.wrap("生命 攻击 armor", 5)).containsExactly("生命 攻击", "armor");
        // 单 token 超行宽：字符硬断（abcdefg = 3.5 列 > 3）
        assertThat(UnitInfoText.wrap("abcdefg", 3)).containsExactly("abcdef", "g");
        // maxColumns ≤ 0 或整行已容纳：原样单行
        assertThat(UnitInfoText.wrap("生命 攻击", 0)).containsExactly("生命 攻击");
        assertThat(UnitInfoText.wrap("生命", 7)).containsExactly("生命");
        assertThat(UnitInfoText.wrap(null, 7)).containsExactly("");
    }

    @Test
    @DisplayName("卡高截断：未超容量原样返回，超容丢弃末尾并以 … 示意")
    void clipLinesToCapacity() {
        List<String> five = Arrays.asList("一", "二", "三", "四", "五");
        assertThat(UnitInfoText.clipLines(five, 5)).isSameAs(five);
        assertThat(UnitInfoText.clipLines(five, 0)).isSameAs(five);
        assertThat(UnitInfoText.clipLines(five, 3)).containsExactly("一", "二", "…");
        assertThat(UnitInfoText.clipLines(five, 1)).containsExactly("一", "…"); // 至少保留 1 行实文
    }
}
