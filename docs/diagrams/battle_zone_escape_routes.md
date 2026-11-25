# 格子棋子设计空间：逃逸路线图

> 困境（战斗期空间不可控 × 格子效果需要空间确定性）→ 两条已否路线（留档）→ 三条逃逸路线（A/C/B）→ 分阶段落地。
> 浏览器查看版：`battle_zone_escape_routes.html`（双击打开）
> 依据：`../planning/battle_zone_pieces.md`（策划分析讨论稿，2026-09-23）；锚点见该文引用（GDD §2.1/§2.2/§4.1/§5.1/§6.3/§6.4/§10.2；battle §三/四/§5.3/§八；architecture §2.1/§六；`MovementSystem.java:24-51`）

```mermaid
flowchart TD
    DILEMMA["核心困境<br/>战斗期空间不可控（贪婪步自动走位）<br/>× 格子效果需要空间确定性<br/>控制权在战前，效果却设计在战中"]

    subgraph REJECTED["已否路线（用户裁决关闭，仅留档）"]
        direction LR
        R1["全员固定<br/>→ 滑向《背包乱斗》<br/>硬伤：最近对位距离 2 > 近战 range 1<br/>近战全废 · 60s 超时判负 · 移动/索敌/弹道悬空"]
        R2["我固定 · 敌移动<br/>→ 滑向《植物大战僵尸》<br/>硬伤：玩家侧移速收益全废（刺客/野兽羁绊）<br/>敌阵站位信息贬值 → 侦察价值砍半"]
    end

    subgraph ESCAPE["逃逸路线（不消灭移动，把确定性还给格子）"]
        direction LR
        A["A 开战快照光环<br/>开战瞬间按初始站位结算，之后不随移动变化<br/>控制点 = 布阵期<br/>成本 低-中 · 顺手销账光环装备旧债"]
        C["C 不动子类棋子<br/>图腾/驻守单位 immobile<br/>全员可动唯独它不动 = 确定空间锚点<br/>成本 低（机制）+ 中（内容）"]
        B["B 持续地面区域<br/>效果挂格子不挂棋子，踩上即生效<br/>不可控 → 区域拒止 / 路径塑造玩法<br/>成本 中-高（新 ZoneSystem + 渲染贴花）"]
    end

    subgraph PHASE["分阶段落地（我的倾向，供参考）"]
        direction LR
        P1["第一阶段：A + C<br/>快照光环 + 2~3 个图腾棋子<br/>验证空间玩法手感"]
        P2["第二阶段：B<br/>ZoneSystem 完全体<br/>『指定格子』投射布置在此实现"]
        P3["后置：姿态预设 D · 阻挡格<br/>（待寻路精化）"]
    end

    BOOST["乘法优势（本项目独有）<br/>敌阵不变重试制（GDD §2.2）× 战斗确定性（随机仅暴击）<br/>= 布阵 → 验证 → 调整 → 同敌阵复验的调优闭环"]

    DILEMMA --> REJECTED
    DILEMMA --> ESCAPE
    A --> P1
    C --> P1
    B --> P2
    P1 --> P2 --> P3
    ESCAPE --> BOOST

    classDef rejected fill:#3a2226,stroke:#c0392b,stroke-dasharray: 5 5,color:#e8a9a9
    classDef escape fill:#1e3a2a,stroke:#27ae60,color:#bfe8d0
    classDef phase fill:#1f2c3a,stroke:#5dade2,color:#c9e2f5
    classDef dilemma fill:#3a3221,stroke:#f1c40f,color:#f5e6b8
    classDef boost fill:#2b2b3a,stroke:#a569bd,color:#dcc8ee
    class R1,R2 rejected
    class A,C,B escape
    class P1,P2,P3 phase
    class DILEMMA dilemma
    class BOOST boost
```

## 路线对照表

| 路线 | 控制点 | 关键咬合 | 主要风险 | 量级 |
|---|---|---|---|---|
| A 快照光环 | 布阵期（邻接关系） | 双实体开战快照（architecture §2.1）；旧债销账（phase5 §8 差异声明 #3） | 战斗中光环不随移动，需表现层光柱提示 | 低-中 |
| C 不动棋子 | 布阵期（放置位置） | 决策链跳过分支；仍可被索敌攻击 | 纯近战不动棋永够不着——加载校验禁止 | 低+中 |
| B 地面区域 | 布阵期 + 路径预判 | 毒雾弹雏形（data_schema §5.5）；StatusSystem 词表全复用 | 贪婪步遇阻罚站 → 拖战斗撞 60s 超时；阻挡格依赖寻路精化 | 中-高 |
