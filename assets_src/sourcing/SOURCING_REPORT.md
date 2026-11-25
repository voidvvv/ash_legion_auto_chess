# Pilot 素材选型报告（4 族各 1 子）

> 日期：2026-11-22 ｜ 范围：pilot 4 单位（兽人 / 野兽 / 亡灵 / 巨人）｜ 交付：`assets_src/draft_batch1/`
> 角色：选材 + 评审（对本报告与交付物 1~5 自负其责）

---

## 1. 结论

选定 **0x72《DungeonTileset II》**（CC0 1.0）。理由：

1. **一包覆盖 4 族**：`orc_warrior`（兽人）、`wogol`（野兽）、`skelet`（亡灵）、`ogre`（巨人）同包同画风，避免混包。
2. **自带序列帧**：每个怪物有 `idle` 与 `run` 各 4 帧（16×16 为主，ogre 32×32），walk/idle 有真实帧差，不是单图平移。
3. **许可最干净**：CC0 1.0，公有领域，可商用、无需署名（仍完整留证）。
4. **尺寸兼容**：16×16 最近邻放大到 ~21px 内容；ogre 原生 32×32 做 25px 内容，作为“巨人”天然更大。

落盘：原始包与许可存证在 `assets_src/sourced/0x72-dungeontileset-ii/`；成品 48 帧在 `assets_src/draft_batch1/units/`。

---

## 2. 候选包对比

