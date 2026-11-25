# UI 抽屉化改造 · 实施最终事实清单

> 日期：2026-11-22　用途：设计文档回写的唯一事实来源（由执行方批4 返回的"文档回写用最终事实清单"落盘，协调方忠实转述）
> 代码：分支 `feature/new_idea-2`，批0~批4 已提交（见 `git log`）；测试 1025 / 0 / 0 / 0
> 上游：`assets_src/ui_redesign/UI_PLAN.md`（方案与 Q1~Q11 裁决）、`ASSET_REQUIREMENTS.md`（采购单）

## 0. 主人裁决汇总（实施依据）

| # | 议题 | 最终决定 |
|---|---|---|
| Q1 | 装备网格 | 3 列 × 2 行可见 + 滚动 |
| Q2 | 开合 | 左缘竖把手点击开合；点抽屉外或 Esc 收起；收起留把手（角标「后备 n/9」）；默认收起；战毕不自动恢复展开；从抽屉往外拖棋子时面板让位 |
| Q3 | 出售 | 保留现有拖拽出售区 ⑦（零改动）；抽屉内不做出售按钮 |
| Q4 | 头像条 | = 备战席 9 格横向形态，可见 6、滚轮/滑块看余下，槽号即 bench 下标 |
| Q5 | 装备区 | 网格 = 背包库存；「装备」标签页 = 选中棋子三件并可卸下；「介绍」标签页 = 棋子详情；抽屉取代详情弹窗与背包悬停卡 |
| Q6 | 战斗期 | 默认收起；允许只读展开；禁止写操作 |
| Q7 | 抽屉尺寸 | 244×218，不压棋盘 |
| Q8 | 缩放 | 抽屉走不缩放层 |
| Q9 | 让位件 | ⑥ 开战按钮迁到右侧 (508,200)；抽屉展开时压制备战期棋盘悬停卡 |
| Q10 | 坐标漂移 | 另案处理；本次按实机落点写，几何常量化留校准 |
| Q11 | 立绘 | 选中棋子 idle 帧放大 2 倍 |
| 追加 | 点棋子 | = 选中并展开抽屉（维持） |
| 追加 | 通知层级 | A 案：抽屉层移到 ⑨ 通知之上（展开期间通知被整块盖住、L 键大窗左半也被盖） |
| 追加 | 装备待定态 | 待定期间点抽屉外不收起；把手/关闭钮/Esc 仍可收起；待定清除后恢复点外收起 |
| 追加 | 稀有度底色 | 纯观感参数，不动，留最终目检时现场调 |
| 追加 | 敌阵悬停卡 | 接受：抽屉展开时备战期敌阵侦察虚影的悬停卡一并抑制（侦察请先收起抽屉） |
| 追加 | 战斗期查看范围 | 接受：战斗期只读展开只显示战前最后选中的棋子，不提供切换 |
| 终审 | 2026-11-22 | 主人终审通过：素材与交付物入库并推送；GDD 按清单同步；本次 UI 改造收官 |

## 1. 抽屉几何（`render/ui/DrawerGeometry`；640×360 y-up）

> **两套坐标，务必区分**（`DrawerGeometry.java:6-10`）：
> - **抽屉局部**：原点 = 展开态抽屉主体左下角。下文三分区及其内的部件矩形（标题板 / 立绘 / 描述框 / 标签 / 槽 / 滚动条 / 卸下钮 / 关闭钮）均为此系。
> - **舞台**（= 不缩放层局部）：主体矩形、把手、角标、交互域用此系。
> - 换算：舞台 = 抽屉局部 + (2, 88)。例：头像条分区局部 y 78~131 = 舞台 y 166~219，与把手舞台矩形 (0,166,14,53) 等高对齐（`DrawerGeometry.java:192`）。

