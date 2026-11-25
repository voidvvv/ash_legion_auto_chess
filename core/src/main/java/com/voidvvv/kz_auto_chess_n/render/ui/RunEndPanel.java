package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.RunEndCause;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;

/**
 * 终局面板（RUN_END，Phase 6 = RunResult 形态 MVP——裁决 D6）：成因文案 + 存活轮次 +
 * 档案结算行（熟练度/升级/解锁，BattleScreen 观察注入）+ 本局 seed + RESTART + 返回主菜单。
 * 档案写入归 BattleScreen→MetaService（Screen 点火器——裁决 D11），本类只读展示。
 *
 * <p>品质改造 §0.1/§0.2：档案轨关闭的局（测试档 / 对拍）终局隐藏熟练度行（不结算档案故无行可
 * 回退）；对拍局通关标题「对拍完成」、重开钮「按同种子重开」。
 */
public final class RunEndPanel extends Group {

    /** 重开回调（Screen 实现：同英雄同场景新 seed 组装新鲜 RunContext 后 restart） */
    public interface RestartListener {
        void onRestart();
    }

    /** 返回主菜单回调（Screen 实现：setScreen(MainMenuScreen)） */
    public interface MenuListener {
        void onMenuRequested();
    }

    private final Assets assets;
    private final RestartListener restartListener;
    private final MenuListener menuListener;
    private final java.util.function.Supplier<RunContext> context;
    /** 运行模式（品质改造 §0.1：不可变模式参数——测试档终局隐藏熟练度行） */
    private final RunMode mode;
    /** 结算行（RUN_END 首帧由 Screen 注入；null = 尚未结算，回退旧行） */
    private List<String> settlementLines;

    /** 兼容构造（存量调用方）：正式模式（品质改造 §0.1 既有构造器默认正式） */
    public RunEndPanel(Assets assets, RestartListener restartListener, MenuListener menuListener,
                       java.util.function.Supplier<RunContext> context) {
        this(assets, restartListener, menuListener, context, RunMode.NORMAL);
    }

    public RunEndPanel(Assets assets, RestartListener restartListener, MenuListener menuListener,
                       java.util.function.Supplier<RunContext> context, RunMode mode) {
        this.assets = assets;
        this.restartListener = restartListener;
        this.menuListener = menuListener;
        this.context = context;
        this.mode = java.util.Objects.requireNonNull(mode, "mode 不能为 null");
        Actor restart = new EndButton(restartLabel(mode), 120f, 300f, new Runnable() {
            @Override
            public void run() {
                restartListener.onRestart();
            }
        });
        addActor(restart);
        Actor menu = new EndButton("返回主菜单", 220f, 300f, new Runnable() {
            @Override
            public void run() {
                menuListener.onMenuRequested();
            }
        });
        addActor(menu);
    }

    /** 结算行注入（BattleScreen 观察 RUN_END 首帧调用——每局一次） */
    public void setSettlementLines(List<String> lines) {
        this.settlementLines = lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        batch.setColor(0f, 0f, 0f, 0.65f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 90f, 74f, 460f, 216f);
        batch.setColor(Color.WHITE);
        RunContext ctx = context.get();
        assets.titleFont().draw(batch, endTitleText(ctx.getRunState().getEndCause(), mode), 0f, 270f,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.VIRTUAL_W,
                com.badlogic.gdx.utils.Align.center, false); // 标题黑体 20px 居中
        int round = ctx.getRunState().getRound();
        assets.uiFont().draw(batch, "抵达第 " + round + "/" + ctx.totalRounds() + " 轮", 0f, 242f,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.VIRTUAL_W,
                com.badlogic.gdx.utils.Align.center, false);
        if (settlementLines != null) {
            float y = 220f;
            for (String line : settlementLines) {
                assets.uiFont().draw(batch, line, 0f, y,
                        com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.VIRTUAL_W,
                        com.badlogic.gdx.utils.Align.center, false);
                y -= 16f;
            }
        } else if (fallbackMasteryLine(ctx.getRunState(), mode) != null) {
            assets.uiFont().draw(batch, fallbackMasteryLine(ctx.getRunState(), mode), 0f, 220f,
                    com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.VIRTUAL_W,
                    com.badlogic.gdx.utils.Align.center, false);
        }
        assets.uiFont().draw(batch, "种子 " + ctx.getRunState().getSeed(), 0f, 128f,
                com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry.VIRTUAL_W,
                com.badlogic.gdx.utils.Align.center, false);
    }

    /** 结算行缺省回退（纯函数；品质改造 §0.1/§0.2）：档案轨关闭的局（测试档 / 对拍）终局隐藏
     *  熟练度行（null = 不画）——不结算档案，回退行只会造成「有熟练度进账」的假信号。 */
    static String fallbackMasteryLine(com.voidvvv.kz_auto_chess_n.entities.RunState runState,
                                      RunMode mode) {
        return mode.closesArchiveTrack() ? null : "熟练度 +" + runState.getMasteryAwarded();
    }

    /** 终局标题（存量 1 参口径——正式局） */
    static String endTitleText(RunEndCause cause) {
        return endTitleText(cause, com.voidvvv.kz_auto_chess_n.entities.RunMode.NORMAL);
    }

    /** 终局标题（品质改造 §0.2）：对拍局通关 = 「对拍完成」，败/弃走原流程文案；
     *  正式局 COMPLETED 远征通关 / ABANDONED 远征已放弃 / DEFEATED 远征失败（render §九） */
    static String endTitleText(RunEndCause cause, RunMode mode) {
        if (mode.isSampleDuel() && cause == RunEndCause.COMPLETED) {
            return "对拍完成";
        }
        if (cause == null) {
            return "远征通关"; // 防御：RUN_END 期 endCause 必非 null
        }
        switch (cause) {
            case ABANDONED:
                return "远征已放弃";
            case DEFEATED:
                return "远征失败";
            case COMPLETED:
            default:
                return "远征通关";
        }
    }

    /** 重开按钮文案（品质改造 §0.2）：对拍「按同种子重开」（原题原种子重建上下文），其余「重新开始」 */
    static String restartLabel(RunMode mode) {
        return mode.isSampleDuel() ? "按同种子重开" : "重新开始";
    }

    /** 终局双钮共用壳（原创四态按钮壳） */
    private final class EndButton extends Actor {
        private final String text;
        private final ClickListener listener;

        EndButton(String text, float x, float y, final Runnable action) {
            this.text = text;
            setSize(150f, 32f);
            setPosition(x, y);
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    action.run();
                }
            };
            addListener(listener);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            com.voidvvv.kz_auto_chess_n.render.ButtonArt.drawShell(batch, assets, listener,
                    getX(), getY(), getWidth(), getHeight(), parentAlpha, false);
            assets.uiFont().draw(batch, text, getX(), getY() + 21f, getWidth(),
                    com.badlogic.gdx.utils.Align.center, false);
        }
    }
}
