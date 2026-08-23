# Phase 6 自动裁决记录（预授权协议执行报告）

> 状态：执行完毕，供事后复核 ｜ 归属：`2026-08-22_phase6_meta_progression.md` 配套文档 ｜ 日期：2026-08-22
>
> **背景**：本次 Phase 6 开发采用用户预先授权的两级工作流——libgdx-impl-planner 产出 spec → libgdx-impl-executor 落地代码。两个 agent 的铁律原本是「遇含糊/矛盾必须先向用户求证」；用户预先授权：**遇无法处理的问题时记录该问题，并允许按各自推荐方案先行实施**。本文档即该协议的执行账本，列出全部未经理前人工裁决、按推荐方案自动落地的决定，供逆向审阅。任何一条如不同意，按「影响面」列可定位改动点复核。

## 1. 执行结果概览

| 项 | 结果 |
|---|---|
| spec | `2026-08-22_phase6_meta_progression.md`（4033 行，18 CP / 9 任务 / 23 测试类用例表）+ 图表 2 组（`docs/diagrams/phase6_*`） |
| 代码 | T1~T9 全部完成，**未触发裁剪线**（D1 预定义的 T4 内容 / T7 快照均顺利实施）；新建主代码 17 类 + 数据文件 1，修改主代码 21 类 + 数据 3 + 文档 3 + `.gitignore`；新建测试 10 / 改写 16 |
| 测试 | 独立复核（主会话重跑）：`gradlew test` 退出码 0，TEST-*.xml 聚合 **733 / 0 失败 / 0 错误 / 0 忽略**（基线 631 → +102） |
| 自动裁决总数 | **31 条** = 规划期 D1~D20 + 执行期 E1~E11，另有计划授权范围内的实现细节登记 4 项（§4） |

## 2. 规划期自动裁决（D1~D20，libgdx-impl-planner）

完整表（备选方案 / 理由 / 影响面五列）见 spec **§4**；此处为速查缩编。带「工作值待调」的数值为按 GDD 量级锚点自拟，Phase 7 数值平衡时复核。

| # | 问题一句话 | 选定方案（推荐方案） |
|---|------------|----------------------|
| D1 | Phase 6 体量 ≈18 CP 是否切子阶段 | **不切**，单期 + 任务级裁剪线（T4 内容 / T7 快照可后延） |
| D2 | 熟练度 Lv.1「初始金币+2」与格雷克被动疑似重复 | **两线叠加**：Lv.1 为全英雄基础权益，格雷克被动另 +2（格雷克 Lv.1 开局 14 金） |
| D3 | 熟练度漏「通关 +60」口径 | **补全**：COMPLETED = 60 + 轮数×3；ABANDONED 维持轮数×3 |
| D4 | Lv.4/Lv.5 解锁内容 GDD 空洞 | **自拟工作值待调**：Lv.4 开局金币 +3；Lv.5 商店刷新费 -1（实付下限 1 金） |
| D5 | Lv.2「+5pp 稀有概率」生效轮次未定义 | **仅基础 3 费概率 >0 的轮次生效**（约第 10 轮起），自 1 费扣减保三档和 100 |
| D6 | RunResultScreen 独立屏（architecture §七） | **RunEndPanel 扩展结算展示（MVP）**，独立演出屏推 Phase 7 |
| D7 | 场景解锁状态存储 | **由 completedScenes 派生**，不落 unlocked 位（消双源漂移） |
| D8 | 场景门控棋子机制落点 | **scenes.json 增 `shopUnlocks[]`**（引用制，不动 units 锁定字段表） |
| D9 | seed 手输 UI | **不做**（无 Skin/TextField 资产；RunEndPanel seed 展示已可复现） |
| D10 | 快照触发口径与范围 | **进 SHOPPING 即写 + pause/hide 补写**；logicTick/notices 不存 |
| D11 | 档案写入时点 | **RUN_END 首帧由 BattleScreen 观察触发**（模拟域零 IO，runEndSettled 旗标保恰一次） |
| D12 | 薇拉「羁绊效果 +25%」作用面 | **当档全部效果**（stat+effect 通道），ADD 四舍五入、PCT/effect 浮点 |
| D13 | 奥兰多「全队回能 +15%」载体 | **energyGainRate ADD +15 百分点**（复用既有词表与消耗点，敌方侧不吃） |
| D14 | 档案/快照存盘路径 | **`Gdx.files.local("save/")` JSON 明文**（运行期落 `assets/save/`，已加 .gitignore） |
| D15 | 新 Boss 专属技能（GDD §7.2 点名未设计） | **暂复用既有 11 技能**，具名化推 Phase 7 |
| D16 | 亡灵/巨人羁绊数值 GDD 未给 | **自拟工作值待调**：亡灵=吸血线、巨人=血甲线（三档位语义与首发羁绊一致） |
| D17 | 英雄被动数据词表 | **`HeroPassiveType` 三值枚举**（词表即代码；扩被动先扩枚举） |
| D18 | CommandManager.history 回放轨消费 | **推 Phase 7**，history 现状零改动 |
| D19 | 场景/单位内容量级 | **最小集三场景齐发**（每场景 3 商店池单位 + 3 Boss + 2 羁绊 + 3 英雄传奇，units 合计 27） |
| D20 | 玩家坏档处理 | **删档重置 + 日志不炸**（与静态资源 fail-fast 区分：玩家档是运行期数据） |

## 3. 执行期自动裁决（E1~E11，libgdx-impl-executor）

