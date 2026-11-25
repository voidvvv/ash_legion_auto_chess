package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.EventInbox;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ⑨ 事件通知（render §5.5）：三流合并（命令 onExecuted + 战斗 CombatEvent + RunState.notices）。
 * 品质改造 §1.5b：缩小态 (20,232,104,58) 四行行高 12、按字宽省略；L 键展开 (20,92,196,188)；
 * 日志存入保留长版本、绘制时按两尺寸各自实测宽度省略；<b>禁触控</b>；隐藏期（抽屉展开 / 结果终局 /
 * 弹窗）只收集不画；展开通知与可见悬停卡矩形重叠时仅暂停绘制、移开恢复、保留 L 键状态。
 *
 * <p>附着守卫（§1.5b）：{@link BattleScope} 以 BattleState 引用身份做守卫——同一实例连续刷新
 * 只前进游标（修同实例逐帧 attach 从头回读）；BattleScreen 先同步战斗引用再刷事件。
 */
public final class NotificationPanel extends Group {

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final NotificationLog log = new NotificationLog();
    private final EventInbox inbox = new EventInbox();
    private final BattleScope scope = new BattleScope();
    /** 命令行中转队列（onExecuted 回调线程与渲染帧解耦——渲染帧统一消费） */
    private final List<String> pendingCommandLines = new ArrayList<String>();
    /** 行宽实测（GlyphLayout 懒建复用——渲染段零分配） */
    private GlyphLayout measureLayout;
    /** 本帧绘制抑制位（Screen 依 §1.5b 规则逐帧写入——收集照常、仅停画） */
    private boolean drawSuppressed;

    public NotificationPanel(Assets assets, Supplier<RunContext> context, CommandManager commandManager) {
        this.assets = assets;
        this.context = context;
        setTouchable(Touchable.disabled); // §1.5b：通知禁触控（透明接收器留 UI 顶层）
        commandManager.addListener(new CommandManager.CommandExecutedListener() {
            @Override
            public void onExecuted(GameCommand command, boolean success) {
                queueLine(NotificationFormat.formatCommand(command)); // 渲染帧统一消费（附队列）
            }
        });
    }

    /**
     * 战斗作用域绑定（品质改造 §1.5b 附着守卫）：以 BattleState 引用身份判定——同一实例连续
     * syncBattle 不再 attach（EventInbox 游标只前进，不从头回读）；引用变化 / 置 null 才重附着。
     * BattleState 引用另供 formatEvent 解析事件主体（feedback06）。
     */
    static final class BattleScope {
        private BattleState bound;
        private final EventInbox inbox = new EventInbox();

        void sync(BattleState state) {
            if (state == bound) {
                return; // 同实例：游标不动（修相同实例逐帧 attach 从头回读）
            }
            bound = state;
            if (state == null) {
                inbox.detach();
            } else {
                inbox.attach(state);
            }
        }

        /** 新事件段消费（未附着 = 空操作） */
        void forEachNew(Consumer<CombatEvent> consumer) {
            inbox.forEachNew(consumer);
        }

        BattleState boundState() {
            return bound;
        }
    }

    /** 战斗作用域同步（Screen 每帧、先于 refresh 调用——§1.5b 修帧序） */
    public void syncBattle(BattleState state) {
        scope.sync(state);
    }

    /** 当前附着战斗（未附着 = null；包级实例 getter 供 headless 生命周期测试观察——沿 ShopBar.getHoveredSlot 暴露形态） */
    BattleState currentBattle() {
        return scope.boundState();
    }

    /** 本帧绘制抑制（Screen 依 §1.5b 规则逐帧写入；收集不受影响——隐藏期继续消费） */
    public void setDrawSuppressed(boolean suppressed) {
        this.drawSuppressed = suppressed;
    }

    /** 便捷重载：取构造注入的上下文（Screen 可免传 ctx） */
    public void refresh() {
        refresh(context.get());
    }