- 主体 244×218 @ 舞台 (2,88)~(246,306)；滑动距离 246；收起态主体右缘 0；常量待主人实机核对后可校准。
- 把手 14×53 @ 舞台 (0,166)（与头像条分区——局部 y 78~131，即舞台 y 166~219——等高对齐，M09 九宫格纵向 72→53）；内容列自抽屉局部 x=14 起（把手右缘 + 2）。
- 信息区 y 131~212：标题板 (6,198,232,14)（通栏，文字 12px）；立绘 64×64 @ (14,132)；描述框 (82,132,120,64)，滚动视口 (86,134,112,60)，9px 字 12 列、可见 6 行、ScrollPane 滚到底（最长 22~23 行）；标签 32×30 @ x=206（介绍 y 166~196 / 装备 y 132~162）；「装备」页三行（武器/盔甲/饰品）行距 18、行矩形 (84,y,116,16) y=176/158/140，「卸下」钮 42×14 右对齐；关闭钮 14×14 @ (224,198)。
- 头像条 y 78~131：可见 6 槽 × 34、间距 4（槽距 38）@ y 93~127，自 x=14；滚动条轨 (14,83,224,8)、滑块 12；槽号 = bench 下标；分隔条 y 78~82 与 127~131（x 6..238）。
- 装备区 y 6~78：网格 (14,6,110,72) 3×2 槽 34/间 4（首行可滚，行距 38）；纵向滚动条 (124,6,8,8→72 高) 滑块 8；预览 (132,6,106,72) = 图标位 y 38~78（图标 ×2=32px）+ 描述滚动视口 (134,8,102,28)（10 列 × 可见 3 行，可滚到底）。
- 角标「后备 n/9」(16,203,54,16)，仅收起态；让位淡色 0.3；开合 0.2s ease-out；不缩放层 scale 1.25 @ (−80,−45)。
- 像素事实（默认 1280×720）：展开态 UI 像素包围盒 x[0..491] y[108..543]，不压 ④（左缘 495）/ ⑧（546）/ ①（≤104）；素材 1 虚拟像素 = 2 整屏幕像素。
- 已知约束：全局缩放系数仅约 0.79~0.82（精确 0.793~0.817，`RenderScaleFitTest` 锁定：0.79 压商店栏、0.82 压棋盘）区间内抽屉不压棋盘/商店栏/顶栏（不缩放层固有代价，当前 0.8）；战斗期只读展开会压棋盘第 0 列约 2.8 虚拟像素（战斗期棋盘比备战期略大）。

## 2. 类清单

- `render/ui`：`UnscaledLayer`、`DrawerGeometry`、`DrawerController`、`DrawerPanel`（装配根）、`DrawerSkin`、`DrawerBadge`、`DrawerScrollKnob`、`DrawerInfoSection`、`DrawerStripSection`、`DrawerEquipSection`、`DrawerTextBlock`、`DrawerSelection`、`DrawerStripScroll`、`DrawerEquipScroll`、`DrawerInfoText`、`DrawerEquipText`
- `input`：`DrawerHitTest`（接口）、`DrawerOutsideTouchProcessor`
- `render`：`UiKeys`、`UiArt`、`UiPlaceholderPainter`、`board/UnitFramePainter`

## 3. 输入分层与路由

multiplexer 自上而下：`dialogStage` → `DrawerOutsideTouchProcessor`（永不吃事件；抽屉开且按下点在抽屉外 → 抬手收起；转场窗口或**装备待定态**不收起；把手/关闭钮/Esc 直连控制器不受影响）→ `uiStage`（抽屉在 `UnscaledLayer`：把手/关闭钮/标签/滑块/装备格/「卸下」钮/描述 ScrollPane 可触摸，其中装备格的点击由其自带监听处理；头像格无监听、事件穿透；主体可命中但无监听——挡住下层面板但不吞事件）→ `BoardInputProcessor`（头像格的起手与落点命中经 `DrawerHitTest`，**屏幕坐标**；滚轮是否落在头像条/装备分区也经它判定，`amountY>0` = 向下 = 看后面）→ `GlobalKeyProcessor`（Esc 先收抽屉再开暂停）。

行为：点棋子 = 选中并展开；穿上即选中；从抽屉往外拖 → 让位（强制收起 + 0.3）；任何相位跳变强制收起。

## 4. 绘制层级（uiStage 自下而上，`BattleScreen.uiLayerOrder`）

① 顶栏 → ⑥ 开战钮（UI (508,200) 64×40）→ 战斗 HUD → 横幅 → 终局面板 → ⑧ 商店栏 → ⑤ 羁绊 → **⑨ 通知 → 抽屉层**（A 案：展开期间通知被盖）→ 悬停卡 → 转场收点。

## 5. 素材链

`Assets.uiRegion(id)` / `Assets.uiPatch(id)` → `UiArt`（`art/ui/{id}.png`，Nearest 过滤，缺文件缓存 miss）→ `PlaceholderArt`（按 `UiKeys` 生成平色占位，`verifyCoverage` 防漏）→ 仍缺抛错。

