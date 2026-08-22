package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.SurrenderCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 战斗 HUD（Q2 极简版）：×1/×2 变速 + 投降按钮 + 60s 计时条。
 *
 * <p>投降走命令（SurrenderCommand）；变速<b>不进命令路径</b>（口径 #5：只改 accumulator
 * 消费速率，经 SpeedListener 回调 Screen 改 speedFactor，模拟路径零感知）。
 * 计时条 = 1×1 白 region tint（口径 #19）。
 */
public final class BattleHud extends Group {
    private static final Color BAR_BACK = new Color(0.2f, 0.15f, 0.15f, 0.9f);
    private static final Color BAR_FRONT = new Color(0.9f, 0.35f, 0.2f, 1f);

    /** 变速回调（Screen 实现：改 speedFactor） */
    public interface SpeedListener {
        void onSpeedChanged(float speedFactor);
    }

    private final Assets assets;
    private final SpeedListener speedListener;
    private final SpeedButton speedButton;
    private float speedFactor = 1f;
    private float timeoutRatio = 1f;

    public BattleHud(final CommandManager commandManager, Assets assets, SpeedListener speedListener) {
        this.assets = assets;
        this.speedListener = speedListener;
        this.speedButton = new SpeedButton();
        speedButton.setPosition(432f, BoardGeometry.VIRTUAL_H - 46f);
        addActor(speedButton);
        Actor giveUp = new GiveUpButton(commandManager, assets);
        giveUp.setPosition(520f, BoardGeometry.VIRTUAL_H - 46f);
        addActor(giveUp);
    }

    /** 每帧刷新计时条（BATTLE 期由 Screen 调用；state 只读） */
    public void refresh(BattleState state) {
        timeoutRatio = state == null ? 1f
                : Math.max(0f, 1f - state.getElapsed() / GameBalance.BATTLE_TIMEOUT);
    }

    /** 重开时归位 ×1（Screen.restartRun 调用） */
    public void resetSpeed() {
        speedFactor = 1f;
        speedListener.onSpeedChanged(1f);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        float width = 200f;
        float x = 428f;
        float y = BoardGeometry.VIRTUAL_H - 14f;
        batch.setColor(BAR_BACK);
        batch.draw(assets.region(PlaceholderKeys.WHITE), x, y, width, 4f);
        batch.setColor(BAR_FRONT);
        batch.draw(assets.region(PlaceholderKeys.WHITE), x, y, width * timeoutRatio, 4f);
        batch.setColor(Color.WHITE);
    }

    /** SPD 切换按钮（x1 ⇄ x2） */
    private final class SpeedButton extends Actor {
        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.35f, 0.45f, 0.6f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, speedFactor == 1f ? "SPD x1" : "SPD x2",
                    getX() + 6f, getY() + 24f);
        }

        SpeedButton() {
            setSize(76f, 34f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    speedFactor = speedFactor == 1f ? GameBalance.BATTLE_SPEED_FACTOR_FAST : 1f;
                    speedListener.onSpeedChanged(speedFactor);
                }
            });
        }
    }

    /** 投降按钮（命令路径） */
    private static final class GiveUpButton extends Actor {
        private final CommandManager commandManager;
        private final Assets assets;

        GiveUpButton(final CommandManager commandManager, Assets assets) {
            this.commandManager = commandManager;
            this.assets = assets;
            setSize(96f, 34f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(SurrenderCommand.INSTANCE);
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.6f, 0.25f, 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "GIVE UP", getX() + 18f, getY() + 24f);
        }
    }
}
