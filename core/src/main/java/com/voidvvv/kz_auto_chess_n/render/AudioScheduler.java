package com.voidvvv.kz_auto_chess_n.render;

import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.systems.ThreatPredictor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分层音频调度（品质改造 §1.2）：事件 → cue 映射、限频、同帧合并、优先级分配与 60ms 短排期。
 * <b>纯逻辑零 Gdx——假时钟 + 假播放器可测</b>；播放出口 {@link CuePlayer} 由 Assets 注入
 * （Gdx.audio.newSound，Assets 持有并 dispose，屏只管播放句柄）。
 *
 * <p>限频口径（§1.2，未乘战斗倍速的播放时钟）：普通命中全局 140ms / 同目标 240ms / 滚动 1s ≤6 次；
 * 关键命中按 tick+sourceId+skillId 合并多目标、全局 120ms；关键施法全局 220ms、同施法者 500ms；
 * 施法开声后零延迟关键命中排后 60ms；击杀全局 220ms、每名亡者一次，同帧击杀与关键命中优先保留
 * 击杀、仍有声道时保留关键命中（命中先、击杀后约 60ms）；普通命中在关键声开始后 100ms 内跳过。
 * 同帧先按优先级分配、同类首事件胜出；最多 3 个在播/排期，满时高优抢占最早低优
 * （{@code Sound.stop(playId)}）；只允许两种 60ms 短排期，其它超限直接丢；
 * 终局到达立刻清排期、停交战声并播一次结果声（同一 BattleState 只播一次）。
 */
public final class AudioScheduler {

    /** 音效 cue（assets/audio/ 六件；BGM 走 Music 接口另行接线，默认关闭——§1.2） */
    public enum Cue { VICTORY, DEFEAT, CAST, KILL, HIT, UPGRADE }

    private static final float NORMAL_HIT_MIN_GAP = 0.140f;
    private static final float NORMAL_HIT_SAME_TARGET_GAP = 0.240f;
    private static final int NORMAL_HIT_MAX_PER_SECOND = 6;
    private static final float NORMAL_HIT_ROLLING_WINDOW = 1f;
    private static final float NORMAL_AFTER_CAST_SKIP = 0.100f;
    private static final float KEY_HIT_MIN_GAP = 0.120f;
    private static final float CAST_MIN_GAP = 0.220f;
    private static final float CAST_SAME_SOURCE_GAP = 0.500f;
    private static final float KILL_MIN_GAP = 0.220f;
    /** 唯一允许的短排期间隔（§1.2：施法后零延迟关键命中 / 同帧命中→击杀） */
    public static final float SHORT_SCHEDULE_GAP = 0.060f;
    /** 在播 + 排期上限（§1.2：最多 3 个） */
    public static final int MAX_CHANNELS = 3;

    /** 播放出口（Assets 实现：Gdx.audio.newSound 播放并返回 playId；假播放器记录调用） */
    public interface CuePlayer {
        long play(Cue cue, float volume);

        void stop(long playId);
    }

    /** 优先级（§1.2 表：终局 5 > 关键施法/升星 4 > 击杀 3 > 关键命中 2 > 普通命中 1） */
    public static int priorityOf(Cue cue) {
        switch (cue) {
            case VICTORY:
            case DEFEAT:
                return 5;
            case CAST:
            case UPGRADE:
                return 4;
            case KILL:
                return 3;
            case HIT:
                return 2; // 关键命中；普通命中请求在映射时降为 1
            default:
                return 1;
        }
    }

    /** 音量（§1.2 表） */
    public static float volumeOf(Cue cue) {
        switch (cue) {
            case VICTORY:
            case DEFEAT:
                return 0.80f;
            case CAST:
            case UPGRADE:
                return 0.68f;
            case KILL:
                return 0.60f;
            default:
                return 0.24f; // 普通命中；关键命中由请求覆盖为 0.58
        }
    }

    /** 素材时长秒（§1.2 表区间中值——过期清理与排期上限用） */
    public static float durationOf(Cue cue) {
        switch (cue) {
            case VICTORY:
            case DEFEAT:
                return 1.1f;
            case CAST:
                return 0.26f;
            case KILL:
                return 0.24f;
            case UPGRADE:
                return 0.42f;
            default:
                return 0.10f; // HIT
        }
    }

