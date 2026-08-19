# 自走棋游戏 - 输入控制与命令系统架构设计文档

> **适用引擎**：LibGDX  
> **核心目标**：实现高内聚、低耦合的玩家输入处理，确保逻辑确定性（帧同步友好），并规避多点触控、模态框穿透等常见陷阱。

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
- **棋盘输入**：自定义 `BoardInputProcessor` 类实现 `InputProcessor` 接口，专门处理棋盘格子的点击与拖拽。
- **键盘输入**：独立的 `GlobalKeyProcessor` 监听快捷键（如 `D` 键刷新、`Space` 暂停）。

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

---

## 3. 输入处理中的五大陷阱与防御方案

| 陷阱 | 现象 | 解决方案 |
| :--- | :--- | :--- |
| **多点触控冲突** | 拖拽棋子时，手指误触屏幕边缘，导致棋子甩飞或重叠。 | 在 `BoardInputProcessor` 中按 `pointer`（手指ID）独立存储拖拽状态（`HashMap<Integer, DragContext>`），并限制同时拖拽数量（手机建议 ≤ 1）。 |
| **点击与拖拽误判** | 玩家只想轻点棋子查看属性，却因微小位移（1~2像素）被判定为拖拽移动。 | 引入**死区阈值（Dead Zone）**，距离 < 20像素时视为“点击”，进入拖拽状态后才允许移动棋子。 |
| **模态对话框穿透** | 弹窗显示时，点击弹窗背景缝隙，事件穿透导致底层棋盘棋子被误移动。 | 在 `BoardInputProcessor` 和 `KeyProcessor` 的方法首行检查全局标志 `UIDialogManager.isShowing()`，若为 `true` 则直接 `return true` 消费事件但不做任何事。 |
| **坐标映射错误** | 点击位置与棋子实际位置偏移，尤其在异形屏或不同分辨率下。 | 必须使用 **Viewport** 的 `unproject()` 方法转换坐标。**切勿混淆**：棋盘处理器使用棋盘 `Stage` 的 `Viewport`，UI处理器使用UI `Stage` 的 `Viewport`。 |
| **键盘焦点抢夺** | 玩家在聊天框输入文字时，按下 `D` 键意外触发了商店刷新。 | 维护 `isTextInputActive` 标志。或在 `keyDown` 中检查组合键（如 `CTRL`/`SHIFT`），组合键操作直接放行给系统，不处理游戏逻辑。 |

---

## 4. 命令模式（Command Pattern）实现规范

### 4.1 命令（Command）必须是“纯数据载体”
- **禁止**在 `Command` 类中编写 `run()` 或 `execute()` 业务方法。
- **原因**：便于序列化（录像/断线重连）、支持命令去重、避免逻辑与环境强耦合。

```java
// 纯数据示例
public class MoveUnitCommand implements GameCommand {
    public final int unitId;
    public final int targetRow;
    public final int targetCol;
    // 构造函数省略...
}
```

### 4.2 执行策略：使用“查表法（策略模式）”
- **不要**为每个 Command 配独立的 `CommandAction` 类（过度设计）。
- **推荐**：在 `CommandManager` 中维护 `Map<Class, CommandHandler>`，在逻辑 Tick 中根据命令类型分发执行。

```java
// 注册处理器（通常在 Manager 初始化时进行）
handlers.put(MoveUnitCommand.class, (cmd, ctx) -> {
    // 执行移动逻辑，调用 ctx.board.moveUnit(...)
});
```

---

## 5. 命令管理器（CommandManager）设计

### 5.1 核心职责
- **存放命令**：维护线程安全的命令队列（`ConcurrentLinkedQueue`）。
- **保留历史**：在命令入队时同步备份至 `history` 列表，用于录像回放（队列消费后即删除，但历史永久保留）。
- **分发执行**：提供 `executeAll(BattleContext context)` 方法，在固定逻辑 Tick 中被 Screen 调用，轮询队列并执行所有命令。

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

    public void executeAll(BattleContext context) {
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

public void render(float delta) {
    accumulator += delta;
    while (accumulator >= LOGIC_STEP) {
        commandManager.executeAll(battleContext); // 消费所有玩家命令
        battleManager.updateAI(LOGIC_STEP);       // AI逻辑不经过命令队列
        accumulator -= LOGIC_STEP;
    }
    // ... 渲染代码
}
```

---

## 6. 执行上下文（BattleContext）设计

`BattleContext` 是传递给 `CommandHandler` 的“工具箱”，用于解耦命令与具体业务类。

### 6.1 必须包含的内容
| 组件 | 类型 | 职责 |
| :--- | :--- | :--- |
| `PlayerManager` | 数据管理 | 金币、经验、等级、血量 |
| `BoardManager` | 数据管理 | 棋盘格子占位、备战席管理 |
| `ShopManager` | 数据管理 | 商店候选棋子列表 |
| `UnitManager` | 数据管理 | 全场棋子实例（纯数据类，不含 Actor） |
| `RandomGenerator` | 工具类 | 确定性随机数种子（联机帧同步基石） |

### 6.2 绝对禁止放入的内容
- **严禁**放入 `Stage`、`Actor`、`SpriteBatch`、`Texture` 等任何渲染相关对象。
- **原因**：命令执行发生在逻辑线程，若操作渲染对象会导致并发修改异常（`ConcurrentModificationException`）。

### 6.3 命令执行示例（购买棋子）
```java
// 在 ShopManager 注册的 Handler 中
handlers.put(BuyUnitCommand.class, (cmd, ctx) -> {
    if (ctx.player.getGold() < cmd.cost) return;
    ctx.player.addGold(-cmd.cost);                // 扣钱
    UnitData data = ctx.shop.popUnit(cmd.slot);   // 取数据
    Unit unit = ctx.units.createUnit(data, ctx.random); // 创建实例
    ctx.board.addToBench(unit);                   // 放到备战席
});
```

---

## 7. 硬性规则与约束（务必遵守）

1. **AI 行为不走命令队列**：电脑对手的攻击、移动等自动行为**绝不**通过 `addCommand` 入队，应直接调用 `BattleManager` 内部方法。否则队列会被海量 AI 指令撑爆。
2. **命令只承载“外部输入”**：仅玩家操作（点击、键盘、网络消息）走命令队列。
3. **Screen 只做“点火器”**：`BattleScreen` 仅负责初始化 `InputMultiplexer` 和在 `render` 中调用 `executeAll`，不编写具体的业务逻辑判断。
4. **单元测试友好**：由于 `Command` 是纯数据，`BattleContext` 是纯 Java 对象，你可以完全不启动 LibGDX 后端，在 JUnit 中直接测试命令执行逻辑。

---

*文档版本：1.0*  
*适用阶段：自走棋项目输入与控制层架构设计*