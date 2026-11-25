# 三段字体改造报告：标题黑体 / 正文楷体 / UI 宋体

- 日期：2026-11-25
- 分支/工作区：`feature/quality_v2`（继续沿用，保留全部既有未提交改动）
- 任务：主人提出新样式——标题用中文黑体、游戏内容（棋子/技能/剧情描述）用楷体、
  UI 标签/数值/按钮用宋体，整体字号调小；筛选可随游戏分发的开源 TTF，接入 FreeType
  生成/释放并按用途建立独立字体角色，输出角色→字体→字号映射并验证。
- 约束：不提交、不推送、不重启主人预览；保留真 PNG 素材与既有修改。

## 1. 结论

运行期字体从单套 Fusion Pixel 像素 TTF 改为 **3 套 SIL OFL 1.1 子集 TTF**，
`Assets` 提供 `titleFont()` / `bodyFont()` / `uiFont()` 三个角色，调用点全部按用途切换，
不再存在单一 `Assets.font()`：

| 角色 | 上游 | 版本 | 许可 | 子集 TTF 大小 | 虚拟 UI 字号 | 2× 栅格 | 逻辑行高 |
|------|------|------|------|---------------|--------------|---------|----------|
| 标题 TITLE | 思源黑体 CN Medium | 2.005R | SIL OFL 1.1 | 262,000 B | **20px**（原 24） | 40px | 29px |
| 正文 BODY | 霞鹜文楷 Regular | v1.530 | SIL OFL 1.1 | 377,280 B | **8.5px**（原 9） | 17px | 10px |
| UI | 思源宋体 CN Regular | 2.003R | SIL OFL 1.1 | 343,740 B | **10px**（原 12） | 20px | 14.5px |

- 三套合计 **1,043,020 B ≈ 1.02MB**；上一版单套 Fusion Pixel 全量 TTF 为 7,316,996 B，
  新方案约为其 14%，同时把老 TTF 移出 `assets/`，包体净减约 6.27MB。
- 每角色 1024² 单页 → 运行期 3 张页纹理（约 12MB VRAM），`FontCoverageTest` 锁定 ≤1 页。
- 字符集 865 字（ASCII + data 全部非 ASCII + 主源码字符串字面量 + 回归字 崽/饷/薇），
  三套 TTF cmap 0 缺失；FreeType 逐字生成 0 缺失。
- `:core:test` **1,157 tests / 0 failures / 0 errors**；
  `:core:compileJava :core:compileTestJava :lwjgl3:compileJava` **EXIT 0**；
  独立 lwjgl3 实例逐屏截图验证通过（`docs/font_roles_v2/`）。

## 2. 候选筛选（官方源实际核验）