    /** 单帧请求（调用方由 {@link #map} 产出；字段为限频/合并键） */
    public static final class Request {
        public final Cue cue;
        public final int tick;
        public final int sourceId;
        public final int targetId;
        public final String skillId;
        public final float damage;
        public final int star;
        public final float volume;
        public final int priority;

        private Request(Cue cue, int tick, int sourceId, int targetId, String skillId,
                        float damage, int star, float volume, int priority) {
            this.cue = cue;
            this.tick = tick;
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.skillId = skillId;
            this.damage = damage;
            this.star = star;
            this.volume = volume;
            this.priority = priority;
        }

        /** 关键命中请求（音量 0.58 / 优先级 2——§1.2 表）；普通命中音量 0.24 / 优先级 1 */
        static Request of(Cue cue, CombatEvent event) {
            boolean keyHit = cue == Cue.HIT
                    && ThreatPredictor.isKeyedEnemySkill(event.getSkillId());
            return new Request(cue, event.getTick(), event.getSourceId(), event.getTargetId(),
                    event.getSkillId(), Math.max(0f, event.getAmount()), 0,
                    keyHit ? 0.58f : volumeOf(cue),
                    keyHit ? 2 : (cue == Cue.HIT ? 1 : priorityOf(cue)));
        }

        static Request upgrade(int star) {
            return new Request(Cue.UPGRADE, -1, -1, -1, null, 0f, star, volumeOf(Cue.UPGRADE),
                    priorityOf(Cue.UPGRADE));
        }

        /** 是否关键命中（限频通道分流） */
        public boolean isKeyed() {
            return cue == Cue.HIT && ThreatPredictor.isKeyedEnemySkill(skillId);
        }
    }

    /**
     * 事件 → 请求（§1.2 映射表；排除项返回 null）：放逐、可复活倒地、复活、弹道消散/拦截、
     * 0 扣血、死因不明（killerId = -1）、同侧击杀均不触发对应击杀/命中声。
     */
    public static Request map(CombatEvent event, BattleState state) {
        switch (event.getType()) {
            case BATTLE_ENDED:
                return Request.of(event.getOutcome() == BattleOutcome.PLAYER_WIN
                        ? Cue.VICTORY : Cue.DEFEAT, event);
            case CAST:
                if (!ThreatPredictor.isKeyedEnemySkill(event.getSkillId())) {
                    return null; // 仅两题关键施法有声（§1.2 表）
                }
                return Request.of(Cue.CAST, event);
            case UNIT_DIED: {
                if (event.getKillerId() < 0) {
                    return null; // 死因不明
                }
                BattleUnit victim = state.getUnitById(event.getSourceId());
                BattleUnit killer = state.getUnitById(event.getKillerId());
                if (victim == null || killer == null || victim.getSide() == killer.getSide()) {
                    return null; // 须对侧有效 killerId
                }
                return Request.of(Cue.KILL, event);
            }
            case HIT:
                if (event.getAmount() <= 0f) {
                    return null; // 0 扣血
                }
                return Request.of(Cue.HIT, event);
            default:
                return null; // ATTACK_LAUNCHED/STATUS_APPLIED/HEALED/SHIELDED/UNIT_BANISHED/
                             // UNIT_DOWNED/UNIT_REVIVED/PROJECTILE_FIZZLED/PROJECTILE_BLOCKED
        }
    }

    /** 在播 / 已排期声道（§1.2：最多 3 个；startAt &gt; now = 排期中） */
    private static final class Channel {
        Cue cue;
        float startAt;
        float endAt;
        float volume;
        long playId = -1L;
        int priority;
        boolean scheduled;
    }

    private final CuePlayer player;
    private final EventInbox inbox = new EventInbox();
    private BattleState bound;
    private boolean resultPlayed; // 同一 BattleState 结果声只播一次（§1.2）
    /** 本帧已播放的关键施法起点（零延迟关键命中排后 60ms 的锚点；null = 本帧无施法声） */
    private Float castStartAnchor;
    private int castSourceAnchor = -1;
    // —— 限频状态（clear 后全部复位） ——
    private float lastNormalHitAt = Float.NEGATIVE_INFINITY;
    private int lastNormalHitTargetId = -1;
    private float lastNormalHitTargetAt = Float.NEGATIVE_INFINITY;
    private final float[] normalHitTimes = filledNormalTimes();
    private int normalHitCursor;

