package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.input.BoardInputProcessor;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.EventInbox;
import com.voidvvv.kz_auto_chess_n.render.FloatTextFormat;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;
import com.voidvvv.kz_auto_chess_n.systems.ThreatPredictor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 棋盘域总绘制（render §3.2 层序 ②~⑧；单 batch，begin/end 配对由本类保证）。
 *
 * <p>战斗视图生命周期 = BattleState（铁律 3）：draw 内观察 battleState 引用变化自动
 * rebuild（id → UnitView 映射，避免 getUnitById 线性查找）/ clear（回 SHOPPING）。
 * 事件消费走 EventInbox cursor 游标（口径 #12：getEvents 视图只取一次缓存）；
 * 遍历 getUnits + isCleaned 自行过滤（口径 #13：不每帧调 aliveUnits）。
 * 只读 entities（铁律 1）。
 */
public final class BattleRenderer {
    /** 备战期引导文案见 {@link BoardGeometry#SHOP_HINT_TEXT}（批4：方位词与出售区常量同源；抽屉展开时左缘让位，
     *  见 {@link BoardGeometry#shopHintX}）。中文化——Fusion Pixel 已入库，降级口径见计划 §5.3-6 */

    /** 敌区格底（#2A2028——§1.3，邻格亮度差 ≤4；包级供亮度差验收件直读） */
    static final com.badlogic.gdx.graphics.Color ENEMY_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.165f, 0.125f, 0.157f, 1f);
    /** 缓冲带格底（#20252B；包级供亮度差验收件直读） */
    static final com.badlogic.gdx.graphics.Color BUFFER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.125f, 0.145f, 0.169f, 1f);
    /** 友区格底（#1D292B；包级供亮度差验收件直读） */
    static final com.badlogic.gdx.graphics.Color PLAYER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.114f, 0.161f, 0.169f, 1f);
    /** 格线（#87939B α0.18） */
    private static final com.badlogic.gdx.graphics.Color GRID_LINE_TINT =
            new com.badlogic.gdx.graphics.Color(0.529f, 0.576f, 0.608f, 0.18f);
    /** 棋盘外框（#87939B α0.34，1px） */
    private static final com.badlogic.gdx.graphics.Color BOARD_FRAME_TINT =
            new com.badlogic.gdx.graphics.Color(0.529f, 0.576f, 0.608f, 0.34f);
    /** 合法落点填色（#80E0B0 α0.30——放棋体下面，§1.3 填色制） */
    private static final com.badlogic.gdx.graphics.Color DROP_OK_FILL =
            new com.badlogic.gdx.graphics.Color(0.502f, 0.878f, 0.690f, 0.30f);
    /** 合法落点绿线（#80E0B0 α0.95，内缩 5px——与威胁边均露出） */
    private static final com.badlogic.gdx.graphics.Color DROP_OK_LINE =
            new com.badlogic.gdx.graphics.Color(0.502f, 0.878f, 0.690f, 0.95f);
    private static final com.badlogic.gdx.graphics.Color DROP_BAD_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.3f, 0.3f, 0.3f);   // 非法落点红（保持红色反馈）
    private static final com.badlogic.gdx.graphics.Color SELL_DROP_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.8f, 0.25f, 0.45f);  // 出售区拖入高亮（§1.3 恢复高亮）
    /** 出售区空闲底（§1.3：底 α0.24、文字 α0.65） */
    private static final com.badlogic.gdx.graphics.Color SELL_ZONE_IDLE =
            new com.badlogic.gdx.graphics.Color(0.45f, 0.32f, 0.16f, 0.24f);

    // —— 战前危险提示（品质改造 §1.1；画法参数取 §1.3 初版，观感统一留批 4） ——
    /** 琥珀（A 破阵踏 #F2B544） */
    private static final com.badlogic.gdx.graphics.Color THREAT_AMBER =
            new com.badlogic.gdx.graphics.Color(0.949f, 0.710f, 0.267f, 1f);
    /** 红橙（B 处决 #FF785D） */
    private static final com.badlogic.gdx.graphics.Color THREAT_RED_ORANGE =
            new com.badlogic.gdx.graphics.Color(1f, 0.471f, 0.365f, 1f);
    /** 危险格淡底（§1.3：A α0.23 / B α0.22） */
    private static final com.badlogic.gdx.graphics.Color THREAT_FILL_AMBER =
            new com.badlogic.gdx.graphics.Color(0.949f, 0.710f, 0.267f, 0.23f);
    private static final com.badlogic.gdx.graphics.Color THREAT_FILL_RED =
            new com.badlogic.gdx.graphics.Color(1f, 0.471f, 0.365f, 0.22f);
    /** 来源格角标衬底（§1.3 #15171C） */
    private static final com.badlogic.gdx.graphics.Color THREAT_MARK_BACKING =
            new com.badlogic.gdx.graphics.Color(0.082f, 0.090f, 0.110f, 1f);
    /** 提示文字浅色（§1.1：浅色 12px 字深色底；字体原生 12px） */
    private static final com.badlogic.gdx.graphics.Color THREAT_TEXT_TINT =
            new com.badlogic.gdx.graphics.Color(0.92f, 0.94f, 0.96f, 1f);
    private static final float THREAT_EDGE_ALPHA = 0.95f;
    private static final int THREAT_INSET_PX = 2;
    private static final int THREAT_EDGE_PX = 2;
    private static final int THREAT_CORNER_ARM_PX = 6;
    private static final int THREAT_SOURCE_MARK_W = 6;
    private static final int THREAT_SOURCE_MARK_H = 2;
    /** 邻格短虚线节律（§1.3：5px 亮 / 3px 间隔） */
    private static final int THREAT_DASH_ON_PX = 5;
    private static final int THREAT_DASH_OFF_PX = 3;

    private final Assets assets;
    /** 当前附着的战斗实例（null = 备战期/无战斗；变化即 rebuild/clear） */
    private BattleState trackedBattle;
    private final Map<Integer, UnitView> unitViews = new HashMap<Integer, UnitView>();
    private final List<UnitView> viewList = new ArrayList<UnitView>();
    private final EventInbox inbox = new EventInbox();
    private final ProjectileView projectileView;
    private final FxLayer fxLayer;
    private final ObjectPool<FloatingText> floatPool = new ObjectPool<FloatingText>(
            new ObjectPool.Factory<FloatingText>() {
                @Override
                public FloatingText create() {
                    return new FloatingText();
                }
            });
    private final List<FloatingText> floats = new ArrayList<FloatingText>();
    /** 同帧飘字错位槽位（render §5.2：同目标同帧多段错位堆叠） */
    private int floatSlot;
    /** 引导文案排版缓存（懒建一次，零稳态分配） */
    private GlyphLayout hintLayout;
    /** 战前预判缓存（零稳态分配）：敌阵引用或部署指纹变化才重算（§1.1） */
    private java.util.List<ThreatPredictor.Preview> threatPreviews;
    private java.util.List<WaveSpec> threatWaveRef;
    private int threatFingerprint;

    public BattleRenderer(Assets assets) {
        this.assets = assets;
        this.projectileView = new ProjectileView();
        this.fxLayer = new FxLayer(assets);
    }

    /**
     * 总入口（BattleScreen 每帧调用）。
     *
     * @param alpha       逻辑步间插值系数（弹道外推）
     * @param renderClock 渲染帧时钟（秒）
     * @param dt          本帧时长（动画推进）
     * @param input       棋盘域输入（ghost 与落点高亮只读暴露；可为 null）
     * @param worldScale   备战期世界域内容缩放（= 1 / worldCamera.zoom）——引导文案让位的坐标反算用
     * @param drawerStageRightEdge 抽屉主体当前舞台右缘（收起态 ≤ 0 = 不让位）——引导文案让位用
     */
    public void draw(SpriteBatch batch, RunContext ctx, float alpha, float renderClock, float dt,
                     BoardInputProcessor input, float chromeFadeAlpha, float worldScale,
                     float drawerStageRightEdge) {
        syncBattleScope(ctx);
        batch.begin();
        drawGrid(batch);
        floatSlot = 0;
        if (ctx.getRunState().getPhase() == GamePhase.SHOPPING || ctx.getBattleState() == null) {
            drawLegalDropFill(batch, input); // §1.3：合法落点填色放棋体下面
            drawShopping(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge, input);
            drawDropOverlay(batch, ctx, input);
        } else {
            if (chromeFadeAlpha > 0f) { // 开战转场窗口：备战层 chrome 随姿态退场（render §5.6；K5 不画部署帧）
                drawShoppingChrome(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge, input);
            }
            drawBattle(batch, ctx, alpha, renderClock, dt);
        }
        batch.end();
    }

    /** 战斗作用域同步：battleState 引用变化 → 视图集合整体重建/清空（P1c：起始格取备战期位置滑入） */
    private void syncBattleScope(RunContext ctx) {
        BattleState current = ctx.getBattleState();
        if (current == trackedBattle) {
            return;
        }
        unitViews.clear();
        viewList.clear();
        floats.clear(); // 池中空闲实例保留复用
        projectileView.clear();
        fxLayer.clear();
        inbox.detach();
        if (current != null) {
            BattleEntryCells playerEntries = BattleEntryCells.ofDeployed(ctx.getPlayer());
            BattleEntryCells enemyEntries = BattleEntryCells.ofEnemyWave(ctx.getRunState().getEnemyWave());
            for (BattleUnit unit : current.getUnits()) { // 含亡者：死亡淡出仍需视图
                BattleEntryCells entries = unit.getSide() == Side.PLAYER ? playerEntries : enemyEntries;
                int[] start = entries.consume(unit.getTemplate().getId());
                UnitView view = new UnitView(unit, assets,
                        start == null ? unit.getGridX() : start[0],
                        start == null ? unit.getGridY() : start[1]); // 无匹配落锚点（无滑入，无害）
                unitViews.put(view.unitId(), view);
                viewList.add(view);
            }
            inbox.attach(current);
        }
        trackedBattle = current;
    }

    // —— ④ 棋盘格底：敌区/缓冲带/玩家区分色 + 格线 ——

    private void drawGrid(SpriteBatch batch) {
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        for (int y = 0; y < GameBalance.BOARD_ROWS; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                int px = BoardGeometry.BOARD_X + x * BoardGeometry.CELL;
                int py = BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (y + 1) * BoardGeometry.CELL;
                batch.setColor(zoneTint(y));
                batch.draw(white, px, py, BoardGeometry.CELL, BoardGeometry.CELL);
                batch.setColor(GRID_LINE_TINT);
                batch.draw(white, px, py, BoardGeometry.CELL, 1); // 格线（上边）
                batch.draw(white, px, py, 1, BoardGeometry.CELL); // 格线（左边）
            }
        }
        batch.setColor(BOARD_FRAME_TINT); // 棋盘外框（§1.3：1px α0.34）
        batch.draw(white, BoardGeometry.BOARD_X, BoardGeometry.BOARD_Y,
                BoardGeometry.BOARD_W, 1);
        batch.draw(white, BoardGeometry.BOARD_X, BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - 1,
                BoardGeometry.BOARD_W, 1);
        batch.draw(white, BoardGeometry.BOARD_X, BoardGeometry.BOARD_Y, 1, BoardGeometry.BOARD_H);
        batch.draw(white, BoardGeometry.BOARD_X + BoardGeometry.BOARD_W - 1, BoardGeometry.BOARD_Y,
                1, BoardGeometry.BOARD_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private static com.badlogic.gdx.graphics.Color zoneTint(int gridY) {
        if (gridY <= 2) {
            return ENEMY_ZONE_TINT;
        }
        if (gridY == 3) {
            return BUFFER_ZONE_TINT;
        }
        return PLAYER_ZONE_TINT;
    }

    // —— 备战期：玩家部署 + 敌阵预览（侦察，口径 #25）；② 备战席已迁入 UI 抽屉头像条（UI 抽屉化批2） ——

    /** 备战层总入口：chrome（⑦ 提示虚影，随转场姿态）+ 玩家部署帧（随淡出系数；转场期由 UnitView 接管不画——K5） */
    private void drawShopping(SpriteBatch batch, RunContext ctx, float chromeFadeAlpha,
                              float worldScale, float drawerStageRightEdge, BoardInputProcessor input) {
        drawShoppingChrome(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge, input);
        Player player = ctx.getPlayer();
        for (int y = 4; y <= 6; y++) { // 逐行自屏幕上往下绘制（y 大者在屏幕上方，先画）
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit != null) {
                    int[] center = BoardGeometry.cellCenter(x, y);
                    drawUnitFrame(batch, unit.getTemplate().getId(),
                            PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false,
                            chromeFadeAlpha, unit.getStar(), SideColors.PLAYER_MARK, 1f);
                }
            }
        }
    }

    /**
     * 备战层 chrome：敌阵虚影（淡出=实体化一拍）+ ⑦ 出售区 + 布阵提示（淡出）。
     * ② 备战席槽 + 席上棋子的世界域绘制已退役（UI 抽屉化批2：由 DrawerPanel 头像条接管，备战席不再左滑）。
     */
    private void drawShoppingChrome(SpriteBatch batch, RunContext ctx, float chromeFadeAlpha,
                                      float worldScale, float drawerStageRightEdge,
                                      BoardInputProcessor input) {
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（底角标 + 半透明，P1b/§1.3；转场随层淡出；恒 1★ 骨白）
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA * chromeFadeAlpha,
                    1, SideColors.ENEMY_MARK, 0.78f); // §1.3：备战敌影 α0.78
        }
        drawThreatPreviews(batch, ctx, chromeFadeAlpha);
        drawSellZone(batch, chromeFadeAlpha, input != null && input.isDropOnSellZone());
        drawShoppingHint(batch, chromeFadeAlpha, worldScale, drawerStageRightEdge);
    }

    // —— 战前危险提示（品质改造 §1.1；§1.4：战前标记只描述开局，真实命中以事件为准） ——

    /** 格左下角像素 x（y-up；drawGrid 同式） */
    private static int threatCellLeftX(int gridX) {
        return BoardGeometry.BOARD_X + gridX * BoardGeometry.CELL;
    }

    /** 格左下角像素 y（行 0 在顶翻转口径同 drawGrid） */
    private static int threatCellBottomY(int gridY) {
        return BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (gridY + 1) * BoardGeometry.CELL;
    }

    /** 战前预判缓存读取（敌阵引用 + 部署指纹（id 与格位加权和）任一变化才重算——零稳态分配） */
    private java.util.List<ThreatPredictor.Preview> threatPreviews(RunContext ctx) {
        java.util.List<WaveSpec> wave = ctx.getRunState().getEnemyWave();
        int fingerprint = 0;
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = ctx.getPlayer().deployedAt(x, y);
                if (unit != null) {
                    fingerprint = fingerprint * 31 + (unit.getId() ^ (x * 7) ^ (y * 13));
                }
            }
        }
        if (threatPreviews != null && threatWaveRef == wave && fingerprint == threatFingerprint) {
            return threatPreviews;
        }
        threatPreviews = ThreatPredictor.predict(ctx.getGameData(), wave, ctx.getPlayer());
        threatWaveRef = wave;
        threatFingerprint = fingerprint;
        return threatPreviews;
    }

    /** 威胁色（A 琥珀 / B 红橙） */
    private static com.badlogic.gdx.graphics.Color threatColor(ThreatPredictor.Kind kind) {
        return kind == ThreatPredictor.Kind.BREAK_STOMP ? THREAT_AMBER : THREAT_RED_ORANGE;
    }

    private static com.badlogic.gdx.graphics.Color threatFill(ThreatPredictor.Kind kind) {
        return kind == ThreatPredictor.Kind.BREAK_STOMP ? THREAT_FILL_AMBER : THREAT_FILL_RED;
    }

    private static String threatCopy(ThreatPredictor.Kind kind) {
        return kind == ThreatPredictor.Kind.BREAK_STOMP
                ? BoardGeometry.THREAT_COPY_BREAK_STOMP : BoardGeometry.THREAT_COPY_EXECUTE;
    }

    /**
     * 战前危险提示绘制（§1.1）：敌原格角标；A = 预测主目标格实线框 + 十字四邻琥珀短虚线 + 淡底；
     * B = 预测目标格四红橙角 + 后排短虚线括号；未上棋只画敌角标。危险格已由预判裁到合法棋盘
     * （保留缓冲带格展示形状）；随 chromeFadeAlpha 淡出，转场/BATTLE/RESULT 由 draw 分支保证不达。
     */
    private void drawThreatPreviews(SpriteBatch batch, RunContext ctx, float fade) {
        java.util.List<ThreatPredictor.Preview> previews = threatPreviews(ctx);
        if (previews.isEmpty()) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        for (ThreatPredictor.Preview preview : previews) {
            com.badlogic.gdx.graphics.Color color = threatColor(preview.getKind());
            com.badlogic.gdx.graphics.Color fill = threatFill(preview.getKind());
            if (preview.hasTarget()) {
                int targetPx = threatCellLeftX(preview.getTargetGridX());
                int targetPy = threatCellBottomY(preview.getTargetGridY());
                if (preview.getKind() == ThreatPredictor.Kind.BREAK_STOMP) {
                    for (int[] cell : preview.getDangerCells()) { // 十字四邻（主目标格除外）：淡底 + 短虚线
                        if (cell[0] == preview.getTargetGridX() && cell[1] == preview.getTargetGridY()) {
                            continue;
                        }
                        int px = threatCellLeftX(cell[0]);
                        int py = threatCellBottomY(cell[1]);
                        batch.setColor(fill.r, fill.g, fill.b, fill.a * fade);
                        batch.draw(white, px, py, BoardGeometry.CELL, BoardGeometry.CELL);
                        drawThreatDashedFrame(batch, white, px, py, color, THREAT_EDGE_ALPHA * fade);
                    }
                    drawThreatFrame(batch, white, targetPx, targetPy, color, THREAT_EDGE_ALPHA * fade);
                } else {
                    batch.setColor(fill.r, fill.g, fill.b, fill.a * fade);
                    batch.draw(white, targetPx, targetPy, BoardGeometry.CELL, BoardGeometry.CELL);
                    drawThreatCorners(batch, white, targetPx, targetPy, color, THREAT_EDGE_ALPHA * fade);
                    drawBacklineBracket(batch, white, preview.getBacklineRowY(), color, fade);
                }
            }
            drawThreatSourceMark(batch, white, threatCellLeftX(preview.getEnemyGridX()),
                    threatCellBottomY(preview.getEnemyGridY()), color, fade);
        }
        drawThreatHint(batch, previews, fade);
    }

    /** 提示文案（棋盘下缘 (224,14,324,32) 深色底 + 浅色 12px 字，两行基线 42/28——§1.1） */
    private void drawThreatHint(SpriteBatch batch, java.util.List<ThreatPredictor.Preview> previews,
                                float fade) {
        String secondCopy = previews.size() > 1 ? threatCopy(previews.get(1).getKind()) : null;
        String[] lines = BoardGeometry.threatHintLines(previews.size(),
                threatCopy(previews.get(0).getKind()), secondCopy);
        if (lines.length == 0) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        batch.setColor(0f, 0f, 0f, 0.62f * fade);
        batch.draw(white, BoardGeometry.THREAT_HINT_X, BoardGeometry.THREAT_HINT_Y,
                BoardGeometry.THREAT_HINT_W, BoardGeometry.THREAT_HINT_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        assets.font().setColor(THREAT_TEXT_TINT.r, THREAT_TEXT_TINT.g, THREAT_TEXT_TINT.b, fade);
        float textX = BoardGeometry.THREAT_HINT_X + BoardGeometry.THREAT_HINT_TEXT_INSET_PX;
        assets.font().draw(batch, lines[0], textX, BoardGeometry.THREAT_HINT_LINE1_Y);
        if (lines.length > 1) {
            assets.font().draw(batch, lines[1], textX, BoardGeometry.THREAT_HINT_LINE2_Y);
        }
        assets.font().setColor(com.badlogic.gdx.graphics.Color.WHITE); // 用后即还（共用字体纪律）
    }

    /** 合法落点填色（品质改造 §1.3：#80E0B0 α0.30，棋体之下——先于棋体绘制） */
    private void drawLegalDropFill(SpriteBatch batch, BoardInputProcessor input) {
        if (input == null || !input.isDragging()) {
            return;
        }
        com.voidvvv.kz_auto_chess_n.command.PlacementTarget preview = input.getDropPreview();
        if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) {
            com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell cell =
                    (com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) preview;
            batch.setColor(DROP_OK_FILL);
            batch.draw(assets.region(PlaceholderKeys.WHITE),
                    BoardGeometry.BOARD_X + cell.gridX * BoardGeometry.CELL,
                    BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (cell.gridY + 1) * BoardGeometry.CELL,
                    BoardGeometry.CELL, BoardGeometry.CELL);
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        }
    }

    /** 格实线框（内缩/线宽可参；整数对齐——render §八.2） */
    private void drawCellRectFrame(SpriteBatch batch, TextureRegion white, int px, int py,
                                   com.badlogic.gdx.graphics.Color color, float alpha,
                                   int inset, int thickness) {
        batch.setColor(color.r, color.g, color.b, alpha);
        int inner = inset;
        int span = BoardGeometry.CELL - 2 * inner;
        batch.draw(white, px + inner, py + inner, span, thickness);
        batch.draw(white, px + inner, py + BoardGeometry.CELL - inner - thickness, span, thickness);
        batch.draw(white, px + inner, py + inner + thickness, thickness, span - 2 * thickness);
        batch.draw(white, px + BoardGeometry.CELL - inner - thickness, py + inner + thickness,
                thickness, span - 2 * thickness);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** 格实线框（威胁边口径：内缩 2px、线宽 2px——§1.3） */
    private void drawThreatFrame(SpriteBatch batch, TextureRegion white, int px, int py,
                                 com.badlogic.gdx.graphics.Color color, float alpha) {
        drawCellRectFrame(batch, white, px, py, color, alpha, THREAT_INSET_PX, THREAT_EDGE_PX);
    }

    /** 格短虚线框（§1.3 邻格 5px 亮 / 3px 间隔节律） */
    private void drawThreatDashedFrame(SpriteBatch batch, TextureRegion white, int px, int py,
                                       com.badlogic.gdx.graphics.Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        int inner = THREAT_INSET_PX;
        int span = BoardGeometry.CELL - 2 * inner;
        drawDashedEdgeH(batch, white, px + inner, py + inner, span);
        drawDashedEdgeH(batch, white, px + inner, py + BoardGeometry.CELL - inner - THREAT_EDGE_PX, span);
        drawDashedEdgeV(batch, white, px + inner, py + inner, span);
        drawDashedEdgeV(batch, white, px + BoardGeometry.CELL - inner - THREAT_EDGE_PX, py + inner, span);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private void drawDashedEdgeH(SpriteBatch batch, TextureRegion white, int x, int y, int length) {
        int step = THREAT_DASH_ON_PX + THREAT_DASH_OFF_PX;
        for (int offset = 0; offset < length; offset += step) {
            batch.draw(white, x + offset, y, Math.min(THREAT_DASH_ON_PX, length - offset), THREAT_EDGE_PX);
        }
    }

    private void drawDashedEdgeV(SpriteBatch batch, TextureRegion white, int x, int y, int length) {
        int step = THREAT_DASH_ON_PX + THREAT_DASH_OFF_PX;
        for (int offset = 0; offset < length; offset += step) {
            batch.draw(white, x, y + offset, THREAT_EDGE_PX, Math.min(THREAT_DASH_ON_PX, length - offset));
        }
    }

    /** 格四角标记（每角 6px 臂长 / 2px 线宽——§1.3 B 预测目标） */
    private void drawThreatCorners(SpriteBatch batch, TextureRegion white, int px, int py,
                                   com.badlogic.gdx.graphics.Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        int left = px + THREAT_INSET_PX;
        int right = px + BoardGeometry.CELL - THREAT_INSET_PX;
        int bottom = py + THREAT_INSET_PX;
        int top = py + BoardGeometry.CELL - THREAT_INSET_PX;
        batch.draw(white, left, bottom, THREAT_CORNER_ARM_PX, THREAT_EDGE_PX);
        batch.draw(white, left, bottom, THREAT_EDGE_PX, THREAT_CORNER_ARM_PX);
        batch.draw(white, right - THREAT_CORNER_ARM_PX, bottom, THREAT_CORNER_ARM_PX, THREAT_EDGE_PX);
        batch.draw(white, right - THREAT_EDGE_PX, bottom, THREAT_EDGE_PX, THREAT_CORNER_ARM_PX);
        batch.draw(white, left, top - THREAT_EDGE_PX, THREAT_CORNER_ARM_PX, THREAT_EDGE_PX);
        batch.draw(white, left, top - THREAT_CORNER_ARM_PX, THREAT_EDGE_PX, THREAT_CORNER_ARM_PX);
        batch.draw(white, right - THREAT_CORNER_ARM_PX, top - THREAT_EDGE_PX, THREAT_CORNER_ARM_PX, THREAT_EDGE_PX);
        batch.draw(white, right - THREAT_EDGE_PX, top - THREAT_CORNER_ARM_PX, THREAT_EDGE_PX, THREAT_CORNER_ARM_PX);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** B 后排短虚线括号（玩家最后一个有棋的排；左右两道竖向虚线——§1.1） */
    private void drawBacklineBracket(SpriteBatch batch, TextureRegion white, int rowY,
                                     com.badlogic.gdx.graphics.Color color, float fade) {
        if (rowY < 0) {
            return;
        }
        batch.setColor(color.r, color.g, color.b, THREAT_EDGE_ALPHA * fade);
        int span = BoardGeometry.CELL - 2 * THREAT_INSET_PX;
        drawDashedEdgeV(batch, white, BoardGeometry.BOARD_X + THREAT_INSET_PX,
                threatCellBottomY(rowY) + THREAT_INSET_PX, span);
        drawDashedEdgeV(batch, white,
                BoardGeometry.BOARD_X + BoardGeometry.BOARD_W - THREAT_INSET_PX - THREAT_EDGE_PX,
                threatCellBottomY(rowY) + THREAT_INSET_PX, span);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** 来源格上角边条（同色 6×2px + #15171C 1px 衬底——§1.3） */
    private void drawThreatSourceMark(SpriteBatch batch, TextureRegion white, int px, int py,
                                      com.badlogic.gdx.graphics.Color color, float fade) {
        batch.setColor(THREAT_MARK_BACKING);
        batch.draw(white, px + THREAT_INSET_PX - 1, py + BoardGeometry.CELL - THREAT_INSET_PX - 3,
                THREAT_SOURCE_MARK_W + 2, THREAT_SOURCE_MARK_H + 2);
        batch.setColor(color.r, color.g, color.b, THREAT_EDGE_ALPHA * fade);
        batch.draw(white, px + THREAT_INSET_PX, py + BoardGeometry.CELL - THREAT_INSET_PX - 2,
                THREAT_SOURCE_MARK_W, THREAT_SOURCE_MARK_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** ⑦ 出售区（render §九；棋盘域自绘，仅 SHOPPING 路径可达；fade = 转场淡出系数）。
     *  品质改造 §1.3：空闲底 α0.24 / 文字 α0.65，拖入恢复高亮（金红 + 文字全亮） */
    private void drawSellZone(SpriteBatch batch, float fade, boolean hovering) {
        TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
        batch.setColor(hovering ? SELL_DROP_TINT : SELL_ZONE_IDLE);
        batch.draw(panel, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        assets.font().setColor(1f, 1f, 1f, (hovering ? 1f : 0.65f) * fade); // 用后即还（共用字体纪律）
        assets.font().draw(batch, "出售", BoardGeometry.SELL_ZONE_X + 16f, BoardGeometry.SELL_ZONE_Y + 28f);
        assets.font().setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** 备战期引导文案（棋盘上方居中，不遮挡棋盘/备战席/⑧ 商店栏，feedback01 修正；字体缺文件回退时中文不渲染但不炸——§5.3-6） */
    private void drawShoppingHint(SpriteBatch batch, float fade, float worldScale, float drawerStageRightEdge) {
        if (hintLayout == null) {
            hintLayout = new GlyphLayout(assets.font(), BoardGeometry.SHOP_HINT_TEXT);
        }
        assets.font().setColor(1f, 1f, 1f, fade); // 用后即还（共用字体纪律）
        // 左缘：默认居中；抽屉展开时让位到抽屉舞台右缘 + 4px 之外（批4，BoardGeometry.shopHintX 纯函数可测）
        assets.font().draw(batch, BoardGeometry.SHOP_HINT_TEXT,
                BoardGeometry.shopHintX(hintLayout.width, worldScale, drawerStageRightEdge), BoardGeometry.SHOP_HINT_Y);
        assets.font().setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    // —— 战斗期：⑦ 单位视图 → ⑧ 弹道 → 特效 → 飘字 ——

    private void drawBattle(SpriteBatch batch, RunContext ctx, float alpha, float renderClock, float dt) {
        inbox.forEachNew(new java.util.function.Consumer<CombatEvent>() { // 长持有 consumer 语义（口径 #12）
            @Override
            public void accept(CombatEvent event) {
                routeEvent(event, renderClock);
            }
        });
        for (int i = 0; i < viewList.size(); i++) {
            viewList.get(i).update(renderClock, dt);
        }
        sortViewsByScreenYDescending(renderClock); // §1.3：逐行自屏幕上往下绘制棋体（下方覆盖上方）
        for (int i = 0; i < viewList.size(); i++) {
            viewList.get(i).draw(batch, renderClock);
        }
        projectileView.draw(batch, assets, trackedBattle.getProjectiles(), alpha);
        fxLayer.updateAndDraw(batch, dt, trackedBattle);
        for (int i = floats.size() - 1; i >= 0; i--) { // 倒序换出回收（零分配）
            FloatingText f = floats.get(i);
            if (!f.update(dt)) {
                floats.remove(i);
                floatPool.free(f);
            }
        }
        for (int i = 0; i < floats.size(); i++) {
            floats.get(i).draw(batch, assets);
        }
    }

    /** 绘制序按屏幕 y 降序（品质改造 §1.3「逐行由屏幕上往下绘制棋体」）：近乎有序 → 插入排序零分配；
     *  viewList 顺序不影响 id 查找（unitViews 为映射） */
    private void sortViewsByScreenYDescending(float renderClock) {
        for (int i = 1; i < viewList.size(); i++) {
            UnitView view = viewList.get(i);
            int y = view.virtualY(renderClock);
            int j = i - 1;
            while (j >= 0 && viewList.get(j).virtualY(renderClock) < y) {
                viewList.set(j + 1, viewList.get(j));
                j--;
            }
            viewList.set(j + 1, view);
        }
    }

    /** 事件路由：出手/施法 → 攻方动画；受击 → 白闪 + 飘字 + 落点闪光；死亡 → DEATH 锁定 */
    private void routeEvent(CombatEvent event, float renderClock) {
        switch (event.getType()) {
            case ATTACK_LAUNCHED:
                UnitView attacker = unitViews.get(event.getSourceId());
                if (attacker != null) {
                    attacker.anim().onEvent(CombatEvent.Type.ATTACK_LAUNCHED);
                    attacker.anim().triggerAttackSwing(); // 攻击摆动叠加层（attack_feedback FP1；近战/远程统一出口）
                }
                break;
            case CAST:
                UnitView caster = unitViews.get(event.getSourceId());
                if (caster != null) {
                    caster.anim().onEvent(CombatEvent.Type.CAST);
                    fxLayer.sparkCast(event.getSkillId(), caster.virtualX(renderClock),
                            caster.virtualY(renderClock), keyedEnemyCastTint(caster, event.getSkillId()));
                }
                break;
            case HIT:
                attacker = unitViews.get(event.getSourceId());
                if (attacker != null) {
                    attacker.anim().onEvent(CombatEvent.Type.HIT); // 近战即时：出手即命中
                }
                onDamaged(event, event.getSkillId(), renderClock);
                break;
            case HEALED:
            case SHIELDED:
                onDamaged(event, null, renderClock); // 只产飘字与落点闪光，无白闪
                break;
            case UNIT_DIED:
                UnitView dead = unitViews.get(event.getSourceId()); // sourceId = 亡者 id
                if (dead != null) {
                    dead.anim().onEvent(CombatEvent.Type.UNIT_DIED);
                }
                break;
            default:
                break;
        }
    }

    /** 敌方关键技施法闪光色调（品质改造 §1.1：两题敌方 CAST 用敌方琥珀色；其余原色 null） */
    private static com.badlogic.gdx.graphics.Color keyedEnemyCastTint(UnitView caster, String skillId) {
        return caster.unit().getSide() == Side.ENEMY && ThreatPredictor.isKeyedEnemySkill(skillId)
                ? THREAT_AMBER : null;
    }

    /** 受击方表现：白闪 + 飘字（FloatTextFormat）+ 落点闪光 */
    private void onDamaged(CombatEvent event, String burstSkillId, float renderClock) {
        UnitView target = unitViews.get(event.getTargetId());
        if (target == null) {
            return;
        }
        float x = target.virtualX(renderClock);
        float y = target.virtualY(renderClock);
        if (event.getType() == CombatEvent.Type.HIT) {
            target.anim().triggerHitFlash();
            target.anim().triggerHitShake(hitShakeAxis(event, target, (int) x, renderClock)); // 受击抖动（attack_feedback FP2）
        }
        FloatTextFormat.Spec spec = FloatTextFormat.of(event);
        if (spec != null) {
            float ox = (floatSlot % 5) * 7f - 14f; // 同帧错位堆叠（render §5.2）
            floatSlot++;
            FloatingText f = floatPool.obtain();
            f.spawn(spec.text, spec.color, spec.scale, x + ox, y + 6f);
            floats.add(f);
        }
        fxLayer.sparkBurst(burstSkillId, x, y);
    }

    /** 受击抖动水平轴（attack_feedback FP2）：攻击者来向（在受击者左 → +1，首拍向受力反方向弹开）；
     *  攻击者视图缺失（sourceId = -1，DamagePipeline.java:40-41）→ 按受击者 id 奇偶确定性回退，不引入随机源 */
    private int hitShakeAxis(CombatEvent event, UnitView target, int targetX, float renderClock) {
        UnitView attacker = unitViews.get(event.getSourceId());
        if (attacker == null) {
            return HitShakeAxis.fallback(event.getTargetId());
        }
        return HitShakeAxis.resolve(attacker.virtualX(renderClock), targetX, event.getTargetId());
    }

    /** 占位单位帧绘制（品质改造 §1.3：38px 正方、横向居中、底边 cy−16 锚点 + 脚下影子 + 底角标；
     *  enemyFace = 水平翻转；star 星级色 tint；markColor 敌我底角标色；shadowAlpha 影子透明度；
     *  alpha 整体透明度）。帧本体委托 {@link UnitFramePainter}（与抽屉头像格 / 立绘共用同一套取帧-翻转-tint-还原） */
    private void drawUnitFrame(SpriteBatch batch, String unitId, String anim, int frame,
                               int cx, int cy, boolean enemyFace, float alpha, int star,
                               com.badlogic.gdx.graphics.Color markColor, float shadowAlpha) {
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        batch.setColor(UnitView.UNIT_SHADOW.r, UnitView.UNIT_SHADOW.g, UnitView.UNIT_SHADOW.b,
                shadowAlpha * alpha);
        batch.draw(white, BoardGeometry.unitDrawLeft(cx), BoardGeometry.unitDrawBottom(cy),
                BoardGeometry.UNIT_SHADOW_W, BoardGeometry.UNIT_SHADOW_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        UnitFramePainter.draw(batch, assets, unitId, anim, frame,
                BoardGeometry.unitDrawLeft(cx), BoardGeometry.unitDrawBottom(cy),
                BoardGeometry.UNIT_DRAW_PX, enemyFace, alpha, StarColors.of(star));
        SideColors.drawCornerMark(batch, white, cx, cy, markColor);
    }

    // —— 拖拽 ghost 与落点高亮（input §4.3 表现层预校验；handler 层门控独立） ——

    private void drawDropOverlay(SpriteBatch batch, RunContext ctx, BoardInputProcessor input) {
        if (input == null || !input.isDragging()) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        // ⑦ 出售区拖入高亮已由 drawSellZone 按 isDropOnSellZone 恢复（§1.3）
        com.voidvvv.kz_auto_chess_n.command.PlacementTarget preview = input.getDropPreview();
        if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) {
            com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell cell =
                    (com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) preview;
            // 合法落点绿线（§1.3：2px 内缩 5px，棋体之上、与威胁边均露出；填色已在棋体下面）
            batch.setColor(DROP_OK_LINE);
            int px = BoardGeometry.BOARD_X + cell.gridX * BoardGeometry.CELL;
            int py = BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (cell.gridY + 1) * BoardGeometry.CELL;
            int inset = BoardGeometry.DROP_OK_LINE_INSET_PX;
            int span = BoardGeometry.CELL - 2 * inset;
            batch.draw(white, px + inset, py + inset, span, THREAT_EDGE_PX);
            batch.draw(white, px + inset, py + BoardGeometry.CELL - inset - THREAT_EDGE_PX, span, THREAT_EDGE_PX);
            batch.draw(white, px + inset, py + inset + THREAT_EDGE_PX, THREAT_EDGE_PX, span - 2 * THREAT_EDGE_PX);
            batch.draw(white, px + BoardGeometry.CELL - inset - THREAT_EDGE_PX, py + inset + THREAT_EDGE_PX,
                    THREAT_EDGE_PX, span - 2 * THREAT_EDGE_PX);
        } else if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Bench) {
            // 备战席落点高亮已随 ② 迁入抽屉：由 DrawerPanel 在头像格上叠选中框（世界域无备战席可画）
        } else {
            int[] cell = BoardGeometry.pixelToCell((int) input.getDragVirtualX(), (int) input.getDragVirtualY());
            if (cell != null) { // 悬停棋盘但非法行（敌区/缓冲带）：红高亮（§1.3 保持红色反馈）
                batch.setColor(DROP_BAD_TINT);
                batch.draw(white, BoardGeometry.BOARD_X + cell[0] * BoardGeometry.CELL,
                        BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (cell[1] + 1) * BoardGeometry.CELL,
                        BoardGeometry.CELL, BoardGeometry.CELL);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        Unit dragged = findUnit(ctx.getPlayer(), input.getDragUnitId());
        if (dragged != null) { // ghost：拖拽单位半透明跟随（带被拖单位星级色，CP11）
            com.badlogic.gdx.graphics.Color tint = StarColors.of(dragged.getStar());
            batch.setColor(tint.r, tint.g, tint.b, 0.65f);
            batch.draw(assets.region(PlaceholderKeys.unitFrame(
                            dragged.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0)),
                    input.getDragVirtualX() - BoardGeometry.CELL / 2f,
                    input.getDragVirtualY() - BoardGeometry.CELL / 2f,
                    BoardGeometry.CELL, BoardGeometry.CELL);
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        }
    }

    /** 名单查单位（bench + 部署表；名单小线性可）——模板 id 与星级同源 */
    private static Unit findUnit(Player player, int unitId) {
        for (Unit unit : player.getBench()) {
            if (unit.getId() == unitId) {
                return unit;
            }
        }
        for (Unit unit : player.getDeployedUnits()) {
            if (unit.getId() == unitId) {
                return unit;
            }
        }
        return null;
    }
}
