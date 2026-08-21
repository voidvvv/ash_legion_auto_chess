# Phase 2 波次生成开发实施计划

> **日期**：2026-08-21
> **范围**：Phase 2 —— 波次生成（GDD §十二路线图 / project_structure §六出生时间表）
> **依据文档版本**：GDD V0.13、data_schema V1.4、battle_design V1.6、architecture V1.8、project_structure V1.1
> **状态**：📋 实施计划待审阅（未开工）
> **分支**：`docs/phase2-wave-generator-plan`

---

## 一、范围界定（本次开工前经确认）

**做**：
- `assets/data/scenes.json`（仅森林场景最小闭环）
- `units.json` / `skills.json` 种子增补（2 个 Boss 模板 + 2 条具名技能）
- `data/SceneData`（场景 POJO，含 EnemyPoolEntry 内嵌类）
- `config/JsonLoader` 扩展（parseScenes + scenes 交叉校验）与 `data/GameData` 扩展（scenes 容器）
- `entities/WaveSpec`（敌阵规格，不可变中间产物）
- `systems/WaveGenerator`（半随机波次生成）
- `utils/RandomGenerator`（确定性随机，RNG 消耗点之首，architecture §六.1）
- `tools/WaveConsoleMain`（控制台模拟 25 轮输出）
- 以上全部配套单元测试（TDD：RED → GREEN）

**不做**（防蔓延）：
- `BattleUnit` / `BattleState` / `RunState` / `UnitRegistry` / `BattleSystem` / `SynergySystem`（Phase 3；WaveSpec → BattleUnit 派生属 Phase 3 属性管线，battle_design §八）
- 墓穴 / 雪山场景种子（随 24 棋子池补全，见 §十）
- 商店刷新 / 宝箱 roll 的 RNG 消耗点（Phase 5）；战斗内暴击 RNG（Phase 3）
- `Main.java` 改造与任何 Screen / 渲染（Phase 4；`lwjgl3:run` 仍为 logo 画面）
- 敌方羁绊的属性结算（Phase 3 从 WaveSpec 模板统计后走 battle §八修正管线；本期抽取允许同名重复，凑羁绊由池配置涌现）

---

## 二、口径确认记录（开工前 Q1~Q4，用户已定）

| # | 问题 | 结论 |
|---|------|------|
| Q1 | WaveGenerator 产出形态 | **中间产物 `WaveSpec`**（模板引用+星级+格坐标+强度系数）；`BattleUnit` 仍按 project_structure §六 在 Phase 3 出生，届时由 BattleSystem 派生。与 GDD §10.2 示意签名 `List<BattleUnit>` 的差异见 §三差异声明 |
| Q2 | scenes.json 种子程度 | **仅森林最小闭环**：现有 3 杂兵进 enemyPool + 新增 2 Boss 模板凑齐 7/15/25；墓穴/雪山延后。已知偏差：森林"野兽+游侠"倾向（GDD §7.4）随 24 棋子池补全再调整 |
| Q3 | Boss 轮敌阵口径 | **Boss 额外出场 + 不吃 k**：Boss 轮敌阵 = 人口锚点杂兵（×k）+ 1 个额外 Boss（烘焙终值，不再乘 k，遵循 data_schema §4.2"不做运行时倍率"） |
| Q4 | 自动布阵规则 | **规则式：按 range 推导**（阵型模板在任何文档中未定义，属空白）：近战前排、远程后排、行内中央向外；详见 §五.7。不新增任何 schema 字段 |

### 实现层口径（文档未明说、本次定的执行细节）

