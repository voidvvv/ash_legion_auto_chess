package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RunState（Q1 减配出生）测试：一局运行态的初始值、受控可变写方法与敌阵视图。
 */
class RunStateTest {

    // —— 名单夹具：最小模板（沿 PlayerTest 先例） ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static RunState newState() {
        return new RunState(42L, "scene_forest", new SequentialIdIssuer());
    }

    @Test
    @DisplayName("初始态：round=1、阶段 SHOPPING、敌阵为空、怜悯计数 0")
    void initialState() {
        RunState state = newState();
        assertThat(state.getRound()).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(GamePhase.SHOPPING);
        assertThat(state.getEnemyWave()).isEmpty();
        assertThat(state.getMercyLossCount()).isZero();
    }

    @Test
    @DisplayName("构造传入的 seed/sceneId/idIssuer 只读可查（idIssuer 同实例持有）")
    void ctorFieldsReadOnly() {
        SequentialIdIssuer issuer = new SequentialIdIssuer();
        RunState state = new RunState(7L, "scene_forest", issuer);
        assertThat(state.getSeed()).isEqualTo(7L);
        assertThat(state.getSceneId()).isEqualTo("scene_forest");
        assertThat(state.getIdIssuer()).isSameAs(issuer);
    }

    @Test
    @DisplayName("setPhase 更新阶段；advanceRound 递增轮次")
    void phaseAndRoundTransitions() {
        RunState state = newState();
        state.setPhase(GamePhase.BATTLE);
        assertThat(state.getPhase()).isEqualTo(GamePhase.BATTLE);
        state.advanceRound();
        assertThat(state.getRound()).isEqualTo(2);
        state.advanceRound();
        assertThat(state.getRound()).isEqualTo(3);
    }

    @Test
    @DisplayName("setMercyLossCount 更新怜悯计数（字段就位，触发逻辑 Phase 5）")
    void mercyLossCountUpdatable() {
        RunState state = newState();
        state.setMercyLossCount(3);
        assertThat(state.getMercyLossCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("setEnemyWave 后敌阵逐位可查（WaveSpec 确定性对拍）")
    void enemyWaveStored() {
        RunState state = newState();
        UnitData t1 = tpl("u1");
        UnitData t2 = tpl("u2");
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        wave.add(new WaveSpec(t1, 1, 1.4f, 2, 1));
        wave.add(new WaveSpec(t2, 1, 1.4f, 3, 0));
        state.setEnemyWave(wave);
        assertThat(state.getEnemyWave()).containsExactly(
                new WaveSpec(t1, 1, 1.4f, 2, 1),
                new WaveSpec(t2, 1, 1.4f, 3, 0));
    }

    @Test
    @DisplayName("getEnemyWave 返回不可变视图（add 抛 UnsupportedOperationException）")
    void enemyWaveUnmodifiable() {
        RunState state = newState();
        state.setEnemyWave(new ArrayList<WaveSpec>());
        assertThatThrownBy(() -> state.getEnemyWave().add(new WaveSpec(tpl("u1"), 1, 1f, 0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("setEnemyWave 防御性拷贝：外部列表后续变更不影响内部")
    void enemyWaveDefensiveCopy() {
        RunState state = newState();
        UnitData t1 = tpl("u1");
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        wave.add(new WaveSpec(t1, 1, 1f, 0, 0));
        state.setEnemyWave(wave);
        wave.add(new WaveSpec(tpl("u2"), 1, 1f, 1, 0));
        assertThat(state.getEnemyWave()).hasSize(1);
    }

    @Test
    @DisplayName("setEnemyWave 拒绝 null")
    void enemyWaveRejectsNull() {
        RunState state = newState();
        assertThatThrownBy(() -> state.setEnemyWave(null))
                .isInstanceOf(NullPointerException.class);
    }
}
