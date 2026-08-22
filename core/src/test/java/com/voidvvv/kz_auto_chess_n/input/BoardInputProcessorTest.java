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

    // —— Phase 5.1 CP6：悬停轨迹与候选查询（四类抑制集中于此） ——

    @Test
    @DisplayName("mouseMoved 命中备战席棋子：候选 = PLAYER_UNIT 携该棋子；移到空地清空；事件不消费")
    void mouseMovedTracksHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> true);

        assertThat(processor.mouseMoved(BENCH0_X, BENCH0_Y)).isFalse();
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }

    @Test
    @DisplayName("RESULT 期抑制：候选 NONE（查询侧与 mouseMoved 双路径）")
    void resultPhaseSuppressesHoverCandidate() {
        RunContext ctx = shoppingContext();
        BoardInputProcessor processor = new BoardInputProcessor(new IdentityViewport(),
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

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
                new CommandManager(), () -> ctx, () -> false);

        processor.mouseMoved(BENCH0_X, BENCH0_Y);
        assertThat(processor.getHoverCandidate().isNone()).isTrue();
    }
}
