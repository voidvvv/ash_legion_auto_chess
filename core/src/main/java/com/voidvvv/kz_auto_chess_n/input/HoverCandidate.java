package com.voidvvv.kz_auto_chess_n.input;

import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;

/**
 * 棋盘域悬停候选（feedback04：敌方详情 hover-only）——指针所指单位的轻量只读描述。
 *
 * <p>敌方无玩家 {@link Unit} 实例（虚影 = WaveSpec、战斗单位 = BattleUnit），
 * 故候选直接携带模板（{@link UnitData} 终身只读，持引用安全）与敌方标记，
 * 消费侧（HoverPreviewCard）不再回查玩家名单——模板级行集
 * {@code UnitInfoText.previewLines(UnitData, GameData, int)} 零改动适配。
 *
 * <p>{@link #key()} 为悬停状态机驻留键：源 id × {@link #KEY_STRIDE} + kind 序——
 * 不同种类即使源 id 相同也互不相等（状态机据此判"候选变化"重置驻留计时）；
 * NONE 单例键 -1。不可变值对象，每帧查询产生一个短命小对象（沿"小量分配"
 * 既定口径，渲染段无稳态累积）。
 */
public final class HoverCandidate {

    /** 候选来源（SHOPPING 玩家名单 / SHOPPING 敌阵虚影 / BATTLE 战斗单位敌我） */
    public enum Kind { PLAYER_UNIT, ENEMY_PREVIEW, BATTLE_UNIT }

    /** 驻留键步进（> kind 数即可；源 id &lt; 2^29 时无 int 溢出） */
    private static final int KEY_STRIDE = 4;

    /** 无候选单例（抑制/未命中；模板为 null） */
    public static final HoverCandidate NONE =
            new HoverCandidate(null, -1, null, false);

    private final Kind kind;
    private final int key;
    private final UnitData template;
    private final boolean enemy;

    private HoverCandidate(Kind kind, int key, UnitData template, boolean enemy) {
        this.kind = kind;
        this.key = key;
        this.template = template;
        this.enemy = enemy;
    }

    /** 玩家名单单位（SHOPPING：备战席/玩家区，敌方标记恒假） */
    public static HoverCandidate ofPlayerUnit(Unit unit) {
        return new HoverCandidate(Kind.PLAYER_UNIT,
                unit.getId() * KEY_STRIDE + Kind.PLAYER_UNIT.ordinal(),
                unit.getTemplate(), false);
    }

    /** 敌阵侦察虚影（SHOPPING：WaveSpec 无实体 id，以轮内列表索引为源 id——轮内波次固定，键稳定） */
    public static HoverCandidate ofEnemyPreview(int waveIndex, UnitData template) {
        return new HoverCandidate(Kind.ENEMY_PREVIEW,
                waveIndex * KEY_STRIDE + Kind.ENEMY_PREVIEW.ordinal(),
                template, true);
    }

    /** 战斗单位（BATTLE：敌我 BattleUnit；id 与玩家名单同一发号空间，敌方标记按 side） */
    public static HoverCandidate ofBattleUnit(BattleUnit unit) {
        return new HoverCandidate(Kind.BATTLE_UNIT,
                unit.getId() * KEY_STRIDE + Kind.BATTLE_UNIT.ordinal(),
                unit.getTemplate(), unit.getSide() == Side.ENEMY);
    }

    public Kind kind() { return kind; }

    /** 悬停状态机驻留键（&lt;0 = 无候选） */
    public int key() { return key; }

    /** 候选模板（NONE 时为 null；行集走模板级 previewLines） */
    public UnitData template() { return template; }

    /** 敌方标记（卡首行示"（敌方）"）：虚影与敌侧战斗单位为真 */
    public boolean isEnemy() { return enemy; }

    public boolean isNone() { return key < 0; }
}
