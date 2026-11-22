package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;
import com.voidvvv.kz_auto_chess_n.systems.support.BattleTestFixtures;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 棋盘域输入测试（CP19）：⑦ 出售区拖拽终点 + 死区点击回调；UI 抽屉化批2：头像条替代 ② 的命中源切换。
 *
 * <p>viewport 注入恒等桩（屏幕坐标 == 虚拟坐标，仿 FitViewport 满屏 640×360 的 1:1 口径）
 * ——真 {@code Viewport.unproject} 依赖 Gdx.graphics（headless NPE），纯判定逻辑经桩零 Gdx 覆盖。
 * 备战席命中源 = {@link DrawerHitTest} 桩（由抽屉实现；返回的槽号即 bench 下标）——
 * 其后拖拽 / 出售 / 死区点击代码不变，既有断言按此迁移（测试数只增不减）。
 */
class BoardInputProcessorTest {

    /** 头像条槽 0（= 备战席槽 0）中心（屏幕坐标；恒等 viewport 下与旧 benchSlotCenter(0) 同值——断言迁移） */
    private static final int BENCH0_X = 38;
    private static final int BENCH0_Y = 68;
    /** 头像条槽 2 中心（同排右移 40px，不与槽 0 的死区重叠） */
    private static final int BENCH2_X = BENCH0_X + 40;
    private static final int BENCH2_Y = BENCH0_Y;
    /** ⑦ 出售区中心 */
    private static final int SELL_X = BoardGeometry.SELL_ZONE_X + BoardGeometry.SELL_ZONE_W / 2;
    private static final int SELL_Y = BoardGeometry.SELL_ZONE_Y + BoardGeometry.SELL_ZONE_H / 2;

    /** 恒等 viewport 桩：unproject 原样返回（纯 Java 零 Gdx） */
    private static class IdentityViewport extends Viewport {
        @Override
        public Vector2 unproject(Vector2 screenCoords) {
            return screenCoords;
        }
    }

    /**
     * 抽屉命中桩：屏幕点 (BENCH0) → 槽 0、(BENCH2) → 槽 2，其余 -1；
     * {@code open = false} 模拟抽屉收起（同一屏幕位置不再命中）；记录收到的屏幕坐标与滚轮步数。
     */
    private static final class FakeDrawerHitTest implements DrawerHitTest {
        boolean open = true;
        int lastScreenX = Integer.MIN_VALUE;
        int lastScreenY = Integer.MIN_VALUE;
        int scrollSteps;

        @Override
        public int stripSlotAt(int screenX, int screenY) {
            lastScreenX = screenX;
            lastScreenY = screenY;
            if (!open) {
                return -1;
            }
            if (screenX == BENCH0_X && screenY == BENCH0_Y) {
                return 0;
            }
            if (screenX == BENCH2_X && screenY == BENCH2_Y) {
                return 2;
            }
            return -1;
        }

        @Override
        public boolean scrollStrip(int screenX, int screenY, int steps) {
            boolean over = stripSlotAt(screenX, screenY) >= 0;
            if (over) {
                scrollSteps += steps;
            }
            return over;
        }
    }

    /** 每个用例独享一个桩（可变状态不跨用例泄漏） */
    private static FakeDrawerHitTest hit() {
        return new FakeDrawerHitTest();
    }

    // —— 夹具（口径对齐 RosterSystemTest） ——

