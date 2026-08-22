package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 悬停预览卡（Phase 5.1 R1，裁决 A：固定锚、不跟随鼠标、只读精简）。
 * 棋盘域与商店卡各一锚点、各一 {@link HoverStateMachine}（双源互斥——单指针不同时悬停
 * 两处，棋盘源优先）；行集由 {@link UnitInfoText#previewLines} 生成（模板级，不含
 * spend/已穿装备）；超出卡高的行按优先序截断、末行示 …（§5.3-4，完整信息走详情弹窗）。
 * 非模态瞬态：uiStage 最上层普通 Actor，无输入监听、不阻断任何交互。
 */
public final class HoverPreviewCard extends Actor {

    private static final float LINE_HEIGHT = 12f;
    private static final float PADDING_X = 4f;
    private static final float PADDING_TOP = 4f;
    /** 行容量 = floor((H - 顶留 4 - 底 4) / 12)（§5.3-4） */
    private static final int BOARD_LINE_CAPACITY = 15;
    private static final int SHOP_LINE_CAPACITY = 15;
    /** 折行列宽（§5.3-5：(W - 8) / 12 取整） */
    private static final int BOARD_MAX_COLUMNS = 7;
    private static final int SHOP_MAX_COLUMNS = 8;

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

    public HoverPreviewCard(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
    }

    /**
     * 每帧推进（BattleScreen.render 显式调用，delta 显式传入——不依赖 act）。
     *
     * @param boardUnitId 棋盘域悬停候选（§6.CP6 getHoverCandidateUnitId，抑制已在查询侧施加）
     * @param shopSlot    商店悬停槽位（§6.CP7 getHoveredSlot；空槽/非 SHOPPING 在此归一为 -1）
     * @param suppressed  frozen（paused || 弹窗模态）——两源共用抑制位（§5.3-8）
     * @param delta       帧间隔秒（冻结时调用方传 0）
     */
    public void refresh(int boardUnitId, int shopSlot, boolean suppressed, float delta) {
        RunContext ctx = context.get();
        boolean shopping = ctx.getRunState().getPhase() == GamePhase.SHOPPING;
        int board = shopping ? boardUnitId : -1;
        int shop = shopping && shopSlot >= 0 && ctx.getShop().slotAt(shopSlot) != null ? shopSlot : -1;
        boardHover.update(board, suppressed, delta);
        shopHover.update(shop, suppressed, delta);
        recompute(ctx);
    }

    /** 行集与卡位重算（候选可见 id 或模板变化才重建——渲染段零稳态分配） */
    private void recompute(RunContext ctx) {
        UnitData template = resolveTemplate(ctx);
        if (template == null) {
            lines = Collections.emptyList();
            return;
        }
        if (boardHover.visibleId() >= 0) {
            place(BoardGeometry.BOARD_HOVER_X, BoardGeometry.BOARD_HOVER_Y,
                    BoardGeometry.BOARD_HOVER_W, BoardGeometry.BOARD_HOVER_H);
            lines = UnitInfoText.clipLines(
                    UnitInfoText.previewLines(template, ctx.getGameData(), BOARD_MAX_COLUMNS),
                    BOARD_LINE_CAPACITY);
        } else {
            place(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                    BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
            lines = UnitInfoText.clipLines(
                    UnitInfoText.previewLines(template, ctx.getGameData(), SHOP_MAX_COLUMNS),
                    SHOP_LINE_CAPACITY);
        }
    }

    /** 解析当前应显示的模板（棋盘源优先；名单核验防残卡；空槽归一后商店源必有效） */
    private UnitData resolveTemplate(RunContext ctx) {
        if (boardHover.visibleId() >= 0) {
            Unit unit = ctx.getPlayer().getUnitById(boardHover.visibleId());
            return unit == null ? null : unit.getTemplate();
        }
        if (shopHover.visibleId() >= 0) {
            return ctx.getShop().slotAt(shopHover.visibleId());
        }
        return null;
    }

    private void place(int x, int y, int w, int h) {
        this.drawX = x;
        this.drawY = y;
        this.drawW = w;
        this.drawH = h;
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
            assets.font().draw(batch, lines.get(i), drawX + PADDING_X, y);
            y -= LINE_HEIGHT;
        }
    }
}
