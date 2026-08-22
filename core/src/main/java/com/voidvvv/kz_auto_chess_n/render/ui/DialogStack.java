package com.voidvvv.kz_auto_chess_n.render.ui;

import java.util.ArrayDeque;

/**
 * 弹窗栈语义（CP26 纯逻辑抽取，headless 可测）：push 幂等（同一弹窗重复压栈跳过）、
 * closeTop 后进先出、clearAll 清空、isShowing 判空。UIDialogManager 委托本类；
 * Stage/Actor 组装与背板同步归宿主（Stage 需 GL，走 lwjgl3 手验——抽取先例
 * InventoryPanel.reconcilePending）。
 */
final class DialogStack<T> {

    private final ArrayDeque<T> dialogs = new ArrayDeque<T>();

    /** 压栈；已在栈中 → false（幂等跳过） */
    boolean push(T dialog) {
        if (dialogs.contains(dialog)) {
            return false;
        }
        dialogs.addLast(dialog);
        return true;
    }

    /** 弹顶层并返回；空栈 → null */
    T closeTop() {
        return dialogs.pollLast();
    }

    void clearAll() {
        dialogs.clear();
    }

    boolean isShowing() {
        return !dialogs.isEmpty();
    }

    /** 自底向顶迭代（背板同步的 toFront 顺序） */
    Iterable<T> bottomToTop() {
        return dialogs;
    }
}
