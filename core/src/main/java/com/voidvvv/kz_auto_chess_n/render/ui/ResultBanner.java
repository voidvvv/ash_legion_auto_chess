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
                    flow.continueAfterDefeat(context.get()); // 胜局 no-op（pendingChest 守卫，口径 #9）
                }
            });
        }
    }

    private final RunFlowSystem flow;
    private final java.util.function.Supplier<RunContext> context;
    private final Assets assets;
    private String text = "";
    private Color tint = Color.WHITE;
    private String hint = "";

    public ResultBanner(RunFlowSystem flow, java.util.function.Supplier<RunContext> context, Assets assets) {
        this.flow = flow;
        this.context = context;
        this.assets = assets;
        addActor(new ClickCatcher());
    }

    /**
     * 每帧刷新文案（RESULT 期由 Screen 调用）。statusLine：败局附加行（Screen 组装）——
     * 败箱期「选择战败补给」/ 败 1~2 无箱「点击任意处重试 · 剩余机会 N」/ 败 3「机会耗尽 · 远征失败」；
     * null = 无附加行（败局回退内置提示；胜局 hint 内置）。
     */
    public void refresh(BattleOutcome outcome, String statusLine) {
        if (outcome == BattleOutcome.PLAYER_WIN) {
            text = "胜利";
            tint = Color.GREEN;
            hint = "选择一个宝箱"; // 胜局唯一出口 = PickChest（口径 #9，无自动推进）
        } else if (outcome == BattleOutcome.ENEMY_WIN) {
            text = "战败";
            tint = Color.RED;
            hint = statusLine != null ? statusLine : "点击任意处重试";
        } else {
            text = "超时";
            tint = Color.YELLOW;
            hint = statusLine != null ? statusLine : "点击任意处重试";
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
        assets.font().draw(batch, text, 296f, 185f); // 2 字 ×24px 居中（原 7 字 ×~24px 的 270）
        assets.font().getData().setScale(1f);
        assets.font().setColor(Color.WHITE);
        assets.font().draw(batch, hint, 272f, 150f); // 提示行居中基准（怜悯长行手验微调）
    }
}
