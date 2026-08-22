package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.save.MetaService;

/**
 * 装载屏（render §2.1 双 Viewport 同参数）：占位图集已在 Main.create 同步生成完毕，
 * 此处首帧绘出提示后切主菜单（"首帧完成占位生成后切 MainMenu"）。字体经 Assets.font()
 * 加载 Fusion Pixel（已入库）；缺文件回退内置默认时中文不渲染但不炸（计划 §5.3-6）。
 */
public final class LoadingScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final MetaService metaService;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport;
    private final SpriteBatch batch;
    private int framesDrawn;

    public LoadingScreen(Game game, Assets assets, GameData data, MetaService metaService) {
        this.game = game;
        this.assets = assets;
        this.data = data;
        this.metaService = metaService;
        this.viewport = new FitViewport(BoardGeometry.VIRTUAL_W, BoardGeometry.VIRTUAL_H, camera);
        this.batch = new SpriteBatch();
    }

    @Override
    public void show() {
        framesDrawn = 0;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.08f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        assets.font().draw(batch, "余烬军团", 296f, BoardGeometry.VIRTUAL_H / 2f + 20f); // 4 字居中（lore 暂定名）
        assets.font().draw(batch, "装载中……", 293f, BoardGeometry.VIRTUAL_H / 2f - 10f);
        batch.end();
        framesDrawn++;
        if (framesDrawn >= 2) { // 首帧已上屏 → 装载完成
            game.setScreen(new MainMenuScreen(game, assets, data, metaService));
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
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
        batch.dispose();
    }
}
