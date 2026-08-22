package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.function.Supplier;

/**
 * ⑧ 商店栏（render §九；仅 SHOPPING）：5 卡 + 刷新（2 金）+ 买经验（4 金）。
 * 点击卡片入队 BuyUnit(slot)——查价不信任载荷（input §6.3）；灰置 = 表现层预校验
 * （金币不足/席满不立即合成/空槽/Lv.7 封顶），最终防线在 ShopSystem handler。
 *
 * <p>坐标为组内局部坐标（组定位 0,{@code BoardGeometry.SHOP_BAR_Y} 归 CP29 装配）。
 * 按钮宽 80：两钮收在 [472,636]——原稿 560+100=660 越出 640 虚拟宽 20px（T6 执行适配）。
 */
public final class ShopBar extends Group {

    private static final float CARD_W = 84f;
    private static final float CARD_H = 56f;
    private static final float CARD_GAP = 8f;
    private static final float CARD_X0 = 12f;
    private static final float BUTTON_X_REFRESH = 472f;
    private static final float BUTTON_X_EXP = 556f;

    private final CommandManager commandManager;
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final boolean[] affordable = new boolean[GameBalance.SHOP_SLOTS];

    public ShopBar(CommandManager commandManager, Assets assets, Supplier<RunContext> context) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.context = context;
        for (int i = 0; i < GameBalance.SHOP_SLOTS; i++) {
            addActor(new ShopCard(i));
        }
        Actor refresh = new ActionButton("REFRESH 2G") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(RefreshShopCommand.INSTANCE);
            }
        };
        refresh.setPosition(BUTTON_X_REFRESH, 14f);
        addActor(refresh);
        Actor exp = new ActionButton("EXP 4G") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(BuyExpCommand.INSTANCE);
            }
        };
        exp.setPosition(BUTTON_X_EXP, 14f);
        addActor(exp);
    }

    /** 每帧刷新预校验态（只读；SHOPPING 期 Screen 调用） */
    public void refresh(RunContext ctx) {
        for (int i = 0; i < affordable.length; i++) {
            affordable[i] = canBuy(ctx, i);
        }
    }

    /**
     * 预校验（input §4.3 只读）：空槽/金币不足/席满不立即合成 → 灰置；
     * 席满例外 = 名单已有同名一星 ×2（购买即 3 合 1，不占席）。不改状态不产生命令。
     */
    static boolean canBuy(RunContext ctx, int slot) {
        UnitData template = ctx.getShop().slotAt(slot);
        return template != null
                && ctx.getPlayer().canAfford(template.getCost())
                && (ctx.getPlayer().getBench().size() < GameBalance.BENCH_SIZE
                    || countSameTemplateStar1(ctx, template.getId()) >= 2);
    }

    private static int countSameTemplateStar1(RunContext ctx, String templateId) {
        int count = 0;
        for (Unit unit : ctx.getPlayer().getBench()) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == 1) {
                count++;
            }
        }
        for (Unit unit : ctx.getPlayer().getDeployedUnits()) {
            if (unit.getTemplate().getId().equals(templateId) && unit.getStar() == 1) {
                count++;
            }
        }
        return count;
    }

    /** 商店卡（占位帧 + 费价 + 灰置态） */
    private final class ShopCard extends Actor {
        private final int slot;

        ShopCard(int slot) {
            this.slot = slot;
            setSize(CARD_W, CARD_H);
            setPosition(CARD_X0 + slot * (CARD_W + CARD_GAP), 4f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new BuyUnitCommand(slot));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            RunContext ctx = context.get();
            UnitData template = ctx.getShop().slotAt(slot);
            if (template == null) {
                return; // 已购空槽：不绘制（点击预校验在 handler 拒绝）
            }
            boolean can = affordable[slot];
            Color old = batch.getColor();
            batch.setColor(can ? 0.3f : 0.18f, can ? 0.32f : 0.18f, can ? 0.38f : 0.2f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            batch.draw(assets.region(PlaceholderKeys.unitFrame(template.getId(), PlaceholderKeys.ANIM_IDLE, 0)),
                    getX() + 4f, getY() + 18f, 32f, 32f);
            assets.font().draw(batch, String.valueOf(template.getCost()) + "G", getX() + 44f, getY() + 44f);
            assets.font().draw(batch, template.getName(), getX() + 6f, getY() + 12f);
        }
    }

    /** 矩形动作按钮（刷新/买经验共用壳） */
    private abstract class ActionButton extends Actor {
        private final String text;

        ActionButton(String text) {
            this.text = text;
            setSize(80f, 36f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            });
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.32f, 0.36f, 0.3f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, text, getX() + 8f, getY() + 22f);
        }
    }
}
