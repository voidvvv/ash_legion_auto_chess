package com.voidvvv.kz_auto_chess_n.screens;

import com.badlogic.gdx.Screen;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.save.MetaService;
import com.voidvvv.kz_auto_chess_n.save.Profile;
import com.voidvvv.kz_auto_chess_n.save.ProfileService;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Narrow practice-session boundary. Call on the game thread between frames. Views may cross threads. */
public final class PracticeAccess {
    private final GameData data;
    private final MetaService meta;
    private final Supplier<Screen> activeScreen;

    public PracticeAccess(GameData data, MetaService meta, Supplier<Screen> activeScreen) {
        this.data = data;
        this.meta = meta;
        this.activeScreen = activeScreen;
    }

    public View read() {
        List<SceneView> scenes = new ArrayList<>();
        Profile profile = meta.getProfile();
        for (SceneData scene : data.getScenes().values()) {
            scenes.add(new SceneView(scene, profile));
        }
        BattleScreen screen = battleScreen();
        RunContext ctx = screen == null ? null : screen.practiceContext();
        return new View(scenes, ctx == null ? null : new RunView(ctx),
                screen == null ? "请先开始或继续远征。" : screen.practiceBlockReason());
    }

    public Result setSceneGrant(String sceneId, boolean open) {
        SceneData scene = data.getScenes().get(sceneId);
        if (scene == null) return Result.rejected("场景不存在。");
        Profile profile = meta.getProfile();
        boolean naturallyOpen = ProfileService.isNaturallyUnlocked(profile, scene);
        boolean granted = profile.getPracticeSceneGrants().contains(sceneId);
        if (naturallyOpen && (open || !granted)) {
            return Result.rejected("此场景由正常进度开放，练习授权无法关闭它。");
        }
        if (granted == open) {
            return Result.rejected(open ? "此场景已获练习授权。" : "此场景已锁定。");
        }
        if (!meta.savePracticeSceneGrant(sceneId, open)) {
            return Result.rejected("练习档案写入失败，开放状态未更改。");
        }
        if (open) return Result.saved("已授权此场景。");
        return Result.saved(naturallyOpen ? "练习授权已撤销，场景仍由正常进度开放。" : "已撤销此场景的练习授权。");
    }

    public Result setGold(RunView expected, int gold) {
        BattleScreen screen = battleScreen();
        String reason = checkRun(screen, expected);
        if (reason != null) return Result.rejected(reason);
        String invalid = PracticeRules.setGold(screen.practiceContext(), gold);
        if (invalid != null) return Result.rejected(invalid);
        return Result.applied("金币已设为 " + gold + "。", screen.savePracticeChange());
    }

    public Result changeExp(RunView expected, int delta) {
        BattleScreen screen = battleScreen();
        String reason = checkRun(screen, expected);
        if (reason != null) return Result.rejected(reason);
        String invalid = PracticeRules.changeExp(screen.practiceContext(), delta);
        if (invalid != null) return Result.rejected(invalid);
        return Result.applied("已调整经验 " + (delta > 0 ? "+" : "") + delta + "。", screen.savePracticeChange());
    }

    public Result editRoster(RunView expected, SlotView slot, RosterSystem.Edit edit, String templateId, int star) {
        BattleScreen screen = battleScreen();
        String reason = checkRun(screen, expected);
        if (reason != null) return Result.rejected(reason);
        if (slot == null) return Result.rejected("请选择备战席或己方格。");
        if (!expected.bench.contains(slot) && !expected.deployment.contains(slot)) {
            return Result.rejected("该位置不属于当前读数，请重新选择。");
        }
        String invalid = screen.editPracticeRoster(slot.target, slot.unitId, slot.star, edit, templateId, star);
        if (invalid != null) return Result.rejected(invalid);
        String message;
        switch (edit) {
            case ADD: message = "已新增棋子。"; break;
            case REPLACE: message = "已替换棋子，原装备已归还背包。"; break;
            case REMOVE: message = "已移除棋子，原装备已归还背包。"; break;
            default: message = "星级已设为 " + star + "，装备保留。";
        }
        return Result.applied(message, screen.savePracticeChange());
    }

    public Result jump(RunView expected, String sceneId, int round) {
        BattleScreen screen = battleScreen();
        String reason = checkRun(screen, expected);
        if (reason != null) return Result.rejected(reason);
        RunContext ctx = screen.practiceContext();
        if (!data.getScenes().containsKey(sceneId)) return Result.rejected("目标场景不存在。");
        if (!meta.isSceneUnlocked(sceneId, data)) return Result.rejected("目标场景尚未开放。");
        if (round < 1 || round > ctx.totalRounds()) {
            return Result.rejected("波次必须在 1 至 " + ctx.totalRounds() + " 之间。");
        }
        RunState run = ctx.getRunState();
        if (run.getSceneId().equals(sceneId) && round == run.getRound()) {
            return Result.rejected("已在该波次，重试、敌阵和补给状态保持原样。");
        }
        if (run.getSceneId().equals(sceneId) && round > run.getRound()) {
            screen.preparePracticeWave(round);
            return Result.applied("已前往第 " + round + " 波，保留阵容与经济。", screen.savePracticeChange());
        }
        return Result.applied("已从第 " + round + " 波开启新练习远征。",
                screen.replaceWithPracticeRun(sceneId, round));
    }

    private BattleScreen battleScreen() {
        Screen active = activeScreen.get();
        return active instanceof BattleScreen ? (BattleScreen) active : null;
    }

    private static String checkRun(BattleScreen screen, RunView expected) {
        if (screen == null || expected == null) return "请先开始或继续远征。";
        String reason = screen.practiceBlockReason();
        if (reason != null) return reason;
        RunState run = screen.practiceContext().getRunState();
        if (run.getSeed() != expected.seed || !run.getSceneId().equals(expected.sceneId)
                || run.getRound() != expected.round) {
            return "当前远征或波次已变化，请核对后重试。";
        }
        return null;
    }

