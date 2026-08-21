# 🗃️ 数据层 Schema 设计文档

> **版本**：V1.4（风味种族校验口径定稿：软告警对称双向；百分比类 stat 缺省值与百分点刻度约定）  
> **定位**：静态数据 JSON 的字段终版、同名词表、校验规则——Phase 1 数据层（`data/` + `config/JsonLoader`）的开工依据  
> **依据**：GDD V0.11 §6.5、`battle_design.md` V1.2（组合执行模型）、`architecture_design.md` V1.6、`render_design.md` V1.0（图集命名约定）  
> **改版日**：2026-08-21

---

## 一、范围与文件清单

| 文件 | 阶段 | 本文档状态 |
|------|------|-----------|
| `units.json` | Phase 1 | **字段终版 + 完整示例**（兼作种子内容；技能改为 `skillId` 引用） |
| `skills.json` | Phase 1 | **字段终版 + 完整示例**（V1.1 新增：具名技能模块） |
| `synergies.json` | Phase 1 | **字段终版 + 完整示例** |
| `scenes.json` | Phase 2 | **结构锁定**（字段终版，示例为骨架） |
| `equipments.json` | Phase 5 | **结构锁定**（字段终版，示例为骨架） |
| `heroes.json` | Phase 6 | **延后**——档案层系统未设计，预写必返工 |

> **waves.json 取消**：并入 `scenes.json`（场景=敌人池+Boss 映射；波次强度/人口曲线为 `GameBalance` 全局常量，见 §十）。

## 二、通用约定

1. **编码**：UTF-8（名称含中文）；不用 BOM
2. **两套 id，两个命名空间**：
   - **模板 id（String）**：`unit_warrior_01` / `skill_rampage`——自描述、JSON 内互相引用；
   - **运行时实体 id（int）**：`UnitRegistry` 发号——只存在于内存与存档，永不入静态 JSON
3. **加载即校验（fail-fast）**：缺必填字段、枚举值非法、引用悬空、数值越界 → 启动期直接抛错并指明文件/条目/字段，不带病运行
4. **近似不可变**：`*Data` 类字段 private、无 setter；libGDX `Json` 反射赋值一次后终身只读（加载器是唯一写入点）
5. **本档为字段权威**：GDD §10.3 的 JSON 示意以本文档为准

## 三、同名词表（JSON ↔ 枚举 ↔ 代码 三处共用，杜绝字符串魔法）

| 词表 | 合法值 |
|------|--------|
| **SkillShape**（技能目标形状，V1.2 增 `AOE_2`） | `SINGLE_TARGET` `SELF` `LOWEST_ALLY` `ALL_ALLIES` `AOE_1` `AOE_2` `ALL_ENEMIES` |
| **SkillEffectType**（技能效果类型，V1.1） | `DAMAGE` `HEAL` `SHIELD` `APPLY_STATUS` |
| **statKey**（可修改属性白名单，V1.3 增 `skillPower`） | `hp` `attack` `armor` `attackSpeed` `moveSpeed` `range` `lifesteal` `energyGainRate` `skillPower`（技能数值幅度 %，作用于 DAMAGE/HEAL/SHIELD 的 value，与星级缩放叠乘） |
| **TargetPriority**（索敌） | `NEAREST` `BACKLINE` `LOWEST_HP` `HIGHEST_ATK` |
| **Delivery**（弹道载体） | `MELEE_INSTANT` `HOMING` `LINE` |
| **StatusType**（状态，MVP 集） | `STUN` `BLEED` `POISON` `SLOW` `ATK_UP` `ATK_DOWN` `ASPD_UP` `SHIELD` `REGEN`（扩展需登记 battle_design §七） |
| **EffectOp**（羁绊/装备效果运算） | `ADD` `PCT` |
| **EffectTarget** | `ALLIES`（MVP 仅此一档；预留 `TRAITS`） |
| **EquipSlot** | `WEAPON` `ARMOR` `ACCESSORY` |
| **Rarity** | `WHITE`（白）`FINISHED`（成）`LEGENDARY`（传说） |
| **SynergySource** | `RACE` `CLASS` |

