# Phase 5 装备实体与命令链路图

> Q1 裁决 A 全链：equipments.json → EquipmentData → Equipment 实体（第二类实体、单一 id 空间）→ 穿脱命令 → 开战派生（装备修正源 + passiveStatus）→ 宝箱/卖出闭环。
> 浏览器查看版：`phase5_equipment_chain.html`（双击打开）
> 依据：`gdd_idea_0.0.0.1.md` §5.2（B2 灵活可拆卸）/§3.6（卖出自动卸下）；`data_schema_design.md` §八（结构锁定）；`architecture_design.md` §4.1（命令载荷）；Phase 3 Q4 裁决（修正源列表化——装备源即插）

```mermaid
flowchart TB
    JSON["assets/data/equipments.json（本期新建，8 件种子）<br/>id / name / slot(WEAPON|ARMOR|TRINKET) / rarity(WHITE|RARE|LEGENDARY)<br/>effects[{stat,op,value}]（≤3 条）/ passiveStatus{type,power,tick}（可选）"]
    LOADER["JsonLoader.parseEquipments（本期扩展）<br/>词汇校验（StatKey/EffectOp/StatusType）+ fail-fast<br/>passiveStatus.type 本期仅允许 REGEN（差异声明）"]
    ED["data/EquipmentData（不可变模板）<br/>+ EquipmentSlot / EquipmentRarity / EquipmentEffect / EquipmentPassive"]
    GD["GameData.getEquipment(id) / getEquipments()（声明序）"]

    ENT["entities/Equipment（不可变实体）<br/>id（IdIssuer 单一 int 空间——棋子/装备共用，architecture §2.2）<br/>template 引用 · equals 按 id"]
    INV["Player.inventory（背包：未穿戴装备列表）"]
    UNIT["entities/Unit（本期扩展）<br/>equipped: List&lt;Equipment&gt; ≤3 · 槽位唯一（武器/盔甲/饰品各一）<br/>spend 累计花费（卖出/合并口径）"]

    JSON --> LOADER --> ED --> GD
    GD --> ENT

    subgraph CMDS["穿脱命令（UI 域两段式点击 → 队列 → EquipmentSystem handler）"]
        EQ["EquipItem(itemId, unitId)<br/>背包→棋子；槽位被占 → 拒绝（先手动卸下）"]
        UNE["UnequipItem(itemId)<br/>棋子→背包（owner 由名单扫描，architecture §4.1 载荷无 unitId）"]
    end
    INV <-->|"EquipmentSystem（校验：SHOPPING 门控 · 物品在包/在身 · 槽位空闲）"| UNIT

    subgraph SOURCES2["装备获取 / 流失闭环"]
        CHESTEQ["宝箱槽3 装备选项（ChestSystem.roll）<br/>稀有度权重 白70/成25/传5；Boss 箱 0/80/20<br/>池 = equipments.json 全集"]
        SELLEQ["卖出（SellUnit）→ 自动卸下全部装备回背包（GDD §3.6）"]
        MERGEEQ["3 合 1 → 参与合并棋子的装备自动卸下回背包（实现口径）"]
    end
    CHESTEQ --> INV
    SELLEQ --> INV
    MERGEEQ --> INV

    subgraph BATTLE["开战派生（BattleSystem.startBattle 本期改造）"]
        SRC["修正源列表（Phase 3 Q4 预留插点，BattleSystem.java:237 现 singletonList）<br/>sources = [ SynergySnapshot（羁绊·侧全体）, EquipmentStats.of(equipped)（装备·单体） ]<br/>StatPipeline.deriveBaseline 结算器零改动"]
        PASSIVE["passiveStatus 落地（装备入口进 StatusSystem 的第二种形态，data_schema §八）<br/>ActiveStatus.tickInterval（本期新增，缺省 1s 不破既有 DOT/REGEN）<br/>例：龙心 REGEN power=0.02 tick=5 → 每 5s 回 2% maxHp"]
    end
    UNIT --> SRC
    UNIT --> PASSIVE
```

## 关键裁决与口径

| 项 | 决定 | 出处 |
|----|------|------|
| 装备是背包物品，随时 A↔B 转移；每棋子 3 槽（武器+盔甲+饰品） | GDD §5.2 B2 | 已实现为 slot 唯一性校验 |
| MVP 无合成；CombineEquipment 留大版本扩展 | GDD §5.2 | 本期不做 |
| 装备不与技能联动 | GDD §6.5 | 修正只走 stat 通道 + passiveStatus |
| 槽位被占时 EquipItem | 拒绝（UI 提示先卸下）——实现口径 | GDD 未定，B2 哲学下取最简确定语义 |
| 卖出自动卸下回背包，不随棋子消失 | GDD §3.6 | SellUnit handler 内 EquipmentSystem.unequipAll |
| 战歌号角"全体友军能量获取+15%" | 本期实现为穿戴者自身 energyGainRate +15%（schema 无 target 字段，光环装备待 schema 扩展） | 差异声明，记 §8 |
| passiveStatus.type | 本期仅 REGEN（加载期 fail-fast），其余类型待扩展 | 实现口径 |
| 背包无上限；InventoryPanel（③ 区 3×2）显示前 6 件 + 总数角标 | 实现口径（GDD/render 未定上限） | 记 §8 WARNING |
