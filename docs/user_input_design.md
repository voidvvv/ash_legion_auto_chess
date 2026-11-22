# 自走棋游戏 - 输入控制与命令系统架构设计文档

> **适用引擎**：LibGDX  
> **核心目标**：实现高内聚、低耦合的玩家输入处理，确保逻辑确定性（单机录像回放 / 存档 / 单元测试友好；本项目纯单机，无帧同步联机需求），并规避多点触控、模态框穿透等常见陷阱。

---

## 1. 总体架构概览（数据流向）

游戏遵循 **“硬件捕获 → 语义翻译 → 命令缓存 → 逻辑消费”** 的四层流向：

1. **硬件层**：`Gdx.input` 捕获原始屏幕坐标与按键码。
2. **路由层**：`InputMultiplexer` 将事件分发给 `Stage`（UI）或自定义 `BoardInputProcessor`（棋盘）。
3. **翻译层**：处理器将坐标转换为业务意图（如 `MoveUnitCommand`），并压入队列。
4. **消费层**：在固定的逻辑 Tick 中，`CommandManager` 弹出命令并调用对应的 `CommandHandler` 修改游戏数据。

---

## 2. 输入捕获层设计（InputProcessor 与 Multiplexer）

### 2.1 核心组件
- **UI 输入**：由 LibGDX 的 `Stage`（Scene2D.UI）自动处理。按钮点击通过 `ClickListener` 生成命令。
- **棋盘输入**：自定义 `BoardInputProcessor` 类实现 `InputProcessor` 接口，统辖**棋盘 / 备战席 / 出售区**等自绘网格族的点击与拖拽（归属裁决见 2.5）。**2026-11-22（UI 抽屉化）**：备战席在屏上的 3×3 格子退役，改为左侧抽屉里的头像条——它画在 UI 域，但拖拽仍归本处理器：头像格不可触摸，事件穿过 `uiStage` 落到这里，命中经注入的 `DrawerHitTest` 回查（见 2.6）。
- **抽屉外点击观察**（2026-11-22 新增）：`DrawerOutsideTouchProcessor`——夹在 `dialogStage` 与 `uiStage` 之间的透传观察者，永不吃事件，只负责「点抽屉外收起」（见 2.2 / 2.6）。
- **键盘输入**：独立的 `GlobalKeyProcessor` 监听快捷键（如 `D` 键刷新、`Space` 暂停、`L` 键通知日志大窗开关）。快捷键经**绑定表**（`KeyBinding`，档案域持久化）解析，`GlobalKeyProcessor` 查表分发而非硬编码；MVP 内置默认表，重绑定 UI 列入待定。
- **Android 返回键（BACK）**：走 `keyDown`（需启用 catchBackKey）；语义**上下文敏感**——弹窗开 → 关闭顶层弹窗；备战 / 战斗中 → 打开暂停菜单（2026-11-22：左侧抽屉开着时 Esc / BACK 先收抽屉，抽屉已收起才开暂停菜单，见 2.6）；主菜单 → 退出确认。与 `Escape` 同源同规则（见 §3 模态穿透行的例外条款）。

### 2.2 多路复用器（InputMultiplexer）配置
**切勿直接将 `Screen` 或 `Stage` 设为全局输入处理器**，必须使用 `InputMultiplexer` 并按优先级排序：

```java
// 在 BattleScreen.show() 中
InputMultiplexer multiplexer = new InputMultiplexer();
multiplexer.addProcessor(dialogStage);      // 第一优先级（模态弹窗）
multiplexer.addProcessor(drawerOutsideTouch); // 第二优先级（抽屉外点击观察者，2026-11-22；永不吃事件）
multiplexer.addProcessor(uiStage);          // 第三优先级（UI 按钮 + 左侧抽屉的可触摸件）
multiplexer.addProcessor(boardProcessor);   // 第四优先级（棋盘点击 / 备战席头像格穿透后的拖拽）
multiplexer.addProcessor(keyProcessor);     // 第五优先级（键盘监听）
Gdx.input.setInputProcessor(multiplexer);
```

**阻断机制**：`InputMultiplexer` 链式遍历，直到某个 Processor 返回 `true` 为止。点击 UI 按钮时，`uiStage` 返回 `true`，事件被消费，不会穿透到棋盘。

**五层说明（2026-11-22 起）**：原四层 `dialogStage > uiStage > boardProcessor > keyProcessor` 的次序不变，只在 `dialogStage` 与 `uiStage` 之间插入 `DrawerOutsideTouchProcessor`。它必须排在 `uiStage` **之前**——uiStage 会先吃掉落在商店卡 / 顶栏等 UI 件上的点击，排在其后就看不见这些「点抽屉外」；又必须**永不吃事件**（全部回调返回 false）——否则会截断 `boardProcessor` 的落子抬手。

