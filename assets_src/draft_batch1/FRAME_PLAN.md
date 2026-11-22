# Pilot 帧源与后处理说明（0x72 DungeonTileset II）

> 2026-11-22 ｜ 替代此前 OpenAI 基图方案 ｜ 执行脚本：`assets_src/tools/process_pilot.ps1`

## 1. 素材来源

- 包：**0x72 DungeonTileset II**
- 作者：0x72 (Robert) ｜ 源：https://0x72.itch.io/dungeontileset-ii
- 许可：**CC0 1.0**（公有领域，无需署名；记录见 `sourced/0x72-dungeontileset-ii/PROVENANCE.md`）
- 源帧：`monsters/{name}_idle_anim.png`、`monsters/{name}_run_anim.png`，各 4 帧；帧尺寸见下表。

## 2. 12 帧来源表（每单位同构）

源帧序号从 0 起（左侧第一帧）。`lean` = 逐行水平偏移（上身前倾/后仰，脚底不动）；`×n` = 明度乘算。

| key 后缀 | 源 sheet | 源帧 | 处理 | 目视意图 |
|---|---|---|---|---|
| `idle_0` | idle | 0 | plain | 站立基准 |
| `idle_1` | idle | 1 | plain | 呼吸（源帧自带 1px 起落） |
| `walk_0` | run | 1 | plain | 迈步 A |
| `walk_1` | run | 3 | plain | 迈步 B |
| `attack_0` | idle | 0 | lean −1, ×0.92 | 蓄力后仰 |
| `attack_1` | run | 2 | lean +2, ×1.06 | 前冲出招 |
| `attack_2` | idle | 1 | lean +1 | 收势 |
| `cast_0` | idle | 0 | plain | 施法起手 |
| `cast_1` | idle | 0 | 1px rim, ×1.06 | 亮边脉冲（不新增外框） |
| `death_0` | idle | 0 | plain | 倒地前 |
| `death_1` | idle | 0 | squash 0.62, lean +2, ×0.85 | 屈身倒下 |
| `death_2` | idle | 0 | 旋转 90°, ×0.75 | 横卧 |

> 说明：源包只有 idle/run 两套序列。attack/cast/death 的“倾身 / 亮边 / 压缩+旋转”是**显式声明的合成帧**，不是作者原画；没有把同一张图整体平移冒充动作（lean 为逐行形变，旋转为 90° 整像素重排）。

## 3. 尺寸与基线

| 单位 | 源帧 | 目标内容高 | 宽度（约） | 说明 |
|---|---|---|---|---|
| unit_warrior_01 | 16×20 | 21px | 14px | 兽人 |
| unit_wolf_pup | 16×20 | 19px | 12px | 最小体型 |
| unit_skeleton_soldier | 16×16 | 20px | 15px | 骷髅 |
| unit_frost_giant | 32×32 | 25px | 19px | 最大体型（1:1 附近，像素更细） |

- 画布固定 **32×32**；每帧内容水平居中，**脚底最下非透明行 = y 26**（0-based）。
- 交付校验：48/48 帧 bottom=26，四周 ≥2px，alpha 仅 0/255，单帧 ≤6 色。
- 宽高按内容 bbox 等比缩放；不拉伸。

## 4. 明度化规则（R1 的核心）

1. 对一帧内所有不透明像素按亮度排序（`0.299R+0.587G+0.114B`）。
2. 按排名映射到 6 档中性灰：`0.12 + 0.85 × t^0.70`（t = 排名归一化）。
3. 输出只有灰阶 + 透明，运行期由 `UnitView`/`BattleRenderer`/`ShopBar` 以星级色乘法 tint。
4. 颜色不进入交付图；彩色 idle_0 仅存 `variants_color/` 供评审对照。

> 依据：`previews/color_vs_grey_stars.png`。彩色源在 3★ 会被压成暗蓝紫、1★ 过暗；灰度版三星干净、1★ 亮度足够。

## 5. manifest 字段

`manifest.csv` / `manifest.json` 每行：

`key, file, width, height, sha256, unit_id, race, class, source_pack, source_url, author, license, attribution, source_sheet, source_frame, transform, bottom_row, palette_colors`

## 6. 校验命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\process_pilot.ps1
```

输出应含：`OK units=4 frames=48 baseline_bad=0 max_colors=6`。脚本无随机数，重跑逐字节一致。

## 7. 接入与回退

```powershell
Copy-Item assets_src\draft_batch1\units\*.png assets\art\units\ -Force
gradlew :lwjgl3:run
```

`RealArt` 按 `art/units/{key}.png` 懒加载；缺 key 自动落占位，删文件即回退。**不要**复制 `variants_color/`。

## 8. 本批边界

- 只覆盖 4 族 pilot；职业轴（顶带/核点/斜切/空心）未落地，见 `sourcing/SOURCING_REPORT.md` §7-1。
- 攻击/施法/死亡为合成帧，正式批需决定是否升级为原画动作。
