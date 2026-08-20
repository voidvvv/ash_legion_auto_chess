# 游戏交互流程图（V0.2，待定项已全部裁决）

> 启动 → 局外选择 → 局内循环（备战 / 战斗 / 结算）→ 远征结束的全交互地图。  
> 浏览器查看版：`interaction_flow.html`（双击打开）  
> 裁决依据：GDD V0.6 决策日志、`architecture_design.md` §九

**标注图例**：【命令】局内命令（入队、tick 消费、可回放）｜【档案】档案域操作（持久化、无回放）｜【UI】纯界面交互（不产生命令）｜【系统】确定性自动反应（非命令）

```mermaid
flowchart TD
    BOOT(["游戏启动"]) --> MENU["主菜单"]
    MENU -->|"点击·退出【UI】"| EXIT(["退出游戏"])

    subgraph META["局外 · 档案域"]
        SETTINGS["设置：音量/键位/加速默认值<br/>【UI · 复用Dialog组件】"]
        CODEX["图鉴 / 战绩 / 解锁总览<br/>【UI · 只读】"]
        HEROSEL["英雄选择界面<br/>点卡片＝高亮＋被动预览【UI】"]
        SCENESEL["场景选择界面<br/>点卡片＝敌情预览【UI】"]
        STARTRUN["StartRun (heroId, sceneId, seed)<br/>解锁校验【档案】<br/>＝回放流第 0 条记录"]
        LOAD["继续远征：读取快照<br/>重建 RunState【档案】"]
        MENU -->|"点击·设置"| SETTINGS
        SETTINGS -->|"返回【UI】"| MENU
        MENU -->|"点击·图鉴"| CODEX
        CODEX -->|"返回【UI】"| MENU
        MENU -->|"点击·新远征"| HEROSEL
        HEROSEL -->|"点击·下一步【UI】"| SCENESEL
        SCENESEL -->|"点击·开始远征"| STARTRUN
        MENU -->|"点击·继续（存在挂起存档）"| LOAD
    end

    STARTRUN --> ROUNDSTART
    LOAD -->|"存档点＝仅备战阶段（已定）"| SHOPPING

    subgraph RUNX["局内 · 模拟域（一次远征）"]
        ROUNDSTART["轮次开始【系统】<br/>轮次＋1 · 生成敌阵（本轮重试不重掷）· 商店免费刷新"]
        SHOPPING(("备战阶段<br/>SHOPPING"))
        BATTLE(("战斗阶段<br/>BATTLE"))
        RESULT(("结算阶段<br/>RESULT"))
        PAUSE["暂停菜单【UI】<br/>（暂停时逻辑 accumulator 停摆）"]
        RUNEND["远征结束【系统】<br/>通关＝击败最终Boss / 放弃"]
        SETTLE["局外结算【档案】<br/>熟练度经验 · 场景解锁 · 点亮星辰"]

        ROUNDSTART --> SHOPPING

        SHOPPING -->|"侦察敌阵 / 棋子Tooltip【UI·只读】"| SHOPPING
        SHOPPING -->|"BuyUnit【命令】→ 自动3合1【系统】"| SHOPPING
        SHOPPING -->|"SellUnit【命令】"| SHOPPING
        SHOPPING -->|"RefreshShop【命令】（轮首免费那次为系统行为）"| SHOPPING
        SHOPPING -->|"BuyExp【命令】→ 人口升级【系统】"| SHOPPING
        SHOPPING -->|"MoveUnit【命令】·上场/下场/走位/交换"| SHOPPING
        SHOPPING -->|"EquipItem / UnequipItem【命令】"| SHOPPING
        SHOPPING -->|"StartBattle【命令】·零棋子亦可开战（已定）<br/>→ 派生BattleUnit＋羁绊快照【系统】"| BATTLE

        BATTLE -->|"观战 · 加速×1/×2 · 暂停画面【UI】"| BATTLE
        BATTLE -->|"战斗tick推进 · 超时60s判负【系统】"| BATTLE
        BATTLE -->|"Surrender【命令】· 投降立即判负（已定）"| SHOPPING
        BATTLE -->|"判负【系统】：全灭/超时/投降<br/>同轮重试 · 敌阵不变 · 怜悯金币"| SHOPPING
        BATTLE -->|"判胜【系统】→ 宝箱内容roll【系统】"| RESULT

        RESULT -->|"浏览三选一【UI·只读】"| RESULT
        RESULT -->|"PickChest【命令】→ 应用奖励【系统】"| NEXT{"最终Boss轮？"}
        NEXT -->|"否"| ROUNDSTART
        NEXT -->|"是"| RUNEND

        SHOPPING -->|"Esc / 按钮【UI】"| PAUSE
        BATTLE -->|"Esc / 按钮【UI】"| PAUSE
        PAUSE -->|"返回【UI】"| SHOPPING
        PAUSE -->|"返回【UI】（原阶段）"| BATTLE
        PAUSE -->|"放弃远征 → AbandonRun【命令】（已定）"| RUNEND
        RUNEND --> SETTLE
    end

    SETTLE -->|"自动写入档案存档【档案】"| MENU
```

## V0.1 挂起项的裁决结果（2026-08-20，全部定案）

| 原待定项 | 结论 |
|----------|------|
| `Surrender`（投降） | **保留**：战斗中随时投降，立即判负原地重试 |
| `AbandonRun`（放弃远征） | **保留**：备战/战斗阶段经暂停菜单触发，按波数结算熟练度 |
| 每轮开始商店免费刷新 | **有**：新轮进入备战时触发；重试不触发 |
| 装备合成 | **MVP 无合成**：宝箱直接给成品（图按无合成绘制，无 CombineEquipment 命令） |
| 零棋子开战 | **允许**：StartBattle 无前置校验 |
| 存档点约束 | **仅备战阶段可挂起存档** |
