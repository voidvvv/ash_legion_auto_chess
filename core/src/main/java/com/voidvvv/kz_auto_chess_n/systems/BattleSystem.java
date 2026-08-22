package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.IdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 战斗组合根（battle §二）：以构造器组装六个子系统（无构造环——DamagePipeline 收
 * {@link CastTrigger} 接口，由本类方法引用延迟绑定）。
 *
 * <p>主循环五阶段固定序（口径 #2，battle §二 + 弹道推进）：
 * ①状态推进 → ②弹道推进 → ③逐单位行动（id 序，互斥链）→ ④死亡清扫 → ⑤胜负判定。
 * 普攻发射处 roll 暴击——RNG 消耗序 = 发射序（architecture §六第 4 点）；
 * 开战（tick 0）零 RNG 消耗（发号与布阵均确定序）。
 */
public final class BattleSystem {
    private final TargetingSystem targeting = new TargetingSystem();
    private final MovementSystem movement = new MovementSystem();
    private final SynergySystem synergySystem = new SynergySystem();
    private final DamagePipeline damagePipeline;   // new DamagePipeline(this::tryCastInline)
    private final StatusSystem statusSystem;
    private final SkillExecutor skillExecutor;
    private final ProjectileSystem projectileSystem;
    /** 就地施放嵌套深度（口径 #19，单线程逻辑步内安全） */
    private int castDepth;

    public BattleSystem() {
        this.damagePipeline = new DamagePipeline(this::tryCastInline);
        this.statusSystem = new StatusSystem(damagePipeline);
        this.skillExecutor = new SkillExecutor(damagePipeline, statusSystem);
        this.projectileSystem = new ProjectileSystem(damagePipeline, skillExecutor);
    }