| # | 决定 | 依据/说明 |
|---|------|-----------|
| 1 | RNG = `java.util.Random` 封装 | JVM 规范保证跨平台位级确定、零 `Gdx.*` 依赖、JUnit 直测（分层约束，project_structure §四） |
| 2 | 敌方星级恒为 **1** | GDD 未定义敌方升星；k 系数即敌方成长轴（§7.3） |
| 3 | 抽取为**按权重有放回**（允许同名重复） | GDD §7.1"敌方初级羁绊（如 2 战士）"隐含同名/同类凑对 |
| 4 | `weight` 为**正整数**（≥1） | 沿项目"JSON 不出现易错小数"精神（data_schema §三刻度约定同源） |
| 5 | `bosses` 三键 **{7, 15, 25} 必须齐全** | 第 25 轮为通关轮必须有最终 Boss（GDD §2.1/§7.2）；缺键启动即死 |
| 6 | 布阵行号口径（对 Q4 选项文字的修正） | 提问时表述"近战放敌区第 0 行（最前排）"有误：敌区 0~2 行、玩家 4~6 行、缓冲带第 3 行（GDD §4.1），敌方自上而下推进，**邻缓冲带的第 2 行才是先接敌的前排**，第 0 行为敌方纵深最后排。规则实质不变（近战前、远程后），仅行号语义修正为：近战目标排 `[2,1,0]`、远程 `[0,1,2]` |
| 7 | RNG 消耗计数 | 每次底层随机 +1 计数器；`weightedPick` 每次 1 个随机数 → WaveGenerator 每轮消耗 = 杂兵人数（Boss 为确定性映射，不消耗）。供测试断言与回放审计（architecture §六清单管理的落点） |
| 8 | `unlockAfter` 校验 | 引用存在 + 禁自指 + 前置链成环报错（档案域解锁判定本身 Phase 6 才实现，本期仅加载期校验） |

---

## 三、总体设计

### 3.1 数据流

```
scenes.json ─┐
units.json ──┤              ┌─→ GameData（+scenes 查找表，不可变）
skills.json ─┼→ JsonLoader ─┤        │
synergies.json┘   (+parseScenes     │ generateEnemyWave(round, sceneId, data, rng)
                   +交叉校验)        ▼
                              WaveGenerator ──→ List<WaveSpec>（杂兵抽取序 + Boss 殿后）
                                 │  ▲                  │
                        GameBalance│（已就绪）          ▼
                    enemyCount/enemyScale/isBossRound   （Phase 3：BattleSystem 按
                                 │                      battle §八管线派生 BattleUnit）
                        RandomGenerator（注入 seed）     本期终点
```

### 3.2 与技术文档的差异声明（如实记录，不改文档）

| 文档原文 | 本实施 | 理由 |
|---------|--------|------|
| GDD §10.2：`generateEnemyWave` 返回 `List<BattleUnit>`，"敌方单位由 WaveGenerator 直接构造" | 返回 `List<WaveSpec>` | 用户确认 Q1：BattleUnit 依赖战斗细则（弹道/能量/H 语义），提前定型有返工风险；出生时间表（project_structure §六，V1.1 评审特意补 BattleUnit 到 Phase 3）保持不变 |
| GDD §10.2：签名 `(int round, String sceneId)` | `(int round, String sceneId, GameData data, RandomGenerator rng)` | rng 显式注入是确定性回放的前提（architecture §六）；GameData 用于 enemyPool 的 unitId → 模板解析 |
| GDD §7.3："敌方棋子数值 = 模板基础值 × k"（未排除 Boss） | 杂兵 ×k；Boss 用烘焙值不乘 | 用户确认 Q3；与 data_schema §4.2"数值已烘焙、不做运行时倍率"对齐（该处与 GDD §7.3 原文存在口径冲突，按用户裁决执行） |
| GDD §7.3："按预设阵型模板（前排肉+后排输出）自动布阵" | 规则式按 range 推导，无阵型数据 | 用户确认 Q4；"阵型模板"任何文档均未定义（空白），不新增 schema 字段 |
| project_structure §六："控制台模拟入口"（未指定包） | 新建 `tools/` 包放 `WaveConsoleMain` | 不污染 systems 逻辑包；Phase 4 后保留为数值调试工具或删除均可 |

---

## 四、变更清单

### 新增：种子数据（`assets/data/`）

| 文件 | 变更 | 内容 |
|------|------|------|
| `scenes.json` | **新建**（1 场景） | scene_forest 翡翠林地（见 §五.1） |
| `units.json` | **增补 2 条**（4 → 6） | boss_one_eye（第 15 轮）、boss_thorn_true（第 25 轮），数值已烘焙 |
| `skills.json` | **增补 2 条**（9 → 11） | skill_pierce_sky 穿云箭、skill_thorn_sea 荆棘海 |

