package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;

/**
 * 收起态角标「后备 n/9」（Q2；n = 备战席条目数）；随展开淡出。
 * 独占一份标题板九宫格——NinePatch 颜色是实例态，不能污染与标题板共享的那份。
 * 文案按条目数指纹缓存（渲染段零分配）。批前重构自 DrawerPanel 抽出，行为不变。
 */
final class DrawerBadge extends Actor {

    private static final float BADGE_TEXT_X = 3f;

    private final Assets assets;
    private final NinePatch platePatch;
    private final DrawerGeometry.Rect rect;
    private final Color tint = new Color(1f, 1f, 1f, 1f);
    private String text = DrawerInfoText.badgeText(0);
    private int cachedBenchCount = -1;

    DrawerBadge(Assets assets) {
        this.assets = assets;
        UiKeys.Entry plate = UiKeys.TITLE_PLATE;
        this.platePatch = new NinePatch(assets.uiRegion(plate.id()),
                plate.left(), plate.right(), plate.top(), plate.bottom());
        this.rect = DrawerGeometry.badgeRect();
        setPosition(rect.x(), rect.y());
        setSize(rect.width(), rect.height());
        setTouchable(Touchable.disabled);
    }

    void setBenchCount(int count) {
        if (cachedBenchCount != count) {
            cachedBenchCount = count;
            text = DrawerInfoText.badgeText(count);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = DrawerSkin.alphaOf(this, parentAlpha);
        // 角标淡出/让位 alpha 写在独占九宫格的自身色上（NinePatch 吃自身颜色）
        tint.set(1f, 1f, 1f, alpha);
        platePatch.setColor(tint);
        DrawerSkin.beginUntinted(batch, alpha);
        platePatch.draw(batch, getX(), getY(), getWidth(), getHeight());
        DrawerSkin.endUntinted(batch);
        DrawerSkin.drawText(batch, assets.uiFont(), text, getX() + BADGE_TEXT_X,
                DrawerSkin.centeredTextTop(getY(), getHeight(), DrawerSkin.TEXT_HEIGHT), alpha);
    }
}