### 2.3 生命周期管理（防“僵尸监听”）
在 `Screen.hide()` 或 `dispose()` 中**必须主动解绑**，防止切屏后输入错乱：

```java
@Override
public void hide() {
    if (Gdx.input.getInputProcessor() == multiplexer) {
        Gdx.input.setInputProcessor(null);
    }
}
```

### 2.4 交互语义映射（手势 → 命令，V1.4 落定）

> 自走棋输入层的核心一环：把手势翻译成 11 个命令。PC 鼠标与 Android 触屏采用**统一手势模型**（拖拽语义一致，仅 pointer 类型不同）。

| 命令 | 触发手势 | 细则 |
|------|----------|------|
| `BuyUnit` | **点击**商店卡 | 不做拖拽购买（MVP；拖拽购买列入待定增强） |
| `SellUnit` | **拖拽到出售区** | 棋盘 / 备战席皆可拖去；出售区常驻屏幕角落。**2026-11-22**：备战席 = 抽屉头像格，起手命中改走 `DrawerHitTest`（见 2.6）；出售判定与命令入队不变 |
| `MoveUnit` | **拖拽** | 死区内松手 = 点击查看详情（Tooltip / 选中态），不产生命令；超过死区进入拖拽。**2026-11-22**：死区内松手 = 抽屉选中该棋子并展开（详情弹窗已退役，见 2.6） |
| `EquipItem` | **两段式点击**：点背包格 → 进入“装备待定态”（高亮可装备棋子）→ 点目标棋子完成；点空白 / 再点同一装备 = 取消 | 不做跨域拖拽（理由见 2.5）。**2026-11-22**：背包格 = 抽屉装备格（可滚动网格）；取消 = 点空装备格 / 再点同一装备格；待定期间点抽屉外不收起（见 2.6） |
| `UnequipItem` | 点击棋子详情面板的“卸下”按钮 | 详情面板由点击棋子唤起。**2026-11-22**：按钮改在抽屉「装备」标签页每件右侧；抽屉由点击棋子唤起（选中并展开，见 2.6） |
| `StartBattle` / `Surrender` / `PickChest` | 按钮 | — |
| `AbandonRun` | 暂停菜单按钮 + **二次确认弹窗** | 防误触放弃 |

> **命令集不变（2026-11-22 核实）**：抽屉化只改了「手势落在哪、命中怎么查」，没有新增 / 删除 / 改义任何命令——仍是 §4.3 与 `architecture_design.md` §四 / §五 的 11 命令。抽屉相关四个命令的入队点：`MoveUnitCommand` / `SellUnitCommand` ← `BoardInputProcessor.touchUp`（松手时判落点 / 出售区）；`EquipItemCommand` ← `BattleScreen` 的死区点击回调（有待定装备时）；`UnequipItemCommand` ← `DrawerInfoSection` 的「卸下」钮。抽屉的开合 / 选中 / 滚动 / 待定都不产生命令。

**通用拖拽规则**（拖拽状态由 `BoardInputProcessor` 的 `DragContext` 承载，见 §3 多点触控行）：
1. **拿起时原格保留 ghost、不空出**——逻辑层在命令结算前完全不动，视觉半透明占位；`MoveUnit` 命令在**松手瞬间**才入队，非法落点根本不产生命令，回弹零成本；
2. **非法落点**（人口已满时席→板、第 0~3 行等）：回弹原位 + 红光 / 音效提示，不产生命令；
3. **合法交换**：落点被己方棋子占用 → 生成一条 `MoveUnit`（交换语义由 systems 判定，命令不感知，见 4.1）；
4. **同时拖拽 ≤ 1**；
5. 拖拽中弹窗打开（模态）→ 立即取消当前拖拽并回弹。

### 2.5 输入归属裁决（V1.4 落定）

**棋盘域 × UI 域两分天下**：

| 区域 | 归属 | 理由 |
|------|------|------|
| 棋盘 6×7、备战席 9 格、出售区 | **棋盘域**：`boardProcessor` 自绘网格族 | `MoveUnit` / `SellUnit` 的全部拖拽收在同一处理器内——**拖拽永不跨域**，拖拽状态机只写一遍 |
| 商店卡、背包格、按钮、弹窗、详情面板 | **UI 域**：`uiStage`（Scene2D Actor） | 点击语义即可覆盖全部交互 |

