package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
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
 * 小窗最近 4 行常驻；L 键（GlobalKeyProcessor）切大窗（最近 200 行，无过滤——WARNING-6）。
 */
public final class NotificationPanel extends Group {

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final NotificationLog log = new NotificationLog();
    private final EventInbox inbox = new EventInbox();
    /** 当前附着战斗（feedback06：事件主体 id → 单位名解析源；与 inbox 生命周期对齐——attach 赋值 / detach 置 null） */
    private BattleState battleState;
    /** 命令行中转队列（onExecuted 回调线程与渲染帧解耦——渲染帧统一消费） */
    private final List<String> pendingCommandLines = new ArrayList<String>();

    public NotificationPanel(Assets assets, Supplier<RunContext> context, CommandManager commandManager) {
        this.assets = assets;
        this.context = context;
        commandManager.addListener(new CommandManager.CommandExecutedListener() {
            @Override
            public void onExecuted(GameCommand command, boolean success) {
                queueLine(NotificationFormat.formatCommand(command)); // 渲染帧统一消费（附队列）
            }
        });
    }

    /** 战斗作用域同步（Screen 观察调用；attach 于战斗创建帧 = 0 事件，无历史回灌）。
     *  同时保留 BattleState 引用供 formatEvent 解析事件主体（feedback06）；detach 置 null 可 GC */
    public void syncBattle(BattleState state) {
        this.battleState = state;
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
    }

    /** 当前附着战斗（未附着 = null；包级实例 getter 供 headless 生命周期测试观察——沿 ShopBar.getHoveredSlot 暴露形态） */
    BattleState currentBattle() {
        return battleState;
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
        inbox.forEachNew(new Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                String line = formatEvent(event, ctx.getGameData(), battleState);
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

    /** 通知行截断列宽上限（feedback06 口径 A2-3）：16 列 = 192px，NOTIFY_X+6 起 → 右缘 218 < 棋盘左缘 224；
     *  覆盖全部现实文案（4 字名 +（敌方）+「 施放 」+ 5 字技能名 ≈ 15.5 列），截断仅极端防御 */
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

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0.05f, 0.05f, 0.08f, 0.7f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE),
                BoardGeometry.NOTIFY_X, BoardGeometry.NOTIFY_Y,
                BoardGeometry.NOTIFY_W, BoardGeometry.NOTIFY_H);
        batch.setColor(old);
        List<String> lines = log.visibleLines();
        float y = BoardGeometry.NOTIFY_Y + BoardGeometry.NOTIFY_H - 8f;
        int count = 0;
        for (int i = lines.size() - 1; i >= 0 && count < NotificationLog.SMALL_WINDOW_LINES; i--, count++) {
            assets.font().draw(batch, lines.get(i), BoardGeometry.NOTIFY_X + 6f, y);
            y -= 12f;
        }
        if (log.isLargeMode()) {
            drawLarge(batch);
        }
    }

    private void drawLarge(Batch batch) {
        batch.setColor(0.05f, 0.05f, 0.08f, 0.88f);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), 320f, 40f, 300f, 250f);
        batch.setColor(Color.WHITE);
        float y = 275f;
        List<String> lines = log.visibleLines();
        for (int i = lines.size() - 1; i >= 0 && y > 50f; i--) {
            assets.font().draw(batch, lines.get(i), 330f, y);
            y -= 12f;
        }
    }
}
