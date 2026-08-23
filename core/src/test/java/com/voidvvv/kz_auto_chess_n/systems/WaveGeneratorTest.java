package com.voidvvv.kz_auto_chess_n.systems;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WaveGenerator 用例：人口锚点 / Boss 轮 / 强度系数 / 星级 / minRound 门控 /
 * 规则式布阵 / 坐标合法性 / 确定性（同 seed 全等、异 seed 相异）/ RNG 消耗。
 * 种子场景经 ../assets/data 注入；布阵用单兵池夹具（放置与 RNG 无关，纯规则可精确断言）。
 */
class WaveGeneratorTest {

    private static GameData seedData;
    private static final WaveGenerator GENERATOR = new WaveGenerator();

    @BeforeAll
    static void loadSeedData() {
        seedData = com.voidvvv.kz_auto_chess_n.config.JsonLoader
                .loadFromDirectory(new FileHandle("../assets/data"));
    }

    // —— 夹具：单兵池场景（布阵断言不依赖 RNG）——

    private static GameData singlePoolData(int range) {
        UnitData minion = new UnitData("m", "夹具兵", "兽人", "战士", 1,
                new BaseStats(100, 10, 0, 1f, range, 1f, 0, 100, 0),
                1.8f, TargetPriority.NEAREST, null, "sk", false);
        UnitData boss = new UnitData("b", "夹具Boss", "兽人", "战士", 0,
                new BaseStats(500, 20, 0, 1f, range, 1f, 0, 100, 0),
                1.0f, TargetPriority.NEAREST, null, "sk", true);
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("m", minion);
        units.put("b", boss);

        List<SceneData.EnemyPoolEntry> pool = new ArrayList<SceneData.EnemyPoolEntry>();
        pool.add(new SceneData.EnemyPoolEntry("m", 1, 1));
        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "b");
        bosses.put(15, "b");
        bosses.put(25, "b");
        Map<String, SceneData> scenes = new LinkedHashMap<String, SceneData>();
        scenes.put("sc", new SceneData("sc", "夹具场景", null, pool, bosses));