> **词表即代码的铁律**：新增 Shape / 效果类型 / StatusType / statKey 都是引擎代码改动——先在此登记，再进 JSON。
>
> **百分比刻度约定（V1.4）**：百分比类 stat（`lifesteal` / `skillPower` / `energyGainRate`）以**百分点整数刻度**存储（基准 0 / 0 / 100），结算处统一 ÷100 换算——保证 ADD/PCT 运算语义跨全部 9 键一致、管线零特例、JSON 不出现易错小数（如 energyGainRate 115 → ×1.15）。

## 四、units.json（字段终版）

### 4.1 字段表

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `id` | string | ✓ | 全文件唯一 | 模板 id，同时是图集 key 前缀（render §七） |
| `name` | string | ✓ | — | 显示名 |
| `race` | string | ✓ | 任意字符串；未匹配任何 synergy key 的值按**风味**处理（不计羁绊），加载期末**聚合软告警**一次（防拼写错误，见 §九） | 种族 |
| `class` | string | ✓ | 同上 | 职业 |
| `cost` | int | ✓ | ∈ {1, 2, 3}；boss 为 0 | 费阶——商店查价的事实源（input §6.3） |
| `baseStats.hp` | int | ✓ | > 0 | 基础生命 |
| `baseStats.attack` | int | ✓ | > 0 | 基础攻击 |
| `baseStats.armor` | int | ✓ | ≥ 0 | 基础护甲 |
| `baseStats.attackSpeed` | float | ✓ | > 0 | 次/秒 |
| `baseStats.range` | int | ✓ | ≥ 1 | 格（曼哈顿距离） |
| `baseStats.moveSpeed` | float | ✓ | > 0 | 格/秒（跳格冷却 = 1/moveSpeed） |
| `baseStats.lifesteal` | int | ✗ | 缺省 **0**（百分点） | 吸血 %，结算 ÷100 |
| `baseStats.skillPower` | int | ✗ | 缺省 **0**（百分点） | 技能幅度加成 %，结算 ÷100 |
| `baseStats.energyGainRate` | int | ✗ | 缺省 **100**（百分点，100 = ×1.0） | 回能速率，结算 ÷100 作乘数（115 → ×1.15） |
| `upgradeMultiplier` | float | ✓ | 缺省 1.8 | 星级倍率：属性 = 基础 × m^(星−1) |
| `defaultPriority` | enum | ✗ | 缺省 `NEAREST` | 索敌 |
| `specialPriority` | enum | ✗ | nullable | 索敌覆盖（刺客 `BACKLINE` 等） |
| `skillId` | string | ✓ | **必 ∈ skills.json**（加载期引用校验） | 每棋子恰好 1 个技能；多单位可共享同一技能 |
| `boss` | bool | ✗ | 缺省 false | Boss 模板标记（数值**已烘焙**，见 4.2） |

动画帧数全棋子统一（idle 2 / walk 2 / attack 3 / cast 2 / death 3），不做字段。

### 4.2 Boss 数值：烘焙进模板（已定）

Boss 模板的 `baseStats` 直接写**乘好倍率的最终值**（普通 Boss ×2.5HP/×2.0攻、最终 Boss ×3.0/×2.5），不做运行时倍率——`scenes.json` 只负责"第几轮上哪个 Boss 模板"。

### 4.3 完整示例（Phase 1 种子内容）

```json
[
  {
    "id": "unit_warrior_01", "name": "兽人战士", "race": "兽人", "class": "战士", "cost": 1,
    "baseStats": { "hp": 100, "attack": 15, "armor": 10, "attackSpeed": 1.0, "range": 1, "moveSpeed": 1.0 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": null,
    "skillId": "skill_warcry"
  },
  {
    "id": "unit_assassin_01", "name": "暗夜刺客", "race": "暗夜", "class": "刺客", "cost": 3,
    "baseStats": { "hp": 80, "attack": 25, "armor": 5, "attackSpeed": 1.5, "range": 1, "moveSpeed": 1.8 },
    "upgradeMultiplier": 1.8,
    "defaultPriority": "NEAREST", "specialPriority": "BACKLINE",
    "skillId": "skill_execute"
  },
  {
    "id": "unit_ranger_01", "name": "丛林游侠", "race": "精灵", "class": "游侠", "cost": 2,
    "baseStats": { "hp": 70, "attack": 18, "armor": 3, "attackSpeed": 1.2, "range": 3, "moveSpeed": 0.8 },
    "upgradeMultiplier": 1.8,
    "skillId": "skill_pierce"
  },
  {
    "id": "boss_thorn_mother", "name": "荆棘之母", "race": "植物", "class": "Boss", "cost": 0,
    "baseStats": { "hp": 1250, "attack": 42, "armor": 20, "attackSpeed": 0.8, "range": 1, "moveSpeed": 0.6 },
    "upgradeMultiplier": 1.0,
    "skillId": "skill_thorn_vine",
    "boss": true
  }
]
```

