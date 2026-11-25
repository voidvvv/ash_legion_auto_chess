package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
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
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.skill;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 卫队战吼（CP23，K7）全链路测试：经 startBattle 调度 + step 消费。
 * 全员 range 3 且开局即在射程 → 全程零走位，坐标/距离断言稳定；
 * 观察者用 HIGHEST_ATK 索敌——高攻战士 ≠ 旗手，嘲讽前后目标可区分。
 */
class TimedTriggerSystemTest {

    private static final BattleSystem SYSTEM = new BattleSystem();

    /** 战士模板（含机制候选；atk 可挑高攻区分 HIGHEST_ATK 索敌） */
    private static UnitData warrior(String id, float atk) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(10000, (int) atk, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, null, "sk_hit", false);
    }

    /** 观察者模板（HIGHEST_ATK 锁高攻战士——非旗手） */
    private static UnitData observer(String id, float hp) {
        return new UnitData(id, "夹具" + id, "哥布林", "游侠", 1,
                new BaseStats((int) hp, 1, 0, 1f, 3, 1f, 0, 100, 0, 20, 150), 1.8f,
                TargetPriority.NEAREST, TargetPriority.HIGHEST_ATK, "sk_hit", false);
    }

    /** syn_warrior (4)：WARCRY_TAUNT 6s 延迟 / 3s 持续 / TRAITS 战士 */
    private static GameData data() {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put("sk_hit", skill("sk_hit", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 1f, null, null)));
        Map<String, SynergyData> synergies = new LinkedHashMap<String, SynergyData>();
        synergies.put("syn_warrior", new SynergyData("syn_warrior", "战士", SynergySource.CLASS, "战士",
                Arrays.asList(new SynergyData.Threshold(4, Arrays.asList(
                        new EffectData(null, null,
                                com.voidvvv.kz_auto_chess_n.data.SynergyMechanic.WARCRY_TAUNT,
                                null, 6f, 3f, null, null, EffectTarget.TRAITS, "战士"))))));
        return new GameData(units, skills, synergies,
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());
    }

    private static void deploy(Player player, int rosterId, UnitData template, int x, int y) {
        Unit unit = new Unit(rosterId, template, 1); // roster id 仅备战记账，战斗 id 由发号器另发
        player.addToBench(unit);
        player.deploy(unit, x, y);
    }

    private static List<WaveSpec> wave(WaveSpec... specs) {
        return Arrays.asList(specs);
    }

    private static WaveSpec at(UnitData template, int x, int y) {
        return new WaveSpec(template, 1, 1f, x, y);
    }

    @Test
    @DisplayName("战吼全周期：6s（tick 360）恰触发——半径 1 内敌 targetId=旗手、圈外不动；480 重评估被豁免；540 到期、600 恢复自由索敌")
    void warcryTriggerExpiryAndRetargetCycle() {
        GameData data = data();
        Player player = new Player(10);
        int roster = 1;
        deploy(player, roster++, warrior("w_banner", 1), 0, 4); // 旗手：min y 平局 min x
        deploy(player, roster++, warrior("w2", 1), 1, 4);
        deploy(player, roster++, warrior("w3", 1), 3, 4);
        deploy(player, roster++, warrior("w_magnet", 50), 2, 4); // HIGHEST_ATK 磁铁（非旗手）
        List<WaveSpec> wave = wave(at(observer("e_in", 10000), 0, 3),   // 距旗手 1：半径内
                at(observer("e_out", 10000), 2, 3),                     // 距旗手 3：圈外
                at(observer("e_far", 10000), 4, 3));                    // 距旗手 7：圈外
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer());
        state.skipIntroCountdown();

        BattleUnit banner = state.getUnits().get(0);  // 玩家扫描序首个 = (0,4)
        BattleUnit magnet = state.getUnits().get(2);  // (2,4) atk 50（扫描序 y↑x↑ 排第 3）
        BattleUnit inRadius = state.getUnits().get(4); // 敌 e_in (0,3)
        BattleUnit outside = state.getUnits().get(5);  // 敌 e_out (2,3)
        assertThat(inRadius.getTargetId()).isEqualTo(magnet.getId()); // 嘲讽前：HIGHEST_ATK

        for (int i = 0; i < 359; i++) {
            SYSTEM.step(state);
        }
        assertThat(state.getTick()).isEqualTo(359);
        assertThat(inRadius.getTargetId()).isEqualTo(magnet.getId()); // 未到 6s 不触发
        assertThat(inRadius.isTaunted(state.getTick())).isFalse();

        SYSTEM.step(state); // tick 360 = 6s：战吼触发
        assertThat(inRadius.isTaunted(state.getTick())).isTrue();
        assertThat(inRadius.getTargetId()).isEqualTo(banner.getId()); // 半径内强制指向旗手
        assertThat(outside.getTargetId()).isEqualTo(magnet.getId()); // 圈外不受影响

        for (int i = 0; i < 120; i++) {
            SYSTEM.step(state); // tick 480：retargetAll 全局重评估
        }
        assertThat(inRadius.getTargetId()).isEqualTo(banner.getId()); // 嘲讽期豁免重评估

        assertThat(inRadius.isTaunted(539)).isTrue();  // 3s = 180 tick：[360, 540)
        assertThat(inRadius.isTaunted(540)).isFalse();
        for (int i = 0; i < 120; i++) {
            SYSTEM.step(state); // 推到 tick 600：到期后首次全局重评估
        }
        assertThat(inRadius.isTaunted(state.getTick())).isFalse();
        assertThat(inRadius.getTargetId()).isEqualTo(magnet.getId()); // 恢复自由索敌（HIGHEST_ATK）
    }

    @Test
    @DisplayName("旗手死亡失效：被嘲讽者下一行动 tick 解除嘲讽并立即重选（K7「目标失效立即解除」）")
    void tauntBreaksWhenBannerDies() {
        GameData data = data();
        Player player = new Player(10);
        int roster = 1;
        // 单敌 (0,3)：四战士全部落其射程 3 内（(0,5) 距 2）→ 全程零走位，旗手恒为 (0,4)
        deploy(player, roster++, warrior("w_banner", 1), 0, 4);
        deploy(player, roster++, warrior("w2", 1), 1, 4);
        deploy(player, roster++, warrior("w_magnet", 50), 2, 4);
        deploy(player, roster++, warrior("w3", 1), 0, 5);
        List<WaveSpec> wave = wave(at(observer("e_in", 100000), 0, 3));
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer());
        state.skipIntroCountdown();

        BattleUnit banner = state.getUnits().get(0);
        BattleUnit magnet = state.getUnits().get(2); // (2,4) atk 50
        BattleUnit inRadius = state.getUnits().get(4);
        for (int i = 0; i < 360; i++) {
            SYSTEM.step(state); // 触发战吼
        }
        assertThat(inRadius.isTaunted(state.getTick())).isTrue();

        state.removeFromGrid(banner); // 测试充当 DeathSystem：旗手被清扫
        banner.markCleaned();
        SYSTEM.step(state); // 下一行动 tick：解除嘲讽 + 常规重选

        assertThat(inRadius.isTaunted(state.getTick())).isFalse();
        assertThat(inRadius.getTargetId()).isEqualTo(magnet.getId()); // 立即恢复 HIGHEST_ATK
    }

    @Test
    @DisplayName("敌方对称（K7）：敌方 4 战士触发 → 旗手=max y，半径内玩家单位被嘲讽指向敌旗手")
    void warcrySymmetricOnEnemySide() {
        GameData data = data();
        Player player = new Player(10);
        int roster = 1;
        deploy(player, roster++, observer("p_obs", 10000), 2, 4); // 玩家观察者：HIGHEST_ATK
        List<WaveSpec> wave = wave(at(warrior("ew_banner", 1), 2, 3), // 敌旗手：max y
                at(warrior("ew2", 1), 2, 2),
                at(warrior("ew_magnet", 50), 3, 2),
                at(warrior("ew4", 1), 1, 2));
        BattleState state = SYSTEM.startBattle(player, wave, data,
                new RandomGenerator(42L), new SequentialIdIssuer());
        state.skipIntroCountdown();

        BattleUnit playerObserver = state.getUnits().get(0);
        BattleUnit enemyBanner = state.getUnits().get(1); // 敌 (2,3)
        BattleUnit enemyMagnet = state.getUnits().get(3); // 敌 (3,2) atk 50
        assertThat(playerObserver.getTargetId()).isEqualTo(enemyMagnet.getId()); // 嘲讽前 HIGHEST_ATK

        for (int i = 0; i < 359; i++) {
            SYSTEM.step(state);
        }
        assertThat(playerObserver.getTargetId()).isEqualTo(enemyMagnet.getId()); // 未触发

        SYSTEM.step(state); // tick 360
        assertThat(playerObserver.isTaunted(state.getTick())).isTrue();
        assertThat(playerObserver.getTargetId()).isEqualTo(enemyBanner.getId()); // 距敌旗手 |2-2|+|4-3|=1
    }
}