键集 `UiKeys`：必需 12（`ui_panel_body_9p` 64² 12/12/12/12、`ui_inset_9p` 24² 6、`ui_slot_9p` 36² 8、`ui_slot_selected_9p` 36² 8、`ui_tab_active/inactive_9p` 32×18 6、`ui_scroll_track_9p` 16² 4、`ui_scroll_knob_9p` 12² 4、`ui_handle` 14×72 0/0/12/12、`ui_btn_close` 14²、`ui_divider_h` 24×4 4/4/0/0、`ui_title_plate_9p` 48×18 8/8/6/6）+ 加分 8（`ui_btn_sell` 不采购）。

## 6. 已退役

`UnitDetailDialog`（含 `BattleScreen` 中永不触发的死装配）、`InventoryPanel`、`BoardGeometry.benchSlotCenter / pixelToBenchSlot / inventorySlotCenter` 与 ②③ 矩形常量（`BENCH_X/Y/W/H` 仅作缩放公式样本保留）、`BattleTransitionController.benchOffsetX()`、② 世界域绘制、背包悬停卡源、`BoardInputProcessor.scrolled` 恒 false。

保留：`UnitInfoText.clipLines`（活调用方 `ChestDialog`）、`PlaceholderKeys.PANEL_9SLICE`（顶栏/商店/羁绊/通知/弹窗仍在用）。

## 7. 引导文案

`BoardGeometry.SHOP_HINT_TEXT`「拖拽棋子布阵 · 拖至右上出售区卖出 · 点开战」——方位词由 `BoardGeometry.SELL_ZONE_*` 常量派生（`sellZoneDirectionText()` = 「右上」，依据实机落点：三张探针截图均显示出售区在右上）；抽屉展开时文案左缘让位到抽屉主体右缘 +4（逐帧按当前舞台右缘计算）。

## 8. 观感调参（留最终目检）

- 稀有度底色：`core/src/main/java/com/voidvvv/kz_auto_chess_n/render/ui/DrawerEquipSection.java` 的 `RARITY_ALPHA = 0.35f`（约第 44 行；白片叠在槽底内缩 2px 的区域上）；加强可提到 0.5~0.6，无其它依赖。
- 待定高亮：同文件 `PENDING_ALPHA = 0.9f`。

## 9. 入库情况与待办

- 入库内容（commit `a2c4fbc`，2026-11-22）：`assets/art/ui/` 1:1 必需 12 张；`assets_src/ui_redesign/` 全部（方案 / 采购单 / 交付说明 / manifest / 预览 / 草图 / `sourced_assets/`）；`assets_src/sourced/golden-ui/` 许可存证；`assets_src/tools/` 两个 UI 脚本。
- **`1_25x/` 备用列不入库**：Q8 裁定抽屉走不缩放层、只用 1:1，永不使用；已加入 `.gitignore`，说明见 `assets_src/ui_redesign/ASSET_DELIVERY.md` §2 的协调方注（manifest 为逐张记录的数组且被测试直接解析，故未在其中加注）。
- 素材已入库（commit `a2c4fbc`，2026-11-22）：`UiArtInventoryTest` 的 4 个素材对账用例在干净检出下实跑（入库前按设计条件跳过）。
- **终审通过**（主人 2026-11-22）：逐像素清晰度与非整数窗口、拖拽手感（死区/让位时机）、滚动条拖滑块、⑨ 被盖住的实际感受、引导语让位后与棋盘上沿的观感、标签九宫格纵向拉伸观感、装备格稀有度底色——观感参数（`DrawerEquipSection.RARITY_ALPHA` 等）留后续调参备查，入口见 §8。
- **已裁决接受（主人 2026-11-22 终审；属既定行为，不作为缺陷处理）**——实施中发现、本清单原未覆盖的两项副作用：
  1. 抽屉展开时，备战期**敌阵侦察虚影的悬停卡也被抑制**——它与玩家棋子共用同一张棋盘悬停卡，抑制位作用于 `BoardInputProcessor.getHoverCandidate()` 整体（Q9 只裁了「棋盘悬停卡让位」）。**接受**：需要侦察敌阵时先收起抽屉。
  2. **战斗期不能切换选中**——非备战期 `BoardInputProcessor.touchDown` 直接吞事件，战斗期只读展开只能看战前最后选中的那一颗棋子。**接受**：战斗期查看范围限于战前最后选中的棋子。
- 小项已清：代码注释滞后 3 处及同文件同因的 5 处已修正（commit `4e39b38`，纯注释、无行为改动）；采购单 §1 第 4 条与 M07/M08 取 4px 的矛盾已在 `ASSET_REQUIREMENTS.md` 中改为明确例外（以 `UiKeys` 为准）。
- 另案：界面坐标上下镜像的既有漂移（设计表 y 向下而 ②③⑤⑦⑨ 表值被当 y-up 用）。
