# 运行期字体迁移报告：位图 .fnt → Fusion Pixel 12px TTF

> **后续（2026-11-25）**：主人提出三段字体与整体字号调小的新要求，运行期已由本报告的
> 单套 Fusion Pixel TTF 切换为标题黑体/正文楷体/UI 宋体三套子集 TTF。本报告保留为
> 上一轮迁移与像素字体对账的历史记录；现行方案见 `docs/font_roles_report_2026-11-25.md`
> 与 `assets/font/README.md`。

- 日期：2026-11-25
- 分支：`feature/quality_v2`（保留全部既有未提交改动；本任务不提交、不推送、不重启预览）
- 任务：从官方来源筛选可合法随游戏打包、覆盖简体中文的开源像素风 TTF；替换运行期位图字体，
  接入 libGDX FreeType 的生成/释放，适配 Assets/Drawer 字号层级、Nearest 与打包，
  并验证覆盖「崽、饷、薇」。

## 1. 结论

选定 **Fusion Pixel Font（缝合像素字体）12px proportional zh_hans**，release **2026.11.10**，
SIL OFL 1.1。运行期由 `Assets.font()` 用 gdx-freetype 生成 12px `BitmapFont`：
`mono=true` 硬边、Nearest 过滤、内置 `PixmapPacker` 1024² 分页打包、`spaceY=-2` 把
TTF 行高 16 收回旧位图契约 14；生成器用完即弃，字体随 `Assets.dispose()` 释放。
`assets/font/charset.txt` 收录 7,480 字（ASCII + GB2312 全量可用字 + 仓库 data/源码用字 +
回归字），FreeType 逐字生成 **0 缺失**；`崽/饷/薇` 均为 11×11、步进 12px。

## 2. 候选筛选（官方来源）

