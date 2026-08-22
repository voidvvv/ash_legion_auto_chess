package com.voidvvv.kz_auto_chess_n.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态数据聚合容器：JsonLoader 的产出。
 *
 * <p>模板完全不可变（architecture §2.4 第一层），加载一次终身只读；
 * 查找表保持 JSON 文件内的声明顺序（LinkedHashMap），便于确定性遍历。
 *
 * <p>{@code warnings} 为加载期软告警（孤儿技能 / 孤儿羁绊 / 风味种族聚合 / ALL_ENEMIES 非 Boss 引用，
 * data_schema §九）——不阻断运行，由调用方决定展示方式；本类不触碰 Gdx.*。
 */
public final class GameData {
    private final Map<String, UnitData> units;
    private final Map<String, SkillData> skills;
    private final Map<String, SynergyData> synergies;
    private final Map<String, SceneData> scenes;
    private final Map<String, EquipmentData> equipments;
    private final List<String> warnings;

    /** 兼容重载：无装备表（存量测试构造先例） */
    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    List<String> warnings) {
        this(units, skills, synergies, scenes, new LinkedHashMap<String, EquipmentData>(), warnings);
    }

    public GameData(Map<String, UnitData> units, Map<String, SkillData> skills,
                    Map<String, SynergyData> synergies, Map<String, SceneData> scenes,
                    Map<String, EquipmentData> equipments, List<String> warnings) {
        this.units = Collections.unmodifiableMap(new LinkedHashMap<String, UnitData>(units));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillData>(skills));
        this.synergies = Collections.unmodifiableMap(new LinkedHashMap<String, SynergyData>(synergies));
        this.scenes = Collections.unmodifiableMap(new LinkedHashMap<String, SceneData>(scenes));
        this.equipments = Collections.unmodifiableMap(new LinkedHashMap<String, EquipmentData>(equipments));
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public UnitData getUnit(String id) { return units.get(id); }
    public SkillData getSkill(String id) { return skills.get(id); }
    public SynergyData getSynergy(String id) { return synergies.get(id); }
    public SceneData getScene(String id) { return scenes.get(id); }
    public EquipmentData getEquipment(String id) { return equipments.get(id); }

    public Map<String, UnitData> getUnits() { return units; }
    public Map<String, SkillData> getSkills() { return skills; }
    public Map<String, SynergyData> getSynergies() { return synergies; }
    public Map<String, SceneData> getScenes() { return scenes; }
    public Map<String, EquipmentData> getEquipments() { return equipments; }

    /** 加载期软告警（可能为空，永不为 null） */
    public List<String> getWarnings() { return warnings; }
}
