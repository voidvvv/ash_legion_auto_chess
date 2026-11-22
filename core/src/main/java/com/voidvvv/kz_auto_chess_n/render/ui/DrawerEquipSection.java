package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.UiKeys;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 抽屉装备区（UI 抽屉化批3·Q1/Q5/Q6；接替 ③ {@code InventoryPanel}）：
 * <ul>
 *   <li><b>装备网格 = 背包库存</b>：3 列 × 2 行可见 + 纵向滚动（取消「前 6 件 +N」，背包无上限的完整列表）；
 *       槽可点击——点装备 → {@link EquipPendingState} 待定 → 点棋子（头像格或棋盘）穿上，两段式流程不变；</li>
 *   <li><b>装备大图预览 + 装备描述</b>：预览目标 = 悬停槽的物品，否则待定物品；描述走 ScrollPane 可滚到底；</li>
 *   <li><b>写门控</b>（Q6）：仅备战期可点装备格（战斗期只读展开，悬停预览仍按归一规则抑制）。</li>
 * </ul>
 * 稀有度底色 / 待定高亮 / 槽位短标沿 InventoryPanel 写法；只读实体、零命令（穿戴走棋盘点击回调，卸下在信息区）。
 * 坐标 = 抽屉局部（本组位于主体原点）。
 */
final class DrawerEquipSection extends Group {

    /** 装备图标位：图标 ×2（16 → 32px） */
    private static final float ICON_SCALE = 2f;
    private static final float SLOT_PAD = 2f;
    private static final float SLOT_TEXT_PAD = 3f;
    /** 稀有度底色叠在槽底的透明度（槽框仍可辨） */
    private static final float RARITY_ALPHA = 0.35f;
    private static final float PENDING_ALPHA = 0.9f;
    private static final String NO_ITEM_PLACEHOLDER = "（点击或悬停装备格查看）";

    private final Assets assets;
    private final EquipPendingState pending;
    private final TextureRegion white;
    private final DrawerGeometry.Rect track = DrawerGeometry.equipScrollBar();
    private final EquipSlot[] slots = new EquipSlot[DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS];
    private final DrawerScrollKnob knob;
    private final EquipPreview preview;
    /** 首个可见行（库存下标 = 首行 × 3） */
    private int firstRow;
    /** 悬停中的可见槽位（原始；-1 = 无）；归一见 bind */
    private int hoveredVisible = -1;
    /** 最近一次绑定时的库存条目数（拖滑块换算用） */
    private int boundCount;
    /** 当前相位（写门控；DrawerPanel.refresh 每帧同步） */
    private GamePhase phase = GamePhase.SHOPPING;

