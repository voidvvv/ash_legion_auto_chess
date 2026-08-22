package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.function.Supplier;

/**
 * 棋子详情弹窗（Phase 4 欠账；dialogStage 弹窗族）。MVP：名/星/spend/模板属性 + 三槽卸下 + 关闭。
 * 每帧 refresh（名单/装备可能经命令变化）；单位已不在名单 → 请求关闭（isExpired）。
 * unitGone 为包级静态纯函数（headless 已测——计划测试要点给出的提取方案）。
 */
public final class UnitDetailDialog extends Group {

    /** 关闭请求（Screen 实现：dialogManager.closeTop） */
    public interface CloseListener {
        void onCloseRequested();
    }

    private final CommandManager commandManager;
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final CloseListener closeListener;
    private int unitId = -1;

    public UnitDetailDialog(CommandManager commandManager, Assets assets,
                            Supplier<RunContext> context, CloseListener closeListener) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.context = context;
        this.closeListener = closeListener;
        Actor close = new CloseButton();
        close.setPosition(310f, 200f);
        addActor(close);
    }

    /** 打开（棋盘域点击回调） */
    public void showUnit(int unitId) {
        this.unitId = unitId;
    }

    /** 单位已不在名单（板/席，被卖出/合并）→ true，装配点据此收起 */
    public boolean isExpired() {
        return unitGone(context.get(), unitId);
    }

    /** 过期判定（纯函数，headless 可测；未打开的 -1 与不存在 id 同判过期） */
    static boolean unitGone(RunContext ctx, int unitId) {
        return ctx.getPlayer().getUnitById(unitId) == null;
    }

    /** 每帧刷新：三槽卸下按钮重建（装备集变化幂等——按钮为轻量 Actor） */
    public void refresh() {
        for (int i = getChildren().size - 1; i >= 0; i--) {
            if (getChildren().get(i) instanceof UnequipButton) {
                getChildren().get(i).remove();
            }
        }
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            return;
        }
        float y = 170f;
        for (Equipment item : unit.getEquipped()) {
            UnequipButton button = new UnequipButton(item);
            button.setPosition(150f, y);
            addActor(button);
            y -= 30f;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            return;
        }
        Color old = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.75f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 70f, 60f, 380f, 180f);
        batch.setColor(old);
        UnitData template = unit.getTemplate();
        BaseStats stats = template.getBaseStats();
        assets.font().draw(batch, template.getName() + "  " + unit.getStar() + "星"
                + "  spend " + unit.getSpend(), 90f, 215f);
        assets.font().draw(batch, "HP " + stats.getHp() + "  ATK " + stats.getAttack()
                + "  ARMOR " + stats.getArmor(), 90f, 195f);
        assets.font().draw(batch, "ASPD " + stats.getAttackSpeed() + "  RANGE " + stats.getRange()
                + "  MSPD " + stats.getMoveSpeed(), 90f, 178f);
        super.draw(batch, parentAlpha); // 卸下/关闭按钮
    }

    private final class CloseButton extends Actor {
        CloseButton() {
            setSize(48f, 22f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    closeListener.onCloseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.4f, 0.32f, 0.32f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "X", getX() + 20f, getY() + 15f);
        }
    }

    /** 单件卸下按钮（UnequipItem 命令路径，input §2.4） */
    private final class UnequipButton extends Actor {
        private final Equipment item;

        UnequipButton(final Equipment item) {
            this.item = item;
            setSize(200f, 24f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new UnequipItemCommand(item.getId()));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.3f, 0.34f, 0.42f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, item.getTemplate().getName() + "  [卸下]",
                    getX() + 8f, getY() + 16f);
        }
    }
}
