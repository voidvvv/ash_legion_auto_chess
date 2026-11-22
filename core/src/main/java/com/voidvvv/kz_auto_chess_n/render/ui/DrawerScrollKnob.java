package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;

/**
 * 抽屉滚动条滑块（UI 抽屉化批2 头像条横向；批前重构自 DrawerPanel 抽出并加纵/横轴参数，横向行为不变）。
 * 可拖：格子本身穿透给 boardProcessor，条内只支持滚轮 + 拖滑块。拖动时把「指针在轨内沿轴的偏移」
 * （已扣掉半个滑块，即滑块左缘/下缘相对轨起点的像素）回传给持有者，持有者据此换算首槽下标。
 * 坐标换算用 {@link #stageToLocalCoordinates}（只走父链，零 Stage 依赖）——持有者无须知道主体滑到哪。
 */
final class DrawerScrollKnob extends Actor {

    /** 拖动回调：@param knobEdge 滑块左缘（纵向：下缘）在<b>父组局部坐标</b>里的沿轴位置——持有者自行减去轨起点 */
    interface DragListener {
        void onDrag(float knobEdge);
    }

    static final boolean HORIZONTAL = false;
    static final boolean VERTICAL = true;

    private final TextureRegion region;
    private final boolean vertical;
    private final DragListener dragListener;
    /** 复用（指针换算，零分配） */
    private final Vector2 pointer = new Vector2();

    DrawerScrollKnob(TextureRegion region, boolean vertical, int knobSize, DragListener dragListener) {
        this.region = region;
        this.vertical = vertical;
        this.dragListener = dragListener;
        setSize(knobSize, knobSize);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true; // 捕获：后续拖动事件送到本滑块
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                onDrag(event);
            }
        });
    }

    private void onDrag(InputEvent event) {
        pointer.set(event.getStageX(), event.getStageY());
        stageToLocalCoordinates(pointer); // 指针相对滑块左下角
        float along = vertical ? pointer.y : pointer.x;
        dragListener.onDrag(getX() + along - getWidth() / 2f);
    }

    /**
     * 沿轴落位。
     *
     * @param axisPos    滑块左缘（纵向：下缘）在父组局部坐标里的沿轴位置
     * @param crossCenter 轨在垂直于滚动轴方向上的中心（父组局部坐标）——滑块在该轴居中
     */
    void place(float axisPos, float crossCenter) {
        if (vertical) {
            setPosition(crossCenter - getWidth() / 2f, axisPos);
        } else {
            setPosition(axisPos, crossCenter - getHeight() / 2f);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        DrawerSkin.drawRegion(batch, region, getX(), getY(), getWidth(), getHeight(),
                DrawerSkin.alphaOf(this, parentAlpha));
    }
}
