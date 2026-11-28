package com.voidvvv.kz_auto_chess_n;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderArt;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.screens.LoadingScreen;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess;

/**
 * 应用入口（project_structure §一 #10：Phase 4 替换为 Game + Screen）。
 *
 * <p>create：加载静态数据（软告警打日志）→ 档案域门面 → 运行时占位图集 → Assets 门面 → 装载屏。
 * 数据目录为内部资源 {@code data/}，适用于本地开发和打包运行。
 */
public class Main extends Game {
    private Assets assets;
    private final FileHandle practiceDirectory;
    private PracticeAccess practiceAccess;

    public Main() {
        this(null);
    }

    /** Alternate desktop entry point. Practice files always use distinct names. */
    protected Main(FileHandle practiceDirectory) {
        this.practiceDirectory = practiceDirectory;
    }

    protected final PracticeAccess practiceAccess() {
        return practiceAccess;
    }

    @Override
    public void create() {
        GameData data = JsonLoader.loadFromDirectory(Gdx.files.internal("data/"));
        for (String warning : data.getWarnings()) {
            Gdx.app.log("Main", "[软告警] " + warning);
        }
        // 档案域门面（Phase 6，裁决 D14：save/ 目录随首次写入创建）
        MetaService metaService;
        if (practiceDirectory == null) {
            metaService = new MetaService(Gdx.files.local("save/profile.json"),
                    Gdx.files.local("save/run_snapshot.json"));
        } else {
            metaService = new MetaService(practiceDirectory.child("gm-profile.json"),
                    practiceDirectory.child("gm-run_snapshot.json"));
            practiceAccess = new PracticeAccess(data, metaService, this::getScreen);
        }
        metaService.loadProfile();
        PlaceholderArt art = new PlaceholderArt(data); // GL 线程一次性生成
        this.assets = new Assets(art);
        assets.playBgmIfEnabled(); // §1.2 BGM 接线（GameBalance.BGM_ENABLED 默认关闭）
        // 启动参数解析结果一次性从启动层读出（品质改造 §0.1）：999 档首屏直达测试局，
        // 其余走正常入口（装载屏后的 Screen 分流见 LoadingScreen.nextScreen）
        RunMode launchMode = RunMode.fromLaunchOverride(GameBalance.testStartGoldOverride);
        if (launchMode.isTestPeriod()) { // 桌面协作者需一眼确认经济环境已声明（设计书 §0.1）
            Gdx.app.log("Main", "[品质改造 §0.1] --test-gold=999 测试档：装载后首屏直达测试局"
                    + "（固定森林 / 无英雄 / 999 金 / 存档隔离，退出后走正式入口）");
        }
        setScreen(new LoadingScreen(this, assets, data, metaService, launchMode));
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
