package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.systems.SynergySystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * ⑤ 羁绊面板（render §九；全程可见，BATTLE 期置灰 alpha 0.35——差异声明 #8）。
 * 品质改造 §1.5a 三态：<b>零部署</b>「上阵棋子后 / 查看羁绊」、<b>有部署无激活</b>
 * 「未激活羁绊 / 同种族或职业 / 凑到档位激活」、<b>激活</b>照现有列表（空态无「-」）；
 * 空态底 #22252B α0.42、副文 #B0B9BE 行距 14px；战时淡化规则沿用。
 *
 * <p><b>行来源</b>（品质改造批7）：备战期 = 按已上场名单的达档预演（SynergySystem.resolve 零改复用，
 * WARNING-4：不计备战席同名）；<b>战斗期 = {@link BattleState#getPlayerSynergies} 开战快照</b>
 * （按 race/class 与实际阈值判档的权威口径——不从累计属性快照猜效果）。
 *
 * <p><b>行级悬停 tooltip</b>（批7）：每行常驻一个不可见 hit target Actor（行 ↔ 实例稳定对应），
 * 悬停显示羁绊名、完整生效效果（档位全量效果，自 SynergyData 阈值结构生成）、当前上阵数与生效档、
 * 下一档门槛；tooltip 钳制在 FitViewport 安全区（640×360 内缩 2px），指针移开/行变化/模态/结算期即时消失。
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
    /** 空态行距（§1.5a：宋体 UI 行高 14.5px；面板行与标题同族） */
    private static final float EMPTY_LINE_HEIGHT = Assets.UI_LINE_HEIGHT;
    /** 激活行行距（同上） */
    private static final float ROW_LINE_HEIGHT = Assets.UI_LINE_HEIGHT;
    /** 标题占位（首行顶 = 面板高 − 14 标题 − 18 间距） */
    private static final float TITLE_ZONE = 32f;

    // —— tooltip（批7）——
    /** tooltip 宽（与面板同宽；面板在屏幕右缘，tooltip 置于面板左侧） */
    private static final float TOOLTIP_W = 112f;
    private static final float TOOLTIP_PAD = 4f;
    private static final Color TOOLTIP_TINT = new Color(0.07f, 0.06f, 0.11f, 0.94f);
    /** 安全区内缩（FitViewport 640×360 内 2px） */
    private static final float SAFE_MARGIN = 2f;

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final SynergySystem synergySystem = new SynergySystem();
    private List<String> lastLines = new ArrayList<String>();
    /** 激活行同步的羁绊 id（与 lastLines 同长段对应；空态为空表——图标接线，第二阶段） */
    private List<String> lastIds = new ArrayList<String>();
    /** 激活行原始条目（tooltip 数据源：战时 = BattleState 快照 / 备战 = resolve 预演） */
    private List<SynergySnapshot.ActiveSynergy> activeRows = Collections.emptyList();
    /** 上场模板（tooltip 的上阵计数口径，与行来源同帧刷新） */
    private List<UnitData> deployedTemplates = Collections.emptyList();
    private boolean lastEmpty;
    /** 行内容版本（lastIds 变化时 +1——tooltip 重建判据） */
    private int contentVersion;

    // —— 悬停 / tooltip 状态 ——
    private final RowHit[] rowHits = new RowHit[MAX_LINES];
    private BattleState displayedBattle;
    private boolean hoverSuppressed;
    private List<String> tooltipLines = Collections.emptyList();
    private int builtRow = -1;
    private int builtVersion = -1;
    private float tooltipX;
    private float tooltipTop;
    private final Color savedFontColor = new Color();
    private final Color savedBatchColor = new Color();
    private final Vector2 pointer = new Vector2();
    private final Vector2 panelPointer = new Vector2();
    private final GlyphLayout measure = new GlyphLayout();
    private float tooltipLineHeight;

    public SynergyPanel(Assets assets, Supplier<RunContext> context) {
        this.assets = assets;
        this.context = context;
        setPosition(BoardGeometry.SYNERGY_X, BoardGeometry.SYNERGY_Y);
        setSize(BoardGeometry.SYNERGY_W, BoardGeometry.SYNERGY_H);
        setTouchable(Touchable.childrenOnly); // 命中只落在行 hit target（面板底不吞点击）
        for (int i = 0; i < MAX_LINES; i++) {
            rowHits[i] = new RowHit(i);
            addActor(rowHits[i]);
        }
    }

    /** 每帧刷新（行变更才重建列表——渲染段零额外分配；激活行同步收集羁绊 id 供图标接线） */
    public void refresh(RunContext ctx) {
        refresh(ctx, false);
    }

    /**
     * 每帧刷新（批7 悬停口径）。
     *
     * @param hoverSuppressed 悬停抑制位（BattleScreen frozen = 暂停 / 弹窗模态；结算/终局相位于此内部判定）
     */
    public void refresh(RunContext ctx, boolean hoverSuppressed) {
        List<UnitData> templates = new ArrayList<UnitData>();
        GamePhase phase = ctx.getRunState().getPhase();
        BattleState battle = phase == GamePhase.BATTLE ? ctx.getBattleState() : null;
        List<SynergySnapshot.ActiveSynergy> actives;
        if (phase == GamePhase.BATTLE) {
            actives = battle != null ? battle.getPlayerSynergies().getActives() : Collections.emptyList();
            if (battle != null) {
                for (BattleUnit unit : battle.getUnits()) {
                    if (unit.getSide() == Side.PLAYER) {
                        templates.add(unit.getTemplate());
                    }
                }
            }
        } else {
            for (Unit unit : ctx.getPlayer().getDeployedUnits()) {
                templates.add(unit.getTemplate());
            }
            actives = synergySystem.resolve(templates, ctx.getGameData(),
                    ctx.getRunState().getModifiers().getSynergyAmp()).getActives();
        }
        List<String> ids = new ArrayList<String>();
        List<String> lines = linesFromActives(actives, ids, templates.isEmpty());
        if (battle != displayedBattle || !actives.equals(activeRows) || !templates.equals(deployedTemplates)) {
            contentVersion++;
        }
        displayedBattle = battle;
        deployedTemplates = templates;
        this.lastIds = ids;
        this.activeRows = actives;
        this.lastLines = lines;
        this.lastEmpty = isEmptyState(lastLines);
        this.hoverSuppressed = hoverSuppressed
                || phase == GamePhase.RESULT || phase == GamePhase.RUN_END; // 结算/终局覆盖期不悬停
        syncRowHits();
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
        return activeLines(ctx, synergySystem.resolve(templates, ctx.getGameData()), null);
    }

    /** 行装配内核（纯函数；outIds 非 null 时与行同步收集羁绊 id——图标接线用，空态不收集） */
    static List<String> activeLines(RunContext ctx, SynergySnapshot snapshot, List<String> outIds) {
        return linesFromActives(ctx, snapshot.getActives(), outIds);
    }

    /** 行装配内核（批7：条目入参化——战时传 BattleState 快照 actives、备战传 resolve 预演 actives） */
    static List<String> linesFromActives(RunContext ctx, List<SynergySnapshot.ActiveSynergy> actives,
                                         List<String> outIds) {
        return linesFromActives(actives, outIds, ctx.getPlayer().getDeployedUnits().isEmpty());
    }

    private static List<String> linesFromActives(List<SynergySnapshot.ActiveSynergy> actives,
                                                List<String> outIds, boolean noDeployment) {
        List<String> lines = new ArrayList<String>();
        for (SynergySnapshot.ActiveSynergy active : actives) {
            lines.add(active.getName() + " (" + active.getThresholdCount() + ")");
            if (outIds != null) {
                outIds.add(active.getSynergyId());
            }
        }
        if (!lines.isEmpty()) {
            return lines;
        }
        if (noDeployment) {
            lines.add(EMPTY_ZERO_MAIN); // 零部署：先上阵再谈羁绊
            lines.add(EMPTY_ZERO_SUB);
            return lines;
        }
        lines.add(EMPTY_NONE_MAIN); // 有部署但未达档：给出凑档方向
        lines.add(EMPTY_NONE_SUB_1);
        lines.add(EMPTY_NONE_SUB_2);
        return lines;
    }

    // —— 行 hit target 与 tooltip（批7）——

    /** 行 hit target 落位（激活行才有；行 ↔ RowHit 实例稳定对应，内容变化只改可见性） */
    private void syncRowHits() {
        for (int i = 0; i < MAX_LINES; i++) {
            float top = getHeight() - TITLE_ZONE - i * ROW_LINE_HEIGHT;
            boolean has = !lastEmpty && i < lastIds.size();
            rowHits[i].setVisible(has);
            if (has) {
                rowHits[i].setBounds(2f, top - 12f, getWidth() - 4f, ROW_LINE_HEIGHT);
            }
        }
    }

    /** tooltip 重建判据与清除（行/内容未变零分配；抑制位/无行 → 即时清空） */
    /** 所有遮挡层更新后，按当前指针和 Stage 层级解析命中。 */
    public void syncTooltip(RunContext ctx, boolean blocked, int[] overlay) {
        int row = -1;
        if (!blocked && !hoverSuppressed && !lastEmpty && isVisible() && getStage() != null) {
            getStage().screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY()));
            stageToLocalCoordinates(panelPointer.set(pointer)).add(getX(), getY());
            boolean covered = overlay != null && panelPointer.x >= overlay[0] && panelPointer.y >= overlay[1]
                    && panelPointer.x < overlay[0] + overlay[2] && panelPointer.y < overlay[1] + overlay[3];
            if (!covered) {
                Actor hit = getStage().hit(pointer.x, pointer.y, true);
                if (hit instanceof RowHit && hit.getParent() == this) {
                    row = ((RowHit) hit).row;
                }
            }
        }
        if (row < 0) {
            tooltipLines = Collections.emptyList();
            builtRow = -1;
            return;
        }
        if (row == builtRow && contentVersion == builtVersion) {
            return; // 同行同内容：沿用
        }
        builtRow = row;
        builtVersion = contentVersion;
        List<String> raw = tooltipLines(ctx.getGameData(), activeRows.get(row), deployedTemplates);
        BitmapFont font = assets.bodyFont();
        tooltipLines = DrawerTextBlock.wrapMeasured(font, measure, raw, TOOLTIP_W - 2 * TOOLTIP_PAD);
        tooltipLineHeight = font.getLineHeight();
        tooltipTop = getY() + getHeight() - TITLE_ZONE - row * ROW_LINE_HEIGHT + 4f;
        float height = tooltipHeight();
        // FitViewport 安全区钳制：不越 640×360（内缩 2px）；超高优先保顶部行可见
        tooltipTop = Math.min(tooltipTop, BoardGeometry.VIRTUAL_H - SAFE_MARGIN);
        if (tooltipTop - height < SAFE_MARGIN) {
            tooltipTop = height + SAFE_MARGIN;
        }
        tooltipX = Math.max(SAFE_MARGIN, getX() - TOOLTIP_W - 6f); // 面板居屏幕右缘：tooltip 置左
    }

    /** tooltip 卡高（纯函数）：上下留白 + 行数 × 行高 */
    private float tooltipHeight() {
        return 2 * TOOLTIP_PAD + tooltipLines.size() * tooltipLineHeight;
    }

    /**
     * tooltip 行集（纯函数，批7）：羁绊名（种族/职业）→ 主题描述 → 上阵数与生效档 →
     * <b>完整生效效果</b>（档位全量，自 {@link SynergyData} 阈值结构生成——不从累计属性快照猜）→ 下一档门槛。
     */
    static List<String> tooltipLines(GameData data, SynergySnapshot.ActiveSynergy active,
                                     List<UnitData> deployed) {
        List<String> lines = new ArrayList<String>();
        SynergyData synergy = data.getSynergy(active.getSynergyId());
        if (synergy == null) {
            lines.add(active.getName());
            lines.add("（未登记羁绊数据）");
            return lines;
        }
        lines.add(synergy.getName() + "（" + sourceLabel(synergy.getSource()) + "）");
        String desc = synergy.getDesc();
        if (desc != null && !desc.isEmpty()) {
            lines.add(DrawerInfoText.synergyDescription(desc));
        }
        lines.add("上阵 " + countDeployed(deployed, synergy) + " 只 · 生效档 " + active.getThresholdCount());
        SynergyData.Threshold tier = tierOf(synergy, active.getThresholdCount());
        List<EffectData> effects = active.getEffects();
        if (effects.isEmpty() && tier != null) {
            effects = tier.getEffects();
        }
        for (EffectData effect : effects) {
            lines.add("生效 " + DrawerInfoText.synergyEffectText(effect));
        }
        SynergyData.Threshold next = nextTier(synergy, active.getThresholdCount());
        if (next != null) {
            lines.add("下一档 " + next.getCount() + "（基础）：" + joinedEffects(next));
        } else {
            lines.add("已达最高档");
        }
        return lines;
    }

    private static String sourceLabel(SynergySource source) {
        return source == SynergySource.RACE ? "种族" : "职业";
    }

    /** 该羁绊 key 的上阵计数（与 SynergySystem 双通道同口径：RACE 比 race、CLASS 比 unitClass） */
    private static int countDeployed(List<UnitData> deployed, SynergyData synergy) {
        int count = 0;
        for (UnitData template : deployed) {
            String value = synergy.getSource() == SynergySource.RACE
                    ? template.getRace() : template.getUnitClass();
            if (synergy.getKey().equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static SynergyData.Threshold tierOf(SynergyData synergy, int count) {
        for (SynergyData.Threshold tier : synergy.getThresholds()) {
            if (tier.getCount() == count) {
                return tier;
            }
        }
        return null;
    }

    private static SynergyData.Threshold nextTier(SynergyData synergy, int count) {
        for (SynergyData.Threshold tier : synergy.getThresholds()) {
            if (tier.getCount() > count) {
                return tier;
            }
        }
        return null;
    }

    private static String joinedEffects(SynergyData.Threshold tier) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tier.getEffects().size(); i++) {
            if (i > 0) {
                sb.append("·");
            }
            sb.append(DrawerInfoText.synergyEffectText(tier.getEffects().get(i)));
        }
        return sb.toString();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha; // 战时淡化规则沿用（§1.5a）
        savedBatchColor.set(batch.getColor());
        batch.setColor(lastEmpty ? EMPTY_BG.r : 0.2f, lastEmpty ? EMPTY_BG.g : 0.19f,
                lastEmpty ? EMPTY_BG.b : 0.24f,
                (lastEmpty ? EMPTY_BG.a : 0.85f) * alpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
        batch.setColor(savedBatchColor);
        BitmapFont font = assets.uiFont();
        savedFontColor.set(font.getColor());
        float y = getY() + getHeight() - 14f;
        font.setColor(1f, 1f, 1f, parentAlpha);
        font.draw(batch, "羁绊", getX() + 8f, y);
        y -= 18f;
        for (int i = 0; i < lastLines.size() && i < MAX_LINES; i++) {
            if (lastEmpty && i > 0) {
                font.setColor(EMPTY_SUB_TINT.r, EMPTY_SUB_TINT.g, EMPTY_SUB_TINT.b, parentAlpha);
            }
            // 原创羁绊图标（16×16；激活行行首，文案右移让位；缺图标行保持原排版）
            TextureRegion icon = (!lastEmpty && i < lastIds.size())
                    ? assets.regionOrNull("icon_" + lastIds.get(i)) : null;
            float textX = getX() + 8f;
            if (icon != null) {
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(icon, getX() + 6f, y - 12f, ROW_LINE_HEIGHT, ROW_LINE_HEIGHT);
                batch.setColor(savedBatchColor);
                textX = getX() + 24f;
            }
            font.draw(batch, lastLines.get(i), textX, y);
            y -= lastEmpty ? EMPTY_LINE_HEIGHT : ROW_LINE_HEIGHT; // 空态副文行距 14px（§1.5a）
        }
        font.setColor(savedFontColor);
        drawTooltip(batch, parentAlpha);
    }

    /** tooltip 绘制（PANEL_9SLICE 底 + 9px 小字；行/指针变化由 refresh 即时刷新/消失） */
    private void drawTooltip(Batch batch, float parentAlpha) {
        if (tooltipLines.isEmpty()) {
            return;
        }
        float height = tooltipHeight();
        batch.setColor(TOOLTIP_TINT.r, TOOLTIP_TINT.g, TOOLTIP_TINT.b, TOOLTIP_TINT.a * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), tooltipX, tooltipTop - height, TOOLTIP_W, height);
        batch.setColor(savedBatchColor);
        BitmapFont font = assets.bodyFont();
        try {
            font.setColor(1f, 1f, 1f, parentAlpha);
            float y = tooltipTop - TOOLTIP_PAD;
            for (String line : tooltipLines) {
                font.draw(batch, line, tooltipX + TOOLTIP_PAD, y);
                y -= tooltipLineHeight;
            }
        } finally {
            font.setColor(savedFontColor);
        }
    }

    /** 行级稳定 hit target（不可见；行 i ↔ 实例 i 固定对应，enter/exit 记悬停行） */
    private static final class RowHit extends Actor {
        private final int row;

        RowHit(int row) {
            this.row = row;
            setTouchable(Touchable.enabled);
        }
    }
}
