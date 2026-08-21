package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

/**
 * ① 区极简顶栏（口径 #21）：轮次 / 金币 / 等级单行——Q3 闭环可读性（round 必须可见）。
 * 完整 TopBar 随 Phase 5。文字变更才 setText（渲染段零分配）。
 */
public final class TopBar extends Group {
    private final Label label;
    private String lastText = "";

    public TopBar(Assets assets) {
        this.label = new Label("", new Label.LabelStyle(assets.font(), Color.WHITE));
        addActor(label);
        setPosition(8f, BoardGeometry.VIRTUAL_H - 18f);
    }

    /** 每帧刷新（值变化才重建字符串） */
    public void refresh(RunContext ctx) {
        int round = ctx.getRunState().getRound();
        String text = "ROUND " + round + "/" + GameBalance.TOTAL_ROUNDS
                + "  GOLD " + ctx.getPlayer().getGold()
                + "  LV " + ctx.getPlayer().getLevel();
        if (!text.equals(lastText)) {
            lastText = text;
            label.setText(text);
        }
    }
}
