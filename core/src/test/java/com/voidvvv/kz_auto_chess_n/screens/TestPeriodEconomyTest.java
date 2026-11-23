package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 品质改造批1（§0.1）验收件：各档买棋 / 刷新 / 买经验扣除数各检查一次——
 * 「卡片价格和扣款照常」（设计书 §0.1），测试档不豁免、正式档不豁免。
 * 走真实命令链（ShopSystem handler + CommandManager），商店槽位用 restoreSlots 受控注入。
 */
@DisplayName("各档经济扣除数：买棋 -1 / 刷新 -2 / 买经验 -4（品质改造 §0.1）")
class TestPeriodEconomyTest {

    /** 一费模板（真实数据，兽人战士 cost=1） */
    private static final String ONE_COST_ID = "unit_warrior_01";

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** 各档夹具：标签 + 模式 + 局外修正 + 预期起始金（正式口径 10/12/14 + 测试档 999） */
    private static final class Tier {
        final String label;
        final RunMode mode;
        final RunModifiers modifiers;
        final int expectedStartGold;

        Tier(String label, RunMode mode, RunModifiers modifiers, int expectedStartGold) {
            this.label = label;
            this.mode = mode;
            this.modifiers = modifiers;
            this.expectedStartGold = expectedStartGold;
        }
    }

    private static java.util.List<Tier> tiers() {
        return Arrays.asList(
                new Tier("正式·白板", RunMode.NORMAL, RunModifiers.EMPTY, 10),
                new Tier("正式·薇拉", RunMode.NORMAL, ProfileService.runModifiers(
                        data.getHero("hero_vera"), Profile.fresh(), data), 12),
                new Tier("正式·格雷克", RunMode.NORMAL, ProfileService.runModifiers(
                        data.getHero("hero_greg"), Profile.fresh(), data), 14),
                new Tier("测试档 999", RunMode.TEST_PERIOD_999, RunModifiers.EMPTY, 999));
    }

    @Test
    @DisplayName("四档各跑一次完整账本：买 1 费棋 -1、刷新 -2、买经验 -4（含 +4 经验升 Lv.2）")
    void deductionsPerTier() {
        for (Tier tier : tiers()) {
            ShopSystem shop = new ShopSystem();
            shop.restoreSlots(Arrays.asList(data.getUnit(ONE_COST_ID), null, null, null, null));
            RunContext ctx = BattleScreen.assembleContext(data, shop, 42L, "scene_forest",
                    null, tier.mode, tier.modifiers);
            assertThat(ctx.getPlayer().getGold())
                    .as("%s 起始金（实际余额）", tier.label).isEqualTo(tier.expectedStartGold);

            CommandManager manager = new CommandManager();
            shop.registerHandlers(manager);

            manager.addCommand(new BuyUnitCommand(0)); // 1 费兽人战士
            manager.executeAll(ctx);
            assertThat(ctx.getPlayer().getGold()).as("%s 买棋后", tier.label)
                    .isEqualTo(tier.expectedStartGold - 1);

            manager.addCommand(RefreshShopCommand.INSTANCE); // 2 金（EMPTY/格雷克 Lv.1 无刷新折扣）
            manager.executeAll(ctx);
            assertThat(ctx.getPlayer().getGold()).as("%s 刷新后", tier.label)
                    .isEqualTo(tier.expectedStartGold - 3);

            manager.addCommand(BuyExpCommand.INSTANCE); // 4 金 = 4 经验（首级 4 经验 → Lv.2）
            manager.executeAll(ctx);
            assertThat(ctx.getPlayer().getGold()).as("%s 买经验后", tier.label)
                    .isEqualTo(tier.expectedStartGold - 7);
            assertThat(ctx.getPlayer().getLevel()).as("%s 买经验升级", tier.label).isEqualTo(2);
            assertThat(ctx.getPlayer().getCurrentExp()).as("%s 升级余量", tier.label).isEqualTo(0);
        }
    }
}
