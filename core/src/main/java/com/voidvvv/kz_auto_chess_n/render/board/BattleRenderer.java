package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.List;

/**
 * 棋盘域总绘制（render §3.2 层序 ②~⑧；单 batch，begin/end 配对由本类保证）。
 *
 * <p>生命周期铁律 3：{@code UnitView} 等战斗视图 = BattleState 作用域——
 * draw 内观察 battleState 引用变化自动 rebuild/clear（本提交为静态层骨架，
 * 视图集合随提交 8 接入）。备战期走 drawShopping（名单 + 敌阵预览 = 侦察，口径 #25）。
 * 只读 entities：对 Player/BattleUnit 仅调读方法（铁律 1）。
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

    public BattleRenderer(Assets assets) {
        this.assets = assets;
    }

    /**
     * 总入口：phase==SHOPPING → drawShopping；BATTLE/RESULT → drawBattle。
     *
     * @param alpha      逻辑步间插值系数（弹道外推用，提交 8 接入）
     * @param renderClock 渲染帧时钟（秒，插值与动画计时）
     */
    public void draw(SpriteBatch batch, RunContext ctx, float alpha, float renderClock) {
        syncBattleScope(ctx);
        batch.begin();
        drawGrid(batch);
        if (ctx.getRunState().getPhase() == GamePhase.SHOPPING) {
            drawShopping(batch, ctx);
        }
        batch.end();
    }

    /** 战斗作用域同步：battleState 引用变化 → 视图集合重建/清空（提交 8 接入视图） */
    private void syncBattleScope(RunContext ctx) {
        BattleState current = ctx.getBattleState();
        if (current != trackedBattle) {
            trackedBattle = current; // rebuild/clear 钩子（提交 8：UnitView/EventInbox）
        }
    }

    // —— ④ 棋盘格底 ——敌区/缓冲带/玩家区分色 + 格线 ——

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

    // —— 备战期：② 备战席 + 玩家部署 + 敌阵预览（侦察） ——

    private void drawShopping(SpriteBatch batch, RunContext ctx) {
        TextureRegion panel = assets.region(PlaceholderKeys.PANEL_9SLICE);
        for (int slot = 0; slot < GameBalance.BENCH_SIZE; slot++) {
            int[] center = BoardGeometry.benchSlotCenter(slot);
            batch.setColor(0.5f, 0.48f, 0.45f, 0.8f);
            batch.draw(panel, center[0] - BoardGeometry.BENCH_SLOT_W / 2f,
                    center[1] - BoardGeometry.BENCH_SLOT_H / 2f,
                    BoardGeometry.BENCH_SLOT_W, BoardGeometry.BENCH_SLOT_H);
        }
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
        for (WaveSpec spec : ctx.getRunState().getEnemyWave()) { // 敌阵预览 = 侦察（render §九）
            int[] center = BoardGeometry.cellCenter(spec.getGridX(), spec.getGridY());
            drawUnitFrame(batch, spec.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                    center[0], center[1], true, spec.isBoss() ? 1f : 0.85f);
        }
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
