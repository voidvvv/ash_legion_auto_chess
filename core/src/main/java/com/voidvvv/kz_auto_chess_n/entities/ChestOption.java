package com.voidvvv.kz_auto_chess_n.entities;

import java.util.Objects;

/** 宝箱单选项（不可变）：GDD 用语 金币/经验书/装备 ↔ GOLD/EXP_BOOK/EQUIPMENT */
public final class ChestOption {
    public enum Kind { GOLD, EXP_BOOK, EQUIPMENT }

    private final Kind kind;
    /** GOLD/EXP_BOOK 金额；EQUIPMENT 为 0 */
    private final int amount;
    /** 仅 EQUIPMENT 非 null */
    private final String equipmentId;

    private ChestOption(Kind kind, int amount, String equipmentId) {
        this.kind = Objects.requireNonNull(kind, "kind 不能为 null");
        this.amount = amount;
        this.equipmentId = equipmentId;
    }

    public static ChestOption gold(int amount) { return new ChestOption(Kind.GOLD, amount, null); }
    public static ChestOption expBook(int amount) { return new ChestOption(Kind.EXP_BOOK, amount, null); }
    public static ChestOption equipment(String equipmentId) {
        return new ChestOption(Kind.EQUIPMENT, 0, Objects.requireNonNull(equipmentId, "equipmentId 不能为 null"));
    }

    public Kind getKind() { return kind; }
    public int getAmount() { return amount; }
    public String getEquipmentId() { return equipmentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChestOption)) {
            return false;
        }
        ChestOption that = (ChestOption) o;
        return amount == that.amount && kind == that.kind && Objects.equals(equipmentId, that.equipmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, amount, equipmentId);
    }
}
