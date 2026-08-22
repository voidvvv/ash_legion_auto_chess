package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;

/**
 * 命令 → 通知行文案（纯函数，headless 可测；动态数值行由 RunState.notices 承担——口径 #13）。
 */
public final class NotificationFormat {

    private NotificationFormat() {
    }

    /** 命令执行行（onExecuted 数据源）；返回 null = 不入面板 */
    public static String formatCommand(GameCommand cmd) {
        if (cmd instanceof RefreshShopCommand) {
            return "刷新商店（-2 金）";
        }
        if (cmd instanceof BuyExpCommand) {
            return "购买经验（-4 金 +4 经验）";
        }
        if (cmd instanceof SellUnitCommand) {
            return "卖出棋子"; // 返还数额动态 → notices 富行已覆盖，此处静态行去重跳过（WARNING-15）
        }
        return null; // 其余命令（买/穿/脱/领箱）动态行均走 notices
    }
}
