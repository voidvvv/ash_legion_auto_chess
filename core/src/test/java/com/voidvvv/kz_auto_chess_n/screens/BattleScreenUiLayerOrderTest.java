package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * uiStage 绘制/命中层级回归锁（品质改造 §1.5b 层序）：常驻 HUD（顶栏/⑥/战斗 HUD/⑧ 商店栏）→
 * ⑤ 羁绊 → ⑨ 通知 → <b>抽屉层</b> → 悬停卡 → <b>结果遮罩与终局</b> → 转场收点；
 * 模态弹窗在 dialogStage（独立 Stage，恒最上）。装配唯一入口 {@link BattleScreen#uiLayerOrder}——
 * 序即绘制序即命中优先级（后加者在上）。headless：仅比较实例在数组中的位置。
 */
class BattleScreenUiLayerOrderTest {

    @Test
    @DisplayName("层级：常驻 HUD → ⑤ 羁绊 → ⑨ 通知 → 抽屉层 → 悬停卡 → 结果遮罩/终局 → 转场收点最顶")
    void layerOrderFollowsQualitySpec() {
        Actor topBar = new Actor();
        Actor shoppingHud = new Actor();
        Actor battleHud = new Actor();
        Actor resultBanner = new Actor();
        Actor runEndPanel = new Actor();
        Actor shopBar = new Actor();
        Actor synergyPanel = new Actor();
        Actor notificationPanel = new Actor();
        Actor drawerLayer = new Actor();
        Actor hoverPreview = new Actor();
        Actor inputCatcher = new Actor();
        Actor[] order = BattleScreen.uiLayerOrder(topBar, shoppingHud, battleHud, resultBanner, runEndPanel,
                shopBar, synergyPanel, notificationPanel, drawerLayer, hoverPreview, inputCatcher);

        assertThat(order).hasSize(11).doesNotHaveDuplicates();
        assertThat(order[0]).isSameAs(topBar); // 顶栏最底
        int drawer = indexOf(order, drawerLayer);
        assertThat(drawer).as("抽屉层高于 ⑨ 通知与 ⑤ 羁绊（§1.5b）")
                .isGreaterThan(indexOf(order, notificationPanel))
                .isGreaterThan(indexOf(order, synergyPanel))
                .isGreaterThan(indexOf(order, shopBar))
                .isGreaterThan(indexOf(order, battleHud));
        assertThat(drawer).as("悬停卡（瞬态）仍在抽屉之上").isLessThan(indexOf(order, hoverPreview));
        assertThat(indexOf(order, resultBanner)).as("结果遮罩高于悬停卡（§1.5b）")
                .isGreaterThan(indexOf(order, hoverPreview));
        assertThat(indexOf(order, runEndPanel)).as("终局面板与结果遮罩同层段")
                .isGreaterThan(indexOf(order, hoverPreview));
        assertThat(indexOf(order, inputCatcher)).as("转场收点最顶")
                .isGreaterThan(indexOf(order, runEndPanel));
        assertThat(indexOf(order, notificationPanel))
                .as("⑨ 通知仍在 ⑤ 羁绊之上").isGreaterThan(indexOf(order, synergyPanel));
    }

    private static int indexOf(Actor[] order, Actor actor) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == actor) {
                return i;
            }
        }
        throw new AssertionError("不在层级表内: " + actor);
    }
}
