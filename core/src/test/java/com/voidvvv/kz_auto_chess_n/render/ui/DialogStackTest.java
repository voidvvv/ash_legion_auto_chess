package com.voidvvv.kz_auto_chess_n.render.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 弹窗栈语义测试（CP26）：push 幂等、closeTop 后进先出、clearAll、isShowing。
 * UIDialogManager 的栈行为委托本纯逻辑类（Stage 组装需 GL 走 lwjgl3 手验，
 * 栈语义 headless 固化——沿 InventoryPanel.reconcilePending 抽取先例）。
 */
class DialogStackTest {

    @Test
    @DisplayName("空栈 isShowing=false；push 后 true")
    void showingTogglesWithPush() {
        DialogStack<Object> stack = new DialogStack<Object>();

        assertThat(stack.isShowing()).isFalse();
        stack.push(new Object());
        assertThat(stack.isShowing()).isTrue();
    }

    @Test
    @DisplayName("重复 push 同一弹窗幂等跳过（栈深不变）")
    void pushIsIdempotent() {
        DialogStack<Object> stack = new DialogStack<Object>();
        Object menu = new Object();

        assertThat(stack.push(menu)).isTrue();
        assertThat(stack.push(menu)).isFalse();
        stack.push(new Object());

        assertThat(stack.closeTop()).isNotSameAs(menu); // 顶层是第二个弹窗
        assertThat(stack.closeTop()).isSameAs(menu);
        assertThat(stack.isShowing()).isFalse();
    }

    @Test
    @DisplayName("closeTop 后进先出（栈式：确认窗在菜单之上）")
    void closeTopIsLifo() {
        DialogStack<Object> stack = new DialogStack<Object>();
        Object menu = new Object();
        Object confirm = new Object();
        stack.push(menu);
        stack.push(confirm);

        assertThat(stack.closeTop()).isSameAs(confirm);
        assertThat(stack.closeTop()).isSameAs(menu);
    }

    @Test
    @DisplayName("空栈 closeTop 返回 null 不抛错")
    void closeTopOnEmptyReturnsNull() {
        assertThat(new DialogStack<Object>().closeTop()).isNull();
    }

    @Test
    @DisplayName("clearAll 清空（重开新局收全弹窗）")
    void clearAllEmptiesStack() {
        DialogStack<Object> stack = new DialogStack<Object>();
        stack.push(new Object());
        stack.push(new Object());
        stack.clearAll();

        assertThat(stack.isShowing()).isFalse();
        assertThat(stack.closeTop()).isNull();
    }

    @Test
    @DisplayName("自底向顶迭代顺序（syncBackdrop 的 toFront 顺序）")
    void iteratesBottomToTop() {
        DialogStack<String> stack = new DialogStack<String>();
        stack.push("菜单");
        stack.push("确认窗");

        assertThat(stack.bottomToTop()).containsExactly("菜单", "确认窗");
    }
}
