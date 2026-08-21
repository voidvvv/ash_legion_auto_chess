package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 半随机波次生成（GDD §7.3 / §10.2；无状态实例类）。
 *
 * <p>算法固定序（每步确定性）：人口锚点 n 与强度系数 k 取自 GameBalance；
 * 可用池 = enemyPool 过滤 minRound ≤ round（声明序）；杂兵按权重有放回抽取
 * （允许同名重复，口径 #3，RNG 消耗 = 杂兵人数，Boss 为确定性映射不消耗）；
 * 规则式布阵（Q4）：近战目标排 [2,1,0]（第 2 行邻缓冲带先接敌）、远程 [0,1,2]，
 * 行内列序 [2,3,1,4,0,5]（中央向外）；Boss 轮额外追加 1 个 Boss 殿后（scale=1.0，Q3）。
 */
public final class WaveGenerator {
    /** 近战（range ≤ 1）目标排序列：先贴缓冲带（第 2 行），放满向敌方纵深退 */
    private static final int[] MELEE_ROW_ORDER = {2, 1, 0};
    /** 远程（range ≥ 2）目标排序列：先占敌方纵深（第 0 行） */
    private static final int[] RANGED_ROW_ORDER = {0, 1, 2};
    /** 行内列序：6 列中央向外 */
    private static final int[] COLUMN_ORDER = {2, 3, 1, 4, 0, 5};
    /** 敌区行数（GDD §4.1：棋盘 6×7，敌区 0~2 行，缓冲带第 3 行） */
    private static final int ENEMY_ZONE_ROWS = 3;

    /**
     * 生成第 round 轮敌阵（sceneId 场景）。
     *
     * @return 不可变列表：杂兵按抽取序在前，Boss 殿后——列表序即确定性序
     * @throws IllegalArgumentException 场景不存在
     * @throws IllegalStateException    可用池为空或敌区放满（防御，理论不可达）
     */
    public List<WaveSpec> generateEnemyWave(int round, String sceneId,
                                            GameData data, RandomGenerator rng) {
        SceneData scene = data.getScene(sceneId);
        if (scene == null) {
            throw new IllegalArgumentException("场景不存在: " + sceneId);
        }
        int count = GameBalance.enemyCount(round);  // round 合法性由 GameBalance 内聚校验
        float scale = GameBalance.enemyScale(round);

        List<SceneData.EnemyPoolEntry> pool = new ArrayList<SceneData.EnemyPoolEntry>();
        for (SceneData.EnemyPoolEntry entry : scene.getEnemyPool()) {
            if (entry.getMinRound() <= round) {
                pool.add(entry);
            }
        }
        if (pool.isEmpty()) { // 防御：S5 保证第 1 轮非空、后续轮池单调不减
            throw new IllegalStateException("第 " + round + " 轮可用敌池为空: " + sceneId);
        }
        int[] weights = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = pool.get(i).getWeight();
        }

        boolean[][] occupied = new boolean[GameBalance.BOARD_COLS][ENEMY_ZONE_ROWS];
        List<WaveSpec> wave = new ArrayList<WaveSpec>(count + 1);
        for (int i = 0; i < count; i++) {
            UnitData template = data.getUnit(pool.get(rng.weightedPick(weights)).getUnitId());
            int[] cell = place(template, occupied);
            wave.add(new WaveSpec(template, 1, scale, cell[0], cell[1]));
        }
        if (GameBalance.isBossRound(round)) {
            UnitData bossTemplate = data.getUnit(scene.getBossUnitId(round)); // S3 保证非 null
            int[] cell = place(bossTemplate, occupied); // 最后放置，同一规则
            wave.add(new WaveSpec(bossTemplate, 1, 1.0f, cell[0], cell[1]));
        }
        return Collections.unmodifiableList(wave);
    }

    /** 在目标排序列中逐排找"该排按列序第一个空格"；整列序放满进下一排；放满无位抛错（防御） */
    private static int[] place(UnitData template, boolean[][] occupied) {
        int[] rowOrder = template.getBaseStats().getRange() >= 2 ? RANGED_ROW_ORDER : MELEE_ROW_ORDER;
        for (int y : rowOrder) {
            for (int x : COLUMN_ORDER) {
                if (!occupied[x][y]) {
                    occupied[x][y] = true;
                    return new int[]{x, y};
                }
            }
        }
        throw new IllegalStateException("敌区已放满，无处落位: " + template.getId());
    }
}
