package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 羁绊结算测试（battle §三双通道 + data_schema §六替换制档位）：
 * RACE 按 race、CLASS 按 unitClass 各自计数；仅匹配已登记 synergy.key 的值计数（风味值不计）；
 * 达档取该档全量效果（不与低档叠加）；stat 通道进修正块、effect 通道进开局效果列表。
 */
class SynergySystemTest {

    private static final SynergySystem SYSTEM = new SynergySystem();

    // —— 夹具：兽人(RACE)/战士(CLASS) 双羁绊 + 6 档含 effect 通道 ——

    private static UnitData unit(String id, String race, String unitClass) {
        return new UnitData(id, "夹具" + id, race, unitClass, 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static SynergyData.Threshold threshold(int count, EffectData... effects) {
        return new SynergyData.Threshold(count, Arrays.asList(effects));
    }

    private static EffectData stat(StatKey key, EffectOp op, float value) {
        return new EffectData(key, null, op, value, EffectTarget.ALLIES);
    }

    private static EffectData effect(StatusType type, float value) {
        return new EffectData(null, type, null, value, EffectTarget.ALLIES);
    }

    private static GameData data() {
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_orc", new SynergyData("syn_orc", "兽人", SynergySource.RACE, "兽人",
                Arrays.asList(
                        threshold(2, stat(StatKey.HP, EffectOp.ADD, 150f)),
                        threshold(4, stat(StatKey.HP, EffectOp.ADD, 400f), stat(StatKey.ATTACK, EffectOp.PCT, 20f)),
                        threshold(6, effect(StatusType.SHIELD, 0.3f), stat(StatKey.LIFESTEAL, EffectOp.ADD, 20f)))));
        synergies.put("syn_warrior", new SynergyData("syn_warrior", "战士", SynergySource.CLASS, "战士",
                Arrays.asList(
                        threshold(2, stat(StatKey.ARMOR, EffectOp.ADD, 20f)),
                        threshold(4, stat(StatKey.ARMOR, EffectOp.ADD, 50f), stat(StatKey.ATTACK, EffectOp.PCT, 15f)),
                        threshold(6, stat(StatKey.ARMOR, EffectOp.ADD, 100f)))));
        synergies.put("syn_beast", new SynergyData("syn_beast", "野兽", SynergySource.RACE, "野兽",
                Arrays.asList(
                        threshold(2, stat(StatKey.ATTACK_SPEED, EffectOp.PCT, 15f)))));
        return new GameData(new LinkedHashMap<String, UnitData>(), new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                synergies, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());
    }

