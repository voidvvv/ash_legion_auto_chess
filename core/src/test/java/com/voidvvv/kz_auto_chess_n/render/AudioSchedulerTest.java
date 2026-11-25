package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.ThreatPredictor;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分层音频调度验收件（品质改造 §1.2，假时钟 + 假播放器）：cue 映射与排除项、同 tick 群体合并
 * （2× 帧同理）、普通命中限频（140/240/滚动 1s ≤6）、施法后 100ms 跳过与 60ms 短排期、
 * 同帧命中→击杀 60ms、满通道高优抢占、同一终局不重复、暂停 clear 后无补播、升星收口同帧一次。
 */
@DisplayName("分层音频调度：映射 / 限频 / 合并 / 抢占 / 短排期（品质改造 §1.2）")
class AudioSchedulerTest {

    /** 假播放器：记录 play（cue@音量）与 stop（playId） */
    private static final class FakePlayer implements AudioScheduler.CuePlayer {
        final List<String> plays = new ArrayList<String>();
        final List<Long> stops = new ArrayList<Long>();

        @Override
        public long play(AudioScheduler.Cue cue, float volume) {
            plays.add(cue + "@" + volume);
            return plays.size();
        }

        @Override
        public void stop(long playId) {
            stops.add(playId);
        }
    }

    private static GameData data;

    @BeforeAll
    static void fixture() {
        data = PaiDuelDriver.realData();
    }

    /** 夹具：玩家(1) vs 影刃(2)——事件侧别判定；每用例新建（事件流互不污染） */
    private static BattleState state() {
        return BattleTestFixtures.state(
                BattleTestFixtures.placed(1, Side.PLAYER,
                        data.getUnit(PaiTestScript.W), 2, 4),
                BattleTestFixtures.placed(2, Side.ENEMY,
                        data.getUnit(ThreatPredictor.UNIT_SHADOW_BLADE), 2, 2));
    }

    /** 帧推进（§1.2 时序：先同步战斗引用 → 排期到点 → 本帧事件映射分配） */
    private static void frame(AudioScheduler scheduler, BattleState state, float now) {
        scheduler.syncBattle(state);
        scheduler.advance(now);
        scheduler.acceptFrame(now);
    }

    private static AudioScheduler scheduler(FakePlayer player, BattleState state) {
        AudioScheduler scheduler = new AudioScheduler(player);
        scheduler.syncBattle(state);
        return scheduler;
    }

    // —— 映射与排除项（§1.2 表；放逐/倒地/复活/消散/拦截/0扣血/死因不明/同侧击杀不触发） ——

    @Test
    @DisplayName("映射：终局按结局分胜/败（超时计败）、关键施法/击杀/关键命中成立、排除项全 null")
    void mappingAndExclusions() {
        BattleState state = state();
        assertThat(AudioScheduler.map(CombatEvent.battleEnded(10, BattleOutcome.PLAYER_WIN), state).cue)
                .isEqualTo(AudioScheduler.Cue.VICTORY);
        assertThat(AudioScheduler.map(CombatEvent.battleEnded(10, BattleOutcome.TIMEOUT), state).cue)
                .isEqualTo(AudioScheduler.Cue.DEFEAT);
        assertThat(AudioScheduler.map(CombatEvent.cast(10, 2, 1, ThreatPredictor.SKILL_EXECUTE), state).cue)
                .isEqualTo(AudioScheduler.Cue.CAST);
        assertThat(AudioScheduler.map(CombatEvent.cast(10, 2, 1, "skill_other"), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.unitDied(10, 2, 1), state).cue)
                .isEqualTo(AudioScheduler.Cue.KILL);
        assertThat(AudioScheduler.map(CombatEvent.unitDied(10, 2, -1), state)).isNull(); // 死因不明
        assertThat(AudioScheduler.map(CombatEvent.unitDied(10, 2, 2), state)).isNull();  // 同侧击杀
        assertThat(AudioScheduler.map(
                CombatEvent.hit(10, 2, 1, 8f, false, ThreatPredictor.SKILL_EXECUTE), state).cue)
                .isEqualTo(AudioScheduler.Cue.HIT);
        assertThat(AudioScheduler.map(CombatEvent.hit(10, 2, 1, 0f, false, ThreatPredictor.SKILL_EXECUTE), state))
                .isNull(); // 0 扣血
        assertThat(AudioScheduler.map(CombatEvent.unitDowned(10, 1), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.unitRevived(10, 1), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.unitBanished(10, 1), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.projectileFizzled(10, 2, 1), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.projectileBlocked(10, 2, 1), state)).isNull();
        assertThat(AudioScheduler.map(CombatEvent.attackLaunched(10, 1, 2), state)).isNull();
    }

    @Test
    @DisplayName("分层音量：关键命中 0.58 / 普通命中 0.24（§1.2 表）")
    void hitVolumeLayers() {
        BattleState state = state();
        assertThat(AudioScheduler.map(
                CombatEvent.hit(10, 2, 1, 8f, false, ThreatPredictor.SKILL_EXECUTE), state).volume)
                .isEqualTo(0.58f);
        assertThat(AudioScheduler.map(CombatEvent.hit(10, 2, 1, 8f, false, null), state).volume)
                .isEqualTo(0.24f);
    }

