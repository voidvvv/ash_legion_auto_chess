package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * 不缩放层（Q8 对策一）数学断言——UI 抽屉化批1 的点名验证项。
 *
 * <p>① 反向缩放后抽屉局部坐标 = 舞台坐标（与 {@link BoardGeometry#screenX} 同式）；
 * ② 抽屉 1 虚拟像素对应整数屏幕像素（默认窗口 2×）；
 * ③ 实例态：用 libGDX 真实的 localToParentCoords / Stage.hit 复核公式，并锁死「全屏层不得遮蔽既有面板」
 * （层自身 childrenOnly——否则商店卡/顶栏等下层面板全部点不中）。
 * 全程 headless：Actor/Group/Stage 的命中与坐标换算不触 GL。
 */
class UnscaledLayerMathTest {

    private static final float[] SHRINK_SCALES = {0.7f, 0.8f, 0.9f, 1.0f};

    /** 根缩放后的根位置（BattleScreen.applyGlobalUiScale 同式） */
    private static float rootX(float uiScale) {
        return BoardGeometry.VIRTUAL_W * (1f - uiScale) / 2f;
    }

    private static float rootY(float uiScale) {
        return BoardGeometry.VIRTUAL_H * (1f - uiScale) / 2f;
    }

    /** libGDX Actor 变换序：子坐标 × scale + position（层），再 × scale + position（根）→ 舞台 */
    private static float composeToStage(float local, float uiScale, boolean xAxis) {
        float layerPos = xAxis ? UnscaledLayer.layerX(uiScale) : UnscaledLayer.layerY(uiScale);
        float rootPos = xAxis ? rootX(uiScale) : rootY(uiScale);
        return (local * UnscaledLayer.layerScale(uiScale) + layerPos) * uiScale + rootPos;
    }

    @Test
    @DisplayName("层参数（计划 §4.1 数值）：scale = 1/0.8 = 1.25、位置 (−80, −45)——恰抵消根缩放 (0.8, 64, 36)")
    void layerScaleAndPositionMatchPlan() {
        assertThat(UnscaledLayer.layerScale(0.8f)).isEqualTo(1.25f);
        assertThat(UnscaledLayer.layerX(0.8f)).isEqualTo(-80f);
        assertThat(UnscaledLayer.layerY(0.8f)).isEqualTo(-45f);
        // 根位置与 BoardGeometry.uiContentRect 同源（计划 §4.1「同源」口径）
        float[] content = BoardGeometry.uiContentRect(0.8f);
        assertThat(content[0]).isEqualTo(64f);
        assertThat(content[1]).isEqualTo(36f);
        assertThat(UnscaledLayer.layerX(0.8f) * 0.8f).isEqualTo(-64f); // 层位置 × uiScale = −根位置
        assertThat(UnscaledLayer.layerY(0.8f) * 0.8f).isEqualTo(-36f);
    }

    @Test
    @DisplayName("反向缩放后抽屉局部坐标 = 舞台坐标（恒等映射），且与 BoardGeometry.screenX 同式——缩放矩阵 {0.7~1.0} 逐档")
    void localEqualsStageForAllShrinkScales() {
        float[] samples = {0f, 2f, 14f, 88f, 123.5f, 246f, 306f, 360f, 640f};
        for (float s : SHRINK_SCALES) {
            float composed = s * UnscaledLayer.layerScale(s);
            assertThat(composed).as("s=%f 合成缩放 uiScale×layerScale = 1", s).isCloseTo(1f, within(1e-6f));
            for (float v : samples) {
                float viaX = UnscaledLayer.toStageX(v, s);
                float viaY = UnscaledLayer.toStageY(v, s);
                assertThat(viaX).as("s=%f 局部 x=%f → 舞台 x", s, v).isCloseTo(v, within(1e-3f));
                assertThat(viaY).as("s=%f 局部 y=%f → 舞台 y", s, v).isCloseTo(v, within(1e-3f));
                // 与 BoardGeometry.screenX/screenY 同式
                assertThat(viaX).isEqualTo(BoardGeometry.screenX(v, s * UnscaledLayer.layerScale(s)));
                assertThat(viaY).isEqualTo(BoardGeometry.screenY(v, s * UnscaledLayer.layerScale(s)));
                assertThat(viaX).isCloseTo(BoardGeometry.screenX(v, 1f), within(1e-3f));
                // 与 libGDX Actor 变换序（×scale + position 两层复合）一致
                assertThat(viaX).isCloseTo(composeToStage(v, s, true), within(1e-3f));
                assertThat(viaY).isCloseTo(composeToStage(v, s, false), within(1e-3f));
            }
        }
    }

    @Test
    @DisplayName("对照：留在 0.8 缩放层的普通 UI 件局部坐标 ≠ 舞台坐标（0 → 64；200 → 224）——不缩放层确有反向补偿")
    void plainUiChildIsNotIdentity() {
        float s = GameBalance.RENDER_GLOBAL_SCALE;
        assertThat(BoardGeometry.screenX(0f, s)).isEqualTo(64f);
        assertThat(BoardGeometry.screenX(200f, s)).isEqualTo(224f);
        assertThat(UnscaledLayer.toStageX(200f, s)).isEqualTo(200f);
    }

    @Test
    @DisplayName("抽屉 1 虚拟像素 = 整数屏幕像素（默认窗口 1280×720 → 2×）：面板 244×218 → 488×436 整；对照普通 UI 件 0.8×2 = 1.6 非整")
    void drawerPixelMapsToWholeScreenPixels() {
        float stageToScreen = UnscaledLayer.screenPixelsPerStagePixel(1280, 720);
        assertThat(stageToScreen).isEqualTo(2f);
        assertThat(stageToScreen).isEqualTo((float) Math.rint(stageToScreen)); // 整数倍
        // 抽屉（不缩放层）：位图 1px → 1 舞台 px → 2 屏幕 px
        assertThat(1 * stageToScreen).isEqualTo(2f);
        assertThat(DrawerGeometry.DRAWER_W * stageToScreen).isEqualTo(488f);
        assertThat(DrawerGeometry.DRAWER_H * stageToScreen).isEqualTo(436f);
        assertThat(DrawerGeometry.HANDLE_W * stageToScreen).isEqualTo(28f);
        // 对照：普通 UI 件（0.8 层）每位图像素 = 0.8 × 2 = 1.6 屏幕像素（宽窄不一的根因）
        float plainUi = GameBalance.RENDER_GLOBAL_SCALE * stageToScreen;
        assertThat(plainUi).isEqualTo(1.6f);
        assertThat(plainUi).isNotEqualTo((float) Math.rint(plainUi));
    }

    @Test
    @DisplayName("边界锁：非整数倍窗口（1600×900 → 2.5×）下整数像素保证失效——全游戏既有条件（FitViewport 无黑边即无整数倍）")
    void nonIntegerWindowBreaksWholePixelGuarantee() {
        float scale = UnscaledLayer.screenPixelsPerStagePixel(1600, 900);
        assertThat(scale).isEqualTo(2.5f);
        assertThat(scale).isNotEqualTo((float) Math.rint(scale));
        // 带 letterbox 的窗口：倍率取 min(宽比, 高比)，整屏像素数 = 舞台 × 倍率
        assertThat(UnscaledLayer.screenPixelsPerStagePixel(1920, 1080)).isEqualTo(3f);
        assertThat(UnscaledLayer.screenPixelsPerStagePixel(1280, 1024)).isEqualTo(2f); // 两侧黑边，倍率仍 2
    }

    @Test
    @DisplayName("实例态（libGDX 真实变换）：root(scale 0.8, pos 64,36) → UnscaledLayer 的 localToStageCoordinates 恒等；层参数如计划")
    void realActorTransformIsIdentity() {
        Group root = new Group();
        root.setScale(GameBalance.RENDER_GLOBAL_SCALE);
        root.setPosition(rootX(GameBalance.RENDER_GLOBAL_SCALE), rootY(GameBalance.RENDER_GLOBAL_SCALE));
        UnscaledLayer layer = new UnscaledLayer(GameBalance.RENDER_GLOBAL_SCALE);
        root.addActor(layer);

        assertThat(layer.getScaleX()).isEqualTo(1.25f);
        assertThat(layer.getScaleY()).isEqualTo(1.25f);
        assertThat(layer.getX()).isEqualTo(-80f);
        assertThat(layer.getY()).isEqualTo(-45f);
        assertThat(layer.getWidth()).isEqualTo(BoardGeometry.VIRTUAL_W);
        assertThat(layer.getHeight()).isEqualTo(BoardGeometry.VIRTUAL_H);
        float[][] points = {{0f, 0f}, {2f, 88f}, {246f, 306f}, {100f, 200f}, {640f, 360f}};
        for (float[] p : points) {
            Vector2 stage = layer.localToStageCoordinates(new Vector2(p[0], p[1]));
            assertThat(stage.x).as("局部 x=%f → 舞台 x", p[0]).isCloseTo(p[0], within(1e-3f));
            assertThat(stage.y).as("局部 y=%f → 舞台 y", p[1]).isCloseTo(p[1], within(1e-3f));
        }
    }

    @Test
    @DisplayName("遮蔽陷阱回归：全屏层自身 childrenOnly——下层既有面板（如 ③ 背包槽）在抽屉收起后照常可命中；抽屉底可命中但无监听（不吞事件，下传 boardProcessor）")
    void layerDoesNotOccludeUnderlyingPanels() {
        // Stage.hit 仅委托 root.hit——用普通 Group 作根等价复现（真 Stage 构造需 GL/非空 Batch，headless 不造）
        Group root = new Group();
        float s = GameBalance.RENDER_GLOBAL_SCALE;
        root.setScale(s);
        root.setPosition(rootX(s), rootY(s));

        Actor inventorySlot = new Actor(); // 代表下层既有面板：③ 背包槽（UI 子坐标 20..128 × 172..244）
        inventorySlot.setSize(108f, 72f);
        inventorySlot.setPosition(20f, 172f); // 舞台 ≈ (80,174)~(166,231)——落在抽屉展开态范围内
        root.addActor(inventorySlot);

        UnscaledLayer layer = new UnscaledLayer(s);
        assertThat(layer.getTouchable()).isEqualTo(Touchable.childrenOnly);
        Group drawerBody = new Group(); // 抽屉主体占位：可命中、无监听（事件不被吞）
        drawerBody.setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        drawerBody.setPosition(DrawerGeometry.DRAWER_X, DrawerGeometry.DRAWER_Y);
        Actor handle = new Actor();
        handle.setSize(DrawerGeometry.HANDLE_W, DrawerGeometry.HANDLE_H);
        handle.setPosition(DrawerGeometry.handleRect().x(), DrawerGeometry.handleRect().y());
        layer.addActor(drawerBody);
        layer.addActor(handle);
        root.addActor(layer);
        assertThat(drawerBody.getListeners().size).as("抽屉底无输入监听").isZero();

        // 舞台 (7, 197) = 把手中心；(100, 215) = 抽屉主体与 ③ 槽重叠处；(400, 300) = 谁都不在的空处
        assertThat(stageHit(root, 7f, 197f)).isSameAs(handle);
        assertThat(stageHit(root, 100f, 215f)).isSameAs(drawerBody);        // 展开态：主体挡住下层 ③（并存期防穿透误点）
        assertThat(stageHit(root, 400f, 300f)).as("空处不被全屏层截获").isNull();
        // 抽屉收起后（主体隐藏）：同一位置的 ③ 槽重新可命中——全屏层自身不遮蔽
        drawerBody.setVisible(false);
        assertThat(stageHit(root, 100f, 215f)).isSameAs(inventorySlot);
        assertThat(stageHit(root, 7f, 197f)).isSameAs(handle);              // 把手常驻可命中
    }

    /** 复现 libGDX Stage.hit：先把舞台坐标经根变换（parentToLocalCoordinates）换到根子坐标，再 root.hit */
    private static Actor stageHit(Group root, float stageX, float stageY) {
        Vector2 local = root.parentToLocalCoordinates(new Vector2(stageX, stageY));
        return root.hit(local.x, local.y, true);
    }

    @Test
    @DisplayName("入参校验：uiScale ≤ 0 即抛 IllegalArgumentException（反向缩放无意义，fail-fast）")
    void rejectsNonPositiveScale() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new UnscaledLayer(0f));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new UnscaledLayer(-0.8f));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> UnscaledLayer.layerScale(0f));
    }
}
