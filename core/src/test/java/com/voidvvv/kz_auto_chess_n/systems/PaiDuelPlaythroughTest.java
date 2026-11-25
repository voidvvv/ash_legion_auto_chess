package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelRecorder;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelRecorder.BattleRecord;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelRecorder.RunRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无头全流程试玩验收件（品质改造批6）：A/B × 种子 42/43/44 各跑完整三战（§0.2 流程：
 * 买棋 → 开战 → 胜箱 → 第 2/3 战 → 对拍终局），全程走真实系统链路、<b>零注入胜局</b>。
 * 逐战断言记录字段齐备并输出记录表（System.out——收口文档 stage1_closure.md 直贴）；
 * 音频触发序按未乘倍速的真实帧时钟对齐（逐逻辑 tick 喂 AudioScheduler）。
 */
@DisplayName("无头全流程试玩：A/B × 三种子完整三战（品质改造批6）")
class PaiDuelPlaythroughTest {

    private static final long[] SEEDS = {42L, 43L, 44L};

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    @Test
    @DisplayName("A 破阵踏 · 三种子：完整三战达对拍终局，逐战记录字段齐备")
    void topicAPlaythrough() {
        runAndAssert(PaiTestScript.Topic.A);
    }

    @Test
    @DisplayName("B 处决 · 三种子：完整三战达对拍终局，逐战记录字段齐备")
    void topicBPlaythrough() {
        runAndAssert(PaiTestScript.Topic.B);
    }

    private static void runAndAssert(PaiTestScript.Topic topic) {
        for (long seed : SEEDS) {
            RunRecord run = PaiDuelRecorder.playThrough(data, topic, seed);
            System.out.print(PaiDuelRecorder.format(run)); // 记录表（收口文档直贴）

            assertThat(run.reachedRunEnd)
                    .as("%s seed %d 走到对拍终局（RUN_END）", topic, seed).isTrue();
            assertThat(run.battles).as("%s seed %d 三战记录", topic, seed).hasSize(3);
            for (BattleRecord record : run.battles) {
                String where = topic + " seed " + seed + " 第 " + record.round + " 战";
                assertThat(record.deployment).as(where + " 初始阵位").isNotEmpty();
                assertThat(record.shopSlots).as(where + " 商店槽序").hasSize(5);
                assertThat(record.outcome).as(where + " 结局")
                        .isEqualTo(com.voidvvv.kz_auto_chess_n.entities.BattleOutcome.PLAYER_WIN);
                assertThat(record.enemyAlive).as(where + " 敌方清场").isZero();
                assertThat(record.playerAlive).as(where + " 我方存活（§1.1 探测表）").isGreaterThan(0);
                assertThat(record.attribution).as(where + " 归因句").isNotBlank();
                assertThat(record.roundStartGold - record.battleStartGold)
                        .as(where + " 净支出 ≥ 0").isGreaterThanOrEqualTo(0);
            }
            // §1.2：第一战有关键施法触发；末战有胜利结果声（对齐顺序由记录序保证）
            assertThat(String.join(" ", run.battles.get(0).audioCues))
                    .as("%s seed %d 首战关键施法声", topic, seed).contains("CAST@");
            assertThat(String.join(" ", run.battles.get(2).audioCues))
                    .as("%s seed %d 终局结果声", topic, seed).contains("VICTORY@");
        }
    }

    @Test
    @DisplayName("整局确定性：同题同种子两次完整试玩，逐战记录逐字段一致（§0.2 收口复核）")
    void sameTopicSameSeedRunsIdentical() {
        RunRecord first = PaiDuelRecorder.playThrough(data, PaiTestScript.Topic.A, 42L);
        RunRecord second = PaiDuelRecorder.playThrough(data, PaiTestScript.Topic.A, 42L);
        assertThat(second.reachedRunEnd).isEqualTo(first.reachedRunEnd);
        assertThat(second.battles).hasSameSizeAs(first.battles);
        for (int i = 0; i < first.battles.size(); i++) {
            BattleRecord a = first.battles.get(i);
            BattleRecord b = second.battles.get(i);
            assertThat(b.deployment).as("第 %d 战阵位一致", a.round).isEqualTo(a.deployment);
            assertThat(b.shopSlots).as("第 %d 战槽序一致", a.round).isEqualTo(a.shopSlots);
            assertThat(b.firstCastTick).as("第 %d 战首施法 tick 一致", a.round).isEqualTo(a.firstCastTick);
            assertThat(b.firstHitSubject).as("第 %d 战首命中对象一致", a.round).isEqualTo(a.firstHitSubject);
            assertThat(b.outcome).as("第 %d 战结局一致", a.round).isEqualTo(a.outcome);
            assertThat(b.playerAlive).as("第 %d 战存活一致", a.round).isEqualTo(a.playerAlive);
            assertThat(b.attribution).as("第 %d 战归因一致", a.round).isEqualTo(a.attribution);
            assertThat(b.audioCues).as("第 %d 战音频触发序一致", a.round).isEqualTo(a.audioCues);
        }
        assertThat(second.finalGold).isEqualTo(first.finalGold);
        assertThat(second.finalPlayerLevel).isEqualTo(first.finalPlayerLevel);
    }
}
