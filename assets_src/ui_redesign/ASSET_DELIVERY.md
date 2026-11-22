# UI 抽屉素材交付说明（12 张必需）

> 日期：2026-11-22 ｜ 对应采购单：`assets_src/ui_redesign/ASSET_REQUIREMENTS.md`（修订版：B02 不采购、M09 必需、总数 12+8=20）
> 交付范围：**12 张必需**；B02 `ui_btn_sell` 未采购、未入库；M09 `ui_handle` 已按“必需”交付。
> 来源包：**Golden UI** by **Buch**，OpenGameArt，**CC0 1.0**。存证：`assets_src/sourced/golden-ui/PROVENANCE.md`。

---

## 1. 许可结论

| 项 | 结论 |
|---|---|
| 来源包 | Golden UI（`ui_split.png` + `ui.psd`） |
| 作者 | Buch（https://opengameart.org/users/buch） |
| 来源页 | https://opengameart.org/content/golden-ui |
| 许可 | **CC0 1.0**（https://creativecommons.org/publicdomain/zero/1.0/） |
| 署名 | **不强制**；作者希望 credit “Buch / OpenGameArt”，本项目在本文与 manifest 中保留 |
| 商用 | 允许；可修改；无 NC/SA 限制 |
| 存证 | 页面快照 + CC0 deed + `ui_split.png` / `ui.psd` SHA-256，见 `PROVENANCE.md` |
| 结论 | **可入库**（按 CC0，无署名义务；仍保留来源记录） |

> Golden UI 包内没有独立 `LICENSE` 文件；授权声明在 OGA 页面。`assets_src/sourced/golden-ui/LICENSE.txt` 是按页面声明整理的说明，不是包内原文件。

---

## 2. 12 张成品清单

路径：`assets/art/ui/<id>.png`（1:1，**已入库**）与 `assets/art/ui/1_25x/<id>.png`（1.25× 备用，**不入库**）。

> 协调方注（2026-11-22，入库时追加）：Q8 裁定抽屉走不缩放层、只用 1:1 列，`1_25x/` 备用列永不进代码，故不入库；manifest 仍登记其尺寸与 SHA-256 以备查（manifest 为逐张记录的数组、被 `UiArtInventoryTest` 直接解析，故未在其中加注）。该目录已加入 `.gitignore`；如将来改走缩放层方案，可由 `assets_src/tools/process_ui_assets.ps1` 重新生成。
副本：`assets_src/ui_redesign/sourced_assets/<id>.png`（1:1）。
完整 SHA-256 / 来源矩形 / 切分线：`assets_src/ui_redesign/ui_asset_manifest.csv`（UTF-8+BOM）与 `.json`。

