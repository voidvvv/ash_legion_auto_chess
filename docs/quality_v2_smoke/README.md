# 品质改造第二阶段 · 素材全量接入烟测记录（2026-11-24）

> 2026-11-25 后续：运行期字体已改为标题黑体/正文楷体/UI 宋体三套 TTF，本目录截图为旧
> Fusion Pixel 字体时代记录；现行字体视觉见 `docs/font_roles_v2/`。

generated_v2 原创像素素材全量接入后的桌面（lwjgl3）实机烟测记录。
截图均为 Windows 实机窗口截图（1296×759，虚拟 640×360 × 2）。

## 截图清单

| 文件 | 场景 | 验证点 | 结果 |
|------|------|--------|------|
| `01_main_menu.png` | 主菜单 | 原创主菜单背景 640×360、四态按钮壳、栅格化字体标题 | ✅ 背景为带细节插画（1.4 万+ 色非纯色清屏）、标题白字清晰 |
| `02_battle_board.png` | 测试局备战（`--test-gold=999`） | 棋盘框 + 三区地砖（敌红褐/缓冲土黄/友绿森林）、a/b 交错、敌阵虚影、商店卡头像、羁绊/出售/开战、抽屉把手角标、中文文案 | ✅ 全部在位；边框带装饰铆钉；中文全部可读（字体页正常） |
| `03_battle_drawer.png` | 备战抽屉展开（买卡后点把手） | 抽屉面板/标题板/介绍·装备标签/头像条/装备格+预览/关闭钮四态、单位信息区肖像 | ✅ 面板结构完整、肖像与文案正常、无花屏/透明缺失 |
| `04_run_setup.png` | 远征准备（主菜单 → 开始远征） | 3 张英雄卡右上 48×48 英雄肖像（三张各异）、被动文案让位折行、九宫格按钮壳、场景解锁门控 | ✅ 三肖像分别为灰白老兵/绿衣德鲁伊/金发诗人；`+25%`/`+15%` 折行到第二行（设计口径） |

## 复现命令

```sh
./gradlew :core:test              # 1156 tests, 0 failures, 0 skipped
./gradlew :lwjgl3:jar             # ember-legion-1.0.0.jar
./gradlew :lwjgl3:run             # 主菜单（截图 01/04 入口）
./gradlew :lwjgl3:run --args="--test-gold=999"   # 直达测试局（截图 02/03）
./gradlew :android:assembleDebug  # android/build/outputs/apk/debug/android-debug.apk（9.6MB，含 generated_v2 全部 249 文件）
```

截图工具：`build/tools/smoke_shot.ps1`（Win32 前台激活 + 虚拟坐标点击 + 窗口截图）、
`build/tools/shot_stats.ps1`（像素统计防呆）。

## 结论

- 运行期贴图全部走 `art/generated_v2/`（manifest 驱动 `GeneratedArt`，Nearest 过滤，每 PNG 一 Texture，dispose 释放）；旧 `art/units/`（397 张 itch 逐帧开源图）与 `art/ui/`（12 张采购件 + 旧主菜单背景）已无任何代码引用。
- 字体页按主人裁定由 Fusion Pixel TTF（OFL 1.1，release 2026.08.11）栅格化重制：4505 有墨字形尺寸与 .fnt 记录 100% 一致、与旧页墨迹重合率 100%（见 `assets/art/generated_v2/font/README.md`），manifest 收口 605/605。
- 遗留观察（非本次范围）：图鉴列表仍为纯文字（可后续接入头像行）；场景卡无缩略图；通知文案按宽度省略（既有行为）。
