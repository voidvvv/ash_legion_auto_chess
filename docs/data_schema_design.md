# 🗃️ 数据层 Schema 设计文档

> **版本**：V1.0  
> **定位**：静态数据 JSON 的字段终版、同名词表、校验规则——Phase 1 数据层（`data/` + `config/JsonLoader`）的开工依据  
> **依据**：GDD V0.10 §10.3、`battle_design.md` V1.1（技能模板/索敌/载体/stat 白名单）、`architecture_design.md` V1.5（双实体/发号器）、`render_design.md` V1.0（图集命名约定）  
> **定稿日**：2026-08-20

---

## 一、范围与文件清单

| 文件 | 阶段 | 本文档状态 |
|------|------|-----------|
| `units.json` | Phase 1 | **字段终版 + 完整示例**（兼作 Phase 1 种子内容） |
| `synergies.json` | Phase 1 | **字段终版 + 完整示例** |
| `scenes.json` | Phase 2 | **结构锁定**（字段终版，示例为骨架） |
| `equipments.json` | Phase 5 | **结构锁定**（字段终版，示例为骨架） |
| `heroes.json` | Phase 6 | **延后**——档案层系统未设计，预写必返工 |

> **waves.json 取消**：原结构规划中的 `waves.json` 与 `scenes.json` 概念重复，统一并入 `scenes.json`（场景=敌人池+Boss 映射；波次强度/人口曲线为 `GameBalance` 全局常量，见 §九）。

## 二、通用约定

1. **编码**：UTF-8（名称含中文）；不用 BOM
2. **两套 id，两个命名空间**：
   - **模板 id（String）**：`unit_warrior_01`——自描述、JSON 内引用；
   - **运行时实体 id（int）**：`UnitRegistry` 发号——只存在于内存与存档，永不入静态 JSON
3. **加载即校验（fail-fast）**：缺必填字段、枚举值非法、引用悬空、数值越界 → 启动期直接抛错并指明文件/条目/字段，不带病运行
4. **近似不可变**：`*Data` 类字段 private、无 setter；libGDX `Json` 反射赋值一次后终身只读（加载器是唯一写入点，纪律约束补偿 Java 8 无 record 的缺口）
5. **本档为字段权威**：GDD §10.3 的 JSON 示意以本文档为准；示意性示例保留但不再是字段来源

## 三、同名词表（JSON ↔ 枚举 ↔ 代码 三处共用，杜绝字符串魔法）

| 词表 | 合法值 |
|------|--------|
| **statKey**（可修改属性白名单，battle_design §8.2） | `hp` `attack` `armor` `attackSpeed` `moveSpeed` `range` `lifesteal` `energyGainRate` |
| **TargetPriority**（索敌） | `NEAREST` `BACKLINE` `LOWEST_HP` `HIGHEST_ATK` |
| **SkillType**（技能模板） | `NUKED` `AOE` `HEAL` `SHIELD` `BUFF` `STUN` |
| **Delivery**（弹道载体，battle_design §5.3） | `MELEE_INSTANT` `HOMING` `LINE` |
| **StatusType**（状态，MVP 集） | `STUN` `BLEED` `SLOW` `ATK_UP` `ATK_DOWN` `ASPD_UP` `SHIELD` `REGEN`（扩展需登记 battle_design §7） |
| **EffectOp**（效果运算） | `ADD`（加算固定值）`PCT`（百分比） |
| **EffectTarget** | `ALLIES`（MVP 仅此一档；预留 `TRAITS` 仅同羁绊者） |
| **EquipSlot** | `WEAPON` `ARMOR` `ACCESSORY` |
| **Rarity** | `WHITE`（白）`FINISHED`（成）`LEGENDARY`（传说） |
| **SynergySource** | `RACE` `CLASS` |

## 四、units.json（字段终版）

