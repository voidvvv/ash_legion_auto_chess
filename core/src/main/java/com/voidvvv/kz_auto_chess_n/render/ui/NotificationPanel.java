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
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
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

    /** 战斗作用域同步（Screen 观察调用；attach 于战斗创建帧 = 0 事件，无历史回灌） */
    public void syncBattle(BattleState state) {
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
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
                String line = formatEvent(event, ctx.getGameData());
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

    /** 战斗事件行（仅 UNIT_DIED/CAST——HIT/HEALED 过噪跳过，口径 #13；技能行显中文名，查表失败回退 id） */
    static String formatEvent(CombatEvent event, GameData data) {
        switch (event.getType()) {
            case UNIT_DIED:
                return "单位倒下";
            case CAST:
                return "技能施放：" + skillName(data, event.getSkillId());
            default:
                return null;
        }
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
