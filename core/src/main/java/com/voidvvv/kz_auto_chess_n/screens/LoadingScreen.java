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

/**
 * 装载屏（render §2.1 双 Viewport 同参数）：占位图集已在 Main.create 同步生成完毕，
 * 此处首帧绘出提示后切主菜单（"首帧完成占位生成后切 MainMenu"）。文字用内置默认字体
 * （Q4：无 CJK 字模，本期屏显文案用 ASCII，观感降级已知）。
 */
public final class LoadingScreen implements Screen {
    private final Game game;
    private final Assets assets;
    private final GameData data;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport;
    private final SpriteBatch batch;
    private int framesDrawn;

    public LoadingScreen(Game game, Assets assets, GameData data) {
        this.game = game;
        this.assets = assets;
        this.data = data;
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
        assets.font().draw(batch, "EMBER LEGION", 40f, BoardGeometry.VIRTUAL_H / 2f + 20f);
        assets.font().draw(batch, "loading...", 60f, BoardGeometry.VIRTUAL_H / 2f - 10f);
        batch.end();
        framesDrawn++;
        if (framesDrawn >= 2) { // 首帧已上屏 → 装载完成
            game.setScreen(new MainMenuScreen(game, assets, data));
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
