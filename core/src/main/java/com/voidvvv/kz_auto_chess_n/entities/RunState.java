package com.voidvvv.kz_auto_chess_n.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一局运行态（architecture §2.3；Q1 减配出生）。
 *
 * <p>受控可变（沿 {@link BattleUnit} 的 framework-internal 纪律）：写方法仅供
 * RunFlowSystem / 命令 handler 在局内调用，渲染层只读。
 * 发号器归本类持有（口径 #2），跨场持续递增不重置——玩家名单与战斗实例同一 id 空间。
 *
 * <p>敌阵 {@code enemyWave} 为轮开始事件产物（口径 #3）：beginRound 时生成、轮内固定；
 * 商店/怜悯等经济态推 Phase 5（字段 {@code mercyLossCount} 先建好，触发逻辑后接）。
 */
public final class RunState {
    private final long seed;
    private final String sceneId;
    private final IdIssuer idIssuer;
    private int round = 1;
    private GamePhase phase = GamePhase.SHOPPING;
    private int mercyLossCount;
    private List<WaveSpec> enemyWave = Collections.emptyList();
    /** 系统反应通知行（有界 32，UI drain 后清空——实现口径 #13 第三流；CP13 切片提前落地供 T2 经营系统调用） */
    private final List<String> notices = new ArrayList<String>();

    public RunState(long seed, String sceneId, IdIssuer idIssuer) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.idIssuer = Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
    }

    public long getSeed() { return seed; }
    public String getSceneId() { return sceneId; }
    public IdIssuer getIdIssuer() { return idIssuer; }
    public int getRound() { return round; }
    public GamePhase getPhase() { return phase; }
    public int getMercyLossCount() { return mercyLossCount; }
    public List<WaveSpec> getEnemyWave() { return enemyWave; }

    // —— framework-internal 写方法 ——

    public void setPhase(GamePhase phase) {
        this.phase = Objects.requireNonNull(phase, "phase 不能为 null");
    }

    public void advanceRound() {
        round++;
    }

    public void setMercyLossCount(int count) {
        this.mercyLossCount = count;
    }

    /** 轮开始事件产物落位（防御性拷贝 + 不可变视图） */
    public void setEnemyWave(List<WaveSpec> wave) {
        Objects.requireNonNull(wave, "wave 不能为 null");
        this.enemyWave = Collections.unmodifiableList(new ArrayList<WaveSpec>(wave));
    }

    /** 系统反应通知行（有界 32，FIFO 丢最老；null/空串忽略——实现口径 #13） */
    public void addNotice(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        if (notices.size() >= 32) {
            notices.remove(0);
        }
        notices.add(line);
    }

    /** 取走全部通知行（拷贝后清空——UI 每帧 drain） */
    public List<String> drainNotices() {
        List<String> drained = new ArrayList<String>(notices);
        notices.clear();
        return drained;
    }
}
