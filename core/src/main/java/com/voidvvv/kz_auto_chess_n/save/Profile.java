package com.voidvvv.kz_auto_chess_n.save;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 局外档案（architecture §一「局外档案态」；不可变，整体替换式更新）。
 * 场景解锁不落档——由 completedScenes 经 unlockAfter 链派生（裁决 D7，防双源漂移）。
 */
public final class Profile {
    public static final int CURRENT_VERSION = 1;

    private final int version;
    private final Map<String, HeroProgress> heroProgress;
    private final Set<String> completedScenes;
    private final Set<String> practiceSceneGrants;

    public Profile(int version, Map<String, HeroProgress> heroProgress, Set<String> completedScenes) {
        this(version, heroProgress, completedScenes, Collections.emptySet());
    }

    Profile(int version, Map<String, HeroProgress> heroProgress, Set<String> completedScenes,
            Set<String> practiceSceneGrants) {
        this.version = version;
        this.heroProgress = Collections.unmodifiableMap(
                new LinkedHashMap<String, HeroProgress>(heroProgress));
        this.completedScenes = Collections.unmodifiableSet(
                new LinkedHashSet<String>(completedScenes));
        this.practiceSceneGrants = Collections.unmodifiableSet(
                new LinkedHashSet<String>(practiceSceneGrants));
    }

    /** 初始档案（无进度、无通关记录） */
    public static Profile fresh() {
        return new Profile(CURRENT_VERSION,
                new LinkedHashMap<String, HeroProgress>(),
                new LinkedHashSet<String>());
    }

    public int getVersion() { return version; }
    public Map<String, HeroProgress> getHeroProgress() { return heroProgress; }
    public Set<String> getCompletedScenes() { return completedScenes; }
    public Set<String> getPracticeSceneGrants() { return practiceSceneGrants; }

    Profile withPracticeSceneGrant(String sceneId, boolean granted) {
        Set<String> next = new LinkedHashSet<>(practiceSceneGrants);
        if (granted) {
            next.add(sceneId);
        } else {
            next.remove(sceneId);
        }
        return new Profile(version, heroProgress, completedScenes, next);
    }

    /** 替换式更新：写入/覆盖单英雄进度（返回新档案） */
    public Profile withHeroProgress(String heroId, HeroProgress progress) {
        LinkedHashMap<String, HeroProgress> next =
                new LinkedHashMap<String, HeroProgress>(heroProgress);
        next.put(heroId, progress);
        return new Profile(version, next, completedScenes, practiceSceneGrants);
    }

    /** 替换式更新：登记通关场景（幂等；返回新档案） */
    public Profile withCompletedScene(String sceneId) {
        if (completedScenes.contains(sceneId)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<String>(completedScenes);
        next.add(sceneId);
        return new Profile(version, heroProgress, next, practiceSceneGrants);
    }
}
