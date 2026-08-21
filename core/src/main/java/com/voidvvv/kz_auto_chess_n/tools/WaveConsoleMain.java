package com.voidvvv.kz_auto_chess_n.tools;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.WaveGenerator;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 控制台波次模拟入口（project_structure §六；Phase 4 后保留为数值调试工具或删除均可）。
 *
 * <p>普通 main，不改动 Main.java（Phase 4 才改造为 Game）。
 * {@link FileHandle} 用纯 JVM 的 File 构造（gdx-files 无 GL，分层允许，project_structure §四例外条款）。
 */
public final class WaveConsoleMain {
    private WaveConsoleMain() {
    }

    /**
     * @param args args[0] = seed（缺省 42）；args[1] = dataDir（缺省 ../assets/data，相对 core/）
     */
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        String dataDir = args.length > 1 ? args[1] : "../assets/data";

        GameData data = JsonLoader.loadFromDirectory(new FileHandle(new File(dataDir)));
        for (String warning : data.getWarnings()) {
            System.out.println("[软告警] " + warning);
        }

        String sceneId = data.getScenes().keySet().iterator().next(); // 首个场景（种子仅森林）
        System.out.println("=== 余烬军团 · 波次模拟（seed=" + seed + ", scene=" + sceneId + "）===");

        WaveGenerator generator = new WaveGenerator();
        RandomGenerator rng = new RandomGenerator(seed); // 单 RNG 贯穿 25 轮
        for (int round = 1; round <= GameBalance.TOTAL_ROUNDS; round++) {
            List<WaveSpec> wave = generator.generateEnemyWave(round, sceneId, data, rng);
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                    "轮 %2d | k=%.1f | 杂兵 %d%s |",
                    round, GameBalance.enemyScale(round), GameBalance.enemyCount(round),
                    GameBalance.isBossRound(round) ? " + Boss" : ""));
            for (WaveSpec spec : wave) {
                line.append(String.format(Locale.ROOT, " (%d,%d) %s%s scale=%.1f",
                        spec.getGridX(), spec.getGridY(), spec.getTemplate().getName(),
                        spec.isBoss() ? "[Boss]" : "", spec.getScale()));
            }
            System.out.println(line);
        }
        System.out.println("=== RNG 消耗合计：" + rng.getConsumedCount() + " 次 ===");
    }
}
