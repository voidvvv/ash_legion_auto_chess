package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 对拍题表（品质改造 §0.2 单一来源）：固定敌阵 + 固定商店 + 种子公式 + 对拍命名。
 * 游戏对拍分支（{@link RunFlowSystem} / {@link ShopSystem}）与纯 JVM 验收件共用本类——
 * 控制台与测试一律经此取表，不得复制敌阵、商店与种子公式（§0.2 明文）。
 *
 * <p>固定路径全部消耗 0 RNG（§0.2）：敌阵与补货按表直取；随机只发生在交战与胜箱，
 * 分别由 {@link #battleSeed} / {@link #chestSeed} 两个公式给起点（仅对拍使用）。
 * A、B 是两种可独立运行的首战，第 2、3 轮共用敌阵以验证经济。
 */
public final class PaiTestScript {

    /** 对拍题：A 破阵踏（冰霜巨人首战）/ B 处决（暗影之刃首战）——§1.1 战前提示的两题 */
    public enum Topic { A, B }

    /** 对拍总轮数（§0.2 题表行数）：第 3 轮胜箱领完走对拍终局（「对拍完成」） */
    public static final int TOTAL_ROUNDS = 3;
    /** 交战种子步长：battle = seed + 1009×(round−1)；胜箱 = battle + 1（两公式仅对拍使用——§0.2） */
    public static final long BATTLE_SEED_STRIDE = 1009L;
    /** 付费刷新序长度（①②③，第四次回 ①——§0.2） */
    public static final int PAID_CYCLE_LENGTH = 3;
    /** 主菜单种子循环（42 → 43 → 44 → 42，默认 42——§0.2） */
    public static final int[] SEED_CYCLE = {42, 43, 44};

    // —— 商店符号（均为实际 1 费模板——设计书 §0.2）——
    public static final String W = "unit_warrior_01";
    public static final String M = "unit_mage_apprentice";
    public static final String S = "unit_spear_headhunter";
    public static final String P = "unit_boar_rider";
    public static final String L = "unit_wolf_pup";

    private PaiTestScript() {
    }

    // —— 敌阵表 ——

    /** 敌阵行：(列,行) 为零基敌区坐标 + 强度倍率；星级恒 1（WaveSpec 口径） */
    private static final class WaveRow {
        final String unitId;
        final int gridX;
        final int gridY;
        final float scale;

        WaveRow(String unitId, int gridX, int gridY, float scale) {
            this.unitId = unitId;
            this.gridX = gridX;
            this.gridY = gridY;
            this.scale = scale;
        }
    }

    private static WaveRow row(String unitId, int gridX, int gridY, float scale) {
        return new WaveRow(unitId, gridX, gridY, scale);
    }

    private static List<WaveRow> rows(WaveRow... entries) {
        List<WaveRow> result = new ArrayList<WaveRow>(entries.length);
        for (WaveRow entry : entries) {
            result.add(entry);
        }
        return result;
    }

    /** 敌阵表（§0.2）：轮 1 按题分流（A 冰霜巨人 ×1.20 / B 暗影之刃 ×2.00），轮 2、3 共用 */
    private static List<WaveRow> enemyWaveRows(Topic topic, int round) {
        checkRound(round);
        switch (round) {
            case 1:
                return topic == Topic.A
                        ? rows(row("unit_glacial_giant", 1, 1, 1.20f))
                        : rows(row("unit_shadow_blade", 2, 2, 2.00f));
            case 2:
                return rows(row(L, 2, 2, 1.00f));
            default: // 轮 3
                return rows(row(L, 2, 2, 1.00f), row(S, 3, 0, 1.00f));
        }
    }

    /** 固定敌阵（0 RNG——§0.2）：RunFlowSystem.beginRound 对拍分支调用；表序 = 入场序，星级均 1 */
    public static List<WaveSpec> enemyWave(Topic topic, int round, GameData data) {
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(topic, "对拍局缺少题号");
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        for (WaveRow entry : enemyWaveRows(topic, round)) {
            wave.add(new WaveSpec(requireUnit(data, entry.unitId), 1, entry.scale,
                    entry.gridX, entry.gridY));
        }
        return wave;
    }

    // —— 商店表 ——

    /** 新轮免费五槽（§0.2 左列，从左到右）：第 1/2/3 轮 */
    private static final String[][] FREE_SLOTS = {
            {W, W, M, S, P},
            {L, P, M, S, W},
            {W, P, L, S, M},
    };

    /** 付费刷新序（§0.2 右列，全轮共用）：① → ② → ③，第四次回 ① */
    private static final String[][] PAID_SLOTS = {
            {W, L, M, P, S},
            {P, S, L, W, M},
            {L, M, W, S, P},
    };

    /** 新轮免费五槽（0 RNG——§0.2）：ShopSystem.replenish 免费分支调用 */
    public static List<UnitData> freeSlots(int round, GameData data) {
        checkRound(round);
        return slots(FREE_SLOTS[round - 1], data);
    }

    /** 付费刷新槽序（0 RNG——§0.2）：paidIndex 0-based（0 = ①），越界按循环取模（第四次回 ①） */
    public static List<UnitData> paidSlots(int paidIndex, GameData data) {
        if (paidIndex < 0) {
            throw new IllegalArgumentException("对拍付费序号不能为负，实际=" + paidIndex);
        }
        return slots(PAID_SLOTS[paidIndex % PAID_CYCLE_LENGTH], data);
    }

    private static List<UnitData> slots(String[] unitIds, GameData data) {
        List<UnitData> result = new ArrayList<UnitData>(unitIds.length);
        for (String unitId : unitIds) {
            result.add(requireUnit(data, unitId));
        }
        return result;
    }

    // —— 种子公式（仅对拍使用——§0.2） ——

    /** 交战种子：seed + 1009×(round−1)——同轮重试从同一随机起点开始 */
    public static long battleSeed(long seed, int round) {
        checkRound(round);
        return seed + BATTLE_SEED_STRIDE * (round - 1);
    }

    /** 胜箱种子：交战种子 + 1——避免交战多打几下改变装备选项 */
    public static long chestSeed(long seed, int round) {
        return battleSeed(seed, round) + 1L;
    }

    // —— 菜单 / 命名 ——

    /** 菜单种子循环：当前种子的下一个（42 → 43 → 44 → 42）；不在循环内回 42（防御） */
    public static int nextSeed(int current) {
        for (int i = 0; i < SEED_CYCLE.length; i++) {
            if (SEED_CYCLE[i] == current) {
                return SEED_CYCLE[(i + 1) % SEED_CYCLE.length];
            }
        }
        return SEED_CYCLE[0];
    }

    /** 对拍顶栏标题：「对拍 A · 42」（§0.2） */
    public static String hudTitle(Topic topic, long seed) {
        return "对拍 " + topic + " · " + seed;
    }

    private static void checkRound(int round) {
        if (round < 1 || round > TOTAL_ROUNDS) {
            throw new IllegalArgumentException("对拍题表仅 1~" + TOTAL_ROUNDS + " 轮，实际=" + round);
        }
    }

    private static UnitData requireUnit(GameData data, String unitId) {
        UnitData template = data.getUnit(unitId);
        if (template == null) {
            throw new IllegalStateException("对拍题表引用的单位不存在: " + unitId);
        }
        return template;
    }
}
