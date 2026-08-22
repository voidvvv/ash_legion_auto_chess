package com.voidvvv.kz_auto_chess_n.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 新命令 ×9（CP8）纯载荷测试：getter/toString/INSTANCE 单例性与构造校验。
 * 命令为纯数据载体（input §4.1 禁业务方法）——只验证载荷，不验证执行语义（归各系统测试）。
 */
class GameCommandTest {

    // —— StartRun（开局域边界事件，第 12 命令）——

    @Test
    @DisplayName("StartRunCommand：seed/sceneId/heroId 载荷与 toString；heroId=null 合法（Phase 6 扩展位）")
    void startRunPayload() {
        StartRunCommand cmd = new StartRunCommand(42L, "scene_forest", null);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getSeed()).isEqualTo(42L);
        assertThat(cmd.getSceneId()).isEqualTo("scene_forest");
        assertThat(cmd.getHeroId()).isNull();
        assertThat(cmd.toString()).isEqualTo("StartRun(seed=42, scene=scene_forest, hero=null)");
    }

    @Test
    @DisplayName("StartRunCommand：sceneId 为 null 构造拒绝")
    void startRunRejectsNullScene() {
        assertThatThrownBy(() -> new StartRunCommand(42L, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sceneId");
    }

    // —— 经营命令载荷 ——

    @Test
    @DisplayName("BuyUnitCommand：载荷仅槽位索引（查价不信任载荷）")
    void buyUnitPayload() {
        BuyUnitCommand cmd = new BuyUnitCommand(3);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getSlotIndex()).isEqualTo(3);
        assertThat(cmd.toString()).isEqualTo("BuyUnit(slot=3)");
    }

    @Test
    @DisplayName("SellUnitCommand：载荷仅 unitId（返还 = Unit.spend 100%）")
    void sellUnitPayload() {
        SellUnitCommand cmd = new SellUnitCommand(7);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getUnitId()).isEqualTo(7);
        assertThat(cmd.toString()).isEqualTo("SellUnit(unit=7)");
    }

    @Test
    @DisplayName("RefreshShopCommand：无载荷 INSTANCE 单例（沿 SurrenderCommand 先例）")
    void refreshShopSingleton() {
        assertThat(RefreshShopCommand.INSTANCE).isInstanceOf(GameCommand.class);
        assertThat(RefreshShopCommand.INSTANCE).isSameAs(RefreshShopCommand.INSTANCE);
        assertThat(RefreshShopCommand.INSTANCE.toString()).isEqualTo("RefreshShop");
    }

    @Test
    @DisplayName("BuyExpCommand：无载荷 INSTANCE 单例")
    void buyExpSingleton() {
        assertThat(BuyExpCommand.INSTANCE).isInstanceOf(GameCommand.class);
        assertThat(BuyExpCommand.INSTANCE).isSameAs(BuyExpCommand.INSTANCE);
        assertThat(BuyExpCommand.INSTANCE.toString()).isEqualTo("BuyExp");
    }

    @Test
    @DisplayName("EquipItemCommand：载荷 itemId + unitId（槽位由装备类型推导）")
    void equipItemPayload() {
        EquipItemCommand cmd = new EquipItemCommand(11, 3);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getItemId()).isEqualTo(11);
        assertThat(cmd.getUnitId()).isEqualTo(3);
        assertThat(cmd.toString()).isEqualTo("EquipItem(item=11, unit=3)");
    }

    @Test
    @DisplayName("UnequipItemCommand：载荷仅 itemId（穿戴者由名单扫描）")
    void unequipItemPayload() {
        UnequipItemCommand cmd = new UnequipItemCommand(5);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getItemId()).isEqualTo(5);
        assertThat(cmd.toString()).isEqualTo("UnequipItem(item=5)");
    }

    @Test
    @DisplayName("PickChestCommand：载荷仅选项索引（内容进 RESULT 时已 roll 好，零 RNG）")
    void pickChestPayload() {
        PickChestCommand cmd = new PickChestCommand(2);
        assertThat(cmd).isInstanceOf(GameCommand.class);
        assertThat(cmd.getOptionIndex()).isEqualTo(2);
        assertThat(cmd.toString()).isEqualTo("PickChest(option=2)");
    }

    @Test
    @DisplayName("AbandonRunCommand：无载荷 INSTANCE 单例")
    void abandonRunSingleton() {
        assertThat(AbandonRunCommand.INSTANCE).isInstanceOf(GameCommand.class);
        assertThat(AbandonRunCommand.INSTANCE).isSameAs(AbandonRunCommand.INSTANCE);
        assertThat(AbandonRunCommand.INSTANCE.toString()).isEqualTo("AbandonRun");
    }
}
