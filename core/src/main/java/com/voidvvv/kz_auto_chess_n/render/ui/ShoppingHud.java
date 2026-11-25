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
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * ⑥ 区开战按钮（SHOPPING 期）：点击入队 StartBattleCommand——UI 域只发命令不改状态
 * （input §7.1）；门控（SHOPPING 才有效）由 RunFlowSystem handler 判定。
 * 自绘 Actor（无 Skin 资产，Q4）。位置/尺寸取 {@link BoardGeometry#FIGHT_BUTTON_X} 族常量
 * （UI 抽屉化批1·Q9：迁至右侧、⑤ 羁绊面板上方；待主人实机核对后可校准）。
 */
public final class ShoppingHud extends Group {

    public ShoppingHud(final CommandManager commandManager, Assets assets) {
        addActor(new FightButton(commandManager, assets));
    }

    private static final class FightButton extends Actor {
        private final CommandManager commandManager;
        private final Assets assets;
        private final ClickListener listener;

        FightButton(CommandManager commandManager, Assets assets) {
            this.commandManager = commandManager;
            this.assets = assets;
            setSize(BoardGeometry.FIGHT_BUTTON_W, BoardGeometry.FIGHT_BUTTON_H);
            // Q9（UI 抽屉化批1）：自备战席右侧 (134,88) 迁至右侧、⑤ 羁绊面板上方——原位划归抽屉范围
            setPosition(BoardGeometry.FIGHT_BUTTON_X, BoardGeometry.FIGHT_BUTTON_Y);
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(StartBattleCommand.INSTANCE);
                }
            };
            addListener(listener);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            com.voidvvv.kz_auto_chess_n.render.ButtonArt.drawShell(batch, assets, listener,
                    getX(), getY(), getWidth(), getHeight(), parentAlpha, false);
            assets.uiFont().draw(batch, "开战", getX(), getY() + 24f, getWidth(),
                    com.badlogic.gdx.utils.Align.center, false);
        }
    }
}
