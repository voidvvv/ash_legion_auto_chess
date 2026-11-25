package com.voidvvv.kz_auto_chess_n.input;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.SellUnitCommand;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 棋盘域输入（input §2.3/§2.4/§3）：统辖棋盘 6×7 玩家区与备战席——备战席现由左侧抽屉头像条承载，命中经 {@link DrawerHitTest} 注入（屏幕坐标，见下）。
 *
 * <p>unproject 用棋盘 viewport（含其 camera）——坐标陷阱防御（input §3 第四行）；
 * 每 pointer 独立 DragContext（第一陷阱：多点互不串扰，同时拖拽 ≤1 有意义）；
 * 拖拽死区 DRAG_DEAD_ZONE_PX（unproject 后虚拟坐标，§3 第三陷阱：位移未出死区不算拖拽）；
 * 模态阻断位首行吞事件（BattleScreen 已接 UIDialogManager::isShowing）。松手才入队（表现层只在合法落点产生命令，
 * input §4.3 双层校验的输入侧；handler 侧门控归 RunFlowSystem）。
 *
 * <p><b>备战席命中源</b>（UI 抽屉化批2）：② 备战席世界域矩形退役，改由 {@link DrawerHitTest}（抽屉头像条实现）
 * 按屏幕坐标给出槽号——槽号即 bench 下标，其后拖拽 / 出售 / 死区点击代码零改动；滚轮落在头像条上亦经此路由。
 */
public final class BoardInputProcessor implements InputProcessor {
    /** 单 pointer 拖拽上下文（可变，GL 线程单消费者） */
    private static final class DragContext {
        final int unitId;
        final PlacementTarget source;
        final float startX;
        final float startY;
        float currentX;
        float currentY;
        /** 当前指针的屏幕坐标（UI 抽屉化批2：抽屉命中走屏幕坐标，R2 跨域口径） */
        float currentScreenX;
        float currentScreenY;
        boolean dragging; // 已出死区

        DragContext(int unitId, PlacementTarget source, float x, float y) {
            this.unitId = unitId;
            this.source = source;
            this.startX = x;
            this.startY = y;
            this.currentX = x;
            this.currentY = y;
        }
    }