### 新增：`core/src/main/java/.../`

| 文件 | 包 | 职责 |
|------|-----|------|
| `data/SceneData.java` | data | 场景模板（enemyPool + bosses 映射），不可变，含 `EnemyPoolEntry` 内嵌类（沿 SynergyData.Threshold 先例） |
| `entities/WaveSpec.java` | entities | 敌阵规格（不可变）：模板引用 + 星级 + 强度系数 + 格坐标 |
| `systems/WaveGenerator.java` | systems | 半随机波次生成（无状态实例类） |
| `utils/RandomGenerator.java` | utils | 确定性随机 + 加权抽取 + 消耗计数 |
| `tools/WaveConsoleMain.java` | tools（新包） | 控制台模拟 25 轮（普通 main，不动 Main.java） |

### 修改：`core/src/main/java/.../`

| 文件 | 变更 |
|------|------|
| `config/JsonLoader.java` | 新增 `parseScenes()`；`load` 签名三文件 → **四文件**（旧三参签名删除）；`loadFromDirectory` 增读 scenes.json；交叉校验新增 scenes 组（§五.4） |
| `data/GameData.java` | 构造器增第 5 参 `Map<String, SceneData> scenes`；新增 `getScene(id)` / `getScenes()`；不可变包装同现有风格 |

> **破坏性变更提示**：GameData 构造器与 JsonLoader.load 签名变更，现有测试调用点（JsonLoaderTest / JsonLoaderValidationTest 夹具）需同步迁移，见 §六。

---

## 五、详细设计

### 5.1 `scenes.json` 种子（森林最小闭环）

```json
[
  {
    "id": "scene_forest",
    "name": "翡翠林地",
    "unlockAfter": null,
    "enemyPool": [
      { "unitId": "unit_warrior_01",  "weight": 3, "minRound": 1 },
      { "unitId": "unit_ranger_01",   "weight": 2, "minRound": 2 },
      { "unitId": "unit_assassin_01", "weight": 2, "minRound": 5 }
    ],
    "bosses": { "7": "boss_thorn_mother", "15": "boss_one_eye", "25": "boss_thorn_true" }
  }
]
```

- 字段结构与 data_schema §七完全一致（结构锁定版，无增删改字段）
- **权重 / minRound 为工作值（待调）**：刺客（3 费）minRound=5 对应 GDD §3.4"3 费约第 8 轮起"的敌侧弱化版；游侠 minRound=2 提供远程位多样性
- 已知偏差（Q2 确认接受）：森林倾向应为"野兽+游侠"（GDD §7.4），现有种子 3 杂兵为兽人战士/丛林游侠/暗夜刺客——正式倾向随 24 棋子池补全调整，本期仅为可运行闭环

### 5.2 `units.json` / `skills.json` 种子增补

**units.json +2（Boss 数值烘焙，data_schema §4.2；基准与 boss_thorn_mother 一致：2 费模板 hp 500 / attack 21）**：

```json
{ "id": "boss_one_eye", "name": "独眼猎神", "race": "独眼", "class": "Boss", "cost": 0,
  "baseStats": { "hp": 1250, "attack": 42, "armor": 12, "attackSpeed": 1.1, "range": 3, "moveSpeed": 0.8 },
  "upgradeMultiplier": 1.0,
  "defaultPriority": "NEAREST", "specialPriority": null,
  "skillId": "skill_pierce_sky",
  "boss": true },

{ "id": "boss_thorn_true", "name": "荆棘之母·真体", "race": "植物", "class": "Boss", "cost": 0,
  "baseStats": { "hp": 1500, "attack": 52, "armor": 25, "attackSpeed": 0.9, "range": 1, "moveSpeed": 0.6 },
  "upgradeMultiplier": 1.0,
  "defaultPriority": "NEAREST", "specialPriority": null,
  "skillId": "skill_thorn_sea",
  "boss": true }
```

- 烘焙算例：独眼猎神 = 普通Boss（hp 500×2.5=1250、attack 21×2.0=42），"攻击 ×2.0"加强方向体现在远程（range 3）+ 高攻速；真体 = 最终Boss（hp 500×3.0=1500、attack 21×2.5=52.5 → **52** 取整）。**数值全部待调**（GDD §7.2/§十一）
- race "独眼"/"植物"、class "Boss" 均为风味值（未登记羁绊，计入聚合软告警，data_schema §九.4）