> 烘焙算例（boss_thorn_mother）：以 2 费模板 hp 500 为基准 ×2.5 = 1250、attack 21×2.0 = 42。

## 五、skills.json（V1.1 新增：具名技能模块）

### 5.1 设计模型

一个技能 = **目标形状（shape）× 效果列表（effects[]）× 载体（delivery）** 三维组合；执行时三步走：**shape 解析目标集合 → 逐效果应用 → 走既有管线（伤害/治疗/状态）**。效果词汇与羁绊/装备同源（StatusType / 属性词表），**零新机制，纯表达力提升**。旧"六模板"退役为组合特例（对照表见 battle_design §六）。

### 5.2 字段表

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `id` | string | ✓ | 唯一（`skill_rampage`）；兼作图标/特效 key（`fx_{skillId}`） | 技能 id |
| `name` | string | ✓ | — | 显示名（暴走 / 群体治疗…） |
| `desc` | string | ✓ | — | 描述（详情面板 / 图鉴用） |
| `shape` | enum | ✓ | SkillShape | 目标形状（语义见 5.3） |
| `delivery` | enum | ✗ | 缺省 `MELEE_INSTANT` | 载体（`HOMING`/`LINE` 时效果在命中点结算） |
| `effects[]` | array | ✓ | **非空**，1~3 条（多条依次应用，如暴走=双状态） | 效果列表 |
| `effects[].effect` | enum | ✓ | SkillEffectType | — |
| `effects[].value` | float | ✓* | DAMAGE/HEAL/SHIELD 必填且 > 0 | 单位见 5.4 |
| `effects[].status` | enum | ✗ | APPLY_STATUS 必填，∈ StatusType | 要挂的状态 |
| `effects[].duration` | float | ✓* | APPLY_STATUS 必填且 > 0（秒）；ATK_UP 等持续时间 | STUN 的眩晕秒数也在此 |

### 5.3 SkillShape 语义

| Shape | 目标集合 |
|-------|----------|
| `SINGLE_TARGET` | 当前锁定目标 |
| `SELF` | 施放者自身 |
| `LOWEST_ALLY` | HP% 最低友军（含自己）；全满 → 延后施放（保留能量） |
| `ALL_ALLIES` | 全体存活友军（含自己） |
| `AOE_1` | 落点（载体命中点 / 当前目标格）及其 4 邻格中的**所有敌人** |
| `AOE_2` | 落点周围 2 格（13 格菱形）中的所有敌人——"毒雾弹"类大范围 |
| `ALL_ENEMIES` | 全体存活敌人（Boss 演出常用；普通单位慎用，加载期软告警） |

### 5.4 effect.value 单位与星级缩放

| effect | value 单位 | 星级缩放（×(1+0.5×(星−1))） |
|--------|-----------|------------------------------|
| `DAMAGE` | 攻击力倍率（3.5 = 350%），走护甲公式 | ✓ 缩放 |
| `HEAL` | maxHp 比例（0.25 = 25%） | ✓ 缩放 |
| `SHIELD` | maxHp 比例 | ✓ 缩放 |
| `APPLY_STATUS` | 状态强度：ATK_UP 30 = +30%；STUN 无 value（时长用 duration）；**DOT 类（POISON/BLEED）value = 每跳伤害倍率，每跳 = 施放者攻击力快照 × value** | ✗ 不缩放（时长与强度固定） |

### 5.5 完整示例（Phase 1 种子内容，9 条）

