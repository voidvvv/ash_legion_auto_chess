package com.voidvvv.kz_auto_chess_n.command;

/** 放弃远征（GDD §2.1：暂停菜单 + 二次确认；按已达波数结算部分熟练度） */
public final class AbandonRunCommand implements GameCommand {
    public static final AbandonRunCommand INSTANCE = new AbandonRunCommand();

    private AbandonRunCommand() {
    }

    @Override
    public String toString() { return "AbandonRun"; }
}
