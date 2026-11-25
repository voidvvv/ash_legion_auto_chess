package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预判与开局索敌对齐验收件（品质改造 §1.1）：预判目标格 == BattleState 开局锁定目标格
 * （startBattle 的 retargetAll 在 step 前已定，开局位形与预判同源）。覆盖 §1.1 四组站位。
 */
@DisplayName("战前预判与开局索敌对齐（品质改造 §1.1）")
class ThreatAlignmentTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    private static BattleUnit enemyByTemplate(Session session, String unitId) {
        for (BattleUnit unit : session.lastBattle().getUnits()) {
            if (unit.getSide() == Side.ENEMY && unit.getTemplate().getId().equals(unitId)) {
                return unit;
            }
        }
        throw new IllegalStateException("对拍敌阵缺少关键敌人: " + unitId);
    }

    @Test
    @DisplayName("四组站位的预判目标格 == BattleState 开局锁定目标格（A 与 B 两题）")
    void predictedTargetMatchesBattleInitialTargeting() {
        for (PaiDuelDriver.Layout layout : PaiDuelDriver.Layout.values()) {
            Session session = PaiDuelDriver.openLayout(data, layout, 42L);
            List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(
                    data, session.ctx().getRunState().getEnemyWave(), session.ctx().getPlayer());
            assertThat(previews).as("布局 %s 应命中预判", layout).isNotEmpty();

            session.startBattleOnly(); // 开局索敌在 step 前已定（startBattle → retargetAll）
            for (ThreatPredictor.Preview preview : previews) {
                String unitId = preview.getKind() == ThreatPredictor.Kind.BREAK_STOMP
                        ? ThreatPredictor.UNIT_GLACIAL_GIANT : ThreatPredictor.UNIT_SHADOW_BLADE;
                BattleUnit enemy = enemyByTemplate(session, unitId);
                BattleUnit locked = session.lastBattle().getUnitById(enemy.getTargetId());
                assertThat(locked).as("布局 %s 开局已锁定目标", layout).isNotNull();
                assertThat(locked.getGridX()).as("布局 %s 预判 X", layout)
                        .isEqualTo(preview.getTargetGridX());
                assertThat(locked.getGridY()).as("布局 %s 预判 Y", layout)
                        .isEqualTo(preview.getTargetGridY());
            }
        }
    }

    @Test
    @DisplayName("无部署时开局无锁定目标（预判与实战一致地不出目标格）")
    void emptyRosterMatchesNoTargetPrediction() {
        Session session = Session.start(data, PaiTestScript.Topic.A, 42L);
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(
                data, session.ctx().getRunState().getEnemyWave(), session.ctx().getPlayer());
        assertThat(previews.get(0).hasTarget()).isFalse(); // 预判不出目标格

        session.startBattleOnly();
        BattleUnit enemy = enemyByTemplate(session, ThreatPredictor.UNIT_GLACIAL_GIANT);
        assertThat(enemy.getTargetId()).as("开局无候选 → targetId = -1").isEqualTo(-1);
    }
}
