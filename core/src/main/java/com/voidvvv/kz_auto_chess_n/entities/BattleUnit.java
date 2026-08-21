package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.systems.StatPipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 战斗实例（architecture §2.1；开战派生、战毕整体丢弃）。
 *
 * <p>身份与基准（第一级，battle §8.1）不可变；战斗态为"受控可变"
 * （architecture §2.4 第三层的显式例外）——全部公开写方法为
 * <b>framework-internal</b>：仅供 systems 包在战斗作用域内调用，
 * UI 层只读（口径 #22，code review 检查项）。
 *
 * <p>有效属性（第二级）走脏标记缓存：状态增删自动打脏，读取时按需重算。
 */
public final class BattleUnit {
    private final int id;
    private final UnitData template;
    private final int star;
    private final Side side;
    private final SkillData skill;
    private final BattleStats baseStats;

    private float currentHp;
    private float energy;                 // 0~100，封顶口径 #5
    private int gridX;
    private int gridY;
    private int targetId = -1;            // -1 = 无
    private float attackTimer;
    private float moveTimer;
    private final List<ActiveStatus> statuses = new ArrayList<ActiveStatus>();
    private BattleStats effectiveStats;
    private boolean statsDirty;
    private boolean cleaned;

    public BattleUnit(int id, UnitData template, int star, Side side,
                      SkillData skill, BattleStats baseStats) {
        this.id = id;
        this.template = Objects.requireNonNull(template, "template 不能为 null");
        this.star = star;
        this.side = Objects.requireNonNull(side, "side 不能为 null");
        this.skill = Objects.requireNonNull(skill, "skill 不能为 null（加载校验保证模板技能存在）");
        this.baseStats = Objects.requireNonNull(baseStats, "baseStats 不能为 null");
        this.currentHp = baseStats.getHp();
        this.effectiveStats = baseStats;
        // 开局即就绪（口径 #4）：计时器初始已满——计时器是冷却不是蓄力
        this.attackTimer = 1f / baseStats.getAttackSpeed();
        this.moveTimer = 1f / baseStats.getMoveSpeed();
    }

    // —— 只读 ——

    public int getId() { return id; }
    public UnitData getTemplate() { return template; }
    public int getStar() { return star; }
    public Side getSide() { return side; }
    public SkillData getSkill() { return skill; }
    public BattleStats getBaseStats() { return baseStats; }
    public float getCurrentHp() { return currentHp; }
    public float getEnergy() { return energy; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getTargetId() { return targetId; }
    public boolean isCleaned() { return cleaned; }

    /** 状态列表（不可变视图；framework-internal 写方法变更） */
    public List<ActiveStatus> getStatuses() {
        return Collections.unmodifiableList(statuses);
    }

    /** 有效属性（第二级，脏则重算；口径 #7 float 全精度） */
    public float getEffective(StatKey key) {
        if (statsDirty) {
            effectiveStats = StatPipeline.deriveEffective(baseStats, StatPipeline.statusModifiers(statuses));
            statsDirty = false;
        }
        return effectiveStats.get(key);
    }

    /** 最大生命 = 有效 HP 键 */
    public float maxHp() {
        return getEffective(StatKey.HP);
    }

    /** 血量比例（索敌 LOWEST_HP 键） */
    public float hpRatio() {
        return currentHp / maxHp();
    }

    /** 是否被控制（STUN 在身——列表内状态均为未到期） */
    public boolean hasControl() {
        for (ActiveStatus status : statuses) {
            if (status.getType() == StatusType.STUN) {
                return true;
            }
        }
        return false;
    }

    /** 活着 = 未清扫（濒死未清扫亦"活"，H 语义延迟清扫的前提） */
    public boolean isAlive() {
        return !cleaned;
    }

    /** 攻击间隔（秒）：1 / 有效攻速 */
    public float attackInterval() {
        return 1f / getEffective(StatKey.ATTACK_SPEED);
    }

    /** 攻击计时器当前值（只读观察，测试与调试用） */
    public float getAttackTimer() {
        return attackTimer;
    }

    /** 移动计时器当前值（只读观察，测试与调试用） */
    public float getMoveTimer() {
        return moveTimer;
    }

    /** 走一格冷却（秒）：1 / 有效移速（格/秒） */
    public float moveCooldown() {
        return 1f / getEffective(StatKey.MOVE_SPEED);
    }

    // —— framework-internal 写方法（口径 #22） ——

    /** framework-internal：落格（BattleState.placeUnit 同步调用） */
    public void setPosition(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    /** framework-internal */
    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    /** framework-internal：每 tick 恒累计（与射程无关，口径 #4） */
    public void advanceTimers(float dt) {
        attackTimer += dt;
        moveTimer += dt;
    }

    /** framework-internal：攻击计时器是否就绪 */
    public boolean canActOnAttackTimer() {
        return attackTimer >= attackInterval();
    }

    /** framework-internal：移动计时器是否就绪 */
    public boolean canActOnMoveTimer() {
        return moveTimer >= moveCooldown();
    }

    /** framework-internal：出手消耗并结转余数（口径 #3/#4）。取模语义：仅结转不足一周期的余数——
     *  被控制期间积压的整周期不折算成解除后的连发（防眩晕报复性爆发） */
    public void consumeAttackTimer() {
        attackTimer %= attackInterval();
    }

    /** framework-internal：走步消耗并结转余数（取模语义，同 consumeAttackTimer） */
    public void consumeMoveTimer() {
        moveTimer %= moveCooldown();
    }

    /** framework-internal：HP 变动，双向钳制 [0, maxHp]（溢出作废） */
    public void modifyHp(float delta) {
        currentHp = Math.max(0f, Math.min(maxHp(), currentHp + delta));
    }

    /** framework-internal：能量直设（施放清零），封顶 100 不上溢（口径 #5） */
    public void setEnergy(float value) {
        energy = clampEnergy(value);
    }

    /** framework-internal：能量增减（回能入口），封顶 100 不上溢 */
    public void modifyEnergy(float delta) {
        energy = clampEnergy(energy + delta);
    }

    /** framework-internal：挂状态（自动打脏标记；同 type 合并语义归 StatusSystem） */
    public void addStatus(ActiveStatus status) {
        statuses.add(Objects.requireNonNull(status, "status 不能为 null"));
        invalidateStats();
    }

    /** framework-internal：移除状态（自动打脏标记） */
    public void removeStatus(ActiveStatus status) {
        statuses.remove(status);
        invalidateStats();
    }

    /** framework-internal：显式打脏（SHIELD 消耗至零等属性外变更后调用） */
    public void invalidateStats() {
        statsDirty = true;
    }

    /** framework-internal：清扫（死亡标记）——状态销毁、目标失效 */
    public void markCleaned() {
        cleaned = true;
        statuses.clear();
        targetId = -1;
    }

    private static float clampEnergy(float value) {
        return Math.max(0f, Math.min(GameBalance.ENERGY_MAX, value));
    }
}
