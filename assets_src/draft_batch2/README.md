# 批 2 交付（24 常规 + 9 Boss · 396 帧）

> 2026-11-22 ｜ 源包：0x72 DungeonTileset II（CC0 1.0）｜ 6 档灰 + 星级 tint ｜ 32×32 ｜ 脚底 y=26
> 状态：**draft_batch2**，待 claude 集成测试；未直接接入 `assets/`。

## 1. 内容

| 项 | 数量 | 说明 |
|---|---|---|
| 常规棋子 | 24 | 4 族 × 6，含 pilot 4 子的正式版（带职业武器） |
| Boss | 9 | class=Boss → PLAIN；大身体 × Boss 细节 |
| 单位帧 | **396** | 33 × 12（idle2/walk2/attack3/cast2/death3） |
| 键名 | 396/396 | 与 `PlaceholderKeys.unitFrame` 逐字符一致 |
| 颜色变体 | 24 | `variants_color/` 仅常规棋子 idle_0 的彩色身体，评审对照用 |
| FX | 0 | 0x72 无合适法术/命中/状态特效；全部保留占位，见 `FX_MAPPING.md` |

## 2. 文件

```
assets_src/draft_batch2/
  units/                       396 个 PNG（{unitId}_{anim}_{frame}.png）
  manifest.csv / manifest.json 396 行；UTF-8（CSV 带 BOM）
  MAPPING.md                   24 + 9 映射、12 帧计划、种族标记、许可
  FX_MAPPING.md                P3 结论：无合适源，保留占位
  README.md                    本文件
  previews/
    roster_24_32.png           24 子 idle_0，4 族 × 6，32px 原尺寸 4× 放大
    roster_24_24px.png         同上，24px 最近邻 8×（模拟游戏观感）
    roster_bosses_32.png       9 Boss idle_0，32px 5×
    roster_bosses_24px.png     9 Boss，24px 8×
    contact_兽人/野兽/亡灵/巨人.png   每族 6 子 × 12 帧，2×
    contact_bosses.png         9 Boss × 12 帧，2×
    star_tints_24.png          24 子 idle_0 的 3★ 紫 tint 4×
  variants_color/              24 张彩色身体对照（不含武器）
```

## 3. 校验（全部通过）

- 键名：`units.json` 的 33 个模板 × 12 帧 = **396**；实际 396，`Compare-Object` 零差异。
- 尺寸：396/396 **32×32**；alpha 仅 0/255；**脚底 26/26**（0 帧越界）。
- 用色：单帧 **≤10 色**（身体 6 档 + 武器/细节 4 档）；最大 10。
- manifest：396 行，33 个 unitId；每行 SHA-256 与实际文件一致（0 不一致）。
- 编码：manifest CSV UTF-8 + BOM；`race`/`class` 中文读出正常（兽人/战士 等）。
- pilot 回归：`draft_batch1/manifest.csv` 已按 UTF-8 BOM 重存，48 行、哈希/基线仍全绿。

## 4. 自评

### 4.1 通过项

- **合同**：键名/尺寸/基线/用色/哈希全绿；`RealArt` 按 `art/units/{key}.png` 可直接替换。
- **每族 6 子不重复**：24 个“身体×武器（+附加）”组合在族内唯一（见 `MAPPING.md` §1）。
- **职业轴**：沿用 round-2 盲测 16/16 的四类剪影（战士盾+武器、法师杖+球/帽、游侠弓/矛/投石索、刺客兜帽+刃），并扩展到锤/剑/新月/骷髅/提灯/水晶/双刃/弯刀等变体。
- **种族轴**：四族标记（兽人獠牙、野兽嘴鼻、亡灵下颌、巨人肩甲+腰带）已应用到全部 24 子。
- **Boss**：9 个“身体×细节”组合唯一；32px 可辨（见 `roster_bosses_32.png`）。

### 4.2 未盲测 / 偏弱项（集成测试重点看）

1. **扩展武器变体未做二次盲测**：round-2 只验证了 axe/staff/bow/dagger 四件。hammer/sword/mace/cleaver/javelin/sling/longbow/skull/lantern/crystal/dual/knife 是新增，自读可辨但**没有外部盲测数据**；最弱的是 `sling`（碎石仔，Y 形投石索）、`javelin`（掷矛猎头者，三根竖线）、`knife`（收账人，短弯刀）。
2. **Boss 细节在 24px 偏隐**：thorn_mother / thorn_true 同为 swampy 大身体，仅靠荆棘冠 vs 鹿角区分；one_eye 的眼在 24px 可能被压成暗块。32px 清楚，24px 需集成测试确认。
3. **武器/服饰固定像素尺寸**：小身体（goblin/imp/tiny_zombie）上道具比例偏大；大身体（ogre/big_zombie）上偏小。视觉上“能读职业”，但不是最终比例打磨。
4. **身体复用**：masked_orc、wogol、big_zombie、ogre 在族内/跨族复用（靠武器/标记区分）。同族内已保证组合唯一；跨族（big_zombie 同时用于亡灵/巨人）靠种族标记区分，最容易被挑刺的是 big_zombie 在两个族里的身份。
5. **合成帧**：attack/cast/death 仍是 lean/rim/squash/rotate 合成，不是原画；与 pilot 一致并已在 manifest 的 `op` 列声明。
6. **非 pilot 身体的种族标记是近似**：亡灵的“下颌线”画在 necromancer/zombie 等身体上像一条嘴线；巨人的肩甲/腰带画在 imp/big_zombie 上像护甲。可用，但不是逐个身体定制。

## 5. 集成

```powershell
Copy-Item assets_src\draft_batch2\units\*.png assets\art\units\ -Force
gradlew :lwjgl3:run
# 回退：删除 assets\art\units\ 下对应 PNG，RealArt miss 即回落占位
```

> 批 2 的 24 个常规棋子包含 pilot 4 子的正式版（带武器），会覆盖 `assets/art/units` 里同名 pilot 文件；最终素材清单以批 2 为准。
> 只复制 `units/`；`variants_color/` 不参与运行。

## 6. 预览说明

- `roster_24_32.png`：4 行 = 兽人/野兽/亡灵/巨人；每行 6 列按 `MAPPING.md` 顺序排列；表头 `slot 1~6`，下方是短 id。
- `contact_*.png`：行 = 12 帧（idle_0…death_2），列 = 该族 6 子；用于检查动作帧一致性与基线。
- `star_tints_24.png`：3★ 紫 tint 下的 24 子，确认武器/标记在 tint 后仍可读。
- `roster_bosses_*.png`：3×3，顺序同 `MAPPING.md` §2。
