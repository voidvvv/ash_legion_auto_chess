# generated_v2/font — 栅格化字体页（605/605 收口）

主人裁定（2026-11-24）：manifest 唯一缺项 `font/fusion_pixel_12.png`（ImageGen 无法准确复现
CJK 字形）改用**字体栅格化**生成新页，不用旧位图页、不用 AI 生成字形。

## 品质改造批7 排版决议（2026-11-25，未更换字体）

> **2026-11-25 后续（已失效）**：主人提出“标题黑体 / 正文楷体 / UI 宋体、整体字号调小”，
> 运行期已改为 `assets/font/` 下三套矢量 TTF（思源黑体/霞鹜文楷/思源宋体），本节
> Fusion Pixel 12px 与 `DrawerSkin.SMALL_TEXT_SCALE` 描述仅作历史记录；现行角色映射与
> 字号见 `assets/font/README.md` 与 `docs/font_roles_report_2026-11-25.md`。

- **字体保留 Fusion Pixel 12px proportional zh_hans（SIL OFL 1.1）**。仓内可用资产是
  `.fnt`、栅格化页和许可原文。原始 TTF 不在仓内，`assets/font/licenses/` 下的 Ark Pixel、
  Cubic 11、Galmuri 是上游组件许可，目录中没有对应的字体文件或栅格化页。
- **字号层级**：标题板 / 三页签 / 面板行等结构字 = 原生 12px（scale 1.0，不缩）；
  抽屉详情/描述正文 = 0.75×（9px，`DrawerSkin.SMALL_TEXT_SCALE`，与悬停卡同档）。
- **实测排版**：正文换行与行高不再按「全角 12 列 / 9px 行」列估算——`DrawerTextBlock` 以
  `GlyphLayout` 实测逐串宽度，行距取字体的 lineHeight（14 × 0.75 = 10.5 px）。
  内容保留显式换行并在裁切区内滚动。现有位图字库仍缺少数据中用到的「崽」「饷」「薇」。
- **纹理过滤**：`Assets.font()` 显式置 Nearest（与 GeneratedArt / PlaceholderArt 同像素艺术口径）。

## 生成方式（可复现）

```sh
javac -d build/tools assets_src/tools/RasterizeFontPage.java
java -cp build/tools RasterizeFontPage \
  /path/to/fusion-pixel-12px-proportional-zh_hans.ttf \
  assets/font/fusion_pixel_12.fnt assets/art/generated_v2/font assets/font/fusion_pixel_12.png
```

- **字体源**：Fusion Pixel 12px proportional zh_hans（release `2026.08.11`，
  GitHub `TakWolf/fusion-pixel-font`）——即 `assets/font/README.md` 记载的同一字体同一版本；
  许可 SIL OFL 1.1，原文见本目录 `OFL-LICENSE.txt`（与 `assets/font/OFL-LICENSE.txt` 同源拷贝）。
- **度量契约**：`.fnt`（4508 字形）为 `assets/font/fusion_pixel_12.fnt` 的**字节级拷贝**——
  字符集/矩形/步进/base=12/lineHeight=14 全部沿用，游戏内排版数学（CJK 12px / ASCII 半宽
  步进口径）零漂移。
- **栅格化参数**（与原页生成口径一致）：12px、TEXT_ANTIALIAS OFF、FRACTIONALMETRICS OFF，
  逐字形墨迹平移到 .fnt 记录矩形原点；1024×1024 白字透明底。
- **验证**（2026-11-24 运行记录）：4505 个有墨字形全部落页，尺寸与 .fnt 记录 100% 一致
  （0 偏差、0 缺字形）；与旧页逐像素对账墨迹重合率 100.00%（同字体同参数的确定性复现，
  佐证新页字形准确）。

## 文件清单

| 文件 | 说明 |
|------|------|
| `fusion_pixel_12.fnt` | AngelCode 文本格式字体描述（字节级拷贝自 assets/font/，页引用同基名本目录页） |
| `fusion_pixel_12.png` | 唯一页图（1024×1024，本次重新栅格化产出） |
| `OFL-LICENSE.txt` | SIL OFL 1.1 许可原文 |

运行期加载（2026-11-25 起）：三套矢量 TTF 由 `Assets.FontRole` 角色化 FreeType 栅格化
（标题黑体/正文楷体/UI 宋体；来源、许可与字号见 `assets/font/README.md`）。
本目录 `.fnt`/`.png` 保留为旧位图页与栅格化工具的对账基线，**不再被任何运行期代码引用**。