```json
[
  { "id": "skill_warcry", "name": "战吼", "desc": "号令全军，攻击提升",
    "shape": "ALL_ALLIES", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "APPLY_STATUS", "status": "ATK_UP", "value": 15, "duration": 5 } ] },

  { "id": "skill_execute", "name": "处决", "desc": "对目标发起致命一击",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 2.5 } ] },

  { "id": "skill_pierce", "name": "贯穿箭", "desc": "射出穿透性一箭",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 2.0 } ] },

  { "id": "skill_thorn_vine", "name": "荆棘藤蔓", "desc": "藤蔓自地底爆发",
    "shape": "AOE_1", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 1.5 } ] },

  { "id": "skill_rampage", "name": "暴走", "desc": "战意失控：自身攻击与攻速大幅提升",
    "shape": "SELF", "delivery": "MELEE_INSTANT",
    "effects": [
      { "effect": "APPLY_STATUS", "status": "ATK_UP",  "value": 30, "duration": 8 },
      { "effect": "APPLY_STATUS", "status": "ASPD_UP", "value": 30, "duration": 8 }
    ] },

  { "id": "skill_mass_heal", "name": "群体治疗", "desc": "圣光洒向全军",
    "shape": "ALL_ALLIES", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "HEAL", "value": 0.25 } ] },

  { "id": "skill_long_snipe", "name": "超远程狙击", "desc": "锁定要害的致命远击",
    "shape": "SINGLE_TARGET", "delivery": "HOMING",
    "effects": [ { "effect": "DAMAGE", "value": 3.5 } ] },

  { "id": "skill_starfall", "name": "星陨", "desc": "坠落星辰的碎片轰击全场（最终Boss）",
    "shape": "ALL_ENEMIES", "delivery": "MELEE_INSTANT",
    "effects": [ { "effect": "DAMAGE", "value": 1.8 } ] },

  { "id": "skill_poison_cloud", "name": "毒雾弹", "desc": "毒雾在落点弥散，区域内敌人持续中毒",
    "shape": "AOE_2", "delivery": "HOMING",
    "effects": [ { "effect": "APPLY_STATUS", "status": "POISON", "value": 0.1, "duration": 6 } ] }
]
```

## 六、synergies.json（字段终版）

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `id` | string | ✓ | 唯一（`syn_warrior`） |
| `name` | string | ✓ | 显示名 |
| `source` | enum | ✓ | `RACE` / `CLASS` |
| `key` | string | ✓ | 与 units 的 `race`（source=RACE）或 `class`（source=CLASS）值精确匹配 |
| `thresholds[].count` | int | ✓ | 升序、唯一（2/4/6 或 3/5/7，终态可自选） |
| `thresholds[].effects[]` | array | ✓ | 非空 |
| `effects[].stat` | statKey | ✗ | 与 `effect` 二选一；走属性修正管线 |
| `effects[].effect` | StatusType | ✗ | 无 stat 的特殊效果（SHIELD 等） |
| `effects[].op` | EffectOp | ✓* | stat 存在时必填 |
| `effects[].value` | float | ✓ | — |
| `effects[].target` | EffectTarget | ✗ | 缺省 `ALLIES` |

**语义补充（V1.3 明文）**：
- **档位替换制**：达到更高 count 时生效该档**全量**效果（数值已含低档等价物），**不与低档叠加**；
- **风味种族**：暗夜 / 精灵 / 植物 等未登记羁绊的种族仅为风味标签，**不产生计数**——单位只经种族或职业中**已登记羁绊**的 key 进入计数；
- 吸血以 `stat: lifesteal`（ADD，百分点）表达，不使用 effect 通道。

