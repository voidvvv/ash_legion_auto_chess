package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;
import com.voidvvv.kz_auto_chess_n.render.StarColors;
import com.voidvvv.kz_auto_chess_n.render.board.UnitFramePainter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 抽屉信息区（UI 抽屉化批3；批前重构自 DrawerPanel 抽出）：标题板（棋子名）+ 立绘位（选中棋子 idle 帧 ×2，Q11）
 * + 描述框——两标签页内容区（Q5）：
 * <ul>
 *   <li><b>「介绍」</b>：{@link UnitInfoText#detailLines} 折 12 列的<b>全量</b>行集，scene2d {@link ScrollPane}
 *       滚动（最长真实文案 23 行必须能滚到底——批3 硬验收，{@code DrawerTextCapacityTest}）；</li>
 *   <li><b>「装备」</b>：选中棋子当前三件（武器 / 盔甲 / 饰品），每件带「卸下」钮——{@link UnequipItemCommand}
 *       经 commandManager（原 UnitDetailDialog 卸下按钮逻辑迁入——该弹窗批4 退役；Q6 仅备战期可写）。</li>
 * </ul>
 * 选中棋子经 {@link #setUnit} 绑定（文案按 id/星/累计花费指纹缓存；卸下按钮按装备 id 序列指纹重建——
 * Scene2d ClickListener 要求 down/up 命中同一实例）。坐标 = 抽屉局部。
 */
final class DrawerInfoSection extends Group {

    private static final float TAB_LABEL_X = 4f;
    private static final float ROW_LABEL_X = 2f;
    private static final float ROW_NAME_X = 28f;
    private static final float ROW_TEXT_PAD_X = 2f;
    private static final String TAB_INFO_LABEL = "介绍";
    private static final String TAB_EQUIP_LABEL = "装备";
    private static final String UNEQUIP_LABEL = "卸下";
    private static final String EMPTY_SLOT_LABEL = "—";

    private final Assets assets;
    private final CommandManager commandManager;
    private final NinePatch buttonPatch;
    private final TitlePlate title;
    private final Portrait portrait;
    private final DrawerTextBlock introBlock;
    private final ScrollPane introPane;
    /** 「装备」标签页内容：三行（武器/盔甲/饰品）+ 每件的卸下钮 */
    private final EquipTabPane equipTab;
    /** 当前标签页（true = 介绍 / false = 装备） */
    private boolean infoTabActive = true;
    private GamePhase phase = GamePhase.SHOPPING;
    // —— 文案缓存指纹（渲染段零分配） ——
    private int cachedUnitId = -1;
    private int cachedStar;
    private int cachedSpend;

    DrawerInfoSection(Assets assets, CommandManager commandManager, NinePatch insetPatch, NinePatch titlePatch,
                      NinePatch tabActivePatch, NinePatch tabInactivePatch) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager 不能为 null");
        this.buttonPatch = tabInactivePatch;
        setTouchable(Touchable.childrenOnly);
        setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);

        DrawerGeometry.Rect pane = DrawerGeometry.descriptionPane();
        DrawerGeometry.Rect viewport = DrawerGeometry.descriptionViewport();
        title = new TitlePlate(titlePatch);
        portrait = new Portrait(insetPatch);
        addActor(title);
        addActor(portrait);
        // 描述框 = 内凹底 + 介绍滚动框（介绍页）/ 装备页（三行 + 卸下钮）——两页共用同一矩形，按标签切换可见
        addActor(new DrawerSkin.PatchActor(insetPatch, pane));
        introBlock = new DrawerTextBlock(assets, DrawerSkin.SMALL_TEXT_SCALE);
        introPane = DrawerSkin.newTextScrollPane(introBlock);
        introPane.setPosition(viewport.x(), viewport.y());
        introPane.setSize(viewport.width(), viewport.height());
        addActor(introPane);
        equipTab = new EquipTabPane();
        addActor(equipTab);
        addActor(new TabButton(true, tabActivePatch, tabInactivePatch));
        addActor(new TabButton(false, tabActivePatch, tabInactivePatch));
        syncTabVisibility(); // 两页互斥（须在 introPane / equipTab 均构造后）
        setUnit(null, null);
    }

    /** 当前标签页是否为「介绍」 */
    boolean isInfoTabActive() {
        return infoTabActive;
    }

    /** 当前相位（卸下钮的写门控；DrawerPanel.refresh 每帧同步） */
    void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    /**
     * 绑定选中棋子（null = 未选中）：文案按指纹（id / 星 / 累计花费）变化才重建；卸下钮按装备 id 序列变化才重建。
     *
     * @param data 文案数据源（介绍行集查技能 / 羁绊；unit 为 null 时可为 null）
     */
    void setUnit(Unit unit, GameData data) {
        if (unit == null) {
            if (cachedUnitId != -1) {
                cachedUnitId = -1;
                title.setText(DrawerInfoText.NO_SELECTION_TITLE);
                portrait.setUnit(null);
                DrawerSkin.setContent(introPane, introBlock, DrawerInfoText.noSelectionIntro(),
                        DrawerGeometry.descriptionViewport().width());
                equipTab.sync(null);
            }
            return;
        }
        if (unit.getId() == cachedUnitId && unit.getStar() == cachedStar && unit.getSpend() == cachedSpend) {
            equipTab.sync(unit); // 文案指纹未变；仍核对装备序列（穿/卸不改变三星指纹）
            return;
        }
        cachedUnitId = unit.getId();
        cachedStar = unit.getStar();
        cachedSpend = unit.getSpend();
        title.setText(DrawerInfoText.title(unit));
        portrait.setUnit(unit);
        DrawerSkin.setContent(introPane, introBlock, DrawerInfoText.introLines(unit, data),
                DrawerGeometry.descriptionViewport().width());
        equipTab.sync(unit);
    }

    /** 两页互斥可见（不可见 Actor 不参与命中） */
    private void syncTabVisibility() {
        introPane.setVisible(infoTabActive);
        equipTab.setVisible(!infoTabActive);
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

    /**
     * 「装备」标签页：三行（武器 / 盔甲 / 饰品）文案 + 每件的卸下钮（{@link UnequipItemCommand}）。
     * 钮实例只在装备 id 序列变化时重建（ClickListener 要求 down/up 命中同一实例——沿原 UnitDetailDialog 做法，该弹窗已退役）。
     */
    private final class EquipTabPane extends Group {
        private Unit unit;
        /** 卸下按钮集的构建指纹（DrawerSelection.fingerprintChanged）：单位 id + 装备 id 序列——均未变则跳过重建 */
        private int buttonUnitId = -1;
        private List<Integer> buttonEquipIds = new ArrayList<Integer>();

        EquipTabPane() {
            setTouchable(Touchable.childrenOnly);
            setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        }

        /** 绑定单位并按需重建卸下钮（指纹未变零分配） */
        void sync(Unit unit) {
            this.unit = unit;
            if (!DrawerSelection.fingerprintChanged(buttonUnitId, buttonEquipIds, unit)) {
                return; // 序列未变：保留现按钮实例
            }
            buttonUnitId = unit != null ? unit.getId() : -1;
            buttonEquipIds = DrawerSelection.equippedIds(unit != null ? unit.getEquipped()
                    : java.util.Collections.<Equipment>emptyList());
            rebuildButtons();
        }

        /** 移除旧卸下钮并按当前穿着序重建 */
        private void rebuildButtons() {
            for (int i = getChildren().size - 1; i >= 0; i--) {
                if (getChildren().get(i) instanceof UnequipButton) {
                    getChildren().get(i).remove();
                }
            }
            if (unit == null) {
                return;
            }
            for (final Equipment item : unit.getEquipped()) {
                int row = DrawerSelection.rowOf(item.getTemplate().getSlot());
                UnequipButton button = new UnequipButton(item);
                DrawerGeometry.Rect r = DrawerGeometry.equipTabUnequipButton(row);
                button.setPosition(r.x(), r.y());
                addActor(button);
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = parentAlpha * getColor().a;
            for (int row = 0; row < DrawerGeometry.EQUIP_TAB_ROWS; row++) {
                EquipmentSlot slot = EquipmentSlot.values()[row];
                DrawerGeometry.Rect r = DrawerGeometry.equipTabRow(row);
                Equipment item = equippedIn(slot);
                float top = DrawerSkin.centeredTextTop(r.y(), r.height(), DrawerSkin.TEXT_HEIGHT);
                DrawerSkin.drawText(batch, assets.font(), EquipmentInfoText.slotLabel(slot), r.x() + ROW_LABEL_X, top, alpha);
                DrawerSkin.drawText(batch, assets.font(), item != null ? item.getTemplate().getName() : EMPTY_SLOT_LABEL,
                        r.x() + ROW_NAME_X + ROW_TEXT_PAD_X, top, alpha);
            }
            super.draw(batch, parentAlpha); // 卸下钮
        }

        private Equipment equippedIn(EquipmentSlot slot) {
            if (unit == null) {
                return null;
            }
            for (Equipment item : unit.getEquipped()) {
                if (item.getTemplate().getSlot() == slot) {
                    return item;
                }
            }
            return null;
        }
    }

    /** 单件卸下钮（UnequipItem 命令路径，input §2.4；Q6 仅备战期可写） */
    private final class UnequipButton extends Actor {
        private final Equipment item;

        UnequipButton(final Equipment item) {
            this.item = item;
            DrawerGeometry.Rect r = DrawerGeometry.equipTabUnequipButton(0);
            setSize(r.width(), r.height());
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (DrawerEquipText.writesAllowed(phase)) {
                        commandManager.addCommand(new UnequipItemCommand(item.getId()));
                    }
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = DrawerSkin.alphaOf(this, parentAlpha);
            DrawerSkin.drawPatch(batch, buttonPatch, getX(), getY(), getWidth(), getHeight(), alpha);
            DrawerSkin.drawText(batch, assets.font(), UNEQUIP_LABEL, getX() + (getWidth() - 24f) / 2f,
                    DrawerSkin.centeredTextTop(getY(), getHeight(), DrawerSkin.TEXT_HEIGHT), alpha);
        }
    }

    /** 标签页按钮（九宫格 32×30；点击切页） */
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
                    syncTabVisibility();
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
