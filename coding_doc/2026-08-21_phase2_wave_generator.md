# Phase 2 波次生成开发总结

> **日期**：2026-08-21
> **范围**：Phase 2 —— 波次生成（GDD §十二路线图 / project_structure §六出生时间表）
> **依据文档版本**：GDD V0.13、data_schema V1.4、battle_design V1.6、architecture V1.8、project_structure V1.1
> **实施计划**：`coding_plan/2026-08-21_phase2_wave_generator.md`（Q1~Q4 口径确认与实现层口径 #1~#8 见该文档 §二，本文不重复）
> **状态**：✅ 开发完成并已提交（4 个 feat 提交，切分见 §六）；`gradlew core:test` 全绿（10 测试类 / 104 例 / 0 失败）

---

## 一、范围界定

**做**：`scenes.json` 种子（森林最小闭环）、`units.json`/`skills.json` 增补（2 Boss 模板 + 2 具名技能）、`data/SceneData`、`config/JsonLoader` 扩展（parseScenes + 交叉校验 S1~S6）、`data/GameData` 扩展、`entities/WaveSpec`、`systems/WaveGenerator`、`utils/RandomGenerator`、`tools/WaveConsoleMain`、全部配套单元测试。

**不做**（防蔓延，同计划 §一）：`BattleUnit`/`BattleState`/`RunState`/`UnitRegistry`/`BattleSystem`/`SynergySystem`（Phase 3）；墓穴/雪山场景种子（随 24 棋子池补全）；商店刷新/宝箱 roll 的 RNG 消耗点（Phase 5）；战斗内暴击 RNG（Phase 3）；`Main.java` 改造与任何 Screen/渲染（Phase 4）；敌方羁绊属性结算（Phase 3 从 WaveSpec 模板统计后走 battle §八修正管线）。

**口径确认**（Q1~Q4 摘要）：产出形态为中间产物 `WaveSpec`（Q1）；种子仅森林闭环（Q2）；Boss 额外出场且不吃 k（Q3）；布阵规则式按 range 推导、不新增 schema 字段（Q4）。

---

## 二、变更清单

### 新增：种子数据（`assets/data/`）
| 文件 | 变更 | 内容 |
|------|------|------|
| `scenes.json` | 新建（1 场景） | scene_forest 翡翠林地：池 = 战士(w3, r1) / 游侠(w2, r2) / 刺客(w2, r5)；bosses = 7→荆棘之母 / 15→独眼猎神 / 25→荆棘之母·真体 |
| `units.json` | 增补 2 条（4→6） | boss_one_eye（hp1250/atk42/甲12/攻速1.1/**range3**/移速0.8，远程加强方向）、boss_thorn_true（hp1500/atk52/甲25/range1/移速0.6）；数值烘焙不乘 k |
| `skills.json` | 增补 2 条（9→11） | 穿云箭（SINGLE_TARGET/HOMING/DAMAGE×3.0）、荆棘海（ALL_ENEMIES/MELEE_INSTANT/DAMAGE×1.5）——对齐 GDD §7.2 Boss 专属技能表 |

### 新增：生产代码（5 个类）
| 文件 | 包 | 职责 |
|------|-----|------|
| `SceneData` | data | 不可变场景 POJO；`EnemyPoolEntry` 内嵌类（沿 SynergyData.Threshold 先例）；`getBossUnitId(round)` 非 Boss 轮返回 null |
| `WaveSpec` | entities | 不可变敌阵规格：模板**直接引用** + star（Phase 2 恒 1，留待 Phase 3）+ scale（杂兵=k / Boss=1.0）+ 格坐标；equals/hashCode 供确定性对拍 |
| `WaveGenerator` | systems | 无状态实例类；`generateEnemyWave(round, sceneId, data, rng)`；列表序 = 抽取序 + Boss 殿后（确定性序） |
| `RandomGenerator` | utils | 封装 `java.util.Random`；`weightedPick` 恰耗 1 个随机数（浮点累积扫描+边界钳制）；消耗计数 |
| `WaveConsoleMain` | tools（新包） | 普通 main，不动 Main.java；args[0]=seed（缺省42）、args[1]=dataDir（缺省 ../assets/data） |

### 修改：生产代码
- `config/JsonLoader`：`load` 三参→**四参**（旧签名删除）、`loadFromDirectory` 增读 scenes.json；`parseScenes`（S5/S6 解析期校验：weight≥1、minRound∈[1,25]、至少一条 minRound≤1、bosses 键∈{7,15,25} 且三键齐全）；交叉校验新增 S1~S4（池悬空/Boss 入池/Boss 位悬空或非 Boss/unlockAfter 悬空·自指·成环）
- `data/GameData`：构造器四参→**五参**（scenes 插在 synergies 后）；`getScene(id)`/`getScenes()` 不可变