**skills.json +2（对齐 GDD §7.2 Boss 专属技能表）**：

```json
{ "id": "skill_pierce_sky", "name": "穿云箭", "desc": "贯穿苍穹的致命一击",
  "shape": "SINGLE_TARGET", "delivery": "HOMING",
  "effects": [ { "effect": "DAMAGE", "value": 3.0 } ] },

{ "id": "skill_thorn_sea", "name": "荆棘海", "desc": "荆棘自四面八方涌起，吞没全场（最终Boss）",
  "shape": "ALL_ENEMIES", "delivery": "MELEE_INSTANT",
  "effects": [ { "effect": "DAMAGE", "value": 1.5 } ] }
```

- GDD §7.2 原文对照：独眼猎神=穿云箭（SINGLE_TARGET 大倍率）✓；荆棘之母·真体=荆棘海（ALL_ENEMIES）✓；荆棘之母=荆棘藤蔓（已有 skill_thorn_vine）✓
- ALL_ENEMIES 被 Boss 引用不触发软告警（§九.6 豁免 Boss）

### 5.3 `data/SceneData`（不可变 POJO）

```java
public final class SceneData {
    private final String id;
    private final String name;
    /** 解锁前置场景 id；null = 初始开放（data_schema §七） */
    private final String unlockAfter;
    private final List<EnemyPoolEntry> enemyPool;   // unmodifiable，保持 JSON 声明序
    private final Map<Integer, String> bosses;      // {7,15,25} → unitId，unmodifiable

    /** 查 Boss 轮映射；非 Boss 轮返回 null */
    public String getBossUnitId(int round) { ... }

    public static final class EnemyPoolEntry {
        private final String unitId;   // 加载期校验必 ∈ units
        private final int weight;      // ≥ 1（实现层口径 #4）
        private final int minRound;    // 1~25
    }
    // 构造器 + getter，无 setter（沿 UnitData 风格）
}
```

### 5.4 `config/JsonLoader` 扩展

**解析**（复用现有工具方法 requireString / requireInt / optionalVocab / checkUnknownKeys，报错格式 `scenes.json#条目id/字段路径: 问题`）：

- 条目允许字段：`id / name / unlockAfter / enemyPool / bosses`（未知字段一律死）
- `unlockAfter`：可选，null 放行
- `enemyPool[]`：非空数组；条目允许字段 `unitId / weight / minRound`，三者必填
- `bosses`：对象；键解析为 int 必须 ∈ `GameBalance.BOSS_ROUNDS`，**三键 {7,15,25} 齐全**（实现层口径 #5）

**交叉校验新增 scenes 组**（编号续 data_schema §九）：

| # | 规则 | 来源 |
|---|------|------|
| S1 | `enemyPool[].unitId` 必 ∈ units.json（悬空即死） | **文档明文** §九.4 |
| S2 | `boss:true` 模板不得出现在 enemyPool 权重位 | **文档明文** §九.6 |
| S3 | `bosses` 值必 ∈ units.json（悬空即死）；且被引用模板必须 `isBoss()` | 前半**文档明文** §九.4；后半**实现延伸**（§九.6 的对称防御：防杂兵模板占 Boss 位） |
| S4 | `unlockAfter`：null 或必 ∈ 场景 id 集；≠ 自身；多场景前置链**成环报错** | 字段语义 §七；校验细则为**实现延伸**（实现层口径 #8） |
| S5 | 每场景 enemyPool **至少一条 minRound ≤ 1** | **实现延伸**：S1 的运行时推论——否则第 1 轮可用池为空，WaveGenerator 无兵可抽 |
| S6 | `weight ≥ 1`；`minRound ∈ [1, 25]` | **实现延伸**（边界防御，风格同 §九.2） |

**签名变更**：

```java
// 旧（删除）：load(FileHandle units, skills, synergies)
// 新：
public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                            FileHandle synergiesFile, FileHandle scenesFile)
public static GameData loadFromDirectory(FileHandle dataDir)  // 增读 scenes.json
```

