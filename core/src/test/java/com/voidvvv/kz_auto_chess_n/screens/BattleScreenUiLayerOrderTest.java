package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.scenes.scene2d.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * uiStage 绘制/命中层级回归锁（UI 抽屉化批4·主人裁决 ⑨ 层级 A 案）：抽屉层高于 ⑤ 羁绊面板与 ⑨ 通知小窗，
 * 仍低于瞬态悬停卡与转场收点；顶栏在最底。装配唯一入口 {@link BattleScreen#uiLayerOrder}——
 * 序即绘制序即命中优先级（后加者在上）。headless：仅比较实例在数组中的位置。
 */
class BattleScreenUiLayerOrderTest {

    @Test
    @DisplayName("层级：顶栏最底 → ⑤ 羁绊 → ⑨ 通知 → 抽屉层（高于通知）→ 悬停卡 → 转场收点最顶")
    void drawerLayerSitsAboveNotificationAndBelowHoverAndCatcher() {
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
        assertThat(order[0]).isSameAs(topBar);
        int drawer = indexOf(order, drawerLayer);
        assertThat(drawer).as("抽屉层高于 ⑨ 通知（主人裁决 A 案：展开期间通知被盖住）")
                .isGreaterThan(indexOf(order, notificationPanel));
        assertThat(drawer).isGreaterThan(indexOf(order, synergyPanel));
        assertThat(drawer).isGreaterThan(indexOf(order, shopBar));
        assertThat(drawer).as("悬停卡（瞬态）仍在抽屉之上").isLessThan(indexOf(order, hoverPreview));
        assertThat(drawer).as("转场收点最顶").isLessThan(indexOf(order, inputCatcher));
        assertThat(indexOf(order, notificationPanel)).as("⑨ 通知仍在 ⑤ 羁绊之上").isGreaterThan(indexOf(order, synergyPanel));
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