    @Test
    @DisplayName("双通道计数：一个兽人战士在种族与职业羁绊各计 1 次（2 兽人战士 → 双双达 2 档）")
    void dualChannelCounting() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(
                unit("a", "兽人", "战士"), unit("b", "兽人", "战士")), data());
        assertThat(snapshot.getActives()).extracting(SynergySnapshot.ActiveSynergy::getSynergyId)
                .containsExactly("syn_orc", "syn_warrior"); // GameData 声明序
        assertThat(snapshot.modifiers().addOf(StatKey.HP)).isEqualTo(150f);   // 兽人 2 档
        assertThat(snapshot.modifiers().addOf(StatKey.ARMOR)).isEqualTo(20f); // 战士 2 档
    }

    @Test
    @DisplayName("同名重复单位各计 1（有放回抽取口径）：6 个同模板 → 兽人 6 档（敌方侧同一套语义）")
    void duplicateNamesEachCountOnce() {
        List<UnitData> six = new ArrayList<UnitData>();
        for (int i = 0; i < 6; i++) {
            six.add(unit("e" + i, "兽人", "游侠")); // 游侠职业无羁绊登记
        }
        SynergySnapshot snapshot = SYSTEM.resolve(six, data());
        assertThat(snapshot.getActives()).hasSize(1);
        assertThat(snapshot.getOpeningEffects()).hasSize(1); // 兽人 6 档 SHIELD 进 effect 通道
        assertThat(snapshot.getOpeningEffects().get(0).getEffect()).isEqualTo(StatusType.SHIELD);
        assertThat(snapshot.getOpeningEffects().get(0).getValue()).isEqualTo(0.3f);
    }

    @Test
    @DisplayName("风味值不计：race/class 无对应羁绊登记的值不产生计数")
    void flavorValuesDoNotCount() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(
                unit("a", "植物", "德鲁伊")), data());
        assertThat(snapshot).isSameAs(SynergySnapshot.EMPTY);
    }

    @Test
    @DisplayName("未达最低档为空：1 个兽人 → EMPTY")
    void belowLowestThresholdIsEmpty() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(unit("a", "兽人", "战士")), data());
        assertThat(snapshot).isSameAs(SynergySnapshot.EMPTY);
        assertThat(snapshot.getActives()).isEmpty();
        assertThat(snapshot.getOpeningEffects()).isEmpty();
        assertThat(snapshot.modifiers().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("档位替换制：4 兽人取 4 档全量（hp+400/atk PCT20），不含 2 档的 hp+150")
    void replacementTakesHighestTierOnly() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(
                unit("a", "兽人", "法师"), unit("b", "兽人", "法师"),
                unit("c", "兽人", "法师"), unit("d", "兽人", "法师")), data());
        assertThat(snapshot.modifiers().addOf(StatKey.HP)).isEqualTo(400f);
        assertThat(snapshot.modifiers().pctOf(StatKey.ATTACK)).isEqualTo(20f);
    }

    @Test
    @DisplayName("stat 通道跨羁绊求和：兽人 4 档 + 战士 4 档 → PCT 攻击 20+15")
    void statChannelSumsAcrossSynergies() {
        List<UnitData> four = new ArrayList<UnitData>();
        for (int i = 0; i < 4; i++) {
            four.add(unit("w" + i, "兽人", "战士"));
        }
        SynergySnapshot snapshot = SYSTEM.resolve(four, data());
        assertThat(snapshot.modifiers().pctOf(StatKey.ATTACK)).isEqualTo(35f); // 20 + 15
        assertThat(snapshot.getActives()).hasSize(2);
    }

    @Test
    @DisplayName("actives 含档位信息：synergyId / name / thresholdCount")
    void activesCarryTierInfo() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(
                unit("a", "兽人", "战士"), unit("b", "兽人", "战士")), data());
        SynergySnapshot.ActiveSynergy orc = snapshot.getActives().get(0);
        assertThat(orc.getSynergyId()).isEqualTo("syn_orc");
        assertThat(orc.getName()).isEqualTo("兽人");
        assertThat(orc.getThresholdCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("快照不可变：actives 与 openingEffects 视图拒绝修改")
    void snapshotIsImmutable() {
        SynergySnapshot snapshot = SYSTEM.resolve(Arrays.asList(
                unit("a", "兽人", "战士"), unit("b", "兽人", "战士")), data());
        assertThatThrownBy(() -> snapshot.getActives().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.getOpeningEffects().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("EMPTY 常量：空 actives / 空修正 / 空开局效果，且 isEmpty")
    void emptyConstant() {
        assertThat(SynergySnapshot.EMPTY.getActives()).isEmpty();
        assertThat(SynergySnapshot.EMPTY.getOpeningEffects()).isEmpty();
        assertThat(SynergySnapshot.EMPTY.modifiers()).isEqualTo(StatModifierBlock.empty());
    }

    @Test
    @DisplayName("参数防御：null 集合 / null GameData 抛 NullPointerException")
    void rejectsNullArguments() {
        assertThatThrownBy(() -> SYSTEM.resolve(null, data()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SYSTEM.resolve(new ArrayList<UnitData>(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // —— 增幅重载（Phase 6 CP10，裁决 D12：「荆语」当档全效果 ×1.25） ——

    @Test
    @DisplayName("2 参重载回归锚：与 3 参空映射输出全等（actives/修正块/开局效果）")
    void twoArgOverloadMatchesEmptyAmp() {
        List<UnitData> four = new ArrayList<UnitData>();
        for (int i = 0; i < 4; i++) {
            four.add(unit("w" + i, "兽人", "战士"));
        }
        SynergySnapshot legacy = SYSTEM.resolve(four, data());
        SynergySnapshot withEmptyAmp = SYSTEM.resolve(four, data(),
                new java.util.LinkedHashMap<String, Float>());
        assertThat(withEmptyAmp.getActives()).hasSameSizeAs(legacy.getActives());
        assertThat(withEmptyAmp.modifiers().addOf(StatKey.HP)).isEqualTo(legacy.modifiers().addOf(StatKey.HP));
        assertThat(withEmptyAmp.modifiers().pctOf(StatKey.ATTACK)).isEqualTo(legacy.modifiers().pctOf(StatKey.ATTACK));
        assertThat(withEmptyAmp.getOpeningEffects()).hasSameSizeAs(legacy.getOpeningEffects());
    }

    @Test
    @DisplayName("增幅三通道：ADD 四舍五入（150→188）/ PCT 浮点（20→25、15→18.75）/ effect 浮点（SHIELD 0.3→0.375）")
    void ampScalesAllChannels() {
        java.util.Map<String, Float> amp = new java.util.LinkedHashMap<String, Float>();
        amp.put("syn_orc", 0.25f);

        // 兽人 (2) 档：hp ADD 150 → Math.round(150×1.25)=188
        SynergySnapshot two = SYSTEM.resolve(Arrays.asList(
                unit("a", "兽人", "法师"), unit("b", "兽人", "法师")), data(), amp);
        assertThat(two.modifiers().addOf(StatKey.HP)).isEqualTo(188f);

        // 兽人 (4) 档：hp ADD 400 → 500；attack PCT 20 → 25
        List<UnitData> four = new ArrayList<UnitData>();
        for (int i = 0; i < 4; i++) {
            four.add(unit("o" + i, "兽人", "法师"));
        }
        SynergySnapshot fourSnapshot = SYSTEM.resolve(four, data(), amp);
        assertThat(fourSnapshot.modifiers().addOf(StatKey.HP)).isEqualTo(500f);
        assertThat(fourSnapshot.modifiers().pctOf(StatKey.ATTACK)).isEqualTo(25f);

        // 兽人 (6) 档：SHIELD 0.3 → 0.375（effect 通道浮点）；lifesteal ADD 20 → 25
        List<UnitData> six = new ArrayList<UnitData>();
        for (int i = 0; i < 6; i++) {
            six.add(unit("s" + i, "兽人", "游侠"));
        }
        SynergySnapshot sixSnapshot = SYSTEM.resolve(six, data(), amp);
        assertThat(sixSnapshot.getOpeningEffects().get(0).getValue()).isEqualTo(0.375f);
        assertThat(sixSnapshot.modifiers().addOf(StatKey.LIFESTEAL)).isEqualTo(25f);

        // 野兽 (2) 档：attackSpeed PCT 15 → 18.75（「荆语」主目标形态）
        java.util.Map<String, Float> beastAmp = new java.util.LinkedHashMap<String, Float>();
        beastAmp.put("syn_beast", 0.25f);
        SynergySnapshot beasts = SYSTEM.resolve(Arrays.asList(
                unit("b1", "野兽", "刺客"), unit("b2", "野兽", "刺客")), data(), beastAmp);
        assertThat(beasts.modifiers().pctOf(StatKey.ATTACK_SPEED)).isEqualTo(18.75f);
    }

    @Test
    @DisplayName("未列入 amp 的羁绊零影响（战士档原值）；空阵容仍 EMPTY")
    void unlistedSynergyUnaffected() {
        java.util.Map<String, Float> amp = new java.util.LinkedHashMap<String, Float>();
        amp.put("syn_beast", 0.25f); // 只增幅野兽，兽人/战士不受影响
        List<UnitData> four = new ArrayList<UnitData>();
        for (int i = 0; i < 4; i++) {
            four.add(unit("w" + i, "兽人", "战士"));
        }
        SynergySnapshot snapshot = SYSTEM.resolve(four, data(), amp);
        assertThat(snapshot.modifiers().addOf(StatKey.HP)).isEqualTo(400f); // 兽人 (4) 原值
        assertThat(snapshot.modifiers().addOf(StatKey.ARMOR)).isEqualTo(50f); // 战士 (4) 原值

        assertThat(SYSTEM.resolve(new ArrayList<UnitData>(), data(), amp))
                .isSameAs(SynergySnapshot.EMPTY);
    }
}
