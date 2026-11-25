# 余烬军团 v2 素材接线

本目录中的 PNG 是内置 ImageGen 原创画面按用途裁切和像素规格化后的游戏素材。输入只有工作区现有的代码、配置和尺寸元数据。全部新加载路径以 `art/generated_v2/` 开头，相对于 LibGDX 的 `assets/` 根目录。

- `inventory.json` 记录生成前盘点，包括原 key、旧路径或程序化来源、目标尺寸及用途。
- `manifest.json` 在同一清单中逐条给出交付状态、替换 key、PNG 路径、帧裁切和来源名称。读取尺寸或矩形时都采用像素单位。
- `imagegen_sources.json` 记录内置 ImageGen 每一批的实际提示词与默认落盘路径。源坐标用于追溯，运行期使用 `path` 和 `atlas_rect`。

## 单位、头像与特效

- `RealArt` 按 manifest 优先查找同名单位帧。每个单位一个 128×96 RGBA PNG，四列三行。帧在其 32×32 格内以第 30 行为底部参考线，LibGDX 仍按 `BoardGeometry.UNIT_FOOT_ANCHOR_Y` 绘制，敌我翻转仍由现有渲染逻辑控制。
- `atlas_rect` 是从 PNG 左上角起算的 `[x,y,w,h]`。新建 `TextureRegion(texture,x,y,w,h)` 时直接使用。每个 PNG 只创建一次 Texture，设置 min/mag 为 `Nearest`，退出时按 Texture 实例释放。  

| 图集中行 | 第 1 列 | 第 2 列 | 第 3 列 | 第 4 列 |
| --- | --- | --- | --- | --- |
| 1 | idle_0 | idle_1 | walk_0 | walk_1 |
| 2 | attack_0 | attack_1 | attack_2 | cast_0 |
| 3 | cast_1 | death_0 | death_1 | death_2 |

- `UnitView`、`UnitFramePainter` 和拖拽影继续读取现有单位帧 key。`portrait_<unitId>` 是从原创单位 idle 图提取的 32×32 头像，可接入口为抽屉头像条、商店卡、装备页与查看卡。`portrait_<heroId>` 是 48×48 独立英雄肖像。
- `Assets.regionFx()` 从清单读取相同 key 的独立 PNG，包括技能发射与落点、亡语冲击、状态和通用命中。伤害数字的 PNG 每张只包含一个共享字形，实际数值和着色仍由战斗浮字渲染逻辑控制。
- `icon_<equipmentId>`、`icon_<synergyId>` 分别按装备和羁绊数据 ID 使用。抽屉当前的装备类别回退仍可以读取三个现有的 `ui_icon_equip_*`。

## 抽屉、通用面板与按钮

- `UiArt` 可按同名 key 读新 PNG，`UiKeys` 切分数值沿用现有口径。manifest 的 `nine_patch` 顺序是左、右、上、下。悬停及选中槽图的中央透明，可直接叠到空槽上。
- `ui_panel_9slice` 对应 `PlaceholderKeys.PANEL_9SLICE`，经 `Assets.region()` 加载。用于退出、通知、羁绊、悬停卡、商店和出售区等现有面板。需要拉伸时可按 5/5/5/5 使用 NinePatch。
- 通用文本按钮的 `ui_button_normal`、`ui_button_hover`、`ui_button_pressed`、`ui_button_disabled` 均为 48×24，切分 8/8/8/8。四态关闭按钮均为 14×14，pressed 对应 `ui_btn_close_pressed`。可在现有按钮 Actor 里根据禁用、按下、悬停、正常的优先顺序选择图，文案继续由 BitmapFont 绘制。
- `main_menu_background` 是 640×360 的主菜单背景。对应 `Assets.mainMenuBackground()` 中的显式加载路径。

## 棋盘与地块

- `BattleRenderer.drawGrid` 当前用白纹理着色绘制，棋盘几何在 `BoardGeometry`。先把 `board_frame_<sceneId>`（216×248）绘于原棋盘外各留 12 px 的范围，再逐格绘制 32×32 地砖。
- 行号 0–2 用 `enemy`，3 用 `neutral`，4–6 用 `ally`。地砖 key 为 `tile_<sceneId>_<area>_<a|b>`，用 `(x+y)&1` 做交错。原格线、威胁预览、拖放和棋子渲染继续建立在原格坐标之上。
- 棋盘渲染、点击几何、英雄选择和其他新图入口由第二阶段接线。美术阶段未运行游戏做整屏效果验证。

## 功能纹理与平台文件

- `fx_white` 是绘制血条、颜色叠层、战斗高亮和矩形的 1×1 白色 texel。交付文件从生成图中取了一个 `RGBA(255,255,255,255)` 像素，可按旧 key 接入并继续用 batch tint 着色。
- `font/fusion_pixel_12.png` 以 `fusion_pixel_12.fnt` 的 glyph rect、advance 和 Unicode 映射为契约。当前字体页继续保留。若要替换字体，须一起生成可覆盖原有动态文案的字体与同源 .fnt。
- 桌面图标对应 `Lwjgl3Launcher.setWindowIcon`。Android 与 iOS 原生图标单列在清单里，按旧资源的目录和尺寸提供供移植的文件。Android 适配版和 iOS 启动画面需要在平台代码接线时实机确认。

## 覆盖与检查

| 范围 | 交付 |
| --- | --- |
| 单位动作 | 33 单位 × 12 帧，共 396 帧 |
| 头像 | 33 单位、3 英雄 |
| 棋盘 | 3 框、18 地块 |
| 界面 | 21 抽屉及通用面板 key、6 个新增按钮状态 key、1 主菜单背景 |
| 战斗贴图 | 56 技能与通用 FX、10 状态、10 数字、1 白色基础纹理 |
| 数据图标 | 8 装备、8 羁绊 |
| 平台 | 4 桌面窗口图标、26 个移动端图标和启动图规格 |

按 key 计共交付 604/605 项（99.8%，含平台及新增 key），合计 241 张 PNG。既有游戏内文件纹理覆盖 409/410，缺项是 `font/fusion_pixel_12.png`。既有主菜单背景、12 张抽屉贴图和全部 396 个单位帧均有替代素材。

全部交付 key 已核对 PNG 尺寸、透明度和可见像素，33 张单位图集逐张检查了 12 个不重叠的帧矩形。以最近邻放大观察了动作、角色辨识、色调、面板边框和状态图标。丛林游侠首版的白底在检查中淘汰，清单映射至通过检查的重生成版本。
