package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;

/**
 * 档案文件 IO（薄层）：缺失 → 初始档案；解析/校验失败 → 记日志并重置（裁决 D20，
 * 玩家数据演进常态不炸档）；写入失败 → 记日志返回 false（调用方决定表现，不中断局内）。
 * 日志走 System.err（沿 CommandManager 先例，JUnit 零 Gdx.app 可测）。
 */
public final class ProfileStore {

    private final FileHandle file;

    public ProfileStore(FileHandle file) {
        this.file = file;
    }

    public Profile load() {
        if (file == null || !file.exists()) {
            return Profile.fresh();
        }
        try {
            return ProfileCodec.read(file.readString("UTF-8"));
        } catch (RuntimeException ex) {
            System.err.println("[ProfileStore] 档案损坏，重置为初始档案: "
                    + (file == null ? "?" : file.path()) + " / " + ex.getMessage());
            return Profile.fresh();
        }
    }

    public boolean save(Profile profile) {
        if (file == null) {
            return false;
        }
        try {
            if (file.parent() != null) {
                file.parent().mkdirs();
            }
            file.writeString(ProfileCodec.write(profile), false, "UTF-8");
            return true;
        } catch (RuntimeException ex) {
            System.err.println("[ProfileStore] 档案写入失败: " + file.path() + " / " + ex.getMessage());
            return false;
        }
    }
}