**配套规则**：
- **跨域交互全部退化为点击**：点击天然跨体系（各处理器各自消费自己的点击，翻译层维护一个“装备待定态”式的轻量 pending 状态即可），无需两套拖拽体系互通；
- 装备穿脱因此定为两段式点击（见 2.4），背包不并入棋盘域；
- 商店点击即买，不做拖拽购买；
- 该裁决同时约束渲染层：棋盘域三件为自绘网格（render 包），UI 域为 Stage Actor。

> **2026-11-22 修订（UI 抽屉化；主人批准 `assets_src/ui_redesign/UI_PLAN.md` Q4 / Q5）**：备战席 9 格在屏上的 3×3 格子退役，改为左侧抽屉里的头像条——**绘制**迁入 UI 域（`render/ui/DrawerStripSection`，见 `render_design.md` §9.1），但**输入归属不变**：头像格设为不可触摸，事件穿过 `uiStage` 落到 `boardProcessor`，由注入的 `DrawerHitTest`（抽屉实现）按屏幕坐标给出槽号（= bench 下标），其后拖拽 / 出售 / 死区点击代码零改动——「拖拽状态机只写一遍」「拖拽永不跨域」两条原则原样保住。背包格 → 抽屉装备格、详情面板 → 抽屉信息区，仍是 UI 域点击语义。规则细则见 2.6。

### 2.6 左侧抽屉输入规则（UI 抽屉化，2026-11-22）

> **裁决来源**：`assets_src/ui_redesign/UI_PLAN.md` §5.1（主人 2026-11-22 批准 Q1~Q11）及同日五项追加裁决（含终审裁决两项：敌阵悬停卡抑制 / 战斗期查看范围）；**事实源**：`docs/spec_plan/2026-11-22_ui_drawer_impl_facts.md` §3（代码基线 HEAD 14ffb45，本节已逐项对照代码核对）。抽屉长什么样、画在哪见 `render_design.md` §9.1。

**大白话**：备战席和装备背包搬进了一块从屏幕左边滑出来的面板。玩家点左边缘的把手把它拉出来，点面板外面或按 Esc 收回去；可以直接从面板里的头像格把棋子拖到棋盘或出售区，这时面板会自己让开。

**(1) 开合**

| 操作 | 结果 |
|---|---|
| 点左缘把手（14×53，常驻左缘不随主体滑动） | 开 ⇄ 合切换 |
| 点关闭钮（14×14，标题板右上） | 收起 |
| 抽屉开着时按 `Esc` / Android `BACK` | **先收抽屉**；抽屉已收起才开暂停菜单（「有弹窗先关顶层」仍最优先） |
| 抽屉开着时点抽屉外（以**按下点**是否在抽屉外为准，抬手时触发） | 收起一次 |
| 默认态 | 收起（`DrawerController.DEFAULT_OPEN = false`，常量可切） |
| 战毕回备战 | 不自动恢复展开，只留把手 |

- **抽屉交互域** = 把手 ∪ 展开中的主体（`DrawerGeometry.isInside`，舞台坐标）。以按下点为准：从抽屉内起手的拖拽 / 拖滑块，即便抬手在抽屉外也不算「点外面」。
- **装备待定态期间点抽屉外不收起**（主人追加裁决）：两段式穿戴中途不被打断。把手 / 关闭钮 / Esc 是直连控制器的显式收起，不经这条判定，待定态下照常有效；待定清除后（穿上 / 取消 / 物品失配自清）恢复点外收起。
- 点外**不收起**的其它情形：转场窗口内（输入封禁）、有弹窗时、系统取消触摸（`touchCancelled` 丢弃待判定）。多指各自独立判定。
- 转场窗口内所有开合请求（含把手 / 关闭钮 / Esc）一律被拒。

**(2) 触摸穿透**（抽屉挂在 `uiStage` 下，但「拖棋子上阵」整条链在 `boardProcessor`）

抽屉若整块可触摸，会把落在它范围内的点击全部吞掉。所以把抽屉的件分成三类：

| 类别 | 哪些件 | 事件去向 |
|---|---|---|
| 可触摸 | 把手、关闭钮、「介绍 / 装备」两个标签、头像条与装备网格的两个滚动条滑块、6 个装备格、「卸下」钮、介绍描述框与装备描述框（`ScrollPane`：拖动 + 滚轮） | `uiStage` 消费，不下传 |
| 不可触摸 | 头像格、立绘、标题板、面板底板、分隔条、滚动条轨、收起态角标 | `uiStage` 不消费，经 multiplexer 下传到 `boardProcessor` |
| 可命中但无监听 | 抽屉主体（展开时） | 挡住下层面板防误点，但不吞事件——点在头像格 / 底板这类无控件响应处，事件照样下传 |

- `UnscaledLayer` 与 `DrawerPanel` 自身不可命中（`Touchable.childrenOnly`）：这一层铺满舞台，若自身可命中会截走商店卡 / 顶栏等全部点击。

