package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
 * 悬停预览卡（Phase 5.1 R1，裁决 A：固定锚、不跟随鼠标、只读精简；feedback04 敌方悬停；
 * feedback07 背包格装备卡）。棋盘域/商店卡/背包格各一锚点、各一 {@link HoverStateMachine}
 * （三源互斥——单指针不同时悬停多处，棋盘源优先）；棋子卡行集由
 * {@link UnitInfoText#previewLines} 生成（模板级，不含 spend/已穿装备——R1 口径限于棋子卡，
 * 背包格卡展示装备本体，行集走 EquipmentInfoText，差异声明 #D1）；超出卡高的行按优先序
 * 截断、末行示 …（§5.3-4，完整信息走详情弹窗）。
 * 棋盘候选经 {@link HoverCandidate} 携带模板（feedback04：敌方虚影/敌侧战斗单位无玩家
 * 实例，不回查名单——名单核验移至查询侧重命中）；敌方候选首行加"（敌方）"标记。
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
    /** feedback07 背包格装备卡：折行列宽 (90-8)/12 = 6；行容量 (100-8)/12 = 7 */
    private static final int INVENTORY_MAX_COLUMNS = 6;
    private static final int INVENTORY_LINE_CAPACITY = 7;
    /** 敌方候选首行标记（feedback04 视觉区分：廉价文案行方案，红描边弃用——见交付报告） */
    private static final String ENEMY_MARKER_LINE = "（敌方）";

    private static final Color CARD_TINT = new Color(0.07f, 0.06f, 0.11f, 0.92f);

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final HoverStateMachine boardHover = new HoverStateMachine();
    private final HoverStateMachine shopHover = new HoverStateMachine();
    /** feedback07 背包格第三源（驻留键 = 槽位索引；与双源同一状态机实现） */
    private final HoverStateMachine inventoryHover = new HoverStateMachine();
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
     * @param boardCandidate 棋盘域悬停候选（§6.CP6 getHoverCandidate，抑制已在查询侧施加；
     *                       SHOPPING 玩家/虚影、BATTLE 敌我战斗单位，候选键即状态机驻留键）
     * @param shopSlot       商店悬停槽位（§6.CP7 getHoveredSlot；空槽/非 SHOPPING 在此归一为 -1）
     * @param inventorySlot  背包悬停槽位（feedback07 CP-B3 getHoveredSlot；拖拽抑制在调用方传 -1，
     *                       BATTLE 置灰/空槽/越界在此归一为 -1——口径 B4-1/B4-2）
     * @param suppressed     frozen（paused || 弹窗模态）——三源共用抑制位（§5.3-8）
     * @param delta          帧间隔秒（冻结时调用方传 0）
     */
    public void refresh(HoverCandidate boardCandidate, int shopSlot, int inventorySlot,
                        boolean suppressed, float delta) {
        RunContext ctx = context.get();
        boolean shopping = ctx.getRunState().getPhase() == GamePhase.SHOPPING;
        int shop = shopping && shopSlot >= 0 && ctx.getShop().slotAt(shopSlot) != null ? shopSlot : -1;
        int inventory = normalizeInventorySlot(ctx.getRunState().getPhase(), inventorySlot,
                ctx.getPlayer().getInventory().size()); // BATTLE 置灰/空槽/越界 → -1（口径 B4-1）
        boardHover.update(boardCandidate.key(), suppressed, delta);
        shopHover.update(shop, suppressed, delta);
        inventoryHover.update(inventory, suppressed, delta);
        recompute(ctx, boardCandidate);
    }

    /** 行集与卡位重算（优先级：棋盘 > 商店 > 背包——单指针物理互斥，优先级为防御性定义；
     *  可见键必等于本帧候选键——update 在候选变化帧即时清可见） */
    private void recompute(RunContext ctx, HoverCandidate boardCandidate) {
        if (boardHover.visibleId() >= 0) {
            place(BoardGeometry.BOARD_HOVER_X, BoardGeometry.BOARD_HOVER_Y,
                    BoardGeometry.BOARD_HOVER_W, BoardGeometry.BOARD_HOVER_H);
            lines = boardCardLines(boardCandidate.template(), boardCandidate.isEnemy(),
                    ctx.getGameData());
            return;
        }
        UnitData shopTemplate = shopHover.visibleId() >= 0
                ? ctx.getShop().slotAt(shopHover.visibleId()) : null;
        if (shopTemplate != null) {
            place(BoardGeometry.SHOP_HOVER_X, BoardGeometry.SHOP_HOVER_Y,
                    BoardGeometry.SHOP_HOVER_W, BoardGeometry.SHOP_HOVER_H);
            lines = UnitInfoText.clipLines(
                    UnitInfoText.previewLines(shopTemplate, ctx.getGameData(), SHOP_MAX_COLUMNS),
                    SHOP_LINE_CAPACITY);
            return;
        }
        if (inventoryHover.visibleId() >= 0) { // feedback07 背包格装备卡（第三源）
            place(BoardGeometry.INVENTORY_HOVER_X, BoardGeometry.INVENTORY_HOVER_Y,
                    BoardGeometry.INVENTORY_HOVER_W, BoardGeometry.INVENTORY_HOVER_H);
            lines = inventoryCardLines(ctx.getPlayer().getInventory()
                    .get(inventoryHover.visibleId()).getTemplate());
            return;
        }
        lines = Collections.emptyList();
    }

    /** 棋盘域卡行集（纯静态，headless 可测）：previewLines 折行 → 敌方加首行标记 → 容量截断 */
    static List<String> boardCardLines(UnitData template, boolean enemy, GameData data) {
        List<String> body = UnitInfoText.previewLines(template, data, BOARD_MAX_COLUMNS);
        if (!enemy) {
            return UnitInfoText.clipLines(body, BOARD_LINE_CAPACITY);
        }
        List<String> marked = new ArrayList<String>(body.size() + 1);
        marked.add(ENEMY_MARKER_LINE);
        marked.addAll(body);
        return UnitInfoText.clipLines(marked, BOARD_LINE_CAPACITY);
    }

    /** 背包格装备卡行集（feedback07；纯静态，headless 可测）：lines 逐行折行 → 容量截断（§5.3-4 同口径） */
    static List<String> inventoryCardLines(EquipmentData template) {
        List<String> wrapped = new ArrayList<String>();
        for (String line : EquipmentInfoText.lines(template)) {
            wrapped.addAll(UnitInfoText.wrap(line, INVENTORY_MAX_COLUMNS));
        }
        return UnitInfoText.clipLines(wrapped, INVENTORY_LINE_CAPACITY);
    }

    /** 背包悬停归一（feedback07 口径 B4-1；纯函数三参）：BATTLE 置灰期（差异声明 #8——非交互只读
     *  快照）/ 空槽（slot ≥ 背包数）/ 负值 → -1；抑制施加于查询侧（§5.3-8 同口径） */
    static int normalizeInventorySlot(GamePhase phase, int slot, int inventorySize) {
        if (phase == GamePhase.BATTLE || slot < 0 || slot >= inventorySize) {
            return -1;
        }
        return slot;
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
