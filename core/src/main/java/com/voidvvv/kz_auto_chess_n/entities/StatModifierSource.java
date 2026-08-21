package com.voidvvv.kz_auto_chess_n.entities;

/**
 * 修正源接口（Q4 裁决：修正源列表）：属性派生管线对"源"的唯一依赖。
 *
 * <p>本期唯一实现 = 羁绊快照（systems.SynergySnapshot）；Phase 5 装备源作为
 * 新修正源追加进列表即可，{@code StatPipeline} 结算器零改动。
 */
public interface StatModifierSource {

    /** 该源贡献的全部修正（不可变块） */
    StatModifierBlock modifiers();
}
