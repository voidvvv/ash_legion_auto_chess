package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 战前危险预判（品质改造 §1.1）：备战期对两题关键敌技做开局落点预测。
 *
 * <p>纯 Java、零 Gdx、<b>零 RNG、不构造 BattleState、不发号</b>（签名为准：无 rng/idIssuer/
 * BattleState 入参）。入参 = {@code RunState.getEnemyWave()} 的模板/技能/格坐标 +
 * {@code Player.getDeployedUnits()}；输出 = 敌原格、预测目标格、危险格、提示类型。
 * 预判只描述开局——「开局预判，目标会随交战变化」（交战期目标随走位改变，真实命中以事件为准）。
 *
 * <p>识别口径（§1.1）：按敌模板与技能二元组识别，另复核形状/载体与<b>实际生效优先级</b>
 * （specialPriority != null ? specialPriority : defaultPriority）。同类多个敌人取敌阵表首个
 * 符合者；两种同屏各显示一条。平局顺序对齐 {@code BattleSystem.startBattle} 的部署扫描序
 * y↑x↑（{@link Player#getDeployedUnits()} 即扫描序，开局发号序与之同源）。
 */
public final class ThreatPredictor {

    /** 两题识别二元组的模板 id（§1.1） */
    public static final String UNIT_GLACIAL_GIANT = "unit_glacial_giant";
    public static final String UNIT_SHADOW_BLADE = "unit_shadow_blade";
    /** 两题识别二元组的技能 id（FxLayer 敌方琥珀施法闪光同源——§1.1） */
    public static final String SKILL_BREAK_STOMP = "skill_break_stomp";
    public static final String SKILL_EXECUTE = "skill_execute";

    /** 提示类型：A 破阵踏（AOE 十字）/ B 处决（后排单目标） */
    public enum Kind { BREAK_STOMP, EXECUTE }

    /** 部署扫描条目：格位随扫描携带（Unit 本体不存格位——与 {@code BattleSystem.startBattle} 同式） */
    private static final class DeployedUnit {
        final Unit unit;
        final int gridX;
        final int gridY;

        DeployedUnit(Unit unit, int gridX, int gridY) {
            this.unit = unit;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }

    private ThreatPredictor() {
    }

    /** 单条预判（不可变）：敌原格 + 预测目标格 + 危险格 + 类型与 B 的后排括号排 */
    public static final class Preview {
        private final Kind kind;
        private final int enemyGridX;
        private final int enemyGridY;
        private final int targetGridX;
        private final int targetGridY;
        /** 危险格（已裁到合法棋盘、含缓冲带格；A = 目标格 + 十字四邻，B = 单目标格） */
        private final List<int[]> dangerCells;
        /** B 专属：玩家最后一个有棋的排（短虚线括号绘制排）；A / 无部署 = -1 */
        private final int backlineRowY;

        private Preview(Kind kind, int enemyGridX, int enemyGridY,
                        int targetGridX, int targetGridY, List<int[]> dangerCells, int backlineRowY) {
            this.kind = kind;
            this.enemyGridX = enemyGridX;
            this.enemyGridY = enemyGridY;
            this.targetGridX = targetGridX;
            this.targetGridY = targetGridY;
            this.dangerCells = dangerCells;
            this.backlineRowY = backlineRowY;
        }

        public Kind getKind() { return kind; }
        public int getEnemyGridX() { return enemyGridX; }
        public int getEnemyGridY() { return enemyGridY; }

        /** 预测主目标格（-1 = 无部署：只画敌角标与规则行——§1.1） */
        public boolean hasTarget() { return targetGridX >= 0; }
        public int getTargetGridX() { return targetGridX; }
        public int getTargetGridY() { return targetGridY; }

        /** 危险格（不可变；元素 = int[]{x, y}） */
        public List<int[]> getDangerCells() { return dangerCells; }
        public int getBacklineRowY() { return backlineRowY; }
    }

    /**
     * 预判两题（§1.1）：无关键敌人返回空表；两种同屏各一条（A 在前，绘制序与文案序同源）。
     */
    public static List<Preview> predict(GameData data, List<WaveSpec> enemyWave, Player player) {
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(enemyWave, "enemyWave 不能为 null");
        Objects.requireNonNull(player, "player 不能为 null");
        List<Preview> previews = new ArrayList<Preview>(2);
        Preview stomp = firstMatching(data, enemyWave, Kind.BREAK_STOMP, player);
        if (stomp != null) {
            previews.add(stomp);
        }
        Preview execute = firstMatching(data, enemyWave, Kind.EXECUTE, player);
        if (execute != null) {
            previews.add(execute);
        }
        return Collections.unmodifiableList(previews);
    }

    /** 是否两题关键敌方技能（§1.1：施法闪光用敌方琥珀色——BattleRenderer 路由用） */
    public static boolean isKeyedEnemySkill(String skillId) {
        return SKILL_BREAK_STOMP.equals(skillId) || SKILL_EXECUTE.equals(skillId);
    }

    /** 同类多个敌人取敌阵表首个符合者（§1.1）；识别三关 = 模板/技能二元组 + 形状载体 + 生效优先级 */
    private static Preview firstMatching(GameData data, List<WaveSpec> enemyWave, Kind kind,
                                         Player player) {
        for (WaveSpec spec : enemyWave) {
            UnitData template = spec.getTemplate();
            if (!template.getId().equals(unitIdOf(kind))) {
                continue;
            }
            SkillData skill = template.getSkillId() == null ? null : data.getSkill(template.getSkillId());
            if (skill == null || !skill.getId().equals(skillIdOf(kind))) {
                continue;
            }
            if (skill.getShape() != shapeOf(kind) || skill.getDelivery() != deliveryOf(kind)) {
                continue;
            }
            if (effectivePriority(template) != priorityOf(kind)) {
                continue; // B 另查实际生效优先级为 BACKLINE（§1.1）；A 同式复核 NEAREST
            }
            return buildPreview(kind, spec, player);
        }
        return null;
    }

    /** 实际生效优先级（TargetingSystem.findTarget 同式：special 优先，缺省回 default） */
    private static TargetPriority effectivePriority(UnitData template) {
        return template.getSpecialPriority() != null
                ? template.getSpecialPriority() : template.getDefaultPriority();
    }

    private static Preview buildPreview(Kind kind, WaveSpec spec, Player player) {
        List<DeployedUnit> deployed = deployedScan(player); // 扫描序 y↑x↑（开局发号序同源）
        int picked = pickIndex(deployed, priorityOf(kind), spec.getGridX(), spec.getGridY());
        if (picked < 0) {
            return new Preview(kind, spec.getGridX(), spec.getGridY(), -1, -1,
                    Collections.<int[]>emptyList(), -1); // 未上棋：只有敌角标与规则行（§1.1）
        }
        DeployedUnit target = deployed.get(picked);
        int targetX = target.gridX;
        int targetY = target.gridY;
        if (kind == Kind.BREAK_STOMP) {
            return new Preview(kind, spec.getGridX(), spec.getGridY(), targetX, targetY,
                    crossCells(targetX, targetY), -1);
        }
        return new Preview(kind, spec.getGridX(), spec.getGridY(), targetX, targetY,
                Collections.singletonList(new int[]{targetX, targetY}), backlineRowY(deployed));
    }

    /** 部署扫描序（与 {@code BattleSystem.startBattle} 玩家侧派生序完全同式：y↑x↑） */
    private static List<DeployedUnit> deployedScan(Player player) {
        List<DeployedUnit> scan = new ArrayList<DeployedUnit>();
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit != null) {
                    scan.add(new DeployedUnit(unit, x, y));
                }
            }
        }
        return scan;
    }

    /**
     * 开局索敌复现（与 {@code TargetingSystem.findTarget} 平局链同式）：
     * 主键（坐标键）→ 到敌起点曼哈顿距离 → 部署扫描序（先行者保位，即 y↑x↑）。
     * 空名单返回 -1。
     */
    private static int pickIndex(List<DeployedUnit> deployed, TargetPriority priority,
                                 int enemyGridX, int enemyGridY) {
        int best = -1;
        float bestKey = 0f;
        int bestDistance = 0;
        boolean maxWins = priority == TargetPriority.BACKLINE;
        for (int i = 0; i < deployed.size(); i++) {
            DeployedUnit candidate = deployed.get(i);
            float key = TargetingSystem.coordinateSortKey(priority, enemyGridX, enemyGridY,
                    candidate.gridX, candidate.gridY);
            int distance = Math.abs(enemyGridX - candidate.gridX)
                    + Math.abs(enemyGridY - candidate.gridY);
            if (best < 0
                    || (maxWins ? key > bestKey : key < bestKey)
                    || (key == bestKey && distance < bestDistance)) {
                best = i;
                bestKey = key;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A 破阵踏危险格：以目标格为中心、曼哈顿 ≤1 的十字（含中心与四正邻），裁到合法棋盘
     *  （x 0~5、y 0~6——保留缓冲带格 y=3 以展示形状，§1.1） */
    private static List<int[]> crossCells(int centerX, int centerY) {
        int[][] candidates = {
                {centerX, centerY},
                {centerX - 1, centerY}, {centerX + 1, centerY},
                {centerX, centerY - 1}, {centerX, centerY + 1}};
        List<int[]> cells = new ArrayList<int[]>(candidates.length);
        for (int[] cell : candidates) {
            if (cell[0] >= 0 && cell[0] < GameBalance.BOARD_COLS
                    && cell[1] >= 0 && cell[1] < GameBalance.BOARD_ROWS) {
                cells.add(new int[]{cell[0], cell[1]});
            }
        }
        return Collections.unmodifiableList(cells);
    }

    /** B 括号排：玩家最后一个有棋的排（y 最大；部署名单空时 -1） */
    private static int backlineRowY(List<DeployedUnit> deployed) {
        int max = -1;
        for (DeployedUnit entry : deployed) {
            max = Math.max(max, entry.gridY);
        }
        return max;
    }

    private static String unitIdOf(Kind kind) {
        return kind == Kind.BREAK_STOMP ? UNIT_GLACIAL_GIANT : UNIT_SHADOW_BLADE;
    }

    private static String skillIdOf(Kind kind) {
        return kind == Kind.BREAK_STOMP ? SKILL_BREAK_STOMP : SKILL_EXECUTE;
    }

    private static SkillShape shapeOf(Kind kind) {
        return kind == Kind.BREAK_STOMP ? SkillShape.AOE_1 : SkillShape.SINGLE_TARGET;
    }

    private static Delivery deliveryOf(Kind kind) {
        return kind == Kind.BREAK_STOMP ? Delivery.MELEE_INSTANT : Delivery.HOMING;
    }

    private static TargetPriority priorityOf(Kind kind) {
        return kind == Kind.BREAK_STOMP ? TargetPriority.NEAREST : TargetPriority.BACKLINE;
    }
}
