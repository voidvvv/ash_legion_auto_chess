# Phase 5.1 悬停状态机与预览数据流图

> 裁决 1（2026-08-22）= A 固定锚悬停卡：悬停 ~250ms 显示、不跟随鼠标、固定卡位、只读精简、拖拽中与模态期抑制、与详情弹窗共用格式化函数。
> 浏览器查看版：`phase5.1_hover_flow.html`（双击打开）
> 依据：`2026-08-22_phase5.1_ui_polish_brief.md` R1；`user_input_design.md` §2.4（Tooltip/选中态）；计划 `2026-08-22_phase5.1_ui_polish.md` §5.1/§5.3-2/§5.3-8

## 1. 悬停驻留状态机（HoverStateMachine，每源一实例）

```mermaid
stateDiagram-v2
    direction LR
    [*] --> NONE
    NONE --> ARMING : candidate ≥ 0 且 !suppressed<br/>（帧 delta 累计，accumulator 型）
    ARMING --> ARMING : 同候选继续累计<br/>（同格微移不重抖）
    ARMING --> NONE : candidate 变化 / -1 / suppressed<br/>（计时清零）
    ARMING --> VISIBLE : elapsed ≥ DELAY_SECONDS(0.25)
    VISIBLE --> VISIBLE : 同候选持续（保持显示）
    VISIBLE --> NONE : candidate 变化 / -1 / suppressed
```

抑制条件的施加位置（集中两处，状态机只认 candidate 与 suppressed）：

| 源 | 归一位置 | 条件 |
|----|----------|------|
| 棋盘域 | `BoardInputProcessor.getHoverCandidateUnitId()`（CP6） | 拖拽中 `isDragging()` / `modalBlocked` / 非 SHOPPING / 名单核验 `getUnitById`（悬停中被卖出/合并 → -1） |
| 商店卡 | `HoverPreviewCard.refresh` 归一（CP8） | 非 SHOPPING / 空槽（`slotAt==null`）/ `suppressed` |
| 两源共用 | `BattleScreen.render` 传 `frozen`（CP16） | `frozen = paused || UIDialogManager.isShowing()`（与模拟冻结同源；冻结帧 delta 传 0） |

## 2. 预览数据流（输入 → 状态机 → 卡 → 格式化 → 数据）

```mermaid
flowchart TB
    subgraph SRC["双悬停源（互斥——单指针不同时悬停两处；棋盘源优先）"]
        BP["BoardInputProcessor（棋盘域，CP6）<br/>mouseMoved → unproject → unitAt 命中<br/>（备战槽 / 玩家区格）→ hoverUnitId<br/>返回 false：不消费事件"]
        SB["ShopBar（UI 域，CP7）<br/>ShopCard 增 InputListener enter/exit<br/>→ hoveredSlot；getHoveredSlot() 原始暴露"]
    end

    subgraph SM["HoverStateMachine ×2（CP5；250ms 驻留，见上图状态机）"]
        S1["boardHover.update(board候选, frozen, delta)"]
        S2["shopHover.update(shop槽位, frozen, delta)"]
    end

    BS["BattleScreen.render（CP16 装配点）<br/>hoverPreview.refresh(<br/>  boardProcessor.getHoverCandidateUnitId(),<br/>  shopBar.getHoveredSlot(), frozen, frozen ? 0 : delta)"]

    CARD["HoverPreviewCard（CP8；uiStage 最上层非模态瞬态）<br/>resolveTemplate（棋盘源优先，名单/空槽再核验）<br/>→ UnitInfoText.previewLines(模板, data, maxColumns)<br/>→ clipLines(容量 15，末行 …) → draw<br/>锚点：棋盘 (128,48,94,192) / 商店 (508,48,112,192)"]

    UIT["UnitInfoText（CP1；纯函数，headless 可测）<br/>名/N费 → 属性 3 行 → 技能名+desc → 羁绊块×≤2（名+desc+档位行）<br/>wrap：CJK=1 列 / ASCII=0.5 列；棋盘 7 列 / 商店 8 列<br/>synergyTierLine：档位数值行由 thresholds/effects 生成（R2 混合口径）"]

    DATA["GameData（只读）<br/>skills.json desc —— 已有（SkillData.getDesc 就位，零改动）<br/>synergies.json desc —— CP2~CP4 新增（必填 fail-fast）"]

    DETAIL["UnitDetailDialog（CP9；dialogStage 模态）<br/>UnitInfoText.detailLines（实例级：星/累计花费）<br/>唤起 = 棋子死区点击（既有链路）；模态期悬停自动抑制"]

    BS --> S1
    BS --> S2
    BP --> BS
    SB --> BS
    S1 --> CARD
    S2 --> CARD
    CARD --> UIT
    UIT --> DATA
    UIT --> DETAIL
```

## 3. 边界与约束

| 约束 | 出处 |
|------|------|
| 悬停只读、模板级（不含 spend/已穿装备）；实例级信息走点击详情弹窗 | 简报 R1 / 裁决 1 |
| 计时为表现层 accumulator（帧 delta），不进 stepSimulation 固定步长、不进命令队列、零 RNG | architecture §4.2 同类口径 / 计划 §5.3-2 |
| 悬停卡非模态：无输入监听、不阻断任何交互（区别于 dialogStage 弹窗族） | 计划 §5.1 |
| 双源互斥 + 棋盘源优先（单指针物理不可能同时命中两源，优先级为防御性定义） | 计划 §5.1 |
| 行集/折行/截断全部纯函数化（wrap/clipLines/synergyTierLine），绘制层零策略 | TDD 约束 / 计划 §6.CP1 |
| CJK 降级：缺字体回退时中文不渲染但不炸（不维护双语表） | 计划 §5.3-6 / 裁决 3 |
