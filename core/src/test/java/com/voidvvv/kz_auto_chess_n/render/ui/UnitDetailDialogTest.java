package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 棋子详情弹窗过期判定测试（CP25）：unitGone 纯函数直测——单位在名单（板/席）内
 * 保留弹窗，被卖出/合并移出后自动收起。弹窗本体绘制/卸下按钮走 lwjgl3 手验
 * （Assets 构造需 GL——计划测试要点给出的静态抽取方案）。
 */
class UnitDetailDialogTest {

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static RunContext context(Player player) {
        GameData empty = new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, SkillData>(),
                new LinkedHashMap<String, SynergyData>(),
                new LinkedHashMap<String, SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
        return new RunContext(player, new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                empty, new RandomGenerator(42L));
    }

    @Test
    @DisplayName("单位在备战席：未过期（弹窗保留）")
    void unitOnBenchKeepsDialog() {
        Player player = new Player(10);
        player.addToBench(new Unit(5, tpl("unit_a"), 1));

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isFalse();
    }

    @Test
    @DisplayName("单位已上棋盘：同样未过期")
    void deployedUnitKeepsDialog() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit); // deploy 前置：先入席再上板
        player.deploy(unit, 3, 4); // 玩家区 y 4~6

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isFalse();
    }

    @Test
    @DisplayName("单位被移出名单（卖出/合并）：过期（弹窗自动收起）")
    void removedUnitExpiresDialog() {
        Player player = new Player(10);
        Unit unit = new Unit(5, tpl("unit_a"), 1);
        player.addToBench(unit);
        player.removeUnit(unit);

        assertThat(UnitDetailDialog.unitGone(context(player), 5)).isTrue();
    }

    @Test
    @DisplayName("不存在的 id（含未打开过的 -1）：过期")
    void unknownIdIsGone() {
        Player player = new Player(10);

        assertThat(UnitDetailDialog.unitGone(context(player), 999)).isTrue();
        assertThat(UnitDetailDialog.unitGone(context(player), -1)).isTrue();
    }
}
