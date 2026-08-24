# 判负三分岔流程（每轮 3 次机会制，GDD V0.15 §2.2）

> 归属：`docs/spec_plan/2026-08-24_defeat_chest_economy_pack.md` §5 / CP15 ｜ 日期：2026-08-24
> 机会上限 `GameBalance.DEFEAT_LIMIT_PER_ROUND = 3`（工作值待调）；判负成因 = 全灭 / 超时 / 投降（同口径）。

```mermaid
flowchart TD
    A[判负 全灭/超时/投降] --> B["onBattleOver: defeatCountPerRound +1（零棋子照扣）"]
    B --> C{败几次?}
    C -- "1~2 且上场>0" --> D[公式构造败箱 二选一 零RNG<br>金币=胜箱x50%向下取整 / 经验书=胜箱同值<br>挂 pendingChest origin=DEFEAT]
    C -- "1~2 且零棋子" --> E[无败箱 横幅<br>点击任意处重试 · 剩余机会 N]
    C -- "3 机会耗尽" --> F[无败箱 横幅<br>机会耗尽 · 远征失败]
    D --> G[ChestDialog「战败补给」二选一<br>PickChest 唯一出口<br>横幅点击/3s 均被 pendingChest 守卫拦截]
    G --> H[continueAfterDefeat<br>战斗态丢弃 · 回 SHOPPING 同轮重试<br>round/敌阵/商店不变]
    E --> I[横幅 3s 自动 / 点击] --> H
    F --> J[横幅 3s 自动 / 点击] --> K["endRun(DEFEATED)<br>熟练度 = 轮x3（同 ABANDONED）"]
    K --> L[RUN_END · RunEndPanel「远征失败」<br>restart 同英雄同场景新 seed]
    H --> M[进 SHOPPING 首帧写快照<br>defeatCountPerRound 入档 · 续玩不重置]
    N[胜局领箱推进新轮] --> O[defeatCountPerRound 清零]
```

## 分岔矩阵

| 判负时状态 | 机会计数 | pendingChest | RESULT 出口 | 去向 |
|---|---|---|---|---|
| 上场 > 0，第 1/2 败 | +1（→1 或 2） | 败箱二选一（零 RNG） | PickChest（唯一出口） | 回 SHOPPING 同轮重试 |
| 零棋子，第 1/2 败 | +1（→1 或 2） | null | 横幅 3s / 点击 | 回 SHOPPING 同轮重试 |
| 任意（含零棋子/投降），第 3 败 | +1（→3） | null | 横幅 3s / 点击 | endRun(DEFEATED) → RUN_END |
