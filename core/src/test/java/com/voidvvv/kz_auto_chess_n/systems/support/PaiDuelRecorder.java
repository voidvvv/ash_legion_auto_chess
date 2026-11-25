package com.voidvvv.kz_auto_chess_n.systems.support;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.BattleUnit;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.CombatEvent;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Side;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.render.AudioScheduler;
import com.voidvvv.kz_auto_chess_n.systems.BattleAttribution;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.ThreatPredictor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 对拍无头试玩记录器（品质改造批6；§0.2「验证记录」字段 + §1.4 归因句 + §1.2 音频触发序）。
 * <b>只读真实系统链路</b>（RunFlowSystem / ShopSystem / RosterSystem / BattleSystem /
 * ThreatPredictor / BattleAttribution / AudioScheduler），不改游戏行为；题表与种子公式
 * 取自 {@link PaiTestScript} 单一来源，夹具与命令链复用 {@link PaiDuelDriver}。
 *
 * <p>流程（§0.2）：备战（§0.3「留钱后升人口」路线，A 题用分散站位、B 题用战士在后）
 * → 开战（逐逻辑 tick 推进，音频调度按未乘倍速的真实帧时钟对齐）→ 胜箱领取 → 第 2/3 战
 * → 第 3 轮胜箱领完走对拍终局（RUN_END + COMPLETED）。
 */
public final class PaiDuelRecorder {

    /** 单战记录（§0.2 验证记录字段 + 归因 + 音频触发序；不可变） */
    public static final class BattleRecord {
        public final PaiTestScript.Topic topic;
        public final long seed;
        public final int round;
        public final List<String> deployment;   // 初始阵位（模板名 @ (x,y)）
        public final List<String> shopSlots;    // 开战前商店槽序（null = 已买空槽）
        public final int roundStartGold;
        public final int battleStartGold;
        public final Integer firstCastTick;     // 首个关键施法 tick
        public final String firstCastSkill;     // 首个关键施法技能名
        public final String firstHitSubject;    // 首个技能命中对象（模板名）
        public final BattleOutcome outcome;
        public final int playerAlive;
        public final int enemyAlive;
        public final String attribution;        // §1.4 归因句
        public final List<String> audioCues;    // §1.2 触发序（cue @ 时钟秒）

        BattleRecord(PaiTestScript.Topic topic, long seed, int round, List<String> deployment,
                     List<String> shopSlots, int roundStartGold, int battleStartGold,
                     Integer firstCastTick, String firstCastSkill, String firstHitSubject,
                     BattleOutcome outcome, int playerAlive, int enemyAlive,
                     String attribution, List<String> audioCues) {
            this.topic = topic;
            this.seed = seed;
            this.round = round;
            this.deployment = deployment;
            this.shopSlots = shopSlots;
            this.roundStartGold = roundStartGold;
            this.battleStartGold = battleStartGold;
            this.firstCastTick = firstCastTick;
            this.firstCastSkill = firstCastSkill;
            this.firstHitSubject = firstHitSubject;
            this.outcome = outcome;
            this.playerAlive = playerAlive;
            this.enemyAlive = enemyAlive;
            this.attribution = attribution;
            this.audioCues = audioCues;
        }
    }

    /** 全程试玩记录（一次完整三战 + 终局） */
    public static final class RunRecord {
        public final PaiTestScript.Topic topic;
        public final long seed;
        public final List<BattleRecord> battles;
        public final boolean reachedRunEnd;
        public final int finalGold;
        public final int finalPlayerLevel;

        RunRecord(PaiTestScript.Topic topic, long seed, List<BattleRecord> battles,
                  boolean reachedRunEnd, int finalGold, int finalPlayerLevel) {
            this.topic = topic;
            this.seed = seed;
            this.battles = battles;
            this.reachedRunEnd = reachedRunEnd;
            this.finalGold = finalGold;
            this.finalPlayerLevel = finalPlayerLevel;
        }
    }

    /** 音频触发记录（cue + 未乘倍速的播放时钟秒——§1.2 触发序对齐） */
    private static final class RecordingPlayer implements AudioScheduler.CuePlayer {
        float now;
        final List<String> cues = new ArrayList<String>();