    DrawerEquipSection(Assets assets, NinePatch slotPatch, NinePatch selectedPatch, NinePatch insetPatch,
                       NinePatch trackPatch, EquipPendingState pending) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.pending = Objects.requireNonNull(pending, "pending 不能为 null");
        this.white = assets.region(PlaceholderKeys.WHITE);
        setTouchable(Touchable.childrenOnly);
        setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new EquipSlot(i, slotPatch, selectedPatch);
            addActor(slots[i]);
        }
        addActor(new DrawerSkin.PatchActor(trackPatch, track));
        knob = new DrawerScrollKnob(assets.uiRegion(UiKeys.SCROLL_KNOB.id()), DrawerScrollKnob.VERTICAL,
                DrawerGeometry.EQUIP_KNOB, new DrawerScrollKnob.DragListener() {
                    @Override
                    public void onDrag(float knobEdge) {
                        firstRow = DrawerEquipScroll.firstRowForKnob(track.height(), DrawerGeometry.EQUIP_KNOB,
                                (int) (knobEdge - track.y()), boundCount);
                    }
                });
        addActor(knob);
        preview = new EquipPreview(insetPatch);
        addActor(preview);
    }

    /** 首个可见行 */
    int firstRow() {
        return firstRow;
    }

    /** 滚轮落在装备区：滚动若干行（按库存条目数钳制） */
    void scrollRows(int steps, int itemCount) {
        firstRow = DrawerEquipScroll.scrollRows(firstRow, itemCount, steps);
    }

    void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    /**
     * 每帧绑定：钳制首行、落位滑块、把库存映射到可见槽、归一悬停、刷新预览。
     *
     * @param inventory 背包（只读）
     * @param dragging  拖拽中（悬停抑制，口径 B4-2）
     */
    void bind(List<Equipment> inventory, boolean dragging) {
        boundCount = inventory.size();
        firstRow = DrawerEquipScroll.clampFirstRow(firstRow, boundCount);
        int hover = DrawerEquipText.normalizeHover(phase, hoveredVisible, boundCount, dragging);
        int first = DrawerEquipScroll.firstIndex(firstRow);
        Equipment hoveredItem = null;
        for (int i = 0; i < slots.length; i++) {
            int index = first + i;
            Equipment item = index < boundCount ? inventory.get(index) : null;
            boolean isHover = hover == i && item != null;
            slots[i].bind(item, pending.pendingItemId(), isHover);
            if (isHover) {
                hoveredItem = item;
            }
        }
        knob.place(track.y() + DrawerEquipScroll.knobOffsetY(track.height(), DrawerGeometry.EQUIP_KNOB, firstRow, boundCount),
                track.x() + track.width() / 2f);
        preview.setItem(hoveredItem != null ? hoveredItem
                : pending.hasPending() ? inventoryItem(inventory, pending.pendingItemId()) : null);
    }

    private static Equipment inventoryItem(List<Equipment> inventory, int itemId) {
        for (Equipment item : inventory) {
            if (item.getId() == itemId) {
                return item;
            }
        }
        return null;
    }

    private TextureRegion iconRegion(EquipmentSlot slot) {
        switch (slot) {
            case ARMOR:
                return assets.uiRegion(UiKeys.ICON_EQUIP_ARMOR.id());
            case TRINKET:
                return assets.uiRegion(UiKeys.ICON_EQUIP_TRINKET.id());
            case WEAPON:
            default:
                return assets.uiRegion(UiKeys.ICON_EQUIP_WEAPON.id());
        }
    }

    // —— 件 ——

    /** 装备格（可点击：两段式穿戴起点；enter/exit 记悬停） */
    private final class EquipSlot extends Actor {
        private final int visibleIndex;
        private final NinePatch slotPatch;
        private final NinePatch selectedPatch;
        private final DrawerGeometry.Rect rect;
        private Equipment item;
        private boolean pendingSource;
        private boolean hovered;

        EquipSlot(int visibleIndex, NinePatch slotPatch, NinePatch selectedPatch) {
            this.visibleIndex = visibleIndex;
            this.slotPatch = slotPatch;
            this.selectedPatch = selectedPatch;
            this.rect = DrawerGeometry.equipSlot(visibleIndex, 0);
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!DrawerEquipText.writesAllowed(phase)) {
                        return; // Q6：战斗期只读展开，禁止写操作
                    }
                    int next = DrawerEquipText.nextPending(pending.pendingItemId(), item);
                    if (next >= 0) {
                        pending.set(next); // 进入待定态（等棋子点击落点）
                    } else {
                        pending.clear(); // 点空白 / 再点同一装备 = 取消（input §2.4）
                    }
                }
            });
            addListener(new InputListener() { // 悬停槽位轨迹（沿 InventoryPanel/ShopBar 先例）
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    hoveredVisible = visibleIndex;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (hoveredVisible == visibleIndex) {
                        hoveredVisible = -1;
                    }
                }
            });
        }

        void bind(Equipment item, int pendingItemId, boolean hovered) {
            this.item = item;
            this.pendingSource = item != null && item.getId() == pendingItemId;
            this.hovered = hovered;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = DrawerSkin.alphaOf(this, parentAlpha);
            DrawerSkin.drawPatch(batch, slotPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            if (item != null) {
                Color tint = DrawerEquipText.rarityTint(item);
                tintOverlay(batch, tint, RARITY_ALPHA * alpha);
                EquipmentData template = item.getTemplate();
                BitmapFont font = assets.font();
                font.getData().setScale(DrawerSkin.SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
                DrawerSkin.drawText(batch, font, DrawerEquipText.shortName(template.getName()),
                        rect.x() + SLOT_TEXT_PAD, rect.y() + rect.height() - SLOT_PAD - DrawerSkin.SMALL_LINE_HEIGHT, alpha);
                DrawerSkin.drawText(batch, font, DrawerEquipText.slotMark(template.getSlot()),
                        rect.x() + SLOT_TEXT_PAD, rect.y() + SLOT_PAD + DrawerSkin.SMALL_LINE_HEIGHT, alpha);
                font.getData().setScale(1f);
            }
            if (pendingSource) {
                tintOverlay(batch, DrawerEquipText.TINT_PENDING, PENDING_ALPHA * alpha);
            }
            if (hovered) {
                DrawerSkin.drawPatch(batch, selectedPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            }
        }

        /** 稀有度 / 待定底色：白片 tint 叠在槽底内缘 */
        private void tintOverlay(Batch batch, Color tint, float alpha) {
            batch.setColor(tint.r, tint.g, tint.b, alpha);
            batch.draw(white, rect.x() + SLOT_PAD, rect.y() + SLOT_PAD,
                    rect.width() - 2 * SLOT_PAD, rect.height() - 2 * SLOT_PAD);
            DrawerSkin.endUntinted(batch);
        }
    }

    /** 装备大图预览：内凹底 + 图标位（上）+ 装备描述滚动框（下，可滚到底；ScrollPane 自行消费滚轮） */
    private final class EquipPreview extends Group {
        private final DrawerGeometry.Rect previewRect = DrawerGeometry.equipPreview();
        private final DrawerGeometry.Rect iconZone = DrawerGeometry.equipIconZone();
        private final DrawerGeometry.Rect descViewport = DrawerGeometry.equipDescViewport();
        private final DrawerTextBlock descBlock = new DrawerTextBlock(assets, DrawerSkin.SMALL_TEXT_SCALE);
        private final ScrollPane descPane;
        private Equipment item;

        EquipPreview(NinePatch insetPatch) {
            setTouchable(Touchable.childrenOnly);
            addActor(new DrawerSkin.PatchActor(insetPatch, previewRect));
            descPane = DrawerSkin.newTextScrollPane(descBlock);
            descPane.setPosition(descViewport.x(), descViewport.y());
            descPane.setSize(descViewport.width(), descViewport.height());
            addActor(descPane);
            setItem(null);
        }

        void setItem(Equipment item) {
            if (item == this.item) {
                return; // 同一件（含同为 null）：沿用
            }
            this.item = item;
            DrawerSkin.setContent(descPane, descBlock, item != null
                            ? DrawerEquipText.descriptionLines(item.getTemplate(), DrawerEquipText.DESC_MAX_COLUMNS)
                            : Collections.<String>singletonList(NO_ITEM_PLACEHOLDER),
                    descViewport.width());
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha); // 底板 + 描述滚动框
            if (item != null) {
                TextureRegion icon = iconRegion(item.getTemplate().getSlot());
                float size = icon.getRegionWidth() * ICON_SCALE;
                float alpha = parentAlpha * getColor().a;
                DrawerSkin.drawRegion(batch, icon, iconZone.x() + (iconZone.width() - size) / 2f,
                        iconZone.y() + (iconZone.height() - size) / 2f, size, size, alpha);
            }
        }
    }
}
