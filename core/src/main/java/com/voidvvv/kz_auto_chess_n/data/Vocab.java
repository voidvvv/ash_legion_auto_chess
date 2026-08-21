package com.voidvvv.kz_auto_chess_n.data;

/**
 * 词表枚举公共接口：JSON 中的合法值名。
 *
 * <p>data_schema §三"词表即代码"的铁律：JSON 的 stat 名、状态 type、代码枚举三处共用一套同名词表。
 * 枚举常量名与 JSON 值不一致时（如 {@code attackSpeed} ↔ {@code ATTACK_SPEED}），以 {@link #jsonName()} 为准。
 */
public interface Vocab {
    /** 该枚举值在 JSON 中的合法字面值 */
    String jsonName();
}
