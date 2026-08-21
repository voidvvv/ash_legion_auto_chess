package com.voidvvv.kz_auto_chess_n.render.board;

/**
 * 跳格插值（render §4.2；纯 Java 可测）。
 *
 * <p>格坐标（cell 单位）插值：坐标变化（onCellPolled）时 from=旧 to、to=新格、
 * 记录切换时刻；位置 = lerp(from, to, t)，t = clamp((clock - 切换时刻) × moveSpeed, 0, 1)。
 * clock 为渲染帧时钟（秒），moveSpeed 单位格/秒。到达停稳待机。
 */
public final class LerpMotion {
    private final float moveSpeed;

    private float fromX;
    private float fromY;
    private float toX;
    private float toY;
    private float startTime;
    private int lastX;
    private int lastY;

    public LerpMotion(float moveSpeed) {
        if (moveSpeed <= 0f) {
            throw new IllegalArgumentException("moveSpeed 必须为正（格/秒），实际=" + moveSpeed);
        }
        this.moveSpeed = moveSpeed;
    }

    /** 直落指定格（战斗重建时无插值） */
    public void reset(int gridX, int gridY) {
        fromX = gridX;
        fromY = gridY;
        toX = gridX;
        toY = gridY;
        startTime = 0f;
        lastX = gridX;
        lastY = gridY;
    }

    /** 每渲染帧轮询逻辑坐标：变化即起跳（from = 旧 to，链式） */
    public void onCellPolled(int gridX, int gridY, float clock) {
        if (gridX == lastX && gridY == lastY) {
            return;
        }
        fromX = toX;
        fromY = toY;
        toX = gridX;
        toY = gridY;
        startTime = clock;
        lastX = gridX;
        lastY = gridY;
    }

    public float positionX(float clock) {
        return lerp(fromX, toX, t(clock));
    }

    public float positionY(float clock) {
        return lerp(fromY, toY, t(clock));
    }

    /** 是否已停稳（t ≥ 1，供动画层切换 Idle/Walk） */
    public boolean isSettled(float clock) {
        return t(clock) >= 1f;
    }

    private float t(float clock) {
        if (fromX == toX && fromY == toY) {
            return 1f; // 无在途位移
        }
        float elapsed = clock - startTime;
        if (elapsed <= 0f) {
            return 0f;
        }
        return Math.min(1f, elapsed * moveSpeed);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
