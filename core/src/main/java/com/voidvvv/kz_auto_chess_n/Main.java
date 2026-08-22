package com.voidvvv.kz_auto_chess_n;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderArt;
import com.voidvvv.kz_auto_chess_n.screens.LoadingScreen;

/**
 * 应用入口（project_structure §一 #10：Phase 4 替换为 Game + Screen）。
 *
 * <p>create：加载静态数据（软告警打日志）→ 运行时占位图集 → Assets 门面 → 装载屏。
 * 数据目录按运行时工作区相对路径 {@code data/}（lwjgl3:run 的 workingDir 即 assets/，
 * build.gradle 已配）。dispose：弃 Assets 与当前 Screen（Assets 归本类持有，Screen 不重复弃）。
 */
public class Main extends Game {
    private Assets assets;

    @Override
    public void create() {
        GameData data = JsonLoader.loadFromDirectory(Gdx.files.local("data/"));
        for (String warning : data.getWarnings()) {
            Gdx.app.log("Main", "[软告警] " + warning);
        }
        PlaceholderArt art = new PlaceholderArt(data); // GL 线程一次性生成
        this.assets = new Assets(art);
        setScreen(new LoadingScreen(this, assets, data));
    }

    @Override
    public void dispose() {
        super.dispose(); // 弃当前 Screen（各自 dispose 自有资源）
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
    }
}
