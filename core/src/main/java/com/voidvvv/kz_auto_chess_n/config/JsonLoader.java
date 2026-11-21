package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.CastCondition;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleKind;
import com.voidvvv.kz_auto_chess_n.data.DeathrattleSpec;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.data.HeroPassiveType;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergyMechanic;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.data.UndyingSpec;
import com.voidvvv.kz_auto_chess_n.data.Vocab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.checkNonNegative;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.checkUnknownKeys;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.fail;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.join;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalBool;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalFloat;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalFloatObj;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalInt;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalString;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.optionalVocab;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.parseArray;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.require;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.requireFloat;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.requireInt;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.requireObject;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.requireString;
import static com.voidvvv.kz_auto_chess_n.config.JsonReadUtils.requireVocab;

/**
 * 静态数据加载器（data_schema §九 加载期校验清单的完整实现）。
 *
 * <p>实现口径（Q4 已定）：不用 gdx Json 的反射反序列化，用 {@link JsonReader} → {@link JsonValue}
 * 手工映射到 POJO——每个字段显式读取 + 显式校验，报错精确到"文件/条目id/字段路径"；
 * Java 侧字段名 {@code unitClass} 对应 JSON 的 {@code "class"}（关键字冲突）。
 *
 * <p>未知字段一律报错（fail-fast，防拼写错误静默失效）。
 * 软告警（孤儿技能 / 孤儿羁绊 / 风味种族聚合 / ALL_ENEMIES 非 Boss 引用）不阻断，
 * 汇入 {@link GameData#getWarnings()} 由调用方处置；本类不调用 Gdx.*（分层约束，data/config 仅许 Json/FileHandle）。
 */
public final class JsonLoader {

    private static final float DEFAULT_UPGRADE_MULTIPLIER = 1.8f;

    private JsonLoader() {
    }