    public static final class View {
        public final List<SceneView> scenes;
        public final RunView run;
        public final String runBlockReason;

        private View(List<SceneView> scenes, RunView run, String runBlockReason) {
            this.scenes = Collections.unmodifiableList(scenes);
            this.run = run;
            this.runBlockReason = runBlockReason;
        }
    }

    public static final class SceneView {
        public final String id;
        public final String name;
        public final String prerequisite;
        public final boolean naturalOpen;
        public final boolean granted;
        public final boolean completed;

        private SceneView(SceneData scene, Profile profile) {
            id = scene.getId();
            name = scene.getName();
            prerequisite = scene.getUnlockAfter();
            naturalOpen = ProfileService.isNaturallyUnlocked(profile, scene);
            granted = profile.getPracticeSceneGrants().contains(id);
            completed = profile.getCompletedScenes().contains(id);
        }

        public boolean isOpen() {
            return naturalOpen || granted;
        }
    }

    public static final class RunView {
        private final long seed;
        public final String sceneId;
        public final int round;
        public final int totalRounds;
        public final GamePhase phase;
        public final int gold;
        public final int level;
        public final int exp;
        public final int nextLevelExp;
        public final int remainingExp;
        public final int population;
        public final int deployed;
        public final int retriesUsed;
        public final int retryLimit;
        public final boolean practice;
        public final boolean chestPending;
        public final boolean incomePaid;
        public final String enemies;
        public final List<SlotView> bench;
        public final List<SlotView> deployment;
        public final List<TemplateView> templates;
        public final int boardColumns;

        private RunView(RunContext ctx) {
            RunState run = ctx.getRunState();
            Player player = ctx.getPlayer();
            seed = run.getSeed();
            sceneId = run.getSceneId();
            round = run.getRound();
            totalRounds = ctx.totalRounds();
            phase = run.getPhase();
            gold = player.getGold();
            level = player.getLevel();
            exp = player.getCurrentExp();
            nextLevelExp = GameBalance.expToNextLevel(level);
            remainingExp = PracticeRules.remainingExp(player);
            population = player.getPopulationCap();
            deployed = player.getDeployedUnits().size();
            retriesUsed = run.getDefeatCountPerRound();
            retryLimit = GameBalance.DEFEAT_LIMIT_PER_ROUND;
            practice = run.isPractice();
            chestPending = run.getPendingChest() != null;
            incomePaid = run.isRoundIncomePaid();
            List<String> names = new ArrayList<>();
            for (WaveSpec enemy : run.getEnemyWave()) {
                names.add(enemy.getTemplate().getName());
            }
            enemies = String.join("、", names);
            List<SlotView> benchSlots = new ArrayList<>();
            List<Unit> benchUnits = player.getBench();
            for (int i = 0; i < GameBalance.BENCH_SIZE; i++) {
                Unit unit = i < benchUnits.size() ? benchUnits.get(i) : null;
                benchSlots.add(new SlotView(new PlacementTarget.Bench(i), unit, i == benchUnits.size()));
            }
            bench = Collections.unmodifiableList(benchSlots);
            List<SlotView> grid = new ArrayList<>();
            boardColumns = GameBalance.BOARD_COLS;
            for (int y = 4; y <= 6; y++) {
                for (int x = 0; x < boardColumns; x++) {
                    Unit unit = player.deployedAt(x, y);
                    grid.add(new SlotView(new PlacementTarget.Cell(x, y), unit, unit == null));
                }
            }
            deployment = Collections.unmodifiableList(grid);
            List<TemplateView> choices = new ArrayList<>();
            for (UnitData template : ctx.getGameData().getUnits().values()) {
                if (RosterSystem.isPracticeTemplate(ctx, template)) choices.add(new TemplateView(template));
            }
            templates = Collections.unmodifiableList(choices);
        }
    }

    public static final class SlotView {
        private final PlacementTarget target;
        public final String position;
        public final int unitId;
        public final String name;
        public final String templateId;
        public final int star;
        public final int equipment;
        public final boolean canAdd;

        private SlotView(PlacementTarget target, Unit unit, boolean canAdd) {
            this.target = target;
            if (target instanceof PlacementTarget.Bench) {
                position = Integer.toString(((PlacementTarget.Bench) target).slotIndex + 1);
            } else {
                PlacementTarget.Cell cell = (PlacementTarget.Cell) target;
                position = "(" + cell.gridX + ", " + cell.gridY + ")";
            }
            unitId = unit == null ? 0 : unit.getId();
            name = unit == null ? "" : unit.getTemplate().getName();
            templateId = unit == null ? "" : unit.getTemplate().getId();
            star = unit == null ? 0 : unit.getStar();
            equipment = unit == null ? 0 : unit.getEquipped().size();
            this.canAdd = canAdd;
        }

        public boolean occupied() {
            return unitId != 0;
        }
    }

    public static final class TemplateView {
        public final String id;
        public final String name;

        private TemplateView(UnitData template) {
            id = template.getId();
            name = template.getName();
        }
    }

    public static final class Result {
        public final boolean applied;
        public final boolean saved;
        public final String message;

        private Result(boolean applied, boolean saved, String message) {
            this.applied = applied;
            this.saved = saved;
            this.message = message;
        }

        private static Result rejected(String message) {
            return new Result(false, false, message);
        }

        private static Result saved(String message) {
            return applied(message, true);
        }

        private static Result applied(String message, boolean saved) {
            return new Result(true, saved, message + (saved ? " 已保存。" : " 已在游戏中生效，快照写入失败。"));
        }
    }
}