**(3) 备战席命中接口 `DrawerHitTest`**（`input/DrawerHitTest`，由 `DrawerPanel` 实现，经构造器注入 `BoardInputProcessor`）

| 方法 | 入参 | 返回 | 谁在用 |
|---|---|---|---|
| `stripSlotAt(screenX, screenY)` | **屏幕坐标** | 可见槽号（= bench 下标）；不在可见槽上（含抽屉收起）返回 -1 | 起手命中（`BoardInputProcessor.unitAt`）与落点判定（`dropTargetAt` → `PlacementTarget.Bench(slot)`） |
| `scrollStrip(screenX, screenY, steps)` | 屏幕坐标 + 步数（正 = 看后面的槽） | 是否消费 | 滚轮落在头像条分区上（抽屉局部 (14,78,224,53)） |
| `scrollEquip(screenX, screenY, steps)` | 屏幕坐标 + 步数（正 = 看后面的行） | 是否消费 | 滚轮落在装备分区上（抽屉局部 (14,6,224,72)） |

- **为什么用屏幕坐标**：抽屉在 UI 域、棋盘在世界域，备战期两域缩放并不相等（UI 0.8、世界约 0.755）；只有抽屉知道自己滚了多少、滑到哪了，所以由它自己换算（`DrawerPanel.toLocal`：`uiViewport.unproject` 到舞台坐标，再减去主体当前位置得到抽屉局部坐标）。`boardProcessor` 其余路径照旧用棋盘 viewport 的 unproject（§3「坐标映射错误」行）。
- **槽号即 bench 下标**：返回值直接用于 `player.getBench().get(slot)`。头像条可见 6 槽 = `bench[首槽下标 .. 首槽下标+5]`，首槽下标随滚动变化；落点落在空槽、下标超出席长时由名单层钳制追加。
- 未注入 `DrawerHitTest` 时备战席不可命中（只剩棋盘）——② 的世界域矩形已退役，不再有第二条命中路径。
- 装备格的**点击**（两段式穿戴起点）不走本接口，是 `uiStage` 里装备格自己的点击监听（见 (7)）。

**(4) 滚轮路由与方向**（`BoardInputProcessor.scrolled`）
- 指针（最近一次 `mouseMoved` 的屏幕坐标）落在**头像条分区**或**装备分区**上 → 交给抽屉滚动并消费；其余位置不消费、不拦。
- 方向：libGDX 约定 `amountY > 0` = 向下滚 = **看后面的槽 / 行**（与 `ScrollPane` 同向）；步数 = ±max(1, round(|amountY|))。
- 介绍描述框 / 装备描述框上的滚轮，在 `uiStage` 层先被 `ScrollPane` 自行消费，不进 `boardProcessor`。
- 头像条与装备网格只支持「滚轮 + 拖滑块」：在头像格上按住拖动 = 拖棋子，在装备格上按住拖动不滚动，都不会带动列表。
- 模态 / 转场期不路由。

**(5) 拖拽让位**（Q2）
- 从头像格起手、位移一出死区（20 虚拟像素）→ 抽屉**强制收起**（无视转场封禁位），收起过程淡到 0.3；松手后保持收起、不自动弹回。触发位 = `BoardInputProcessor.isDragFromBench()`。
- 让位不影响拖拽链路：落点判定（棋盘格 / 头像格 / ⑦ 出售区）仍走原路径；落点头像格上叠选中框由抽屉绘制（世界域不再画备战席落点高亮）。
- 抽屉完全收起后头像条不再可命中（`stripSlotAt` 返回 -1）——该次拖拽不能落回头像条；落在非法处不产生命令，回弹由 ghost 消失实现。
- 拖拽中抑制装备格的悬停预览。

**(6) 点棋子 = 选中并展开**（主人追加裁决：维持）
- 死区内松手（棋盘上的己方棋子，或头像格上的棋子）：
  - 有待定装备 → 入队 `EquipItemCommand(itemId, unitId)`、清待定，并**穿上即选中**该棋子（「装备」标签页随即显示其三件）；
  - 否则 → 抽屉选中该棋子并展开（原「弹详情弹窗」已退役）。
- 抽屉已开着时点选谁就显示谁。同一次点击里「点外收起」先于「选中展开」执行（观察处理器排在 `uiStage` 之前），两次请求在同一帧内先后到达控制器，开合进度不变——最终结果为展开，无可见闪烁（代码路径推演）。