    private static UnitData tpl(String id) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1,
                new BaseStats(100, 10, 5, 1f, 1, 1f, 0, 100, 0, 20, 150), 1.8f,
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
                () -> ctx, () -> false, null, hit());

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
                () -> ctx, () -> false, null, hit());

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
                () -> ctx, () -> false, clicks::add, hit());

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchDragged(BENCH0_X + 7, BENCH0_Y + 4, 0)).isTrue(); // 位移 8px < 死区 20px
        assertThat(processor.touchUp(BENCH0_X + 7, BENCH0_Y + 4, 0, 0)).isTrue();

        assertThat(clicks).containsExactly(1);
        manager.executeAll(ctx);
        assertThat(manager.getHistory()).isEmpty();
    }

    @Test
    @DisplayName("无点击监听 + 抽屉命中：头像格上死区松手不炸、零命令（点击监听可缺省，命中源不可缺省）")
    void deadZoneReleaseWithoutListenerKeepsSilent() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false, null, hit());

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchUp(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();

        manager.executeAll(ctx);
        assertThat(manager.getHistory()).isEmpty();
    }

    // —— Phase 5.1 CP6：悬停轨迹与候选查询（四类抑制集中于此） ——

    @Test
    @DisplayName("mouseMoved 命中备战席棋子：候选 = PLAYER_UNIT 携该棋子；移到空地清空；事件不消费")
    void mouseMovedTracksHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        assertThat(processor.mouseMoved(BENCH0_X, BENCH0_Y)).isFalse(); // 不消费：uiStage 照常收
        assertThat(processor.getHoverCandidate().kind()).isEqualTo(HoverCandidate.Kind.PLAYER_UNIT);
        assertThat(processor.getHoverCandidate().template()).isSameAs(ctx.getPlayer().getBench().get(0).getTemplate());

        assertThat(processor.mouseMoved(400, 60)).isFalse(); // 玩家区底行空格（无棋子、无虚影）
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("拖拽抑制：出死区拖拽中候选恒 NONE（即使 mouseMoved 仍悬在棋子上）")
    void draggingSuppressesHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.touchDragged(SELL_X, SELL_Y, 0)).isTrue(); // 出死区成拖拽
        processor.mouseMoved(BENCH0_X, BENCH0_Y); // 拖拽中轨迹仍记录
        assertThat(processor.getHoverCandidate().isNone()).isTrue(); // 查询侧抑制
    }

    @Test
    @DisplayName("模态抑制：modalBlocked=true 时 mouseMoved 清悬停且候选 NONE")
    void modalBlockSuppressesHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> true, null, hit());

        assertThat(processor.mouseMoved(BENCH0_X, BENCH0_Y)).isFalse();
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("RESULT 期抑制：候选 NONE（查询侧与 mouseMoved 双路径）")
    void resultPhaseSuppressesHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        processor.mouseMoved(BENCH0_X, BENCH0_Y); // 备战期悬停命中
        assertThat(processor.getHoverCandidate().kind()).isEqualTo(HoverCandidate.Kind.PLAYER_UNIT);
        ctx.getRunState().setPhase(GamePhase.RESULT); // 战毕横幅（悬停中切换）
        assertThat(processor.getHoverCandidate().isNone()).isTrue(); // 查询侧抑制
        processor.mouseMoved(BENCH0_X, BENCH0_Y); // 非 SHOPPING/BATTLE 分支亦清
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("名单核验：悬停中棋子被卖出/合并移出名单 → 候选 NONE（防残卡）")
    void removedUnitInvalidatesHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.getHoverCandidate().kind()).isEqualTo(HoverCandidate.Kind.PLAYER_UNIT);

        ctx.getPlayer().removeUnit(ctx.getPlayer().getBench().get(0));
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    // —— feedback04：敌方悬停详情（SHOPPING 敌阵虚影 + BATTLE 战斗单位） ——

    @Test
    @DisplayName("SHOPPING 悬停敌阵侦察虚影：候选 = ENEMY_PREVIEW 携 WaveSpec 模板（多 spec 同格取第一个）")
    void shoppingEnemyPreviewHover() {
        RunContext ctx = shoppingContext();
        UnitData ghostA = tpl("ghost_a");
        UnitData ghostB = tpl("ghost_b");
        ctx.getRunState().setEnemyWave(Arrays.asList(
                new WaveSpec(ghostA, 1, 1f, 2, 1),
                new WaveSpec(ghostB, 1, 1f, 2, 1), // 与 ghostA 同格：取第一个
                new WaveSpec(ghostB, 1, 1f, 4, 0)));
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        int[] ghostCell = BoardGeometry.cellCenter(2, 1);
        assertThat(processor.mouseMoved(ghostCell[0], ghostCell[1])).isFalse();
        HoverCandidate candidate = processor.getHoverCandidate();
        assertThat(candidate.kind()).isEqualTo(HoverCandidate.Kind.ENEMY_PREVIEW);
        assertThat(candidate.isEnemy()).isTrue();
        assertThat(candidate.template()).isSameAs(ghostA);

        int[] otherCell = BoardGeometry.cellCenter(4, 0);
        processor.mouseMoved(otherCell[0], otherCell[1]);
        assertThat(processor.getHoverCandidate().template()).isSameAs(ghostB);
    }

    @Test
    @DisplayName("SHOPPING 虚影命中优先于玩家区（区域互斥：虚影仅敌区行 0~2，玩家区行 4~6）")
    void playerAreaStillResolvesPlayerCandidate() {
        RunContext ctx = shoppingContext();
        Player player = ctx.getPlayer();
        Unit deployed = new Unit(2, tpl("u2"), 1);
        player.addToBench(deployed);
        player.deploy(deployed, 3, 5);
        ctx.getRunState().setEnemyWave(Arrays.asList(new WaveSpec(tpl("ghost_a"), 1, 1f, 3, 5))); // 越界口径防御：虚影不落玩家区
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        int[] cell = BoardGeometry.cellCenter(3, 5);
        processor.mouseMoved(cell[0], cell[1]);
        assertThat(processor.getHoverCandidate().kind()).isEqualTo(HoverCandidate.Kind.PLAYER_UNIT); // 玩家棋子优先
    }

    @Test
    @DisplayName("BATTLE 悬停战斗单位：敌我双向候选（BATTLE_UNIT，模板来自 BattleUnit）")
    void battleHoverResolvesBattleUnits() {
        RunContext ctx = shoppingContext();
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        ctx.setBattleState(BattleTestFixtures.state(
                BattleTestFixtures.unit(9, Side.PLAYER, tpl("ally9"), 1, 4),
                BattleTestFixtures.unit(10, Side.ENEMY, tpl("foe10"), 2, 1)));
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        int[] allyCell = BoardGeometry.cellCenter(1, 4);
        processor.mouseMoved(allyCell[0], allyCell[1]);
        HoverCandidate ally = processor.getHoverCandidate();
        assertThat(ally.kind()).isEqualTo(HoverCandidate.Kind.BATTLE_UNIT);
        assertThat(ally.isEnemy()).isFalse();
        assertThat(ally.template().getId()).isEqualTo("ally9");

        int[] foeCell = BoardGeometry.cellCenter(2, 1);
        processor.mouseMoved(foeCell[0], foeCell[1]);
        HoverCandidate foe = processor.getHoverCandidate();
        assertThat(foe.kind()).isEqualTo(HoverCandidate.Kind.BATTLE_UNIT);
        assertThat(foe.isEnemy()).isTrue();
        assertThat(foe.template().getId()).isEqualTo("foe10");

        int[] bench = BoardGeometry.benchSlotCenter(0); // 备战席在 BATTLE 非战斗单位域
        processor.mouseMoved(bench[0], bench[1]);
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("BATTLE 已清扫单位过滤：isCleaned 的格子命中 → 候选 NONE（沿 BattleRenderer 口径）")
    void battleCleanedUnitFiltered() {
        RunContext ctx = shoppingContext();
        ctx.getRunState().setPhase(GamePhase.BATTLE);
        BattleUnit foe = BattleTestFixtures.unit(10, Side.ENEMY, tpl("foe10"), 2, 1);
        ctx.setBattleState(BattleTestFixtures.state(foe));
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        int[] cell = BoardGeometry.cellCenter(2, 1);
        processor.mouseMoved(cell[0], cell[1]);
        assertThat(processor.getHoverCandidate().kind()).isEqualTo(HoverCandidate.Kind.BATTLE_UNIT);

        foe.markCleaned(); // 测试充当 systems 层（framework-internal 纪律的豁免主体）
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("BATTLE 无战斗实例（battleState=null）：候选 NONE")
    void battleWithoutStateYieldsNone() {
        RunContext ctx = shoppingContext();
        ctx.getRunState().setPhase(GamePhase.BATTLE); // 未 setBattleState
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false, null, hit());

        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    // —— UI 抽屉化批2：头像条替代 ②——命中源切换（DrawerHitTest 注入；其后拖拽/出售/死区点击代码不动） ——

    @Test
    @DisplayName("点在头像格上起手拖拽 → 落玩家区格：入队 MoveUnitCommand(Cell) 且 handler 真实执行（棋子离席上场）")
    void stripSlotDragDeploysToBoard() {
        RunContext ctx = shoppingContext();
        CommandManager manager = new CommandManager();
        new com.voidvvv.kz_auto_chess_n.systems.RosterSystem().registerHandlers(manager);
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false, null, hit());
        int[] cell = BoardGeometry.cellCenter(2, 4);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(processor.isDragFromBench()).as("未出死区还不算拖拽").isFalse();
        assertThat(processor.touchDragged(cell[0], cell[1], 0)).isTrue();
        assertThat(processor.isDragging()).isTrue();
        assertThat(processor.isDragFromBench()).as("源在头像条（bench）").isTrue();
        assertThat(processor.getDropPreview()).isInstanceOf(PlacementTarget.Cell.class);
        assertThat(((PlacementTarget.Cell) processor.getDropPreview()).gridX).isEqualTo(2);
        assertThat(((PlacementTarget.Cell) processor.getDropPreview()).gridY).isEqualTo(4);
        assertThat(processor.touchUp(cell[0], cell[1], 0, 0)).isTrue();

        manager.executeAll(ctx);
        assertThat(manager.getHistory()).hasSize(1);
        assertThat(manager.getHistory().get(0).getCommand()).isInstanceOf(MoveUnitCommand.class);
        assertThat(ctx.getPlayer().getBench()).isEmpty();
        assertThat(ctx.getPlayer().deployedAt(2, 4).getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("拖下：场上棋子拖到头像格 → MoveUnitCommand(Bench(slot))，落点预览为 Bench；空槽下标超出席长由名单层钳制追加")
    void boardUnitDraggedOntoStripSlotReturnsToBench() {
        RunContext ctx = shoppingContext(); // 备战席已有 1 号棋子
        Player player = ctx.getPlayer();
        Unit benchUnit = player.getBench().get(0);
        Unit deployed = new Unit(2, tpl("u2"), 1); // 与席上棋子不同 id
        player.addToBench(deployed);
        player.deploy(deployed, 3, 5);
        CommandManager manager = new CommandManager();
        new com.voidvvv.kz_auto_chess_n.systems.RosterSystem().registerHandlers(manager);
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), manager,
                () -> ctx, () -> false, null, hit());
        int[] cell = BoardGeometry.cellCenter(3, 5);

        assertThat(processor.touchDown(cell[0], cell[1], 0, 0)).isTrue();
        assertThat(processor.touchDragged(BENCH2_X, BENCH2_Y, 0)).isTrue();
        assertThat(processor.isDragFromBench()).as("源在棋盘").isFalse();
        assertThat(processor.getDropPreview()).isInstanceOf(PlacementTarget.Bench.class);
        assertThat(((PlacementTarget.Bench) processor.getDropPreview()).slotIndex).isEqualTo(2);
        assertThat(processor.touchUp(BENCH2_X, BENCH2_Y, 0, 0)).isTrue();

        manager.executeAll(ctx);
        assertThat(manager.getHistory().get(0).getCommand()).isInstanceOf(MoveUnitCommand.class);
        assertThat(player.deployedAt(3, 5)).isNull();
        assertThat(player.getBench()).as("下标 2 > 席长 1 → 钳制追加到末位").containsExactly(benchUnit, deployed);
    }

    @Test
    @DisplayName("抽屉收起时同一屏幕位置不起手：命中桩返回 -1 → touchDown 不消费、无拖拽、无悬停候选")
    void collapsedDrawerDoesNotStartDrag() {
        RunContext ctx = shoppingContext();
        FakeDrawerHitTest hit = hit();
        hit.open = false;
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), new CommandManager(),
                () -> ctx, () -> false, null, hit);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isFalse();
        assertThat(processor.touchDragged(SELL_X, SELL_Y, 0)).isFalse();
        assertThat(processor.touchUp(SELL_X, SELL_Y, 0, 0)).isFalse();
        assertThat(processor.isDragging()).isFalse();
        assertThat(processor.isDragFromBench()).isFalse();
        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("跨域换算用屏幕坐标（R2）：世界域 viewport 非恒等（×1.3）时抽屉命中桩收到屏幕坐标，棋盘域落点仍走 unproject 后的世界坐标")
    void drawerHitTestReceivesScreenCoordsNotWorldCoords() {
        RunContext ctx = shoppingContext();
        FakeDrawerHitTest hit = hit();
        Viewport scaling = new IdentityViewport() {
            @Override
            public Vector2 unproject(Vector2 screenCoords) {
                return screenCoords.scl(1.3f);
            }
        };
        BoardInputProcessor processor = new BoardInputProcessor(scaling, new CommandManager(),
                () -> ctx, () -> false, null, hit);

        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isTrue();
        assertThat(hit.lastScreenX).isEqualTo(BENCH0_X);
        assertThat(hit.lastScreenY).isEqualTo(BENCH0_Y);
        processor.touchDragged(BENCH0_X + 40, BENCH0_Y + 30, 0); // 出死区（世界域位移 > 20px）
        assertThat(processor.isDragging()).isTrue();
        assertThat(processor.getDragVirtualX()).isCloseTo((BENCH0_X + 40) * 1.3f, within(1e-3f));
        assertThat(processor.getDragVirtualY()).isCloseTo((BENCH0_Y + 30) * 1.3f, within(1e-3f));
        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(hit.lastScreenX).as("悬停路径同样传屏幕坐标").isEqualTo(BENCH0_X);
    }

    @Test
    @DisplayName("滚轮接头像条：指针在头像格上 → 传给抽屉并消费（向下滚 = 看后面的槽）；指针在别处或无命中接口 → 不消费")
    void wheelOverStripScrollsDrawer() {
        RunContext ctx = shoppingContext();
        FakeDrawerHitTest hit = hit();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), new CommandManager(),
                () -> ctx, () -> false, null, hit);

        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.scrolled(0f, -1f)).isTrue();
        assertThat(hit.scrollSteps).isEqualTo(1);
        assertThat(processor.scrolled(0f, 1f)).isTrue();
        assertThat(hit.scrollSteps).as("反向一步抵消").isZero();
        assertThat(processor.scrolled(0f, 0f)).as("零滚动量不消费").isFalse();

        processor.mouseMoved(400, 60);
        assertThat(processor.scrolled(0f, -1f)).isFalse();
        assertThat(hit.scrollSteps).isZero();

        BoardInputProcessor legacy = new BoardInputProcessor(new IdentityViewport(), new CommandManager(),
                () -> ctx, () -> false);
        legacy.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(legacy.scrolled(0f, -1f)).as("未注入抽屉：恒不消费（原口径）").isFalse();
    }

    @Test
    @DisplayName("旧备战席世界域矩形退役：未注入 DrawerHitTest 时点在原 ② 矩形内不再起手、无悬停候选（命中源只剩抽屉）")
    void legacyBenchRectNoLongerHitsWithoutDrawer() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(), new CommandManager(),
                () -> ctx, () -> false);

        assertThat(BoardGeometry.pixelToBenchSlot(BENCH0_X, BENCH0_Y)).as("旧函数仍在（去留待批4裁决）").isEqualTo(0);
        assertThat(processor.touchDown(BENCH0_X, BENCH0_Y, 0, 0)).isFalse();
        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }
}
