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
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
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
     *
     * <p>同种子对拍入口（品质改造批0）：以相同 seed/round 跑两次，{@code diff} stdout 即事件流逐位对拍——
     * 输出全确定性：单 RNG 贯穿波次与暴击、单位 id 自 {@link SequentialIdIssuer} 从头发号、部署固定。
     */
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int round = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        String dataDir = args.length > 2 ? args[2] : "../assets/data";
        System.out.println(renderBattle(seed, round, dataDir));
    }

    /**
     * 渲染整场战斗的确定性文本流（品质改造批0：同种子对拍入口）。
     * 以相同 seed/round 调用两次，两次返回值逐位一致——即事件流对拍的实现口径（§0.2）。
     */
    static String renderBattle(long seed, int round, String dataDir) {
        StringBuilder out = new StringBuilder();
        GameData data = JsonLoader.loadFromDirectory(new FileHandle(new File(dataDir)));
        for (String warning : data.getWarnings()) {
            out.append("[软告警] ").append(warning).append('\n');
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
        BattleState state = battleSystem.startBattle(player, wave, data, rng, idIssuer,
                RunModifiers.EMPTY, round); // D10：敌方羁绊档位随轮次截断
        battleSystem.runToEnd(state, MAX_TICKS);

        out.append("=== 余烬军团 · 整场战斗模拟（seed=").append(seed).append(", round=").append(round)
                .append(", scene=").append(sceneId).append("）===\n");
        appendSynergies(out, "玩家羁绊", state.getPlayerSynergies());
        appendSynergies(out, "敌方羁绊", state.getEnemySynergies());
        appendRoster(out, state);
        out.append("--- 事件流 ---\n");
        for (CombatEvent event : state.getEvents()) {
            out.append(event).append('\n');
        }
        out.append("--- 结局 ---\n");
        out.append(String.format(Locale.ROOT, "outcome=%s | tick=%d | elapsed=%.1fs | 存活 玩家%d/敌方%d\n",
                state.getOutcome(), state.getTick(), state.getElapsed(),
                state.aliveCount(Side.PLAYER), state.aliveCount(Side.ENEMY)));
        out.append("=== RNG 消耗合计：").append(rng.getConsumedCount())
                .append(" 次（波次生成 ").append(waveRngCost).append(" + 暴击 roll ")
                .append(rng.getConsumedCount() - waveRngCost).append("）===\n");
        return out.toString();
    }

    /** 买入（发号）→ 入席 → 部署（买卖经济归 Phase 5，此处直构演示） */
    private static void buyAndDeploy(Player player, IdIssuer idIssuer, GameData data,
                                     String unitId, int gridX, int gridY) {
        Unit unit = new Unit(idIssuer.nextId(), data.getUnit(unitId), 1);
        player.addToBench(unit);
        player.deploy(unit, gridX, gridY);
    }

    private static void appendSynergies(StringBuilder out, String label, SynergySnapshot snapshot) {
        StringBuilder line = new StringBuilder(label + ": ");
        if (snapshot.isEmpty()) {
            line.append("（无）");
        } else {
            for (SynergySnapshot.ActiveSynergy active : snapshot.getActives()) {
                line.append(active.getName()).append('×').append(active.getThresholdCount()).append(' ');
            }
        }
        out.append(line.toString().trim()).append('\n');
    }

    private static void appendRoster(StringBuilder out, BattleState state) {
        out.append("--- 阵容表 ---\n");
        for (BattleUnit unit : state.getUnits()) {
            out.append(String.format(Locale.ROOT,
                    "id %d | %-4s | %-14s | (%d,%d) | hp %.0f/%.0f atk %.1f armor %.0f aspd %.2f range %.0f ms %.2f | 技能 %s\n",
                    unit.getId(), unit.getSide() == Side.PLAYER ? "玩家" : "敌方",
                    unit.getTemplate().getName(), unit.getGridX(), unit.getGridY(),
                    unit.getCurrentHp(), unit.getEffective(StatKey.HP),
                    unit.getEffective(StatKey.ATTACK), unit.getEffective(StatKey.ARMOR),
                    unit.getEffective(StatKey.ATTACK_SPEED), unit.getEffective(StatKey.RANGE),
                    unit.getEffective(StatKey.MOVE_SPEED), unit.getSkill().getName()));
        }
    }
}
