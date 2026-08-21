package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 属性管线测试（battle §8.1/§8.2 + 口径 #8 映射表）：
 * 第一级基准 = (模板 × 星级 × scale + ΣADD) × (1 + ΣPCT/100)，
 * 第二级有效 = (基准 + Σ状态ADD) × (1 + Σ状态PCT/100)；PCT 以百分点存储、结算 ÷100。
 */
class StatPipelineTest {

    /** 基准夹具模板：hp100 / atk10 / armor5 / aspd1.0 / range3 / ms1.0 / ls0 / egr100 / sp0 */
    private static UnitData tpl() {
        return new UnitData("u", "夹具兵", "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 3, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static StatModifierSource source(final StatModifierBlock block) {
        return new StatModifierSource() {
            @Override
            public StatModifierBlock modifiers() {
                return block;
            }
        };
    }

    private static ActiveStatus status(StatusType type, float power) {
        return new ActiveStatus(type, 99, power, 5f);
    }

    // —— 第一级：raw = 模板 × 星级倍率 × scale ——

    @Test
    @DisplayName("raw 抽检：1 星 ×scale1.0 各键直通（hp100/atk10/armor5/aspd1/range3）")
    void rawPassThroughAtStarOne() {
        BattleStats stats = StatPipeline.deriveBaseline(tpl(), 1, 1.0f, Collections.<StatModifierSource>emptyList());
        assertThat(stats.get(StatKey.HP)).isEqualTo(100f);
        assertThat(stats.get(StatKey.ATTACK)).isEqualTo(10f);
        assertThat(stats.get(StatKey.ARMOR)).isEqualTo(5f);
        assertThat(stats.get(StatKey.ATTACK_SPEED)).isEqualTo(1f);
        assertThat(stats.get(StatKey.RANGE)).isEqualTo(3f);
    }

    @Test
    @DisplayName("星级倍率：2 星 ×1.8、3 星 ×3.24（upgradeMultiplier=1.8）")
    void starMultiplierScalesRaw() {
        assertThat(StatPipeline.deriveBaseline(tpl(), 2, 1f, Collections.<StatModifierSource>emptyList())
                .get(StatKey.ATTACK)).isCloseTo(18f, within(1e-6f));
        assertThat(StatPipeline.deriveBaseline(tpl(), 3, 1f, Collections.<StatModifierSource>emptyList())
                .get(StatKey.ATTACK)).isCloseTo(32.4f, within(1e-4f));
    }

    @Test
    @DisplayName("scale 抽检：k=1.4 → atk14、k=3.4 → atk34、Boss scale=1.0 → atk10")
    void enemyScaleScalesRaw() {
        assertThat(StatPipeline.deriveBaseline(tpl(), 1, 1.4f, Collections.<StatModifierSource>emptyList())
                .get(StatKey.ATTACK)).isCloseTo(14f, within(1e-6f));
        assertThat(StatPipeline.deriveBaseline(tpl(), 1, 3.4f, Collections.<StatModifierSource>emptyList())
                .get(StatKey.ATTACK)).isCloseTo(34f, within(1e-6f));
        assertThat(StatPipeline.deriveBaseline(tpl(), 1, 1.0f, Collections.<StatModifierSource>emptyList())
                .get(StatKey.ATTACK)).isCloseTo(10f, within(1e-6f));
    }

    @Test
    @DisplayName("先加后乘合成序：atk10 + ADD20 + PCT30 → (10+20)×1.3 = 39（先乘后加会得 33）")
    void addBeforeMultiplyOrder() {
        StatModifierSource syn = source(StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, 20f));
        BattleStats stats = StatPipeline.deriveBaseline(tpl(), 1, 1f, Collections.singletonList(syn));
        // 再叠一个 PCT 源验证多源求和后统一乘
        StatModifierBlock combined = StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, 20f)
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT, 30f));
        BattleStats both = StatPipeline.deriveBaseline(tpl(), 1, 1f, Collections.singletonList(source(combined)));
        assertThat(both.get(StatKey.ATTACK)).isCloseTo(39f, within(1e-6f));
        assertThat(stats.get(StatKey.ATTACK)).isCloseTo(30f, within(1e-6f));
    }

    @Test
    @DisplayName("多修正源合并：羁绊源 + 模拟装备源各自 ADD/PCT 求和（Q4 零改插入验证）")
    void multipleSourcesSumIndependently() {
        StatModifierSource synergy = source(StatModifierBlock.of(StatKey.HP, EffectOp.ADD, 150f)
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT, 20f)));
        StatModifierSource equipment = source(StatModifierBlock.of(StatKey.HP, EffectOp.ADD, 50f)
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, 5f)));
        BattleStats stats = StatPipeline.deriveBaseline(tpl(), 1, 1f, Arrays.asList(synergy, equipment));
        // HP = (100 + 150 + 50) × 1.0 = 300；ATK = (10 + 5) × 1.2 = 18
        assertThat(stats.get(StatKey.HP)).isCloseTo(300f, within(1e-6f));
        assertThat(stats.get(StatKey.ATTACK)).isCloseTo(18f, within(1e-6f));
    }

    @Test
    @DisplayName("HP 键作用于 maxHp（基准块 hp 即战斗 maxHp 来源）")
    void hpKeyDerivesMaxHp() {
        StatModifierSource orc = source(StatModifierBlock.of(StatKey.HP, EffectOp.ADD, 150f));
        BattleStats stats = StatPipeline.deriveBaseline(tpl(), 1, 1f, Collections.singletonList(orc));
        assertThat(stats.get(StatKey.HP)).isCloseTo(250f, within(1e-6f));
    }

    // —— 第二级：有效属性 ——

    @Test
    @DisplayName("第二级公式：基准 atk10 + ATK_UP30 → (10+0)×1.3 = 13")
    void effectiveAppliesStatusModifiers() {
        BattleStats base = StatPipeline.deriveBaseline(tpl(), 1, 1f, Collections.<StatModifierSource>emptyList());
        StatModifierBlock mods = StatPipeline.statusModifiers(
                Collections.singletonList(status(StatusType.ATK_UP, 30f)));
        BattleStats effective = StatPipeline.deriveEffective(base, mods);
        assertThat(effective.get(StatKey.ATTACK)).isCloseTo(13f, within(1e-6f));
        assertThat(effective.get(StatKey.HP)).isEqualTo(base.get(StatKey.HP)); // 未触碰键不变
    }

    @Test
    @DisplayName("empty 修正块恒等：deriveEffective(base, empty) 与 base 逐键相等")
    void emptyBlockIsIdentity() {
        BattleStats base = StatPipeline.deriveBaseline(tpl(), 1, 1f, Collections.<StatModifierSource>emptyList());
        BattleStats effective = StatPipeline.deriveEffective(base, StatModifierBlock.empty());
        assertThat(effective).isEqualTo(base);
        assertThat(StatModifierBlock.empty().isEmpty()).isTrue();
        assertThat(StatModifierBlock.empty().addOf(StatKey.ATTACK)).isEqualTo(0f);
        assertThat(StatModifierBlock.empty().pctOf(StatKey.ATTACK)).isEqualTo(0f);
    }

    @Test
    @DisplayName("9 键齐全：get 覆盖全部 StatKey 且互不串键")
    void allNineKeysReachable() {
        BattleStats stats = new BattleStats(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f);
        assertThat(stats.get(StatKey.HP)).isEqualTo(1f);
        assertThat(stats.get(StatKey.ATTACK)).isEqualTo(2f);
        assertThat(stats.get(StatKey.ARMOR)).isEqualTo(3f);
        assertThat(stats.get(StatKey.ATTACK_SPEED)).isEqualTo(4f);
        assertThat(stats.get(StatKey.RANGE)).isEqualTo(5f);
        assertThat(stats.get(StatKey.MOVE_SPEED)).isEqualTo(6f);
        assertThat(stats.get(StatKey.LIFESTEAL)).isEqualTo(7f);
        assertThat(stats.get(StatKey.ENERGY_GAIN_RATE)).isEqualTo(8f);
        assertThat(stats.get(StatKey.SKILL_POWER)).isEqualTo(9f);
    }

    // —— 口径 #8：StatusType → StatKey 映射表 ——

    @Test
    @DisplayName("ATK_UP → attack PCT +v；ATK_DOWN → attack PCT −v")
    void atkUpDownMapToAttackPct() {
        StatModifierBlock up = StatPipeline.statusModifiers(
                Collections.singletonList(status(StatusType.ATK_UP, 30f)));
        assertThat(up.pctOf(StatKey.ATTACK)).isEqualTo(30f);
        assertThat(up.addOf(StatKey.ATTACK)).isEqualTo(0f);

        StatModifierBlock down = StatPipeline.statusModifiers(
                Collections.singletonList(status(StatusType.ATK_DOWN, 25f)));
        assertThat(down.pctOf(StatKey.ATTACK)).isEqualTo(-25f);
    }

    @Test
    @DisplayName("ASPD_UP → attackSpeed PCT +v；SLOW → moveSpeed PCT −v")
    void aspdUpAndSlowMapCorrectly() {
        StatModifierBlock aspd = StatPipeline.statusModifiers(
                Collections.singletonList(status(StatusType.ASPD_UP, 30f)));
        assertThat(aspd.pctOf(StatKey.ATTACK_SPEED)).isEqualTo(30f);

        StatModifierBlock slow = StatPipeline.statusModifiers(
                Collections.singletonList(status(StatusType.SLOW, 40f)));
        assertThat(slow.pctOf(StatKey.MOVE_SPEED)).isEqualTo(-40f);
    }

    @Test
    @DisplayName("非属性类（STUN/BLEED/POISON/REGEN/SHIELD）不产生属性修正")
    void nonAttributeTypesProduceNothing() {
        List<ActiveStatus> statuses = new ArrayList<ActiveStatus>();
        for (StatusType type : new StatusType[]{StatusType.STUN, StatusType.BLEED, StatusType.POISON,
                StatusType.REGEN, StatusType.SHIELD}) {
            statuses.add(status(type, 50f));
        }
        assertThat(StatPipeline.statusModifiers(statuses).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("同 type 双状态不叠加修正：power 取大者（口径 #11 对称延伸）")
    void sameTypeStatusesTakeMaxPower() {
        List<ActiveStatus> statuses = Arrays.asList(
                status(StatusType.ATK_UP, 20f), status(StatusType.ATK_UP, 35f));
        assertThat(StatPipeline.statusModifiers(statuses).pctOf(StatKey.ATTACK)).isEqualTo(35f);
    }

    @Test
    @DisplayName("StatModifierBlock.of 拒绝未知运算符域外的 null 键")
    void blockRejectsNullArguments() {
        assertThatThrownBy(() -> StatModifierBlock.of(null, EffectOp.ADD, 1f))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StatModifierBlock.of(StatKey.ATTACK, null, 1f))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("plus 返回新对象（不可变）：原块不变、ADD/PCT 分别累计")
    void plusIsImmutableAndAccumulates() {
        StatModifierBlock a = StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, 10f);
        StatModifierBlock b = StatModifierBlock.of(StatKey.ATTACK, EffectOp.ADD, 5f)
                .plus(StatModifierBlock.of(StatKey.ATTACK, EffectOp.PCT, 15f));
        StatModifierBlock sum = a.plus(b);
        assertThat(a.addOf(StatKey.ATTACK)).isEqualTo(10f); // 原块不变
        assertThat(sum.addOf(StatKey.ATTACK)).isEqualTo(15f);
        assertThat(sum.pctOf(StatKey.ATTACK)).isEqualTo(15f);
        assertThat(sum.isEmpty()).isFalse();
    }
}
