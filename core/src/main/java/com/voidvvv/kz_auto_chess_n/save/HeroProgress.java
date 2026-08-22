package com.voidvvv.kz_auto_chess_n.save;

/** 单英雄熟练度进度（不可变：等级 1~5 + 当前级内经验）。 */
public final class HeroProgress {
    private final int level;
    private final int exp;

    public HeroProgress(int level, int exp) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("熟练度等级必须在 1~5，实际=" + level);
        }
        this.level = level;
        this.exp = Math.max(0, exp);
    }

    /** 初始进度（Lv.1 / 0 经验——GDD §8.1 等级表起点） */
    public static HeroProgress initial() {
        return new HeroProgress(1, 0);
    }

    public int getLevel() { return level; }
    public int getExp() { return exp; }

    /** 值等（不可变值对象；round-trip 断言用） */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeroProgress)) {
            return false;
        }
        HeroProgress other = (HeroProgress) o;
        return level == other.level && exp == other.exp;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + level * 7 + exp;
    }
}
