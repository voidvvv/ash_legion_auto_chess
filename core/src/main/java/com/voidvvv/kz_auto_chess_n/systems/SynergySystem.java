package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 羁绊系统（battle §三；无状态实例类）：双通道统计 → 替换制档位 → 不可变快照。
 *
 * <p>双通道：RACE 按 race、CLASS 按 unitClass 匹配 synergy.key 计数——仅已登记 key 的值
 * 计数（风味值不计，data_schema §六 V1.3）；每单位在其种族羁绊与职业羁绊各计 1 次
 * （同名重复各计 1，沿 Phase 2 有放回抽取口径）。
 * 档位替换制：count 达到的最高档生效该档<b>全量</b>效果（{@code SynergyData.activeThreshold}）。
 */
public final class SynergySystem {

    /**
     * 结算一侧阵容的羁绊快照。
     *
     * @param templates 该侧全部上场模板（玩家侧 = getDeployedUnits 映射 getTemplate；敌方侧 = WaveSpec 模板集）
     * @param data      静态数据（synergies 按 JSON 声明序遍历，结果序确定）
     */
    public SynergySnapshot resolve(Collection<UnitData> templates, GameData data) {
        Objects.requireNonNull(templates, "templates 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");

        List<SynergySnapshot.ActiveSynergy> actives = new ArrayList<SynergySnapshot.ActiveSynergy>();
        StatModifierBlock statModifiers = StatModifierBlock.empty();
        List<EffectData> openingEffects = new ArrayList<EffectData>();

        for (SynergyData synergy : data.getSynergies().values()) {
            int count = 0;
            for (UnitData template : templates) {
                String value = synergy.getSource() == SynergySource.RACE
                        ? template.getRace() : template.getUnitClass();
                if (synergy.getKey().equals(value)) {
                    count++;
                }
            }
            SynergyData.Threshold tier = synergy.activeThreshold(count);
            if (tier == null) {
                continue; // 未达最低档
            }
            actives.add(new SynergySnapshot.ActiveSynergy(synergy.getId(), synergy.getName(), tier.getCount()));
            for (EffectData effect : tier.getEffects()) {
                if (effect.isStatChannel()) {
                    statModifiers = statModifiers.plus(StatModifierBlock.of(
                            effect.getStat(), effect.getOp(), effect.getValue()));
                } else {
                    openingEffects.add(effect);
                }
            }
        }

        if (actives.isEmpty()) {
            return SynergySnapshot.EMPTY;
        }
        return new SynergySnapshot(actives, statModifiers, openingEffects);
    }
}
