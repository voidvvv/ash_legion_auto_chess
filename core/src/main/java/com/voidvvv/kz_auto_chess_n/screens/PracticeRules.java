package com.voidvvv.kz_auto_chess_n.screens;

import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;

final class PracticeRules {
    private PracticeRules() {
    }

    static String mutationBlock(RunContext ctx) {
        if (ctx == null) return "请先开始或继续远征。";
        if (ctx.getMode().closesArchiveTrack()) return "测试档与样片局只读。";
        RunState run = ctx.getRunState();
        if (run.getPendingChest() != null) return "请先领取当前宝箱。";
        if (!run.isRunStarted() || run.getPhase() != GamePhase.SHOPPING || ctx.getBattleState() != null) {
            return "只能在备战阶段调整。";
        }
        return null;
    }

    static int remainingExp(Player player) {
        int remaining = 0;
        for (int level = player.getLevel(); level < GameBalance.MAX_PLAYER_LEVEL; level++) {
            remaining += GameBalance.expToNextLevel(level);
        }
        return Math.max(0, remaining - player.getCurrentExp());
    }

    static String setGold(RunContext ctx, int gold) {
        if (gold < 0) return "金币必须在 0 至 " + Integer.MAX_VALUE + " 之间。";
        Player player = ctx.getPlayer();
        if (gold != player.getGold()) {
            player.addGold(gold - player.getGold());
            ctx.getRunState().markPractice();
        }
        return null;
    }

    static String changeExp(RunContext ctx, int delta) {
        Player player = ctx.getPlayer();
        if (player.getLevel() == GameBalance.MAX_PLAYER_LEVEL && delta != 0) {
            return "已达最高等级，经验不可调整。";
        }
        int minimum = -player.getCurrentExp();
        int maximum = remainingExp(player);
        if (delta < minimum || delta > maximum) {
            return "经验改变量必须在 " + minimum + " 至 +" + maximum + " 之间，且不能降级。";
        }
        if (delta != 0) {
            player.addExp(delta);
            ctx.getRunState().markPractice();
        }
        return null;
    }
}
