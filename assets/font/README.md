# assets/font — 三段运行期 TTF 字体（标题黑体 / 正文楷体 / UI 宋体）

2026-11-25 主人新样式要求：**标题用中文黑体、游戏内容（棋子/技能/剧情描述）用楷体、
UI 标签/数值/按钮用宋体，整体字号调小**。原单套 Fusion Pixel 像素字体不再作为运行期字体，
本目录改为三套可随游戏分发的 SIL OFL 1.1 子集 TTF：

| 角色 | 文件 | 上游字体 | 版本 | 许可 | 文件大小 | 运行期字号（虚拟 UI） | 2× 栅格 |
|------|------|----------|------|------|----------|------------------------|---------|
| 标题 | `ember-legion-hei.ttf` | 思源黑体 CN Medium（Source Han Sans CN Medium） | 2.005R | SIL OFL 1.1 | 262,000 B | 20px | 40px |
| 正文 | `ember-legion-kai.ttf` | 霞鹜文楷 Regular（LXGW WenKai） | v1.530 | SIL OFL 1.1 | 377,280 B | 8.5px | 17px |
| UI | `ember-legion-song.ttf` | 思源宋体 CN Regular（Source Han Serif CN Regular） | 2.003R | SIL OFL 1.1 | 343,740 B | 10px | 20px |

三套字体均从官方 release/仓库下载、经 `fontTools.subset` 子集化；思源两套另经
`otf2ttf`（cu2qu）转为 TTF。**修改版已重命名为 `Ember Legion Hei/Kai/Song`**：
思源黑体/宋体 OFL 版权声明保留 Reserved Font Name `Source`，霞鹜文楷保留
`霞鹜/霞鶩/落霞孤鹜/落霞孤鶩/LXGW`（其附加许可仅覆盖 Web 字体子集化），
按 OFL 1.1 第 3 条，随游戏分发的子集不得使用原保留名，故全部改名并且不删除版权/许可元数据。

## 官方来源与版本（已实际下载核验）

### 思源黑体 / Source Han Sans

- 官方仓库：<https://github.com/adobe-fonts/source-han-sans>
- 官方 release：<https://github.com/adobe-fonts/source-han-sans/releases/tag/2.005R>
- 下载文件：`SubsetOTF/CN/SourceHanSansCN-Medium.otf`
  - 官方 raw：<https://raw.githubusercontent.com/adobe-fonts/source-han-sans/release/SubsetOTF/CN/SourceHanSansCN-Medium.otf>
  - 大小：8,406,556 bytes
  - SHA-256：`a94e558a2fe972bee4f46bce0843abff37063fd68c33f1e7d9058f6f09432b01`
  - 字体内部版本：`Version 2.005;addfeatures 5.0.0b21`
- 许可：SIL OFL 1.1（`licenses/source-han-sans-LICENSE.txt`，取自 release 分支
  <https://github.com/adobe-fonts/source-han-sans/blob/release/LICENSE.txt>，
  SHA-256 `fcac737e761ec63dbfbdce11030a1780161920d80315edba9c8beff1c2bac5a2`）

### 思源宋体 / Source Han Serif

- 官方仓库：<https://github.com/adobe-fonts/source-han-serif>
- 官方 release：<https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R>
- 下载文件：`SubsetOTF/CN/SourceHanSerifCN-Regular.otf`
  - 官方 raw：<https://raw.githubusercontent.com/adobe-fonts/source-han-serif/release/SubsetOTF/CN/SourceHanSerifCN-Regular.otf>
  - 大小：11,626,108 bytes
  - SHA-256：`3754ea669c530e2473354f8f6d9f79680a44d7e26ec7d00eeabee4a7e0753c5d`
  - 字体内部版本：`Version 2.003;hotconv 1.1.1;makeotfexe 2.6.0`
- 许可：SIL OFL 1.1（`licenses/source-han-serif-LICENSE.txt`，取自
  <https://github.com/adobe-fonts/source-han-serif/blob/release/LICENSE.txt>，
  SHA-256 `9ff5bb567e1b92c801fc1069e5fbf992ff8efccacb9db94e5959a5b3ba9bb903`）

### 霞鹜文楷 / LXGW WenKai