    private static float[] filledNormalTimes() {
        float[] times = new float[NORMAL_HIT_MAX_PER_SECOND];
        Arrays.fill(times, Float.NEGATIVE_INFINITY);
        return times;
    }
    private float lastKeyHitStartAt = Float.NEGATIVE_INFINITY;
    private float lastCastStartAt = Float.NEGATIVE_INFINITY;
    private final Map<Integer, Float> castStartBySource = new HashMap<Integer, Float>();
    private float lastKillStartAt = Float.NEGATIVE_INFINITY;
    private final Set<Integer> killedVictims = new HashSet<Integer>();
    private final List<Channel> channels = new ArrayList<Channel>();

    public AudioScheduler(CuePlayer player) {
        this.player = player;
    }

    /** 战斗作用域同步（§1.2：按战斗对象身份单独 EventInbox，换对象才 attach；含最后一逻辑帧）。
     *  战斗实例切换同时复位按战斗记账的状态（亡者集合/施法限频表——battle id 每战重发，跨战不沿用） */
    public void syncBattle(BattleState state) {
        if (state == bound) {
            return;
        }
        bound = state;
        resultPlayed = false; // 新战斗实例：结果声重新可播（同一 BattleState 只播一次）
        killedVictims.clear();
        castStartBySource.clear();
        if (state == null) {
            inbox.detach();
        } else {
            inbox.attach(state);
        }
    }

    /**
     * 帧消费（品质改造 §1.2「经步进后、弹出箱子前」由 Screen 调用）：本帧新事件 → 映射 →
     * 同类首事件胜出 → 限频/分配/播放。{@code now} 为未乘战斗倍速的播放时钟。
     */
    public void acceptFrame(float now) {
        final List<Request> requests = new ArrayList<Request>();
        inbox.forEachNew(new java.util.function.Consumer<CombatEvent>() {
            @Override
            public void accept(CombatEvent event) {
                Request request = bound == null ? null : map(event, bound);
                if (request != null) {
                    requests.add(request);
                }
            }
        });
        castStartAnchor = null;
        accept(now, requests);
    }

    /** 备战升星（§1.2：ShopSystem 升星收口；级联合成同帧只播一次、保留最高星级——调用方归并） */
    public void acceptUpgrade(float now, int star) {
        accept(now, Arrays.asList(Request.upgrade(star)));
    }

    /** 推进排期（到点即播）并按素材时长清理过期声道 */
    public void advance(float now) {
        for (int i = channels.size() - 1; i >= 0; i--) {
            Channel channel = channels.get(i);
            if (channel.scheduled && now >= channel.startAt) {
                channel.playId = player.play(channel.cue, channel.volume);
                channel.scheduled = false;
            }
            if (!channel.scheduled && now >= channel.endAt) {
                channels.remove(i);
            }
        }
    }

    /** 清掉在播与排期（暂停 / 离屏——§1.2；恢复时继续消费新事件，无积压回放） */
    public void clear() {
        for (Channel channel : channels) {
            if (channel.playId >= 0) {
                player.stop(channel.playId);
            }
        }
        channels.clear();
        Arrays.fill(normalHitTimes, Float.NEGATIVE_INFINITY); // 限频状态复位（§1.2 clear 语义）
    }

    /** 在播/排期声道数（headless 验收观察面） */
    public int channelCount() {
        return channels.size();
    }

    // —— 分配核心 ——