### 4.1 字段表

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `id` | string | ✓ | 全文件唯一 | 模板 id，同时是图集 key 前缀（render §七） |
| `name` | string | ✓ | — | 显示名 |
| `race` | string | ✓ | 需存在于某 synergy 或登记为无羁绊 | 种族 |
| `class` | string | ✓ | 同上 | 职业 |
| `cost` | int | ✓ | ∈ {1, 2, 3} | **费阶——商店查价的事实源**（input §6.3），boss 模板为 0 |
| `baseStats.hp` | int | ✓ | > 0 | 基础生命 |
| `baseStats.attack` | int | ✓ | > 0 | 基础攻击 |
| `baseStats.armor` | int | ✓ | ≥ 0 | 基础护甲 |
| `baseStats.attackSpeed` | float | ✓ | > 0 | 次/秒 |
| `baseStats.range` | int | ✓ | ≥ 1 | 格（曼哈顿距离） |
| `baseStats.moveSpeed` | float | ✓ | > 0 | 格/秒（跳格冷却 = 1/moveSpeed） |
| `upgradeMultiplier` | float | ✓ | 缺省 1.8 | 星级倍率：属性 = 基础 × m^(星−1) |
| `defaultPriority` | enum | ✗ | 缺省 `NEAREST` | 索敌（无 specialPriority 时生效） |
| `specialPriority` | enum | ✗ | nullable | 索敌覆盖（刺客 `BACKLINE` 等） |
| `skill` | object | ✓ | — | 每棋子恰好 1 个（已定） |
| `skill.name` | string | ✓ | — | 技能显示名（UI / 详情面板用） |
| `skill.type` | enum | ✓ | SkillType | — |
| `skill.power` | float | ✓ | > 0 | **单位随 type**（见 4.2） |
| `skill.duration` | float | ✗ | 缺省按 type | BUFF 持续秒（缺省 5）；STUN 的时长即 `power`，无 duration |
| `skill.delivery` | enum | ✗ | 缺省按 type | 载体（缺省表见 4.2） |
| `boss` | bool | ✗ | 缺省 false | Boss 模板标记（数值**已烘焙**，见 4.3） |

动画帧数全棋子统一（idle 2 / walk 2 / attack 3 / cast 2 / death 3，GDD §9.3），不做字段。

### 4.2 skill.power 单位与载体缺省

| type | power 单位 | delivery 缺省 |
|------|-----------|---------------|
| `NUKED` | 攻击力倍率（如 2.5） | `HOMING` |
| `AOE` | 攻击力倍率 | `HOMING`（命中点爆炸，4 邻格） |
| `HEAL` | maxHp 比例（0.25） | `MELEE_INSTANT`（即时） |
| `SHIELD` | maxHp 比例 | `MELEE_INSTANT` |
| `BUFF` | 百分比（15 = +15%） | `MELEE_INSTANT` |
| `STUN` | **秒**（1.5） | `LINE`（直线冲击） |

### 4.3 Boss 数值：烘焙进模板（已定）

Boss 模板的 `baseStats` 直接写**乘好倍率的最终值**（普通 Boss ×2.5HP/×2.0攻、最终 Boss ×3.0/×2.5，GDD §7.2），不做运行时倍率——单一事实源，`scenes.json` 只负责"第几轮上哪个 Boss 模板"。

### 4.4 完整示例（Phase 1 种子内容）

