package com.voidvvv.kz_auto_chess_n.systems;

import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SkillData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.entities.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 赛后归因（品质改造 §1.4）：为结束的 BattleState 计算一条有事实依据、可用于下次布阵的话。
 * 纯 Java、零 Gdx；读 {@code BattleState.getEvents()} 原始追加流 + getUnitById + GameData 名称。
 * 屏幕按 BattleState 引用缓存，一经算出供结算横幅与箱子共用（两处只显其一）。
 *
 * <p>关键命中（§1.4）：只把匹配题目敌方模板与技能的玩家侧正扣血计入——A（破阵踏）从对应 CAST 起
 * 取同 tick 同源同技能命中，去重后数目标；B（处决）以每条同源单目标 HIT 为证据（HOMING 追踪落点，
 * <b>禁止把施法时锁定目标当作已命中</b>）。匹配死亡 = 同 tick、同亡者、同 killerId，且该伤害是
 * 该目标死亡前最后一条正扣血事件（文案「随后倒下」，避免把同期不可见伤害归为该技能）。
 * 候选优先级与九行文案表见设计书 §1.4；同类多条取 A 命中人数多 → 技能扣血总额 → 最早事件序。
 */
public final class BattleAttribution {

    private BattleAttribution() {
    }

    /** 关键命中（不可变）：一类题目敌技的一次实测命中证据 */
    static final class KeyHit {
        final String skillId;
        final boolean area;            // A 群伤（CAST 同 tick 去重数目标）/ B 单目标
        final int targetCount;         // A = 去重目标数；B = 1
        final int sourceId;            // 施法敌方 battle id
        final int primaryTargetId;     // 点名兜底（A = 最早命中目标；B = 命中目标）
        final float totalDamage;       // 技能扣血总额（同类排序键）
        final int firstEventIndex;     // 事件序（同类排序键）
        final Integer deathUnitId;     // 匹配死亡的亡者（无 = null）

        KeyHit(String skillId, boolean area, int targetCount, int sourceId, int primaryTargetId,
               float totalDamage, int firstEventIndex, Integer deathUnitId) {
            this.skillId = skillId;
            this.area = area;
            this.targetCount = targetCount;
            this.sourceId = sourceId;
            this.primaryTargetId = primaryTargetId;
            this.totalDamage = totalDamage;
            this.firstEventIndex = firstEventIndex;
            this.deathUnitId = deathUnitId;
        }
    }

    /** 候选优先级类（§1.4：序即优先级） */
    private enum Rank {
        SURRENDER, TIMEOUT, NO_DEPLOYMENT, KEY_HIT_WITH_DEATH, A_MULTI_HIT,
        OTHER_KEY_HIT, CAST_WITHOUT_DAMAGE, KILLED_BEFORE_CAST, GENERIC
    }

    /** 候选（不可变）：优先级类 + 句子 + 同类排序键（A 目标数 / 技能扣血总额 / 最早事件序） */
    private static final class Candidate {
        final Rank rank;
        final String sentence;
        final int aTargets;
        final float skillDamage;
        final int firstEventIndex;

        Candidate(Rank rank, String sentence, int aTargets, float skillDamage, int firstEventIndex) {
            this.rank = rank;
            this.sentence = sentence;
            this.aTargets = aTargets;
            this.skillDamage = skillDamage;
            this.firstEventIndex = firstEventIndex;
        }
    }

    private static final Comparator<Candidate> WITHIN_RANK = new Comparator<Candidate>() {
        @Override
        public int compare(Candidate a, Candidate b) {
            if (a.aTargets != b.aTargets) {
                return Integer.compare(b.aTargets, a.aTargets); // A 命中人数多者优先
            }
            if (a.skillDamage != b.skillDamage) {
                return Float.compare(b.skillDamage, a.skillDamage); // 技能扣血总额
            }
            return Integer.compare(a.firstEventIndex, b.firstEventIndex); // 最早事件序
        }
    };