    /**
     * 开战：玩家侧 getDeployedUnits()（扫描序 y↑x↑）+ 敌方 WaveSpec 列表序 → IdIssuer 发号
     * （口径 #16）→ 两侧 SynergySystem.resolve → StatPipeline.deriveBaseline（玩家 scale=1.0 /
     * 敌方 spec.getScale()）→ BattleState 布格 → 开局效果落地（口径 #17）→ 按 id 序初始索敌。
     */
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer) {
        return startBattle(player, enemyWave, data, rng, idIssuer, RunModifiers.EMPTY);
    }

    /**
     * 开战（带局外修正，Phase 6）：玩家侧羁绊结算吃增幅（「荆语」——CP10）；玩家侧派生
     * 修正源列表追加 RunModifiers（「战歌」全队回能 ADD 百分点，走 DamagePipeline 现成
     * 管线——裁决 D13）；敌方侧恒不注入。
     */
    public BattleState startBattle(Player player, List<WaveSpec> enemyWave,
                                   GameData data, RandomGenerator rng, IdIssuer idIssuer,
                                   RunModifiers modifiers) {
        Objects.requireNonNull(player, "player 不能为 null");
        Objects.requireNonNull(enemyWave, "enemyWave 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(rng, "rng 不能为 null");
        Objects.requireNonNull(idIssuer, "idIssuer 不能为 null");
        Objects.requireNonNull(modifiers, "modifiers 不能为 null");

        List<Unit> deployed = player.getDeployedUnits();
        SynergySnapshot playerSynergies = synergySystem.resolve(templatesOfDeployed(player), data,
                modifiers.getSynergyAmp());
        List<UnitData> enemyTemplates = new ArrayList<UnitData>(enemyWave.size());
        for (WaveSpec spec : enemyWave) {
            enemyTemplates.add(spec.getTemplate());
        }
        SynergySnapshot enemySynergies = synergySystem.resolve(enemyTemplates, data);

        List<BattleUnit> units = new ArrayList<BattleUnit>();
        List<Unit> rosterDeployed = player.getDeployedUnits(); // 与下方扫描同序（y↑x↑，口径 #16）
        // 玩家侧：部署表扫描序 y↑x↑（= getDeployedUnits 序，坐标同源）
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit == null) {
                    continue;
                }
                units.add(deriveUnit(idIssuer.nextId(), unit.getTemplate(), unit.getStar(),
                        Side.PLAYER, 1.0f, playerSynergies, data, x, y, unit.getEquipped(),
                        modifiers));
            }
        }
        verifyDeployedCount(deployed.size(), units.size());
        // 敌方：WaveSpec 列表序（杂兵抽取序 + Boss 殿后）；敌方无装备路径（空列表派生）
        for (WaveSpec spec : enemyWave) {
            units.add(deriveUnit(idIssuer.nextId(), spec.getTemplate(), spec.getStar(),
                    Side.ENEMY, spec.getScale(), enemySynergies, data, spec.getGridX(), spec.getGridY(),
                    Collections.<Equipment>emptyList(), null));
        }

        BattleState state = new BattleState(units, rng, playerSynergies, enemySynergies);
        for (BattleUnit unit : units) {
            state.placeUnit(unit, unit.getGridX(), unit.getGridY());
        }
        applyEquipmentPassives(state, rosterDeployed); // 装备被动（龙心类）：索引对齐玩家侧前 N 个 BattleUnit
        applyOpeningEffects(state, playerSynergies, Side.PLAYER);
        applyOpeningEffects(state, enemySynergies, Side.ENEMY);
        targeting.retargetAll(state); // 按 id 序初始索敌
        return state;
    }

    /** 推进一个 LOGIC_STEP（五阶段固定序）；战斗已结束则空操作 */
    public void step(BattleState state) {
        Objects.requireNonNull(state, "state 不能为 null");
        if (state.isOver()) {
            return;
        }
        state.beginTick();
        if (state.getTick() % ticksOf(GameBalance.RETARGET_INTERVAL) == 0) {
            targeting.retargetAll(state); // 每 2s（120 tick）全局强制重评估
        }

        // ① 状态推进（DOT/REGEN 心跳 · 时长递减 · 到期移除）
        statusSystem.tickStatuses(state, GameBalance.LOGIC_STEP);

        // ② 弹道推进（口径 #2：先落地——后行动者看到最新血量）
        projectileSystem.advanceAll(state, GameBalance.LOGIC_STEP);

        // ③ 逐单位行动（id 序 = 发号序，同 tick 玩方先手；互斥链口径 #3）
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned()) {
                unit.advanceTimers(GameBalance.LOGIC_STEP); // 恒累计（口径 #4）
            }
        }
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned()) {
                act(state, unit);
            }
        }

        // ④ 死亡清扫（H 语义：本 tick 内 HP≤0 者统一于此清除）
        List<Integer> deadIds = new ArrayList<Integer>();
        for (BattleUnit unit : state.getUnits()) {
            if (!unit.isCleaned() && unit.getCurrentHp() <= 0f) {
                unit.markCleaned();
                state.removeFromGrid(unit);
                state.record(CombatEvent.unitDied(state.getTick(), unit.getId()));
                deadIds.add(unit.getId());
            }
        }
        for (int deadId : deadIds) {
            targeting.retargetOnDeath(state, deadId); // 清扫后立即重选（battle §三）
        }

        // ⑤ 胜负判定（口径 #15：玩家全灭从严 → 敌方全灭 → 超时）
        if (state.aliveCount(Side.PLAYER) == 0) {
            state.finish(BattleOutcome.ENEMY_WIN);
        } else if (state.aliveCount(Side.ENEMY) == 0) {
            state.finish(BattleOutcome.PLAYER_WIN);
        } else if (state.getElapsed() >= GameBalance.BATTLE_TIMEOUT) {
            state.finish(BattleOutcome.TIMEOUT);
        }
        if (state.isOver()) {
            state.record(CombatEvent.battleEnded(state.getTick(), state.getOutcome()));
        }
    }

    /** 便利推进至战斗结束（maxTicks 上限防御）；控制台与测试用 */
    public void runToEnd(BattleState state, int maxTicks) {
        int steps = 0;
        while (!state.isOver() && steps < maxTicks) {
            step(state);
            steps++;
        }
    }

    // —— 内部：行动链（严格互斥，battle §二 if/else 链） ——

    private void act(BattleState state, BattleUnit unit) {
        if (unit.hasControl()) {
            return; // 被控制 → 跳过
        }
        BattleUnit target = currentTarget(state, unit);
        if (target == null) {
            target = targeting.findTarget(state, unit); // 目标失效 → 重选
            unit.setTargetId(target == null ? -1 : target.getId());
            if (target == null) {
                return; // 战场将终
            }
        }
        if (unit.getEnergy() >= GameBalance.ENERGY_MAX) {
            if (skillExecutor.cast(state, unit)) {
                return; // 能量满 → 施放（成功耗尽本 tick 行动；延后落入后续链）
            }
        }
        if (manhattan(unit, target) <= unit.getEffective(StatKey.RANGE)) {
            if (!unit.canActOnAttackTimer()) {
                return; // 射程内 → 出手（冷却未到则等待）
            }
            boolean crit = state.getRng().nextFloat() < GameBalance.CRIT_CHANCE;
            state.record(CombatEvent.attackLaunched(state.getTick(), unit.getId(), target.getId()));
            if (unit.getEffective(StatKey.RANGE) >= 2f) {
                state.spawnProjectile(new Projectile(unit.getId(), target.getId(),
                        unit.getGridX() + 0.5f, unit.getGridY() + 0.5f,
                        unit.getEffective(StatKey.ATTACK), crit, null, 1f)); // 口径 #12：远程普攻 = HOMING 锁定弹
            } else {
                damagePipeline.applyDirectHit(state, unit, target,
                        unit.getEffective(StatKey.ATTACK), 1f, crit, true, null); // 近战即时结算
            }
            unit.consumeAttackTimer();
            return;
        }
        if (unit.canActOnMoveTimer() && movement.tryStep(state, unit, target)) {
            unit.consumeMoveTimer(); // 否则 → 走一步
        }
    }

    /** 就地施放回调（能量跨百）：深度上限保护（口径 #19），超限推迟到下一行动 tick */
    private void tryCastInline(BattleState state, BattleUnit caster) {
        if (castDepth >= GameBalance.MAX_INLINE_CAST_DEPTH) {
            return;
        }
        castDepth++;
        try {
            skillExecutor.cast(state, caster);
        } finally {
            castDepth--;
        }
    }

    private static BattleUnit currentTarget(BattleState state, BattleUnit unit) {
        if (unit.getTargetId() < 0) {
            return null;
        }
        BattleUnit target = state.getUnitById(unit.getTargetId());
        if (target == null || target.isCleaned() || target.getSide() == unit.getSide()) {
            return null;
        }
        return target;
    }

    private static BattleUnit deriveUnit(int id, UnitData template, int star, Side side, float scale,
                                         SynergySnapshot synergies, GameData data, int x, int y,
                                         List<Equipment> equipped, RunModifiers modifiers) {
        List<StatModifierSource> sources = new ArrayList<StatModifierSource>(3);
        sources.add(synergies);                     // 羁绊（侧全体）
        sources.add(EquipmentStats.of(equipped));   // 装备（单体）
        if (modifiers != null) {
            sources.add(modifiers);                 // 局外修正（全队回能——Phase 6，仅玩家侧注入）
        }
        BattleStats baseStats = StatPipeline.deriveBaseline(template, star, scale, sources);
        BattleUnit unit = new BattleUnit(id, template, star, side,
                data.getSkill(template.getSkillId()), baseStats); // 加载校验保证技能存在
        unit.setPosition(x, y);
        return unit;
    }

    /** 装备被动落地（data_schema §八：装备入口进 StatusSystem 的第二种形态）。
     *  rosterDeployed[i] ↔ units[i]（玩家侧前 N 个，同一扫描序）；REGEN 常驻（duration=∞，sourceId=-1）。 */
    private void applyEquipmentPassives(BattleState state, List<Unit> rosterDeployed) {
        List<BattleUnit> units = state.getUnits();
        for (int i = 0; i < rosterDeployed.size(); i++) {
            for (Equipment item : rosterDeployed.get(i).getEquipped()) {
                EquipmentPassive passive = item.getTemplate().getPassive();
                if (passive == null) {
                    continue;
                }
                statusSystem.apply(state, units.get(i), passive.getType(), passive.getPower(),
                        Float.POSITIVE_INFINITY, -1, passive.getTickInterval());
            }
        }
    }

    /** 开局效果（口径 #17）：effect 通道对"该侧"全部单位落地；本期仅支持 SHIELD */
    private void applyOpeningEffects(BattleState state, SynergySnapshot snapshot, Side side) {
        for (EffectData effect : snapshot.getOpeningEffects()) {
            if (effect.getEffect() != StatusType.SHIELD) {
                throw new IllegalStateException("本期 effect 通道仅支持 SHIELD，遇到: " + effect.getEffect());
            }
            for (BattleUnit unit : state.getUnits()) {
                if (unit.getSide() != side) {
                    continue;
                }
                statusSystem.apply(state, unit, StatusType.SHIELD,
                        unit.getEffective(StatKey.HP) * effect.getValue(),
                        Float.POSITIVE_INFINITY, -1);
            }
        }
    }

    private static List<UnitData> templatesOfDeployed(Player player) {
        List<UnitData> templates = new ArrayList<UnitData>();
        for (Unit unit : player.getDeployedUnits()) {
            templates.add(unit.getTemplate());
        }
        return templates;
    }

    private static int manhattan(BattleUnit a, BattleUnit b) {
        return Math.abs(a.getGridX() - b.getGridX()) + Math.abs(a.getGridY() - b.getGridY());
    }

    /** 秒 → tick 数（60Hz 整数倍场景；非整除向上取整） */
    private static int ticksOf(float seconds) {
        return Math.round(seconds / GameBalance.LOGIC_STEP);
    }

    /** 防御：部署表两遍扫描（羁绊统计 / 发号）结果必须一致 */
    private static void verifyDeployedCount(int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException("部署扫描与发号数量不一致: " + expected + " vs " + actual);
        }
    }
}
