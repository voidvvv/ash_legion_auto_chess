package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;

/**
 * 胜负横幅（RESULT 瞬态，口径 #6）：全屏透明 Actor 收点——点击立即继续；
 * 未点击则 RESULT_BANNER_SECONDS 后自动推进（RunFlowSystem.tickResult）。
 * 战毕 BattleState 保留只读供横幅读 outcome。
 */
public final class ResultBanner extends Group {

    /** 全屏透明收点 Actor（点哪都算点击继续） */
    private final class ClickCatcher extends Actor {
        ClickCatcher() {
            setSize(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    flow.continueAfterDefeat(context.get());
                }
            });
        }
    }

    private final RunFlowSystem flow;
    private final java.util.function.Supplier<RunContext> context;
    private final Assets assets;
    private String text = "";
    private Color tint = Color.WHITE;

    public ResultBanner(RunFlowSystem flow, java.util.function.Supplier<RunContext> context, Assets assets) {
        this.flow = flow;
        this.context = context;
        this.assets = assets;
        addActor(new ClickCatcher());
    }

    /** 每帧刷新文案（RESULT 期由 Screen 调用） */
    public void refresh(BattleOutcome outcome) {
        if (outcome == BattleOutcome.PLAYER_WIN) {
            text = "VICTORY";
            tint = Color.GREEN;
        } else if (outcome == BattleOutcome.ENEMY_WIN) {
            text = "DEFEAT";
            tint = Color.RED;
        } else {
            text = "TIMEOUT";
            tint = Color.YELLOW;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha); // ClickCatcher 透明仍收点
        batch.setColor(0f, 0f, 0f, 0.45f * parentAlpha);
        batch.draw(assets.region(com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys.WHITE),
                120f, 130f, 400f, 90f);
        batch.setColor(Color.WHITE);
        assets.font().getData().setScale(2f);
        assets.font().setColor(tint);
        assets.font().draw(batch, text, 270f, 185f);
        assets.font().getData().setScale(1f);
        assets.font().setColor(Color.WHITE);
        assets.font().draw(batch, "click to continue", 258f, 150f);
    }
}
