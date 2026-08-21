package com.voidvvv.kz_auto_chess_n.utils;

import java.util.Arrays;

/**
 * 锚点分段线性插值表：keys 升序锚点，values 对应值；键间线性插值，越界钳制到端点值。
 * （敌方人口曲线与商店费阶概率共用，GDD §7.3 / §3.4 锚点表的"逐轮线性插值"工具）
 *
 * <p>不可变：构造后只读，可安全共享。
 */
public final class AnchorTable {
    private final float[] keys;
    private final float[] values;

    public AnchorTable(float[] keys, float[] values) {
        if (keys == null || values == null || keys.length == 0) {
            throw new IllegalArgumentException("锚点表不允许为空");
        }
        if (keys.length != values.length) {
            throw new IllegalArgumentException("锚点键值长度不匹配: keys=" + keys.length + ", values=" + values.length);
        }
        for (int i = 1; i < keys.length; i++) {
            if (keys[i] <= keys[i - 1]) {
                throw new IllegalArgumentException("锚点键必须严格升序: keys=" + Arrays.toString(keys));
            }
        }
        this.keys = keys.clone();
        this.values = values.clone();
    }

    /**
     * 取 key 对应的插值：精确命中返回锚点值；两锚点间线性插值；越界钳制到首/末锚点值。
     */
    public float valueAt(float key) {
        if (key <= keys[0]) {
            return values[0];
        }
        int last = keys.length - 1;
        if (key >= keys[last]) {
            return values[last];
        }
        // 二分找右邻：keys[i-1] < key < keys[i]
        int lo = 0, hi = last;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] <= key) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = keys[hi] - keys[lo];
        float t = (key - keys[lo]) / span;
        return values[lo] + t * (values[hi] - values[lo]);
    }
}
