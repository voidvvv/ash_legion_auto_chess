package com.voidvvv.kz_auto_chess_n.render.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知日志模型（render §5.5；纯逻辑零 Gdx，headless 可测）。
 * 有界 200 行 FIFO；小窗最近 4 行；单帧追加 ≤2 行（战斗爆发期防刷屏——超出丢弃，WARNING-6）。
 */
public final class NotificationLog {
    public static final int CAPACITY = 200;
    public static final int SMALL_WINDOW_LINES = 4;
    public static final int MAX_APPENDS_PER_FRAME = 2;

    private final ArrayDeque<String> lines = new ArrayDeque<String>(CAPACITY);
    private boolean largeMode;

    /** 追加一行（超单帧上限丢弃；null/空忽略） */
    public boolean appendCapped(String line, int appendedThisFrame) {
        if (line == null || line.trim().isEmpty() || appendedThisFrame >= MAX_APPENDS_PER_FRAME) {
            return false;
        }
        if (lines.size() >= CAPACITY) {
            lines.pollFirst();
        }
        lines.addLast(line);
        return true;
    }

    /** 当前可见行（小窗 = 最近 4 行；大窗 = 全量无过滤——WARNING-6） */
    public List<String> visibleLines() {
        List<String> visible = new ArrayList<String>();
        if (largeMode) {
            visible.addAll(lines);
        } else {
            int skip = Math.max(0, lines.size() - SMALL_WINDOW_LINES);
            int i = 0;
            for (String line : lines) {
                if (i++ >= skip) {
                    visible.add(line);
                }
            }
        }
        return visible;
    }

    public void setLargeMode(boolean largeMode) {
        this.largeMode = largeMode;
    }

    public void toggleLargeMode() {
        this.largeMode = !largeMode;
    }

    public boolean isLargeMode() {
        return largeMode;
    }

    public void clear() {
        lines.clear();
    }
}