| 候选 | 官方许可 | 简体/项目覆盖 | 体积/结论 |
|------|----------|---------------|-----------|
| [思源黑体 Source Han Sans CN](https://github.com/adobe-fonts/source-han-sans/releases/tag/2.005R) | SIL OFL 1.1 | 官方 CN 子集 cmap 30,926；本项目 865 字全含 + 标题样例全含 | 官方单字重 OTF 8,406,556 B；子集后 262,000 B → **选定标题** |
| [思源宋体 Source Han Serif CN](https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R) | SIL OFL 1.1 | 官方 CN 子集 cmap 30,930；本项目 865 字全含 + UI 样例全含 | 官方单字重 OTF 11,626,108 B；子集后 343,740 B → **选定 UI** |
| [霞鹜文楷 LXGW WenKai](https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530) | SIL OFL 1.1（含 RFN 附加说明） | 官方 TTF cmap 46,926；本项目 865 字全含 + 正文样例全含 | 官方 25,928,940 B；子集后 377,280 B → **选定正文** |
| [Noto Sans/Serif SC](https://github.com/google/fonts/tree/main/ofl/notosanssc) | SIL OFL 1.1 | 与思源同设计，覆盖足够 | 可变字体 17.7/25.1MB，需实例化；官方子集不如思源直接 → 不选 |
| Fusion Pixel / Ark Pixel / Cubic 11 | SIL OFL 1.1 | 像素字体，无法满足“黑体/楷体/宋体”版式 | 不选（旧版本保留） |
| [Zpix 最像素](https://github.com/SolidZORO/zpix-pixel-font) | 商业单产品 USD $1000；个人/教育免费；禁止修改与拆分 | 22,237 字，覆盖够 | **否决**：不能免费随商业游戏打包 |

筛选标准：① 官方 release/仓库可下载且许可允许嵌入、子集、随游戏分发；② 简体中文与
本项目实际字符全含；③ 体积可控（子集后单套 <400KB）；④ 字面符合“黑体/楷体/宋体”要求。

## 3. 字体版本、来源、许可

### 3.1 思源黑体 CN Medium（标题）

- 仓库：<https://github.com/adobe-fonts/source-han-sans>
- release：<https://github.com/adobe-fonts/source-han-sans/releases/tag/2.005R>
- 官方文件：`SubsetOTF/CN/SourceHanSansCN-Medium.otf`
  - <https://raw.githubusercontent.com/adobe-fonts/source-han-sans/release/SubsetOTF/CN/SourceHanSansCN-Medium.otf>
  - 8,406,556 B；SHA-256 `a94e558a2fe972bee4f46bce0843abff37063fd68c33f1e7d9058f6f09432b01`
  - 内部版本：`Version 2.005;addfeatures 5.0.0b21`
- 许可：SIL OFL 1.1；原文 `assets/font/licenses/source-han-sans-LICENSE.txt`
  （SHA-256 `fcac737e761ec63dbfbdce11030a1780161920d80315edba9c8beff1c2bac5a2`）
- 版权声明含 Reserved Font Name `Source`；子集/转 TTF 后按 OFL 1.1 §3 改名为
  `Ember Legion Hei` / `EmberLegionHei-Regular`。

### 3.2 思源宋体 CN Regular（UI）

- 仓库：<https://github.com/adobe-fonts/source-han-serif>
- release：<https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R>
- 官方文件：`SubsetOTF/CN/SourceHanSerifCN-Regular.otf`
  - <https://raw.githubusercontent.com/adobe-fonts/source-han-serif/release/SubsetOTF/CN/SourceHanSerifCN-Regular.otf>
  - 11,626,108 B；SHA-256 `3754ea669c530e2473354f8f6d9f79680a44d7e26ec7d00eeabee4a7e0753c5d`
  - 内部版本：`Version 2.003;hotconv 1.1.1;makeotfexe 2.6.0`
- 许可：SIL OFL 1.1；原文 `assets/font/licenses/source-han-serif-LICENSE.txt`
  （SHA-256 `9ff5bb567e1b92c801fc1069e5fbf992ff8efccacb9db94e5959a5b3ba9bb903`）
- 同样因 Reserved Font Name `Source` 改名为 `Ember Legion Song`。

### 3.3 霞鹜文楷 Regular（正文）

- 仓库：<https://github.com/lxgw/LxgwWenKai>
- release：<https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530>
- 官方文件：`fonts/TTF/LXGWWenKai-Regular.ttf`
  - <https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/fonts/TTF/LXGWWenKai-Regular.ttf>
  - 25,928,940 B；SHA-256 `2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b`
  - 内部版本：`Version 1.530; October 18, 2026`
- 许可：SIL OFL 1.1；原文 `assets/font/licenses/lxgw-wenkai-OFL.txt`
  （SHA-256 `1a25e35da1031c6c3436fde545bb9cb5aca954e9873afe510c834b8b79bd21a0`）
- 版权声明含 RFN `霞鹜/霞鶩/落霞孤鹜/落霞孤鶩/LXGW`；其附加许可只覆盖 Web 字体子集化，
  游戏包内属桌面分发，故按 OFL §3 改名为 `Ember Legion Kai`（不使用任何保留名）。
- 版权与许可元数据保留在子集 TTF 的 `name` 表（nameID 0/13/14）。

### 3.4 子集输出

| 文件 | 大小 | SHA-256 | 内部 family / PS name |
|------|------|---------|------------------------|
| `assets/font/ember-legion-hei.ttf` | 262,000 B | `13e2da055e08bc4f4208e2a98bd2cd1e3323a127a83979af9e7eedc5d1269df4` | Ember Legion Hei / EmberLegionHei-Regular |
| `assets/font/ember-legion-kai.ttf` | 377,280 B | `298b243ce50497caa7474bf7ce3423fcc66f1e945ca2b83438a225fef55a5bc1` | Ember Legion Kai / EmberLegionKai-Regular |
| `assets/font/ember-legion-song.ttf` | 343,740 B | `7d620c51e9ff9f59582bb900c75207bb7b9048b45d9eb1c0ada600cc375273b7` | Ember Legion Song / EmberLegionSong-Regular |

子集方法：`fontTools.subset`（保留 name 0/13/14，删除 DSIG）；思源两套先用
`otf2ttf`（cu2qu）从官方 CFF OTF 转为 TTF，再子集。命名通过 fontTools 重写
nameID 1/2/3/4/6/16/17/18，删除 21/22/25（WWS/变体名），确保不携带上游保留名。

## 4. 字符集与覆盖

`assets/font/charset.txt`（单行 UTF-8，2,403 bytes，**865 字符**）：

- ASCII 0x20–0x7E（95）
- `assets/data/*.json` 全部非 ASCII 字符（370 字）
- `core/src/main/java/**/*.java` 字符串字面量中全部非 ASCII 字符（与 data 并集 **770 字**）
- 回归字 `崽 饷 薇`
- 不含源码注释用字；不保留旧 7,480 字 GB2312 全量（游戏文本是有限闭集，由测试锁定）

覆盖验证（`FontCoverageTest`，headless FreeType，不依赖 GL）：

| 项 | 标题 | 正文 | UI |
|----|------|------|----|
| TTF cmap 字符数 | 866 | 866 | 866 |
| 本项目 865 字缺失 | 0 | 0 | 0 |
| 标题/正文/UI 样例字符串缺字 | 0 | 0 | 0 |
| FreeType 逐字生成缺失 | 0 | 0 | 0 |
| `崽/饷/薇` 墨迹 | 有 | 有 | 有 |
| 1024² 打包页数 | 1 | 1 | 1 |
| 逻辑行高实测 | 29.0 | 10.0 | 14.5 |

`FontCoverageTest` 每次运行重新扫描 `assets/data`，未来新增 data 用字但未更新 charset 会直接失败。

## 5. 角色 → 字体 → 字号映射（代码事实源）

`Assets.FontRole`：

| 角色 | 文件 | 2× 栅格 | 逻辑字号 | 逻辑行高 | FreeType 参数 |
|------|------|---------|----------|----------|---------------|
| `TITLE` | `font/ember-legion-hei.ttf` | 40 | 20 | 29 | `mono=false`、`Hinting.AutoMedium`、Linear、`FONT_LOGICAL_SCALE=0.5`、整数定位关 |
| `BODY` | `font/ember-legion-kai.ttf` | 17 | 8.5 | 10 | `mono=false`、`Hinting.AutoSlight`（保楷书笔锋）、Linear |
| `UI` | `font/ember-legion-song.ttf` | 20 | 10 | 14.5 | `mono=false`、`Hinting.AutoMedium`、Linear |
| 打包 | 1024² PixmapPacker；每角色 1 页 | | | | `FreeTypeFontGenerator.setMaxTextureSize(1024)` |
| 释放 | `generateFont` 后立即 `generator.dispose()`；`Assets.dispose()` 逐字体释放页纹理 | | | | |

为什么是 2× 栅格 + 0.5 data scale：虚拟 UI 640×360、默认窗口 1280×720 是整数 2×；
40/20/17px 栅格在默认窗口下 1 栅格像素 = 1 屏幕像素，矢量字形无需 mono/Nearest 硬边。
非整数窗口尺寸由 Linear 过滤平滑。正文楷体用 AutoSlight 而不是 Full，避免楷书笔锋被
强 hinting 拉直；标题/UI 用 AutoMedium 保证小字号横竖笔画清晰。

用途分配：

- **标题（黑体 20px）**：主菜单/装载屏、远征准备、图鉴、测试样片、暂停、胜负横幅、
  终局面板、宝箱弹窗标题。
- **正文（楷体 8.5px）**：棋子「自身/羁绊」详情、技能与装备描述、悬停卡、图鉴列表、
  远征准备英雄被动、剧情/归因句、通知日志、羁绊 tooltip。
- **UI（宋体 10px）**：按钮、页签、标签、数值、棋子/装备/场景名、羁绊行、顶栏、商店价签、
  出售/开战/速度/投降、种子/轮次等。

## 6. 代码集成

`Assets`：

- 新增 `FontRole` 枚举与 `titleFont()` / `bodyFont()` / `uiFont()`；
- `font(FontRole)` 惰性生成、按角色字符集/栅格参数/过滤生成，`dispose()` 遍历释放；
- 旧 `FONT_SIZE` / `FONT_LINE_HEIGHT` / `FONT_SPACE_Y` 常量改为
  `TITLE/UI/BODY_FONT_SIZE`、`TITLE/UI/BODY_LINE_HEIGHT`、`FONT_LOGICAL_SCALE`；
- 标题角色使用独立小字符集（ASCII + 标题字 + 回归字），保证 40px 栅格单页。

调用点迁移（全部 `assets.font()` → 角色字体）：

- 屏幕：`MainMenuScreen`、`LoadingScreen`、`RunSetupScreen`、`CodexScreen`、`SampleMenuScreen`；
- 棋盘域：`BattleRenderer`（威胁提示/出售/备战提示）、`FloatingText`；
- UI：`TopBar`、`ShopBar`、`ShoppingHud`、`BattleHud`、`ChestDialog`、`PauseMenuDialog`、
  `ResultBanner`、`RunEndPanel`、`NotificationPanel`、`SynergyPanel`、`DrawerSkin`、
  `DrawerTextBlock`、`DrawerInfoSection`、`DrawerEquipSection`、`DrawerBadge`、`HoverPreviewCard`；
- 去掉旧 `setScale(2f)` / `setScale(1.5f)` / `0.75×` 字号补丁：标题直接画 20px 字体，
  正文直接画 8.5px 字体；行距常量改为角色行高/布局常量；
- 按钮/标题居中改用 `BitmapFont.draw(..., targetWidth, Align.center, false)`，不再用
  `text.length()*6f` 的半角估算（比例字体下更准确）。

伴随的布局常量调整：

- `DrawerSkin.TEXT_HEIGHT = UI_FONT_SIZE (10)`；`SMALL_LINE_HEIGHT = BODY_LINE_HEIGHT (10)`；
- `HoverPreviewCard.LINE_HEIGHT = 9`（正文 8.5px 的紧凑行距，保持 27 行 ≤ 252 容量锚点）；
- `RunSetupScreen.PASSIVE_WRAP_COLUMNS 9 → 12`（8.5px 正文约 102px）；
- `DrawerTextCapacityTest` / `HoverPreviewCardTest` 的旧 9px/215px 断言更新为新行高；
  `FontCoverageTest` 重写为三角色覆盖 + 单页 + 行高断言。

## 7. 体积与运行期资源

- 打包字体：262,000 + 377,280 + 343,740 = **1,043,020 B**；旧单套 7,316,996 B。
  `assets/font/fusion-pixel-12px-proportional-zh_hans.ttf` 已移到
  `assets_src/sourcing/fonts/`（不参与 assets 打包），老 `.fnt/.png` 与历史 OFL 保留为对账基线。
- 运行期字体页：3 × 1024² RGBA = 12MB VRAM；`FontCoverageTest` 断言每角色 ≤1 页。
- 生成耗时：三套合计在测试环境 <1s（标题字符集小、正文/UI 各 865 字）；角色惰性生成，
  首屏只先生成标题。

## 8. 验证

### 8.1 测试与编译

| 命令 | 结果 |
|------|------|
| `.\gradlew.bat :core:compileJava :core:compileTestJava :lwjgl3:compileJava --console=plain` | **EXIT 0** |
| `.\gradlew.bat :core:test --tests "com.voidvvv.kz_auto_chess_n.render.FontCoverageTest" --console=plain` | 通过（三角色 / 0 缺失 / 各 1 页 / 行高 29/10/14.5） |
| `.\gradlew.bat :core:test --console=plain` | **1,157 tests completed, 0 failed** |

### 8.2 桌面视觉验证

独立 lwjgl3 实例，1280×720 客户区，截图见 `docs/font_roles_v2/`：
主菜单、远征准备、图鉴、备战棋盘、抽屉（空态/选中/装备页/自身页/悬停卡）、通知流、暂停菜单。
可见：标题黑体、正文楷体、UI 宋体三者字面区分明确；标题 20px 比旧 24px 小但仍醒目；
正文 8.5px 在 2× 窗口下可读；无缺字方块、无控制台字体告警；抽屉滚动/悬停卡换行/页签无回归。

> 环境说明：主人预览进程仍持有旧 `core-1.0.0.jar`，标准 `:lwjgl3:run` 构建 jar 时会因
> Windows 文件锁无法覆盖；视觉验证使用 `--init-script build/font_work/alt-jar.gradle`
> 把构建输出改名为 `*-fontverify` 后运行，未停止、未重启主人预览。验证实例已退出。

## 9. 改动文件（本任务）

| 状态 | 文件 | 说明 |
|------|------|------|
| M | `core/.../render/Assets.java` | 三角色字体枚举/生成/释放；新字号/行高常量；字符集口径 |
| M | 14 个 `render/ui/*.java` | 按角色切换字体；去掉 0.75×/2× scale；行距常量 |
| M | 5 个 `screens/*.java` | 标题/正文/按钮角色化；居中改实测/Align |
| M | `render/board/BattleRenderer.java`、`FloatingText.java` | 棋盘提示/出售/飘字角色化 |
| M | `assets/font/README.md` | 三字体来源、版本、许可、覆盖、字号映射 |
| M | `assets/art/generated_v2/font/README.md` | 标注 Fusion Pixel 运行期已失效，仅对账基线 |
| M | `docs/font_ttf_migration_report_2026-11-25.md` | 顶部注明已被三段字体方案取代 |
| M | `core/.../render/FontCoverageTest.java` | 重写为三角色覆盖/单页/行高测试 |
| M | `DrawerTextCapacityTest.java`、`HoverPreviewCardTest.java` | 新字号/行高断言 |
| ? | `assets/font/ember-legion-{hei,kai,song}.ttf` | 三套子集 TTF（新文件） |
| ? | `assets/font/charset.txt` | 865 字运行期字符集（替换旧 7480 字版本） |
| ? | `assets/font/licenses/{source-han-sans,source-han-serif,lxgw-wenkai}*` | 三套 OFL 原文 |
| ? | `docs/font_roles_report_2026-11-25.md`、`docs/font_roles_v2/` | 本报告与实机截图 |
| 移动 | `assets/font/fusion-pixel-*.ttf` → `assets_src/sourcing/fonts/` | 旧运行期 TTF 移出打包目录 |

未提交、未推送、未重启主人预览；既有未提交改动（art generated_v2、音频、UI 素材等）全部保留。

## 10. 风险与后续

- 字体子集只覆盖当前 data + 主源码字符串（865 字）。新增文案若引入新汉字，`FontCoverageTest`
  会失败并提示补字符集/重新子集；这是有意的“闭集 + 测试守卫”策略。
- 正文 8.5px（2× 窗口 17px 物理）已实机可读，但在 1× 窗口（640×360）下会显示为 8.5px，
  属于“更小字号”的目标取舍；若主人觉得过小，可单点上调 `BODY_FONT_SIZE`（栅格 2× 同步）。
- 悬停卡沿用 9px 紧凑行距以保证 27 行不溢出锚区；若上调正文，需要同步复核卡高容量。
- 标题字体只生成标题字符集；若新增标题使用特别用字，需加入 `FontRole.TITLE.extraCharacters`。
- 未执行 Android/iOS 真机构建；三端 FreeType native 依赖沿用上一轮已接入的配置。

## 11. 复现命令（字体构建）

原始字体保存于 `build/font_work/src/`（未入库）。重新生成：

```sh
# 1) 思源 OTF → TTF（fonttools + cu2qu；本地 Python 3.15 需禁用编译扩展，见任务记录）
otf2ttf -o build/font_work/src/SourceHanSansCN-Medium.ttf build/font_work/src/SourceHanSansCN-Medium.otf
otf2ttf -o build/font_work/src/SourceHanSerifCN-Regular.ttf build/font_work/src/SourceHanSerifCN-Regular.otf

# 2) 子集（charset.txt 由 data + 主源码字符串字面量生成，865 字）
python -m fontTools.subset build/font_work/src/SourceHanSansCN-Medium.ttf \
  --text-file=assets/font/charset.txt --output-file=... --name-IDs='*' --name-languages='*' \
  --layout-features='*' --drop-tables+=DSIG
# 3) 用 fontTools 重写 name 1/2/3/4/6/16/17/18 为 Ember Legion *，删除 21/22/25
```

构建后的实际命令与校验值见 `assets/font/README.md`；截图脚本在
`build/tools/smoke_shot.ps1`（按窗口标题）与 `build/font_work/shot_pid.ps1`（按 PID，
避免碰到主人预览）。
