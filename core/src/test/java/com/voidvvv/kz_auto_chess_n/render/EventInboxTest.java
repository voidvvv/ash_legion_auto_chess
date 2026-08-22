package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventInbox 测试：cursor 游标消费（差异声明 #2）——首批全量、无新零回调、仅新段、不重播、detach 静默。
 */
class EventInboxTest {

    private static BattleState emptyState() {
        return new BattleState(new ArrayList<>(), new RandomGenerator(42L),
                SynergySnapshot.EMPTY, SynergySnapshot.EMPTY);
    }

    @Test
    @DisplayName("attach 后首批全量送达")
    void deliversAllOnFirstDrain() {
        BattleState state = emptyState();
        state.record(CombatEvent.unitDied(1, 7));
        state.record(CombatEvent.battleEnded(2, BattleOutcome.PLAYER_WIN));
        EventInbox inbox = new EventInbox();
        inbox.attach(state);
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(2);
    }

    @Test
    @DisplayName("无新事件零回调")
    void noNewEventsNoCallback() {
        BattleState state = emptyState();
        state.record(CombatEvent.unitDied(1, 7));
        EventInbox inbox = new EventInbox();
        inbox.attach(state);
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("追加后仅新段送达")
    void deliversOnlyNewSegmentAfterAppend() {
        BattleState state = emptyState();
        state.record(CombatEvent.unitDied(1, 7));
        EventInbox inbox = new EventInbox();
        inbox.attach(state);
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        state.record(CombatEvent.unitDied(3, 9));
        state.record(CombatEvent.unitDied(4, 11));
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(3);
        assertThat(received.get(1).getTick()).isEqualTo(3);
        assertThat(received.get(2).getTick()).isEqualTo(4);
    }

    @Test
    @DisplayName("cursor 不回退：同事件不重播")
    void cursorNeverRewinds() {
        BattleState state = emptyState();
        state.record(CombatEvent.unitDied(1, 7));
        EventInbox inbox = new EventInbox();
        inbox.attach(state);
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(1);
        state.record(CombatEvent.unitDied(2, 8));
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(2);
        assertThat(received.get(1).getSourceId()).isEqualTo(8); // 第二次只送达新事件
    }

    @Test
    @DisplayName("detach 后零回调（不炸）")
    void detachedDeliversNothing() {
        BattleState state = emptyState();
        state.record(CombatEvent.unitDied(1, 7));
        EventInbox inbox = new EventInbox();
        inbox.attach(state);
        inbox.detach();
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("重新 attach 重置 cursor：新战斗从头消费")
    void reattachResetsCursor() {
        BattleState first = emptyState();
        first.record(CombatEvent.unitDied(1, 7));
        EventInbox inbox = new EventInbox();
        inbox.attach(first);
        List<CombatEvent> received = new ArrayList<CombatEvent>();
        inbox.forEachNew(received::add);
        BattleState second = emptyState();
        second.record(CombatEvent.unitDied(5, 21));
        inbox.attach(second);
        inbox.forEachNew(received::add);
        assertThat(received).hasSize(2);
        assertThat(received.get(1).getSourceId()).isEqualTo(21);
    }
}
