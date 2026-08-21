package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.data.BaseStats;
import com.voidvvv.kz_auto_chess_n.data.Delivery;
import com.voidvvv.kz_auto_chess_n.data.EffectData;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.EffectTarget;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.data.SkillEffect;
import com.voidvvv.kz_auto_chess_n.data.SkillEffectType;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.SkillShape;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.SynergyData;
import com.voidvvv.kz_auto_chess_n.data.SynergySource;
import com.voidvvv.kz_auto_chess_n.data.TargetPriority;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.data.Vocab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final JsonReader READER = new JsonReader();

    private static final float DEFAULT_UPGRADE_MULTIPLIER = 1.8f;

    private JsonLoader() {
    }

    /** 从目录按标准文件名加载：units.json / skills.json / synergies.json / scenes.json */
    public static GameData loadFromDirectory(FileHandle dataDir) {
        return load(dataDir.child("units.json"), dataDir.child("skills.json"),
                dataDir.child("synergies.json"), dataDir.child("scenes.json"));
    }

    public static GameData load(FileHandle unitsFile, FileHandle skillsFile,
                                FileHandle synergiesFile, FileHandle scenesFile) {
        Map<String, UnitData> units = parseUnits(unitsFile);
        Map<String, SkillData> skills = parseSkills(skillsFile);
        Map<String, SynergyData> synergies = parseSynergies(synergiesFile);
        Map<String, SceneData> scenes = parseScenes(scenesFile);
        List<String> warnings = new ArrayList<String>();
        crossValidate(units, skills, synergies, scenes, warnings);
        return new GameData(units, skills, synergies, scenes, warnings);
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
                    "upgradeMultiplier", "defaultPriority", "specialPriority", "skillId", "boss");

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

            result.put(id, new UnitData(id, name, race, unitClass, cost, baseStats,
                    upgradeMultiplier, defaultPriority, specialPriority, skillId, boss));
        }
        return result;
    }

    private static BaseStats parseBaseStats(JsonValue bs, String w) {
        requireObject(bs, w);
        checkUnknownKeys(bs, w, "hp", "attack", "armor", "attackSpeed", "range", "moveSpeed",
                "lifesteal", "energyGainRate", "skillPower");

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
        // 百分比三键（百分点整数刻度，data_schema §三 V1.4；缺省见 StatKey.baseStatsDefault()）
        int lifesteal = optionalInt(bs, "lifesteal", w, StatKey.LIFESTEAL.baseStatsDefault());
        checkNonNegative(bs, "lifesteal", w, lifesteal);
        int energyGainRate = optionalInt(bs, "energyGainRate", w, StatKey.ENERGY_GAIN_RATE.baseStatsDefault());
        checkNonNegative(bs, "energyGainRate", w, energyGainRate);
        int skillPower = optionalInt(bs, "skillPower", w, StatKey.SKILL_POWER.baseStatsDefault());
        checkNonNegative(bs, "skillPower", w, skillPower);

        return new BaseStats(hp, attack, armor, attackSpeed, range, moveSpeed,
                lifesteal, energyGainRate, skillPower);
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
            checkUnknownKeys(e, w, "id", "name", "desc", "shape", "delivery", "effects");

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

            result.put(id, new SkillData(id, name, desc, shape, delivery, effects));
        }
        return result;
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
            checkUnknownKeys(e, w, "id", "name", "source", "key", "thresholds");

            String name = requireString(e, "name", w);
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

            result.put(id, new SynergyData(id, name, source, key, thresholds));
        }
        return result;
    }

    private static EffectData parseSynergyEffect(JsonValue ee, String w) {
        requireObject(ee, w);
        checkUnknownKeys(ee, w, "stat", "effect", "op", "value", "target");

        JsonValue statNode = ee.get("stat");
        JsonValue effectNode = ee.get("effect");
        boolean hasStat = statNode != null && !statNode.isNull();
        boolean hasEffect = effectNode != null && !effectNode.isNull();
        if (hasStat == hasEffect) {
            fail(w, "stat 与 effect 必须二选一（属性修正走 stat，特殊效果走 effect）");
        }

        float value = requireFloat(ee, "value", w);
        EffectTarget target = optionalVocab(ee, "target", EffectTarget.class, w, EffectTarget.ALLIES);
        if (hasStat) {
            StatKey stat = requireVocab(ee, "stat", StatKey.class, w);
            EffectOp op = requireVocab(ee, "op", EffectOp.class, w);
            return new EffectData(stat, null, op, value, target);
        }
        StatusType effect = requireVocab(ee, "effect", StatusType.class, w);
        return new EffectData(null, effect, null, value, target);
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
            checkUnknownKeys(e, w, "id", "name", "unlockAfter", "enemyPool", "bosses");

            String name = requireString(e, "name", w);
            String unlockAfter = optionalString(e, "unlockAfter", w);
            List<SceneData.EnemyPoolEntry> enemyPool = parseEnemyPool(require(e, "enemyPool", w), w);
            Map<Integer, String> bosses = parseBosses(require(e, "bosses", w), w);

            result.put(id, new SceneData(id, name, unlockAfter, enemyPool, bosses));
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

    // ==================================================================
    // 交叉校验（data_schema §九.4 / §九.6 + scenes 组 S1~S4）
    // ==================================================================

    private static void crossValidate(Map<String, UnitData> units, Map<String, SkillData> skills,
                                      Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                                      List<String> warnings) {
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
    }

    // ==================================================================
    // JsonValue 读取与校验工具（报错统一含 文件#条目/字段路径）
    // ==================================================================

    private static JsonValue parseArray(FileHandle file) {
        if (!file.exists()) {
            fail(file.name(), "文件不存在");
        }
        String text = file.readString("UTF-8");
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1); // 容错剥离 BOM（约定不用 BOM，data_schema §二.1）
        }
        JsonValue root = READER.parse(text);
        if (root == null || !root.isArray()) {
            fail(file.name(), "根节点必须是数组");
        }
        return root;
    }

    private static void requireObject(JsonValue v, String where) {
        if (!v.isObject()) {
            fail(where, "条目必须是对象");
        }
    }

    private static JsonValue require(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            fail(where + field, "缺必填字段");
        }
        return child;
    }

    private static String requireString(JsonValue obj, String field, String where) {
        JsonValue child = require(obj, field, where);
        if (!child.isString() || child.asString().trim().isEmpty()) {
            fail(where + field, "必须为非空字符串");
        }
        return child.asString();
    }

    /** 可选字符串：缺省或显式 null 放行返回 null；出现则必须为非空字符串 */
    private static String optionalString(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isString() || child.asString().trim().isEmpty()) {
            fail(where + field, "必须为非空字符串");
        }
        return child.asString();
    }

    private static int requireInt(JsonValue obj, String field, String where) {
        JsonValue child = require(obj, field, where);
        if (!child.isNumber()) {
            fail(where + field, "必须为数字，实际=" + child);
        }
        double d = child.asDouble();
        if (Math.rint(d) != d) {
            fail(where + field, "必须为整数，实际=" + d);
        }
        return (int) d;
    }

    private static float requireFloat(JsonValue obj, String field, String where) {
        JsonValue child = require(obj, field, where);
        if (!child.isNumber()) {
            fail(where + field, "必须为数字，实际=" + child);
        }
        return child.asFloat();
    }

    private static boolean optionalBool(JsonValue obj, String field, String where, boolean def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        if (!child.isBoolean()) {
            fail(where + field, "必须为布尔值，实际=" + child);
        }
        return child.asBoolean();
    }

    private static int optionalInt(JsonValue obj, String field, String where, int def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        return requireInt(obj, field, where);
    }

    private static float optionalFloat(JsonValue obj, String field, String where, float def) {
        Float v = optionalFloatObj(obj, field, where);
        return v == null ? def : v;
    }

    private static Float optionalFloatObj(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isNumber()) {
            fail(where + field, "必须为数字，实际=" + child);
        }
        return child.asFloat();
    }

    private static void checkNonNegative(JsonValue obj, String field, String w, int value) {
        if (value < 0) {
            fail(w + field, "不允许负值，实际=" + value);
        }
    }

    /** 词表解析：按 {@link Vocab#jsonName()} 匹配，非法值报错并列出全部合法值 */
    private static <E extends Enum<E> & Vocab> E requireVocab(JsonValue obj, String field, Class<E> type, String where) {
        String raw = requireString(obj, field, where);
        for (E e : type.getEnumConstants()) {
            if (e.jsonName().equals(raw)) {
                return e;
            }
        }
        fail(where + field, "非法枚举值 \"" + raw + "\"，合法值: " + vocabNames(type));
        return null; // 不可达
    }

    private static <E extends Enum<E> & Vocab> E optionalVocab(JsonValue obj, String field, Class<E> type, String where, E def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        return requireVocab(obj, field, type, where);
    }

    private static void checkUnknownKeys(JsonValue obj, String where, String... allowed) {
        for (JsonValue child = obj.child; child != null; child = child.next) {
            boolean known = false;
            for (String a : allowed) {
                if (a.equals(child.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                fail(where + child.name(), "未知字段（允许: " + join(java.util.Arrays.asList(allowed), ", ") + "）");
            }
        }
    }

    private static <E extends Enum<E> & Vocab> String vocabNames(Class<E> type) {
        StringBuilder sb = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(e.jsonName());
        }
        return sb.toString();
    }

    private static String join(Iterable<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    private static void fail(String where, String message) {
        throw new DataValidationException(where + ": " + message);
    }
}
