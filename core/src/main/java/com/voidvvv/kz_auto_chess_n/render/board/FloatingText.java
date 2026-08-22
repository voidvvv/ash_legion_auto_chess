package com.voidvvv.kz_auto_chess_n.render.board;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.voidvvv.kz_auto_chess_n.render.Assets;

/**
 * 飘字实例（render §5.2；池化可变对象——spawn/update/draw 生命周期由 BattleRenderer 管理）。
 * 上浮淡出 0.8s；文本预格式化入池（渲染段零字符串分配）。
 */
public final class FloatingText {
    /** 总寿命（秒） */
    public static final float LIFETIME = 0.8f;
    /** 上浮总像素 */
    public static final float RISE_PIXELS = 22f;

    private final Color tint = new Color();
    /** 恢复用颜色值拷贝——BitmapFont.getColor() 返回内部 live 引用，直接持有则恢复成自身 = no-op（feedback05 修复） */
    private final Color savedColor = new Color();
    private String text = "";
    private float scale = 1f;
    private float x;
    private float y;
    private float elapsed;
    private boolean active;

    /** 激活（池取出后调用；color 值拷贝进内部实例） */
    public void spawn(String text, Color color, float scale, float x, float y) {
        this.text = text;
        this.tint.set(color);
        this.scale = scale;
        this.x = x;
        this.y = y;
        this.elapsed = 0f;
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    /** 推进；到寿返回 false（回收信号） */
    public boolean update(float dt) {
        if (!active) {
            return false;
        }
        elapsed += dt;
        if (elapsed >= LIFETIME) {
            active = false;
            return false;
        }
        return true;
    }

    public void draw(SpriteBatch batch, Assets assets) {
        if (!active) {
            return;
        }
        float t = elapsed / LIFETIME;
        float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f; // 后 30% 淡出
        com.badlogic.gdx.graphics.g2d.BitmapFont font = assets.font();
        float oldScale = font.getScaleX();
        savedColor.set(font.getColor());
        font.getData().setScale(oldScale * scale);
        font.setColor(tint.r, tint.g, tint.b, alpha);
        font.draw(batch, text, x, y + RISE_PIXELS * t);
        font.getData().setScale(oldScale);
        font.setColor(savedColor);
    }
}
