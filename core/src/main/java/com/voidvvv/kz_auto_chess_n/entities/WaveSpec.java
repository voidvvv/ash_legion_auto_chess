package com.voidvvv.kz_auto_chess_n.entities;

import com.voidvvv.kz_auto_chess_n.data.UnitData;

/**
 * 敌阵规格（WaveGenerator 的不可变中间产物，Phase 2 口径 Q1）。
 *
 * <p>模板直接引用（生成期已经 GameData 解析；模板终身只读，持引用安全）。
 * Phase 3 由 BattleSystem 派生 BattleUnit：
 * 属性 = baseStats × starStatMultiplier(star) × scale，再走装备/羁绊修正管线（battle §八）。
 *
 * <p>星级 Phase 2 恒 1（实现层口径 #2）；scale 为已应用的强度系数：
 * 杂兵 = k（enemyScale），Boss = 1.0（烘焙终值不二次放大，Q3）。
 */
public final class WaveSpec {
    private final UnitData template;
    private final int star;
    private final float scale;
    private final int gridX;
    private final int gridY;

    public WaveSpec(UnitData template, int star, float scale, int gridX, int gridY) {
        this.template = template;
        this.star = star;
        this.scale = scale;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public UnitData getTemplate() { return template; }
    /** 星级：Phase 2 恒 1，字段保留供 Phase 3 派生公式 */
    public int getStar() { return star; }
    /** 已应用的强度系数：杂兵 = k，Boss = 1.0 */
    public float getScale() { return scale; }
    /** 敌区格坐标列（0~5） */
    public int getGridX() { return gridX; }
    /** 敌区格坐标行（0~2） */
    public int getGridY() { return gridY; }
    /** Boss 判定经模板，不冗余存储 */
    public boolean isBoss() { return template.isBoss(); }

    /** 确定性对拍断言用：模板按同一 GameData 中的规范实例引用比较，scale 按位比较 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WaveSpec)) {
            return false;
        }
        WaveSpec other = (WaveSpec) o;
        return star == other.star
                && gridX == other.gridX
                && gridY == other.gridY
                && Float.floatToIntBits(scale) == Float.floatToIntBits(other.scale)
                && template == other.template;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + star;
        result = 31 * result + gridX;
        result = 31 * result + gridY;
        result = 31 * result + Float.floatToIntBits(scale);
        result = 31 * result + System.identityHashCode(template);
        return result;
    }
}