| # | 成品 id | 用途 | 1:1 | 1.25× | 来源（Golden UI `ui_split.png`） | 9-slice 左/右/上/下 | 处理 |
|---|---|---|---|---|---|---|---|
| M01 | `ui_panel_body_9p` | 抽屉面板底 | 64×64 | 80×80 | **程序化重绘**（同包 16 色盘/线宽/角钉母题） | 12/12/12/12 | 深蓝灰石框 + 暗金边 + 4 角钉；中心纯色 |
| M02 | `ui_inset_9p` | 内凹描述底 | 24×24 | 30×30 | **程序化重绘**（同包色盘；2px 边返修） | 6/6/6/6 | 上/左 2px 暗、下/右 2px 亮；中心 16×16 |
| M03 | `ui_slot_9p` | 头像/装备槽 | 36×36 | 44×44 | **程序化重绘**（同包色盘；2px 边返修） | 8/8/8/8 | 2px 外框+金边+2px 暗/亮；中心 24×24（9-slice 中心 20×20） |
| M04 | `ui_slot_selected_9p` | 选中/待定高亮框 | 36×36 | 44×44 | 直接裁切 `13,140,18,18`，金色映射 | 8/8/8/8 | **中心透明**，叠在 M03 上；暗金 2px 框 |
| M05 | `ui_tab_active_9p` | 标签页·选中 | 32×18 | 40×22 | **程序化重绘**（32×18 标签；端帽仅四角） | 6/6/6/6 | 中段 20×6 纯色可拉伸；x=29 填实；亮金 |
| M06 | `ui_tab_inactive_9p` | 标签页·未选中 | 32×18 | 40×22 | **程序化重绘**（32×18 标签；端帽仅四角） | 6/6/6/6 | 同形压暗；中段 20×6 纯色可拉伸；与 M05 一眼可分 |
| M07 | `ui_scroll_track_9p` | 滚动条轨（横竖共用） | 16×16 | 20×20 | **程序化重绘**（同包色盘/线宽） | 4/4/4/4 | 2px 外框 + 2px 深色槽线；竖向用时旋转 90° |
| M08 | `ui_scroll_knob_9p` | 滚动条滑块 | 12×12 | 16×16 | 直接裁切 `266,59,12,12`（宝石钮） | 4/4/4/4 | 绿→金重映射；保留 2px 暗描边 |
| M09 | `ui_handle` | 左缘把手（必需） | 14×72 | 18×90 | **程序化重绘**（木牌；装饰只放上下端帽） | 0/0/12/12 | 左缘平齐；顶帽 8×8 图标位、底帽拉槽；中段干净纵向可拉伸 |
| M10 | `ui_btn_close` | 关闭钮 | 14×14 | 18×18 | **程序化重绘**（木/金小牌 + 2px 骨白 ×） | 不拉伸 | 14×14 全不透明，无透明缺口；按下态程序调暗 |
| M11 | `ui_divider_h` | 横向分隔条 | 24×4 | 30×4 | **程序化重绘**（2px 暗 + 2px 亮） | 4/4/0/0 | 上 2px 暗线、下 2px 亮线；端头 2×2 收尾 |
| M12 | `ui_title_plate_9p` | 标题板 | 48×18 | 60×22 | **程序化重绘**（木牌 + 角钉） | 8/8/6/6 | 暗棕木底 + 2px 暗金框 + 4 角钉；中间可拉伸 |

> “程序化重绘”= 以 Golden UI 的 16 色盘、2px 线宽、角钉/木牌母题为约束，用脚本生成；不是手改原图，也不是纯色占位方块。原因见 §4。

---

## 3. 预览与拉伸验证

| 文件 | 内容 |
|---|---|
| `assets_src/ui_redesign/previews/ui_assets_1x.png` | 12 张 1:1 逐张预览（放大 4× 查看，标签含实际尺寸） |
| `assets_src/ui_redesign/previews/ui_assets_125x.png` | 12 张 1.25× 预览 |
| `assets_src/ui_redesign/previews/mock_drawer_1x.png` | 640×360 抽屉 mock（面板 244×218、6 槽、选中框、标签、滚动条、把手、关闭、分隔条、标题板） |
| `assets_src/ui_redesign/previews/mock_drawer_1x_2x.png` | 上图的 2× 视图 |
| `assets_src/ui_redesign/previews/panel_9slice_stretch_test.png` | M01 244×218、M02 120×60、M03 36×36/72×36/36×72、M07 220×16 拉伸测试 |

拉伸测试结果：四角不拉伸、边缘连续、无接缝；M01 面板拉伸到 244×218 后边角完整。

---

## 4. 为什么有 10 张是“程序化重绘”

Golden UI 是 606×414 的拆件图；本次返修后 **2 张直接裁切**（M04 选中框、M08 滑块），**10 张按同包色盘/线宽重绘**。拆件图里没有同时满足“干净四角 + 2px 线 + 无相邻件/文字/装饰溢出”的 9-slice 安全裁片；直接硬裁会带侧边卷饰、邻格线、裁切残列，或让拉槽/饰纹跨拉伸区。

按采购单“允许就近尺寸 + 后处理 / 风格统一”的口径，重绘件保证：
- 一套 7 色左右（深蓝灰 4 阶 + 木棕 2 阶 + 暖金 1 阶；关闭钮也用木棕/暗金，不另加暗红）；
- 2px 线宽；
- 透明底、硬 alpha；
- 与 0x72 地牢的暗色关系一致。

### 4.1 本次返修记录（claude 批 0 核验）

