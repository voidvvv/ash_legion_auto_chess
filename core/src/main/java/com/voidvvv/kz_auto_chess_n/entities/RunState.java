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
    /** StartRun 已执行标记（防重入；重开 = 新鲜 RunState 天然复位） */
    private boolean runStarted;
    /** 胜局 RESULT 期的宝箱 offer；领取后置 null（非胜局恒 null） */
    private ChestOffer pendingChest;
    /** RUN_END 期非 null */
    private RunEndCause endCause;
    /** 本轮已发怜悯金币（GDD §3.2 每轮 ≤3；新轮进入清零） */
    private int mercyGoldThisRound;
    /** 全局逻辑钟（CommandManager 消费 tick 后推进——Phase 4 口径 #11 统一销账，实现口径 #17） */
    private int logicTick;
    /** 熟练度结算暂存（MasteryCalculator stub 产出；Phase 6 接档案域） */
    private int masteryAwarded;
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
    public boolean isRunStarted() { return runStarted; }
    public ChestOffer getPendingChest() { return pendingChest; }
    public RunEndCause getEndCause() { return endCause; }
    public int getMercyGoldThisRound() { return mercyGoldThisRound; }
    public int getLogicTick() { return logicTick; }
    public int getMasteryAwarded() { return masteryAwarded; }

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

    public void setMercyGoldThisRound(int count) {
        this.mercyGoldThisRound = count;
    }

    public void setPendingChest(ChestOffer offer) {
        this.pendingChest = offer;
    }

    public void setEndCause(RunEndCause cause) {
        this.endCause = Objects.requireNonNull(cause, "cause 不能为 null");
    }

    public void setMasteryAwarded(int awarded) {
        this.masteryAwarded = awarded;
    }

    /** StartRun handler 专属（一次性） */
    public void markRunStarted() {
        this.runStarted = true;
    }

    /** 逻辑钟推进（CommandManager.executeAll 尾调用——唯一调用点） */
    public void advanceTick() {
        logicTick++;
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
