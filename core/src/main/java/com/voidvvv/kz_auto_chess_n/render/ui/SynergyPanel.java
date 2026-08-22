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
 * 备战期 = 按已上场名单的达档预演（SynergySystem.resolve 零改复用，WARNING-4：不计备战席同名）；
 * 战斗期 = 同一产物（开战时部署名单已冻结，数值一致）。
 * 每帧 resolve 有小量分配（WARNING-5：名单 ≤27、60fps 可忽略；版本号缓存留后）。
 */
public final class SynergyPanel extends Group {

    private static final int MAX_LINES = 8;

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final SynergySystem synergySystem = new SynergySystem();
    private List<String> lastLines = new ArrayList<String>();

    public SynergyPanel(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
        setPosition(BoardGeometry.SYNERGY_X, BoardGeometry.SYNERGY_Y);
        setSize(BoardGeometry.SYNERGY_W, BoardGeometry.SYNERGY_H);
    }

    /** 每帧刷新（行变更才重建列表——渲染段零额外分配） */
    public void refresh(RunContext ctx) {
        this.lastLines = previewLines(ctx, synergySystem);
    }

    /** 预演行拼装（纯函数）：上场名单 → resolve → "名称 (档位数)"；空态占位 "-" */
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
        if (lines.isEmpty()) {
            lines.add("-"); // 空态占位
        }
        return lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha;
        Color old = batch.getColor();
        batch.setColor(0.2f, 0.19f, 0.24f, 0.85f * alpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
        batch.setColor(old);
        float y = getY() + getHeight() - 14f;
        for (int i = 0; i < lastLines.size() && i < MAX_LINES; i++) {
            assets.font().draw(batch, lastLines.get(i), getX() + 8f, y);
            y -= 16f;
        }
    }
}
