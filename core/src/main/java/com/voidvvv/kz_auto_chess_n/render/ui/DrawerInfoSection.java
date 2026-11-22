package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;
import com.voidvvv.kz_auto_chess_n.render.board.UnitFramePainter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 抽屉信息区（UI 抽屉化批3 批前重构：自 DrawerPanel 抽出，纯搬迁、行为不变）：
 * 标题板（棋子名）+ 立绘位（选中棋子 idle 帧 ×2，Q11）+ 描述框（介绍 / 装备两标签页内容区）+ 两枚标签按钮。
 * 选中棋子经 {@link #setUnit} 绑定（按 id/星/累计花费指纹缓存文案，渲染段零分配）；未选中 → 占位文案。
 * 坐标 = 抽屉局部（本组位于主体原点）。
 */
final class DrawerInfoSection extends Group {

    private static final float TAB_LABEL_X = 4f;
    private static final float DESC_PAD_X = 4f;
    private static final float DESC_PAD_TOP = 4f;
    private static final String EQUIP_TAB_PLACEHOLDER = "（装备迁入见批3）";
    private static final String TAB_INFO_LABEL = "介绍";
    private static final String TAB_EQUIP_LABEL = "装备";
    private static final List<String> EQUIP_TAB_LINES = Collections.singletonList(EQUIP_TAB_PLACEHOLDER);

    private final Assets assets;
    private final TitlePlate title;
    private final Portrait portrait;
    private final DescriptionPane description;
    /** 当前标签页（true = 介绍 / false = 装备） */
    private boolean infoTabActive = true;
    // —— 文案缓存指纹（渲染段零分配） ——
    private int cachedUnitId = -1;
    private int cachedStar;
    private int cachedSpend;

    DrawerInfoSection(Assets assets, NinePatch insetPatch, NinePatch titlePatch,
                      NinePatch tabActivePatch, NinePatch tabInactivePatch) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        setTouchable(Touchable.childrenOnly);
        title = new TitlePlate(titlePatch);
        portrait = new Portrait(insetPatch);
        description = new DescriptionPane(insetPatch);
        addActor(title);
        addActor(portrait);
        addActor(description);
        addActor(new TabButton(true, tabActivePatch, tabInactivePatch));
        addActor(new TabButton(false, tabActivePatch, tabInactivePatch));
    }

    /** 当前标签页是否为「介绍」 */
    boolean isInfoTabActive() {
        return infoTabActive;
    }

    /**
     * 绑定选中棋子（null = 未选中）：指纹（id / 星 / 累计花费）变化才重建文案。
     *
     * @param data 文案数据源（介绍行集查技能 / 羁绊）
     */
    void setUnit(Unit unit, GameData data) {
        if (unit == null) {
            if (cachedUnitId != -1) {
                cachedUnitId = -1;
                title.setText(DrawerInfoText.NO_SELECTION_TITLE);
                portrait.setUnit(null);
                description.setLines(DrawerInfoText.noSelectionIntro());
            }
            return;
        }
        if (unit.getId() == cachedUnitId && unit.getStar() == cachedStar && unit.getSpend() == cachedSpend) {
            return; // 指纹未变：沿用缓存
        }
        cachedUnitId = unit.getId();
        cachedStar = unit.getStar();
        cachedSpend = unit.getSpend();
        title.setText(DrawerInfoText.title(unit));
        portrait.setUnit(unit);
        description.setLines(DrawerInfoText.introLines(unit, data));
    }

    // —— 件 ——

    /** 标题板 + 棋子名 */
    private final class TitlePlate extends DrawerSkin.PatchActor {
        private String text = DrawerInfoText.NO_SELECTION_TITLE;

        TitlePlate(NinePatch patch) {
            super(patch, DrawerGeometry.titlePlate());
        }

        void setText(String text) {
            this.text = text;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            DrawerSkin.drawText(batch, assets.font(), text, rect.x() + DrawerGeometry.PADDING,
                    DrawerSkin.centeredTextTop(rect.y(), rect.height(), DrawerSkin.TEXT_HEIGHT),
                    DrawerSkin.alphaOf(this, parentAlpha));
        }
    }

    /** 立绘位：选中棋子 idle 帧 ×2（Q11） */
    private final class Portrait extends DrawerSkin.PatchActor {
        private Unit unit;

        Portrait(NinePatch patch) {
            super(patch, DrawerGeometry.portrait());
        }

        void setUnit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            if (unit != null) {
                UnitFramePainter.draw(batch, assets, unit.getTemplate().getId(), PlaceholderKeys.ANIM_IDLE, 0,
                        rect.x(), rect.y(), rect.width(), false, DrawerSkin.alphaOf(this, parentAlpha),
                        StarColors.of(unit.getStar()));
            }
        }
    }

    /** 描述框（介绍 / 装备两标签页的内容区；9px 小字） */
    private final class DescriptionPane extends DrawerSkin.PatchActor {
        private List<String> lines = DrawerInfoText.noSelectionIntro();

        DescriptionPane(NinePatch patch) {
            super(patch, DrawerGeometry.descriptionPane());
        }

        void setLines(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            BitmapFont font = assets.font();
            font.getData().setScale(DrawerSkin.SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            List<String> shown = infoTabActive ? lines : EQUIP_TAB_LINES;
            float top = rect.top() - DESC_PAD_TOP;
            for (int i = 0; i < shown.size() && top - DrawerSkin.SMALL_LINE_HEIGHT >= rect.y(); i++) {
                DrawerSkin.drawText(batch, font, shown.get(i), rect.x() + DESC_PAD_X, top,
                        DrawerSkin.alphaOf(this, parentAlpha));
                top -= DrawerSkin.SMALL_LINE_HEIGHT;
            }
            font.getData().setScale(1f);
        }
    }

    /** 标签页按钮（九宫格 32×30；点击切视觉态） */
    private final class TabButton extends Actor {
        private final boolean infoTab;
        private final NinePatch activePatch;
        private final NinePatch inactivePatch;

        TabButton(boolean infoTab, NinePatch activePatch, NinePatch inactivePatch) {
            this.infoTab = infoTab;
            this.activePatch = activePatch;
            this.inactivePatch = inactivePatch;
            DrawerGeometry.Rect rect = infoTab ? DrawerGeometry.tabInfo() : DrawerGeometry.tabEquip();
            setPosition(rect.x(), rect.y());
            setSize(rect.width(), rect.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    infoTabActive = infoTab;
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = DrawerSkin.alphaOf(this, parentAlpha);
            DrawerSkin.drawPatch(batch, infoTab == infoTabActive ? activePatch : inactivePatch,
                    getX(), getY(), getWidth(), getHeight(), alpha);
            DrawerSkin.drawText(batch, assets.font(), infoTab ? TAB_INFO_LABEL : TAB_EQUIP_LABEL,
                    getX() + TAB_LABEL_X, DrawerSkin.centeredTextTop(getY(), getHeight(), DrawerSkin.TEXT_HEIGHT), alpha);
        }
    }
}
