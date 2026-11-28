package com.voidvvv.kz_auto_chess_n.command;

/** 切换下一次免费补货时保留余货。 */
public final class ToggleShopLockCommand implements GameCommand {
    public static final ToggleShopLockCommand INSTANCE = new ToggleShopLockCommand();

    private ToggleShopLockCommand() {
    }

    @Override
    public String toString() { return "ToggleShopLock"; }
}