    private void accept(float now, List<Request> requests) {
        sweepExpired(now);
        List<Request> picked = pickPerClass(requests); // 同类首事件胜出
        // ① 终局（最高优先）：立刻清排期、停止交战声并播放一次结果声（同一 BattleState 只播一次）
        Request result = firstOf(picked, Cue.VICTORY, Cue.DEFEAT);
        if (result != null) {
            if (!resultPlayed) {
                finishWithResult(now, result);
                resultPlayed = true;
            }
            return;
        }
        // ② 关键施法（同帧后续请求的 60ms 排期锚点；全局 220ms / 同施法者 500ms）
        Request cast = firstOf(picked, Cue.CAST);
        if (cast != null && castAllowed(now, cast)) {
            start(now, cast);
            lastCastStartAt = now;
            castStartBySource.put(cast.sourceId, now);
            castStartAnchor = now;
            castSourceAnchor = cast.sourceId;
        }
        // ③ 同帧「关键命中 + 击杀」配对：命中先、击杀后约 60ms（§1.2）
        Request keyHit = firstKeyedHit(picked);
        Request kill = firstOf(picked, Cue.KILL);
        if (keyHit != null && kill != null) {
            if (keyHitAllowed(now, keyHit)) {
                float hitStart = keyHitStart(now);
                if (hitStart > now) {
                    schedule(hitStart, keyHit);
                } else {
                    start(now, keyHit);
                    lastKeyHitStartAt = now;
                }
                if (killAllowed(now, kill)) {
                    schedule(hitStart + SHORT_SCHEDULE_GAP, kill);
                    lastKillStartAt = now;
                    killedVictims.add(kill.targetId);
                }
            } else if (killAllowed(now, kill)) { // 关键命中被限频：击杀照常
                start(now, kill);
                lastKillStartAt = now;
                killedVictims.add(kill.targetId);
            }
        } else {
            // ④ 击杀单独（全局 220ms / 每亡者一次）
            if (kill != null && killAllowed(now, kill)) {
                start(now, kill);
                lastKillStartAt = now;
                killedVictims.add(kill.targetId);
            }
            // ⑤ 关键命中单独（全局 120ms；施法后 60ms 排期）
            if (keyHit != null && keyHitAllowed(now, keyHit)) {
                float hitStart = keyHitStart(now);
                if (hitStart > now) {
                    schedule(hitStart, keyHit);
                } else {
                    start(now, keyHit);
                    lastKeyHitStartAt = now;
                }
            }
        }
        // ⑥ 升星（不叠未结束结果音——结果音路径已 return；级联合成同帧只播一次）
        Request upgrade = firstOf(picked, Cue.UPGRADE);
        if (upgrade != null) {
            start(now, upgrade);
        }
        // ⑦ 普通命中（关键声开始后 100ms 内跳过；140ms 全局 / 240ms 同目标 / 滚动 1s ≤6）
        Request normalHit = firstNormalHit(picked);
        if (normalHit != null && !skippedAfterCast(now) && normalHitAllowed(now, normalHit)) {
            start(now, normalHit);
        }
    }

    /** 首个指定 cue 的请求（同帧同类首事件胜出后的留存项；无则 null） */
    private static Request firstOf(List<Request> picked, Cue... cues) {
        for (Request request : picked) {
            for (Cue cue : cues) {
                if (request.cue == cue) {
                    return request;
                }
            }
        }
        return null;
    }

    /** 首个关键命中请求（§1.2：按 tick+sourceId+skillId 合并多目标后的留存项） */
    private static Request firstKeyedHit(List<Request> picked) {
        for (Request request : picked) {
            if (request.cue == Cue.HIT && request.isKeyed()) {
                return request;
            }
        }
        return null;
    }

    /** 首个普通命中请求（HIT 且非关键技） */
    private static Request firstNormalHit(List<Request> picked) {
        for (Request request : picked) {
            if (request.cue == Cue.HIT && !request.isKeyed()) {
                return request;
            }
        }
        return null;
    }

    /** 关键命中起点（施法开声后零延迟命中排后 60ms——§1.2；无施法锚点 = 立即） */
    private float keyHitStart(float now) {
        if (castStartAnchor != null && now - castStartAnchor < SHORT_SCHEDULE_GAP) {
            return castStartAnchor + SHORT_SCHEDULE_GAP;
        }
        return now;
    }

    /** 普通命中跳过窗口（关键声开始后 100ms——§1.2） */
    private boolean skippedAfterCast(float now) {
        return castStartAnchor != null && now - castStartAnchor < NORMAL_AFTER_CAST_SKIP;
    }

