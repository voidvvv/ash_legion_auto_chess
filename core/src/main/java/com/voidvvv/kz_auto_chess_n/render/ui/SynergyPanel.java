package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.systems.SynergySystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ⑤ 羁绊面板（render §九；全程可见，BATTLE 期置灰 alpha 0.35——差异声明 #8）。
 * 品质改造 §1.5a 三态：<b>零部署</b>「上阵棋子后 / 查看羁绊」、<b>有部署无激活</b>
 * 「未激活羁绊 / 同种族或职业 / 凑到档位激活」、<b>激活</b>照现有列表（空态无「-」）；
 * 空态底 #22252B α0.42、副文 #B0B9BE 行距 14px；战时淡化规则沿用。
 * 备战期 = 按已上场名单的达档预演（SynergySystem.resolve 零改复用，WARNING-4：不计备战席同名，
 * 同名棋子计数口径保持）；每帧 resolve 有小量分配（WARNING-5：名单 ≤27、60fps 可忽略）。
 */
public final class SynergyPanel extends Group {

    private static final int MAX_LINES = 8;

    /** 空态主文 / 副文（§1.5a 原文） */
    static final String EMPTY_ZERO_MAIN = "上阵棋子后";
    static final String EMPTY_ZERO_SUB = "查看羁绊";
    static final String EMPTY_NONE_MAIN = "未激活羁绊";
    static final String EMPTY_NONE_SUB_1 = "同种族或职业";
    static final String EMPTY_NONE_SUB_2 = "凑到档位激活";
    /** 空态底色（#22252B α0.42） */
    private static final Color EMPTY_BG = new Color(0.133f, 0.145f, 0.169f, 0.42f);
    /** 空态副文（#B0B9BE） */
    private static final Color EMPTY_SUB_TINT = new Color(0.690f, 0.725f, 0.745f, 1f);
    /** 空态行距（§1.5a：副文 14px） */
    private static final float EMPTY_LINE_HEIGHT = 14f;

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final SynergySystem synergySystem = new SynergySystem();
    private List<String> lastLines = new ArrayList<String>();
    private boolean lastEmpty;

    public SynergyPanel(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
        setPosition(BoardGeometry.SYNERGY_X, BoardGeometry.SYNERGY_Y);
        setSize(BoardGeometry.SYNERGY_W, BoardGeometry.SYNERGY_H);
    }

    /** 每帧刷新（行变更才重建列表——渲染段零额外分配） */
    public void refresh(RunContext ctx) {
        this.lastLines = previewLines(ctx, synergySystem);
        this.lastEmpty = isEmptyState(lastLines);
    }

    /** 空态判定（激活列表非空 = 非空态；§1.5a 空态无「-」占位） */
    static boolean isEmptyState(List<String> lines) {
        return lines.isEmpty() || EMPTY_NONE_MAIN.equals(lines.get(0))
                || EMPTY_ZERO_MAIN.equals(lines.get(0));
    }

    /** 面板行装配（纯函数；§1.5a 三态）：激活 = 名称 (档位)；零部署 / 有部署无激活 各自空态文案 */
    static List<String> previewLines(RunContext ctx, SynergySystem synergySystem) {
        List<UnitData> templates = new ArrayList<UnitData>();
        for (Unit unit : ctx.getPlayer().getDeployedUnits()) {
            templates.add(unit.getTemplate());
        }
        SynergySnapshot snapshot = synergySystem.resolve(templates, ctx.getGameData());
        List<String> lines = new ArrayList<String>();
        for (SynergySnapshot.ActiveSynergy active : snapshot.getActives()) {
            lines.add(active.getName() + " (" + active.getThresholdCount() + ")");
        }
        if (!lines.isEmpty()) {
            return lines;
        }
        if (ctx.getPlayer().getDeployedUnits().isEmpty()) {
            lines.add(EMPTY_ZERO_MAIN); // 零部署：先上阵再谈羁绊
            lines.add(EMPTY_ZERO_SUB);
            return lines;
        }
        lines.add(EMPTY_NONE_MAIN); // 有部署但未达档：给出凑档方向
        lines.add(EMPTY_NONE_SUB_1);
        lines.add(EMPTY_NONE_SUB_2);
        return lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha; // 战时淡化规则沿用（§1.5a）
        Color old = batch.getColor();
        batch.setColor(lastEmpty ? EMPTY_BG.r : 0.2f, lastEmpty ? EMPTY_BG.g : 0.19f,
                lastEmpty ? EMPTY_BG.b : 0.24f,
                (lastEmpty ? EMPTY_BG.a : 0.85f) * alpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
        batch.setColor(old);
        float y = getY() + getHeight() - 14f;
        assets.font().draw(batch, "羁绊", getX() + 8f, y); // 标题（§1.5a：三态共用标题）
        y -= 18f;
        for (int i = 0; i < lastLines.size() && i < MAX_LINES; i++) {
            if (lastEmpty && i > 0) {
                assets.font().setColor(EMPTY_SUB_TINT.r, EMPTY_SUB_TINT.g, EMPTY_SUB_TINT.b, alpha);
            }
            assets.font().draw(batch, lastLines.get(i), getX() + 8f, y);
            assets.font().setColor(Color.WHITE);
            y -= lastEmpty ? EMPTY_LINE_HEIGHT : 16f; // 空态副文行距 14px（§1.5a）
        }
    }
}
