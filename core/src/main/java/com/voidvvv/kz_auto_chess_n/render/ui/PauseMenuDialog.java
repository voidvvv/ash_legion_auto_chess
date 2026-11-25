package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.voidvvv.kz_auto_chess_n.command.AbandonRunCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.render.Assets;
import com.voidvvv.kz_auto_chess_n.render.PlaceholderKeys;

/**
 * 暂停菜单（Q5 裁决 MVP = 继续/放弃；设置 Dialog 推 Phase 7）。放弃走二次确认
 * （GDD §2.1 防误触）：确认子弹窗 push 同一 dialogStage（栈式）→ AbandonRunCommand 入队。
 * 「返回开始菜单」不弃档：只切屏（Screen 的 hide() 补写备战快照——存档点仅备战，
 * 裁决 D10），不走 AbandonRunCommand，故不触发终局结算/奖励、不删快照，主菜单「继续远征」仍可续。
 * 模拟冻结由 UIDialogManager.isShowing() 驱动（口径 #14），本类不触碰模拟。
 */
public final class PauseMenuDialog extends Group {

    /** 返回主菜单回调（Screen 实现：setScreen(MainMenuScreen)——保留快照，不结算） */
    public interface MenuListener {
        void onMenuRequested();
    }

    private final CommandManager commandManager;
    private final Assets assets;
    private final UIDialogManager dialogManager;
    private final MenuListener menuListener;
    private final Group confirmDialog = new Group();

    public PauseMenuDialog(CommandManager commandManager, Assets assets, UIDialogManager dialogManager,
                           MenuListener menuListener) {
        this.commandManager = commandManager;
        this.assets = assets;
        this.dialogManager = dialogManager;
        this.menuListener = menuListener;
        Actor resume = new MenuButton(assets, "继续") {
            @Override
            protected void onClicked() {
                dialogManager.closeTop();
            }
        };
        resume.setPosition(250f, 170f);
        addActor(resume);
        Actor menu = new MenuButton(assets, "返回开始菜单") {
            @Override
            protected void onClicked() {
                menuListener.onMenuRequested(); // 不 closeTop：整屏切换即弃本弹窗栈
            }
        };
        menu.setPosition(250f, 130f);
        addActor(menu);
        Actor abandon = new MenuButton(assets, "放弃远征") { // 破坏性操作离「继续」最远
            @Override
            protected void onClicked() {
                showConfirm();
            }
        };
        abandon.setPosition(250f, 90f);
        addActor(abandon);

        Actor yes = new MenuButton(assets, "确认放弃") {
            @Override
            protected void onClicked() {
                commandManager.addCommand(AbandonRunCommand.INSTANCE);
                dialogManager.closeTop(); // 收确认
                dialogManager.closeTop(); // 收菜单（RUN_END 后 Screen 观察收全）
            }
        };
        yes.setPosition(190f, 150f);
        confirmDialog.addActor(yes);
        Actor no = new MenuButton(assets, "取消") {
            @Override
            protected void onClicked() {
                dialogManager.closeTop(); // 只收确认，菜单保留
            }
        };
        no.setPosition(330f, 150f);
        confirmDialog.addActor(no);
    }

    /** 唤起二次确认（push 幂等——取消后可再次唤起，不设一次性旗标） */
    private void showConfirm() {
        dialogManager.push(confirmDialog);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor();
        batch.setColor(0.1f, 0.1f, 0.14f, 0.95f * parentAlpha);
        batch.draw(assets.region(PlaceholderKeys.PANEL_9SLICE), 220f, 70f, 200f, 160f);
        batch.setColor(old);
        assets.titleFont().draw(batch, "暂停", 220f, 214f, 200f,
                com.badlogic.gdx.utils.Align.center, false);
        super.draw(batch, parentAlpha);
    }

    /** 自绘菜单按钮（Assets 构造注入——render §7.6 注入式裁决，禁静态持有；原创四态按钮壳） */
    abstract static class MenuButton extends Actor {
        final Assets assets;
        private final String text;
        private final ClickListener listener;

        MenuButton(final Assets assets, final String text) {
            this.assets = assets;
            this.text = text;
            setSize(140f, 32f);
            this.listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onClicked();
                }
            };
            addListener(listener);
        }

        protected abstract void onClicked();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            com.voidvvv.kz_auto_chess_n.render.ButtonArt.drawShell(batch, assets, listener,
                    getX(), getY(), getWidth(), getHeight(), parentAlpha, false);
            assets.uiFont().draw(batch, text, getX(), getY() + 21f, getWidth(),
                    com.badlogic.gdx.utils.Align.center, false);
        }
    }
}
