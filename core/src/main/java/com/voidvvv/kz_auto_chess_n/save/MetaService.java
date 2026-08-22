package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.HeroData;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;

import java.util.Objects;
import java.util.Set;

/**
 * 档案域门面（architecture §三 方案 A）：Screen 层唯一入口。
 * 当前 Profile 整体替换式更新；规则全部委托 ProfileService/SnapshotCodec 纯函数，
 * 本类只做状态持有 + IO 编排（profile/快照句柄经构造注入——Main 装配，裁决 D14）。
 */
public final class MetaService {

    private final ProfileStore profileStore;
    private final SnapshotStore snapshotStore;
    private Profile profile = Profile.fresh();

    public MetaService(FileHandle profileFile, FileHandle snapshotFile) {
        this.profileStore = new ProfileStore(profileFile);
        this.snapshotStore = new SnapshotStore(snapshotFile);
    }

    /** 启动装载（Main.create 调一次；损坏自动重置——裁决 D20） */
    public void loadProfile() {
        this.profile = profileStore.load();
    }

    public Profile getProfile() {
        return profile;
    }

    /** 已解锁场景 id 集（ProfileService 派生——裁决 D7） */
    public Set<String> unlockedSceneIds(GameData data) {
        return ProfileService.unlockedSceneIds(profile, data);
    }

    public boolean isSceneUnlocked(String sceneId, GameData data) {
        return unlockedSceneIds(data).contains(sceneId);
    }

    /** 装配期局外修正（RunSetup 选定 heroId → BattleScreen.newContext 消费） */
    public RunModifiers resolveRunModifiers(String heroId, GameData data) {
        HeroData hero = heroId == null ? null : data.getHero(heroId);
        return ProfileService.runModifiers(hero, profile, data);
    }

    /**
     * RUN_END 结算（BattleScreen 观察触发，每局恰一次——裁决 D11）：
     * 纯结算 → 内存档案替换 → 落盘；写失败记日志不炸（裁决 D20）。
     */
    public ProfileService.Settlement settleRun(GameData data, RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        ProfileService.Settlement settlement = ProfileService.settle(profile, data,
                ctx.getRunState().getHeroId(), ctx.getRunState().getSceneId(),
                ctx.getRunState().getEndCause(), ctx.getRunState().getRound(),
                ctx.getRunState().getMasteryAwarded());
        this.profile = settlement.getNewProfile();
        profileStore.save(profile);
        return settlement;
    }

    // —— 快照轨（CP16；触发口径见 BattleScreen——进 SHOPPING 即写 + pause/hide 补写，裁决 D10） ——

    public boolean hasRunSnapshot() {
        return snapshotStore.exists();
    }

    /** 读快照（主菜单「继续远征」）；不存在/损坏/引用悬空 → 删档并返回 null（裁决 D20） */
    public RunSnapshot loadRunSnapshot(GameData data) {
        return snapshotStore.load(data);
    }

    public void saveRunSnapshot(RunContext ctx) {
        snapshotStore.save(SnapshotCodec.capture(ctx));
    }

    public void clearRunSnapshot() {
        snapshotStore.delete();
    }
}
