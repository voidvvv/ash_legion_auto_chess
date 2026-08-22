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
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.HeroProgress;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 图鉴屏 MVP（architecture §七 CodexScreen，Phase 5 Q5 销账）：英雄熟练度 + 场景解锁只读。 */
public final class CodexScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final Stage stage;
    private final List<String> lines = new ArrayList<String>();

    public CodexScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.stage = new Stage(new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H));
        Actor back = new BackButton();
        back.setPosition(12f, 12f);
        stage.addActor(back);
        Set<String> unlocked = metaService.unlockedSceneIds(data);
        for (HeroData hero : data.getHeroes().values()) {
            lines.addAll(codexHeroLines(hero, metaService.getProfile(), data));
        }
        for (SceneData scene : data.getScenes().values()) {
            lines.addAll(codexSceneLines(scene, unlocked, metaService.getProfile()));
        }
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
        assets.font().draw(stage.getBatch(), "图鉴", 298f, 340f);
        assets.font().getData().setScale(1f);
        float y = BoardGeometry.VIRTUAL_H - 24f;
        for (int i = 0; i < lines.size() && y > 44f; i++) {
            assets.font().draw(stage.getBatch(), lines.get(i), 16f, y);
            y -= 18f;
        }
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

    private final class BackButton extends Actor {
        BackButton() {
            setSize(96f, 28f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new MainMenuScreen(game, assets, data, metaService));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.75f, 0.35f, 0.25f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "返回", getX() + 36f, getY() + 19f);
        }
    }

    /** 英雄块文案（包级静态可测） */
    static List<String> codexHeroLines(HeroData hero, Profile profile, GameData data) {
        List<String> result = new ArrayList<String>(2);
        HeroProgress progress = profile.getHeroProgress().get(hero.getId());
        int level = progress == null ? 1 : progress.getLevel();
        int exp = progress == null ? 0 : progress.getExp();
        int need = GameBalance.masteryExpToNext(level);
        String mastery = need > 0
                ? "Lv." + level + "（经验 " + exp + "/" + need + "）"
                : "Lv." + level + "（满级）";
        result.add("【" + hero.getName() + "】" + RunSetupScreen.passiveText(hero) + " · " + mastery);
        UnitData legendary = hero.getLegendaryUnitId() == null
                ? null : data.getUnit(hero.getLegendaryUnitId());
        String legendaryText = legendary == null ? "" : " · 专属传奇：" + legendary.getName()
                + (level >= 3 ? "（已解锁）" : "（Lv.3 解锁）");
        result.add("   " + hero.getDesc() + legendaryText);
        return result;
    }

    /** 场景块文案（包级静态可测） */
    static List<String> codexSceneLines(SceneData scene, Set<String> unlocked, Profile profile) {
        List<String> result = new ArrayList<String>(1);
        boolean isUnlocked = unlocked.contains(scene.getId());
        boolean completed = profile.getCompletedScenes().contains(scene.getId());
        String status = completed ? "已通关" : (isUnlocked ? "已解锁" : "未解锁");
        result.add("【" + scene.getName() + "】" + status);
        return result;
    }
}
