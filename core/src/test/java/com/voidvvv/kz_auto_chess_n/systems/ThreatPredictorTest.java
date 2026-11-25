package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 战前危险预判规则验收件（品质改造 §1.1，纯 JVM）：识别三关（模板/技能二元组 + 形状载体 +
 * 实际生效优先级）、A 十字裁剪（含缓冲带格）、无部署、同排同距平局、两威胁同屏、同类取首个。
 * 预判零 RNG / 不构造 BattleState / 不发号——以签名为准（无 rng/idIssuer/BattleState 入参）。
 */
@DisplayName("ThreatPredictor：战前危险预判规则（品质改造 §1.1）")
class ThreatPredictorTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    /** 备战夹具：按 {x, y, 模板 id} 直构部署（预判只读布阵，不走购买链路） */
    private static Player playerAt(Object[][] cells) {
        Player player = new Player(10);
        SequentialIdIssuer ids = new SequentialIdIssuer();
        for (Object[] cell : cells) {
            Unit unit = new Unit(ids.nextId(), data.getUnit((String) cell[2]), 1);
            player.addToBench(unit);
            player.deploy(unit, (Integer) cell[0], (Integer) cell[1]);
        }
        return player;
    }

    private static List<String> cellTexts(List<int[]> cells) {
        List<String> result = new ArrayList<String>();
        for (int[] cell : cells) {
            result.add("(" + cell[0] + "," + cell[1] + ")");
        }
        return result;
    }

    /** §1.1 A 题敌阵：冰霜巨人 (x,y) ×1.20（破阵踏） */
    private static List<WaveSpec> giantWave(int gridX, int gridY) {
        return Collections.singletonList(new WaveSpec(
                data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1.20f, gridX, gridY));
    }

    /** §1.1 B 题敌阵：暗影之刃 (2,2) ×2.00（处决） */
    private static List<WaveSpec> bladeWave() {
        return Collections.singletonList(new WaveSpec(
                data.getUnit(ThreatPredictor.UNIT_SHADOW_BLADE), 1, 2.00f, 2, 2));
    }

    @Test
    @DisplayName("A 预判：开局 NEAREST 选中最近部署，十字含中心 + 四正邻（聚拢站位 → 祭司 (1,4)）")
    void breakStompPredictsNearestWithCross() {
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}, {1, 4, PaiTestScript.M},
                {2, 5, PaiTestScript.S}});
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(
                data, giantWave(1, 1), player);
        assertThat(previews).hasSize(1);
        ThreatPredictor.Preview preview = previews.get(0);
        assertThat(preview.getKind()).isEqualTo(ThreatPredictor.Kind.BREAK_STOMP);
        assertThat(preview.getEnemyGridX()).isEqualTo(1);
        assertThat(preview.getEnemyGridY()).isEqualTo(1);
        assertThat(preview.hasTarget()).isTrue();
        // 距离：W(2,4)=4 / M(1,4)=3 / S(2,5)=5 → 最近 = 祭司 (1,4)
        assertThat(preview.getTargetGridX()).isEqualTo(1);
        assertThat(preview.getTargetGridY()).isEqualTo(4);
        // 十字：中心 + 四正邻；含缓冲带格 (1,3) 展示形状（§1.1）
        assertThat(cellTexts(preview.getDangerCells())).containsExactly(
                "(1,4)", "(0,4)", "(2,4)", "(1,3)", "(1,5)");
    }

    @Test
    @DisplayName("A 边缘裁剪：目标贴左列时界外邻格裁掉、缓冲带格保留")
    void crossClippedAtBoardEdge() {
        Player player = playerAt(new Object[][]{{0, 4, PaiTestScript.W}});
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(
                data, giantWave(0, 2), player);
        assertThat(previews).hasSize(1);
        assertThat(cellTexts(previews.get(0).getDangerCells())).containsExactly(
                "(0,4)", "(1,4)", "(0,3)", "(0,5)"); // (-1,4) 界外裁掉；(0,3) 缓冲带保留
    }

    @Test
    @DisplayName("无部署：只有敌原格预判，无目标格与危险格（§1.1 只画敌角标与规则行）")
    void noDeploymentYieldsEnemyMarkOnly() {
        Player player = new Player(10);
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(data, giantWave(1, 1), player);
        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).hasTarget()).isFalse();
        assertThat(previews.get(0).getDangerCells()).isEmpty();
        assertThat(previews.get(0).getBacklineRowY()).isEqualTo(-1);
        assertThat(previews.get(0).getEnemyGridX()).isEqualTo(1);
    }

    @Test
    @DisplayName("同排同距平局：按部署扫描序 y↑x↑ 取先者（§1.1 平局顺序对齐 startBattle）")
    void sameRowSameDistanceTieBreaksByScanOrder() {
        // 敌 (2,2) 对 (1,4) 与 (3,4) 均为距离 3 且同排 → 扫描序先者 (1,4) 胜
        Player player = playerAt(new Object[][]{{1, 4, PaiTestScript.W}, {3, 4, PaiTestScript.M}});
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(
                data, giantWave(2, 2), player);
        assertThat(previews.get(0).getTargetGridX()).isEqualTo(1);
        assertThat(previews.get(0).getTargetGridY()).isEqualTo(4);
    }

    @Test
    @DisplayName("B 预判：实际生效优先级 BACKLINE 取离中线最远者 + 括号排 = 最后有棋的排")
    void executePredictsBacklineAndBracketRow() {
        Player player = playerAt(new Object[][]{{2, 5, PaiTestScript.W}, {2, 6, PaiTestScript.M},
                {2, 4, PaiTestScript.S}}); // §1.1 B 祭司在后
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(data, bladeWave(), player);
        assertThat(previews).hasSize(1);
        ThreatPredictor.Preview preview = previews.get(0);
        assertThat(preview.getKind()).isEqualTo(ThreatPredictor.Kind.EXECUTE);
        assertThat(preview.getTargetGridX()).isEqualTo(2); // |y−3| 最大 = M(2,6)
        assertThat(preview.getTargetGridY()).isEqualTo(6);
        assertThat(preview.getBacklineRowY()).isEqualTo(6);
        assertThat(cellTexts(preview.getDangerCells())).containsExactly("(2,6)"); // 单目标
    }

    @Test
    @DisplayName("两威胁同屏：A、B 各显示一条且 A 在前（§1.1）")
    void bothThreatsProduceTwoPreviews() {
        List<WaveSpec> wave = Arrays.asList(
                new WaveSpec(data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1.20f, 1, 1),
                new WaveSpec(data.getUnit(ThreatPredictor.UNIT_SHADOW_BLADE), 1, 2.00f, 2, 2));
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}, {0, 4, PaiTestScript.M},
                {0, 6, PaiTestScript.S}});
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(data, wave, player);
        assertThat(previews).hasSize(2);
        assertThat(previews.get(0).getKind()).isEqualTo(ThreatPredictor.Kind.BREAK_STOMP);
        assertThat(previews.get(1).getKind()).isEqualTo(ThreatPredictor.Kind.EXECUTE);
        assertThat(previews.get(1).getTargetGridY()).isEqualTo(6); // 分散站位：M(0,6) 最后排
    }

    @Test
    @DisplayName("同类多个敌人：取敌阵表首个符合者（§1.1）")
    void duplicateEnemiesUseFirstWaveEntry() {
        List<WaveSpec> wave = Arrays.asList(
                new WaveSpec(data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1.20f, 1, 1),
                new WaveSpec(data.getUnit(ThreatPredictor.UNIT_GLACIAL_GIANT), 1, 1.20f, 4, 1));
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}});
        List<ThreatPredictor.Preview> previews = ThreatPredictor.predict(data, wave, player);
        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).getEnemyGridX()).isEqualTo(1); // 首个，而非 (4,1)
    }

    @Test
    @DisplayName("非关键敌阵（狼崽等）不产生预判")
    void nonKeyedWaveProducesNoPreview() {
        List<WaveSpec> wave = Collections.singletonList(
                new WaveSpec(data.getUnit(PaiTestScript.L), 1, 1.00f, 2, 2));
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}});
        assertThat(ThreatPredictor.predict(data, wave, player)).isEmpty();
    }

    @Test
    @DisplayName("识别三关之二：模板同名但技能二元组不符 → 不识别（§1.1 按模板与技能识别）")
    void templateWithoutMatchingSkillIsIgnored() {
        UnitData impostor = new UnitData(ThreatPredictor.UNIT_GLACIAL_GIANT, "冒名巨人", "巨人", "战士", 3,
                new BaseStats(170, 26, 18, 0.7f, 1, 0.8f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, ThreatPredictor.SKILL_EXECUTE, false);
        List<WaveSpec> wave = Collections.singletonList(new WaveSpec(impostor, 1, 1.20f, 1, 1));
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}});
        assertThat(ThreatPredictor.predict(data, wave, player)).isEmpty();
    }

    @Test
    @DisplayName("识别三关之三：生效优先级不符（破阵踏配 BACKLINE）→ 不识别（§1.1 另查实际生效优先级）")
    void wrongEffectivePriorityIsIgnored() {
        UnitData misprioritized = new UnitData("unit_glacial_giant_clone", "错序巨人", "巨人", "战士", 3,
                new BaseStats(170, 26, 18, 0.7f, 1, 0.8f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.BACKLINE, TargetPriority.BACKLINE, ThreatPredictor.SKILL_BREAK_STOMP, false);
        List<WaveSpec> wave = Collections.singletonList(new WaveSpec(misprioritized, 1, 1.20f, 1, 1));
        Player player = playerAt(new Object[][]{{2, 4, PaiTestScript.W}});
        assertThat(ThreatPredictor.predict(data, wave, player)).isEmpty();
    }

    @Test
    @DisplayName("isKeyedEnemySkill：仅两题关键技为真（FxLayer 敌方琥珀闪光路由）")
    void keyedEnemySkillPredicate() {
        assertThat(ThreatPredictor.isKeyedEnemySkill(ThreatPredictor.SKILL_BREAK_STOMP)).isTrue();
        assertThat(ThreatPredictor.isKeyedEnemySkill(ThreatPredictor.SKILL_EXECUTE)).isTrue();
        assertThat(ThreatPredictor.isKeyedEnemySkill(null)).isFalse();
        assertThat(ThreatPredictor.isKeyedEnemySkill("skill_other")).isFalse();
    }

    @Test
    @DisplayName("坐标排序键抽取：NEAREST = 曼哈顿距离 / BACKLINE = |y−3|，与 findTarget 主键同源")
    void coordinateSortKeyMatchesTargetingSystem() {
        assertThat(TargetingSystem.coordinateSortKey(TargetPriority.NEAREST, 1, 1, 2, 4)).isEqualTo(4f);
        assertThat(TargetingSystem.coordinateSortKey(TargetPriority.BACKLINE, 2, 2, 2, 6)).isEqualTo(3f);
        assertThat(TargetingSystem.coordinateSortKey(TargetPriority.BACKLINE, 2, 2, 2, 4)).isEqualTo(1f);
        assertThatThrownBy(() -> TargetingSystem.coordinateSortKey(
                TargetPriority.LOWEST_HP, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class); // 预判仅支持坐标类优先级（fail-fast）
    }
}
