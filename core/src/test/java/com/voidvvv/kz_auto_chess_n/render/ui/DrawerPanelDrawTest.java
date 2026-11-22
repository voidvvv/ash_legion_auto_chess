package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抽屉整图绘制的着色纪律（UI 抽屉化批1 冒烟回归固化）。
 *
 * <p>根因：libGDX {@code SpriteBatch.getColor()} 返回<b>内部同一 Color 实例</b>（1.14.0 字节码核实），既有面板的
 * {@code Color old = batch.getColor(); …; batch.setColor(old)} 保存/恢复实为空操作——tint 会泄漏给同一批次里
 * 后续未自设色的绘制。备战期抽屉里的把手 / 标签 / 关闭钮 / 滑块因此被前方背包槽的底色染暗
 * （截图里 UI 调色板像素数 = 0，RESULT 期前方面板隐藏后恢复 7336）。
 * 对策：抽屉内一切 TextureRegion 绘制统一走 {@link DrawerPanel#drawRegion}——先置白、再画、画毕还原白。
 * 用记录型 Batch 桩验证调用序（零 GL）。
 */
class DrawerPanelDrawTest {

    /** 记录调用序的 Batch 桩（动态代理；其余方法返回类型默认值） */
    private static final class RecordingHandler implements java.lang.reflect.InvocationHandler {
        final List<String> calls = new ArrayList<String>();
        final List<Color> colors = new ArrayList<Color>();

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            calls.add(method.getName());
            if ("setColor".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Color) {
                colors.add((Color) args[0]);
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return Boolean.FALSE;
            }
            if (type.isPrimitive() && type != void.class) {
                return 0;
            }
            return null;
        }
    }

    private static Batch recordingBatch(RecordingHandler handler) {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, handler);
    }

    @Test
    @DisplayName("整图绘制调用序：置白 → draw → 还原白（不继承同批次内前序 Actor 泄漏的 tint）")
    void regionDrawSetsWhiteBeforeAndAfter() {
        RecordingHandler handler = new RecordingHandler();
        Batch batch = recordingBatch(handler);
        DrawerPanel.drawRegion(batch, new TextureRegion(), 2f, 88f, 14f, 72f);
        assertThat(handler.calls).containsExactly("setColor", "draw", "setColor");
        assertThat(handler.colors).hasSize(2);
        assertThat(handler.colors.get(0)).isEqualTo(Color.WHITE);
        assertThat(handler.colors.get(1)).isEqualTo(Color.WHITE);
    }

    @Test
    @DisplayName("九宫格绘制共用同一对置白/还原入口（NinePatch.draw 同样被批次颜色相乘——像素采样实证；真 NinePatch 需 GL 不在 headless 构造）")
    void patchDrawUsesTheSameUntintedGuards() {
        RecordingHandler handler = new RecordingHandler();
        Batch batch = recordingBatch(handler);
        DrawerPanel.beginUntinted(batch);
        DrawerPanel.endUntinted(batch);
        assertThat(handler.calls).containsExactly("setColor", "setColor");
        assertThat(handler.colors).containsExactly(Color.WHITE, Color.WHITE);
    }

    @Test
    @DisplayName("对照：泄漏机理——Batch.getColor() 桩返回同一实例时，save/restore 写法无法还原（证明必须显式置色）")
    void savedColorAliasCannotRestore() {
        final Color internal = new Color(1f, 1f, 1f, 1f); // 模拟 SpriteBatch 内部实例
        Batch leaky = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> {
                    if ("getColor".equals(method.getName())) {
                        return internal; // libGDX 语义：返回内部实例（别名）
                    }
                    if ("setColor".equals(method.getName()) && args[0] instanceof Color) {
                        internal.set((Color) args[0]);
                    }
                    return null;
                });
        Color old = leaky.getColor();          // 既有写法：以为存了副本
        leaky.setColor(new Color(0.25f, 0.24f, 0.28f, 0.9f));
        leaky.setColor(old);                   // 空操作：old 已随内部实例一起被改
        assertThat(leaky.getColor()).isEqualTo(new Color(0.25f, 0.24f, 0.28f, 0.9f));
    }
}
