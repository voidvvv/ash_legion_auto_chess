package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.effect;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.shieldEffect;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.skill;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.statEffect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 主循环组合根测试（battle §二五阶段 + 口径 #2/#3/#15/#16/#17）：
 * 开局派生 / 行动链互斥 / H 语义延迟清扫 / 胜负判定序 / 确定性 / RNG 审计。
 * 玩家单位只能部署在玩家区 y∈[4,6]（Player.deploy 域校验）。
 */
class BattleSystemTest {

    private static final BattleSystem SYSTEM = new BattleSystem();

    // —— 微型数据集：兽人战士（近战）/ 远程弓手 / 非兽人杂兵 ——

    private static UnitData melee(String id, String race, float hp, float atk, float aspd) {
        return new UnitData(id, "夹具" + id, race, "战士", 1,
                new BaseStats((int) hp, (int) atk, 0, aspd, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    private static UnitData ranged(String id, float hp, float atk, float aspd) {
        return new UnitData(id, "夹具" + id, "精灵", "游侠", 1,
                new BaseStats((int) hp, (int) atk, 0, aspd, 3, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    private static SkillData hitSkill() {
        return skill("sk_hit", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null));
    }

    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("orc", melee("orc", "兽人", 100, 10, 1f));
        units.put("elf", ranged("elf", 100, 10, 1f));
        units.put("grunt", melee("grunt", "哥布林", 100, 10, 1f));
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_orc", new SynergyData("syn_orc", "兽人", SynergySource.RACE, "兽人",
                Arrays.asList(
                        new SynergyData.Threshold(2, Arrays.asList(
                                statEffect(StatKey.HP, com.voidvvv.kz_auto_chess_n.data.EffectOp.ADD, 150f))),
                        new SynergyData.Threshold(6, Arrays.asList(
                                shieldEffect(0.3f),
                                statEffect(StatKey.LIFESTEAL, com.voidvvv.kz_auto_chess_n.data.EffectOp.ADD, 20f))))));
        return new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(), new ArrayList<String>());
    }

    private static Player deployPlayer(GameData data, UnitData template, int gridX, int gridY) {
        Player player = new Player(10);
        Unit unit = new Unit(1, template, 1);
        player.addToBench(unit);
        player.deploy(unit, gridX, gridY);
        return player;
    }

    private static List<WaveSpec> waveOf(UnitData template, float scale, int... cells) {
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        for (int i = 0; i < cells.length; i += 2) {
            wave.add(new WaveSpec(template, 1, scale, cells[i], cells[i + 1]));
        }
        return wave;
    }

    private static BattleState start(GameData data, Player player, List<WaveSpec> wave, long seed) {
        return SYSTEM.startBattle(player, wave, data, new RandomGenerator(seed), new SequentialIdIssuer());
    }

    // —— 开局 ——

    @Test
    @DisplayName("开局基准三合一：星级 × scale + 羁绊源（2 兽人 → hp=(100+150)=250）、HP=maxHp")
    void baselineWithSynergyAndScale() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        Unit second = new Unit(2, data.getUnit("orc"), 1);
        player.addToBench(second);
        player.deploy(second, 3, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1.4f, 2, 0);

        BattleState state = start(data, player, wave, 42L);
        assertThat(state.getUnits()).hasSize(3);
        assertThat(state.getUnits().get(0).getBaseStats().get(StatKey.HP)).isCloseTo(250f, within(1e-4f));
        assertThat(state.getUnits().get(0).getCurrentHp()).isCloseTo(250f, within(1e-4f));
        assertThat(state.getUnits().get(2).getBaseStats().get(StatKey.ATTACK)).isCloseTo(14f, within(1e-4f));
    }

