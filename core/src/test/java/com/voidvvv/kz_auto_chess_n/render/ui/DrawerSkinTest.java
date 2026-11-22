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
 * 抽屉绘制皮肤的着色纪律（UI 抽屉化批1 冒烟回归固化）。
 *
 * <p>根因：libGDX {@code SpriteBatch.getColor()} 返回<b>内部同一 Color 实例</b>（1.14.0 字节码核实），既有面板的
 * {@code Color old = batch.getColor(); …; batch.setColor(old)} 保存/恢复实为空操作——tint 会泄漏给同一批次里
 * 后续未自设色的绘制。备战期抽屉里的把手 / 标签 / 关闭钮 / 滑块因此被前方背包槽的底色染暗
 * （截图里 UI 调色板像素数 = 0，RESULT 期前方面板隐藏后恢复 7336）。
 * 对策：抽屉内一切 TextureRegion 绘制统一走 {@link DrawerSkin#drawRegion}——先置白、再画、画毕还原白。
 * 用记录型 Batch 桩验证调用序（零 GL）。
 */
class DrawerSkinTest {

    /** 记录调用序的 Batch 桩（动态代理；其余方法返回类型默认值） */
    private static final class RecordingHandler implements java.lang.reflect.InvocationHandler {
        final List<String> calls = new ArrayList<String>();
        final List<Color> colors = new ArrayList<Color>();
        /** 四浮点 setColor(r,g,b,a) 的 a 分量序列 */
        final List<Float> alphas = new ArrayList<Float>();

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            calls.add(method.getName());
            if ("setColor".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Color) {
                colors.add((Color) args[0]);
            }
            if ("setColor".equals(method.getName()) && args != null && args.length == 4) {
                alphas.add((Float) args[3]);
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
    @DisplayName("整图绘制调用序：置白(alpha) → draw → 还原白（不继承同批次内前序 Actor 泄漏的 tint）")
    void regionDrawSetsWhiteBeforeAndAfter() {
        RecordingHandler handler = new RecordingHandler();
        Batch batch = recordingBatch(handler);
        DrawerSkin.drawRegion(batch, new TextureRegion(), 2f, 88f, 14f, 72f, 1f);
        assertThat(handler.calls).containsExactly("setColor", "draw", "setColor");
        assertThat(handler.alphas).as("进入：四浮点 setColor，alpha=1").containsExactly(1f);
        assertThat(handler.colors).as("离开：setColor(Color.WHITE)").containsExactly(Color.WHITE);
    }

    @Test
    @DisplayName("九宫格绘制共用同一对置白/还原入口（NinePatch.draw 同样被批次颜色相乘——像素采样实证；真 NinePatch 需 GL 不在 headless 构造）")
    void patchDrawUsesTheSameUntintedGuards() {
        RecordingHandler handler = new RecordingHandler();
        Batch batch = recordingBatch(handler);
        DrawerSkin.beginUntinted(batch, 1f);
        DrawerSkin.endUntinted(batch);
        assertThat(handler.calls).containsExactly("setColor", "setColor");
        assertThat(handler.alphas).containsExactly(1f);
        assertThat(handler.colors).containsExactly(Color.WHITE);
    }

    @Test
    @DisplayName("透明度透传：让位三成 / 转场淡出经 alpha 入参进批次色（0.3），画毕仍还原白")
    void alphaPassesThroughUntintedGuards() {
        RecordingHandler handler = new RecordingHandler();
        Batch batch = recordingBatch(handler);
        DrawerSkin.drawRegion(batch, new TextureRegion(), 0f, 0f, 1f, 1f, DrawerSkin.DIM_ALPHA);
        assertThat(handler.calls).containsExactly("setColor", "draw", "setColor");
        assertThat(handler.alphas).containsExactly(DrawerSkin.DIM_ALPHA);
        assertThat(handler.colors).containsExactly(Color.WHITE);
        assertThat(DrawerSkin.DIM_ALPHA).isEqualTo(0.3f);
    }

    @Test
    @DisplayName("让位透明度合成规则：让位期间逐帧压到 0.3（即使转场稳态刚把它写回 1）；退出让位那帧还原 1；其余帧保留转场淡出值")
    void bodyAlphaResolveRule() {
        // 进入让位（上一帧未让位；转场稳态刚写了 1）
        assertThat(DrawerSkin.resolveBodyAlpha(1f, true, false)).isEqualTo(0.3f);
        // 让位持续：转场稳态每帧归 1 → 仍压回 0.3
        assertThat(DrawerSkin.resolveBodyAlpha(1f, true, true)).isEqualTo(0.3f);
        // 退出让位：还原 1（下一帧起交还转场姿态）
        assertThat(DrawerSkin.resolveBodyAlpha(0.3f, false, true)).isEqualTo(1f);
        // 非让位稳态 / 转场窗内：原值透传（淡出 1−p 不被覆盖）
        assertThat(DrawerSkin.resolveBodyAlpha(0.42f, false, false)).isEqualTo(0.42f);
        assertThat(DrawerSkin.resolveBodyAlpha(1f, false, false)).isEqualTo(1f);
    }

    @Test
    @DisplayName("文字行顶居中规则（libGDX BitmapFont.draw 的 y 是行顶、向下延伸）：标题板/标签/角标内 12px 字行框完整落在盒内且上下留白相等")
    void centeredTextTopKeepsLineBoxInsideBox() {
        DrawerGeometry.Rect title = DrawerGeometry.titlePlate();
        float titleTop = DrawerSkin.centeredTextTop(title.y(), title.height(), 12f);
        assertThat(titleTop - 12f).isGreaterThanOrEqualTo(title.y());
        assertThat(titleTop).isLessThanOrEqualTo(title.y() + title.height());
        for (DrawerGeometry.Rect tab : new DrawerGeometry.Rect[]{DrawerGeometry.tabInfo(), DrawerGeometry.tabEquip()}) {
            float top = DrawerSkin.centeredTextTop(tab.y(), tab.height(), 12f);
            assertThat(top - 12f - tab.y()).as("标签下留白").isEqualTo(tab.y() + tab.height() - top);
            assertThat(top - 12f).isGreaterThanOrEqualTo(tab.y());
            assertThat(top).isLessThanOrEqualTo(tab.y() + tab.height());
        }
        DrawerGeometry.Rect badge = DrawerGeometry.badgeRect();
        float badgeTop = DrawerSkin.centeredTextTop(badge.y(), badge.height(), 12f);
        assertThat(badgeTop - 12f).isGreaterThanOrEqualTo(badge.y());
        assertThat(badgeTop).isLessThanOrEqualTo(badge.y() + badge.height());
        // 对照：批1 的基线摆法（y + 2）会让行框 [y−10, y+2] 大半落到盒外
        assertThat(title.y() + 2f - 12f).isLessThan(title.y());
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
