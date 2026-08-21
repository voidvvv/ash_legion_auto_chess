package com.voidvvv.kz_auto_chess_n.systems.support;

import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleStats;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierBlock;
import com.voidvvv.kz_auto_chess_n.entities.StatModifierSource;
import com.voidvvv.kz_auto_chess_n.systems.SynergySnapshot;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗系统共享测试夹具（计划 §八）：默认模板/技能构造器、微型 GameData、直接落格单位。
 * 全部夹具<b>不构造 LINE 技能</b>（Q2 裁决）。
 */
public final class BattleTestFixtures {
    /** 快速终局常用：hp100 / atk10 / armor5 / aspd1 / range1 / ms1 / 无特殊键 */
    public static final float FAST_STEP = 1f / 60f;

    private BattleTestFixtures() {
    }

    // —— 模板与技能 ——

    public static BaseStats base(float hp, float atk, float armor, float aspd, float range, float ms) {
        return new BaseStats((int) hp, (int) atk, (int) armor, aspd, (int) range, ms, 0, 100, 0);
    }

    public static UnitData tpl(String id) {
        return tpl(id, base(100, 10, 5, 1f, 1, 1f), TargetPriority.NEAREST, null);
    }

    public static UnitData tpl(String id, BaseStats stats) {
        return tpl(id, stats, TargetPriority.NEAREST, null);
    }

    public static UnitData tpl(String id, BaseStats stats, TargetPriority special) {
        return tpl(id, stats, TargetPriority.NEAREST, special);
    }

    public static UnitData tpl(String id, BaseStats stats, TargetPriority def, TargetPriority special) {
        return new UnitData(id, "夹具" + id, "兽人", "战士", 1, stats, 1.8f, def, special, "sk_melee", false);
    }

    /** 默认近战即时单体直伤技（DAMAGE 2.0） */
    public static SkillData meleeSkill() {
        return skill("sk_melee", SkillShape.SINGLE_TARGET, Delivery.MELEE_INSTANT,
                effect(SkillEffectType.DAMAGE, 2f, null, null));
    }

    public static SkillEffect effect(SkillEffectType type, Float value, StatusType status, Float duration) {
        return new SkillEffect(type, value, status, duration);
    }

    public static SkillData skill(String id, SkillShape shape, Delivery delivery, SkillEffect... effects) {
        return new SkillData(id, "夹具技" + id, "", shape, delivery, Arrays.asList(effects));
    }

    // —— 战斗实例 ——

    public static BattleStats stats(float hp, float atk) {
        return new BattleStats(hp, atk, 5f, 1f, 1f, 1f, 0f, 100f, 0f);
    }

    public static BattleStats stats(float hp, float atk, float armor, float aspd, float range, float ms) {
        return new BattleStats(hp, atk, armor, aspd, range, ms, 0f, 100f, 0f);
    }

    public static BattleUnit unit(int id, Side side, UnitData template, int x, int y) {
        BattleUnit unit = new BattleUnit(id, template, 1, side, meleeSkill(), tplStats(template));
        unit.setPosition(x, y); // 测试充当 systems 层（framework-internal 纪律的豁免主体）
        return unit;
    }

    private static BattleStats tplStats(UnitData template) {
        BaseStats b = template.getBaseStats();
        return new BattleStats(b.getHp(), b.getAttack(), b.getArmor(), b.getAttackSpeed(),
                b.getRange(), b.getMoveSpeed(), b.getLifesteal(), b.getEnergyGainRate(), b.getSkillPower());
    }

    /** 空羁绊状态 + seed 42 的 RNG；单位按传入序落格 */
    public static com.voidvvv.kz_auto_chess_n.entities.BattleState state(BattleUnit... units) {
        return state(Arrays.asList(units), SynergySnapshot.EMPTY, SynergySnapshot.EMPTY);
    }

    public static com.voidvvv.kz_auto_chess_n.entities.BattleState state(
            List<BattleUnit> units, SynergySnapshot playerSyn, SynergySnapshot enemySyn) {
        com.voidvvv.kz_auto_chess_n.entities.BattleState state =
                new com.voidvvv.kz_auto_chess_n.entities.BattleState(units, new RandomGenerator(42L), playerSyn, enemySyn);
        for (BattleUnit unit : units) {
            state.placeUnit(unit, unit.getGridX(), unit.getGridY());
        }
        return state;
    }

    /** 已落格单位工厂（positioning 委托 state()） */
    public static BattleUnit placed(int id, Side side, UnitData template, int x, int y) {
        return unit(id, side, template, x, y);
    }

    // —— 微型 GameData ——

    /** 单技能 + 可选羁绊的微型数据集（声明序确定） */
    public static GameData microData(SkillData skill, SynergyData... synergies) {
        Map<String, UnitData> units = new LinkedHashMap<String, UnitData>();
        Map<String, SkillData> skills = new LinkedHashMap<String, SkillData>();
        skills.put(skill.getId(), skill);
        Map<String, SynergyData> syn = new LinkedHashMap<String, SynergyData>();
        for (SynergyData s : synergies) {
            syn.put(s.getId(), s);
        }
        return new GameData(units, skills, syn, new LinkedHashMap<String, com.voidvvv.kz_auto_chess_n.data.SceneData>(),
                new ArrayList<String>());
    }

    public static SynergyData synRace(String id, String key, int count, EffectData... effects) {
        return new SynergyData(id, id, SynergySource.RACE, key,
                Arrays.asList(new SynergyData.Threshold(count, Arrays.asList(effects))));
    }

    public static EffectData statEffect(StatKey key, EffectOp op, float value) {
        return new EffectData(key, null, op, value, EffectTarget.ALLIES);
    }

    public static EffectData shieldEffect(float ratio) {
        return new EffectData(null, StatusType.SHIELD, null, ratio, EffectTarget.ALLIES);
    }

    public static StatModifierSource modifierSource(StatKey key, EffectOp op, float value) {
        final StatModifierBlock block = StatModifierBlock.of(key, op, value);
        return new StatModifierSource() {
            @Override
            public StatModifierBlock modifiers() {
                return block;
            }
        };
    }
}