    @Test
    @DisplayName("id 发号序（口径 #16）：玩家扫描序 y↑x↑ 在前、敌方 WaveSpec 列表序在后")
    void idIssuingOrder() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 4, 5);
        Unit second = new Unit(2, data.getUnit("orc"), 1);
        player.addToBench(second);
        player.deploy(second, 1, 4); // y=4 先于 y=5
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0, 3, 1);

        BattleState state = start(data, player, wave, 42L);
        assertThat(state.getUnits()).extracting(BattleUnit::getId).containsExactly(1, 2, 3, 4);
        assertThat(state.getUnits().get(0).getGridY()).isEqualTo(4);
        assertThat(state.getUnits().get(0).getSide()).isEqualTo(Side.PLAYER);
        assertThat(state.getUnits().get(2).getSide()).isEqualTo(Side.ENEMY);
        assertThat(state.unitAt(2, 0)).isSameAs(state.getUnits().get(2));
    }

    @Test
    @DisplayName("初始索敌：开局按 id 序 findTarget 全员就位")
    void initialTargeting() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);
        assertThat(state.getUnits().get(0).getTargetId()).isEqualTo(2);
        assertThat(state.getUnits().get(1).getTargetId()).isEqualTo(1);
    }

    @Test
    @DisplayName("开局盾（口径 #17）：兽人 6 档（替换制，maxHp=100）两侧各自结算，SHIELDED 落 tick 0")
    void openingShieldBothSides() {
        GameData data = data();
        Player player = new Player(10);
        for (int i = 0; i < 6; i++) {
            Unit unit = new Unit(100 + i, data.getUnit("orc"), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        // 敌方同样 6 兽人（有放回抽取的等价形态）——兽人 6 档对敌方侧同样生效
        List<WaveSpec> wave = waveOf(data.getUnit("orc"), 1f, 0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0);
        BattleState state = start(data, player, wave, 42L);

        long shields = 0;
        for (CombatEvent e : state.getEvents()) {
            assertThat(e.getTick()).isEqualTo(0);
            if (e.getType() == CombatEvent.Type.SHIELDED) {
                shields++;
            }
        }
        assertThat(shields).isEqualTo(12); // 两侧各 6
        assertThat(state.getUnits().get(0).getStatuses()).hasSize(1);
        assertThat(state.getUnits().get(0).getStatuses().get(0).getPower()).isCloseTo(30f, within(1e-4f));
        assertThat(state.getUnits().get(6).getStatuses()).hasSize(1); // 敌方首位同样有盾
    }

    // —— 主循环 ——

    @Test
    @DisplayName("行动序 = id 升序：双方同 tick 进入射程时，首个 HIT 来源为玩家单位（id 小先手）")
    void actionOrderById() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 1);
        BattleState state = start(data, player, wave, 42L);

        SYSTEM.step(state); // tick1：双方各走一步（玩家先动、占 (2,3)；敌方跟进 (2,2)）
        SYSTEM.step(state); // tick2：双方同处射程起点——id 1 先结算
        CombatEvent firstHit = null;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.HIT) {
                firstHit = e;
                break;
            }
        }
        assertThat(firstHit).isNotNull();
        assertThat(firstHit.getSourceId()).isEqualTo(1);
        assertThat(firstHit.getTargetId()).isEqualTo(2);
    }

    @Test
    @DisplayName("互斥行动链（口径 #3）：能量满时施放耗尽本 tick 行动（不移动）")
    void castConsumesSoleAction() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 6);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);
        BattleUnit playerUnit = state.getUnits().get(0);
        playerUnit.setEnergy(100f);
        int y = playerUnit.getGridY();

        SYSTEM.step(state);
        boolean casted = false;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.CAST && e.getSourceId() == 1) {
                casted = true;
            }
        }
        assertThat(casted).isTrue();
        assertThat(playerUnit.getGridY()).isEqualTo(y); // 施放占据行动——未移动
        // 口径 #6：技能直伤同样触发攻守回能——清零后攻击者又 +10
        assertThat(playerUnit.getEnergy()).isEqualTo(10f);

        SYSTEM.step(state); // 能量清零后走步恢复
        assertThat(playerUnit.getGridY()).isLessThan(y);
    }

    @Test
    @DisplayName("计时器结转（口径 #4）：aspd 2 → 0.5s/击，150 tick 内 4~6 次普攻无掉次")
    void attackTimerCarryOver() {
        GameData data = data();
        Player player = deployPlayer(data, melee("tank", "兽人", 5000, 1, 2f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("etank", "哥布林", 5000, 1, 2f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);

        for (int i = 0; i < 150; i++) {
            SYSTEM.step(state);
        }
        int hits = 0;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.HIT && e.getSourceId() == 1) {
                hits++;
            }
        }
        assertThat(hits).isBetween(4, 6);
    }

    @Test
    @DisplayName("H 语义互秒（口径 #15 从严）：同 tick 双双 HP≤0 均进清扫，判 ENEMY_WIN")
    void mutualKillSameTick() {
        GameData data = data();
        Player player = deployPlayer(data, melee("duel", "兽人", 250, 100, 1f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("eduel", "哥布林", 250, 100, 1f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);

        SYSTEM.runToEnd(state, 300);
        int deaths = 0;
        int deathTick = -1;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.UNIT_DIED) {
                deaths++;
                deathTick = e.getTick();
            }
        }
        assertThat(deaths).isEqualTo(2);
        assertThat(deathTick).isGreaterThan(0);
        // 全部 UNIT_DIED 同 tick（互秒）
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.UNIT_DIED) {
                assertThat(e.getTick()).isEqualTo(deathTick);
            }
        }
        assertThat(state.isOver()).isTrue();
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN);
        assertThat(state.getEvents().get(state.getEvents().size() - 1).getType())
                .isEqualTo(CombatEvent.Type.BATTLE_ENDED);
    }

    @Test
    @DisplayName("死亡腾格 + 立即重选：亡者格不再被其占用，观察者当场换目标")
    void deathFreesCellAndRetargets() {
        GameData data = data();
        Player player = deployPlayer(data, melee("killer", "兽人", 100, 100, 1f), 2, 4);
        UnitData lowHpObserver = new UnitData("observer", "观察者", "精灵", "游侠", 1,
                new BaseStats(100, 1, 0, 1f, 3, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, TargetPriority.LOWEST_HP, "sk_hit", false);
        Unit observerUnit = new Unit(2, lowHpObserver, 1);
        player.addToBench(observerUnit);
        player.deploy(observerUnit, 1, 4);
        List<WaveSpec> wave = waveOf(melee("weak", "哥布林", 50, 0, 1f), 1f, 2, 0, 4, 0);
        BattleState state = start(data, player, wave, 42L);

        BattleUnit firstEnemy = state.getUnits().get(2); // id 3
        BattleUnit secondEnemy = state.getUnits().get(3); // id 4
        state.getUnits().get(1).setTargetId(firstEnemy.getId()); // 观察者锁定 id 3
        secondEnemy.modifyHp(-40f); // id 4 血量更低——清扫 id 3 后应切火

        SYSTEM.runToEnd(state, 600);
        assertThat(firstEnemy.isCleaned()).isTrue();
        assertThat(state.unitAt(firstEnemy.getGridX(), firstEnemy.getGridY()))
                .isNotSameAs(firstEnemy); // 腾格（可能已被他者占位）
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
    }

    @Test
    @DisplayName("120 tick 全局重评估：LOWEST_HP 单位切换到血量更低的敌人")
    void retargetEvery120Ticks() {
        GameData data = data();
        UnitData lowHpObserver = new UnitData("observer", "观察者", "精灵", "游侠", 1,
                new BaseStats(10000, 1, 0, 1f, 3, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, TargetPriority.LOWEST_HP, "sk_hit", false);
        Player player = deployPlayer(data, lowHpObserver, 2, 4);
        List<WaveSpec> wave = waveOf(melee("e1", "哥布林", 1000, 1, 1f), 1f, 1, 0, 4, 0);
        BattleState state = start(data, player, wave, 42L);
        BattleUnit observer = state.getUnits().get(0);
        assertThat(observer.getTargetId()).isEqualTo(2); // 满血平局 → 距离近者

        state.getUnits().get(2).modifyHp(-500f); // id 3 血量更低
        for (int i = 0; i < 120; i++) {
            SYSTEM.step(state);
        }
        assertThat(observer.getTargetId()).isEqualTo(3); // 全局重评估切火
    }

    @Test
    @DisplayName("超时判负（口径 #15）：60s 打不死 → TIMEOUT（= 玩家判负）")
    void timeoutCountsAsPlayerLoss() {
        GameData data = data();
        Player player = deployPlayer(data, melee("immortal", "兽人", 100000, 1, 1f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("eimmortal", "哥布林", 100000, 1, 1f), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);

        SYSTEM.runToEnd(state, 4000);
        assertThat(state.isOver()).isTrue();
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.TIMEOUT);
        assertThat(state.getOutcome().playerWon()).isFalse();
        assertThat(state.getElapsed()).isGreaterThanOrEqualTo(GameBalance.BATTLE_TIMEOUT);
    }

    @Test
    @DisplayName("敌方全灭 → PLAYER_WIN")
    void enemyWipeWins() {
        GameData data = data();
        Player player = deployPlayer(data, melee("hunter", "兽人", 100, 100, 1f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("prey", "哥布林", 30, 0, 1f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);

        SYSTEM.runToEnd(state, 600);
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
    }

    @Test
    @DisplayName("runToEnd 上限防御：不终局的战斗在 maxTicks 停步")
    void runToEndRespectsCap() {
        GameData data = data();
        Player player = deployPlayer(data, melee("immortal", "兽人", 100000, 1, 1f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("eimmortal", "哥布林", 100000, 1, 1f), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);

        SYSTEM.runToEnd(state, 100);
        assertThat(state.isOver()).isFalse();
        assertThat(state.getTick()).isEqualTo(100);
    }

    @Test
    @DisplayName("就地施放语义：受击者能量跨百当 tick 反打（CAST 与打它的 HIT 同 tick）")
    void inlineCastOnEnergyCrossing() {
        GameData data = data();
        Player player = deployPlayer(data, ranged("archer", 10000, 1, 1f), 2, 4); // 射程 3：开局即在射程
        List<WaveSpec> wave = waveOf(melee("target", "哥布林", 10000, 1, 1f), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);
        state.getUnits().get(1).setEnergy(95f); // 受击 +5 → 跨百（敌在 4 格外，首个能量事件必为受击）

        for (int i = 0; i < 60; i++) { // 弹道对开进目标：对向闭合约 7 格/秒
            SYSTEM.step(state);
        }
        int hitTick = -1;
        int castTick = -1;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.HIT && e.getTargetId() == 2 && hitTick < 0) {
                hitTick = e.getTick();
            }
            if (e.getType() == CombatEvent.Type.CAST && e.getSourceId() == 2) {
                castTick = e.getTick();
            }
        }
        assertThat(hitTick).isGreaterThan(0);
        assertThat(castTick).isEqualTo(hitTick); // 弹道命中与就地反打同 tick
    }

    @Test
    @DisplayName("确定性（验收 #4）：同 seed 同输入两次 runToEnd 事件流逐位 equals")
    void deterministicReplay() {
        GameData data = data();
        for (long seed : new long[]{42L, 7L}) {
            List<CombatEvent> first = runBattle(data, seed);
            List<CombatEvent> second = runBattle(data, seed);
            assertThat(second).isEqualTo(first);
        }
    }

    private static List<CombatEvent> runBattle(GameData data, long seed) {
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        Unit second = new Unit(2, data.getUnit("elf"), 1);
        player.addToBench(second);
        player.deploy(second, 3, 5);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1.3f, 2, 0, 3, 1, 1, 2);
        BattleState state = start(data, player, wave, seed);
        SYSTEM.runToEnd(state, 4000);
        return state.getEvents();
    }

    @Test
    @DisplayName("RNG 审计（验收 #3）：整场消耗 = 普攻发射次数（暴击 roll 唯一消耗点）")
    void rngConsumptionEqualsAttackRolls() {
        GameData data = data();
        Player player = deployPlayer(data, melee("striker", "兽人", 100, 40, 2f), 2, 4);
        Unit second = new Unit(2, data.getUnit("elf"), 1);
        player.addToBench(second);
        player.deploy(second, 3, 5);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1.2f, 2, 0, 3, 1);
        RandomGenerator rng = new RandomGenerator(99L);
        BattleState state = SYSTEM.startBattle(player, wave, data, rng, new SequentialIdIssuer());
        SYSTEM.runToEnd(state, 4000);

        int launches = 0;
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.ATTACK_LAUNCHED) {
                launches++;
            }
        }
        assertThat(launches).isGreaterThan(0);
        assertThat(rng.getConsumedCount()).isEqualTo(launches); // 除暴击 roll 外零消耗
    }

    // —— CP16：装备进战斗的两条通道（stat 修正源 + passiveStatus，Q1 裁决） ——

    private static Equipment sword() {
        return new Equipment(100, new EquipmentData("eq_sword", "铁剑",
                EquipmentSlot.WEAPON, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.PCT, 20f)), null));
    }

    @Test
    @DisplayName("装备 stat 通道：铁剑 attack PCT20 → 派生攻击 = 模板 ×1.2（无羁绊）；敌方无装备路径零串扰")
    void equipmentStatsFeedBaselineDerivation() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        player.getDeployedUnits().get(0).equip(sword()); // 单兽人不触发羁绊阈值
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1.4f, 2, 0);

        BattleState state = start(data, player, wave, 42L);
        assertThat(state.getUnits().get(0).getBaseStats().get(StatKey.ATTACK))
                .isCloseTo(12f, within(1e-4f)); // 10 × (1 + 0.20)
        assertThat(state.getUnits().get(1).getBaseStats().get(StatKey.ATTACK))
                .isCloseTo(14f, within(1e-4f)); // 敌方走空装备派生：10 × 1.4 不受玩家装备影响
    }

    @Test
    @DisplayName("龙心 passive 通道：HP +400 且常驻 REGEN（power 0.02 / interval 5s / ∞ 时长）")
    void dragonHeartHpAndRegenPassive() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        player.getDeployedUnits().get(0).equip(new Equipment(101, new EquipmentData("eq_heart", "龙心",
                EquipmentSlot.ARMOR, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f))));
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);

        BattleState state = start(data, player, wave, 42L);
        BattleUnit derived = state.getUnits().get(0);
        assertThat(derived.getBaseStats().get(StatKey.HP)).isCloseTo(500f, within(1e-4f));
        assertThat(derived.getCurrentHp()).isCloseTo(500f, within(1e-4f));
        ActiveStatus regen = null;
        for (ActiveStatus status : derived.getStatuses()) {
            if (status.getType() == StatusType.REGEN) {
                regen = status;
            }
        }
        assertThat(regen).isNotNull();
        assertThat(regen.getPower()).isCloseTo(0.02f, within(1e-6f));
        assertThat(regen.getTickInterval()).isCloseTo(5f, within(1e-6f));
        assertThat(regen.getRemainingTime()).isEqualTo(Float.POSITIVE_INFINITY);
    }

    @Test
    @DisplayName("穿脱影响即时性经每战重派生：卸下铁剑后重开战攻击回落模板值")
    void unequipRestoresStatsOnNextBattle() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        Unit wearer = player.getDeployedUnits().get(0);
        Equipment sword = sword();
        wearer.equip(sword);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
        assertThat(start(data, player, wave, 42L).getUnits().get(0)
                .getBaseStats().get(StatKey.ATTACK)).isCloseTo(12f, within(1e-4f));

        wearer.unequip(sword);
        assertThat(start(data, player, wave, 43L).getUnits().get(0)
                .getBaseStats().get(StatKey.ATTACK)).isCloseTo(10f, within(1e-4f));
    }

    // —— Phase 6：局外修正接入（CP10：玩家侧第 3 修正源 / 羁绊增幅 / 回归锚） ——

    @Test
    @DisplayName("「战歌」玩家侧生效敌方隔离：energyPp 15 → 玩家 115 / 敌方恒 100")
    void energyGainPlayerSideOnly() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
        RunModifiers orlando = new RunModifiers(0, 0, 0, 15,
                new LinkedHashMap<String, Float>(), null, new java.util.LinkedHashSet<String>(), false);
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), orlando);
        assertThat(state.getUnits().get(0).getEffective(StatKey.ENERGY_GAIN_RATE))
                .isCloseTo(115f, within(1e-4f)); // 玩家侧第 3 修正源注入（裁决 D13）
        assertThat(state.getUnits().get(1).getEffective(StatKey.ENERGY_GAIN_RATE))
                .isCloseTo(100f, within(1e-4f)); // 敌方侧恒不注入
    }

    @Test
    @DisplayName("「战歌」与法师羁绊同键叠加：energyGainRate ADD 15+15 → 130")
    void energyGainStacksWithMageSynergy() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("mage1", new UnitData("mage1", "夹具法师", "精灵", "法师", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false));
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_mage", new SynergyData("syn_mage", "法师", SynergySource.CLASS, "法师",
                Arrays.asList(new SynergyData.Threshold(4, Arrays.asList(
                        statEffect(StatKey.ENERGY_GAIN_RATE, EffectOp.ADD, 15f))))));
        GameData data = new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());

        Player player = new Player(10);
        for (int i = 0; i < 4; i++) {
            Unit unit = new Unit(100 + i, units.get("mage1"), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        RunModifiers orlando = new RunModifiers(0, 0, 0, 15,
                new LinkedHashMap<String, Float>(), null, new java.util.LinkedHashSet<String>(), false);
        BattleState state = SYSTEM.startBattle(player, new ArrayList<WaveSpec>(), data,
                new RandomGenerator(42L), new SequentialIdIssuer(), orlando);
        assertThat(state.getUnits().get(0).getEffective(StatKey.ENERGY_GAIN_RATE))
                .isCloseTo(130f, within(1e-4f)); // 羁绊 15 + 局外 15
    }

    @Test
    @DisplayName("「荆语」玩家侧增幅：2 兽人 hp 250 → 288（150 增幅为 188）；敌方同羁绊原值")
    void synergyAmpPlayerSideOnly() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        Unit second = new Unit(2, data.getUnit("orc"), 1);
        player.addToBench(second);
        player.deploy(second, 3, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);

        Map<String, Float> amp = new LinkedHashMap<String, Float>();
        amp.put("syn_orc", 0.25f);
        RunModifiers vera = new RunModifiers(0, 0, 0, 0, amp, null,
                new java.util.LinkedHashSet<String>(), false);
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), vera);
        assertThat(state.getUnits().get(0).getBaseStats().get(StatKey.HP))
                .isCloseTo(288f, within(1e-4f)); // (100 + 188)
        assertThat(state.getUnits().get(2).getBaseStats().get(StatKey.HP))
                .isCloseTo(100f, within(1e-4f)); // 敌方哥布林无兽人羁绊不受影响
    }

    @Test
    @DisplayName("EMPTY 修正回归锚：6 参重载（EMPTY）与旧 5 参输出全等（id/位置/派生属性）")
    void emptyOverloadMatchesLegacySignature() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        Unit second = new Unit(2, data.getUnit("orc"), 1);
        player.addToBench(second);
        player.deploy(second, 3, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1.4f, 2, 0);

        BattleState legacy = start(data, player, wave, 42L);
        BattleState withEmpty = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), RunModifiers.EMPTY);
        assertThat(withEmpty.getUnits()).extracting(BattleUnit::getId)
                .containsExactlyElementsOf(extractIds(legacy));
        for (int i = 0; i < legacy.getUnits().size(); i++) {
            assertThat(withEmpty.getUnits().get(i).getBaseStats().get(StatKey.HP))
                    .isCloseTo(legacy.getUnits().get(i).getBaseStats().get(StatKey.HP), within(1e-6f));
            assertThat(withEmpty.getUnits().get(i).getBaseStats().get(StatKey.ATTACK))
                    .isCloseTo(legacy.getUnits().get(i).getBaseStats().get(StatKey.ATTACK), within(1e-6f));
            assertThat(withEmpty.getUnits().get(i).getGridX()).isEqualTo(legacy.getUnits().get(i).getGridX());
            assertThat(withEmpty.getUnits().get(i).getGridY()).isEqualTo(legacy.getUnits().get(i).getGridY());
        }
    }

    private static List<Integer> extractIds(BattleState state) {
        List<Integer> ids = new ArrayList<Integer>();
        for (BattleUnit unit : state.getUnits()) {
            ids.add(unit.getId());
        }
        return ids;
    }
}
