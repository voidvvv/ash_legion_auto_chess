package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局按键路由测试（CP26）：Escape / Android BACK / L 三键回调触发与返回值透传，
 * 其余键与非按键事件一律不消费（input §3 例外条款——Escape/BACK 永不被模态吞）。
 */
class GlobalKeyProcessorTest {

    /** 记录型监听（返回值可配，验证透传） */
    private static final class RecordingListener implements GlobalKeyProcessor.Listener {
        private int escapeCalls;
        private int toggleCalls;
        private boolean escapeReturn = true;
        private boolean toggleReturn = true;

        @Override
        public boolean onEscapeOrBack() {
            escapeCalls++;
            return escapeReturn;
        }

        @Override
        public boolean onNotificationToggle() {
            toggleCalls++;
            return toggleReturn;
        }
    }

    @Test
    @DisplayName("Escape 触发 onEscapeOrBack 且透传 true")
    void escapeRoutes() {
        RecordingListener listener = new RecordingListener();
        GlobalKeyProcessor processor = new GlobalKeyProcessor(listener);

        assertThat(processor.keyDown(Input.Keys.ESCAPE)).isTrue();
        assertThat(listener.escapeCalls).isEqualTo(1);
        assertThat(listener.toggleCalls).isZero();
    }

    @Test
    @DisplayName("Android BACK 同走 Escape 回调（BACK=4，WARNING-9 真机回归）")
    void backRoutesLikeEscape() {
        RecordingListener listener = new RecordingListener();
        GlobalKeyProcessor processor = new GlobalKeyProcessor(listener);

        assertThat(processor.keyDown(Input.Keys.BACK)).isTrue();
        assertThat(listener.escapeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("L 键触发通知大小窗切换回调")
    void lKeyTogglesNotificationWindow() {
        RecordingListener listener = new RecordingListener();
        GlobalKeyProcessor processor = new GlobalKeyProcessor(listener);

        assertThat(processor.keyDown(Input.Keys.L)).isTrue();
        assertThat(listener.toggleCalls).isEqualTo(1);
        assertThat(listener.escapeCalls).isZero();
    }

    @Test
    @DisplayName("回调返回 false 时透传 false（键未消费，交下层处理）")
    void passthroughFalseWhenNotConsumed() {
        RecordingListener listener = new RecordingListener();
        listener.escapeReturn = false;
        listener.toggleReturn = false;
        GlobalKeyProcessor processor = new GlobalKeyProcessor(listener);

        assertThat(processor.keyDown(Input.Keys.ESCAPE)).isFalse();
        assertThat(processor.keyDown(Input.Keys.L)).isFalse();
    }

    @Test
    @DisplayName("其余按键不路由不消费")
    void otherKeysNotConsumed() {
        RecordingListener listener = new RecordingListener();
        GlobalKeyProcessor processor = new GlobalKeyProcessor(listener);

        assertThat(processor.keyDown(Input.Keys.A)).isFalse();
        assertThat(processor.keyDown(Input.Keys.SPACE)).isFalse();
        assertThat(listener.escapeCalls).isZero();
        assertThat(listener.toggleCalls).isZero();
    }

    @Test
    @DisplayName("抬键/字符/触点/滚轮事件一律不消费")
    void nonKeyDownEventsNotConsumed() {
        GlobalKeyProcessor processor = new GlobalKeyProcessor(new RecordingListener());

        assertThat(processor.keyUp(Input.Keys.ESCAPE)).isFalse();
        assertThat(processor.keyTyped('a')).isFalse();
        assertThat(processor.touchDown(0, 0, 0, 0)).isFalse();
        assertThat(processor.touchUp(0, 0, 0, 0)).isFalse();
        assertThat(processor.touchDragged(0, 0, 0)).isFalse();
        assertThat(processor.touchCancelled(0, 0, 0, 0)).isFalse();
        assertThat(processor.mouseMoved(0, 0)).isFalse();
        assertThat(processor.scrolled(0f, 1f)).isFalse();
    }
}
