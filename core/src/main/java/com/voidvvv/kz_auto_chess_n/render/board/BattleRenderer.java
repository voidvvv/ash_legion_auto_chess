package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.EventInbox;
import com.voidvvv.kz_auto_chess_n.render.FloatTextFormat;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

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
    private static final com.badlogic.gdx.graphics.Color ENEMY_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.26f, 0.14f, 0.14f, 1f); // 敌区暗红
    private static final com.badlogic.gdx.graphics.Color BUFFER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.12f, 0.11f, 0.10f, 1f); // 缓冲带
    private static final com.badlogic.gdx.graphics.Color PLAYER_ZONE_TINT =
            new com.badlogic.gdx.graphics.Color(0.13f, 0.20f, 0.14f, 1f); // 玩家区暗绿
    private static final com.badlogic.gdx.graphics.Color GRID_LINE_TINT =
            new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.35f);

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
     */
    public void draw(SpriteBatch batch, RunContext ctx, float alpha, float renderClock, float dt) {
        syncBattleScope(ctx);
        batch.begin();
        drawGrid(batch);
        floatSlot = 0;
        if (ctx.getRunState().getPhase() == GamePhase.SHOPPING || ctx.getBattleState() == null) {
            drawShopping(batch, ctx);
        } else {
            drawBattle(batch, ctx, alpha, renderClock, dt);
        }
        batch.end();
    }

    /** 战斗作用域同步：battleState 引用变化 → 视图集合整体重建/清空 */
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
            for (BattleUnit unit : current.getUnits()) { // 含亡者：死亡淡出仍需视图
                UnitView view = new UnitView(unit, assets);
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

    // —— 备战期：② 备战席 + 玩家部署 + 敌阵预览（侦察，口径 #25） ——

    private void drawShopping(SpriteBatch batch, RunContext ctx) {
        TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
        for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
            int[] center = BoardGeometry.benchSlotCenter(slot);
            batch.setColor(0.5f, 0.48f, 0.45f, 0.8f);
            batch.draw(panel, center[0] - BoardGeometry.BENCH_SLOT_W / 2f,
                    center[1] - BoardGeometry.BENCH_SLOT_H / 2f,
                    BoardGeometry.BENCH_SLOT_W, BoardGeometry.BENCH_SLOT_H);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        Player player = ctx.getPlayer();
        List<Unit> bench = player.getBench();
        for (int slot = 0; slot < bench.size(); slot++) {
            int[] center = BoardGeometry.benchSlotCenter(slot);
            drawUnitFrame(batch, bench.get(slot).getTemplate().getId(),
                    PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false, 1f);
        }
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit != null) {
                    int[] center = BoardGeometry.cellCenter(x, y);
                    drawUnitFrame(batch, unit.getTemplate().getId(),
                            PlaceholderKeys.ANIM_IDLE, 0, center[0], center[1], false, 1f);
                }
            }
        }
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) {
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, spec.isBoss() ? 1f : 0.85f);
        }
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

    /** 占位单位帧绘制（中心定位；enemyFace = 水平翻转 + 轻微暗色区分敌我） */
    private void drawUnitFrame(SpriteBatch batch, String unitId, String anim, int frame,
                               int cx, int cy, boolean enemyFace, float brightness) {
        TextureRegion region = assets.region(PlaceholderKeys.unitFrame(unitId, anim, frame));
        boolean wasFlip = region.isFlipX();
        if (enemyFace != wasFlip) {
            region.flip(true, false); // 用后即还（region 共享，铁律：不残留状态）
        }
        if (brightness < 1f) {
            batch.setColor(brightness, brightness, brightness, 1f);
        }
        batch.draw(region, cx - BoardGeometry.CELL / 2f, cy - BoardGeometry.CELL / 2f,
                BoardGeometry.CELL, BoardGeometry.CELL);
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        if (region.isFlipX() != wasFlip) {
            region.flip(true, false); // 还原
        }
    }
}
