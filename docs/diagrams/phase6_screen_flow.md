# Phase 6 Screen 导航与存档触发流转图

> Phase 5 遗留 Q3/Q5 销账：RunSetupScreen / CodexScreen / 终局结算 / 挂起快照（RunResultScreen 独立演出屏推 Phase 7，见 spec §4 裁决 D6）。
> 浏览器查看版：`phase6_screen_flow.html`（双击打开）
> 依据：`architecture_design.md` §七（6 Screen）/§八（持久化双轨）；`gdd_idea_0.0.0.1.md` §2.1

```mermaid
flowchart TB
    LD["LoadingScreen<br/>（不变：数据加载 → 首帧切菜单；新增 MetaService 透传）"]
    MM["MainMenuScreen（本期扩展）<br/>① 开始远征 → RunSetupScreen<br/>② 继续远征（存在 run_snapshot.json 时可见）<br/>③ 图鉴 → CodexScreen"]
    RS["RunSetupScreen（本期新建）<br/>英雄 3 卡（名/被动/熟练度 Lv·经验）<br/>场景 3 卡（未解锁灰置 + 前置提示）<br/>开始 → BattleScreen(seed, sceneId, heroId)"]
    CX["CodexScreen（本期新建，只读）<br/>英雄熟练度页 + 场景解锁页 + 返回"]
    BS["BattleScreen（本期扩展装配）<br/>newContext：sceneId/heroId/RunModifiers（起始金含加成）<br/>show：StartRun(seed, sceneId, heroId) 入队<br/>RUN_END 首帧：MetaService.settleRun + 清快照<br/>返回主菜单按钮"]
    RE["RunEndPanel（本期扩展）<br/>成因 + 轮次 + 结算行（熟练度 +N / Lv.a→Lv.b / 解锁场景）<br/>RESTART（同英雄同场景新 seed）/ 返回主菜单"]
    SNAP[("save/run_snapshot.json（快照轨）<br/>进入 SHOPPING 即写 · pause/hide 补写（仅备战期）<br/>RUN_END 删除 · 引用悬空删档不炸")]

    LD --> MM
    MM -->|"开始远征"| RS
    MM -->|"图鉴"| CX
    RS -->|"开始（域边界事件）"| BS
    MM -->|"继续远征 = loadRunSnapshot → restoreRunContext<br/>（跳过 StartRun，round/敌阵/商店/名单/RNG 流全复原）"| BS
    RS -->|"返回"| MM
    CX -->|"返回"| MM
    BS -->|"第 25 轮领箱 / AbandonRun → RUN_END"| RE
    RE -->|"RESTART：新 seed 复入 startRun"| BS
    RE -->|"返回主菜单"| MM
    BS <-->|"进入 SHOPPING 写 / RUN_END 清"| SNAP
    MM -.->|"hasRunSnapshot() 决定「继续远征」可见性"| SNAP
```

## 快照轨触发点（存档点仅备战阶段，决策 2026-08-20 沿用）

| 触发 | 时机 | 条件 | 动作 |
|------|------|------|------|
| 轮次快照 | BattleScreen 观察到 phase 进入 SHOPPING（新轮/重试回备战/开局） | `runStarted == true` | `MetaService.saveRunSnapshot` |
| 挂起快照 | `pause()` / `hide()`（Android 挂起、切窗、退出） | `phase == SHOPPING && runStarted` | 同上 |
| 删档 | RUN_END 首帧（结算同拍） | — | `MetaService.clearRunSnapshot` |
| 读档 | 主菜单「继续远征」 | 快照存在且引用完整 | `restore`：Player/RunState/商店槽/敌阵/装备背包/**RNG 消耗计数**（burn 恢复）全复原 |

## 恢复保真清单（SnapshotCodec.restore）

- seed / sceneId / heroId / round / 怜悯双计数 / runStarted
- RNG：`new RandomGenerator(seed, consumedCount)` 重放 nextFloat 对齐底层流（全消耗点均为单次 nextFloat，architecture §六）
- id 发号器：`SequentialIdIssuer(next)` 续号（单一 id 空间不断档）
- 名单：备战席入席序 + 部署表 18 格（单位 id/模板/星级/spend/已穿装备）
- 背包：装备 id/模板；商店 5 槽模板（含空槽）；敌阵 WaveSpec（模板/星级/强度/坐标——保「轮内敌阵不变」不变量）
- 不持久化：logicTick（命令历史不保，恢复后清空）、notices（瞬态）
