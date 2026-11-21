package com.voidvvv.kz_auto_chess_n.data;

/** 施放条件（skills.json "condition"，可选）：不满足 → 延后施放（口径 #18「持能等待」） */
public final class CastCondition {
    /** 条件类型（本期一档：主目标生命比例低于 value） */
    public enum Type implements Vocab { TARGET_HP_BELOW("TARGET_HP_BELOW");
        private final String jsonName;
        Type(String jsonName) { this.jsonName = jsonName; }
        @Override public String jsonName() { return jsonName; } }

    private final Type type;
    private final float value;

    public CastCondition(Type type, float value) {
        this.type = type; this.value = value;
    }
    public Type getType() { return type; }
    public float getValue() { return value; }
}
