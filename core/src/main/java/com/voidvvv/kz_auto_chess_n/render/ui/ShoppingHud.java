package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * ⑥ 区开战按钮（SHOPPING 期）：点击入队 StartBattleCommand——UI 域只发命令不改状态
 * （input §7.1）；门控（SHOPPING 才有效）由 RunFlowSystem handler 判定。
 * 自绘 Actor（无 Skin 资产，Q4）。
 */
public final class ShoppingHud extends Group {

    public ShoppingHud(final CommandManager commandManager, Assets assets) {
        addActor(new FightButton(commandManager, assets));
    }

    private static final class FightButton extends Actor {
        private final CommandManager commandManager;
        private final Assets assets;

        FightButton(CommandManager commandManager, Assets assets) {
            this.commandManager = commandManager;
            this.assets = assets;
            setSize(64f, 40f);
            setPosition(BoardGeometry.BENCH_X + BoardGeometry.BENCH_W + 6f, BoardGeometry.BENCH_Y + 40f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(StartBattleCommand.INSTANCE);
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.8f, 0.45f, 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "FIGHT", getX() + 10f, getY() + 24f);
        }
    }
}
