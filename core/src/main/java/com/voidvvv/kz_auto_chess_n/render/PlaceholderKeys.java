package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 占位图集命名约定（render §7.1/§7.5；纯函数 + 全 key 枚举，零 Gdx）。
 *
 * <p>单位帧 {@code {unitId}_{anim}_{frame}}（idle2/walk2/attack3/cast2/death3）；
 * 技能 {@code fx_{skillId}} 与落点 {@code fx_{skillId}_burst}；状态 {@code fx_status_{type}}；
 * 数字 {@code fx_digit_0~9}；通用件 4 键；UI 框架件键集另立 {@link UiKeys}（必需 12 + 加分 8，
 * 同入 enumerateFor）。<b>合成 skillId 亦入约定</b>（2026-11-21 亡语 FX 缺口回归）：
 * 亡语直伤 {@code deathrattle_{unitId}}（{@link DeathrattleSpec#causeSkillId}）与亡语弹
 * {@code deathrattle_projectile}（{@link DeathrattleSpec#VOLLEY_SKILL_ID}）各派生落点 burst 键。
 * enumerateFor 供 PlaceholderArt 全量生成（防漏）。
 */
public final class PlaceholderKeys {

    public static final String ANIM_IDLE = "idle";
    public static final String ANIM_WALK = "walk";
    public static final String ANIM_ATTACK = "attack";
    public static final String ANIM_CAST = "cast";
    public static final String ANIM_DEATH = "death";
    public static final String[] ANIMS = {ANIM_IDLE, ANIM_WALK, ANIM_ATTACK, ANIM_CAST, ANIM_DEATH};

    public static final String CAST_DEFAULT = "fx_cast_default";
    public static final String HIT_DEFAULT = "fx_hit_default";
    public static final String PANEL_9SLICE = "ui_panel_9slice";
    public static final String WHITE = "fx_white";

    private PlaceholderKeys() {
    }

    public static String unitFrame(String unitId, String anim, int frame) {
        return unitId + "_" + anim + "_" + frame;
    }

    public static String skillFx(String skillId) {
        return "fx_" + skillId;
    }

    public static String skillFxBurst(String skillId) {
        return "fx_" + skillId + "_burst";
    }

    public static String statusFx(StatusType type) {
        return "fx_status_" + type.name().toLowerCase(Locale.ROOT);
    }

    public static String digitFx(int digit) {
        return "fx_digit_" + digit;
    }

    /** 各动画帧数（render §7.1） */
    public static int frameCount(String anim) {
        if (ANIM_IDLE.equals(anim)) {
            return 2;
        }
        if (ANIM_WALK.equals(anim)) {
            return 2;
        }
        if (ANIM_ATTACK.equals(anim)) {
            return 3;
        }
        if (ANIM_CAST.equals(anim)) {
            return 2;
        }
        if (ANIM_DEATH.equals(anim)) {
            return 3;
        }
        throw new IllegalArgumentException("未知动画名: " + anim);
    }

    /** 全 key 枚举：unitId×5 动画×帧数 + skillId×2 + 亡语合成 id burst（逐亡语单位 + 亡语弹）+
     *  StatusType×1 + 数字×10 + 通用件×4 + UI 框架件×20（{@link UiKeys#ALL}：必需 12 + 加分 8，UI 抽屉化批0） */
    public static List<String> enumerateFor(GameData data) {
        List<String> keys = new ArrayList<String>();
        for (String unitId : data.getUnits().keySet()) {
            for (String anim : ANIMS) {
                for (int frame = 0; frame < frameCount(anim); frame++) {
                    keys.add(unitFrame(unitId, anim, frame));
                }
            }
        }
        for (String skillId : data.getSkills().keySet()) {
            keys.add(skillFx(skillId));
            keys.add(skillFxBurst(skillId));
        }
        for (com.voidvvv.kz_auto_chess_n.data.UnitData unit : data.getUnits().values()) {
            if (unit.getDeathrattle() != null) {
                keys.add(skillFxBurst(DeathrattleSpec.causeSkillId(unit.getId()))); // 亡语直伤落点
            }
        }
        keys.add(skillFxBurst(DeathrattleSpec.VOLLEY_SKILL_ID)); // 亡语弹命中落点（通用 1 键）
        for (StatusType type : StatusType.values()) {
            keys.add(statusFx(type));
        }
        for (int digit = 0; digit <= 9; digit++) {
            keys.add(digitFx(digit));
        }
        keys.add(CAST_DEFAULT);
        keys.add(HIT_DEFAULT);
        keys.add(PANEL_9SLICE);
        keys.add(WHITE);
        keys.addAll(UiKeys.allIds()); // UI 框架件（与 UiKeys 键集同源，防漏生成）
        return keys;
    }
}