### 测试：新增 4 类 42 例 + 存量迁移
| 测试类 | 例数 | 覆盖 |
|--------|------|------|
| `RandomGeneratorTest` | 8 | 同 seed 逐位一致、与裸 Random 委托对拍、值域边界、单权必中/零负权永不中、3:1 比例对拍(4000次±150)、全非正抛错、消耗计数 |
| `JsonLoaderScenesTest` | 18 | 正向全字段/声明序、getBossUnitId 查询、合法前置链；S1/S2/S3(悬空+非Boss位)/S4(悬空/自指/A→B→A成环)/S5/S6(weight=0、minRound=0/26)；bosses 缺键/非法键(8、x)；空池/未知字段/重复 id |
| `SceneDataTest` | 2 | getBossUnitId 行为、池/映射容器不可变 |
| `WaveGeneratorTest` | 14 | 锚点抽检(1/3/5/8/12/16/20轮)、Boss 轮总数+对应 Boss 殿后、杂兵 scale=k(1.4/3.4)、Boss scale=1.0、star 恒 1、minRound 门控(轮1仅战士/轮4无刺客)、近战全落第2行列序2,3,1,4、远程全落第0行、排满换行(轮25精确坐标链)、全25轮无冲突且坐标合法、同seed全等/异seed相异、RNG消耗=杂兵数(1/3/4)、场景不存在抛错 |

存量迁移（不新增语义断言，仅适配 + 数字更新）：`JsonLoaderValidationTest` 夹具补 scenes 最小集（默认 units 扩至 u1/u2/b1/b2/b3——合法场景必含 Boss 模板，S3 所需）、`load()` 增四参重载、`missingFileIsFatal` 改四参、BOM/根节点用例补写 scenes.json；`JsonLoaderTest.seedCounts` 4/9/6 → 6/11/6 + 1 场景。

---

## 三、测试结果

```
gradlew core:test  →  BUILD SUCCESSFUL（EXIT=0，104 例 / 0 失败 / 0 忽略）

GameBalanceTest:           14    JsonLoaderTest:            9
JsonLoaderScenesTest:      18    JsonLoaderValidationTest: 25
SceneDataTest:              2    SynergyDataTest:           4
PlayerTest:                 6    WaveGeneratorTest:        14
AnchorTableTest:            4    RandomGeneratorTest:       8
```

TDD 过程：RandomGenerator 先测 + 抛异常骨架（RED 8/8）→ 实现（GREEN 8/8）；WaveGenerator 先测（RED 精确命中唯一缺口——WaveSpec 无 equals，14 例仅 1 失败）→ 补 equals/hashCode（GREEN 14/14）；加载管线随签名变更整体落盘后全量 104 例全绿。

---

## 四、执行期新增的实现决定（计划 §二未列）

| # | 决定 | 说明 |
|---|------|------|
| 1 | scenes 条目 id 全文件唯一（重复即死） | 沿 units/skills/synergies 先例，防 LinkedHashMap 键静默覆盖 |
| 2 | `JsonLoaderScenesTest` 全部用 @TempDir 自包含夹具 | 不依赖 assets 种子（夹具先行，加载器与种子可分别落地验证） |
| 3 | `WaveSpec.equals` 的 template 按**引用等值**比较（hashCode 用 identityHashCode） | 模板实例由 GameData 规范化持有，同 id 即同实例；语义正确且一致 |
| 4 | 交叉校验 scenes 组排在既有检查之后 | 保证"units skillId 悬空"等既有报错优先级不变，存量负向用例零改动 |

---

## 五、验收记录（对照计划 §七逐条）

1. **测试全绿**：104 例（存量 62 迁移后保持全绿 + 新增 42；计划预估 ≈99，超出源于 Scenes 用例 18 vs 预估 ≈15）。
2. **控制台模拟**（seed=42，`tools.WaveConsoleMain`）：
   ```
   轮  1 | k=1.0 | 杂兵 1 | (2,2) 兽人战士 scale=1.0
   轮  7 | k=1.6 | 杂兵 4 + Boss | ... (1,2) 荆棘之母[Boss] scale=1.0
   轮 15 | k=2.4 | 杂兵 6 + Boss | ... (2,0) 独眼猎神[Boss] scale=1.0   ← 远程 Boss 落第 0 行
   轮 25 | k=3.4 | 杂兵 8 + Boss | ... (2,1) 兽人战士 (2,0) 丛林游侠 (3,1) 荆棘之母·真体[Boss] scale=1.0
   === RNG 消耗合计：129 次 ===
   ```
   逐轮人口 = 1,2,2,3,3,3,4,4,4,5,5,5,5,6,6,6,6,7,7,7,7,7,8,8,8（Σ=129，与计划核算一致）；轮 25 布阵正确溢出第 2 行转第 1 行、近战 Boss 接续列序。
