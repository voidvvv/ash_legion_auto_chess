package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;

/**
 * 终局面板（RUN_END，Phase 6 = RunResult 形态 MVP——裁决 D6）：成因文案 + 存活轮次 +
 * 档案结算行（熟练度/升级/解锁，BattleScreen 观察注入）+ 本局 seed + RESTART + 返回主菜单。
 * 档案写入归 BattleScreen→MetaService（Screen 点火器——裁决 D11），本类只读展示。
 */
public final class RunEndPanel extends Group {

    /** 重开回调（Screen 实现：同英雄同场景新 seed 组装新鲜 RunContext 后 restart） */
    public interface RestartListener {
        void onRestart();
    }

    /** 返回主菜单回调（Screen 实现：setScreen(MainMenuScreen)） */
    public interface MenuListener {
        void onMenuRequested();
    }

    private final Assets assets;
    private final RestartListener restartListener;
    private final MenuListener menuListener;
    private final java.util.function.Supplier<RunContext> context;
    /** 结算行（RUN_END 首帧由 Screen 注入；null = 尚未结算，回退旧行） */
    private List<String> settlementLines;

    public RunEndPanel(Assets assets, RestartListener restartListener, MenuListener menuListener,
                       java.util.function.Supplier<RunContext> context) {
        this.assets = assets;
        this.restartListener = restartListener;
        this.menuListener = menuListener;
        this.context = context;
        Actor restart = new EndButton("重新开始", 120f, 300f, new Runnable() {
            @Override
            public void run() {
                restartListener.onRestart();
            }
        });
        addActor(restart);
        Actor menu = new EndButton("返回主菜单", 220f, 300f, new Runnable() {
            @Override
            public void run() {
                menuListener.onMenuRequested();
            }
        });
        addActor(menu);
    }

    /** 结算行注入（BattleScreen 观察 RUN_END 首帧调用——每局一次） */
    public void setSettlementLines(List<String> lines) {
        this.settlementLines = lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 74f, 460f, 216f);
        batch.setColor(Color.WHITE);
        RunContext ctx = context.get();
        boolean abandoned = ctx.getRunState().getEndCause() == RunEndCause.ABANDONED;
        assets.font().getData().setScale(2f);
        assets.font().draw(batch, abandoned ? "远征已放弃" : "远征通关", 272f, 268f); // 4 字 ×24px 居中
        assets.font().getData().setScale(1f);
        int round = ctx.getRunState().getRound();
        assets.font().draw(batch, "抵达第 " + round + "/" + GameBalance.TOTAL_ROUNDS + " 轮", 280f, 240f);
        if (settlementLines != null) {
            float y = 218f;
            for (String line : settlementLines) {
                assets.font().draw(batch, line, 252f, y);
                y -= 20f;
            }
        } else {
            assets.font().draw(batch, "熟练度 +" + ctx.getRunState().getMasteryAwarded(), 262f, 218f);
        }
        assets.font().draw(batch, "种子 " + ctx.getRunState().getSeed(), 285f, 126f);
    }

    /** 终局双钮共用壳 */
    private final class EndButton extends Actor {
        private final String text;

        EndButton(String text, float x, float y, final Runnable action) {
            this.text = text;
            setSize(150f, 32f);
            setPosition(x, y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    action.run();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 75f - text.length() * 6f, getY() + 21f);
        }
    }
}
