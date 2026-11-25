package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;

/**
 * 测试样片菜单（品质改造 §0.2）：主菜单「测试样片」进入的轻量菜单——「破阵踏对拍」「处决对拍」
 * 与返回，两个对拍按钮共用一组种子按钮（42 → 43 → 44 → 42 循环，默认 42）。选题与种子后进入
 * 真实 {@link BattleScreen}（对拍局口径：固定森林 / 无英雄 / EMPTY 修正 / 10 金 / 空名册，
 * 固定敌阵与商店表见 {@link PaiTestScript}）。
 *
 * <p>出口：备战可由现有暂停菜单放弃后重开（同种子）；终局「按同种子重开」以原题原种子重建
 * 整个上下文；返回主菜单可重新选题与种子。自绘 Actor（无 Skin 资产，Q4 遗留口径——
 * 沿 MainMenu/RunSetup 先例各屏独立实现按钮壳）。
 */
public final class SampleMenuScreen implements Screen {

    /** 按钮文案（设计书 §0.2 原文；包级常量供验收对照） */
    static final String BUTTON_DUEL_A = "破阵踏对拍";
    static final String BUTTON_DUEL_B = "处决对拍";
    static final String BUTTON_BACK = "返回";

    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;
    /** 共用种子（两个对拍按钮同源；默认 42，点击循环 42 → 43 → 44 → 42） */
    private int seed = PaiTestScript.SEED_CYCLE[0];

    public SampleMenuScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        addMenuButton(BUTTON_DUEL_A, 232f, new Runnable() {
            @Override
            public void run() {
                startDuel(PaiTestScript.Topic.A);
            }
        });
        addMenuButton(BUTTON_DUEL_B, 188f, new Runnable() {
            @Override
            public void run() {
                startDuel(PaiTestScript.Topic.B);
            }
        });
        addMenuButton(BUTTON_BACK, 8f, 96f, 28f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new MainMenuScreen(game, assets, data, metaService));
            }
        });
        // 种子按钮放最后构造：文案动态读共用 seed（点击循环即刷新——§0.2「共用一组种子按钮」）
        stage.addActor(new SeedButton());
        Gdx.input.setInputProcessor(stage);
    }

    /** 域边界事件：原题原种子进入真实 BattleScreen（对拍局——§0.2） */
    private void startDuel(PaiTestScript.Topic topic) {
        game.setScreen(BattleScreen.newSampleDuelRun(game, assets, data, metaService, topic, seed));
    }

    private void addMenuButton(String text, float y, final Runnable action) {
        addMenuButton(text, y, 160f, 36f, action);
    }

    private void addMenuButton(String text, float y, float width, float height, final Runnable action) {
        final String labelText = text;
        Actor button = new SampleButton(width, height) {
            @Override
            protected String labelText() {
                return labelText;
            }

            @Override
            protected void onClicked() {
                action.run();
            }
        };
        button.setPosition((BoardGeometry.VIRTUAL_W - width) / 2f, y);
        stage.addActor(button);
    }

    /** 种子按钮文案（纯函数；包级可测——种子循环的可视反馈） */
    static String seedLabel(int seed) {
        return "种子 " + seed;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.getBatch().begin();
        assets.titleFont().draw(stage.getBatch(), "测试样片", 0f, 340f,
                BoardGeometry.VIRTUAL_W, com.badlogic.gdx.utils.Align.center, false);
        assets.uiFont().draw(stage.getBatch(), "选对拍题与种子", 0f, 320f,
                BoardGeometry.VIRTUAL_W, com.badlogic.gdx.utils.Align.center, false);
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

    /** 自绘菜单按钮壳（原创四态按钮壳 + 内置字体；沿 MainMenuScreen.MenuButton 形制独立实现） */
    private abstract class SampleButton extends Actor {
        private final float width;
        private final float height;
        private final ClickListener listener;

        SampleButton(float width, float height) {
            this.width = width;
            this.height = height;
            setSize(width, height);
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            };
            addListener(listener);
        }

        /** 每帧取文案（SeedButton 动态读共用 seed） */
        protected abstract String labelText();

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            String text = labelText();
            com.voidvvv.kz_auto_chess_n.render.ButtonArt.drawShell(batch, assets, listener,
                    getX(), getY(), width, height, parentAlpha, false);
            assets.uiFont().draw(batch, text, getX(), getY() + 23f, width,
                    com.badlogic.gdx.utils.Align.center, false);
        }
    }

    /** 共用种子按钮：文案随当前 seed 实时刷新（42 → 43 → 44 → 42） */
    private final class SeedButton extends SampleButton {
        SeedButton() {
            super(160f, 36f);
            setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, 144f);
        }

        @Override
        protected String labelText() {
            return seedLabel(seed);
        }

        @Override
        protected void onClicked() {
            seed = PaiTestScript.nextSeed(seed);
        }
    }
}
