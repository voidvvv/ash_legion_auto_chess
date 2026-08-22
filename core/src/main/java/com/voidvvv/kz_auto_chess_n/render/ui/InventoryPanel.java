package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;
import java.util.function.Supplier;

/**
 * ③ 装备背包 3×2（render §九；全程可见，BATTLE 期置灰 alpha 0.35——差异声明 #8）。
 * 显示前 6 件 + 总数角标（口径 #16 背包无上限）；两段式点击的起点（EquipPendingState）。
 * 槽位坐标用 {@link BoardGeometry#inventorySlotCenter} 绝对定位（组保持原点）。
 */
public final class InventoryPanel extends Group {

    private static final int VISIBLE_SLOTS = 6;

    /** 稀有度底色与待定高亮（static final：渲染段零分配） */
    private static final Color TINT_EMPTY = new Color(0.25f, 0.24f, 0.28f, 1f);
    private static final Color TINT_LEGENDARY = new Color(0.55f, 0.42f, 0.12f, 1f);
    private static final Color TINT_RARE = new Color(0.2f, 0.3f, 0.5f, 1f);
    private static final Color TINT_WHITE = new Color(0.32f, 0.32f, 0.34f, 1f);
    private static final Color TINT_PENDING = new Color(1f, 0.9f, 0.3f, 1f);

    private final Assets assets;
    private final Supplier<RunContext> context;
    private final EquipPendingState pending;
    /** 悬停中的背包槽位（feedback07：InventorySlot enter/exit 维护；-1 = 无；原始暴露，归一见 HoverPreviewCard） */
    private int hoveredSlot = -1;

    public InventoryPanel(Assets assets, Supplier<RunContext> context, EquipPendingState pending) {
        this.assets = assets;
        this.context = context;
        this.pending = pending;
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            addActor(new InventorySlot(i));
        }
    }

    /** 悬停中的背包槽位（feedback07）；无悬停 = -1（空槽/BATTLE 置灰期的归一在 HoverPreviewCard.refresh） */
    public int getHoveredSlot() {
        return hoveredSlot;
    }

    /** 每帧刷新（无内部缓存：待定高亮与置灰随 ctx 变化即时反映） */
    public void refresh() {
        reconcilePending(pending, context.get().getPlayer());
    }

    /** 待定物品已不在包（已穿/卖出）→ 失配自动取消；仍在包/无待定 → 保留/无操作 */
    static void reconcilePending(EquipPendingState pending, Player player) {
        if (pending.hasPending() && player.findInventoryItem(pending.pendingItemId()) == null) {
            pending.clear();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float alpha = context.get().getRunState().getPhase() == GamePhase.BATTLE
                ? parentAlpha * 0.35f : parentAlpha; // 战斗期置灰（差异声明 #8）
        super.draw(batch, alpha);
        List<Equipment> inventory = context.get().getPlayer().getInventory();
        if (inventory.size() > VISIBLE_SLOTS) {
            assets.font().draw(batch, "+" + (inventory.size() - VISIBLE_SLOTS),
                    BoardGeometry.INVENTORY_X + BoardGeometry.INVENTORY_W - 18f,
                    BoardGeometry.INVENTORY_Y + 10f);
        }
    }

    private final class InventorySlot extends Actor {
        private final int index;

        InventorySlot(int index) {
            this.index = index;
            int[] center = BoardGeometry.inventorySlotCenter(index);
            setSize(BoardGeometry.INVENTORY_SLOT_W, BoardGeometry.INVENTORY_SLOT_H);
            setPosition(center[0] - BoardGeometry.INVENTORY_SLOT_W / 2f,
                    center[1] - BoardGeometry.INVENTORY_SLOT_H / 2f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Equipment item = itemAt();
                    if (item == null) {
                        pending.clear(); // 点空白取消
                        return;
                    }
                    if (pending.pendingItemId() == item.getId()) {
                        pending.clear(); // 再点同一装备 = 取消（input §2.4）
                    } else {
                        pending.set(item.getId()); // 进入待定态（等棋子点击落点）
                    }
                }
            });
            addListener(new InputListener() { // feedback07 悬停槽位轨迹（Scene2D enter/exit，沿 ShopBar 先例）
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    hoveredSlot = index;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (hoveredSlot == index) {
                        hoveredSlot = -1;
                    }
                }
            });
        }

        private Equipment itemAt() {
            List<Equipment> inventory = context.get().getPlayer().getInventory();
            return index < inventory.size() ? inventory.get(index) : null;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Equipment item = itemAt();
            boolean isPendingSource = item != null && pending.pendingItemId() == item.getId();
            Color tint = isPendingSource ? TINT_PENDING : rarityTint(item);
            Color old = batch.getColor();
            batch.setColor(tint.r, tint.g, tint.b, (isPendingSource ? 1f : 0.9f) * parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            if (item != null) {
                assets.font().draw(batch, item.getTemplate().getName().substring(0,
                                Math.min(3, item.getTemplate().getName().length())),
                        getX() + 4f, getY() + 13f);
                assets.font().draw(batch, slotMark(item), getX() + 4f, getY() + 27f);
            }
        }
    }

    private static Color rarityTint(Equipment item) {
        if (item == null) {
            return TINT_EMPTY;
        }
        if (item.getTemplate().getRarity() == EquipmentRarity.LEGENDARY) {
            return TINT_LEGENDARY;
        }
        if (item.getTemplate().getRarity() == EquipmentRarity.RARE) {
            return TINT_RARE;
        }
        return TINT_WHITE;
    }

    private static String slotMark(Equipment item) {
        switch (item.getTemplate().getSlot()) {
            case WEAPON: return "武";
            case ARMOR: return "甲";
            case TRINKET: default: return "饰";
        }
    }
}