均为「spec 预审代码与代码现状矛盾 / spec 内部自相矛盾 → 按 spec 精神自选」的偏差处理。

| # | 问题 | spec vs 现状 | 选择（推荐方案） | 理由 |
|---|------|--------------|------------------|------|
| E1 | 免费刷新不吃局外修正 | spec CP9 只改 RefreshShop 与 reroll 重载；`RunFlowSystem.startRun/advanceAfterVictory` 轮首免费 reroll 仍调 3 参（=EMPTY） | 两处调用点补传 `runState.getModifiers()` | 门控语义必须覆盖全部刷商店路径（否则森林局免费商店可刷出墓穴棋子/他人传奇，与手验 #8/#9 矛盾）；RNG 消耗序不变 |
| E2 | libGDX `JsonWriter` 无 `writeValue(String,int/long,String)` 重载 | spec CP16 预审代码编译失败 | `SnapshotCodec.write` 改 StringBuilder 手拼（与 ProfileCodec 同款） | 输出仍确定性、字段序固定；round-trip 测试锁定 |
| E3 | `HeroProgress` 无 equals | spec 测试要点以值等表达，但 spec 代码无 equals（引用等必挂） | 补 equals/hashCode | 不可变值对象语义，测试要点意图即值等 |
| E4 | 空档案 `{}` 读侧冲突 | spec 代码对 `{}` 抛「version 缺失」，测试要点要求「`{}` → fresh 语义」 | version 缺省视为当前版本（显式错误版本仍抛错） | 取测试要点口径；坏档重置链路不受影响 |
| E5 | refreshCost 测试数值自相矛盾 | spec 要点「折扣 1=2」与公式 `max(1, 2-discount)` 矛盾 | 按公式实现：EMPTY=2 / 折扣 1=1 / 折扣 5=1（下限） | 公式与 D4 裁决（实付下限 1 金）一致，要点数字系笔误 |
| E6 | units 增量数不一致 | spec §3.3「+12 条」vs CP11 清单 15 条、测试要点总数 27 | 按 CP11 清单落地（合计 27） | 清单与总数是 spec 自身权威口径 |
| E7 | T1 边界缺 heroes.json 内容 | CP2 交叉校验要求传奇 ∈ units（T4 才扩 units），T1 改加载器会红 | T1 落空数组占位，T4 填充 | spec 自注「T1/T4 同批提交」；保证每任务边界测试绿 |
| E8 | 存量 temp-dir 测试助手连锁 | 加载器增读 heroes.json 后，5 处临时目录缺该文件即死 | 五处助手补写 `heroes.json: "[]"`（沿 Phase 5 equipments 先例） | 机械连锁非行为变更；「缺 heroes.json 启动即死」另有专门用例锁定 |
| E9 | CP16 实现时点与 T 序冲突 | CP7 MetaService 委托 CP16 SnapshotStore，按 T 序 T2 无法编译 | 快照三件套随 T2 实现并测试，T7 槽位作全量检查点 | 编译依赖倒置；T7 验收件（round-trip/续战等价/流重放）全部在位且绿 |
| E10 | T5/T6/T8 的 BattleScreen 互锁 | 新构造链与 RunEndPanel 新签名跨任务互相锁定 | CP17 按三波落地（构造器随 T5 / RunEndPanel 随 T6 / 快照触发随 T8） | 每波编译与测试均绿，最终形态与 CP17 定稿逐行一致 |
| E11 | ShopBar 价签测试不可构造 | UI 测试无 Assets 实例化先例，spec 要求断言价签 | 价签抽为包级静态 `ShopBar.refreshPriceText(RunModifiers)` | 逻辑单测可达；绘制路径行为不变 |

## 4. 计划授权范围内的实现细节登记（不构成偏差）

1. **`SnapshotCodec` 定稿口径**：按 spec CP16 的 TODO 注释实现（equippedItemIndex 直存装备池下标、`read()` 全字段对称映射），另增「装备池下标越限」防御校验。
2. **`MetaServiceTest`**：spec §9 清单外的补充测试（覆盖 settleRun / 快照生命周期接线）。
3. **`ShopSystemTest` 概率断言方法**：单一连续 RNG 流 4000 次重掷的条件占比统计（夹具无 2 费模板，理论值 1/6→1/4；已用 python 复刻 LCG 预演）。
4. **`JsonLoader` 拆出 `config/JsonReadUtils.java`（195 行）**：Phase 6 新增解析段使该文件达 924 行超 800 硬上限，按「JsonValue 读取与校验工具」职责拆分（调用点静态导入零改动，拆后 765 行）。

## 5. 复核指引

- **优先复核建议**（影响玩家可感数值/规则，最值得人工过目）：D2（格雷克开局 14 金）、D3（通关熟练度 60+轮×3）、D4（Lv.4/5 工作值）、D5（+5pp 生效轮）、D12（薇拉增幅取整口径）、D16（亡灵/巨人数值线）、E1（免费刷新吃修正）。
- 数值类裁决（D4/D5/D12/D16 等「工作值待调」项）已集中登记，Phase 7 数值平衡批次统一复核即可。
- 结构类裁决（D7/D8/D10/D11/D14/D17/D20）已随 CP18 回写进 `data_schema_design.md` / `architecture_design.md`，与设计文档一致。
- 如需推翻某条：按对应「影响面」列定位代码点（spec §4 有完整影响面），改动后重跑 `gradlew test`（XML 聚合计数口径）。