| 项 | 处理 |
|---|---|
| M05/M06 标签页 | 重绘为 32×18 全不透明；x=29 透明列/杂点清除；左端饰纹只保留四角帽（x=3/27，y=3/13），中段 x=6..25、y=6..11 为干净可拉伸底纹 |
| M09 把手 | 重绘：8×8 图标位只放顶帽（y=0..11），拉槽只放底帽（y=60..71）；中段 y=12..59 仅留 1px 木纹，不跨拉伸区 |
| M10 关闭钮 | 重绘为 14×14 全不透明方形小牌；左侧 2×2 透明缺口补齐；2px 骨白 × |
| M02/M03 线宽 | 重绘为 2px 外框 + 2px 暗/亮边；M03 槽 36×36 中心 24×24（9-slice 后中心 20×20），未小于 20×20；M02 中心 16×16 |

如果主人要求“每一张都必须来自原包像素”，可退回：M01 用 message box 主面板（会带侧饰/邻件）、M09 用面板左缘（会带内容纹理）、M10 用原关闭钮（14px 下 × 会糊）。当前版本优先可读性与 9-slice 干净。

---

## 5. 接入建议

- 1:1 直接按 `slice` 做 9-slice；目标尺寸：面板 244×218（max），槽 36×36，标签 32×18，标题板可拉伸。
- 1.25× 是备用列，按表尺寸直接用；若走 0.8 缩放层，优先用 1.25× 原图而不是把 1:1 再放大。
- M04 中心透明，必须叠在 M03 上；不要单独当槽底。
- M07 横竖共用：竖向时旋转 90°（四角对称，旋转安全）。
- M10 按下态：程序整体压暗 15%；不需要第二张图。
- M12 中间可拉伸，文字由游戏字体画；不要把文字烘进图。
- B02 不出现在 `assets/art/ui/`；如后续主人改判，再从 Golden UI 按钮件补。

---

## 6. 交付前自查

- [x] 12 张必需齐全，文件名与 §2.1 逐字一致，路径 `assets/art/ui/<id>.png`
- [x] 尺寸与表一致（1:1 / 1.25×；M03/M04 1.25× 为 44×44，不是 45）
- [x] 透明底；alpha 仅 0/255（脚本校验）
- [x] 单张用色 3~6 色（≤32；一套合计不超过 10 色）
- [x] 线宽 2px（重绘件 2px；M02/M03 为 2px 外框 + 2px 暗/亮边；1.25× 最近邻放大后 ≥2px）
- [x] M05/M06 x=29 透明列已消除；中段 x=6..25 无饰纹
- [x] M09 中段 y=12..59 无拉槽/饰纹，装饰只在上下端帽
- [x] M10 14×14 全不透明，左侧无 2×2 透明缺口
- [x] M03 槽 36×36 中心 24×24（9-slice 后中心 20×20，≥20×20）
- [x] 9-slice 拉伸测试无接缝/无花边（`panel_9slice_stretch_test.png`）
- [x] 每个来源包有 `PROVENANCE.md` + `LICENSE.txt` + 页面快照；CC0 明确
- [x] `ui_asset_manifest.csv|json` 含素材名/用途/来源/许可/尺寸/SHA-256
- [x] B02 `ui_btn_sell` 未采购、未入库
- [x] 未采购：带文字按钮、整套大 UI、棋子立绘、装备图标成品（B07~B09 未做）

---

## 7. 文件清单

```
assets/art/ui/
  ui_panel_body_9p.png       64x64
  ui_inset_9p.png            24x24
  ui_slot_9p.png             36x36
  ui_slot_selected_9p.png    36x36
  ui_tab_active_9p.png       32x18
  ui_tab_inactive_9p.png     32x18
  ui_scroll_track_9p.png     16x16
  ui_scroll_knob_9p.png      12x12
  ui_handle.png              14x72
  ui_btn_close.png           14x14
  ui_divider_h.png           24x4
  ui_title_plate_9p.png      48x18
  1_25x/  (same 12 ids; 80x80 / 30x30 / 44x44 / 44x44 / 40x22 / 40x22 / 20x20 / 16x16 / 18x90 / 18x18 / 30x4 / 60x22)
```

脚本：
- `assets_src/tools/process_ui_assets.ps1` — 裁切/9-slice/调色/重绘 + manifest
- `assets_src/tools/make_ui_previews.ps1` — 预览/ mock / 拉伸测试
