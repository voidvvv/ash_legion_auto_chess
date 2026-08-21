package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一场战斗的全部状态（GDD §10.2 注 / architecture §2.3：棋盘归它）。
 *
 * <p>units 列表构造序 = id 升序（发号序，口径 #16）；grid[x][y] 索引 [列][行]
 * （沿 WaveGenerator 的 occupied[x][y] 先例，GDD §4.4 boardGrid[6][7]）。
 * 写方法为 <b>framework-internal</b>：仅供 systems 包在战斗作用域内调用（口径 #22）。
 */
public final class BattleState {
    private final List<BattleUnit> units;
    private final BattleUnit[][] grid;             // [x][y]，null = 空
    private final List<Projectile> projectiles;
    private final List<CombatEvent> events;        // 追加式
    private final RandomGenerator rng;
    private final SynergySnapshot playerSynergies;
    private final SynergySnapshot enemySynergies;
    private int tick;
    private float elapsed;
    private boolean over;
    private BattleOutcome outcome;

    public BattleState(List<BattleUnit> units, RandomGenerator rng,
                       SynergySnapshot playerSynergies, SynergySnapshot enemySynergies) {
        this.units = Collections.unmodifiableList(new ArrayList<BattleUnit>(units));
        this.grid = new BattleUnit[GameBalance.BOARD_COLS][GameBalance.BOARD_ROWS];
        this.projectiles = new ArrayList<Projectile>();
        this.events = new ArrayList<CombatEvent>();
        this.rng = Objects.requireNonNull(rng, "rng 不能为 null");
        this.playerSynergies = Objects.requireNonNull(playerSynergies, "playerSynergies 不能为 null");
        this.enemySynergies = Objects.requireNonNull(enemySynergies, "enemySynergies 不能为 null");
    }

    // —— 只读查询 ——

    /** 全部单位（构造序 = id 升序，含已清扫——列表终身持有） */
    public List<BattleUnit> getUnits() {
        return units;
    }

    /** 按 id 查单位；不存在返回 null */
    public BattleUnit getUnitById(int id) {
        for (BattleUnit unit : units) {
            if (unit.getId() == id) {
                return unit;
            }
        }
        return null;
    }

    /** 棋盘格占用查询；空格返回 null */
    public BattleUnit unitAt(int gridX, int gridY) {
        return grid[gridX][gridY];
    }

    /** 该侧未清扫单位（id 序） */
    public List<BattleUnit> aliveUnits(Side side) {
        List<BattleUnit> result = new ArrayList<BattleUnit>();
        for (BattleUnit unit : units) {
            if (unit.getSide() == side && unit.isAlive()) {
                result.add(unit);
            }
        }
        return result;
    }

    public int aliveCount(Side side) {
        return aliveUnits(side).size();
    }

    public List<Projectile> getProjectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    /** 事件流（不可变视图，追加式） */
    public List<CombatEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public int getTick() { return tick; }
    public float getElapsed() { return elapsed; }
    public boolean isOver() { return over; }
    public BattleOutcome getOutcome() { return outcome; }
    public RandomGenerator getRng() { return rng; }
    public SynergySnapshot getPlayerSynergies() { return playerSynergies; }
    public SynergySnapshot getEnemySynergies() { return enemySynergies; }

    // —— framework-internal 写方法 ——

    /** framework-internal：落格（占用/越界抛错，坐标同步到单位） */
    public void placeUnit(BattleUnit unit, int gridX, int gridY) {
        Objects.requireNonNull(unit, "unit 不能为 null");
        checkBounds(gridX, gridY);
        if (grid[gridX][gridY] != null) {
            throw new IllegalStateException("格已被占用: (" + gridX + "," + gridY + ")");
        }
        grid[gridX][gridY] = unit;
        unit.setPosition(gridX, gridY);
    }

    /** framework-internal：按单位当前坐标腾格 */
    public void removeFromGrid(BattleUnit unit) {
        grid[unit.getGridX()][unit.getGridY()] = null;
    }

    /** framework-internal：在途弹入列 */
    public void spawnProjectile(Projectile projectile) {
        projectiles.add(Objects.requireNonNull(projectile, "projectile 不能为 null"));
    }

    /** framework-internal：移除在途弹（命中或消散后） */
    public void removeProjectile(Projectile projectile) {
        projectiles.remove(projectile);
    }

    /** framework-internal：推进一个逻辑步的时钟 */
    public void beginTick() {
        tick++;
        elapsed += GameBalance.LOGIC_STEP;
    }

    /** framework-internal：追加事件 */
    public void record(CombatEvent event) {
        events.add(Objects.requireNonNull(event, "event 不能为 null"));
    }

    /** framework-internal：终局（幂等防御：首个结局生效） */
    public void finish(BattleOutcome result) {
        if (over) {
            return;
        }
        this.over = true;
        this.outcome = Objects.requireNonNull(result, "result 不能为 null");
    }

    private static void checkBounds(int gridX, int gridY) {
        if (gridX < 0 || gridX >= GameBalance.BOARD_COLS || gridY < 0 || gridY >= GameBalance.BOARD_ROWS) {
            throw new IllegalArgumentException(
                    "棋盘坐标越界（x 0~" + (GameBalance.BOARD_COLS - 1) + "，y 0~"
                            + (GameBalance.BOARD_ROWS - 1) + "），实际=(" + gridX + "," + gridY + ")");
        }
    }
}