### 5.5 `data/GameData` 变更

- 构造器：`GameData(units, skills, synergies, scenes, warnings)`（scenes 插在 synergies 后、warnings 前）
- 新增：`getScene(String id)` / `getScenes()`（unmodifiable LinkedHashMap，保持声明序，同现有风格）

### 5.6 `utils/RandomGenerator`

```java
/** 确定性随机（architecture §六 RNG 消耗点清单的唯一入口）。
 *  封装 java.util.Random（实现层口径 #1），附带消耗计数供回放审计与测试断言。 */
public final class RandomGenerator {
    private final Random random;        // java.util.Random
    private int consumedCount;

    public RandomGenerator(long seed) { ... }

    public int nextInt(int bound)               // 委托，消耗 +1
    public float nextFloat()                    // 委托，消耗 +1
    /** 按权重抽取，返回命中索引；每次消耗恰好 1 个随机数。
     *  实现：r = nextFloat() * sum(weights)；线性累积扫描，浮点边界钳制到最后一个有效索引；
     *  weight ≤ 0 的条目永不命中；全 ≤ 0 抛 IllegalArgumentException。 */
    public int weightedPick(int[] weights) { ... }
    public int getConsumedCount() { ... }
}
```

- 确定性保证：weights 数组顺序 = enemyPool 声明序（LinkedHashMap 遍历序），同 seed 同序列

### 5.7 `entities/WaveSpec` + `systems/WaveGenerator`

**WaveSpec（不可变，实现 equals/hashCode 供确定性对拍断言）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `template` | `UnitData` | 模板**直接引用**（生成期已经 GameData 解析；模板终身只读，持引用安全） |
| `star` | `int` | 星级，**Phase 2 恒 1**（实现层口径 #2）；字段保留供 Phase 3 派生公式 `baseStats × starStatMultiplier × scale` |
| `scale` | `float` | 已应用的强度系数：**杂兵 = k，Boss = 1.0**（Q3：烘焙值不二次放大） |
| `gridX` / `gridY` | `int` | 敌区落位（gridY ∈ 0~2） |

Boss 判定经 `template.isBoss()`，不冗余存储。

**WaveGenerator（无状态实例类，方法对齐 GDD §10.2 命名）**：

```java
public final class WaveGenerator {
    /** 生成第 round 轮敌阵（sceneId 场景）。RNG 消耗 = 杂兵人数（Boss 不消耗）。
     *  返回顺序：杂兵按抽取序在前，Boss 殿后——列表序即确定性序。 */
    public List<WaveSpec> generateEnemyWave(int round, String sceneId,
                                            GameData data, RandomGenerator rng) { ... }
}
```

**算法步骤（固定序，每步确定性）**：

```
0. scene = data.getScene(sceneId)；null → IllegalArgumentException
   （round 合法性由 GameBalance.enemyCount/enemyScale 内聚校验）
1. n = GameBalance.enemyCount(round)          // 人口锚点插值取整（已实现）
   k = GameBalance.enemyScale(round)          // k = 1 + 0.1×(轮−1)（已实现）
2. 可用池 = scene.enemyPool 过滤 minRound ≤ round（保持声明序）
   空 → IllegalStateException（防御：S5 已保证第 1 轮非空、后续轮池单调不减，理论不可达）
3. 杂兵：i = 1..n：
     idx = rng.weightedPick(可用池权重)       // 有放回，允许同名重复（口径 #3）
     (x, y) = place(模板)                      // 布阵规则见下
     产出 WaveSpec(template, 1, k, x, y)
4. Boss 轮（GameBalance.isBossRound(round)）：
     bossId = scene.getBossUnitId(round)       // S5/S6 保证必命中
     (x, y) = place(bossTemplate)              // 最后放置，同一规则
     产出 WaveSpec(bossTemplate, 1, 1.0f, x, y) 殿后追加
5. 返回列表（不可变包装）
```

**布阵规则（Q4 规则式，行号口径 #6）**——棋盘 6 列 × 7 行，敌区 0~2 行、缓冲带第 3 行（GDD §4.1）：