```json
[
  {
    "id": "unit_warrior_01", "name": "兽人战士", "race": "兽人", "class": "战士", "cost": 1,
    "baseStats": { "hp": 100, "attack": 15, "armor": 10, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": null,
    "skill": { "type": "BUFF", "power": 15, "name": "战吼" }
  },
  {
    "id": "unit_assassin_01", "name": "暗夜刺客", "race": "暗夜", "class": "刺客", "cost": 3,
    "baseStats": { "hp": 80, "attack": 25, "armor": 5, "attackSpeed": 1.5, "range": 1, "moveSpeed": 1.8 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "BACKLINE",
    "skill": { "type": "NUKED", "power": 2.5, "name": "处决" }
  },
  {
    "id": "unit_ranger_01", "name": "丛林游侠", "race": "精灵", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 70, "attack": 18, "armor": 3, "attackSpeed": 1.2, "range": 3, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8,
    "skill": { "type": "NUKED", "power": 2.0, "delivery": "HOMING", "name": "贯穿箭" }
  },
  {
    "id": "boss_thorn_mother", "name": "荆棘之母", "race": "植物", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1250, "attack": 42, "armor": 20, "attackSpeed": 0.8, "range": 1, "moveSpeed": 0.6 },
    "upgradeMultiplier": 1.0,
    "skill": { "type": "AOE", "power": 1.5, "name": "荆棘藤蔓" },
    "boss": true
  }
]
```

> 烘焙算例（boss_thorn_mother）：以 2 费模板 hp 500 为基准 ×2.5 = 1250、attack 21×2.0 = 42。

## 五、synergies.json（字段终版）

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `id` | string | ✓ | 唯一（`syn_warrior`） |
| `name` | string | ✓ | 显示名 |
| `source` | enum | ✓ | `RACE` / `CLASS` |
| `key` | string | ✓ | 与 units 的 `race`（source=RACE）或 `class`（source=CLASS）值精确匹配 |
| `thresholds[].count` | int | ✓ | 升序、唯一（2/4/6 或 3/5/7，终态可自选） |
| `thresholds[].effects[]` | array | ✓ | 非空 |
| `effects[].stat` | statKey | ✗ | 与 `effect` 二选一；走属性修正管线 |
| `effects[].effect` | StatusType | ✗ | 无 stat 的特殊效果（LIFESTEAL 预留登记） |
| `effects[].op` | EffectOp | ✓* | stat 存在时必填 |
| `effects[].value` | float | ✓ | — |
| `effects[].target` | EffectTarget | ✗ | 缺省 `ALLIES` |

```json
[
  {
    "id": "syn_warrior", "name": "战士", "source": "CLASS", "key": "战士",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "armor", "op": "ADD", "value": 20 } ] },
      { "count": 4, "effects": [ { "stat": "armor", "op": "ADD", "value": 50 },
                                  { "stat": "attack", "op": "PCT", "value": 15 } ] },
      { "count": 6, "effects": [ { "stat": "armor", "op": "ADD", "value": 100 },
                                  { "stat": "attack", "op": "PCT", "value": 30 },
                                  { "effect": "SHIELD", "value": 0.3 } ] }
    ]
  }
]
```

> 兽人 6 档"开局 30% 最大生命护盾"以 `SHIELD` 开局状态表达（battle_design §7.3 三入口之一）。

## 六、scenes.json（Phase 2，结构锁定）

```json
[
  {
    "id": "scene_forest", "name": "翡翠林地",
    "unlockAfter": null,
    "enemyPool": [
      { "unitId": "unit_wolf_01", "weight": 3, "minRound": 1 },
      { "unitId": "unit_archer_01", "weight": 2, "minRound": 4 },
      { "unitId": "unit_treant_01", "weight": 1, "minRound": 8 }
    ],
    "bosses": { "7": "boss_thorn_mother", "15": "boss_one_eye", "25": "boss_thorn_true" }
  },
  { "id": "scene_crypt", "name": "亡者墓穴", "unlockAfter": "scene_forest", "enemyPool": [ "…同上结构…" ], "bosses": { "…": "…" } }
]
```

| 字段 | 说明 |
|------|------|
| `unlockAfter` | 解锁前置场景 id（null = 初始开放），档案域判定 |
| `enemyPool[].unitId` | 引用 units.json（加载期交叉校验必须存在） |
| `enemyPool[].weight` | 抽取权重 |
| `enemyPool[].minRound` | 该敌人最早出现轮次（池内费阶门控） |
| `bosses` | 轮次 → Boss 模板 id（7/15/25 固定） |

强度系数与敌方人口曲线**不在场景文件里**——全局常量（§九），场景只管"谁出场"。

