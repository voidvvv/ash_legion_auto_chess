# Phase 5 RESULT 期与 PickChest / 判负重试状态流转图

> 1C-R 战败处理（GDD §2.2）本期完整落地：胜 → 宝箱三选一 → 下一轮；负 → 同轮重试 + 怜悯；AbandonRun → RUN_END。
> 浏览器查看版：`phase5_result_retry_flow.html`（双击打开）
> 依据：`gdd_idea_0.0.0.1.md` §2.2/§3.2；`architecture_design.md` §5.1（状态图）/§5.4（判负路径）；Phase 4 差异声明 #6（判负同轮重试推 Phase 5）本期销账

```mermaid
flowchart TB
    SH["SHOPPING（备战期）<br/>商店 / 买卖 / 布阵 / 穿脱装备（门控矩阵 ✓）<br/>暂停菜单可开（AbandonRun 合法）"]
    BT["BATTLE<br/>battleSystem.step 60Hz<br/>投降 Surrender → 判负"]
    RSW["RESULT（胜局）<br/>onBattleOver：outcome == PLAYER_WIN<br/>→ roll 宝箱（RNG=2）→ RunState.pendingChest<br/>ChestDialog（dialogStage 模态）· 不自动推进"]
    RSL["RESULT（败局：全灭 / 超时 / 投降）<br/>无宝箱 · 无 RNG 消耗<br/>DEFEAT 横幅（含怜悯提示行）<br/>点击继续或 RESULT_BANNER_SECONDS 自动推进"]
    RE["RUN_END（终态）<br/>endCause = COMPLETED（第 25 轮胜局 PickChest 后）<br/>或 ABANDONED（AbandonRun）<br/>RunEndPanel：文案 / seed / 熟练度（stub）· RESTART"]

    SH -->|"StartBattle（零棋子允许）"| BT
    BT -->|"判胜 PLAYER_WIN"| RSW
    BT -->|"判负（全灭 / TIMEOUT / Surrender）"| RSL
    RSW -->|"PickChest(option)（唯一出口，必须领取）"| ADV
    RSL -->|"continueAfterDefeat：点击 / 自动（tickResult 仅败局生效）"| RETRY
    ADV{"advanceAfterVictory<br/>round == 25 ?"}
    ADV -->|"否：round+1 · 怜悯清零 · beginRound（敌阵重生成）<br/>· 商店免费刷新 · phase=SHOPPING"| SH
    ADV -->|"是：endCause=COMPLETED · mastery 结算（stub）"| RE
    RETRY["重试回备战（同轮）<br/>round 不变 · 敌阵不变 · 商店不变<br/>battleState=null（战斗实例整体丢弃）<br/>怜悯处理：上场数>0 → mercyLossCount+1<br/>  第 3 败起且本轮怜悯金<3 → gold+1"]
    RETRY --> SH
    SH & BT -.->|"AbandonRun（二次确认后）<br/>endCause=ABANDONED · mastery 结算（stub）"| RE
    RE -.->|"RESTART：UI 边界新 seed → 新鲜 RunContext → StartRun（回放第 0 条记录）"| SH

    subgraph MERCY["怜悯规则（GDD §3.2，architecture §5.4）"]
        M1["mercyLossCount：同轮连败计数（RunState.java:24 字段已建）<br/>新轮进入清零；零棋子战败不加"]
        M2["mercyGoldThisRound：本轮已发怜悯金（本期新增）<br/>mercyLossCount ≥ 3 且 < 3 时每次判负 +1 金"]
    end
```

## 与 Phase 4 行为的差异（销账 Phase 4 差异声明 #6）

| 项 | Phase 4 | Phase 5 |
|----|---------|---------|
| 判负流转 | 与胜局相同：round+1 推进 | 同轮重试：round/敌阵/商店全不变，怜悯计数+1 |
| RESULT 出口 | 横幅点击或 3s 自动统一 round+1 | 胜局必须 PickChest（无自动推进）；败局保留自动推进 |
| 第 25 轮战毕 | 直接 RUN_END | 胜局仍先 PickChest（architecture §4.4：回放流终点 = 最终 Boss 轮的 PickChest）再 RUN_END |
| RUN_END 成因 | 无区分 | endCause：COMPLETED / ABANDONED（RunEndPanel 文案区分） |
| 重开 seed | 同 DEMO_SEED=42（RunFlowSystem.java:31） | UI 域边界新 seed（Q3 裁决 B 配套），DEMO_SEED 常量删除 |

## 门控矩阵增量（architecture §5.2 对照）

| 命令 | SHOPPING | BATTLE | RESULT | 备注 |
|------|:---:|:---:|:---:|------|
| PickChest | | | ✓ | 待 pendingChest != null 且未领取 |
| AbandonRun | ✓ | ✓ | | RESULT 期不合法（败局走继续、胜局必须领箱） |
| StartRun | 仅新鲜上下文（round==1 且未 started） | | | 回放第 0 条记录；seed/sceneId 与上下文一致性校验 |