```json
[
  { "id": "syn_orc", "name": "兽人", "source": "RACE", "key": "兽人",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "hp", "op": "ADD", "value": 150 } ] },
      { "count": 4, "effects": [ { "stat": "hp", "op": "ADD", "value": 400 },
                                  { "stat": "attack", "op": "PCT", "value": 20 } ] },
      { "count": 6, "effects": [ { "effect": "SHIELD", "value": 0.3 },
                                  { "stat": "lifesteal", "op": "ADD", "value": 20 } ] }
    ] },
  { "id": "syn_warrior", "name": "战士", "source": "CLASS", "key": "战士",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "armor", "op": "ADD", "value": 20 } ] },
      { "count": 4, "effects": [ { "stat": "armor", "op": "ADD", "value": 50 },
                                  { "stat": "attack", "op": "PCT", "value": 15 } ] },
      { "count": 6, "effects": [ { "stat": "armor", "op": "ADD", "value": 100 },
                                  { "stat": "attack", "op": "PCT", "value": 30 },
                                  { "stat": "lifesteal", "op": "ADD", "value": 20 } ] }
    ] },
  { "id": "syn_mage", "name": "法师", "source": "CLASS", "key": "法师",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 15 } ] },
      { "count": 4, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 30 },
                                  { "stat": "energyGainRate", "op": "ADD", "value": 15 } ] },
      { "count": 6, "effects": [ { "stat": "skillPower", "op": "ADD", "value": 50 },
                                  { "stat": "energyGainRate", "op": "ADD", "value": 30 } ] }
    ] },
  { "id": "syn_assassin", "name": "刺客", "source": "CLASS", "key": "刺客",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "attack", "op": "PCT", "value": 20 } ] },
      { "count": 4, "effects": [ { "stat": "attack", "op": "PCT", "value": 35 },
                                  { "stat": "moveSpeed", "op": "ADD", "value": 1.0 } ] },
      { "count": 6, "effects": [ { "stat": "attack", "op": "PCT", "value": 50 },
                                  { "stat": "attackSpeed", "op": "PCT", "value": 30 },
                                  { "stat": "moveSpeed", "op": "ADD", "value": 2.0 } ] }
    ] },
  { "id": "syn_beast", "name": "野兽", "source": "RACE", "key": "野兽",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 15 } ] },
      { "count": 4, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 25 },
                                  { "stat": "moveSpeed", "op": "ADD", "value": 1.0 } ] },
      { "count": 6, "effects": [ { "stat": "attackSpeed", "op": "PCT", "value": 40 },
                                  { "stat": "moveSpeed", "op": "ADD", "value": 2.0 },
                                  { "stat": "attack", "op": "PCT", "value": 15 } ] }
    ] },
  { "id": "syn_ranger", "name": "游侠", "source": "CLASS", "key": "游侠",
    "thresholds": [
      { "count": 2, "effects": [ { "stat": "range", "op": "ADD", "value": 1 } ] },
      { "count": 4, "effects": [ { "stat": "range", "op": "ADD", "value": 2 },
                                  { "stat": "attack", "op": "PCT", "value": 20 } ] },
      { "count": 6, "effects": [ { "stat": "range", "op": "ADD", "value": 2 },
                                  { "stat": "attack", "op": "PCT", "value": 35 },
                                  { "stat": "attackSpeed", "op": "PCT", "value": 15 } ] }
    ] }
]
```

## 七、scenes.json（Phase 2，结构锁定）

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

强度系数与敌方人口曲线**不在场景文件里**——全局常量（§十）。

## 八、equipments.json（Phase 5，结构锁定）

```json
[
  { "id": "eq_iron_sword", "name": "铁剑", "slot": "WEAPON", "rarity": "WHITE",
    "effects": [ { "stat": "attack", "op": "PCT", "value": 20 } ] },
  { "id": "eq_dragon_heart", "name": "龙心", "slot": "ARMOR", "rarity": "LEGENDARY",
    "effects": [ { "stat": "hp", "op": "ADD", "value": 400 } ],
    "passiveStatus": { "type": "REGEN", "power": 0.02, "tick": 5 } }
]
```

- `effects`：与 synergies 同一套 `{stat, op, value}` 词汇，开战时进基准快照
- `passiveStatus`（可选）：穿着期间常驻的状态——装备入口进 StatusSystem 的第二种形态
- 掉落权重不在本文件：归宝箱奖励池配置（GDD §5.2 待调项）

## 九、加载期校验清单（JsonLoader 实现 + 单测依据）

1. 必填字段齐全；枚举值 ∈ 词表（§三）
2. `cost` ∈ {0,1,2,3}；`baseStats` 数值边界（hp>0、range≥1…）
3. `id` 全文件唯一；`thresholds.count` 升序唯一；**`effects[]` 非空且条数 ≤ 3**
4. **引用完整性**：
   - units 的 `skillId` 必 ∈ skills.json（悬空即死）；
   - skills 被 0 个单位引用 → 孤儿技能**告警**（不阻断）；
   - scenes 的 `unitId`/`bosses` 必 ∈ units；synergies 的 `key` 与种族/职业值匹配（孤儿羁绊告警）；
   - units 的 race/class 未匹配任何 synergy key → **风味值聚合软告警**（去重值一行列出，如"精灵、暗夜、植物——按风味处理"；防拼写错误，与孤儿羁绊告警**双向对称**）
