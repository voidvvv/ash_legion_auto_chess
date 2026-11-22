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

    private static final com.badlogic.gdx.graphics.Color ENEMY_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.26f, 0.14f, 0.14f, 1f); // 敌区暗红
    private static final com.badlogic.gdx.graphics.Color BUFFER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.12f, 0.11f, 0.10f, 1f); // 缓冲带
    private static final com.badlogic.gdx.graphics.Color PLAYER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.13f, 0.20f, 0.14f, 1f); // 玩家区暗绿
    private static final com.badlogic.gdx.graphics.Color GRID_LINE_TINT =
            new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.35f);
    private static final com.badlogic.gdx.graphics.Color DROP_OK_TINT =
            new com.badlogic.gdx.graphics.Color(0.3f, 1f, 0.4f, 0.35f);  // 合法落点绿
    private static final com.badlogic.gdx.graphics.Color DROP_BAD_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.3f, 0.3f, 0.3f);   // 非法落点红
    private static final com.badlogic.gdx.graphics.Color SELL_DROP_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.8f, 0.25f, 0.45f);  // 出售区悬停金

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
            drawShopping(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge);
            drawDropOverlay(batch, ctx, input);
        } else {
            if (chromeFadeAlpha > 0f) { // 开战转场窗口：备战层 chrome 随姿态退场（render §5.6；K5 不画部署帧）
                drawShoppingChrome(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge);
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
                              float worldScale, float drawerStageRightEdge) {
        drawShoppingChrome(batch, ctx, chromeFadeAlpha, worldScale, drawerStageRightEdge);
        Player player = ctx.getPlayer();
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit != null) {
                    int[] center = BoardGeometry.cellCenter(x, y);
                    drawUnitFrame(batch, unit.getTemplate().getId(),
                            PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false,
                            chromeFadeAlpha, unit.getStar(), SideColors.PLAYER);
                }
            }
        }
    }

    /**
     * 备战层 chrome：敌阵虚影（淡出=实体化一拍）+ ⑦ 出售区 + 布阵提示（淡出）。
     * ② 备战席槽 + 席上棋子的世界域绘制已退役（UI 抽屉化批2：由 DrawerPanel 头像条接管，备战席不再左滑）。
     */
    private void drawShoppingChrome(SpriteBatch batch, RunContext ctx, float chromeFadeAlpha,
                                      float worldScale, float drawerStageRightEdge) {
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵侦察虚影（红框 + 半透明，P1b；转场随层淡出；恒 1★ 骨白）
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, SideColors.ENEMY_PREVIEW_ALPHA * chromeFadeAlpha,
                    1, SideColors.ENEMY);
        }
        drawSellZone(batch, chromeFadeAlpha);
        drawShoppingHint(batch, chromeFadeAlpha, worldScale, drawerStageRightEdge);
    }

    /** ⑦ 出售区（render §九；棋盘域自绘，仅 SHOPPING 路径可达；fade = 转场淡出系数） */
    private void drawSellZone(SpriteBatch batch, float fade) {
        TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
        batch.setColor(0.45f, 0.32f, 0.16f, 0.9f * fade);
        batch.draw(panel, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        assets.font().setColor(1f, 1f, 1f, fade); // 用后即还（共用字体纪律）
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
                    fxLayer.sparkCast(event.getSkillId(), caster.virtualX(renderClock), caster.virtualY(renderClock));
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

    /** 占位单位帧绘制（中心定位；enemyFace = 水平翻转；star 星级色 tint；border 敌我色框，null 不画；alpha 整体透明度）。
     *  帧本体委托 {@link UnitFramePainter}（UI 抽屉化批2：与抽屉头像格 / 立绘共用同一套取帧-翻转-tint-还原） */
    private void drawUnitFrame(SpriteBatch batch, String unitId, String anim, int frame,
                               int cx, int cy, boolean enemyFace, float alpha, int star,
                               com.badlogic.gdx.graphics.Color border) {
        UnitFramePainter.draw(batch, assets, unitId, anim, frame,
                cx - BoardGeometry.CELL / 2f, cy - BoardGeometry.CELL / 2f, BoardGeometry.CELL,
                enemyFace, alpha, StarColors.of(star));
        if (border != null) {
            SideColors.drawBorder(batch, assets.region(PlaceholderKeys.WHITE), cx, cy, border);
        }
    }

    // —— 拖拽 ghost 与落点高亮（input §4.3 表现层预校验；handler 层门控独立） ——

    private void drawDropOverlay(SpriteBatch batch, RunContext ctx, BoardInputProcessor input) {
        if (input == null || !input.isDragging()) {
            return;
        }
        TextureRegion white = assets.region(PlaceholderKeys.WHITE);
        if (input.isDropOnSellZone()) { // ⑦ 出售区悬停：金红高亮（可卖出提示）
            batch.setColor(SELL_DROP_TINT);
            batch.draw(white, BoardGeometry.SELL_ZONE_X, BoardGeometry.SELL_ZONE_Y,
                    BoardGeometry.SELL_ZONE_W, BoardGeometry.SELL_ZONE_H);
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            return;
        }
        com.voidvvv.kz_auto_chess_n.command.PlacementTarget preview = input.getDropPreview();
        if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) {
            com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell cell =
                    (com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Cell) preview;
            batch.setColor(DROP_OK_TINT);
            batch.draw(white, BoardGeometry.BOARD_X + cell.gridX * BoardGeometry.CELL,
                    BoardGeometry.BOARD_Y + BoardGeometry.BOARD_H - (cell.gridY + 1) * BoardGeometry.CELL,
                    BoardGeometry.CELL, BoardGeometry.CELL);
        } else if (preview instanceof com.voidvvv.kz_auto_chess_n.command.PlacementTarget.Bench) {
            // 备战席落点高亮已随 ② 迁入抽屉：由 DrawerPanel 在头像格上叠选中框（世界域无备战席可画）
        } else {
            int[] cell = BoardGeometry.pixelToCell((int) input.getDragVirtualX(), (int) input.getDragVirtualY());
            if (cell != null) { // 悬停棋盘但非法行（敌区/缓冲带）：红高亮
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