    /**
     * 归因主入口（§1.4）：按候选优先级取第一条成立者；{@code surrendered} 来自
     * {@code BattleState.isSurrendered()}（成功 SurrenderCommand 处理入口写入）。
     */
    public static String attribute(BattleState state, GameData data, boolean surrendered) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        collectCandidates(state, data, surrendered, candidates);
        if (candidates.isEmpty()) {
            return genericSentence(state); // 防御：无任何候选（理论不可达）回通用结果
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                int byRank = Integer.compare(a.rank.ordinal(), b.rank.ordinal());
                return byRank != 0 ? byRank : WITHIN_RANK.compare(a, b);
            }
        });
        return candidates.get(0).sentence;
    }

    // —— 候选收集（按 §1.4 优先级顺序） ——

    private static void collectCandidates(BattleState state, GameData data, boolean surrendered,
                                          List<Candidate> out) {
        List<CombatEvent> events = state.getEvents();
        List<KeyHit> keyHits = collectKeyHits(state, events);
        Set<String> castedSkillIds = castedKeyedSkillIds(events, state);

        if (surrendered) { // ① 主动认输
            out.add(new Candidate(Rank.SURRENDER, "本场主动认输，下次开战前可先检查敌方危险格。", 0, 0f, 0));
            return;
        }
        if (state.getOutcome() == BattleOutcome.TIMEOUT) { // ② 超时
            out.add(new Candidate(Rank.TIMEOUT, (int) GameBalance.BATTLE_TIMEOUT + "秒时敌方还有"
                    + state.aliveCount(Side.ENEMY) + "枚棋子，下次让输出更早接敌。", 0, 0f, 0));
            return;
        }
        if (deployedCount(state) == 0) { // ③ 零部署
            out.add(new Candidate(Rank.NO_DEPLOYMENT, "这场没有上阵棋子，先拖一枚棋子到己方格。", 0, 0f, 0));
            return;
        }
        Candidate withDeath = keyHitWithDeath(keyHits, state, data); // ④ 带关联死亡的关键命中
        if (withDeath != null) {
            out.add(withDeath);
            return;
        }
        Candidate aMulti = firstKeyHit(keyHits, Rank.A_MULTI_HIT, state, data, true); // ⑤ A 命中 ≥2 人
        if (aMulti != null) {
            out.add(aMulti);
            return;
        }
        Candidate other = firstKeyHit(keyHits, Rank.OTHER_KEY_HIT, state, data, false); // ⑥ 其它关键命中
        if (other != null) {
            out.add(other);
            return;
        }
        if (!castedSkillIds.isEmpty()) { // ⑦ 已确认施法但无对应正扣血
            String skillName = smallestName(castedSkillIds, data);
            out.add(new Candidate(Rank.CAST_WITHOUT_DAMAGE,
                    skillName + "施放后未扣血，下次可先试用相同站位。", 0, 0f, 0));
            return;
        }
        String killedBeforeCast = killedBeforeFirstCast(events, state, data); // ⑧ 题目敌人在首施法前被击杀
        if (killedBeforeCast != null) {
            out.add(new Candidate(Rank.KILLED_BEFORE_CAST, killedBeforeCast, 0, 0f, 0));
            return;
        }
        out.add(new Candidate(Rank.GENERIC, genericSentence(state), 0, 0f, 0)); // ⑨ 通用结果
    }

    /** 普通战场优先报告首次异侧控制，其次首次阵亡；没有可核事件时沿用结果摘要。 */
    private static String genericSentence(BattleState state) {
        CombatEvent firstDeath = null;
        for (CombatEvent event : state.getEvents()) {
            String status = controlName(state, event);
            if (status != null) {
                BattleUnit source = state.getUnitById(event.getSourceId());
                BattleUnit target = state.getUnitById(event.getTargetId());
                return sideName(source) + "对" + sideName(target) + "施加" + status + "。";
            }
            if (firstDeath == null && event.getType() == CombatEvent.Type.UNIT_DIED
                    && state.getUnitById(event.getSourceId()) != null) {
                firstDeath = event;
            }
        }
        if (firstDeath != null) return sideName(state.getUnitById(firstDeath.getSourceId())) + "最先阵亡。";
        int survivors = state.aliveCount(Side.PLAYER);
        int deployed = deployedCount(state);
        if (state.getOutcome() == BattleOutcome.PLAYER_WIN) {
            return "己方剩" + survivors + "枚棋子，下次可先沿用这套站位。";
        }
        if (survivors == 0) {
            return "己方上阵" + deployed + "枚全数出局，下次让耐打的棋子先接敌。";
        }
        return "本场战败，下次先把耐打的棋子放到敌人接近的一侧。";
    }

    /** 只依据状态落地事件。来源可为技能或羁绊，不据此推断成因。 */
    public static String controlName(BattleState state, CombatEvent event) {
        if (event.getType() != CombatEvent.Type.STATUS_APPLIED) return null;
        BattleUnit source = state.getUnitById(event.getSourceId());
        BattleUnit target = state.getUnitById(event.getTargetId());
        if (source == null || target == null || source.getSide() == target.getSide()) return null;
        switch (event.getStatusType()) {
            case STUN: return "眩晕";
            case SLOW: return "减速";
            default: return null;
        }
    }

    private static String sideName(BattleUnit unit) {
        return (unit.getSide() == Side.PLAYER ? "己方" : "敌方") + unit.getTemplate().getName();
    }

    /** 上阵棋子数（开局部署名单 = 玩家侧单位总数，含亡者——列表终身持有） */
    private static int deployedCount(BattleState state) {
        int count = 0;
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() == Side.PLAYER) {
                count++;
            }
        }
        return count;
    }

    // —— 关键命中收集（§1.4 识别口径） ——

    private static Set<String> castedKeyedSkillIds(List<CombatEvent> events, BattleState state) {
        Set<String> ids = new HashSet<String>();
        for (CombatEvent event : events) {
            if (event.getType() != CombatEvent.Type.CAST) continue;
            BattleUnit caster = state.getUnitById(event.getSourceId());
            if (caster != null && caster.getSide() == Side.ENEMY
                    && topicEnemyOf(caster.getTemplate().getId(), event.getSkillId())) {
                ids.add(event.getSkillId());
            }
        }
        return ids;
    }

    /** 集合中最短名（⑦ 文案点名兜底；集合通常单元素） */
    private static String smallestName(Set<String> skillIds, GameData data) {
        String best = null;
        for (String skillId : skillIds) {
            String name = skillName(data, skillId);
            if (best == null || name.length() < best.length()) {
                best = name;
            }
        }
        return best == null ? "" : best;
    }

    private static String skillName(GameData data, String skillId) {
        SkillData skill = skillId == null ? null : data.getSkill(skillId);
        return skill != null ? skill.getName() : String.valueOf(skillId);
    }

    /** 题目敌技名（§1.4 文案 {技能}） */
    private static String skillNameOf(KeyHit keyHit, GameData data) {
        return skillName(data, keyHit.skillId);
    }

    /** 单位名（{棋子}/{敌棋}；查不到回退 #id——防御路径） */
    private static String unitName(BattleState state, int unitId) {
        BattleUnit unit = state.getUnitById(unitId);
        return unit != null ? unit.getTemplate().getName() : "#" + unitId;
    }

    /** 单位是否战士（§1.4 B 行「可确认的战士」——职业词表） */
    private static boolean isWarrior(BattleState state, int unitId) {
        BattleUnit unit = state.getUnitById(unitId);
        return unit != null && "战士".equals(unit.getTemplate().getUnitClass());
    }

    /**
     * ⑧「{敌棋}在施放{技能}前倒下」（§1.4）：战斗中存在题目敌人（模板+技能二元组），
     * 其死亡事件先于自身首次施法（或从未施法）；多个命中取事件序最早者。无则返回 null。
     */
    private static String killedBeforeFirstCast(List<CombatEvent> events, BattleState state,
                                                GameData data) {
        for (BattleUnit unit : state.getUnits()) {
            if (unit.getSide() != Side.ENEMY
                    || !topicEnemyOf(unit.getTemplate().getId(), unit.getTemplate().getSkillId())) {
                continue;
            }
            Integer deathTick = null;
            int firstCastTick = Integer.MAX_VALUE;
            for (CombatEvent event : events) {
                if (event.getType() == CombatEvent.Type.UNIT_DIED && event.getSourceId() == unit.getId()
                        && deathTick == null) {
                    deathTick = event.getTick();
                }
                if (event.getType() == CombatEvent.Type.CAST && event.getSourceId() == unit.getId()) {
                    firstCastTick = Math.min(firstCastTick, event.getTick());
                }
            }
            if (deathTick != null && deathTick < firstCastTick) {
                return unit.getTemplate().getName() + "在施放"
                        + skillName(data, unit.getTemplate().getSkillId())
                        + "前倒下，下次可试沿用这套开局站位。";
            }
        }
        return null;
    }

    /**
     * 关键命中收集（§1.4）：玩家侧正扣血且 (来源模板, 技能) 为题目二元组。
     * A = CAST 起同 tick 同源同技能去重数目标；B = 每条单目标 HIT 一条证据；随后匹配死亡。
     */
    private static List<KeyHit> collectKeyHits(BattleState state, List<CombatEvent> events) {
        List<KeyHit> hits = new ArrayList<KeyHit>();
        for (int i = 0; i < events.size(); i++) {
            CombatEvent event = events.get(i);
            if (!isKeyedEnemyHit(event, state)) {
                continue;
            }
            boolean isStomp = ThreatPredictor.SKILL_BREAK_STOMP.equals(event.getSkillId());
            if (isStomp) {
                if (!hasCast(events, event.getSourceId(), event.getSkillId(), event.getTick())) {
                    continue; // A 证据必须挂在对应 CAST 之后（§1.4：从对应 CAST 起取同 tick 命中）
                }
                hits.add(stompKeyHit(state, events, i, event));
            } else {
                hits.add(new KeyHit(event.getSkillId(), false, 1, event.getSourceId(),
                        event.getTargetId(), event.getAmount(), i,
                        matchedDeath(state, events, i, event)));
            }
        }
        return hits;
    }

    /** 玩家侧正扣血且来源为题目敌方（模板 + 技能二元组，§1.4） */
    private static boolean isKeyedEnemyHit(CombatEvent event, BattleState state) {
        if (event.getType() != CombatEvent.Type.HIT || event.getAmount() <= 0f
                || !ThreatPredictor.isKeyedEnemySkill(event.getSkillId())) {
            return false;
        }
        BattleUnit source = state.getUnitById(event.getSourceId());
        BattleUnit target = state.getUnitById(event.getTargetId());
        return source != null && source.getSide() == Side.ENEMY
                && target != null && target.getSide() == Side.PLAYER
                && topicEnemyOf(source.getTemplate().getId(), event.getSkillId());
    }

    /** 来源模板 + 技能是否构成题目二元组（§1.1/§1.4 同一口径） */
    private static boolean topicEnemyOf(String templateId, String skillId) {
        return (ThreatPredictor.UNIT_GLACIAL_GIANT.equals(templateId)
                && ThreatPredictor.SKILL_BREAK_STOMP.equals(skillId))
                || (ThreatPredictor.UNIT_SHADOW_BLADE.equals(templateId)
                && ThreatPredictor.SKILL_EXECUTE.equals(skillId));
    }

    private static boolean hasCast(List<CombatEvent> events, int sourceId, String skillId, int tick) {
        for (CombatEvent event : events) {
            if (event.getType() == CombatEvent.Type.CAST && event.getTick() == tick
                    && event.getSourceId() == sourceId && skillId.equals(event.getSkillId())) {
                return true;
            }
        }
        return false;
    }

    /** A 群伤证据：同 tick 同源同技能正扣血去重数目标（事件序 = CAST 序；点名 = 最早命中目标） */
    private static KeyHit stompKeyHit(BattleState state, List<CombatEvent> events, int castIndex,
                                      CombatEvent cast) {
        Set<Integer> targets = new HashSet<Integer>();
        float total = 0f;
        int firstTarget = -1;
        for (int i = castIndex; i < events.size(); i++) {
            CombatEvent event = events.get(i);
            if (event.getTick() > cast.getTick()) {
                break; // 同 tick 群伤窗口
            }
            if (event.getType() == CombatEvent.Type.HIT
                    && ThreatPredictor.SKILL_BREAK_STOMP.equals(event.getSkillId())
                    && event.getSourceId() == cast.getSourceId() && event.getAmount() > 0f) {
                targets.add(event.getTargetId());
                total += event.getAmount();
                if (firstTarget < 0) {
                    firstTarget = event.getTargetId();
                }
            }
        }
        Integer death = null;
        for (Integer target : targets) {
            death = matchedDeath(state, events, castIndex,
                    CombatEvent.hit(cast.getTick(), cast.getSourceId(), target, total, false,
                            cast.getSkillId()));
            if (death != null) {
                break; // 任一去重目标的匹配死亡即成立（§1.4「带关联死亡」）
            }
        }
        return new KeyHit(cast.getSkillId(), true, targets.size(), cast.getSourceId(),
                firstTarget, total, castIndex, death);
    }

    /**
     * 匹配死亡（§1.4）：同 tick、同亡者（命中目标）、同 killerId（命中来源），
     * 且该命中是亡者死亡事件前最后一条正扣血 HIT——否则死亡另有其因，不归为该技能。
     */
    private static Integer matchedDeath(BattleState state, List<CombatEvent> events, int hitIndex,
                                        CombatEvent hit) {
        for (int i = hitIndex + 1; i < events.size(); i++) {
            CombatEvent event = events.get(i);
            if (event.getTick() > hit.getTick()) {
                break; // 匹配死亡窗口 = 同 tick（§1.4：同 tick、同亡者、同 killerId）
            }
            if (event.getType() == CombatEvent.Type.UNIT_DIED
                    && event.getSourceId() == hit.getTargetId()
                    && event.getKillerId() == hit.getSourceId()) {
                return hit.getTargetId();
            }
            if (event.getType() == CombatEvent.Type.HIT && event.getTargetId() == hit.getTargetId()
                    && event.getAmount() > 0f) {
                return null; // 亡者在本命中之后还有正扣血：本命中不是最后一条（§1.4 口径）→ 不归因
            }
        }
        return null;
    }

    // —— 候选构造（④⑤⑥ 文案表） ——

    /** ④ 带关联死亡的关键命中（A/B 两式；同类多条由排序收敛） */
    private static Candidate keyHitWithDeath(List<KeyHit> keyHits, BattleState state, GameData data) {
        Candidate best = null;
        for (KeyHit keyHit : keyHits) {
            if (keyHit.deathUnitId == null) {
                continue;
            }
            String victim = unitName(state, keyHit.deathUnitId);
            String sentence = keyHit.area
                    ? "破阵踏打中" + keyHit.targetCount + "人，" + victim + "随后倒下，下次让后排离前排至少两格。"
                    : victim + "被处决打中后倒下，下次把战士摆到最后一排。";
            Candidate candidate = new Candidate(Rank.KEY_HIT_WITH_DEATH, sentence,
                    keyHit.area ? keyHit.targetCount : 0, keyHit.totalDamage, keyHit.firstEventIndex);
            if (best == null || WITHIN_RANK.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /** ⑤⑥ 关键命中（A 群伤/A 单目标/B 战士/B 其它——按目标数与职业分流） */
    private static Candidate firstKeyHit(List<KeyHit> keyHits, Rank rank, BattleState state,
                                         GameData data, boolean areaOnly) {
        Candidate best = null;
        for (KeyHit keyHit : keyHits) {
            if (keyHit.deathUnitId != null) {
                continue; // 带关联死亡已在 ④ 消费
            }
            if (rank == Rank.A_MULTI_HIT && (!keyHit.area || keyHit.targetCount < 2)) {
                continue; // A 命中 ≥2 人
            }
            if (rank == Rank.OTHER_KEY_HIT) {
                if (keyHit.area && keyHit.targetCount >= 2) {
                    continue; // 已在 ⑤ 消费
                }
            }
            String sentence = keyHitSentence(keyHit, state, data);
            Candidate candidate = new Candidate(rank, sentence,
                    keyHit.area ? keyHit.targetCount : 0, keyHit.totalDamage, keyHit.firstEventIndex);
            if (best == null || WITHIN_RANK.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /** §1.4 文案表：⑤ A 群伤 / ⑥ A 单目标 / ⑥ B 战士 / ⑥ B 其它 */
    private static String keyHitSentence(KeyHit keyHit, BattleState state, GameData data) {
        if (keyHit.area) {
            if (keyHit.targetCount >= 2) { // ⑤（调用方分流，防御性双写）
                return "破阵踏打中" + keyHit.targetCount + "人，下次让后排离前排至少两格。";
            }
            return "破阵踏只打中" + unitName(state, keyHit.primaryTargetId)
                    + "，下次可让其他棋子继续隔两格支援。";
        }
        String victim = unitName(state, keyHit.primaryTargetId);
        if (isWarrior(state, keyHit.primaryTargetId)) {
            return "战士" + victim + "接下处决，下次可以沿用战士后置。";
        }
        return "处决打中" + victim + "，下次可让耐打的棋子站最后。";
    }
}
