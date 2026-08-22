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
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.board.BoardGeometry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 棋盘域输入（input §2.3/§2.4/§3）：统辖棋盘 6×7 玩家区与备战席 3×3。
 *
 * <p>unproject 用棋盘 viewport（含其 camera）——坐标陷阱防御（input §3 第四行）；
 * 每 pointer 独立 DragContext（第一陷阱：多点互不串扰，同时拖拽 ≤1 有意义）；
 * 拖拽死区 DRAG_DEAD_ZONE_PX（unproject 后虚拟坐标，§3 第三陷阱：位移未出死区不算拖拽）；
 * 模态阻断位首行吞事件（本期常 false）。松手才入队（表现层只在合法落点产生命令，
 * input §4.3 双层校验的输入侧；handler 侧门控归 RunFlowSystem）。
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
    private final Map<Integer, DragContext> drags = new HashMap<Integer, DragContext>();
    private final Vector2 touch = new Vector2(); // 复用（unproject 输出，零分配）

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked) {
        this(boardViewport, commandManager, context, modalBlocked, null);
    }

    public BoardInputProcessor(Viewport boardViewport, CommandManager commandManager,
                               Supplier<RunContext> context, BooleanSupplier modalBlocked,
                               IntConsumer unitClickListener) {
        this.boardViewport = boardViewport;
        this.commandManager = commandManager;
        this.context = context;
        this.modalBlocked = modalBlocked;
        this.unitClickListener = unitClickListener;
    }

    // —— InputProcessor ——

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (modalBlocked.getAsBoolean()) {
            return true; // 模态阻断：吞事件不动作（input §3）
        }
        RunContext ctx = context.get();
        if (ctx.getRunState().getPhase() != GamePhase.SHOPPING) {
            return true; // 门控表现层：仅备战期可拖（input §4.3）
        }
        unproject(screenX, screenY);
        Unit unit = unitAt(touch.x, touch.y, ctx);
        if (unit == null) {
            return false; // 未命中单位：事件下传
        }
        drags.put(pointer, new DragContext(unit.getId(), placementAt(touch.x, touch.y), touch.x, touch.y));
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        DragContext drag = drags.get(pointer);
        if (drag == null) {
            return false; // 其他 pointer 或非拖拽源
        }
        unproject(screenX, screenY);
        drag.currentX = touch.x;
        drag.currentY = touch.y;
        if (!drag.dragging) {
            float dx = drag.currentX - drag.startX;
            float dy = drag.currentY - drag.startY;
            drag.dragging = dx * dx + dy * dy >= (float) (GameBalance.DRAG_DEAD_ZONE_PX * GameBalance.DRAG_DEAD_ZONE_PX);
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
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
        PlacementTarget target = dropTargetAt(drag.currentX, drag.currentY);
        if (target != null) {
            commandManager.addCommand(new MoveUnitCommand(drag.unitId, target));
        } // 非法落点：不产生命令，回弹由 ghost 消失自然实现
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        drags.remove(pointer); // 系统取消（焦点丢失等）：丢弃拖拽不产命令
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
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
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
        return dropTargetAt(drag.currentX, drag.currentY);
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

    /** 命中单位：备战席槽位索引即 bench 索引；棋盘玩家区格 → deployedAt */
    private Unit unitAt(float vx, float vy, RunContext ctx) {
        int slot = BoardGeometry.pixelToBenchSlot((int) vx, (int) vy);
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

    /** 指定虚拟坐标的落点（玩家区格 / 备战槽）；非法区返回 null */
    private PlacementTarget dropTargetAt(float vx, float vy) {
        int[] cell = BoardGeometry.pixelToCell((int) vx, (int) vy);
        if (cell != null && cell[1] >= 4) {
            return new PlacementTarget.Cell(cell[0], cell[1]);
        }
        int slot = BoardGeometry.pixelToBenchSlot((int) vx, (int) vy);
        if (slot >= 0) {
            return new PlacementTarget.Bench(slot);
        }
        return null;
    }

    /** 起手落点描述（源：Bench 槽或 Cell；界外防御给 Cell(0,4) 占位——拖拽源必在域内） */
    private PlacementTarget placementAt(float vx, float vy) {
        PlacementTarget target = dropTargetAt(vx, vy);
        return target != null ? target : new PlacementTarget.Cell(0, 4);
    }
}
