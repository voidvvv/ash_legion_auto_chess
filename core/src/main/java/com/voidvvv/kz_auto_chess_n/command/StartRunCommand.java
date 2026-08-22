package com.voidvvv.kz_auto_chess_n.command;

import java.util.Objects;

/** 开局域边界事件（architecture §一：回放流第 0 条记录；Q3 裁决：seed 由 UI 给定，heroId 留 Phase 6 扩展位恒 null） */
public final class StartRunCommand implements GameCommand {
    private final long seed;
    private final String sceneId;
    private final String heroId;

    public StartRunCommand(long seed, String sceneId, String heroId) {
        this.seed = seed;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId 不能为 null");
        this.heroId = heroId;
    }

    public long getSeed() { return seed; }
    public String getSceneId() { return sceneId; }
    /** Phase 6 扩展位：本期恒 null */
    public String getHeroId() { return heroId; }

    @Override
    public String toString() {
        return "StartRun(seed=" + seed + ", scene=" + sceneId + ", hero=" + heroId + ")";
    }
}
