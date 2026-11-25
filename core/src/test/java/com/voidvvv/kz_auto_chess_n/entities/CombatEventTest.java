package com.voidvvv.kz_auto_chess_n.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 战斗事件值对象测试（CP13）：killer 字段 equals/hashCode 对拍——确定性对拍口径维持
 * （同 seed 事件流逐位比较），事件流不落盘无存档兼容面。
 */
class CombatEventTest {

    @Test
    @DisplayName("unitDied 带击杀归因：killerId 进 equals/hashCode（同 killer 相等、异 killer 不等）")
    void unitDiedKillerFieldInEquality() {
        CombatEvent a = CombatEvent.unitDied(5, 3, 7);
        CombatEvent same = CombatEvent.unitDied(5, 3, 7);
        CombatEvent otherKiller = CombatEvent.unitDied(5, 3, 9);

        assertThat(a).isEqualTo(same);
        assertThat(a.hashCode()).isEqualTo(same.hashCode());
        assertThat(a).isNotEqualTo(otherKiller);

        assertThat(a.getSourceId()).isEqualTo(3); // 亡者 id（source 位）
        assertThat(a.getTargetId()).isEqualTo(-1);
        assertThat(a.getKillerId()).isEqualTo(7);
    }

    @Test
    @DisplayName("unitDied 旧工厂委托 killer=-1：与全参工厂 killer=-1 逐位相等（存量对拍不破）")
    void legacyUnitDiedFactoryDelegatesMinusOne() {
        assertThat(CombatEvent.unitDied(5, 3)).isEqualTo(CombatEvent.unitDied(5, 3, -1));
        assertThat(CombatEvent.unitDied(5, 3).getKillerId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("非 UNIT_DIED 事件 killer 恒 -1（旧 10 字段工厂全部走委托路径）")
    void otherFactoriesCarryDefaultKiller() {
        assertThat(CombatEvent.hit(1, 2, 3, 10f, false, null).getKillerId()).isEqualTo(-1);
        assertThat(CombatEvent.unitBanished(1, 4).getKillerId()).isEqualTo(-1);
        assertThat(CombatEvent.projectileFizzled(1, 2, 3).getKillerId()).isEqualTo(-1);
    }
}
