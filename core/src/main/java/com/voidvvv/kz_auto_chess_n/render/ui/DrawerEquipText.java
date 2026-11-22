package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽屉装备区的文案与配色纯函数（UI 抽屉化批3·Q1/Q5；自 {@code InventoryPanel}/{@code HoverPreviewCard} 背包源迁入，
 * 行集仍走 {@link EquipmentInfoText}）。零 Gdx，headless 可测。
 *
 * <p>「前 6 件 +N」与背包悬停卡「折 6 列截 7 行」均已退役：网格滚全量、描述滚动框滚到底。
 */
final class DrawerEquipText {

    /** 装备描述滚动框净宽 98px（102 − 左右各 2）÷ 9px → 全角 10 列 */
    static final int DESC_MAX_COLUMNS = 10;
    /** 装备描述滚动框净高 28px → 不滚动可见 3 行 */
    static final int DESC_VISIBLE_LINES = 3;
    /** 槽内短名列数（9px 小字 3 字 = 27px ≤ 34 槽宽） */
    static final int SHORT_NAME_COLUMNS = 3;

    /** 稀有度底色 / 待定高亮（沿 InventoryPanel 口径；static final：渲染段零分配） */
    static final Color TINT_EMPTY = new Color(0.25f, 0.24f, 0.28f, 1f);
    static final Color TINT_LEGENDARY = new Color(0.55f, 0.42f, 0.12f, 1f);
    static final Color TINT_RARE = new Color(0.2f, 0.3f, 0.5f, 1f);
    static final Color TINT_WHITE = new Color(0.32f, 0.32f, 0.34f, 1f);
    static final Color TINT_PENDING = new Color(1f, 0.9f, 0.3f, 1f);

    private DrawerEquipText() {
    }

    /** 装备描述行集（全量、可滚动）：名 → 稀有度·槽位 → 效果条目，逐行折到 maxColumns 列；不截断 */
    static List<String> descriptionLines(EquipmentData template, int maxColumns) {
        List<String> wrapped = new ArrayList<String>();
        for (String line : EquipmentInfoText.lines(template)) {
            wrapped.addAll(UnitInfoText.wrap(line, maxColumns));
        }
        return wrapped;
    }

    /** 槽内短名（超宽按列截断加 …） */
    static String shortName(String name) {
        return UnitInfoText.truncateColumns(name, SHORT_NAME_COLUMNS);
    }

    /** 槽位短标（背包格内短形武/甲/饰——InventoryPanel.slotMark 既有现状） */
    static String slotMark(EquipmentSlot slot) {
        switch (slot) {
            case WEAPON:
                return "武";
            case ARMOR:
                return "甲";
            case TRINKET:
            default:
                return "饰";
        }
    }

    /** 稀有度底色（空槽 = TINT_EMPTY） */
    static Color rarityTint(Equipment item) {
        if (item == null) {
            return TINT_EMPTY;
        }
        EquipmentRarity rarity = item.getTemplate().getRarity();
        if (rarity == EquipmentRarity.LEGENDARY) {
            return TINT_LEGENDARY;
        }
        if (rarity == EquipmentRarity.RARE) {
            return TINT_RARE;
        }
        return TINT_WHITE;
    }

    /**
     * 点击装备格后的待定态（两段式穿戴，input §2.4）：点空白取消；再点同一装备 = 取消；点其它装备 = 换待定。
     *
     * @return 新的待定物品 id（-1 = 无待定）
     */
    static int nextPending(int currentPendingId, Equipment clicked) {
        if (clicked == null || currentPendingId == clicked.getId()) {
            return -1;
        }
        return clicked.getId();
    }

    /** 待定物品已不在包（已穿 / 卖出）→ 失配自动取消；仍在包 / 无待定 → 保留（InventoryPanel.reconcilePending 迁入） */
    static void reconcilePending(EquipPendingState pending, Player player) {
        if (pending.hasPending() && player.findInventoryItem(pending.pendingItemId()) == null) {
            pending.clear();
        }
    }

    /** 预览悬停归一（BATTLE 置灰期 / 拖拽中 / 空槽 / 越界 / 负值 → -1；口径沿背包悬停卡 B4-1/B4-2） */
    static int normalizeHover(GamePhase phase, int slot, int inventorySize, boolean dragging) {
        if (dragging || phase != GamePhase.SHOPPING || slot < 0 || slot >= inventorySize) {
            return -1;
        }
        return slot;
    }

    /** 写操作门控（Q6）：仅备战期允许穿戴 / 卸下 / 待定 */
    static boolean writesAllowed(GamePhase phase) {
        return phase == GamePhase.SHOPPING;
    }
}
