package com.voidvvv.kz_auto_chess_n.config;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.data.Vocab;

import java.util.Arrays;

/**
 * JsonValue 读取与校验工具（自 JsonLoader 拆出，Phase 6——文件规模上限）：
 * 报错统一含 文件#条目/字段路径（DataValidationException 即死）。
 * 仅供本包加载器共用；词表解析按 {@link Vocab#jsonName()} 匹配。
 */
final class JsonReadUtils {
    private static final JsonReader READER = new JsonReader();

    private JsonReadUtils() {
    }

    // ==================================================================
    // JsonValue 读取与校验工具（报错统一含 文件#条目/字段路径）
    // ==================================================================

    static JsonValue parseArray(FileHandle file) {
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

    static void requireObject(JsonValue v, String where) {
        if (!v.isObject()) {
            fail(where, "条目必须是对象");
        }
    }

    static JsonValue require(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            fail(where + field, "缺必填字段");
        }
        return child;
    }

    static String requireString(JsonValue obj, String field, String where) {
        JsonValue child = require(obj, field, where);
        if (!child.isString() || child.asString().trim().isEmpty()) {
            fail(where + field, "必须为非空字符串");
        }
        return child.asString();
    }

    /** 可选字符串：缺省或显式 null 放行返回 null；出现则必须为非空字符串 */
    static String optionalString(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isString() || child.asString().trim().isEmpty()) {
            fail(where + field, "必须为非空字符串");
        }
        return child.asString();
    }

    static int requireInt(JsonValue obj, String field, String where) {
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

    static float requireFloat(JsonValue obj, String field, String where) {
        JsonValue child = require(obj, field, where);
        if (!child.isNumber()) {
            fail(where + field, "必须为数字，实际=" + child);
        }
        return child.asFloat();
    }

    static boolean optionalBool(JsonValue obj, String field, String where, boolean def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        if (!child.isBoolean()) {
            fail(where + field, "必须为布尔值，实际=" + child);
        }
        return child.asBoolean();
    }

    static int optionalInt(JsonValue obj, String field, String where, int def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        return requireInt(obj, field, where);
    }

    static float optionalFloat(JsonValue obj, String field, String where, float def) {
        Float v = optionalFloatObj(obj, field, where);
        return v == null ? def : v;
    }

    static Float optionalFloatObj(JsonValue obj, String field, String where) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isNumber()) {
            fail(where + field, "必须为数字，实际=" + child);
        }
        return child.asFloat();
    }

    static void checkNonNegative(JsonValue obj, String field, String w, int value) {
        if (value < 0) {
            fail(w + field, "不允许负值，实际=" + value);
        }
    }

    /** 词表解析：按 {@link Vocab#jsonName()} 匹配，非法值报错并列出全部合法值 */
    static <E extends Enum<E> & Vocab> E requireVocab(JsonValue obj, String field, Class<E> type, String where) {
        String raw = requireString(obj, field, where);
        for (E e : type.getEnumConstants()) {
            if (e.jsonName().equals(raw)) {
                return e;
            }
        }
        fail(where + field, "非法枚举值 \"" + raw + "\"，合法值: " + vocabNames(type));
        return null; // 不可达
    }

    static <E extends Enum<E> & Vocab> E optionalVocab(JsonValue obj, String field, Class<E> type, String where, E def) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return def;
        }
        return requireVocab(obj, field, type, where);
    }

    static void checkUnknownKeys(JsonValue obj, String where, String... allowed) {
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

    static <E extends Enum<E> & Vocab> String vocabNames(Class<E> type) {
        StringBuilder sb = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(e.jsonName());
        }
        return sb.toString();
    }

    static String join(Iterable<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    static void fail(String where, String message) {
        throw new DataValidationException(where + ": " + message);
    }
}
