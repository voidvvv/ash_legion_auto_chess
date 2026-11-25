package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
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
    /** 命中的机制词（K1：startBattle 按 target 谓词过滤后写一次；批0 惰性数据，批2~4 消费） */
    private List<EffectData> mechanics = Collections.emptyList();
    private boolean banished;
    /** 战斗内计数器（K2 计数通道：遗物/旧疤/普攻计数；变更后需 invalidateStats() 打脏） */
    private final CombatCounters combatCounters = new CombatCounters();

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
        // 开战铺垫后从零蓄力（口径 #4 修订，2026-09-02）：计时器初始为 0——首刀/首步各延后一个完整间隔
        this.attackTimer = 0f;
        this.moveTimer = 0f;
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

    /** 有效属性（第二级，脏则重算；口径 #7 float 全精度；计数通道并入 K2） */
    public float getEffective(StatKey key) {
        if (statsDirty) {
            effectiveStats = StatPipeline.deriveEffective(baseStats,
                    StatPipeline.statusModifiers(statuses), combatCounters);
            statsDirty = false;
        }
        return effectiveStats.get(key);
    }

    /** 战斗内计数器（K2：遗物/旧疤并入第二级；framework-internal 写，变更后调 invalidateStats） */
    public CombatCounters getCombatCounters() {
        return combatCounters;
    }

    /** 最大生命 = 有效 HP 键 */
    public float maxHp() {
        return getEffective(StatKey.HP);
    }

    /** 血量比例（索敌 LOWEST_HP 键） */
    public float hpRatio() {
        return currentHp / maxHp();
    }

    /** 是否被控制（C7 ②：倒地 ∨ STUN 在身——列表内状态均为未到期） */
    public boolean hasControl() {
        if (downed) {
            return true;
        }
        for (ActiveStatus status : statuses) {
            if (status.getType() == StatusType.STUN) {
                return true;
            }
        }
        return false;
    }

    /** 倒地中（未清扫但不可行动/不可被索敌伤害治疗——C7 ③；字段非状态：markCleaned 清状态不波及） */
    public boolean isDowned() { return downed; }

    public float getDownedTimerRemaining() { return downedTimerRemaining; }

    /** 本场是否已用过不死（C1：undying 首倒专用，二次 HP≤0 为真死） */
    public boolean isUndyingUsed() { return undyingUsed; }

    /** 可行动存活 = 未清扫且非倒地（§〇.4：名册继承/点算候选/索敌过滤的统一资格谓词） */
    public boolean isActable() {
        return !cleaned && !downed;
    }

    /** 活着 = 未清扫（濒死未清扫亦"活"，H 语义延迟清扫的前提） */
    public boolean isAlive() {
        return !cleaned;
    }

    /** 攻击间隔（秒）：1 / (有效攻速 × 全局攻速系数)——系数只在消耗点乘算（battle §5.1，2026-09-02；
     *  canActOnAttackTimer/consumeAttackTimer/蓄力条显示共用本式，units.json 与属性管线不动） */
    public float attackInterval() {
        return 1f / (getEffective(StatKey.ATTACK_SPEED) * GameBalance.ATTACK_SPEED_GLOBAL_FACTOR);
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

    // —— undying 倒地（C7，CP17；framework-internal 写方法归 UndyingRule）——

    private boolean downed;
    private float downedTimerRemaining;
    private boolean undyingUsed;

    /** framework-internal：进入倒地（UndyingRule 调；占格维持、能量保留冻结、目标失效） */
    public void enterDowned(float seconds) {
        downed = true;
        downedTimerRemaining = seconds;
        undyingUsed = true;
        targetId = -1;
    }

    /** framework-internal：倒地计时递减（advanceTimers 旁，恒累计口径不破；非倒地空操作） */
    public void advanceDownedTimer(float dt) {
        if (downed) {
            downedTimerRemaining = Math.max(0f, downedTimerRemaining - dt);
        }
    }

    /** framework-internal：原地站起（40% 血 + 能量回满；格被占时由 UndyingRule 先就近落格） */
    public void revive(float hpRatio) {
        downed = false;
        setEnergy(GameBalance.ENERGY_MAX);
        currentHp = maxHp() * hpRatio;
        invalidateStats();
    }

    /** framework-internal：清扫（死亡标记）——状态销毁、目标失效 */
    public void markCleaned() {
        cleaned = true;
        statuses.clear();
        targetId = -1;
    }

    /** framework-internal：放逐移除（C10 非死亡）——同清扫清场，但额外携带 banished 标记供表现层区分 */
    public void markBanished() {
        markCleaned();
        banished = true;
    }

    /** 放逐移除标记（仅表现层读取；机制语义全部由「非死亡事件」口径保证） */
    public boolean isBanished() {
        return banished;
    }

    /** framework-internal：机制词下发（K1：startBattle 派生时按 target 谓词过滤后写一次） */
    public void setMechanics(List<EffectData> granted) {
        Objects.requireNonNull(granted, "granted 不能为 null");
        this.mechanics = Collections.unmodifiableList(new ArrayList<EffectData>(granted));
    }

    /** 命中的机制词全量（不可变视图；批2~4 各系统消费） */
    public List<EffectData> getMechanics() {
        return mechanics;
    }

    /** 本单位命中的机制词（K1：按 target 谓词过滤后下发；无=null） */
    public EffectData getMechanic(SynergyMechanic kind) {
        for (EffectData effect : mechanics) {
            if (effect.getMechanic() == kind) {
                return effect;
            }
        }
        return null;
    }

    private static float clampEnergy(float value) {
        return Math.max(0f, Math.min(GameBalance.ENERGY_MAX, value));
    }

    // —— 击杀归因（CP13，C5/C12）——

    private int lastDamagerId = -1;

    /** 最近有效伤害来源（普攻/技能直伤/DOT/亡语/反弹；-1 = 无） */
    public int getLastDamagerId() { return lastDamagerId; }

    /** framework-internal：伤害命中记录（DamagePipeline 调；hpDamage>0 时才写） */
    public void setLastDamagerId(int damagerId) {
        this.lastDamagerId = damagerId;
    }

    // —— 战吼嘲讽（CP23，K7；framework-internal 写方法归 TimedTriggerSystem/BattleSystem）——

    private int tauntTargetId = -1;
    private int tauntUntilTick;

    /** 被嘲讽中（tick < 到期 tick；旗手 id 见 getTauntTargetId） */
    public boolean isTaunted(int tick) {
        return tauntTargetId >= 0 && tick < tauntUntilTick;
    }

    /** 嘲讽目标（旗手 unit id；-1 = 未被嘲讽） */
    public int getTauntTargetId() {
        return tauntTargetId;
    }

    /** framework-internal：被战吼嘲讽——同时强制 targetId 指向旗手（K7「targetId 强制指向旗手」） */
    public void setTaunted(int bannerId, int untilTick) {
        this.tauntTargetId = bannerId;
        this.tauntUntilTick = untilTick;
        this.targetId = bannerId;
    }

    /** framework-internal：解除嘲讽（旗手失效时；到期出豁免走 clearExpiredTaunt） */
    public void clearTaunt() {
        this.tauntTargetId = -1;
    }

    /** framework-internal：到期自动出豁免（act 头部调用；过期即作废不再占用 retargetAll 豁免位） */
    public void clearExpiredTaunt(int tick) {
        if (tauntTargetId >= 0 && tick >= tauntUntilTick) {
            tauntTargetId = -1;
        }
    }

    // —— 旧疤受击计数（CP21，K2 计数通道）——

    /** framework-internal：被普攻直伤命中 +1 计数（DamagePipeline 普攻命中挂点调）——
     *  每 SCAR.value 次进一层（封顶 value2），层数经 combatCounters 并入第二级有效值。
     *  DOT/真伤/反弹不经此路径（豁免天然成立）；无 SCAR 机制时空操作。 */
    public void onScarHit() {
        EffectData scar = getMechanic(SynergyMechanic.SCAR);
        if (scar == null || scar.getValue2() == null) {
            return;
        }
        int perStack = Math.round(scar.getValue());
        if (perStack <= 0) {
            return; // 防御：value 非正不进层（加载校验已拦，测试夹具兜底）
        }
        combatCounters.addBasicHitTaken();
        if (combatCounters.getBasicHitTakenCount() < perStack) {
            return;
        }
        combatCounters.resetBasicHitTakenCount();
        if (combatCounters.getScarStacks() < Math.round(scar.getValue2())) {
            combatCounters.addScarStack();
            invalidateStats();
        }
    }
}
