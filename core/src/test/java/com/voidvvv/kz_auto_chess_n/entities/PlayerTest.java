package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Player（Phase 1 版：金币/经验/等级）测试——GDD §3.5 经验表驱动升级。
 * 名单（备战席/上场部署）随 Unit 实体在 Phase 3 增补（project_structure §六出生时间表）。
 * Phase 5 CP6 增补：背包与按 id 名单操作、undeploy 席满收口。
 */
class PlayerTest {

    // —— 名单夹具：最小模板 + 1 星单位 ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "skill_warcry", false);
    }

    private static Unit unit(int id) {
        return new Unit(id, tpl("u" + id), 1);
    }

    private static EquipmentData eqTpl(String id, EquipmentSlot slot) {
        return new EquipmentData(id, "夹具" + id, slot, EquipmentRarity.WHITE,
                Collections.singletonList(new EquipmentEffect(StatKey.ATTACK, EffectOp.ADD, 1f)), null);
    }

    private static Equipment item(int id, EquipmentSlot slot) {
        return new Equipment(id, eqTpl("eq" + id, slot));
    }

    @Test
    @DisplayName("初始状态：起始金币10、1级、0经验、人口上限3")
    void initialState() {
        Player player = new Player(10);
        assertThat(player.getGold()).isEqualTo(10);
        assertThat(player.getLevel()).isEqualTo(1);
        assertThat(player.getCurrentExp()).isEqualTo(0);
        assertThat(player.getPopulationCap()).isEqualTo(3); // Lv.1 人口 3
    }

    // —— 复原构造与名单整体替换（快照轨，Phase 6 CP16）——

    @Test
    @DisplayName("复原构造：gold/level/exp 透传；等级越界即死；负经验钳 0")
    void restoreConstructorValidates() {
        Player restored = new Player(37, 3, 5);
        assertThat(restored.getGold()).isEqualTo(37);
        assertThat(restored.getLevel()).isEqualTo(3);
        assertThat(restored.getCurrentExp()).isEqualTo(5);

        assertThatThrownBy(() -> new Player(10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("棋手等级");
        assertThatThrownBy(() -> new Player(10, 8, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
        assertThat(new Player(10, 2, -7).getCurrentExp()).isZero(); // 负经验防御钳 0
    }

    @Test
    @DisplayName("restoreRoster：整体替换备战席与 18 格部署表（空格 null 原样）")
    void restoreRosterReplacesBothStores() {
        Player player = new Player(10);
        player.addToBench(unit(1)); // 旧状态应被整体替换
        Unit benchUnit = unit(2);
        Unit deployedUnit = unit(3);
        Unit[] grid = new Unit[18];
        grid[7] = deployedUnit;
        player.restoreRoster(java.util.Collections.singletonList(benchUnit), grid);
        assertThat(player.getBench()).containsExactly(benchUnit);
        assertThat(player.getDeployedUnits()).containsExactly(deployedUnit);
        assertThat(player.deployedAt(1, 4)).isNull(); // 空格原样 null
        assertThat(player.getRosterSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("restoreRoster 校验：超席/长度≠18/留空位越界即死")
    void restoreRosterValidatesBounds() {
        Player player = new Player(10);
        java.util.List<Unit> tooMany = new java.util.ArrayList<Unit>();
        for (int i = 0; i < GameBalance.BENCH_SIZE + 1; i++) {
            tooMany.add(unit(100 + i));
        }
        assertThatThrownBy(() -> player.restoreRoster(tooMany, new Unit[18]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("备战席");
        assertThatThrownBy(() -> player.restoreRoster(java.util.Collections.<Unit>emptyList(), new Unit[17]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("部署表长度");
    }

    @Test
    @DisplayName("加经验恰好升级，余数归零")
    void levelsUpWithExactExp() {
        Player player = new Player(10);
        player.addExp(4); // Lv.1→2 需 4
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(0);
        assertThat(player.getPopulationCap()).isEqualTo(4);
    }

    @Test
    @DisplayName("加经验跨级保留余数")
    void keepsRemainderExpAcrossLevels() {
        Player player = new Player(10);
        player.addExp(8); // 4 升 Lv.2，余 4 存入 Lv.2（Lv.2→3 需 8）
        assertThat(player.getLevel()).isEqualTo(2);
        assertThat(player.getCurrentExp()).isEqualTo(4);
    }

    @Test
    @DisplayName("大额经验连跳至封顶 Lv.7，封顶后经验不再累积")
    void capsAtMaxLevelAndDiscardsExtraExp() {
        Player player = new Player(10);
        player.addExp(1000);
        assertThat(player.getLevel()).isEqualTo(7);
        assertThat(player.getPopulationCap()).isEqualTo(9); // Lv.7 人口上限 9
        assertThat(player.getCurrentExp()).isEqualTo(0);    // 封顶后余量作废

        player.addExp(4); // 封顶后再买经验无效
        assertThat(player.getLevel()).isEqualTo(7);
        assertThat(player.getCurrentExp()).isEqualTo(0);
    }

    @Test
    @DisplayName("金币增减不透支为负")
    void goldNeverGoesNegative() {
        Player player = new Player(10);
        player.addGold(-5);
        assertThat(player.getGold()).isEqualTo(5);
        player.addGold(-99); // 防御性钳制（命令层已校验，此处兜底）
        assertThat(player.getGold()).isEqualTo(0);
        player.addGold(3);
        assertThat(player.getGold()).isEqualTo(3);
    }

    @Test
    @DisplayName("金币充足判定")
    void canAffordChecksGold() {
        Player player = new Player(4);
        assertThat(player.canAfford(4)).isTrue();
        assertThat(player.canAfford(5)).isFalse();
    }

    // —— Phase 3 名单增补：bench 9 格 + 部署表 18 格 ——

    @Test
    @DisplayName("备战席初始为空，getBench 返回不可变视图")
    void benchStartsEmptyWithUnmodifiableView() {
        Player player = new Player(10);
        assertThat(player.getBench()).isEmpty();
        assertThatThrownBy(() -> player.getBench().add(unit(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("addToBench 入席并计入 rosterSize")
    void addToBenchCountsIntoRosterSize() {
        Player player = new Player(10);
        Unit a = unit(1);
        Unit b = unit(2);
        player.addToBench(a);
        player.addToBench(b);
        assertThat(player.getBench()).containsExactly(a, b);
        assertThat(player.getRosterSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("备战席满 9 格再入席抛 IllegalStateException（防御兜底）")
    void benchFullThrows() {
        Player player = new Player(10);
        for (int i = 1; i <= 9; i++) {
            player.addToBench(unit(i));
        }
        assertThatThrownBy(() -> player.addToBench(unit(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("insertToBench 插入指定槽位，后续单位后移（入席序即展示序，口径 #8）")
    void insertToBenchShiftsLaterUnits() {
        Player player = new Player(10);
        Unit a = unit(1);
        Unit b = unit(2);
        Unit c = unit(3);
        player.addToBench(a);
        player.addToBench(b);
        player.addToBench(c);
        Unit d = unit(4);
        player.insertToBench(d, 1);
        assertThat(player.getBench()).containsExactly(a, d, b, c);
    }

    @Test
    @DisplayName("insertToBench 索引钳制 [0,size]：负数归 0、超 size 落末位")
    void insertToBenchClampsIndex() {
        Player player = new Player(10);
        Unit a = unit(1);
        Unit b = unit(2);
        player.addToBench(a);
        player.addToBench(b);
        Unit c = unit(3);
        player.insertToBench(c, -5); // 钳到 0
        assertThat(player.getBench()).containsExactly(c, a, b);
        Unit d = unit(4);
        player.insertToBench(d, 99); // 钳到 size（末位）
        assertThat(player.getBench()).containsExactly(c, a, b, d);
    }

    @Test
    @DisplayName("insertToBench 满 9 格抛 IllegalStateException（同 addToBench 兜底）")
    void insertToBenchThrowsWhenFull() {
        Player player = new Player(10);
        for (int i = 1; i <= 9; i++) {
            player.addToBench(unit(i));
        }
        assertThatThrownBy(() -> player.insertToBench(unit(10), 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("removeFromBench 移除指定单位；不在席抛 IllegalArgumentException")
    void removeFromBenchValidatesMembership() {
        Player player = new Player(10);
        Unit a = unit(1);
        player.addToBench(a);
        player.removeFromBench(a);
        assertThat(player.getBench()).isEmpty();

        assertThatThrownBy(() -> player.removeFromBench(a))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deploy 自动从备战席摘除并落格，deployedAt 可查")
    void deployRemovesFromBenchAndOccupiesCell() {
        Player player = new Player(10);
        Unit a = unit(1);
        player.addToBench(a);
        player.deploy(a, 2, 5);
        assertThat(player.getBench()).isEmpty();
        assertThat(player.deployedAt(2, 5)).isSameAs(a);
        assertThat(player.getRosterSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("deploy 越界抛错：缓冲行 y=3、界外 y=7、界外 x=6 均拒绝")
    void deployRejectsOutOfPlayerZone() {
        Player player = new Player(10);
        player.addToBench(unit(1));
        player.addToBench(unit(2));
        player.addToBench(unit(3));
        assertThatThrownBy(() -> player.deploy(unit(1), 0, 3)) // 缓冲行不可部署
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.deploy(unit(2), 0, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.deploy(unit(3), 6, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deploy 目标格已占用抛 IllegalStateException")
    void deployRejectsOccupiedCell() {
        Player player = new Player(10);
        player.addToBench(unit(1));
        player.addToBench(unit(2));
        player.deploy(unit(1), 2, 4);
        assertThatThrownBy(() -> player.deploy(unit(2), 2, 4))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deploy 要求单位在备战席（名单一致性：非席内单位拒绝）")
    void deployRequiresBenchMembership() {
        Player player = new Player(10);
        assertThatThrownBy(() -> player.deploy(unit(9), 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("undeploy 清格并回备战席；空格 undeploy 抛错")
    void undeployReturnsUnitToBench() {
        Player player = new Player(10);
        Unit a = unit(1);
        player.addToBench(a);
        player.deploy(a, 3, 6);
        player.undeploy(3, 6);
        assertThat(player.deployedAt(3, 6)).isNull();
        assertThat(player.getBench()).containsExactly(a);

        assertThatThrownBy(() -> player.undeploy(3, 6))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getDeployedUnits 扫描序 y↑x↑（确定性序 = 开战发号序，口径 #16）")
    void deployedUnitsScanInRowMajorOrder() {
        Player player = new Player(10);
        Unit u5 = unit(5);   // (1,5)
        Unit u4 = unit(4);   // (4,4)
        Unit u6 = unit(6);   // (4,6)
        player.addToBench(u4);
        player.addToBench(u5);
        player.addToBench(u6);
        player.deploy(u5, 1, 5);
        player.deploy(u4, 4, 4);
        player.deploy(u6, 4, 6);
        List<Unit> deployed = player.getDeployedUnits();
        assertThat(deployed).containsExactly(u4, u5, u6); // (4,4) → (1,5) → (4,6)
    }

    // —— Phase 5 CP6：undeploy 席满收口 + 背包与按 id 名单操作 ——

    @Test
    @DisplayName("undeploy 备战席已满抛 IllegalStateException（Phase 3 假设洞收口）")
    void undeployThrowsWhenBenchFull() {
        Player player = new Player(10);
        for (int i = 1; i <= 9; i++) {
            player.addToBench(unit(i));
        }
        Unit deployed = unit(9);
        player.deploy(deployed, 0, 4);  // 席 8，腾出一格给部署
        player.addToBench(unit(10));    // 席 9（满）
        assertThatThrownBy(() -> player.undeploy(0, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("备战席已满");
        assertThat(player.deployedAt(0, 4)).isSameAs(deployed); // 状态未变
    }

    @Test
    @DisplayName("背包增删与视图：入包序、不可变视图、出包不在抛 IllegalArgumentException")
    void inventoryAddRemoveWithUnmodifiableView() {
        Player player = new Player(10);
        Equipment weapon = item(11, EquipmentSlot.WEAPON);
        Equipment armor = item(12, EquipmentSlot.ARMOR);
        player.addToInventory(weapon);
        player.addToInventory(armor);
        assertThat(player.getInventory()).containsExactly(weapon, armor);
        assertThatThrownBy(() -> player.getInventory().add(item(13, EquipmentSlot.TRINKET)))
                .isInstanceOf(UnsupportedOperationException.class);

        player.removeFromInventory(weapon);
        assertThat(player.getInventory()).containsExactly(armor);
        assertThatThrownBy(() -> player.removeFromInventory(weapon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("装备不在背包");
    }

    @Test
    @DisplayName("findInventoryItem 按 id 查背包；未找到返回 null")
    void findInventoryItemById() {
        Player player = new Player(10);
        Equipment weapon = item(11, EquipmentSlot.WEAPON);
        player.addToInventory(weapon);
        assertThat(player.findInventoryItem(11)).isSameAs(weapon);
        assertThat(player.findInventoryItem(99)).isNull();
    }

    @Test
    @DisplayName("getUnitById 三态：席上 / 板上 / 未找到")
    void getUnitByIdCoversBenchAndDeployment() {
        Player player = new Player(10);
        Unit benchUnit = unit(1);
        Unit boardUnit = unit(2);
        player.addToBench(benchUnit);
        player.addToBench(boardUnit);
        player.deploy(boardUnit, 3, 5);
        assertThat(player.getUnitById(1)).isSameAs(benchUnit);
        assertThat(player.getUnitById(2)).isSameAs(boardUnit);
        assertThat(player.getUnitById(99)).isNull();
    }

    @Test
    @DisplayName("removeUnit 三态：席上移除 / 板上移除并释放人口 / 不在名单返回 false")
    void removeUnitCoversBenchAndDeployment() {
        Player player = new Player(10);
        Unit benchUnit = unit(1);
        Unit boardUnit = unit(2);
        player.addToBench(benchUnit);
        player.addToBench(boardUnit);
        player.deploy(boardUnit, 3, 5);
        assertThat(player.getRosterSize()).isEqualTo(2);

        assertThat(player.removeUnit(benchUnit)).isTrue();
        assertThat(player.getBench()).isEmpty();

        assertThat(player.removeUnit(boardUnit)).isTrue();
        assertThat(player.deployedAt(3, 5)).isNull();
        assertThat(player.getRosterSize()).isZero();

        assertThat(player.removeUnit(unit(9))).isFalse();
    }

    @Test
    @DisplayName("findEquipOwner 按 id 找穿戴者：席上 / 板上 / 未找到")
    void findEquipOwnerAcrossRoster() {
        Player player = new Player(10);
        Unit benchUnit = unit(1);
        Unit boardUnit = unit(2);
        Equipment benchItem = item(11, EquipmentSlot.WEAPON);
        Equipment boardItem = item(12, EquipmentSlot.ARMOR);
        benchUnit.equip(benchItem);
        boardUnit.equip(boardItem);
        player.addToBench(benchUnit);
        player.addToBench(boardUnit);
        player.deploy(boardUnit, 3, 5);

        assertThat(player.findEquipOwner(11)).isSameAs(benchUnit);
        assertThat(player.findEquipOwner(12)).isSameAs(boardUnit);
        assertThat(player.findEquipOwner(99)).isNull();
    }
}
