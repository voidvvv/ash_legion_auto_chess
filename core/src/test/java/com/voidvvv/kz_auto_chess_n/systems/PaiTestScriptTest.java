package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.support.PaiDuelDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对拍题表验收件（品质改造 §0.2 单一来源）：固定敌阵 / 固定商店 / 种子公式 / 命名逐项对照设计书。
 * 表内容以 {@link PaiTestScript} 常量为唯一事实源断言（表即测试——改表必须改这里，防止静默漂移）。
 */
@DisplayName("PaiTestScript：对拍题表单一来源（品质改造 §0.2）")
class PaiTestScriptTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = PaiDuelDriver.realData();
    }

    private static List<String> slotIds(List<UnitData> slots) {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (UnitData template : slots) {
            ids.add(template.getId());
        }
        return ids;
    }

    // —— 敌阵表（表序 = 入场序，星级均 1） ——

    @Test
    @DisplayName("敌阵表：轮1 A = 冰霜巨人 (1,1)×1.20 / B = 暗影之刃 (2,2)×2.00")
    void roundOneWavesPerTopic() {
        List<WaveSpec> a = PaiTestScript.enemyWave(PaiTestScript.Topic.A, 1, data);
        assertThat(a).hasSize(1);
        assertThat(a.get(0).getTemplate().getId()).isEqualTo("unit_glacial_giant");
        assertThat(a.get(0).getGridX()).isEqualTo(1);
        assertThat(a.get(0).getGridY()).isEqualTo(1);
        assertThat(a.get(0).getScale()).isEqualTo(1.20f);
        assertThat(a.get(0).getStar()).isEqualTo(1);

        List<WaveSpec> b = PaiTestScript.enemyWave(PaiTestScript.Topic.B, 1, data);
        assertThat(b).hasSize(1);
        assertThat(b.get(0).getTemplate().getId()).isEqualTo("unit_shadow_blade");
        assertThat(b.get(0).getGridX()).isEqualTo(2);
        assertThat(b.get(0).getGridY()).isEqualTo(2);
        assertThat(b.get(0).getScale()).isEqualTo(2.00f);
    }

    @Test
    @DisplayName("敌阵表：轮2、轮3 共用（狼崽 + 轮3 加掷矛猎头者），表序 = 入场序")
    void sharedRoundsTwoAndThree() {
        List<WaveSpec> round2 = PaiTestScript.enemyWave(PaiTestScript.Topic.A, 2, data);
        assertThat(round2).hasSize(1);
        assertThat(round2.get(0).getTemplate().getId()).isEqualTo(PaiTestScript.L);
        assertThat(round2.get(0).getGridX()).isEqualTo(2);
        assertThat(round2.get(0).getGridY()).isEqualTo(2);
        assertThat(round2.get(0).getScale()).isEqualTo(1.00f);

        List<WaveSpec> round3 = PaiTestScript.enemyWave(PaiTestScript.Topic.B, 3, data);
        assertThat(round3).hasSize(2); // 表序 = 入场序
        assertThat(round3.get(0).getTemplate().getId()).isEqualTo(PaiTestScript.L);
        assertThat(round3.get(1).getTemplate().getId()).isEqualTo(PaiTestScript.S);
        assertThat(round3.get(1).getGridX()).isEqualTo(3);
        assertThat(round3.get(1).getGridY()).isEqualTo(0);
    }

    @Test
    @DisplayName("敌阵表越界拒绝：round 0 / 4 抛 IllegalArgumentException（题表仅 1~3 轮）")
    void waveRoundBounds() {
        assertThatThrownBy(() -> PaiTestScript.enemyWave(PaiTestScript.Topic.A, 0, data))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaiTestScript.enemyWave(PaiTestScript.Topic.A, 4, data))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaiTestScript.enemyWave(null, 1, data))
                .isInstanceOf(NullPointerException.class); // 对拍局缺少题号 fail-fast
    }

    // —— 商店表（新轮免费五槽 + 付费刷新序） ——

    @Test
    @DisplayName("免费五槽：第1轮 W W M S P / 第2轮 L P M S W / 第3轮 W P L S M，均 1 费非 Boss")
    void freeSlotTable() {
        assertThat(slotIds(PaiTestScript.freeSlots(1, data)))
                .containsExactly(PaiTestScript.W, PaiTestScript.W, PaiTestScript.M,
                        PaiTestScript.S, PaiTestScript.P);
        assertThat(slotIds(PaiTestScript.freeSlots(2, data)))
                .containsExactly(PaiTestScript.L, PaiTestScript.P, PaiTestScript.M,
                        PaiTestScript.S, PaiTestScript.W);
        assertThat(slotIds(PaiTestScript.freeSlots(3, data)))
                .containsExactly(PaiTestScript.W, PaiTestScript.P, PaiTestScript.L,
                        PaiTestScript.S, PaiTestScript.M);
        for (int round = 1; round <= PaiTestScript.TOTAL_ROUNDS; round++) {
            for (UnitData template : PaiTestScript.freeSlots(round, data)) {
                assertThat(template.getCost()).as("轮 %d 全一费", round).isEqualTo(1);
                assertThat(template.isBoss()).as("轮 %d 非 Boss", round).isFalse();
            }
        }
    }

    @Test
    @DisplayName("付费刷新序：① W L M P S / ② P S L W M / ③ L M W S P；第四次回到 ①（循环取模）")
    void paidSlotTableCycles() {
        assertThat(slotIds(PaiTestScript.paidSlots(0, data)))
                .containsExactly(PaiTestScript.W, PaiTestScript.L, PaiTestScript.M,
                        PaiTestScript.P, PaiTestScript.S);
        assertThat(slotIds(PaiTestScript.paidSlots(1, data)))
                .containsExactly(PaiTestScript.P, PaiTestScript.S, PaiTestScript.L,
                        PaiTestScript.W, PaiTestScript.M);
        assertThat(slotIds(PaiTestScript.paidSlots(2, data)))
                .containsExactly(PaiTestScript.L, PaiTestScript.M, PaiTestScript.W,
                        PaiTestScript.S, PaiTestScript.P);
        assertThat(slotIds(PaiTestScript.paidSlots(3, data)))
                .containsExactlyElementsOf(slotIds(PaiTestScript.paidSlots(0, data))); // ④ 回 ①
        assertThatThrownBy(() -> PaiTestScript.paidSlots(-1, data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // —— 种子公式（仅对拍使用） ——

    @Test
    @DisplayName("种子公式：battle = seed + 1009×(round−1)，chest = battle + 1")
    void seedFormulas() {
        assertThat(PaiTestScript.battleSeed(42L, 1)).isEqualTo(42L);
        assertThat(PaiTestScript.battleSeed(42L, 2)).isEqualTo(1051L);
        assertThat(PaiTestScript.battleSeed(42L, 3)).isEqualTo(2060L);
        assertThat(PaiTestScript.chestSeed(42L, 1)).isEqualTo(43L);
        assertThat(PaiTestScript.chestSeed(42L, 3)).isEqualTo(2061L);
        assertThatThrownBy(() -> PaiTestScript.battleSeed(42L, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // —— 菜单 / 命名 ——

    @Test
    @DisplayName("菜单种子循环：42 → 43 → 44 → 42；不在循环内回 42（防御）")
    void menuSeedCycle() {
        assertThat(PaiTestScript.nextSeed(42)).isEqualTo(43);
        assertThat(PaiTestScript.nextSeed(43)).isEqualTo(44);
        assertThat(PaiTestScript.nextSeed(44)).isEqualTo(42);
        assertThat(PaiTestScript.nextSeed(999)).isEqualTo(42); // 防御分支
    }

    @Test
    @DisplayName("对拍顶栏标题：「对拍 A · 42」（§0.2）")
    void hudTitle() {
        assertThat(PaiTestScript.hudTitle(PaiTestScript.Topic.A, 42L)).isEqualTo("对拍 A · 42");
        assertThat(PaiTestScript.hudTitle(PaiTestScript.Topic.B, 44L)).isEqualTo("对拍 B · 44");
    }

    @Test
    @DisplayName("表引用完整性：免费与付费槽的全部单位在真实 data/ 可解析且为 1 费模板")
    void allTableUnitsResolvable() {
        for (int round = 1; round <= PaiTestScript.TOTAL_ROUNDS; round++) {
            for (UnitData template : PaiTestScript.freeSlots(round, data)) {
                assertThat(data.getUnit(template.getId())).isSameAs(template);
            }
        }
        for (int index = 0; index < PaiTestScript.PAID_CYCLE_LENGTH; index++) {
            for (UnitData template : PaiTestScript.paidSlots(index, data)) {
                assertThat(data.getUnit(template.getId())).isSameAs(template);
            }
        }
    }
}
