package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 头像条横向滚动纯逻辑（UI 抽屉化批2·Q4：头像条 = 备战席 9 格横向形态，可见 6、滚轮/滑块看余下；
 * 顺序即备战席槽序——首槽下标 = 第一个可见槽的 bench 下标）。零 Gdx，headless 可测。
 */
public final class DrawerStripScroll {

    private DrawerStripScroll() {
    }

    /** 最大首槽下标：条目数 ≤ 可见槽数 → 0（不可滚）；满席 9 → 3 */
    public static int maxScroll(int itemCount) {
        return Math.max(0, itemCount - DrawerGeometry.STRIP_VISIBLE_SLOTS);
    }

    /** 首槽下标钳制到 [0, maxScroll]（条目缩水时自动归零） */
    public static int clamp(int index, int itemCount) {
        return Math.max(0, Math.min(index, maxScroll(itemCount)));
    }

    /** 滚动若干步：steps 正 = 看后面的槽（内容左移），负 = 往回；到头钳住 */
    public static int scroll(int index, int itemCount, int steps) {
        return clamp(index + steps, itemCount);
    }

    /** 滑块在轨内的 x 偏移（整像素）：首槽 0 停左端、首槽到最大停右端；不可滚停左端 */
    public static int knobOffsetX(int trackWidth, int knobWidth, int index, int itemCount) {
        int range = Math.max(0, trackWidth - knobWidth);
        int max = maxScroll(itemCount);
        if (max == 0) {
            return 0;
        }
        return Math.round(range * (float) clamp(index, itemCount) / max);
    }

    /** 拖滑块：轨内指针偏移 → 首槽下标（两端钳制；不可滚恒 0） */
    public static int indexForKnobOffset(int trackWidth, int knobWidth, int pointerOffsetX, int itemCount) {
        int range = Math.max(0, trackWidth - knobWidth);
        int max = maxScroll(itemCount);
        if (range == 0 || max == 0) {
            return 0;
        }
        float ratio = Math.max(0f, Math.min(1f, (float) pointerOffsetX / range));
        return Math.round(ratio * max);
    }
}
