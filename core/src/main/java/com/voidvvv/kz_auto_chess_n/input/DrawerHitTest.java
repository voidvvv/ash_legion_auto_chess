package com.voidvvv.kz_auto_chess_n.input;

/**
 * 抽屉 ↔ 棋盘域输入的窄命中接口（UI 抽屉化批2·UI_PLAN §3.5-2；由 {@code DrawerPanel} 实现）。
 *
 * <p>② 备战席世界域矩形退役后，备战席棋子的命中源只剩本接口：<b>返回的槽号即 bench 下标</b>
 * （合成 / 购买逻辑零改动），{@code BoardInputProcessor} 其后的拖拽 / 出售 / 死区点击代码不动。
 * 坐标口径：<b>屏幕坐标</b>（R2 跨域换算统一走屏幕坐标——抽屉在 UI 域、棋盘在世界域，
 * 备战期两域缩放并不相等；由实现自行 unproject 到舞台坐标，只有它知道自己滚了多少、滑到哪了）。
 * 头像格本身不可触摸（事件穿过 uiStage 落到 boardProcessor），故不经 Scene2d 监听。
 */
public interface DrawerHitTest {

    /** 屏幕坐标 → 头像条可见槽号（= 备战席 bench 下标）；不在可见槽上（含抽屉收起）返回 -1 */
    int stripSlotAt(int screenX, int screenY);

    /**
     * 滚轮落在头像条上：滚动若干步（正 = 看后面的槽）。
     *
     * @return 是否消费（指针不在头像条上 = false，事件不拦）
     */
    boolean scrollStrip(int screenX, int screenY, int steps);
}