**(7) 装备两段式穿戴与卸下**（流程不变，落点改了）
1. 点抽屉装备格（仅备战期可点）→ 进入装备待定态（该格叠待定高亮），预览区显示该装备。
2. 点目标棋子（头像格或棋盘上的己方棋子）→ 入队 `EquipItemCommand`、清待定、抽屉选中该棋子。
3. 取消：再点同一装备格 / 点空装备格；待定物品已不在背包（已穿上 / 卖出）→ 每帧自动清除（`DrawerEquipText.reconcilePending`）。点其它装备格 = 换待定。
4. 卸下：抽屉「装备」标签页每件右侧的「卸下」钮 → 入队 `UnequipItemCommand(itemId)`（仅备战期）。

**(8) 战斗期只读**（Q6）
- 默认收起；允许只读展开。
- 禁止一切写操作：装备格点击与「卸下」钮在非 SHOPPING 相位直接忽略（`DrawerEquipText.writesAllowed`）；棋盘域拖拽本就只在备战期放行（`BoardInputProcessor.touchDown` 非 SHOPPING 即吞事件）；装备格悬停预览在战斗期抑制。
- **战斗期查看范围**：只读展开只显示战前最后一次选中的棋子、不提供切换（代码事实：`BoardInputProcessor.touchDown` 在非 SHOPPING 相位直接吞事件、不建拖拽上下文，点棋子不触发选中；若该棋子已卖出 / 合并则显示「未选中棋子」）。**主人 2026-11-22 终审裁决接受，属既定行为，不作为缺陷处理。**

**(9) 相位跳变强制收起**
- 任何相位跳变（进战斗 / 进结算 / 回备战 / 进终局）当帧强制收起，无视转场封禁位；同相位重复同步不动作。

**(10) 抽屉相关裁决一览**（均 2026-11-22；全文见 `assets_src/ui_redesign/UI_PLAN.md` §5.1）

| 日期 | 决策 | 结论 |
|---|---|---|
| 2026-11-22 | Q2 开合方式 | 左缘竖把手点击开合；点抽屉外或 Esc 收起；收起留把手（角标「后备 n/9」）；默认收起；战毕不自动恢复展开；从抽屉往外拖棋子时面板让位 |
| 2026-11-22 | Q4 备战席形态 | 头像条 = 备战席 9 格横向形态：可见 6、滚轮 / 滑块看余下，槽号即 bench 下标；条内只放备战席棋子，不含已上阵 |
| 2026-11-22 | Q5 信息架构 | 装备网格 = 背包库存；「装备」页 = 选中棋子三件并可卸下；「介绍」页 = 棋子详情；详情弹窗与背包悬停卡退役 |
| 2026-11-22 | Q6 战斗期 | 默认收起；允许只读展开；禁止写操作 |
| 2026-11-22 | 追加 · 点棋子 | = 选中并展开抽屉（维持） |
| 2026-11-22 | 追加 · 装备待定态 | 待定期间点抽屉外不收起；把手 / 关闭钮 / Esc 仍可收起；待定清除后恢复 |
| 2026-11-22 | 终审 · 敌阵悬停卡 | 抽屉展开时敌阵侦察虚影的悬停卡一并抑制（侦察请先收起抽屉）——主人终审裁决接受，属既定行为，不作为缺陷处理 |
| 2026-11-22 | 终审 · 战斗期查看范围 | 战斗期只读展开只显示战前最后选中的棋子、不提供切换——主人终审裁决接受，属既定行为，不作为缺陷处理 |
| 2026-11-22 | Q10 坐标漂移 | 另案处理；本次按实机落点写（见 `render_design.md` §9.1） |

---

## 3. 输入处理中的六大陷阱与防御方案