5. 效果字段配平：`DAMAGE/HEAL/SHIELD` 必有 `value>0`；`APPLY_STATUS` 必有合法 `status` 与 `duration>0`
6. `boss:true` 模板 `cost` 必须 = 0 且不出现在 `enemyPool` 普通权重位；`ALL_ENEMIES` 形状被非 boss 单位引用 → 软告警
7. 校验失败 → 抛错信息含 `文件 / 条目id / 字段路径`，启动即死

## 十、GameBalance 常量清单（代码侧，全局数值一览）

| 组 | 常量 | 值（已定/待调） |
|----|------|----------------|
| 回合 | 每局轮数 / Boss 轮 | 25 / 7、15、25 |
| 战斗 | 逻辑步长 / 超时 / 暴击率 / 暴击倍率 | 1/60 s / 60 s / 20% / ×1.5 |
| 能量 | 上限 / 命中回能 / 受击回能 | 100 / +10 / +5 |
| 弹道 | 全局速度 | 6 格/秒 |
| 索敌 | 重评估周期 | 2 s |
| 技能 | 星级缩放系数 / 每技能效果上限 | ×(1+0.5×(星−1)) / 3 条 |
| 敌方 | 强度系数 / 人口锚点 | k=1+0.1×(轮−1) / GDD §7.3 锚点表 |
| 经济 | 起始金币 / 刷新 / 买经验 / 宝箱公式 / 怜悯 | 10 / 2 / 4 / 3+floor(轮/3)、封顶 10、Boss×2 / 第3败起+1、封顶+3、零棋子不计 |
| 商店 | 槽位数 / 费阶概率锚点 | 5 / GDD §3.4 锚点表 |
| 棋盘 | 尺寸 / 敌我分区 / 备战席 | 6×7 / 敌0~2·缓冲3·我4~6 / 9 格 |
| 等级 | 人口表 / 经验需求 | GDD §3.5 表 |
| DOT | 心跳间隔 | 1 s |

## 十一、决策日志

| 日期 | 决策 | 结论 |
|------|------|------|
| 2026-08-20 | units 增补 `cost` | 费阶入模板——商店查价单一事实源 |
| 2026-08-20 | waves.json 取消 | 并入 scenes.json |
| 2026-08-20 | Boss 数值烘焙 | 模板直存最终值，无运行时倍率 |
| 2026-08-20 | heroes.json 延后 | 档案层未设计，阶段错位不预写 |
| 2026-08-20 | 近似不可变 | private 字段+无 setter+加载器唯一写入 |
| 2026-08-20 | 加载即校验 | fail-fast + 引用完整性交叉校验 |
| 2026-08-21 | **技能抽取为独立模块** | 新增 skills.json：具名+组合式（shape × effects[] × delivery）；units 改 `skillId` 引用；效果词汇与羁绊/装备同源 |
| 2026-08-21 | **组合式优于纯抽取** | 六模板退役为组合特例；暴走/群体治疗等超出旧模板的需求由形状+多效果表达 |
| 2026-08-21 | MVP 词表边界 | Shape 6 种 / 效果类型 4 种 / 每技能效果 ≤3 条；`ALL_ENEMIES` 供 Boss 演出，非 boss 引用软告警 |
| 2026-08-21 | 词表增补（V1.2） | **`POISON` 入 StatusType、`AOE_2`（13 格菱形）入 SkillShape；DOT value 语义定稿**：每跳 = 施放者攻击力快照 × value；新增示例「毒雾弹」（种子 9 条） |
| 2026-08-21 | 羁绊种子补全（V1.3） | synergies 种子 1 → **6 条全档位**（兽人/战士/法师/刺客/野兽/游侠，工作值待调）；**`skillPower` 入 statKey**（第 9 键，法师羁绊依赖）；**档位替换制**与**风味种族**语义明文；吸血统一 `stat: lifesteal`（effect 通道废弃） |
| 2026-08-21 | 风味校验口径（V1.4） | **未匹配 synergy 的 race/class 不报错**（运行时自然不计羁绊）；加载期末**去重聚合软告警**（防拼写，与孤儿羁绊告警对称）——消灭"登记为无羁绊"悬空条款 |
| 2026-08-21 | 百分比刻度约定（V1.4） | `lifesteal=0 / skillPower=0 / energyGainRate=100` 缺省定稿；**百分点整数刻度 + 结算 ÷100**——ADD/PCT 语义 9 键统一，管线零特例，JSON 无小数 |
