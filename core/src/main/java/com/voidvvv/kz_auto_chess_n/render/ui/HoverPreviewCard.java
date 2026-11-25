package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.input.HoverCandidate;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 悬停预览卡（Phase 5.1 R1，裁决 A：固定锚、不跟随鼠标、只读精简；feedback04 敌方悬停）。
 * 棋盘域/商店卡各一锚点、各一 {@link HoverStateMachine}（两源互斥——单指针不同时悬停多处，棋盘源优先）；
 * 棋子卡行集由 {@link UnitInfoText#previewLines} 生成（模板级，不含 spend/已穿装备——R1 口径限于棋子卡）。
 * 2026-11-21 主人整改：v5 全量文案（机制通道 17 词）后棋子卡<b>完整容纳全部文本不截断</b>——
 * 卡高自适应行数（棋盘卡 9 列/商店卡 11 列，卡内字号 0.75×＝9px），最长真实组合由
 * UnitInfoTextSeedDataTest 的容量断言锁定（27/24 行 ≤ 锚区 252/224）。
 * UI 抽屉化批3（Q5）：feedback07 的<b>背包格装备卡（第三源）退役</b>——装备信息改在抽屉装备区
 * 「大图预览 + 可滚动描述」呈现（{@code DrawerEquipSection}），其行集 / 悬停归一断言迁至 DrawerEquipTextTest。
 * 棋盘候选经 {@link HoverCandidate} 携带模板（feedback04：敌方虚影/敌侧战斗单位无玩家
 * 实例，不回查名单——名单核验移至查询侧重命中）；敌方候选首行加"（敌方）"标记。
 * 非模态瞬态：uiStage 最上层普通 Actor，无输入监听、不阻断任何交互。
 */
public final class HoverPreviewCard extends Actor {

    /** 卡内行高（正文楷体 8.5px；9px 行距保住既有容量锚点：27 行 ≤ 252） */
    static final float LINE_HEIGHT = 9f;
    private static final float PADDING_X = 4f;
    private static final float PADDING_TOP = 4f;
    private static final float PADDING_BOTTOM = 4f;
    /** 折行列宽（§5.3-5 口径 (W - 8) / 卡内字号宽 取整：(94-8)/9 = 9；(112-8)/9 = 11） */
    static final int BOARD_MAX_COLUMNS = 9;
    static final int SHOP_MAX_COLUMNS = 11;
    /** 敌方候选首行标记（feedback04 视觉区分：廉价文案行方案，红描边弃用——见交付报告） */
    private static final String ENEMY_MARKER_LINE = "（敌方）";

    private static final Color CARD_TINT = new Color(0.07f, 0.06f, 0.11f, 0.92f);

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final HoverStateMachine boardHover = new HoverStateMachine();
    private final HoverStateMachine shopHover = new HoverStateMachine();
    private List<String> lines = Collections.emptyList();
    private float drawX;
    private float drawY;
    private float drawW;
    private float drawH;
    /** 可见卡矩形缓存（零分配；品质改造 §1.5b 展开通知重叠闸数据源） */
    private final int[] cardRect = new int[4];

    public HoverPreviewCard(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
    }

    /** 是否有可见卡（品质改造 §1.5b：展开通知与其重叠时暂停绘制通知） */
    public boolean hasVisibleCard() {
        return !lines.isEmpty();
    }

    /** 可见卡矩形（缓存数组零分配，{x, y, w, h} 取整像素；无可见卡时内容无意义） */
    public int[] visibleCardRect() {
        cardRect[0] = Math.round(drawX);
        cardRect[1] = Math.round(drawY);
        cardRect[2] = Math.round(drawW);
        cardRect[3] = Math.round(drawH);
        return cardRect;
    }

    /**
     * 每帧推进（BattleScreen.render 显式调用，delta 显式传入——不依赖 act）。
     *
     * @param boardCandidate 棋盘域悬停候选（§6.CP6 getHoverCandidate，抑制已在查询侧施加；
     *                       SHOPPING 玩家/虚影、BATTLE 敌我战斗单位，候选键即状态机驻留键）
     * @param shopSlot       商店悬停槽位（§6.CP7 getHoveredSlot；空槽/非 SHOPPING 在此归一为 -1）
     * @param suppressed     frozen（paused || 弹窗模态）——两源共用抑制位（§5.3-8）
     * @param delta          帧间隔秒（冻结时调用方传 0）
     */
    public void refresh(HoverCandidate boardCandidate, int shopSlot, boolean suppressed, float delta) {
        RunContext ctx = context.get();
        boolean shopping = ctx.getRunState().getPhase() == GamePhase.SHOPPING;
        int shop = shopping && shopSlot >= 0 && ctx.getShop().slotAt(shopSlot) != null ? shopSlot : -1;
        boardHover.update(boardCandidate.key(), suppressed, delta);
        shopHover.update(shop, suppressed, delta);
        recompute(ctx, boardCandidate);
    }

    /** 行集与卡位重算（优先级：棋盘 > 商店——单指针物理互斥，优先级为防御性定义；
     *  可见键必等于本帧候选键——update 在候选变化帧即时清可见） */
    private void recompute(RunContext ctx, HoverCandidate boardCandidate) {
        if (boardHover.visibleId() >= 0) {
            lines = boardCandidate.isEnemy() ? RosterPreviewText.enemy(ctx, boardCandidate.template())
                    : boardCardLines(boardCandidate.template(), false,
                    ctx.getGameData());
            placeAdaptive(BoardGeometry.BOARD_HOVER_X, BoardGeometry.BOARD_HOVER_Y,
                    BoardGeometry.BOARD_HOVER_W, BoardGeometry.BOARD_HOVER_H);
            return;
        }
        UnitData shopTemplate = shopHover.visibleId() >= 0
                ? ctx.getShop().slotAt(shopHover.visibleId()) : null;
        if (shopTemplate != null) {
            lines = RosterPreviewText.shop(ctx, shopHover.visibleId());
            placeAdaptive(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                    BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
            return;
        }
        lines = Collections.emptyList();
    }

    /** 棋盘域卡行集（纯静态，headless 可测）：previewLines 折行 → 敌方加首行标记——全量不截断
     *  （容量由 UnitInfoTextSeedDataTest 锁定 ≤ 锚区高度） */
    static List<String> boardCardLines(UnitData template, boolean enemy, GameData data) {
        List<String> body = UnitInfoText.previewLines(template, data, BOARD_MAX_COLUMNS);
        if (!enemy) {
            return body;
        }
        List<String> marked = new ArrayList<String>(body.size() + 1);
        marked.add(ENEMY_MARKER_LINE);
        marked.addAll(body);
        return marked;
    }

    /** 商店卡行集（纯静态，headless 可测）：previewLines 折行——全量不截断（容量同上锁定） */
    static List<String> shopCardLines(UnitData template, GameData data) {
        return UnitInfoText.previewLines(template, data, SHOP_MAX_COLUMNS);
    }

    public void showDropPreview(int unitId, PlacementTarget target) {
        if (!(target instanceof PlacementTarget.Cell)) return;
        RunContext ctx = context.get();
        Unit unit = ctx.getPlayer().getUnitById(unitId);
        if (unit == null) return;
        lines = RosterPreviewText.drop(ctx, unit, (PlacementTarget.Cell) target);
        placeAdaptive(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
    }

    private void place(int x, int y, int w, int h) {
        this.drawX = x;
        this.drawY = y;
        this.drawW = w;
        this.drawH = h;
    }

    /** 自适应落位（2026-11-21 整改）：卡高 = 行数 × 行高 + 上下留白，封顶锚区高（容量断言保证不触顶） */
    private void placeAdaptive(int x, int y, int w, int zoneH) {
        place(x, y, w, (int) Math.min(zoneH, cardHeightFor(lines.size())));
    }

    /** 卡高纯函数（headless 可测）：顶留白 + 行数×行高 + 底留白 */
    static float cardHeightFor(int lineCount) {
        return PADDING_TOP + lineCount * LINE_HEIGHT + PADDING_BOTTOM;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (lines.isEmpty()) {
            return;
        }
        Color old = batch.getColor();
        batch.setColor(CARD_TINT.r, CARD_TINT.g, CARD_TINT.b, CARD_TINT.a * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), drawX, drawY, drawW, drawH);
        batch.setColor(old);
        float y = drawY + drawH - PADDING_TOP;
        for (int i = 0; i < lines.size() && y > drawY; i++) {
            assets.bodyFont().draw(batch, lines.get(i), drawX + PADDING_X, y);
            y -= LINE_HEIGHT;
        }
    }
}