    /** 同帧同类首事件胜出（§1.2；关键命中按 tick+sourceId+skillId 合并多目标——事件流追加序即序） */
    private static List<Request> pickPerClass(List<Request> requests) {
        List<Request> picked = new ArrayList<Request>();
        Set<Cue> seen = new HashSet<Cue>();
        for (Request request : requests) {
            if (seen.add(request.cue)) {
                picked.add(request);
            }
        }
        return picked;
    }

    private static void sortForAllocation(List<Request> picked) {
        java.util.Collections.sort(picked, new java.util.Comparator<Request>() {
            @Override
            public int compare(Request a, Request b) {
                return Integer.compare(b.priority, a.priority); // 同帧按优先级分配
            }
        });
    }

    /** 终局（§1.2）：立刻清排期、停止交战声并播放一次结果声 */
    private void finishWithResult(float now, Request request) {
        clear();
        start(now, request);
    }

    private void start(float now, Request request) {
        Channel channel = new Channel();
        channel.cue = request.cue;
        channel.startAt = now;
        channel.endAt = now + durationOf(request.cue);
        channel.volume = request.volume;
        channel.priority = request.priority;
        if (channels.size() >= MAX_CHANNELS && !preempt(request.priority)) {
            return; // 无可抢占低优声道：直接丢（§1.2）
        }
        channel.playId = player.play(channel.cue, channel.volume);
        channels.add(channel);
    }

    private void schedule(float startAt, Request request) {
        if (channels.size() >= MAX_CHANNELS && !preempt(request.priority)) {
            return;
        }
        Channel channel = new Channel();
        channel.cue = request.cue;
        channel.startAt = startAt;
        channel.endAt = startAt + durationOf(request.cue);
        channel.volume = request.volume;
        channel.priority = request.priority;
        channel.scheduled = true;
        channels.add(channel);
    }

    /** 满时高优抢占最早开始的低优声道（Sound.stop(playId)；无可抢占 = false） */
    private boolean preempt(int priority) {
        Channel earliest = null;
        for (Channel channel : channels) {
            if (channel.priority < priority
                    && (earliest == null || channel.startAt < earliest.startAt)) {
                earliest = channel;
            }
        }
        if (earliest == null) {
            return false;
        }
        if (earliest.playId >= 0) {
            player.stop(earliest.playId);
        }
        channels.remove(earliest);
        return true;
    }

    private void sweepExpired(float now) {
        for (int i = channels.size() - 1; i >= 0; i--) {
            Channel channel = channels.get(i);
            if (!channel.scheduled && now >= channel.endAt) {
                channels.remove(i); // 过期清理（§1.2：按素材时长）
            }
        }
    }

    // —— 限频判定 ——

    private boolean castAllowed(float now, Request request) {
        if (now - lastCastStartAt < CAST_MIN_GAP) {
            return false;
        }
        Float last = castStartBySource.get(request.sourceId);
        return last == null || now - last >= CAST_SAME_SOURCE_GAP;
    }

    private boolean keyHitAllowed(float now, Request request) {
        return now - lastKeyHitStartAt >= KEY_HIT_MIN_GAP;
    }

    private boolean killAllowed(float now, Request request) {
        if (now - lastKillStartAt < KILL_MIN_GAP || killedVictims.contains(request.targetId)) {
            return false;
        }
        return true;
    }

    private boolean normalHitAllowed(float now, Request request) {
        if (now - lastNormalHitAt < NORMAL_HIT_MIN_GAP
                || (request.targetId == lastNormalHitTargetId
                && now - lastNormalHitTargetAt < NORMAL_HIT_SAME_TARGET_GAP)) {
            return false;
        }
        if (castStartAnchor != null && now - castStartAnchor < NORMAL_AFTER_CAST_SKIP) {
            return false; // 关键声开始后 100ms 内跳过（§1.2）
        }
        int recent = 0;
        for (float time : normalHitTimes) {
            if (time > now - NORMAL_HIT_ROLLING_WINDOW) {
                recent++;
            }
        }
        if (recent >= NORMAL_HIT_MAX_PER_SECOND) {
            return false;
        }
        normalHitTimes[normalHitCursor % NORMAL_HIT_MAX_PER_SECOND] = now;
        normalHitCursor++;
        lastNormalHitAt = now;
        lastNormalHitTargetId = request.targetId;
        lastNormalHitTargetAt = now;
        return true;
    }
}
