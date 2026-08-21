package com.voidvvv.kz_auto_chess_n.tools;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.IdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.systems.WaveGenerator;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 控制台整场战斗模拟入口（project_structure §六；Phase 4 后保留为数值调试工具或删除均可）。
 *
 * <p>普通 main，不改动 Main.java（Phase 4 才改造为 Game）。
 * {@link FileHandle} 用纯 JVM 的 File 构造（gdx-files 无 GL，分层允许，沿 WaveConsoleMain 先例）。
 * 输出：① 双方阵容表 ② 逐条事件流 ③ 结局 + tick 数 + RNG 消耗合计（= 波次生成 + 暴击 roll）。
 */
public final class BattleConsoleMain {
    /** runToEnd 上限：60s 超时 = 3600 tick，留余量防意外死循环 */
    private static final int MAX_TICKS = 4000;

    private BattleConsoleMain() {
    }

    /**
     * @param args args[0] = seed（缺省 42）；args[1] = round（缺省 5，越过 minRound 门控有杂兵多样性）；
     *             args[2] = dataDir（缺省 ../assets/data，相对 core/）
     */
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int round = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        String dataDir = args.length > 2 ? args[2] : "../assets/data";

        GameData data = JsonLoader.loadFromDirectory(new FileHandle(new File(dataDir)));
        for (String warning : data.getWarnings()) {
            System.out.println("[软告警] " + warning);
        }
        String sceneId = data.getScenes().keySet().iterator().next(); // 首个场景（种子仅森林）

        RandomGenerator rng = new RandomGenerator(seed); // 单 RNG 贯穿：波次生成 → 战斗暴击 roll
        List<WaveSpec> wave = new WaveGenerator().generateEnemyWave(round, sceneId, data, rng);
        int waveRngCost = rng.getConsumedCount();

        Player player = new Player(GameBalance.START_GOLD);
        IdIssuer idIssuer = new SequentialIdIssuer();
        // 固定演示部署：近战行 5（战士/刺客）、远程行 6（游侠），全部 1 星
        buyAndDeploy(player, idIssuer, data, "unit_warrior_01", 2, 5);
        buyAndDeploy(player, idIssuer, data, "unit_assassin_01", 3, 5);
        buyAndDeploy(player, idIssuer, data, "unit_ranger_01", 2, 6);

        BattleSystem battleSystem = new BattleSystem();
        BattleState state = battleSystem.startBattle(player, wave, data, rng, idIssuer);
        battleSystem.runToEnd(state, MAX_TICKS);

        System.out.println("=== 余烬军团 · 整场战斗模拟（seed=" + seed + ", round=" + round + ", scene=" + sceneId + "）===");
        printSynergies("玩家羁绊", state.getPlayerSynergies());
        printSynergies("敌方羁绊", state.getEnemySynergies());
        printRoster(state);
        System.out.println("--- 事件流 ---");
        for (CombatEvent event : state.getEvents()) {
            System.out.println(event);
        }
        System.out.println("--- 结局 ---");
        System.out.println(String.format(Locale.ROOT, "outcome=%s | tick=%d | elapsed=%.1fs | 存活 玩家%d/敌方%d",
                state.getOutcome(), state.getTick(), state.getElapsed(),
                state.aliveCount(Side.PLAYER), state.aliveCount(Side.ENEMY)));
        System.out.println("=== RNG 消耗合计：" + rng.getConsumedCount()
                + " 次（波次生成 " + waveRngCost + " + 暴击 roll " + (rng.getConsumedCount() - waveRngCost) + "）===");
    }

    /** 买入（发号）→ 入席 → 部署（买卖经济归 Phase 5，此处直构演示） */
    private static void buyAndDeploy(Player player, IdIssuer idIssuer, GameData data,
                                     String unitId, int gridX, int gridY) {
        Unit unit = new Unit(idIssuer.nextId(), data.getUnit(unitId), 1);
        player.addToBench(unit);
        player.deploy(unit, gridX, gridY);
    }

    private static void printSynergies(String label, SynergySnapshot snapshot) {
        StringBuilder line = new StringBuilder(label + ": ");
        if (snapshot.isEmpty()) {
            line.append("（无）");
        } else {
            for (SynergySnapshot.ActiveSynergy active : snapshot.getActives()) {
                line.append(active.getName()).append('×').append(active.getThresholdCount()).append(' ');
            }
        }
        System.out.println(line.toString().trim());
    }

    private static void printRoster(BattleState state) {
        System.out.println("--- 阵容表 ---");
        for (BattleUnit unit : state.getUnits()) {
            System.out.println(String.format(Locale.ROOT,
                    "id %d | %-4s | %-14s | (%d,%d) | hp %.0f/%.0f atk %.1f armor %.0f aspd %.2f range %.0f ms %.2f | 技能 %s",
                    unit.getId(), unit.getSide() == Side.PLAYER ? "玩家" : "敌方",
                    unit.getTemplate().getName(), unit.getGridX(), unit.getGridY(),
                    unit.getCurrentHp(), unit.getEffective(StatKey.HP),
                    unit.getEffective(StatKey.ATTACK), unit.getEffective(StatKey.ARMOR),
                    unit.getEffective(StatKey.ATTACK_SPEED), unit.getEffective(StatKey.RANGE),
                    unit.getEffective(StatKey.MOVE_SPEED), unit.getSkill().getName()));
        }
    }
}