- 官方仓库：<https://github.com/lxgw/LxgwWenKai>
- 官方 release：<https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530>
- 下载文件：`LXGWWenKai-Regular.ttf`
  - 官方 raw：<https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/fonts/TTF/LXGWWenKai-Regular.ttf>
  - 大小：25,928,940 bytes
  - SHA-256：`2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b`
  - 字体内部版本：`Version 1.530; October 18, 2026`
- 许可：SIL OFL 1.1（`licenses/lxgw-wenkai-OFL.txt`，取自
  <https://github.com/lxgw/LxgwWenKai/blob/main/OFL.txt>，
  SHA-256 `1a25e35da1031c6c3436fde545bb9cb5aca954e9873afe510c834b8b79bd21a0`）

## 候选筛选（官方源 + 简体覆盖 + 可打包许可）

| 候选 | 官方许可 | 简体/项目覆盖 | 结论 |
|------|----------|---------------|------|
| 思源黑体 Source Han Sans CN Medium | SIL OFL 1.1 | 官方 CN 子集 30,926 cmap；本项目 865 字全含，作为标题黑体 | **选定（标题）** |
| 思源宋体 Source Han Serif CN Regular | SIL OFL 1.1 | 官方 CN 子集 30,930 cmap；本项目 865 字全含，作为 UI 宋体 | **选定（UI）** |
| 霞鹜文楷 LXGW WenKai Regular | SIL OFL 1.1（含 RFN 附加说明） | 官方 TTF 46,926 cmap；本项目 865 字全含，笔锋适合正文 | **选定（正文）** |
| Fusion Pixel / Ark Pixel / Cubic 11 | SIL OFL 1.1 | 像素字体，与“黑体/楷体/宋体”版式要求不符 | 不选（历史版本保留） |
| Zpix 最像素 | 商业许可，禁止免费随商业游戏打包 | 覆盖够 | 否决 |
| Noto Sans/Serif SC | SIL OFL 1.1 | 与思源同源，但为可变字体且体积 17–25MB；需额外实例化 | 不选（思源官方子集更直接） |

## 子集化与体积控制

- 字符集：`charset.txt`（UTF-8 单行，865 字符）
  - ASCII 0x20–0x7E
  - `assets/data/*.json` 全部非 ASCII 字符（370 字）
  - `core/src/main/java` 字符串字面量全部非 ASCII 字符（与 data 并集 770 字）
  - 回归字 `崽 饷 薇`
  - 不含源码注释用字（不参与运行期显示），比旧 7,480 字版显著缩小
- 三套字体共用同一子集字符集；标题运行期只生成 ASCII + 标题专用字 + 回归字
  （保持 40px 栅格 1024² 单页）。
- 打包体积：1,043,020 B（≈1.02MB，三套合计），旧 Fusion Pixel 全量 TTF 为 7,316,996 B；
  新字体总体积约为旧单套的 14%。
- 运行期纹理：每角色 1 张 1024² RGBA 页（合计 3 页/12MB VRAM），
  `FontCoverageTest` 将“≤1 页”作为回归断言。

子集输出哈希：

| 文件 | 大小 | SHA-256 |
|------|------|---------|
| `ember-legion-hei.ttf` | 262,000 B | `13e2da055e08bc4f4208e2a98bd2cd1e3323a127a83979af9e7eedc5d1269df4` |
| `ember-legion-kai.ttf` | 377,280 B | `298b243ce50497caa7474bf7ce3423fcc66f1e945ca2b83438a225fef55a5bc1` |
| `ember-legion-song.ttf` | 343,740 B | `7d620c51e9ff9f59582bb900c75207bb7b9048b45d9eb1c0ada600cc375273b7` |

## 运行期配置（`Assets.FontRole`）

