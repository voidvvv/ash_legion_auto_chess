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
 * 终局面板（RUN_END，Q3）：终局文字 + RESTART 按钮——同 DEMO_SEED 重开（口径 #22，
 * 确定性对照；新上下文组装归 Screen 装配点，本类只回调）。
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
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, "RUN END", 250f, 230f);
        assets.font().getData().setScale(1f);
        int round = context.get().getRunState().getRound();
        assets.font().draw(batch, "survived to round " + round + "/" + GameBalance.TOTAL_ROUNDS, 240f, 200f);
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
            assets.font().draw(batch, "RESTART", getX() + 34f, getY() + 23f);
        }
    }
}
