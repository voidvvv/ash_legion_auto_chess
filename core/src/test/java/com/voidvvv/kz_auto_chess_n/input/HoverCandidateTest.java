package com.voidvvv.kz_auto_chess_n.input;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 悬停候选值对象测试（feedback04）：驻留键 kind 互斥（不同种类同源 id 不串键——
 * 悬停状态机按 key 判"候选变化"）、敌方标记来源（虚影/敌侧战斗单位为真，玩家侧为假）、
 * NONE 语义。模板/战斗单位夹具沿 BattleTestFixtures。
 */
class HoverCandidateTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    @Test
    @DisplayName("玩家单位候选：kind=PLAYER_UNIT、敌方标记假、携带模板")
    void playerUnitCandidate() {
        Unit unit = new Unit(7, tpl("u7"), 1);
        HoverCandidate candidate = HoverCandidate.ofPlayerUnit(unit);

        assertThat(candidate.kind()).isEqualTo(HoverCandidate.Kind.PLAYER_UNIT);
        assertThat(candidate.isEnemy()).isFalse();
        assertThat(candidate.template()).isSameAs(unit.getTemplate());
        assertThat(candidate.isNone()).isFalse();
        assertThat(candidate.key()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("敌阵虚影候选：kind=ENEMY_PREVIEW、敌方标记真、携带 WaveSpec 模板")
    void enemyPreviewCandidate() {
        UnitData template = tpl("ghost");
        HoverCandidate candidate = HoverCandidate.ofEnemyPreview(2, template);

        assertThat(candidate.kind()).isEqualTo(HoverCandidate.Kind.ENEMY_PREVIEW);
        assertThat(candidate.isEnemy()).isTrue();
        assertThat(candidate.template()).isSameAs(template);
    }

    @Test
    @DisplayName("战斗单位候选：敌侧真/我侧假（BATTLE 期敌我均可悬停）")
    void battleUnitCandidateSides() {
        HoverCandidate enemy = HoverCandidate.ofBattleUnit(
                BattleTestFixtures.unit(10, Side.ENEMY, tpl("e10"), 2, 1));
        assertThat(enemy.kind()).isEqualTo(HoverCandidate.Kind.BATTLE_UNIT);
        assertThat(enemy.isEnemy()).isTrue();

        HoverCandidate ally = HoverCandidate.ofBattleUnit(
                BattleTestFixtures.unit(11, Side.PLAYER, tpl("a11"), 1, 4));
        assertThat(ally.kind()).isEqualTo(HoverCandidate.Kind.BATTLE_UNIT);
        assertThat(ally.isEnemy()).isFalse();
    }

    @Test
    @DisplayName("驻留键 kind 互斥：三种候选同源 id 互不相等（状态机不误判同一候选）")
    void keysAreDisjointAcrossKinds() {
        HoverCandidate player = HoverCandidate.ofPlayerUnit(new Unit(3, tpl("p3"), 1));
        HoverCandidate preview = HoverCandidate.ofEnemyPreview(3, tpl("g3"));
        HoverCandidate battle = HoverCandidate.ofBattleUnit(
                BattleTestFixtures.unit(3, Side.ENEMY, tpl("b3"), 0, 0));

        assertThat(player.key()).isNotEqualTo(preview.key());
        assertThat(player.key()).isNotEqualTo(battle.key());
        assertThat(preview.key()).isNotEqualTo(battle.key());
    }

    @Test
    @DisplayName("NONE：无候选语义（key<0、isNone 真、模板空）")
    void noneSemantics() {
        assertThat(HoverCandidate.NONE.key()).isLessThan(0);
        assertThat(HoverCandidate.NONE.isNone()).isTrue();
        assertThat(HoverCandidate.NONE.template()).isNull();
    }
}