| 规则 | 定义 |
|------|------|
| 分排 | `template.baseStats.range ≤ 1`（近战）→ 目标排序列 **[2,1,0]**（第 2 行邻缓冲带先接敌）；`range ≥ 2`（远程）→ **[0,1,2]**（第 0 行为敌方纵深最后排） |
| 行内列序 | 全部按 **[2, 3, 1, 4, 0, 5]**（6 列中央向外） |
| 放置 | 单位依次（先抽取先放置，Boss 最后）在自己的目标排序列中逐排找"该排按列序第一个空格"；整列序放满进下一排 |
| 容量 | 敌区 18 格 ≥ 最大 9 单位（杂兵锚点峰值 8 + Boss 1），理论不溢出；放满仍无位 → IllegalStateException（防御） |
| 语义 | "前排肉 + 后排输出"（GDD §7.3）：近战贴缓冲带承伤，远程纵深输出 |

### 5.8 `tools/WaveConsoleMain`（控制台模拟入口）

```java
public final class WaveConsoleMain {
    /** args[0] = seed（缺省 42）；args[1] = dataDir（缺省 ../assets/data）。
     *  加载种子（软告警打印后继续）→ 逐轮生成 → 打印表格。 */
    public static void main(String[] args) { ... }
}
```

- `FileHandle` 直接 `new FileHandle(File)` 构造（gdx-files 纯 JVM，无 GL；分层允许，project_structure §四例外条款）
- 不改动 `Main.java`（Phase 4 才改造为 Game）
- 输出格式（示例，实测输出以运行为准）：

```
=== 余烬军团 · 波次模拟（seed=42, scene=scene_forest）===
轮  1 | k=1.0 | 杂兵 1 | (2,2) 兽人战士        scale=1.0
轮  5 | k=1.4 | 杂兵 3 | (2,2) 兽人战士 (0,0) 丛林游侠 (1,3) 丛林游侠 ...
轮  7 | k=1.6 | 杂兵 4 + Boss | ... (2,2) 荆棘之母[Boss] scale=1.0
...
轮 25 | k=3.4 | 杂兵 8 + Boss | ... (2,2) 荆棘之母·真体[Boss] scale=1.0
=== RNG 消耗合计：N 次 ===
```

---

## 六、TDD 测试计划（RED → GREEN → REFACTOR）

### 新增测试（镜像包结构）

| 测试类 | 预估例数 | 覆盖 |
|--------|---------|------|
| `config/JsonLoaderScenesTest` | ≈15 | 种子正向全字段（三 Boss 键/池条目/unlockAfter null）；S1 unitId 悬空死；S2 Boss 模板入池死；S3 Boss 位悬空死 / Boss 位引用非 Boss 模板死；S5 无 minRound≤1 条目死；S6 weight=0 / minRound=0 / minRound=26 死；bosses 缺键（如只有 7/15）死；bosses 键非法（"8"）死；S4 unlockAfter 悬空 / 自指 / A→B→A 成环死；enemyPool 空数组死；未知字段死；`getBossUnitId` 查询（7/15/25 命中、其余 null） |
| `systems/WaveGeneratorTest` | ≈13 | 人口锚点抽检（第 1/3/5/8/12/16/20/25 轮 = 1/2/3/4/5/6/7/8）；Boss 轮总数 = 锚点+1 且含对应 Boss（7→荆棘之母/15→独眼猎神/25→真体）；杂兵 scale = k（第 5 轮 1.4 / 第 25 轮 3.4）；Boss scale = 1.0；star 恒 1；minRound 门控（第 1 轮仅出战士；第 4 轮无刺客）；近战落位第 2 行（全近战池夹具）；远程自第 0 行起；全 25 轮无坐标冲突且 gridY ∈ 0~2、gridX ∈ 0~5；同 seed 两次生成全等（equals）；固定两个不同 seed 至少一轮构成不同；RNG 消耗 = 杂兵数（第 7 轮 = 4）；场景不存在抛错 |
| `utils/RandomGeneratorTest` | ≈7 | 同 seed 序列逐位一致；nextInt 值域/边界；nextFloat ∈ [0,1)；weightedPick 单权必中 / 零权永不中 / 固定 seed 比例对拍；全零权抛错；消耗计数准确（含 weightedPick 恰 1 次） |
| `data/SceneDataTest` | ≈2 | getBossUnitId 行为；池/映射容器不可变 |

