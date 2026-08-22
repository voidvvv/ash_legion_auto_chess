package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 弹道绘制（render §5.3）：HOMING 追踪目标当前格中心渲染 + §4.1 alpha 外推
 * （渲染侧缓存上次 poll 的 pos 作 prev，lerp(prev, pos, alpha)——差异声明 #3）。
 * 不旋转（HOMING 无旋转，render §八.3）；技能弹用 fx_{skillId}、普攻弹白点。
 */
public final class ProjectileView {
    private static final float SIZE = 10f;

    /** 弹道 → 上次渲染位置缓存（[x, y]，格单位连续坐标；identity 键控零查找） */
    private final Map<Projectile, float[]> prevPositions = new IdentityHashMap<Projectile, float[]>();

    /** 绘制全部在途弹（外推后取整吸附）；同帧先 draw 后 poll 更新缓存 */
    public void draw(SpriteBatch batch, Assets assets, Iterable<Projectile> projectiles, float alpha) {
        for (Projectile p : projectiles) {
            float[] prev = prevPositions.get(p);
            if (prev == null) {
                prev = new float[]{p.getPosX(), p.getPosY()}; // 出生帧无历史：直落
                prevPositions.put(p, prev);
            }
            float x = lerp(prev[0], p.getPosX(), alpha);
            float y = lerp(prev[1], p.getPosY(), alpha);
            // 弹道 pos 为连续格坐标（格中心 = 整数 + 0.5），走 continuousCenter 换算（P0 修正：
            // 原按整数口径多加 CELL/2，导致每弹右偏/上偏半格对角）
            int px = Math.round(BoardGeometry.continuousCenterX(x) - SIZE / 2f);
            int py = Math.round(BoardGeometry.continuousCenterY(y) - SIZE / 2f);
            if (p.getSkill() != null) {
                batch.draw(assets.region(PlaceholderKeys.skillFx(p.getSkill().getId())), px, py, SIZE, SIZE);
            } else {
                batch.setColor(1f, 0.95f, 0.7f, 1f);
                batch.draw(assets.region(PlaceholderKeys.WHITE), px, py, SIZE, SIZE);
                batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            }
            prev[0] = p.getPosX();
            prev[1] = p.getPosY();
        }
        evictVanished(projectiles);
    }

    /** 战斗作用域结束：缓存整体弃（BattleState 整体丢弃） */
    public void clear() {
        prevPositions.clear();
    }

    /** 移除已消散弹的缓存（迭代器原地清除，零分配） */
    private void evictVanished(Iterable<Projectile> projectiles) {
        Iterator<Map.Entry<Projectile, float[]>> it = prevPositions.entrySet().iterator();
        outer:
        while (it.hasNext()) {
            Projectile key = it.next().getKey();
            for (Projectile p : projectiles) {
                if (p == key) {
                    continue outer;
                }
            }
            it.remove();
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
