# Phase 4 Screen 流转与命令链路图

> Main(Game) → 三 Screen 导航（浅图，无导航栈框架）× GamePhase 逻辑状态机（BattleScreen 观察并联动 UI）× 玩家操作命令链路。
> 浏览器查看版：`phase4_screen_flow.html`（双击打开）
> 依据：`architecture_design.md` §五（阶段状态机）/§七（Screen 架构）；`user_input_design.md` §2.2（multiplexer）/§2.4~2.5（手势与归属）；Q1~Q4 裁决（2026-08-22）

```mermaid
flowchart TB
    MAIN["Main extends Game（本期改造，替换 ApplicationAdapter 模板）<br/>create(): JsonLoader.loadFromDirectory → PlaceholderArt 生成 → new Assets → LoadingScreen"]
    LD["LoadingScreen<br/>全量加载永不卸载（本期近空载）· 占位图集 GL 线程生成"]
    MM["MainMenuScreen（极简，architecture §七）<br/>标题 + 开始按钮（UI 态 → Screen 导航）"]
    BAT["BattleScreen（局内模拟域 · 内部零换屏）<br/>装配 RunContext / CommandManager / RunFlowSystem / BattleSystem<br/>双 viewport + InputMultiplexer + 帧循环"]

    MAIN --> LD --> MM --> BAT

    subgraph PHASE["GamePhase 状态机（RunState.phase；RunFlowSystem 推进，Screen 只观察联动）"]
        SH["SHOPPING<br/>拖拽布阵（MoveUnit）· 开战按钮（StartBattle）<br/>顶栏：轮次 / 金币 / 等级"]
        BT["BATTLE<br/>battleSystem.step 60Hz · 战斗 HUD（×1/×2 / 投降 / 60s 计时条）"]
        RS["RESULT（瞬态横幅 ≤3s，Q3）<br/>胜负文字 + 点击继续或超时自动"]
        RE["RUN_END（第 25 轮战斗结束后）<br/>终局文字 + 重开（同 seed，确定性对照）"]
        SH -->|"StartBattle（零棋子允许开战）"| BT
        BT -->|"state.isOver()（胜负/超时/投降）"| RS
        RS -->|"round+1 · WaveGenerator 重生成敌阵 · battleState=null"| SH
        RS -->|"round == TOTAL_ROUNDS(25)"| RE
        RE -->|"restart：round=1 · 同 seed 重掷（重试/怜悯推 Phase 5）"| SH
    end
    BAT --- PHASE

    subgraph CMD["命令链路（input §1 四层流向；AI / 系统行为不入队）"]
        direction TB
        IP["输入捕获+路由：InputMultiplexer<br/>① uiStage（UI 域：按钮命中）② boardProcessor（棋盘域：棋盘 6×7 / 备战席 3×3）<br/>（dialogStage / keyProcessor 推 Phase 5，位置预留）"]
        TR["翻译层<br/>boardProcessor: unproject 棋盘 viewport · 死区 20px 判点击/拖拽<br/>拖拽松手才入队，非法落点回弹不产生命令"]
        Q["CommandManager（Q1 出生）<br/>MoveUnit / StartBattle / Surrender（Q2 裁决）<br/>队列 + (tick, cmd) 历史 + onExecuted"]
        HD["handler（RunFlowSystem 注册，Phase 5 拆归各 system）<br/>纯确定性校验：禁时间 / 禁随机 / 非法静默忽略+记日志"]
        SYS["模拟层<br/>MoveUnitExecutor → Player 名单 API（含交换语义）<br/>StartBattle → BattleSystem.startBattle → BattleState<br/>Surrender → state.finish(ENEMY_WIN)"]
        IP --> TR --> Q --> HD --> SYS
    end
    BAT --- CMD
```

## 边界与约束

| 约束 | 出处 |
|------|------|
| SHOPPING / BATTLE / RESULT 是逻辑状态机的状态，由 BattleScreen **观察**并切换 UI 可用性，不是 Screen | architecture §七 |
| 命令只承载玩家输入（纯数据，禁 run/execute 方法）；系统行为（阶段推进/轮开始/演示名单）不入队 | input §4.1 / §7.1~7.2 |
| Screen 只做点火器：初始化 multiplexer + render 里 executeAll，业务判断不写在 Screen | input §7.3 |
| 拖拽永不跨域：棋盘/备战席归 boardProcessor，按钮归 uiStage；跨域交互退化为点击 | input §2.5 |
| 判负同轮重试 + 怜悯推 Phase 5：本期胜负统一推进轮次（差异声明 #6） | Q3 裁决 / architecture §5.1 |
| 6 Screen 中 Loading / MainMenu(极简) / Battle 本期出生；RunSetup / Codex / RunResult 推 Phase 5~6 | architecture §七 |
