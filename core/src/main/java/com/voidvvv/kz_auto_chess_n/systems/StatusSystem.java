package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.entities.ActiveStatus;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 状态系统（battle §七统一框架；技能/装备/羁绊三入口共用）。
 *
 * <p>挂载/刷新：同 type 不叠加——duration 取更长、power 取更大（口径 #11）；
 * SHIELD 走吸收条目（power=吸收点数、时长无限、同类取大），落地发 SHIELDED 不发
 * STATUS_APPLIED（防双事件，口径 #21）。DOT/REGEN 的 1s 心跳在 {@link #tickStatuses}：
 * 施加时 tickTimer=0，首跳在满 1s（口径 #10）；DOT 走真伤管线可致死、不触发回能。
 */
public final class StatusSystem {
    /** 时间边界容差 = 半个逻辑步：dt 步进累积的浮点微差（每步可达 ~1e-7，长时长可累积到 ~1e-5）
     *  不改变整秒心跳/到期语义——距边界不足半步即视为到达 */
    private static final float TIME_EPSILON = GameBalance.LOGIC_STEP / 2f;

    private final DamagePipeline damagePipeline;

    public StatusSystem(DamagePipeline damagePipeline) {
        this.damagePipeline = Objects.requireNonNull(damagePipeline, "damagePipeline 不能为 null");
    }

    /** 挂载或刷新一个状态（sourceId：施加者 unit id，开局效果为 -1；心跳间隔缺省 1s） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId) {
        apply(state, target, type, power, duration, sourceId, GameBalance.DOT_TICK_INTERVAL);
    }

    /** 全参重载：装备 passiveStatus 落地（自定义心跳间隔，实现口径 #7） */
    public void apply(BattleState state, BattleUnit target, StatusType type,
                      float power, float duration, int sourceId, float tickInterval) {
        Objects.requireNonNull(state, "state 不能为 null");
        Objects.requireNonNull(target, "target 不能为 null");
        Objects.requireNonNull(type, "type 不能为 null");

        ActiveStatus existing = findSameType(target, type);
        if (type == StatusType.SHIELD) {
            if (existing == null) {
                target.addStatus(new ActiveStatus(StatusType.SHIELD, sourceId, power,
                        Float.POSITIVE_INFINITY, tickInterval));
            } else {
                existing.setPower(Math.max(existing.getPower(), power));
                target.invalidateStats();
            }
            state.record(CombatEvent.shielded(state.getTick(), sourceId, target.getId(),
                    existing == null ? power : Math.max(existing.getPower(), power)));
            return;
        }

        if (existing == null) {
            target.addStatus(new ActiveStatus(type, sourceId, power, duration, tickInterval));
        } else {
            existing.setPower(Math.max(existing.getPower(), power));
            existing.setRemainingTime(Math.max(existing.getRemainingTime(), duration));
            target.invalidateStats();
        }
        state.record(CombatEvent.statusApplied(state.getTick(), sourceId, target.getId(),
                type, power, duration));
    }

    /** 阶段①：DOT/REGEN 1s 心跳 · 时长递减 · 到期移除（id 序逐单位推进） */
    public void tickStatuses(BattleState state, float dt) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.isCleaned()) {
                continue;
            }
            List<ActiveStatus> snapshot = new ArrayList<ActiveStatus>(unit.getStatuses());
            for (ActiveStatus status : snapshot) {
                tickHeartbeats(state, unit, status, dt);
                status.setRemainingTime(status.getRemainingTime() - dt);
            }
            for (ActiveStatus status : snapshot) {
                if (status.getRemainingTime() <= TIME_EPSILON) {
                    unit.removeStatus(status); // 到期移除（自动打脏标记）
                }
            }
        }
    }

    private void tickHeartbeats(BattleState state, BattleUnit owner, ActiveStatus status, float dt) {
        if (status.getType() != StatusType.BLEED && status.getType() != StatusType.POISON
                && status.getType() != StatusType.REGEN) {
            return; // 非 DOT/HOT 类无心跳
        }
        status.setTickTimer(status.getTickTimer() + dt);
        float interval = status.getTickInterval();
        while (status.getTickTimer() >= interval - TIME_EPSILON) {
            status.setTickTimer(status.getTickTimer() - interval);
            if (status.getType() == StatusType.REGEN) {
                damagePipeline.applyHeal(state, owner,
                        owner.getEffective(StatKey.HP) * status.getPower());
            } else {
                BattleUnit source = state.getUnitById(status.getSourceId()); // 可能 null（施加者不在场）
                damagePipeline.applyTrueDamage(state, source, owner, status.getPower());
            }
        }
    }

    private static ActiveStatus findSameType(BattleUnit target, StatusType type) {
        for (ActiveStatus status : target.getStatuses()) {
            if (status.getType() == type) {
                return status;
            }
        }
        return null;
    }
}