        return new GameData(units, new LinkedHashMap<>(), new LinkedHashMap<>(), scenes,
                new ArrayList<String>());
    }

    // —— 人口锚点与 Boss 轮 ——

    @Test
    @DisplayName("人口锚点抽检：第 1/3/5/8/12/16/20 轮杂兵 = 1/2/3/4/5/6/7")
    void enemyCountAnchors() {
        int[] rounds = {1, 3, 5, 8, 12, 16, 20};
        int[] expected = {1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < rounds.length; i++) {
            List<WaveSpec> wave = GENERATOR.generateEnemyWave(rounds[i], "scene_forest", seedData,
                    new RandomGenerator(42L));
            assertThat(wave).hasSize(expected[i]); // 非 Boss 轮总数 = 杂兵数
        }
    }

    @Test
    @DisplayName("Boss 轮：总数 = 锚点 + 1，对应 Boss 殿后（7→荆棘之母 / 15→独眼猎神 / 25→真体）")
    void bossRoundsAppendBoss() {
        assertBossRound(7, 4, "boss_thorn_mother");
        assertBossRound(15, 6, "boss_one_eye");
        assertBossRound(25, 8, "boss_thorn_true");
    }

    private static void assertBossRound(int round, int minionCount, String bossId) {
        List<WaveSpec> wave = GENERATOR.generateEnemyWave(round, "scene_forest", seedData,
                new RandomGenerator(42L));
        assertThat(wave).hasSize(minionCount + 1);
        WaveSpec last = wave.get(wave.size() - 1);
        assertThat(last.getTemplate().getId()).isEqualTo(bossId);
        assertThat(last.isBoss()).isTrue();
    }

    // —— 强度系数与星级 ——

    @Test
    @DisplayName("杂兵 scale = k（第 5 轮 1.4 / 第 25 轮 3.4）")
    void minionScaleEqualsK() {
        List<WaveSpec> round5 = GENERATOR.generateEnemyWave(5, "scene_forest", seedData, new RandomGenerator(42L));
        for (WaveSpec spec : round5) {
            assertThat(spec.getScale()).isEqualTo(1.4f);
        }
        List<WaveSpec> round25 = GENERATOR.generateEnemyWave(25, "scene_forest", seedData, new RandomGenerator(42L));
        for (int i = 0; i < 8; i++) { // 前 8 为杂兵
            assertThat(round25.get(i).getScale()).isEqualTo(3.4f);
        }
    }

    @Test
    @DisplayName("Boss scale = 1.0（烘焙终值不乘 k，Q3）")
    void bossScaleOne() {
        for (int round : GameBalance.BOSS_ROUNDS) {
            List<WaveSpec> wave = GENERATOR.generateEnemyWave(round, "scene_forest", seedData,
                    new RandomGenerator(42L));
            assertThat(wave.get(wave.size() - 1).getScale()).isEqualTo(1.0f);
        }
    }

    @Test
    @DisplayName("star 恒 1（全 25 轮所有单位）")
    void starAlwaysOne() {
        for (int round = 1; round <= 25; round++) {
            List<WaveSpec> wave = GENERATOR.generateEnemyWave(round, "scene_forest", seedData,
                    new RandomGenerator(42L));
            for (WaveSpec spec : wave) {
                assertThat(spec.getStar()).isEqualTo(1);
            }
        }
    }

    // —— minRound 门控 ——

    @Test
    @DisplayName("minRound 门控：第 1 轮仅出兽人战士；第 4 轮无刺客")
    void minRoundGating() {
        List<WaveSpec> round1 = GENERATOR.generateEnemyWave(1, "scene_forest", seedData, new RandomGenerator(42L));
        assertThat(round1).extracting(s -> s.getTemplate().getId())
                .containsOnly("unit_warrior_01"); // 游侠 minRound=2、刺客 minRound=5 未解锁

        List<WaveSpec> round4 = GENERATOR.generateEnemyWave(4, "scene_forest", seedData, new RandomGenerator(42L));
        assertThat(round4).extracting(s -> s.getTemplate().getId())
                .doesNotContain("unit_assassin_01") // 刺客 minRound=5
                .containsAnyOf("unit_warrior_01", "unit_ranger_01"); // 池非空
    }

    // —— 布阵规则（单兵池夹具，与 RNG 无关）——

    @Test
    @DisplayName("近战落位：第 8 轮 4 兵全落第 2 行，列序 2,3,1,4（中央向外）")
    void meleeFillsFrontRowFirst() {
        List<WaveSpec> wave = GENERATOR.generateEnemyWave(8, "sc", singlePoolData(1), new RandomGenerator(42L));
        assertThat(wave).extracting(WaveSpec::getGridY).containsExactly(2, 2, 2, 2);
        assertThat(wave).extracting(WaveSpec::getGridX).containsExactly(2, 3, 1, 4);
    }

    @Test
    @DisplayName("远程落位：第 8 轮 4 兵全落第 0 行（敌方纵深），列序 2,3,1,4")
    void rangedFillsBackRowFirst() {
        List<WaveSpec> wave = GENERATOR.generateEnemyWave(8, "sc", singlePoolData(3), new RandomGenerator(42L));
        assertThat(wave).extracting(WaveSpec::getGridY).containsExactly(0, 0, 0, 0);
        assertThat(wave).extracting(WaveSpec::getGridX).containsExactly(2, 3, 1, 4);
    }

    @Test
    @DisplayName("排满换行：第 25 轮 8 杂兵布满第 2 行后转入第 1 行，近战 Boss 殿后接续")
    void overflowToNextRow() {
        List<WaveSpec> wave = GENERATOR.generateEnemyWave(25, "sc", singlePoolData(1), new RandomGenerator(42L));
        assertThat(wave).hasSize(9); // 8 杂兵 + Boss
        assertThat(wave).extracting(s -> s.getGridX() + "," + s.getGridY()).containsExactly(
                "2,2", "3,2", "1,2", "4,2", "0,2", "5,2", // 第 2 行整行（列序 2,3,1,4,0,5）
                "2,1", "3,1",                              // 转入第 1 行
                "1,1");                                    // Boss 殿后接续第 1 行列序
    }

    // —— 坐标合法性与冲突（种子场景全 25 轮）——

    @Test
    @DisplayName("全 25 轮坐标无冲突且 gridY ∈ 0~2、gridX ∈ 0~5")
    void noCoordinateConflictsAllRounds() {
        RandomGenerator rng = new RandomGenerator(42L); // 单 RNG 贯穿，同控制台模拟
        for (int round = 1; round <= 25; round++) {
            List<WaveSpec> wave = GENERATOR.generateEnemyWave(round, "scene_forest", seedData, rng);
            Set<String> cells = new HashSet<String>();
            for (WaveSpec spec : wave) {
                assertThat(spec.getGridX()).isBetween(0, 5);
                assertThat(spec.getGridY()).isBetween(0, 2);
                assertThat(cells.add(spec.getGridX() + "," + spec.getGridY()))
                        .as("第 %d 轮坐标冲突: (%d,%d)", round, spec.getGridX(), spec.getGridY())
                        .isTrue();
            }
        }
    }

    // —— 确定性 ——

    @Test
    @DisplayName("同 seed 两次全 25 轮生成逐位全等（WaveSpec.equals）")
    void sameSeedRunsEqual() {
        List<List<WaveSpec>> runA = allRounds(42L);
        List<List<WaveSpec>> runB = allRounds(42L);
        assertThat(runA).isEqualTo(runB);
    }

    @Test
    @DisplayName("不同 seed（42 vs 43）至少一轮敌阵构成不同")
    void differentSeedsDiffer() {
        List<List<WaveSpec>> runA = allRounds(42L);
        List<List<WaveSpec>> runB = allRounds(43L);
        assertThat(runA).isNotEqualTo(runB);
    }

    private static List<List<WaveSpec>> allRounds(long seed) {
        RandomGenerator rng = new RandomGenerator(seed);
        List<List<WaveSpec>> rounds = new ArrayList<List<WaveSpec>>();
        for (int round = 1; round <= 25; round++) {
            rounds.add(GENERATOR.generateEnemyWave(round, "scene_forest", seedData, rng));
        }
        return rounds;
    }

    // —— RNG 消耗与防御 ——

    @Test
    @DisplayName("RNG 消耗 = 杂兵数（Boss 不消耗）：第 1/5/7 轮 = 1/3/4")
    void rngConsumptionEqualsMinionCount() {
        int[][] cases = {{1, 1}, {5, 3}, {7, 4}};
        for (int[] c : cases) {
            RandomGenerator rng = new RandomGenerator(42L);
            GENERATOR.generateEnemyWave(c[0], "scene_forest", seedData, rng);
            assertThat(rng.getConsumedCount()).isEqualTo(c[1]);
        }
    }

    @Test
    @DisplayName("场景不存在抛 IllegalArgumentException；轮次越界由 GameBalance 校验")
    void unknownSceneThrows() {
        assertThatThrownBy(() -> GENERATOR.generateEnemyWave(1, "nope", seedData, new RandomGenerator(42L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("场景不存在");
        assertThatThrownBy(() -> GENERATOR.generateEnemyWave(0, "scene_forest", seedData, new RandomGenerator(42L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("轮次");
    }

    // —— Phase 6（CP11）：墓穴/雪山场景生成与 minRound 门控 ——

    private static Set<String> waveUnitIds(List<WaveSpec> wave) {
        Set<String> ids = new java.util.LinkedHashSet<String>();
        for (WaveSpec spec : wave) {
            ids.add(spec.getTemplate().getId());
        }
        return ids;
    }

    @Test
    @DisplayName("墓穴 minRound 门控：r1 仅骸骨士兵 / r5 加怨灵 / r10 全池（Boss 轮另加殿后 Boss）")
    void cryptSceneMinRoundGating() {
        Set<String> r1 = waveUnitIds(GENERATOR.generateEnemyWave(1, "scene_crypt", seedData,
                new RandomGenerator(42L)));
        assertThat(r1).containsExactly("unit_skeleton_soldier"); // 唯一 minRound ≤ 1 条目

        Set<String> r5 = waveUnitIds(GENERATOR.generateEnemyWave(5, "scene_crypt", seedData,
                new RandomGenerator(42L)));
        assertThat(r5).isSubsetOf("unit_skeleton_soldier", "unit_wraith"); // 死亡骑士 minRound 10 未到

        List<WaveSpec> r10 = GENERATOR.generateEnemyWave(10, "scene_crypt", seedData,
                new RandomGenerator(42L));
        assertThat(r10).hasSize(GameBalance.enemyCount(10));
        assertThat(waveUnitIds(r10)).isSubsetOf("unit_skeleton_soldier", "unit_wraith", "unit_death_knight");

        List<WaveSpec> r7 = GENERATOR.generateEnemyWave(7, "scene_crypt", seedData,
                new RandomGenerator(42L));
        assertThat(r7.get(r7.size() - 1).getTemplate().getId()).isEqualTo("boss_tomb_colossus"); // Boss 殿后
        assertThat(r7.get(r7.size() - 1).isBoss()).isTrue();
        assertThat(r7.get(r7.size() - 1).getScale()).isEqualTo(1.0f); // Boss 烘焙终值不二次放大
    }

    @Test
    @DisplayName("雪山 minRound 门控：r5 仅小雪怪 / r12 全池；r25 Boss = 星骸守卫")
    void snowSceneMinRoundGating() {
        Set<String> r5 = waveUnitIds(GENERATOR.generateEnemyWave(5, "scene_snow", seedData,
                new RandomGenerator(42L)));
        assertThat(r5).containsExactly("unit_frost_imp"); // 霜巨人 minRound 6 未到

        List<WaveSpec> r12 = GENERATOR.generateEnemyWave(12, "scene_snow", seedData,
                new RandomGenerator(42L));
        assertThat(waveUnitIds(r12)).isSubsetOf("unit_frost_imp", "unit_frost_giant", "unit_glacial_giant");

        List<WaveSpec> r25 = GENERATOR.generateEnemyWave(25, "scene_snow", seedData,
                new RandomGenerator(42L));
        assertThat(r25.get(r25.size() - 1).getTemplate().getId()).isEqualTo("boss_star_warden");
        assertThat(waveUnitIds(GENERATOR.generateEnemyWave(15, "scene_snow", seedData,
                new RandomGenerator(42L)))).contains("boss_star_breaker");
    }
}
