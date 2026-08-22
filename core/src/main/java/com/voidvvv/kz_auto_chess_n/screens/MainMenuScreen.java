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
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.RunSnapshot;

/**
 * 主菜单（architecture §七：开始远征/继续远征/图鉴）。快照存在性决定「继续远征」可见性；
 * 续玩 = loadRunSnapshot → BattleScreen 快照构造（跳过 StartRun——CP16/CP17 配套）。
 * 自绘 Actor（无 Skin 资产，Q4 遗留口径）。
 */
public final class MainMenuScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;

    public MainMenuScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        addMenuButton("开始远征", 190f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new RunSetupScreen(game, assets, data, metaService));
            }
        });
        if (metaService.hasRunSnapshot()) {
            addMenuButton("继续远征", 146f, new Runnable() {
                @Override
                public void run() {
                    RunSnapshot snapshot = metaService.loadRunSnapshot(data);
                    if (snapshot != null) {
                        game.setScreen(new BattleScreen(game, assets, data, metaService, snapshot));
                    }
                }
            });
        }
        addMenuButton("图鉴", 102f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new CodexScreen(game, assets, data, metaService));
            }
        });
        Gdx.input.setInputProcessor(stage);
    }

    private void addMenuButton(String text, float y, final Runnable action) {
        Actor button = new MenuButton(text) {
            @Override
            protected void onClicked() {
                action.run();
            }
        };
        button.setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, y);
        stage.addActor(button);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.getBatch().begin();
        assets.font().getData().setScale(2f);
        assets.font().draw(stage.getBatch(), "余烬军团", 276f, 300f); // 标题（lore 暂定名）
        assets.font().getData().setScale(1f);
        stage.getBatch().end();
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

    /** 自绘菜单按钮（白 region tint + 内置字体） */
    private abstract class MenuButton extends Actor {
        private final String text;

        MenuButton(String text) {
            this.text = text;
            setSize(160f, 36f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            });
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 160f / 2f - text.length() * 6f, getY() + 23f);
        }
    }
}
