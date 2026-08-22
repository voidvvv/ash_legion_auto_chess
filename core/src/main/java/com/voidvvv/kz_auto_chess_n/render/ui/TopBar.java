package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * ① 区顶栏完整版（CP21）：轮次 / 金币 / 等级+经验（Lv.7 显 MAX）+ 暂停按钮（① 区右侧，
 * 回调开暂停菜单——Screen 装配见 CP29）。文字变更才 setText（渲染段零分配，既有约定保持）。
 */
public final class TopBar extends Group {

    /** 暂停回调（Screen 实现：开暂停菜单） */
    public interface PauseListener {
        void onPauseRequested();
    }

    private final Label label;
    private final Assets assets;
    private final PauseListener pauseListener;
    private String lastText = "";

    public TopBar(Assets assets, PauseListener pauseListener) {
        this.assets = assets;
        this.pauseListener = pauseListener;
        this.label = new Label("", new Label.LabelStyle(assets.font(), Color.WHITE));
        addActor(label);
        setPosition(8f, BoardGeometry.VIRTUAL_H - 18f);
        Actor pause = new PauseButton();
        pause.setPosition(BoardGeometry.VIRTUAL_W - 56f, BoardGeometry.VIRTUAL_H - 26f);
        addActor(pause);
    }

    /** 每帧刷新（值变化才重建字符串）：轮次 / 金币 / 等级+经验（① 区完整版） */
    public void refresh(RunContext ctx) {
        String text = statusText(ctx.getRunState().getRound(), ctx.getPlayer().getGold(),
                ctx.getPlayer().getLevel(), ctx.getPlayer().getCurrentExp());
        if (!text.equals(lastText)) {
            lastText = text;
            label.setText(text);
        }
    }

    /** ① 区文案数据源（纯函数；expToNextLevel(Lv.7)=0 → 满级；术语见计划 §2.1） */
    static String statusText(int round, int gold, int level, int currentExp) {
        int need = GameBalance.expToNextLevel(level);
        String exp = need == 0 ? "满级" : currentExp + "/" + need;
        return "轮次 " + round + "/" + GameBalance.TOTAL_ROUNDS
                + "  金币 " + gold
                + "  等级 " + level + "（" + exp + "）";
    }

    /** 暂停按钮（自绘，无 Skin——Q4=B） */
    private final class PauseButton extends Actor {
        PauseButton() {
            setSize(48f, 22f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    pauseListener.onPauseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.35f, 0.4f, 0.5f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "II", getX() + 19f, getY() + 15f);
        }
    }
}