| 陷阱 | 现象 | 解决方案 |
| :--- | :--- | :--- |
| **多点触控冲突** | 拖拽棋子时，手指误触屏幕边缘，导致棋子甩飞或重叠。 | 在 `BoardInputProcessor` 中按 `pointer`（手指ID）独立存储拖拽状态（`HashMap<Integer, DragContext>`），并限制同时拖拽数量（手机建议 ≤ 1）。 |
| **点击与拖拽误判** | 玩家只想轻点棋子查看属性，却因微小位移被判定为拖拽移动。 | 引入**死区阈值（Dead Zone）**：**死区在 unproject 之后的 640×360 虚拟坐标系中定义**（20 虚拟像素 ≈ 0.6 格，格 32px；物理像素在 1080p 手机与 4K 屏上折算差 4 倍，语义会跨设备漂移）。虚拟坐标系内位移 < 20 时视为“点击”，进入拖拽状态后才允许移动棋子。 |
| **模态对话框穿透** | 弹窗显示时，点击弹窗背景缝隙，事件穿透导致底层棋盘棋子被误移动。 | 在 `BoardInputProcessor` 和 `KeyProcessor` 的方法首行检查全局标志 `UIDialogManager.isShowing()`，若为 `true` 则直接 `return true` 消费事件但不做任何事。**例外**：`Escape` / Android `BACK` 不吞——转交弹窗自身处理（有弹窗 → 关闭顶层弹窗，无弹窗 → 打开暂停菜单），保证键盘与返回键永远能关掉界面。 |
| **坐标映射错误** | 点击位置与棋子实际位置偏移，尤其在异形屏或不同分辨率下。 | 必须使用 **Viewport** 的 `unproject()` 方法转换坐标。**切勿混淆**：棋盘处理器使用**棋盘 viewport（含其 camera）**的 unproject——自定义处理器没有 Stage；UI 处理器使用 `uiStage` 的 viewport。 |
| **键盘焦点抢夺** | 玩家在聊天框输入文字时，按下 `D` 键意外触发了商店刷新。 | **查询 `stage.getKeyboardFocus() != null`**（文本框为 Scene2D `TextField` 时适用），**不另设手工标志**——手工标志与引擎焦点是双账本，必失同步。若未来使用系统输入法对话框（非 Scene2D 文本框），需另行挂接其激活回调。组合键（`CTRL`/`SHIFT`）操作直接放行给系统。 |
| **恢复瞬间的巨型 delta** | Android 挂起恢复 / GC 停顿后，单帧 `delta` 可达数秒，固定步长循环一口气跑数百个逻辑 tick（死亡螺旋）甚至卡死。 | **delta 钳制**（上限如 0.1s）+ **单帧最大 tick 数**（如 5），超限丢弃积压余量并记日志——宁可短暂慢放，不可螺旋卡死（实现见 §5.3）。 |

---

## 4. 命令模式（Command Pattern）实现规范

### 4.1 命令（Command）必须是“纯数据载体”
- **禁止**在 `Command` 类中编写 `run()` 或 `execute()` 业务方法。
- **原因**：便于序列化（录像/断线重连）、支持命令去重、避免逻辑与环境强耦合。

```java
// 纯数据示例：载荷只存“意图”，不存任何可推导事实（价格、属性、阶段等由 handler 现查）
public class MoveUnitCommand implements GameCommand {
    public final int unitId;
    public final PlacementTarget target;   // 上场/下场/走位/交换 四合一的落点
    // 构造函数省略...
}

// PlacementTarget：能表达“拖到备战席”与“拖到棋盘格”两种落点的小类型
public abstract class PlacementTarget {
    public static final class Bench extends PlacementTarget { public final int slotIndex; }
    public static final class Cell  extends PlacementTarget { public final int row, col; }
}
```

- **交换语义在 systems 判定**：目标格被己方棋子占用 → 交换。命令载荷不感知该规则（对齐 `architecture_design.md` §4.1 MoveUnit）。

**tick 配对（V1.1 新增）**：命令入队时由 `CommandManager` 盖 tick 戳，历史以 `(tick, command)` 二元组保存——这是“命令可序列化、支持录像回放”承诺的落点，回放按 tick 顺序重演命令流（tick 由管理器记录，不污染命令的纯数据）。

### 4.2 执行策略：使用“查表法（策略模式）”
- **不要**为每个 Command 配独立的 `CommandAction` 类（过度设计）。
- **推荐**：在 `CommandManager` 中维护 `Map<Class, CommandHandler>`，在逻辑 Tick 中根据命令类型分发执行。

```java
// 注册处理器（通常在 Manager 初始化时进行）
handlers.put(MoveUnitCommand.class, (cmd, ctx) -> {
    // 执行移动逻辑，调用 ctx.board.moveUnit(...)
});
```

### 4.3 阶段门控（自走棋特有约束，V1.1 新增）

命令并非全程合法：**购买 / 布阵 / 装备仅备战（SHOPPING）阶段可执行**；战斗（BATTLE）阶段玩家仅可 `Surrender`（投降）；结算（RESULT）阶段合法命令仅 `PickChest`（第 1/2 败领败箱用，2026-08-23）——但 RESULT 的**出口**不全是命令：零棋子败（败 1~2）与第 3 败（机会耗尽）经横幅点击 / 3 秒自动推进（系统行为，无命令产生，后者直入 RUN_END）；`AbandonRun`（放弃远征）在备战与战斗阶段均合法。`CommandHandler` 执行前先查当前逻辑阶段，不合法命令**静默忽略并记日志**。完整的 11 命令清单与门控矩阵见 `architecture_design.md` §四 / §五。