    /** 渲染帧统一消费三流（BattleScreen 每帧调用；三流共享单帧 2 行上限——计数器透传匿名类） */
    public void refresh(RunContext ctx) {
        int[] appended = new int[1];
        for (String notice : ctx.getRunState().drainNotices()) {
            if (log.appendCapped(notice, appended[0])) {
                appended[0]++;
            }
        }
        scope.forEachNew(new Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                String line = formatEvent(event, ctx.getGameData(), scope.boundState());
                if (line != null && log.appendCapped(line, appended[0])) {
                    appended[0]++; // 超限丢弃（§5.5 防刷屏，WARNING-6）
                }
            }
        });
        for (String queued : pendingCommandLines) {
            if (log.appendCapped(queued, appended[0])) {
                appended[0]++;
            }
        }
        pendingCommandLines.clear();
    }

    private void queueLine(String line) {
        if (line != null) {
            pendingCommandLines.add(line);
        }
    }

    /** 通知行长版本列宽上限（feedback06 口径 A2-3）：16 列 = 192px ≥ 展开窗可用宽 184px——
     *  日志存入保留可供展开态使用的长版本，两尺寸各自再按实测宽度省略（§1.5b） */
    static final int NOTIFY_MAX_COLUMNS = 16;

    /** 战斗事件行（仅 UNIT_DIED/CAST——HIT/HEALED 过噪跳过，口径 #13 不扩；feedback06 加主体名：
     *  主体查 BattleState（含已清扫单位，死后仍可查）；技能行显中文名，查表失败回退 id） */
    static String formatEvent(CombatEvent event, GameData data, BattleState battleState) {
        switch (event.getType()) {
            case UNIT_DIED:
                return UnitInfoText.truncateColumns(
                        subjectName(battleState, event.getSourceId()) + " 倒下", NOTIFY_MAX_COLUMNS);
            case CAST:
                return UnitInfoText.truncateColumns(
                        subjectName(battleState, event.getSourceId()) + " 施放 "
                                + skillName(data, event.getSkillId()), NOTIFY_MAX_COLUMNS);
            default:
                return null;
        }
    }

    /** 事件主体名（纯函数）：查 BattleState.getUnitById → 模板中文名；敌方附「（敌方）」标记
     *  （feedback04-2 悬停卡同款字面）；查不到（state 缺失 / id 未登记——防御路径）回退 "#id" */
    static String subjectName(BattleState battleState, int unitId) {
        BattleUnit unit = battleState == null ? null : battleState.getUnitById(unitId);
        if (unit == null) {
            return "#" + unitId;
        }
        return unit.getTemplate().getName() + (unit.getSide() == Side.ENEMY ? "（敌方）" : "");
    }

    /** 技能中文名（GameData 查表；未登记 id 回退原值——防御，正常路径加载期已校验存在） */
    private static String skillName(GameData data, String skillId) {
        SkillData skill = data.getSkill(skillId);
        return skill != null ? skill.getName() : skillId;
    }

    public void toggleLargeMode() {
        log.toggleLargeMode();
    }

    /**
     * §1.5b 隐藏规则（Screen 逐帧判定后经 {@link #setDrawSuppressed} 写入；纯函数可测）：
     * 抽屉展开（进度 &gt; 0）、结果 / 终局相位、弹窗可见 → 只收集不画。
     */
    public static boolean suppressedByLayout(boolean drawerOpen, boolean resultOrEndPhase, boolean modalShowing) {
        return drawerOpen || resultOrEndPhase || modalShowing;
    }

    /** 结果 / 终局相位判定（§1.5b「结果/终局」） */
    public static boolean isResultOrEndPhase(GamePhase phase) {
        return phase == GamePhase.RESULT || phase == GamePhase.RUN_END;
    }

    /**
     * 展开通知与可见悬停卡矩形重叠 → 仅暂停绘制（移开恢复、L 键状态保留；§1.5b）。
     * 小窗 (20,232,104,58) 与两悬停卡锚区（128/508 起）不相交，恒不触发。
     */
    public static boolean overlapsVisibleHoverCard(boolean largeMode, int[] hoverRect) {
        if (!largeMode || hoverRect == null) {
            return false;
        }
        return BoardGeometry.rectsOverlap(BoardGeometry.NOTIFY_LARGE_X, BoardGeometry.NOTIFY_LARGE_Y,
                BoardGeometry.NOTIFY_LARGE_W, BoardGeometry.NOTIFY_LARGE_H,
                hoverRect[0], hoverRect[1], hoverRect[2], hoverRect[3]);
    }

    /** 展开态（Screen 供重叠闸判定；L 键状态保留——§1.5b） */
    public boolean isLargeMode() {
        return log.isLargeMode();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (drawSuppressed) {
            return; // 只收集不画（§1.5b）：隐藏期与悬停卡重叠期，L 键状态保留
        }
        List<String> lines = log.visibleLines();
        if (log.isLargeMode()) {
            drawLarge(batch, lines);
            return; // 展开时收起小窗（§1.5b）
        }
        Color old = batch.getColor();
        batch.setColor(0.05f, 0.05f, 0.08f, 0.7f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE),
                BoardGeometry.NOTIFY_SMALL_X, BoardGeometry.NOTIFY_SMALL_Y,
                BoardGeometry.NOTIFY_SMALL_W, BoardGeometry.NOTIFY_SMALL_H);
        batch.setColor(old);
        float textX = BoardGeometry.NOTIFY_SMALL_X + BoardGeometry.NOTIFY_PADDING_PX;
        float maxWidth = BoardGeometry.NOTIFY_SMALL_W - 2f * BoardGeometry.NOTIFY_PADDING_PX;
        float y = BoardGeometry.NOTIFY_SMALL_Y + BoardGeometry.NOTIFY_SMALL_H
                - BoardGeometry.NOTIFY_PADDING_PX - BoardGeometry.NOTIFY_FIRST_BASELINE_INSET_PX;
        int count = 0;
        for (int i = lines.size() - 1; i >= 0 && count < NotificationLog.SMALL_WINDOW_LINES; i--, count++) {
            assets.bodyFont().draw(batch, ellipsize(lines.get(i), maxWidth), textX, y);
            y -= BoardGeometry.NOTIFY_LINE_HEIGHT;
        }
    }

    /** 展开窗（§1.5b：(20,92,196,188)；按实测宽度省略，容量沿用日志 200 行） */
    private void drawLarge(Batch batch, List<String> lines) {
        Color old = batch.getColor();
        batch.setColor(0.05f, 0.05f, 0.08f, 0.88f);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE),
                BoardGeometry.NOTIFY_LARGE_X, BoardGeometry.NOTIFY_LARGE_Y,
                BoardGeometry.NOTIFY_LARGE_W, BoardGeometry.NOTIFY_LARGE_H);
        batch.setColor(old);
        float textX = BoardGeometry.NOTIFY_LARGE_X + BoardGeometry.NOTIFY_PADDING_PX;
        float maxWidth = BoardGeometry.NOTIFY_LARGE_W - 2f * BoardGeometry.NOTIFY_PADDING_PX;
        float y = BoardGeometry.NOTIFY_LARGE_Y + BoardGeometry.NOTIFY_LARGE_H
                - BoardGeometry.NOTIFY_PADDING_PX - BoardGeometry.NOTIFY_FIRST_BASELINE_INSET_PX;
        for (int i = lines.size() - 1; i >= 0 && y > BoardGeometry.NOTIFY_LARGE_Y
                + BoardGeometry.NOTIFY_PADDING_PX; i--) {
            assets.bodyFont().draw(batch, ellipsize(lines.get(i), maxWidth), textX, y);
            y -= BoardGeometry.NOTIFY_LINE_HEIGHT;
        }
    }

    /** 按当前字体实测宽度省略（§1.5b「两尺寸按当前字体测宽」；GlyphLayout 懒建复用零稳态分配） */
    private String ellipsize(final String line, float maxWidth) {
        if (measureLayout == null) {
            measureLayout = new GlyphLayout();
        }
        return NotificationFormat.ellipsize(line, maxWidth, new NotificationFormat.WidthMeasurer() {
            @Override
            public float widthOf(String text) {
                measureLayout.setText(assets.bodyFont(), text);
                return measureLayout.width;
            }
        });
    }
}
