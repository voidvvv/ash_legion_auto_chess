package com.voidvvv.kz_auto_chess_n.command;

/** 购买经验（4 金 = 4 经验，GDD §3.5）；Lv.7 封顶禁买（handler 校验 + UI 灰置） */
public final class BuyExpCommand implements GameCommand {
    public static final BuyExpCommand INSTANCE = new BuyExpCommand();

    private BuyExpCommand() {
    }

    @Override
    public String toString() { return "BuyExp"; }
}
