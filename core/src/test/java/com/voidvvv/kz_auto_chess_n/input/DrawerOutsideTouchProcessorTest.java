package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.render.ui.DrawerGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「点抽屉外收起」观察处理器测试（UI 抽屉化批1；Q2）。
 *
 * <p>处理器是<b>永不吃事件的透传观察者</b>（全部回调返回 false）——挂在 multiplexer 的 uiStage 之前：
 * 既能看见落在商店卡/顶栏等 UI 件上的点击，又原样放行给 uiStage 与 boardProcessor（拖拽落子的抬手不被截断）。
 * viewport 注入恒等桩（屏幕坐标 == 舞台坐标，沿 BoardInputProcessorTest 先例——真 unproject 依赖 Gdx.graphics）。
 */
class DrawerOutsideTouchProcessorTest {

    /** 恒等 viewport 桩：unproject 原样返回（纯 Java 零 Gdx） */
    private static final class IdentityViewport extends Viewport {
        @Override
        public Vector2 unproject(Vector2 screenCoords) {
            return screenCoords;
        }
    }

    /** 记录后续处理器是否照常收到事件（透传验证） */
    private static final class RecordingProcessor implements InputProcessor {
        final List<String> events = new ArrayList<String>();

        @Override
        public boolean keyDown(int keycode) {
            events.add("keyDown");
            return false;
        }

        @Override
        public boolean keyUp(int keycode) {
            events.add("keyUp");
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            events.add("keyTyped");
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            events.add("touchDown");
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            events.add("touchUp");
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            events.add("touchDragged");
            return false;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            events.add("mouseMoved");
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            events.add("scrolled");
            return false;
        }

