package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.base;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.state;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.tpl;
import static com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 索敌系统测试（battle §三优先链）：NEAREST / BACKLINE（口径 #23）/ LOWEST_HP / HIGHEST_ATK；
 * 平局链 = 距离 → id 升序；候选 = 存活敌方（含濒死未清扫）。
 */
class TargetingSystemTest {

    private static final TargetingSystem SYSTEM = new TargetingSystem();

    @Test
    @DisplayName("NEAREST：选曼哈顿最近敌方")
    void nearestPicksClosest() {
        BattleUnit self = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit far = unit(2, Side.ENEMY, tpl("e2"), 5, 0);
        BattleUnit near = unit(3, Side.ENEMY, tpl("e3"), 2, 1);
        BattleState state = state(self, far, near);
        assertThat(SYSTEM.findTarget(state, self)).isSameAs(near);
    }

    @Test
    @DisplayName("NEAREST 平局：等距取 id 小者")
    void nearestTieBreaksById() {
        BattleUnit self = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit a = unit(5, Side.ENEMY, tpl("e5"), 1, 2); // d=3
        BattleUnit b = unit(4, Side.ENEMY, tpl("e4"), 3, 2); // d=3
        BattleState state = state(self, a, b);
        assertThat(SYSTEM.findTarget(state, self)).isSameAs(b); // id 4 < 5
    }

    @Test
    @DisplayName("BACKLINE 纵深 |y-3|：敌方 y0 与玩家 y6 对称最深（口径 #23）")
    void backlineDepthSymmetric() {
        UnitData backlineTpl = tpl("p", base(100, 10, 5, 1f, 1, 1f), TargetPriority.BACKLINE);
        // 玩家视角：敌方 y0（纵深 3）优先于 y2（纵深 1）
        BattleUnit self = unit(1, Side.PLAYER, backlineTpl, 2, 4);
        BattleUnit shallow = unit(2, Side.ENEMY, tpl("e2"), 1, 2);
        BattleUnit deep = unit(3, Side.ENEMY, tpl("e3"), 4, 0);
        assertThat(SYSTEM.findTarget(state(self, shallow, deep), self)).isSameAs(deep);

        // 敌方视角：玩家 y6（纵深 3）优先于 y4（纵深 1）——两侧对称成立
        BattleUnit enemySelf = unit(4, Side.ENEMY, backlineTpl, 2, 0);
        BattleUnit playerShallow = unit(5, Side.PLAYER, tpl("e5"), 1, 4);
        BattleUnit playerDeep = unit(6, Side.PLAYER, tpl("e6"), 4, 6);
        assertThat(SYSTEM.findTarget(state(enemySelf, playerShallow, playerDeep), enemySelf))
                .isSameAs(playerDeep);
    }

    @Test
    @DisplayName("BACKLINE 平局：同纵深取距离近者，再 id 小者")
    void backlineTieBreaksByDistanceThenId() {
        UnitData backlineTpl = tpl("p", base(100, 10, 5, 1f, 1, 1f), TargetPriority.BACKLINE);
        BattleUnit self = unit(1, Side.PLAYER, backlineTpl, 0, 5);
        BattleUnit sameRowFar = unit(2, Side.ENEMY, tpl("e2"), 5, 0); // 纵深 3，d=5+5=10
        BattleUnit sameRowNear = unit(3, Side.ENEMY, tpl("e3"), 1, 0); // 纵深 3，d=1+5=6
        assertThat(SYSTEM.findTarget(state(self, sameRowFar, sameRowNear), self)).isSameAs(sameRowNear);
    }

    @Test
    @DisplayName("LOWEST_HP：选血量比例最低者（非绝对值）")
    void lowestHpPicksByRatio() {
        UnitData lowHpTpl = tpl("p", base(200, 10, 5, 1f, 1, 1f), TargetPriority.LOWEST_HP);
        BattleUnit self = unit(1, Side.PLAYER, lowHpTpl, 2, 4);
        BattleUnit bigHpHurt = unit(2, Side.ENEMY, tpl("e2"), 0, 0); // hp100 满
        bigHpHurt.modifyHp(-50f); // ratio 0.5
        BattleUnit smallHpHurt = unit(3, Side.ENEMY, tpl("e3"), 1, 0); // hp100
        smallHpHurt.modifyHp(-80f); // ratio 0.2
        assertThat(SYSTEM.findTarget(state(self, bigHpHurt, smallHpHurt), self)).isSameAs(smallHpHurt);
    }

