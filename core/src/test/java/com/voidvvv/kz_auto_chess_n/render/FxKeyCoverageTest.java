package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FX 键集完备性锁定（2026-11-21 亡语 FX 缺口回归固化）：渲染层可能请求的全部 FX 键
 * 必须落在 {@link PlaceholderKeys#enumerateFor}（= PlaceholderArt 生成契约，verifyCoverage
 * 强制逐键生成；无 .atlas 文件——占位阶段图集为代码生成，本测试即"请求键全集 ⊆ 图集"）。
 * 键全集从<b>代码约定 + 真实数据</b>派生（非手抄清单）：skills.json、units.json 亡语 spec、
 * StatusType 枚举、数字/通用件常量——任何新增合成 skillId 约定漏登 enumerateFor 会在此红测。
 */
class FxKeyCoverageTest {

    private static GameData data;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** 渲染层可能请求的 FX 键全集（约定派生：FxLayer sparkCast/sparkBurst/statusDots + ProjectileView） */
    private static Set<String> requestableFxKeys() {
        Set<String> keys = new HashSet<String>();
        for (String skillId : data.getSkills().keySet()) {
            keys.add(PlaceholderKeys.skillFx(skillId));            // ProjectileView 弹道图 / sparkCast 起手
            keys.add(PlaceholderKeys.skillFxBurst(skillId));       // sparkBurst 落点
        }
        for (UnitData unit : data.getUnits().values()) {
            if (unit.getDeathrattle() != null) {
                // 亡语直伤（AREA/KILLER 等 applyTrueDamage 带 causeId）HIT → sparkBurst
                keys.add(PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId(unit.getId())));
            }
        }
        keys.add(PlaceholderKeys.skillFxBurst(DeathrattleSpec.VOLLEY_SKILL_ID)); // 亡语弹命中（C6 真伤路由）
        for (StatusType type : StatusType.values()) {
            keys.add(PlaceholderKeys.statusFx(type));              // 状态色点
        }
        for (int digit = 0; digit <= 9; digit++) {
            keys.add(PlaceholderKeys.digitFx(digit));              // 飘字数字
        }
        keys.add(PlaceholderKeys.CAST_DEFAULT);                    // sparkCast 兜底
        keys.add(PlaceholderKeys.HIT_DEFAULT);                     // sparkBurst 兜底 / regionFx 降级目标
        return keys;
    }

    @Test
    @DisplayName("请求键全集 ⊆ enumerateFor（= PlaceholderArt 生成契约）——亡语合成 id 全覆盖")
    void allRequestableFxKeysCoveredByEnumeration() {
        Set<String> enumerated = new HashSet<String>(PlaceholderKeys.enumerateFor(data));
        for (String key : requestableFxKeys()) {
            assertThat(enumerated.contains(key))
                    .as("FX 键 %s 必须在图集枚举内（缺失即 21 分钟局式硬崩）", key).isTrue();
        }
    }

    @Test
    @DisplayName("回归锚点：骸骨士兵亡语直伤键与亡语弹通用键均在枚举内（修复前缺失即崩）")
    void regressionDeathrattleKeysPresent() {
        List<String> enumerated = PlaceholderKeys.enumerateFor(data);
        assertThat(enumerated).contains("fx_deathrattle_unit_skeleton_soldier_burst"); // 21 分钟局崩溃键
        assertThat(enumerated).contains(PlaceholderKeys.skillFxBurst(DeathrattleSpec.VOLLEY_SKILL_ID));
        // 全部 5 个带亡语单位的直伤键逐一在列（不只修骸骨士兵一个）
        assertThat(enumerated).contains(
                PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId("unit_skeleton_soldier")),
                PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId("unit_debt_collector")),
                PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId("unit_wraith")),
                PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId("unit_lamplighter")),
                PlaceholderKeys.skillFxBurst(DeathrattleSpec.causeSkillId("unit_bowstring_archer")));
    }

    @Test
    @DisplayName("枚举无重复（合成 id 与真实 skillId 无碰撞）；亡语键条数 = 亡语单位数 + 亡语弹 1")
    void deathrattleKeyCountMatchesData() {
        List<String> enumerated = PlaceholderKeys.enumerateFor(data);
        assertThat(enumerated).hasSize((int) enumerated.stream().distinct().count());

        int deathrattleUnits = 0;
        for (UnitData unit : data.getUnits().values()) {
            if (unit.getDeathrattle() != null) {
                deathrattleUnits++;
            }
        }
        assertThat(deathrattleUnits).isEqualTo(5); // v5 亡灵六子中五亡语（死骑走 undying）
        List<String> deathrattleKeys = new ArrayList<String>();
        for (String key : enumerated) {
            if (key.startsWith("fx_" + DeathrattleSpec.CAUSE_SKILL_ID_PREFIX)) {
                deathrattleKeys.add(key);
            }
        }
        assertThat(deathrattleKeys).hasSize(deathrattleUnits + 1); // + 亡语弹通用键
    }
}
