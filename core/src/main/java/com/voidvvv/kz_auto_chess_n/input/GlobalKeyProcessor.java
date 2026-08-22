package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * 全局按键处理器（input §2.2 第 4 层）。Escape / Android BACK 永不被模态吞
 * （§3 例外条款）：有弹窗 → 关顶层；无弹窗 → 开暂停菜单（回调制，装配点决定）。
 * L 键 → 通知面板大小窗切换（render §5.5）。回调返回值透传（false = 未消费交下层）。
 */
public final class GlobalKeyProcessor implements InputProcessor {

    /** 按键回调（返回是否已消费） */
    public interface Listener {
        boolean onEscapeOrBack();

        boolean onNotificationToggle();
    }

    private final Listener listener;

    public GlobalKeyProcessor(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
            return listener.onEscapeOrBack();
        }
        if (keycode == Input.Keys.L) {
            return listener.onNotificationToggle();
        }
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

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
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
}