| 项 | 标题 TITLE | 正文 BODY | UI |
|----|------------|-----------|----|
| TTF | `font/ember-legion-hei.ttf` | `font/ember-legion-kai.ttf` | `font/ember-legion-song.ttf` |
| 2× 栅格 size | 40 | 17 | 20 |
| 逻辑字号 | 20px | 8.5px | 10px |
| 逻辑行高 | 29px | 10px | 14.5px |
| 抗锯齿 | `mono=false` | `mono=false` | `mono=false` |
| hinting | `AutoMedium` | `AutoSlight`（保留楷书笔锋） | `AutoMedium` |
| 过滤 | Linear | Linear | Linear |
| 整数定位 | 关（亚像素字距） | 关 | 关 |
| 打包页 | 1024² / 1 页 | 1024² / 1 页 | 1024² / 1 页 |
| 生成/释放 | `generateFont` 后立即 `generator.dispose()`；`Assets.dispose()` 逐字体释放 | 同左 | 同左 |

说明：游戏虚拟 UI 为 640×360，桌面窗口 1280×720（2×）。字体按 2× 物理像素栅格化，
以 `FONT_LOGICAL_SCALE=0.5` 收回虚拟字号；默认窗口下 1 栅格像素 = 1 屏幕像素，
因此不靠像素字体的 `mono`/Nearest 硬边，矢量字形边缘自然。非整数窗口尺寸由 Linear
过滤平滑，不会出现像素字体时代的块状放大。

## charset.txt / 覆盖验证

- `charset.txt`：865 字符（ASCII 95 + 非 ASCII 770），单行 UTF-8；
  与三套 TTF 的 cmap 对齐后仅缺 U+FEFF（BOM，非显示字）。
- `FontCoverageTest` 每次运行重新扫描 `assets/data/*.json`，断言：
  1. charset 覆盖 data 全部非 ASCII 与 `崽/饷/薇`；
  2. 三套 TTF 的 cmap 覆盖标题/正文/UI 各角色样例字符串的每个字；
  3. FreeType 逐字生成 0 缺失；每角色 1024² 打包 ≤1 页；
  4. 逻辑行高 29 / 10 / 14.5，CJK 全角步进 = 2× 栅格字号。

三回归字（FreeType 栅格实测，三套均 >0 墨迹）：

| 字 | 码点 | 标题 40px | 正文 17px | UI 20px |
|----|------|-----------|-----------|---------|
| 崽 | U+5D3D | 有字形 | 有字形 | 有字形 |
| 饷 | U+9977 | 有字形 | 有字形 | 有字形 |
| 薇 | U+8587 | 有字形 | 有字形 | 有字形 |

## 角色使用约定

- 标题（黑体，20px）：主菜单/装载屏标题、远征准备/图鉴/测试样片大标题、暂停/胜负/终局标题、
  宝箱弹窗标题。
- 正文（楷体，8.5px）：棋子详情「自身/羁绊」描述、技能文本、装备描述、悬停卡、图鉴列表、
  远征准备被动文案、剧情/归因句、通知日志、羁绊 tooltip。
- UI（宋体，10px）：按钮、页签、标签、数值、棋子名/装备名/场景名、羁绊行、顶栏、商店价签、
  出售/开战/速度/投降、种子与轮次等。

代码中禁止再使用“单一 `Assets.font()`”：所有调用点按角色取
`titleFont()` / `bodyFont()` / `uiFont()`。

## 文件清单

| 文件 | 说明 |
|------|------|
| `ember-legion-hei.ttf` | 标题黑体子集（OFL 1.1，源自思源黑体 CN Medium 2.005R） |
| `ember-legion-kai.ttf` | 正文楷体子集（OFL 1.1，源自霞鹜文楷 v1.530） |
| `ember-legion-song.ttf` | UI 宋体子集（OFL 1.1，源自思源宋体 CN Regular 2.003R） |
| `charset.txt` | 运行期字符集（865 字，单行 UTF-8） |
| `licenses/source-han-sans-LICENSE.txt` | 思源黑体 OFL 1.1 原文 |
| `licenses/source-han-serif-LICENSE.txt` | 思源宋体 OFL 1.1 原文 |
| `licenses/lxgw-wenkai-OFL.txt` | 霞鹜文楷 OFL 1.1 原文 |
| `fusion_pixel_12.fnt` / `.png` | **历史位图**（已不加载；保留作仓库历史/对账） |
| `OFL-LICENSE.txt` / `licenses/ark-pixel-OFL.txt` 等 | **历史 Fusion Pixel 许可**（已不加载；保留合规档案） |

来源、覆盖、构建与视觉验证记录见 `docs/font_roles_report_2026-11-25.md`。
