# Phase 5 暂停菜单 / dialogStage 模态阻断链图

> Q5 裁决：RunResultScreen / CodexScreen / 存档推 Phase 6；本期做暂停菜单（MVP=继续/放弃）+ dialogStage + GlobalKeyProcessor（Escape/BACK 永可达）+ AbandonRun 二次确认。
> 浏览器查看版：`phase5_dialog_modal_chain.html`（双击打开）
> 依据：`user_input_design.md` §2.2（multiplexer 四层）/§3（模态穿透陷阱：Escape/BACK 例外）/§2.4（AbandonRun 二次确认）；`render_design.md` §九（弹窗层级 = 输入优先级）；`architecture_design.md` §七（两类永不做 Screen：弹窗归 Dialog）

```mermaid
flowchart TB
    subgraph MP["InputMultiplexer（input §2.2 四层定序；Phase 4 两层 → 本期四层）"]
        direction TB
        DS["① dialogStage（UIDialogManager 持有）<br/>弹窗背板 Actor 全屏收点 + 弹窗本体<br/>有弹窗时吞掉全部触摸，uiStage 收不到"]
        US["② uiStage（UI 域）<br/>TopBar / ShopBar / InventoryPanel / SynergyPanel /<br/>ShoppingHud(FIGHT) / BattleHud / ResultBanner / RunEndPanel / NotificationPanel"]
        BP["③ boardProcessor（棋盘域）<br/>首行查 modalBlocked（BattleScreen.java:129-134 预留位本期接真实供应者）<br/>= UIDialogManager::isShowing → 吞事件不动作"]
        KP["④ GlobalKeyProcessor（本期新建）<br/>Escape / Android BACK：永不被模态吞（input §3 例外条款）<br/>有弹窗 → 关顶层；无弹窗 → 开暂停菜单<br/>L 键 → 通知面板大小窗切换"]
    end

    DM["UIDialogManager（render/ui/ 新建）<br/>dialogStage + 弈窗栈（push/closeTop/clearAll）<br/>isShowing() 供 modalBlocked 与模拟冻结<br/>act/draw/resize/dispose 委托"]

    subgraph DLGS["dialogStage 弹窗族（Scene2D Group 自绘，无 Skin——Q4=B Kenney 包推后）"]
        PAUSE["PauseMenuDialog（MVP：继续 / 放弃）<br/>放弃 → 二次确认子弹窗 → AbandonRunCommand 入队<br/>（设置 Dialog 推 Phase 7）"]
        CHEST["ChestDialog（RESULT 胜局）<br/>三选一按钮 → PickChestCommand(option)<br/>领取后 Screen 观察.pendingChest==null 自动收起"]
        DETAIL["UnitDetailDialog（点击棋子唤起）<br/>名/星/属性/已穿装备 + 每件卸下按钮 → UnequipItemCommand"]
        CONFIRM["AbandonConfirmDialog（二次确认，GDD §2.1 防误触）"]
    end

    FREEZE["模拟冻结口径（实现层）<br/>frozen = paused || UIDialogManager.isShowing()<br/>→ accumulator 不累积（沿 Screen.pause 既有分支模式）<br/>RESULT 败局自动计时同被冻结——模态期间不推进，关窗恢复"]

    DRAG["拖拽中弹窗打开（input §2.4 通用规则 5）<br/>boardProcessor 被 modalBlocked 吞 touchDragged/touchUp<br/>→ 拖拽自然作废回弹（DragContext 遗弃，ghost 消失）"]

    DM --> DS
    DM --> FREEZE
    DM --> BP
    PAUSE & CHEST & DETAIL & CONFIRM --- DM
    KP -->|"Escape / BACK"| DM
```

## 弹窗生命周期与状态一致性

| 弹窗 | 开 | 关 | 数据一致性 |
|------|----|----|-----------|
| PauseMenuDialog | Escape/BACK（无弹窗时）或 TopBar 暂停按钮 | 继续 / Escape / BACK | 只读 RunState；放弃经命令队列结算（UI 不直改状态） |
| AbandonConfirmDialog | 放弃按钮 | 确认（入队 AbandonRun）/ 取消 | 确认后 handler 置 RUN_END，Screen 观察切 RunEndPanel |
| ChestDialog | Screen 观察 phase==RESULT 且 pendingChest!=null 时 push 一次 | 领取后 pendingChest==null → pop（Screen 观察） | 选项内容进 RESULT 时已 roll 好（architecture §4.1），弹窗只读 offer |
| UnitDetailDialog | 棋盘域死区内松手（点击）且无装备待定态 | 关闭按钮 / Escape / BACK | 展示期名单可能变化 → 每帧 refresh；卸下走命令 |

## 边界与约束

| 约束 | 出处 |
|------|------|
| 弹窗/面板/对话框永不做 Screen（归 dialogStage） | architecture §七 |
| 弹窗层级与输入优先级一致：dialogStage > uiStage > boardProcessor > keyProcessor | render §九 / input §2.2 |
| Escape/BACK 是唯一穿透模态的键：有弹窗关顶层、无弹窗开暂停 | input §3 模态陷阱防御 |
| 拖拽中弹窗打开 → 立即取消拖拽回弹 | input §2.4 规则 5（boardProcessor 首行吞事件自然实现） |
| 暂停 = 表演层（accumulator 冻结），不改逻辑状态 | architecture §4.2 |
| AbandonRun 在 SHOPPING/BATTLE 合法；RESULT 期不合法（胜局必须领箱、败局走继续） | 门控矩阵 + 本期实现口径 |
| 设置 Dialog 推 Phase 7；暂停菜单 MVP = 继续/放弃 | Q5 裁决 |