## 七、equipments.json（Phase 5，结构锁定）

```json
[
  { "id": "eq_iron_sword", "name": "铁剑", "slot": "WEAPON", "rarity": "WHITE",
    "effects": [ { "stat": "attack", "op": "PCT", "value": 20 } ] },
  { "id": "eq_dragon_heart", "name": "龙心", "slot": "ARMOR", "rarity": "LEGENDARY",
    "effects": [ { "stat": "hp", "op": "ADD", "value": 400 } ],
    "passiveStatus": { "type": "REGEN", "power": 0.02, "tick": 5 } }
]
```

- `effects`：与 synergies 同一套 `{stat, op, value}` 词汇，开战时进基准快照（battle_design §8.1）
- `passiveStatus`（可选）：穿着期间常驻的状态（龙心 = 每 5s 回 2% maxHp）——装备入口进 StatusSystem 的第二种形态
- 掉落权重不在本文件：归宝箱奖励池配置（GDD §5.2 待调项）

## 八、加载期校验清单（JsonLoader 实现 + 单测依据）

1. 必填字段齐全；枚举值 ∈ 词表（§三）
2. `cost` ∈ {0,1,2,3}；`baseStats` 数值边界（hp>0、range≥1…）
3. `id` 全文件唯一；`thresholds.count` 升序唯一
4. **引用完整性**：scenes 的 `unitId`/`bosses` 必 ∈ units；synergies 的 `key` 必须 与某 synergy 声明的种族/职业匹配得上（孤儿羁绊告警）
5. `boss:true` 模板 `cost` 必须 = 0 且不出现在任何 `enemyPool` 普通权重位
6. 校验失败 → 抛错信息含 `文件 / 条目id / 字段路径`，启动即死

## 九、GameBalance 常量清单（代码侧，全局数值一览）

| 组 | 常量 | 值（已定/待调） |
|----|------|----------------|
| 回合 | 每局轮数 / Boss 轮 | 25 / 7、15、25 |
| 战斗 | 逻辑步长 / 超时 / 暴击率 / 暴击倍率 | 1/60 s / 60 s / 20% / ×1.5 |
| 能量 | 上限 / 命中回能 / 受击回能 | 100 / +10 / +5 |
| 弹道 | 全局速度 | 6 格/秒 |
| 索敌 | 重评估周期 | 2 s |
| 敌方 | 强度系数 / 人口锚点 | k=1+0.1×(轮−1) / GDD §7.3 锚点表 |
| 经济 | 起始金币 / 刷新 / 买经验 / 宝箱公式 / 怜悯 | 10 / 2 / 4 / 3+floor(轮/3)、封顶 10、Boss×2 / 第3败起+1、封顶+3、零棋子不计 |
| 商店 | 槽位数 / 费阶概率锚点 | 5 / GDD §3.4 锚点表 |
| 棋盘 | 尺寸 / 敌我分区 / 备战席 | 6×7 / 敌0~2·缓冲3·我4~6 / 9 格 |
| 等级 | 人口表 / 经验需求 | GDD §3.5 表 |
| DOT | 心跳间隔 | 1 s |

## 十、决策日志

| 日期 | 决策 | 结论 |
|------|------|------|
| 2026-08-20 | units 增补 `cost` | 费阶入模板——商店查价单一事实源（补 GDD 示例缺漏） |
| 2026-08-20 | waves.json 取消 | 并入 scenes.json（场景=池+Boss 映射；曲线走 GameBalance） |
| 2026-08-20 | Boss 数值烘焙 | 模板直存最终值，无运行时倍率 |
| 2026-08-20 | heroes.json 延后 | 档案层未设计，阶段错位不预写 |
| 2026-08-20 | 近似不可变 | private 字段+无 setter+加载器唯一写入（Java 8 无 record 的补偿） |
| 2026-08-20 | 加载即校验 | fail-fast + 引用完整性交叉校验，启动即死不带病运行 |
