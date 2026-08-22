package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * 特效层（render §5.3/§5.4）：事件驱动一次性闪光（起手 fx_{skillId} / 落点 burst 与兜底，
 * 区域锚点 ≤0.5s）+ 单位持续状态色点（轮询 statuses，第四行）。全部经 ObjectPool 池化。
 * 遍历 units + isCleaned 过滤（口径 #13：避免每帧 aliveUnits 新建列表）。
 */
public final class FxLayer {
    /** 一次性闪光寿命（秒，区域锚点 ≤0.5s） */
    public static final float SPARK_LIFETIME = 0.5f;
    private static final int MAX_STATUS_DOTS = 4;

    /** 池化一次性闪光（可变实例） */
    private static final class Spark {
        TextureRegion region;
        float x;
        float y;
        float elapsed;
        boolean active;
    }

    private final Assets assets;
    private final ObjectPool<Spark> pool = new ObjectPool<Spark>(new ObjectPool.Factory<Spark>() {
        @Override
        public Spark create() {
            return new Spark();
        }
    });
    private final List<Spark> active = new ArrayList<Spark>();

    public FxLayer(Assets assets) {
        this.assets = assets;
    }

    /** 起手闪光：单位锚点 fx_{skillId}（无技能图则 cast 兜底） */
    public void sparkCast(String skillId, float x, float y) {
        TextureRegion region = assets.region(skillId != null
                ? PlaceholderKeys.skillFx(skillId) : PlaceholderKeys.CAST_DEFAULT);
        spark(region, x, y);
    }

    /** 落点闪光：区域锚点 fx_{skillId}_burst（普攻/治疗无技能图用 hit 兜底） */
    public void sparkBurst(String skillId, float x, float y) {
        TextureRegion region = assets.region(skillId != null
                ? PlaceholderKeys.skillFxBurst(skillId) : PlaceholderKeys.HIT_DEFAULT);
        spark(region, x, y);
    }

    private void spark(TextureRegion region, float x, float y) {
        Spark s = pool.obtain();
        s.region = region;
        s.x = x;
        s.y = y;
        s.elapsed = 0f;
        s.active = true;
        active.add(s);
    }

    /** 推进并绘制：一次性闪光淡出 + 每单位状态色点 */
    public void updateAndDraw(SpriteBatch batch, float dt, BattleState state) {
        for (int i = active.size() - 1; i >= 0; i--) { // 倒序换出回收（零分配）
            Spark s = active.get(i);
            s.elapsed += dt;
            if (s.elapsed >= SPARK_LIFETIME) {
                s.active = false;
                active.remove(i);
                pool.free(s);
            }
        }
        for (int i = 0; i < active.size(); i++) {
            Spark s = active.get(i);
            float t = s.elapsed / SPARK_LIFETIME;
            float scale = 0.8f + 0.5f * t; // 轻微扩张（无旋转）
            batch.setColor(1f, 1f, 1f, 1f - t);
            batch.draw(s.region, s.x - s.region.getRegionWidth() * scale / 2f,
                    s.y - s.region.getRegionHeight() * scale / 2f,
                    s.region.getRegionWidth() * scale, s.region.getRegionHeight() * scale);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        drawStatusDots(batch, state);
    }

    /** 单位持续状态色点：头顶横排 fx_status_{type}（轮询，≤4 个） */
    private void drawStatusDots(SpriteBatch batch, BattleState state) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
            List<ActiveStatus> statuses = unit.getStatuses();
            if (statuses.isEmpty()) {
                continue;
            }
            int cx = BoardGeometry.cellCenterX(unit.getGridX());
            int cy = BoardGeometry.cellCenterY(unit.getGridY());
            int count = Math.min(MAX_STATUS_DOTS, statuses.size());
            for (int i = 0; i < count; i++) {
                TextureRegion dot = assets.region(PlaceholderKeys.statusFx(statuses.get(i).getType()));
                batch.draw(dot, cx - count * 5f + i * 10f, cy + 22f);
            }
        }
    }

    /** 战斗作用域结束：回收全部 */
    public void clear() {
        for (Spark s : active) {
            s.active = false;
            pool.free(s);
        }
        active.clear();
    }
}