        @Override
        public long play(AudioScheduler.Cue cue, float volume) {
            cues.add(cue + "@" + String.format(Locale.ROOT, "%.2fs", now));
            return cues.size();
        }

        @Override
        public void stop(long playId) {
            // 试玩记录不含抢占回放（§1.2 抢占由 AudioSchedulerTest 假播放器覆盖）
        }
    }

    /** 各题站位（§1.1 探测表：A 分散 / B 战士在后——三种子均胜的首战布阵） */
    private static PaiDuelDriver.Layout layoutOf(PaiTestScript.Topic topic) {
        return topic == PaiTestScript.Topic.A ? PaiDuelDriver.Layout.A_SCATTER
                : PaiDuelDriver.Layout.B_WARRIOR_BACK;
    }

    private PaiDuelRecorder() {
    }

    /** 完整三战试玩（A/B 任一题；§0.3「留钱后升人口」路线：第 1/2 箱取书、第 3 战前买经验补棋） */
    public static RunRecord playThrough(GameData data, PaiTestScript.Topic topic, long seed) {
        PaiDuelDriver.Session session = PaiDuelDriver.openLayout(data, layoutOf(topic), seed);
        RecordingPlayer player = new RecordingPlayer();
        AudioScheduler scheduler = new AudioScheduler(player);
        BattleSystem battleSystem = new BattleSystem(); // 无状态服务集（状态随 BattleState 传入）
        float clock = 0f;
        List<BattleRecord> battles = new ArrayList<BattleRecord>();

        for (int round = 1; round <= PaiTestScript.TOTAL_ROUNDS; round++) {
            int roundStartGold = session.ctx().getPlayer().getGold();
            prepareRound(session, topic, round);
            int battleStartGold = session.ctx().getPlayer().getGold();
            List<String> deployment = deploymentOf(session);
            List<String> slots = session.slotIds();

            session.startBattleOnly();
            BattleState state = session.lastBattle();
            int steps = 0;
            while (!state.isOver() && steps < 4000) { // 逐逻辑 tick 推进（真实帧时钟对齐）
                battleSystem.step(state);
                clock += 1f / 60f;
                player.now = clock;
                scheduler.syncBattle(state);
                scheduler.advance(clock);
                scheduler.acceptFrame(clock);
                steps++;
            }
            session.observeBattleOver(); // isOver 观察-委托（胜箱/败箱/终局）
            battles.add(record(session, data, topic, seed, round, deployment, slots,
                    roundStartGold, battleStartGold, player));
            // 胜箱领取（留钱后升人口路线：第 1/2 箱取书 → 第 2/3 战前升级；第 3 箱取金）
            if (session.ctx().getRunState().getPendingChest() != null) {
                session.pickChestByKind(round <= 2 ? ChestOption.Kind.EXP_BOOK
                        : ChestOption.Kind.GOLD);
            }
        }
        boolean runEnd = session.ctx().getRunState().getPhase() == GamePhase.RUN_END;
        return new RunRecord(topic, seed, battles, runEnd,
                session.ctx().getPlayer().getGold(), session.ctx().getPlayer().getLevel());
    }

    /** §0.3「留钱后升人口」备战编排（账本已由 SampleDuelLedgerTest 背书） */
    private static void prepareRound(PaiDuelDriver.Session session, PaiTestScript.Topic topic,
                                     int round) {
        if (round == 1) {
            return; // 开局夹具已按 §1.1 购买 W/M/S 并部署（openLayout）
        }
        if (round == 2) { // 第 1 箱取书 → Lv.2：买免费页 P 上 4
            session.buyTemplate(PaiTestScript.P);
            session.deployNextBench(5, 4);
            return;
        }
        // 第 3 战前：第 2 箱取书 → 买 4 经验到 Lv.3，再买免费页 L 上 5
        session.buyExp();
        session.buyTemplate(PaiTestScript.L);
        session.deployNextBench(3, 5);
    }