    // —— 同 tick 群体合并（2× 帧同理） ——

    @Test
    @DisplayName("同 tick 群体合并：一帧 3 条同 tick 同源关键命中（AOE 多目标/2× 帧）只播 1 次 0.58")
    void multiTargetKeyHitMergedPerFrame() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        state.record(CombatEvent.hit(100, 2, 1, 6f, false, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(100, 2, 1, 4f, false, ThreatPredictor.SKILL_EXECUTE)); // 同目标重复
        state.record(CombatEvent.hit(100, 2, 2, 5f, false, ThreatPredictor.SKILL_EXECUTE)); // 另一 tick 同帧
        frame(scheduler, state, 5f);
        assertThat(player.plays).hasSize(1);
        assertThat(player.plays.get(0)).isEqualTo("HIT@0.58");
    }

    // —— 普通命中限频（140 / 240 / 滚动 1s ≤6 / 施法后 100ms 跳过） ——

    @Test
    @DisplayName("普通命中限频：140ms 全局间隔与同目标 240ms（§1.2）")
    void normalHitRateLimit() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        // t=0.00：首条播放
        state.record(CombatEvent.hit(10, 2, 1, 3f, false, null));
        frame(scheduler, state, 0.00f);
        assertThat(player.plays).hasSize(1);
        // t=0.10：距上条 0.10 < 0.14 → 限频
        state.record(CombatEvent.hit(20, 2, 2, 3f, false, null));
        frame(scheduler, state, 0.10f);
        assertThat(player.plays).hasSize(1);
        // t=0.15：过了全局间隔、且目标不同 → 播放
        state.record(CombatEvent.hit(30, 2, 2, 3f, false, null));
        frame(scheduler, state, 0.15f);
        assertThat(player.plays).hasSize(2);
        // t=0.20：同目标（t=0.15 命中 2）距 0.05 < 0.24 → 限频
        state.record(CombatEvent.hit(40, 2, 2, 3f, false, null));
        frame(scheduler, state, 0.20f);
        assertThat(player.plays).hasSize(2);
        // t=0.40：同目标已过 0.24 → 播放
        state.record(CombatEvent.hit(50, 2, 2, 3f, false, null));
        frame(scheduler, state, 0.40f);
        assertThat(player.plays).hasSize(3);
    }

    @Test
    @DisplayName("普通命中滚动 1s 上限：1s 内第 7 条限频，窗口滑出后恢复（§1.2 ≤6 次）")
    void normalHitRollingWindow() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        float now = 2f;
        int played = 0;
        for (int i = 0; i < 8; i++) { // 8 条间隔 0.15s（全局间隔满足）
            state.record(CombatEvent.hit((int) (now * 60), 2, 1 + (i % 2), 3f, false, null));
            frame(scheduler, state, now);
            played = player.plays.size();
            now += 0.15f;
        }
        // 第 7 条（t=2.90）落在 1s 窗口内 6 条 → 限频；第 8 条（t=3.05）窗口滑出 → 恢复播放（§1.2）
        assertThat(played).as("滚动 1s ≤6 次 + 窗口滑出恢复").isEqualTo(7);
    }

    @Test
    @DisplayName("施法后 100ms 内普通命中跳过；关键命中排施法后 60ms（§1.2 两种短排期其一）")
    void castThenKeyHitSchedulesAndSkipsNormal() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        // 同帧：施法 + 关键命中 + 普通命中 → 施法立即播、关键命中排 +60ms、普通命中跳过
        state.record(CombatEvent.cast(100, 2, 1, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(100, 2, 1, 8f, false, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(100, 2, 1, 3f, false, null));
        frame(scheduler, state, 5f);
        assertThat(player.plays).containsExactly("CAST@0.68");
        assertThat(scheduler.channelCount()).isEqualTo(2); // 施法（在播）+ 关键命中（排期中）
        // t=5.06：排期到点 → 关键命中播放
        scheduler.syncBattle(state);
        scheduler.advance(5.06f);
        assertThat(player.plays).containsExactly("CAST@0.68", "HIT@0.58");
    }

    @Test
    @DisplayName("同帧命中→击杀：击杀短排期 60ms（命中先、击杀后——§1.2）")
    void killScheduledAfterSameFrameHit() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        state.record(CombatEvent.hit(100, 2, 1, 8f, false, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.unitDied(100, 1, 2));
        frame(scheduler, state, 7f);
        assertThat(player.plays).containsExactly("HIT@0.58"); // 命中先
        scheduler.syncBattle(state);
        scheduler.advance(7.06f);
        assertThat(player.plays).containsExactly("HIT@0.58", "KILL@0.6"); // 击杀后约 60ms
    }

    // —— 通道上限与抢占 ——

    @Test
    @DisplayName("满 3 通道高优抢占：升星(4)抢占最早开始的击杀(3)声道（Sound.stop）")
    void fullChannelsPreemptLowestPriority() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        // 三帧长素材 cue（击杀 0.24s / 施法 0.26s / 升星 0.42s）→ 3 个在播声道
        state.record(CombatEvent.unitDied(10, 2, 1)); // 敌 2 击杀玩家 1（对侧有效）
        frame(scheduler, state, 0f);
        state.record(CombatEvent.cast(20, 2, 1, ThreatPredictor.SKILL_EXECUTE));
        frame(scheduler, state, 0.01f);
        scheduler.acceptUpgrade(0.02f, 2);
        assertThat(player.plays).hasSize(3);
        assertThat(scheduler.channelCount()).isEqualTo(3);
        // 第 4 个请求（升星，优先级 4）→ 声道满 → 抢占最早开始的低优声道（击杀，优先级 3）
        scheduler.acceptUpgrade(0.03f, 3);
        assertThat(player.plays).hasSize(4);
        assertThat(player.stops).hasSize(1); // Sound.stop 一次（被抢占的击杀声道）
        assertThat(scheduler.channelCount()).isEqualTo(3);
    }

    // —— 终局与暂停 ——

    @Test
    @DisplayName("终局：清交战声道、播一次结果声；同一 BattleState 不重复（§1.2）")
    void resultSoundOncePerBattle() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        // 先制造交战声（普通命中）
        state.record(CombatEvent.hit(10, 2, 1, 3f, false, null));
        frame(scheduler, state, 0f);
        assertThat(player.plays).hasSize(1);
        // 终局（玩家胜）：清声道 + 播胜利
        state.record(CombatEvent.battleEnded(200, BattleOutcome.PLAYER_WIN));
        frame(scheduler, state, 3f);
        assertThat(player.plays).containsExactly("HIT@0.24", "VICTORY@0.8");
        assertThat(scheduler.channelCount()).isEqualTo(1); // 只剩结果声
        // 同一 BattleState 再推帧（无新事件）→ 不重复
        frame(scheduler, state, 4f);
        assertThat(player.plays).hasSize(2);
    }

    @Test
    @DisplayName("暂停/离屏 clear：排期丢弃、无补播（§1.2）")
    void clearDropsPendingSchedule() {
        FakePlayer player = new FakePlayer();
        BattleState state = state();
        AudioScheduler scheduler = scheduler(player, state);
        // 施法 + 关键命中同帧 → 关键命中进入 +60ms 排期
        state.record(CombatEvent.cast(100, 2, 1, ThreatPredictor.SKILL_EXECUTE));
        state.record(CombatEvent.hit(100, 2, 1, 8f, false, ThreatPredictor.SKILL_EXECUTE));
        frame(scheduler, state, 5f);
        assertThat(scheduler.channelCount()).isEqualTo(2);
        scheduler.clear(); // 暂停/离屏
        scheduler.syncBattle(state);
        scheduler.advance(9f);
        scheduler.acceptFrame(9f);
        assertThat(player.plays).hasSize(1); // 排期已丢，无补播
        assertThat(scheduler.channelCount()).isZero();
    }

    // —— 升星收口（§1.2：级联合成同帧只播一次、保留最高星级） ——

    @Test
    @DisplayName("ShopSystem 升星收口：级联合成同帧只记一次并保留最高星级，drain 后清空")
    void shopUpgradeFunnelPerFrame() {
        RunContext ctx = com.voidvvv.kz_auto_chess_n.screens.RunAssembler.assembleSampleDuel(
                data, new ShopSystem(), 42L, PaiTestScript.Topic.A);
        ShopSystem shop = ctx.getShop();
        com.voidvvv.kz_auto_chess_n.command.CommandManager commands =
                new com.voidvvv.kz_auto_chess_n.command.CommandManager();
        new com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem().startRun(ctx); // 首轮开局：固定免费五槽落位
        shop.registerHandlers(commands);
        // 首轮免费五槽 = W W M S P：买 0(W)/1(W)/2(M)/3(S)（4 金，余 6）
        for (int slot : Arrays.asList(0, 1, 2, 3)) {
            commands.addCommand(new com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand(slot));
        }
        commands.executeAll(ctx);
        assertThat(ctx.getPlayer().getRosterSize()).isEqualTo(4); // 尚未合成（仅 2 张 W）
        // 付费刷新（2 金，余 4）→ ① W L M P S；买 0(W)（1 金，余 3）→ 第三张 W 触发 3 合 1
        commands.addCommand(com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand.INSTANCE);
        commands.executeAll(ctx);
        commands.addCommand(new com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand(0));
        commands.executeAll(ctx);
        assertThat(ctx.getPlayer().getRosterSize()).isEqualTo(3); // 3 合 1 已折叠（W 二星 + M + S）
        assertThat(shop.drainFrameUpgradeStar()).as("本帧升星（保留最高星级）").isEqualTo(2);
        assertThat(shop.drainFrameUpgradeStar()).as("drain 后清空").isEqualTo(-1);
    }
}