| 候选 | 许可（官方） | 简体覆盖 / 限制 | 结论 |
|------|--------------|-----------------|------|
| [Fusion Pixel](https://github.com/TakWolf/fusion-pixel-font) | 字体 [SIL OFL 1.1](https://github.com/TakWolf/fusion-pixel-font/blob/master/LICENSE-OFL)；构建程序 MIT | 12px 比例/等宽、zh-Hans 专用字形；TTF 37,678 字形；GB2312 全量与游戏 data 实测可生成 | **选定** |
| [Ark Pixel](https://github.com/TakWolf/ark-pixel-font) | 字体 SIL OFL 1.1；构建程序 MIT | 官方 README 明确“仍在开发、缺大量字符”，并建议 10/12px 改用 Fusion Pixel | 不选（仅作 Fusion 上游字形来源） |
| [Cubic 11](https://github.com/ACh-K/Cubic-11) | SIL OFL 1.1 | 繁体优先；简体仅 GB2312 Level-1 / 通用规范一级。`崽/饷/薇` 均为 GB2312 **Level-2**（`E1CC/E2C3/DEB1`），不能保证 | 不选（作为 Fusion 上游之一保留许可） |
| [Zpix 最像素](https://github.com/SolidZORO/zpix-pixel-font) | 商业单产品 USD $1000、个人/教育免费；禁止修改、反编译、拆分 | 22,237 字，覆盖够 | **否决**：不能免费随商业游戏打包 |

选择 Fusion Pixel 的核心理由：
1. OFL 1.1 明确允许嵌入、捆绑分发与商用，只需随包保留许可；上游组件许可已一并入库。
2. 12px proportional zh-Hans 与既有位图字体同族同尺寸，排版层级、字宽与像素风格连续。
3. 字符覆盖可证：GB2312 全量 + 数据全字 + 三个回归字均能在 TTF 中取到字形。
4. 上游 Ark Pixel 官方推荐 10/12px 使用 Fusion Pixel，避免直接使用仍缺字的 Ark。

## 3. 字体版本与许可

- 官方仓库：<https://github.com/TakWolf/fusion-pixel-font>
- 官方 release：<https://github.com/TakWolf/fusion-pixel-font/releases/tag/2026.11.10>
- 发布资产：`fusion-pixel-font-12px-proportional-ttf-v2026.11.10.zip`
  - 下载：<https://github.com/TakWolf/fusion-pixel-font/releases/download/2026.11.10/fusion-pixel-font-12px-proportional-ttf-v2026.11.10.zip>
  - zip SHA-256：`aa6b4da3f80d95af45f41d74b37db0fa3a832d7f428adeb7234b4758d50bbae9`
    （与 GitHub Release API 的 asset digest 一致）
- 解包 TTF：`fusion-pixel-12px-proportional-zh_hans.ttf`
  - 7,316,996 bytes
  - SHA-256：`7e93fedd2ac55bb37badb2262aaf43b08e3cb5976a0e3de2d90986ce59fe902e`
- 许可：
  - 字体 SIL OFL 1.1：`assets/font/OFL-LICENSE.txt`，与 release `OFL.txt` 字节一致
    （SHA-256 `bc518cf64b8032c07690f33cc270c35c179255a6ac8efa7c165ebae7e8f76a63`）
  - 上游组件：`assets/font/licenses/{ark-pixel-OFL,cubic-11-OFL,galmuri-LICENSE}.txt`，
    与 release `LICENSES/` 原文件逐字节一致

## 4. 集成实现

### 4.1 Gradle 依赖

- `core`：`api gdx-freetype`（跨平台；class 由 core 暴露）
- `lwjgl3`：`gdx-freetype-platform:natives-desktop`
- `android`：`gdx-freetype-platform` 的 arm64-v8a / armeabi-v7a / x86 / x86_64 natives
- `ios`：`gdx-freetype-platform:natives-ios`
- `core` 测试：`gdx-freetype-platform:natives-desktop` + `gdx-platform:natives-desktop`
  （无需 GL 即可驱动 FreeType/PixmapPacker）

### 4.2 Assets.font()

`core/.../render/Assets.java`：

- `FONT_PATH = font/fusion-pixel-12px-proportional-zh_hans.ttf`
- `FONT_SIZE = 12`、`FONT_SPACE_Y = -2`、`FONT_PAGE_SIZE = 1024`
- `FreeTypeFontParameter`：`mono=true`、`Hinting.Full`、`kerning=false`、无 mipmap、
  min/mag = Nearest；`setMaxTextureSize(1024)`
- 生成后立即 `generator.dispose()`；页纹理显式 Nearest + `setUseIntegerPositions(true)`
- `Assets.dispose()` 继续 `font.dispose()`；非增量字体的页纹理由 BitmapFont 拥有，
  逐页释放（内置 packer 的页 Pixmap 随 Texture 释放）
- TTF 缺失时软告警并回退内置 `BitmapFont`（保留原 headless/缺资源守卫）

### 4.3 Assets / Drawer 字号层级

- 原生 12px：`DrawerSkin.TEXT_HEIGHT = Assets.FONT_SIZE`；标题仍 2×=24px；描述正文仍
  `SMALL_TEXT_SCALE = 0.75` → 9px。
- TTF 自然行高 16px；`spaceY=-2` 固定为 14px，与旧位图契约一致；`DrawerTextBlock` /
  `SynergyPanel` 的 `font.getLineHeight()` 实测仍为 14（0.75×=10.5px），滚动容量不漂移。
- 12px/14px 魔法数收敛到 `Assets.FONT_SIZE` / `Assets.FONT_LINE_HEIGHT`，
  `DrawerSkin`、`HoverPreviewCard`、`SynergyPanel` 直接引用，避免层级与行距常量分叉。
- 页纹理 Nearest + 整数定位；非整数屏幕缩放仍走既有 `UnscaledLayer` 体系，字体页过滤
  不随批次默认值漂移。

### 4.4 字符集

`assets/font/charset.txt`（UTF-8 单行，22,102 bytes，7,480 字）：

- ASCII 0x20–0x7E
- GB2312 全量可解码字符（A1–F7 / A1–FE）
- 旧 `fusion_pixel_12.fnt` 的 4,508 个 id（连续保留旧覆盖）
- `assets/data/*.json` 与 core 源码全部非 ASCII 字符（data 去重后 370 字）
- 回归字 `崽 饷 薇`
- 用 TTF Unicode cmap 过滤不可显示字符：68 个 GB2312 数学符号/生僻字被剔除；
  游戏 data 字符 0 缺失

`FontCoverageTest` 会在每次测试运行时重新扫描 `assets/data` 并断言 charset 覆盖全部非 ASCII
字符，未来新增 data 用字会直接失败，提示重新生成 charset。

## 5. 覆盖验证

### 5.1 TTF cmap（JDK AWT）

- `psName = Fusion-Pixel-12px-Prop-zh-Hans-Regular`，`numGlyphs = 37,678`
- `崽 U+5D3D` / `饷 U+9977` / `薇 U+8587`：`canDisplay = true`，12px 字形墨迹均为 11×11

### 5.2 FreeType 12px mono（无 GL 探针）

- 字符集 7,480 字全部生成：`charsetMissing = 0`
- 同参 PixmapPacker 1024²：1 页；生成耗时约 274ms（桌面）
- 自然 `lineHeight=16`；`spaceY=-2` 后 `lineHeight=14`
- 回归字字形：`崽/饷/薇` 均 `11×11`、`advance=12`；`国` 11×11/12；`A` 7×9/8

### 5.3 与旧位图页对账（信息性）

4508 个旧字形中 4390 个与旧 `fusion_pixel_12.png` 逐像素 mask 完全一致；110 个存在少量
像素差异（合计 2,084 / 507,824 px = 0.41%）；7 个尺寸差异集中在空格、U+3000、U+2015、
上标罗马数字等非正文边缘项，另有 1 个 U+FEFF 无字形。游戏实际 CJK 字形尺寸与步进一致。

说明：旧位图页的源版本为 `2026.08.11`，本次 TTF 为 `2026.11.10`；12px 关键度量实测一致
（CJK 11×11 / 步进 12、A 7×9 / 步进 8、自然行高 16、`spaceY=-2` 后 14），故排版契约不因
版本升级漂移。

步进对账：4,508 个旧字形中仅 `U+0020` 空格（旧 4 → TTF 自然 6）与 `U+3000` 表意空格
（旧 4 → 12）两个步进不同，另有 `U+FEFF` BOM（非可见字）无字形；其余 **4,505 个完全一致**。
游戏 data 不使用 U+3000；空格取 TTF 自然 6px 与现有 UI 居中公式 `text.length() * 6f` 同口径。
对照 2026.08.11 原始 TTF，空格自然值也是 6（旧 `.fnt` 的 4 是当时栅格化工具的度量覆盖），
故不是本次版本升级引入的差异。

### 5.4 JUnit 回归

`core/src/test/java/.../render/FontCoverageTest.java`：

- charset 含 `崽/饷/薇`，含 data 全部非 ASCII 字符
- TTF cmap `hasGlyph` 三字均 true
- FreeType 对 charset 每个字符生成字形，0 缺失
- `lineHeight == 14`，CJK 步进 12，三字墨迹宽高 > 0

## 6. 构建与测试结果

在仓库根目录执行（Windows，JDK 25，Gradle wrapper）：

| 命令 | 结果 |
|------|------|
| `.\gradlew.bat :core:compileJava :core:compileTestJava :lwjgl3:compileJava --console=plain` | EXIT 0 |
| `.\gradlew.bat :core:test --tests "com.voidvvv.kz_auto_chess_n.render.FontCoverageTest" --console=plain` | EXIT 0；tests=1, failures=0, errors=0, time≈0.41s |
| `.\gradlew.bat :core:test --console=plain` | EXIT 0；131 suites / 1,157 tests / 0 failures / 0 errors / 0 skipped |

`:core:compileJava` 为任务要求的最终结果，已通过。Android/iOS 模块仅补 native 依赖，
本机未配置对应 SDK/工具链，未执行其构建。

## 7. 改动文件

| 状态 | 文件 | 说明 |
|------|------|------|
| M | `core/build.gradle` | 增加 gdx-freetype 与测试 native |
| M | `lwjgl3/build.gradle` | 桌面 freetype native |
| M | `android/build.gradle` | 四 ABI freetype native |
| M | `ios/build.gradle` | iOS freetype native |
| M | `core/src/main/java/.../render/Assets.java` | TTF 生成/字符集/Nearest/释放；保留既有 generated_v2 改动 |
| M | `core/src/main/java/.../render/ui/DrawerSkin.java` | 12px/0.75× 层级常量化；字号/行高注释 |
| M | `core/src/main/java/.../render/ui/HoverPreviewCard.java` | 卡内 9px 行高改为 `Assets.FONT_SIZE * 0.75` |
| M | `core/src/main/java/.../render/ui/SynergyPanel.java` | 14px 行距改为 `Assets.FONT_LINE_HEIGHT` |
| M | `assets/font/README.md` | 新字体来源、候选依据、运行期配置与覆盖数据 |
| ? | `assets/font/fusion-pixel-12px-proportional-zh_hans.ttf` | 运行期 TTF（OFL 1.1） |
| ? | `assets/font/charset.txt` | 7,480 字运行期字符集 |
| ? | `core/src/test/java/.../render/FontCoverageTest.java` | 覆盖回归测试 |
| ? | `docs/font_ttf_migration_report_2026-11-25.md` | 本报告 |
| 既有未跟踪目录内更新 | `assets/art/generated_v2/font/README.md` | 标注旧位图页降为对账基线 |

旧 `assets/font/fusion_pixel_12.fnt/.png` 与 `assets/art/generated_v2/font/` 的字体文件均保留，
不删除；仅更新了 `assets/art/generated_v2/font/README.md` 的运行期引用说明。
`Assets.FONT_PATH` 已不再引用旧位图。既有未提交改动（65 个文件的二进制/代码修改及
generated_v2 素材）全部保留，未做 reset/stash/commit/push。

## 8. 未做与风险

- 未提交、未推送、未重启预览；运行中的预览进程不受影响，但需下一次启动才会用 TTF。
- 未在真机/模拟器测量移动端首次 `font()` 生成耗时：桌面约 0.3–0.5s；7480 字、1 张
  1024² 页（4MB RGBA）。低端设备若出现启动卡顿，可先削减 charset 至游戏实际用字。
- TTF 使包体增加约 7.0MB（zip 内 TTF 7,316,996 bytes）；OFL 要求随包保留许可。
- 68 个 GB2312 字符在 zh_hans TTF 中无字形（数学符号/生僻字），已从 charset 剔除；
  当前游戏 data 不包含这些字符（测试锁定）。
- 旧位图页保留为历史/对账基线；若后续需要回滚，只需恢复 `Assets.FONT_PATH` 与
  `BitmapFont` 加载分支（本报告与 git 历史保留旧实现）。