3. **确定性**：同 seed 两次运行 `diff` 逐字节一致（+ 测试断言全等、异 seed 相异）。
4. **软告警符合 §八**：孤儿技能 5 条不变、孤儿羁绊 2 条不变、风味聚合行 = `暗夜、精灵、植物、Boss、独眼`（+独眼）。
5. **分层约束**：新增/修改类零 `Gdx.*` 实际调用（grep 命中仅 3 处 javadoc 提及）；`WaveConsoleMain` 用纯 JVM `FileHandle(File)`。

---

## 六、提交切分（feature/phase_2）

| 提交 | 内容 |
|------|------|
| `284d8cf` | feat: RandomGenerator 确定性随机与加权抽取（含 8 例测试） |
| `4a8486e` | feat: scenes.json 加载管线——SceneData/parseScenes/S1~S6 校验 + 种子落盘（含 20 例测试与存量迁移） |
| `e51a2b7` | feat: WaveGenerator 波次生成——WaveSpec/规则式布阵/确定性抽取（含 14 例测试） |
| `f67953e` | feat: tools 控制台波次模拟入口（25 轮/RNG 审计） |

> 计划 §九 原 5 步中步骤 2/3 存在种子耦合（`JsonLoaderTest` 直读 `../assets/data`，`loadFromDirectory` 增读 scenes.json 后种子必须同步落盘），故合并为一个提交；其余切分与计划一致。

---

## 七、与实施计划的偏差记录（均为被验收标准强制的必然结果，开工时已向用户声明）

| # | 偏差 | 处理 |
|---|------|------|
| 1 | 计划 §六"存量迁移仅适配签名、不新增断言" vs `seedCounts` 断言 4/9/6 | 改 6/11/6 + scenes 1（验收 #1"保持全绿"强制） |
| 2 | 计划 §六所述 `JsonLoaderTest` 的 load 四参/new GameData 五参迁移点实际不存在（该类只用 `loadFromDirectory`） | 实际迁移集中在 `JsonLoaderValidationTest`（3 参 load 调用、夹具） |
| 3 | 最小合法夹具 units 须含 3 个 Boss 模板（S3：Boss 位引用必为 isBoss） | `minimalValidSetLoadsCleanly` 断言 1→5；`flavorRaceAggregatedWarning` 夹具基座换全量 units、风味单位 id u2→u3（避重） |
| 4 | 计划头"分支 docs/phase2-wave-generator-plan"已过时（方案文档已并入 feature/phase_2） | 在 feature/phase_2 实施 |
| 5 | 计划 §5.8 输出示例中坐标为示意（计划自注"实测输出以运行为准"） | 实际坐标由 §5.7 规则表决定，见 §五验收摘录 |

---

## 八、过程问题记录

| 问题 | 处置 |
|------|------|
| `WaveGeneratorTest` 首版夹具 `int[][]` 混入 String 编译错 | 改 `assertBossRound(round, minions, bossId)` 辅助方法三连调用 |
| Windows/MSYS 下 `java -cp "a;b"` 分号类路径被 bash 参数转换损坏（NoClassDefFoundError） | 改用 `CLASSPATH` 环境变量 + `cygpath -w`（env 不经过参数转换）；运行备忘见下 |
| 控制台重定向输出为 GBK（Windows 下 PrintStream 随控制台编码），bash 直显乱码 | 文件内容正确，验证时 `iconv -f GBK -t UTF-8` 转码读取；非缺陷 |
| Gradle 输出在同步管道下偶尔为空 | 以 EXIT 码 + `core/build/test-results/test/*.xml` 为结果事实源（沿 Phase 1 经验） |

`WaveConsoleMain` 运行备忘（Git Bash / Windows）：
```bash
cd core
export CLASSPATH="$(cygpath -w "$PWD/build/classes/java/main");$(cygpath -w <gdx-1.14.0.jar 的gradle缓存路径>)"
java com.voidvvv.kz_auto_chess_n.tools.WaveConsoleMain [seed] [dataDir]
```

---

## 九、后续

- [ ] Phase 3：BattleSystem 按 battle §八管线由 WaveSpec 派生 BattleUnit（属性 = baseStats × starStatMultiplier(star) × scale，先加后乘）；敌方羁绊从 `WaveSpec.template` 的 race/class 双通道统计后经 SynergySystem 结算（有放回抽取已为同名凑对留口）
- [ ] 24 棋子池 + 墓穴/雪山场景（结构以计划 §5.1 为准）：消解孤儿告警与森林"野兽+游侠"倾向偏差（Q2 已知项）
- [ ] 数值待调（GDD §十一）：Boss 烘焙值、池权重/minRound 工作值；`WaveConsoleMain` 保留为数值调试工具（Phase 4 后可删）
