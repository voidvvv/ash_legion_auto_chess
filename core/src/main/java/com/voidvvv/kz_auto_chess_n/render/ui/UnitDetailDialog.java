package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.UnequipItemCommand;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 棋子详情弹窗（Phase 5.1 R2 信息架构重排，基线 42d9104 布局修复版）：
 * 标题（名/星/费/累计花费）→ 属性两行 → 技能（名：desc）→ 两条羁绊（desc + 档位行，R2 混合口径）
 * → 装备区（三槽卸下按钮 x=90 自 y=100 下排 26px 步进）→ 关闭（右上角）。
 * 文案行集由 {@link UnitInfoText#detailLines} 生成（每帧 refresh 重建——幂等轻量重建，
 * 小量分配沿 SynergyPanel 口径）；单位已不在名单 → 请求关闭（isExpired）。
 */
public final class UnitDetailDialog extends Group {

    /** 弹窗几何（42d9104 的 380×200 增高至 252：内容行数 3 → 8） */
    private static final float BG_X = 70f;
    private static final float BG_Y = 44f;
    private static final float BG_W = 380f;
    private static final float BG_H = 252f;
    private static final float TEXT_X = 90f;
    private static final float TITLE_Y = 282f;
    private static final float LINE_HEIGHT = 16f;
    /** 装备区（42d9104 防遮挡方案的延续：卸下按钮自 y 下排，与文案区行底留 ≥28px） */
    private static final float EQUIP_X = 90f;
    private static final float EQUIP_Y0 = 100f;
    private static final float EQUIP_STEP = 26f;

    /** 关闭请求（Screen 实现：dialogManager.closeTop） */
    public interface CloseListener {
        void onCloseRequested();
    }

    private final CommandManager commandManager;
    private final Assets assets;
    private final Supplier<RunContext> context;
    private final CloseListener closeListener;
    private int unitId = -1;
    private List<String> lines = new ArrayList<String>();
    /** 卸下按钮集的构建指纹（feedback04）：单位 id + 装备 id 序列——均未变则跳过重建 */
    private int buttonUnitId = -1;
    private List<Integer> buttonEquipIds = new ArrayList<Integer>();

    public UnitDetailDialog(CommandManager commandManager, Assets assets,
                            Supplier<RunContext> context, CloseListener closeListener) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.context = context;
        this.closeListener = closeListener;
        Actor close = new CloseButton();
        close.setPosition(396f, 272f); // 右上角（顶缘 294 ≤ 背景顶 296）
        addActor(close);
    }

    /** 打开（棋盘域点击回调） */
    public void showUnit(int unitId) {
        this.unitId = unitId;
    }

    /** 单位已不在名单（板/席，被卖出/合并）→ true，装配点据此收起 */
    public boolean isExpired() {
        return unitGone(context.get(), unitId);
    }

    /** 过期判定（纯函数，headless 可测；未打开的 -1 与不存在 id 同判过期） */
    static boolean unitGone(RunContext ctx, int unitId) {
        return ctx.getPlayer().getUnitById(unitId) == null;
    }

    /**
     * 每帧刷新：文案行重建（幂等轻量重建，字符串不影响交互）；卸下按钮只在
     * 「单位 id + 装备 id 序列」变化时重建（feedback04）——Scene2D ClickListener
     * 要求 touchDown/touchUp 命中同一 actor 实例，每帧换实例会被 Stage 取消触摸
     * 焦点致 clicked() 永不触发（卸下点击失效根因）。
     */
    public void refresh() {
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            lines = new ArrayList<String>();
            return; // 过期帧不重建（draw 对 null 单位早退，残留按钮不可见；收起由装配点 isExpired 驱动）
        }
        lines = UnitInfoText.detailLines(unit, context.get().getGameData());
        List<Integer> ids = equippedIds(unit.getEquipped());
        if (unitId == buttonUnitId && sameEquippedIds(buttonEquipIds, ids)) {
            return; // 序列未变：保留现按钮实例
        }
        rebuildUnequipButtons(unit);
        buttonUnitId = unitId;
        buttonEquipIds = ids;
    }

    /** 移除旧卸下按钮并按当前穿着序重建（位置：EQUIP_X 自 EQUIP_Y0 下排） */
    private void rebuildUnequipButtons(Unit unit) {
        for (int i = getChildren().size - 1; i >= 0; i--) {
            if (getChildren().get(i) instanceof UnequipButton) {
                getChildren().get(i).remove();
            }
        }
        float y = EQUIP_Y0;
        for (Equipment item : unit.getEquipped()) {
            UnequipButton button = new UnequipButton(item);
            button.setPosition(EQUIP_X, y);
            addActor(button);
            y -= EQUIP_STEP;
        }
    }

    /** 装备 id 序列（重建判定输入；Equipment 按 id 全局唯一实例，id 同即实例同） */
    static List<Integer> equippedIds(List<Equipment> equipped) {
        List<Integer> ids = new ArrayList<Integer>(equipped.size());
        for (Equipment item : equipped) {
            ids.add(item.getId());
        }
        return ids;
    }

    /** 序列比对（纯函数，headless 可测）：同长且逐位 id 相等 → true */
    static boolean sameEquippedIds(List<Integer> a, List<Integer> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Unit unit = context.get().getPlayer().getUnitById(unitId);
        if (unit == null) {
            return;
        }
        Color old = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.75f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), BG_X, BG_Y, BG_W, BG_H);
        batch.setColor(old);
        float y = TITLE_Y;
        for (int i = 0; i < lines.size(); i++) { // 行集：UnitInfoText.detailLines（中文化+技能/羁绊）
            assets.font().draw(batch, lines.get(i), TEXT_X, y);
            y -= LINE_HEIGHT;
        }
        super.draw(batch, parentAlpha); // 卸下/关闭按钮
    }

    private final class CloseButton extends Actor {
        CloseButton() {
            setSize(48f, 22f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    closeListener.onCloseRequested();
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.4f, 0.32f, 0.32f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, "关闭", getX() + 12f, getY() + 15f);
        }
    }

    /** 单件卸下按钮（UnequipItem 命令路径，input §2.4） */
    private final class UnequipButton extends Actor {
        private final Equipment item;

        UnequipButton(final Equipment item) {
            this.item = item;
            setSize(200f, 24f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new UnequipItemCommand(item.getId()));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor();
            batch.setColor(0.3f, 0.34f, 0.42f, parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, item.getTemplate().getName() + "  [卸下]",
                    getX() + 8f, getY() + 16f);
        }
    }
}
