package com.voidvvv.kz_auto_chess_n.command;

/** 宝箱三选一领取（RESULT 期）：内容进 RESULT 时已 roll 好，本命令零 RNG（architecture §4.1） */
public final class PickChestCommand implements GameCommand {
    private final int optionIndex;

    public PickChestCommand(int optionIndex) { this.optionIndex = optionIndex; }

    public int getOptionIndex() { return optionIndex; }

    @Override
    public String toString() { return "PickChest(option=" + optionIndex + ")"; }
}