### 存量测试迁移（不新增断言，仅适配签名）

| 测试类 | 变更 |
|--------|------|
| `JsonLoaderTest` | load 调用改四参；直接 new GameData 的调用点改五参构造器；夹具补 scenes 最小集（新增 `withScenes()` 夹具工具，@TempDir 现写风格不变） |
| `JsonLoaderValidationTest` | 同上；其"最小合法集"夹具需含一条合法 scenes 条目（否则加载即死） |
| `GameBalanceTest` / `SynergyDataTest` / `PlayerTest` / `AnchorTableTest` | 不动 |

---

## 七、验收标准

1. `gradlew core:test` 全绿：存量 62 例迁移后保持全绿 + 新增 ≈37 例，合计 ≈99 例
2. `tools/WaveConsoleMain`（缺省 seed=42）完整输出 25 轮：锚点轮人口/k 与 GameBalance 公式一致；Boss 轮（7/15/25）均含对应 Boss 且 scale=1.0；RNG 消耗合计 = Σ enemyCount(round) = **129** 次（已按 AnchorTable 分段线性插值 + `Math.round`（floor(x+0.5)）逐轮核算：1~25 轮人数 = 1,2,2,3,3,3,4,4,4,5,5,5,5,6,6,6,6,7,7,7,7,7,8,8,8）
3. 确定性：同 seed 两次生成/两次运行输出逐位一致（测试断言 + 控制台人工复核）
4. 种子软告警变化符合预期（见 §八）
5. 分层约束复查：新增类零 `Gdx.*` 调用（`data/config` 仅许 Json/FileHandle；`WaveConsoleMain` 用纯 JVM 构造的 FileHandle）

---

## 八、种子软告警预期变化（加载 assets/data）

| 告警类 | Phase 1 现状 | Phase 2 预期 | 说明 |
|--------|-------------|--------------|------|
| 孤儿技能 | 5 条（暴走/群体治疗/超远程狙击/星陨/毒雾弹） | **不变 5 条** | 新增穿云箭/荆棘海被 2 个新 Boss 引用，非孤儿；星陨仍孤儿（星骸守卫属雪山场景） |
| 孤儿羁绊 | 2 条（syn_beast / syn_mage） | **不变 2 条** | 森林池无野兽/法师 |
| 风味聚合 | 暗夜、精灵、植物、Boss | **+独眼** | boss_one_eye.race="独眼"（风味） |

---

## 九、实现顺序（建议提交切分）

1. `RandomGenerator` + 测试（零依赖，先行）
2. `SceneData` + `JsonLoader.parseScenes` + `GameData` 扩展 + `JsonLoaderScenesTest`（含存量夹具迁移——此步完成后 `core:test` 必须恢复全绿）
3. 种子数据落盘（scenes.json 新建 + units/skills 增补；告警变化按 §八核对）
4. `WaveSpec` + `WaveGenerator` + `WaveGeneratorTest`
5. `WaveConsoleMain` + 全量验收（§七）

---

## 十、风险与后续

| 项 | 说明 |
|----|------|
| GameData/JsonLoader 签名破坏性变更 | 波及存量夹具，已列 §六迁移清单；若实施中发现其他调用点，随改随记 |
| 森林池与 §7.4 倾向偏差 | 种子最小闭环的已知项（Q2 接受）；24 棋子池补全时一并消解孤儿告警 |
| WaveSpec.scale 的 Phase 3 契约 | 派生公式 = `baseStats × starStatMultiplier(star) × scale` 再走装备/羁绊修正管线（battle §八合成顺序写死：先加后乘）；本期只定义字段不实现结算 |
| 敌方羁绊 | Phase 3 从 WaveSpec.template 的 race/class 统计（双通道计数同玩家侧）后经 SynergySystem 结算；本期有放回抽取已为同名凑对留好口子 |
| bosses JSON 数字键顺序 | 解析按键显式取值（"7"/"15"/"25"），与对象内声明顺序无关 |
| 后续待办 | 24 棋子池 + 墓穴/雪山场景（内容阶段）；`scenes.json` 三场景骨架的数据制作以本文档 §5.1 结构为准 |
