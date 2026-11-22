package com.voidvvv.kz_auto_chess_n.render.ui;

/**
 * 装备待定态（input §2.5 配套规则：翻译层维护的轻量 pending 状态——跨域交互退化为点击的中转）。
 * 纯状态容器，BattleScreen 注入抽屉装备区（DrawerEquipSection）与棋盘点击回调共用。
 *
 * <p>生命周期：点背包格 set → 点目标棋子完成 EquipItem（消费方负责 clear）→
 * 失配（物品已不在包）由 {@code DrawerEquipText.reconcilePending} 每帧自动取消（UI 抽屉化批3 起）。
 */
public final class EquipPendingState {
    private int pendingItemId = -1;

    public boolean hasPending() { return pendingItemId >= 0; }
    public int pendingItemId() { return pendingItemId; }
    public void set(int itemId) { this.pendingItemId = itemId; }
    public void clear() { this.pendingItemId = -1; }
}
