# Phase 1 数据层开发总结

> **日期**：2026-08-21
> **范围**：Phase 1 —— 数据层（GDD §十二路线图 / project_structure §六出生时间表）
> **依据文档版本**：GDD V0.13、data_schema V1.4、battle_design V1.6、architecture V1.8、project_structure V1.1
> **状态**：✅ 开发完成，`gradlew core:test` 全绿（6 测试类 / 62 例 / 0 失败），**变更未提交**

---

## 一、范围界定（本次开工前经确认）

**做**：种子 JSON、`data/` 词表与 POJO、`config/`（JsonLoader + GameBalance）、`entities/Player`（Phase 1 精简版）、`utils/`、镜像测试。

**不做**（防蔓延）：
- `scenes.json` + WaveGenerator（Phase 2）
- `Unit` / `BattleUnit` / `BattleState` / SynergySystem / BattleSystem（Phase 3）
- Screen / 输入 / 渲染 / Main.java 改造（Phase 4，`lwjgl3:run` 仍为 logo 画面）
- `equipments.json` / ShopSystem（Phase 5）、`heroes.json` / 存档（Phase 6）
- RandomGenerator（数据加载不消耗随机数，Phase 2 随 WaveGenerator 引入）

**口径确认**（开工前 Q1~Q4，已随文档 V1.3/V1.4 提交定稿）：
| # | 问题 | 结论 |
|---|------|------|
| Q1 | 种子 synergies 写多少 | **6 羁绊全档位**（兽人/战士/法师/刺客/野兽/游侠，2/4/6 工作值待调） |
| Q2 | 无羁绊种族/职业校验口径 | 不报错，运行时不计；加载期末**去重聚合软告警**（与孤儿羁绊告警双向对称） |
| Q3 | 可选属性缺省值 | `lifesteal=0 / skillPower=0 / energyGainRate=100`，**百分点整数刻度，结算 ÷100** |
| Q4 | JSON 解析方式 | **JsonReader → JsonValue 手工映射**（不用 gdx Json 反射），报错精确到文件/条目/字段路径；Java 字段 `unitClass` 对应 JSON `"class"` |

---

## 二、变更清单

### 新增：种子数据（`assets/data/`）
| 文件 | 内容 | 说明 |
|------|------|------|
| `units.json` | 4 条 | 兽人战士(1费) / 暗夜刺客(3费) / 丛林游侠(2费) / 荆棘之母(Boss, cost=0, 数值已烘焙) |
| `skills.json` | 9 条 | 战吼/处决/贯穿箭/荆棘藤蔓/暴走(双状态)/群体治疗/超远程狙击/星陨(ALL_ENEMIES)/毒雾弹(AOE_2+POISON) |
| `synergies.json` | 6 条 | 首发 6 羁绊 2/4/6 全档位；吸血走 `stat: lifesteal`（effect 通道废弃） |

### 新增：`core/src/main/java/.../data/`（19 个文件）
- **词表枚举（9）**：`SkillShape`(7) / `SkillEffectType`(4) / `StatKey`(9 键，含百分比三键标记与缺省值) / `TargetPriority` / `Delivery` / `StatusType`(9) / `EffectOp` / `EffectTarget` / `SynergySource`，均实现 `Vocab` 接口（`jsonName()` 统一 JSON 字面值映射）
- **不可变 POJO（7）**：`UnitData` / `BaseStats` / `SkillData` / `SkillEffect` / `EffectData`（synergies 与 equipments 共用词汇）/ `SynergyData`（含 `activeThreshold()` 档位替换制判定）/ `GameData`（聚合容器，查找表不可变）

### 新增：`core/src/main/java/.../config/`
- `JsonLoader`：加载 + §九校验清单全项（fail-fast），软告警汇入 `GameData.getWarnings()`；不调用 `Gdx.*`（分层约束）
- `GameBalance`：§十常量表全量 + 公式（升星倍率/技能星级缩放/敌方强度/人口/宝箱金币/费阶概率/经验人口表/Boss 轮判定），参数越界抛 `IllegalArgumentException`
- `DataValidationException`：消息格式 `文件#条目id/字段路径: 问题`

### 新增：`entities/Player`（Phase 1 精简版）
- 金币/经验/等级 + `addExp` 连续升级 + `getPopulationCap()`；名单字段（bench/上场）随 Unit 实体 Phase 3 增补
- 无 health 字段（1C-R 重试制，决策 2026-08-19）

### 新增：`utils/AnchorTable`
- 锚点分段线性插值（二分查找），越界钳制端点值；敌方人口曲线与商店费阶概率三表共用