**双层校验**：表现层（输入翻译 / UI）做**预校验**——金币不足灰置按钮、飘 toast 提示，**只读、不改状态、不产生命令**；handler 层的静默确定性校验是**最后防线**（为回放与异常时序兜底）。两层职责不同，缺一不可：只有前者，快速连点、模态边缘等异常路径会穿透；只有后者，玩家会遭遇“点了没反应”的死机感。

---

## 5. 命令管理器（CommandManager）设计

### 5.1 核心职责
- **存放命令**：维护线程安全的命令队列（`ConcurrentLinkedQueue`）。注：libGDX 单渲染线程下输入与消费同线程，并发队列并非必需；保留属防御性选择（为未来异步来源留余地），成本近零。
- **保留历史**：在命令入队时同步备份至 `history` 列表，用于录像回放（队列消费后即删除，但历史永久保留）。
- **分发执行**：提供 `executeAll(RunContext context)` 方法，在固定逻辑 Tick 中被 Screen 调用，轮询队列并执行所有命令。
- **执行结果监听**：`onExecuted(cmd, success)` 回调——命令执行**成功**即通知订阅方（**通知面板的经营事件数据源**，见 `render_design.md` §5.5）；失败静默不通知（与 4.3 双层校验口径一致）。

### 5.2 类结构骨架

```java
public class CommandManager {
    private final Queue<GameCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final List<GameCommand> history = new ArrayList<>();
    private final Map<Class<? extends GameCommand>, CommandHandler> handlers = new HashMap<>();

    public void addCommand(GameCommand cmd) {
        commandQueue.offer(cmd);
        history.add(cmd); // 备份
    }

    public void executeAll(RunContext context) {
        GameCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            CommandHandler handler = handlers.get(cmd.getClass());
            if (handler != null) handler.handle(cmd, context);
        }
    }
    
    public void clearHistory() { history.clear(); }
    public void registerHandler(Class<?> clazz, CommandHandler handler) { ... }
}
```

### 5.3 消费时机（固定时间步长）
在 `BattleScreen.render()` 中，必须采用**固定时间步长（Fixed Timestep）**消费命令，防止帧率波动导致逻辑错乱：

```java
private float accumulator = 0f;
private final float LOGIC_STEP = 1/60f;
private final float MAX_DELTA = 0.1f;        // delta 钳制：挂起恢复 / GC 停顿的巨型帧（§3 第六陷阱）
private final int MAX_TICKS_PER_FRAME = 5;   // 单帧最大逻辑 tick 数

public void render(float delta) {
    accumulator += Math.min(delta, MAX_DELTA);          // 钳制，防死亡螺旋
    int ticks = 0;
    while (accumulator >= LOGIC_STEP && ticks++ < MAX_TICKS_PER_FRAME) {
        commandManager.executeAll(runContext);           // 命令消费：所有阶段都进行
        if (runContext.runState.getPhase() == Phase.BATTLE) {
            battleSystem.tick(LOGIC_STEP);               // 战斗推进：仅 BATTLE 阶段
        }                                                // （战斗主循环四阶段见 battle_design §二）
        accumulator -= LOGIC_STEP;
    }
    if (accumulator >= LOGIC_STEP) {                     // 超出单帧上限：丢弃积压并记日志
        accumulator = 0f;                                // 宁可短暂慢放，不可卡死
    }
    // ... 渲染代码
}
```

> **阶段感知**：命令消费在**所有**阶段进行；战斗推进**仅 BATTLE 阶段**（对齐 `architecture_design.md` §五门控）。战斗为“状态推进 → 行动 → 清扫 → 判定”四段式 tick（`battle_design.md` §二），不是单一的“updateAI”。

---

## 6. 执行上下文（RunContext）设计

`RunContext`（V1.3 更名，原名 `BattleContext`——它承载**整局**生命周期、含备战与购物，不只是“一场战斗”）是传递给 `CommandHandler` 的“工具箱”，用于解耦命令与具体业务类，是**全部 11 个命令 handler 的唯一工具箱**。

### 6.1 必须包含的内容
| 组件 | 类型 | 职责 |
| :--- | :--- | :--- |
| `Player` | 数据 | 金币、经验、等级（**无血量字段**：随 1C-R 废除，决策 2026-08-19）、名单（备战席 + 上场部署表） |
| `ShopSystem` | 数据 | 5 槽位（模板 + 价格）：`getPrice` / `pop` / `roll`（轮首免费刷新由系统触发，非命令） |
| `RunState` | 数据 | 轮次、场景、**当前逻辑阶段（门控查询入口）**、本轮战败机会计数（2026-08-23 取代怜悯计数）、随机种子、id 发号器、命令历史 |
| `BattleState` | 数据 | 战斗实例集合 + 6×7 棋盘占位（仅 BATTLE 阶段存在，战毕整体丢弃） |
| `UnitRegistry` | 数据解析 | 全场实体（棋子 / 装备 / 战斗实例）id → 对象解析与发号，无业务逻辑 |
| `RandomGenerator` | 工具 | 确定性随机（消耗点仅 4 处，见 `architecture_design.md` §六） |

