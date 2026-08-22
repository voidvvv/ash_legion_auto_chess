package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;

/**
 * 快照文件 IO：写 = capture 序列化直存；读 = 反序列化 + 引用完整性干跑校验
 * （restore 到临时 ShopSystem/初始 Profile——校验模板引用不落真档），
 * 悬空/损坏 → 删档 + 日志 + 返回 null（裁决 D20；与静态资源 fail-fast 口径区分）。
 */
public final class SnapshotStore {

    private final FileHandle file;

    public SnapshotStore(FileHandle file) {
        this.file = file;
    }

    public boolean exists() {
        return file != null && file.exists();
    }

    /** 读快照；不存在/损坏/引用悬空 → 删档返回 null（主菜单按钮随即不可见） */
    public RunSnapshot load(GameData data) {
        if (!exists()) {
            return null;
        }
        try {
            RunSnapshot snapshot = SnapshotCodec.read(file.readString("UTF-8"));
            SnapshotCodec.restore(snapshot, data, Profile.fresh(), new ShopSystem()); // 干跑校验
            return snapshot;
        } catch (RuntimeException ex) {
            System.err.println("[SnapshotStore] 快照损坏或引用悬空，删除: "
                    + (file == null ? "?" : file.path()) + " / " + ex.getMessage());
            delete();
            return null;
        }
    }

    public boolean save(RunSnapshot snapshot) {
        if (file == null) {
            return false;
        }
        try {
            if (file.parent() != null) {
                file.parent().mkdirs();
            }
            file.writeString(SnapshotCodec.write(snapshot), false, "UTF-8");
            return true;
        } catch (RuntimeException ex) {
            System.err.println("[SnapshotStore] 快照写入失败: " + file.path() + " / " + ex.getMessage());
            return false;
        }
    }

    public void delete() {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (RuntimeException ex) {
                System.err.println("[SnapshotStore] 快照删除失败: " + file.path() + " / " + ex.getMessage());
            }
        }
    }
}
