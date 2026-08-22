package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 棋盘域输入测试（CP19）：⑦ 出售区拖拽终点 + 死区点击回调。
 *
 * <p>viewport 注入恒等桩（屏幕坐标 == 虚拟坐标，仿 FitViewport 满屏 640×360 的 1:1 口径）
 * ——真 {@code Viewport.unproject} 依赖 Gdx.graphics（headless NPE），纯判定逻辑经桩零 Gdx 覆盖。
 */
class BoardInputProcessorTest {

    /** 备战席槽 0 中心（BoardGeometry.benchSlotCenter(0)） */
    private static final int BENCH0_X = 38;
    private static final int BENCH0_Y = 68;
    /** ⑦ 出售区中心 */
    private static final int SELL_X = BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W / 2;
    private static final int SELL_Y = BoardGeometry.SELL_ZONE_Y + BoardGeometry.SELL_ZONE_H / 2;

    /** 恒等 viewport 桩：unproject 原样返回（纯 Java 零 Gdx） */
    private static final class IdentityViewport extends Viewport {
        @Override
        public Vector2 unproject(Vector2 screenCoords) {
            return screenCoords;
        }
    }

    // —— 夹具（口径对齐 RosterSystemTest） ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0), 1.8f,
                TargetPriority.NEAREST, null, "sk_" + id, false);
    }

    private static GameData emptyData() {
        return new GameData(new LinkedHashMap<String, UnitData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SkillData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SynergyData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.EquipmentData>(),
                new ArrayList<String>());
    }

    /** SHOPPING 期上下文：备战席槽 0 放 1 号棋子（RunState 默认 SHOPPING） */
    private static RunContext shoppingContext() {
        Player player = new Player(0);
        player.addToBench(new Unit(1, tpl("u1"), 1));
        return new RunContext(player, new RunState(42L, "scene_forest", new SequentialIdIssuer()),
                emptyData(), new RandomGenerator(42L));
    }

    // —— CP19：出售区拖拽终点 ——

    @Test
    @DisplayName("拖棋子到 ⑦ 出售区松手：入队 SellUnitCommand 且 handler 真实执行（备战席清空）")
    void dropOnSellZoneEnqueuesSellUnit() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        new com.voidvvv.kz_auto_chess_n.systems.RosterSystem().registerHandlers(manager);
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchDragged(SELL_X, SELL_Y, 0)).isTrue();
        assertThat(processor.touchUp(SELL_X, SELL_Y, 0, 0)).isTrue();

        manager.executeAll(ctx);
        assertThat(manager.getHistory()).hasSize(1);
        assertThat(manager.getHistory().get(0).getCommand()).isInstanceOf(SellUnitCommand.class);
        assertThat(((SellUnitCommand) manager.getHistory().get(0).getCommand()).getUnitId()).isEqualTo(1);
        assertThat(ctx.getPlayer().getBench()).isEmpty(); // 命令经 RosterSystem 真卖掉
    }

    @Test
    @DisplayName("拖棋子悬停 ⑦ 出售区：isDropOnSellZone() 真；悬停玩家区格为假；未拖拽为假")
    void sellZoneHoverFlag() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false);

        assertThat(processor.isDropOnSellZone()).isFalse(); // 未拖拽
        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchDragged(SELL_X, SELL_Y, 0)).isTrue();
        assertThat(processor.isDragging()).isTrue();
        assertThat(processor.isDropOnSellZone()).isTrue();

        int[] cell = BoardGeometry.cellCenter(2, 4); // 悬停玩家区格：非出售区
        assertThat(processor.touchDragged(cell[0], cell[1], 0)).isTrue();
        assertThat(processor.isDropOnSellZone()).isFalse();
    }

    // —— CP19：死区点击回调 ——

    @Test
    @DisplayName("死区内松手 = 点击：触发 unitClickListener（携带 unitId）且零命令入队")
    void deadZoneReleaseInvokesClickListener() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        final List<Integer> clicks = new ArrayList<Integer>();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false, clicks::add);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchDragged(BENCH0_X + 7, BENCH0_Y + 4, 0)).isTrue(); // 位移 8px < 死区 20px
        assertThat(processor.touchUp(BENCH0_X + 7, BENCH0_Y + 4, 0, 0)).isTrue();

        assertThat(clicks).containsExactly(1);
        manager.executeAll(ctx);
        assertThat(manager.getHistory()).isEmpty();
    }

    @Test
    @DisplayName("四参构造（无监听）死区松手：不炸、零命令（存量 BattleScreen 调用点兼容）")
    void deadZoneReleaseWithoutListenerKeepsSilent() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchUp(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();

        manager.executeAll(ctx);
        assertThat(manager.getHistory()).isEmpty();
    }
}