    /** 从目录按标准文件名加载：units / skills / synergies / scenes / equipments / heroes */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"),
                dataDir.child("equipments.json"), dataDir.child("heroes.json"));
    }

    /** 兼容重载：无装备文件（存量测试路径）——装备表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, null);
    }

    /** 兼容重载：无英雄文件（Phase 5 存量测试路径）——英雄表为空 */
    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile) {
        return load(unitsFile, skillsFile, synergiesFile, scenesFile, equipmentsFile, null);
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile,
                                FileHandle equipmentsFile, FileHandle heroesFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        Map<String, EquipmentData> equipments = parseEquipments(equipmentsFile);
        Map<String, HeroData> heroes = parseHeroes(heroesFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, heroes, warnings);
        warnEmptyRarityPools(equipments, warnings);
        return new GameData(units, skills, synergies, scenes, equipments, heroes, warnings);
    }

    // ==================================================================
    // units.json
    // ==================================================================

    private static Map<String, UnitData> parseUnits(FileHandle file) {
        Map<String, UnitData> result = new LinkedHashMap<String, UnitData>();
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "race", "class", "cost", "baseStats",
                    "upgradeMultiplier", "defaultPriority", "specialPriority", "skillId", "boss",
                    "deathrattle", "undying");

            String name = requireString(e, "name", w);
            String race = requireString(e, "race", w);
            String unitClass = requireString(e, "class", w);
            boolean boss = optionalBool(e, "boss", w, false);
            int cost = requireInt(e, "cost", w);
            if (boss) {
                if (cost != 0) {
                    fail(w + "cost", "Boss 模板 cost 必须 = 0，实际=" + cost);
                }
            } else if (cost < 1 || cost > 3) {
                fail(w + "cost", "非 Boss 模板 cost ∈ {1,2,3}，实际=" + cost);
            }

            BaseStats baseStats = parseBaseStats(require(e, "baseStats", w), w + "baseStats/");

            float upgradeMultiplier = optionalFloat(e, "upgradeMultiplier", w, DEFAULT_UPGRADE_MULTIPLIER);
            if (upgradeMultiplier <= 0) {
                fail(w + "upgradeMultiplier", "必须 > 0，实际=" + upgradeMultiplier);
            }
            TargetPriority defaultPriority = optionalVocab(e, "defaultPriority", TargetPriority.class, w, TargetPriority.NEAREST);
            TargetPriority specialPriority = optionalVocab(e, "specialPriority", TargetPriority.class, w, null);
            String skillId = requireString(e, "skillId", w);

            JsonValue deathrattleNode = e.get("deathrattle");
            DeathrattleSpec deathrattle = deathrattleNode == null || deathrattleNode.isNull()
                    ? null : parseDeathrattle(deathrattleNode, w + "deathrattle/");
            JsonValue undyingNode = e.get("undying");
            UndyingSpec undying = undyingNode == null || undyingNode.isNull()
                    ? null : parseUndying(undyingNode, w + "undying/");

            result.put(id, new UnitData(id, name, race, unitClass, cost, baseStats,
                    upgradeMultiplier, defaultPriority, specialPriority, skillId, boss,
                    deathrattle, undying));
        }
        return result;
    }

    /** 亡语参数（units.json "deathrattle"，v5 亡灵线；kind 逐词必填校验） */
    private static DeathrattleSpec parseDeathrattle(JsonValue node, String w) {
        requireObject(node, w);
        checkUnknownKeys(node, w, "kind", "ratio", "radius", "statusType", "statusPower",
                "statusDuration", "regenRatio", "duration");
        DeathrattleKind kind = requireVocab(node, "kind", DeathrattleKind.class, w);
        float ratio = optionalFloat(node, "ratio", w, 0f);
        int radius = optionalInt(node, "radius", w, 0);
        StatusType statusType = optionalVocab(node, "statusType", StatusType.class, w, null);
        float statusPower = optionalFloat(node, "statusPower", w, 0f);
        float statusDuration = optionalFloat(node, "statusDuration", w, 0f);
        float regenRatio = optionalFloat(node, "regenRatio", w, 0f);
        float duration = optionalFloat(node, "duration", w, 0f);
        switch (kind) {
            case AREA_TRUE_DAMAGE:
                if (ratio <= 0f) {
                    fail(w + "ratio", "AREA_TRUE_DAMAGE 必须有 ratio > 0（攻击快照倍率）");
                }
                if (radius < 1) {
                    fail(w + "radius", "AREA_TRUE_DAMAGE 必须有 radius ≥ 1（曼哈顿半径），实际=" + radius);
                }
                if (statusType == null) {
                    if (statusPower != 0f || statusDuration != 0f) {
                        fail(w, "附带状态必须三件套同给（statusType/statusPower/statusDuration）");
                    }
                } else if (statusPower <= 0f || statusDuration <= 0f) {
                    fail(w, "附带状态必须给 statusPower > 0 与 statusDuration > 0");
                }
                if (regenRatio != 0f) {
                    fail(w, "AREA_TRUE_DAMAGE 不携带 regenRatio");
                }
                break;
            case KILLER_TRUE_DAMAGE:
            case DEATH_VOLLEY:
                if (ratio <= 0f) {
                    fail(w + "ratio", kind.jsonName() + " 必须有 ratio > 0（攻击快照倍率）");
                }
                if (radius != 0 || statusType != null || regenRatio != 0f || duration != 0f) {
                    fail(w, kind.jsonName() + " 仅携带 ratio");
                }
                break;
            case ALLY_REGEN:
                if (regenRatio <= 0f) {
                    fail(w + "regenRatio", "ALLY_REGEN 必须有 regenRatio > 0（maxHp 比例/秒）");
                }
                if (duration <= 0f) {
                    fail(w + "duration", "ALLY_REGEN 必须有 duration > 0（秒），实际=" + duration);
                }
                if (ratio != 0f || radius != 0 || statusType != null) {
                    fail(w, "ALLY_REGEN 不携带 ratio/radius/statusType");
                }
                break;
            default:
                fail(w + "kind", "未知亡语 kind: " + kind);
        }
        return new DeathrattleSpec(kind, ratio, radius, statusType, statusPower, statusDuration,
                regenRatio, duration);
    }

    /** 不死参数（units.json "undying"，誓约未竟） */
    private static UndyingSpec parseUndying(JsonValue node, String w) {
        requireObject(node, w);
        checkUnknownKeys(node, w, "reviveDelaySeconds", "reviveHpRatio");
        float reviveDelaySeconds = requireFloat(node, "reviveDelaySeconds", w);
        float reviveHpRatio = requireFloat(node, "reviveHpRatio", w);
        if (reviveDelaySeconds <= 0f) {
            fail(w + "reviveDelaySeconds", "必须 > 0（秒），实际=" + reviveDelaySeconds);
        }
        if (reviveHpRatio <= 0f || reviveHpRatio > 1f) {
            fail(w + "reviveHpRatio", "必须在 (0, 1]（复活生命比例），实际=" + reviveHpRatio);
        }
        return new UndyingSpec(reviveDelaySeconds, reviveHpRatio);
    }

    private static BaseStats parseBaseStats(JsonValue bs, String w) {
        requireObject(bs, w);
        checkUnknownKeys(bs, w, "hp", "attack", "armor", "attackSpeed", "range", "moveSpeed",
                "lifesteal", "energyGainRate", "skillPower", "critChance", "critDamage");

        int hp = requireInt(bs, "hp", w);
        if (hp <= 0) {
            fail(w + "hp", "必须 > 0，实际=" + hp);
        }
        int attack = requireInt(bs, "attack", w);
        if (attack <= 0) {
            fail(w + "attack", "必须 > 0，实际=" + attack);
        }
        int armor = requireInt(bs, "armor", w);
        if (armor < 0) {
            fail(w + "armor", "必须 ≥ 0，实际=" + armor);
        }
        float attackSpeed = requireFloat(bs, "attackSpeed", w);
        if (attackSpeed <= 0) {
            fail(w + "attackSpeed", "必须 > 0，实际=" + attackSpeed);
        }
        int range = requireInt(bs, "range", w);
        if (range < 1) {
            fail(w + "range", "必须 ≥ 1，实际=" + range);
        }
        float moveSpeed = requireFloat(bs, "moveSpeed", w);
        if (moveSpeed <= 0) {
            fail(w + "moveSpeed", "必须 > 0，实际=" + moveSpeed);
        }
        // 百分比五键（百分点整数刻度，data_schema §三 V1.4 + v5 §五.5 暴击两键；缺省见 StatKey.baseStatsDefault()）
        int lifesteal = optionalInt(bs, "lifesteal", w, StatKey.LIFESTEAL.baseStatsDefault());
        checkNonNegative(bs, "lifesteal", w, lifesteal);
        int energyGainRate = optionalInt(bs, "energyGainRate", w, StatKey.ENERGY_GAIN_RATE.baseStatsDefault());
        checkNonNegative(bs, "energyGainRate", w, energyGainRate);
        int skillPower = optionalInt(bs, "skillPower", w, StatKey.SKILL_POWER.baseStatsDefault());
        checkNonNegative(bs, "skillPower", w, skillPower);
        int critChance = optionalInt(bs, "critChance", w, StatKey.CRIT_CHANCE.baseStatsDefault());
        checkNonNegative(bs, "critChance", w, critChance);
        int critDamage = optionalInt(bs, "critDamage", w, StatKey.CRIT_DAMAGE.baseStatsDefault());
        checkNonNegative(bs, "critDamage", w, critDamage);

        return new BaseStats(hp, attack, armor, attackSpeed, range, moveSpeed,
                lifesteal, energyGainRate, skillPower, critChance, critDamage);
    }

    // ==================================================================
    // skills.json
    // ==================================================================

    private static Map<String, SkillData> parseSkills(FileHandle file) {
        Map<String, SkillData> result = new LinkedHashMap<String, SkillData>();
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "desc", "shape", "delivery", "effects", "condition");

            String name = requireString(e, "name", w);
            String desc = requireString(e, "desc", w);
            SkillShape shape = requireVocab(e, "shape", SkillShape.class, w);
            Delivery delivery = optionalVocab(e, "delivery", Delivery.class, w, Delivery.MELEE_INSTANT);

            JsonValue effectsNode = require(e, "effects", w);
            if (!effectsNode.isArray() || effectsNode.size < 1) {
                fail(w + "effects", "必须为非空数组（1~" + GameBalance.MAX_EFFECTS_PER_SKILL + " 条）");
            }
            if (effectsNode.size > GameBalance.MAX_EFFECTS_PER_SKILL) {
                fail(w + "effects", "每技能效果至多 " + GameBalance.MAX_EFFECTS_PER_SKILL + " 条，实际=" + effectsNode.size);
            }
            List<SkillEffect> effects = new ArrayList<SkillEffect>(effectsNode.size);
            for (JsonValue fe = effectsNode.child; fe != null; fe = fe.next) {
                effects.add(parseSkillEffect(fe, w + "effects[" + effects.size() + "]/"));
            }

            JsonValue conditionNode = e.get("condition");
            CastCondition condition = conditionNode == null || conditionNode.isNull()
                    ? null : parseCastCondition(conditionNode, w + "condition/");

            result.put(id, new SkillData(id, name, desc, shape, delivery, effects, condition));
        }
        return result;
    }

    /** 施放条件（skills.json "condition"，可选）：本期一档 TARGET_HP_BELOW */
    private static CastCondition parseCastCondition(JsonValue node, String w) {
        requireObject(node, w);
        checkUnknownKeys(node, w, "type", "value");
        CastCondition.Type type = requireVocab(node, "type", CastCondition.Type.class, w);
        float value = requireFloat(node, "value", w);
        if (type == CastCondition.Type.TARGET_HP_BELOW && (value <= 0f || value > 1f)) {
            fail(w + "value", "TARGET_HP_BELOW 必须有 0 < value ≤ 1（生命比例），实际=" + value);
        }
        return new CastCondition(type, value);
    }

    private static SkillEffect parseSkillEffect(JsonValue fe, String w) {
        requireObject(fe, w);
        checkUnknownKeys(fe, w, "effect", "value", "status", "duration");
        SkillEffectType type = requireVocab(fe, "effect", SkillEffectType.class, w);

        Float value = optionalFloatObj(fe, "value", w);
        Float duration = optionalFloatObj(fe, "duration", w);
        StatusType status = optionalVocab(fe, "status", StatusType.class, w, null);

        switch (type) {
            case DAMAGE:
            case HEAL:
            case SHIELD:
                if (value == null || value <= 0) {
                    fail(w + "value", type.jsonName() + " 必须有 value > 0");
                }
                if (status != null || duration != null) {
                    fail(w, type.jsonName() + " 不允许 status/duration（仅 APPLY_STATUS 使用）");
                }
                break;
            case APPLY_STATUS:
                if (status == null) {
                    fail(w + "status", "APPLY_STATUS 必须有合法 status");
                }
                if (duration == null || duration <= 0) {
                    fail(w + "duration", "APPLY_STATUS 必须有 duration > 0（秒），实际=" + duration);
                }
                if (value != null && value < 0) {
                    fail(w + "value", "不允许负值，实际=" + value);
                }
                break;
            case GAIN_ENERGY:
                if (value == null || value <= 0) {
                    fail(w + "value", "GAIN_ENERGY 必须有 value > 0");
                }
                if (status != null || duration != null) {
                    fail(w, "GAIN_ENERGY 不允许 status/duration");
                }
                break;
            case KNOCKBACK:
                if (value == null || value < 1f || value != Math.floor(value)) {
                    fail(w + "value", "KNOCKBACK 必须有整数 value ≥ 1（格）");
                }
                if (status != null || duration != null) {
                    fail(w, "KNOCKBACK 不允许 status/duration");
                }
                break;
            case BANISH:
                if (value != null || status != null || duration != null) {
                    fail(w, "BANISH 不携带 value/status/duration（条件在技能级 condition）");
                }
                break;
            default:
                fail(w + "effect", "未知效果类型: " + type);
        }
        return new SkillEffect(type, value, status, duration);
    }

    // ==================================================================
    // synergies.json
    // ==================================================================

    private static Map<String, SynergyData> parseSynergies(FileHandle file) {
        Map<String, SynergyData> result = new LinkedHashMap<String, SynergyData>();
        Set<String> ids = new HashSet<String>();
        Set<String> sourceKeys = new HashSet<String>(); // "RACE:兽人" 防同 key 双登记
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "desc", "source", "key", "thresholds");

            String name = requireString(e, "name", w);
            String desc = requireString(e, "desc", w);
            SynergySource source = requireVocab(e, "source", SynergySource.class, w);
            String key = requireString(e, "key", w);
            if (!sourceKeys.add(source.jsonName() + ":" + key)) {
                fail(w + "key", "同 source 下 key 重复登记: " + key);
            }

            JsonValue thresholdsNode = require(e, "thresholds", w);
            if (!thresholdsNode.isArray() || thresholdsNode.size < 1) {
                fail(w + "thresholds", "必须为非空数组");
            }
            List<SynergyData.Threshold> thresholds = new ArrayList<SynergyData.Threshold>(thresholdsNode.size);
            int prevCount = 0;
            int index = 0;
            for (JsonValue t = thresholdsNode.child; t != null; t = t.next, index++) {
                String tw = w + "thresholds[" + index + "]/";
                requireObject(t, tw);
                checkUnknownKeys(t, tw, "count", "effects");
                int count = requireInt(t, "count", tw);
                if (count <= prevCount) {
                    fail(tw + "count", "门槛必须为正整数且严格升序唯一，实际=" + count + "（前值=" + prevCount + "）");
                }
                prevCount = count;

                JsonValue effectsNode = require(t, "effects", tw);
                if (!effectsNode.isArray() || effectsNode.size < 1) {
                    fail(tw + "effects", "必须为非空数组");
                }
                List<EffectData> effects = new ArrayList<EffectData>(effectsNode.size);
                for (JsonValue ee = effectsNode.child; ee != null; ee = ee.next) {
                    effects.add(parseSynergyEffect(ee, tw + "effects[" + effects.size() + "]/"));
                }
                thresholds.add(new SynergyData.Threshold(count, effects));
            }

            result.put(id, new SynergyData(id, name, desc, source, key, thresholds));
        }
        return result;
    }

    private static EffectData parseSynergyEffect(JsonValue ee, String w) {
        requireObject(ee, w);
        checkUnknownKeys(ee, w, "stat", "effect", "mechanic", "op", "value", "value2",
                "status", "duration", "target", "trait");

        JsonValue statNode = ee.get("stat");
        JsonValue effectNode = ee.get("effect");
        JsonValue mechanicNode = ee.get("mechanic");
        boolean hasStat = statNode != null && !statNode.isNull();
        boolean hasEffect = effectNode != null && !effectNode.isNull();
        boolean hasMechanic = mechanicNode != null && !mechanicNode.isNull();
        if ((hasStat ? 1 : 0) + (hasEffect ? 1 : 0) + (hasMechanic ? 1 : 0) != 1) {
            fail(w, "stat / effect / mechanic 必须三选一（属性修正走 stat，开局 SHIELD 走 effect，触发式机制走 mechanic）");
        }

        Float value = optionalFloatObj(ee, "value", w);
        Float value2 = optionalFloatObj(ee, "value2", w);
        StatusType status = optionalVocab(ee, "status", StatusType.class, w, null);
        Float duration = optionalFloatObj(ee, "duration", w);
        EffectTarget target = optionalVocab(ee, "target", EffectTarget.class, w, EffectTarget.ALLIES);
        String trait = optionalString(ee, "trait", w);
        if (target == EffectTarget.TRAITS && (trait == null || trait.trim().isEmpty())) {
            fail(w + "trait", "target=TRAITS 时 trait 必填非空（race 或 class 名）");
        }

        if (hasStat) {
            if (value == null) {
                fail(w + "value", "stat 通道必须有 value");
            }
            StatKey stat = requireVocab(ee, "stat", StatKey.class, w);
            EffectOp op = requireVocab(ee, "op", EffectOp.class, w);
            return new EffectData(stat, null, null, op, value, null, null, null, target, null);
        }
        if (hasEffect) {
            if (value == null) {
                fail(w + "value", "effect 通道必须有 value");
            }
            StatusType effect = requireVocab(ee, "effect", StatusType.class, w);
            return new EffectData(null, effect, null, null, value, null, null, null, target, null);
        }
        SynergyMechanic mechanic = requireVocab(ee, "mechanic", SynergyMechanic.class, w);
        validateMechanicParams(mechanic, value, value2, status, duration, w);
        return new EffectData(null, null, mechanic, null, value, value2, status, duration, target, trait);
    }

    /** 机制词逐词必填校验（K1 参数表；无参词禁止携带载荷） */
    private static void validateMechanicParams(SynergyMechanic mechanic, Float value, Float value2,
                                               StatusType status, Float duration, String w) {
        switch (mechanic) {
            case BASIC_ATTACK_STATUS:
                if (status == null) {
                    fail(w + "status", "BASIC_ATTACK_STATUS 必须有 status（SLOW/BURN）");
                }
                if (value == null || value <= 0f) {
                    fail(w + "value", "BASIC_ATTACK_STATUS 必须有 value > 0");
                }
                if (duration == null || duration <= 0f) {
                    fail(w + "duration", "BASIC_ATTACK_STATUS 必须有 duration > 0（秒），实际=" + duration);
                }
                break;
            case SCAR:
                if (value == null || value <= 0f) {
                    fail(w + "value", "SCAR 必须有 value > 0（每层承受普攻次数）");
                }
                if (value2 == null || value2 <= 0f) {
                    fail(w + "value2", "SCAR 必须有 value2 > 0（层数上限）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            case DEATH_TAX:
                if (value == null || value <= 0f) {
                    fail(w + "value", "DEATH_TAX 必须有 value > 0（攻 ADD）");
                }
                if (value2 == null || value2 <= 0f) {
                    fail(w + "value2", "DEATH_TAX 必须有 value2 > 0（生命上限 ADD）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            case DOUBLE_DEATHRATTLE:
            case KNOCKBACK_IMMUNE:
            case SHIELD_WALL:
            case KILL_ENERGY_REFUND:
                if (value != null || value2 != null || status != null || duration != null) {
                    fail(w, mechanic.jsonName() + " 为无参机制词，不携带 value/value2/status/duration");
                }
                break;
            case DEATH_AUDIT:
                if (value == null || value <= 0f || value >= 100f) {
                    fail(w + "value", "DEATH_AUDIT 必须有 0 < value < 100（生命比例阈值百分点）");
                }
                if (value2 != null || status != null || duration != null) {
                    fail(w, "DEATH_AUDIT 不携带 value2/status/duration");
                }
                break;
            case ENEMY_DEATH_SURGE:
                if (value == null || value <= 0f) {
                    fail(w + "value", "ENEMY_DEATH_SURGE 必须有 value > 0（攻速增益百分点）");
                }
                if (duration == null || duration <= 0f) {
                    fail(w + "duration", "ENEMY_DEATH_SURGE 必须有 duration > 0（秒）");
                }
                if (value2 != null || status != null) {
                    fail(w, "ENEMY_DEATH_SURGE 不携带 value2/status");
                }
                break;
            case REFLECT:
            case DAMAGE_REDUCTION:
            case SPELL_PEN:
                if (value == null || value <= 0f || value >= 100f) {
                    fail(w + "value", mechanic.jsonName() + " 必须有 0 < value < 100（百分比）");
                }
                if (value2 != null || status != null || duration != null) {
                    fail(w, mechanic.jsonName() + " 不携带 value2/status/duration");
                }
                break;
            case WARCRY_TAUNT:
                if (value == null || value <= 0f) {
                    fail(w + "value", "WARCRY_TAUNT 必须有 value > 0（开战延迟秒）");
                }
                if (value2 == null || value2 <= 0f) {
                    fail(w + "value2", "WARCRY_TAUNT 必须有 value2 > 0（嘲讽持续秒）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            case OPENING_ENERGY:
            case AFTER_CAST_ENERGY:
                if (value == null || value <= 0f) {
                    fail(w + "value", mechanic.jsonName() + " 必须有 value > 0（能量值）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            case EXECUTE:
                if (value == null || value <= 0f) {
                    fail(w + "value", "EXECUTE 必须有 value > 0（增伤百分比）");
                }
                if (value2 == null || value2 <= 0f || value2 >= 100f) {
                    fail(w + "value2", "EXECUTE 必须有 0 < value2 < 100（生命比例阈值百分点）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            case CRIT_RHYTHM:
                if (value == null || value < 1f || value != Math.floor(value)) {
                    fail(w + "value", "CRIT_RHYTHM 必须有整数 value ≥ 1（第 N 刀必暴）");
                }
                if (value2 == null || value2 <= 0f) {
                    fail(w + "value2", "CRIT_RHYTHM 必须有 value2 > 0（必暴刀暴伤额外百分点）");
                }
                rejectPayload(mechanic, status, duration, w);
                break;
            default:
                fail(w + "mechanic", "未知机制词: " + mechanic);
        }
    }

    /** 带数值词禁止 status/duration 载荷（value/value2 之外的字段） */
    private static void rejectPayload(SynergyMechanic mechanic, StatusType status, Float duration, String w) {
        if (status != null || duration != null) {
            fail(w, mechanic.jsonName() + " 不携带 status/duration");
        }
    }

    // ==================================================================
    // scenes.json（data_schema §七 结构锁定版；S5/S6 解析期校验）
    // ==================================================================

    private static Map<String, SceneData> parseScenes(FileHandle file) {
        Map<String, SceneData> result = new LinkedHashMap<String, SceneData>();
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "unlockAfter", "enemyPool", "bosses", "shopUnlocks");

            String name = requireString(e, "name", w);
            String unlockAfter = optionalString(e, "unlockAfter", w);
            List<SceneData.EnemyPoolEntry> enemyPool = parseEnemyPool(require(e, "enemyPool", w), w);
            Map<Integer, String> bosses = parseBosses(require(e, "bosses", w), w);
            List<String> shopUnlocks = parseShopUnlocks(e.get("shopUnlocks"), w);

            result.put(id, new SceneData(id, name, unlockAfter, enemyPool, bosses, shopUnlocks));
        }
        return result;
    }

    private static List<SceneData.EnemyPoolEntry> parseEnemyPool(JsonValue node, String w) {
        if (!node.isArray() || node.size < 1) {
            fail(w + "enemyPool", "必须为非空数组");
        }
        List<SceneData.EnemyPoolEntry> pool = new ArrayList<SceneData.EnemyPoolEntry>(node.size);
        boolean anyFromRoundOne = false; // S5：至少一条 minRound ≤ 1，否则第 1 轮可用池为空
        int index = 0;
        for (JsonValue p = node.child; p != null; p = p.next, index++) {
            String pw = w + "enemyPool[" + index + "]/";
            requireObject(p, pw);
            checkUnknownKeys(p, pw, "unitId", "weight", "minRound");
            String unitId = requireString(p, "unitId", pw);
            int weight = requireInt(p, "weight", pw);
            if (weight < 1) { // S6
                fail(pw + "weight", "必须为正整数（≥ 1），实际=" + weight);
            }
            int minRound = requireInt(p, "minRound", pw);
            if (minRound < 1 || minRound > GameBalance.TOTAL_ROUNDS) { // S6
                fail(pw + "minRound", "必须在 1~" + GameBalance.TOTAL_ROUNDS + "，实际=" + minRound);
            }
            if (minRound <= 1) {
                anyFromRoundOne = true;
            }
            pool.add(new SceneData.EnemyPoolEntry(unitId, weight, minRound));
        }
        if (!anyFromRoundOne) {
            fail(w + "enemyPool", "至少一条 minRound ≤ 1（否则第 1 轮可用池为空，无兵可抽）");
        }
        return pool;
    }

    private static Map<Integer, String> parseBosses(JsonValue node, String w) {
        if (!node.isObject()) {
            fail(w + "bosses", "必须为对象（轮次 → Boss 模板 id）");
        }
        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        for (JsonValue b = node.child; b != null; b = b.next) {
            String key = b.name();
            int round;
            try {
                round = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                fail(w + "bosses", "键必须为整数轮次，实际=\"" + key + "\"");
                return null; // 不可达
            }
            boolean isBossRound = false;
            for (int bossRound : GameBalance.BOSS_ROUNDS) {
                if (bossRound == round) {
                    isBossRound = true;
                    break;
                }
            }
            if (!isBossRound) {
                fail(w + "bosses", "键必须 ∈ {7, 15, 25}，实际=" + round);
            }
            bosses.put(round, requireString(node, key, w + "bosses/"));
        }
        for (int bossRound : GameBalance.BOSS_ROUNDS) { // 实现层口径 #5：三键必须齐全
            if (!bosses.containsKey(bossRound)) {
                fail(w + "bosses", "三键 {7, 15, 25} 必须齐全，缺 \"" + bossRound + "\"");
            }
        }
        return bosses;
    }

    /** shopUnlocks（可选数组，缺省/null = 空表）：场景解锁后进入商店池的单位 id（裁决 D8） */
    private static List<String> parseShopUnlocks(JsonValue node, String w) {
        List<String> result = new ArrayList<String>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isArray()) {
            fail(w + "shopUnlocks", "必须为数组（单位 id）");
        }
        Set<String> seen = new HashSet<String>();
        for (JsonValue u = node.child; u != null; u = u.next) {
            if (!u.isString() || u.asString().trim().isEmpty()) {
                fail(w + "shopUnlocks", "元素必须为非空字符串（单位 id）");
            }
            String unitId = u.asString();
            if (!seen.add(unitId)) {
                fail(w + "shopUnlocks", "场景内重复单位: " + unitId);
            }
            result.add(unitId);
        }
        return result;
    }

    // ==================================================================
    // equipments.json（data_schema §八 结构锁定版；Phase 5）
    // ==================================================================

    /** equipmentsFile 可 null（兼容重载）：null → 空表；传入文件不存在沿 parseArray 即死（loadFromDirectory 生产路径缺文件启动死） */
    private static Map<String, EquipmentData> parseEquipments(FileHandle file) {
        Map<String, EquipmentData> result = new LinkedHashMap<String, EquipmentData>();
        if (file == null) {
            return result;
        }
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "slot", "rarity", "effects", "passiveStatus");

            String name = requireString(e, "name", w);
            EquipmentSlot slot = requireVocab(e, "slot", EquipmentSlot.class, w);
            EquipmentRarity rarity = requireVocab(e, "rarity", EquipmentRarity.class, w);

            JsonValue effectsNode = require(e, "effects", w);
            if (!effectsNode.isArray() || effectsNode.size < 1) {
                fail(w + "effects", "必须为非空数组（1~" + GameBalance.MAX_EFFECTS_PER_SKILL + " 条）");
            }
            if (effectsNode.size > GameBalance.MAX_EFFECTS_PER_SKILL) {
                fail(w + "effects", "每装备效果至多 " + GameBalance.MAX_EFFECTS_PER_SKILL + " 条，实际=" + effectsNode.size);
            }
            List<EquipmentEffect> effects = new ArrayList<EquipmentEffect>(effectsNode.size);
            for (JsonValue fe = effectsNode.child; fe != null; fe = fe.next) {
                effects.add(parseEquipmentEffect(fe, w + "effects[" + effects.size() + "]/"));
            }
            EquipmentPassive passive = null;
            JsonValue passiveNode = e.get("passiveStatus");
            if (passiveNode != null && !passiveNode.isNull()) {
                passive = parseEquipmentPassive(passiveNode, w + "passiveStatus/");
            }
            result.put(id, new EquipmentData(id, name, slot, rarity, effects, passive));
        }
        return result;
    }

    private static EquipmentEffect parseEquipmentEffect(JsonValue fe, String w) {
        requireObject(fe, w);
        checkUnknownKeys(fe, w, "stat", "op", "value");
        StatKey stat = requireVocab(fe, "stat", StatKey.class, w);
        EffectOp op = requireVocab(fe, "op", EffectOp.class, w);
        float value = requireFloat(fe, "value", w);
        return new EquipmentEffect(stat, op, value);
    }

    private static EquipmentPassive parseEquipmentPassive(JsonValue node, String w) {
        requireObject(node, w);
        checkUnknownKeys(node, w, "type", "power", "tick");
        StatusType type = requireVocab(node, "type", StatusType.class, w);
        if (type != StatusType.REGEN) {
            fail(w + "type", "passiveStatus 本期仅支持 REGEN，遇到: " + type.jsonName());
        }
        float power = requireFloat(node, "power", w);
        if (power <= 0) {
            fail(w + "power", "必须 > 0，实际=" + power);
        }
        float tick = requireFloat(node, "tick", w);
        if (tick <= 0) {
            fail(w + "tick", "必须 > 0（秒），实际=" + tick);
        }
        return new EquipmentPassive(type, power, tick);
    }

    /** 软告警：equipments 非空但某稀有度池为空（宝箱 roll 将降级——内容缺失预警，不阻断） */
    private static void warnEmptyRarityPools(Map<String, EquipmentData> equipments, List<String> warnings) {
        if (equipments.isEmpty()) {
            return;
        }
        for (EquipmentRarity rarity : EquipmentRarity.values()) {
            boolean any = false;
            for (EquipmentData equipment : equipments.values()) {
                if (equipment.getRarity() == rarity) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                warnings.add("装备稀有度池为空（宝箱该档将降级/退化金币）: " + rarity.jsonName());
            }
        }
    }

    // ==================================================================
    // heroes.json（Phase 6；裁决 D17 词表制）
    // ==================================================================

    /** heroesFile 可 null（兼容重载）：null → 空表；生产路径缺文件沿 parseArray 即死 */
    private static Map<String, HeroData> parseHeroes(FileHandle file) {
        Map<String, HeroData> result = new LinkedHashMap<String, HeroData>();
        if (file == null) {
            return result;
        }
        Set<String> ids = new HashSet<String>();
        for (JsonValue e = parseArray(file).child; e != null; e = e.next) {
            requireObject(e, file.name());
            String id = requireString(e, "id", file.name() + "#?");
            String w = file.name() + "#" + id + "/";
            if (!ids.add(id)) {
                fail(w, "id 全文件唯一，重复声明");
            }
            checkUnknownKeys(e, w, "id", "name", "desc", "passive", "legendaryUnitId");

            String name = requireString(e, "name", w);
            String desc = requireString(e, "desc", w);
            JsonValue passiveNode = require(e, "passive", w);
            String pw = w + "passive/";
            requireObject(passiveNode, pw);
            checkUnknownKeys(passiveNode, pw, "type", "value", "synergyIds");
            HeroPassiveType type = requireVocab(passiveNode, "type", HeroPassiveType.class, pw);
            float value = requireFloat(passiveNode, "value", pw);
            if (value <= 0) {
                fail(pw + "value", "必须 > 0，实际=" + value);
            }
            List<String> synergyIds = new ArrayList<String>();
            JsonValue synergyNode = passiveNode.get("synergyIds");
            if (synergyNode != null && !synergyNode.isNull()) {
                if (!synergyNode.isArray()) {
                    fail(pw + "synergyIds", "必须为数组");
                }
                for (JsonValue s = synergyNode.child; s != null; s = s.next) {
                    if (!s.isString() || s.asString().trim().isEmpty()) {
                        fail(pw + "synergyIds", "元素必须为非空字符串（羁绊 id）");
                    }
                    synergyIds.add(s.asString());
                }
            }
            if (type == HeroPassiveType.SYNERGY_AMP && synergyIds.isEmpty()) {
                fail(pw + "synergyIds", "SYNERGY_AMP 必须指定至少 1 个羁绊 id");
            }
            if (type != HeroPassiveType.SYNERGY_AMP && !synergyIds.isEmpty()) {
                fail(pw + "synergyIds", "仅 SYNERGY_AMP 允许 synergyIds");
            }
            String legendaryUnitId = optionalString(e, "legendaryUnitId", w);
            result.put(id, new HeroData(id, name, desc, type, value, synergyIds, legendaryUnitId));
        }
        return result;
    }

    // ==================================================================
    // 交叉校验（data_schema §九.4 / §九.6 + scenes 组 S1~S4）
    // ==================================================================

    private static void crossValidate(Map<String, UnitData> units, Map<String, SkillData> skills,
                                      Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                                      Map<String, HeroData> heroes, List<String> warnings) {
        // 1. units 的 skillId 必 ∈ skills（悬空即死）
        Set<String> referencedSkills = new HashSet<String>();
        for (UnitData unit : units.values()) {
            if (!skills.containsKey(unit.getSkillId())) {
                fail("units.json#" + unit.getId() + "/skillId", "引用了不存在的技能: " + unit.getSkillId());
            }
            referencedSkills.add(unit.getSkillId());
        }
        // 2. 孤儿技能软告警
        for (String skillId : skills.keySet()) {
            if (!referencedSkills.contains(skillId)) {
                warnings.add("孤儿技能（无单位引用）: " + skillId);
            }
        }
        // 3. 风味种族/职业聚合软告警（与孤儿羁绊对称，data_schema §九.4 V1.4）
        Set<String> raceKeys = new HashSet<String>();
        Set<String> classKeys = new HashSet<String>();
        for (SynergyData synergy : synergies.values()) {
            (synergy.getSource() == SynergySource.RACE ? raceKeys : classKeys).add(synergy.getKey());
        }
        Set<String> matchedRaceKeys = new HashSet<String>();
        Set<String> matchedClassKeys = new HashSet<String>();
        LinkedHashSet<String> flavorValues = new LinkedHashSet<String>();
        for (UnitData unit : units.values()) {
            if (raceKeys.contains(unit.getRace())) {
                matchedRaceKeys.add(unit.getRace());
            } else {
                flavorValues.add(unit.getRace());
            }
            if (classKeys.contains(unit.getUnitClass())) {
                matchedClassKeys.add(unit.getUnitClass());
            } else {
                flavorValues.add(unit.getUnitClass());
            }
        }
        if (!flavorValues.isEmpty()) {
            warnings.add("风味种族/职业（未登记羁绊，不计计数）: " + join(flavorValues, "、"));
        }
        // 4. 孤儿羁绊软告警
        for (SynergyData synergy : synergies.values()) {
            Set<String> matched = synergy.getSource() == SynergySource.RACE ? matchedRaceKeys : matchedClassKeys;
            if (!matched.contains(synergy.getKey())) {
                warnings.add("孤儿羁绊（无单位匹配 key）: " + synergy.getId() + "（key=" + synergy.getKey() + "）");
            }
        }
        // 5. ALL_ENEMIES 形状被非 Boss 单位引用 → 软告警（§九.6）
        for (UnitData unit : units.values()) {
            if (unit.isBoss()) {
                continue;
            }
            SkillData skill = skills.get(unit.getSkillId());
            if (skill != null && skill.getShape() == SkillShape.ALL_ENEMIES) {
                warnings.add("技能形状 ALL_ENEMIES 被非 Boss 单位引用: " + unit.getId() + " → " + skill.getId());
            }
        }
        // 6. S1/S2：enemyPool unitId 必 ∈ units（悬空即死，§九.4）且 Boss 模板不得占权重位（§九.6）
        for (SceneData scene : scenes.values()) {
            String sw = "scenes.json#" + scene.getId() + "/";
            for (SceneData.EnemyPoolEntry entry : scene.getEnemyPool()) {
                UnitData unit = units.get(entry.getUnitId());
                if (unit == null) {
                    fail(sw + "enemyPool", "引用了不存在的单位: " + entry.getUnitId());
                }
                if (unit != null && unit.isBoss()) {
                    fail(sw + "enemyPool", "Boss 模板不得出现在 enemyPool 权重位: " + entry.getUnitId());
                }
            }
            // 7. S3：bosses 值必 ∈ units（悬空即死，§九.4）且被引用模板必须 isBoss（§九.6 对称防御）
            for (Map.Entry<Integer, String> boss : scene.getBosses().entrySet()) {
                UnitData unit = units.get(boss.getValue());
                if (unit == null) {
                    fail(sw + "bosses/" + boss.getKey(), "Boss 位引用了不存在的单位: " + boss.getValue());
                }
                if (unit != null && !unit.isBoss()) {
                    fail(sw + "bosses/" + boss.getKey(), "Boss 位引用了非 Boss 模板: " + boss.getValue());
                }
            }
            // 8. S4a/S4b：unlockAfter 必 ∈ 场景 id 集、禁自指（实现层口径 #8）
            String unlockAfter = scene.getUnlockAfter();
            if (unlockAfter != null) {
                if (!scenes.containsKey(unlockAfter)) {
                    fail(sw + "unlockAfter", "引用了不存在的场景: " + unlockAfter);
                }
                if (unlockAfter.equals(scene.getId())) {
                    fail(sw + "unlockAfter", "禁自指");
                }
            }
        }
        // 9. S4c：多场景前置链成环报错（引用存在性与自指已在上一轮全量校验）
        for (SceneData scene : scenes.values()) {
            Set<String> visited = new HashSet<String>();
            String cursor = scene.getId();
            while (cursor != null && scenes.containsKey(cursor)) {
                if (!visited.add(cursor)) {
                    fail("scenes.json#" + scene.getId() + "/unlockAfter", "前置链成环（回边到: " + cursor + "）");
                }
                cursor = scenes.get(cursor).getUnlockAfter();
            }
        }
        // 10. heroes 交叉校验（Phase 6）：synergyIds ∈ synergies；传奇 ∈ units 且非 Boss、cost=3；传奇不得共用
        Set<String> legendaryUnits = new HashSet<String>();
        for (HeroData hero : heroes.values()) {
            String hw = "heroes.json#" + hero.getId() + "/";
            for (String synergyId : hero.getPassiveSynergyIds()) {
                if (!synergies.containsKey(synergyId)) {
                    fail(hw + "passive/synergyIds", "引用了不存在的羁绊: " + synergyId);
                }
            }
            String legendary = hero.getLegendaryUnitId();
            if (legendary != null) {
                UnitData unit = units.get(legendary);
                if (unit == null) {
                    fail(hw + "legendaryUnitId", "引用了不存在的单位: " + legendary);
                }
                if (unit != null && (unit.isBoss() || unit.getCost() != 3)) {
                    fail(hw + "legendaryUnitId", "传奇棋子必须为非 Boss 且 cost=3: " + legendary);
                }
                if (!legendaryUnits.add(legendary)) {
                    fail(hw + "legendaryUnitId", "传奇棋子被多名英雄共用: " + legendary);
                }
            }
        }
        // 11. shopUnlocks 引用校验（裁决 D8）：∈ units、非 Boss、不跨场景重复、不得为英雄传奇
        Set<String> shopUnlockAll = new HashSet<String>();
        for (SceneData scene : scenes.values()) {
            for (String unitId : scene.getShopUnlocks()) {
                String sw = "scenes.json#" + scene.getId() + "/shopUnlocks";
                UnitData unit = units.get(unitId);
                if (unit == null) {
                    fail(sw, "引用了不存在的单位: " + unitId);
                }
                if (unit != null && unit.isBoss()) {
                    fail(sw, "Boss 模板不得进入商店池: " + unitId);
                }
                if (!shopUnlockAll.add(unitId)) {
                    fail(sw, "单位被多个场景 shopUnlocks 重复登记: " + unitId);
                }
                if (legendaryUnits.contains(unitId)) {
                    fail(sw, "英雄专属传奇不得登记进场景商店池（两机制互斥）: " + unitId);
                }
            }
        }
    }
}