    private static List<String> deploymentOf(PaiDuelDriver.Session session) {
        List<String> cells = new ArrayList<String>();
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = session.ctx().getPlayer().deployedAt(x, y);
                if (unit != null) {
                    cells.add(unit.getTemplate().getName() + "@(" + x + "," + y + ")");
                }
            }
        }
        return cells;
    }

    private static BattleRecord record(PaiDuelDriver.Session session, GameData data,
                                       PaiTestScript.Topic topic, long seed, int round,
                                       List<String> deployment, List<String> slots,
                                       int roundStartGold, int battleStartGold,
                                       RecordingPlayer player) {
        BattleState state = session.lastBattle();
        Integer firstCastTick = null;
        String firstCastSkill = null;
        String firstHitSubject = null;
        List<CombatEvent> events = state.getEvents();
        for (CombatEvent event : events) {
            if (!ThreatPredictor.isKeyedEnemySkill(event.getSkillId())) {
                continue;
            }
            if (firstCastTick == null && event.getType() == CombatEvent.Type.CAST) {
                firstCastTick = event.getTick();
                firstCastSkill = skillName(data, event.getSkillId());
            }
            if (firstHitSubject == null && event.getType() == CombatEvent.Type.HIT
                    && event.getAmount() > 0f) {
                BattleUnit target = state.getUnitById(event.getTargetId());
                firstHitSubject = target != null ? target.getTemplate().getName()
                        : "#" + event.getTargetId();
            }
            if (firstCastTick != null && firstHitSubject != null) {
                break;
            }
        }
        List<String> cues = new ArrayList<String>(player.cues);
        player.cues.clear();
        return new BattleRecord(topic, seed, round, Collections.unmodifiableList(deployment),
                Collections.unmodifiableList(slots), roundStartGold, battleStartGold,
                firstCastTick, firstCastSkill, firstHitSubject, state.getOutcome(),
                state.aliveCount(Side.PLAYER), state.aliveCount(Side.ENEMY),
                BattleAttribution.attribute(state, data, state.isSurrendered()),
                Collections.unmodifiableList(cues));
    }

    private static String skillName(GameData data, String skillId) {
        com.voidvvv.kz_auto_chess_n.data.SkillData skill = data.getSkill(skillId);
        return skill != null ? skill.getName() : String.valueOf(skillId);
    }

    /** 记录行（收口文档直贴；净支出 = 本轮开战前余额差） */
    public static String format(RunRecord run) {
        StringBuilder out = new StringBuilder();
        out.append("### ").append(run.topic == PaiTestScript.Topic.A ? "A 破阵踏" : "B 处决")
                .append(" · 种子 ").append(run.seed).append("\n\n");
        out.append("| 轮 | 初始阵位 | 商店槽序 | 净支出 | 首个关键施法 | 首个命中对象 | 结局 | 存活(玩/敌) | 归因 |\n");
        out.append("|---|---|---|---|---|---|---|---|---|\n");
        for (BattleRecord record : run.battles) {
            out.append("| ").append(record.round)
                    .append(" | ").append(join(record.deployment))
                    .append(" | ").append(join(record.shopSlots))
                    .append(" | ").append(record.roundStartGold - record.battleStartGold).append(" 金")
                    .append(" | ").append(record.firstCastTick == null ? "（未施法）"
                            : "t" + record.firstCastTick + " " + record.firstCastSkill)
                    .append(" | ").append(record.firstHitSubject == null ? "（未命中）" : record.firstHitSubject)
                    .append(" | ").append(record.outcome)
                    .append(" | ").append(record.playerAlive).append("/").append(record.enemyAlive)
                    .append(" | ").append(record.attribution)
                    .append(" |\n");
        }
        out.append("\n终局：").append(run.reachedRunEnd ? "对拍终局（RUN_END/COMPLETED）" : "未达终局")
                .append("；末余额 ").append(run.finalGold).append(" 金；等级 Lv.")
                .append(run.finalPlayerLevel).append("。\n");
        out.append("\n音频触发序（未乘倍速时钟）：\n\n```\n");
        for (BattleRecord record : run.battles) {
            out.append("第 ").append(record.round).append(" 战: ");
            out.append(record.audioCues.isEmpty() ? "（无）" : String.join(" → ", record.audioCues));
            out.append("\n");
        }
        out.append("```\n");
        return out.toString();
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(" / ");
            }
            String value = values.get(i);
            out.append(value == null ? "空" : value);
        }
        return out.toString();
    }
}
