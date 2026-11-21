package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Projectile;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.effect;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.skill;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 弹道系统测试（battle §5.3 + 口径 #13/#14）：HOMING 锁定弹追踪目标当前格中心；
 * 6 格/秒；冻结载荷命中；目标清扫消散；施放者清扫不影响在途弹。
 */
class ProjectileSystemTest {

    private static final class Wired {
        final DamagePipeline pipeline = new DamagePipeline(new CastTrigger() {
            @Override
            public void tryCast(BattleState state, BattleUnit caster) {
            }
        });
        final StatusSystem statusSystem = new StatusSystem(pipeline);
        final SkillExecutor executor = new SkillExecutor(pipeline, statusSystem);
        final ProjectileSystem system = new ProjectileSystem(pipeline, executor);

        Wired() {
            pipeline.bindStatusSystem(statusSystem); // CP25：普攻附带状态经 StatusSystem 挂载
        }
    }

    private static final Wired W = new Wired();

    /** 0 甲直构单位（格中心 = (x+0.5, y+0.5)） */
    private static BattleUnit u(int id, Side side, int x, int y) {
        BattleUnit unit = new BattleUnit(id, tpl("u" + id), 1, side, skill(
                "sk_" + id, SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)),
                new com.voidvvv.kz_auto_chess_n.entities.BattleStats(100f, 10f, 0f, 1f, 3f, 1f, 0f, 100f, 0f, 20f, 150f));
        unit.setPosition(x, y);
        return unit;
    }

    @Test
    @DisplayName("6 格/秒到达 tick 数算例：3 格距离 → 30±1 tick 命中")
    void travelTimeForThreeCells() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2); // 中心距 3.0
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 28; i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isEqualTo(100f); // 未到
        assertThat(state.getProjectiles()).hasSize(1);

        int hitTick = -1;
        for (int i = 28; i < 32; i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
            if (state.getProjectiles().isEmpty()) {
                hitTick = i + 1;
                break;
            }
        }
        assertThat(hitTick).isBetween(29, 31);
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("追踪目标移动后的格（改向）：目标换格后弹道折向新格中心并命中")
    void homesOntoMovedTarget() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 10; i++) { // 前进 1.0 格 → 约 (2.5,4.5)
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        state.removeFromGrid(target);
        state.placeUnit(target, 4, 2); // 目标右移两格

        for (int i = 0; i < 60 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty(); // 命中移动后的目标
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("普攻弹命中 = 发射快照 × 命中时护甲：发射后攻击者被降攻仍按快照结算（口径 #13）")
    void basicProjectileUsesFrozenSnapshot() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        atk.addStatus(new ActiveStatus(StatusType.ATK_DOWN, 9, 50f, 5f)); // 攻击减半——不应影响在途弹

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f)); // 快照 10 而非当前 5
    }

    @Test
    @DisplayName("暴击标志原样落地：crit 弹伤害 ×1.5、Hit 事件 crit=true")
    void critFlagCarriesThrough() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, true, null, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(85f, within(1e-6f));
        CombatEvent hit = state.getEvents().get(0);
        assertThat(hit.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(hit.isCrit()).isTrue();
    }

    @Test
    @DisplayName("目标先一步被清扫 → 消散：Fizzled 事件、无伤害无回能（口径 #14）")
    void fizzledWhenTargetCleaned() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        target.markCleaned();

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isEqualTo(100f);
        assertThat(atk.getEnergy()).isEqualTo(0f); // 落空不触发回能/吸血
        assertThat(state.getEvents()).hasSize(1);
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.PROJECTILE_FIZZLED);
    }

    @Test
    @DisplayName("施放者被清扫不影响在途弹（口径 #14）：命中照常结算")
    void casterDeathDoesNotAffectProjectile() {
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, null, 1f));
        atk.markCleaned();

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.HIT);
    }

    @Test
    @DisplayName("多弹互不碰撞：两弹各自命中各自目标")
    void multipleProjectilesDoNotCollide() {
        BattleUnit atk1 = u(1, Side.PLAYER, 1, 5);
        BattleUnit atk2 = u(2, Side.PLAYER, 4, 5);
        BattleUnit t3 = u(3, Side.ENEMY, 1, 2);
        BattleUnit t4 = u(4, Side.ENEMY, 4, 2);
        BattleState state = state(atk1, atk2, t3, t4);
        state.spawnProjectile(new Projectile(1, 3, 1.5f, 5.5f, 10f, false, null, 1f));
        state.spawnProjectile(new Projectile(2, 4, 4.5f, 5.5f, 10f, false, null, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(t3.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(t4.getCurrentHp()).isCloseTo(90f, within(1e-6f));
    }

    @Test
    @DisplayName("MELEE_INSTANT 技能不产弹（cast 即结算）")
    void meleeSkillSpawnsNoProjectile() {
        SkillData melee = skill("sk_m", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit caster = new BattleUnit(1, tpl("c"), 1, Side.PLAYER, melee,
                new com.voidvvv.kz_auto_chess_n.entities.BattleStats(100f, 10f, 0f, 1f, 1f, 1f, 0f, 100f, 0f, 20f, 150f));
        caster.setPosition(2, 4);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        caster.setTargetId(2);
        BattleState state = state(caster, target);
        assertThat(W.executor.cast(state, caster)).isTrue();
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 已即时结算
    }

    @Test
    @DisplayName("技能弹命中走 applyAtImpact：HIT 事件带 skillId、伤害按冻结载荷")
    void skillProjectileAppliesOnImpact() {
        SkillData homing = skill("sk_h", SkillShape.SINGLE_TARGET, Delivery.HOMING,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit atk = u(1, Side.PLAYER, 2, 5);
        BattleUnit target = u(2, Side.ENEMY, 2, 2);
        BattleState state = state(atk, target);
        state.spawnProjectile(new Projectile(1, 2, 2.5f, 5.5f, 10f, false, homing, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(target.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 10 × 2
        assertThat(state.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(state.getEvents().get(0).getSkillId()).isEqualTo("sk_h");
    }

    // —— v5 亡语源弹道（CP18，C6/§五.7）——

    /** 亡语源弹场景：源（已亡）在 (2,5)、原目标 (2,2)、重选候选见各用例 */
    private static Projectile rattleShot(int targetId) {
        return new Projectile(1, targetId, 2.5f, 5.5f, 10f, false, null, 1f, true);
    }

    @Test
    @DisplayName("亡语源弹目标清扫 → 无 FIZZLED：重选最低血可行动敌、命中走真伤（无视护甲）")
    void deathrattleShotRetargetsOnCleanedTarget() {
        BattleUnit source = u(1, Side.PLAYER, 2, 5);
        source.markCleaned(); // 亡语主人已亡——载荷冻结照飞
        BattleUnit gone = u(2, Side.ENEMY, 2, 2);
        BattleUnit half = u(3, Side.ENEMY, 3, 2);
        half.modifyHp(-40f); // 60% → hp 60
        BattleUnit low = u(4, Side.ENEMY, 4, 2);
        low.modifyHp(-50f);  // 50% ← LOWEST_HP → hp 50
        BattleState state = state(source, gone, half, low);
        state.spawnProjectile(rattleShot(2));
        gone.markCleaned();

        for (int i = 0; i < 60 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();                 // 重选后命中而非消散
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.PROJECTILE_FIZZLED);
        assertThat(low.getCurrentHp()).isCloseTo(40f, within(1e-6f)); // 50 − 真伤 10 全额（无视护甲）
        assertThat(half.getCurrentHp()).isEqualTo(60f);
        CombatEvent hit = state.getEvents().get(state.getEvents().size() - 1);
        assertThat(hit.getType()).isEqualTo(CombatEvent.Type.HIT);
        assertThat(hit.getSkillId()).isEqualTo("deathrattle_projectile"); // 紫飘字载荷口径
    }

    @Test
    @DisplayName("亡语源弹目标倒地 → 同清扫路径重选；普攻弹目标倒地 → 维持 FIZZLED")
    void deathrattleShotRetargetsOnDownedTarget() {
        BattleUnit source = u(1, Side.PLAYER, 2, 5);
        source.markCleaned();
        BattleUnit downed = u(2, Side.ENEMY, 2, 2);
        BattleUnit other = u(3, Side.ENEMY, 3, 2);
        BattleState state = state(source, downed, other);
        state.spawnProjectile(rattleShot(2));
        downed.enterDowned(1.0f); // 测试充当 systems 层（UndyingRule 写路径）

        for (int i = 0; i < 60 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(state.getEvents())
                .noneMatch(e -> e.getType() == CombatEvent.Type.PROJECTILE_FIZZLED);
        assertThat(other.getCurrentHp()).isCloseTo(90f, within(1e-6f)); // 重选命中倒地者旁的敌
        assertThat(downed.getCurrentHp()).isEqualTo(100f);

        // 对照：普攻弹（deathrattleBorn=false）目标倒地 → 维持既有消散口径
        BattleUnit source2 = u(4, Side.PLAYER, 2, 5);
        BattleUnit downed2 = u(5, Side.ENEMY, 2, 2);
        BattleState state2 = state(source2, downed2);
        state2.spawnProjectile(new Projectile(4, 5, 2.5f, 5.5f, 10f, false, null, 1f));
        downed2.enterDowned(1.0f);
        W.system.advanceAll(state2, GameBalance.LOGIC_STEP);
        assertThat(state2.getProjectiles()).isEmpty();
        assertThat(state2.getEvents().get(0).getType())
                .isEqualTo(CombatEvent.Type.PROJECTILE_FIZZLED);
    }

    @Test
    @DisplayName("亡语源弹无可选目标（全场无敌方可行动存活）→ FIZZLED")
    void deathrattleShotFizzlesWithoutCandidates() {
        BattleUnit source = u(1, Side.PLAYER, 2, 5);
        source.markCleaned();
        BattleUnit gone = u(2, Side.ENEMY, 2, 2);
        BattleUnit downed = u(3, Side.ENEMY, 3, 2);
        BattleState state = state(source, gone, downed);
        state.spawnProjectile(rattleShot(2));
        gone.markCleaned();
        downed.enterDowned(1.0f);

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(state.getEvents().get(0).getType())
                .isEqualTo(CombatEvent.Type.PROJECTILE_FIZZLED);
    }

    // —— v5 批4：盾墙拦截（CP24，K8——只拦普攻弹道）——

    /** SHIELD_WALL 机制直授（无参词） */
    private static void grantWall(BattleUnit unit) {
        unit.setMechanics(java.util.Collections.singletonList(
                new com.voidvvv.kz_auto_chess_n.data.EffectData(null, null,
                        com.voidvvv.kz_auto_chess_n.data.SynergyMechanic.SHIELD_WALL, null,
                        null, null, null, null, com.voidvvv.kz_auto_chess_n.data.EffectTarget.ALLIES, null)));
    }

    /** BASIC_ATTACK_STATUS 机制直授（普攻附带，联 CP25） */
    private static void grantBurn(BattleUnit unit) {
        unit.setMechanics(java.util.Collections.singletonList(
                new com.voidvvv.kz_auto_chess_n.data.EffectData(null, null,
                        com.voidvvv.kz_auto_chess_n.data.SynergyMechanic.BASIC_ATTACK_STATUS, null,
                        30f, null, StatusType.BURN, 3f,
                        com.voidvvv.kz_auto_chess_n.data.EffectTarget.ALLIES, null)));
    }

    @Test
    @DisplayName("盾墙拦普攻弹（K8）：目标在战士身后 → 挡下（BLOCKED、无 HIT、双方零掉血）；敌我两侧对称")
    void shieldWallBlocksBasicProjectile() {
        // 玩家侧：敌射手 → 玩家后排（在玩家战士身后）
        BattleUnit shooter = u(1, Side.ENEMY, 2, 2);
        BattleUnit warrior = u(2, Side.PLAYER, 2, 4);
        BattleUnit backline = u(3, Side.PLAYER, 2, 5); // y=5 > 4：身后
        grantWall(warrior);
        BattleState state = state(shooter, warrior, backline);
        state.spawnProjectile(new Projectile(1, 3, 2.5f, 4.55f, 10f, false, null, 1f));

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        assertThat(state.getProjectiles()).isEmpty();
        assertThat(backline.getCurrentHp()).isEqualTo(100f);
        assertThat(warrior.getCurrentHp()).isEqualTo(100f);
        CombatEvent blocked = state.getEvents().get(0);
        assertThat(blocked.getType()).isEqualTo(CombatEvent.Type.PROJECTILE_BLOCKED);
        assertThat(blocked.getSourceId()).isEqualTo(warrior.getId());
        assertThat(blocked.getTargetId()).isEqualTo(backline.getId());
        assertThat(state.getEvents()).noneMatch(e -> e.getType() == CombatEvent.Type.HIT);

        // 敌方镜像：玩家射手 → 敌后排（在敌战士身后：y 更小）
        BattleUnit pShooter = u(4, Side.PLAYER, 2, 5);
        BattleUnit eWarrior = u(5, Side.ENEMY, 2, 2);
        BattleUnit eBackline = u(6, Side.ENEMY, 2, 1); // y=1 < 2：身后
        grantWall(eWarrior);
        BattleState state2 = state(pShooter, eWarrior, eBackline);
        state2.spawnProjectile(new Projectile(4, 6, 2.5f, 2.45f, 10f, false, null, 1f));
        W.system.advanceAll(state2, GameBalance.LOGIC_STEP);
        assertThat(eBackline.getCurrentHp()).isEqualTo(100f);
        assertThat(state2.getEvents().get(0).getType()).isEqualTo(CombatEvent.Type.PROJECTILE_BLOCKED);
        assertThat(state2.getEvents().get(0).getSourceId()).isEqualTo(eWarrior.getId());
    }

    @Test
    @DisplayName("技能弹与亡语源弹不受盾墙限制（K8 分界=普攻弹）：穿墙照常命中")
    void shieldWallIgnoresSkillAndDeathrattleShots() {
        SkillData homing = skill("sk_sp", SkillShape.SINGLE_TARGET, Delivery.HOMING,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
        BattleUnit shooter = u(1, Side.ENEMY, 2, 2);
        BattleUnit warrior = u(2, Side.PLAYER, 2, 4);
        BattleUnit backline = u(3, Side.PLAYER, 2, 5);
        grantWall(warrior);
        BattleState state = state(shooter, warrior, backline);
        state.spawnProjectile(new Projectile(1, 3, 2.5f, 4.55f, 10f, false, homing, 1f));
        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(backline.getCurrentHp()).isCloseTo(80f, within(1e-6f)); // 技能弹命中
        assertThat(state.getEvents()).noneMatch(e -> e.getType() == CombatEvent.Type.PROJECTILE_BLOCKED);

        // 亡语源弹（deathrattleBorn=true，非普攻弹）同样穿墙
        BattleUnit shooter2 = u(4, Side.ENEMY, 2, 2);
        BattleUnit warrior2 = u(5, Side.PLAYER, 2, 4);
        BattleUnit backline2 = u(6, Side.PLAYER, 2, 5);
        grantWall(warrior2);
        BattleState state2 = state(shooter2, warrior2, backline2);
        state2.spawnProjectile(new Projectile(4, 6, 2.5f, 4.55f, 10f, false, null, 1f, true));
        W.system.advanceAll(state2, GameBalance.LOGIC_STEP);
        assertThat(state2.getEvents()).noneMatch(e -> e.getType() == CombatEvent.Type.PROJECTILE_BLOCKED);
    }

    @Test
    @DisplayName("目标不在任何战士身后 → 不拦：普攻弹照常命中")
    void shieldWallSkipsWhenTargetNotBehind() {
        BattleUnit shooter = u(1, Side.ENEMY, 2, 1);
        BattleUnit warrior = u(2, Side.PLAYER, 2, 4);
        BattleUnit frontAlly = u(3, Side.PLAYER, 2, 3); // y=3 < 4：非身后
        grantWall(warrior);
        BattleState state = state(shooter, warrior, frontAlly);
        state.spawnProjectile(new Projectile(1, 3, 2.5f, 3.45f, 10f, false, null, 1f));

        for (int i = 0; i < 40 && !state.getProjectiles().isEmpty(); i++) {
            W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        }
        assertThat(frontAlly.getCurrentHp()).isCloseTo(90f, within(1e-6f));
        assertThat(state.getEvents()).noneMatch(e -> e.getType() == CombatEvent.Type.PROJECTILE_BLOCKED);
    }

    @Test
    @DisplayName("盾墙拦截后普攻附带落空（CP24×CP25 联测）：攻击者带 BURN，被拦目标无 BURN")
    void shieldWallBlockDropsBasicAttackStatus() {
        BattleUnit shooter = u(1, Side.ENEMY, 2, 2);
        BattleUnit warrior = u(2, Side.PLAYER, 2, 4);
        BattleUnit backline = u(3, Side.PLAYER, 2, 5);
        grantWall(warrior);
        grantBurn(shooter);
        BattleState state = state(shooter, warrior, backline);
        state.spawnProjectile(new Projectile(1, 3, 2.5f, 4.55f, 10f, false, null, 1f));

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        assertThat(backline.getStatuses()).isEmpty(); // impact 未发生 → 附带不落地
    }

    @Test
    @DisplayName("多战士取距目标最近者（K8/R8 参照=目标）：距 1 者优先于距 2 者")
    void shieldWallPicksNearestToTarget() {
        BattleUnit shooter = u(1, Side.ENEMY, 2, 2);
        BattleUnit nearWall = u(2, Side.PLAYER, 2, 4);  // 距目标 (2,5) = 1
        BattleUnit farWall = u(3, Side.PLAYER, 2, 3);   // 距目标 (2,5) = 2（也在目标身前）
        BattleUnit backline = u(4, Side.PLAYER, 2, 5);
        grantWall(nearWall);
        grantWall(farWall);
        BattleState state = state(shooter, nearWall, farWall, backline);
        state.spawnProjectile(new Projectile(1, 4, 2.5f, 4.55f, 10f, false, null, 1f));

        W.system.advanceAll(state, GameBalance.LOGIC_STEP);
        CombatEvent blocked = state.getEvents().get(0);
        assertThat(blocked.getType()).isEqualTo(CombatEvent.Type.PROJECTILE_BLOCKED);
        assertThat(blocked.getSourceId()).isEqualTo(nearWall.getId()); // 距目标最近者挡下
    }
}
