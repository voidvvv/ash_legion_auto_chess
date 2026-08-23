package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.config.DataValidationException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 档案 JSON 编解码（纯 String 双向，零 FileHandle/Gdx——JUnit 直测）。
 * 格式：{"version":1,"heroes":{"hero_x":{"level":2,"exp":35}},"completedScenes":["scene_forest"]}
 * 读侧沿 JsonLoader 口径：显式映射 + 未知字段即死；版本不符抛错（由 Store 决定重置）。
 */
public final class ProfileCodec {

    private static final JsonReader READER = new JsonReader();

    private ProfileCodec() {
    }

    public static String write(Profile profile) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"version\":").append(profile.getVersion());
        sb.append(",\"heroes\":{");
        boolean first = true;
        for (Map.Entry<String, HeroProgress> e : profile.getHeroProgress().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":{\"level\":").append(e.getValue().getLevel())
                    .append(",\"exp\":").append(e.getValue().getExp()).append('}');
        }
        sb.append("},\"completedScenes\":[");
        first = true;
        for (String sceneId : profile.getCompletedScenes()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(sceneId).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static Profile read(String json) {
        JsonValue root = READER.parse(json == null || json.trim().isEmpty() ? "{}" : json);
        if (!root.isObject()) {
            throw new DataValidationException("profile.json: 根节点必须是对象");
        }
        checkUnknownKeys(root, "version", "heroes", "completedScenes");
        JsonValue versionNode = root.get("version");
        int version = versionNode == null || versionNode.isNull()
                ? Profile.CURRENT_VERSION : requireInt(root, "version");
        if (version != Profile.CURRENT_VERSION) {
            throw new DataValidationException(
                    "profile.json/version: 不支持的档案版本 " + version + "（当前 " + Profile.CURRENT_VERSION + "）");
        }
        LinkedHashMap<String, HeroProgress> heroes = new LinkedHashMap<String, HeroProgress>();
        JsonValue heroesNode = root.get("heroes");
        if (heroesNode != null && heroesNode.isObject()) {
            for (JsonValue h = heroesNode.child; h != null; h = h.next) {
                checkUnknownKeys(h, "level", "exp");
                int level = requireInt(h, "level");
                if (level < 1 || level > 5) {
                    throw new DataValidationException(
                            "profile.json/level: 熟练度等级必须在 1~5，实际=" + level);
                }
                heroes.put(h.name(), new HeroProgress(level, requireInt(h, "exp")));
            }
        }
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        JsonValue scenesNode = root.get("completedScenes");
        if (scenesNode != null && scenesNode.isArray()) {
            for (JsonValue s = scenesNode.child; s != null; s = s.next) {
                if (!s.isString() || s.asString().trim().isEmpty()) {
                    throw new DataValidationException("profile.json/completedScenes: 元素必须为非空字符串");
                }
                completed.add(s.asString());
            }
        }
        return new Profile(version, heroes, completed);
    }

    private static void checkUnknownKeys(JsonValue obj, String... allowed) {
        for (JsonValue child = obj.child; child != null; child = child.next) {
            boolean known = false;
            for (String a : allowed) {
                if (a.equals(child.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new DataValidationException("profile.json: 未知字段 " + child.name()
                        + "（允许: " + Arrays.toString(allowed) + "）");
            }
        }
    }

    private static int requireInt(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            throw new DataValidationException("profile.json/" + field + ": 缺失或非数字");
        }
        double d = child.asDouble();
        if (Math.rint(d) != d) {
            throw new DataValidationException("profile.json/" + field + ": 必须为整数，实际=" + d);
        }
        return (int) d;
    }
}
