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
- **棋盘输入**：自定义 `BoardInputProcessor` 类实现 `InputProcessor` 接口，统辖**棋盘 / 备战席 / 出售区**等自绘网格族的点击与拖拽（归属裁决见 2.5）。
- **键盘输入**：独立的 `GlobalKeyProcessor` 监听快捷键（如 `D` 键刷新、`Space` 暂停、`L` 键通知日志大窗开关）。快捷键经**绑定表**（`KeyBinding`，档案域持久化）解析，`GlobalKeyProcessor` 查表分发而非硬编码；MVP 内置默认表，重绑定 UI 列入待定。
- **Android 返回键（BACK）**：走 `keyDown`（需启用 catchBackKey）；语义**上下文敏感**——弹窗开 → 关闭顶层弹窗；备战 / 战斗中 → 打开暂停菜单；主菜单 → 退出确认。与 `Escape` 同源同规则（见 §3 模态穿透行的例外条款）。

### 2.2 多路复用器（InputMultiplexer）配置
**切勿直接将 `Screen` 或 `Stage` 设为全局输入处理器**，必须使用 `InputMultiplexer` 并按优先级排序：

```java
// 在 BattleScreen.show() 中
InputMultiplexer multiplexer = new InputMultiplexer();
multiplexer.addProcessor(dialogStage);      // 第一优先级（模态弹窗）
multiplexer.addProcessor(uiStage);          // 第二优先级（UI按钮）
multiplexer.addProcessor(boardProcessor);   // 第三优先级（棋盘点击）
multiplexer.addProcessor(keyProcessor);     // 第四优先级（键盘监听）
Gdx.input.setInputProcessor(multiplexer);
```

**阻断机制**：`InputMultiplexer` 链式遍历，直到某个 Processor 返回 `true` 为止。点击 UI 按钮时，`uiStage` 返回 `true`，事件被消费，不会穿透到棋盘。

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
| `SellUnit` | **拖拽到出售区** | 棋盘 / 备战席皆可拖去；出售区常驻屏幕角落 |
| `MoveUnit` | **拖拽** | 死区内松手 = 点击查看详情（Tooltip / 选中态），不产生命令；超过死区进入拖拽 |
| `EquipItem` | **两段式点击**：点背包格 → 进入“装备待定态”（高亮可装备棋子）→ 点目标棋子完成；点空白 / 再点同一装备 = 取消 | 不做跨域拖拽（理由见 2.5） |
| `UnequipItem` | 点击棋子详情面板的“卸下”按钮 | 详情面板由点击棋子唤起 |
| `StartBattle` / `Surrender` / `PickChest` | 按钮 | — |
| `AbandonRun` | 暂停菜单按钮 + **二次确认弹窗** | 防误触放弃 |

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

命令并非全程合法：**购买 / 布阵 / 装备仅备战（SHOPPING）阶段可执行**；战斗（BATTLE）阶段玩家仅可 `Surrender`（投降）；结算（RESULT）阶段仅 `PickChest`；`AbandonRun`（放弃远征）在备战与战斗阶段均合法。`CommandHandler` 执行前先查当前逻辑阶段，不合法命令**静默忽略并记日志**。完整的 11 命令清单与门控矩阵见 `architecture_design.md` §四 / §五。

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
| `RunState` | 数据 | 轮次、场景、**当前逻辑阶段（门控查询入口）**、怜悯计数、随机种子、id 发号器、命令历史 |
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

*文档版本：1.5（2026-08-21：快捷键示例增 `L`（通知日志大窗）；§5.1 增 `onExecuted` 执行结果监听（通知面板经营事件数据源）。1.4：落定 2.4 交互语义映射与 2.5 输入归属；陷阱表扩为六项。1.3：RunContext 更名与工具箱补全等）*  
*适用阶段：自走棋项目输入与控制层架构设计*