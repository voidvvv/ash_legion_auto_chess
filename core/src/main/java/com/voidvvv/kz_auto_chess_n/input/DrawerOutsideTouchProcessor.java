package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Objects;

/**
 * 「点抽屉外收起」观察处理器（UI 抽屉化批1·Q2）——<b>永不吃事件的透传观察者</b>（全部回调返回 false）。
 *
 * <p>挂在 multiplexer 的 uiStage <b>之前</b>（dialogStage 之后）：既能看见落在商店卡 / 顶栏等 UI 件上的点击
 * （uiStage 会先吃掉这些事件，排在其后就看不见），又原样放行——uiStage 照常响应、boardProcessor 的拖拽
 * 抬手不被截断。<b>不能做成 uiStage 内的监听器</b>：libGDX 1.14 的 Stage.touchUp 只派发给 touchDown 时登记
 * touchFocus 的监听，而 InputListener 对 touchUp 恒返回「已处理」——会把 boardProcessor 的落子抬手吞掉。
 *
 * <p>判定口径：抽屉开着且<b>按下点</b>在抽屉交互域之外 → 抬手时请求收起一次（以按下点为准：从抽屉内
 * 起手的拖拽 / 滑块即便抬手在外也不算「点外面」）；转场窗口内抬手不收起（§3.8 不许开合）；
 * 系统取消（touchCancelled）丢弃待判定；多指各自独立。
 */
public final class DrawerOutsideTouchProcessor implements InputProcessor {

    /** 宿主回调（BattleScreen 实现：开合态 / 转场封禁位 / 交互域判定 / 收起请求） */
    public interface Host {
        boolean isDrawerOpen();

        /** 转场窗口封禁位（为真时抽屉外点击不收起） */
        boolean isDrawerTouchBlocked();

        /**
         * 「点抽屉外收起」的业务抑制位（UI 抽屉化批4·主人裁决：装备待定态期间点外不收起）。
         * 与封禁位分工：封禁位 = 转场窗口（时序）；本位 = 业务态（待定）。只抑制「点外收起」这一条路径——
         * 把手 / 关闭钮 / Esc 等显式收起不经本处理器，照常有效。
         */
        boolean isOutsideClickSuppressed();

        /** 舞台坐标是否落在抽屉交互域内（把手 ∪ 展开中的主体） */
        boolean isInsideDrawer(float stageX, float stageY);

        /** 抽屉外点击（抬手时触发） */
        void onOutsideClick();
    }

    /** 指针槽位数（沿 libGDX Stage 指针数组口径，防越界） */
    private static final int MAX_POINTERS = 20;

    private final Viewport uiViewport;
    private final Host host;
    private final boolean[] armed = new boolean[MAX_POINTERS];
    /** 复用（unproject 输出，零分配） */
    private final Vector2 touch = new Vector2();

    public DrawerOutsideTouchProcessor(Viewport uiViewport, Host host) {
        this.uiViewport = Objects.requireNonNull(uiViewport, "uiViewport 不能为 null");
        this.host = Objects.requireNonNull(host, "host 不能为 null");
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer < 0 || pointer >= MAX_POINTERS) {
            return false;
        }
        armed[pointer] = false;
        if (host.isDrawerOpen()) {
            touch.set(screenX, screenY);
            uiViewport.unproject(touch);
            armed[pointer] = !host.isInsideDrawer(touch.x, touch.y);
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer >= 0 && pointer < MAX_POINTERS && armed[pointer]) {
            armed[pointer] = false;
            if (!host.isDrawerTouchBlocked() && !host.isOutsideClickSuppressed()) {
                host.onOutsideClick();
            }
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        if (pointer >= 0 && pointer < MAX_POINTERS) {
            armed[pointer] = false;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }
}
