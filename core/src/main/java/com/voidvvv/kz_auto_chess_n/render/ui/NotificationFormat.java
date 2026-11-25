package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.GameCommand;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;

/**
 * 命令 → 通知行文案（纯函数，headless 可测；动态数值行由 RunState.notices 承担——口径 #13）。
 */
public final class NotificationFormat {

    /** 行宽测量函数（品质改造 §1.5b：通知两尺寸均按当前字体实测宽度省略；运行时以 GlyphLayout 实现） */
    public interface WidthMeasurer {
        float widthOf(String text);
    }

    private static final String ELLIPSIS = "…";

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

    /**
     * 按实测宽度省略（品质改造 §1.5b：日志存入保留长版本，绘制时按两尺寸各自宽度截断）：
     * 未超宽原样返回；超宽取「最长放得下的前缀 + …」。零前缀放不下时仅返回省略号。
     */
    public static String ellipsize(String line, float maxWidth, WidthMeasurer measurer) {
        if (line == null || line.isEmpty() || measurer.widthOf(line) <= maxWidth) {
            return line;
        }
        int low = 0;
        int high = line.length();
        while (low < high) { // 二分最长可容纳前缀（前缀宽度随长度单调不减）
            int mid = (low + high + 1) / 2;
            if (measurer.widthOf(line.substring(0, mid) + ELLIPSIS) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return line.substring(0, low) + ELLIPSIS;
    }
}
