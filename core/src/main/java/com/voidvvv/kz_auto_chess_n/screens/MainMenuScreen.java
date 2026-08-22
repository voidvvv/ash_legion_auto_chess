package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * 极简主菜单（Q2 范围）：标题 + START 按钮。按钮为自绘 Actor（无 Skin 资产，Q4）——
 * 白 region tint + 内置字体；点击进战斗屏。
 */
public final class MainMenuScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final Stage stage;

    public MainMenuScreen(Game game, Assets assets, GameData data) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        stage.addActor(new StartButton());
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    /** 自绘 START 按钮（白 region tint + 默认字体；点击行为见类注释） */
    private final class StartButton extends Actor {
        StartButton() {
            setSize(160f, 36f);
            setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, 120f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // UI 域边界事件：点击"开始远征"结算 seed → StartRun 参数（Q3 裁决；architecture §一）
                    long runSeed = System.nanoTime();
                    game.setScreen(new BattleScreen(game, assets, data, runSeed));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "START", getX() + 58f, getY() + 23f);
        }
    }
}