        @Override
        public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
            events.add("touchCancelled");
            return false;
        }
    }

    /** 可配置宿主：开合态 / 转场封禁 / 命中域（真实 DrawerGeometry 判定，进度可控） */
    private static final class FakeHost implements DrawerOutsideTouchProcessor.Host {
        boolean open;
        boolean blocked;
        float progress = 1f;
        int outsideClicks;

        @Override
        public boolean isDrawerOpen() {
            return open;
        }

        @Override
        public boolean isDrawerTouchBlocked() {
            return blocked;
        }

        @Override
        public boolean isInsideDrawer(float stageX, float stageY) {
            return DrawerGeometry.isInside(stageX, stageY, progress);
        }

        @Override
        public void onOutsideClick() {
            outsideClicks++;
        }
    }

    /** 把手中心（舞台坐标；常驻左缘） */
    private static final int HANDLE_X = DrawerGeometry.HANDLE_W / 2;
    private static final int HANDLE_Y = (int) DrawerGeometry.handleRect().y() + DrawerGeometry.HANDLE_H / 2;
    /** 抽屉主体中心（展开态） */
    private static final int BODY_X = DrawerGeometry.DRAWER_X + DrawerGeometry.DRAWER_W / 2;
    private static final int BODY_Y = DrawerGeometry.DRAWER_Y + DrawerGeometry.DRAWER_H / 2;
    /** 棋盘中段——必在抽屉外 */
    private static final int BOARD_X = 320;
    private static final int BOARD_Y = 180;

    @Test
    @DisplayName("抽屉开着：抽屉外按下、抬手 → 请求收起 1 次；按下/抬手均不吃事件（返回 false）")
    void outsideClickWhileOpenRequestsClose() {
        FakeHost host = new FakeHost();
        host.open = true;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        assertThat(p.touchDown(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(host.outsideClicks).as("按下不收起（抬手才算点击）").isZero();
        assertThat(p.touchUp(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(host.outsideClicks).isEqualTo(1);
        p.touchUp(BOARD_X, BOARD_Y, 0, 0); // 重复抬手不重复触发
        assertThat(host.outsideClicks).isEqualTo(1);
    }

    @Test
    @DisplayName("抽屉开着：按下点在抽屉内（把手 / 展开主体）→ 即便抬手在抽屉外也不收起（以按下点为准）")
    void pressInsideNeverCloses() {
        FakeHost host = new FakeHost();
        host.open = true;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        assertThat(p.touchDown(HANDLE_X, HANDLE_Y, 0, 0)).isFalse(); // 把手：交给 uiStage 的 ClickListener 切换
        assertThat(p.touchUp(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(p.touchDown(BODY_X, BODY_Y, 0, 0)).isFalse();     // 主体（如拖滑块/头像起手）
        assertThat(p.touchUp(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(host.outsideClicks).isZero();
    }

    @Test
    @DisplayName("抽屉收着：抽屉外按下抬手不触发；主体位置在收起态不算「抽屉内」（但此时也不开——不收起）")
    void closedDrawerIgnoresOutsideClicks() {
        FakeHost host = new FakeHost();
        host.open = false;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        assertThat(p.touchDown(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(p.touchUp(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        host.progress = 0f;
        assertThat(p.touchDown(BODY_X, BODY_Y, 0, 0)).isFalse();
        assertThat(p.touchUp(BODY_X, BODY_Y, 0, 0)).isFalse();
        assertThat(host.outsideClicks).isZero();
    }

    @Test
    @DisplayName("转场窗口内（§3.8 不许开合）：抽屉外点击不收起")
    void blockedByTransitionWindow() {
        FakeHost host = new FakeHost();
        host.open = true;
        host.blocked = true;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        p.touchDown(BOARD_X, BOARD_Y, 0, 0);
        p.touchUp(BOARD_X, BOARD_Y, 0, 0);
        assertThat(host.outsideClicks).isZero();
        host.blocked = false; // 解禁后照常
        p.touchDown(BOARD_X, BOARD_Y, 0, 0);
        p.touchUp(BOARD_X, BOARD_Y, 0, 0);
        assertThat(host.outsideClicks).isEqualTo(1);
    }

    @Test
    @DisplayName("按下后系统取消（touchCancelled）→ 丢弃待判定；多指各自独立")
    void cancelledAndMultiPointer() {
        FakeHost host = new FakeHost();
        host.open = true;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        p.touchDown(BOARD_X, BOARD_Y, 0, 0);
        p.touchCancelled(BOARD_X, BOARD_Y, 0, 0);
        p.touchUp(BOARD_X, BOARD_Y, 0, 0);
        assertThat(host.outsideClicks).as("取消后抬手不触发").isZero();

        p.touchDown(BOARD_X, BOARD_Y, 0, 0);   // 指针 0：外
        p.touchDown(HANDLE_X, HANDLE_Y, 1, 0); // 指针 1：内
        p.touchUp(HANDLE_X, HANDLE_Y, 1, 0);
        assertThat(host.outsideClicks).as("内指指针抬手不触发").isZero();
        p.touchUp(BOARD_X, BOARD_Y, 0, 0);
        assertThat(host.outsideClicks).as("外指指针抬手触发").isEqualTo(1);
    }

    @Test
    @DisplayName("透传：挂在 multiplexer 最前也不截断后续处理器（touchDown/Dragged/Up/取消/移动/滚轮/键全部放行）")
    void neverConsumesEvents() {
        FakeHost host = new FakeHost();
        host.open = true;
        DrawerOutsideTouchProcessor p = new DrawerOutsideTouchProcessor(new IdentityViewport(), host);
        RecordingProcessor downstream = new RecordingProcessor();
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(p);
        mux.addProcessor(downstream);
        assertThat(mux.touchDown(BOARD_X, BOARD_Y, 0, 0)).isFalse();
        assertThat(mux.touchDragged(300, 170, 0)).isFalse();
        assertThat(mux.touchUp(300, 170, 0, 0)).isFalse();
        assertThat(mux.mouseMoved(10, 350)).isFalse();
        assertThat(mux.scrolled(0f, 1f)).isFalse();
        assertThat(mux.keyDown(21)).isFalse();
        assertThat(mux.touchCancelled(0, 0, 1, 0)).isFalse();
        assertThat(downstream.events).containsExactly(
                "touchDown", "touchDragged", "touchUp", "mouseMoved", "scrolled", "keyDown", "touchCancelled");
        assertThat(host.outsideClicks).isEqualTo(1);
    }
}
