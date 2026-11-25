package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * UI 框架件占位绘制（UI 抽屉化批0；{@link PlaceholderArt} 构造期逐 {@link UiKeys} 条目调用，GL 线程）。
 *
 * <p>平色占位：画布 = 条目的 1:1 源尺寸（九宫格件按切分线可正确拉伸——四角装饰落在切分线之内、
 * 四边均匀），观感仿采购单的明暗关系（面板底最暗 → 内凹再暗一阶 → 槽略亮 → 选中/高亮暖金）。
 * 真素材到位一张换一张（{@link Assets#uiRegion} 真素材优先），占位只管「缺件不崩、布局可读」。
 * Pixmap 坐标 y 向下：行 0 = 显示在上。
 */
final class UiPlaceholderPainter {

    private static final Color PANEL_FILL = new Color(0.16f, 0.17f, 0.23f, 1f);
    private static final Color PANEL_EDGE = new Color(0.55f, 0.52f, 0.60f, 1f);
    private static final Color RIVET = new Color(0.78f, 0.64f, 0.34f, 1f);
    private static final Color INSET_FILL = new Color(0.09f, 0.10f, 0.15f, 1f);
    private static final Color INSET_DARK = new Color(0.04f, 0.04f, 0.07f, 1f);
    private static final Color INSET_LIGHT = new Color(0.36f, 0.39f, 0.47f, 1f);
    private static final Color SLOT_FILL = new Color(0.22f, 0.23f, 0.30f, 1f);
    private static final Color SLOT_EDGE = new Color(0.10f, 0.10f, 0.14f, 1f);
    private static final Color GOLD = new Color(0.85f, 0.68f, 0.25f, 1f);
    private static final Color LIGHT = new Color(0.82f, 0.82f, 0.90f, 1f);
    private static final Color TAB_ACTIVE_FILL = new Color(0.42f, 0.36f, 0.20f, 1f);
    private static final Color TAB_INACTIVE_FILL = new Color(0.14f, 0.13f, 0.18f, 1f);
    private static final Color TRACK_FILL = new Color(0.10f, 0.10f, 0.14f, 1f);
    private static final Color TRACK_EDGE = new Color(0.30f, 0.30f, 0.38f, 1f);
    private static final Color GROOVE = new Color(0.03f, 0.03f, 0.05f, 1f);
    private static final Color KNOB_FILL = new Color(0.76f, 0.74f, 0.80f, 1f);
    private static final Color WOOD = new Color(0.40f, 0.28f, 0.16f, 1f);
    private static final Color WOOD_LIGHT = new Color(0.52f, 0.38f, 0.22f, 1f);
    private static final Color EDGE_DARK = new Color(0.08f, 0.07f, 0.09f, 1f);
    private static final Color CLOSE_FILL = new Color(0.55f, 0.20f, 0.18f, 1f);
    private static final Color CLOSE_PRESSED = new Color(0.36f, 0.12f, 0.11f, 1f);
    private static final Color CROSS = new Color(0.90f, 0.88f, 0.82f, 1f);
    private static final Color DIVIDER_DARK = new Color(0.06f, 0.06f, 0.09f, 1f);
    private static final Color DIVIDER_LIGHT = new Color(0.46f, 0.33f, 0.15f, 1f);
    private static final Color BOARD_A = new Color(0.12f, 0.12f, 0.17f, 1f);
    private static final Color BOARD_B = new Color(0.16f, 0.16f, 0.22f, 1f);
    private static final Color ICON_WEAPON = new Color(0.55f, 0.60f, 0.70f, 1f);
    private static final Color ICON_ARMOR = new Color(0.60f, 0.45f, 0.25f, 1f);
    private static final Color ICON_TRINKET = new Color(0.60f, 0.42f, 0.68f, 1f);

    private UiPlaceholderPainter() {
    }

    /** 按条目生成占位（调用方负责把 Pixmap 转 Texture 并 dispose） */
    static Pixmap paint(UiKeys.Entry entry) {
        int w = entry.width();
        int h = entry.height();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        if (entry == UiKeys.PANEL_BODY) {
            framed(pm, w, h, PANEL_FILL, PANEL_EDGE, 2);
            fill(pm, RIVET, 4, 4, 2, 2);           // 四角小铆钉（落在 12px 切分角内）
            fill(pm, RIVET, w - 6, 4, 2, 2);
            fill(pm, RIVET, 4, h - 6, 2, 2);
            fill(pm, RIVET, w - 6, h - 6, 2, 2);
        } else if (entry == UiKeys.INSET) {
            fill(pm, INSET_FILL, 0, 0, w, h);
            fill(pm, INSET_DARK, 0, 0, w, 2);      // 上/左暗
            fill(pm, INSET_DARK, 0, 0, 2, h);
            fill(pm, INSET_LIGHT, 0, h - 2, w, 2); // 下/右亮——凹进去
            fill(pm, INSET_LIGHT, w - 2, 0, 2, h);
        } else if (entry == UiKeys.SLOT) {
            framed(pm, w, h, SLOT_FILL, SLOT_EDGE, 2);
        } else if (entry == UiKeys.SLOT_SELECTED) {
            outline(pm, w, h, 2, GOLD);            // 中心透明：叠在槽上
        } else if (entry == UiKeys.SLOT_HOVER) {
            outline(pm, w, h, 2, LIGHT);
        } else if (entry == UiKeys.TAB_ACTIVE) {
            framed(pm, w, h, TAB_ACTIVE_FILL, GOLD, 2);
        } else if (entry == UiKeys.TAB_INACTIVE) {
            framed(pm, w, h, TAB_INACTIVE_FILL, EDGE_DARK, 2);
        } else if (entry == UiKeys.SCROLL_TRACK) {
            framed(pm, w, h, TRACK_FILL, TRACK_EDGE, 2);
            fill(pm, GROOVE, 2, h / 2 - 1, w - 4, 2); // 2px 深色槽线
        } else if (entry == UiKeys.SCROLL_KNOB) {
            framed(pm, w, h, KNOB_FILL, EDGE_DARK, 2);
        } else if (entry == UiKeys.SCROLL_ARROW) {
            fill(pm, LIGHT, 2, 1, w - 4, 1);
            fill(pm, LIGHT, 1, 2, w - 2, 2);
            fill(pm, LIGHT, 2, 4, w - 4, 1);
            fill(pm, LIGHT, 3, 5, w - 6, 1);
            fill(pm, LIGHT, w / 2 - 1, 6, 2, 1);   // 向下小箭头（翻转复用）
        } else if (entry == UiKeys.HANDLE) {
            framed(pm, w, h, WOOD, EDGE_DARK, 2);
            fill(pm, GOLD, w - 2, 2, 2, h - 4);    // 右缘金边（贴抽屉一侧可辨）
            fill(pm, INSET_DARK, 3, 8, 8, 8);      // 上部 8×8 图标位
        } else if (entry == UiKeys.BTN_CLOSE) {
            closeButton(pm, w, h, CLOSE_FILL);
        } else if (entry == UiKeys.BTN_CLOSE_PRESSED) {
            closeButton(pm, w, h, CLOSE_PRESSED);
        } else if (entry == UiKeys.BTN_CLOSE_HOVER) {
            closeButton(pm, w, h, CLOSE_FILL); // 悬停占位暂用正常底（真素材已交付，占位仅缺件兜底）
        } else if (entry == UiKeys.BTN_CLOSE_DISABLED) {
            closeButton(pm, w, h, TRACK_FILL);
        } else if (entry == UiKeys.BUTTON_NORMAL) {
            framed(pm, w, h, SLOT_FILL, PANEL_EDGE, 2);
        } else if (entry == UiKeys.BUTTON_HOVER) {
            framed(pm, w, h, TAB_ACTIVE_FILL, GOLD, 2);
        } else if (entry == UiKeys.BUTTON_PRESSED) {
            framed(pm, w, h, CLOSE_PRESSED, GOLD, 2);
        } else if (entry == UiKeys.BUTTON_DISABLED) {
            framed(pm, w, h, TRACK_FILL, TRACK_EDGE, 2);
        } else if (entry == UiKeys.DIVIDER_H) {
            fill(pm, DIVIDER_DARK, 0, 0, w, h / 2);  // 上缘暗线
            fill(pm, DIVIDER_LIGHT, 0, h / 2, w, h - h / 2); // 下缘亮线
        } else if (entry == UiKeys.TITLE_PLATE) {
            framed(pm, w, h, WOOD_LIGHT, GOLD, 2);
            fill(pm, RIVET, 3, 3, 2, 2);
            fill(pm, RIVET, w - 5, 3, 2, 2);
            fill(pm, RIVET, 3, h - 5, 2, 2);
            fill(pm, RIVET, w - 5, h - 5, 2, 2);
        } else if (entry == UiKeys.PREVIEW_BOARD) {
            fill(pm, BOARD_A, 0, 0, w, h);
            for (int y = 0; y < h; y += 8) {        // 暗棋盘格纹（8px 格）
                for (int x = (y / 8) % 2 == 0 ? 0 : 8; x < w; x += 16) {
                    fill(pm, BOARD_B, x, y, Math.min(8, w - x), Math.min(8, h - y));
                }
            }
            outline(pm, w, h, 2, PANEL_EDGE);
        } else if (entry == UiKeys.CORNER_ORNAMENT) {
            diamond(pm, w, h, GOLD);
        } else if (entry == UiKeys.ICON_EQUIP_WEAPON) {
            framed(pm, w, h, ICON_WEAPON, EDGE_DARK, 2);
        } else if (entry == UiKeys.ICON_EQUIP_ARMOR) {
            framed(pm, w, h, ICON_ARMOR, EDGE_DARK, 2);
        } else if (entry == UiKeys.ICON_EQUIP_TRINKET) {
            framed(pm, w, h, ICON_TRINKET, EDGE_DARK, 2);
        } else {
            framed(pm, w, h, PANEL_FILL, PANEL_EDGE, 2); // 未登记画法的新键：通用面板兜底（verifyCoverage 不漏）
        }
        return pm;
    }

    private static void closeButton(Pixmap pm, int w, int h, Color body) {
        framed(pm, w, h, body, EDGE_DARK, 2);
        int n = w - 8;
        for (int i = 0; i + 1 < n; i += 2) {       // 2px 骨白「×」
            fill(pm, CROSS, 4 + i, 4 + i, 2, 2);
            fill(pm, CROSS, w - 6 - i, 4 + i, 2, 2);
        }
    }

    private static void diamond(Pixmap pm, int w, int h, Color color) {
        int cx = w / 2;
        for (int y = 0; y < h; y++) {
            int half = Math.min(y + 1, h - y);
            fill(pm, color, cx - Math.min(half, cx), y, Math.min(half, cx) * 2, 1);
        }
    }

    /** 填充 + 厚 t 的边框 */
    private static void framed(Pixmap pm, int w, int h, Color body, Color edge, int t) {
        fill(pm, body, 0, 0, w, h);
        outline(pm, w, h, t, edge);
    }

    /** 空心边框（内部不动——中心透明件靠它） */
    private static void outline(Pixmap pm, int w, int h, int t, Color edge) {
        fill(pm, edge, 0, 0, w, t);
        fill(pm, edge, 0, h - t, w, t);
        fill(pm, edge, 0, 0, t, h);
        fill(pm, edge, w - t, 0, t, h);
    }

    private static void fill(Pixmap pm, Color color, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        pm.setColor(color);
        pm.fillRectangle(x, y, w, h);
    }
}
