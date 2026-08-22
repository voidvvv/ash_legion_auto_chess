# assets/font/ — Fusion Pixel 12px 位图字体（Q4=B）

`Assets.font()` 换载 `font/fusion_pixel_12.fnt`；文件缺失时回退 libGDX 内置默认
（headless 测试不依赖素材存在）。`.fnt` 为 AngelCode 文本格式，`BitmapFont` 直接可载，
页图引用 `fusion_pixel_12.png`（与本文件同目录，勿改名——页名写在 .fnt 内）。

## 来源（OFL 1.1）

- 字体：[Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font)，作者 TakWolf
- 版本：release `2026.08.11`，包 `fusion-pixel-font-12px-proportional-ttf-v2026.08.11.zip`
  内 `fusion-pixel-12px-proportional-zh_hans.ttf`（12px 比例宽度、简中变体，含 Latin）
- 许可：SIL Open Font License 1.1 —— 原文见本目录 `OFL-LICENSE.txt`
  （复制自上述 release 包内 `OFL.txt`）；上游组件许可见 `licenses/`
  （ark-pixel / cubic-11 / galmuri，Fusion Pixel 为其衍生合集）

## 生成参数（2026-08-22，一次性生成，可复现）

计划原定经 Hiero 导出；实际执行时 gdx-tools 1.12.1 无 CLI、旧版 Hiero 为 Swing
不可无头运行，改用等价的 JDK AWT 栅格化工具（偏差已登记交付报告）。参数：

| 项 | 值 |
|----|----|
| 尺寸 | 12px，Regular，无抗锯齿、整数度量（FRACTIONALMETRICS_OFF 等） |
| 字符集 | ASCII 0x20–0x7E + GB2312 符号区/一级常用字（A1–D7 区）+ 仓库 data/ 与 core 源码中实际出现的 CJK 字符，共 4539 字符 |
| 字形数 | 4508（31 个字符不在 zh_hans 变体字体内被跳过，主要为假名/西里尔等，游戏文本不使用） |
| 页 | 1024×1024 ×1，shelf 装箱、1px 间距，白字透明底 |
| 度量 | lineHeight=14，base=12（由全部字形墨水范围实测得出） |
| 字距 kerning | 无（`kernings count=0`，像素字体与 CJK 场景可接受） |

## 文件清单

| 文件 | 说明 |
|------|------|
| `fusion_pixel_12.fnt` | AngelCode 文本格式字体描述（4508 字形） |
| `fusion_pixel_12.png` | 唯一页图（1024×1024，透明底白字） |
| `OFL-LICENSE.txt` | SIL OFL 1.1 许可原文（自 release 包原样复制） |
| `licenses/` | 上游组件许可（ark-pixel / cubic-11 / galmuri） |
