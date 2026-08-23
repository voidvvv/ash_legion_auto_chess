package com.voidvvv.kz_auto_chess_n.render.board;

/**
 * 受击抖动水平轴决策（纯函数，attack_feedback FP2；零 Gdx 可测）。
 *
 * <p>+1 = 首拍向右、−1 = 首拍向左。抖动本身正弦双向往复，轴符号只决定首拍相位
 * （缺省取「受力反方向弹开」：攻击者在受击者左侧 → 首拍向右）。无随机源——
 * 攻击者视图取不到（sourceId = -1，DamagePipeline.applyDirectHit）或与受击者
 * 同列时，按受击者 id 奇偶确定性回退。
 */
public final class HitShakeAxis {

    private HitShakeAxis() {
    }

    /** 有攻击者视图：攻击者严格在受击者左侧 → +1；右侧 → −1；同列（x 相等）走奇偶回退 */
    public static int resolve(int attackerX, int targetX, int targetId) {
        if (attackerX != targetX) {
            return attackerX < targetX ? 1 : -1;
        }
        return fallback(targetId);
    }

    /** 无攻击者回退：受击者 id 偶数 → +1、奇数 → −1（确定性，不引入随机源） */
    public static int fallback(int targetId) {
        return (targetId & 1) == 0 ? 1 : -1;
    }
}