    private final Viewport boardViewport;
    private final CommandManager commandManager;
    private final Supplier<RunContext> context;
    private final BooleanSupplier modalBlocked;
    /** 死区内松手 = 点击棋子的回调（Phase 5：详情面板 / 装备待定态落点；null = 无监听） */
    private final IntConsumer unitClickListener;
    /** 备战席命中源（UI 抽屉化批2：头像条替代 ② 世界域矩形；null = 无备战席命中，只剩棋盘） */
    private final DrawerHitTest drawerHitTest;
    /** 悬停指针轨迹（feedback04：mouseMoved 记虚拟坐标，查询时按当前局面重命中——
     *  BATTLE 期单位移动/清扫/名单变化即时反映；候选抑制见 getHoverCandidate） */
    private float hoverX;
    private float hoverY;
    /** 悬停指针的屏幕坐标（抽屉命中 / 滚轮路由用） */
    private int hoverScreenX;
    private int hoverScreenY;
    private boolean hoverTracked;
    private int inspectPointer = -1;
    private final Map<Integer, DragContext> drags = new HashMap<Integer, DragContext>();
    private final Vector2 touch = new Vector2(); // 复用（unproject 输出，零分配）

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked) {
        this(boardViewport, commandManager, context, modalBlocked, null, null);
    }

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked,
                               IntConsumer unitClickListener) {
        this(boardViewport, commandManager, context, modalBlocked, unitClickListener, null);
    }

    /** @param drawerHitTest 备战席命中源（抽屉头像条；null = 备战席不可命中——② 矩形已退役） */
    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked,
                               IntConsumer unitClickListener, DrawerHitTest drawerHitTest) {
        this.boardViewport = boardViewport;
        this.commandManager = commandManager;
        this.context = context;
        this.modalBlocked = modalBlocked;
        this.unitClickListener = unitClickListener;
        this.drawerHitTest = drawerHitTest;
    }

    // —— InputProcessor ——

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (modalBlocked.getAsBoolean()) {
            return true; // 模态阻断：吞事件不动作（input §3）
        }
        RunContext ctx = context.get();
        if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
            if (ctx.getRunState().getPhase() == GamePhase.BATTLE) beginInspection(screenX, screenY, pointer);
            return true; // 门控表现层：仅备战期可拖（input §4.3）
        }
        unproject(screenX, screenY);
        Unit unit = unitAt(screenX, screenY, touch.x, touch.y, ctx);
        if (unit == null) {
            return beginInspection(screenX, screenY, pointer); // 触屏长按敌方虚影可侦察
        }
        drags.put(pointer, new DragContext(unit.getId(), placementAt(screenX, screenY, touch.x, touch.y),
                touch.x, touch.y));
        hoverTracked = false; // 按下即清悬停（起手瞬间不出残卡；拖拽抑制由候选查询施加）
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (pointer == inspectPointer) {
            mouseMoved(screenX, screenY);
            return true;
        }
        DragContext drag = drags.get(pointer);
        if (drag == null) {
            return false; // 其他 pointer 或非拖拽源
        }
        unproject(screenX, screenY);
        drag.currentX = touch.x;
        drag.currentY = touch.y;
        drag.currentScreenX = screenX;
        drag.currentScreenY = screenY;
        if (!drag.dragging) {
            float dx = drag.currentX - drag.startX;
            float dy = drag.currentY - drag.startY;
            drag.dragging = dx * dx + dy * dy >= (float) (GameBalance.DRAG_DEAD_ZONE_PX * GameBalance.DRAG_DEAD_ZONE_PX);
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (endInspection(pointer)) return true;
        DragContext drag = drags.remove(pointer);
        if (drag == null) {
            return false;
        }
        if (!drag.dragging) {
            if (unitClickListener != null) {
                unitClickListener.accept(drag.unitId); // 死区内松手 = 点击（input §2.4：查看详情/装备落点）
            }
            return true;
        }
        if (BoardGeometry.isInSellZone((int) drag.currentX, (int) drag.currentY)) {
            commandManager.addCommand(new SellUnitCommand(drag.unitId)); // ⑦ 出售区（GDD §3.6）
            return true;
        }
        PlacementTarget target = dropTargetAt((int) drag.currentScreenX, (int) drag.currentScreenY,
                drag.currentX, drag.currentY);
        if (target != null) {
            commandManager.addCommand(new MoveUnitCommand(drag.unitId, target));
        } // 非法落点：不产生命令，回弹由 ghost 消失自然实现
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        endInspection(pointer);
        drags.remove(pointer); // 系统取消（焦点丢失等）：丢弃拖拽不产命令
        return true;
    }

    private boolean beginInspection(int screenX, int screenY, int pointer) {
        mouseMoved(screenX, screenY);
        if (getHoverCandidate().isNone()) return false;
        inspectPointer = pointer;
        return true;
    }

    private boolean endInspection(int pointer) {
        if (pointer != inspectPointer) return false;
        inspectPointer = -1;
        hoverTracked = false;
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
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
    public boolean mouseMoved(int screenX, int screenY) {
        if (modalBlocked.getAsBoolean()) {
            hoverTracked = false; // 模态期无悬停（input §3）
            return false;
        }
        GamePhase phase = context.get().getRunState().getPhase();
        if (phase != GamePhase.SHOPPING && phase != GamePhase.BATTLE) {
            hoverTracked = false; // 悬停仅备战/战斗期（feedback04：BATTLE 敌我战斗单位）
            return false;
        }
        unproject(screenX, screenY);
        hoverX = touch.x;
        hoverY = touch.y;
        hoverScreenX = screenX;
        hoverScreenY = screenY;
        hoverTracked = true;
        return false; // 悬停不消费事件（uiStage/后续处理器照常收）
    }

    /**
     * 悬停候选（渲染帧轮询，R1 + feedback04）：拖拽中/模态/无轨迹 → NONE（抑制集中于此）。
     * 按阶段查找：SHOPPING → 玩家名单（备战席/玩家区）+ 敌阵侦察虚影（多 spec 同格取第一个，
     * 与 drawShopping 绘制序一致的登记口径）；BATTLE → 战斗单位（敌我，isCleaned 自行过滤
     * ——沿 BattleRenderer 口径 #13）；RESULT/其它 → NONE。
     */
    public HoverCandidate getHoverCandidate() {
        if (isDragging() || modalBlocked.getAsBoolean() || !hoverTracked) {
            return HoverCandidate.NONE;
        }
        RunContext ctx = context.get();
        GamePhase phase = ctx.getRunState().getPhase();
        if (phase == GamePhase.SHOPPING) {
            return shoppingCandidateAt(hoverX, hoverY, hoverScreenX, hoverScreenY, ctx);
        }
        if (phase == GamePhase.BATTLE) {
            return battleCandidateAt(hoverX, hoverY, ctx);
        }
        return HoverCandidate.NONE;
    }

    /** SHOPPING 候选：玩家棋子优先（区域互斥——虚影仅敌区行 0~2，玩家区行 4~6 + 抽屉头像条） */
    private HoverCandidate shoppingCandidateAt(float vx, float vy, int screenX, int screenY, RunContext ctx) {
        Unit player = unitAt(screenX, screenY, vx, vy, ctx);
        if (player != null) {
            return HoverCandidate.ofPlayerUnit(player);
        }
        int[] cell = BoardGeometry.pixelToCell((int) vx, (int) vy);
        if (cell == null) {
            return HoverCandidate.NONE;
        }
        List<WaveSpec> wave = ctx.getRunState().getEnemyWave();
        for (int i = 0; i < wave.size(); i++) {
            WaveSpec spec = wave.get(i);
            if (spec.getGridX() == cell[0] && spec.getGridY() == cell[1]) {
                return HoverCandidate.ofEnemyPreview(i, spec.getTemplate());
            }
        }
        return HoverCandidate.NONE;
    }

    /** BATTLE 候选：战斗单位敌我（getUnits 构造序 = id 升序，首命中即返回——确定性） */
    private HoverCandidate battleCandidateAt(float vx, float vy, RunContext ctx) {
        BattleState battle = ctx.getBattleState();
        if (battle == null) {
            return HoverCandidate.NONE;
        }
        int[] cell = BoardGeometry.pixelToCell((int) vx, (int) vy);
        if (cell == null) {
            return HoverCandidate.NONE;
        }
        for (BattleUnit unit : battle.getUnits()) { // 含亡者：isCleaned 自行过滤（口径 #13）
            if (!unit.isCleaned() && unit.getGridX() == cell[0] && unit.getGridY() == cell[1]) {
                return HoverCandidate.ofBattleUnit(unit);
            }
        }
        return HoverCandidate.NONE;
    }

    /**
     * 滚轮路由（UI 抽屉化批2/批3）：指针（最近一次 mouseMoved 的屏幕坐标）落在抽屉头像条 / 装备网格上 →
     * 交给抽屉滚动并消费；其余位置不消费（描述滚动框的滚轮由 scene2d ScrollPane 在 uiStage 层自行消费）。
     * <b>方向</b>：libGDX 约定 {@code amountY > 0} = 向下滚 = 看后面的槽 / 行（LWJGL3 后端 {@code scrolled(−x, −y)}
     * 取负 GLFW 偏移；与 ScrollPane 的 {@code setScrollY(amountY + 50×Δ)} 同向——批2 初版写反，批3 修正）。
     */
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (drawerHitTest == null || !hoverTracked || modalBlocked.getAsBoolean()) {
            return false;
        }
        int steps = amountY > 0f ? Math.max(1, Math.round(amountY)) : amountY < 0f ? -Math.max(1, Math.round(-amountY)) : 0;
        return steps != 0 && (drawerHitTest.scrollStrip(hoverScreenX, hoverScreenY, steps)
                || drawerHitTest.scrollEquip(hoverScreenX, hoverScreenY, steps));
    }

    // —— 渲染只读暴露（ghost 绘制与高亮，input §4.1） ——

    public boolean isDragging() {
        return dropContext() != null;
    }

    public int getDragUnitId() {
        DragContext drag = dropContext();
        return drag == null ? -1 : drag.unitId;
    }

    public float getDragVirtualX() {
        DragContext drag = dropContext();
        return drag == null ? 0f : drag.currentX;
    }

    public float getDragVirtualY() {
        DragContext drag = dropContext();
        return drag == null ? 0f : drag.currentY;
    }

    /** 当前悬停落点（合法绿/非法红高亮）；未拖拽或悬在非法区返回 null */
    public PlacementTarget getDropPreview() {
        DragContext drag = dropContext();
        if (drag == null) {
            return null;
        }
        return dropTargetAt((int) drag.currentScreenX, (int) drag.currentScreenY, drag.currentX, drag.currentY);
    }

    /**
     * 当前拖拽是否从备战席起手（UI 抽屉化批2·Q2「从抽屉往外拖棋子时面板自动让位」的触发位）：
     * 未拖拽 / 从棋盘起手 = false。
     */
    public boolean isDragFromBench() {
        DragContext drag = dropContext();
        return drag != null && drag.source instanceof PlacementTarget.Bench;
    }

    /** 拖拽悬停是否在 ⑦ 出售区（渲染金红高亮用） */
    public boolean isDropOnSellZone() {
        DragContext drag = dropContext();
        return drag != null && BoardGeometry.isInSellZone((int) drag.currentX, (int) drag.currentY);
    }

    // —— 内部 ——

    /** 取"已出死区"的拖拽上下文（多点中至多一个有效拖拽） */
    private DragContext dropContext() {
        for (DragContext drag : drags.values()) {
            if (drag.dragging) {
                return drag;
            }
        }
        return null;
    }

    private void unproject(int screenX, int screenY) {
        touch.set(screenX, screenY);
        boardViewport.unproject(touch);
    }

    /**
     * 命中单位：抽屉头像条槽号（屏幕坐标 → {@link DrawerHitTest}，槽号即 bench 下标）；棋盘玩家区格 → deployedAt。
     * 备战席命中只认屏幕坐标（R2）：② 的世界域矩形已退役，未注入抽屉时备战席不可命中。
     */
    private Unit unitAt(int screenX, int screenY, float vx, float vy, RunContext ctx) {
        int slot = drawerHitTest != null ? drawerHitTest.stripSlotAt(screenX, screenY) : -1;
        if (slot >= 0) {
            java.util.List<Unit> bench = ctx.getPlayer().getBench();
            return slot < bench.size() ? bench.get(slot) : null;
        }
        int[] cell = BoardGeometry.pixelToCell((int) vx, (int) vy);
        if (cell != null && cell[1] >= 4) { // 玩家区行（敌区/缓冲带无我方单位）
            return ctx.getPlayer().deployedAt(cell[0], cell[1]);
        }
        return null;
    }

    /** 指定位置的落点（玩家区格 / 抽屉头像格）；非法区返回 null */
    private PlacementTarget dropTargetAt(int screenX, int screenY, float vx, float vy) {
        int[] cell = BoardGeometry.pixelToCell((int) vx, (int) vy);
        if (cell != null && cell[1] >= 4) {
            return new PlacementTarget.Cell(cell[0], cell[1]);
        }
        int slot = drawerHitTest != null ? drawerHitTest.stripSlotAt(screenX, screenY) : -1;
        if (slot >= 0) {
            return new PlacementTarget.Bench(slot); // 空槽下标超出席长由名单层钳制追加
        }
        return null;
    }

    /** 起手落点描述（源：Bench 槽或 Cell；界外防御给 Cell(0,4) 占位——拖拽源必在域内） */
    private PlacementTarget placementAt(int screenX, int screenY, float vx, float vy) {
        PlacementTarget target = dropTargetAt(screenX, screenY, vx, vy);
        return target != null ? target : new PlacementTarget.Cell(0, 4);
    }
}
