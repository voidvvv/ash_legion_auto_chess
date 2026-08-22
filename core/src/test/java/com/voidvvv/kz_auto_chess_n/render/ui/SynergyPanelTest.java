package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.systems.SynergySystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SynergyPanel 预演口径测试（CP24；WARNING-4）：备战期按<b>已上场名单</b>预演达档羁绊
 * ——备战席同名不计；行序 = GameData 声明序；空态占位 "-"。
 * resolve 本体由 SynergySystemTest 背书，此处测面板行拼装的上场口径。
 */
class SynergyPanelTest {

    private static final SynergySystem SYSTEM = new SynergySystem();

    // —— 夹具（同 SynergySystemTest：兽人 RACE / 战士 CLASS，档 2/4/6）——

    private static UnitData unit(String id, String race, String unitClass) {
        return new UnitData(id, "夹具" + id, race, unitClass, 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static SynergyData.Threshold threshold(int count) {
        return new SynergyData.Threshold(count, Arrays.<EffectData>asList(
                new EffectData(StatKey.HP, null, EffectOp.ADD, 100f, EffectTarget.ALLIES)));
    }

    private static GameData data() {
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_orc", new SynergyData("syn_orc", "兽人", SynergySource.RACE, "兽人",
                Arrays.asList(threshold(2), threshold(4), threshold(6))));
        synergies.put("syn_warrior", new SynergyData("syn_warrior", "战士", SynergySource.CLASS, "战士",
                Arrays.asList(threshold(2), threshold(4), threshold(6))));
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                synergies, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());
    }

    private static RunContext context() {
        return new RunContext(new Player(10),
                new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                data(), new RandomGenerator(42L));
    }

    /** 上阵一枚（自动入席再部署；格位按调用序铺开，确定性） */
    private static void deploy(RunContext ctx, UnitData template, int ordinal) {
        Unit unit = new Unit(100 + ordinal, template, 1);
        ctx.getPlayer().addToBench(unit);
        ctx.getPlayer().deploy(unit, ordinal % 6, 4 + ordinal / 6);
    }

    @Test
    @DisplayName("上阵 2 兽人战士 → 兽人 (2) + 战士 (2)（声明序）")
    void deployedPairProducesLines() {
        RunContext ctx = context();
        deploy(ctx, unit("a", "兽人", "战士"), 0);
        deploy(ctx, unit("b", "兽人", "战士"), 1);
        assertThat(SynergyPanel.previewLines(ctx, SYSTEM))
                .containsExactly("兽人 (2)", "战士 (2)");
    }

    @Test
    @DisplayName("WARNING-4 口径：仅备战席的兽人不计——2 兽人在席 → 空态占位")
    void benchOnlyUnitsDoNotCount() {
        RunContext ctx = context();
        ctx.getPlayer().addToBench(new Unit(1, unit("a", "兽人", "战士"), 1));
        ctx.getPlayer().addToBench(new Unit(2, unit("b", "兽人", "战士"), 1));
        assertThat(SynergyPanel.previewLines(ctx, SYSTEM)).containsExactly("-");
    }

    @Test
    @DisplayName("上阵 1 + 席 1：只计上场的 1，未达 2 档 → 空态")
    void mixedDeploymentCountsDeployedOnly() {
        RunContext ctx = context();
        deploy(ctx, unit("a", "兽人", "战士"), 0);
        ctx.getPlayer().addToBench(new Unit(2, unit("b", "兽人", "战士"), 1));
        assertThat(SynergyPanel.previewLines(ctx, SYSTEM)).containsExactly("-");
    }

    @Test
    @DisplayName("档位随上场数达 4：显示 兽人 (4)（替换制最高档）")
    void fourDeployedShowHigherTier() {
        RunContext ctx = context();
        for (int i = 0; i < 4; i++) {
            deploy(ctx, unit("o" + i, "兽人", "游侠"), i); // 游侠无职业羁绊登记
        }
        assertThat(SynergyPanel.previewLines(ctx, SYSTEM)).containsExactly("兽人 (4)");
    }

    @Test
    @DisplayName("空上场 → 空态占位 -")
    void emptyDeploymentShowsPlaceholder() {
        assertThat(SynergyPanel.previewLines(context(), SYSTEM)).containsExactly("-");
    }
}
