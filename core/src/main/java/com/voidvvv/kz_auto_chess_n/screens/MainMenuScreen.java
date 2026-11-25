package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
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
    private final Texture background;

    public MainMenuScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.background = assets.mainMenuBackground();
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
        addMenuButton("测试样片", 58f, new Runnable() { // 图鉴下（品质改造 §0.2 对拍入口）
            @Override
            public void run() {
                game.setScreen(new SampleMenuScreen(game, assets, data, metaService));
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
        stage.getViewport().apply();
        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        batch.draw(background, 0f, 0f, BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H);
        assets.titleFont().draw(batch, "余烬军团", 0f, 300f, BoardGeometry.VIRTUAL_W, Align.center, false);
        batch.end();
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

    /** 自绘菜单按钮（原创四态按钮壳 + 内置字体文案；悬停/按下态见 ButtonArt） */
    private abstract class MenuButton extends Actor {
        private final String text;
        private final ClickListener listener;

        MenuButton(String text) {
            this.text = text;
            setSize(160f, 36f);
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            };
            addListener(listener);
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            com.voidvvv.kz_auto_chess_n.render.ButtonArt.drawShell(batch, assets, listener,
                    getX(), getY(), getWidth(), getHeight(), parentAlpha, false);
            assets.uiFont().draw(batch, text, getX(), getY() + 23f, getWidth(), Align.center, false);
        }
    }
}
