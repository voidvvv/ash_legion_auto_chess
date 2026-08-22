package com.voidvvv.kz_auto_chess_n.render.board;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开战入场格匹配（用户反馈 P1c：战斗 rebuild 时 UnitView 起始格取"备战期位置"再滑入锚点）。
 *
 * <p>玩家侧 = 部署表扫描序（y↑x↑，与 BattleSystem 开战派生序同源）；敌方 = 敌阵 WaveSpec 序。
 * BattleUnit id 为开战新发号（不与名单 Unit id 复用，BattleSystem.startBattle 口径），
 * 故按模板 id + 入序消耗匹配；匹配不到返回 null——调用方落战斗锚点（无滑入，无害）。
 * 纯 Java 可测，零 Gdx；只读 entities。
 */
public final class BattleEntryCells {
    private final Map<String, List<int[]>> cellsByTemplate = new LinkedHashMap<String, List<int[]>>();
    private final Map<String, Integer> cursors = new HashMap<String, Integer>();

    private BattleEntryCells() {
    }

    /** 玩家部署表建表（y↑x↑ 扫描序 = 开战发号序） */
    public static BattleEntryCells ofDeployed(Player player) {
        BattleEntryCells entries = new BattleEntryCells();
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit != null) {
                    entries.add(unit.getTemplate().getId(), x, y);
                }
            }
        }
        return entries;
    }

    /** 敌阵 WaveSpec 建表（列表序） */
    public static BattleEntryCells ofEnemyWave(List<WaveSpec> wave) {
        BattleEntryCells entries = new BattleEntryCells();
        for (WaveSpec spec : wave) {
            entries.add(spec.getTemplate().getId(), spec.getGridX(), spec.getGridY());
        }
        return entries;
    }

    /** 同模板按序消耗未用格；耗尽或未知模板返回 null */
    public int[] consume(String templateId) {
        List<int[]> cells = cellsByTemplate.get(templateId);
        Integer cursor = cursors.get(templateId);
        int index = cursor == null ? 0 : cursor.intValue();
        if (cells == null || index >= cells.size()) {
            return null;
        }
        cursors.put(templateId, Integer.valueOf(index + 1));
        return cells.get(index);
    }

    private void add(String templateId, int gridX, int gridY) {
        List<int[]> cells = cellsByTemplate.get(templateId);
        if (cells == null) {
            cells = new ArrayList<int[]>();
            cellsByTemplate.put(templateId, cells);
        }
        cells.add(new int[]{gridX, gridY});
    }
}