| 候选 | 来源 / 许可 | 规格 | 4 族覆盖 | 动画 | 结论 |
|---|---|---|---|---|---|
| **0x72 DungeonTileset II** | [itch 作者页](https://0x72.itch.io/dungeontileset-ii)（本次经 [HydeCo 镜像](https://hydecogames.com/art/packs/0x72-dungeontileset-ii/) 下载）/ **CC0 1.0** | 16×16 为主，ogre 32×32；146 sprites | 兽人 `orc_warrior`、野兽 `wogol`、亡灵 `skelet`、巨人 `ogre` 全部命中 | idle×4 + run×4 序列帧 | **选用** |
| Kenney Tiny Dungeon | [kenney.nl](https://kenney.nl/assets/tiny-dungeon) / **CC0 1.0** | 16×16，130 tiles | 兽人（绿皮）、亡灵（灰甲/幽灵）、野兽（蝙蝠/蜘蛛）可凑；**无明确巨人** | 单帧 tiles，无序列帧 | 备选（见 §7-2） |
| Kenney Tiny Battle | [kenney.nl](https://kenney.nl/assets/tiny-battle) / **CC0 1.0** | 16×16，现代军事 | 不适用（坦克/现代士兵） | 单帧 | 否决（题材不符） |

> 说明：0x72 作者页在取件当时返回 HTTP 429，未能存档；已存镜像页（含作者/源链接/CC0 声明）、包内 `LICENSE.txt` 与 CC0 1.0 法条页 HTML 作为存证，见 `sourced/0x72-dungeontileset-ii/provenance/`。

---

## 3. 许可结论

- **CC0 1.0 Universal（公有领域贡献）**：可复制、修改、分发、商用，**无需署名**。
- 仍按项目规范记录作者/来源/许可（manifest 每行 + `PROVENANCE.md`），便于审计。
- 修改（裁切、灰度化、缩放、姿态变换）在 CC0 下允许；本项目未使用作者商标/署名暗示 endorsement。

---

## 4. Pilot 映射

| 游戏单位 | 种族/职业 | 源精灵 | 源帧 | 目标内容高 | 映射理由 |
|---|---|---|---|---|---|
| `unit_warrior_01` 兽人战士 | 兽人 / 战士 | `monsters/orc_warrior` | 16×20 | 21px | 绿皮+獠牙+粗壮体型，兽人第一读法 |
| `unit_wolf_pup` 狼崽 | 野兽 / 刺客 | `monsters/wogol` | 16×20 | 19px | 佝偻、爪、非人比例，体型最小；按“同族视觉亲缘”归野兽 |
| `unit_skeleton_soldier` 骸骨士兵 | 亡灵 / 战士 | `monsters/skelet` | 16×16 | 20px | 骷髅头+肋骨，亡灵第一读法 |
| `unit_frost_giant` 霜巨人 | 巨人 / 战士 | `monsters/ogre` | 32×32 | 25px | 原生大体型+粗臂，四族中显著更大；“霜”不靠颜色（见 §6 R1） |

选择这 4 个 ID 与归档批 `draft_batch1_scriptgen/pilot_keys.json` 保持一致，方便对照与后续替换测试。

---

## 5. 后处理（脚本 `assets_src/tools/process_pilot.ps1`）

1. 从 4 帧条带切帧，按 alpha 求内容 bbox，裁掉透明边。
2. **明度化**：统计本帧所有不透明像素的亮度排名，映射到 6 档中性灰（0.12→0.97）。保留原图的明暗层级与深色描边，**去掉色相**，让运行期星级色乘法 tint 得到干净的骨白/金/紫。
3. 最近邻缩放到目标内容高（21/19/20/25px），宽度按比例，水平居中。
4. 姿态微调（逐帧表见 `draft_batch1/FRAME_PLAN.md`）：
   - attack：`lean`（上身前倾/后仰 1~2px，逐行偏移，不整体平移）+ 明度微调；
   - cast：1px 亮边（rim）+ 亮度 +6%；
   - death：纵向压缩 0.62 + 前倾，末帧整体旋转 90°（倒地）+ 压暗。
5. 放进 32×32 画布：脚底最下行固定 **y=26**，左右居中，四周留 ≥2px。
6. 输出 RGBA8 PNG，校验：48/48 帧 baseline=26，单帧 ≤6 色，alpha 仅 0/255。
7. 另出 `variants_color/`（仅 4 张 idle_0 彩色版，作 tint 对照，**不用于导入**）。

---

## 6. 自评（对基准 R1~R8）

| 项 | 结论 | 证据 / 说明 |
|---|---|---|
| R1 星级 tint 兼容 | **通过** | 交付为 6 档灰度；`previews/star_tints.png` 与 `color_vs_grey_stars.png` 对比：彩色源 3★ 被压成暗蓝紫、1★ 过暗；灰度版三星干净可辨。 |
| R2 24px 三轴 | **部分通过** | 种族：4 剪影可辨（`display_24px_vs_32px.png`）；星级：三星可辨；**职业：未验证**——pilot 4 子中 3 个是战士，且现成素材没有占位系统的“顶带/核点/斜切/空心”变体标记。见 §7-1。 |
| R3 色役冲突 | **通过** | 去色后无红/黄/紫/蓝同色冲突；1★ 亮灰在敌区暗红、玩家区暗绿、缓冲带上都保持对比度；红框仍是最强敌方信号。 |
| R4 风格一致 | **部分通过** | 同包同调色板、同 6 档灰；但 ogre 是 32px 原生密度、其余 16px 放大 ~1.3×，巨人像素更细。作为“巨人个体更大/更精”可接受，严格统一需降采样重做。 |
| R5 工艺 | **通过** | 48/48 帧脚底 y=26；alpha 二值；无抗锯齿/半透明脏边；四周 ≥2px；无孤立噪点。 |
| R6 尺寸/命名 | **通过** | 48 个 key 与 `PlaceholderKeys` 完全一致，32×32，路径 `units/{key}.png`。 |
| R7 可复现 | **通过（强于要求）** | 脚本 + 固定映射；重跑两次 manifest 逐字节相同；每 key SHA-256 已记录。 |
| R8 可验证 | **通过（待游戏内）** | 复制 `units\*.png` 到 `assets/art/units/` 即生效；删除即回退占位。命令见 §8。 |

---

## 7. 已知问题 / 需主人或美术裁决

1. **职业轴缺位（最重要）**：现成怪物包里没有“战士顶带 / 法师核点 / 刺客斜切 / 游侠空心”这套变体标记，且 pilot 4 子以战士为主。两条路：
   - A. 职业改由**武器/服饰剪影**承载（法师杖、游侠弓、刺客短刃），美术按职业选源精灵；
   - B. 保留“标记”系统，后处理在身体上加 2px 深色标记（需接受它覆盖原图细节）。
   建议 A，pilot 阶段先不硬加标记；批次 2 前裁决。
2. **野兽映射**：`wogol` 是直立红皮兽人形，不是字面狼。按派单放宽的“同种族视觉亲缘”可接受；若主人要求字面狼/兽，需换包或单独找 CC0 狼精灵（会引入混包风格风险）。
3. **攻击/施法/死亡为合成帧**：源包只有 idle/run；attack 用倾身+run 帧、cast 用亮边、death 用压缩+旋转。24px 可读，但不是作者原画级动画。批次 2 若要求正式动作，需选带 attack/death 序列的包（注意许可）或继续程序化。
4. **非整数缩放**：16→21px 是 1.31×，像素宽度有 1/2px 不均。24px 观感可接受；4K/整数倍严格像素完美不成立。
5. **1★ 偏灰**：这是“星级=色”体系的代价——贴图本身无彩色，1★ 显示为骨白灰。若希望 1★ 也有种族色，只能在代码层对 1★ 跳过 tint（不建议，会破坏三轴）。
6. **朝向**：源图是正面/3-4 视角，不是侧视；`flipX` 只镜像，不改变“面朝”。战场是俯视棋盘，可接受；敌我仍由红/蓝框区分（`enemy_redframe_flipx.png`）。

---

## 8. 交付物与接入

```
assets_src/
  sourcing/SOURCING_REPORT.md          # 本文件
  sourcing/selected_sources.png        # 4 个源精灵彩色对照
  sourced/0x72-dungeontileset-ii/      # zip + 解包 + LICENSE + provenance/*.html
  sourced/kenney-tiny-dungeon/         # 备选包 zip + LICENSE + 页面存证
  tools/process_pilot.ps1              # 后处理脚本（可重跑）
  draft_batch1/
    units/*.png                        # 48 帧成品（唯一导入源）
    variants_color/*.png               # 4 张彩色对照，不导入
    manifest.csv / manifest.json       # 每 key 来源/许可/变换/sha256/基线/用色数
    FRAME_PLAN.md                      # 帧源与姿态表
    previews/
      contact_all.png                  # 12 帧 × 4 单位
      display_24px_vs_32px.png         # 24px 实看 vs 32px 参考
      star_tints.png                   # 1/2/3★
      color_vs_grey_stars.png          # 彩色 vs 灰度 tint 对照（决策证据）
      enemy_redframe_flipx.png         # 敌框+翻转模拟
```

游戏内替换测试（由工作区 claude 执行）：

```powershell
Copy-Item assets_src\draft_batch1\units\*.png assets\art\units\ -Force
gradlew :lwjgl3:run
# 回退：删除 assets\art\units\ 下对应 PNG，RealArt miss 即回落占位
```

> 只复制 `units/`。`variants_color/` 是评审对照，不参与运行。

---

## 9. 复现

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\process_pilot.ps1
```

- 依赖：Windows PowerShell 5.1 + System.Drawing；无 Python/第三方包。
- 脚本已存 UTF-8 BOM（PS 5.1 正确读中文）；重跑结果逐字节一致。
- 源包 zip SHA-256 见 `sourced/0x72-dungeontileset-ii/PROVENANCE.md`。
