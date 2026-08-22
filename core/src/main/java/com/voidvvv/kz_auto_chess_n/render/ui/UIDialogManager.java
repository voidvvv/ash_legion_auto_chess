package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.Objects;

/**
 * 弹窗宿主（input §2.2 第 1 层；render §九 RESULT 弹窗层 = 输入最高优先级）。
 * dialogStage + 弹窗栈 + 全屏收点背板；isShowing() 同时供 boardProcessor.modalBlocked
 * 与 BattleScreen 模拟冻结（实现口径 #14）。栈语义委托 {@link DialogStack}（headless 已测）；
 * act/draw/resize/dispose 由 Screen 委托调用。
 */
public final class UIDialogManager {

    private final Assets assets;
    private final Stage dialogStage;
    private final DialogStack<Actor> stack = new DialogStack<Actor>();
    private final Actor backdrop;

    public UIDialogManager(Assets assets) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.dialogStage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        this.backdrop = createBackdrop();
        dialogStage.addActor(backdrop);
    }

    public Stage getStage() {
        return dialogStage;
    }

    /** 压栈展示（同一弹窗重复 push 幂等跳过） */
    public void push(Actor dialog) {
        if (stack.push(dialog)) {
            dialogStage.addActor(dialog);
        }
        syncBackdrop();
    }

    /** 关顶层 */
    public void closeTop() {
        Actor top = stack.closeTop();
        if (top != null) {
            top.remove();
        }
        syncBackdrop();
    }

    /** 清空（重开新局） */
    public void clearAll() {
        while (stack.isShowing()) {
            closeTop();
        }
    }

    public boolean isShowing() {
        return stack.isShowing();
    }

    public void act(float delta) {
        dialogStage.act(delta);
    }

    public void draw() {
        if (isShowing()) {
            dialogStage.getViewport().apply();
            dialogStage.draw();
        }
    }

    public void resize(int width, int height) {
        dialogStage.getViewport().update(width, height, true);
    }

    public void dispose() {
        dialogStage.dispose();
    }

    /** 全屏半透明收点背板（模态穿透防御，input §3——只吞点击不动作） */
    private Actor createBackdrop() {
        Actor backdrop = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                batch.setColor(0f, 0f, 0f, 0.45f * parentAlpha);
                batch.draw(assets.region(PlaceholderKeys.WHITE), 0f, 0f, getWidth(), getHeight());
                batch.setColor(Color.WHITE);
            }
        };
        backdrop.setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
        backdrop.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // 背板只吞点击不动作
            }
        });
        return backdrop;
    }

    /** 背板可见性随栈空切换；z 序 = 背板垫底、弹窗按栈序叠放 */
    private void syncBackdrop() {
        backdrop.setVisible(isShowing());
        backdrop.toFront();
        for (Actor dialog : stack.bottomToTop()) {
            dialog.toFront();
        }
    }
}
