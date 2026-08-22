package com.voidvvv.kz_auto_chess_n.command;

import java.util.Objects;

/** 开局域边界事件（architecture §一：回放流第 0 条记录；seed/heroId 由 UI 给定——Phase 6 heroId 启用） */
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
    public String getHeroId() { return heroId; }

    @Override
    public String toString() {
        return "StartRun(seed=" + seed + ", scene=" + sceneId + ", hero=" + heroId + ")";
    }
}
