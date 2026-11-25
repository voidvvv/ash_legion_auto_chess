package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
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
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
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
                new BaseStats((int) hp, (int) atk, 0, aspd, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    private static UnitData ranged(String id, float hp, float atk, float aspd) {
        return new UnitData(id, "夹具" + id, "精灵", "游侠", 1,
                new BaseStats((int) hp, (int) atk, 0, aspd, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
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

    // —— 开战铺垫（battle §二）——

    @Test
    @DisplayName("开战转场冻结清单（battle §二）：转场期间 tick/elapsed/事件/RNG/计时器全冻结，归零后主循环起步")
    void introCountdownFreezesMainLoop() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 0);
        BattleState state = start(data, player, wave, 42L);

        assertThat(state.isIntroCountdownActive()).isTrue();
        assertThat(state.getIntroRemaining())
                .isCloseTo(GameBalance.BATTLE_INTRO_TRANSITION_SECONDS, within(1e-6f));
        int rngBefore = state.getRng().getConsumedCount();
        for (int i = 0; i < 35; i++) { // 0.6s = 36 步（浮点容差：35 步仍在转场）
            SYSTEM.step(state);
        }
        assertThat(state.getTick()).isZero();          // beginTick 未达
        assertThat(state.getElapsed()).isEqualTo(0f);   // 60s 超时钟不起表
        assertThat(state.getEvents()).isEmpty();        // 零 CombatEvent
        assertThat(state.getRng().getConsumedCount()).isEqualTo(rngBefore); // 零 RNG
        assertThat(state.getUnits().get(0).getAttackTimer()).isEqualTo(0f); // 计时器保持 0

        for (int i = 35; i < 41; i++) { // 转场归零后主循环刚起步（± 浮点余量，口径 K1）
            SYSTEM.step(state);
        }
        assertThat(state.isIntroCountdownActive()).isFalse();
        assertThat(state.getTick()).isBetween(1, 6);    // 主循环刚起步
        assertThat(state.getElapsed())                  // elapsed 恒 = tick × LOGIC_STEP（不含转场）
                .isCloseTo(state.getTick() * GameBalance.LOGIC_STEP, within(1e-5f));
    }

    @Test
    @DisplayName("首刀延后（§5.1 修订）：铺垫后从零蓄力，首个出手 = 满 1/(aspd×0.6) 秒")
    void firstAttackDelayedByFullInterval() {
        GameData data = data();
        Player player = deployPlayer(data, ranged("sentry", 10000, 1, 1f), 2, 4); // 射程 3，敌 (2,1) 距 3：开局即在射程
        List<WaveSpec> wave = waveOf(melee("dummy", "哥布林", 10000, 1, 1f), 1f, 2, 1);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 隔离变量：本用例只验证蓄力口径，不验证铺垫
        int launchTick = -1;
        for (int i = 0; i < 120 && launchTick < 0; i++) {
            SYSTEM.step(state);
            for (CombatEvent e : state.getEvents()) {
                if (e.getType() == CombatEvent.Type.ATTACK_LAUNCHED && e.getSourceId() == 1) {
                    launchTick = e.getTick();
                }
            }
        }
        assertThat(launchTick).isBetween(100, 101); // 1/(1×0.6) = 1.6667s ≈ 100 tick（浮点容差 ±1）
    }

    // —— 主循环 ——

    @Test
    @DisplayName("行动序 = id 升序：双方同 tick 进入射程时，首个 HIT 来源为玩家单位（id 小先手）")
    void actionOrderById() {
        GameData data = data();
        Player player = deployPlayer(data, data.getUnit("orc"), 2, 4);
        List<WaveSpec> wave = waveOf(data.getUnit("grunt"), 1f, 2, 1);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）

        for (int i = 0; i < 120; i++) { // 归零蓄力：接敌 60 tick + 首刀 100 tick（浮点余量）
            SYSTEM.step(state);
        }
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
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）
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

        for (int i = 0; i < 70; i++) { // 首步 60 tick（归零蓄力）后走步恢复
            SYSTEM.step(state);
        }
        assertThat(playerUnit.getGridY()).isLessThan(y);
    }

    @Test
    @DisplayName("计时器结转（口径 #4）：aspd 2 → 1/(2×0.6)s/击，300 tick 内 4~6 次普攻无掉次")
    void attackTimerCarryOver() {
        GameData data = data();
        Player player = deployPlayer(data, melee("tank", "兽人", 5000, 1, 2f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("etank", "哥布林", 5000, 1, 2f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）

        for (int i = 0; i < 300; i++) { // 间隔 50 tick + 接敌 60 tick：出手 60/110/…/260 共 5 次
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
        Player player = deployPlayer(data, melee("duel", "兽人", 200, 100, 1f), 2, 4);
        List<WaveSpec> wave = waveOf(melee("eduel", "哥布林", 200, 100, 1f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）

        // hp=2×atk：一刀（含暴击 150）必不致死、两刀（无暴击 100）必杀 → 同 tick 双灭与暴击 roll 序列无关
        SYSTEM.runToEnd(state, 400); // 双灭落 tick 200（间隔 100 + 接敌 60），留余量
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
                new BaseStats(100, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, TargetPriority.LOWEST_HP, "sk_hit", false);
        Unit observerUnit = new Unit(2, lowHpObserver, 1);
        player.addToBench(observerUnit);
        player.deploy(observerUnit, 1, 4);
        List<WaveSpec> wave = waveOf(melee("weak", "哥布林", 50, 0, 1f), 1f, 2, 0, 4, 0);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）

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
                new BaseStats(10000, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, TargetPriority.LOWEST_HP, "sk_hit", false);
        Player player = deployPlayer(data, lowHpObserver, 2, 4);
        List<WaveSpec> wave = waveOf(melee("e1", "哥布林", 1000, 1, 1f), 1f, 1, 0, 4, 0);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）
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

    // —— v5 undying 倒地全链（CP17，C1/C7）——

    /** 死骑型模板：undying 1.0s / 40% 血 */
    private static UnitData deathKnight() {
        return new UnitData("dk", "夹具死骑", "亡灵", "战士", 3,
                new BaseStats(200, 22, 13, 0.9f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false,
                null, new com.voidvvv.kz_auto_chess_n.data.UndyingSpec(1.0f, 0.4f));
    }

    @Test
    @DisplayName("死骑倒地链（C7）：首倒 UNIT_DOWNED 非死亡不判负 → 1s 后 UNIT_REVIVED 满能施放 → 二次倒下 UNIT_DIED 真死")
    void deathKnightDownsRevivesThenTrulyDies() {
        GameData data = data();
        Player player = deployPlayer(data, deathKnight(), 2, 4);
        List<WaveSpec> wave = waveOf(melee("eslayer", "哥布林", 100000, 300, 1f), 1f, 2, 2);
        BattleState state = start(data, player, wave, 42L);
        state.skipIntroCountdown();

        SYSTEM.runToEnd(state, 4000);

        List<CombatEvent> events = state.getEvents();
        long downedCount = events.stream()
                .filter(e -> e.getType() == CombatEvent.Type.UNIT_DOWNED).count();
        long revivedCount = events.stream()
                .filter(e -> e.getType() == CombatEvent.Type.UNIT_REVIVED).count();
        long diedCount = events.stream()
                .filter(e -> e.getType() == CombatEvent.Type.UNIT_DIED).count();
        assertThat(downedCount).isEqualTo(1); // 首倒分流
        assertThat(revivedCount).isEqualTo(1); // 1.0s 后站起
        assertThat(diedCount).isEqualTo(1);    // 二次 HP≤0 真死（仅一条 UNIT_DIED）

        // 复活后满能 → 下个行动 tick 施放（UNIT_REVIVED 之后存在死骑的 CAST）
        int revivedIdx = -1;
        int dkId = state.getUnits().get(0).getId();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getType() == CombatEvent.Type.UNIT_REVIVED) {
                revivedIdx = i;
            }
        }
        final int from = revivedIdx;
        assertThat(events.stream().skip(from)
                .anyMatch(e -> e.getType() == CombatEvent.Type.CAST && e.getSourceId() == dkId))
                .isTrue();

        assertThat(state.isOver()).isTrue();
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.ENEMY_WIN); // 死骑真死 → 玩家全灭
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
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）

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
        state.skipIntroCountdown(); // 既有用例关注主循环语义，跳过开战铺垫（battle §二新口径）
        state.getUnits().get(1).setEnergy(85f); // 敌近战 +10 → 95，弹道受击 +5 → 跨百（近战先于弹道落地，2026-09-02 修订）

        for (int i = 0; i < 240; i++) { // 走位 60 + 蓄力 100 + 弹道 ~30 tick（归零蓄力 + 系数 0.6）
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
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
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

    // —— v5 批0：D10 敌方羁绊档位截断（CP7）——

    @Test
    @DisplayName("D10 敌方档位截断：r2 双兽人不触发兽人(2)（cap 0）；r8 触发（cap 2 → hp+150）")
    void enemySynergyCappedByRound() {
        GameData data = data();
        Player player = deployPlayer(data, melee("lone", "哥布林", 100, 10, 1f), 2, 4);
        // 敌方两只兽人（夹具 syn_orc (2) 档 = hp ADD 150）
        List<WaveSpec> wave = waveOf(data.getUnit("orc"), 1f, 2, 0, 3, 0);

        BattleState round2 = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), RunModifiers.EMPTY, 2);
        assertThat(round2.getUnits().get(1).getBaseStats().get(StatKey.HP))
                .isCloseTo(100f, within(1e-4f)); // cap 0 → 敌方无羁绊

        BattleState round8 = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), RunModifiers.EMPTY, 8);
        assertThat(round8.getUnits().get(1).getBaseStats().get(StatKey.HP))
                .isCloseTo(250f, within(1e-4f)); // cap 2 → (2) 档 hp+150
        // 玩家侧不截断：旧六参重载委托 round=25（cap 6）与七参 25 输出全等
        BattleState legacy = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer());
        BattleState round25 = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer(), RunModifiers.EMPTY, 25);
        assertThat(round25.getUnits().get(1).getBaseStats().get(StatKey.HP))
                .isCloseTo(legacy.getUnits().get(1).getBaseStats().get(StatKey.HP), within(1e-6f));
    }

    @Test
    @DisplayName("机制词下发（CP7 基建）：TRAITS 过滤——兽人(6) SCAR 只下发兽人侧兽人系，人类杂兵不入")
    void mechanicsGrantedByTraitPredicate() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("orc1", new UnitData("orc1", "夹具兽人", "兽人", "战士", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false));
        units.put("grunt", new UnitData("grunt", "夹具杂兵", "哥布林", "战士", 1,
                new BaseStats(100, 10, 0, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false));
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_orc", new SynergyData("syn_orc", "兽人", SynergySource.RACE, "兽人",
                Arrays.asList(new SynergyData.Threshold(2, Arrays.asList(
                        new EffectData(null, null, SynergyMechanic.SCAR, null,
                                3f, 6f, null, null, EffectTarget.TRAITS, "兽人"))))));
        GameData data = new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(), new ArrayList<String>());

        Player player = new Player(10);
        for (int i = 0; i < 2; i++) {
            Unit unit = new Unit(100 + i, units.get("orc1"), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        Unit grunt = new Unit(200, units.get("grunt"), 1); // 同侧人类杂兵（职业战士）
        player.addToBench(grunt);
        player.deploy(grunt, 4, 4);
        List<WaveSpec> wave = waveOf(units.get("grunt"), 1f, 2, 0);

        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer());
        assertThat(state.getUnits().get(0).getMechanic(SynergyMechanic.SCAR)).isNotNull();  // 兽人命中
        assertThat(state.getUnits().get(1).getMechanic(SynergyMechanic.SCAR)).isNotNull();
        assertThat(state.getUnits().get(2).getMechanic(SynergyMechanic.SCAR)).isNull();     // 哥布林不入
        assertThat(state.getUnits().get(3).getMechanic(SynergyMechanic.SCAR)).isNull();     // 敌方不入（玩家侧羁绊）
    }

    // —— v5 批4：开局谓词 + 群星响应（CP22）——

    @Test
    @DisplayName("群星响应开局能量（CP22）：法师(6) OPENING_ENERGY 50 TRAITS → 仅法师系单位 +50，野兽杂兵与敌方不入")
    void openingEnergyByTraitsPredicate() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("mage", new UnitData("mage", "夹具法师", "精灵", "法师", 1,
                new BaseStats(100, 10, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false));
        units.put("beast", new UnitData("beast", "夹具野兽", "野兽", "游侠", 1,
                new BaseStats(100, 10, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false));
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_mage", new SynergyData("syn_mage", "法师", SynergySource.CLASS, "法师",
                Arrays.asList(new SynergyData.Threshold(6, Arrays.asList(
                        new EffectData(null, null, SynergyMechanic.OPENING_ENERGY, null,
                                50f, null, null, null, EffectTarget.TRAITS, "法师"))))));
        GameData data = new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());

        Player player = new Player(10);
        int roster = 1;
        for (int i = 0; i < 6; i++) { // 6 法师触发 (6) 档
            Unit unit = new Unit(roster++, units.get("mage"), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        Unit beast = new Unit(roster++, units.get("beast"), 1); // 同侧野兽杂兵（不入 TRAITS 法师）
        player.addToBench(beast);
        player.deploy(beast, 5, 5);
        List<WaveSpec> wave = waveOf(melee("grunt", "哥布林", 100, 10, 1f), 1f, 2, 0);

        BattleState state = start(data, player, wave, 42L);
        for (int i = 0; i < 6; i++) {
            assertThat(state.getUnits().get(i).getEnergy())
                    .as("法师系单位 %s 开局 +50", i).isEqualTo(50f);
        }
        assertThat(state.getUnits().get(6).getEnergy()).isEqualTo(0f);  // 野兽杂兵不入
        assertThat(state.getUnits().get(7).getEnergy()).isEqualTo(0f);  // 敌方不入（玩家侧快照）
    }

    @Test
    @DisplayName("开局通道 fail-fast（口径 #17 扩）：未知 effect（非 SHIELD/OPENING_ENERGY）抛错")
    void openingRejectsUnknownEffect() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        units.put("orc", melee("orc", "兽人", 100, 10, 1f));
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_bad", new SynergyData("syn_bad", "兽人", SynergySource.RACE, "兽人",
                Arrays.asList(new SynergyData.Threshold(2, Arrays.asList(
                        new EffectData(null, StatusType.STUN, null, null,
                                0.5f, null, null, null, EffectTarget.ALLIES, null))))));
        GameData data = new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());

        Player player = new Player(10);
        for (int i = 0; i < 2; i++) {
            Unit unit = new Unit(100 + i, units.get("orc"), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        start(data, player, waveOf(melee("g", "哥布林", 100, 10, 1f), 1f, 2, 0), 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("开局通道不支持的效果");
    }

    // —— v5 批4：三连装填（CP27，K10）——

    /** 游侠模板：critChance 可调（0 = 天然永不暴、100 = 天然必暴——对拍刀序确定） */
    private static UnitData ranger(String id, float hp, float atk, int critChance) {
        return new UnitData(id, "夹具" + id, "精灵", "游侠", 1,
                new BaseStats((int) hp, (int) atk, 0, 1f, 3, 1f, 0, 100, 0, critChance, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    /** 肉靶模板（range 3：与游侠互在射程 → 全程不动，ATTACK_LAUNCHED 序确定） */
    private static UnitData tank(String id) {
        return new UnitData(id, "夹具" + id, "哥布林", "游侠", 1,
                new BaseStats(100000, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    /** 无 CRIT_RHYTHM（A，threshold=0 无羁绊）/ 有 CRIT_RHYTHM 3/50（B）两个数据集——
     *  stat 通道全同，时间线全同；threshold 可调（6 游侠满编 / 1 单游侠），词值恒 3 刀必暴 +50pp */
    private static GameData rhythmData(boolean withRhythm) {
        return rhythmData(withRhythm ? 6 : 0);
    }

    private static GameData rhythmData(int threshold) {
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", hitSkill());
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        if (threshold > 0) {
            synergies.put("syn_ranger", new SynergyData("syn_ranger", "游侠", SynergySource.CLASS, "游侠",
                    Arrays.asList(new SynergyData.Threshold(threshold, Arrays.asList(
                            new EffectData(null, null, SynergyMechanic.CRIT_RHYTHM, null,
                                    3f, 50f, null, null, EffectTarget.TRAITS, "游侠"))))));
        }
        return new GameData(new LinkedHashMap<String, UnitData>(), skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());
    }

    /** 6 游侠（critChance=0）× 2 肉靶：同 seed 跑指定 tick 数（无死亡 → 时间线确定） */
    private static BattleState runRhythmBattle(GameData data, int ticks, long seed) {
        Player player = new Player(10);
        for (int i = 0; i < 6; i++) {
            Unit unit = new Unit(100 + i, ranger("r" + i, 100000, 10, 0), 1);
            player.addToBench(unit);
            player.deploy(unit, i, 4);
        }
        List<WaveSpec> wave = waveOf(tank("t1"), 1f, 1, 2, 4, 2);
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(seed), new SequentialIdIssuer());
        state.skipIntroCountdown();
        for (int i = 0; i < ticks; i++) {
            SYSTEM.step(state);
        }
        return state;
    }

    private static List<CombatEvent> attacksOf(BattleState state) {
        List<CombatEvent> attacks = new ArrayList<CombatEvent>();
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.ATTACK_LAUNCHED) {
                attacks.add(e);
            }
        }
        return attacks;
    }

    /** 按出手单位分组的普攻命中序列（skillId=null 的 HIT；限玩家侧——肉靶反打的普攻不入对拍） */
    private static Map<Integer, List<CombatEvent>> basicHitsBySource(BattleState state) {
        Map<Integer, List<CombatEvent>> hits = new LinkedHashMap<Integer, List<CombatEvent>>();
        for (CombatEvent e : state.getEvents()) {
            if (e.getType() == CombatEvent.Type.HIT && e.getSkillId() == null
                    && state.getUnitById(e.getSourceId()).getSide() == Side.PLAYER) {
                List<CombatEvent> list = hits.get(e.getSourceId());
                if (list == null) {
                    list = new ArrayList<CombatEvent>();
                    hits.put(e.getSourceId(), list);
                }
                list.add(e);
            }
        }
        return hits;
    }

    @Test
    @DisplayName("三连同 seed 对拍（K10 确定性红线）：有/无 CRIT_RHYTHM 两场 ATTACK_LAUNCHED 逐位一致、RNG 消耗一致——覆写不增耗")
    void critRhythmOverwriteKeepsRngOrder() {
        BattleState plain = runRhythmBattle(rhythmData(false), 700, 42L);
        BattleState rhythm = runRhythmBattle(rhythmData(true), 700, 42L);

        List<CombatEvent> attacksA = attacksOf(plain);
        List<CombatEvent> attacksB = attacksOf(rhythm);
        assertThat(attacksA.size()).isGreaterThan(12);            // 每游侠 ≥2 刀（样本充分）
        assertThat(attacksB).isEqualTo(attacksA);                 // 出手序逐位一致（覆写不改时间线）
        assertThat(rhythm.getRng().getConsumedCount())
                .isEqualTo(plain.getRng().getConsumedCount());    // RNG 消耗序不变（零新增消耗点）
        assertThat(rhythm.getRng().getConsumedCount())
                .isEqualTo(attacksB.size());                      // 仍 = 暴击 roll 唯一消耗点

        Map<Integer, List<CombatEvent>> hitsA = basicHitsBySource(plain);
        Map<Integer, List<CombatEvent>> hitsB = basicHitsBySource(rhythm);
        assertThat(hitsA.size()).isEqualTo(6);
        for (Map.Entry<Integer, List<CombatEvent>> entry : hitsB.entrySet()) {
            List<CombatEvent> hitA = hitsA.get(entry.getKey());
            List<CombatEvent> hitB = entry.getValue();
            assertThat(hitB).hasSameSizeAs(hitA); // 命中序一致（时间线未漂移）
            for (int i = 0; i < hitB.size(); i++) {
                int blade = i + 1;
                if (blade % 3 == 0) {
                    assertThat(hitB.get(i).isCrit())
                            .as("单位 %s 第 %d 刀必暴", entry.getKey(), blade).isTrue();
                    assertThat(hitB.get(i).getAmount())
                            .as("必暴刀 ×2.0（150+50）").isCloseTo(hitA.get(i).getAmount() * 2f, within(1e-3f));
                } else {
                    assertThat(hitB.get(i).isCrit()).isFalse();   // critChance=0：非必暴刀照 roll 结果
                    assertThat(hitB.get(i).getAmount()).isCloseTo(hitA.get(i).getAmount(), within(1e-6f));
                }
            }
            for (CombatEvent hit : hitA) {                        // 无机制场：全程零暴击
                assertThat(hit.isCrit()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("三连计数跨目标延续：第 1 刀杀敌换目标后，第 3 刀（总计数）仍必暴（per-unit 累计）")
    void critRhythmCounterPersistsAcrossTargets() {
        Player player = new Player(10);
        Unit hunter = new Unit(1, ranger("hunter", 1000, 100, 0), 1); // 一刀 100（无暴）
        player.addToBench(hunter);
        player.deploy(hunter, 2, 4);
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        wave.add(new WaveSpec(new UnitData("prey1", "夹具猎物1", "哥布林", "游侠", 1,
                new BaseStats(100, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false), 1, 1f, 1, 2));
        wave.add(new WaveSpec(new UnitData("prey2", "夹具猎物2", "哥布林", "游侠", 1,
                new BaseStats(250, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false), 1, 1f, 4, 2));

        BattleState state = SYSTEM.startBattle(player, wave, rhythmData(1),
                new RandomGenerator(42L), new SequentialIdIssuer());
        state.skipIntroCountdown();
        SYSTEM.runToEnd(state, 600);

        List<CombatEvent> hunterHits = basicHitsBySource(state).get(1);
        assertThat(hunterHits).hasSize(3);                       // 刀 1 杀 prey1；刀 2/3 打 prey2
        assertThat(hunterHits.get(0).isCrit()).isFalse();        // 第 1 刀（prey1，致死）
        assertThat(hunterHits.get(1).isCrit()).isFalse();        // 第 2 刀（prey2，跨目标延续计数）
        assertThat(hunterHits.get(2).isCrit()).isTrue();         // 第 3 刀必暴（总计数 3）
        assertThat(hunterHits.get(2).getAmount()).isCloseTo(200f, within(1e-3f)); // 100×2.0
        assertThat(state.getOutcome()).isEqualTo(BattleOutcome.PLAYER_WIN);
    }

    @Test
    @DisplayName("暴伤 +50pp 只作用必暴刀（R2 口径）：critChance=100 时 1/2 刀 ×1.5、第 3 刀 ×2.0")
    void critRhythmBonusOnlyOnForcedBlade() {
        Player player = new Player(10);
        Unit hunter = new Unit(1, ranger("surer", 100000, 10, 100), 1); // 天然刀刀必暴
        player.addToBench(hunter);
        player.deploy(hunter, 2, 4);
        List<WaveSpec> wave = waveOf(tank("t1"), 1f, 2, 2);

        BattleState state = SYSTEM.startBattle(player, wave, rhythmData(1),
                new RandomGenerator(42L), new SequentialIdIssuer());
        state.skipIntroCountdown();
        for (int i = 0; i < 360; i++) { // ~3 刀（首刀 ~100 tick 发射 + 弹道 ~24 tick + 间隔 100）
            SYSTEM.step(state);
        }

        List<CombatEvent> hits = basicHitsBySource(state).get(1);
        assertThat(hits.size()).isGreaterThanOrEqualTo(3);
        assertThat(hits.get(0).getAmount()).isCloseTo(15f, within(1e-3f)); // ×1.5（天然暴，无加成）
        assertThat(hits.get(1).getAmount()).isCloseTo(15f, within(1e-3f)); // ×1.5
        assertThat(hits.get(2).getAmount()).isCloseTo(20f, within(1e-3f)); // ×2.0（必暴刀 150+50）
    }
}
