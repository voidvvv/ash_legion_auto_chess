package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import java.util.ArrayList;
import java.util.List;

/** 根据当前棋盘预演档位变化。候补仅在假设上场或参与真实合成时影响结果。 */
final class RosterPreviewText {
    private RosterPreviewText() {}

    static List<String> shop(RunContext ctx, int slot) {
        UnitData card = ctx.getShop().slotAt(slot);
        Player player = ctx.getPlayer();
        List<String> lines = UnitInfoText.previewStatsAndSkill(card, ctx.getGameData(), 11);
        lines.add(card.getRace() + " · " + card.getUnitClass());
        int copies = ShopSystem.countSameTemplateStar(player, card.getId(), 1);
        lines.add("已有一星 " + copies + "/3");
        if (!ShopBar.canBuy(ctx, slot)) {
            lines.add(player.canAfford(card.getCost()) ? "备战席已满" : "金币不足");
        } else if (copies >= 2) {
            lines.add("购入即合成");
            int lost = ShopSystem.deployedLostOnPurchase(player, card);
            if (lost > 0) lines.add("上阵同名减少 " + lost);
            lines.addAll(changes(ctx, null, card, lost));
        } else if (player.getDeployedUnits().size() < player.getPopulationCap()) {
            lines.add("购入进后备 · 上阵后");
            List<String> diff = changes(ctx, card, null, 0);
            if (diff.isEmpty()) {
                for (SynergyData synergy : ctx.getGameData().getSynergies().values()) {
                    if (!matches(synergy, card)) continue;
                    int after = deployedCount(player, synergy) + 1;
                    for (SynergyData.Threshold threshold : synergy.getThresholds()) {
                        if (after < threshold.getCount()) {
                            lines.add(synergy.getName() + " " + after + "/" + threshold.getCount());
                            break;
                        }
                    }
                }
            } else {
                lines.addAll(diff);
            }
        } else {
            lines.add("人口已满 · 购入进后备");
            // 给出一个明确的合法换位例子。拖放预演使用玩家实际指向的格子。
            Unit example = null;
            List<String> exampleChanges = new ArrayList<String>();
            for (Unit out : player.getDeployedUnits()) {
                List<String> diff = changes(ctx, card, out.getTemplate(), 1);
                if (diff.size() > exampleChanges.size()) {
                    example = out;
                    exampleChanges = diff;
                }
            }
            if (example != null) {
                lines.addAll(UnitInfoText.wrap("举例：下阵" + example.getTemplate().getName(), 11));
                lines.addAll(exampleChanges);
            } else {
                lines.add("上阵后档位不变");
            }
        }
        return lines;
    }

    static List<String> drop(RunContext ctx, Unit unit, PlacementTarget.Cell cell) {
        List<String> lines = new ArrayList<String>();
        if (cell.gridX < 0 || cell.gridX >= GameBalance.BOARD_COLS || cell.gridY < 4 || cell.gridY > 6) return lines;
        Player player = ctx.getPlayer();
        Unit out = player.deployedAt(cell.gridX, cell.gridY);
        if (out == null && player.getDeployedUnits().size() >= player.getPopulationCap()) {
            lines.add("人口已满 · 拖至棋子上");
            return lines;
        }
        lines.addAll(UnitInfoText.wrap(out == null ? "上阵" + unit.getTemplate().getName()
                : "下阵" + out.getTemplate().getName(), 11));
        List<String> diff = changes(ctx, unit.getTemplate(), out == null ? null : out.getTemplate(), 1);
        if (diff.isEmpty()) lines.add("羁绊档位不变");
        else lines.addAll(diff);
        return lines;
    }

    private static List<String> changes(RunContext ctx, UnitData in, UnitData out, int removed) {
        List<String> lines = new ArrayList<String>();
        for (SynergyData synergy : ctx.getGameData().getSynergies().values()) {
            int before = deployedCount(ctx.getPlayer(), synergy);
            int after = before + (matches(synergy, in) ? 1 : 0) - (matches(synergy, out) ? removed : 0);
            SynergyData.Threshold previous = synergy.activeThreshold(before);
            SynergyData.Threshold next = synergy.activeThreshold(after);
            if (previous != next) {
                lines.add(synergy.getName() + " " + before + "→" + after
                        + (after > before ? " 升档" : " 掉档"));
                // 只展示一个代表性变化，完整档位说明仍在羁绊面板。
                if (next == null || (previous != null && hasAttackStatus(previous))) continue;
                for (EffectData effect : next.getEffects()) {
                    if (effect.getMechanic() == SynergyMechanic.BASIC_ATTACK_STATUS) {
                        lines.add("普攻" + UnitInfoText.statusLabel(effect.getStatus()));
                        break;
                    }
                }
            }
        }
        return lines;
    }

    static List<String> enemy(RunContext ctx, UnitData card) {
        List<String> lines = UnitInfoText.previewStatsAndSkill(card, ctx.getGameData(), 9);
        lines.add(0, "（敌方）");
        lines.add("敌阵本轮生效档");
        int cap = GameBalance.enemySynergyCountCap(ctx.getRunState().getRound());
        int first = lines.size();
        if (ctx.getBattleState() != null) {
            for (SynergySnapshot.ActiveSynergy active : ctx.getBattleState().getEnemySynergies().getActives()) {
                lines.add(active.getName() + " " + active.getThresholdCount() + "档");
            }
            if (first == lines.size()) lines.add("未激活");
            return lines;
        }
        for (SynergyData synergy : ctx.getGameData().getSynergies().values()) {
            int count = 0;
            for (WaveSpec spec : ctx.getRunState().getEnemyWave()) {
                if (matches(synergy, spec.getTemplate())) count++;
            }
            SynergyData.Threshold tier = synergy.activeThreshold(Math.min(count, cap));
            if (tier != null) lines.add(synergy.getName() + " " + tier.getCount() + "档");
        }
        if (first == lines.size()) lines.add("未激活");
        return lines;
    }

    private static boolean hasAttackStatus(SynergyData.Threshold tier) {
        for (EffectData effect : tier.getEffects()) {
            if (effect.getMechanic() == SynergyMechanic.BASIC_ATTACK_STATUS) return true;
        }
        return false;
    }

    private static int deployedCount(Player player, SynergyData synergy) {
        int count = 0;
        for (Unit unit : player.getDeployedUnits()) {
            if (matches(synergy, unit.getTemplate())) count++;
        }
        return count;
    }

    private static boolean matches(SynergyData synergy, UnitData card) {
        return card != null && synergy.getKey().equals(synergy.getSource() == SynergySource.RACE
                ? card.getRace() : card.getUnitClass());
    }
}
