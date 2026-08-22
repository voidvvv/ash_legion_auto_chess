package com.voidvvv.kz_auto_chess_n.command;

/** 轮内主动刷新（2 金整批替换）；轮首免费那次是系统行为不入队（architecture §4.1） */
public final class RefreshShopCommand implements GameCommand {
    public static final RefreshShopCommand INSTANCE = new RefreshShopCommand();

    private RefreshShopCommand() {
    }

    @Override
    public String toString() { return "RefreshShop"; }
}
