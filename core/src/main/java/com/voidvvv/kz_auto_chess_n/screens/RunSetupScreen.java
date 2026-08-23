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
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;

import java.util.ArrayList;
import java.util.List;

/**
 * 远征准备屏（architecture §七 RunSetupScreen：英雄选择 + 场景选择两步式合一）。
 * 纯 UI 态——高亮/选中在本屏；「开始远征」为域边界事件，结算 seed/sceneId/heroId
 * 三参传 BattleScreen（StartRun 命令在 BattleScreen.show 入队 = 回放第 0 条记录）。
 * 解锁判定/熟练度查询走 MetaService（档案语义调用，本屏零规则逻辑）。
 */
public final class RunSetupScreen implements Screen {
    private static final float CARD_W = 186f;
    private static final float CARD_H = 84f;
    private static final float CARD_GAP = 16f;
    private static final float HERO_Y = 232f;
    private static final float SCENE_Y = 116f;
    private static final float SCENE_H = 56f;

    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;

    private final List<HeroData> heroes = new ArrayList<HeroData>();
    private final List<SceneData> scenes = new ArrayList<SceneData>();
    private final List<String> unlockedSceneIds;
    private int selectedHero = -1;
    private int selectedScene = -1;

    public RunSetupScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        heroes.addAll(data.getHeroes().values());
        scenes.addAll(data.getScenes().values());
        this.unlockedSceneIds = new ArrayList<String>(metaService.unlockedSceneIds(data));
        buildUi();
    }

    private void buildUi() {
        for (int i = 0; i < heroes.size(); i++) {
            stage.addActor(new HeroCard(i));
        }
        for (int i = 0; i < scenes.size(); i++) {
            stage.addActor(new SceneCard(i));
        }
        Actor start = new TextButton("开始远征", 160f, 36f, new Runnable() {
            @Override
            public void run() {
                startRun();
            }
        });
        start.setPosition((BoardGeometry.VIRTUAL_W - 160f) / 2f, 44f);
        stage.addActor(start);
        Actor back = new TextButton("返回", 96f, 28f, new Runnable() {
            @Override
            public void run() {
                game.setScreen(new MainMenuScreen(game, assets, data, metaService));
            }
        });
        back.setPosition((BoardGeometry.VIRTUAL_W - 96f) / 2f, 8f);
        stage.addActor(back);
    }

    /** 域边界事件：结算 StartRun 参数（hero/scene 必选，seed 由 UI 给定——Q3 裁决口径） */
    private void startRun() {
        if (selectedHero < 0 || selectedScene < 0) {
            return; // 未选齐：忽略（UI 已用提示文案引导）
        }
        long runSeed = System.nanoTime();
        game.setScreen(new BattleScreen(game, assets, data, metaService,
                runSeed, scenes.get(selectedScene).getId(), heroes.get(selectedHero).getId()));
    }

    private float cardX(int index) {
        float total = scenes.size() * CARD_W + (scenes.size() - 1) * CARD_GAP;
        return (BoardGeometry.VIRTUAL_W - total) / 2f + index * (CARD_W + CARD_GAP);
    }

    private boolean isSceneUnlocked(int index) {
        return unlockedSceneIds.contains(scenes.get(index).getId());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        stage.getBatch().begin();
        assets.font().getData().setScale(2f);
        assets.font().draw(stage.getBatch(), "远征准备", 276f, 336f); // 4 字 ×24px 居中
        assets.font().getData().setScale(1f);
        assets.font().draw(stage.getBatch(), "选择英雄与场景", 286f, 318f);
        stage.getBatch().end();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    /** 英雄卡：名 + 被动一行 + 熟练度 Lv/经验 + 选中高亮 */
    private final class HeroCard extends Actor {
        private final int index;

        HeroCard(int index) {
            this.index = index;
            setSize(CARD_W, CARD_H);
            setPosition(cardX(index), HERO_Y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedHero = HeroCard.this.index;
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            HeroData hero = heroes.get(index);
            boolean selected = selectedHero == index;
            Color old = batch.getColor();
            batch.setColor(selected ? 0.4f : 0.24f, selected ? 0.46f : 0.3f,
                    selected ? 0.4f : 0.36f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            int level = ProfileService.masteryLevel(metaService.getProfile(), hero.getId());
            assets.font().draw(batch, hero.getName(), getX() + 10f, getY() + 66f);
            assets.font().draw(batch, passiveText(hero), getX() + 10f, getY() + 48f);
            assets.font().draw(batch, masteryText(level), getX() + 10f, getY() + 30f);
            assets.font().draw(batch, selected ? "（已选）" : "点击选择", getX() + 10f, getY() + 12f);
        }
    }

    /** 场景卡：名 + 解锁态（未解锁灰置 + 前置名提示） */
    private final class SceneCard extends Actor {
        private final int index;

        SceneCard(int index) {
            this.index = index;
            setSize(CARD_W, SCENE_H);
            setPosition(cardX(index), SCENE_Y);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (isSceneUnlocked(SceneCard.this.index)) {
                        selectedScene = SceneCard.this.index;
                    }
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            SceneData scene = scenes.get(index);
            boolean unlocked = isSceneUnlocked(index);
            boolean selected = selectedScene == index;
            Color old = batch.getColor();
            if (unlocked) {
                batch.setColor(selected ? 0.4f : 0.24f, selected ? 0.46f : 0.3f,
                        selected ? 0.4f : 0.36f, parentAlpha);
            } else {
                batch.setColor(0.16f, 0.16f, 0.18f, parentAlpha);
            }
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, scene.getName(), getX() + 10f, getY() + 38f);
            if (unlocked) {
                assets.font().draw(batch, selected ? "（已选）" : "点击选择", getX() + 10f, getY() + 18f);
            } else {
                SceneData prerequisite = data.getScene(scene.getUnlockAfter());
                String gate = prerequisite == null ? scene.getUnlockAfter() : prerequisite.getName();
                assets.font().draw(batch, "通关「" + gate + "」解锁", getX() + 10f, getY() + 18f);
            }
        }
    }

    /** 通用文字按钮（自绘壳，沿 PauseMenuDialog.MenuButton 形制独立实现避免跨包复用） */
    private final class TextButton extends Actor {
        private final Runnable action;
        private final String text;
        private final float textY;

        TextButton(String text, float width, float height, Runnable action) {
            this.text = text;
            this.action = action;
            this.textY = height / 2f + 5f;
            setSize(width, height);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    TextButton.this.action.run();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + getWidth() / 2f - text.length() * 6f,
                    getY() + textY);
        }
    }

    /** 被动一行文案（HeroData → 展示文本；中文文案、英文标识符；包级静态可测） */
    static String passiveText(HeroData hero) {
        switch (hero.getPassiveType()) {
            case START_GOLD:
                return "被动：开局金币 +" + Math.round(hero.getPassiveValue());
            case SYNERGY_AMP:
                return "被动：" + hero.getPassiveSynergyIds().size() + " 系羁绊效果 +"
                        + Math.round(hero.getPassiveValue()) + "%";
            case ENERGY_GAIN:
                return "被动：全队能量获取 +" + Math.round(hero.getPassiveValue()) + "%";
            default:
                return "被动：？";
        }
    }

    /** 熟练度一行文案（包级静态可测） */
    static String masteryText(int level) {
        if (level >= GameBalance.MASTERY_MAX_LEVEL) {
            return "熟练度 Lv." + level + "（满级）";
        }
        return "熟练度 Lv." + level + "（升级解锁加成）";
    }
}
