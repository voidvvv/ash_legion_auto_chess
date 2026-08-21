package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 战斗实例 BattleUnit 测试（battle §8.2 两级属性 + 受控可变纪律）：
 * 身份与基准不可变；有效属性脏标记重算；HP/能量钳制；清扫语义。
 */
class BattleUnitTest {

    private static UnitData tpl() {
        return new UnitData("u", "夹具兵", "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk", false);
    }

    private static SkillData skill() {
        return new SkillData("sk", "夹具技", "", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                Collections.singletonList(new SkillEffect(SkillEffectType.DAMAGE, 2f, null, null)));
    }

    private static BattleStats stats() {
        return new BattleStats(100f, 10f, 5f, 1f, 1f, 1f, 0f, 100f, 0f);
    }

    private static BattleUnit unit() {
        return new BattleUnit(1, tpl(), 1, Side.PLAYER, skill(), stats());
    }

    @Test
    @DisplayName("身份与基准不可变：字段只读，开局 HP=maxHp、能量 0")
    void identityAndBaseline() {
        BattleUnit unit = unit();
        assertThat(unit.getId()).isEqualTo(1);
        assertThat(unit.getSide()).isEqualTo(Side.PLAYER);
        assertThat(unit.getStar()).isEqualTo(1);
        assertThat(unit.getSkill().getId()).isEqualTo("sk");
        assertThat(unit.getBaseStats().get(StatKey.HP)).isEqualTo(100f);
        assertThat(unit.getCurrentHp()).isEqualTo(100f);
        assertThat(unit.getEnergy()).isEqualTo(0f);
        assertThat(unit.isAlive()).isTrue();
        assertThat(unit.isCleaned()).isFalse();
    }

    @Test
    @DisplayName("getEffective 脏标记重算：挂 ATK_UP 前后攻击变化，移除后恢复")
    void effectiveStatsRecomputeOnStatusChange() {
        BattleUnit unit = unit();
        assertThat(unit.getEffective(StatKey.ATTACK)).isEqualTo(10f);
        ActiveStatus buff = new ActiveStatus(StatusType.ATK_UP, 9, 30f, 5f);
        unit.addStatus(buff);
        assertThat(unit.getEffective(StatKey.ATTACK)).isCloseTo(13f, within(1e-6f)); // (10)×1.3
        unit.removeStatus(buff);
        assertThat(unit.getEffective(StatKey.ATTACK)).isEqualTo(10f);
    }

    @Test
    @DisplayName("hasControl：STUN 在身为 true，无状态为 false")
    void hasControlWithStun() {
        BattleUnit unit = unit();
        assertThat(unit.hasControl()).isFalse();
        unit.addStatus(new ActiveStatus(StatusType.STUN, 9, 0f, 2f));
        assertThat(unit.hasControl()).isTrue();
    }

    @Test
    @DisplayName("isAlive 语义：HP≤0 未清扫仍算活（H 语义延迟清扫），markCleaned 后为 false")
    void aliveUntilCleaned() {
        BattleUnit unit = unit();
        unit.modifyHp(-999f);
        assertThat(unit.getCurrentHp()).isEqualTo(0f);
        assertThat(unit.isAlive()).isTrue(); // 濒死未清扫
        unit.markCleaned();
        assertThat(unit.isAlive()).isFalse();
        assertThat(unit.isCleaned()).isTrue();
    }

    @Test
    @DisplayName("markCleaned 后状态清空、目标失效")
    void markCleanedClearsStatuses() {
        BattleUnit unit = unit();
        unit.addStatus(new ActiveStatus(StatusType.ATK_UP, 9, 30f, 5f));
        unit.setTargetId(5);
        unit.markCleaned();
        assertThat(unit.getStatuses()).isEmpty();
        assertThat(unit.getTargetId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("modifyHp 双向钳制：不透 0、不溢 maxHp")
    void modifyHpClamps() {
        BattleUnit unit = unit();
        unit.modifyHp(50f);
        assertThat(unit.getCurrentHp()).isEqualTo(100f); // 溢出作废
        unit.modifyHp(-150f);
        assertThat(unit.getCurrentHp()).isEqualTo(0f);
    }

    @Test
    @DisplayName("能量封顶 100（口径 #5）：setEnergy/modifyEnergy 均不溢")
    void energyClampsAtHundred() {
        BattleUnit unit = unit();
        unit.modifyEnergy(120f);
        assertThat(unit.getEnergy()).isEqualTo(100f);
        unit.setEnergy(150f);
        assertThat(unit.getEnergy()).isEqualTo(100f);
        unit.setEnergy(30f);
        unit.modifyEnergy(-50f);
        assertThat(unit.getEnergy()).isEqualTo(0f);
    }

    @Test
    @DisplayName("attackInterval/moveCooldown 换算：aspd2 → 0.5s/击；ms2 → 0.5s/格")
    void intervalConversions() {
        BattleUnit fast = new BattleUnit(2, tpl(), 1, Side.ENEMY, skill(),
                new BattleStats(100f, 10f, 5f, 2f, 1f, 2f, 0f, 100f, 0f));
        assertThat(fast.attackInterval()).isCloseTo(0.5f, within(1e-6f));
        assertThat(fast.moveCooldown()).isCloseTo(0.5f, within(1e-6f));
        assertThat(unit().attackInterval()).isCloseTo(1f, within(1e-6f));
    }

    @Test
    @DisplayName("开局即就绪（口径 #4）：计时器初始已满，本 tick 即可出手/走步")
    void timersStartReady() {
        BattleUnit unit = unit();
        assertThat(unit.canActOnAttackTimer()).isTrue();
        assertThat(unit.canActOnMoveTimer()).isTrue();
    }

    @Test
    @DisplayName("getStatuses 返回不可变视图")
    void statusesViewUnmodifiable() {
        BattleUnit unit = unit();
        unit.addStatus(new ActiveStatus(StatusType.SLOW, 9, 30f, 5f));
        assertThatThrownBy(() -> unit.getStatuses().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