### 新增：`core/src/test/java/`（6 测试类，62 例）
| 测试类 | 例数 | 覆盖 |
|--------|------|------|
| `GameBalanceTest` | 14 | 升星 ×1/1.8/3.24、技能星级 ×1/1.5/2.0、k 系数锚点、人口锚点与单调性、宝箱曲线与 Boss 双倍、费阶概率锚点/插值/和恒 100、经验人口表、常量快照、Boss 轮判定 |
| `JsonLoaderTest` | 9 | 种子全字段断言、缺省值（NEAREST / 0/100/0 / 1.8）、Boss 烘焙值、羁绊档位与 stat/effect 通道、加载后门槛判定集成、软告警精确断言、容器不可变 |
| `JsonLoaderValidationTest` | 25 | §九逐条负断言 + BOM 容错 + 最小合法集零告警（@TempDir 现写夹具） |
| `SynergyDataTest` | 4 | 档位替换制（2/4/6 与 3/5/7 两种风格）、高档全量生效 |
| `PlayerTest` | 6 | 经验升级/余数/连跳封顶、金币钳 0、canAfford |
| `AnchorTableTest` | 4 | 锚点命中/插值/钳制/构造校验 |

### 修改：`core/build.gradle`
```gradle
testRuntimeOnly "org.junit.platform:junit-platform-launcher:1.11.4"
```
**原因**：Gradle 9 要求测试运行时显式提供 JUnit Platform Launcher，否则 `Could not start Gradle Test Executor 1`。此前文档验证时测试源集为空未暴露此问题。

---

## 三、测试结果

```
gradlew core:test  →  BUILD SUCCESSFUL（EXIT=0）

com...config.GameBalanceTest:          tests=14 fail=0 err=0
com...config.JsonLoaderTest:           tests=9  fail=0 err=0
com...config.JsonLoaderValidationTest: tests=25 fail=0 err=0
com...data.SynergyDataTest:            tests=4  fail=0 err=0
com...entities.PlayerTest:             tests=6  fail=0 err=0
com...utils.AnchorTableTest:           tests=4  fail=0 err=0
```

TDD 过程：先写测试 + 骨架（RED：28 例 23 失败）→ 实现（GREEN：全过）。

---

## 四、实现层口径（文档未明说、本次做的决定）

| # | 决定 | 依据/说明 |
|---|------|-----------|
| 1 | 敌方人口插值后 **Math.round 取整** | GDD §7.3 只说"线性插值"未定取整规则（第 2 轮 1.5 → 2 人）；结果全程单调不降（有测试保证） |
| 2 | **Lv.7 封顶后经验余量作废**（currentExp 清零，再买经验无效） | GDD 未定义封顶后行为 |
| 3 | **未知 JSON 字段一律报错**（含 baseStats 内部） | fail-fast 防拼写错误静默失效（如 `atackSpeed`），比文档更严 |
| 4 | 百分比三键（lifesteal/energyGainRate/skillPower）负值拒绝（≥0） | 文档只定缺省未定下界 |
| 5 | GameBalance 参数越界抛 IllegalArgumentException（星 1~3 / 轮 1~25 / 级 1~7） | 防御式编程，防非法输入流入公式 |
| 6 | 非 APPLY_STATUS 效果携带 status/duration 报错 | 效果字段配平的严格化（文档仅要求"APPLY_STATUS 必有"，未禁其他类型携带） |
| 7 | `Player.addGold` 防御性钳 0（不为负） | 命令层已校验，此处兜底 |
| 8 | 同 source 下 synergy key 重复登记报错（如两条 RACE:兽人） | 防同 key 双羁绊重复计数，文档隐含未明文 |
| 9 | 词表解析失败时报错**列出全部合法值** | 提升数据制作排错效率 |

> 以上 1/2 若需改口径（如 floor 取整、封顶保留经验），只改 `GameBalance`/`Player` 单点 + 对应测试。

---

## 五、种子数据的软告警现状（预期行为，非缺陷）

加载 `assets/data` 种子会产出 8 条软告警：
- **孤儿技能 ×5**：暴走/群体治疗/超远程狙击/星陨/毒雾弹（种子 4 棋子只引用 4 个技能）
- **孤儿羁绊 ×2**：`syn_beast`（野兽）/ `syn_mage`（法师）——种子单位无野兽种族、无法师职业
- **风味聚合 ×1**：暗夜、精灵、植物、Boss（未登记羁绊，不计计数）

随 Phase 1+ 补全 24 棋子池，这些告警会自然消解。

---

## 六、过程问题记录

| 问题 | 处置 |
|------|------|
| 中文命名的 Java 测试方法（其一以数字开头无法编译） | **规则：Java 标识符一律英文**，中文说明走 `@DisplayName`/注释；已全部重写并记入长期记忆 |
| JsonLoader 三处编译错误（重载残留 / String[] 非 Iterable） | 修复后全绿 |
| 验证测试夹具 5 例失败：JSON 数组拼接落在 `]` 之外 + 最小夹具职业未登记羁绊 | 提取 `withExtraElement()` 夹具工具 + 最小夹具补 CLASS 战士羁绊，修正后全绿 |
| Gradle 输出在同步管道下为空 | 以 `core/build/test-results/test/*.xml`（JUnit 报告）为结果事实源 |

---

## 七、后续

- [ ] 变更提交（待确认）
- [ ] Phase 2：`scenes.json` + `WaveGenerator`（半随机池 + 控制台模拟 25 轮）+ RandomGenerator（确定性随机，RNG 消耗点之一）
- [ ] 种子 4 棋子 → 24 棋子池补全（消解孤儿告警；数值表 GDD 待定项）