> 职责终裁（`architecture_design.md` §2.3）：棋盘占位归 `BattleState`、备战席归 `Player`，**无独立 BoardManager**。
>
> **handler 与所属 system 的关系**：命令 handler 由其所属 system 注册（闭包引用自身系统，如 `StartBattle` 的派生逻辑就在 `BattleSystem` 注册的 handler 内）；`RunContext` 只承载**跨系统共享**的数据与解析器，防止工具箱膨胀成万能上下文。
| `RandomGenerator` | 工具类 | 确定性随机数（单机录像回放 / 测试 / 存档一致性的基石；消耗点清单见 `architecture_design.md` §六） |

### 6.2 绝对禁止放入的内容
- **严禁**放入 `Stage`、`Actor`、`SpriteBatch`、`Texture` 等任何渲染相关对象。
- **原因**：逻辑与渲染解耦——`RunContext` 保持纯 Java 可测（无需启动 LibGDX 后端），并杜绝逻辑代码绕过视图直接操纵画面。（注：本项目为单渲染线程，此为架构边界约束而非线程安全问题）

### 6.3 命令执行示例（购买棋子，V1.3 重写对齐终裁）
```java
// 在 ShopSystem 注册的 Handler 中
handlers.put(BuyUnitCommand.class, (cmd, ctx) -> {
    int cost = ctx.shop.getPrice(cmd.slot);   // 查价：价格是商店的事实，命令载荷只有 slot，
                                              // 不信任“外部申报的金额”（单一事实源）
    if (ctx.player.getGold() < cost) return;  // 静默校验（表现层已预校验，见 4.3 双层校验）
    ctx.player.addGold(-cost);                // 扣钱
    UnitData data = ctx.shop.popUnit(cmd.slot); // 取模板
    Unit unit = ctx.registry.createUnit(data);  // 创建实例：id 来自确定性发号器（计数器），与 RNG
                                                // 无关——购买不是随机消耗点（architecture §六）
    ctx.player.addToBench(unit);              // 备战席归 Player（终裁 architecture §2.3）
});
```

> 命令类名沿用 `Command` 后缀（`BuyUnitCommand` 实现 `GameCommand`）；语义名即 11 命令集的 `BuyUnit`。

---

## 7. 硬性规则与约束（务必遵守）

1. **AI 行为不走命令队列**：电脑对手的攻击、移动等自动行为**绝不**通过 `addCommand` 入队，应直接调用 `BattleManager` 内部方法。否则队列会被海量 AI 指令撑爆。更根本的原因：AI 行为是游戏状态的确定性函数，若入队，回放时将无法区分“玩家命令”与“系统行为”，命令流模型即失效。
2. **命令只承载“外部输入”**：仅玩家操作（点击、键盘、网络消息）走命令队列。
3. **Screen 只做“点火器”**：`BattleScreen` 仅负责初始化 `InputMultiplexer` 和在 `render` 中调用 `executeAll`，不编写具体的业务逻辑判断。
4. **单元测试友好**：由于 `Command` 是纯数据，`RunContext` 是纯 Java 对象，你可以完全不启动 LibGDX 后端，在 JUnit 中直接测试命令执行逻辑。

---

*文档版本：1.7（2026-11-22 UI 抽屉化回写：2.2 输入分层由四层改五层（`dialogStage` → `DrawerOutsideTouchProcessor` → `uiStage` → `boardProcessor` → `keyProcessor`）；新增 2.6 左侧抽屉输入规则；2.4 四行手势落点随抽屉化修订并核实命令集不变；2.5 备战席输入归属补注——裁决来源 `assets_src/ui_redesign/UI_PLAN.md` §5.1 及五项追加裁决（含终审裁决两项：敌阵悬停卡抑制 / 战斗期查看范围），事实源 `docs/spec_plan/2026-11-22_ui_drawer_impl_facts.md`。1.6：2026-08-23：§4.3 RESULT 门控补机会制出口——第 3 败无 PickChest、横幅直入 RUN_END；§6.1 RunState 怜悯计数改战败机会计数。1.5：快捷键示例增 `L`（通知日志大窗）；§5.1 增 `onExecuted` 执行结果监听（通知面板经营事件数据源）。1.4：落定 2.4 交互语义映射与 2.5 输入归属；陷阱表扩为六项。1.3：RunContext 更名与工具箱补全等）*  
*适用阶段：自走棋项目输入与控制层架构设计*