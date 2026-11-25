package com.voidvvv.kz_auto_chess_n.data;

/** 亡语载荷形态（4 kind 覆盖 v5 全部 5 条亡语；数值在 units.json，K1） */
public enum DeathrattleKind implements Vocab {
    AREA_TRUE_DAMAGE("AREA_TRUE_DAMAGE"),   // 尸体格为心曼哈顿半径内真伤（可附 SLOW）
    KILLER_TRUE_DAMAGE("KILLER_TRUE_DAMAGE"), // 对击杀者（无效则按 C5 转移）
    DEATH_VOLLEY("DEATH_VOLLEY"),           // 自尸体格发亡语弹（第 1 发 HIGHEST_ATK；双响第 2 发 LOWEST_HP）
    ALLY_REGEN("ALLY_REGEN");               // 存活友军 REGEN

    private final String jsonName;
    DeathrattleKind(String jsonName) { this.jsonName = jsonName; }
    @Override public String jsonName() { return jsonName; }
}
