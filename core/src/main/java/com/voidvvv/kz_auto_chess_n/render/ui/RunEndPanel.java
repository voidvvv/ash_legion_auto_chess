package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 终局面板（RUN_END，Q5 裁决）：成因文案（通关/放弃）+ 存活轮次 + 熟练度 stub +
 * 本局 seed（复现参考）+ RESTART 按钮——RESTART 换新 seed 与新上下文组装归 Screen
 * 装配点（CP29），本类只回调。
 */
public final class RunEndPanel extends Group {

    /** 重开回调（Screen 实现：组装新鲜 RunContext 后 runFlowSystem.restart） */
    public interface RestartListener {
        void onRestart();
    }

    private final Assets assets;
    private final RestartListener restartListener;
    private final java.util.function.Supplier<RunContext> context;

    public RunEndPanel(Assets assets, RestartListener restartListener,
                       java.util.function.Supplier<RunContext> context) {
        this.assets = assets;
        this.restartListener = restartListener;
        this.context = context;
        Actor button = new RestartButton();
        button.setPosition((BoardGeometry.VIRTUAL_W - 140f) / 2f, 110f);
        addActor(button);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 80f, 460f, 200f);
        batch.setColor(Color.WHITE);
        RunContext ctx = context.get();
        boolean abandoned = ctx.getRunState().getEndCause() == com.voidvvv.kz_auto_chess_n.entities.RunEndCause.ABANDONED;
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, abandoned ? "远征已放弃" : "远征通关", 272f, 230f); // 4 字 ×24px 居中
        assets.font().getData().setScale(1f);
        int round = ctx.getRunState().getRound();
        assets.font().draw(batch, "抵达第 " + round + "/" + GameBalance.TOTAL_ROUNDS + " 轮", 280f, 200f);
        assets.font().draw(batch, "熟练度 +" + ctx.getRunState().getMasteryAwarded() + "（Phase 6 接档案）", 262f, 180f);
        assets.font().draw(batch, "种子 " + ctx.getRunState().getSeed(), 285f, 160f);
    }

    private final class RestartButton extends Actor {
        RestartButton() {
            setSize(140f, 36f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    restartListener.onRestart();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "重新开始", getX() + 46f, getY() + 23f);
        }
    }
}
