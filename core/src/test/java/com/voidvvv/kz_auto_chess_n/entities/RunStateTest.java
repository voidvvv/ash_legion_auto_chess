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

    // —— 系统反应通知行（CP13 切片提前落地：T2 经营系统 CP10~CP12 依赖 addNotice） ——

    @Test
    @DisplayName("addNotice 追加通知行；drainNotices 取走全部并清空（二次 drain 为空）")
    void noticeAddAndDrain() {
        RunState state = newState();
        state.addNotice("第一行");
        state.addNotice("第二行");
        assertThat(state.drainNotices()).containsExactly("第一行", "第二行");
        assertThat(state.drainNotices()).isEmpty();
    }

    @Test
    @DisplayName("addNotice 忽略 null 与空白行")
    void noticeIgnoresBlankLines() {
        RunState state = newState();
        state.addNotice(null);
        state.addNotice("   ");
        assertThat(state.drainNotices()).isEmpty();
    }

    @Test
    @DisplayName("addNotice 有界 32：超限 FIFO 丢最老")
    void noticeBoundedAt32Fifo() {
        RunState state = newState();
        for (int i = 0; i < 40; i++) {
            state.addNotice("行" + i);
        }
        List<String> drained = state.drainNotices();
        assertThat(drained).hasSize(32);
        assertThat(drained.get(0)).isEqualTo("行8");
        assertThat(drained.get(31)).isEqualTo("行39");
    }

    // —— CP13：流程域字段（防重入 / 宝箱 / 终局成因 / 怜悯金 / 逻辑钟 / 熟练度） ——

    @Test
    @DisplayName("runStarted 初始 false；markRunStarted 一次性置位（重开 = 新鲜 RunState 复位）")
    void runStartedFlag() {
        RunState state = newState();
        assertThat(state.isRunStarted()).isFalse();
        state.markRunStarted();
        assertThat(state.isRunStarted()).isTrue();
    }

    @Test
    @DisplayName("pendingChest 默认 null；setPendingChest 置换可查（领取后置回 null）")
    void pendingChestReplaceable() {
        RunState state = newState();
        assertThat(state.getPendingChest()).isNull();
        ChestOffer offer = new ChestOffer(1, false, java.util.Arrays.asList(
                ChestOption.gold(3), ChestOption.expBook(4), ChestOption.gold(3)));
        state.setPendingChest(offer);
        assertThat(state.getPendingChest()).isSameAs(offer);
        state.setPendingChest(null);
        assertThat(state.getPendingChest()).isNull();
    }

    @Test
    @DisplayName("endCause 默认 null（RUN_END 期非空）；setEndCause 拒绝 null")
    void endCauseNullableUntilEnd() {
        RunState state = newState();
        assertThat(state.getEndCause()).isNull();
        state.setEndCause(RunEndCause.COMPLETED);
        assertThat(state.getEndCause()).isEqualTo(RunEndCause.COMPLETED);
        state.setEndCause(RunEndCause.ABANDONED);
        assertThat(state.getEndCause()).isEqualTo(RunEndCause.ABANDONED);
        assertThatThrownBy(() -> state.setEndCause(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mercyGoldThisRound / masteryAwarded 默认 0；写方法可更新可查")
    void mercyGoldAndMasteryAwardedUpdatable() {
        RunState state = newState();
        assertThat(state.getMercyGoldThisRound()).isZero();
        assertThat(state.getMasteryAwarded()).isZero();
        state.setMercyGoldThisRound(3);
        state.setMasteryAwarded(75);
        assertThat(state.getMercyGoldThisRound()).isEqualTo(3);
        assertThat(state.getMasteryAwarded()).isEqualTo(75);
    }

    @Test
    @DisplayName("logicTick 初始 0；advanceTick 递增（CommandManager.executeAll 尾调用——唯一推进点）")
    void logicTickAdvances() {
        RunState state = newState();
        assertThat(state.getLogicTick()).isZero();
        state.advanceTick();
        state.advanceTick();
        assertThat(state.getLogicTick()).isEqualTo(2);
    }
}
