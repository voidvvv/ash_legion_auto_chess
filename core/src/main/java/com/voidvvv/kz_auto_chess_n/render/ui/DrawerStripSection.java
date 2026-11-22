package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;
import com.voidvvv.kz_auto_chess_n.render.board.UnitFramePainter;

import java.util.List;
import java.util.Objects;

/**
 * 抽屉头像条（UI 抽屉化批2·Q4；批前重构自 DrawerPanel 抽出，纯搬迁、行为不变）：
 * 备战席 9 格的横向形态——可见 6 槽 + 横向滚动条（轨 + 滑块），顺序即备战席槽序、<b>槽号即 bench 下标</b>。
 * 头像格不可触摸（穿透给 boardProcessor，命中经 {@link DrawerHitTest} 回查）；选中 / 拖拽落点叠选中框。
 * 条内只支持滚轮 + 拖滑块（滑块可触摸）。
 */
final class DrawerStripSection extends Group {

    private static final float KNOB_SIZE = 12f;
    private static final float UNIT_FRAME = 32f;

    private final Assets assets;
    private final StripSlot[] slots = new StripSlot[DrawerGeometry.STRIP_VISIBLE_SLOTS];
    private final DrawerScrollKnob knob;
    private final DrawerGeometry.Rect track = DrawerGeometry.stripScrollBar();
    /** 首槽下标（= 第一个可见槽的 bench 下标） */
    private int scrollIndex;
    /** 最近一次绑定时的条目数（拖滑块换算用；bind 每帧刷新） */
    private int boundCount;

    DrawerStripSection(Assets assets, NinePatch slotPatch, NinePatch selectedPatch,
                       NinePatch trackPatch, TextureRegion knobRegion) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        setTouchable(Touchable.childrenOnly);
        setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new StripSlot(i, slotPatch, selectedPatch);
            addActor(slots[i]);
        }
        addActor(new DrawerSkin.PatchActor(trackPatch, track));
        knob = new DrawerScrollKnob(knobRegion, DrawerScrollKnob.HORIZONTAL, (int) KNOB_SIZE,
                new DrawerScrollKnob.DragListener() {
                    @Override
                    public void onDrag(float knobEdge) {
                        scrollIndex = DrawerStripScroll.indexForKnobOffset(track.width(), (int) KNOB_SIZE,
                                (int) (knobEdge - track.x()), boundCount);
                    }
                });
        addActor(knob);
    }

    /** 首槽下标（= 第一个可见槽的 bench 下标） */
    int scrollIndex() {
        return scrollIndex;
    }

    /** 滚动若干步（正 = 看后面的槽）；按条目数钳制 */
    void scrollBy(int steps, int itemCount) {
        scrollIndex = DrawerStripScroll.scroll(scrollIndex, itemCount, steps);
    }

    /**
     * 每帧绑定：钳制首槽下标、落位滑块、把备战席条目映射到可见槽。
     *
     * @param bench     备战席（只读）
     * @param selected  选中棋子（可空；在条内则叠选中框）
     * @param dropTarget 拖拽落点预览（可空；Bench(slot) 落在可见槽上则叠选中框）
     */
    void bind(List<Unit> bench, Unit selected, PlacementTarget dropTarget) {
        boundCount = bench.size();
        scrollIndex = DrawerStripScroll.clamp(scrollIndex, bench.size());
        for (StripSlot slot : slots) {
            slot.bind(bench, scrollIndex, selected, dropTarget);
        }
        knob.place(track.x() + DrawerStripScroll.knobOffsetX(track.width(), (int) KNOB_SIZE, scrollIndex, bench.size()),
                track.y() + track.height() / 2f);
    }

    /** 头像格（不可触摸；显示 bench[firstIndex + visibleIndex]；选中 / 落点叠选中框） */
    private final class StripSlot extends Actor {
        private final int visibleIndex;
        private final NinePatch slotPatch;
        private final NinePatch selectedPatch;
        private final DrawerGeometry.Rect rect;
        private Unit unit;
        private boolean highlight;

        StripSlot(int visibleIndex, NinePatch slotPatch, NinePatch selectedPatch) {
            this.visibleIndex = visibleIndex;
            this.slotPatch = slotPatch;
            this.selectedPatch = selectedPatch;
            this.rect = DrawerGeometry.stripSlot(visibleIndex, 0);
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            setTouchable(Touchable.disabled);
        }

        void bind(List<Unit> bench, int firstIndex, Unit selected, PlacementTarget target) {
            int index = firstIndex + visibleIndex;
            unit = index < bench.size() ? bench.get(index) : null;
            boolean dropHere = target instanceof PlacementTarget.Bench
                    && ((PlacementTarget.Bench) target).slotIndex == index;
            highlight = dropHere || (unit != null && unit == selected);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = DrawerSkin.alphaOf(this, parentAlpha);
            DrawerSkin.drawPatch(batch, slotPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            if (unit != null) {
                UnitFramePainter.draw(batch, assets, unit.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                        rect.x() + (rect.width() - UNIT_FRAME) / 2f, rect.y() + (rect.height() - UNIT_FRAME) / 2f,
                        UNIT_FRAME, false, alpha, StarColors.of(unit.getStar()));
            }
            if (highlight) {
                DrawerSkin.drawPatch(batch, selectedPatch, rect.x(), rect.y(), rect.width(), rect.height(), alpha);
            }
        }
    }
}