    @Test
    @DisplayName("HIGHEST_ATK：选有效攻击最高者（含 ATK_UP 加成后的有效值）")
    void highestAtkPicksEffectiveAttack() {
        UnitData atkTpl = tpl("p", base(100, 10, 5, 1f, 1, 1f), TargetPriority.HIGHEST_ATK);
        BattleUnit self = unit(1, Side.PLAYER, atkTpl, 2, 4);
        BattleUnit plain = unit(2, Side.ENEMY, tpl("e2"), 0, 0); // atk 10
        BattleUnit buffed = unit(3, Side.ENEMY, tpl("e3"), 1, 0); // atk 10 → 挂 ATK_UP 30 → 13
        buffed.addStatus(new com.voidvvv.kz_auto_chess_n.entities.ActiveStatus(
                com.voidvvv.kz_auto_chess_n.data.StatusType.ATK_UP, 9, 30f, 5f));
        assertThat(SYSTEM.findTarget(state(self, plain, buffed), self)).isSameAs(buffed);
    }

    @Test
    @DisplayName("specialPriority 覆盖 defaultPriority（UnitData 词表口径）")
    void specialPriorityOverridesDefault() {
        // 默认 NEAREST、特化 BACKLINE：两个敌方等深时选纵深大者而非最近者
        UnitData tplSpecial = tpl("p", base(100, 10, 5, 1f, 1, 1f), TargetPriority.BACKLINE);
        BattleUnit self = unit(1, Side.PLAYER, tplSpecial, 2, 4);
        BattleUnit nearShallow = unit(2, Side.ENEMY, tpl("e2"), 2, 2); // d=2，纵深 1
        BattleUnit farDeep = unit(3, Side.ENEMY, tpl("e3"), 5, 0);    // d=7，纵深 3
        assertThat(SYSTEM.findTarget(state(self, nearShallow, farDeep), self)).isSameAs(farDeep);
    }

    @Test
    @DisplayName("排除友方与已清扫单位；濒死未清扫仍是合法目标")
    void candidatesFilterAndDyingStillValid() {
        BattleUnit self = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit friend = unit(2, Side.PLAYER, tpl("f"), 2, 2);
        BattleUnit dead = unit(3, Side.ENEMY, tpl("e3"), 1, 2);
        dead.markCleaned();
        BattleUnit dying = unit(4, Side.ENEMY, tpl("e4"), 5, 0);
        dying.modifyHp(-999f); // hp 0 未清扫
        assertThat(SYSTEM.findTarget(state(self, friend, dead, dying), self)).isSameAs(dying);
    }

    @Test
    @DisplayName("无候选返回 null（敌方全灭/全清扫）")
    void noCandidatesReturnsNull() {
        BattleUnit self = unit(1, Side.PLAYER, tpl("p"), 2, 4);
        BattleUnit dead = unit(2, Side.ENEMY, tpl("e2"), 0, 0);
        dead.markCleaned();
        assertThat(SYSTEM.findTarget(state(self, dead), self)).isNull();
    }

    @Test
    @DisplayName("retargetAll 按 id 序为全部未清扫单位设置目标（无候选置 -1）")
    void retargetAllAssignsEveryAliveUnit() {
        BattleUnit p1 = unit(1, Side.PLAYER, tpl("p1"), 2, 4);
        BattleUnit p2 = unit(2, Side.PLAYER, tpl("p2"), 3, 4);
        BattleUnit e3 = unit(3, Side.ENEMY, tpl("e3"), 2, 0);
        BattleUnit e4 = unit(4, Side.ENEMY, tpl("e4"), 3, 0);
        BattleState state = state(p1, p2, e3, e4);
        SYSTEM.retargetAll(state);
        assertThat(p1.getTargetId()).isEqualTo(3); // (2,0) 距 (2,4) 最近
        assertThat(p2.getTargetId()).isEqualTo(4); // (3,0) 距 (3,4) 最近
        assertThat(e3.getTargetId()).isEqualTo(1);
        assertThat(e4.getTargetId()).isEqualTo(2);

        e3.markCleaned();
        SYSTEM.retargetAll(state);
        assertThat(p1.getTargetId()).isEqualTo(4); // 全局强制重评估
        assertThat(p2.getTargetId()).isEqualTo(4);

        e4.markCleaned();
        SYSTEM.retargetAll(state);
        assertThat(p1.getTargetId()).isEqualTo(-1); // 无候选
    }

    @Test
    @DisplayName("retargetOnDeath 只影响指向亡者的单位")
    void retargetOnDeathOnlyRetargetsDependents() {
        BattleUnit p1 = unit(1, Side.PLAYER, tpl("p1"), 1, 4);
        BattleUnit p2 = unit(2, Side.PLAYER, tpl("p2"), 4, 4);
        BattleUnit e3 = unit(3, Side.ENEMY, tpl("e3"), 1, 0);
        BattleUnit e4 = unit(4, Side.ENEMY, tpl("e4"), 4, 0);
        BattleState state = state(p1, p2, e3, e4);
        p1.setTargetId(3);
        p2.setTargetId(4);
        e3.markCleaned();
        SYSTEM.retargetOnDeath(state, 3);
        assertThat(p1.getTargetId()).isEqualTo(4); // 指向亡者 → 重选
        assertThat(p2.getTargetId()).isEqualTo(4); // 未指向亡者 → 不动
    }
}
