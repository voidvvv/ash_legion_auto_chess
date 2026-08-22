package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

/**
 * 宝箱三选一弹窗（RESULT 胜局；Q2 裁决 A）。内容进 RESULT 时已 roll 好（architecture §4.1），
 * 本弹窗只读 offer；点击入队 PickChest(option)，领取后 Screen 观察 pendingChest==null 收起（§6.CP29）。
 * optionText/optionTint 为包级静态纯函数（headless 已测——计划测试要点授权的执行期提取）。
 */
public final class ChestDialog extends Group {

    /** 选项底色（static final：渲染段零分配；传说金棕与 InventoryPanel 同源） */
    private static final Color TINT_LEGENDARY = new Color(0.55f, 0.42f, 0.12f, 1f);
    private static final Color TINT_DEFAULT = new Color(0.3f, 0.32f, 0.4f, 1f);

    private final CommandManager commandManager;
    private final Assets assets;
    private final GameData data;
    private ChestOffer offer;

    public ChestDialog(CommandManager commandManager, Assets assets, GameData data) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.data = data;
        for (int i = 0; i < 3; i++) {
            addActor(new OptionButton(i));
        }
    }

    /** Screen 在 push 前刷新（offer 不可变，无逐帧刷新需求） */
    public void refresh(ChestOffer offer) {
        this.offer = offer;
    }

    /** 选项文案：金币/经验书带数额，装备取表内模板名 */
    static String optionText(GameData data, ChestOption option) {
        switch (option.getKind()) {
            case GOLD:
                return "金币 +" + option.getAmount();
            case EXP_BOOK:
                return "经验 +" + option.getAmount();
            case EQUIPMENT:
            default:
                return data.getEquipment(option.getEquipmentId()).getName();
        }
    }

    /** 选项底色：仅传说装备金棕，其余默认 */
    static Color optionTint(GameData data, ChestOption option) {
        if (option.getKind() == ChestOption.Kind.EQUIPMENT
                && data.getEquipment(option.getEquipmentId()).getRarity() == EquipmentRarity.LEGENDARY) {
            return TINT_LEGENDARY;
        }
        return TINT_DEFAULT;
    }

    private final class OptionButton extends Actor {
        private final int index;

        OptionButton(final int index) {
            this.index = index;
            setSize(120f, 60f);
            setPosition(140f + index * 130f, 130f);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    commandManager.addCommand(new PickChestCommand(index));
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (offer == null) {
                return;
            }
            ChestOption option = offer.optionAt(index);
            Color tint = optionTint(data, option);
            Color old = batch.getColor();
            batch.setColor(tint.r, tint.g, tint.b, 0.95f * parentAlpha);
            batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), getX(), getY(), getWidth(), getHeight());
            batch.setColor(old);
            assets.font().draw(batch, optionText(data, option), getX() + 14f, getY() + 34f);
            assets.font().draw(batch, "选择", getX() + 44f, getY() + 14f);
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0f, 0f, 0f, 0.8f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.WHITE), 110f, 100f, 420f, 140f);
        batch.setColor(old);
        assets.font().getData().setScale(1.5f);
        assets.font().draw(batch, offer != null && offer.isBoss() ? "BOSS 宝箱" : "宝箱", 268f, 216f);
        assets.font().getData().setScale(1f);
        super.draw(batch, parentAlpha);
    }
}
